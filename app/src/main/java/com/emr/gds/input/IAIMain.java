package com.emr.gds.input;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Global holder for the TextAreaManager bridge.
 * Register once after the 10 TextAreas are created.
 */
public final class IAIMain {

    /** The single, thread-safe instance of the manager. */
    private static final AtomicReference<IAITextAreaManager> MANAGER = new AtomicReference<>(null);

    /** Private constructor to prevent instantiation of this utility class. */
    private IAIMain() {}

    /**
     * Registers the bridge.
     * This must be called exactly once after the 10 EMR TextAreas have been created.
     *
     * @param manager The non-null instance of IAITextAreaManager.
     * @throws NullPointerException if the provided manager is null.
     */
    public static void setTextAreaManager(IAITextAreaManager manager) {
        Objects.requireNonNull(manager, "TextAreaManager must not be null");
        MANAGER.set(manager);
    }

    /**
     * Retrieves the bridge.
     *
     * @return The registered instance of IAITextAreaManager.
     * @throws IllegalStateException if the manager has not been set.
     */
    public static IAITextAreaManager getTextAreaManager() {
        IAITextAreaManager m = MANAGER.get();
        if (m == null) {
            throw new IllegalStateException(
                "TextAreaManager not set. Call IAIMain.setTextAreaManager(...) after creating the 10 EMR TextAreas."
            );
        }
        return m;
    }

    /**
     * Provides an optional getter for the manager, avoiding an exception if it is not yet set.
     *
     * @return An Optional containing the manager, or an empty Optional if it is not set.
     */
    public static Optional<IAITextAreaManager> maybeManager() {
        return Optional.ofNullable(MANAGER.get());
    }

    /**
     * Clears the registered manager, typically used during application shutdown.
     */
    public static void clear() {
        MANAGER.set(null);
    }
}