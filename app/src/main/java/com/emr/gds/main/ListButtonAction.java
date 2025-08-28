package com.emr.gds.main;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.emr.gds.AbbdbControl;
import com.emr.gds.IttiaApp;

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
    // ---- Instance Variables ----
    private final IttiaApp app;
    private final Connection dbConn;
    private final Map<String, String> abbrevMap;

    // ---- Constructor ----
    // Modify the constructor
    public ListButtonAction(IttiaApp app, Connection dbConn, Map<String, String> abbrevMap) {
        this.app = app;
        this.dbConn = dbConn;
        this.abbrevMap = abbrevMap;
    }

    // ---- Public Methods ----
    public ToolBar buildTopBar() {
        Button btnInsertTemplate = new Button("Insert Template (Ctrl+I)");
        btnInsertTemplate.setOnAction(e -> app.insertTemplateIntoFocusedArea(TemplateLibrary.HPI));

        Button btnFormat = new Button("Auto Format (Ctrl+Shift+F)");
        btnFormat.setOnAction(e -> app.formatCurrentArea());

        Button btnCopyAll = new Button("Copy All (Ctrl+Shift+C)");
        btnCopyAll.setOnAction(e -> app.copyAllToClipboard());

        Button btnClearAll = new Button("CE");
        btnClearAll.setOnAction(e -> app.clearAllText());

        // START: ADD THIS CODE
        Button btnManageDb = new Button("Manage Abbrs...");
        btnManageDb.setOnAction(e -> {
            // Get the main window to act as the owner for the modal dialog
            Stage ownerStage = (Stage) btnManageDb.getScene().getWindow();
            AbbdbControl controller = new AbbdbControl(dbConn, abbrevMap, ownerStage, app);
            controller.showDbManagerDialog();
        });

        // Templates menu
        MenuButton templatesMenu = new MenuButton("Templates");
        for (TemplateLibrary t : TemplateLibrary.values()) {
            MenuItem mi = new MenuItem(t.displayName());
            mi.setOnAction(e -> app.insertTemplateIntoFocusedArea(t));
            templatesMenu.getItems().add(mi);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label hint = new Label("Focus area: Ctrl+1..Ctrl+0 | Double-click problem to insert");

        ToolBar tb = new ToolBar(
                templatesMenu,
                btnInsertTemplate,
                new Separator(),
                btnFormat,
                btnCopyAll,
                btnManageDb, // Add the new button here
                btnClearAll,
                spacer,
                hint
        );
        return tb;
    }

    public ToolBar buildBottomBar() {
        // 7 quick snippet buttons
        Button b1 = quickSnippetButton("Vitals", TemplateLibrary.SNIPPET_VITALS.body());
        Button b2 = quickSnippetButton("Meds", TemplateLibrary.SNIPPET_MEDS.body());
        Button b3 = quickSnippetButton("Allergy", TemplateLibrary.SNIPPET_ALLERGY.body());
        Button b4 = quickSnippetButton("Assessment", TemplateLibrary.SNIPPET_ASSESS.body());
        Button b5 = quickSnippetButton("Plan", TemplateLibrary.SNIPPET_PLAN.body());
        Button b6 = quickSnippetButton("F/U", TemplateLibrary.SNIPPET_FOLLOWUP.body());
        Button b7 = quickSnippetButton("Signature", TemplateLibrary.SNIPPET_SIGNATURE.body());

        ToolBar tb = new ToolBar(b1, b2, b3, b4, b5, b6, b7);
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
                "- Notes: \n"),
        A_P("Assessment & Plan",
                "# Assessment & Plan\n" +
                "- Dx: \n" +
                "- Severity: \n" +
                "- Plan: meds / labs / imaging / follow-up\n"),
        LETTER("Letter Template",
                "# Letter\n" +
                "Patient: \nDOB: \nDate: " + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + "\n\n" +
                "Findings:\n- \n\nPlan:\n- \n\nSignature:\nMigoJJ, MD\n"),
        LAB_SUMMARY("Lab Summary",
                "# Labs\n" +
                "- FBS:  mg/dL\n" +
                "- LDL:  mg/dL\n" +
                "- HbA1c:  %\n" +
                "- TSH:  uIU/mL\n"),
        PROBLEM_LIST("Problem List Header",
                "# Problem List\n- \n- \n- \n"),

        // Quick snippets (bottom bar)
        SNIPPET_VITALS("Vitals",
                "# Vitals\n- BP: / mmHg\n- HR: / min\n- Temp:  °C\n- RR: / min\n- SpO2:  %\n"),
        SNIPPET_MEDS("Meds",
                "# Medications\n- \n"),
        SNIPPET_ALLERGY("Allergy",
                "# Allergy\n- NKDA\n"),
        SNIPPET_ASSESS("Assessment",
                "# Assessment\n- \n"),
        SNIPPET_PLAN("Plan",
                "# Plan\n- \n"),
        SNIPPET_FOLLOWUP("Follow-up",
                "# Follow-up\n- Return in  weeks\n"),
        SNIPPET_SIGNATURE("Signature",
                "# Signature\nMigoJJ, MD\nEndocrinology\n");

        private final String display;
        private final String body;

        TemplateLibrary(String display, String body) {
            this.display = display;
            this.body = body;
        }

        public String displayName() {
            return display;
        }

        public String body() {
            return body;
        }
    }
}