package com.emr.gds.input;

public interface TextAreaManager {
    /** Focus a specific EMR text area (0..9). */
    void focusArea(int index);

    /** Insert a single line at the caret in the currently focused area. */
    void insertLineIntoFocusedArea(String line);

    /** Insert a multi-line block at the caret in the currently focused area. */
    void insertBlockIntoFocusedArea(String block);

    /** Same as insertBlockIntoFocusedArea; keep for API symmetry. */
    void insertBlockIntoFocusedAreaNoFocus(String block);

    /** True when underlying TextAreas are ready. */
    boolean isReady();
}
