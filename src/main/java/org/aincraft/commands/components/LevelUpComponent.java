package org.aincraft.commands.components;

import com.google.inject.Inject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.progression.*;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Command component for leveling up guilds.
 * Handles: /g upgrade [confirm]
 */
public class LevelUpComponent implements GuildCommand {
    private final ProgressionService progressionService;
    private final GuildMemberService memberService;
    private final PermissionService permissionService;
    private final ProgressionConfig config;

    @Inject
    public LevelUpComponent(ProgressionService progressionService,
                            GuildMemberService memberService,
                            PermissionService permissionService,
                            ProgressionConfig config) {
        this.progressionService = Objects.requireNonNull(progressionService, "Progression service cannot be null");
        this.memberService = Objects.requireNonNull(memberService, "Member service cannot be null");
        this.permissionService = Objects.requireNonNull(permissionService, "Permission service cannot be null");
        this.config = Objects.requireNonNull(config, "Config cannot be null");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        // Check permission
        if (!guild.isOwner(player.getUniqueId()) &&
            !permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.LEVEL_UP)) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        boolean confirm = args.length > 1 && args[1].equalsIgnoreCase("confirm");

        if (!confirm) {
            return showRequirements(player, guild);
        } else {
            return attemptLevelUp(player, guild);
        }
    }

    private boolean showRequirements(Player player, Guild guild) {
        GuildProgression progression = progressionService.getOrCreateProgression(guild.getId());

        if (progression.getLevel() >= config.getMaxLevel()) {
            Messages.send(player, MessageKey.LEVEL_UP_MAX_LEVEL);
            return true;
        }

        int currentLevel = progression.getLevel();
        int nextLevel = currentLevel + 1;
        long currentXp = progression.getCurrentXp();
        long xpRequired = progressionService.calculateXpRequired(nextLevel);
        LevelUpCost cost = progressionService.calculateLevelUpCost(guild.getId(), currentLevel);

        // Check if XP requirement met
        boolean hasEnoughXp = currentXp >= xpRequired;

        // Build message
        Component message = Component.text()
                .append(Component.text("═══ ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Level Up to " + nextLevel, NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ═══", NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("XP Required: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%,d", xpRequired), hasEnoughXp ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text(hasEnoughXp ? " ✓" : " ✗", hasEnoughXp ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("Current XP: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%,d", currentXp), NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Material Costs:", NamedTextColor.GOLD))
                .append(Component.newline())
                .build();

        // List materials
        for (Map.Entry<Material, Integer> entry : cost.getMaterials().entrySet()) {
            Material material = entry.getKey();
            int required = entry.getValue();

            String materialName = formatMaterialName(material);

            message = message
                    .append(Component.text("  • ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(required + "x ", NamedTextColor.YELLOW))
                    .append(Component.text(materialName, NamedTextColor.WHITE))
                    .append(Component.newline());
        }

        // Show rewards
        int currentMaxMembers = progressionService.calculateMaxMembers(currentLevel);
        int nextMaxMembers = progressionService.calculateMaxMembers(nextLevel);
        int currentMaxChunks = progressionService.calculateMaxChunks(currentLevel);
        int nextMaxChunks = progressionService.calculateMaxChunks(nextLevel);

        message = message
                .append(Component.newline())
                .append(Component.text("Rewards:", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("  • ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Max Members: ", NamedTextColor.GRAY))
                .append(Component.text(currentMaxMembers + " → " + nextMaxMembers, NamedTextColor.GREEN))
                .append(Component.newline())
                .append(Component.text("  • ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Max Chunks: ", NamedTextColor.GRAY))
                .append(Component.text(currentMaxChunks + " → " + nextMaxChunks, NamedTextColor.GREEN))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("To level up, type: ", NamedTextColor.GRAY))
                .append(Component.text("/g levelup confirm", NamedTextColor.YELLOW, TextDecoration.BOLD));

        player.sendMessage(message);
        return true;
    }

    private boolean attemptLevelUp(Player player, Guild guild) {
        LevelUpResult result = progressionService.attemptLevelUp(guild.getId(), player.getUniqueId());

        if (result.isSuccess()) {
            // Broadcast to all guild members
            for (UUID memberId : guild.getMembers()) {
                Player member = player.getServer().getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    Messages.send(member, MessageKey.LEVEL_UP_SUCCESS, result.getNewLevel());
                }
            }

            return true;
        } else {
            Messages.send(player, MessageKey.ERROR_USAGE, result.getErrorMessage());
            return true;
        }
    }

    private String formatMaterialName(Material material) {
        String name = material.name().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();

        for (String word : words) {
            if (!formatted.isEmpty()) {
                formatted.append(" ");
            }
            formatted.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }

        return formatted.toString();
    }

    @Override
    public String getName() {
        return "upgrade";
    }

    @Override
    public String getPermission() {
        return "guilds.upgrade";
    }

    @Override
    public String getUsage() {
        return "/g upgrade [confirm]";
    }
}
