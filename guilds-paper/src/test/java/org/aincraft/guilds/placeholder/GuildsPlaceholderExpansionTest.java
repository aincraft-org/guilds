package org.aincraft.guilds.placeholder;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuildsPlaceholderExpansionTest {

    @Test
    void resolvesGuildDataAndChatPrefixForMayor() {
        UUID playerUuid = UUID.randomUUID();
        Resident resident = resident("mayor", playerUuid, "Knights");
        Guild guild = new Guild("Knights", playerUuid);
        guild.setId("guild-id");
        guild.setGuildLevel(4);
        guild.setBalance(123.456);
        guild.setOpen(false);

        GuildsPlaceholderExpansion expansion = expansion(resident, guild, playerUuid);
        OfflinePlayer player = player(playerUuid);

        assertEquals("Knights", expansion.onRequest(player, "guild"));
        assertEquals("Knights", expansion.onRequest(player, "guild_name"));
        assertEquals("guild-id", expansion.onRequest(player, "guild_id"));
        assertEquals("mayor", expansion.onRequest(player, "role"));
        assertEquals("4", expansion.onRequest(player, "guild_level"));
        assertEquals("123.46", expansion.onRequest(player, "balance"));
        assertEquals("1", expansion.onRequest(player, "members"));
        assertEquals("false", expansion.onRequest(player, "guild_open"));
        assertEquals("true", expansion.onRequest(player, "in_guild"));
        assertEquals("[Knights]", expansion.onRequest(player, "chat_prefix"));
    }

    @Test
    void resolvesAssistantRoleAndAliases() {
        UUID mayorUuid = UUID.randomUUID();
        UUID assistantUuid = UUID.randomUUID();
        Resident resident = resident("assistant", assistantUuid, "Ravens");
        Guild guild = new Guild("Ravens", mayorUuid);
        guild.getResidents().add(assistantUuid);
        guild.getAssistants().add(assistantUuid);

        GuildsPlaceholderExpansion expansion = expansion(resident, guild, assistantUuid);

        assertEquals("assistant", expansion.onRequest(player(assistantUuid), "guild_role"));
        assertEquals("2", expansion.onRequest(player(assistantUuid), "guild_members"));
    }

    @Test
    void returnsSafeEmptyValuesOutsideGuildAndNullForUnknownPlaceholder() {
        UUID playerUuid = UUID.randomUUID();
        GuildService guildService = mock(GuildService.class);
        ResidentService residentService = mock(ResidentService.class);
        when(residentService.getResident(playerUuid)).thenReturn(Optional.empty());
        GuildsPlaceholderExpansion expansion = new GuildsPlaceholderExpansion(
                mock(JavaPlugin.class), guildService, residentService);

        OfflinePlayer player = player(playerUuid);
        assertEquals("", expansion.onRequest(player, "guild"));
        assertEquals("none", expansion.onRequest(player, "role"));
        assertEquals("0", expansion.onRequest(player, "level"));
        assertEquals("0.00", expansion.onRequest(player, "balance"));
        assertEquals("false", expansion.onRequest(player, "has_guild"));
        assertEquals("", expansion.onRequest(player, "chat_prefix"));
        assertNull(expansion.onRequest(player, "does_not_exist"));
    }

    private static GuildsPlaceholderExpansion expansion(
            Resident resident, Guild guild, UUID playerUuid) {
        GuildService guildService = mock(GuildService.class);
        ResidentService residentService = mock(ResidentService.class);
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(resident));
        when(guildService.getGuild(resident.getGuild())).thenReturn(Optional.of(guild));
        return new GuildsPlaceholderExpansion(mock(JavaPlugin.class), guildService, residentService);
    }

    private static Resident resident(String name, UUID uuid, String guildName) {
        Resident resident = new Resident(uuid, name);
        resident.setGuild(guildName);
        return resident;
    }

    private static OfflinePlayer player(UUID uuid) {
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }
}
