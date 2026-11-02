package com.boxoffice.desktop;

import static org.assertj.core.api.Assertions.fail;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.application.Platform;

/**
 * Runs bits of a test on the JavaFX thread, the way the real app runs clicks.
 * The pom points JavaFX at Monocle, a pretend screen, so no window appears.
 */
final class FxThread {

    private static boolean started;

    private FxThread() {
    }

    private static synchronized void start() {
        if (started) {
            return;
        }
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // Another test class started it first: fine.
        }
        // Keep JavaFX running between tests even though no window is open.
        Platform.setImplicitExit(false);
        started = true;
    }

    /** Runs the code on the JavaFX thread and waits for it to finish. */
    static void run(Runnable code) {
        get(() -> {
            code.run();
            return null;
        });
    }

    /** Works something out on the JavaFX thread and hands back the answer. */
    static <T> T get(Supplier<T> code) {
        start();
        CompletableFuture<T> answer = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                answer.complete(code.get());
            } catch (Throwable problem) {
                answer.completeExceptionally(problem);
            }
        });
        try {
            return answer.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // The code itself threw: pass on its own exception, so the test shows the real cause.
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AssertionError(e.getCause());
        } catch (TimeoutException e) {
            throw new AssertionError("The JavaFX thread didn't finish in 5 seconds", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    /** Checks the condition on the JavaFX thread until it's true, for up to 5 seconds. */
    static void waitUntil(BooleanSupplier condition) {
        long giveUp = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < giveUp) {
            if (get(condition::getAsBoolean)) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        fail("Waited 5 seconds and it still wasn't true");
    }
}
