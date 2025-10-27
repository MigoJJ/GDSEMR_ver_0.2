package com.emr.gds.input;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A final utility class that serves as a global, thread-safe holder for the single
 * instance of the {@link IAITextAreaManager}.
 * This provides a centralized access point for the text area manager bridge.
 */
public final class IAIMain {

    /**
     * The atomic reference holding the single instance of the text area manager.
     */
    private static final AtomicReference<IAITextAreaManager> MANAGER_INSTANCE = new AtomicReference<>(null);

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private IAIMain() {}

    /**
     * Registers the singleton instance of the text area manager.
     * This method should be called exactly once after the EMR text areas have been created.
     *
     * @param manager The non-null instance of IAITextAreaManager.
     * @throws NullPointerException if the provided manager is null.
     */
    public static void setTextAreaManager(IAITextAreaManager manager) {
        Objects.requireNonNull(manager, "The TextAreaManager instance cannot be null.");
        MANAGER_INSTANCE.set(manager);
    }

    /**
     * Retrieves the registered text area manager instance.
     *
     * @return The singleton instance of IAITextAreaManager.
     * @throws IllegalStateException if the manager has not yet been set.
     */
    public static IAITextAreaManager getTextAreaManager() {
        IAITextAreaManager manager = MANAGER_INSTANCE.get();
        if (manager == null) {
            throw new IllegalStateException("The TextAreaManager has not been set. Ensure IAIMain.setTextAreaManager() is called during application startup.");
        }
        return manager;
    }

    /**
     * Provides a safe, optional getter for the text area manager, which avoids throwing an exception.
     *
     * @return An {@link Optional} containing the manager if it has been set, or an empty Optional otherwise.
     */
    public static Optional<IAITextAreaManager> getManagerSafely() {
        return Optional.ofNullable(MANAGER_INSTANCE.get());
    }

    /**
     * Clears the registered manager instance. This is typically used during application shutdown to release resources.
     */
    public static void clearManager() {
        MANAGER_INSTANCE.set(null);
    }
}
