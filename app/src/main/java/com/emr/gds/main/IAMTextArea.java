package com.emr.gds.main;

import com.emr.gds.input.IAIFxTextAreaManager;
import com.emr.gds.input.IAIMain;
import com.emr.gds.input.IAITextAreaManager;
import com.emr.gds.soap.ChiefComplaintEditor;
import com.emr.gds.soap.EMRPMH;
import com.emr.gds.soap.IMSPresentIllness;
import com.emr.gds.soap.IMSFollowUp.PlanFollowupAction;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages the central text areas in the EMR application, providing features like:
 * - Consistent and readable styling for focus, hover, and unfocused states.
 * - Automatic abbreviation expansion (e.g., ":key").
 * - Section-specific double-click handlers for specialized editors.
 * - Methods for template parsing and insertion.
 */
public class IAMTextArea {

    // ================================ 
    // Constants
    // ================================ 
    public static final String[] TEXT_AREA_TITLES = {
            "CC>", "PI>", "ROS>", "PMH>", "S>",
            "O>", "Physical Exam>", "A>", "P>", "Comment>"
    };

    private static final String BASE_TEXT_TWEAKS = 
            "-fx-prompt-text-fill: rgba(0,0,0,0.55);" +
            "-fx-highlight-fill: rgba(0,0,0,0.15);" +
            "-fx-highlight-text-fill: #000000;";

    // Style for unfocused text areas (sun-washed sand/ochre theme)
    private static final String STYLE_UNFOCUSED = 
            "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #F7E6B5, #EED28A, #DCC06A);" +
            "-fx-text-fill: #0A2540;" +
            "-fx-border-color: #C97B2B;" +
            "-fx-border-width: 1.5;" +
            "-fx-background-insets: 0;" +
            "-fx-background-radius: 9;" +
            "-fx-border-radius: 9;" +
            "-fx-effect: dropshadow(gaussian, rgba(201,123,43,0.35), 6, 0.4, 0, 1);" +
            BASE_TEXT_TWEAKS;

    // Style for focused text areas (bold saffron to coral for high attention)
    private static final String STYLE_FOCUSED = 
            "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #FFD27E, #FFB45A, #FF8A4C);" +
            "-fx-text-fill: #0A2540;" +
            "-fx-border-color: #8C3B2E;" +
            "-fx-border-width: 3;" +
            "-fx-background-insets: 0;" +
            "-fx-background-radius: 9;" +
            "-fx-border-radius: 9;" +
            "-fx-effect: dropshadow(gaussian, rgba(140,59,46,0.45), 12, 0.25, 0, 2);" +
            BASE_TEXT_TWEAKS;

    // Style for hovered text areas (tropical lagoon teals)
    private static final String STYLE_HOVER = 
            "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #CFE9DF, #A7D8C6, #7FC6B3);" +
            "-fx-text-fill: #0A2540;" +
            "-fx-border-color: #2C8C7A;" +
            "-fx-border-width: 2;" +
            "-fx-background-insets: 0;" +
            "-fx-background-radius: 9;" +
            "-fx-border-radius: 9;" +
            "-fx-effect: dropshadow(gaussian, rgba(44,140,122,0.40), 10, 0.25, 0, 1);" +
            BASE_TEXT_TWEAKS;

    // ================================ 
    // Instance Variables
    // ================================ 
    private final List<TextArea> areas = new ArrayList<>(10);
    private TextArea lastFocusedArea = null;
    private final Map<String, String> abbrevMap;
    private final IAMProblemAction problemAction;
    private final Map<Integer, TextAreaDoubleClickHandler> doubleClickHandlers = new HashMap<>();

    @FunctionalInterface
    public interface TextAreaDoubleClickHandler {
        void handle(TextArea textArea, int areaIndex);
    }

    // ================================ 
    // Constructor
    // ================================ 
    public IAMTextArea(Map<String, String> abbrevMap, IAMProblemAction problemAction) {
        this.abbrevMap = Objects.requireNonNull(abbrevMap, "abbrevMap");
        this.problemAction = Objects.requireNonNull(problemAction, "problemAction");
        initializeDoubleClickHandlers();
        initializeTextAreas();
    }

    // ================================ 
    // Initialization
    // ================================ 

    /**
     * Sets up the default double-click handlers for each text area.
     */
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

    /**
     * Creates and configures the 10 main text areas.
     */
    private void initializeTextAreas() {
        areas.clear();
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            TextArea ta = createStyledTextArea(i);

            // Add listeners for focus, hover, and input events
            addFocusAndHoverListeners(ta);
            addScratchpadListener(ta, idx);
            addAbbreviationExpansionListener(ta);
            addDoubleClickListener(ta, idx);

            areas.add(ta);
        }
    }

    /**
     * Creates a single styled TextArea.
     */
    private TextArea createStyledTextArea(int index) {
        TextArea ta = new TextArea();
        ta.setWrapText(true);
        ta.setFont(Font.font("Malgun Gothic", 12));
        ta.setPrefRowCount(11);
        ta.setPrefColumnCount(58);
        ta.setPromptText(index < TEXT_AREA_TITLES.length ? TEXT_AREA_TITLES[index] : "Area " + (index + 1));
        ta.setStyle(STYLE_UNFOCUSED);
        ta.setTextFormatter(new TextFormatter<>(IAMTextFormatUtil.filterControlChars()));
        return ta;
    }

    // ================================ 
    // UI Builders
    // ================================ 

    /**
     * Constructs the central grid of text areas.
     */
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
    // Event Listener Setup
    // ================================ 

    private void addFocusAndHoverListeners(TextArea ta) {
        ta.focusedProperty().addListener((obs, was, is) -> {
            ta.setStyle(is ? STYLE_FOCUSED : STYLE_UNFOCUSED);
            if (is) lastFocusedArea = ta;
        });

        ta.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            if (!ta.isFocused()) ta.setStyle(STYLE_HOVER);
        });
        ta.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            if (!ta.isFocused()) ta.setStyle(STYLE_UNFOCUSED);
        });
    }

    private void addScratchpadListener(TextArea ta, int idx) {
        if (idx < TEXT_AREA_TITLES.length) {
            ta.textProperty().addListener((o, oldV, newV) -> 
                    problemAction.updateAndRedrawScratchpad(TEXT_AREA_TITLES[idx], newV));
        }
    }

    private void addAbbreviationExpansionListener(TextArea ta) {
        ta.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SPACE) {
                if (expandAbbreviationOnSpace(ta)) event.consume();
            }
        });
    }

    private void addDoubleClickListener(TextArea ta, int idx) {
        ta.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                handleDoubleClick(ta, idx);
                event.consume();
            }
        });
    }

    // ================================ 
    // Double-Click Handling
    // ================================ 

    private void handleDoubleClick(TextArea textArea, int areaIndex) {
        Optional.ofNullable(doubleClickHandlers.get(areaIndex)).ifPresent(handler -> {
            try {
                handler.handle(textArea, areaIndex);
            } catch (Exception e) {
                showErrorAlert("Handler Error", "Failed to execute handler for " + safeTitle(areaIndex), e.getMessage());
            }
        });
    }

    // --- Specific Double-Click Implementations ---

    private void executeChiefComplaintHandler(TextArea textArea, int index) {
        try {
            new ChiefComplaintEditor(textArea).showAndWait();
        } catch (Exception e) {
            handleEditorException("Chief Complaint", textArea, index, e);
        }
    }

    private void executePresentIllnessHandler(TextArea textArea, int index) {
        try {
            new IMSPresentIllness(textArea).showAndWait();
        } catch (Exception e) {
            handleEditorException("Present Illness", textArea, index, e);
        }
    }

    private void executeReviewOfSystemsHandler(TextArea textArea, int index) {
        executeReflectionBasedEditor("com.emr.gds.main.ReviewOfSystemsEditor", "Review of Systems", textArea, index);
    }

    private void executePastMedicalHistoryHandler(TextArea textArea, int index) {
        try {
            new EMRPMH(IAIMain.getTextAreaManager()).setVisible(true);
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
        try {
            new PlanFollowupAction(IAIMain.getTextAreaManager(), problemAction).showAndWait();
        } catch (Exception e) {
            handleEditorException("Plan & Follow-up Assistant", textArea, index, e);
        }
    }

    private void executeCommentHandler(TextArea textArea, int index) {
        executeReflectionBasedEditor("com.emr.gds.main.CommentEditor", "Comment", textArea, index);
    }

    // --- Dynamic Editor Loading & Fallbacks ---

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
        showErrorAlert("Editor Error", "Failed to open " + sectionName + " Editor", e.getMessage());
        showDefaultDoubleClick(sectionName, ta, index);
    }

    private void showDefaultDoubleClick(String sectionName, TextArea ta, int index) {
        String message = String.format("Double-clicked on %s (Area %d)\nCurrent text length: %d characters",
                sectionName, index + 1, ta.getText().length());
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Double-Click Action");
            alert.setHeaderText("Section: " + sectionName);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // ================================ 
    // Public API for Text Manipulation
    // ================================ 

    public void insertTemplateIntoFocusedArea(IAMButtonAction.TemplateLibrary t) {
        insertBlockIntoFocusedArea(t.body());
    }

    public void insertLineIntoFocusedArea(String line) {
        insertBlockIntoFocusedArea(line.endsWith("\n") ? line : line + "\n");
    }

    public void insertBlockIntoFocusedArea(String block) {
        Optional.ofNullable(getFocusedArea()).ifPresent(ta -> {
            String expandedBlock = expandAbbreviations(block);
            ta.insertText(ta.getCaretPosition(), expandedBlock);
            Platform.runLater(ta::requestFocus);
        });
    }

    public void formatCurrentArea() {
        Optional.ofNullable(getFocusedArea()).ifPresent(ta ->
                ta.setText(IAMTextFormatUtil.autoFormat(ta.getText())));
    }

    public void clearAllTextAreas() {
        areas.forEach(TextArea::clear);
    }

    /**
     * Parses a multi-section template and appends the content to the corresponding text areas.
     */
    public void parseAndAppendTemplate(String templateContent) {
        if (templateContent == null || templateContent.isBlank()) return;

        String expandedContent = expandAbbreviations(templateContent);

        Map<String, TextArea> areaMap = new HashMap<>();
        for (int i = 0; i < TEXT_AREA_TITLES.length && i < areas.size(); i++) {
            areaMap.put(TEXT_AREA_TITLES[i], areas.get(i));
        }

        // Regex to split the content by section titles
        String patternString = "(?=(" + String.join("|", TEXT_AREA_TITLES)
                .replace(">", "\\>")
                .replace(" ", "\\s") + "))";
        Pattern pattern = Pattern.compile(patternString);
        String[] parts = pattern.split(expandedContent);

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
                            target.setText(target.getText().isBlank() ? body : target.getText() + "\n" + body);
                            sectionsLoaded++;
                        }
                    }
                    break;
                }
            }
        }

        // If no sections were matched, insert the whole block into the focused area
        if (sectionsLoaded == 0) {
            insertBlockIntoFocusedArea(expandedContent);
        }
    }

    // ================================ 
    // Abbreviation Expansion
    // ================================ 

    private boolean expandAbbreviationOnSpace(TextArea ta) {
        int caret = ta.getCaretPosition();
        String upToCaret = ta.getText(0, caret);
        int start = Math.max(upToCaret.lastIndexOf(' '), upToCaret.lastIndexOf('\n')) + 1;

        String word = upToCaret.substring(start).trim();
        if (!word.startsWith(":")) return false;

        String key = word.substring(1);
        String replacement = getAbbreviationReplacement(key);
        if (replacement == null) return false;

        Platform.runLater(() -> {
            ta.deleteText(start, caret);
            ta.insertText(start, replacement + " ");
        });
        return true;
    }

    private String expandAbbreviations(String text) {
        StringBuilder out = new StringBuilder();
        Pattern abbrevPattern = Pattern.compile(":([\\S]+)");
        Matcher matcher = abbrevPattern.matcher(text);

        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = getAbbreviationReplacement(key);
            matcher.appendReplacement(out, replacement != null ? Matcher.quoteReplacement(replacement) : matcher.group(0));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String getAbbreviationReplacement(String key) {
        if ("cd".equalsIgnoreCase(key)) {
            return LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        }
        return abbrevMap.get(key);
    }

    // ================================ 
    // Getters and Helpers
    // ================================ 

    private TextArea getFocusedArea() {
        return areas.stream().filter(TextArea::isFocused).findFirst().orElse(lastFocusedArea);
    }

    public void focusArea(int idx) {
        if (idx >= 0 && idx < areas.size()) {
            Platform.runLater(() -> areas.get(idx).requestFocus());
            lastFocusedArea = areas.get(idx);
        }
    }

    public List<TextArea> getTextAreas() {
        IAIMain.setTextAreaManager(new IAIFxTextAreaManager(areas));
        return Collections.unmodifiableList(this.areas);
    }

    private void showErrorAlert(String title, String header, String content) {
        Platform.runLater(() -> {
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle(title);
            errorAlert.setHeaderText(header);
            errorAlert.setContentText(content);
            errorAlert.showAndWait();
        });
    }

    private String safeTitle(int index) {
        return (index >= 0 && index < TEXT_AREA_TITLES.length) ? TEXT_AREA_TITLES[index] : "Area " + (index + 1);
    }
}