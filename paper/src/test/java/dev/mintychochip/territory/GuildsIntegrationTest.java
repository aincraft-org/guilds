package dev.mintychochip.territory;

import dev.mintychochip.guilds.GuildsPlugin;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural proof that Guilds is the single Paper plugin:
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

            assertTrue(yaml.contains("name: Guilds"), "single plugin name");
            assertTrue(yaml.contains("main: dev.mintychochip.guilds.GuildsPlugin"), "single main");
            assertFalse(yaml.contains("AzothTerritoryPlugin"),
                    "retired AzothTerritoryPlugin must not remain on the descriptor");
            assertFalse(yaml.contains("org.aincraft.guilds"),
                    "retired org.aincraft.guilds namespace must not remain on the descriptor");

            // Guilds permissions merged onto the single descriptor
            assertTrue(yaml.contains("guilds.guild.create") || yaml.contains("guilds.guild.*"),
                    "guilds permissions present: " + yaml.substring(0, Math.min(400, yaml.length())));
            assertTrue(yaml.contains("guilds.admin") || yaml.contains("guilds.admin.*"),
                    "guilds admin permissions present");
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
            assertTrue(guildsYaml.contains("guild_levels") || guildsYaml.contains("guild:"),
                    "guilds-config should carry town defaults");
            assertTrue(guildsYaml.contains("EXPERIENCE:"),
                    "guild levels must require XP");
            assertFalse(guildsYaml.contains("DIAMOND:"),
                    "guild levels must not require diamond materials");
            assertTrue(territoryYaml.contains("web:") || territoryYaml.contains("economy:"),
                    "territory config should carry web/economy settings");
            assertFalse(territoryYaml.contains("guild_levels"),
                    "territory config must not be overwritten by guilds defaults");
        }
    }

    @Test
    void guildsProductionClasses_loadFromRootClasspath() throws Exception {
        Class<?> services = Class.forName("dev.mintychochip.guilds.GuildsServices");
        Class<?> brigadier = Class.forName("dev.mintychochip.guilds.commands.BrigadierCommandRegistry");
        assertNotNull(services);
        assertNotNull(brigadier);
        // GuildsServices is a subsystem composition root, not a second JavaPlugin main
        assertFalse(org.bukkit.plugin.java.JavaPlugin.class.isAssignableFrom(services),
                "GuildsServices must not extend JavaPlugin after single-plugin integration");
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.aincraft.guilds.GuildsPlugin"),
                "retired org.aincraft.guilds.GuildsPlugin must not remain");
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("dev.mintychochip.territory.AzothTerritoryPlugin"),
                "retired AzothTerritoryPlugin must not remain");
    }

    @Test
    void mainPlugin_wiresGuildsEnablementOnShippedEntryPath() throws Exception {
        Class<?> main = GuildsPlugin.class;
        assertEquals("dev.mintychochip.guilds.GuildsPlugin", main.getName());

        // Public accessor owned by main
        Method getGuilds = main.getMethod("getGuilds");
        assertEquals("dev.mintychochip.guilds.GuildsServices", getGuilds.getReturnType().getName());

        // Source-level wiring: onEnable path calls guilds subsystem enable
        Path source = findMainSource();
        String text = Files.readString(source);
        assertTrue(text.contains("enableGuildsSubsystem") || text.contains("guilds.enable"),
                "GuildsPlugin must enable guilds subsystem");
        assertTrue(text.contains("new GuildsServices(this)") || text.contains("GuildsServices("),
                "main must construct GuildsServices with the host plugin");
        assertTrue(text.contains("import dev.mintychochip.guilds.GuildsServices"),
                "main must import guilds subsystem");
    }

    @Test
    void guildsConfigResourceConstant_matchesShippedResourceName() throws Exception {
        Class<?> services = Class.forName("dev.mintychochip.guilds.GuildsServices");
        Object resource = services.getField("GUILDS_CONFIG").get(null);
        assertEquals("guilds-config.yml", resource);
        assertNotNull(getClass().getClassLoader().getResourceAsStream((String) resource));
    }

    private static Path findMainSource() throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        Path candidate = cwd.resolve("src/main/java/dev/mintychochip/guilds/GuildsPlugin.java");
        if (Files.isRegularFile(candidate)) {
            return candidate;
        }
        // Walk up a few levels for alternate working directories
        Path p = cwd;
        for (int i = 0; i < 4; i++) {
            Path tryPath = p.resolve("src/main/java/dev/mintychochip/guilds/GuildsPlugin.java");
            if (Files.isRegularFile(tryPath)) {
                return tryPath;
            }
            p = p.getParent();
            if (p == null) {
                break;
            }
        }
        throw new IllegalStateException("Could not locate GuildsPlugin.java from " + cwd);
    }
}
