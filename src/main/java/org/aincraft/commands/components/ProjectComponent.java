package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.aincraft.commands.GuildCommand;
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
            Mint.sendMessage(sender, "<error>This command is for players only</error>");
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
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
                Mint.sendMessage(player, "<error>Usage: " + getUsage() + "</error>");
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
            Mint.sendMessage(player, "<error>Usage: /g project start <project_id></error>");
            return true;
        }

        String projectId = args[2];
        ProjectService.ProjectStartResult result = projectService.startProject(guild.getId(), player.getUniqueId(), projectId);

        if (result.success()) {
            Mint.sendMessage(player, "<success>Project started: <primary>" + projectId + "</primary></success>");

            // Open details GUI
            Optional<ProjectDefinition> def = registry.getProject(projectId);
            if (def.isPresent()) {
                int guildLevel = 1; // TODO: get from ProgressionService
                ProjectDetailsGUI gui = new ProjectDetailsGUI(guild, player, projectService, registry, def.get(), result.project(), guildLevel);
                gui.open();
            }
        } else {
            Mint.sendMessage(player, "<error>" + result.errorMessage() + "</error>");
        }

        return true;
    }

    private boolean handleProgress(Player player, Guild guild) {
        Optional<GuildProject> projectOpt = projectService.getActiveProject(guild.getId());
        if (projectOpt.isEmpty()) {
            Mint.sendMessage(player, "<warning>No active project</warning>");
            return true;
        }

        GuildProject project = projectOpt.get();
        Optional<ProjectDefinition> defOpt = registry.getProject(project.getProjectDefinitionId());
        if (defOpt.isEmpty()) {
            Mint.sendMessage(player, "<error>Project definition not found</error>");
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
            Mint.sendMessage(player, "<success>Project completed! Buff <primary>" + result.buff().categoryId() + "</primary> active for " + formatDuration(result.buff().getRemainingMillis()) + "</success>");
        } else {
            Mint.sendMessage(player, "<error>" + result.errorMessage() + "</error>");
            if (result.progress() > 0 && result.progress() < 1.0) {
                Mint.sendMessage(player, "<info>Progress: <primary>" + String.format("%.1f%%", result.progress() * 100) + "</primary></info>");
            }
        }

        return true;
    }

    private boolean handleAbandon(Player player, Guild guild) {
        // Check permission
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_PROJECTS)) {
            Mint.sendMessage(player, "<error>No permission</error>");
            return true;
        }

        boolean abandoned = projectService.abandonProject(guild.getId(), player.getUniqueId());

        if (abandoned) {
            Mint.sendMessage(player, "<success>Project abandoned</success>");
        } else {
            Mint.sendMessage(player, "<warning>No active project</warning>");
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
        Mint.sendMessage(player, "<primary>=== PROJECT DEBUG INFO ===</primary>");

        // Get active project
        Optional<GuildProject> projectOpt = projectService.getActiveProject(guild.getId());
        if (projectOpt.isEmpty()) {
            Mint.sendMessage(player, "<warning>No active project</warning>");
            return true;
        }

        GuildProject project = projectOpt.get();
        Mint.sendMessage(player, "<primary>=== Active Project: " + project.getProjectDefinitionId() + " ===</primary>");

        // Get project definition
        Optional<ProjectDefinition> defOpt = registry.getProject(project.getProjectDefinitionId());
        if (defOpt.isEmpty()) {
            Mint.sendMessage(player, "<error>Project definition not found</error>");
            return true;
        }

        ProjectDefinition definition = defOpt.get();

        // Get vault
        Optional<Vault> vaultOpt = vaultRepository.findByGuildId(guild.getId());
        if (vaultOpt.isEmpty()) {
            Mint.sendMessage(player, "<error>Vault not found</error>");
            return true;
        }

        Vault vault = vaultOpt.get();
        ItemStack[] contents = vaultRepository.getFreshContents(vault.getId());

        Mint.sendMessage(player, "");
        Mint.sendMessage(player, "<primary>=== Material Requirements ===</primary>");

        // Check each material
        for (Map.Entry<Material, Integer> entry : definition.materials().entrySet()) {
            Material material = entry.getKey();
            int required = entry.getValue();
            int inVault = countMaterialInVault(contents, material);
            boolean complete = inVault >= required;

            String status = complete ? "<success>COMPLETE</success>" : "<error>INCOMPLETE</error>";
            String materialName = material.name();

            Mint.sendMessage(player,
                String.format("  %s <secondary>%s</secondary>: %d/%d %s",
                    status,
                    materialName,
                    inVault,
                    required,
                    complete ? "" : "<error>(need " + (required - inVault) + " more)</error>")
            );
        }

        Mint.sendMessage(player, "");
        Mint.sendMessage(player, "<primary>=== Vault Contents (all items) ===</primary>");

        // Show all non-null items in vault for debugging
        int itemCount = 0;
        for (ItemStack stack : contents) {
            if (stack != null && !stack.getType().isAir()) {
                itemCount++;
                Mint.sendMessage(player,
                    String.format("  <neutral>- %s x%d</neutral>",
                        stack.getType().name(),
                        stack.getAmount())
                );
            }
        }

        if (itemCount == 0) {
            Mint.sendMessage(player, "<error>Vault not found</error>");
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
