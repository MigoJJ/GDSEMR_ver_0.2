package com.emr.gds.soap.IMSFollowUp;

import com.emr.gds.input.IAITextAreaManager;
import com.emr.gds.main.IAMProblemAction;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An editor for creating and managing Plan and Follow-up entries in the EMR.
 */
public class PlanFollowupAction {

    private final IAITextAreaManager textAreaManager;
    private final IAMProblemAction problemAction;
    private Stage editorStage;
    private TextArea editorTextArea;
    private TextField fuField, medsCodeField;
    private final Map<String, String> abbrevMap = new HashMap<>();
    private final PlanRepository planRepo;

    private static final String[] PLAN_TEMPLATES = {
            "1w", "2w", "4w", "1d", "3d", "7d", "1m", "3m", "6m", ":cd",
            "5", "55", "6", "8", "2", "4", "0", "1"
    };

    public PlanFollowupAction(IAITextAreaManager textAreaManager, IAMProblemAction problemAction) {
        this.textAreaManager = textAreaManager;
        this.problemAction = problemAction;
        this.planRepo = new PlanRepository(getDbPath("plan_history.db"));
        initDatabases();
        createEditorWindow();
    }

    public void showAndWait() {
        editorStage.showAndWait();
    }

    private void initDatabases() {
        try {
            Class.forName("org.sqlite.JDBC");
            initAbbrevDatabase();
            planRepo.init();
        } catch (Exception e) {
            showError("Failed to initialize databases: " + e.getMessage());
        }
    }

    private void initAbbrevDatabase() throws Exception {
        Path dbFile = getDbPath("abbreviations.db");
        if (!Files.exists(dbFile.getParent())) Files.createDirectories(dbFile.getParent());
        String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM abbreviations")) {
            while (rs.next()) {
                abbrevMap.put(rs.getString("short"), rs.getString("full"));
            }
        }
    }

    private void createEditorWindow() {
        editorStage = new Stage();
        editorStage.setTitle("Plan & Follow-up Editor");
        editorStage.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setTop(createTopSection());
        root.setCenter(createCenterSection());
        root.setBottom(createBottomSection());

        editorStage.setScene(new Scene(root, 1100, 600));
    }

    private VBox createTopSection() {
        return new VBox(5, createStyledLabel("Plan & Follow-up Editor", "-fx-font-size: 16px; -fx-font-weight: bold;"));
    }

    private VBox createCenterSection() {
        editorTextArea = new TextArea();
        editorTextArea.setWrapText(true);
        editorTextArea.setPrefRowCount(10);

        TextArea previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setWrapText(true);
        previewArea.setPrefRowCount(4);
        previewArea.setStyle("-fx-background-color: #f5f5f5;");

        editorTextArea.textProperty().addListener((obs, old, val) -> previewArea.setText(expandAbbreviations(val)));

        return new VBox(10,
                createQuickPlanPanel(),
                new Label("Plan Text:"), editorTextArea,
                new Label("Preview:"), previewArea
        );
    }

    private GridPane createQuickPlanPanel() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        fuField = new TextField();
        medsCodeField = new TextField();
        fuField.setOnAction(e -> medsCodeField.requestFocus());
        medsCodeField.setOnAction(e -> insertQuickPlan());

        grid.add(new Label("Follow-up:"), 0, 0);
        grid.add(fuField, 1, 0);
        grid.add(new Label("Meds Code:"), 0, 1);
        grid.add(medsCodeField, 1, 1);

        GridPane templateGrid = new GridPane();
        templateGrid.setHgap(5);
        templateGrid.setVgap(5);
        for (int i = 0; i < PLAN_TEMPLATES.length; i++) {
            final String template = PLAN_TEMPLATES[i];
            Button btn = new Button(template);
            btn.setOnAction(e -> insertTemplate(template));
            templateGrid.add(btn, i % 5, i / 5);
        }
        grid.add(templateGrid, 0, 2, 2, 1);
        return grid;
    }

    private HBox createBottomSection() {
        Button applyButton = new Button("Apply Changes");
        applyButton.setOnAction(e -> applyChanges());
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> editorStage.close());
        return new HBox(10, applyButton, cancelButton);
    }

    private void insertTemplate(String template) {
        if (template.matches("[0-9]+[wdm]")) {
            fuField.setText(template);
        } else {
            medsCodeField.setText(template);
        }
    }

    private void insertQuickPlan() {
        String fuText = parseFU(fuField.getText());
        String medsText = parseMedsCode(medsCodeField.getText());
        editorTextArea.appendText(String.format("\n- %s\n- %s", fuText, medsText));
        fuField.clear();
        medsCodeField.clear();
    }

    private void applyChanges() {
        String expandedText = expandAbbreviations(editorTextArea.getText());
        if (expandedText.isBlank()) {
            showError("Nothing to apply.");
            return;
        }

        Runnable appendAction = () -> {
            try {
                textAreaManager.insertBlockIntoArea(IAITextAreaManager.AREA_P, expandedText, true);
                if (problemAction != null) {
                    problemAction.updateAndRedrawScratchpad("P>", expandedText);
                }
                new Thread(() -> {
                    try {
                        planRepo.savePlan("P>", expandedText, null, LocalDate.now().toString());
                    } catch (Exception ex) {
                        System.err.println("Failed to save plan history: " + ex.getMessage());
                    }
                }).start();
                editorStage.close();
            } catch (Exception ex) {
                showError("Failed to apply changes: " + ex.getMessage());
            }
        };

        Platform.runLater(appendAction);
    }

    private String expandAbbreviations(String text) {
        return Arrays.stream(text.split("((?<= )|(?= ))"))
                .map(word -> {
                    String clean = word.trim();
                    if (":cd".equals(clean)) return LocalDate.now().format(DateTimeFormatter.ISO_DATE);
                    if (clean.startsWith(":")) return abbrevMap.getOrDefault(clean.substring(1), word);
                    if (clean.matches("[0-9]+[wdm]")) return parseFU(clean);
                    if (Arrays.asList("5", "55", "6", "8", "2", "4", "0", "1").contains(clean)) return parseMedsCode(clean);
                    return word;
                })
                .collect(Collectors.joining());
    }

    private String parseFU(String input) {
        if (input == null || input.isBlank()) return "F/U as needed";
        String num = input.replaceAll("[^0-9]", "");
        if (input.endsWith("w")) return String.format("F/U in %s week(s)", num);
        if (input.endsWith("d")) return String.format("F/U in %s day(s)", num);
        return String.format("F/U in %s month(s)", num);
    }

    private String parseMedsCode(String code) {
        return switch (code) {
            case "5" -> "Start new medication";
            case "55" -> "Discontinue current medication";
            case "6" -> "Continue current medication";
            case "8" -> "Increase dose of current medication";
            case "2" -> "Decrease dose of current medication";
            case "4" -> "Change dose of current medication";
            case "0" -> "Observation and follow-up without medication";
            case "1" -> "Conservative treatment";
            default -> "(Unknown medication code)";
        };
    }

    private Path getDbPath(String fileName) {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("gradlew"))) {
            p = p.getParent();
        }
        return (p != null) ? p.resolve("app/db/").resolve(fileName) : Paths.get("app/db").resolve(fileName);
    }

    private Label createStyledLabel(String text, String style) {
        Label label = new Label(text);
        label.setStyle(style);
        return label;
    }

    private void showError(String message) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, message).showAndWait());
    }

    static final class PlanRepository {
        private final Path dbFile;

        PlanRepository(Path dbFile) {
            this.dbFile = Objects.requireNonNull(dbFile);
        }

        void init() throws Exception {
            Files.createDirectories(dbFile.getParent());
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath()); Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS plan_history (id INTEGER PRIMARY KEY, created_at TEXT NOT NULL, section TEXT, content TEXT, patient_id TEXT, encounter_date TEXT);");
            }
        }

        void savePlan(String section, String content, String patientId, String encounterDate) throws Exception {
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath()); PreparedStatement ps = c.prepareStatement("INSERT INTO plan_history (created_at, section, content, patient_id, encounter_date) VALUES (?,?,?,?,?)")) {
                ps.setString(1, LocalDateTime.now().toString());
                ps.setString(2, section);
                ps.setString(3, content);
                ps.setString(4, patientId);
                ps.setString(5, encounterDate);
                ps.executeUpdate();
            }
        }
    }
}