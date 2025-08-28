package com.emr.gds.main;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public class IttiaAppTextArea {
    // ---- Instance Variables ----
    private final List<TextArea> areas = new ArrayList<>(10);
    private TextArea lastFocusedArea = null;
    private final Map<String, String> abbrevMap;
    private ListProblemAction problemAction;
    
    // ---- Constants ----
    public static final String[] TEXT_AREA_TITLES = {
            "CC>", "PI>", "ROS>", "PMH>", "S>",
            "O>", "Physical Exam>", "A>", "P>", "Comment>"
    };
    
    // Light lemon gradient applied to the editable content of TextArea
    private static final String LEMON_GRADIENT_STYLE =
            "-fx-control-inner-background: linear-gradient(to bottom, #FFFFE0, #FFFACD);" +  // content area
            "-fx-background-insets: 0;" +
            "-fx-background-radius: 6;";
    
    // ---- Constructor ----
    public IttiaAppTextArea(Map<String, String> abbrevMap, ListProblemAction problemAction) {
        this.abbrevMap = abbrevMap;
        this.problemAction = problemAction;
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
            ta.setFont(Font.font("Monospaced", 13));
            ta.setPrefRowCount(10);
            ta.setPrefColumnCount(48);
            
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
            
            // 수정된 TextFormatter 적용 - TextFormatUtil 사용
            ta.setTextFormatter(new TextFormatter<>(TextFormatUtil.filterControlChars()));
            
            grid.add(ta, i % cols, i / cols);
            areas.add(ta);
        }
        
        return grid;
    }
    
    // ---- Text Actions ----
    public void insertTemplateIntoFocusedArea(ListButtonAction.TemplateLibrary t) {
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
        ta.setText(TextFormatUtil.autoFormat(ta.getText()));
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
        return this.areas;
    }
    
    // TextFormatUtil에 있는 기능과 중복이므로 제거하거나 단순히 위임
    public String getUniqueLines(String text) {
        return TextFormatUtil.getUniqueLines(text);
    }
    
    public static String normalizeLine(String s) {
        return TextFormatUtil.normalizeLine(s);
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