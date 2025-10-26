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
 * JavaFX tool-window for quick vaccine logging.
 * <p>
 * • Bottom-right overlay style (JavaFX Stage).<br>
 * • “Side Effect” opens a JavaFX modal (VaccineSideEffect.open).<br>
 * • All other buttons insert a short note into the EMR text-areas.
 * </p>
 */
public class VaccineAction {

    /* --------------------------------------------------------------------- *
     * UI constants
     * --------------------------------------------------------------------- */
    private static final double FRAME_WIDTH = 500;
    private static final double FRAME_HEIGHT = 900;
    private static final String FRAME_TITLE = "Vaccinations";

    // JavaFX Fonts
    private static final Font LABEL_FONT = Font.font("Malgun Gothic", FontWeight.BOLD, 14);
    private static final Font BUTTON_FONT = Font.font("Malgun Gothic", FontWeight.NORMAL, 12);

    // JavaFX CSS Hex Colors
    private static final String HEADER_BG = "-fx-background-color: #DCE6F0;"; // 220, 230, 240
    private static final String VACCINE_BG = "-fx-background-color: #FFFFFF;";
    private static final String SIDEEFFECT_BG = "-fx-background-color: #FFFBE1;"; // 255, 250, 225
    private static final String QUIT_BG = "-fx-background-color: #FFE0E0;"; // 255, 224, 224

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");

    /* --------------------------------------------------------------------- *
     * State
     * --------------------------------------------------------------------- */
    private static Stage activeStage;

    /**
     * Entry point to open the Vaccine window.
     * Prevents multiple instances from being opened.
     */
    public static void open() {
        if (activeStage != null) {
            activeStage.toFront();
            return;
        }
        activeStage = createStage();
        activeStage.setOnHidden(e -> activeStage = null); // Allow re-opening
        positionBottomRight(activeStage);
        activeStage.show();
    }

    /**
     * Creates the JavaFX Stage and populates its content.
     */
    private static Stage createStage() {
        Stage stage = new Stage();
        stage.setTitle(FRAME_TITLE);
        stage.setWidth(FRAME_WIDTH);
        stage.setHeight(FRAME_HEIGHT);
        stage.initStyle(StageStyle.UTILITY); // A simple window style

        VBox root = new VBox(4); // 4px spacing, similar to Swing's vgap
        root.setStyle("-fx-background-color: #333333;"); // Dark Gray
        root.setPadding(new Insets(0));

        // Build UI from the external constants
        for (String txt : VaccineConstants.UI_ELEMENTS) {
            if (txt.startsWith("###")) {
                root.getChildren().add(createHeaderLabel(txt));
            } else {
                root.getChildren().add(createActionButton(txt));
            }
        }

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(scrollPane);
        stage.setScene(scene);
        return stage;
    }

    private static Label createHeaderLabel(String txt) {
        Label hdr = new Label(txt.replace("###", "").trim());
        hdr.setFont(LABEL_FONT);
        hdr.setStyle(HEADER_BG + "; -fx-padding: 6 0 6 0;");
        hdr.setAlignment(Pos.CENTER);
        hdr.setMaxWidth(Double.MAX_VALUE);
        return hdr;
    }

    private static Button createActionButton(String txt) {
        Button btn = new Button(txt);
        btn.setFont(BUTTON_FONT);
        btn.setFocusTraversable(false);
        btn.setMaxWidth(Double.MAX_VALUE);

        // Apply styles
        if ("Side Effect".equals(txt)) {
            btn.setStyle(SIDEEFFECT_BG);
        } else if ("Quit".equals(txt)) {
            btn.setStyle(QUIT_BG);
        } else {
            btn.setStyle(VACCINE_BG);
        }

        // Set the action
        btn.setOnAction(e -> handleButtonClick(txt));
        return btn;
    }

    /**
     * Positions the stage at the lower-right corner of the primary screen.
     */
    private static void positionBottomRight(Stage stage) {
        var screenBounds = Screen.getPrimary().getVisualBounds();
        double x = screenBounds.getMaxX() - stage.getWidth();
        double y = screenBounds.getMaxY() - stage.getHeight();
        stage.setX(x);
        stage.setY(y);
    }

    /**
     * Handles all button click events.
     */
    private static void handleButtonClick(String btnText) {
        if ("Quit".equals(btnText)) {
            if (activeStage != null) {
                activeStage.close();
            }
            return;
        }

        if ("Side Effect".equals(btnText)) {
            // Opens the side-effect entry modal
            VaccineSideEffect.open();
            return;
        }

        // -----------------------------------------------------------------
        // 1. Build the three note fragments
        // -----------------------------------------------------------------
        String today = DATE_FMT.format(new Date());

        String subjective = """
                The patient visits for Vaccination
                  [ ✔ ]  no allergy to eggs, chicken
                               , or any other component of the vaccine.
                  [ ✔ ]  no s/p Guillain-Barré syndrome.
                  [ ✔ ]  no adverse reactions to previous vaccines.
                  [ ✔ ]  no immunosuppression.
                """;

        String assessment = "\n #  " + btnText + "  [" + today + "]";
        String plan = "...Vaccination as scheduled";

        // -----------------------------------------------------------------
        // 2. Obtain the EMR text-area manager (once)
        // -----------------------------------------------------------------
        IAITextAreaManager mgr = IAIMain.getTextAreaManager();
        if (mgr == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("EMR Connection Error");
            alert.setContentText("EMR text-area manager is not available.");
            alert.showAndWait();
            return;
        }

        // -----------------------------------------------------------------
        // 3. Insert each fragment into its target area
        //    (Assumes we are already on the FX Thread)
        // -----------------------------------------------------------------

        // Subjective → area 1 (PI)
        mgr.focusArea(1);
        mgr.insertBlockIntoFocusedArea(subjective);

        // Assessment → area 7 (A)
        mgr.focusArea(7);
        mgr.insertLineIntoFocusedArea(assessment);

        // Plan → area 8 (P)
        mgr.focusArea(8);
        mgr.insertLineIntoFocusedArea(plan);
    }
}
