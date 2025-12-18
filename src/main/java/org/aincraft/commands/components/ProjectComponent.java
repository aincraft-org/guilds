package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.project.*;
import org.aincraft.project.gui.BuffStatusGUI;
import org.aincraft.project.gui.ProjectDetailsGUI;
import org.aincraft.project.gui.ProjectListGUI;
import org.aincraft.vault.Vault;
import org.aincraft.vault.VaultRepository;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ProjectComponent implements GuildCommand {

    private final ProjectService projectService;
    private final ProjectRegistry registry;
    private final GuildMemberService memberService;
    private final PermissionService permissionService;
    private final VaultRepository vaultRepository;

    @Inject
    public ProjectComponent(ProjectService projectService, ProjectRegistry registry, GuildMemberService memberService,
                           PermissionService permissionService, VaultRepository vaultRepository) {
        this.projectService = Objects.requireNonNull(projectService);
        this.registry = Objects.requireNonNull(registry);
        this.memberService = Objects.requireNonNull(memberService);
        this.permissionService = Objects.requireNonNull(permissionService);
        this.vaultRepository = Objects.requireNonNull(vaultRepository);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
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
            case "complete" -> handleComplete(player, guild);
            case "abandon" -> handleAbandon(player, guild);
            case "buffs", "buff" -> handleBuffs(player, guild);
            case "debug" -> handleDebug(player, guild);
            default -> {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Unknown subcommand: " + subcommand));
                yield true;
            }
        };
    }

    private boolean openProjectList(Player player, Guild guild) {
        int guildLevel = 1; // TODO: get from ProgressionService
        ProjectListGUI gui = new ProjectListGUI(guild, player, projectService, registry, guildLevel);
        gui.open();
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
                int guildLevel = 1; // TODO: get from ProgressionService
                ProjectDetailsGUI gui = new ProjectDetailsGUI(guild, player, projectService, registry, def.get(), result.project(), guildLevel);
                gui.open();
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
        int guildLevel = 1; // TODO: get from ProgressionService
        ProjectDetailsGUI gui = new ProjectDetailsGUI(guild, player, projectService, registry, defOpt.get(), project, guildLevel);
        gui.open();
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
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_PROJECTS)) {
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
        int guildLevel = 1; // TODO: get from ProgressionService
        BuffStatusGUI gui = new BuffStatusGUI(guild, player, buffOpt.orElse(null), registry, projectService, guildLevel);
        gui.open();
        return true;
    }

    private boolean handleDebug(Player player, Guild guild) {
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "=== PROJECT DEBUG INFO ==="));

        // Get active project
        Optional<GuildProject> projectOpt = projectService.getActiveProject(guild.getId());
        if (projectOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "No active project"));
            return true;
        }

        GuildProject project = projectOpt.get();
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Active Project: " + project.getProjectDefinitionId()));

        // Get project definition
        Optional<ProjectDefinition> defOpt = registry.getProject(project.getProjectDefinitionId());
        if (defOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Project definition not found"));
            return true;
        }

        ProjectDefinition definition = defOpt.get();

        // Get vault
        Optional<Vault> vaultOpt = vaultRepository.findByGuildId(guild.getId());
        if (vaultOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "No guild vault found"));
            return true;
        }

        Vault vault = vaultOpt.get();
        ItemStack[] contents = vaultRepository.getFreshContents(vault.getId());

        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, ""));
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Material Requirements:"));

        // Check each material
        for (Map.Entry<Material, Integer> entry : definition.materials().entrySet()) {
            Material material = entry.getKey();
            int required = entry.getValue();
            int inVault = countMaterialInVault(contents, material);
            boolean complete = inVault >= required;

            String status = complete ? "<green>COMPLETE</green>" : "<red>INCOMPLETE</red>";
            String materialName = material.name();

            player.sendMessage(MessageFormatter.deserialize(
                String.format("  %s %s: %d/%d %s",
                    status,
                    materialName,
                    inVault,
                    required,
                    complete ? "" : "<red>(need " + (required - inVault) + " more)</red>")
            ));
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, ""));
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Vault Contents (all items):"));

        // Show all non-null items in vault for debugging
        int itemCount = 0;
        for (ItemStack stack : contents) {
            if (stack != null && !stack.getType().isAir()) {
                itemCount++;
                player.sendMessage(MessageFormatter.deserialize(
                    String.format("  <gray>- %s x%d</gray>",
                        stack.getType().name(),
                        stack.getAmount())
                ));
            }
        }

        if (itemCount == 0) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Vault is empty!"));
        }

        return true;
    }

    private int countMaterialInVault(ItemStack[] contents, Material material) {
        int count = 0;
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
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
        return "/g project [list|start|progress|complete|abandon|buffs|debug]";
    }
}
