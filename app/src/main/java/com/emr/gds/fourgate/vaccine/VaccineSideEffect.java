package com.emr.gds.fourgate.vaccine;

import com.emr.gds.input.IAIMain;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A utility class for creating a modal dialog to record vaccine side effects.
 * This class is final and cannot be instantiated.
 */
public final class VaccineSideEffect {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static Stage stage; // Singleton instance of the window

    private VaccineSideEffect() {}

    /**
     * Opens the side effect entry window. If an instance already exists, it brings it to the front.
     */
    public static void open() {
        if (stage != null) {
            stage.toFront();
            stage.requestFocus();
            return;
        }

        stage = new Stage();
        stage.setTitle("Vaccine – Side Effects");

        // --- UI Components ---
        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField vaccineField = new TextField();
        vaccineField.setPromptText("Vaccine name (e.g., Shingrix #2/2)");
        ComboBox<String> severityComboBox = new ComboBox<>(FXCollections.observableArrayList("Mild", "Moderate", "Severe"));
        severityComboBox.setValue("Mild");
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Additional notes...");
        notesArea.setPrefRowCount(4);

        List<CheckBox> sideEffectCheckBoxes = List.of(
                new CheckBox("Fever/chills"), new CheckBox("Myalgia/arthralgia"),
                new CheckBox("Headache"), new CheckBox("Fatigue"),
                new CheckBox("Local pain/redness/swelling"), new CheckBox("Rash/urticaria"),
                new CheckBox("Syncope"), new CheckBox("Anaphylaxis (emergency)")
        );

        Button insertButton = new Button("Insert to EMR");
        insertButton.setDefaultButton(true);
        Button closeButton = new Button("Close");
        closeButton.setCancelButton(true);

        // --- Layout ---
        GridPane formGrid = createFormGrid(datePicker, vaccineField, severityComboBox);
        VBox effectsBox = createEffectsBox(sideEffectCheckBoxes);
        VaccineSelector vaccineSelector = new VaccineSelector().bindAppend(vaccineField);
        HBox buttonBox = new HBox(10, insertButton, closeButton);

        VBox root = new VBox(12,
                createTitledPane("Select Vaccine", vaccineSelector),
                createTitledPane("Details", formGrid),
                createTitledPane("Side Effects", effectsBox),
                createTitledPane("Notes", notesArea),
                buttonBox
        );
        root.setPadding(new Insets(12));

        // --- Event Handlers ---
        insertButton.setOnAction(e -> {
            String report = buildReport(datePicker.getValue(), vaccineField.getText(), severityComboBox.getValue(), sideEffectCheckBoxes, notesArea.getText());
            insertReportIntoEMR(report);
        });

        closeButton.setOnAction(e -> stage.close());
        stage.setOnCloseRequest(e -> stage = null);

        stage.setScene(new Scene(root, 560, 640));
        stage.show();
    }

    private static GridPane createFormGrid(DatePicker datePicker, TextField vaccineField, ComboBox<String> severityComboBox) {
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Date"), 0, 0);
        form.add(datePicker, 1, 0);
        form.add(new Label("Vaccine"), 0, 1);
        form.add(vaccineField, 1, 1);
        form.add(new Label("Severity"), 0, 2);
        form.add(severityComboBox, 1, 2);
        return form;
    }

    private static VBox createEffectsBox(List<CheckBox> checkBoxes) {
        VBox effects = new VBox(8);
        effects.getChildren().addAll(checkBoxes);
        effects.setPadding(new Insets(10));
        effects.setStyle("-fx-background-color: rgba(255, 250, 225, 0.6); -fx-background-radius: 8;");
        return effects;
    }

    private static String buildReport(LocalDate date, String vaccineName, String severity, List<CheckBox> checkBoxes, String notes) {
        String formattedDate = (date != null) ? date.format(DATE_FORMATTER) : LocalDate.now().format(DATE_FORMATTER);
        String vaccine = (vaccineName == null || vaccineName.isBlank()) ? "<unspecified vaccine>" : vaccineName.trim();

        String findings = checkBoxes.stream()
                .filter(CheckBox::isSelected)
                .map(CheckBox::getText)
                .collect(Collectors.joining(", "));

        if (findings.isEmpty()) {
            findings = "no significant adverse events reported";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n# Post-vaccination side effects [").append(formattedDate).append("]\n");
        sb.append("- Vaccine: ").append(vaccine).append("\n");
        sb.append("- Severity: ").append(severity).append("\n");
        sb.append("- Findings: ").append(findings);

        if (notes != null && !notes.isBlank()) {
            sb.append("\n- Note: ").append(notes.trim());
        }

        return sb.toString();
    }

    private static void insertReportIntoEMR(String report) {
        try {
            IAIMain.getTextAreaManager().focusArea(4); // Target 'S>' area
            IAIMain.getTextAreaManager().insertBlockIntoFocusedArea(report);
            if (stage != null) {
                stage.close();
                stage = null;
            }
        } catch (Exception ex) {
            showAlert("Failed to insert into EMR:\n" + ex.getMessage());
        }
    }

    private static TitledPane createTitledPane(String title, Node content) {
        TitledPane tp = new TitledPane(title, content);
        tp.setExpanded(true);
        return tp;
    }

    private static void showAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
            if (stage != null) {
                alert.initOwner(stage);
            }
            alert.setHeaderText(null);
            alert.setTitle("Info");
            alert.showAndWait();
        });
    }
}