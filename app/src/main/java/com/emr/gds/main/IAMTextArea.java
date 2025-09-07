package com.emr.gds.main;

import javafx.application.Platform;			
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

public class IAMTextArea {
    // ---- Instance Variables ----
    private final List<TextArea> areas = new ArrayList<>(10);
    private TextArea lastFocusedArea = null;
    private final Map<String, String> abbrevMap;
    private IAMProblemAction problemAction;
    
    // Double-click handlers for each TextArea
    private final Map<Integer, TextAreaDoubleClickHandler> doubleClickHandlers = new HashMap<>();
    
    // ---- Constants ----
    public static final String[] TEXT_AREA_TITLES = {
            "CC>", "PI>", "ROS>", "PMH>", "S>",
            "O>", "Physical Exam>", "A>", "P>", "Comment>"
    };
       
 // Also add a focused style for better UX
    private static final String LEMON_GRADIENT_STYLE = 
            "-fx-control-inner-background: #F8FAFC;" +  // Slightly tinted when focused
            "-fx-text-fill: #1A202C;" +
            "-fx-border-color: #3B82F6;" +              // Blue border when focused
            "-fx-border-width: 2;" +
            "-fx-background-insets: 0;" +
            "-fx-background-radius: 6;" +
            "-fx-border-radius: 6;";
    
    // ---- Interface for Double-Click Handlers ----
    @FunctionalInterface
    public interface TextAreaDoubleClickHandler {
        void handle(TextArea textArea, int areaIndex);
    }
    
    // ---- Constructor ----
    public IAMTextArea(Map<String, String> abbrevMap, IAMProblemAction problemAction) {
        this.abbrevMap = abbrevMap;
        this.problemAction = problemAction;
        initializeDoubleClickHandlers();
    }
    
    // ---- Initialize Double-Click Handlers ----
    private void initializeDoubleClickHandlers() {
        // CC> - Chief Complaint Handler
        doubleClickHandlers.put(0, (textArea, index) -> executeChiefComplaintHandler(textArea, index));
        
        // PI> - Present Illness Handler
        doubleClickHandlers.put(1, (textArea, index) -> executePresentIllnessHandler(textArea, index));
        
        // ROS> - Review of Systems Handler
        doubleClickHandlers.put(2, (textArea, index) -> executeReviewOfSystemsHandler(textArea, index));
        
        // PMH> - Past Medical History Handler
        doubleClickHandlers.put(3, (textArea, index) -> executePastMedicalHistoryHandler(textArea, index));
        
        // S> - Subjective Handler
        doubleClickHandlers.put(4, (textArea, index) -> executeSubjectiveHandler(textArea, index));
        
        // O> - Objective Handler
        doubleClickHandlers.put(5, (textArea, index) -> executeObjectiveHandler(textArea, index));
        
        // Physical Exam> - Physical Examination Handler
        doubleClickHandlers.put(6, (textArea, index) -> executePhysicalExamHandler(textArea, index));
        
        // A> - Assessment Handler
        doubleClickHandlers.put(7, (textArea, index) -> executeAssessmentHandler(textArea, index));
        
        // P> - Plan Handler
        doubleClickHandlers.put(8, (textArea, index) -> executePlanHandler(textArea, index));
        
        // Comment> - Comment Handler
        doubleClickHandlers.put(9, (textArea, index) -> executeCommentHandler(textArea, index));
    }
    
    // ---- UI Component Builders ----
    public GridPane buildCenterAreas() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        int rows = 5, cols = 2;
        
        for (int i = 0; i < rows * cols; i++) {
            TextArea ta = new TextArea();
            ta.setWrapText(true);
            ta.setFont(Font.font("Monospaced", 11));
            ta.setPrefRowCount(10);
            ta.setPrefColumnCount(55);
            
            // ✨ Apply light lemon gradient inside each TextArea
            ta.setStyle(LEMON_GRADIENT_STYLE);
            
            String title = (i < TEXT_AREA_TITLES.length) ? TEXT_AREA_TITLES[i] : "Area " + (i + 1);
            ta.setPromptText(title);
            
            // Add listeners for focus and text changes
            ta.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    lastFocusedArea = ta;
                }
            });
            
            final int idx = i;
            if (idx < TEXT_AREA_TITLES.length) {
                ta.textProperty().addListener((obs, oldVal, newVal) ->
                        problemAction.updateAndRedrawScratchpad(TEXT_AREA_TITLES[idx], newVal));
            }
            
            // Handle abbreviation expansion on space key press
            ta.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.SPACE) {
                    int caret = ta.getCaretPosition();
                    String text = ta.getText(0, caret);
                    int start = Math.max(text.lastIndexOf(' '), text.lastIndexOf('\n')) + 1;
                    String word = text.substring(start);
                    
                    if (word.startsWith(":")) {
                        String key = word.substring(1);
                        String replacement = "cd".equals(key) ?
                                LocalDate.now().format(DateTimeFormatter.ISO_DATE) :
                                abbrevMap.get(key);
                        
                        if (replacement != null) {
                            ta.deleteText(start, caret);
                            ta.insertText(start, replacement + " ");
                            event.consume();
                        }
                    }
                }
            });
            
            // ✨ Add double-click event handler
            ta.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    handleDoubleClick(ta, idx);
                    event.consume();
                }
            });
            
            // 수정된 TextFormatter 적용 - TextFormatUtil 사용
            ta.setTextFormatter(new TextFormatter<>(IAMTextFormatUtil.filterControlChars()));
            
            grid.add(ta, i % cols, i / cols);
            areas.add(ta);
        }
        
        return grid;
    }
    
    // ---- Double-Click Event Handling ----
    private void handleDoubleClick(TextArea textArea, int areaIndex) {
        TextAreaDoubleClickHandler handler = doubleClickHandlers.get(areaIndex);
        if (handler != null) {
            try {
                System.out.println("Double-click detected on " + TEXT_AREA_TITLES[areaIndex] + " (Area " + areaIndex + ")");
                handler.handle(textArea, areaIndex);
            } catch (Exception e) {
                System.err.println("Error executing double-click handler for area " + areaIndex + ": " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("No handler registered for area " + areaIndex);
        }
    }
    
    // ---- Individual Double-Click Handler Implementations ----
    
    private void executeChiefComplaintHandler(TextArea textArea, int index) {
        System.out.println("Executing Chief Complaint Handler...");
        try {
            // Launch Chief Complaint specialized class
            ChiefComplaintEditor ccEditor = new ChiefComplaintEditor(textArea);
            ccEditor.show();
        } catch (Exception e) {
            System.err.println("Failed to launch Chief Complaint Editor: " + e.getMessage());
            showDefaultDoubleClickAction("Chief Complaint", textArea, index);
        }
    }
    
    private void executePresentIllnessHandler(TextArea textArea, int index) {
        System.out.println("Executing Present Illness Handler...");
        try {
            // Try to use reflection to load the class dynamically
            Class<?> editorClass = Class.forName("com.emr.gds.main.PresentIllnessEditor");
            Object editor = editorClass.getConstructor(TextArea.class).newInstance(textArea);
            editorClass.getMethod("show").invoke(editor);
        } catch (ClassNotFoundException e) {
            System.out.println("PresentIllnessEditor class not found, using default action");
            showDefaultDoubleClickAction("Present Illness", textArea, index);
        } catch (Exception e) {
            System.err.println("Failed to launch Present Illness Editor: " + e.getMessage());
            showDefaultDoubleClickAction("Present Illness", textArea, index);
        }
    }
    
    private void executeReviewOfSystemsHandler(TextArea textArea, int index) {
        System.out.println("Executing Review of Systems Handler...");
        try {
            Class<?> editorClass = Class.forName("com.emr.gds.main.ReviewOfSystemsEditor");
            Object editor = editorClass.getConstructor(TextArea.class).newInstance(textArea);
            editorClass.getMethod("show").invoke(editor);
        } catch (ClassNotFoundException e) {
            System.out.println("ReviewOfSystemsEditor class not found, using default action");
            showDefaultDoubleClickAction("Review of Systems", textArea, index);
        } catch (Exception e) {
            System.err.println("Failed to launch Review of Systems Editor: " + e.getMessage());
            showDefaultDoubleClickAction("Review of Systems", textArea, index);
        }
    }
    
    
    private void executePastMedicalHistoryHandler(TextArea textArea, int index) {
        System.out.println("Executing Past Medical History Handler...");
        try {
            // Get the current TextAreaManager from IttiaAppMain
            IAITextAreaManager manager = IAIMain.getTextAreaManager();
            EMRPMH pmhDialog = new EMRPMH(manager);
            pmhDialog.setVisible(true);
        } catch (Exception e) {
            System.err.println("Failed to launch PMH Editor: " + e.getMessage());
            showDefaultDoubleClickAction("Past Medical History", textArea, index);
        }
    }
    
    private void executeSubjectiveHandler(TextArea textArea, int index) {
        System.out.println("Executing Subjective Handler...");
        try {
            Class<?> editorClass = Class.forName("com.emr.gds.main.SubjectiveEditor");
            Object editor = editorClass.getConstructor(TextArea.class).newInstance(textArea);
            editorClass.getMethod("show").invoke(editor);
        } catch (ClassNotFoundException e) {
            System.out.println("SubjectiveEditor class not found, using default action");
            showDefaultDoubleClickAction("Subjective", textArea, index);
        } catch (Exception e) {
            System.err.println("Failed to launch Subjective Editor: " + e.getMessage());
            showDefaultDoubleClickAction("Subjective", textArea, index);
        }
    }
    
    private void executeObjectiveHandler(TextArea textArea, int index) {
        System.out.println("Executing Objective Handler...");
        try {
            Class<?> editorClass = Class.forName("com.emr.gds.main.ObjectiveEditor");
            Object editor = editorClass.getConstructor(TextArea.class).newInstance(textArea);
            editorClass.getMethod("show").invoke(editor);
        } catch (ClassNotFoundException e) {
            System.out.println("ObjectiveEditor class not found, using default action");
            showDefaultDoubleClickAction("Objective", textArea, index);
        } catch (Exception e) {
            System.err.println("Failed to launch Objective Editor: " + e.getMessage());
            showDefaultDoubleClickAction("Objective", textArea, index);
        }
    }
    
    private void executePhysicalExamHandler(TextArea textArea, int index) {
        System.out.println("Executing Physical Exam Handler...");
        try {
            Class<?> editorClass = Class.forName("com.emr.gds.main.PhysicalExamEditor");
            Object editor = editorClass.getConstructor(TextArea.class).newInstance(textArea);
            editorClass.getMethod("show").invoke(editor);
        } catch (ClassNotFoundException e) {
            System.out.println("PhysicalExamEditor class not found, using default action");
            showDefaultDoubleClickAction("Physical Exam", textArea, index);
        } catch (Exception e) {
            System.err.println("Failed to launch Physical Exam Editor: " + e.getMessage());
            showDefaultDoubleClickAction("Physical Exam", textArea, index);
        }
    }
    
    private void executeAssessmentHandler(TextArea textArea, int index) {
        System.out.println("Executing Assessment Handler...");
        try {
            Class<?> editorClass = Class.forName("com.emr.gds.main.AssessmentEditor");
            Object editor = editorClass.getConstructor(TextArea.class).newInstance(textArea);
            editorClass.getMethod("show").invoke(editor);
        } catch (ClassNotFoundException e) {
            System.out.println("AssessmentEditor class not found, using default action");
            showDefaultDoubleClickAction("Assessment", textArea, index);
        } catch (Exception e) {
            System.err.println("Failed to launch Assessment Editor: " + e.getMessage());
            showDefaultDoubleClickAction("Assessment", textArea, index);
        }
    }
    
    private void executePlanHandler(TextArea textArea, int index) {
        System.out.println("Executing Plan Handler...");
        try {
            Class<?> editorClass = Class.forName("com.emr.gds.main.PlanEditor");
            Object editor = editorClass.getConstructor(TextArea.class).newInstance(textArea);
            editorClass.getMethod("show").invoke(editor);
        } catch (ClassNotFoundException e) {
            System.out.println("PlanEditor class not found, using default action");
            showDefaultDoubleClickAction("Plan", textArea, index);
        } catch (Exception e) {
            System.err.println("Failed to launch Plan Editor: " + e.getMessage());
            showDefaultDoubleClickAction("Plan", textArea, index);
        }
    }
    
    private void executeCommentHandler(TextArea textArea, int index) {
        System.out.println("Executing Comment Handler...");
        try {
            Class<?> editorClass = Class.forName("com.emr.gds.main.CommentEditor");
            Object editor = editorClass.getConstructor(TextArea.class).newInstance(textArea);
            editorClass.getMethod("show").invoke(editor);
        } catch (ClassNotFoundException e) {
            System.out.println("CommentEditor class not found, using default action");
            showDefaultDoubleClickAction("Comment", textArea, index);
        } catch (Exception e) {
            System.err.println("Failed to launch Comment Editor: " + e.getMessage());
            showDefaultDoubleClickAction("Comment", textArea, index);
        }
    }
    
    // ---- Fallback Action ----
    private void showDefaultDoubleClickAction(String sectionName, TextArea textArea, int index) {
        String message = String.format("Double-clicked on %s (Area %d)\nCurrent text length: %d characters", 
            sectionName, index + 1, textArea.getText().length());
        
        // You can replace this with a proper dialog or notification
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Double-Click Action");
            alert.setHeaderText("Section: " + sectionName);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    // ---- Configuration Methods ----
    
    /**
     * Register a custom double-click handler for a specific area
     * @param areaIndex The index of the TextArea (0-9)
     * @param handler The handler to execute on double-click
     */
    public void setDoubleClickHandler(int areaIndex, TextAreaDoubleClickHandler handler) {
        if (areaIndex >= 0 && areaIndex < areas.size()) {
            doubleClickHandlers.put(areaIndex, handler);
        } else {
            throw new IllegalArgumentException("Area index must be between 0 and " + (areas.size() - 1));
        }
    }
    
    /**
     * Remove double-click handler for a specific area
     * @param areaIndex The index of the TextArea
     */
    public void removeDoubleClickHandler(int areaIndex) {
        doubleClickHandlers.remove(areaIndex);
    }
    
    /**
     * Get the current double-click handler for an area
     * @param areaIndex The index of the TextArea
     * @return The current handler, or null if none is set
     */
    public TextAreaDoubleClickHandler getDoubleClickHandler(int areaIndex) {
        return doubleClickHandlers.get(areaIndex);
    }
    
    // ---- Text Actions ----
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
        
        int caret = ta.getCaretPosition();
        ta.insertText(caret, block);
        Platform.runLater(ta::requestFocus);
    }
    
    public void formatCurrentArea() {
        // Get the TextArea that currently has focus.
        TextArea ta = getFocusedArea();
        // If no TextArea is focused, exit the method.
        if (ta == null) {
            return;
        }
        
        // TextFormatUtil의 autoFormat 메서드 사용 (중복 제거)
        ta.setText(IAMTextFormatUtil.autoFormat(ta.getText()));
    }
    
    public void clearAllTextAreas() {
        areas.forEach(TextArea::clear);
    }
    
    // ---- Helper and Utility Methods ----
    private TextArea getFocusedArea() {
        for (TextArea ta : areas) {
            if (ta.isFocused()) {
                return ta;
            }
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
    
    // TextFormatUtil에 있는 기능과 중복이므로 제거하거나 단순히 위임
    public String getUniqueLines(String text) {
        return IAMTextFormatUtil.getUniqueLines(text);
    }
    
    public static String normalizeLine(String s) {
        return IAMTextFormatUtil.normalizeLine(s);
    }
    
    /**
     * Parses a template string and appends its content to the corresponding TextAreas.
     * The template format is expected to use headers like "CC>", "PI>", etc.
     * @param templateContent The full string content of the template.
     */
    public void parseAndAppendTemplate(String templateContent) {
        if (templateContent == null || templateContent.isBlank()) {
            return;
        }
        
        // Create a map of titles to TextAreas for easy lookup
        Map<String, TextArea> areaMap = new HashMap<>();
        for (int i = 0; i < TEXT_AREA_TITLES.length && i < areas.size(); i++) {
            areaMap.put(TEXT_AREA_TITLES[i], areas.get(i));
        }
        
        // Regex to split the content by our headers (CC>, PI>, etc.)
        // This pattern uses a "positive lookahead" to split *before* the delimiter, keeping it.
        String patternString = "(?=(" + String.join("|", TEXT_AREA_TITLES)
                .replace(">", "\\>")
                .replace(" ", "\\s") + "))";
        Pattern pattern = Pattern.compile(patternString);
        String[] parts = pattern.split(templateContent);
        
        int sectionsLoaded = 0;
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            
            // Find which section this part belongs to
            for (String title : TEXT_AREA_TITLES) {
                if (part.startsWith(title)) {
                    TextArea targetArea = areaMap.get(title);
                    if (targetArea != null) {
                        // Get content, which is everything after the title line
                        String contentToAppend = part.substring(title.length()).trim();
                        if (!contentToAppend.isEmpty()) {
                            // Append with a newline for separation if the area is not empty
                            if (targetArea.getText() != null && !targetArea.getText().isBlank()) {
                                targetArea.appendText("\n" + contentToAppend);
                            } else {
                                targetArea.setText(contentToAppend);
                            }
                            sectionsLoaded++;
                        }
                    }
                    break; // Move to the next part
                }
            }
        }
        
        if (sectionsLoaded > 0) {
            // showToast("Template content loaded into " + sectionsLoaded + " section(s).");
        } else {
            // If no sections matched, append the whole template to the focused area
            insertBlockIntoFocusedArea(templateContent);
            // showToast("Template format not recognized. Pasted into focused area.");
        }
    }
}