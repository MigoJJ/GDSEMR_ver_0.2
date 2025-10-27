package com.emr.gds.input;

/**
 * Defines the contract for a manager that handles interactions with the main EMR text areas.
 * This interface provides a bridge for helper windows (like IAIFreqFrame) to write text into the EMR fields
 * in a thread-safe manner, without direct coupling to the main application's UI components.
 */
public interface IAITextAreaManager {

    // --- Section Indices ---
    int AREA_CC = 0;      // Chief Complaint
    int AREA_PI = 1;      // Present Illness
    int AREA_ROS = 2;     // Review of Systems
    int AREA_PMH = 3;     // Past Medical History
    int AREA_S = 4;       // Subjective
    int AREA_O = 5;       // Objective
    int AREA_PE = 6;      // Physical Exam
    int AREA_A = 7;       // Assessment
    int AREA_P = 8;       // Plan
    int AREA_COMMENT = 9; // Comments/Miscellaneous

    /**
     * Sets focus to a specific EMR text area.
     * @param index The zero-based index of the text area to focus.
     */
    void focusArea(int index);

    /**
     * Inserts a single line of text at the current caret position in the focused text area.
     * @param line The line of text to insert.
     */
    void insertLineIntoFocusedArea(String line);

    /**
     * Inserts a block of text (which can be multi-line) at the current caret position in the focused text area.
     * @param block The block of text to insert.
     */
    void insertBlockIntoFocusedArea(String block);

    /**
     * Appends text to the end of a specific text area, regardless of the current focus.
     * @param index The index of the target text area.
     * @param text The text to append.
     */
    void appendTextToSection(int index, String text);

    /**
     * Checks if the text area manager is initialized and ready for use.
     * @return true if ready, false otherwise.
     */
    boolean isReady();

    // --- Default Convenience Methods ---

    /**
     * Returns the total number of text areas managed.
     * @return The count of text areas (typically 10).
     */
    default int areaCount() {
        return 10;
    }

    /**
     * Validates if a given index is within the valid range of managed text areas.
     * @param index The index to validate.
     * @return true if the index is valid, false otherwise.
     */
    default boolean isValidIndex(int index) {
        return index >= 0 && index < areaCount();
    }

    /**
     * Inserts a block of text into a specific text area, optionally moving focus first.
     * @param index The index of the target text area.
     * @param block The block of text to insert.
     * @param moveFocus If true, focuses the area before insertion.
     */
    default void insertBlockIntoArea(int index, String block, boolean moveFocus) {
        if (!isValidIndex(index)) return;
        if (moveFocus) {
            focusArea(index);
        }
        insertBlockIntoFocusedArea(block);
    }

    /**
     * Inserts a line of text into a specific text area, optionally moving focus first.
     * @param index The index of the target text area.
     * @param line The line of text to insert.
     * @param moveFocus If true, focuses the area before insertion.
     */
    default void insertLineIntoArea(int index, String line, boolean moveFocus) {
        if (!isValidIndex(index)) return;
        if (moveFocus) {
            focusArea(index);
        }
        insertLineIntoFocusedArea(line);
    }
}
