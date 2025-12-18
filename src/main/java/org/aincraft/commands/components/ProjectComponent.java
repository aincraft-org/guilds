package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.project.*;
import org.aincraft.project.gui.BuffStatusGUI;
import org.aincraft.project.gui.ProjectDetailsGUI;
import org.aincraft.project.gui.ProjectListGUI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ProjectComponent implements GuildCommand {

    private final ProjectService projectService;
    private final ProjectRegistry registry;
    private final GuildService guildService;

    @Inject
    public ProjectComponent(ProjectService projectService, ProjectRegistry registry, GuildService guildService) {
        this.projectService = Objects.requireNonNull(projectService);
        this.registry = Objects.requireNonNull(registry);
        this.guildService = Objects.requireNonNull(guildService);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        // Default to list/GUI if no subcommand
        if (args.length <= 1) {
            return openProjectList(player, guild);
        }

        String subcommand = args[1].toLowerCase();
        return switch (subcommand) {
            case "list" -> openProjectList(player, guild);
            case "start" -> handleStart(player, guild, args);
            case "progress" -> handleProgress(player, guild);
            case "contribute" -> handleContribute(player, guild);
            case "complete" -> handleComplete(player, guild);
            case "abandon" -> handleAbandon(player, guild);
            case "buffs", "buff" -> handleBuffs(player, guild);
            default -> {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Unknown subcommand: " + subcommand));
                yield true;
            }
        };
    }

    private boolean openProjectList(Player player, Guild guild) {
        int guildLevel = 1; // TODO: get from ProgressionService
        ProjectListGUI gui = new ProjectListGUI(guild, player, projectService, guildLevel);
        player.openInventory(gui.getInventory());
        return true;
    }

    private boolean handleStart(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g project start <project_id>"));
            return true;
        }

        String projectId = args[2];
        ProjectService.ProjectStartResult result = projectService.startProject(guild.getId(), player.getUniqueId(), projectId);

        if (result.success()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.SUCCESS, "Started project: " + projectId));

            // Open details GUI
            Optional<ProjectDefinition> def = registry.getProject(projectId);
            if (def.isPresent()) {
                ProjectDetailsGUI gui = new ProjectDetailsGUI(guild, player, projectService, def.get(), result.project());
                player.openInventory(gui.getInventory());
            }
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, result.errorMessage()));
        }

        return true;
    }

    private boolean handleProgress(Player player, Guild guild) {
        Optional<GuildProject> projectOpt = projectService.getActiveProject(guild.getId());
        if (projectOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "No active project. Use /g project to see available projects."));
            return true;
        }

        GuildProject project = projectOpt.get();
        Optional<ProjectDefinition> defOpt = registry.getProject(project.getProjectDefinitionId());
        if (defOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Project definition not found"));
            return true;
        }

        // Open details GUI
        ProjectDetailsGUI gui = new ProjectDetailsGUI(guild, player, projectService, defOpt.get(), project);
        player.openInventory(gui.getInventory());
        return true;
    }

    private boolean handleContribute(Player player, Guild guild) {
        ProjectService.MaterialContributionResult result = projectService.contributeMaterials(guild.getId(), player.getUniqueId());

        if (result.success()) {
            StringBuilder msg = new StringBuilder("Contributed materials: ");
            for (Map.Entry<org.bukkit.Material, Integer> entry : result.contributed().entrySet()) {
                msg.append(entry.getValue()).append("x ").append(formatMaterialName(entry.getKey())).append(", ");
            }
            player.sendMessage(MessageFormatter.format(MessageFormatter.SUCCESS, msg.toString()));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, result.errorMessage()));
        }

        return true;
    }

    private boolean handleComplete(Player player, Guild guild) {
        ProjectService.ProjectCompletionResult result = projectService.completeProject(guild.getId(), player.getUniqueId());

        if (result.success()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.SUCCESS,
                    "Project completed! Buff activated: " + result.buff().categoryId() +
                            " for " + formatDuration(result.buff().getRemainingMillis())));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, result.errorMessage()));
            if (result.progress() > 0 && result.progress() < 1.0) {
                player.sendMessage(MessageFormatter.format(MessageFormatter.INFO,
                        String.format("Progress: %.1f%%", result.progress() * 100)));
            }
        }

        return true;
    }

    private boolean handleAbandon(Player player, Guild guild) {
        // Check permission
        if (!guildService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_PROJECTS)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to abandon projects"));
            return true;
        }

        boolean abandoned = projectService.abandonProject(guild.getId(), player.getUniqueId());

        if (abandoned) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Project abandoned. All progress has been reset."));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "No active project to abandon."));
        }

        return true;
    }

    private boolean handleBuffs(Player player, Guild guild) {
        Optional<ActiveBuff> buffOpt = projectService.getActiveBuff(guild.getId());
        BuffStatusGUI gui = new BuffStatusGUI(guild, player, buffOpt.orElse(null), registry);
        player.openInventory(gui.getInventory());
        return true;
    }

    private String formatMaterialName(org.bukkit.Material material) {
        String name = material.name().replace("_", " ").toLowerCase();
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private String formatDuration(long millis) {
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        if (days > 0) {
            return days + " days " + hours + " hours";
        } else {
            return hours + " hours";
        }
    }

    @Override
    public String getName() {
        return "project";
    }

    @Override
    public String getPermission() {
        return "guilds.project";
    }

    @Override
    public String getUsage() {
        return "/g project [list|start|progress|contribute|complete|abandon|buffs]";
    }
}
