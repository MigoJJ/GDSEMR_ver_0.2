package com.emr.gds.fourgate.vaccine;

import com.emr.gds.input.IAIMain;
import com.emr.gds.input.IAITextAreaManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A JavaFX tool window for quickly logging vaccine administrations.
 * This window appears as a bottom-right overlay and provides buttons for common vaccines.
 */
public class VaccineAction {

    // UI Constants
    private static final double FRAME_WIDTH = 500;
    private static final double FRAME_HEIGHT = 900;
    private static final String FRAME_TITLE = "Vaccinations";
    private static final Font LABEL_FONT = Font.font("Malgun Gothic", FontWeight.BOLD, 14);
    private static final Font BUTTON_FONT = Font.font("Malgun Gothic", FontWeight.NORMAL, 12);
    private static final String HEADER_STYLE = "-fx-background-color: #DCE6F0; -fx-padding: 6 0 6 0;";
    private static final String VACCINE_BUTTON_STYLE = "-fx-background-color: #FFFFFF;";
    private static final String SIDEEFFECT_BUTTON_STYLE = "-fx-background-color: #FFFBE1;";
    private static final String QUIT_BUTTON_STYLE = "-fx-background-color: #FFE0E0;";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private static Stage activeStage;

    /**
     * Opens the vaccine logging window. If the window is already open, it brings it to the front.
     */
    public static void open() {
        if (activeStage != null) {
            activeStage.toFront();
            return;
        }
        activeStage = createStage();
        activeStage.setOnHidden(e -> activeStage = null); // Allow re-opening after being closed
        positionStageAtBottomRight(activeStage);
        activeStage.show();
    }

    /**
     * Creates and configures the main JavaFX Stage for the tool window.
     */
    private static Stage createStage() {
        Stage stage = new Stage();
        stage.setTitle(FRAME_TITLE);
        stage.setWidth(FRAME_WIDTH);
        stage.setHeight(FRAME_HEIGHT);
        stage.initStyle(StageStyle.UTILITY);

        VBox root = new VBox(4);
        root.setStyle("-fx-background-color: #333333;");
        root.setPadding(Insets.EMPTY);

        // Build UI from constants
        for (String text : VaccineConstants.UI_ELEMENTS) {
            if (text.startsWith("###")) {
                root.getChildren().add(createHeaderLabel(text));
            } else {
                root.getChildren().add(createActionButton(text));
            }
        }

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        stage.setScene(new Scene(scrollPane));
        return stage;
    }

    private static Label createHeaderLabel(String text) {
        Label header = new Label(text.replace("###", "").trim());
        header.setFont(LABEL_FONT);
        header.setStyle(HEADER_STYLE);
        header.setAlignment(Pos.CENTER);
        header.setMaxWidth(Double.MAX_VALUE);
        return header;
    }

    private static Button createActionButton(String text) {
        Button button = new Button(text);
        button.setFont(BUTTON_FONT);
        button.setFocusTraversable(false);
        button.setMaxWidth(Double.MAX_VALUE);

        // Apply styles based on button type
        switch (text) {
            case "Side Effect" -> button.setStyle(SIDEEFFECT_BUTTON_STYLE);
            case "Quit" -> button.setStyle(QUIT_BUTTON_STYLE);
            default -> button.setStyle(VACCINE_BUTTON_STYLE);
        }

        button.setOnAction(e -> handleButtonClick(text));
        return button;
    }

    /**
     * Positions the stage at the lower-right corner of the primary screen.
     */
    private static void positionStageAtBottomRight(Stage stage) {
        var screenBounds = Screen.getPrimary().getVisualBounds();
        stage.setX(screenBounds.getMaxX() - stage.getWidth());
        stage.setY(screenBounds.getMaxY() - stage.getHeight());
    }

    /**
     * Central handler for all button click events.
     */
    private static void handleButtonClick(String buttonText) {
        switch (buttonText) {
            case "Quit":
                if (activeStage != null) activeStage.close();
                break;
            case "Side Effect":
                VaccineSideEffect.open();
                break;
            default:
                insertVaccineRecord(buttonText);
                break;
        }
    }

    /**
     * Inserts a standardized vaccine record into the EMR text areas.
     * @param vaccineName The name of the vaccine from the button text.
     */
    private static void insertVaccineRecord(String vaccineName) {
        IAITextAreaManager emrManager = IAIMain.getTextAreaManager();
        if (emrManager == null) {
            showError("EMR Connection Error", "EMR text-area manager is not available.");
            return;
        }

        String today = DATE_FORMAT.format(new Date());
        String subjectiveNote = """
                The patient visits for Vaccination
                  [ ✔ ]  no allergy to eggs, chicken, or any other component of the vaccine.
                  [ ✔ ]  no s/p Guillain-Barré syndrome.
                  [ ✔ ]  no adverse reactions to previous vaccines.
                  [ ✔ ]  no immunosuppression.
                """;
        String assessmentNote = "\n #  " + vaccineName + "  [" + today + "]";
        String planNote = "...Vaccination as scheduled";

        // Insert fragments into their respective EMR areas
        emrManager.focusArea(1); // PI (Present Illness)
        emrManager.insertBlockIntoFocusedArea(subjectiveNote);

        emrManager.focusArea(7); // A (Assessment)
        emrManager.insertLineIntoFocusedArea(assessmentNote);

        emrManager.focusArea(8); // P (Plan)
        emrManager.insertLineIntoFocusedArea(planNote);
    }

    private static void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}