package com.azoth.territory.building;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Territory;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PermissionService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuildingAuthorizationTest {
    @Test
    void managementRequiresResidentWithSetSpawnAuthority() {
        UUID playerId = UUID.randomUUID();
        GuildService guilds = mock(GuildService.class);
        PermissionService permissions = mock(PermissionService.class);
        Player player = mock(Player.class);
        Guild guild = new Guild("Builders", UUID.randomUUID());
        guild.setId("guild-1");
        guild.getResidents().add(playerId);
        when(player.getUniqueId()).thenReturn(playerId);
        when(guilds.getGuildById("guild-1")).thenReturn(Optional.of(guild));
        BuildingAuthorization authorization = new BuildingAuthorization(guilds, permissions);
        Territory territory = territory().withGoverningGuild("guild-1");

        assertFalse(authorization.canManage(player, territory));
        when(permissions.hasPermission(playerId, "set_spawn", "guild", "Builders"))
                .thenReturn(true);
        assertTrue(authorization.canManage(player, territory));
        assertTrue(authorization.canUseWaystones(playerId, "guild-1"));
    }

    @Test
    void adminOverridesManagementButNotWaystoneMembership() {
        GuildService guilds = mock(GuildService.class);
        Player player = mock(Player.class);
        when(player.hasPermission("azoth.territory.admin")).thenReturn(true);
        BuildingAuthorization authorization = new BuildingAuthorization(
                guilds, mock(PermissionService.class));

        assertTrue(authorization.canManage(player, territory()));
        assertFalse(authorization.canUseWaystones(UUID.randomUUID(), "missing"));
    }

    @Test
    void ungovernedAndMissingGuildDenyNormalPlayers() {
        BuildingAuthorization authorization = new BuildingAuthorization(
                mock(GuildService.class), mock(PermissionService.class));
        Player player = mock(Player.class);

        assertFalse(authorization.canManage(player, territory()));
        assertFalse(authorization.canManage(player, territory().withGoverningGuild("missing")));
    }

    private static Territory territory() {
        return new Territory("t1", "Territory", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))));
    }
}
