package com.azoth.territory.web;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStoreTest {

    @Test
    void createRejectsWrongToken() {
        SessionStore store = new SessionStore("secret", 3600, Clock.systemUTC());
        assertTrue(store.create("nope").isEmpty());
    }

    @Test
    void createAcceptsTokenAndValidatesUntilExpiry() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        SessionStore store = new SessionStore("secret", 60, clock);
        String id = store.create("secret").orElseThrow();
        assertTrue(store.isValid(id));
        now.set(now.get().plusSeconds(61));
        assertFalse(store.isValid(id));
    }

    @Test
    void invalidateRemovesSession() {
        SessionStore store = new SessionStore("secret", 3600, Clock.systemUTC());
        String id = store.create("secret").orElseThrow();
        store.invalidate(id);
        assertFalse(store.isValid(id));
    }

    @Test
    void blankConfiguredTokenNeverCreatesSession() {
        SessionStore store = new SessionStore("", 3600, Clock.systemUTC());
        assertTrue(store.create("").isEmpty());
        assertTrue(store.create("anything").isEmpty());
    }
}
