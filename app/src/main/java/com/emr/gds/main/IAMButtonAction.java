package com.emr.gds.main;

import com.emr.gds.IttiaApp;
import com.emr.gds.input.IAITextAreaManager;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

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
    private static final String INSERT_TEMPLATE_BUTTON_TEXT = "Insert Template (Ctrl+I)";
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
        Button btnInsertTemplate = new Button(INSERT_TEMPLATE_BUTTON_TEXT);
        btnInsertTemplate.setOnAction(e -> {
            app.getTextAreaManager().focusArea(HPI_DEFAULT_FOCUS_AREA_INDEX);
            app.insertTemplateIntoFocusedArea(TemplateLibrary.HPI);
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
            btnInsertTemplate,
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
        
        tb.setPadding(new Insets(8, 0, 0, 0));
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
            // This button has a custom action to call another program's main method.
            com.emr.gds.fourgate.vaccine.VaccineMain.main(new String[]{});
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
        HPI("New Patient",
            "# 공단 검진 HPI\n" +
            "- Onset: \n" +
            "- Location: \n" +
            "- Character: \n" +
            "- Aggravating/Relieving: \n" +
            "- Associated Sx: \n" +
            "- Context: \n" +
            "- Notes: \n", false),
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