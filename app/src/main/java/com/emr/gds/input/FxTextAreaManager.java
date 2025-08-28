// Example file: com/emr/gds/main/FxTextAreaManager.java
package com.emr.gds.input;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import java.util.List;
import java.util.Objects;

public class FxTextAreaManager extends IttiaAppMain.TextAreaManager {
    private final List<TextArea> areas;
    private int focusedIndex = -1;

    public FxTextAreaManager(List<TextArea> areas) {
        this.areas = Objects.requireNonNull(areas);
        if (!areas.isEmpty()) focusedIndex = 0;
    }

    @Override public void focusArea(int index) {
        if (index < 0 || index >= areas.size()) return;
        focusedIndex = index;
        Platform.runLater(() -> {
            TextArea a = areas.get(index);
            a.requestFocus();
            a.positionCaret(a.getText().length());
        });
    }

    @Override public void insertLineIntoFocusedArea(String line) {
        if (focusedIndex < 0 || focusedIndex >= areas.size()) return;
        final String toAdd = (line == null ? "" : line) + System.lineSeparator();
        Platform.runLater(() -> areas.get(focusedIndex)
            .insertText(areas.get(focusedIndex).getCaretPosition(), toAdd));
    }

    @Override public void insertBlockIntoFocusedArea(String block) {
        if (focusedIndex < 0 || focusedIndex >= areas.size()) return;
        final String toAdd = (block == null ? "" : block);
        Platform.runLater(() -> areas.get(focusedIndex)
            .insertText(areas.get(focusedIndex).getCaretPosition(), toAdd));
    }
}
