package com.emr.gds.input;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A compact JavaFX utility window for frequent EMR data entries, including BMI, HbA1c, and vital signs.
 * This window is designed as an undecorated stage that positions itself on the top-right of the screen.
 */
public class IAIFreqFrame extends Stage {

    // BMI components
    private final TextField[] bmiInputs = new TextField[3];

    // HbA1c components
    private final TextArea hba1cOutputArea = new TextArea();
    private final TextField[] hba1cInputs = new TextField[3];

    // Vital signs components
    private TextField vsInputField;
    private TextArea vsOutputArea;
    private TextArea vsDescriptionArea;
    private Set<String> vsValidInputs;
    private Integer sbp, dbp, pulseRate, respirationRate;
    private Double bodyTemperature;

    public IAIFreqFrame() {
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

        setScene(new Scene(root, 360, 760));
        setOnShown(e -> positionInTopRight());

        initializeVitalsValidInputs();
        checkBridgeReady();
        show();
    }

    private TitledPane createBmiPane() {
        GridPane grid = createFormGrid();
        String[] bmiLabels = {"Height (cm):", "Weight (kg):", "Waist (cm or inch):"};
        for (int i = 0; i < bmiLabels.length; i++) {
            bmiInputs[i] = new TextField();
            bmiInputs[i].setPromptText(bmiLabels[i].replace(":", ""));
            final int nextIndex = i + 1;
            bmiInputs[i].setOnAction(e -> {
                if (nextIndex < bmiInputs.length) bmiInputs[nextIndex].requestFocus();
                else onSaveBMI();
            });
            grid.add(new Label(bmiLabels[i]), 0, i);
            grid.add(bmiInputs[i], 1, i);
        }
        Button saveButton = new Button("Save BMI");
        saveButton.setOnAction(e -> onSaveBMI());
        grid.add(saveButton, 0, bmiLabels.length, 2, 1);
        return new TitledPane("BMI Calculator", grid);
    }

    private void onSaveBMI() {
        try {
            double height = Double.parseDouble(bmiInputs[0].getText());
            double weight = Double.parseDouble(bmiInputs[1].getText());
            double bmi = weight / Math.pow(height / 100.0, 2.0);
            String category = (bmi < 18.5) ? "Underweight" : (bmi < 25.0) ? "Healthy" : (bmi < 30.0) ? "Overweight" : "Obesity";
            String waist = processWaist(bmiInputs[2].getText());

            String report = String.format("\n< BMI >\n%s : BMI: [ %.2f ] kg/m^2\nHeight : %.1f cm   Weight : %.1f kg%s",
                    category, bmi, height, weight, waist.isEmpty() ? "" : "   Waist: " + waist + " cm");

            IAIMain.getTextAreaManager().insertBlockIntoFocusedArea(report);
            for (TextField field : bmiInputs) field.clear();
            bmiInputs[0].requestFocus();
        } catch (NumberFormatException ex) {
            showError("Please enter valid numbers for Height and Weight.");
        }
    }

    private String processWaist(String waistRaw) {
        if (waistRaw == null || waistRaw.isBlank()) return "";
        String w = waistRaw.trim().toLowerCase();
        if (w.contains("i")) {
            double inches = Double.parseDouble(w.replaceAll("[^\\d.]", ""));
            return String.format("%.1f", inches * 2.54);
        }
        return w.replaceAll("[^\\d.]", "");
    }

    private TitledPane createHba1cPane() {
        VBox box = new VBox(6, new Label("Output:"), hba1cOutputArea);
        hba1cOutputArea.setEditable(false);
        hba1cOutputArea.setPrefRowCount(4);

        GridPane inputs = createFormGrid();
        String[] hba1cLabels = {"FBS / PP2 time", "Glucose mg/dL", "HbA1c %"};
        for (int i = 0; i < hba1cLabels.length; i++) {
            hba1cInputs[i] = new TextField();
            hba1cInputs[i].setPromptText(hba1cLabels[i]);
            final int index = i;
            hba1cInputs[i].setOnAction(e -> onHba1cInput(index));
            inputs.add(new Label(hba1cLabels[i]), 0, i);
            inputs.add(hba1cInputs[i], 1, i);
        }

        HBox buttons = new HBox(8, createButton("Clear", e -> clearHba1c()), createButton("Save", e -> saveHba1cToEMR()));
        buttons.setAlignment(Pos.CENTER_RIGHT);
        box.getChildren().addAll(new Separator(), inputs, buttons);
        return new TitledPane("HbA1c EMR", box);
    }

    private void onHba1cInput(int index) {
        String value = hba1cInputs[index].getText().trim();
        if (value.isEmpty()) return;

        if (index == 0) {
            hba1cOutputArea.appendText("\n   " + (value.equals("0") ? "FBS" : "PP" + value));
        } else if (index == 1) {
            hba1cOutputArea.appendText("   [ " + value + " ] mg/dL");
        } else if (index == 2) {
            try {
                double hba1c = Double.parseDouble(value);
                hba1cOutputArea.appendText("   HbA1c [ " + value + " ] %\n");
                appendHba1cCalculations(hba1c);
                saveHba1cToEMR();
                clearHba1c();
            } catch (NumberFormatException ex) {
                showError("Invalid HbA1c value.");
            }
        }

        hba1cInputs[index].clear();
        int nextIndex = (index + 1) % hba1cInputs.length;
        hba1cInputs[nextIndex].requestFocus();
    }

    private void appendHba1cCalculations(double hba1c) {
        double ifcc = (hba1c - 2.15) * 10.929;
        double eagMgDl = (28.7 * hba1c) - 46.7;
        hba1cOutputArea.appendText(String.format("\n\tIFCC HbA1c: [ %.0f ] mmol/mol\n\teAG: [ %.0f ] mg/dL\n", ifcc, eagMgDl));

        String status = (hba1c > 9.0) ? "Very poor" : (hba1c > 8.5) ? "Poor" : (hba1c > 7.5) ? "Fair" : (hba1c > 6.5) ? "Good" : "Excellent";
        IAIMain.getTextAreaManager().insertLineIntoArea(IAITextAreaManager.AREA_A, "\n...now [ " + status + " ] controlled glucose status", true);
    }

    private void clearHba1c() {
        hba1cOutputArea.clear();
        for (TextField field : hba1cInputs) field.clear();
        hba1cInputs[0].requestFocus();
    }

    private void saveHba1cToEMR() {
        String text = hba1cOutputArea.getText();
        if (text != null && !text.isBlank()) {
            IAIMain.getTextAreaManager().insertBlockIntoArea(IAITextAreaManager.AREA_O, text, true);
        }
    }

    private TitledPane createVitalsPane() {
        vsInputField = new TextField("Enter code (h/o/g/l/r/i/t36.5) or numbers (SBP→DBP→PR→BT→RR)");
        vsInputField.setOnAction(e -> {
            handleVitalsInput(vsInputField.getText().trim().toLowerCase());
            vsInputField.clear();
        });

        vsDescriptionArea = new TextArea(" at GDS : Regular pulse, Right Seated Position");
        vsDescriptionArea.setPrefRowCount(2);
        vsOutputArea = new TextArea();
        vsOutputArea.setPrefRowCount(5);

        HBox buttons = new HBox(8, createButton("Clear", e -> resetVitalsFields()), createButton("Save", e -> saveVitalsToEMR()));
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(6, new Label("Input:"), vsInputField, new Label("Description:"), vsDescriptionArea, new Label("Output:"), vsOutputArea, buttons);
        return new TitledPane("Vital Sign Tracker", box);
    }

    private void initializeVitalsValidInputs() {
        vsValidInputs = new HashSet<>(List.of("h", "o", "g", "l", "r", "i"));
    }

    private void handleVitalsInput(String input) {
        if (input.isEmpty()) return;

        if (vsValidInputs.contains(input)) {
            updateVitalsDescription(input);
        } else if (input.startsWith("t")) {
            handleVitalsTemperatureInput(input);
        } else {
            try {
                processVitalsNumeric(Double.parseDouble(input));
            } catch (NumberFormatException ex) {
                vsOutputArea.setText("Invalid input. Enter a code or a number.");
            }
        }
    }

    private void updateVitalsDescription(String code) {
        String current = vsDescriptionArea.getText();
        switch (code) {
            case "h" -> vsDescriptionArea.setText("   at home by self");
            case "o" -> vsDescriptionArea.setText("   at Other clinic");
            case "g" -> vsDescriptionArea.setText(" at GDS : Regular pulse, Right Seated Position");
            case "l" -> vsDescriptionArea.setText(current.replace("Right", "Left"));
            case "r" -> vsDescriptionArea.setText(current.replace("Left", "Right"));
            case "i" -> vsDescriptionArea.setText(current.replace("Regular", "Irregular"));
        }
    }

    private void handleVitalsTemperatureInput(String input) {
        try {
            double temp = Double.parseDouble(input.substring(1));
            vsDescriptionArea.setText(" at GDS : Forehead (Temporal Artery) Thermometer:");
            vsOutputArea.setText("Body Temperature [ " + temp + " ] ℃");
        } catch (RuntimeException ex) {
            vsOutputArea.setText("Invalid temperature format. Use 't' followed by a number (e.g., t36.5).");
        }
    }

    private void processVitalsNumeric(double value) {
        if (sbp == null) {
            sbp = (int) value;
            vsOutputArea.setText("\tSBP [" + sbp + "] mmHg");
        } else if (dbp == null) {
            dbp = (int) value;
            vsOutputArea.setText("BP [" + sbp + " / " + dbp + "] mmHg");
        } else if (pulseRate == null) {
            pulseRate = (int) value;
            vsOutputArea.appendText("   PR [" + pulseRate + "]/minute");
        } else if (bodyTemperature == null) {
            bodyTemperature = value;
            vsOutputArea.appendText("\n\tBody Temperature [" + bodyTemperature + "]℃");
        } else if (respirationRate == null) {
            respirationRate = (int) value;
            vsOutputArea.appendText("\n\tRespiration Rate [" + respirationRate + "]/minute");
            resetVitalsStagedValues();
        }
    }

    private void saveVitalsToEMR() {
        String description = vsDescriptionArea.getText();
        String output = vsOutputArea.getText();
        if (description.isBlank() && output.isBlank()) return;

        IAITextAreaManager manager = IAIMain.getTextAreaManager();
        manager.focusArea(IAITextAreaManager.AREA_O);
        if (!description.isBlank()) manager.insertLineIntoFocusedArea(description);
        if (!output.isBlank()) manager.insertLineIntoFocusedArea("\t" + output);
        resetVitalsFields();
    }

    private void resetVitalsFields() {
        vsInputField.clear();
        vsOutputArea.clear();
        vsDescriptionArea.setText(" at GDS : Regular pulse, Right Seated Position");
        resetVitalsStagedValues();
    }

    private void resetVitalsStagedValues() {
        sbp = null;
        dbp = null;
        pulseRate = null;
        bodyTemperature = null;
        respirationRate = null;
    }

    private HBox createBottomButtons() {
        Button quitButton = createButton("Quit All", e -> close());
        HBox box = new HBox(8, quitButton);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private void positionInTopRight() {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        setX(screenBounds.getMaxX() - getWidth() - 20);
        setY(screenBounds.getMinY() + 10);
    }

    private void checkBridgeReady() {
        IAIMain.getManagerSafely().ifPresentOrElse(
                manager -> { /* Ready */ },
                () -> showError("EMR connection not established. Please ensure the main application has initialized the IAIMain bridge.")
        );
    }

    private GridPane createFormGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.setPadding(new Insets(6));
        return grid;
    }

    private Button createButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(text);
        button.setOnAction(handler);
        return button;
    }

    private void showError(String message) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, message).showAndWait());
    }
}