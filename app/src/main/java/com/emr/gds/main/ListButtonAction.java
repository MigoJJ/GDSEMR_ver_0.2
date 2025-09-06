package com.emr.gds.main;

import java.sql.Connection;		
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import com.emr.gds.AbbdbControl;
import com.emr.gds.IttiaApp;
import com.emr.gds.input.TextAreaManager;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class ListButtonAction {
    // ---- Constants ----
    private static final String TEMPLATE_MENU_TEXT = "Templates";
    private static final String INSERT_TEMPLATE_BUTTON_TEXT = "Insert Template (Ctrl+I)";
    private static final String AUTO_FORMAT_BUTTON_TEXT = "Auto Format (Ctrl+Shift+F)";
    private static final String COPY_ALL_BUTTON_TEXT = "Copy All (Ctrl+Shift+C)";
    private static final String MANAGE_ABBREV_BUTTON_TEXT = "Manage Abbrs...";
    private static final String CLEAR_ALL_BUTTON_TEXT = "CE";
    private static final String HINT_LABEL_TEXT = "Focus area: Ctrl+1..Ctrl+0 | Double-click problem to insert";
    private static final int HPI_DEFAULT_FOCUS_AREA_INDEX = TextAreaManager.AREA_S; // Example: AREA_S (index 4) or AREA_O (index 5)
    
    // ---- Instance Variables ----
    private final IttiaApp app;
    private final Connection dbConn;
    private final Map<String, String> abbrevMap;

    // ---- Constructor ----
    public ListButtonAction(IttiaApp app, Connection dbConn, Map<String, String> abbrevMap) {
        this.app = app;
        this.dbConn = dbConn;
        this.abbrevMap = abbrevMap;
    }

    // ---- Public Methods ----
    public ToolBar buildTopBar() {
        // Templates menu
        MenuButton templatesMenu = new MenuButton(TEMPLATE_MENU_TEXT);
        templatesMenu.getItems().addAll(Arrays.stream(TemplateLibrary.values())
            // If you filtered snippets before, make sure this line is commented out or removed
            // .filter(t -> !t.isSnippet()) 
            .map(t -> {
                MenuItem mi = new MenuItem(t.displayName());
                mi.setOnAction(e -> app.insertTemplateIntoFocusedArea(t));
                return mi;
            }).collect(Collectors.toList()));
    	
    	Button btnInsertTemplate = new Button(INSERT_TEMPLATE_BUTTON_TEXT);
        btnInsertTemplate.setOnAction(e -> {
            app.getTextAreaManager().focusArea(HPI_DEFAULT_FOCUS_AREA_INDEX); // Focus a specific area
            app.insertTemplateIntoFocusedArea(TemplateLibrary.HPI); // Insert into whichever area currently has focus
        });

        Button btnFormat = new Button(AUTO_FORMAT_BUTTON_TEXT);
        btnFormat.setOnAction(e -> app.formatCurrentArea());

        Button btnCopyAll = new Button(COPY_ALL_BUTTON_TEXT);
        btnCopyAll.setOnAction(e -> app.copyAllToClipboard());

        Button btnManageDb = new Button(MANAGE_ABBREV_BUTTON_TEXT);
        btnManageDb.setOnAction(e -> {
            Stage ownerStage = (Stage) btnManageDb.getScene().getWindow();
            AbbdbControl controller = new AbbdbControl(dbConn, abbrevMap, ownerStage, app);
            controller.showDbManagerDialog();
        });

        Button btnClearAll = new Button(CLEAR_ALL_BUTTON_TEXT);
        btnClearAll.setOnAction(e -> app.clearAllText());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label hint = new Label(HINT_LABEL_TEXT);

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

    public ToolBar buildBottomBar() {
        ToolBar tb = new ToolBar(
            quickSnippetButton("Vitals", TemplateLibrary.SNIPPET_VITALS.body()),
            quickSnippetButton("Meds", TemplateLibrary.SNIPPET_MEDS.body()),
            quickSnippetButton("Allergy", TemplateLibrary.SNIPPET_ALLERGY.body()),
            quickSnippetButton("Assessment", TemplateLibrary.SNIPPET_ASSESS.body()),
            quickSnippetButton("Plan", TemplateLibrary.SNIPPET_PLAN.body()),
            quickSnippetButton("F/U", TemplateLibrary.SNIPPET_FOLLOWUP.body()),
            quickSnippetButton("Signature", TemplateLibrary.SNIPPET_SIGNATURE.body())
        );
        tb.setPadding(new Insets(8, 0, 0, 0));
        return tb;
    }

    // ---- Private Methods ----
    private Button quickSnippetButton(String title, String snippet) {
        Button b = new Button(title);
        b.setOnAction(e -> app.insertBlockIntoFocusedArea(snippet));
        return b;
    }

    // ---- Nested Classes ----
    // ===== Template library =====
    public enum TemplateLibrary {
        HPI("HPI",
                "# HPI\n" +
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
        // --- ADD THIS NEW ENTRY HERE ---
        VACCINATION_LIST("Vaccination",
                "# Tdap ...List\n- \n- \n- \n", false),
        TFT_LIST("TFT",
                "# T3 ...List\n- \n- \n- \n", false),


        // Quick snippets (bottom bar)
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
        private final boolean isSnippet; // Added to distinguish main templates from snippets

        TemplateLibrary(String display, String body, boolean isSnippet) {
            this.display = display;
            this.body = body;
            this.isSnippet = isSnippet;
        }

        public String displayName() {
            return display;
        }

        public String body() {
            return body;
        }

        public boolean isSnippet() {
            return isSnippet;
        }
    }
}