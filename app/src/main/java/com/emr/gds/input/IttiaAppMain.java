package com.emr.gds.input;

import javafx.application.Platform;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class IttiaAppMain {
    private static final AtomicReference<FxTextAreaManager> MANAGER = new AtomicReference<>(null);

    private IttiaAppMain() {}

    public static void setTextAreaManager(FxTextAreaManager manager) {
        Objects.requireNonNull(manager, "TextAreaManager must not be null");
        MANAGER.set(manager);
    }

    public static FxTextAreaManager getTextAreaManager() {
        FxTextAreaManager m = MANAGER.get();
        if (m == null) {
            throw new IllegalStateException("TextAreaManager is not set. Call IttiaAppMain.setTextAreaManager(...) after creating the 10 EMR TextAreas.");
        }
        return m;
    }

    public static Optional<FxTextAreaManager> maybeManager() {
        return Optional.ofNullable(MANAGER.get());
    }

    /** Utility for safe FX-thread execution inside manager implementations */
    public static void runFx(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }
}
