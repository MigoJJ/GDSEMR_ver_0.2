package com.emr.gds.input;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.util.List;
import java.util.Objects;

/**
 * JavaFX implementation of TextAreaManager.
 * Ensures all mutations run on the JavaFX Application Thread.
 */
public class IAIFxTextAreaManager implements IAITextAreaManager {
    private final List<TextArea> areas;
    private int focusedIndex = AREA_CC;

    public IAIFxTextAreaManager(List<TextArea> areas) {
        if (areas == null || areas.size() < areaCount()) {
            throw new IllegalArgumentException(
                "areas must contain 10 TextAreas (CC, PI, ROS, PMH, S, O, PE, A, P, Comment)"
            );
        }
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
        final String add = ensureTrailingNewline(normalizeNewlines(line));
        runFx(() -> {
            TextArea ta = areas.get(focusedIndex);
            ta.insertText(ta.getCaretPosition(), add);
        });
    }

    @Override
    public void insertBlockIntoFocusedArea(String block) {
        if (block == null || block.isEmpty()) return;
        final String add = ensureTrailingNewline(normalizeNewlines(block));
        runFx(() -> {
            TextArea ta = areas.get(focusedIndex);
            ta.insertText(ta.getCaretPosition(), add);
        });
    }

    @Override
    public boolean isReady() {
        if (areas == null || areas.size() < areaCount()) return false;
        for (int i = 0; i < areaCount(); i++) {
            if (areas.get(i) == null) return false;
        }
        return true;
    }

    // ---- Helpers ----
    private static void runFx(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    private static String normalizeNewlines(String s) {
        // Convert \r\n and \r to \n to match JavaFX TextArea behavior
        return s.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String ensureTrailingNewline(String s) {
        return s.endsWith("\n") ? s : (s + "\n");
    }
}
