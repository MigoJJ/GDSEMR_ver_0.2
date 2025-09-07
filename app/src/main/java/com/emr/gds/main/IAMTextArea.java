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
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import com.emr.gds.input.IAIFxTextAreaManager;
import com.emr.gds.input.IAIMain;
import com.emr.gds.input.IAITextAreaManager;
import com.emr.gds.soap.ChiefComplaintEditor;
import com.emr.gds.soap.EMRPMH;

/**
 * Manages the central text areas in the EMR application, handling UI creation,
 * abbreviation expansion, double-click actions, and text insertions/updates.
 * Ensures abbreviations are expanded when text is updated from external classes.
 */
public class IAMTextArea {
    // ================================
    // CONSTANTS
    // ================================
    public static final String[] TEXT_AREA_TITLES = {
            "CC>", "PI>", "ROS>", "PMH>", "S>",
            "O>", "Physical Exam>", "A>", "P>", "Comment>"
    };

    private static final String FOCUSED_LEMON_GRADIENT_STYLE_SOFT =
            "-fx-background-color: linear-gradient(to bottom, #FEFBEB, #FEF3C7);" +
            "-fx-text-fill: #1E293B;" +
            "-fx-border-color: #60A5FA;" +
            "-fx-border-width: 2;" +
            "-fx-background-insets: 0;" +
            "-fx-background-radius: 6;" +
            "-fx-border-radius: 6;";

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
        this.abbrevMap = abbrevMap;
        this.problemAction = problemAction;
        initializeDoubleClickHandlers();
    }

    // ================================
    // INITIALIZATION METHODS
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

    // ================================
    // UI BUILDERS
    // ================================
    public GridPane buildCenterAreas() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        int rows = 5, cols = 2;

        for (int i = 0; i < rows * cols; i++) {
            TextArea ta = createTextArea(i);
            grid.add(ta, i % cols, i / cols);
            areas.add(ta);
        }

        return grid;
    }

    private TextArea createTextArea(int index) {
        TextArea ta = new TextArea();
        ta.setWrapText(true);
        ta.setFont(Font.font("Monospaced", 11));
        ta.setPrefRowCount(11);
        ta.setPrefColumnCount(58);
        ta.setStyle(FOCUSED_LEMON_GRADIENT_STYLE_SOFT);

        String title = (index < TEXT_AREA_TITLES.length) ? TEXT_AREA_TITLES[index] : "Area " + (index + 1);
        ta.setPromptText(title);

        // Focus listener
        ta.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) lastFocusedArea = ta;
        });

        // Scratchpad update listener
        if (index < TEXT_AREA_TITLES.length) {
            ta.textProperty().addListener((obs, oldVal, newVal) ->
                    problemAction.updateAndRedrawScratchpad(TEXT_AREA_TITLES[index], newVal));
        }

        // Abbreviation expansion on space key press
        ta.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SPACE) {
                expandAbbreviationOnKeyPress(ta);
                event.consume();
            }
        });

        // Double-click handler
        ta.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                handleDoubleClick(ta, index);
                event.consume();
            }
        });

        // Text formatter to filter control characters
        ta.setTextFormatter(new TextFormatter<>(IAMTextFormatUtil.filterControlChars()));

        return ta;
    }

    // ================================
    // DOUBLE-CLICK HANDLING
    // ================================
    private void handleDoubleClick(TextArea textArea, int areaIndex) {
        TextAreaDoubleClickHandler handler = doubleClickHandlers.get(areaIndex);
        if (handler != null) {
            try {
                System.out.println("Double-click detected on " + TEXT_AREA_TITLES[areaIndex] + " (Area " + areaIndex + ")");
                handler.handle(textArea, areaIndex);
            } catch (Exception e) {
                System.err.println("Error executing double-click handler for area " + areaIndex + ": " + e.getMessage());
                e.printStackTrace();
                showErrorAlert("Handler Error", "Failed to execute handler for " + TEXT_AREA_TITLES[areaIndex], e.getMessage());
            }
        } else {
            System.out.println("No handler registered for area " + areaIndex);
        }
    }

    // Individual handlers (refactored for consistency with error handling)
    private void executeChiefComplaintHandler(TextArea textArea, int index) {
        System.out.println("Executing Chief Complaint Handler for TextArea at index: " + index);
        try {
            ChiefComplaintEditor ccEditor = new ChiefComplaintEditor(textArea);
            ccEditor.showAndWait();
            System.out.println("Chief Complaint Editor closed successfully.");
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
        System.out.println("Executing Past Medical History Handler...");
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

    // Generalized method for reflection-based editors
    private void executeReflectionBasedEditor(String className, String sectionName, TextArea textArea, int index) {
        System.out.println("Executing " + sectionName + " Handler...");
        try {
            Class<?> editorClass = Class.forName(className);
            Object editor = editorClass.getConstructor(TextArea.class).newInstance(textArea);
            editorClass.getMethod("show").invoke(editor);
        } catch (ClassNotFoundException e) {
            System.out.println(sectionName + "Editor class not found, using default action");
            showDefaultDoubleClickAction(sectionName, textArea, index);
        } catch (Exception e) {
            handleEditorException(sectionName, textArea, index, e);
        }
    }

    private void handleEditorException(String sectionName, TextArea textArea, int index, Exception e) {
        System.err.println("Failed to launch " + sectionName + " Editor: " + e.getMessage());
        e.printStackTrace();
        showErrorAlert("Editor Error", "Failed to open " + sectionName + " Editor", e.getMessage() + "\n\nFalling back to default editor...");
        showDefaultDoubleClickAction(sectionName, textArea, index);
    }

    private void showDefaultDoubleClickAction(String sectionName, TextArea textArea, int index) {
        String message = String.format("Double-clicked on %s (Area %d)\nCurrent text length: %d characters",
                sectionName, index + 1, textArea.getText().length());
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Double-Click Action");
            alert.setHeaderText("Section: " + sectionName);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // ================================
    // CONFIGURATION METHODS
    // ================================
    public void setDoubleClickHandler(int areaIndex, TextAreaDoubleClickHandler handler) {
        if (areaIndex >= 0 && areaIndex < areas.size()) {
            doubleClickHandlers.put(areaIndex, handler);
        } else {
            throw new IllegalArgumentException("Area index must be between 0 and " + (areas.size() - 1));
        }
    }

    public void removeDoubleClickHandler(int areaIndex) {
        doubleClickHandlers.remove(areaIndex);
    }

    public TextAreaDoubleClickHandler getDoubleClickHandler(int areaIndex) {
        return doubleClickHandlers.get(areaIndex);
    }

    // ================================
    // TEXT ACTIONS
    // ================================
    public void insertTemplateIntoFocusedArea(IAMButtonAction.TemplateLibrary t) {
        insertBlockIntoFocusedArea(t.body());
    }

    public void insertLineIntoFocusedArea(String line) {
        String insert = line.endsWith("\n") ? line : line + "\n";
        insertBlockIntoFocusedArea(insert);
    }

    public void insertBlockIntoFocusedArea(String block) {
        TextArea ta = getFocusedArea();
        if (ta == null) return;

        // Expand abbreviations before inserting (for updates from other classes)
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

        // Expand abbreviations in the entire template content first
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
            part = part.trim();
            if (part.isEmpty()) continue;

            for (String title : TEXT_AREA_TITLES) {
                if (part.startsWith(title)) {
                    TextArea targetArea = areaMap.get(title);
                    if (targetArea != null) {
                        String contentToAppend = part.substring(title.length()).trim();
                        if (!contentToAppend.isEmpty()) {
                            if (targetArea.getText() != null && !targetArea.getText().isBlank()) {
                                targetArea.appendText("\n" + contentToAppend);
                            } else {
                                targetArea.setText(contentToAppend);
                            }
                            sectionsLoaded++;
                        }
                    }
                    break;
                }
            }
        }

        if (sectionsLoaded > 0) {
            // showToast("Template content loaded into " + sectionsLoaded + " section(s).");
        } else {
            insertBlockIntoFocusedArea(templateContent);
            // showToast("Template format not recognized. Pasted into focused area.");
        }
    }

    // ================================
    // ABBREVIATION EXPANSION
    // ================================
    private void expandAbbreviationOnKeyPress(TextArea ta) {
        int caret = ta.getCaretPosition();
        String text = ta.getText(0, caret);
        int start = Math.max(text.lastIndexOf(' '), text.lastIndexOf('\n')) + 1;
        String word = text.substring(start);

        if (word.startsWith(":")) {
            String key = word.substring(1);
            String replacement = getAbbreviationReplacement(key);
            if (replacement != null) {
                ta.deleteText(start, caret);
                ta.insertText(start, replacement + " ");
            }
        }
    }

    /**
     * Expands abbreviations in the given text. Scans for words starting with ':' 
     * and replaces them if a matching key exists.
     * Used for text updates from other classes.
     */
    private String expandAbbreviations(String text) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ':' && (i == 0 || Character.isWhitespace(text.charAt(i - 1)))) {
                // Potential abbreviation start
                int start = i;
                i++;
                StringBuilder key = new StringBuilder();
                while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
                    key.append(text.charAt(i));
                    i++;
                }
                String replacement = getAbbreviationReplacement(key.toString());
                if (replacement != null) {
                    sb.append(replacement);
                } else {
                    sb.append(text.substring(start, i));
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private String getAbbreviationReplacement(String key) {
        if ("cd".equals(key)) {
            return LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        }
        return abbrevMap.get(key);
    }

    // ================================
    // HELPER METHODS
    // ================================
    private TextArea getFocusedArea() {
        for (TextArea ta : areas) {
            if (ta.isFocused()) return ta;
        }
        return lastFocusedArea != null ? lastFocusedArea : (areas.isEmpty() ? null : areas.get(0));
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
}