package com.emr.gds.input;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Global holder for the TextAreaManager bridge.
 * Set once after the 10 TextAreas are created (e.g., right after buildCenterAreas()).
 */
public final class IttiaAppMain {
    private static final AtomicReference<TextAreaManager> MANAGER = new AtomicReference<>(null);

    private IttiaAppMain() {}

    /** Register the bridge. Must be called after the 10 TextAreas exist. */
    public static void setTextAreaManager(TextAreaManager manager) {
        Objects.requireNonNull(manager, "TextAreaManager must not be null");
        MANAGER.set(manager);
    }

    /** Get the bridge or throw if missing. */
    public static TextAreaManager getTextAreaManager() {
        TextAreaManager m = MANAGER.get();
        if (m == null) {
            throw new IllegalStateException(
                "TextAreaManager not set. Call IttiaAppMain.setTextAreaManager(...) after creating the 10 EMR TextAreas."
            );
        }
        return m;
    }

    /** Optional getter without exception. */
    public static Optional<TextAreaManager> maybeManager() {
        return Optional.ofNullable(MANAGER.get());
    }
}
