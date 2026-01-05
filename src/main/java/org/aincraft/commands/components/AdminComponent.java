package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.progression.GuildProgression;
import org.aincraft.progression.ProgressionService;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.TerritoryService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Administrative commands for managing guilds.
 * Requires guilds.admin permission.
 */
public class AdminComponent implements GuildCommand {
    private final GuildLifecycleService lifecycleService;
    private final TerritoryService territoryService;
    private final ProgressionService progressionService;

    @Inject
    public AdminComponent(GuildLifecycleService lifecycleService, TerritoryService territoryService,
                         ProgressionService progressionService) {
        this.lifecycleService = lifecycleService;
        this.territoryService = territoryService;
        this.progressionService = progressionService;
    }

    @Override
    public String getName() {
        return "admin";
    }

    @Override
    public String getPermission() {
        return "guilds.admin";
    }

    @Override
    public String getUsage() {
        return "/g admin <disband|addchunk|removechunk|setowner|bypass|set|add|remove|reset> [args]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(getPermission())) {
            Messages.send(sender, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        if (args.length < 2) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: " + getUsage());
            return false;
        }

        String subCommand = args[1].toLowerCase();

        return switch (subCommand) {
            case "disband" -> executeDisband(sender, args);
            case "addchunk" -> executeAddChunk(sender, args);
            case "removechunk" -> executeRemoveChunk(sender, args);
            case "setowner" -> executeSetOwner(sender, args);
            case "bypass" -> executeBypass(sender, args);
            case "set" -> executeSet(sender, args);
            case "add" -> executeAdd(sender, args);
            case "remove" -> executeRemove(sender, args);
            case "reset" -> executeReset(sender, args);
            default -> {
                Messages.send(sender, MessageKey.ERROR_USAGE, "Unknown admin command: " + subCommand);
                Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: " + getUsage());
                yield false;
            }
        };
    }

    private boolean executeDisband(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin disband <guild>");
            return false;
        }

        String guildName = args[2];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Messages.send(sender, MessageKey.ERROR_GUILD_NOT_FOUND, guildName);
            return true;
        }

        boolean deleted = lifecycleService.deleteGuild(guild.getId(), guild.getOwnerId());
        if (deleted) {
            Messages.send(sender, MessageKey.ADMIN_FORCE_DISBAND, guild.getName());
        } else {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Failed to disband guild");
        }

        return true;
    }

    private boolean executeAddChunk(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (args.length < 3) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin addchunk <guild>");
            return false;
        }

        String guildName = args[2];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Messages.send(sender, MessageKey.ERROR_GUILD_NOT_FOUND, guildName);
            return true;
        }

        ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());

        // Admin claim - bypass all checks
        territoryService.adminClaimChunk(guild.getId(), chunk);

        Messages.send(player, MessageKey.ADMIN_FORCE_CLAIM, chunk.x(), chunk.z(), guild.getName());

        return true;
    }

    private boolean executeRemoveChunk(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());
        Guild owner = territoryService.getChunkOwner(chunk);

        if (owner == null) {
            Messages.send(sender, MessageKey.CLAIM_NOT_OWNED);
            return true;
        }

        territoryService.unclaimChunk(owner.getId(), player.getUniqueId(), chunk);

        Messages.send(player, MessageKey.ADMIN_FORCE_UNCLAIM, chunk.x(), chunk.z(), owner.getName());

        return true;
    }

    private boolean executeSetOwner(CommandSender sender, String[] args) {
        if (args.length < 4) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin setowner <guild> <player>");
            return false;
        }

        String guildName = args[2];
        String playerName = args[3];

        Guild guild = lifecycleService.getGuildByName(guildName);
        if (guild == null) {
            Messages.send(sender, MessageKey.ERROR_GUILD_NOT_FOUND, guildName);
            return true;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        UUID targetId = targetPlayer.getUniqueId();

        if (!guild.isMember(targetId)) {
            Messages.send(sender, MessageKey.ERROR_USAGE, playerName + " is not a member of " + guildName);
            return true;
        }

        guild.transferOwnership(targetId);
        lifecycleService.save(guild);

        Messages.send(sender, MessageKey.GUILD_TRANSFER_SUCCESS, guild.getName(), playerName);

        return true;
    }

    private boolean executeBypass(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        boolean currentBypass = player.hasPermission("guilds.admin.bypass");

        if (currentBypass) {
            Messages.send(sender, MessageKey.INFO_HEADER, "Admin bypass is ENABLED");
        } else {
            Messages.send(sender, MessageKey.INFO_HEADER, "Admin bypass is DISABLED");
        }

        return true;
    }

    private boolean executeSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin set <level|xp> <guild> <value>");
            return false;
        }

        String type = args[2].toLowerCase();

        return switch (type) {
            case "level" -> executeSetLevel(sender, args);
            case "xp" -> executeSetXp(sender, args);
            default -> {
                Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin set <level|xp> <guild> <value>");
                yield false;
            }
        };
    }

    private boolean executeAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin add <level|xp> <guild> <amount>");
            return false;
        }

        String type = args[2].toLowerCase();

        return switch (type) {
            case "level" -> executeAddLevel(sender, args);
            case "xp" -> executeAddXp(sender, args);
            default -> {
                Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin add <level|xp> <guild> <amount>");
                yield false;
            }
        };
    }

    private boolean executeRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin remove <level|xp> <guild> <amount>");
            return false;
        }

        String type = args[2].toLowerCase();

        return switch (type) {
            case "level" -> executeRemoveLevel(sender, args);
            case "xp" -> executeRemoveXp(sender, args);
            default -> {
                Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin remove <level|xp> <guild> <amount>");
                yield false;
            }
        };
    }

    private boolean executeReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin reset <level|xp> <guild>");
            return false;
        }

        String type = args[2].toLowerCase();

        return switch (type) {
            case "level" -> executeResetLevel(sender, args);
            case "xp" -> executeResetXp(sender, args);
            default -> {
                Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin reset <level|xp> <guild>");
                yield false;
            }
        };
    }

    private boolean executeSetLevel(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (args.length < 5) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin set level <guild> <level>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Messages.send(sender, MessageKey.ERROR_GUILD_NOT_FOUND, guildName);
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[4]);
            if (level < 1) {
                Messages.send(sender, MessageKey.ERROR_USAGE, "Level must be at least 1");
                return true;
            }
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Invalid level: " + args[4]);
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        int oldLevel = (progression != null) ? progression.getLevel() : 1;
        progressionService.setLevel(guild.getId(), level, player.getUniqueId());

        Messages.send(sender, MessageKey.ADMIN_RELOAD_SUCCESS, guild.getName(), level);

        return true;
    }

    private boolean executeSetXp(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (args.length < 5) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin set xp <guild> <amount>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Messages.send(sender, MessageKey.ERROR_GUILD_NOT_FOUND, guildName);
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[4]);
            if (amount < 0) {
                Messages.send(sender, MessageKey.ERROR_USAGE, "Amount cannot be negative");
                return true;
            }
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Invalid amount: " + args[4]);
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        long oldXp = (progression != null) ? progression.getCurrentXp() : 0;
        progressionService.setXp(guild.getId(), amount, player.getUniqueId());

        Messages.send(sender, MessageKey.ADMIN_RELOAD_SUCCESS, guild.getName(), amount);

        return true;
    }

    private boolean executeAddLevel(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (args.length < 5) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin add level <guild> <levels>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Messages.send(sender, MessageKey.ERROR_GUILD_NOT_FOUND, guildName);
            return true;
        }

        int levels;
        try {
            levels = Integer.parseInt(args[4]);
            if (levels <= 0) {
                Messages.send(sender, MessageKey.ERROR_USAGE, "Levels must be positive");
                return true;
            }
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Invalid levels: " + args[4]);
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        int oldLevel = (progression != null) ? progression.getLevel() : 1;
        int newLevel = oldLevel + levels;
        progressionService.setLevel(guild.getId(), newLevel, player.getUniqueId());

        Messages.send(sender, MessageKey.ADMIN_RELOAD_SUCCESS, guild.getName(), newLevel);

        return true;
    }

    private boolean executeAddXp(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (args.length < 5) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin add xp <guild> <amount>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Messages.send(sender, MessageKey.ERROR_GUILD_NOT_FOUND, guildName);
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[4]);
            if (amount <= 0) {
                Messages.send(sender, MessageKey.ERROR_USAGE, "Amount must be positive");
                return true;
            }
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Invalid amount: " + args[4]);
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        long oldXp = (progression != null) ? progression.getCurrentXp() : 0;
        progressionService.addXp(guild.getId(), amount, player.getUniqueId());

        Messages.send(sender, MessageKey.ADMIN_RELOAD_SUCCESS, guild.getName(), amount);

        return true;
    }

    private boolean executeRemoveXp(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (args.length < 5) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin remove xp <guild> <amount>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Messages.send(sender, MessageKey.ERROR_GUILD_NOT_FOUND, guildName);
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[4]);
            if (amount <= 0) {
                Messages.send(sender, MessageKey.ERROR_USAGE, "Amount must be positive");
                return true;
            }
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Invalid amount: " + args[4]);
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        long oldXp = (progression != null) ? progression.getCurrentXp() : 0;
        long newXp = Math.max(0, oldXp - amount);
        progressionService.setXp(guild.getId(), newXp, player.getUniqueId());

        Messages.send(sender, MessageKey.ADMIN_RELOAD_SUCCESS, guild.getName(), newXp);

        return true;
    }

    private boolean executeRemoveLevel(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (args.length < 5) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin remove level <guild> <levels>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Messages.send(sender, MessageKey.ERROR_GUILD_NOT_FOUND, guildName);
            return true;
        }

        int levels;
        try {
            levels = Integer.parseInt(args[4]);
            if (levels <= 0) {
                Messages.send(sender, MessageKey.ERROR_USAGE, "Levels must be positive");
                return true;
            }
        } catch (NumberFormatException e) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Invalid levels: " + args[4]);
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        int oldLevel = (progression != null) ? progression.getLevel() : 1;
        int newLevel = Math.max(1, oldLevel - levels);
        progressionService.setLevel(guild.getId(), newLevel, player.getUniqueId());

        Messages.send(sender, MessageKey.ADMIN_RELOAD_SUCCESS, guild.getName(), newLevel);

        return true;
    }

    private boolean executeResetLevel(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (args.length < 4) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin reset level <guild>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Messages.send(sender, MessageKey.ERROR_GUILD_NOT_FOUND, guildName);
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        int oldLevel = (progression != null) ? progression.getLevel() : 1;
        progressionService.setLevel(guild.getId(), 1, player.getUniqueId());

        Messages.send(sender, MessageKey.ADMIN_RELOAD_SUCCESS, guild.getName(), 1);

        return true;
    }

    private boolean executeResetXp(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (args.length < 4) {
            Messages.send(sender, MessageKey.ERROR_USAGE, "Usage: /g admin reset xp <guild>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Messages.send(sender, MessageKey.ERROR_GUILD_NOT_FOUND, guildName);
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        long oldXp = (progression != null) ? progression.getCurrentXp() : 0;
        progressionService.setXp(guild.getId(), 0L, player.getUniqueId());

        Messages.send(sender, MessageKey.ADMIN_RELOAD_SUCCESS, guild.getName(), 0);

        return true;
    }
}
