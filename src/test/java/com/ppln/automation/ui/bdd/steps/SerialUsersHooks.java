package com.ppln.automation.ui.bdd.steps;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serialises scenarios tagged {@code @serial-users} (the Users and Roles features). ThreatIDR's
 * Users page lazily loads every listed user's role while rendering and indexes {@code
 * user.roles[0]}; a user created (committed before its role is assigned) or deleted by another
 * request during that render makes the page answer HTTP 500 (confirmed live 2026-10-19,
 * views/user/views.py:93-138 and templates/user/partials/users.html). Scenarios that render the
 * list and scenarios that create or delete users must therefore not overlap.
 *
 * <p>Acquired first ({@code -120}) and released last, so lock order is always users, settings,
 * login guard.
 */
public class SerialUsersHooks {

    private static final Logger log = LoggerFactory.getLogger(SerialUsersHooks.class);
    private static final Semaphore USERS = new Semaphore(1, true);

    /** A hang elsewhere fails the waiting scenario instead of freezing the whole suite. */
    private static final long LOCK_TIMEOUT_MINUTES = 15;

    /** Per scenario (PicoContainer): release only what this scenario acquired. */
    private boolean acquired;

    @Before(value = "@serial-users", order = -120)
    public void acquire() {
        log.info("Waiting for the @serial-users lock...");
        try {
            if (!USERS.tryAcquire(LOCK_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
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
        log.info("Acquired the @serial-users lock");
    }

    @After(value = "@serial-users", order = -120)
    public void release() {
        if (!acquired) {
            return;
        }
        acquired = false;
        USERS.release();
        log.info("Released the @serial-users lock");
    }
}
