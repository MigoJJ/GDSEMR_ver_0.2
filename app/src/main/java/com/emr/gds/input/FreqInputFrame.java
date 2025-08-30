package com.emr.gds.input;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JavaFX version of FreqInputFrame.
 * ------------------------------------------------------------
 * A compact utility window for quick EMR entries:
 *   1) BMI calculator
 *   2) HbA1c builder (IFCC / eAG auto-calc)
 *   3) Vital sign tracker (scripted input + numeric sequence)
 *
 * NOTES:
 * - This class extends Stage (JavaFX). No Swing.
 * - Your main app MUST wire the bridge once AFTER creating the 10 TextAreas, e.g.:
 *     IttiaAppMain.setTextAreaManager(new FxTextAreaManager(areas));
 *
 * Conventions:
 * - Vitals are saved to O> (index 5).
 * - HbA1c status line is appended to A> (index 7).
 */
public class FreqInputFrame extends Stage {

    // ------------------------------------------------------------
    // BMI section
    // ------------------------------------------------------------
    private static final List<String> BMI_FIELDS = List.of(
            "Height (cm):", "Weight (kg):", "Waist (cm or inch):"
    );
    private final TextField[] bmiInputs = new TextField[BMI_FIELDS.size()];

    // ------------------------------------------------------------
    // HbA1c section
    // ------------------------------------------------------------
    private static final String[] HBA1C_LABELS = { "FBS / PP2 time", "Glucose mg/dL", "HbA1c %" };
    private static final String[][] GLUCOSE_STATUS = {
            {"9.0", "Very poor"},
            {"8.5", "Poor"},
            {"7.5", "Fair"},
            {"6.5", "Good"},
            {"0.0", "Excellent"}
    };
    private final TextArea hba1cOutputArea = new TextArea();
    private final TextField[] hba1cInputs = new TextField[HBA1C_LABELS.length];

    // ------------------------------------------------------------
    // Vital signs section
    // ------------------------------------------------------------
    private TextField vsInputField;
    private TextArea vsOutputArea;
    private TextArea vsDescriptionArea;
    private Set<String> vsValidInputs;

    // staged numeric values
    private Integer sbp = null;
    private Integer dbp = null;
    private Integer pulseRate = null;
    private Double bodyTemperature = null;
    private Integer respirationRate = null;

    // ------------------------------------------------------------
    // Constructor (build & show)
    // ------------------------------------------------------------
    public FreqInputFrame() {
        initStyle(StageStyle.UNDECORATED);
        setTitle("Frequent Data Input");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(8));

        VBox content = new VBox(8);
        content.getChildren().addAll(
                createBmiPane(),
                createHba1cPane(),
                createVitalsPane(),
                createBottomButtons()
        );
        root.setCenter(content);

        Scene scene = new Scene(root, 360, 710);
        setScene(scene);

        // position: top-right of primary screen once shown
        setOnShown(ev -> {
            Rectangle2D vb = Screen.getPrimary().getVisualBounds();
            setX(vb.getMaxX() - getWidth() - 20);
            setY(vb.getMinY() + 10);
        });

        initializeVitalsValidInputs();

        // Soft check: warn early if bridge is not wired
        Platform.runLater(() -> {
            if (!bridgeReady()) {
                showError("EMR text areas are not ready yet.\n" +
                          "Please make sure IttiaAppMain.setTextAreaManager(...) was called after building the 10 TextAreas.");
            }
        });

        show();
    }

    // ============================================================
    // BMI
    // ============================================================
    private TitledPane createBmiPane() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(6));

        for (int i = 0; i < BMI_FIELDS.size(); i++) {
            Label label = new Label(BMI_FIELDS.get(i));
            TextField field = new TextField();
            field.setPromptText(BMI_FIELDS.get(i).replace(":", "").trim());
            field.setPrefHeight(30);
            bmiInputs[i] = field;

            final int row = i;
            field.setOnAction(e -> {
                if (row < bmiInputs.length - 1) {
                    bmiInputs[row + 1].requestFocus();
                } else {
                    onSaveBMI();
                }
            });

            grid.add(label, 0, i);
            grid.add(field, 1, i);
        }

        Button save = new Button("Save BMI");
        save.setOnAction(e -> onSaveBMI());
        GridPane.setColumnSpan(save, 2);
        grid.add(save, 0, BMI_FIELDS.size());

        TitledPane tp = new TitledPane("BMI Calculator", grid);
        tp.setExpanded(true);
        return tp;
    }

    private void onSaveBMI() {
        try {
            double height = Double.parseDouble(bmiInputs[0].getText().trim());
            double weight = Double.parseDouble(bmiInputs[1].getText().trim());
            double bmi = weight / Math.pow(height / 100.0, 2.0);
            String category = bmiCategory(bmi);
            String waist = processWaist(bmiInputs[2].getText().trim());

            String bmiText = String.format("%s : BMI: [ %.2f ] kg/m^2", category, bmi);
            String details = String.format(
                    "\n< BMI >\n%s\nHeight : %.1f cm   Weight : %.1f kg%s",
                    bmiText, height, weight,
                    waist.isEmpty() ? "" : "   Waist: " + waist + " cm"
            );

            if (!bridgeReady()) {
                showError("Cannot insert BMI: EMR text areas not ready.");
                return;
            }
            // Insert into the currently focused area (do not force a section change)
            IttiaAppMain.getTextAreaManager().insertBlockIntoFocusedArea(details);

            for (TextField f : bmiInputs) f.clear();
            bmiInputs[0].requestFocus();

        } catch (NumberFormatException ex) {
            showError("Please enter valid numbers for Height and Weight.");
        }
    }

    private static String processWaist(String waistRaw) {
        if (waistRaw.isEmpty()) return "";
        String w = waistRaw.trim().toLowerCase();
        if (w.contains("i")) { // inches marker present (e.g., "32 in")
            double inches = Double.parseDouble(w.replaceAll("[^\\d.]", ""));
            return String.format("%.1f", inches * 2.54);
        }
        // assume already in cm
        return w.replaceAll("[^\\d.]", "");
    }

    private static String bmiCategory(double bmi) {
        if (bmi < 18.5) return "Underweight";
        if (bmi < 25.0) return "Healthy weight";
        if (bmi < 30.0) return "Overweight";
        return "Obesity";
    }

    // ============================================================
    // HbA1c
    // ============================================================
    private TitledPane createHba1cPane() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(6));

        hba1cOutputArea.setEditable(false);
        hba1cOutputArea.setWrapText(true);
        hba1cOutputArea.setPrefRowCount(4);

        GridPane inputs = new GridPane();
        inputs.setHgap(8);
        inputs.setVgap(6);

        for (int i = 0; i < HBA1C_LABELS.length; i++) {
            Label label = new Label(HBA1C_LABELS[i]);
            TextField tf = new TextField();
            tf.setPrefHeight(30);
            tf.setPromptText(HBA1C_LABELS[i]);
            hba1cInputs[i] = tf;

            final int idx = i;
            tf.setOnAction(e -> onHba1cInput(idx));

            inputs.add(label, 0, i);
            inputs.add(tf, 1, i);
        }

        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Button clear = new Button("Clear");
        Button save  = new Button("Save");
        clear.setOnAction(e -> clearHba1c());
        save.setOnAction(e -> saveHba1cToEMR());
        buttons.getChildren().addAll(clear, save);

        box.getChildren().addAll(new Label("Output:"), hba1cOutputArea,
                new Separator(), inputs, buttons);

        TitledPane tp = new TitledPane("HbA1c EMR", box);
        tp.setExpanded(true);
        return tp;
    }

    private void onHba1cInput(int index) {
        String value = hba1cInputs[index].getText().trim();
        if (value.isEmpty()) return;

        if (index == 0) {
            // "0" -> FBS, otherwise PP{minutes}
            hba1cOutputArea.appendText("\n   " + (value.equals("0") ? "FBS" : "PP" + value));
        } else if (index == 1) {
            hba1cOutputArea.appendText("   [    " + value + "   ] mg/dL");
        } else if (index == 2) {
            try {
                double h = Double.parseDouble(value);
                hba1cOutputArea.appendText("   HbA1c       [    " + value + "   ] %\n");
                appendHba1cCalcs(h);
                // autosave to O> and status to A>, then clear
                saveHba1cToEMR();
                clearHba1c();
            } catch (NumberFormatException ex) {
                showError("Invalid HbA1c value.");
            }
        }

        hba1cInputs[index].clear();
        if (index < hba1cInputs.length - 1) {
            hba1cInputs[index + 1].requestFocus();
        } else {
            hba1cInputs[0].requestFocus();
        }
    }

    private void appendHba1cCalcs(double hba1c) {
        double ifcc = (hba1c - 2.15) * 10.929;
        double eagMgDl = (28.7 * hba1c) - 46.7;
        double eagMmolL = eagMgDl / 18.01559;

        hba1cOutputArea.appendText(String.format(
                "\n\tIFCC HbA1c: [ %.0f ] mmol/mol" +
                "\n\teAG: [ %.0f ] mg/dL" +
                "\n\teAG: [ %.2f ] mmol/L\n",
                ifcc, eagMgDl, eagMmolL));

        // Status line -> A> (index 7), first match by threshold
        for (String[] status : GLUCOSE_STATUS) {
            if (hba1c > Double.parseDouble(status[0])) {
                if (!bridgeReady()) return;
                final String line = "\n...now [ " + status[1] + " ] controlled glucose status";
                IttiaAppMain.getTextAreaManager().focusArea(9); // A>
                IttiaAppMain.getTextAreaManager().insertLineIntoFocusedArea(line);
                break;
            }
        }
    }

    private void clearHba1c() {
        hba1cOutputArea.clear();
        for (TextField f : hba1cInputs) f.clear();
        hba1cInputs[0].requestFocus();
    }

    private void saveHba1cToEMR() {
        String text = hba1cOutputArea.getText().trim();
        if (text.isEmpty()) return;
        if (!bridgeReady()) {
            showError("Cannot save HbA1c: EMR text areas not ready.");
            return;
        }
        // O> index 5 (Objective)
        IttiaAppMain.getTextAreaManager().focusArea(5);
        if (text.contains("\n")) {
            IttiaAppMain.getTextAreaManager().insertBlockIntoFocusedArea(text + "\n");
        } else {
            IttiaAppMain.getTextAreaManager().insertLineIntoFocusedArea(text);
        }
    }

    // ============================================================
    // Vital signs
    // ============================================================
    private TitledPane createVitalsPane() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(6));

        vsInputField = new TextField();
        vsInputField.setPromptText("Enter code (h/o/g/l/r/i/t36.5) or numbers (SBP→DBP→PR→BT→RR)");
        vsInputField.setPrefHeight(30);
        vsInputField.setOnAction(e -> {
            handleVitalsInput(vsInputField.getText().trim().toLowerCase());
            vsInputField.clear();
        });

        vsDescriptionArea = new TextArea(" at GDS : Regular pulse, Right Seated Position");
        vsDescriptionArea.setEditable(false);
        vsDescriptionArea.setWrapText(true);
        vsDescriptionArea.setPrefRowCount(2);

        vsOutputArea = new TextArea();
        vsOutputArea.setEditable(false);
        vsOutputArea.setWrapText(true);
        vsOutputArea.setPrefRowCount(5);

        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Button clear = new Button("Clear");
        Button save  = new Button("Save");
        clear.setOnAction(e -> resetVitalsFields());
        save.setOnAction(e -> saveVitalsToEMR());
        buttons.getChildren().addAll(clear, save);

        box.getChildren().addAll(vsInputField,
                new Label("Description:"), vsDescriptionArea,
                new Label("Output:"), vsOutputArea,
                buttons);

        TitledPane tp = new TitledPane("Vital Sign Tracker", box);
        tp.setExpanded(true);
        return tp;
    }

    private void initializeVitalsValidInputs() {
        vsValidInputs = new HashSet<>();
        vsValidInputs.add("h"); // Home
        vsValidInputs.add("o"); // Other clinic
        vsValidInputs.add("g"); // GDS
        vsValidInputs.add("l"); // Left position
        vsValidInputs.add("r"); // Right position
        vsValidInputs.add("i"); // Irregular pulse
        vsValidInputs.add("t"); // Temperature prefix (e.g., t36.5)
    }

    private void handleVitalsInput(String input) {
        if (input.isEmpty()) return;

        if (vsValidInputs.contains(input) || input.startsWith("t")) {
            if (input.startsWith("t")) {
                handleVitalsTemperatureInput(input);
            } else {
                updateVitalsDescription(input);
            }
        } else {
            // numeric sequence handler: SBP -> DBP -> PR -> BT -> RR
            try {
                double value = Double.parseDouble(input);
                processVitalsNumeric(value);
            } catch (NumberFormatException ex) {
                vsOutputArea.setText("Invalid input. Enter a code (h/o/g/l/r/i/t#) or a number.");
            }
        }
    }

    private void updateVitalsDescription(String code) {
        String cur = vsDescriptionArea.getText();
        switch (code) {
            case "h" -> vsDescriptionArea.setText("   at home by self");
            case "o" -> vsDescriptionArea.setText("   at Other clinic");
            case "g" -> vsDescriptionArea.setText(" at GDS : Regular pulse, Right Seated Position");
            case "l" -> vsDescriptionArea.setText(cur.replace("Right", "Left"));
            case "r" -> vsDescriptionArea.setText(cur.replace("Left", "Right"));
            case "i" -> vsDescriptionArea.setText(cur.replace("Regular", "Irregular"));
            default -> { /* no-op */ }
        }
    }

    private void handleVitalsTemperatureInput(String input) {
        try {
            double t = Double.parseDouble(input.substring(1));
            vsDescriptionArea.setText(" at GDS : Forehead (Temporal Artery) Thermometer:");
            vsOutputArea.setText("Body Temperature [ " + t + " ] ℃");
        } catch (RuntimeException ex) {
            vsOutputArea.setText("Invalid temperature input. Use 't' followed by a number (e.g., t36.5).");
        }
    }

    private void processVitalsNumeric(double v) {
        if (sbp == null) {
            sbp = (int) v;
            vsOutputArea.setText("\tSBP [" + sbp + "] mmHg   ");
            return;
        }
        if (dbp == null) {
            dbp = (int) v;
            vsOutputArea.setText("BP [" + sbp + " / " + dbp + "] mmHg   ");
            return;
        }
        if (pulseRate == null) {
            pulseRate = (int) v;
            vsOutputArea.appendText("PR [" + pulseRate + "]/minute   ");
            return;
        }
        if (bodyTemperature == null) {
            bodyTemperature = v;
            vsOutputArea.appendText("\n\tBody Temperature [" + bodyTemperature + "]℃");
            return;
        }
        if (respirationRate == null) {
            respirationRate = (int) v;
            vsOutputArea.appendText("\n\tRespiration Rate [" + respirationRate + "]/minute");
            // complete set captured; reset for next cycle
            resetVitalsStaged();
        }
    }

    private void saveVitalsToEMR() {
        final String desc = vsDescriptionArea.getText().trim();
        final String out  = vsOutputArea.getText().trim();
        if (desc.isEmpty() && out.isEmpty()) return;

        if (!bridgeReady()) {
            showError("Cannot save vitals: EMR text areas not ready.");
            return;
        }

        // Target O> index = 5
        IttiaAppMain.getTextAreaManager().focusArea(5);
        if (!desc.isEmpty()) {
            IttiaAppMain.getTextAreaManager().insertLineIntoFocusedArea(desc);
        }
        if (!out.isEmpty()) {
            IttiaAppMain.getTextAreaManager().insertLineIntoFocusedArea("\t" + out);
        }
        resetVitalsFields();
    }

    private void resetVitalsFields() {
        vsInputField.clear();
        vsOutputArea.clear();
        vsDescriptionArea.setText(" at GDS : Regular pulse, Right Seated Position");
        resetVitalsStaged();
    }

    private void resetVitalsStaged() {
        sbp = null;
        dbp = null;
        pulseRate = null;
        bodyTemperature = null;
        respirationRate = null;
    }

    // ============================================================
    // Bottom buttons (Quit)
    // ============================================================
    private HBox createBottomButtons() {
        Button quit = new Button("Quit All");
        quit.setOnAction(e -> close());

        HBox box = new HBox(8, quit);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(4, 0, 0, 0));
        return box;
    }

    // ============================================================
    // Helpers
    // ============================================================
    private static boolean bridgeReady() {
        try {
            return IttiaAppMain.getTextAreaManager().isReady();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void showError(String msg) {
        Platform.runLater(() ->
                new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait()
        );
    }
}
