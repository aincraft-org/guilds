package org.aincraft.project.gui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.Guild;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.project.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class ProjectGUIListener implements Listener {

    private final ProjectService projectService;
    private final ProjectRegistry registry;

    @Inject
    public ProjectGUIListener(ProjectService projectService, ProjectRegistry registry) {
        this.projectService = projectService;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getInventory().getHolder() instanceof ProjectListGUI gui) {
            handleProjectListClick(event, player, gui);
        } else if (event.getInventory().getHolder() instanceof ProjectDetailsGUI gui) {
            handleProjectDetailsClick(event, player, gui);
        } else if (event.getInventory().getHolder() instanceof BuffStatusGUI gui) {
            handleBuffStatusClick(event, player, gui);
        }
    }

    private void handleProjectListClick(InventoryClickEvent event, Player player, ProjectListGUI gui) {
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();

        // Close button
        if (slot == 45) {
            player.closeInventory();
            return;
        }

        // View buff button
        if (slot == 49 && clicked.getType() == Material.GLOWSTONE) {
            Optional<ActiveBuff> buff = projectService.getActiveBuff(gui.getGuild().getId());
            BuffStatusGUI buffGUI = new BuffStatusGUI(gui.getGuild(), player, buff.orElse(null), registry);
            player.openInventory(buffGUI.getInventory());
            return;
        }

        // Project slots (19-25)
        if (slot >= 19 && slot <= 25) {
            List<ProjectDefinition> available = projectService.getAvailableProjects(gui.getGuild().getId());
            int projectIndex = slot - 19;

            if (projectIndex < available.size()) {
                ProjectDefinition definition = available.get(projectIndex);
                Optional<GuildProject> activeProject = projectService.getActiveProject(gui.getGuild().getId());

                // Check if this is the active project or a new one
                GuildProject project = null;
                if (activeProject.isPresent() &&
                        activeProject.get().getProjectDefinitionId().equals(definition.id())) {
                    project = activeProject.get();
                }

                // Open details GUI
                ProjectDetailsGUI detailsGUI = new ProjectDetailsGUI(
                        gui.getGuild(), player, projectService, definition, project);
                player.openInventory(detailsGUI.getInventory());
            }
        }
    }

    private void handleProjectDetailsClick(InventoryClickEvent event, Player player, ProjectDetailsGUI gui) {
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();
        Guild guild = gui.getGuild();

        // Back button
        if (slot == 45) {
            ProjectListGUI listGUI = new ProjectListGUI(guild, player, projectService, 1); // TODO: get actual level
            player.openInventory(listGUI.getInventory());
            return;
        }

        if (gui.isActive()) {
            // Contribute materials button
            if (slot == 47 && clicked.getType() == Material.CHEST) {
                ProjectService.MaterialContributionResult result =
                        projectService.contributeMaterials(guild.getId(), player.getUniqueId());

                if (result.success()) {
                    StringBuilder msg = new StringBuilder("Contributed: ");
                    for (Map.Entry<Material, Integer> entry : result.contributed().entrySet()) {
                        msg.append(entry.getValue()).append("x ").append(entry.getKey().name()).append(", ");
                    }
                    player.sendMessage(MessageFormatter.format(MessageFormatter.SUCCESS, msg.toString()));

                    // Refresh GUI
                    Optional<GuildProject> updatedProject = projectService.getActiveProject(guild.getId());
                    ProjectDetailsGUI newGUI = new ProjectDetailsGUI(
                            guild, player, projectService, gui.getDefinition(), updatedProject.orElse(null));
                    player.openInventory(newGUI.getInventory());
                } else {
                    player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, result.errorMessage()));
                }
                return;
            }

            // Complete project button
            if (slot == 49 && clicked.getType() == Material.EMERALD_BLOCK) {
                ProjectService.ProjectCompletionResult result =
                        projectService.completeProject(guild.getId(), player.getUniqueId());

                if (result.success()) {
                    player.sendMessage(MessageFormatter.format(MessageFormatter.SUCCESS,
                            "Project completed! Buff activated: " + result.buff().category().name()));
                    player.closeInventory();
                } else {
                    player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, result.errorMessage()));
                }
                return;
            }

            // Abandon project button
            if (slot == 51 && clicked.getType() == Material.RED_WOOL) {
                boolean abandoned = projectService.abandonProject(guild.getId(), player.getUniqueId());

                if (abandoned) {
                    player.sendMessage(MessageFormatter.format(MessageFormatter.INFO,
                            "Project abandoned. All progress has been reset."));
                    player.closeInventory();
                } else {
                    player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                            "Failed to abandon project."));
                }
                return;
            }
        } else {
            // Start project button
            if (slot == 49 && clicked.getType() == Material.LIME_WOOL) {
                ProjectService.ProjectStartResult result =
                        projectService.startProject(guild.getId(), player.getUniqueId(), gui.getDefinition().id());

                if (result.success()) {
                    player.sendMessage(MessageFormatter.format(MessageFormatter.SUCCESS,
                            "Started project: " + gui.getDefinition().name()));

                    // Open updated details GUI
                    ProjectDetailsGUI newGUI = new ProjectDetailsGUI(
                            guild, player, projectService, gui.getDefinition(), result.project());
                    player.openInventory(newGUI.getInventory());
                } else {
                    player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, result.errorMessage()));
                }
                return;
            }
        }
    }

    private void handleBuffStatusClick(InventoryClickEvent event, Player player, BuffStatusGUI gui) {
        event.setCancelled(true);

        int slot = event.getRawSlot();

        // Back button
        if (slot == 18) {
            ProjectListGUI listGUI = new ProjectListGUI(gui.getGuild(), player, projectService, 1); // TODO: get actual level
            player.openInventory(listGUI.getInventory());
        }
    }
}
