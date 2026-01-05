package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
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
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
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
                Messages.send(player, MessageKey.ERROR_USAGE, getUsage());
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
            Messages.send(player, MessageKey.ERROR_USAGE, "/g project start <project_id>");
            return true;
        }

        String projectId = args[2];
        ProjectService.ProjectStartResult result = projectService.startProject(guild.getId(), player.getUniqueId(), projectId);

        if (result.success()) {
            Messages.send(player, MessageKey.PROJECT_STARTED, projectId);

            // Open details GUI
            Optional<ProjectDefinition> def = registry.getProject(projectId);
            if (def.isPresent()) {
                int guildLevel = 1; // TODO: get from ProgressionService
                ProjectDetailsGUI gui = new ProjectDetailsGUI(guild, player, projectService, registry, def.get(), result.project(), guildLevel);
                gui.open();
            }
        } else {
            Messages.send(player, MessageKey.ERROR_USAGE, result.errorMessage());
        }

        return true;
    }

    private boolean handleProgress(Player player, Guild guild) {
        Optional<GuildProject> projectOpt = projectService.getActiveProject(guild.getId());
        if (projectOpt.isEmpty()) {
            Messages.send(player, MessageKey.PROJECT_NO_ACTIVE);
            return true;
        }

        GuildProject project = projectOpt.get();
        Optional<ProjectDefinition> defOpt = registry.getProject(project.getProjectDefinitionId());
        if (defOpt.isEmpty()) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Project definition not found");
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
            Messages.send(player, MessageKey.PROJECT_COMPLETED, result.buff().categoryId(), formatDuration(result.buff().getRemainingMillis()));
        } else {
            Messages.send(player, MessageKey.ERROR_USAGE, result.errorMessage());
            if (result.progress() > 0 && result.progress() < 1.0) {
                Messages.send(player, MessageKey.PROJECT_PROGRESS, String.format("%.1f%%", result.progress() * 100));
            }
        }

        return true;
    }

    private boolean handleAbandon(Player player, Guild guild) {
        // Check permission
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_PROJECTS)) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        boolean abandoned = projectService.abandonProject(guild.getId(), player.getUniqueId());

        if (abandoned) {
            Messages.send(player, MessageKey.PROJECT_ABANDONED);
        } else {
            Messages.send(player, MessageKey.PROJECT_NO_ACTIVE);
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
        player.sendMessage(Messages.get(MessageKey.LIST_HEADER, "PROJECT DEBUG INFO"));

        // Get active project
        Optional<GuildProject> projectOpt = projectService.getActiveProject(guild.getId());
        if (projectOpt.isEmpty()) {
            Messages.send(player, MessageKey.PROJECT_NO_ACTIVE);
            return true;
        }

        GuildProject project = projectOpt.get();
        player.sendMessage(Messages.get(MessageKey.LIST_HEADER, "Active Project: " + project.getProjectDefinitionId()));

        // Get project definition
        Optional<ProjectDefinition> defOpt = registry.getProject(project.getProjectDefinitionId());
        if (defOpt.isEmpty()) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Project definition not found");
            return true;
        }

        ProjectDefinition definition = defOpt.get();

        // Get vault
        Optional<Vault> vaultOpt = vaultRepository.findByGuildId(guild.getId());
        if (vaultOpt.isEmpty()) {
            Messages.send(player, MessageKey.VAULT_NOT_FOUND);
            return true;
        }

        Vault vault = vaultOpt.get();
        ItemStack[] contents = vaultRepository.getFreshContents(vault.getId());

        player.sendMessage("");
        player.sendMessage(Messages.get(MessageKey.LIST_HEADER, "Material Requirements"));

        // Check each material
        for (Map.Entry<Material, Integer> entry : definition.materials().entrySet()) {
            Material material = entry.getKey();
            int required = entry.getValue();
            int inVault = countMaterialInVault(contents, material);
            boolean complete = inVault >= required;

            String status = complete ? "<green>COMPLETE</green>" : "<red>INCOMPLETE</red>";
            String materialName = material.name();

            player.sendMessage(Messages.get(MessageKey.PROJECT_PROGRESS,
                String.format("  %s %s: %d/%d %s",
                    status,
                    materialName,
                    inVault,
                    required,
                    complete ? "" : "<red>(need " + (required - inVault) + " more)</red>")
            ));
        }

        player.sendMessage("");
        player.sendMessage(Messages.get(MessageKey.LIST_HEADER, "Vault Contents (all items)"));

        // Show all non-null items in vault for debugging
        int itemCount = 0;
        for (ItemStack stack : contents) {
            if (stack != null && !stack.getType().isAir()) {
                itemCount++;
                player.sendMessage(Messages.get(MessageKey.PROJECT_PROGRESS,
                    String.format("  <gray>- %s x%d</gray>",
                        stack.getType().name(),
                        stack.getAmount())
                ));
            }
        }

        if (itemCount == 0) {
            Messages.send(player, MessageKey.VAULT_NOT_FOUND);
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
