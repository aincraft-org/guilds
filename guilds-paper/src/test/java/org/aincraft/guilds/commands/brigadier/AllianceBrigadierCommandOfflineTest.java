package org.aincraft.guilds.commands.brigadier;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.aincraft.guilds.GuildsGovernanceSource;
import org.aincraft.guilds.models.Alliance;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.AllianceService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AllianceBrigadierCommandOfflineTest {
    @Test
    void transfersKingshipUsingPersistedResidentWhenTargetIsOffline() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        JavaPlugin plugin = mock(JavaPlugin.class);
        FileConfiguration config = mock(FileConfiguration.class);
        Server server = mock(Server.class);
        AllianceService alliances = mock(AllianceService.class);
        GuildService guilds = mock(GuildService.class);
        ResidentService residents = mock(ResidentService.class);
        GuildsGovernanceSource governance = mock(GuildsGovernanceSource.class);
        Player actor = mock(Player.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        CommandContext<CommandSourceStack> context = mock(CommandContext.class);

        when(plugin.getConfig()).thenReturn(config);
        when(config.getInt("alliance.min-guilds", 2)).thenReturn(2);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPlayer(targetId)).thenReturn(null);
        when(source.getSender()).thenReturn(actor);
        when(context.getSource()).thenReturn(source);
        when(context.getArgument("player", String.class)).thenReturn("offline-user");
        when(actor.getUniqueId()).thenReturn(actorId);

        Guild guild = new Guild("guild", actorId);
        guild.setId("guild-id");
        Alliance alliance = new Alliance("alliance", guild.getId(), actorId);
        alliance.setId("alliance-id");
        Resident actorResident = new Resident(actorId, "king");
        actorResident.setGuild(guild.getName());
        Resident targetResident = new Resident(targetId, "offline-user");

        when(residents.getResident(actorId)).thenReturn(Optional.of(actorResident));
        when(residents.getResident("offline-user")).thenReturn(Optional.of(targetResident));
        when(guilds.getGuild(guild.getName())).thenReturn(Optional.of(guild));
        when(alliances.getAllAlliances()).thenReturn(List.of(alliance));

        AllianceBrigadierCommand command = new AllianceBrigadierCommand(
                plugin, alliances, guilds, residents, governance);

        assertEquals(1, invoke(command, "handleSetKing", context));
        verify(alliances).setKing(alliance, targetId);

        assertEquals(1, invoke(command, "handleMinisterAdd", context));
        verify(alliances).addMinister(alliance, targetId);

        assertEquals(1, invoke(command, "handleMinisterRemove", context));
        verify(alliances).removeMinister(alliance, targetId);
    }

    private static int invoke(
            AllianceBrigadierCommand command,
            String methodName,
            CommandContext<CommandSourceStack> context
    ) throws Exception {
        Method method = AllianceBrigadierCommand.class.getDeclaredMethod(
                methodName, CommandContext.class);
        method.setAccessible(true);
        return (int) method.invoke(command, context);
    }
}
