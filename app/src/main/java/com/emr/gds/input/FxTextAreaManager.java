package com.emr.gds.input;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.util.List;
import java.util.Objects;

/**
 * JavaFX implementation of TextAreaManager.
 * Ensures all mutations run on the JavaFX Application Thread.
 */
public class FxTextAreaManager implements TextAreaManager {
    private final List<TextArea> areas;
    private int focusedIndex = AREA_CC;

    public FxTextAreaManager(List<TextArea> areas) {
        if (areas == null || areas.size() < areaCount()) {
            throw new IllegalArgumentException("areas must contain 10 TextAreas (CC, PI, ROS, PMH, S, O, PE, A, P, Comment)");
        }
        // null-check each element to be safe
        for (int i = 0; i < areaCount(); i++) {
            Objects.requireNonNull(areas.get(i), "TextArea at index " + i + " is null");
        }
        this.areas = areas;
    }

    @Override
    public void focusArea(int index) {
        if (!isValidIndex(index)) return;
        focusedIndex = index;
        runFx(() -> areas.get(focusedIndex).requestFocus());
    }

    @Override
    public void insertLineIntoFocusedArea(String line) {
        if (line == null || line.isEmpty()) return;
        runFx(() -> {
            TextArea ta = areas.get(focusedIndex);
            String add = line.endsWith("\n") ? line : line + "\n";
            ta.insertText(ta.getCaretPosition(), add);
        });
    }

    @Override
    public void insertBlockIntoFocusedArea(String block) {
        if (block == null || block.isEmpty()) return;
        runFx(() -> {
            TextArea ta = areas.get(focusedIndex);
            String add = block.endsWith("\n") ? block : block + "\n";
            ta.insertText(ta.getCaretPosition(), add);
        });
    }

    @Override
    public boolean isReady() {
        return areas != null && areas.size() >= areaCount();
    }

    // ---- Helpers ----
    private static void runFx(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }
}
