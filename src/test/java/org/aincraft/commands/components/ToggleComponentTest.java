package org.aincraft.commands.components;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.aincraft.Guild;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.GuildLifecycleService;
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
 * Unit tests for ToggleComponent command.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ToggleComponent")
class ToggleComponentTest {

    @Mock private GuildMemberService memberService;
    @Mock private GuildLifecycleService lifecycleService;
    @Mock private Player player;

    private ToggleComponent toggleComponent;
    private UUID playerId;
    private UUID ownerId;
    private Guild guild;

    @BeforeEach
    void setUp() {
        toggleComponent = new ToggleComponent(memberService, lifecycleService);
        playerId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        guild = new Guild("TestGuild", null, ownerId);

        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.hasPermission("guilds.toggle")).thenReturn(true);
        when(memberService.getPlayerGuild(ownerId)).thenReturn(guild);
    }

    @Test
    @DisplayName("should have correct name")
    void shouldHaveCorrectName() {
        assertThat(toggleComponent.getName()).isEqualTo("toggle");
    }

    @Test
    @DisplayName("should toggle explosions on")
    void shouldToggleExplosionsOn() {
        guild.setExplosionsAllowed(false);

        boolean result = toggleComponent.execute(player, new String[]{"toggle", "explosions"});

        assertThat(result).isTrue();
        assertThat(guild.isExplosionsAllowed()).isTrue();
        verify(lifecycleService).save(guild);
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("should toggle explosions off")
    void shouldToggleExplosionsOff() {
        guild.setExplosionsAllowed(true);

        boolean result = toggleComponent.execute(player, new String[]{"toggle", "explosions"});

        assertThat(result).isTrue();
        assertThat(guild.isExplosionsAllowed()).isFalse();
        verify(lifecycleService).save(guild);
    }

    @Test
    @DisplayName("should toggle fire on")
    void shouldToggleFireOn() {
        guild.setFireAllowed(false);

        boolean result = toggleComponent.execute(player, new String[]{"toggle", "fire"});

        assertThat(result).isTrue();
        assertThat(guild.isFireAllowed()).isTrue();
        verify(lifecycleService).save(guild);
    }

    @Test
    @DisplayName("should toggle fire off")
    void shouldToggleFireOff() {
        guild.setFireAllowed(true);

        boolean result = toggleComponent.execute(player, new String[]{"toggle", "fire"});

        assertThat(result).isTrue();
        assertThat(guild.isFireAllowed()).isFalse();
        verify(lifecycleService).save(guild);
    }

    @Test
    @DisplayName("should toggle public on")
    void shouldTogglePublicOn() {
        guild.setPublic(false);

        boolean result = toggleComponent.execute(player, new String[]{"toggle", "public"});

        assertThat(result).isTrue();
        assertThat(guild.isPublic()).isTrue();
        verify(lifecycleService).save(guild);
    }

    @Test
    @DisplayName("should toggle public off")
    void shouldTogglePublicOff() {
        guild.setPublic(true);

        boolean result = toggleComponent.execute(player, new String[]{"toggle", "public"});

        assertThat(result).isTrue();
        assertThat(guild.isPublic()).isFalse();
        verify(lifecycleService).save(guild);
    }

    @Test
    @DisplayName("should accept explosion as alias for explosions")
    void shouldAcceptExplosionAlias() {
        guild.setExplosionsAllowed(false);

        boolean result = toggleComponent.execute(player, new String[]{"toggle", "explosion"});

        assertThat(result).isTrue();
        assertThat(guild.isExplosionsAllowed()).isTrue();
    }

    @Test
    @DisplayName("should fail when not in guild")
    void shouldFailWhenNotInGuild() {
        when(memberService.getPlayerGuild(ownerId)).thenReturn(null);

        boolean result = toggleComponent.execute(player, new String[]{"toggle", "explosions"});

        assertThat(result).isTrue();
        verify(lifecycleService, never()).save(any());
    }

    @Test
    @DisplayName("should fail when not guild owner")
    void shouldFailWhenNotGuildOwner() {
        UUID nonOwner = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(nonOwner);
        when(memberService.getPlayerGuild(nonOwner)).thenReturn(guild);

        boolean result = toggleComponent.execute(player, new String[]{"toggle", "explosions"});

        assertThat(result).isTrue();
        verify(lifecycleService, never()).save(any());
    }

    @Test
    @DisplayName("should fail when no permission")
    void shouldFailWhenNoPermission() {
        when(player.hasPermission("guilds.toggle")).thenReturn(false);

        boolean result = toggleComponent.execute(player, new String[]{"toggle", "explosions"});

        assertThat(result).isTrue();
        verify(lifecycleService, never()).save(any());
    }

    @Test
    @DisplayName("should require setting argument")
    void shouldRequireSettingArgument() {
        boolean result = toggleComponent.execute(player, new String[]{"toggle"});

        assertThat(result).isFalse();
        verify(lifecycleService, never()).save(any());
    }

    @Test
    @DisplayName("should fail with unknown setting")
    void shouldFailWithUnknownSetting() {
        boolean result = toggleComponent.execute(player, new String[]{"toggle", "invalid"});

        assertThat(result).isTrue();
        verify(lifecycleService, never()).save(any());
    }
}
