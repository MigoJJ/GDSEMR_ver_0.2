package com.emr.gds.input;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Global holder for the TextAreaManager bridge.
 * Register once after the 10 TextAreas are created.
 */
public final class IAIMain {
    private static final AtomicReference<IAITextAreaManager> MANAGER = new AtomicReference<>(null);

    private IAIMain() {}

    /** Register the bridge. Must be called after the 10 TextAreas exist. */
    public static void setTextAreaManager(IAITextAreaManager manager) {
        Objects.requireNonNull(manager, "TextAreaManager must not be null");
        MANAGER.set(manager);
    }

    /** Get the bridge or throw if missing. */
    public static IAITextAreaManager getTextAreaManager() {
        IAITextAreaManager m = MANAGER.get();
        if (m == null) {
            throw new IllegalStateException(
                "TextAreaManager not set. Call IttiaAppMain.setTextAreaManager(...) after creating the 10 EMR TextAreas."
            );
        }
        return m;
    }

    /** Optional getter without exception. */
    public static Optional<IAITextAreaManager> maybeManager() {
        return Optional.ofNullable(MANAGER.get());
    }

    /** Clear the registered manager (e.g., app shutdown). */
    public static void clear() {
        MANAGER.set(null);
    }
}
