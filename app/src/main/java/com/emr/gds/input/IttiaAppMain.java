package com.emr.gds.input;

public final class IttiaAppMain {
    private static TextAreaManager textAreaManager = new TextAreaManager(); // no-op default

    private IttiaAppMain() {}

    public static TextAreaManager getTextAreaManager() {
        return textAreaManager;
    }

    public static void setTextAreaManager(TextAreaManager manager) {
        if (manager != null) {
            textAreaManager = manager;
        }
    }

    /** API used by Swing/JFX helpers (FreqInputFrame, etc.) */
    public static class TextAreaManager {
        public void focusArea(int index) { /* no-op default */ }
        public void insertLineIntoFocusedArea(String line) { /* no-op default */ }
        public void insertBlockIntoFocusedArea(String block) { /* no-op default */ }
    }
}
