package com.azoth.territory.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebConfigLoaderTest {

    @TempDir
    Path dataFolder;

    @Test
    void loadsEditorFieldsAndStripsTrailingSlashOnTileBase() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("web.enabled", true);
        cfg.put("web.bind", "127.0.0.1");
        cfg.put("web.port", 9001);
        cfg.put("web.api-token", "tok");
        cfg.put("web.squaremap-tile-base-url", "http://localhost:8080/");
        cfg.put("web.session-ttl-seconds", 120);

        WebConfig loaded = WebConfigLoader.fromValues(cfg, dataFolder);
        assertTrue(loaded.enabled());
        assertEquals("127.0.0.1", loaded.bindHost());
        assertEquals(9001, loaded.port());
        assertEquals("tok", loaded.apiToken());
        assertTrue(loaded.requiresAuth());
        assertEquals("http://localhost:8080", loaded.squaremapTileBaseUrl());
        assertEquals(120, loaded.sessionTtlSeconds());
    }

    @Test
    void defaultsWhenEditorKeysMissing() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("web.enabled", true);
        cfg.put("web.port", 8765);

        WebConfig loaded = WebConfigLoader.fromValues(cfg, dataFolder);
        assertEquals("", loaded.squaremapTileBaseUrl());
        assertEquals(WebConfig.DEFAULT_SESSION_TTL_SECONDS, loaded.sessionTtlSeconds());
        assertFalse(loaded.requiresAuth());
    }
}
