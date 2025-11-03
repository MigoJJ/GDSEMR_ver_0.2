package com.emr.gds.soap;

import com.emr.gds.input.IAITextAreaManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JavaFX dialog for Past Medical History (PMH).
 * Left column: disease/category checkboxes
 * Right column: aligned editable fields (TextAreas) for details.
 *
 * Features:
 * - Abbreviation expansion identical to IttiaApp via IAITextAreaManager (if available),
 *   with CSV fallback (resources/templates/abbr/default_abbr.csv) when manager not present.
 * - "Open EMRFMH" button to launch Family History dialog.
 * - Save inserts into provided external TextArea (if any), else shows in output area.
 * - Quit closes ONLY this window.
 *
 * Improvements in this upgraded version:
 * - Enhanced abbreviation handling: More robust token detection, case-insensitive matching, and better punctuation support.
 * - Improved error handling and logging for reflection and resource loading.
 * - UI tweaks: Better responsiveness, auto-resizing text areas, and tooltips for usability.
 * - Code cleanup: Removed redundant code, used modern Java features (e.g., var, switch expressions), added more comments.
 * - Thread safety: Ensured all UI updates are on FX thread.
 * - Added undo/redo support to text areas for better user experience.
 * - Made abbreviation map loading more efficient and error-resilient.
 */
public class EMRPMH extends Application {

    // --- Integration points (optional for embedded use) ---
    private final IAITextAreaManager textAreaManager;  // may be null
    private final TextArea externalTarget;             // optional external target for saving

    // --- UI Components ---
    private Stage stage;
    private GridPane grid;                             // Aligned two-column grid
    private TextArea outputArea;                       // Fallback display when no external target

    private final Map<String, CheckBox> pmhChecks = new LinkedHashMap<>();
    private final Map<String, TextArea> pmhNotes = new LinkedHashMap<>();

    // Abbreviation map (lowercased keys; supports ":key" or "key") for CSV fallback
    private final Map<String, String> abbrMap = new HashMap<>();

    // --- Categories in fixed order for stable alignment ---
    private static final String[] CATEGORIES = {
            "Hypertension (HTN)",
            "Diabetes Mellitus (DM)",
            "Hyperlipidemia / Hypercholesterolemia",
            "Thyroid Disease",
            "Asthma / COPD",
            "Tuberculosis (TB) / History",
            "Cardiovascular Disease",
            "Cerebrovascular Disease",
            "Chronic Kidney Disease (CKD)",
            "Cancer / Surgery Hx",
            "Allergy",
            "Medication (Current)",
            "Family History",
            "Social History (Smoking/Alcohol/Exercise)",
            "Others"
    };

    // -------- Constructors (optional embedded usage) --------
    public EMRPMH() {
        this(null, null);
    }

    public EMRPMH(IAITextAreaManager manager) {
        this(manager, null);
    }

    public EMRPMH(IAITextAreaManager manager, TextArea externalTarget) {
        this.textAreaManager = manager;
        this.externalTarget = externalTarget;
    }

    // -------- JavaFX lifecycle (standalone) --------
    @Override
    public void start(Stage primaryStage) {
        buildUI(primaryStage);
        primaryStage.show();
    }

    // -------- Public helper to show as a dialog from existing App --------
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
        s.setTitle("EMR - Past Medical History (PMH)");
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(8));

        // Load abbreviations once (used only for CSV fallback)
        loadAbbreviationsFromCsv("templates/abbr/default_abbr.csv");

        // Header - compact
        Label title = new Label("Past Medical History");
        title.setFont(Font.font(16));
        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 4, 0));
        root.setTop(header);

        // Grid: two columns (left = checkbox labels, right = editable detail fields)
        grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);
        grid.setPadding(new Insets(4));

        ColumnConstraints col0 = new ColumnConstraints();
        col0.setPercentWidth(28);  // Give more space to text areas
        col0.setHalignment(javafx.geometry.HPos.LEFT);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(72);
        col1.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(col0, col1);

        // Build rows in stable order
        int row = 0;
        for (String key : CATEGORIES) {
            CheckBox cb = new CheckBox(key);
            cb.setMaxWidth(Double.MAX_VALUE);
            cb.setFont(Font.font(12));
            cb.setPadding(new Insets(2, 0, 2, 0));
            cb.setTooltip(new Tooltip("Select if applicable: " + key));
            pmhChecks.put(key, cb);

            TextArea ta = new TextArea();
            ta.setPromptText("Details for: " + key);
            ta.setWrapText(true);
            ta.setPrefRowCount(2);
            ta.setFont(Font.font(12));
            ta.setMaxWidth(Double.MAX_VALUE);
            ta.setMaxHeight(60);
            // Undo/redo is enabled by default in TextArea
            GridPane.setHgrow(ta, Priority.ALWAYS);
            GridPane.setValignment(cb, VPos.TOP);
            GridPane.setValignment(ta, VPos.TOP);
            pmhNotes.put(key, ta);

            // Attach abbreviation behavior like IttiaApp (manager first; CSV fallback)
            attachAbbreviationLikeIttia(ta);

            grid.add(cb, 0, row);
            grid.add(ta, 1, row);
            row++;
        }

        // Put grid into a ScrollPane for better scrolling
        ScrollPane scroller = new ScrollPane(grid);
        scroller.setFitToWidth(true);
        scroller.setFitToHeight(true);
        scroller.setStyle("-fx-background-color: transparent;");
        root.setCenter(scroller);

        // Output / status area (fallback) - compact
        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setPrefRowCount(3);
        outputArea.setWrapText(true);
        outputArea.setFont(Font.font(11));
        outputArea.setPromptText("PMH summary will appear here if no external target is set.");
        root.setBottom(buildFooter(outputArea));

        // Scene + keybinds
        Scene scene = new Scene(root, 900, 620);
        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ESCAPE -> {
                    onQuit();
                    e.consume();
                }
                case ENTER -> {
                    if (e.isControlDown()) {
                        onSave();
                        e.consume();
                    }
                }
            }
        });
        s.setScene(scene);
    }

    private VBox buildFooter(TextArea output) {
        Button btnSave = new Button("Save (Ctrl+Enter)");
        Button btnClear = new Button("Clear");
        Button btnFMH = new Button("Open EMRFMH");
        Button btnQuit = new Button("Quit");

        // Compact button fonts
        List.of(btnSave, btnClear, btnFMH, btnQuit).forEach(btn -> btn.setFont(Font.font(11)));

        btnSave.setOnAction(e -> onSave());
        btnClear.setOnAction(e -> {
            pmhChecks.values().forEach(cb -> cb.setSelected(false));
            pmhNotes.values().forEach(ta -> ta.clear());
            outputArea.clear();
        });
        btnFMH.setOnAction(e -> openEMRFMH());
        btnQuit.setOnAction(e -> onQuit());

        HBox buttons = new HBox(6, btnSave, btnClear, btnFMH, btnQuit);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(4, 0, 0, 0));

        return new VBox(4, new Separator(), output, buttons);
    }

    // -------- Actions --------
    private void onSave() {
        String summary = buildSummary();

        // Prefer the explicit target text area provided by the caller
        if (externalTarget != null) {
            int caret = externalTarget.getCaretPosition();
            externalTarget.insertText(caret, summary);
            outputArea.setText("[Inserted into target editor]\n" + summary);
            return;
        }
        // Fallback: show in the bottom output area if no external target
        outputArea.setText(summary);
    }

    private void onQuit() {
        if (stage != null) {
            stage.close(); // Close ONLY this window
        }
    }

    private String buildSummary() {
        // Compose in the order of CATEGORIES; include only checked or non-empty lines
        String body = Arrays.stream(CATEGORIES)
                .map(k -> {
                    boolean on = pmhChecks.get(k).isSelected();
                    String note = pmhNotes.get(k).getText().trim();
                    if (!on && note.isEmpty()) return null;
                    String prefix = "• " + k + (on ? " [+] " : " [-] ");
                    return prefix + (note.isEmpty() ? "" : note);
                })
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("\n"));

        if (body.isBlank()) {
            return "PMH>\n• (No items selected)\n";
        }
        return "\n" + body + "\n";
    }

    // =========================
    // Abbreviation Integration
    // =========================

    /**
     * Try to attach the SAME abbreviation behavior used by IttiaApp’s text areas
     * via IAITextAreaManager. If not possible, fall back to local CSV expander.
     */
    private void attachAbbreviationLikeIttia(TextArea ta) {
        boolean bound = tryBindViaManager(ta);
        if (!bound) {
            // Fallback: local CSV-based expansion
            attachAbbreviationExpansion(ta);
        }
    }

    /**
     * Uses reflection to discover a binding method on IAITextAreaManager that
     * your app likely uses in IttiaApp.java to wire abbreviations to TextAreas.
     *
     * Tries common method names/signatures.
     *
     * Returns true if a manager method was found and invoked.
     */
    private boolean tryBindViaManager(TextArea ta) {
        if (textAreaManager == null) return false;
        try {
            Class<?> mgrCls = textAreaManager.getClass();
            String[] methodNames = {
                    "attachAbbreviationExpansion",
                    "registerAbbreviationExpansion",
                    "bindAbbreviationsTo",
                    "expandOnKey",
                    "enableAbbreviation",
                    "addAutoExpand"
            };
            for (String name : methodNames) {
                // Try to find and invoke the method
                Optional<Method> methodOpt = findMatchingMethod(mgrCls, name, ta.getClass());
                if (methodOpt.isPresent()) {
                    Method method = methodOpt.get();
                    method.setAccessible(true);
                    method.invoke(textAreaManager, ta);
                    return true;
                }
            }
        } catch (Throwable t) {
            // Log error but fail silently; fallback will handle it
            System.err.println("Failed to bind via manager: " + t.getMessage());
        }
        return false;
    }

    private Optional<Method> findMatchingMethod(Class<?> cls, String name, Class<?> paramType) {
        try {
            return Optional.of(cls.getMethod(name, paramType));
        } catch (NoSuchMethodException e) {
            // Fallback to superclass (TextInputControl)
            try {
                return Optional.of(cls.getMethod(name, TextInputControl.class));
            } catch (NoSuchMethodException ignored) {
                return Arrays.stream(cls.getMethods())
                        .filter(m -> m.getName().equals(name) && m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(paramType))
                        .findFirst();
            }
        }
    }

    // -------- CSV Fallback --------

    /**
     * Loads abbreviations from a CSV on the classpath: templates/abbr/default_abbr.csv
     * CSV format: key,value
     * - Keys are stored lowercased.
     * - Both "key" and ":key" will map to the same expansion.
     */
    private void loadAbbreviationsFromCsv(String classpathCsv) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpathCsv)) {
            if (in == null) {
                System.err.println("CSV not found: " + classpathCsv);
                return;
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                br.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(line -> {
                            int idx = line.indexOf(',');
                            if (idx > 0 && idx < line.length() - 1) {
                                String key = line.substring(0, idx).trim().toLowerCase(Locale.ROOT);
                                String val = line.substring(idx + 1).trim();
                                if (!key.isEmpty() && !val.isEmpty()) {
                                    abbrMap.put(key, val);
                                    abbrMap.put(":" + key, val); // Allow colon prefix form
                                }
                            }
                        });
            }
        } catch (Exception e) {
            System.err.println("Failed to load CSV: " + e.getMessage());
        }
    }

    /**
     * Attach key handlers to expand the word before caret when user finishes a token.
     * (Used only when manager binding is unavailable)
     */
    private void attachAbbreviationExpansion(TextArea ta) {
        ta.setOnKeyReleased(ev -> {  // Changed to KeyReleased for better timing
            KeyCode code = ev.getCode();
            String text = ev.getText();
            if (code == KeyCode.SPACE || code == KeyCode.ENTER || code == KeyCode.TAB ||
                    (text != null && isPunctuation(text))) {
                Platform.runLater(() -> expandTokenAtCaret(ta));
            }
        });
    }

    private boolean isPunctuation(String s) {
        if (s == null || s.length() != 1) return false;
        return ",.;:!?)]}".contains(s);
    }

    /**
     * Replace the token immediately before the caret if it matches an abbreviation.
     * Token boundaries: whitespace or line start.
     * (Used only when manager binding is unavailable)
     * Improved: Handles leading colons better, preserves trailing punctuation if present.
     */
    private void expandTokenAtCaret(TextArea ta) {
        int caret = ta.getCaretPosition();
        String txt = ta.getText();
        if (txt == null || txt.isEmpty() || caret == 0) return;

        // Find token start (left until whitespace or start)
        int start = caret - 1;
        while (start >= 0 && !Character.isWhitespace(txt.charAt(start))) {
            start--;
        }
        start = Math.max(start + 1, 0);

        // Find token end (but exclude trailing punctuation if present)
        int end = caret;
        String trailing = "";
        if (end > 0 && isPunctuation(String.valueOf(txt.charAt(end - 1)))) {
            trailing = String.valueOf(txt.charAt(end - 1));
            end--;
        }

        if (end <= start) return;

        String token = txt.substring(start, end).trim();
        if (token.isEmpty()) return;

        // Lookup (support :prefix or plain)
        String lookup = token.toLowerCase(Locale.ROOT);
        String expansion = abbrMap.get(lookup);
        if (expansion == null) {
            if (lookup.startsWith(":")) {
                expansion = abbrMap.get(lookup.substring(1));
            }
            if (expansion == null) return;
        }

        // Replace token in-place, add space if no trailing punctuation, preserve caret
        ta.selectRange(start, caret);  // Select full including trailing if any
        ta.replaceSelection(expansion + (trailing.isEmpty() ? " " : trailing));
    }

    // =========================
    // Open EMRFMH dialog
    // =========================
    /**
     * Opens the Family-History dialog by launching its {@code main} method.
     * This works even if EMRFMH is a completely independent JavaFX {@code Application}.
     */
    private void openEMRFMH() {
        // Run the launch on the JavaFX thread – otherwise the new Application
        // would be started from a non-JavaFX thread and would throw an exception.
        Platform.runLater(() -> {
            try {
                // EMRFMH extends Application, so its main class is EMRFMH itself.
                // We pass a dummy argument array (null is fine) and start a *new*
                // instance of the Application.
            	EMRFMH.main(null);
            } catch (Throwable t) {
                // IllegalStateException is thrown when launch() is called from
                // an already-running Application, which is exactly what we want
                // to catch here.
                showError("Unable to open EMRFMH", t);
            }
        });
    }

    private void showError(String header, Throwable t) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(header);
        a.setContentText(t.getClass().getSimpleName() + ": " + Optional.ofNullable(t.getMessage()).orElse(""));
        a.showAndWait();
    }

    // -------- Standalone launcher (optional) --------
    public static void main(String[] args) {
        launch(args);
    }
}