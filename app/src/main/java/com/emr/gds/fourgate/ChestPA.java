package com.emr.gds.fourgate;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.emr.gds.input.IAIMain;
import com.emr.gds.input.IAITextAreaManager;

public class ChestPA extends Stage {
    private static final int EMR_AREA = 5;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_DATE;

    private TextField tracheaField, bonesField, cardiacField, diaphragmField,
            effusionsField, devicesField, comparisonField, historyField;
    private TextArea rulfTextArea, rmlfTextArea, rllfTextArea,
            lulfTextArea, lmlfTextArea, lllfTextArea, findingsTextArea;

    public ChestPA(Stage owner) { this.initOwner(owner); setupStage(); }
    public ChestPA() { setupStage(); }
    private void setupStage() {
        setTitle("Chest PA Systematic Review");
        setScene(createScene());
    }
    private Scene createScene() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10)); grid.setVgap(8); grid.setHgap(10);
        addSystematicFields(grid);
        grid.add(createLungTabs(), 0, 8, 2, 1);
        addButtons(grid);
        findingsTextArea = new TextArea(); findingsTextArea.setPromptText("Generated report will appear here...");
        findingsTextArea.setEditable(false); findingsTextArea.setWrapText(true);
        grid.add(findingsTextArea, 0, 10, 2, 1);
        return new Scene(new VBox(10, grid), 800, 700);
    }
    public void open() {
        setMinWidth(800); setMinHeight(700); show();
    }
    private void addSystematicFields(GridPane grid) {
        tracheaField = addRow(grid, "Airways:", "Trachea (central, patent, deviation)", 0);
        bonesField = addRow(grid, "Bones:", "Ribs, clavicles, vertebrae", 1);
        cardiacField = addRow(grid, "Cardiac:", "Heart size, borders, aortic contour", 2);
        diaphragmField = addRow(grid, "Diaphragm:", "Definition, angles, free air", 3);
        effusionsField = addRow(grid, "Effusions:", "Pleural effusion, consolidation, masses", 4);
        devicesField = addRow(grid, "Devices:", "Lines, tubes, foreign objects", 5);
        comparisonField = addRow(grid, "Comparison:", "Previous CXR/CT correlation", 6);
        historyField = addRow(grid, "History:", "Clinical history", 7);
    }
    private TextField addRow(GridPane g, String label, String prompt, int row) {
        TextField tf = new TextField(); tf.setPromptText(prompt);
        g.add(new Label(label), 0, row); g.add(tf, 1, row);
        return tf;
    }
    private TabPane createLungTabs() {
        rulfTextArea = new TextArea(); rmlfTextArea = new TextArea(); rllfTextArea = new TextArea();
        lulfTextArea = new TextArea(); lmlfTextArea = new TextArea(); lllfTextArea = new TextArea();
        TabPane tabs = new TabPane(
            createTab("RULF", rulfTextArea, "Right Upper Lung Field..."),
            createTab("RMLF", rmlfTextArea, "Right Middle Lung Field..."),
            createTab("RLLF", rllfTextArea, "Right Lower Lung Field..."),
            createTab("LULF", lulfTextArea, "Left Upper Lung Field..."),
            createTab("LMLF", lmlfTextArea, "Left Middle Lung Field..."),
            createTab("LLLF", lllfTextArea, "Left Lower Lung Field...")
        );
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return tabs;
    }
    private Tab createTab(String title, TextArea area, String prompt) {
        area.setPromptText(prompt);
        return new Tab(title, area);
    }
    private void addButtons(GridPane grid) {
        Button gen = new Button("Generate Report"); gen.setOnAction(e -> generateAndDisplayReport());
        Button save = new Button("Save to EMR"); save.setOnAction(e -> saveData());
        grid.add(gen, 0, 9); grid.add(save, 1, 9);
    }

    private void generateAndDisplayReport() {
        findingsTextArea.setText(generateReport(
            tracheaField.getText(), bonesField.getText(), cardiacField.getText(), diaphragmField.getText(),
            effusionsField.getText(), devicesField.getText(), comparisonField.getText(), historyField.getText(),
            rulfTextArea.getText(), rmlfTextArea.getText(), rllfTextArea.getText(),
            lulfTextArea.getText(), lmlfTextArea.getText(), lllfTextArea.getText()
        ));
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
        final String report = findingsTextArea.getText() == null ? "" : findingsTextArea.getText().trim();
        if (report.isEmpty()) { showError("No report content. Generate report first."); return; }
        if (!isIttiaAppReady()) { showError("IttiaApp not ready."); return; }

        final IAITextAreaManager manager;
        try {
            manager = IAIMain.getTextAreaManager();
            if (manager == null) throw new IllegalStateException("EMR TextAreaManager is null");
        } catch (IllegalStateException e) {
            showError("EMR TextAreaManager not initialized."); return;
        }
        if (!bridgeReady(manager)) { showError("EMR bridge not ready."); return; }

        final String stamped = String.format("\n< CHEST PA > %s\n%s", LocalDate.now().format(ISO), report);
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
        tracheaField.clear(); bonesField.clear(); cardiacField.clear(); diaphragmField.clear();
        effusionsField.clear(); devicesField.clear(); comparisonField.clear(); historyField.clear();
        rulfTextArea.clear(); rmlfTextArea.clear(); rllfTextArea.clear();
        lulfTextArea.clear(); lmlfTextArea.clear(); lllfTextArea.clear();
        findingsTextArea.clear();
    }
    private void showError(String msg) {
        Runnable r = () -> {
            Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
            a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
        };
        if (Platform.isFxApplicationThread()) r.run(); else Platform.runLater(r);
    }
}
