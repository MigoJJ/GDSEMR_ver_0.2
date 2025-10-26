package com.emr.gds.fourgate;

import com.emr.gds.input.IAIMain;
import com.emr.gds.input.IAITextAreaManager;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * A JavaFX application for Osteoporosis Risk Assessment based on DEXA scan results.
 * This tool calculates a diagnosis based on T-Score or Z-Score and other clinical factors.
 */
public class DEXA extends Application {

    private TextField scoreField, ageField;
    private ComboBox<String> genderComboBox;
    private TextArea outputTextArea;
    private CheckBox fragilityFractureCheckBox, menopauseCheckBox, hrtCheckBox, tahCheckBox, stonesCheckBox;
    private ToggleGroup scoreTypeToggleGroup;
    private RadioButton tScoreRadioButton, zScoreRadioButton;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Osteoporosis Risk Assessment (DEXA)");

        initComponents();
        Scene scene = new Scene(createLayout(), 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
        scoreField.requestFocus();
    }

    private void initComponents() {
        scoreField = new TextField();
        scoreField.setPrefWidth(100);
        ageField = new TextField();
        ageField.setPrefWidth(100);

        genderComboBox = new ComboBox<>();
        genderComboBox.getItems().addAll("Female", "Male");
        genderComboBox.setValue("Female");

        outputTextArea = new TextArea();
        outputTextArea.setEditable(false);
        outputTextArea.setStyle("-fx-control-inner-background: #FAFAFA;");

        fragilityFractureCheckBox = new CheckBox("Fragility Fracture");
        menopauseCheckBox = new CheckBox("Postmenopausal");
        hrtCheckBox = new CheckBox("On HRT");
        tahCheckBox = new CheckBox("TAH (Total Abdominal Hysterectomy)");
        stonesCheckBox = new CheckBox("History of Kidney Stones");

        scoreTypeToggleGroup = new ToggleGroup();
        tScoreRadioButton = new RadioButton("T-Score");
        tScoreRadioButton.setToggleGroup(scoreTypeToggleGroup);
        tScoreRadioButton.setSelected(true);
        zScoreRadioButton = new RadioButton("Z-Score");
        zScoreRadioButton.setToggleGroup(scoreTypeToggleGroup);

        Font boldFont = Font.font("SanSerif", FontWeight.BOLD, 14);
        scoreField.setFont(boldFont);
        ageField.setFont(boldFont);
        scoreField.setStyle("-fx-alignment: CENTER;");
        ageField.setStyle("-fx-alignment: CENTER;");
    }

    private BorderPane createLayout() {
        BorderPane borderPane = new BorderPane();
        borderPane.setTop(new ScrollPane(outputTextArea));
        borderPane.setLeft(createWestPanel());
        borderPane.setCenter(createInputPanel());
        borderPane.setBottom(createButtonPanel());
        return borderPane;
    }

    private VBox createWestPanel() {
        TextArea zScoreInfo = new TextArea("\nZ-Score is used for:\n" +
                "- Children/adolescents\n" +
                "- Premenopausal women (<50)\n" +
                "- Men under 50\n\n" +
                "Key Lab Tests to Consider:\n" +
                "* Calcium & Phosphorus\n" +
                "* Vitamin D (25-OH)\n" +
                "* Kidney Function (eGFR, Cr)\n" +
                "* Bone Turnover Markers (CTX/NTX, BSAP)");
        zScoreInfo.setEditable(false);
        zScoreInfo.setWrapText(true);
        zScoreInfo.setStyle("-fx-control-inner-background: #E8E8FF;");

        VBox panel = new VBox(zScoreInfo);
        panel.setPadding(new Insets(10));
        panel.setBorder(new Border(new BorderStroke(Color.LIGHTGRAY, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT)));
        panel.setPrefWidth(300);
        return panel;
    }

    private GridPane createInputPanel() {
        GridPane panel = new GridPane();
        panel.setPadding(new Insets(10));
        panel.setHgap(10);
        panel.setVgap(8);

        panel.add(new Label("Score:"), 0, 0);
        panel.add(scoreField, 1, 0);
        panel.add(new Label("Age:"), 0, 1);
        panel.add(ageField, 1, 1);
        panel.add(new Label("Gender:"), 0, 2);
        panel.add(genderComboBox, 1, 2);
        panel.add(new Label("Score Type:"), 0, 3);
        panel.add(new HBox(10, tScoreRadioButton, zScoreRadioButton), 1, 3);

        // Clinical Factors Section
        VBox clinicalFactorsBox = new VBox(8, menopauseCheckBox, fragilityFractureCheckBox, hrtCheckBox, tahCheckBox, stonesCheckBox);
        TitledPane titledPane = new TitledPane("Clinical Factors", clinicalFactorsBox);
        titledPane.setCollapsible(false);
        panel.add(titledPane, 0, 4, 2, 1);

        return panel;
    }

    private HBox createButtonPanel() {
        Button assessButton = new Button("Assess Risk");
        assessButton.setOnAction(e -> processInput());
        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clearFields());
        Button saveButton = new Button("Save to EMR");
        saveButton.setOnAction(e -> saveToEMR());
        Button quitButton = new Button("Quit");
        quitButton.setOnAction(e -> ((Stage) quitButton.getScene().getWindow()).close());

        HBox buttonPanel = new HBox(10, assessButton, clearButton, saveButton, quitButton);
        buttonPanel.setPadding(new Insets(10));
        return buttonPanel;
    }

    private void saveToEMR() {
        String reportText = outputTextArea.getText();
        if (reportText == null || reportText.trim().isEmpty()) {
            showError("There is no report to save.");
            return;
        }

        try {
            IAITextAreaManager emrManager = IAIMain.getTextAreaManager();
            if (emrManager == null || !emrManager.isReady()) {
                showError("Cannot save data: EMR connection is not ready.");
                return;
            }
            // Target the 'O>' (Objective) text area, which is at index 5
            emrManager.focusArea(5);
            emrManager.insertLineIntoFocusedArea("\n" + reportText.trim());
            clearFields();
        } catch (Exception e) {
            showError("An error occurred while saving to the EMR: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void processInput() {
        try {
            double score = Double.parseDouble(scoreField.getText());
            int age = Integer.parseInt(ageField.getText());
            String gender = genderComboBox.getValue();
            boolean hasFracture = fragilityFractureCheckBox.isSelected();
            boolean isMenopausal = menopauseCheckBox.isSelected();
            boolean isOnHrt = hrtCheckBox.isSelected();
            boolean hasTah = tahCheckBox.isSelected();
            boolean hasStones = stonesCheckBox.isSelected();
            String scoreType = tScoreRadioButton.isSelected() ? "T-Score" : "Z-Score";

            String report = formatReport(score, scoreType, age, gender, hasFracture, isMenopausal, isOnHrt, hasTah, hasStones);
            outputTextArea.setText(report);
        } catch (NumberFormatException ex) {
            showError("Invalid input. Please enter numeric values for score and age.");
        }
    }

    private String formatReport(double score, String scoreType, int age, String gender, boolean hasFracture, boolean isMenopausal, boolean isOnHrt, boolean hasTah, boolean hasStones) {
        String diagnosis;
        if ("T-Score".equals(scoreType)) {
            if (score <= -2.5) {
                diagnosis = hasFracture ? "Severe Osteoporosis" : "Osteoporosis";
            } else if (score < -1.0) {
                diagnosis = "Osteopenia";
            } else {
                diagnosis = "Normal Bone Density";
            }
        } else { // Z-Score
            diagnosis = (score <= -2.0) ? "Below the expected range for age" : "Within the expected range for age";
        }

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        StringBuilder report = new StringBuilder();
        report.append(String.format("< DEXA Report - %s >\n", date));
        report.append(String.format("\tDiagnosis: %s (%s: %.1f)\n", diagnosis, scoreType, score));
        report.append(String.format("\tPatient: %d-year-old %s\n", age, gender));

        if ("Female".equals(gender)) {
            report.append(String.format("\tClinical Factors: Menopausal: %s, Fragility Fracture: %s, On HRT: %s, TAH: %s, Kidney Stones: %s\n",
                    isMenopausal ? "Yes" : "No", hasFracture ? "Yes" : "No", isOnHrt ? "Yes" : "No", hasTah ? "Yes" : "No", hasStones ? "Yes" : "No"));
        } else { // Male
            report.append(String.format("\tClinical Factors: Fragility Fracture: %s, Kidney Stones: %s\n",
                    hasFracture ? "Yes" : "No", hasStones ? "Yes" : "No"));
        }

        report.append("\nComment>\n");
        report.append(String.format("# %s based on %s of %.1f.\n", diagnosis, scoreType, score));

        return report.toString();
    }

    private void clearFields() {
        scoreField.clear();
        ageField.clear();
        genderComboBox.getSelectionModel().selectFirst();
        fragilityFractureCheckBox.setSelected(false);
        menopauseCheckBox.setSelected(false);
        hrtCheckBox.setSelected(false);
        tahCheckBox.setSelected(false);
        stonesCheckBox.setSelected(false);
        tScoreRadioButton.setSelected(true);
        outputTextArea.clear();
        scoreField.requestFocus();
    }
}