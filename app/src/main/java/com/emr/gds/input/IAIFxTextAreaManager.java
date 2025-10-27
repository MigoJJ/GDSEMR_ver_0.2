package com.emr.gds.input;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.util.List;
import java.util.Objects;

/**
 * A JavaFX-specific implementation of the {@link IAITextAreaManager} interface.
 * This class ensures that all interactions with the UI (TextArea components) are performed safely on the JavaFX Application Thread.
 */
public class IAIFxTextAreaManager implements IAITextAreaManager {

    private final List<TextArea> textAreas;
    private int focusedIndex = AREA_CC; // Default to the first text area

    /**
     * Constructs a new manager for the given list of TextAreas.
     * @param textAreas A list of 10 non-null TextArea components.
     * @throws IllegalArgumentException if the list is null or does not contain the required number of areas.
     * @throws NullPointerException if any element in the list is null.
     */
    public IAIFxTextAreaManager(List<TextArea> textAreas) {
        Objects.requireNonNull(textAreas, "The list of text areas cannot be null.");
        if (textAreas.size() < areaCount()) {
            throw new IllegalArgumentException("The list must contain at least " + areaCount() + " text areas.");
        }
        for (int i = 0; i < areaCount(); i++) {
            Objects.requireNonNull(textAreas.get(i), "TextArea at index " + i + " cannot be null.");
        }
        this.textAreas = textAreas;
    }

    @Override
    public void focusArea(int index) {
        if (!isValidIndex(index)) return;
        focusedIndex = index;
        runOnFxThread(() -> textAreas.get(focusedIndex).requestFocus());
    }

    @Override
    public void insertLineIntoFocusedArea(String line) {
        if (line == null || line.isEmpty()) return;
        final String textToInsert = ensureTrailingNewline(normalizeNewlines(line));
        runOnFxThread(() -> {
            TextArea focusedArea = textAreas.get(focusedIndex);
            focusedArea.insertText(focusedArea.getCaretPosition(), textToInsert);
        });
    }

    @Override
    public void insertBlockIntoFocusedArea(String block) {
        if (block == null || block.isEmpty()) return;
        final String textToInsert = normalizeNewlines(block);
        runOnFxThread(() -> {
            TextArea focusedArea = textAreas.get(focusedIndex);
            focusedArea.insertText(focusedArea.getCaretPosition(), textToInsert);
        });
    }

    @Override
    public void appendTextToSection(int index, String text) {
        if (!isValidIndex(index) || text == null || text.isEmpty()) return;
        final String textToAppend = ensureTrailingNewline(normalizeNewlines(text));
        runOnFxThread(() -> textAreas.get(index).appendText(textToAppend));
    }

    @Override
    public boolean isReady() {
        // The readiness is confirmed at construction time.
        return true;
    }

    // --- Helper Methods ---

    /**
     * Ensures that a given Runnable is executed on the JavaFX Application Thread.
     * @param action The action to execute.
     */
    private static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    /**
     * Normalizes line endings in a string to use only the newline character (\n).
     */
    private static String normalizeNewlines(String s) {
        return s.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * Ensures that the given string ends with a newline character.
     */
    private static String ensureTrailingNewline(String s) {
        return s.endsWith("\n") ? s : s + "\n";
    }
}