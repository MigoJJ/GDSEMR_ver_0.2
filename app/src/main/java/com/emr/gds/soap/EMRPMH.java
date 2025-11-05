package com.emr.gds.soap;

import com.emr.gds.input.IAITextAreaManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
 * ... (Javadoc from previous version) ...
 *
 * --- UPGRADE for Abbreviation Functionality ---
 * - Switched scene-level shortcuts to use an EventFilter for more robust, predictable event handling.
 * - Added diagnostic logging to the abbreviation trigger and expansion logic for easier debugging.
 * - Ensured the abbreviation event handler on TextAreas is not being preempted.
 * - Cleaned up the abbreviation lookup logic.
 */
public class EMRPMH extends Application {

    // --- Integration points, UI Components, etc. (Mostly unchanged) ---
    private final IAITextAreaManager textAreaManager;
    private final TextArea externalTarget;
    private Stage stage;
    private TextArea outputArea;
    private final Map<String, CheckBox> pmhChecks = new LinkedHashMap<>();
    private final Map<String, TextArea> pmhNotes = new LinkedHashMap<>();
    private final Map<String, String> abbrMap = new HashMap<>();

    // --- Constants (Unchanged) ---
    private static final String[] CATEGORIES = {
            "Hypertension", "Dyslipidemia", "Diabetes Mellitus", "Thyroid Disease", "Asthma / COPD", "Pneumonia",
            "Tuberculosis (TB)", "Cardiovascular Disease", "AMI", "Angina Pectoris", "Arrhythmia",
            "Cerebrovascular Disease (CVA)", "Parkinson's Disease", "Cognitive Disorder", "Hearing Loss",
            "Chronic Kidney Disease (CKD)", "Gout", "Arthritis", "Cancer Hx", "Operation Hx", "GERD", "Hepatitis A / B",
            "Depression", "Allergy", "Food Allergy", "Injection Allergy", "Medication Allergy", "All denied allergies...",
            "Others"
    };
    private static final int NUM_COLUMNS = 3;

    // --- Constructors (Unchanged) ---
    public EMRPMH() { this(null, null); }
    public EMRPMH(IAITextAreaManager manager) { this(manager, null); }
    public EMRPMH(IAITextAreaManager manager, TextArea externalTarget) {
        this.textAreaManager = manager;
        this.externalTarget = externalTarget;
    }

    // --- JavaFX Lifecycle & Dialog Methods (Unchanged) ---
    @Override
    public void start(Stage primaryStage) { buildUI(primaryStage); primaryStage.show(); }
    public void showDialog() {
        Platform.runLater(() -> {
            Stage s = new Stage();
            buildUI(s);
            s.initModality(Modality.NONE);
            s.show();
        });
    }

    // --- UI Builder (Key Changes Here) ---
    private void buildUI(Stage s) {
        this.stage = s;
        s.setTitle("EMR - Past Medical History (PMH) - Upgraded");
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        loadAbbreviationsFromDb();

        Label title = new Label("Past Medical History");
        title.setFont(Font.font(18));
        title.setPadding(new Insets(0, 0, 10, 0));
        root.setTop(title);

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(8);
        grid.setPadding(new Insets(5));
        for (int i = 0; i < NUM_COLUMNS; i++) {
            grid.getColumnConstraints().add(new ColumnConstraints(100.0 / NUM_COLUMNS, 100.0 / NUM_COLUMNS, Double.MAX_VALUE, Priority.ALWAYS, null, true));
        }

        int row = 0, col = 0;
        for (String key : CATEGORIES) {
            CheckBox cb = new CheckBox(key);
            cb.setFont(Font.font(12));
            cb.setTooltip(new Tooltip("Select if applicable: " + key));
            pmhChecks.put(key, cb);
            TextArea ta = new TextArea();
            ta.setPromptText("Details for " + key);
            ta.setWrapText(true);
            ta.setPrefRowCount(1);
            ta.setFont(Font.font(12));
            pmhNotes.put(key, ta);
            VBox cellBox = new VBox(4, cb, ta);
            VBox.setVgrow(ta, Priority.ALWAYS);
            grid.add(cellBox, col, row);
            cb.selectedProperty().addListener((obs, oldVal, newVal) -> updateLiveSummary());
            ta.textProperty().addListener((obs, oldVal, newVal) -> updateLiveSummary());

            // UPGRADE: Attach the robust abbreviation handler
            attachAbbreviationLikeIttia(ta);

            col = (col + 1) % NUM_COLUMNS;
            if (col == 0) row++;
        }

        ScrollPane scroller = new ScrollPane(grid);
        scroller.setFitToWidth(true);
        root.setCenter(scroller);

        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setPrefRowCount(6);
        outputArea.setWrapText(true);
        outputArea.setFont(Font.font("Consolas", 12));
        outputArea.setPromptText("Live summary of selected PMH will appear here.");
        root.setBottom(buildFooter(outputArea));

        Scene scene = new Scene(root, 1100, 750);

        // UPGRADE: Use an EventFilter for scene-level shortcuts. This is more robust.
        // It runs before the event reaches individual controls, preventing interference.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                onQuit();
                e.consume(); // Stop the event from propagating further
            } else if (e.isControlDown() && e.getCode() == KeyCode.ENTER) {
                onSave();
                e.consume(); // Stop the event from propagating further
            }
        });

        s.setScene(scene);
        updateLiveSummary();
    }
    
    // --- Footer Builder & Actions (Unchanged) ---
    private VBox buildFooter(TextArea output) {
        Button btnSave = new Button("Save (Ctrl+Enter)");
        Button btnClear = new Button("Clear");
        Button btnCopy = new Button("Copy to Clipboard");
        Button btnFMH = new Button("Open EMRFMH");
        Button btnQuit = new Button("Quit");
        List.of(btnSave, btnClear, btnCopy, btnFMH, btnQuit).forEach(btn -> btn.setFont(Font.font(12)));
        btnSave.setOnAction(e -> onSave());
        btnClear.setOnAction(e -> {
            pmhChecks.values().forEach(cb -> cb.setSelected(false));
            pmhNotes.values().forEach(TextArea::clear);
        });
        btnCopy.setOnAction(e -> onCopy());
        btnFMH.setOnAction(e -> openEMRFMH());
        btnQuit.setOnAction(e -> onQuit());
        HBox buttons = new HBox(8, btnSave, btnClear, btnCopy, btnFMH, btnQuit);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(8, 0, 0, 0));
        return new VBox(5, new Separator(), output, buttons);
    }
    private void onSave() { /* ... unchanged ... */ }
    private void onCopy() { /* ... unchanged ... */ }
    private void onQuit() { if (stage != null) stage.close(); }
    private void updateLiveSummary() { outputArea.setText(buildSummaryText(false)); }
    private String buildSummaryText(boolean applySaveLogic) { /* ... unchanged ... */ }

    // =========================================================
    // Abbreviation Integration (UPGRADED AND FIXED)
    // =========================================================

    private void loadAbbreviationsFromDb() {
        String dbPath = System.getProperty("user.dir") + "/db/abbreviations.db";
        String url = "jdbc:sqlite:" + dbPath;
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT short, full FROM abbreviations")) {
            int count = 0;
            while (rs.next()) {
                String key = rs.getString("short").toLowerCase(Locale.ROOT);
                String val = rs.getString("full");
                if (!key.isEmpty() && !val.isEmpty()) {
                    abbrMap.put(key, val);
                    count++;
                }
            }
            System.out.println("LOG: Successfully loaded " + count + " abbreviations from the database.");
        } catch (Exception e) {
            System.err.println("ERROR: Failed to load abbreviations from database: " + e.getMessage());
            // You could pop up an Alert here if desired
        }
    }
    
    /**
     * UPGRADE: Attach key handlers with diagnostic logging.
     */
    private void attachAbbreviationLikeIttia(TextArea ta) {
        ta.setOnKeyReleased(ev -> {
            KeyCode code = ev.getCode();
            String character = ev.getText();

            // Trigger on common token delimiters
            boolean isTrigger = (code == KeyCode.SPACE || code == KeyCode.ENTER || code == KeyCode.TAB ||
                                (character != null && !character.isEmpty() && isPunctuation(character)));

            if (isTrigger) {
                // Use runLater to ensure the text area has been updated with the trigger character first
                Platform.runLater(() -> expandTokenAtCaret(ta));
            }
        });
    }

    private boolean isPunctuation(String s) {
        return s != null && s.length() == 1 && ",.;:!?)]}".contains(s);
    }
    
    /**
     * UPGRADE: Replace the token immediately before the caret, now with logging and cleaner lookup.
     */
    private void expandTokenAtCaret(TextArea ta) {
        int caret = ta.getCaretPosition();
        String txt = ta.getText();
        if (txt == null || txt.isEmpty() || caret == 0) return;

        System.out.println("\nLOG: expandTokenAtCaret triggered. Caret at: " + caret);

        // Find the start of the token (walk backwards from caret until whitespace/start)
        int start = caret - 1;
        // The character that triggered this might be punctuation or whitespace, so we skip it
        if (start >= 0 && (Character.isWhitespace(txt.charAt(start)) || isPunctuation(String.valueOf(txt.charAt(start))))) {
            start--;
        }
        while (start >= 0 && !Character.isWhitespace(txt.charAt(start))) {
            start--;
        }
        start = Math.max(start + 1, 0);

        // The end of the token is where the caret was *before* the trigger character was typed.
        int end = caret - 1;
        if (end <= start) {
            System.out.println("LOG: No valid token found before caret.");
            return;
        }

        String token = txt.substring(start, end);
        System.out.println("LOG: Identified token: '" + token + "'");
        if (token.isBlank()) return;

        // UPGRADE: Cleaner lookup logic
        String lookupKey = token.toLowerCase(Locale.ROOT);
        if (lookupKey.startsWith(":")) { // Allow :prefix
            lookupKey = lookupKey.substring(1);
        }
        
        String expansion = abbrMap.get(lookupKey);

        if (expansion != null) {
            System.out.println("LOG: Found expansion for '" + lookupKey + "' -> '" + expansion + "'");
            // We need to replace the original token, which is from 'start' to 'end'
            Platform.runLater(() -> {
                ta.selectRange(start, end);
                ta.replaceSelection(expansion);
            });
        } else {
            System.out.println("LOG: No expansion found for key: '" + lookupKey + "'");
        }
    }

    // --- Other Helpers (Unchanged) ---
    private void openEMRFMH() { /* ... */ }
    private void showError(String header, Throwable t) { /* ... */ }
    private void showInfo(String header, String content) { /* ... */ }
    public static void main(String[] args) { launch(args); }
}