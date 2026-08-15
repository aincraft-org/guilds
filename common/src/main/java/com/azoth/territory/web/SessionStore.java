package com.azoth.territory.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
/**
 * In-memory admin sessions for the map editor.
 * <p>
 * Login exchanges the configured API token for an opaque session id stored in
 * an HttpOnly cookie ({@link #COOKIE_NAME}). Sessions expire after a TTL and
 * are process-local (restart clears them).
 */
public final class SessionStore {
    public static final String COOKIE_NAME = "AZOTH_SESSION";

    private final String expectedToken;
    private final long ttlSeconds;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

    public SessionStore(String expectedToken, long ttlSeconds, Clock clock) {
        this.expectedToken = expectedToken == null ? "" : expectedToken;
        if (ttlSeconds < 1) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
        this.ttlSeconds = ttlSeconds;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    /**
     * Creates a session when {@code presentedToken} matches the configured API token.
     *
     * @return new session id, or empty if the token is wrong or auth is not configured
     */
    public Optional<String> create(String presentedToken) {
        if (expectedToken.isBlank() || presentedToken == null) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                presentedToken.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String id = HexFormat.of().formatHex(bytes);
        sessions.put(id, clock.instant().plusSeconds(ttlSeconds));
        return Optional.of(id);
    }

    public boolean isValid(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        Instant exp = sessions.get(sessionId);
        if (exp == null) {
            return false;
        }
        if (clock.instant().isAfter(exp)) {
            sessions.remove(sessionId, exp);
            return false;
        }
        return true;
    }

    public void invalidate(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }
}
