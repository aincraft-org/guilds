package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to use admin commands"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
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
                sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Unknown admin command: " + subCommand));
                sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
                yield false;
            }
        };
    }

    private boolean executeDisband(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin disband <guild>"));
            return false;
        }

        String guildName = args[2];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild not found: " + guildName));
            return true;
        }

        boolean deleted = lifecycleService.deleteGuild(guild.getId(), guild.getOwnerId());
        if (deleted) {
            sender.sendMessage(MessageFormatter.format("<green>✓ Guild '<gold>%s</gold>' has been disbanded</green>", guild.getName()));
        } else {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Failed to disband guild"));
        }

        return true;
    }

    private boolean executeAddChunk(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "This command must be used by a player"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin addchunk <guild>"));
            return false;
        }

        String guildName = args[2];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild not found: " + guildName));
            return true;
        }

        ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());

        // Admin claim - bypass all checks
        territoryService.adminClaimChunk(guild.getId(), chunk);

        player.sendMessage(MessageFormatter.format("<green>✓ Claimed chunk <gold>%d, %d</gold> for guild '<gold>%s</gold>'</green>",
            chunk.x(), chunk.z(), guild.getName()));

        return true;
    }

    private boolean executeRemoveChunk(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "This command must be used by a player"));
            return true;
        }

        ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());
        Guild owner = territoryService.getChunkOwner(chunk);

        if (owner == null) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "This chunk is not claimed"));
            return true;
        }

        territoryService.unclaimChunk(owner.getId(), player.getUniqueId(), chunk);

        player.sendMessage(MessageFormatter.format("<green>✓ Removed chunk <gold>%d, %d</gold> from guild '<gold>%s</gold>'</green>",
            chunk.x(), chunk.z(), owner.getName()));

        return true;
    }

    private boolean executeSetOwner(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin setowner <guild> <player>"));
            return false;
        }

        String guildName = args[2];
        String playerName = args[3];

        Guild guild = lifecycleService.getGuildByName(guildName);
        if (guild == null) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild not found: " + guildName));
            return true;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        UUID targetId = targetPlayer.getUniqueId();

        if (!guild.isMember(targetId)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, playerName + " is not a member of " + guildName));
            return true;
        }

        guild.transferOwnership(targetId);
        lifecycleService.save(guild);

        sender.sendMessage(MessageFormatter.format("<green>✓ Transferred ownership of '<gold>%s</gold>' to <gold>%s</gold></green>",
            guild.getName(), playerName));

        return true;
    }

    private boolean executeBypass(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "This command must be used by a player"));
            return true;
        }

        boolean currentBypass = player.hasPermission("guilds.admin.bypass");

        if (currentBypass) {
            sender.sendMessage(MessageFormatter.format("<green>✓ Admin bypass is <gold>ENABLED</gold></green>"));
            sender.sendMessage(MessageFormatter.format("<gray>You can break/place blocks in any guild claim</gray>"));
        } else {
            sender.sendMessage(MessageFormatter.format("<red>✗ Admin bypass is <gold>DISABLED</gold></red>"));
            sender.sendMessage(MessageFormatter.format("<gray>Grant yourself guilds.admin.bypass to enable</gray>"));
        }

        return true;
    }

    private boolean executeSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin set <level|xp> <guild> <value>"));
            return false;
        }

        String type = args[2].toLowerCase();

        return switch (type) {
            case "level" -> executeSetLevel(sender, args);
            case "xp" -> executeSetXp(sender, args);
            default -> {
                sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin set <level|xp> <guild> <value>"));
                yield false;
            }
        };
    }

    private boolean executeAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin add <level|xp> <guild> <amount>"));
            return false;
        }

        String type = args[2].toLowerCase();

        return switch (type) {
            case "level" -> executeAddLevel(sender, args);
            case "xp" -> executeAddXp(sender, args);
            default -> {
                sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin add <level|xp> <guild> <amount>"));
                yield false;
            }
        };
    }

    private boolean executeRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin remove <level|xp> <guild> <amount>"));
            return false;
        }

        String type = args[2].toLowerCase();

        return switch (type) {
            case "level" -> executeRemoveLevel(sender, args);
            case "xp" -> executeRemoveXp(sender, args);
            default -> {
                sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin remove <level|xp> <guild> <amount>"));
                yield false;
            }
        };
    }

    private boolean executeReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin reset <level|xp> <guild>"));
            return false;
        }

        String type = args[2].toLowerCase();

        return switch (type) {
            case "level" -> executeResetLevel(sender, args);
            case "xp" -> executeResetXp(sender, args);
            default -> {
                sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin reset <level|xp> <guild>"));
                yield false;
            }
        };
    }

    private boolean executeSetLevel(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin set level <guild> <level>"));
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild not found: " + guildName));
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[4]);
            if (level < 1) {
                sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Level must be at least 1"));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid level: " + args[4]));
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        int oldLevel = (progression != null) ? progression.getLevel() : 1;
        progressionService.setLevel(guild.getId(), level, player.getUniqueId());

        sender.sendMessage(MessageFormatter.format("<green>✓ Set <gold>%s</gold> level to <gold>%d</gold> (was %d)</green>",
            guild.getName(), level, oldLevel));

        return true;
    }

    private boolean executeSetXp(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin set xp <guild> <amount>"));
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild not found: " + guildName));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[4]);
            if (amount < 0) {
                sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Amount cannot be negative"));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid amount: " + args[4]));
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        long oldXp = (progression != null) ? progression.getCurrentXp() : 0;
        progressionService.setXp(guild.getId(), amount, player.getUniqueId());

        sender.sendMessage(MessageFormatter.format("<green>✓ Set <gold>%s</gold> XP to <gold>%,d</gold> (was %,d)</green>",
            guild.getName(), amount, oldXp));

        return true;
    }

    private boolean executeAddLevel(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin add level <guild> <levels>"));
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild not found: " + guildName));
            return true;
        }

        int levels;
        try {
            levels = Integer.parseInt(args[4]);
            if (levels <= 0) {
                sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Levels must be positive"));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid levels: " + args[4]));
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        int oldLevel = (progression != null) ? progression.getLevel() : 1;
        int newLevel = oldLevel + levels;
        progressionService.setLevel(guild.getId(), newLevel, player.getUniqueId());

        sender.sendMessage(MessageFormatter.format("<green>✓ Added <gold>%d</gold> level(s) to <gold>%s</gold> (was %d, now %d)</green>",
            levels, guild.getName(), oldLevel, newLevel));

        return true;
    }

    private boolean executeAddXp(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin add xp <guild> <amount>"));
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild not found: " + guildName));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[4]);
            if (amount <= 0) {
                sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Amount must be positive"));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid amount: " + args[4]));
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        long oldXp = (progression != null) ? progression.getCurrentXp() : 0;
        progressionService.addXp(guild.getId(), amount, player.getUniqueId());

        sender.sendMessage(MessageFormatter.format("<green>✓ Added <gold>%,d</gold> XP to <gold>%s</gold> (was %,d)</green>",
            amount, guild.getName(), oldXp));

        return true;
    }

    private boolean executeRemoveXp(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin remove xp <guild> <amount>"));
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild not found: " + guildName));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[4]);
            if (amount <= 0) {
                sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Amount must be positive"));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid amount: " + args[4]));
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        long oldXp = (progression != null) ? progression.getCurrentXp() : 0;
        long newXp = Math.max(0, oldXp - amount);
        progressionService.setXp(guild.getId(), newXp, player.getUniqueId());

        sender.sendMessage(MessageFormatter.format("<green>✓ Removed <gold>%,d</gold> XP from <gold>%s</gold> (was %,d, now %,d)</green>",
            amount, guild.getName(), oldXp, newXp));

        return true;
    }

    private boolean executeRemoveLevel(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin remove level <guild> <levels>"));
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild not found: " + guildName));
            return true;
        }

        int levels;
        try {
            levels = Integer.parseInt(args[4]);
            if (levels <= 0) {
                sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Levels must be positive"));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid levels: " + args[4]));
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        int oldLevel = (progression != null) ? progression.getLevel() : 1;
        int newLevel = Math.max(1, oldLevel - levels);
        progressionService.setLevel(guild.getId(), newLevel, player.getUniqueId());

        sender.sendMessage(MessageFormatter.format("<green>✓ Removed <gold>%d</gold> level(s) from <gold>%s</gold> (was %d, now %d)</green>",
            levels, guild.getName(), oldLevel, newLevel));

        return true;
    }

    private boolean executeResetLevel(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin reset level <guild>"));
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild not found: " + guildName));
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        int oldLevel = (progression != null) ? progression.getLevel() : 1;
        progressionService.setLevel(guild.getId(), 1, player.getUniqueId());

        sender.sendMessage(MessageFormatter.format("<green>✓ Reset <gold>%s</gold> level to 1 (was %d)</green>",
            guild.getName(), oldLevel));

        return true;
    }

    private boolean executeResetXp(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g admin reset xp <guild>"));
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild not found: " + guildName));
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        long oldXp = (progression != null) ? progression.getCurrentXp() : 0;
        progressionService.setXp(guild.getId(), 0L, player.getUniqueId());

        sender.sendMessage(MessageFormatter.format("<green>✓ Reset XP for <gold>%s</gold> (was %,d)</green>",
            guild.getName(), oldXp));

        return true;
    }
}
