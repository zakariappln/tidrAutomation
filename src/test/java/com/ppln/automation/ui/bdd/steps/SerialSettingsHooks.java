package com.ppln.automation.ui.bdd.steps;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serialises scenarios tagged {@code @serial-settings}: they change state every session sees
 * (language, timezone, RPZ actions, syslog, the shared fixture user's profile, ...), so no two of
 * them may overlap. Each such scenario records the original value before changing it and restores
 * it in cleanup, via {@code UiScenarioContext#restoreAfterwards}.
 *
 * <p>Ordering: acquired before {@link SerialAuthHooks} ({@code -110} &lt; {@code -100}) and
 * released after it (Cucumber runs {@code @After} hooks in descending order), and released only
 * after {@code Hooks#runCleanups} has restored the originals. A semaphore rather than a lock,
 * because nothing guarantees the release runs on the acquiring thread. Lock order is always
 * settings, then login guard, so the two can never deadlock.
 */
public class SerialSettingsHooks {

    private static final Logger log = LoggerFactory.getLogger(SerialSettingsHooks.class);
    private static final Semaphore SETTINGS = new Semaphore(1, true);

    /** A hang elsewhere fails the waiting scenario instead of freezing the whole suite. */
    private static final long LOCK_TIMEOUT_MINUTES = 15;

    /** Per scenario (PicoContainer): release only what this scenario acquired. */
    private boolean acquired;

    @Before(value = "@serial-settings", order = -110)
    public void acquire() {
        log.info("Waiting for the @serial-settings lock...");
        try {
            if (!SETTINGS.tryAcquire(LOCK_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                throw new IllegalStateException(
                        "Lock not acquired within "
                                + LOCK_TIMEOUT_MINUTES
                                + " min: a holder is stuck");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the lock", e);
        }
        acquired = true;
        log.info("Acquired the @serial-settings lock");
    }

    @After(value = "@serial-settings", order = -110)
    public void release() {
        if (!acquired) {
            return;
        }
        acquired = false;
        SETTINGS.release();
        log.info("Released the @serial-settings lock");
    }
}
