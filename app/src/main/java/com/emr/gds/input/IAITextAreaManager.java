// File: app/src/main/java/com/emr/gds/input/IAITextAreaManager.java
package com.emr.gds.input;

/**
 * Bridge interface used by helper windows (e.g., FreqInputFrame) to write into the
 * main EMR TextAreas. Implementations must be JavaFX-thread safe.
 *
 * This interface defines the core contract for managing text areas in the EMR application,
 * providing a consistent way for different parts of the application to interact with
 * the input fields without direct coupling.
 */
public interface IAITextAreaManager {

    // ---- Section indices (avoid magic numbers for better readability) ----
    int AREA_CC      = 0; // Chief Complaint
    int AREA_PI      = 1; // Present Illness
    int AREA_ROS     = 2; // Review of Systems
    int AREA_PMH     = 3; // Past Medical History
    int AREA_S       = 4; // Subjective
    int AREA_O       = 5; // Objective
    int AREA_PE      = 6; // Physical Exam
    int AREA_A       = 7; // Assessment
    int AREA_P       = 8; // Plan
    int AREA_COMMENT = 9; // Comments/Miscellaneous

    /**
     * Focuses a specific EMR text area identified by its index (0 to areaCount()-1).
     * @param index The zero-based index of the text area to focus.
     */
    void focusArea(int index);

    /**
     * Inserts a single line of text at the caret position in the currently focused text area.
     * @param line The string line to insert.
     */
    void insertLineIntoFocusedArea(String line);

    /**
     * Inserts a multi-line block of text at the caret position in the currently focused text area.
     * @param block The string block to insert.
     */
    void insertBlockIntoFocusedArea(String block);

    /**
     * Checks if the underlying TextAreas managed by this manager are ready for interaction.
     * @return true if TextAreas are initialized and ready, false otherwise.
     */
    boolean isReady();

    // ---- Optional conveniences / defaults ----

    /**
     * Appends text to a specific text area.
     * @param index The index of the target text area.
     * @param text The string to append.
     */
    public static void appendTextToSection(String section, String text) {
    }
    static int getIndexForSection(String section) {
		// TODO Auto-generated method stub
		return 0;
	}

	default void insertBlockIntoFocusedAreaNoFocus(String block) {
        insertBlockIntoFocusedArea(block);
    }

    /**
     * Returns the total number of EMR text areas expected to be managed.
     * @return The count of text areas, typically 10.
     */
    default int areaCount() { return 10; }

    /**
     * Validates if the given index falls within the valid range of managed text areas.
     * @param index The index to validate.
     * @return true if the index is valid (0 to areaCount()-1), false otherwise.
     */
    default boolean isValidIndex(int index) { return index >= 0 && index < areaCount(); }

    /**
     * Inserts a block of text into a specific text area.
     * Optionally moves focus to that area before insertion.
     * @param index The index of the target text area.
     * @param block The string block to insert.
     * @param moveFocus If true, focus will be moved to this area before insertion.
     */
    default void insertBlockIntoArea(int index, String block, boolean moveFocus) {
        if (!isValidIndex(index)) return;
        if (moveFocus) focusArea(index);
        insertBlockIntoFocusedArea(block);
    }

    /**
     * Inserts a line of text into a specific text area.
     * Optionally moves focus to that area before insertion.
     * @param index The index of the target text area.
     * @param line The string line to insert.
     * @param moveFocus If true, focus will be moved to this area before insertion.
     */
    default void insertLineIntoArea(int index, String line, boolean moveFocus) {
        if (!isValidIndex(index)) return;
        if (moveFocus) focusArea(index);
        insertLineIntoFocusedArea(line);
    }

    void appendTextToSection(int section, String text);	}