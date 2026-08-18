package dev.mintychochip.territory.building;

import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.guilds.services.PermissionService;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/** Centralizes guild-id/name conversion and form-aware facility authority. */
public final class BuildingAuthorization {
    public static final String MANAGE_PERMISSION = "set_spawn";

    private final GuildService guilds;
    private final PermissionService permissions;

    public BuildingAuthorization(GuildService guilds, PermissionService permissions) {
        this.guilds = Objects.requireNonNull(guilds, "guilds");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    public boolean canManage(Player player, Territory territory) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(territory, "territory");
        if (player.hasPermission("azoth.territory.admin") || player.isOp()) {
            return true;
        }
        Guild guild = territory.governedByGuildId()
                .flatMap(guilds::getGuildById)
                .orElse(null);
        UUID playerId = player.getUniqueId();
        return guild != null && guild.isResident(playerId)
                && permissions.hasPermission(playerId, MANAGE_PERMISSION, "guild", guild.getName());
    }

    public boolean canUseWaystones(UUID playerId, String guildId) {
        if (playerId == null || guildId == null || guildId.isBlank()) {
            return false;
        }
        return guilds.getGuildById(guildId.trim())
                .map(guild -> guild.isResident(playerId))
                .orElse(false);
    }
}
