package com.emr.gds.soap;

import com.emr.gds.input.IAIMain;
import com.emr.gds.input.IAITextAreaManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class EMRPMH extends Application {
    private final IAITextAreaManager textAreaManager;
    private TextArea selectedArea;
    private TextArea outputArea;
    private GridPane checkBoxPanel;
    private final Map<String, String> abbrevMap = new HashMap<>();
    private final Map<String, CheckBox> checkBoxes = new LinkedHashMap<>();
    private final Map<String, Boolean> selectionMap = new LinkedHashMap<>();
    private Stage emrpmhStage;

    private static final String[][] CONDITIONS = {
            {"Dyslipidemia", "Hypertension", "Diabetes Mellitus"},
            {"Cancer", "Operation", "Thyroid Disease"},
            {"Asthma", "Pneumonia", "Tuberculosis"},
            {"GERD", "Hepatitis A / B", "Gout"},
            {"AMI", "Angina Pectoris", "Arrhythmia"},
            {"CVA", "Depression", "Cognitive Disorder"},
            {"Anxiety", "Hearing Loss", "Parkinson's Disease"},
            {"Allergy", "All denied allergies..."},
            {"Food", "Injection", "Medication"}
    };

    public EMRPMH(IAITextAreaManager manager) {
        this.textAreaManager = manager;
    }

    public EMRPMH() {
        this.textAreaManager = null;
    }

    public void showEMRPMH(Stage stage) {
        this.emrpmhStage = stage;
        emrpmhStage.show();
    }

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        showWindow();
    }

    public void showWindow() {
        initAbbrevDatabase();

        if (primaryStage == null) {
            primaryStage = new Stage();
        }

        primaryStage.setTitle("EMR Past Medical History");
        primaryStage.setWidth(820);
        primaryStage.setHeight(820);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(8));

        initComponents(root);

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> primaryStage.close());

        refreshSelectionSummary();
        primaryStage.show();

        // link main stage reference for closing later
        this.emrpmhStage = primaryStage;
    }

    public void setVisible(boolean visible) {
        if (visible) {
            Platform.runLater(this::showWindow);
        } else {
            Platform.runLater(() -> {
                if (primaryStage != null) {
                    primaryStage.close();
                }
            });
        }
    }

    private void initComponents(BorderPane root) {
        selectedArea = createTextArea(6, 10);
        selectedArea.setFont(Font.font("Malgun Gothic", 12));
        selectedArea.setEditable(false);

        outputArea = createTextArea(12, 50);
        outputArea.setFont(Font.font("Malgun Gothic", 12));
        outputArea.setEditable(false);

        VBox topPanel = new VBox(6);
        topPanel.getChildren().addAll(new ScrollPane(selectedArea), new ScrollPane(outputArea));

        checkBoxPanel = new GridPane();
        checkBoxPanel.setHgap(6);
        checkBoxPanel.setVgap(6);
        checkBoxPanel.setPadding(new Insets(10));

        buildCheckBoxes();
        ScrollPane checkBoxScrollPane = new ScrollPane(checkBoxPanel);
        checkBoxScrollPane.setFitToWidth(true);

        HBox buttonPanel = createButtonPanel();

        root.setTop(topPanel);
        root.setCenter(checkBoxScrollPane);
        root.setBottom(buttonPanel);
    }

    private void buildCheckBoxes() {
        int row = 0;
        for (String[] conditionRow : CONDITIONS) {
            int col = 0;
            for (String item : conditionRow) {
                if (item == null || item.isBlank()) continue;
                CheckBox cb = new CheckBox(item);
                cb.setOnAction(e -> onCheckboxToggle(cb));
                checkBoxes.put(item, cb);
                selectionMap.put(item, false);
                checkBoxPanel.add(cb, col, row);
                col++;
            }
            row++;
        }
    }

    private HBox createButtonPanel() {
        Button familyHistoryBtn = new Button("Family History");
        Button clearBtn = new Button("Clear");
        Button generateBtn = new Button("Generate Report");
        Button saveQuitBtn = new Button("Save & Quit");
        Button quitBtn = new Button("Quit");

        familyHistoryBtn.setOnAction(e -> openFamilyHistoryForm());
        clearBtn.setOnAction(e -> onClear());
        generateBtn.setOnAction(e -> onGenerateReport());
        saveQuitBtn.setOnAction(e -> onSaveAndQuit());
        quitBtn.setOnAction(e -> {
            if (emrpmhStage != null) {
                emrpmhStage.close();
            }
        });

        HBox buttonPanel = new HBox(10);
        buttonPanel.setAlignment(Pos.CENTER_RIGHT);
        buttonPanel.setPadding(new Insets(10));
        buttonPanel.getChildren().addAll(
            familyHistoryBtn, clearBtn, generateBtn, saveQuitBtn, quitBtn
        );

        return buttonPanel;
    }

    private TextArea createTextArea(int rows, int cols) {
        TextArea ta = new TextArea();
        ta.setPrefRowCount(rows);
        ta.setPrefColumnCount(cols);
        ta.setWrapText(true);
        return ta;
    }

    private void onCheckboxToggle(CheckBox checkBox) {
        selectionMap.put(checkBox.getText(), checkBox.isSelected());
        refreshSelectionSummary();
    }

    private void openFamilyHistoryForm() {
        try {
            if (IAIMain.getTextAreaManager() != null && IAIMain.getTextAreaManager().isReady()) {
                Platform.runLater(() -> {
                    try {
                        EMRFMH familyHistory = new EMRFMH(IAIMain.getTextAreaManager());
                        familyHistory.setVisible(true);
                    } catch (Exception ex) {
                        showError("Failed to open Family History: " + ex.getMessage());
                    }
                });
            } else {
                showError("The text area manager is not ready.");
            }
        } catch (Exception e) {
            showError("Failed to open Family History form: " + e.getMessage());
        }
    }

    private void onClear() {
        selectionMap.keySet().forEach(key -> selectionMap.put(key, false));
        checkBoxes.values().forEach(cb -> cb.setSelected(false));
        outputArea.setText("");
        refreshSelectionSummary();
    }

    private void onGenerateReport() {
        outputArea.setText(getCombinedText(true));
    }

    private void onSaveAndQuit() {
        if (textAreaManager == null) {
            showError("Cannot save: EMR connection not available.");
            return;
        }
        if (outputArea.getText().isBlank()) {
            showError("Please click 'Generate Report' before saving.");
            return;
        }

        String report = expandAbbreviations(outputArea.getText());
        Platform.runLater(() -> {
            textAreaManager.insertBlockIntoArea(IAITextAreaManager.AREA_PMH, report, true);
            if (emrpmhStage != null) {
                emrpmhStage.close();
            }
        });
    }

    private void refreshSelectionSummary() {
        StringBuilder sb = new StringBuilder();
        selectionMap.forEach((key, value) -> {
            if (value) sb.append("    ▣ ").append(key).append('\n');
        });
        selectedArea.setText(sb.toString());
    }

    private String getCombinedText(boolean withMarks) {
        StringBuilder sb = new StringBuilder();
        for (String[] row : CONDITIONS) {
            for (String item : row) {
                if (item == null || item.isBlank()) continue;
                String mark = withMarks ? (selectionMap.get(item) ? "▣" : "□") : "";
                sb.append(String.format("    %-20s", mark + " " + item));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void initAbbrevDatabase() {
        try {
            Path dbFile = Paths.get("app/db/abbreviations.db").toAbsolutePath();
            if (!Files.exists(dbFile)) {
                System.out.println("Abbreviation database not found at: " + dbFile);
                return;
            }
            String url = "jdbc:sqlite:" + dbFile;
            try (Connection conn = DriverManager.getConnection(url);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM abbreviations")) {
                while (rs.next()) {
                    abbrevMap.put(rs.getString("short"), rs.getString("full"));
                }
                System.out.println("Loaded " + abbrevMap.size() + " abbreviations");
            }
        } catch (SQLException e) {
            showError("Failed to initialize abbreviation database: " + e.getMessage());
        }
    }

    private String expandAbbreviations(String text) {
        return Arrays.stream(text.split("((?<= )|(?= ))"))
                .map(word -> {
                    String cleanWord = word.trim();
                    if (":cd".equals(cleanWord)) {
                        return LocalDate.now().format(DateTimeFormatter.ISO_DATE);
                    }
                    if (cleanWord.startsWith(":")) {
                        return abbrevMap.getOrDefault(cleanWord.substring(1), word);
                    }
                    return word;
                })
                .collect(Collectors.joining());
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public static void show(IAITextAreaManager manager) {
        Platform.runLater(() -> {
            try {
                EMRPMH pmh = new EMRPMH(manager);
                pmh.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}