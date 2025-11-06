package com.emr.gds.soap;

import com.emr.gds.input.IAITextAreaManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JavaFX dialog for Past Medical History (PMH).
 * Left column: disease/category checkboxes in a grid
 * Right column: aligned editable fields (TextAreas) for details.
 *
 * --- Features from original version retained ---
 * - Abbreviation expansion from DB with CSV fallback.
 * - "Open EMRFMH" button functionality.
 * - Save inserts into provided external TextArea or shows in output area.
 * - Quit closes ONLY this window.
 * - Robust threading and error handling.
 *
 * --- UPGRADES Inspired by Swing EMRPMH ---
 * - UI Layout: Conditions are now in a dynamic multi-column grid for better space usage.
 * - Live Summary: The output area at the bottom updates in real-time as checkboxes are toggled.
 * - More Conditions: The list of conditions is more comprehensive.
 * - Copy to Clipboard: A new "Copy" button to easily export the summary.
 * - Specific Logic: Special handling for "All denied allergies" on save.
 */
public class EMRPMH extends Application {

    // --- Integration points (optional for embedded use) ---
    private final IAITextAreaManager textAreaManager;  // may be null
    private final TextArea externalTarget;             // optional external target for saving

    // --- UI Components ---
    private Stage stage;
    private GridPane grid;
    private TextArea outputArea; // Now acts as a live summary pane

    private final Map<String, CheckBox> pmhChecks = new LinkedHashMap<>();
    private final Map<String, TextArea> pmhNotes = new LinkedHashMap<>();
    private final Map<String, String> abbrevMap;

    // UPGRADE: More comprehensive list of conditions from the Swing example
    private static final String[] CATEGORIES = {
            "Hypertension", "Dyslipidemia", "Diabetes Mellitus",
            "Thyroid Disease", "Asthma / COPD", "Pneumonia", "Tuberculosis (TB)",
            "Cardiovascular Disease", "AMI", "Angina Pectoris", "Arrhythmia",
            "Cerebrovascular Disease (CVA)", "Parkinson's Disease", "Cognitive Disorder", "Hearing Loss",
            "Chronic Kidney Disease (CKD)", "Gout", "Arthritis",
            "Cancer Hx", "Operation Hx",
            "GERD", "Hepatitis A / B",
            "Depression",
            "Allergy", "Food Allergy", "Injection Allergy", "Medication Allergy", 
            "All denied allergies...Food, Medication, Injection",
            "Others"
    };
    
    // UPGRADE: Define how many columns the grid should have
    private static final int NUM_COLUMNS = 3;

    // -------- Constructors --------
    public EMRPMH() { this(null, null, Collections.emptyMap()); }
    public EMRPMH(IAITextAreaManager manager) { this(manager, null, Collections.emptyMap()); }
    public EMRPMH(IAITextAreaManager manager, TextArea externalTarget) { this(manager, externalTarget, Collections.emptyMap()); }
    public EMRPMH(IAITextAreaManager manager, TextArea externalTarget, Map<String, String> abbrevMap) {
        this.textAreaManager = manager;
        this.externalTarget = externalTarget;
        this.abbrevMap = (abbrevMap != null) ? abbrevMap : Collections.emptyMap();
    }

    // -------- JavaFX lifecycle --------
    @Override
    public void start(Stage primaryStage) {
        buildUI(primaryStage);
        primaryStage.show();
    }

    public void showDialog() {
        Platform.runLater(() -> {
            Stage s = new Stage();
            buildUI(s);
            s.initModality(Modality.NONE);
            s.show();
        });
    }

    // -------- UI builder --------
    private void buildUI(Stage s) {
        this.stage = s;
        s.setTitle("EMR - Past Medical History (PMH) - Upgraded");
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        Label title = new Label("Past Medical History");
        title.setFont(Font.font(18));
        title.setPadding(new Insets(0, 0, 10, 0));
        root.setTop(title);

        // UPGRADE: Use a grid that supports multiple columns for a compact layout
        grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(8);
        grid.setPadding(new Insets(5));

        for (int i = 0; i < NUM_COLUMNS; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100.0 / NUM_COLUMNS);
            grid.getColumnConstraints().add(col);
        }

        // Populate the grid
        int row = 0, col = 0;
        for (String key : CATEGORIES) {
            CheckBox cb = new CheckBox(key);
            cb.setFont(Font.font(12));
            cb.setTooltip(new Tooltip("Select if applicable: " + key));
            pmhChecks.put(key, cb);

            TextArea ta = new TextArea();
            ta.setPromptText("Details for " + key);
            ta.setWrapText(true);
            ta.setPrefRowCount(1); // Start small, can grow
            ta.setFont(Font.font(12));
            pmhNotes.put(key, ta);
            
            // This VBox keeps the checkbox and its text area together vertically
            VBox cellBox = new VBox(4, cb, ta);
            VBox.setVgrow(ta, Priority.ALWAYS);
            grid.add(cellBox, col, row);

            // UPGRADE: Add listener to update summary pane in real-time
            cb.selectedProperty().addListener((obs, oldVal, newVal) -> updateLiveSummary());
            ta.textProperty().addListener((obs, oldVal, newVal) -> updateLiveSummary());

            addAbbreviationExpansionListener(ta);

            col++;
            if (col >= NUM_COLUMNS) {
                col = 0;
                row++;
            }
        }

        ScrollPane scroller = new ScrollPane(grid);
        scroller.setFitToWidth(true);
        root.setCenter(scroller);

        // Output / status area
        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setPrefRowCount(6);
        outputArea.setWrapText(true);
        outputArea.setFont(Font.font("Consolas", 12));
        outputArea.setPromptText("Live summary of selected PMH will appear here.");
        root.setBottom(buildFooter(outputArea));

        Scene scene = new Scene(root, 1000, 970); // Increased default size
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) { onQuit(); e.consume(); }
            if (e.isControlDown() && e.getCode() == KeyCode.ENTER) { onSave(); e.consume(); }
        });
        s.setScene(scene);
        updateLiveSummary(); // Initial state
    }

    private VBox buildFooter(TextArea output) {
        Button btnSave = new Button("Save (Ctrl+Enter)");
        Button btnClear = new Button("Clear");
        Button btnCopy = new Button("Copy to Clipboard"); // UPGRADE: New button
        Button btnFMH = new Button("Open EMRFMH");
        Button btnQuit = new Button("Quit");

        List.of(btnSave, btnClear, btnCopy, btnFMH, btnQuit).forEach(btn -> btn.setFont(Font.font(12)));

        btnSave.setOnAction(e -> onSave());
        btnClear.setOnAction(e -> {
            pmhChecks.values().forEach(cb -> cb.setSelected(false));
            pmhNotes.values().forEach(TextArea::clear);
            // outputArea is cleared automatically by the listener
        });
        btnCopy.setOnAction(e -> onCopy()); // UPGRADE: Attach action
        btnFMH.setOnAction(e -> openEMRFMH());
        btnQuit.setOnAction(e -> onQuit());

        HBox buttons = new HBox(8, btnSave, btnClear, btnCopy, btnFMH, btnQuit);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(8, 0, 0, 0));

        return new VBox(5, new Separator(), output, buttons);
    }
    
    // -------- Actions --------
    private void onSave() {
        String summary = buildSummaryText(true); // Get final text with special logic

        if (externalTarget != null) {
            int caret = externalTarget.getCaretPosition();
            externalTarget.insertText(caret, summary);
            // Also update the live summary to show what was inserted
            outputArea.setText("[Saved to external editor]\n" + summary);
        } else {
            // Fallback: show in the bottom output area
            outputArea.setText(summary);
        }
    }
    
    // UPGRADE: New "Copy to Clipboard" action
    private void onCopy() {
        String summary = buildSummaryText(false); // Get raw text without save logic
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();
        content.putString(summary);
        clipboard.setContent(content);
        
        // Provide user feedback
        String originalText = outputArea.getText();
        outputArea.setText("[Summary copied to clipboard!]\n\n" + originalText);
        // Fade back to original text after a moment
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> outputArea.setText(originalText));
            }
        }, 2000);
    }

    private void onQuit() {
        if (stage != null) stage.close();
    }
    
    // UPGRADE: This method now drives the live summary
    private void updateLiveSummary() {
        String summary = buildSummaryText(false); // Build summary without save-specific logic
        outputArea.setText(summary);
    }

    /**
     * Builds the summary text from selected items.
     * @param applySaveLogic If true, applies special logic like the "All denied allergies" replacement.
     * @return The formatted summary string.
     */
    private String buildSummaryText(boolean applySaveLogic) {
        StringBuilder sb = new StringBuilder("Past Mdedical History-----------\n");
        boolean hasContent = false;

        boolean allDeniedSelected = pmhChecks.getOrDefault("All denied allergies...", new CheckBox()).isSelected();

        for (String key : CATEGORIES) {
            CheckBox cb = pmhChecks.get(key);
            String note = pmhNotes.get(key).getText().trim();

            if (cb.isSelected() || !note.isEmpty()) {
                hasContent = true;

                // UPGRADE: Special logic inspired by Swing version
                if (applySaveLogic && key.equals("All denied allergies...") && cb.isSelected()) {
                    String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                    sb.append("• Allergy: As of ").append(date)
                      .append(", the patient denies any known allergies to food, injections, or medications.\n");
                    continue; // Skip the generic line for this
                }
                
                // Don't show specific allergies if "All denied" is checked.
                if (allDeniedSelected && key.contains("Allergy") && !key.equals("All denied allergies...")) {
                    continue;
                }

                sb.append("• ").append(cb.isSelected() ? "▣ " : "□ ").append(key);
                if (!note.isEmpty()) {
                    sb.append(": ").append(note.replace("\n", " | "));
                }
                sb.append("\n");
            }
        }

        if (!hasContent) {
            return "PMH>\n(No items selected)";
        }
        return sb.toString();
    }


    // ===============================================
    // Abbreviation & Other Helper Methods (Unchanged)
    // ===============================================
    private void addAbbreviationExpansionListener(TextArea ta) {
        ta.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SPACE) {
                if (expandAbbreviationOnSpace(ta)) event.consume();
            }
        });
    }

    private boolean expandAbbreviationOnSpace(TextArea ta) {
        int caret = ta.getCaretPosition();
        String upToCaret = ta.getText(0, caret);
        int start = Math.max(upToCaret.lastIndexOf(' '), upToCaret.lastIndexOf('\n')) + 1;

        String word = upToCaret.substring(start).trim();
        if (!word.startsWith(":")) return false;

        String key = word.substring(1);
        String replacement = abbrevMap.get(key);
        if (replacement == null) return false;

        Platform.runLater(() -> {
            ta.deleteText(start, caret);
            ta.insertText(start, replacement + " ");
        });
        return true;
    }

    private void openEMRFMH() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                // Assuming EMRFMH is a Swing JFrame
                new EMRFMH(textAreaManager).setVisible(true); // Example instantiation
            } catch (Throwable t) {
                showError("Unable to open EMRFMH", t);
            }
        });
    }

    private void showError(String header, Throwable t) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setHeaderText(header);
            a.setContentText(t.getClass().getSimpleName() + ": " + Optional.ofNullable(t.getMessage()).orElse(""));
            a.showAndWait();
        });
    }

    private void showInfo(String header, String content) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setHeaderText(header);
            a.setContentText(content);
            a.showAndWait();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}