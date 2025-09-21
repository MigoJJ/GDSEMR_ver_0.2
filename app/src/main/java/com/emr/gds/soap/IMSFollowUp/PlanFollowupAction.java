package com.emr.gds.soap.IMSFollowUp;

import com.emr.gds.input.IAITextAreaManager;
import com.emr.gds.main.IAMProblemAction; // Added for potential scratchpad support
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PlanFollowupAction {

    // Constants
    private static final int VERTICAL_SPACING = 15;
    private static final double INSET_PADDING = 5;
    private static final int GRID_HGAP = 10;
    private static final int GRID_VGAP = 10;
    private static final int GRID_TOP_PADDING = 10;
    private static final int GRID_RIGHT_PADDING = 5;
    private static final int GRID_BOTTOM_PADDING = 10;
    private static final int GRID_LEFT_PADDING = 5;
    private static final Font FIELD_FONT = Font.font("Arial", FontWeight.BOLD, 12);

    // Templates and Phrases
    private static final String[] planTemplates = {
        "1w", "2w", "4w", "1d", "3d", "7d", "1m", "3m", "6m", ":cd",
        "5", "55", "6", "8", "2", "4", "0", "1"
    };

    private static final String[] commonPhrases = {
        "Follow-up in 1 week with primary care provider.",
        "Follow-up in 2 weeks for symptom reassessment.",
        "Start new medication as prescribed.",
        "Discontinue current medication due to side effects.",
        "Continue current medication without changes.",
        "Increase dose of current medication as discussed.",
        "Decrease dose of current medication to minimize side effects.",
        "Change medication dose and monitor response.",
        "Observation and follow-up without medication changes.",
        "Proceed with conservative treatment and follow-up."
    };

    // Data model for phrase table
    public static class Phrase {
        private final SimpleStringProperty text;
        public Phrase(String text) {
            this.text = new SimpleStringProperty(text);
        }
        public String getText() { return text.get(); }
        public StringProperty textProperty() { return text; }
    }

    // Instance Variables
    private final IAITextAreaManager textAreaManager;
    private final IAMProblemAction problemAction; // Added for scratchpad support
    private Stage editorStage;
    private TextArea editorTextArea;
    private TextField fuField;
    private TextField medsCodeField;
    private final Map<String, String> abbrevMap = new HashMap<>();

    public PlanFollowupAction(IAITextAreaManager textAreaManager, IAMProblemAction problemAction) {
        this.textAreaManager = textAreaManager;
        this.problemAction = problemAction;
        System.out.println("PlanFollowupAction initialized with textAreaManager: " + textAreaManager);
        initAbbrevDatabase();
        createEditorWindow();
    }

    public void showAndWait() {
        editorStage.showAndWait();
    }

    private void initAbbrevDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            Path dbFile = getDbPath("abbreviations.db");
            Files.createDirectories(dbFile.getParent());
            String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();

            try (Connection conn = DriverManager.getConnection(url);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM abbreviations")) {
                while (rs.next()) {
                    abbrevMap.put(rs.getString("short"), rs.getString("full"));
                }
            }
        } catch (Exception e) {
            showError("Failed to initialize abbreviation database: " + e.getMessage());
        }
    }

    private Path getDbPath(String fileName) {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("gradlew")) && !Files.exists(p.resolve(".git"))) {
            p = p.getParent();
        }
        return (p != null) ? p.resolve("app").resolve("db").resolve(fileName) : Paths.get("").toAbsolutePath();
    }

    private String expandAbbreviations(String text) {
        StringBuilder sb = new StringBuilder();
        for (String word : text.split("(?<=\\s)|(?=\\s)")) {
            String cleanWord = word.trim();
            if (":cd".equals(cleanWord)) {
                sb.append(LocalDate.now().format(DateTimeFormatter.ISO_DATE));
            } else if (cleanWord.startsWith(":")) {
                sb.append(abbrevMap.getOrDefault(cleanWord.substring(1), word));
            } else if (cleanWord.matches("[0-9]+[wdm]")) {
                sb.append(parseFU(cleanWord));
            } else if (Arrays.asList("5", "55", "6", "8", "2", "4", "0", "1").contains(cleanWord)) {
                sb.append(returnchangefield2(cleanWord));
            } else {
                sb.append(word);
            }
        }
        return sb.toString();
    }

    private void createEditorWindow() {
        editorStage = new Stage();
        editorStage.setTitle("Plan & Follow-up Editor");
        editorStage.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(INSET_PADDING));
        root.setTop(createTopSection());
        root.setCenter(createCenterSection());
        root.setBottom(createBottomSection());

        double originalWestPanelWidth = 280;
        double newWestPanelWidth = originalWestPanelWidth * 1.15;
        double newTotalWidth = newWestPanelWidth + (1000 - originalWestPanelWidth);

        VBox westPanel = createWestPanel(newWestPanelWidth);
        root.setLeft(westPanel);

        editorStage.setScene(new Scene(root, newTotalWidth, 600));
        editorStage.setMinWidth(newTotalWidth);
        editorStage.setMinHeight(600);
    }

    private VBox createWestPanel(double width) {
        Label title = createStyledLabel("Phrase Bank", "-fx-font-weight: bold;");

        TableView<Phrase> table = new TableView<>();
        table.setPrefWidth(width + 5);

        TableColumn<Phrase, String> phraseColumn = new TableColumn<>("Common Actions & Phrases");
        phraseColumn.setCellValueFactory(new PropertyValueFactory<>("text"));
        phraseColumn.prefWidthProperty().bind(table.widthProperty().subtract(2));
        phraseColumn.setResizable(false);

        table.getColumns().add(phraseColumn);

        ObservableList<Phrase> phraseData = Arrays.stream(commonPhrases)
                .map(Phrase::new)
                .collect(Collectors.toCollection(FXCollections::observableArrayList));
        table.setItems(phraseData);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                editorTextArea.appendText(newSelection.getText() + " ");
                editorTextArea.requestFocus();
            }
        });

        VBox westBox = new VBox(5, title, table);
        westBox.setPadding(new Insets(0, 10, 0, 0));
        return westBox;
    }

    private VBox createTopSection() {
        return new VBox(5,
            createStyledLabel("Plan & Follow-up Editor", "-fx-font-size: 16px; -fx-font-weight: bold;"),
            createStyledLabel("Enter follow-up plans and medication codes. Use templates or phrases.", "-fx-text-fill: #666666;"),
            createStyledLabel("Tip: Use :cd for current date, or codes like '5' for medication actions.", "-fx-text-fill: #0066cc; -fx-font-size: 11px; -fx-font-style: italic;")
        );
    }

    private VBox createCenterSection() {
        GridPane quickPlanGrid = createQuickPlanPanel();

        editorTextArea = new TextArea();
        editorTextArea.setWrapText(true);
        editorTextArea.setPrefRowCount(10);
        editorTextArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");

        TextArea previewArea = new TextArea();
        previewArea.setWrapText(true);
        previewArea.setPrefRowCount(4);
        previewArea.setEditable(false);
        previewArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px; -fx-background-color: #f5f5f5;");

        editorTextArea.textProperty().addListener((obs, oldText, newText) -> previewArea.setText(expandAbbreviations(newText)));
        previewArea.setText(expandAbbreviations(editorTextArea.getText()));

        return new VBox(10,
            createStyledLabel("Quick Entry:", "-fx-font-weight: bold;"),
            quickPlanGrid,
            createStyledLabel("Plan Text:", "-fx-font-weight: bold;"),
            editorTextArea,
            createStyledLabel("Preview (with expanded abbreviations):", "-fx-font-weight: bold; -fx-text-fill: #666666;"),
            previewArea
        );
    }

    private GridPane createQuickPlanPanel() {
        GridPane grid = new GridPane();
        grid.setHgap(GRID_HGAP);
        grid.setVgap(GRID_VGAP);
        grid.setPadding(new Insets(GRID_TOP_PADDING, GRID_RIGHT_PADDING, GRID_BOTTOM_PADDING, GRID_LEFT_PADDING));

        fuField = new TextField();
        fuField.setFont(FIELD_FONT);
        fuField.setOnAction(e -> medsCodeField.requestFocus());

        medsCodeField = new TextField();
        medsCodeField.setFont(FIELD_FONT);
        medsCodeField.setOnAction(e -> insertQuickPlan());

        grid.add(createStyledLabel("FU:", "-fx-font-weight: bold;"), 0, 0);
        grid.add(fuField, 1, 0);
        grid.add(createStyledLabel("Meds Code:", "-fx-font-weight: bold;"), 0, 1);
        grid.add(medsCodeField, 1, 1);

        GridPane templateGrid = new GridPane(5, 5);
        templateGrid.setPadding(new Insets(5));
        final int cols = 5;

        IntStream.range(0, planTemplates.length).forEach(i -> {
            String template = planTemplates[i].trim();
            Button btn = new Button(template);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e -> insertTemplate(template));
            templateGrid.add(btn, i % cols, i / cols);
        });

        grid.add(createStyledLabel("Quick Templates:", "-fx-font-weight: bold;"), 0, 2);
        grid.add(templateGrid, 0, 3, 2, 1);

        return grid;
    }

    private HBox createBottomSection() {
        HBox bottomBar = new HBox(10,
            createStyledButton("Apply Changes", "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;", e -> {
                System.out.println("Apply Changes button clicked");
                applyChanges();
            }),
            new Button("Cancel") {{ setOnAction(e -> editorStage.close()); }},
            createStyledButton("Clear", "-fx-background-color: #f44336; -fx-text-fill: white;", e -> {
                editorTextArea.clear();
                fuField.clear();
                medsCodeField.clear();
            })
        );
        bottomBar.setPadding(new Insets(10, 0, 0, 0));
        return bottomBar;
    }

    private void insertTemplate(String template) {
        if (template.matches("[0-9]+[wdm]")) {
            fuField.setText(template);
            medsCodeField.requestFocus();
        } else if (Arrays.asList("5", "55", "6", "8", "2", "4", "0", "1").contains(template)) {
            medsCodeField.setText(template);
            editorTextArea.appendText(returnchangefield2(template) + " ");
        } else {
            editorTextArea.appendText(template + " ");
        }
        editorTextArea.requestFocus();
    }

    private void insertQuickPlan() {
        String fuText = parseFU(fuField.getText().trim());
        String medsCode = medsCodeField.getText().trim();
        String medsMessage = returnchangefield2(medsCode);
        String finalPlan = String.format("\n***: %s\n***: %s", fuText, medsMessage);
        editorTextArea.appendText(finalPlan + " ");
        fuField.clear();
        medsCodeField.clear();
        fuField.requestFocus();
        editorTextArea.requestFocus();
    }

    private void applyChanges() {
        String originalText = editorTextArea.getText();
        String expandedText = expandAbbreviations(originalText);
        boolean expansionOccurred = !originalText.equals(expandedText);
        boolean proceed = !expansionOccurred; // If no expansion, proceed automatically

        System.out.println("Proceed: " + proceed + ", Expanded Text: " + expandedText);
        if (expansionOccurred) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Abbreviation Expansion");
            confirm.setHeaderText("Abbreviations will be expanded:");
            confirm.setContentText("Original: " + originalText + "\n\nExpanded: " + expandedText + "\n\nApply changes?");
            proceed = confirm.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
            System.out.println("After dialog, Proceed: " + proceed);
        }

        if (proceed) {
            if (textAreaManager == null) {
                showError("TextAreaManager is null. Cannot append text.");
                System.err.println("Error: textAreaManager is null");
                return;
            }

            try {
                Platform.runLater(() -> {
                	textAreaManager.appendTextToSection(IAITextAreaManager.AREA_P, expandedText);                    if (problemAction != null) {
                        problemAction.updateAndRedrawScratchpad("P>", expandedText);
                    }
                    editorStage.close();
                });
                System.out.println("Text appended to AREA_P: " + expandedText);
            } catch (Exception e) {
                showError("Failed to append text: " + e.getMessage());
                System.err.println("Exception in appendTextToSection: " + e.getMessage());
            }
        }
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            new Alert(Alert.AlertType.ERROR, "An error occurred:\n" + message) {{
                setTitle("Error");
                showAndWait();
            }};
        });
    }

    private Label createStyledLabel(String text, String style) {
        Label label = new Label(text);
        label.setStyle(style);
        return label;
    }

    private Button createStyledButton(String text, String style, EventHandler<ActionEvent> handler) {
        Button button = new Button(text);
        button.setStyle(style);
        button.setOnAction(handler);
        return button;
    }

    private static String parseFU(String input) {
        if (input == null || input.isEmpty()) return "follow-up as needed";
        String num = input.replaceAll("[^0-9]", "");
        if (input.toLowerCase().contains("w")) {
            return String.format("follow-up [ %s ] weeks later", num);
        } else if (input.toLowerCase().contains("d")) {
            return String.format("follow-up [ %s ] days later", num);
        } else {
            return String.format("follow-up [ %s ] months later", num);
        }
    }

    private static String returnchangefield2(String meds) {
        String[] codes = {"5", "55", "6", "8", "2", "4", "0", "1"};
        String[] messages = {
            " |→  starting new medicine to treat ",
            " →|  discontinue current medication",
            " [ → ] advised the patient to continue with current medication",
            " [ ↗ ] increased the dose of current medication",
            " [ ↘ ] decreased the dose of current medication",
            " [ ⭯ ] changed the dose of current medication",
            " Observation & Follow-up without medication",
            " With conservative treatment"
        };

        for (int i = 0; i < codes.length; i++) {
            if (meds.equals(codes[i])) return messages[i];
        }
        return "(unknown code)";
    }
}