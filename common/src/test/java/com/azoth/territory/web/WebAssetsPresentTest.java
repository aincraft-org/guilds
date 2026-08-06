package com.azoth.territory.web;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structural check: embedded map UI assets ship on the classpath. */
class WebAssetsPresentTest {

    @Test
    void staticAssetsAreOnClasspath() throws Exception {
        // plugin-level config.yml ships with the :paper module (covered by
        // GuildsIntegrationTest there); this module owns the map UI assets.
        for (String path : new String[]{"web/index.html", "web/app.js", "web/style.css"}) {
            try (InputStream in = Objects.requireNonNull(
                    getClass().getClassLoader().getResourceAsStream(path),
                    "missing resource: " + path
            )) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(body.length() > 20, path + " too small");
            }
        }
        assertNotNull(Class.forName("com.azoth.territory.web.TerritoryWebServer"));
        assertNotNull(Class.forName("com.azoth.territory.web.ReverseProxySupport"));
    }
}
