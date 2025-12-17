package org.aincraft.role.gui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.GuildPermission;
import org.aincraft.GuildRole;
import org.aincraft.GuildService;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Objects;

/**
 * Listener for role creation wizard GUI events.
 */
@Singleton
public class RoleCreationGUIListener implements Listener {
    private final GuildService guildService;

    @Inject
    public RoleCreationGUIListener(GuildService guildService) {
        this.guildService = Objects.requireNonNull(guildService);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RoleCreationGUI gui)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();

        switch (gui.getCurrentStep()) {
            case PERMISSION_SELECTION -> handlePermissionClick(gui, slot, player);
            case PRIORITY_INPUT -> handlePriorityClick(gui, slot, player);
            case CONFIRMATION -> handleConfirmationClick(gui, slot, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof RoleCreationGUI)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RoleCreationGUI)) {
            return;
        }
        // Session cleanup - nothing needed, GC will handle it
    }

    private void handlePermissionClick(RoleCreationGUI gui, int slot, Player player) {
        // Cancel button
        if (slot == 45) {
            player.closeInventory();
            return;
        }

        // Next button
        if (slot == 53) {
            gui.setStep(WizardStep.PRIORITY_INPUT);
            return;
        }

        // Permission items
        GuildPermission perm = getPermissionAtSlot(gui, slot);
        if (perm != null) {
            gui.togglePermission(perm);
            gui.renderInventory();
        }
    }

    private void handlePriorityClick(RoleCreationGUI gui, int slot, Player player) {
        // Back button
        if (slot == 45) {
            gui.setStep(WizardStep.PERMISSION_SELECTION);
            return;
        }

        // Skip button
        if (slot == 49) {
            gui.setPriority(0);
            gui.setStep(WizardStep.CONFIRMATION);
            return;
        }

        // Next button
        if (slot == 53) {
            gui.setStep(WizardStep.CONFIRMATION);
            return;
        }

        // Decrease -10
        if (slot == 20) {
            gui.setPriority(gui.getPriority() - 10);
            gui.renderInventory();
            return;
        }

        // Decrease -1
        if (slot == 21) {
            gui.setPriority(gui.getPriority() - 1);
            gui.renderInventory();
            return;
        }

        // Increase +1
        if (slot == 23) {
            gui.setPriority(gui.getPriority() + 1);
            gui.renderInventory();
            return;
        }

        // Increase +10
        if (slot == 24) {
            gui.setPriority(gui.getPriority() + 10);
            gui.renderInventory();
        }
    }

    private void handleConfirmationClick(RoleCreationGUI gui, int slot, Player player) {
        // Back button
        if (slot == 45) {
            gui.setStep(WizardStep.PRIORITY_INPUT);
            return;
        }

        // Create button
        if (slot == 53) {
            GuildRole role = guildService.createRole(
                    gui.getGuild().getId(),
                    player.getUniqueId(),
                    gui.getRoleName(),
                    gui.getSelectedPermissions(),
                    gui.getPriority()
            );

            if (role != null) {
                player.closeInventory();
                String priorityText = gui.getPriority() > 0 ? " (priority: " + gui.getPriority() + ")" : "";
                player.sendMessage(MessageFormatter.deserialize(
                        "<green>Created role '<gold>" + gui.getRoleName() + "</gold>'" + priorityText + "</green>"));
            } else {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                        "Failed to create role. You may lack MANAGE_ROLES permission."));
            }
        }
    }

    private GuildPermission getPermissionAtSlot(RoleCreationGUI gui, int slot) {
        java.util.List<GuildPermission> perms = gui.getGrantablePermissions();
        int permIndex = 0;

        for (int row = 1; row < 6; row++) {
            for (int col = 0; col < 3; col++) {
                if (permIndex >= perms.size()) return null;
                int slotPos = row * 9 + col + 1;
                if (slot == slotPos) {
                    return perms.get(permIndex);
                }
                permIndex++;
            }
        }

        return null;
    }
}
