package com.emr.gds.main;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

import com.emr.gds.input.IAIFxTextAreaManager;
import com.emr.gds.input.IAIMain;
import com.emr.gds.input.IAITextAreaManager;
import com.emr.gds.soap.ChiefComplaintEditor;
import com.emr.gds.soap.EMRPMH;

/**
 * Manages the central text areas in the EMR application.
 * - Stable, readable styles (focus/hover/unfocused)
 * - Abbreviation expansion (":key")
 * - Double-click handlers per section
 * - Template parsing/append
 */
public class IAMTextArea {

    // ================================
    // CONSTANTS
    // ================================
    public static final String[] TEXT_AREA_TITLES = {
            "CC>", "PI>", "ROS>", "PMH>", "S>",
            "O>", "Physical Exam>", "A>", "P>", "Comment>"
    };

 // ---- Paul Gauguin Theme (warm earth + tropical sea) ----
    private static final String BASE_TEXT_TWEAKS =
            "-fx-prompt-text-fill: rgba(0,0,0,0.55);" +   // keep
            "-fx-highlight-fill: rgba(0,0,0,0.15);" +     // keep
            "-fx-highlight-text-fill: #000000;";          // keep

    // Unfocused: sun-washed sand/ochre
    private static final String STYLE_UNFOCUSED =
            "-fx-background-color: linear-gradient(135deg,#F7E6B5,#EED28A,#DCC06A);" +
            "-fx-text-fill: #0A2540;" +                  // keep font color
            "-fx-border-color: #C97B2B;" +               // burnt sienna edge
            "-fx-border-width: 1.5;" +
            "-fx-background-insets: 0;" +
            "-fx-background-radius: 9;" +
            "-fx-border-radius: 9;" +
            "-fx-effect: dropshadow(gaussian, rgba(201,123,43,0.35), 6, 0.4, 0, 1);" +
            BASE_TEXT_TWEAKS;

    // Focused: bold saffron → coral (high attention)
    private static final String STYLE_FOCUSED =
            "-fx-background-color: linear-gradient(135deg,#FFD27E,#FFB45A,#FF8A4C);" +
            "-fx-text-fill: #0A2540;" +                  // keep font color for readability
            "-fx-border-color: #8C3B2E;" +               // mahogany accent
            "-fx-border-width: 3;" +
            "-fx-background-insets: 0;" +
            "-fx-background-radius: 9;" +
            "-fx-border-radius: 9;" +
            "-fx-effect: dropshadow(gaussian, rgba(140,59,46,0.45), 12, 0.25, 0, 2);" +
            BASE_TEXT_TWEAKS;

    // Hover: tropical lagoon teals
    private static final String STYLE_HOVER =
            "-fx-background-color: linear-gradient(135deg,#CFE9DF,#A7D8C6,#7FC6B3);" +
            "-fx-text-fill: #0A2540;" +                  // keep font color
            "-fx-border-color: #2C8C7A;" +               // deep teal ring
            "-fx-border-width: 2;" +
            "-fx-background-insets: 0;" +
            "-fx-background-radius: 9;" +
            "-fx-border-radius: 9;" +
            "-fx-effect: dropshadow(gaussian, rgba(44,140,122,0.40), 10, 0.25, 0, 1);" +
            BASE_TEXT_TWEAKS;



    // ================================
    // INSTANCE VARIABLES
    // ================================
    private final List<TextArea> areas = new ArrayList<>(10);
    private TextArea lastFocusedArea = null;
    private final Map<String, String> abbrevMap;
    private IAMProblemAction problemAction;
    private final Map<Integer, TextAreaDoubleClickHandler> doubleClickHandlers = new HashMap<>();

    // ================================
    // INTERFACE FOR DOUBLE-CLICK HANDLERS
    // ================================
    @FunctionalInterface
    public interface TextAreaDoubleClickHandler {
        void handle(TextArea textArea, int areaIndex);
    }

    // ================================
    // CONSTRUCTOR
    // ================================
    public IAMTextArea(Map<String, String> abbrevMap, IAMProblemAction problemAction) {
        this.abbrevMap = Objects.requireNonNull(abbrevMap, "abbrevMap");
        this.problemAction = Objects.requireNonNull(problemAction, "problemAction");
        initializeDoubleClickHandlers();
        initializeTextAreas();
    }

    // ================================
    // INITIALIZATION
    // ================================
    private void initializeDoubleClickHandlers() {
        doubleClickHandlers.put(0, this::executeChiefComplaintHandler);
        doubleClickHandlers.put(1, this::executePresentIllnessHandler);
        doubleClickHandlers.put(2, this::executeReviewOfSystemsHandler);
        doubleClickHandlers.put(3, this::executePastMedicalHistoryHandler);
        doubleClickHandlers.put(4, this::executeSubjectiveHandler);
        doubleClickHandlers.put(5, this::executeObjectiveHandler);
        doubleClickHandlers.put(6, this::executePhysicalExamHandler);
        doubleClickHandlers.put(7, this::executeAssessmentHandler);
        doubleClickHandlers.put(8, this::executePlanHandler);
        doubleClickHandlers.put(9, this::executeCommentHandler);
    }

    private void initializeTextAreas() {
        areas.clear();
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            TextArea ta = new TextArea();
            ta.setWrapText(true);
            ta.setFont(Font.font("Consolas", 12));
            ta.setPrefRowCount(11);
            ta.setPrefColumnCount(58);
            ta.setPromptText(i < TEXT_AREA_TITLES.length ? TEXT_AREA_TITLES[i] : "Area " + (i + 1));
            ta.setStyle(STYLE_UNFOCUSED);

            // Selection & caret are inherited from CSS above. No blank text.
            // Focus: apply focused/unfocused style correctly.
            ta.focusedProperty().addListener((obs, was, is) -> {
                if (is) {
                    ta.setStyle(STYLE_FOCUSED);
                    lastFocusedArea = ta;
                } else {
                    ta.setStyle(STYLE_UNFOCUSED);
                }
            });

            // Hover only affects style when not focused.
            ta.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
                if (!ta.isFocused()) ta.setStyle(STYLE_HOVER);
            });
            ta.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
                if (!ta.isFocused()) ta.setStyle(STYLE_UNFOCUSED);
            });

            // Scratchpad: live mirror by title
            if (idx < TEXT_AREA_TITLES.length) {
                ta.textProperty().addListener((o, oldV, newV) ->
                        problemAction.updateAndRedrawScratchpad(TEXT_AREA_TITLES[idx], newV));
            }

            // Space-key abbreviation expansion. Only consume if we replaced.
            ta.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.SPACE) {
                    boolean replaced = expandAbbreviationOnSpace(ta);
                    if (replaced) event.consume();
                }
            });

            // Double-click handler
            ta.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    handleDoubleClick(ta, idx);
                    event.consume();
                }
            });

            // Filter control characters
            ta.setTextFormatter(new TextFormatter<>(IAMTextFormatUtil.filterControlChars()));

            areas.add(ta);
        }
    }

    // ================================
    // UI BUILDERS
    // ================================
    public GridPane buildCenterAreas() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        if (areas.isEmpty()) initializeTextAreas();

        int rows = 5, cols = 2;
        for (int i = 0; i < Math.min(areas.size(), rows * cols); i++) {
            grid.add(areas.get(i), i % cols, i / cols);
        }
        return grid;
    }

    // ================================
    // DOUBLE-CLICK
    // ================================
    private void handleDoubleClick(TextArea textArea, int areaIndex) {
        TextAreaDoubleClickHandler handler = doubleClickHandlers.get(areaIndex);
        if (handler == null) return;
        try {
            handler.handle(textArea, areaIndex);
        } catch (Exception e) {
            showErrorAlert("Handler Error",
                    "Failed to execute handler for " + safeTitle(areaIndex),
                    e.getMessage());
        }
    }

    private void executeChiefComplaintHandler(TextArea textArea, int index) {
        try {
            ChiefComplaintEditor cc = new ChiefComplaintEditor(textArea);
            cc.showAndWait();
        } catch (Exception e) {
            handleEditorException("Chief Complaint", textArea, index, e);
        }
    }

    private void executePresentIllnessHandler(TextArea textArea, int index) {
        executeReflectionBasedEditor("com.emr.gds.main.PresentIllnessEditor", "Present Illness", textArea, index);
    }

    private void executeReviewOfSystemsHandler(TextArea textArea, int index) {
        executeReflectionBasedEditor("com.emr.gds.main.ReviewOfSystemsEditor", "Review of Systems", textArea, index);
    }

    private void executePastMedicalHistoryHandler(TextArea textArea, int index) {
        try {
            IAITextAreaManager manager = IAIMain.getTextAreaManager();
            EMRPMH pmhDialog = new EMRPMH(manager);
            pmhDialog.setVisible(true);
        } catch (Exception e) {
            handleEditorException("Past Medical History", textArea, index, e);
        }
    }

    private void executeSubjectiveHandler(TextArea textArea, int index) {
        executeReflectionBasedEditor("com.emr.gds.main.SubjectiveEditor", "Subjective", textArea, index);
    }

    private void executeObjectiveHandler(TextArea textArea, int index) {
        executeReflectionBasedEditor("com.emr.gds.main.ObjectiveEditor", "Objective", textArea, index);
    }

    private void executePhysicalExamHandler(TextArea textArea, int index) {
        executeReflectionBasedEditor("com.emr.gds.main.PhysicalExamEditor", "Physical Exam", textArea, index);
    }

    private void executeAssessmentHandler(TextArea textArea, int index) {
        executeReflectionBasedEditor("com.emr.gds.main.AssessmentEditor", "Assessment", textArea, index);
    }

    private void executePlanHandler(TextArea textArea, int index) {
        executeReflectionBasedEditor("com.emr.gds.main.PlanEditor", "Plan", textArea, index);
    }

    private void executeCommentHandler(TextArea textArea, int index) {
        executeReflectionBasedEditor("com.emr.gds.main.CommentEditor", "Comment", textArea, index);
    }

    private void executeReflectionBasedEditor(String className, String sectionName, TextArea textArea, int index) {
        try {
            Class<?> editorClass = Class.forName(className);
            Object editor = editorClass.getConstructor(TextArea.class).newInstance(textArea);
            editorClass.getMethod("show").invoke(editor);
        } catch (ClassNotFoundException e) {
            showDefaultDoubleClick(sectionName, textArea, index);
        } catch (Exception e) {
            handleEditorException(sectionName, textArea, index, e);
        }
    }

    private void handleEditorException(String sectionName, TextArea ta, int index, Exception e) {
        showErrorAlert("Editor Error",
                "Failed to open " + sectionName + " Editor",
                e.getMessage());
        showDefaultDoubleClick(sectionName, ta, index);
    }

    private void showDefaultDoubleClick(String sectionName, TextArea ta, int index) {
        String message = "Double-clicked on " + sectionName + " (Area " + (index + 1) + ")\n" +
                "Current text length: " + ta.getText().length() + " characters";
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Double-Click Action");
            alert.setHeaderText("Section: " + sectionName);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // ================================
    // PUBLIC ACTIONS
    // ================================
    public void setDoubleClickHandler(int areaIndex, TextAreaDoubleClickHandler handler) {
        if (areaIndex < 0 || areaIndex >= TEXT_AREA_TITLES.length)
            throw new IllegalArgumentException("Invalid area index: " + areaIndex);
        doubleClickHandlers.put(areaIndex, handler);
    }

    public void removeDoubleClickHandler(int areaIndex) {
        doubleClickHandlers.remove(areaIndex);
    }

    public TextAreaDoubleClickHandler getDoubleClickHandler(int areaIndex) {
        return doubleClickHandlers.get(areaIndex);
    }

    public void insertTemplateIntoFocusedArea(IAMButtonAction.TemplateLibrary t) {
        insertBlockIntoFocusedArea(t.body());
    }

    public void insertLineIntoFocusedArea(String line) {
        insertBlockIntoFocusedArea(line.endsWith("\n") ? line : line + "\n");
    }

    public void insertBlockIntoFocusedArea(String block) {
        TextArea ta = getFocusedArea();
        if (ta == null) return;
        String expandedBlock = expandAbbreviations(block);
        int caret = ta.getCaretPosition();
        ta.insertText(caret, expandedBlock);
        Platform.runLater(ta::requestFocus);
    }

    public void formatCurrentArea() {
        TextArea ta = getFocusedArea();
        if (ta == null) return;
        ta.setText(IAMTextFormatUtil.autoFormat(ta.getText()));
    }

    public void clearAllTextAreas() {
        areas.forEach(TextArea::clear);
    }

    public void parseAndAppendTemplate(String templateContent) {
        if (templateContent == null || templateContent.isBlank()) return;

        templateContent = expandAbbreviations(templateContent);

        Map<String, TextArea> areaMap = new HashMap<>();
        for (int i = 0; i < TEXT_AREA_TITLES.length && i < areas.size(); i++) {
            areaMap.put(TEXT_AREA_TITLES[i], areas.get(i));
        }

        String patternString = "(?=(" + String.join("|", TEXT_AREA_TITLES)
                .replace(">", "\\>")
                .replace(" ", "\\s") + "))";

        Pattern pattern = Pattern.compile(patternString);
        String[] parts = pattern.split(templateContent);

        int sectionsLoaded = 0;
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;

            for (String title : TEXT_AREA_TITLES) {
                if (p.startsWith(title)) {
                    TextArea target = areaMap.get(title);
                    if (target != null) {
                        String body = p.substring(title.length()).trim();
                        if (!body.isEmpty()) {
                            if (!target.getText().isBlank()) target.appendText("\n" + body);
                            else target.setText(body);
                            sectionsLoaded++;
                        }
                    }
                    break;
                }
            }
        }

        if (sectionsLoaded == 0) {
            insertBlockIntoFocusedArea(templateContent);
        }
    }

    // ================================
    // ABBREVIATIONS
    // ================================
    private boolean expandAbbreviationOnSpace(TextArea ta) {
        int caret = ta.getCaretPosition();
        String upToCaret = ta.getText(0, caret);
        int start = Math.max(upToCaret.lastIndexOf(' '), upToCaret.lastIndexOf('\n')) + 1;
        if (start < 0 || start > caret) return false;

        String word = upToCaret.substring(start);
        if (!word.startsWith(":")) return false;

        String key = word.substring(1);
        String replacement = getAbbreviationReplacement(key);
        if (replacement == null) return false;

        ta.deleteText(start, caret);
        ta.insertText(start, replacement + " ");
        return true;
    }

    private String expandAbbreviations(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ':' && (i == 0 || Character.isWhitespace(text.charAt(i - 1)))) {
                int start = i + 1;
                int j = start;
                while (j < text.length() && !Character.isWhitespace(text.charAt(j))) j++;
                String key = text.substring(start, j);
                String rep = getAbbreviationReplacement(key);
                out.append(rep != null ? rep : ":" + key);
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private String getAbbreviationReplacement(String key) {
        if ("cd".equals(key)) {
            return LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        }
        return abbrevMap.get(key);
    }

    // ================================
    // HELPERS
    // ================================
    private TextArea getFocusedArea() {
        for (TextArea ta : areas) if (ta.isFocused()) return ta;
        return (lastFocusedArea != null) ? lastFocusedArea : (areas.isEmpty() ? null : areas.get(0));
    }

    public void focusArea(int idx) {
        if (idx >= 0 && idx < areas.size()) {
            areas.get(idx).requestFocus();
            lastFocusedArea = areas.get(idx);
        }
    }

    public List<TextArea> getTextAreas() {
        IAIMain.setTextAreaManager(new IAIFxTextAreaManager(areas));
        return this.areas;
    }

    public String getUniqueLines(String text) {
        return IAMTextFormatUtil.getUniqueLines(text);
    }

    public static String normalizeLine(String s) {
        return IAMTextFormatUtil.normalizeLine(s);
    }

    private void showErrorAlert(String title, String header, String content) {
        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
        errorAlert.setTitle(title);
        errorAlert.setHeaderText(header);
        errorAlert.setContentText(content);
        errorAlert.showAndWait();
    }

    private String safeTitle(int index) {
        if (index >= 0 && index < TEXT_AREA_TITLES.length) return TEXT_AREA_TITLES[index];
        return "Area " + (index + 1);
    }
}
