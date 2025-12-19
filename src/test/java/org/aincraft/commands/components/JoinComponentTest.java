package org.aincraft.commands.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for JoinComponent command.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JoinComponent")
class JoinComponentTest {

    @Mock private GuildLifecycleService lifecycleService;
    @Mock private GuildMemberService memberService;
    @Mock private Player player;

    private JoinComponent joinComponent;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        joinComponent = new JoinComponent(lifecycleService, memberService);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
    }

    @Test
    @DisplayName("should have correct name")
    void shouldHaveCorrectName() {
        assertThat(joinComponent.getName()).isEqualTo("join");
    }

    @Test
    @DisplayName("should join guild successfully")
    void shouldJoinGuildSuccessfully() {
        Guild guild = new Guild("TestGuild", null, UUID.randomUUID());
        guild.setPublic(true);  // Make guild public
        when(player.hasPermission("guilds.join")).thenReturn(true);
        when(lifecycleService.getGuildByName("TestGuild")).thenReturn(guild);
        when(memberService.joinGuild(guild.getId(), playerId)).thenReturn(true);

        boolean result = joinComponent.execute(player, new String[]{"join", "TestGuild"});

        assertThat(result).isTrue();
        verify(memberService).joinGuild(guild.getId(), playerId);
    }

    @Test
    @DisplayName("should fail when guild not found")
    void shouldFailWhenGuildNotFound() {
        when(player.hasPermission("guilds.join")).thenReturn(true);
        when(lifecycleService.getGuildByName("NonExistent")).thenReturn(null);

        boolean result = joinComponent.execute(player, new String[]{"join", "NonExistent"});

        assertThat(result).isTrue();
        verify(memberService, never()).joinGuild(anyString(), any());
    }

    @Test
    @DisplayName("should fail when already in guild")
    void shouldFailWhenAlreadyInGuild() {
        Guild guild = new Guild("TestGuild", null, UUID.randomUUID());
        when(player.hasPermission("guilds.join")).thenReturn(true);
        when(lifecycleService.getGuildByName("TestGuild")).thenReturn(guild);
        when(memberService.joinGuild(guild.getId(), playerId)).thenReturn(false);

        boolean result = joinComponent.execute(player, new String[]{"join", "TestGuild"});

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should require guild name argument")
    void shouldRequireGuildNameArgument() {
        when(player.hasPermission("guilds.join")).thenReturn(true);

        boolean result = joinComponent.execute(player, new String[]{"join"});

        assertThat(result).isFalse();
    }
}
