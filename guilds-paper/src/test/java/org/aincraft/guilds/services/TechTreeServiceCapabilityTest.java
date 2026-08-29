package org.aincraft.guilds.services;

import org.aincraft.guilds.config.TechTreeConfigLoader;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.services.impl.TechTreeServiceImpl;
import org.aincraft.guilds.territory.model.FastTravelMode;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class TechTreeServiceCapabilityTest {

    @Test
    void reportsUnlockedTravelCapabilities() {
        TechTreeService service = service();
        Guild guild = new Guild("Travelers", UUID.randomUUID());

        assertFalse(service.hasCapability(guild, "fast_travel"));
        guild.unlockTechNode("fast_travel");
        assertTrue(service.hasCapability(guild, "fast_travel"));
        assertFalse(service.hasCapability(guild, "boat_travel"));

        guild.unlockTechNode("boat_travel");
        assertTrue(service.hasCapability(guild, "boat_travel"));
    }

    @Test
    void readsNumericEffectOnlyFromUnlockedNode() {
        TechTreeService service = service();
        Guild guild = new Guild("Travelers", UUID.randomUUID());

        assertEquals(0.0, service.getNumericEffect(guild, "fast_travel",
                "teleport_cooldown_reduction"));
        guild.unlockTechNode("fast_travel");

        assertEquals(0.5, service.getNumericEffect(guild, "fast_travel",
                "teleport_cooldown_reduction"));
        assertEquals(0.0, service.getNumericEffect(guild, "boat_travel",
                "teleport_cooldown_reduction"));
        assertEquals(0.0, service.cooldownReduction(guild, FastTravelMode.CRYSTAL));
        assertEquals(0.0, service.cooldownReduction(guild, FastTravelMode.LOCAL_TERMINAL));
        assertEquals(0.0, service.cooldownReduction(guild, FastTravelMode.BOAT));
        assertEquals(0.0, service.cooldownReduction(guild, FastTravelMode.AIRSHIP));
        assertEquals(0.5, service.cooldownReduction(guild, FastTravelMode.WAYSTONE));
    }
    @Test
    void packagedAndInlineDefaultsDefineTheSameTravelNodes() throws Exception {
        JavaPlugin packagedPlugin = pluginWithResource(Files.createTempDirectory("techtree-packaged"));
        TechTreeConfigLoader packagedLoader = new TechTreeConfigLoader(packagedPlugin);
        packagedLoader.loadConfiguration();

        JavaPlugin fallbackPlugin = pluginWithResource(Files.createTempDirectory("techtree-fallback"));
        when(fallbackPlugin.getResource("techtree.yml")).thenReturn(null);
        TechTreeConfigLoader fallbackLoader = new TechTreeConfigLoader(fallbackPlugin);
        fallbackLoader.loadConfiguration();

        Map<String, List<Object>> packagedDefinitions = travelDefinitions(packagedLoader.getNodes());
        Map<String, List<Object>> fallbackDefinitions = travelDefinitions(fallbackLoader.getNodes());
        assertEquals(packagedDefinitions, fallbackDefinitions);
        assertTravelDefinitions(packagedDefinitions);
        assertTravelDefinitions(fallbackDefinitions);
    }
    private static void assertTravelDefinitions(Map<String, List<Object>> definitions) {
        assertEquals(4, definitions.size());
        assertNode(definitions, "remote_crystal", "Remote Crystal",
                "Unlocks travel to allied guild crystals", 3, 0, 2);
        assertNode(definitions, "boat_travel", "Boat Travel",
                "Unlocks boat fast travel", 3, 2, 2);
        assertNode(definitions, "airship_travel", "Airship Travel",
                "Unlocks airship fast travel", 4, 3, 2);

        List<Object> fastTravel = definitions.get("fast_travel");
        assertTrue(((String) fastTravel.get(1)).contains("local crystal"));
        assertTrue(((String) fastTravel.get(1)).contains("terminal"));
        assertEquals(3, fastTravel.get(3));
        assertEquals("better_storage", fastTravel.get(4));
        assertEquals(List.of("better_storage"), fastTravel.get(5));
        assertEquals(0.5, ((Map<?, ?>) fastTravel.get(6))
                .get("teleport_cooldown_reduction"));
        assertEquals(1, fastTravel.get(7));
        assertEquals(1, fastTravel.get(8));
    }

    private static void assertNode(Map<String, List<Object>> definitions, String id,
                                   String name, String description, int cost, int x, int y) {
        List<Object> node = definitions.get(id);
        assertEquals(name, node.get(0));
        assertEquals(description, node.get(1));
        assertEquals(cost, node.get(3));
        assertEquals("fast_travel", node.get(4));
        assertEquals(List.of("fast_travel"), node.get(5));
        assertEquals(x, node.get(7));
        assertEquals(y, node.get(8));
        assertEquals(TechTreeBranch.INFRASTRUCTURE, node.get(2));
    }


    private static JavaPlugin pluginWithResource(java.nio.file.Path dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("techtree-resource-test"));
        when(plugin.getResource("techtree.yml")).thenReturn(
                TechTreeServiceCapabilityTest.class.getClassLoader()
                        .getResourceAsStream("techtree.yml"));
        return plugin;
    }

    private static Map<String, List<Object>> travelDefinitions(List<TechTreeNode> nodes) {
        Map<String, List<Object>> definitions = new LinkedHashMap<>();
        for (TechTreeNode node : nodes) {
            if (List.of("fast_travel", "remote_crystal", "boat_travel", "airship_travel")
                    .contains(node.getId())) {
                definitions.put(node.getId(), List.of(node.getName(), node.getDescription(),
                        node.getBranch(), node.getCost(), node.getParent(),
                        node.getPrerequisites(), node.getEffects() == null
                                ? Map.of() : node.getEffects(),
                        node.getPositionX(), node.getPositionY()));
            }
        }
        return definitions;
    }


    private static TechTreeService service() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("tech-tree-capability-test"));
        TechTreeConfigLoader loader = mock(TechTreeConfigLoader.class);
        when(loader.getNodes()).thenReturn(List.of(
                node("fast_travel", 3, List.of("better_storage"),
                        Map.of("teleport_cooldown_reduction", 0.5)),
                node("boat_travel", 3, List.of("fast_travel"), Map.of()),
                node("better_storage", 2, List.of(), Map.of())
        ));
        return new TechTreeServiceImpl(plugin, mock(DatabaseManager.class), loader,
                mock(org.aincraft.guilds.services.GuildService.class));
    }

    private static TechTreeNode node(String id, int cost, List<String> prerequisites,
                                     Map<String, Object> effects) {
        TechTreeNode node = new TechTreeNode(id);
        node.setName(id);
        node.setBranch(TechTreeBranch.INFRASTRUCTURE);
        node.setCost(cost);
        node.setPrerequisites(prerequisites);
        node.setEffects(effects);
        return node;
    }
}
