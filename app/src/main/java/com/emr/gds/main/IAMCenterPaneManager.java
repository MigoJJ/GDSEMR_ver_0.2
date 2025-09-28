package com.emr.gds.main;

import javafx.scene.control.TextArea;			
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages the central grid of TextAreas for the EMR application.
 * This class handles the creation, layout, and event handling for the note-taking areas.
 */
public class IAMCenterPaneManager {
    public static final String[] TEXT_AREA_TITLES = {
            "CC>", "PI>", "ROS>", "PMH>", "S>",
            "O>", "Physical Exam>", "A>", "P>", "Comment>"
    };

    // ---- Instance Variables ----
    private final List<TextArea> areas = new ArrayList<>(10);
    private final GridPane gridPane;
    private TextArea lastFocusedArea = null;

    private final Map<String, String> abbrevMap;
    private final IAMProblemAction problemAction;

    // ---- Constructor ----
    public IAMCenterPaneManager(Map<String, String> abbrevMap, IAMProblemAction problemAction) {
        this.abbrevMap = abbrevMap;
        this.problemAction = problemAction;
        this.gridPane = buildCenterAreas();
    }

    // ---- Public Methods ----
    /**
     * Returns the fully constructed GridPane UI component.
     * @return The GridPane containing all the TextAreas.
     */
    public GridPane getPane() {
        return gridPane;
    }

    /**
     * Returns the list of all TextArea components.
     * @return A List of TextAreas.
     */
    public List<TextArea> getAreas() {
        return areas;
    }

    /**
     * Gets the currently focused TextArea, or the last one that had focus.
     * Defaults to the first area if none have ever been focused.
     * @return The "active" TextArea, or null if the list is empty.
     */
    public TextArea getFocusedArea() {
        for (TextArea ta : areas) {
            if (ta.isFocused()) {
                return ta;
            }
        }
        if (lastFocusedArea != null) {
            return lastFocusedArea;
        }
        return areas.isEmpty() ? null : areas.get(0);
    }

    /**
     * Programmatically sets focus to a specific TextArea by its index.
     * @param idx The index (0-9) of the TextArea to focus.
     */
    public void focusArea(int idx) {
        if (idx >= 0 && idx < areas.size()) {
            areas.get(idx).requestFocus();
        }
    }

    /**
     * Inserts a block of text into the currently focused TextArea.
     * @param block The text to insert.
     */
    public void insertBlock(String block) {
        TextArea ta = getFocusedArea();
        if (ta == null) return;
        int caret = ta.getCaretPosition();
        ta.insertText(caret, block);
    }

    /**
     * Applies auto-formatting to the text in the currently focused TextArea.
     */
    public void formatFocusedArea() {
        TextArea ta = getFocusedArea();
        if (ta == null) return;
        ta.setText(IAMTextFormatUtil.autoFormat(ta.getText()));
    }

    /**
     * Clears the text from all managed TextAreas.
     */
    public void clearAll() {
        for (TextArea ta : areas) {
            ta.clear();
        }
    }

    // ---- Private Methods ----
    private GridPane buildCenterAreas() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        int rows = 5, cols = 2;

        for (int i = 0; i < rows * cols; i++) {
            TextArea ta = new TextArea();
            ta.setWrapText(true);
            ta.setFont(Font.font("Malgun Gothic", 11));
            ta.setPrefRowCount(8);
            ta.setPrefColumnCount(40);
            ta.setPromptText(TEXT_AREA_TITLES[i]);

            // Add focus listener to track the last focused area
            ta.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    lastFocusedArea = ta;
                }
            });

            // Update scratchpad on text change
            final int idx = i;
            ta.textProperty().addListener((obs, oldVal, newVal) ->
                    problemAction.updateAndRedrawScratchpad(TEXT_AREA_TITLES[idx], newVal));

            // Add abbreviation expansion logic
            addAbbreviationHandler(ta);

            ta.setTextFormatter(new TextFormatter<>(IAMTextFormatUtil.filterControlChars()));

            grid.add(ta, i % cols, i / cols);
            areas.add(ta);
        }
        return grid;
    }

    private void addAbbreviationHandler(TextArea ta) {
        ta.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SPACE) {
                int caret = ta.getCaretPosition();
                String text = ta.getText(0, caret);
                int lastSpace = text.lastIndexOf(' ');
                int lastNewline = text.lastIndexOf('\n');
                int start = Math.max(lastSpace, lastNewline) + 1;
                String word = text.substring(start);

                if (word.startsWith(":")) {
                    String key = word.substring(1);
                    String replacement = key.equals("cd")
                            ? LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                            : abbrevMap.get(key);
                    if (replacement != null) {
                        ta.deleteText(start, caret);
                        ta.insertText(start, replacement + " ");
                        event.consume(); // Consume ONLY when a replacement occurs
                    }
                }
            }
        });
    }
}