package org.aincraft.guilds.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Internal PlaceholderAPI expansion for player guild data.
 *
 * <p>The expansion is intentionally backed by the service interfaces instead of
 * Bukkit state, so placeholders also work for offline players when a chat
 * formatter supplies an {@link OfflinePlayer}.</p>
 */
public final class GuildsPlaceholderExpansion extends PlaceholderExpansion {

    private static final List<String> PLACEHOLDERS = List.of(
            "guild",
            "guild_name",
            "guild_id",
            "role",
            "guild_role",
            "level",
            "guild_level",
            "balance",
            "guild_balance",
            "members",
            "guild_members",
            "open",
            "guild_open",
            "in_guild",
            "has_guild",
            "chat_prefix"
    );

    private final JavaPlugin plugin;
    private final GuildService guildService;
    private final ResidentService residentService;

    public GuildsPlaceholderExpansion(
            JavaPlugin plugin,
            GuildService guildService,
            ResidentService residentService) {
        this.plugin = plugin;
        this.guildService = guildService;
        this.residentService = residentService;
    }

    @Override
    public String getAuthor() {
        List<String> authors = plugin.getDescription().getAuthors();
        return authors.isEmpty() ? "Aincraft" : String.join(", ", authors);
    }

    @Override
    public String getIdentifier() {
        return "guilds";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public List<String> getPlaceholders() {
        return PLACEHOLDERS;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        if (!PLACEHOLDERS.contains(key)) {
            return null;
        }

        Resident resident = findResident(player);
        boolean inGuild = resident != null && resident.hasGuild();
        if ("in_guild".equals(key) || "has_guild".equals(key)) {
            return Boolean.toString(inGuild);
        }
        if (!inGuild) {
            return emptyGuildValue(key);
        }

        String guildName = resident.getGuild();
        Guild guild = findGuild(guildName);
        return switch (key) {
            case "guild", "guild_name" -> guildName;
            case "guild_id" -> guild == null ? "" : valueOrEmpty(guild.getId());
            case "role", "guild_role" -> roleFor(guild, player.getUniqueId());
            case "level", "guild_level" -> guild == null ? "0" : Integer.toString(guild.getGuildLevel());
            case "balance", "guild_balance" -> guild == null
                    ? "0.00"
                    : String.format(Locale.ROOT, "%.2f", guild.getBalance());
            case "members", "guild_members" -> guild == null
                    ? "0"
                    : Integer.toString(guild.getResidents().size());
            case "open", "guild_open" -> guild == null ? "false" : Boolean.toString(guild.isOpen());
            case "chat_prefix" -> guild == null ? "" : "[" + guildName + "]";
            default -> null;
        };
    }

    private Resident findResident(OfflinePlayer player) {
        if (player == null) {
            return null;
        }
        try {
            return residentService.getResident(player.getUniqueId()).orElse(null);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE,
                    "Unable to resolve resident placeholder data for " + player.getUniqueId(), exception);
            return null;
        }
    }

    private Guild findGuild(String guildName) {
        if (guildName == null || guildName.isBlank()) {
            return null;
        }
        try {
            return guildService.getGuild(guildName).orElse(null);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE,
                    "Unable to resolve guild placeholder data for " + guildName, exception);
            return null;
        }
    }

    private static String emptyGuildValue(String key) {
        return switch (key) {
            case "role", "guild_role" -> "none";
            case "level", "guild_level", "members", "guild_members" -> "0";
            case "balance", "guild_balance" -> "0.00";
            case "open", "guild_open" -> "false";
            default -> "";
        };
    }

    private static String roleFor(Guild guild, UUID playerUuid) {
        if (guild == null) {
            return "none";
        }
        if (playerUuid.equals(guild.getMayorUuid())) {
            return "mayor";
        }
        if (guild.getAssistants().contains(playerUuid)) {
            return "assistant";
        }
        if (guild.getResidents().contains(playerUuid)) {
            return "resident";
        }
        return "none";
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
