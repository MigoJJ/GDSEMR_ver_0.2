package com.emr.gds.input;

/**
 * Bridge interface used by helper windows (e.g., FreqInputFrame) to write into the
 * main EMR TextAreas. Implementations must be JavaFX-thread safe.
 */
public interface TextAreaManager {

    // ---- Section indices (avoid magic numbers) ----
    int AREA_CC      = 0;
    int AREA_PI      = 1;
    int AREA_ROS     = 2;
    int AREA_PMH     = 3;
    int AREA_S       = 4;
    int AREA_O       = 5;
    int AREA_PE      = 6;
    int AREA_A       = 7;
    int AREA_P       = 8;
    int AREA_COMMENT = 9;

    /** Focus a specific EMR text area (0..9). */
    void focusArea(int index);

    /** Insert a single line at the caret in the currently focused area. */
    void insertLineIntoFocusedArea(String line);

    /** Insert a multi-line block at the caret in the currently focused area. */
    void insertBlockIntoFocusedArea(String block);

    /** True when underlying TextAreas are ready. */
    boolean isReady();

    // ---- Optional conveniences / defaults ----

    /** Same as insertBlockIntoFocusedArea; provided for API symmetry. */
    default void insertBlockIntoFocusedAreaNoFocus(String block) {
        insertBlockIntoFocusedArea(block);
    }

    /** Total EMR areas expected. */
    default int areaCount() { return 10; }

    /** Validate index range. */
    default boolean isValidIndex(int index) { return index >= 0 && index < areaCount(); }
}
