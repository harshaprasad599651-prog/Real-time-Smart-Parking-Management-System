package com.smartpark.server.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing.
 * <p>
 * Failed sign-ins are counted per <b>username</b> and per <b>client address</b> in a sliding
 * window. After {@code max-failures} failures within {@code window} that key is locked for
 * {@code lockout}; meanwhile even a correct password is refused (429), so guessing cannot simply
 * continue. A successful sign-in clears the username's counter. Counting per address as well
 * (with a 4x higher limit, since operators share a terminal) stops one PC from trying many
 * usernames.
 * <pre>
 *   smartpark.security.login.max-failures=5
 *   smartpark.security.login.window=PT15M
 *   smartpark.security.login.lockout=PT5M
 * </pre>
 * State is in memory: a restart clears it, which is acceptable for a LAN application.
 */
@Component
public class LoginAttemptLimiter {

    /** A computer may fail more often than one account (several operators share a terminal). */
    static final int ADDRESS_FACTOR = 4;

    private final int maxFailures;
    private final Duration window;
    private final Duration lockout;
    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    private static final class Entry {
        final Deque<Instant> failures = new ArrayDeque<>();
        Instant lockedUntil;
    }

    @Autowired
    public LoginAttemptLimiter(@Value("${smartpark.security.login.max-failures:5}") int maxFailures,
                               @Value("${smartpark.security.login.window:PT15M}") Duration window,
                               @Value("${smartpark.security.login.lockout:PT5M}") Duration lockout) {
        this(maxFailures, window, lockout, Clock.systemUTC());
    }

    LoginAttemptLimiter(int maxFailures, Duration window, Duration lockout, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.lockout = lockout;
        this.clock = clock;
    }

    /** How long the username or address is still locked, if it is. */
    public Optional<Duration> lockedFor(String username, String address) {
        Instant now = clock.instant();
        Duration longest = Duration.ZERO;
        for (String key : keys(username, address)) {
            Entry e = entries.get(key);
            if (e != null) {
                synchronized (e) {
                    if (e.lockedUntil != null && e.lockedUntil.isAfter(now)) {
                        Duration left = Duration.between(now, e.lockedUntil);
                        if (left.compareTo(longest) > 0) {
                            longest = left;
                        }
                    }
                }
            }
        }
        return longest.isZero() ? Optional.empty() : Optional.of(longest);
    }

    /** @return true if this failure locked the username or address */
    public boolean recordFailure(String username, String address) {
        Instant now = clock.instant();
        boolean locked = false;
        for (String key : keys(username, address)) {
            Entry e = entries.computeIfAbsent(key, k -> new Entry());
            synchronized (e) {
                e.failures.addLast(now);
                while (!e.failures.isEmpty() && e.failures.peekFirst().isBefore(now.minus(window))) {
                    e.failures.removeFirst();
                }
                int limit = key.startsWith("addr:") ? maxFailures * ADDRESS_FACTOR : maxFailures;
                if (e.failures.size() >= limit) {
                    e.lockedUntil = now.plus(lockout);
                    e.failures.clear();
                    locked = true;
                }
            }
        }
        return locked;
    }

    public void recordSuccess(String username) {
        entries.remove("user:" + username);
    }

    /** Forgets all failures and locks (used by the tests between test cases). */
    public void clear() {
        entries.clear();
    }

    @Scheduled(fixedDelayString = "PT10M")
    void purge() {
        Instant now = clock.instant();
        entries.entrySet().removeIf(en -> {
            Entry e = en.getValue();
            synchronized (e) {
                boolean unlocked = e.lockedUntil == null || e.lockedUntil.isBefore(now);
                boolean stale = e.failures.isEmpty() || e.failures.peekLast().isBefore(now.minus(window));
                return unlocked && stale;
            }
        });
    }

    private static String[] keys(String username, String address) {
        return address == null ? new String[]{"user:" + username}
                : new String[]{"user:" + username, "addr:" + address};
    }
}
