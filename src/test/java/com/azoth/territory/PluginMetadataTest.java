package com.azoth.territory;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static evidence: plugin.yml declares Paper/api-version and main class.
 */
class PluginMetadataTest {

    @Test
    void pluginYml_declaresMainAndApiVersion() throws Exception {
        try (InputStream in = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("plugin.yml"),
                "plugin.yml missing from test classpath"
        )) {
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yaml.contains("name: AzothTerritory") || yaml.contains("name: AzothTerritory\n"),
                    "plugin name missing: " + yaml);
            assertTrue(yaml.contains("main: com.azoth.territory.AzothTerritoryPlugin"),
                    "main class missing: " + yaml);
            assertTrue(yaml.contains("api-version:"), "api-version missing: " + yaml);
            assertTrue(yaml.contains("1.21"), "api-version should be 1.21.x: " + yaml);
            // Single identity: no second Towny main on this descriptor
            assertTrue(!yaml.contains("main: org.aincraft.towny.TownyPlugin"),
                    "must not declare TownyPlugin as a second main");
        }
        assertNotNull(Class.forName("com.azoth.territory.AzothTerritoryPlugin"));
        assertNotNull(Class.forName("org.aincraft.towny.TownyPlugin"));
    }
}
