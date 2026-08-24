package org.aincraft.guilds.services;

import org.aincraft.guilds.GuildsServiceTestFixture;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.ResourceContribution;
import org.aincraft.guilds.services.impl.ResourceServiceImpl;
import org.bukkit.Server;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourceServiceImplContributionTest {

    @Test
    void supportedResources_areLimitedToUpgradeResourceTypes() {
        ResourceServiceImpl service = newService();

        assertTrue(service.isSupportedResourceType("diamond"));
        assertTrue(service.isSupportedResourceType("EXPERIENCE"));
        assertFalse(service.isSupportedResourceType("NETHERITE_INGOT"));
        assertEquals(java.util.List.of("diamond", "gold", "iron", "emerald", "experience"),
                service.getSupportedResourceTypes());
        assertTrue(service.isSupportedResourceType("GOLD_INGOT"));
    }

    @Test
    void invalidContribution_doesNotCreateAnObject() {
        ResourceServiceImpl service = newService();
        UUID contributor = UUID.randomUUID();

        assertTrue(service.recordResourceContribution("guild", contributor, "DIAMOND", 0).isEmpty());
        assertTrue(service.recordResourceContribution("guild", contributor, "NETHERITE_INGOT", 1).isEmpty());
        assertTrue(service.recordResourceContribution("guild", contributor, "DIAMOND", -1).isEmpty());
    }

    @Test
    void processContribution_refundsInventoryWhenRecordingFails() {
        UUID contributor = UUID.randomUUID();
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("resource-test"));
        when(plugin.getServer()).thenReturn(server);
        when(server.getPlayer(contributor)).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack diamond = mock(ItemStack.class);
        when(diamond.getType()).thenReturn(Material.DIAMOND);
        when(diamond.getAmount()).thenReturn(8);
        when(inventory.getContents()).thenReturn(new ItemStack[]{diamond});
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new java.util.HashMap<>());
        Guild guild = new Guild("Alpha", contributor);

        DatabaseManager database = mock(DatabaseManager.class);
        when(database.executeTransactionWithResult(any())).thenReturn(Optional.empty());
        ResourceServiceImpl service = spy(new TestResourceService(
                plugin, database, mock(GuildService.class)));

        ResourceService.ContributionResult result =
                service.processContribution(guild, contributor, "DIAMOND", 5);

        assertFalse(result.isSuccessful());
        verify(inventory).addItem(any(ItemStack.class));
    }

    @Test
    void postgresPersistence_reconstructsAndAggregatesContributions(@TempDir Path tempDir) throws Exception {
        GuildsServiceTestFixture.Services services = GuildsServiceTestFixture.create(tempDir);
        try {
            clearTestRows(services.databaseManager());
            JavaPlugin plugin = mockPlugin();
            ResourceServiceImpl service = new ResourceServiceImpl(
                    plugin, services.databaseManager(), services.guildService());
            UUID contributor = UUID.randomUUID();
            services.residentService().createResident(contributor, "contributor");
            Guild guild = services.guildService().createGuild("Contribution Guild", contributor);

            ResourceContribution first = service
                    .recordResourceContribution(guild.getId(), contributor, "DIAMOND", 7)
                    .orElseThrow();
            assertTrue(service.recordResourceContribution(
                    guild.getId(), contributor, "DIAMOND", 3).isPresent());
            assertTrue(service.recordResourceContribution(
                    guild.getId(), contributor, "GOLD", 4).isPresent());

            ResourceContribution reconstructed = service.getResourceContribution(first.getId()).orElseThrow();
            assertEquals(first.getId(), reconstructed.getId());
            assertEquals(7, reconstructed.getAmount());
            assertEquals(Map.of("diamond", 10, "gold", 4),
                    service.calculateTotalContributionsByResource(guild.getId()));
            assertEquals(Map.of("diamond", 10, "gold", 4), service.calculatePlayerContributions(contributor));
            assertEquals(3, service.getGuildContributions(guild.getId()).size());
            assertEquals(3, service.getPlayerContributionsToGuild(guild.getId(), contributor).size());
            assertEquals(3, service.getRecentContributions(guild.getId()).size());
            assertEquals(1, service.getContributionStatistics(guild.getId()).getTotalContributors());
            assertEquals(3, service.getContributionStatistics(guild.getId()).getTotalContributions());
        } finally {
            services.databaseManager().shutdown();
        }
    }

    private static ResourceServiceImpl newService() {
        JavaPlugin plugin = mockPlugin();
        return new ResourceServiceImpl(plugin, mock(DatabaseManager.class), mock(GuildService.class));
    }

    private static JavaPlugin mockPlugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("resource-test"));
        return plugin;
    }

    private static void clearTestRows(DatabaseManager database) throws Exception {
        try (Connection connection = database.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM resource_contributions WHERE guild_id IN "
                             + "(SELECT id FROM guilds WHERE name = 'Contribution Guild')")) {
            statement.executeUpdate();
        }
    }
    private static final class TestResourceService extends ResourceServiceImpl {
        private final ItemStack refundStack = mock(ItemStack.class);

        private TestResourceService(JavaPlugin plugin, DatabaseManager database, GuildService guildService) {
            super(plugin, database, guildService);
        }

        @Override
        protected ItemStack createResourceStack(Material material, int amount) {
            return refundStack;
        }
    }
}
