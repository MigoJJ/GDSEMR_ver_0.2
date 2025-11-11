package com.emr.gds.main;

import com.emr.gds.IttiaApp;
import com.emr.gds.diagnosis.medication.MedsMain;
import com.emr.gds.diagnosis.thyroid.Thyroid;
import com.emr.gds.input.IAITextAreaManager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality; // Import Modality
import javafx.stage.Stage;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

// Import for KCDDatabaseManagerJavaFX
import com.emr.gds.fourgate.KCDdatabase.KCDDatabaseManagerJavaFX;

/**
 * Manages the creation and actions for the top and bottom toolbars of the application.
 * This class handles UI controls like buttons and menus for template insertion, text formatting,
 * and other core application functionalities.
 */
public class IAMButtonAction {

    //================================================================================
    // Constants
    //================================================================================

    private static final String TEMPLATE_MENU_TEXT = "Templates";
    private static final String INSERT_DATE_BUTTON_TEXT = "Date (Ctrl+I)";
    private static final String AUTO_FORMAT_BUTTON_TEXT = "Auto Format (Ctrl+Shift+F)";
    private static final String COPY_ALL_BUTTON_TEXT = "Copy All (Ctrl+Shift+C)";
    private static final String MANAGE_ABBREV_BUTTON_TEXT = "Manage Abbrs...";
    private static final String CLEAR_ALL_BUTTON_TEXT = "CE";
    private static final String HINT_LABEL_TEXT = "Focus area: Ctrl+1..Ctrl+0 | Double-click problem to insert";

    // Default text area to focus when inserting the main HPI template.
    private static final int HPI_DEFAULT_FOCUS_AREA_INDEX = IAITextAreaManager.AREA_S;

    //================================================================================
    // Instance Variables
    //================================================================================

    private final IttiaApp app;
    private final Connection dbConn;
    private final Map<String, String> abbrevMap;

    // --- KCD Database Manager Fields ---
    private KCDDatabaseManagerJavaFX kcdDatabaseManager;
    private Stage kcdStage; // Field to hold the KCD manager's stage reference
    // -----------------------------------

    //================================================================================
    // Constructor
    //================================================================================

    public IAMButtonAction(IttiaApp app, Connection dbConn, Map<String, String> abbrevMap) {
        this.app = app;
        this.dbConn = dbConn;
        this.abbrevMap = abbrevMap;
    }

    //================================================================================
    // Public Methods (Toolbar Builders)
    //================================================================================

    /**
     * Constructs and returns the top toolbar with main actions.
     * @return The configured ToolBar for the top of the UI.
     */
    public ToolBar buildTopBar() {
        // 1. Templates Menu
        MenuButton templatesMenu = new MenuButton(TEMPLATE_MENU_TEXT);
        templatesMenu.getItems().addAll(
            Arrays.stream(TemplateLibrary.values())
                  .filter(t -> !t.isSnippet()) // Filter for main templates only
                  .map(this::createTemplateMenuItem)
                  .collect(Collectors.toList())
        );

        // 2. Individual Buttons
        Button btnInsertDate = new Button(INSERT_DATE_BUTTON_TEXT);
        btnInsertDate.setOnAction(e -> {
            String currentDateString = " [ " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " ]";
            app.insertLineIntoFocusedArea(currentDateString);
        });

        Button btnFormat = new Button(AUTO_FORMAT_BUTTON_TEXT);
        btnFormat.setOnAction(e -> app.formatCurrentArea());

        Button btnCopyAll = new Button(COPY_ALL_BUTTON_TEXT);
        btnCopyAll.setOnAction(e -> app.copyAllToClipboard());

        Button btnManageDb = new Button(MANAGE_ABBREV_BUTTON_TEXT);
        btnManageDb.setOnAction(e -> showAbbreviationManagerDialog(btnManageDb));

        Button btnClearAll = new Button(CLEAR_ALL_BUTTON_TEXT);
        btnClearAll.setOnAction(e -> app.clearAllText());

        // 3. Layout Helpers
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label hint = new Label(HINT_LABEL_TEXT);

        // 4. Assemble Toolbar
        return new ToolBar(
            templatesMenu,
            btnInsertDate,
            new Separator(),
            btnFormat,
            btnCopyAll,
            btnManageDb,
            btnClearAll,
            spacer,
            hint
        );
    }

    /**
     * Constructs and returns the bottom toolbar with quick-insert snippets.
     * @return The configured ToolBar for the bottom of the UI.
     */
    public ToolBar buildBottomBar() {
        ToolBar tb = new ToolBar();

        // Dynamically create buttons from all "snippet" templates
        tb.getItems().addAll(
            Arrays.stream(TemplateLibrary.values())
                  .filter(TemplateLibrary::isSnippet) // Filter for snippets only
                  .map(t -> createSnippetButton(t.displayName(), t.body()))
                  .collect(Collectors.toList())
        );

        // Add any special-purpose buttons that don't come from the template library
        tb.getItems().add(createVaccineButton("Vaccine"));
        tb.getItems().add(createKCD9Button("KCD-9")); // KCD-9 button creation
        tb.getItems().add(createThyroidButton("Thyroid"));
        tb.getItems().add(createMedsMainButton("MedsMain"));
        tb.setPadding(new Insets(9, 0, 0, 0));
        return tb;
    }

    //================================================================================
    // Private Helper Methods
    //================================================================================

    /**
     * Creates a MenuItem for a given template.
     */
    private MenuItem createTemplateMenuItem(TemplateLibrary template) {
        MenuItem mi = new MenuItem(template.displayName());
        mi.setOnAction(e -> app.insertTemplateIntoFocusedArea(template));
        return mi;
    }

    /**
     * Creates a Button that inserts a snippet of text into the focused text area.
     */
    private Button createSnippetButton(String title, String snippet) {
        Button b = new Button(title);
        b.setOnAction(e -> app.insertBlockIntoFocusedArea(snippet));
        return b;
    }

    /**
     * Creates a special-purpose button to launch the Vaccine management tool.
     */
    private Button createVaccineButton(String title) {
        Button b = new Button(title);
        b.setOnAction(e -> {
            try {
            	com.emr.gds.fourgate.vaccine.VaccineAction.open();
            } catch (Exception ex) {
                System.err.println("Failed to launch Vaccine application: " + ex.getMessage());
            }
        });
        return b;
    }

    /**
     * Creates a special-purpose button to launch the KCD-9 Database Manager.
     * This method ensures the KCD window can be re-opened after being closed.
     */
    private Button createKCD9Button(String title) {
        Button b = new Button(title);
        b.setOnAction(e -> {
            try {
                // Check if the manager or its stage is null, or if the stage has been closed
                if (kcdDatabaseManager == null || kcdStage == null || !kcdStage.isShowing()) {
                    // If it's the first time, or the previous stage was truly closed
                    kcdDatabaseManager = new KCDDatabaseManagerJavaFX();
                    kcdStage = new Stage(); // Create a NEW Stage
                    kcdStage.setTitle("KCD Database Manager"); // Set title explicitly
                    kcdStage.initModality(Modality.NONE); // Adjust modality as needed (e.g., Modality.APPLICATION_MODAL)
                    // kcdStage.initOwner(app.getPrimaryStage()); // Uncomment if you want it owned by your main application stage

                    kcdDatabaseManager.start(kcdStage); // Start the manager with the new stage
                    kcdStage.show();

                    // Optional: Handle OS close button (the 'X') to clear references
                    kcdStage.setOnCloseRequest(event -> {
                        // Perform any cleanup for the manager if necessary before clearing references
                        kcdDatabaseManager = null; // Clear the reference to allow garbage collection
                        kcdStage = null; // Clear the stage reference
                    });

                } else {
                    // If the manager exists and its stage is still alive (open or hidden),
                    // ensure it's visible and bring it to the front.
                    kcdStage.show(); // Ensure it's visible (e.g., if it was hidden via OS button minimize)
                    kcdStage.toFront(); // Bring to front if already open
                }
            } catch (Exception ex) {
                System.err.println("Failed to launch KCD-9 application:");
                ex.printStackTrace();
            }
        });
        return b;
    }

    private Button createThyroidButton(String title) {
        Button b = new Button(title);
        b.setOnAction(e -> {
            // It's essential to run UI updates on the JavaFX Application Thread.
            Platform.runLater(() -> {
                try {
                    // 1. Create a new Stage (window) for the Thyroid GDS
                    Stage thyroidStage = new Stage();
                    thyroidStage.setTitle("Thyroid Disorders GDS");

                    // 2. Create an instance of Thyroid to get its UI content
                    // The Thyroid class itself isn't the UI node; it's a factory for the UI.
                    Thyroid thyroidApp = new Thyroid();
                    VBox thyroidRoot = thyroidApp.createThyroidUI(); // Get the VBox containing all categories

                    // 3. Wrap the UI content in a ScrollPane to handle potential overflow
                    ScrollPane scrollPane = new ScrollPane(thyroidRoot);
                    scrollPane.setFitToWidth(true); // Allow content to expand to the width
                    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); // Show scrollbar if needed

                    // 4. Create a Scene for the new Stage
                    Scene scene = new Scene(scrollPane, 800, 600); // Set preferred initial window size

                    // 5. Set the scene to the stage and show the new window
                    thyroidStage.setScene(scene);
                    thyroidStage.show();

                } catch (Exception ex) {
                    System.err.println("Failed to open Thyroid application: " + ex.getMessage());
                    ex.printStackTrace(); // Print full stack trace for debugging

                    // Optionally, show an alert to the user
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Error");
                    errorAlert.setHeaderText("Failed to open Thyroid GDS");
                    errorAlert.setContentText("An unexpected error occurred while trying to launch the Thyroid Diagnosis System.\n" +
                                            "Details: " + ex.getMessage());
                    errorAlert.showAndWait();
                }
            });
        });
        return b;
    }

    private Button createMedsMainButton(String title) {
        Button b = new Button(title);
        b.setOnAction(e -> {
            Platform.runLater(() -> {
                try {
                    MedsMain medsApp = new MedsMain();
                    medsApp.init(); // We need to call this manually
                    Stage medsStage = new Stage();
                    medsApp.start(medsStage); // This will set up and show the window
                } catch (Exception ex) {
                    System.err.println("Failed to open MedsMain application: " + ex.getMessage());
                    ex.printStackTrace();
                }
            });
        });
        return b;
    }
    
    /**
     * Opens the abbreviation manager dialog.
     */
    private void showAbbreviationManagerDialog(Control ownerControl) {
        Stage ownerStage = (Stage) ownerControl.getScene().getWindow();
        IAMAbbdbControl controller = new IAMAbbdbControl(dbConn, abbrevMap, ownerStage, app);
        controller.showDbManagerDialog();
    }

    //================================================================================
    // Nested Enum: TemplateLibrary
    //================================================================================

    /**
     * Defines a collection of reusable text templates and snippets.
     * Each entry has a display name, body content, and a flag to distinguish
     * between full templates (for the top menu) and short snippets (for the bottom bar).
     */
    public enum TemplateLibrary {
        // --- Full Templates (isSnippet = false) ---
        HPI("DM  F/U checking",
            "# DM  F/U check List\n" +
            "   - Retinopathy : no NPDR [  ]\n" +
            "   - Peripheral neuropathy : denied [ :cd ]\n" +
            "   - Nephrolathy : CKD A  G  [  ] : \n" +
            "   - Automonic neuropathy : denied [ :cd ] \n", false),
        A_P("Assessment & Plan",
            "# Assessment & Plan\n" +
            "- Dx: \n" +
            "- Severity: \n" +
            "- Plan: meds / labs / imaging / follow-up\n", false),
        LETTER("Letter Template",
            "# Letter\n" +
            "Patient: \nDOB: \nDate: " + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + "\n\n" +
            "Findings:\n- \n\nPlan:\n- \n\nSignature:\nMigoJJ, MD\n", false),
        LAB_SUMMARY("Lab Summary",
            "# Labs\n" +
            "- FBS:  mg/dL\n" +
            "- LDL:  mg/dL\n" +
            "- HbA1c:  %\n" +
            "- TSH:  uIU/mL\n", false),
        PROBLEM_LIST("Problem List Header",
            "# Problem List\n- \n- \n- \n", false),
        VACCINATION_LIST("Vaccination",
            "# Tdap ...List\n- \n- \n- \n", false),
        TFT_LIST("TFT",
            "# T3 ...List\n- \n- \n- \n", false),

        // --- Quick Snippets (isSnippet = true) ---
        SNIPPET_VITALS("Vitals",
            "# Vitals\n- BP: / mmHg\n- HR: / min\n- Temp:  °C\n- RR: / min\n- SpO2:  %\n", true),
        SNIPPET_MEDS("Meds",
            "# Medications\n- \n", true),
        SNIPPET_ALLERGY("Allergy",
            "# Allergy\n- NKDA\n", true),
        SNIPPET_ASSESS("Assessment",
            "# Assessment\n- \n", true),
        SNIPPET_PLAN("Plan",
            "# Plan\n- \n", true),
        SNIPPET_FOLLOWUP("Follow-up",
            "# Follow-up\n- Return in  weeks\n", true),
        SNIPPET_SIGNATURE("Signature",
            "# Signature\nMigoJJ, MD\nEndocrinology\n", true);

        private final String display;
        private final String body;
        private final boolean isSnippet;

        TemplateLibrary(String display, String body, boolean isSnippet) {
            this.display = display;
            this.body = body;
            this.isSnippet = isSnippet;
        }

        public String displayName() { return display; }
        public String body() { return body; }
        public boolean isSnippet() { return isSnippet; }
    }
}