package dev.mintychochip.territory;

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
            assertTrue(yaml.contains("main: dev.mintychochip.territory.AzothTerritoryPlugin"),
                    "main class missing: " + yaml);
            assertTrue(yaml.contains("api-version:"), "api-version missing: " + yaml);
            assertTrue(yaml.contains("26.2"), "api-version should be 26.2: " + yaml);
            assertTrue(yaml.matches("(?s).*version: [0-9]+\\.[0-9]+\\.[0-9]+(?:\\.[0-9]+)?.*"),
                    "release version should be numeric: " + yaml);
            // Single identity: no second Guilds main on this descriptor
            assertTrue(!yaml.contains("main: org.aincraft.guilds.GuildsPlugin"),
                    "must not declare GuildsPlugin as a second main");
        }
        assertNotNull(Class.forName("dev.mintychochip.territory.AzothTerritoryPlugin"));
        assertNotNull(Class.forName("org.aincraft.guilds.GuildsServices"));
    }

    @Test
    void standingDefaults_arePackaged() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("bonuses.json")) {
            assertNotNull(in, "bonuses.json missing from plugin resources");
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"version\": 1"), "standing resource version missing: " + json);
            assertTrue(json.contains("\"tiers\""), "standing tiers missing: " + json);
        }
    }

    @Test
    void configYml_shipsInfluenceBlock() throws Exception {
        try (InputStream in = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("config.yml"),
                "config.yml missing from test classpath"
        )) {
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yaml.contains("influence:"), "config.yml must ship the influence block");
            assertTrue(yaml.contains("post-flip-cooldown-days: 7"),
                    "config.yml must ship the post-flip cooldown default");
            assertTrue(yaml.contains("declare-countdown-hours: 24"),
                    "config.yml must ship the declare countdown default");
            assertTrue(yaml.contains("upkeep:"), "config.yml must ship the upkeep block");
            assertTrue(yaml.contains("interval-days: 7"),
                    "config.yml must ship the upkeep interval default");
            assertTrue(yaml.contains("grace-days: 2"),
                    "config.yml must ship the upkeep grace default");
        }
    }
}
