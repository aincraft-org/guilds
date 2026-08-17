package dev.mintychochip.territory.storage;

import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.GuildService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Residents may deposit; only officers may withdraw. */
class GuildPermissionStorageAccessTest {
    @Test
    void ranksMatchDefaultStoragePolicy() {
        UUID member = UUID.randomUUID();
        UUID assistant = UUID.randomUUID();
        UUID mayor = UUID.randomUUID();
        Guild guild = mock(Guild.class);
        when(guild.isResident(member)).thenReturn(true);
        when(guild.isResident(assistant)).thenReturn(true);
        when(guild.isResident(mayor)).thenReturn(true);
        when(guild.isAssistant(assistant)).thenReturn(true);
        when(guild.isMayor(mayor)).thenReturn(true);
        GuildService guilds = mock(GuildService.class);
        when(guilds.getGuildById("guild-1")).thenReturn(Optional.of(guild));
        GuildPermissionStorageAccess access = new GuildPermissionStorageAccess(guilds);

        assertTrue(access.canDeposit(member, "guild-1"));
        assertFalse(access.canWithdraw(member, "guild-1"));
        assertTrue(access.canWithdraw(assistant, "guild-1"));
        assertTrue(access.canWithdraw(mayor, "guild-1"));
        assertFalse(access.isResident(UUID.randomUUID(), "guild-1"));
    }
}
