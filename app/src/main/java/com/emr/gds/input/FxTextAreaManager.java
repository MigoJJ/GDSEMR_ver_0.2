package com.emr.gds.input;

import javafx.scene.control.TextArea;

import java.util.List;

import static com.emr.gds.input.IttiaAppMain.runFx;

public class FxTextAreaManager implements TextAreaManager {
    private final List<TextArea> areas;
    private int focusedIndex = 0;

    public FxTextAreaManager(List<TextArea> areas) {
        if (areas == null || areas.size() < 10)
            throw new IllegalArgumentException("areas must contain 10 TextAreas (CC, PI, ROS, PMH, S, O, PE, A, P, Comment)");
        this.areas = areas;
    }

    @Override
    public void focusArea(int index) {
        if (index < 0 || index >= areas.size()) return;
        focusedIndex = index;
        runFx(() -> areas.get(focusedIndex).requestFocus());
    }

    @Override
    public void insertLineIntoFocusedArea(String line) {
        if (line == null || line.isEmpty()) return;
        runFx(() -> {
            TextArea ta = areas.get(focusedIndex);
            String add = line.endsWith("\n") ? line : (line + "\n");
            ta.insertText(ta.getCaretPosition(), add);
        });
    }

    @Override
    public void insertBlockIntoFocusedArea(String block) {
        if (block == null || block.isEmpty()) return;
        runFx(() -> {
            TextArea ta = areas.get(focusedIndex);
            String add = block.endsWith("\n") ? block : (block + "\n");
            ta.insertText(ta.getCaretPosition(), add);
        });
    }

    @Override
    public void insertBlockIntoFocusedAreaNoFocus(String block) {
        insertBlockIntoFocusedArea(block);
    }

    @Override
    public boolean isReady() {
        return areas != null && areas.size() >= 10;
    }
}
