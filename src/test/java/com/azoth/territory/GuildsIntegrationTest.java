package com.azoth.territory;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural proof that Guilds is integrated into the single Azoth Territory plugin:
 * one plugin.yml identity, guilds permissions/resources on that descriptor, production
 * classes loadable, and the main entry owns guilds enablement.
 */
class GuildsIntegrationTest {

    @Test
    void singlePluginYml_declaresOneMainAndGuildsPermissions() throws Exception {
        try (InputStream in = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("plugin.yml"),
                "plugin.yml missing from classpath")) {
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(yaml.contains("name: AzothTerritory"), "single plugin name");
            assertTrue(yaml.contains("main: com.azoth.territory.AzothTerritoryPlugin"), "single main");
            assertFalse(yaml.contains("main: org.aincraft.towny.TownyPlugin"),
                    "TownyPlugin must not be a second Paper main");
            assertFalse(yaml.contains("name: Towny\n") || yaml.matches("(?s).*\\nname: Towny\\n.*"),
                    "must not declare a second plugin identity named Towny");

            // Guilds permissions merged onto the single descriptor
            assertTrue(yaml.contains("towny.town.create") || yaml.contains("towny.town.*"),
                    "guilds/towny permissions present: " + yaml.substring(0, Math.min(400, yaml.length())));
            assertTrue(yaml.contains("towny.admin") || yaml.contains("towny.admin.*"),
                    "towny admin permissions present");
            assertTrue(yaml.contains("Vault"), "softdepend Vault retained for both subsystems");
        }
    }

    @Test
    void guildsResources_areOnClasspathWithoutClobberingTerritoryConfig() throws Exception {
        try (InputStream guilds = getClass().getClassLoader().getResourceAsStream("guilds-config.yml");
             InputStream territory = getClass().getClassLoader().getResourceAsStream("config.yml");
             InputStream techtree = getClass().getClassLoader().getResourceAsStream("techtree.yml")) {
            assertNotNull(guilds, "guilds-config.yml must ship (namespaced away from config.yml)");
            assertNotNull(territory, "territory config.yml must remain");
            assertNotNull(techtree, "techtree.yml must ship");

            String guildsYaml = new String(guilds.readAllBytes(), StandardCharsets.UTF_8);
            String territoryYaml = new String(territory.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(guildsYaml.contains("town_levels") || guildsYaml.contains("town:"),
                    "guilds-config should carry town defaults");
            assertTrue(territoryYaml.contains("web:") || territoryYaml.contains("economy:"),
                    "territory config should carry web/economy settings");
            assertFalse(territoryYaml.contains("town_levels"),
                    "territory config must not be overwritten by guilds defaults");
        }
    }

    @Test
    void guildsProductionClasses_loadFromRootClasspath() throws Exception {
        Class<?> towny = Class.forName("org.aincraft.towny.TownyPlugin");
        Class<?> module = Class.forName("org.aincraft.towny.dependency.TownyModule");
        Class<?> brigadier = Class.forName("org.aincraft.towny.commands.BrigadierCommandRegistry");
        assertNotNull(towny);
        assertNotNull(module);
        assertNotNull(brigadier);
        // TownyPlugin is a subsystem, not a second JavaPlugin main
        assertFalse(org.bukkit.plugin.java.JavaPlugin.class.isAssignableFrom(towny),
                "TownyPlugin must not extend JavaPlugin after single-plugin integration");
    }

    @Test
    void mainPlugin_wiresGuildsEnablementOnShippedEntryPath() throws Exception {
        Class<?> main = AzothTerritoryPlugin.class;
        assertEquals("com.azoth.territory.AzothTerritoryPlugin", main.getName());

        // Public accessor owned by main
        Method getGuilds = main.getMethod("getGuilds");
        assertEquals("org.aincraft.towny.TownyPlugin", getGuilds.getReturnType().getName());

        // Source-level wiring: onEnable path calls guilds subsystem enable
        Path source = findMainSource();
        String text = Files.readString(source);
        assertTrue(text.contains("enableGuildsSubsystem") || text.contains("guilds.enable"),
                "AzothTerritoryPlugin must enable guilds subsystem");
        assertTrue(text.contains("new TownyPlugin(this)") || text.contains("TownyPlugin("),
                "main must construct TownyPlugin with host plugin");
        assertTrue(text.contains("import org.aincraft.towny.TownyPlugin"),
                "main must import guilds subsystem");
    }

    @Test
    void guildsConfigResourceConstant_matchesShippedResourceName() throws Exception {
        Class<?> towny = Class.forName("org.aincraft.towny.TownyPlugin");
        Object resource = towny.getField("GUILDS_CONFIG").get(null);
        assertEquals("guilds-config.yml", resource);
        assertNotNull(getClass().getClassLoader().getResourceAsStream((String) resource));
    }

    private static Path findMainSource() throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        Path candidate = cwd.resolve("src/main/java/com/azoth/territory/AzothTerritoryPlugin.java");
        if (Files.isRegularFile(candidate)) {
            return candidate;
        }
        // Walk up a few levels for alternate working directories
        Path p = cwd;
        for (int i = 0; i < 4; i++) {
            Path tryPath = p.resolve("src/main/java/com/azoth/territory/AzothTerritoryPlugin.java");
            if (Files.isRegularFile(tryPath)) {
                return tryPath;
            }
            p = p.getParent();
            if (p == null) {
                break;
            }
        }
        throw new IllegalStateException("Could not locate AzothTerritoryPlugin.java from " + cwd);
    }
}
