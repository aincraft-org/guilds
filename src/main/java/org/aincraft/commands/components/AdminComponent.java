package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
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
            Mint.sendMessage(sender, "<error>You don't have <accent>permission</accent> to use this command.</error>");
            return true;
        }

        if (args.length < 2) {
            Mint.sendMessage(sender, "<error>Usage: " + getUsage() + "</error>");
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
            case "addchunk" -> executeAddChunk(sender, args);
            case "removechunk" -> executeRemoveChunk(sender, args);
            case "setowner" -> executeSetOwner(sender, args);
            case "bypass" -> executeBypass(sender, args);
            case "set" -> executeSet(sender, args);
            case "add" -> executeAdd(sender, args);
            case "remove" -> executeRemove(sender, args);
            case "reset" -> executeReset(sender, args);
            default -> {
                Mint.sendMessage(sender, "<error>Unknown admin command: <accent>" + subCommand + "</accent></error>");
                Mint.sendMessage(sender, "<error>Usage: " + getUsage() + "</error>");
                yield false;
            }
        };
    }

    private boolean executeDisband(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(sender, "<error>Usage: /g admin disband <guild></error>");
            return false;
        }

        String guildName = args[2];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Mint.sendMessage(sender, "<error>Guild '" + guildName + "' not found.</error>");
            return true;
        }

        boolean deleted = lifecycleService.deleteGuild(guild.getId(), guild.getOwnerId());
        if (deleted) {
            Mint.sendMessage(sender, "<success>Force disbanded guild <secondary>" + guild.getName() + "</secondary>.</success>");
        } else {
            Mint.sendMessage(sender, "<error>Failed to disband guild</error>");
        }

        return true;
    }

    private boolean executeAddChunk(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        if (args.length < 3) {
            Mint.sendMessage(sender, "<error>Usage: /g admin addchunk <guild></error>");
            return false;
        }

        String guildName = args[2];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Mint.sendMessage(sender, "<error>Guild '" + guildName + "' not found.</error>");
            return true;
        }

        ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());

        territoryService.adminClaimChunk(guild.getId(), chunk);

        Mint.sendMessage(player, "<success>Force claimed chunk at <primary>(" + chunk.x() + ", " + chunk.z() + ")</primary> for guild <secondary>" + guild.getName() + "</secondary>.</success>");

        return true;
    }

    private boolean executeRemoveChunk(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());
        Guild owner = territoryService.getChunkOwner(chunk);

        if (owner == null) {
            Mint.sendMessage(sender, "<neutral>Chunk is not claimed by any guild.</neutral>");
            return true;
        }

        territoryService.unclaimChunk(owner.getId(), player.getUniqueId(), chunk);

        Mint.sendMessage(player, "<success>Force unclaimed chunk at <primary>(" + chunk.x() + ", " + chunk.z() + ")</primary> from guild <secondary>" + owner.getName() + "</secondary>.</success>");

        return true;
    }

    private boolean executeSetOwner(CommandSender sender, String[] args) {
        if (args.length < 4) {
            Mint.sendMessage(sender, "<error>Usage: /g admin setowner <guild> <player></error>");
            return false;
        }

        String guildName = args[2];
        String playerName = args[3];

        Guild guild = lifecycleService.getGuildByName(guildName);
        if (guild == null) {
            Mint.sendMessage(sender, "<error>Guild '" + guildName + "' not found.</error>");
            return true;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        UUID targetId = targetPlayer.getUniqueId();

        if (!guild.isMember(targetId)) {
            Mint.sendMessage(sender, "<error>" + playerName + " is not a member of " + guildName + "</error>");
            return true;
        }

        guild.transferOwnership(targetId);
        lifecycleService.save(guild);

        Mint.sendMessage(sender, "<success>Transferred ownership of guild <secondary>" + guild.getName() + "</secondary> to <secondary>" + playerName + "</secondary>.</success>");

        return true;
    }

    private boolean executeBypass(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        boolean currentBypass = player.hasPermission("guilds.admin.bypass");

        if (currentBypass) {
            Mint.sendMessage(sender, "<primary>Admin bypass is <accent>ENABLED</accent></primary>");
        } else {
            Mint.sendMessage(sender, "<primary>Admin bypass is <accent>DISABLED</accent></primary>");
        }

        return true;
    }

    private boolean executeSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(sender, "<error>Usage: /g admin set <level|xp> <guild> <value></error>");
            return false;
        }

        String type = args[2].toLowerCase();

        return switch (type) {
            case "level" -> executeSetLevel(sender, args);
            case "xp" -> executeSetXp(sender, args);
            default -> {
                Mint.sendMessage(sender, "<error>Usage: /g admin set <level|xp> <guild> <value></error>");
                yield false;
            }
        };
    }

    private boolean executeAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(sender, "<error>Usage: /g admin add <level|xp> <guild> <amount></error>");
            return false;
        }

        String type = args[2].toLowerCase();

        return switch (type) {
            case "level" -> executeAddLevel(sender, args);
            case "xp" -> executeAddXp(sender, args);
            default -> {
                Mint.sendMessage(sender, "<error>Usage: /g admin add <level|xp> <guild> <amount></error>");
                yield false;
            }
        };
    }

    private boolean executeRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(sender, "<error>Usage: /g admin remove <level|xp> <guild> <amount></error>");
            return false;
        }

        String type = args[2].toLowerCase();

        return switch (type) {
            case "level" -> executeRemoveLevel(sender, args);
            case "xp" -> executeRemoveXp(sender, args);
            default -> {
                Mint.sendMessage(sender, "<error>Usage: /g admin remove <level|xp> <guild> <amount></error>");
                yield false;
            }
        };
    }

    private boolean executeReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(sender, "<error>Usage: /g admin reset <level|xp> <guild></error>");
            return false;
        }

        String type = args[2].toLowerCase();

        return switch (type) {
            case "level" -> executeResetLevel(sender, args);
            case "xp" -> executeResetXp(sender, args);
            default -> {
                Mint.sendMessage(sender, "<error>Usage: /g admin reset <level|xp> <guild></error>");
                yield false;
            }
        };
    }

    private boolean executeSetLevel(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        if (args.length < 5) {
            Mint.sendMessage(sender, "<error>Usage: /g admin set level <guild> <level></error>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Mint.sendMessage(sender, "<error>Guild '" + guildName + "' not found.</error>");
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[4]);
            if (level < 1) {
                Mint.sendMessage(sender, "<error>Level must be at least 1</error>");
                return true;
            }
        } catch (NumberFormatException e) {
            Mint.sendMessage(sender, "<error>Invalid level: " + args[4] + "</error>");
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        int oldLevel = (progression != null) ? progression.getLevel() : 1;
        progressionService.setLevel(guild.getId(), level, player.getUniqueId());

        Mint.sendMessage(sender, "<primary>Guild <secondary>" + guild.getName() + "</secondary> reloaded successfully.</primary>");

        return true;
    }

    private boolean executeSetXp(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        if (args.length < 5) {
            Mint.sendMessage(sender, "<error>Usage: /g admin set xp <guild> <amount></error>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Mint.sendMessage(sender, "<error>Guild '" + guildName + "' not found.</error>");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[4]);
            if (amount < 0) {
                Mint.sendMessage(sender, "<error>Amount cannot be negative</error>");
                return true;
            }
        } catch (NumberFormatException e) {
            Mint.sendMessage(sender, "<error>Invalid amount: " + args[4] + "</error>");
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        long oldXp = (progression != null) ? progression.getCurrentXp() : 0;
        progressionService.setXp(guild.getId(), amount, player.getUniqueId());

        Mint.sendMessage(sender, "<primary>Guild <secondary>" + guild.getName() + "</secondary> reloaded successfully.</primary>");

        return true;
    }

    private boolean executeAddLevel(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        if (args.length < 5) {
            Mint.sendMessage(sender, "<error>Usage: /g admin add level <guild> <levels></error>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Mint.sendMessage(sender, "<error>Guild '" + guildName + "' not found.</error>");
            return true;
        }

        int levels;
        try {
            levels = Integer.parseInt(args[4]);
            if (levels <= 0) {
                Mint.sendMessage(sender, "<error>Levels must be positive</error>");
                return true;
            }
        } catch (NumberFormatException e) {
            Mint.sendMessage(sender, "<error>Invalid levels: " + args[4] + "</error>");
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        int oldLevel = (progression != null) ? progression.getLevel() : 1;
        int newLevel = oldLevel + levels;
        progressionService.setLevel(guild.getId(), newLevel, player.getUniqueId());

        Mint.sendMessage(sender, "<primary>Guild <secondary>" + guild.getName() + "</secondary> reloaded successfully.</primary>");

        return true;
    }

    private boolean executeAddXp(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        if (args.length < 5) {
            Mint.sendMessage(sender, "<error>Usage: /g admin add xp <guild> <amount></error>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Mint.sendMessage(sender, "<error>Guild '" + guildName + "' not found.</error>");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[4]);
            if (amount <= 0) {
                Mint.sendMessage(sender, "<error>Amount must be positive</error>");
                return true;
            }
        } catch (NumberFormatException e) {
            Mint.sendMessage(sender, "<error>Invalid amount: " + args[4] + "</error>");
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        long oldXp = (progression != null) ? progression.getCurrentXp() : 0;
        progressionService.addXp(guild.getId(), amount, player.getUniqueId());

        Mint.sendMessage(sender, "<primary>Guild <secondary>" + guild.getName() + "</secondary> reloaded successfully.</primary>");

        return true;
    }

    private boolean executeRemoveXp(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        if (args.length < 5) {
            Mint.sendMessage(sender, "<error>Usage: /g admin remove xp <guild> <amount></error>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Mint.sendMessage(sender, "<error>Guild '" + guildName + "' not found.</error>");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[4]);
            if (amount <= 0) {
                Mint.sendMessage(sender, "<error>Amount must be positive</error>");
                return true;
            }
        } catch (NumberFormatException e) {
            Mint.sendMessage(sender, "<error>Invalid amount: " + args[4] + "</error>");
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        long oldXp = (progression != null) ? progression.getCurrentXp() : 0;
        long newXp = Math.max(0, oldXp - amount);
        progressionService.setXp(guild.getId(), newXp, player.getUniqueId());

        Mint.sendMessage(sender, "<primary>Guild <secondary>" + guild.getName() + "</secondary> reloaded successfully.</primary>");

        return true;
    }

    private boolean executeRemoveLevel(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        if (args.length < 5) {
            Mint.sendMessage(sender, "<error>Usage: /g admin remove level <guild> <levels></error>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Mint.sendMessage(sender, "<error>Guild '" + guildName + "' not found.</error>");
            return true;
        }

        int levels;
        try {
            levels = Integer.parseInt(args[4]);
            if (levels <= 0) {
                Mint.sendMessage(sender, "<error>Levels must be positive</error>");
                return true;
            }
        } catch (NumberFormatException e) {
            Mint.sendMessage(sender, "<error>Invalid levels: " + args[4] + "</error>");
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        int oldLevel = (progression != null) ? progression.getLevel() : 1;
        int newLevel = Math.max(1, oldLevel - levels);
        progressionService.setLevel(guild.getId(), newLevel, player.getUniqueId());

        Mint.sendMessage(sender, "<primary>Guild <secondary>" + guild.getName() + "</secondary> reloaded successfully.</primary>");

        return true;
    }

    private boolean executeResetLevel(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        if (args.length < 4) {
            Mint.sendMessage(sender, "<error>Usage: /g admin reset level <guild></error>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Mint.sendMessage(sender, "<error>Guild '" + guildName + "' not found.</error>");
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        int oldLevel = (progression != null) ? progression.getLevel() : 1;
        progressionService.setLevel(guild.getId(), 1, player.getUniqueId());

        Mint.sendMessage(sender, "<primary>Guild <secondary>" + guild.getName() + "</secondary> reloaded successfully.</primary>");

        return true;
    }

    private boolean executeResetXp(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        if (args.length < 4) {
            Mint.sendMessage(sender, "<error>Usage: /g admin reset xp <guild></error>");
            return false;
        }

        String guildName = args[3];
        Guild guild = lifecycleService.getGuildByName(guildName);

        if (guild == null) {
            Mint.sendMessage(sender, "<error>Guild '" + guildName + "' not found.</error>");
            return true;
        }

        GuildProgression progression = progressionService.getProgression(guild.getId()).orElse(null);
        long oldXp = (progression != null) ? progression.getCurrentXp() : 0;
        progressionService.setXp(guild.getId(), 0L, player.getUniqueId());

        Mint.sendMessage(sender, "<primary>Guild <secondary>" + guild.getName() + "</secondary> reloaded successfully.</primary>");

        return true;
    }
}
