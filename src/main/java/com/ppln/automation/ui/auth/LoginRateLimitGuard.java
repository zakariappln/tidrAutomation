package com.ppln.automation.ui.auth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates every real submission of ThreatIDR's login form across the whole JVM, from whichever
 * call site: {@code auth/login.feature}'s own scenarios (via {@code SerialAuthHooks}, tagged
 * {@code @serial-auth}) AND {@link StorageStateManager}'s one-time, cache-populating login for
 * every other feature's Background. Both share this one lock because they share the same
 * server-side resource: ThreatIDR rate-limits the login POST to {@value #LIMIT}/minute by source
 * IP, and confirmed live (2026-10-17) that even successful logins count against it.
 *
 * <p>The guard models that budget as a sliding window: every guarded section declares how many
 * login POSTs it will make ({@link #acquire(int)}), waits until that many slots are free in the
 * last {@value #WINDOW_MS} ms, and on {@link #release()} records them as spent. Three single-login
 * scenarios therefore run back to back; a scenario that deliberately exhausts the limit (4
 * attempts) declares the whole window and waits for it to be clear. Sections are serialised by a
 * semaphore so the count is exact.
 *
 * <p>Nothing is spent at JVM start: the first login of a run never waits. Logins made by other
 * processes (a previous {@code mvn test}, a person signing in by hand) are not visible here; if one
 * of them leaves the window exhausted, {@link StorageStateManager} detects the 429 page and retries
 * after the window instead of every run paying a cooldown up front.
 */
public final class LoginRateLimitGuard {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitGuard.class);

    /** ThreatIDR's login rate limit: 3 POSTs per minute per source IP. */
    public static final int LIMIT = 3;

    /** The server window is 60 s; 61 s gives a one-second safety margin. */
    private static final long WINDOW_MS = 61_000;

    private static final Semaphore LOCK = new Semaphore(1);

    /** A stuck holder fails the waiting caller instead of freezing the whole suite. */
    private static final long LOCK_TIMEOUT_MINUTES = 15;

    /** Times (ms) at which guarded logins were spent, oldest first; pruned as they age out. */
    private static final Deque<Long> SPENT = new ArrayDeque<>();

    private static int pending;

    /** Thread holding the guard and its nesting depth: a guarded section may log in again. */
    private static volatile Thread owner;

    private static int depth;

    private LoginRateLimitGuard() {}

    /** One login: the common case. */
    public static void acquire() {
        acquire(1);
    }

    /**
     * Blocks until it is this caller's turn and {@code logins} slots are free in the current
     * window. Values above {@value #LIMIT} mean "the whole window must be clear".
     */
    public static void acquire(int logins) {
        int needed = Math.max(1, Math.min(logins, LIMIT));
        if (owner == Thread.currentThread()) {
            // Nested: e.g. a @serial-auth scenario whose step needs a cached session that has
            // expired. The extra login is added to this section's budget, never deadlocks.
            depth++;
            pending += needed;
            log.info("Nested login rate-limit reservation ({} login(s) in this section)", pending);
            coolDown(Math.min(pending, LIMIT));
            return;
        }
        log.info("Waiting for the login rate-limit guard ({} login(s) needed)...", needed);
        try {
            if (!LOCK.tryAcquire(LOCK_TIMEOUT_MINUTES, java.util.concurrent.TimeUnit.MINUTES)) {
                throw new IllegalStateException(
                        "Login guard not acquired within %d min: its holder is stuck"
                                .formatted(LOCK_TIMEOUT_MINUTES));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the login guard", e);
        }
        owner = Thread.currentThread();
        depth = 1;
        pending = needed;
        try {
            coolDown(needed);
        } catch (RuntimeException e) {
            pending = 0;
            depth = 0;
            owner = null;
            LOCK.release();
            throw e;
        }
        log.info("Acquired the login rate-limit guard");
    }

    /** {@code true} when the calling thread is inside a guarded section. */
    public static boolean heldByCurrentThread() {
        return owner == Thread.currentThread();
    }

    private static void coolDown(int needed) {
        long wait;
        while ((wait = millisUntilFree(needed, System.currentTimeMillis())) > 0) {
            log.info(
                    "Cooling down {} ms until {} login slot(s) are free in the rate-limit window",
                    wait,
                    needed);
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while cooling down the rate limit", e);
            }
        }
    }

    public static void release() {
        if (owner != Thread.currentThread()) {
            log.warn("release() called by a thread that does not hold the login guard; ignored");
            return;
        }
        if (--depth > 0) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (SPENT) {
            for (int i = 0; i < pending; i++) {
                SPENT.addLast(now);
            }
        }
        pending = 0;
        owner = null;
        LOCK.release();
        log.info("Released the login rate-limit guard");
    }

    /**
     * 0 when {@code needed} slots are free now; otherwise the time until enough of the oldest spent
     * logins have aged out of the window.
     */
    private static long millisUntilFree(int needed, long now) {
        synchronized (SPENT) {
            while (!SPENT.isEmpty() && SPENT.peekFirst() + WINDOW_MS <= now) {
                SPENT.pollFirst();
            }
            int mustExpire = SPENT.size() - (LIMIT - needed);
            if (mustExpire <= 0) {
                return 0;
            }
            Iterator<Long> oldestFirst = SPENT.iterator();
            long expiresAt = 0;
            for (int i = 0; i < mustExpire; i++) {
                expiresAt = oldestFirst.next() + WINDOW_MS;
            }
            return Math.max(1, expiresAt - now);
        }
    }
}
