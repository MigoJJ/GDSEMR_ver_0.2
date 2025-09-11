package com.emr.gds.fourgate;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.emr.gds.input.IAIMain;
import com.emr.gds.input.IAITextAreaManager;

public class ChestPA extends Stage {
    private static final int EMR_AREA = 5;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_DATE;
    private static final String CUSTOM_OPTION = "Custom...";

    // UI Components
    private ComboBox<String> tracheaComboBox, bonesComboBox, cardiacComboBox, diaphragmComboBox,
            effusionsComboBox, devicesComboBox, comparisonComboBox, historyComboBox;
    private TextField customTracheaField, customBonesField, customCardiacField, customDiaphragmField,
            customEffusionsField, customDevicesField, customComparisonField, customHistoryField;
    private VBox rulfCheckList, rmlfCheckList, rllfCheckList, lulfCheckList, lmlfCheckList, lllfCheckList;
    private TextArea customRulfArea, customRmlfArea, customRllfArea, customLulfArea, customLmlfArea, customLllfArea;
    private TextArea findingsTextArea;

    // Data
    private final ObservableList<String> tracheaOptions = FXCollections.observableArrayList(
            "Midline", "Deviated to the right", "Deviated to the left", "No significant deviation",
            "Not well visualized", CUSTOM_OPTION);
    private final ObservableList<String> bonesOptions = FXCollections.observableArrayList(
            "No acute fractures or dislocations", "Degenerative changes noted", "Normal bony thorax",
            "Osteopenia", "Sclerotic lesions in [specific area, e.g., T-spine]", CUSTOM_OPTION);
    private final ObservableList<String> cardiacOptions = FXCollections.observableArrayList(
            "Normal heart size and contour", "Mild cardiomegaly", "Moderate cardiomegaly",
            "Borderline enlarged cardiac silhouette", "No pericardial effusion", CUSTOM_OPTION);
    private final ObservableList<String> diaphragmOptions = FXCollections.observableArrayList(
            "Healed bilateral costo-phrenic angles blunted", "Clear costo-phrenic angles",
            "No diaphragmatic elevation", "Mild elevation of the right hemidiaphragm",
            "Flattening of hemidiaphragms", CUSTOM_OPTION);
    private final ObservableList<String> effusionsOptions = FXCollections.observableArrayList(
            "No pleural effusions", "Small right pleural effusion", "Small left pleural effusion",
            "Bilateral small pleural effusions", "Trace right pleural effusion", CUSTOM_OPTION);
    private final ObservableList<String> devicesOptions = FXCollections.observableArrayList(
            "Endotracheal tube in good position", "Central venous catheter tip in SVC",
            "Nasogastric tube in expected position", "No acute changes related to surgical clips",
            "No foreign bodies identified", CUSTOM_OPTION);
    private final ObservableList<String> comparisonOptions = FXCollections.observableArrayList(
            "There are no active lesions in the lung.", "Compared to previous [date], no significant change",
            "Compared to previous [date], new findings noted", "Compared to previous [date], interval improvement",
            "No prior studies available for comparison", "Compared to previous [date], interval worsening", CUSTOM_OPTION);
    private final ObservableList<String> historyOptions = FXCollections.observableArrayList(
            "Shortness of breath", "Cough", "Chest pain", "Fever", CUSTOM_OPTION);
    private final ObservableList<String> lungFieldFindings = FXCollections.observableArrayList(
            "Clear", "Normal vascularity", "No focal consolidation", "No acute infiltrate",
            "Interstitial opacities", "Atelectasis", "Nodules", "Masses");

    public ChestPA(Stage owner) { this.initOwner(owner); setupStage(); }
    public ChestPA() { setupStage(); }

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

    public void open() {
        setWidth(1300);
        setHeight(850);
        setMinWidth(1000);
        setMinHeight(800);
        show();
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

        // Assign to instance fields based on label
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
            boolean isCustom = CUSTOM_OPTION.equals(newValue);
            customField.setVisible(isCustom);
            customField.setManaged(isCustom);
            if (!isCustom) customField.clear();
        });
    }

    private TabPane createLungTabs() {
        String[] areas = {"RULF", "RMLF", "RLLF", "LULF", "LMLF", "LLLF"};
        VBox[] checkLists = new VBox[6];
        TextArea[] customAreas = new TextArea[6];
        String[] titles = {"Right Upper Lung Field", "Right Middle Lung Field", "Right Lower Lung Field",
                "Left Upper Lung Field", "Left Middle Lung Field", "Left Lower Lung Field"};

        for (int i = 0; i < 6; i++) {
            checkLists[i] = createCheckListBox(areas[i]);
            customAreas[i] = createCustomLungTextArea();
            if (i == 0) { rulfCheckList = checkLists[i]; customRulfArea = customAreas[i]; }
            else if (i == 1) { rmlfCheckList = checkLists[i]; customRmlfArea = customAreas[i]; }
            else if (i == 2) { rllfCheckList = checkLists[i]; customRllfArea = customAreas[i]; }
            else if (i == 3) { lulfCheckList = checkLists[i]; customLulfArea = customAreas[i]; }
            else if (i == 4) { lmlfCheckList = checkLists[i]; customLmlfArea = customAreas[i]; }
            else { lllfCheckList = checkLists[i]; customLllfArea = customAreas[i]; }
        }

        TabPane tabs = new TabPane();
        for (int i = 0; i < 6; i++) {
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
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(checkList, new Label("Custom Findings:"), customArea);
        return new Tab(title, content);
    }

    private void addButtons(GridPane grid) {
        Button gen = new Button("Generate Report");
        gen.setOnAction(e -> generateAndDisplayReport());
        Button save = new Button("Save to EMR");
        save.setOnAction(e -> saveData());
        grid.add(gen, 0, 17);
        grid.add(save, 1, 17);
    }

    private void generateAndDisplayReport() {
        findingsTextArea.setText(generateReport(
                getComboBoxValue(tracheaComboBox, customTracheaField),
                getComboBoxValue(bonesComboBox, customBonesField),
                getComboBoxValue(cardiacComboBox, customCardiacField),
                getComboBoxValue(diaphragmComboBox, customDiaphragmField),
                getComboBoxValue(effusionsComboBox, customEffusionsField),
                getComboBoxValue(devicesComboBox, customDevicesField),
                getComboBoxValue(comparisonComboBox, customComparisonField),
                getComboBoxValue(historyComboBox, customHistoryField),
                getLungFieldFindings(rulfCheckList, customRulfArea),
                getLungFieldFindings(rmlfCheckList, customRmlfArea),
                getLungFieldFindings(rllfCheckList, customRllfArea),
                getLungFieldFindings(lulfCheckList, customLulfArea),
                getLungFieldFindings(lmlfCheckList, customLmlfArea),
                getLungFieldFindings(lllfCheckList, customLllfArea)
        ));
    }

    private String getComboBoxValue(ComboBox<String> comboBox, TextField customField) {
        String selected = comboBox.getValue();
        return CUSTOM_OPTION.equals(selected) ? customField.getText() : selected;
    }

    private String getLungFieldFindings(VBox checkListVBox, TextArea customTextArea) {
        List<String> selectedFindings = new ArrayList<>();
        for (javafx.scene.Node node : checkListVBox.getChildren()) {
            if (node instanceof CheckBox cb && cb.isSelected() && !cb.getText().contains("Findings:")) {
                selectedFindings.add(cb.getText());
            }
        }
        String customText = customTextArea.getText().trim();
        if (!customText.isEmpty()) selectedFindings.add(customText);
        return selectedFindings.isEmpty() ? "Clear" : String.join(", ", selectedFindings);
    }

    private String generateReport(String trachea, String bones, String cardiac, String diaphragm,
                                  String effusions, String devices, String comparison, String history,
                                  String rulf, String rmlf, String rllf, String lulf, String lmlf, String lllf) {
        StringBuilder sb = new StringBuilder("CHEST PA SYSTEMATIC REVIEW\n----------------------------\n\n");
        appendSection(sb, "A. Airways", "Trachea", trachea);
        appendSection(sb, "B. Bones", "Findings", bones);
        appendSection(sb, "C. Cardiac", "Findings", cardiac);
        appendSection(sb, "D. Diaphragm", "Findings", diaphragm);
        appendSection(sb, "E. Effusions/Fields", "Findings", effusions);
        appendSection(sb, "Devices and Foreign Bodies", "Findings", devices);
        sb.append("Comparison and Review:\n")
                .append("   - Comparison: ").append(isEmpty(comparison) ? "Not documented" : comparison).append("\n")
                .append("   - History: ").append(isEmpty(history) ? "Not documented" : history).append("\n\n")
                .append("Structured Lung Field Documentation:\n");
        appendLungField(sb, "RULF", rulf);
        appendLungField(sb, "RMLF", rmlf);
        appendLungField(sb, "RLLF", rllf);
        appendLungField(sb, "LULF", lulf);
        appendLungField(sb, "LMLF", lmlf);
        appendLungField(sb, "LLLF", lllf);
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String section, String name, String text) {
        sb.append(section).append(":\n   - ").append(name).append(": ")
                .append(isEmpty(text) ? "Not documented" : text).append("\n\n");
    }

    private void appendLungField(StringBuilder sb, String zone, String findings) {
        sb.append("   - ").append(zone).append(": ").append(isEmpty(findings) ? "Clear" : findings).append("\n");
    }

    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    private void saveData() {
        String report = findingsTextArea.getText() != null ? findingsTextArea.getText().trim() : "";
        if (report.isEmpty()) { showError("No report content. Generate report first."); return; }
        if (!isIttiaAppReady()) { showError("IttiaApp not ready."); return; }

        IAITextAreaManager manager;
        try {
            manager = IAIMain.getTextAreaManager();
            if (manager == null) throw new IllegalStateException("EMR TextAreaManager is null");
        } catch (IllegalStateException e) {
            showError("EMR TextAreaManager not initialized."); return;
        }
        if (!bridgeReady(manager)) { showError("EMR bridge not ready."); return; }

        String stamped = String.format("\n< CHEST PA > %s\n%s", LocalDate.now().format(ISO), report);
        Platform.runLater(() -> {
            try {
                saveToIttiaApp(stamped);
                if (!manager.isValidIndex(EMR_AREA)) throw new IllegalArgumentException("Invalid EMR index: " + EMR_AREA);
                manager.focusArea(EMR_AREA);
                manager.insertLineIntoFocusedArea(stamped);
                clearFields();
            } catch (Exception ex) {
                showError("Error saving data: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    private boolean isIttiaAppReady() {
        try {
            System.out.println("Checking IttiaApp readiness: simulated ready.");
            return true;
        } catch (Exception e) {
            System.err.println("IttiaApp not ready: " + e.getMessage());
            return false;
        }
    }

    private void saveToIttiaApp(String report) throws Exception {
        System.out.println("Saving to IttiaApp:\n" + report);
    }

    private boolean bridgeReady(IAITextAreaManager manager) { return manager != null && manager.isReady(); }

    private void clearFields() {
        clearCombos();
        clearCustomFields();
        clearLungFields();
        findingsTextArea.clear();
    }

    private void clearCombos() {
        List<ComboBox<String>> combos = List.of(tracheaComboBox, bonesComboBox, cardiacComboBox, diaphragmComboBox,
                effusionsComboBox, devicesComboBox, comparisonComboBox, historyComboBox);
        combos.forEach(cb -> cb.getSelectionModel().clearSelection());
    }

    private void clearCustomFields() {
        List<TextField> fields = List.of(customTracheaField, customBonesField, customCardiacField, customDiaphragmField,
                customEffusionsField, customDevicesField, customComparisonField, customHistoryField);
        fields.forEach(tf -> { tf.clear(); tf.setVisible(false); tf.setManaged(false); });
    }

    private void clearLungFields() {
        List<VBox> lists = List.of(rulfCheckList, rmlfCheckList, rllfCheckList, lulfCheckList, lmlfCheckList, lllfCheckList);
        List<TextArea> areas = List.of(customRulfArea, customRmlfArea, customRllfArea, customLulfArea, customLmlfArea, customLllfArea);
        for (int i = 0; i < 6; i++) {
            clearCheckList(lists.get(i));
            areas.get(i).clear();
        }
    }

    private void clearCheckList(VBox checkListVBox) {
        checkListVBox.getChildren().stream()
                .filter(node -> node instanceof CheckBox)
                .map(node -> (CheckBox) node)
                .forEach(cb -> cb.setSelected(false));
    }

    private void showError(String msg) {
        Runnable r = () -> {
            Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
            a.setHeaderText(null);
            a.setTitle("Error");
            a.showAndWait();
        };
        if (Platform.isFxApplicationThread()) r.run(); else Platform.runLater(r);
    }
}