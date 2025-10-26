package com.emr.gds.fourgate;

import com.emr.gds.input.IAIMain;
import com.emr.gds.input.IAITextAreaManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A JavaFX Stage for conducting a systematic review of a Chest PA (Posteroanterior) X-ray.
 * This tool provides a structured interface for documenting findings across different anatomical areas.
 */
public class ChestPA extends Stage {

    private static final int EMR_TARGET_AREA_INDEX = 5; // Corresponds to the 'O>' (Objective) text area
    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
    private static final String CUSTOM_OPTION_TEXT = "Custom...";

    // UI Components
    private ComboBox<String> tracheaComboBox, bonesComboBox, cardiacComboBox, diaphragmComboBox,
            effusionsComboBox, devicesComboBox, comparisonComboBox, historyComboBox;
    private TextField customTracheaField, customBonesField, customCardiacField, customDiaphragmField,
            customEffusionsField, customDevicesField, customComparisonField, customHistoryField;
    private VBox rulfCheckList, rmlfCheckList, rllfCheckList, lulfCheckList, lmlfCheckList, lllfCheckList;
    private TextArea customRulfArea, customRmlfArea, customRllfArea, customLulfArea, customLmlfArea, customLllfArea;
    private TextArea findingsTextArea;

    // Data sources for ComboBoxes
    private final ObservableList<String> tracheaOptions = createObservableList("Midline", "Deviated to the right", "Deviated to the left", "No significant deviation", "Not well visualized");
    private final ObservableList<String> bonesOptions = createObservableList("No acute fractures or dislocations", "Degenerative changes noted", "Normal bony thorax", "Osteopenia", "Sclerotic lesions in [specific area, e.g., T-spine]");
    private final ObservableList<String> cardiacOptions = createObservableList("Normal heart size and contour", "Mild cardiomegaly", "Moderate cardiomegaly", "Borderline enlarged cardiac silhouette", "No pericardial effusion");
    private final ObservableList<String> diaphragmOptions = createObservableList("Healed bilateral costo-phrenic angles blunted", "Clear costo-phrenic angles", "No diaphragmatic elevation", "Mild elevation of the right hemidiaphragm", "Flattening of hemidiaphragms");
    private final ObservableList<String> effusionsOptions = createObservableList("No pleural effusions", "Small right pleural effusion", "Small left pleural effusion", "Bilateral small pleural effusions", "Trace right pleural effusion");
    private final ObservableList<String> devicesOptions = createObservableList("Endotracheal tube in good position", "Central venous catheter tip in SVC", "Nasogastric tube in expected position", "No acute changes related to surgical clips", "No foreign bodies identified");
    private final ObservableList<String> comparisonOptions = createObservableList("There are no active lesions in the lung.", "Compared to previous [date], no significant change", "Compared to previous [date], new findings noted", "Compared to previous [date], interval improvement", "No prior studies available for comparison", "Compared to previous [date], interval worsening");
    private final ObservableList<String> historyOptions = createObservableList("Shortness of breath", "Cough", "Chest pain", "Fever");
    private final ObservableList<String> lungFieldFindings = createObservableList("Clear", "Normal vascularity", "No focal consolidation", "No acute infiltrate", "Interstitial opacities", "Atelectasis", "Nodules", "Masses");

    public ChestPA(Stage owner) {
        this.initOwner(owner);
        setupStage();
    }

    public ChestPA() {
        setupStage();
    }

    private void setupStage() {
        setTitle("Chest PA Systematic Review");
        setScene(createScene());
        setWidth(1300);
        setHeight(850);
        setMinWidth(1000);
        setMinHeight(900);
    }

    private Scene createScene() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setVgap(8);
        grid.setHgap(10);

        addSystematicInputControls(grid);
        grid.add(createLungTabs(), 0, 16, 2, 1);
        addButtons(grid);

        findingsTextArea = new TextArea();
        findingsTextArea.setPromptText("Generated report will appear here...");
        findingsTextArea.setEditable(false);
        findingsTextArea.setWrapText(true);
        grid.add(findingsTextArea, 0, 18, 2, 1);

        return new Scene(new VBox(10, grid), 1000, 800);
    }

    private void addSystematicInputControls(GridPane grid) {
        int row = 0;
        row = addComboRow(grid, "Airways:", "Trachea", tracheaOptions, row);
        row = addComboRow(grid, "Bones:", "Ribs, clavicles, vertebrae", bonesOptions, row);
        row = addComboRow(grid, "Cardiac:", "Heart size, borders, aortic contour", cardiacOptions, row);
        row = addComboRow(grid, "Diaphragm:", "Definition, angles, free air", diaphragmOptions, row);
        row = addComboRow(grid, "Effusions:", "Pleural effusion, consolidation, masses", effusionsOptions, row);
        row = addComboRow(grid, "Devices:", "Lines, tubes, foreign objects", devicesOptions, row);
        row = addComboRow(grid, "Comparison:", "Previous CXR/CT correlation", comparisonOptions, row);
        row = addComboRow(grid, "History:", "Clinical history", historyOptions, row);
    }

    private int addComboRow(GridPane grid, String label, String prompt, ObservableList<String> options, int row) {
        ComboBox<String> cb = new ComboBox<>(options);
        cb.setPromptText(prompt);
        cb.setMaxWidth(Double.MAX_VALUE);
        grid.add(new Label(label), 0, row);
        grid.add(cb, 1, row);

        TextField tf = new TextField();
        tf.setPromptText("Enter custom finding...");
        tf.setVisible(false);
        tf.setManaged(false);
        grid.add(tf, 1, row + 1);

        setupCustomFieldListener(cb, tf);
        assignComboAndField(label, cb, tf);

        return row + 2;
    }

    private void assignComboAndField(String label, ComboBox<String> cb, TextField tf) {
        switch (label) {
            case "Airways:" -> { tracheaComboBox = cb; customTracheaField = tf; }
            case "Bones:" -> { bonesComboBox = cb; customBonesField = tf; }
            case "Cardiac:" -> { cardiacComboBox = cb; customCardiacField = tf; }
            case "Diaphragm:" -> { diaphragmComboBox = cb; customDiaphragmField = tf; }
            case "Effusions:" -> { effusionsComboBox = cb; customEffusionsField = tf; }
            case "Devices:" -> { devicesComboBox = cb; customDevicesField = tf; }
            case "Comparison:" -> { comparisonComboBox = cb; customComparisonField = tf; }
            case "History:" -> { historyComboBox = cb; customHistoryField = tf; }
        }
    }

    private void setupCustomFieldListener(ComboBox<String> comboBox, TextField customField) {
        comboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            boolean isCustom = CUSTOM_OPTION_TEXT.equals(newValue);
            customField.setVisible(isCustom);
            customField.setManaged(isCustom);
            if (isCustom) {
                customField.requestFocus();
            } else {
                customField.clear();
            }
        });
    }

    private TabPane createLungTabs() {
        String[] areas = {"RULF", "RMLF", "RLLF", "LULF", "LMLF", "LLLF"};
        VBox[] checkLists = {rulfCheckList, rmlfCheckList, rllfCheckList, lulfCheckList, lmlfCheckList, lllfCheckList};
        TextArea[] customAreas = {customRulfArea, customRmlfArea, customRllfArea, customLulfArea, customLmlfArea, customLllfArea};
        String[] titles = {"Right Upper Lung Field", "Right Middle Lung Field", "Right Lower Lung Field",
                "Left Upper Lung Field", "Left Middle Lung Field", "Left Lower Lung Field"};

        TabPane tabs = new TabPane();
        for (int i = 0; i < areas.length; i++) {
            checkLists[i] = createCheckListBox(areas[i]);
            customAreas[i] = createCustomLungTextArea();
            tabs.getTabs().add(createLungTab(titles[i], checkLists[i], customAreas[i]));
        }
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return tabs;
    }

    private VBox createCheckListBox(String areaName) {
        VBox vbox = new VBox(5);
        vbox.setPadding(new Insets(5));
        Label title = new Label(areaName + " Findings:");
        title.setStyle("-fx-font-weight: bold;");
        vbox.getChildren().add(title);
        for (String finding : lungFieldFindings) {
            vbox.getChildren().add(new CheckBox(finding));
        }
        return vbox;
    }

    private TextArea createCustomLungTextArea() {
        TextArea area = new TextArea();
        area.setPromptText("Enter additional custom findings for this lung field...");
        area.setWrapText(true);
        area.setPrefRowCount(3);
        return area;
    }

    private Tab createLungTab(String title, VBox checkList, TextArea customArea) {
        VBox content = new VBox(10, checkList, new Label("Custom Findings:"), customArea);
        content.setPadding(new Insets(10));
        return new Tab(title, content);
    }

    private void addButtons(GridPane grid) {
        Button generateButton = new Button("Generate Report");
        generateButton.setOnAction(e -> generateAndDisplayReport());
        Button saveButton = new Button("Save to EMR");
        saveButton.setOnAction(e -> saveData());
        grid.add(generateButton, 0, 17);
        grid.add(saveButton, 1, 17);
    }

    private void generateAndDisplayReport() {
        findingsTextArea.setText(generateReport());
    }

    private String generateReport() {
        StringBuilder sb = new StringBuilder("CHEST PA SYSTEMATIC REVIEW\n----------------------------\n\n");

        appendSection(sb, "✓. Airways", "Trachea", getComboBoxValue(tracheaComboBox, customTracheaField));
        appendSection(sb, "✓. Bones", "Findings", getComboBoxValue(bonesComboBox, customBonesField));
        appendSection(sb, "✓. Cardiac", "Findings", getComboBoxValue(cardiacComboBox, customCardiacField));
        appendSection(sb, "✓. Diaphragm", "Findings", getComboBoxValue(diaphragmComboBox, customDiaphragmField));
        appendSection(sb, "✓. Effusions/Fields", "Findings", getComboBoxValue(effusionsComboBox, customEffusionsField));
        appendSection(sb, "Devices and Foreign Bodies", "Findings", getComboBoxValue(devicesComboBox, customDevicesField));

        String comparison = getComboBoxValue(comparisonComboBox, customComparisonField);
        String history = getComboBoxValue(historyComboBox, customHistoryField);
        if (!isEmpty(comparison) || !isEmpty(history)) {
            sb.append("Comparison and Review:\n");
            if (!isEmpty(comparison)) sb.append("   - Comparison: ").append(comparison).append("\n");
            if (!isEmpty(history)) sb.append("   - History: ").append(history).append("\n");
            sb.append("\n");
        }

        String rulf = getLungFieldFindings(rulfCheckList, customRulfArea);
        String rmlf = getLungFieldFindings(rmlfCheckList, customRmlfArea);
        String rllf = getLungFieldFindings(rllfCheckList, customRllfArea);
        String lulf = getLungFieldFindings(lulfCheckList, customLulfArea);
        String lmlf = getLungFieldFindings(lmlfCheckList, customLmlfArea);
        String lllf = getLungFieldFindings(lllfCheckList, customLllfArea);

        if (!isEmpty(rulf) || !isEmpty(rmlf) || !isEmpty(rllf) || !isEmpty(lulf) || !isEmpty(lmlf) || !isEmpty(lllf)) {
            sb.append("Structured Lung Field Documentation:\n");
            appendLungField(sb, "RULF", rulf);
            appendLungField(sb, "RMLF", rmlf);
            appendLungField(sb, "RLLF", rllf);
            appendLungField(sb, "LULF", lulf);
            appendLungField(sb, "LMLF", lmlf);
            appendLungField(sb, "LLLF", lllf);
        }

        return sb.toString();
    }

    private String getComboBoxValue(ComboBox<String> comboBox, TextField customField) {
        String selected = comboBox.getValue();
        return CUSTOM_OPTION_TEXT.equals(selected) ? customField.getText() : selected;
    }

    private String getLungFieldFindings(VBox checkListVBox, TextArea customTextArea) {
        List<String> selectedFindings = checkListVBox.getChildren().stream()
                .filter(node -> node instanceof CheckBox && ((CheckBox) node).isSelected())
                .map(node -> ((CheckBox) node).getText())
                .collect(Collectors.toList());

        String customText = customTextArea.getText().trim();
        if (!customText.isEmpty()) {
            selectedFindings.add(customText);
        }
        return String.join(", ", selectedFindings);
    }

    private void appendSection(StringBuilder sb, String section, String name, String text) {
        if (!isEmpty(text) && !"Not documented".equals(text)) {
            sb.append(section).append(":\n   - ").append(name).append(": ").append(text).append("\n\n");
        }
    }

    private void appendLungField(StringBuilder sb, String zone, String findings) {
        if (!isEmpty(findings)) {
            sb.append("   - ").append(zone).append(": ").append(findings).append("\n");
        }
    }

    private void saveData() {
        String report = findingsTextArea.getText();
        if (isEmpty(report)) {
            showError("No report content to save. Please generate the report first.");
            return;
        }

        try {
            IAITextAreaManager manager = IAIMain.getTextAreaManager();
            if (manager == null || !manager.isReady()) {
                showError("EMR connection is not ready. Cannot save data.");
                return;
            }

            String stampedReport = String.format("\n< CHEST PA > %s\n%s", LocalDate.now().format(ISO_DATE_FORMATTER), report.trim());
            Platform.runLater(() -> {
                manager.focusArea(EMR_TARGET_AREA_INDEX);
                manager.insertLineIntoFocusedArea(stampedReport);
                clearAllFields();
            });
        } catch (Exception e) {
            showError("An unexpected error occurred while saving to the EMR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void clearAllFields() {
        List.of(tracheaComboBox, bonesComboBox, cardiacComboBox, diaphragmComboBox, effusionsComboBox, devicesComboBox, comparisonComboBox, historyComboBox)
                .forEach(cb -> cb.getSelectionModel().clearSelection());

        List.of(rulfCheckList, rmlfCheckList, rllfCheckList, lulfCheckList, lmlfCheckList, lllfCheckList)
                .forEach(this::clearCheckList);

        findingsTextArea.clear();
    }

    private void clearCheckList(VBox checkListVBox) {
        if (checkListVBox != null) {
            checkListVBox.getChildren().stream()
                    .filter(CheckBox.class::isInstance)
                    .map(CheckBox.class::cast)
                    .forEach(cb -> cb.setSelected(false));
        }
    }

    private ObservableList<String> createObservableList(String... items) {
        ObservableList<String> list = FXCollections.observableArrayList(items);
        list.add(CUSTOM_OPTION_TEXT);
        return list;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
            alert.setHeaderText(null);
            alert.setTitle("Error");
            alert.showAndWait();
        });
    }
}