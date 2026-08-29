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

        Map<String, List<Object>> definitions = travelDefinitions(packagedLoader.getNodes());
        assertEquals(3, definitions.get("remote_crystal").get(3));
        assertEquals(List.of("fast_travel"), definitions.get("remote_crystal").get(5));
        assertEquals(3, definitions.get("boat_travel").get(3));
        assertEquals(4, definitions.get("airship_travel").get(3));
        assertTrue(((String) definitions.get("fast_travel").get(1)).contains("local crystal"));
        assertEquals(0.5, ((Map<?, ?>) definitions.get("fast_travel").get(6))
                .get("teleport_cooldown_reduction"));
        assertEquals(3, definitions.get("fast_travel").get(3));
        assertEquals(List.of("better_storage"), definitions.get("fast_travel").get(5));


        assertEquals(definitions, travelDefinitions(fallbackLoader.getNodes()));
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
