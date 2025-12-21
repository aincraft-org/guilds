package org.aincraft.role.gui;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildRole;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.service.GuildRoleService;
import org.aincraft.service.PermissionService;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Chest GUI for creating guild roles with permission selection, priority input, and confirmation.
 * Uses Triumph GUI library for pagination and item management.
 */
public class RoleCreationGUI {
    private final Guild guild;
    private final Player creator;
    private final String roleName;
    private final GuildRoleService roleService;
    private final PermissionService permissionService;
    private final List<GuildPermission> grantablePermissions;
    private final List<GuildPermission> disabledPermissions;
    private final List<GuildPermission> enabledPermissions;
    private int selectedPermissions;
    private int priority;
    private WizardStep currentStep;
    private int disabledScrollOffset;
    private int enabledScrollOffset;

    public RoleCreationGUI(Guild guild, Player creator, String roleName, GuildRoleService roleService, PermissionService permissionService) {
        this.guild = guild;
        this.creator = creator;
        this.roleName = roleName;
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.currentStep = WizardStep.PERMISSION_SELECTION;
        this.disabledScrollOffset = 0;
        this.enabledScrollOffset = 0;

        // Check if role already exists - if so, load its values
        GuildRole existingRole = roleService.getRoleByName(guild.getId(), roleName);
        if (existingRole != null) {
            this.selectedPermissions = existingRole.getPermissions();
            this.priority = existingRole.getPriority();
        } else {
            this.selectedPermissions = 0;
            this.priority = 0;
        }

        // Calculate grantable permissions once at creation
        this.grantablePermissions = new ArrayList<>();
        for (GuildPermission perm : GuildPermission.values()) {
            if (permissionService.hasPermission(guild.getId(), creator.getUniqueId(), perm)) {
                this.grantablePermissions.add(perm);
            }
        }

        // Initialize permission lists based on current permissions
        this.disabledPermissions = new ArrayList<>();
        this.enabledPermissions = new ArrayList<>();

        for (GuildPermission perm : grantablePermissions) {
            if ((selectedPermissions & perm.getBit()) != 0) {
                enabledPermissions.add(perm);
            } else {
                disabledPermissions.add(perm);
            }
        }
    }

    public void open() {
        switch (currentStep) {
            case PERMISSION_SELECTION -> openPermissionStep();
            case PRIORITY_INPUT -> openPriorityStep();
            case CONFIRMATION -> openConfirmationStep();
        }
    }

    private void openPermissionStep() {
        GuildRole existingRole = roleService.getRoleByName(guild.getId(), roleName);
        String titlePrefix = existingRole != null ? "Edit Role: " : "Create Role: ";

        Gui gui = Gui.gui()
                .title(Component.text(titlePrefix + roleName).color(NamedTextColor.BLACK))
                .rows(6)
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));

        // Divider (column 5, all rows)
        GuiItem divider = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .asGuiItem();
        for (int row = 1; row <= 6; row++) {
            gui.setItem(row, 5, divider);
        }

        // Disabled side header
        gui.setItem(1, 1, ItemBuilder.from(Material.RED_STAINED_GLASS_PANE)
                .name(Component.text("Disabled Permissions").color(NamedTextColor.RED))
                .asGuiItem());

        // Enabled side header
        gui.setItem(1, 9, ItemBuilder.from(Material.LIME_STAINED_GLASS_PANE)
                .name(Component.text("Enabled Permissions").color(NamedTextColor.GREEN))
                .asGuiItem());

        // Scroll down button for disabled side (row 1, column 4)
        if (disabledScrollOffset + 16 < disabledPermissions.size()) {
            gui.setItem(6, 4, ItemBuilder.from(Material.ARROW)
                    .name(Component.text("Scroll Down").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                    .asGuiItem(event -> {
                        disabledScrollOffset += 16;
                        creator.playSound(creator.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
                        open();
                    }));
        }

        // Add disabled permissions (left side, columns 1-4 for rows 2-5, 4x4 grid = 16 slots)
        int maxDisabledDisplay = 16;
        for (int i = 0; i < maxDisabledDisplay && (disabledScrollOffset + i) < disabledPermissions.size(); i++) {
            GuildPermission perm = disabledPermissions.get(disabledScrollOffset + i);
            int row = 2 + (i / 4);
            int col = 1 + (i % 4);
            gui.setItem(row, col, createPermissionItem(perm, false));
        }

        // Scroll up button for disabled side (row 6, column 4)
        if (disabledScrollOffset > 0) {
            gui.setItem(1, 4, ItemBuilder.from(Material.ARROW)
                    .name(Component.text("Scroll Up").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                    .asGuiItem(event -> {
                        disabledScrollOffset = Math.max(0, disabledScrollOffset - 16);
                        creator.playSound(creator.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                        open();
                    }));
        }

        // Scroll down button for enabled side (row 1, column 6)
        if (enabledScrollOffset + 16 < enabledPermissions.size()) {
            gui.setItem(6, 6, ItemBuilder.from(Material.ARROW)
                    .name(Component.text("Scroll Down").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                    .asGuiItem(event -> {
                        enabledScrollOffset += 16;
                        creator.playSound(creator.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
                        open();
                    }));
        }

        // Add enabled permissions (right side, columns 6-9 for rows 2-5, 4x4 grid = 16 slots)
        int maxEnabledDisplay = 16;
        for (int i = 0; i < maxEnabledDisplay && (enabledScrollOffset + i) < enabledPermissions.size(); i++) {
            GuildPermission perm = enabledPermissions.get(enabledScrollOffset + i);
            int row = 2 + (i / 4);
            int col = 6 + (i % 4);
            gui.setItem(row, col, createPermissionItem(perm, true));
        }

        // Scroll up button for enabled side (row 6, column 6)
        if (enabledScrollOffset > 0) {
            gui.setItem(1, 6, ItemBuilder.from(Material.ARROW)
                    .name(Component.text("Scroll Up").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                    .asGuiItem(event -> {
                        enabledScrollOffset = Math.max(0, enabledScrollOffset - 16);
                        creator.playSound(creator.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                        open();
                    }));
        }

        // Bulk action buttons (row 6, positions 3 and 7)
        gui.setItem(6, 3, ItemBuilder.from(Material.RED_WOOL)
                .name(Component.text("Remove All").color(NamedTextColor.RED))
                .lore(Component.text("Disable all permissions").color(NamedTextColor.GRAY))
                .asGuiItem(event -> {
                    enabledPermissions.forEach(perm -> selectedPermissions &= ~perm.getBit());
                    disabledPermissions.addAll(enabledPermissions);
                    enabledPermissions.clear();
                    creator.playSound(creator.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.5f);
                    open();
                }));

        gui.setItem(6, 7, ItemBuilder.from(Material.LIME_WOOL)
                .name(Component.text("Add All").color(NamedTextColor.GREEN))
                .lore(Component.text("Enable all permissions").color(NamedTextColor.GRAY))
                .asGuiItem(event -> {
                    disabledPermissions.forEach(perm -> selectedPermissions |= perm.getBit());
                    enabledPermissions.addAll(disabledPermissions);
                    disabledPermissions.clear();
                    creator.playSound(creator.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
                    open();
                }));

        // Navigation row (row 6)
        gui.setItem(6, 1, ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Cancel").color(NamedTextColor.RED))
                .asGuiItem(event -> {
                    creator.closeInventory();
                    creator.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Role creation cancelled"));
                }));

        gui.setItem(6, 9, ItemBuilder.from(Material.EMERALD)
                .name(Component.text("Next").color(NamedTextColor.GREEN))
                .asGuiItem(event -> {
                    currentStep = WizardStep.PRIORITY_INPUT;
                    open();
                }));

        gui.open(creator);
    }

    private GuiItem createPermissionItem(GuildPermission perm, boolean enabled) {
        Material material = getPermissionMaterial(perm);

        return ItemBuilder.from(material)
                .name(Component.text(toTitleCase(perm.name())).decoration(TextDecoration.ITALIC, false))
                .lore(
                        Component.text(getPermissionDescription(perm)).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )
                .asGuiItem(event -> {
                    if (enabled) {
                        moveToDisabled(perm);
                    } else {
                        moveToEnabled(perm);
                    }
                    creator.playSound(creator.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    open();  // Refresh the GUI
                });
    }

    private void moveToEnabled(GuildPermission perm) {
        if (disabledPermissions.remove(perm)) {
            enabledPermissions.add(perm);
            selectedPermissions |= perm.getBit();
        }
    }

    private void moveToDisabled(GuildPermission perm) {
        if (enabledPermissions.remove(perm)) {
            disabledPermissions.add(perm);
            selectedPermissions &= ~perm.getBit();
        }
    }

    private void openPriorityStep() {
        Gui gui = Gui.gui()
                .title(Component.text("Set Priority: " + roleName).color(NamedTextColor.BLACK))
                .rows(6)
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));

        // Priority display (center)
        gui.setItem(2, 5, ItemBuilder.from(Material.PAPER)
                .name(Component.text("Priority: " + priority).color(NamedTextColor.GOLD))
                .lore(Component.text("Higher = more authority").color(NamedTextColor.GRAY))
                .asGuiItem());

        // Decrease buttons
        gui.setItem(3, 3, ItemBuilder.from(Material.RED_CONCRETE)
                .name(Component.text("-10").color(NamedTextColor.RED))
                .asGuiItem(event -> {
                    priority = Math.max(0, priority - 10);
                    open();
                }));

        gui.setItem(3, 4, ItemBuilder.from(Material.RED_TERRACOTTA)
                .name(Component.text("-1").color(NamedTextColor.RED))
                .asGuiItem(event -> {
                    priority = Math.max(0, priority - 1);
                    open();
                }));

        // Increase buttons
        gui.setItem(3, 6, ItemBuilder.from(Material.GREEN_TERRACOTTA)
                .name(Component.text("+1").color(NamedTextColor.GREEN))
                .asGuiItem(event -> {
                    priority++;
                    open();
                }));

        gui.setItem(3, 7, ItemBuilder.from(Material.GREEN_CONCRETE)
                .name(Component.text("+10").color(NamedTextColor.GREEN))
                .asGuiItem(event -> {
                    priority += 10;
                    open();
                }));

        // Navigation
        gui.setItem(6, 1, ItemBuilder.from(Material.YELLOW_WOOL)
                .name(Component.text("Back").color(NamedTextColor.YELLOW))
                .asGuiItem(event -> {
                    currentStep = WizardStep.PERMISSION_SELECTION;
                    open();
                }));

        gui.setItem(6, 5, ItemBuilder.from(Material.GRAY_WOOL)
                .name(Component.text("Skip").color(NamedTextColor.GRAY))
                .asGuiItem(event -> {
                    priority = 0;
                    currentStep = WizardStep.CONFIRMATION;
                    open();
                }));

        gui.setItem(6, 9, ItemBuilder.from(Material.LIME_WOOL)
                .name(Component.text("Next").color(NamedTextColor.GREEN))
                .asGuiItem(event -> {
                    currentStep = WizardStep.CONFIRMATION;
                    open();
                }));

        gui.open(creator);
    }

    private void openConfirmationStep() {
        Gui gui = Gui.gui()
                .title(Component.text("Confirm Role: " + roleName).color(NamedTextColor.BLACK))
                .rows(6)
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));

        // Role name
        gui.setItem(2, 3, ItemBuilder.from(Material.NAME_TAG)
                .name(Component.text("Role Name: " + roleName).color(NamedTextColor.GOLD))
                .asGuiItem());

        // Permissions summary
        List<Component> permLore = new ArrayList<>();
        if (enabledPermissions.isEmpty()) {
            permLore.add(Component.text("None").color(NamedTextColor.GRAY));
        } else {
            for (GuildPermission perm : enabledPermissions) {
                permLore.add(Component.text("• " + perm.name()).color(NamedTextColor.GREEN));
            }
        }

        gui.setItem(2, 5, ItemBuilder.from(Material.BOOK)
                .name(Component.text("Permissions").color(NamedTextColor.GOLD))
                .lore(permLore)
                .asGuiItem());

        // Priority display
        gui.setItem(2, 7, ItemBuilder.from(Material.EXPERIENCE_BOTTLE)
                .name(Component.text("Priority: " + priority).color(NamedTextColor.GOLD))
                .asGuiItem());

        // Navigation
        gui.setItem(6, 1, ItemBuilder.from(Material.YELLOW_WOOL)
                .name(Component.text("Back").color(NamedTextColor.YELLOW))
                .asGuiItem(event -> {
                    currentStep = WizardStep.PRIORITY_INPUT;
                    open();
                }));

        // Check if editing existing role
        GuildRole existingRole = roleService.getRoleByName(guild.getId(), roleName);
        boolean isEdit = existingRole != null;

        gui.setItem(6, 9, ItemBuilder.from(Material.EMERALD)
                .name(Component.text(isEdit ? "Save Changes!" : "Create Role!").color(NamedTextColor.GREEN))
                .asGuiItem(event -> {
                    GuildRole role;
                    boolean hasManageRoles = permissionService.hasPermission(guild.getId(), creator.getUniqueId(), GuildPermission.MANAGE_ROLES);
                    if (isEdit) {
                        // Update existing role - update permissions and priority
                        if (roleService.updateRolePermissions(guild.getId(), existingRole.getId(), selectedPermissions, hasManageRoles)) {
                            // Reload the role to get updated permissions
                            GuildRole updated = roleService.getRoleByIdAndGuild(existingRole.getId(), guild.getId()).orElse(null);
                            if (updated != null) {
                                updated.setPriority(priority);
                                roleService.saveRole(updated);
                                role = updated;
                            } else {
                                role = null;
                            }
                        } else {
                            role = null;
                        }
                    } else {
                        // Create new role
                        role = roleService.createRole(
                                guild.getId(),
                                roleName,
                                selectedPermissions,
                                priority,
                                hasManageRoles
                        );
                    }

                    if (role != null) {
                        creator.closeInventory();
                        String priorityText = priority > 0 ? " (priority: " + priority + ")" : "";
                        String action = isEdit ? "Updated" : "Created";
                        creator.sendMessage(MessageFormatter.deserialize(
                                "<green>" + action + " role '<gold>" + roleName + "</gold>'" + priorityText + "</green>"));
                    } else {
                        creator.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                                "Failed to " + (isEdit ? "update" : "create") + " role. You may lack MANAGE_ROLES permission."));
                    }
                }));

        gui.open(creator);
    }

    private Material getPermissionMaterial(GuildPermission perm) {
        return switch (perm) {
            case BUILD -> Material.DIAMOND_PICKAXE;
            case DESTROY -> Material.TNT;
            case INTERACT -> Material.OAK_DOOR;
            case CLAIM -> Material.GOLD_BLOCK;
            case UNCLAIM -> Material.COBBLESTONE;
            case UNCLAIM_ALL -> Material.NETHERITE_BLOCK;
            case INVITE -> Material.NAME_TAG;
            case KICK -> Material.REDSTONE;
            case MANAGE_ROLES -> Material.WRITABLE_BOOK;
            case MANAGE_REGIONS -> Material.LIME_CONCRETE;
            case VAULT_DEPOSIT -> Material.CHEST;
            case VAULT_WITHDRAW -> Material.BARREL;
            case MANAGE_SPAWN -> Material.BEACON;
            case CREATE_ROLE -> Material.EMERALD;
            case DELETE_ROLE -> Material.DRAGON_EGG;
            case EDIT_ROLE -> Material.FEATHER;
            case ADMIN -> Material.NETHER_STAR;
            case VIEW_LOGS -> Material.PAPER;
            case EDIT_GUILD_INFO -> Material.OAK_SIGN;
            case CHAT_GUILD -> Material.BOOK;
            case LEVEL_UP -> Material.EXPERIENCE_BOTTLE;
            case MANAGE_PROJECTS -> Material.FILLED_MAP;
            case MANAGE_SKILLS -> Material.ENCHANTED_BOOK;
            case CHAT_OFFICER -> Material.BELL;
        };
    }

    private String getPermissionDescription(GuildPermission perm) {
        return switch (perm) {
            case BUILD -> "Place blocks in territory";
            case DESTROY -> "Break blocks in territory";
            case INTERACT -> "Use doors, buttons, chests";
            case CLAIM -> "Claim chunks for guild";
            case UNCLAIM -> "Unclaim guild chunks";
            case UNCLAIM_ALL -> "Unclaim all guild chunks";
            case INVITE -> "Invite players to guild";
            case KICK -> "Remove members from guild";
            case MANAGE_ROLES -> "Edit existing roles";
            case MANAGE_REGIONS -> "Create/manage subregions";
            case VAULT_DEPOSIT -> "Put items in vault";
            case VAULT_WITHDRAW -> "Take items from vault";
            case MANAGE_SPAWN -> "Set guild spawn point";
            case CREATE_ROLE -> "Create new roles";
            case DELETE_ROLE -> "Delete roles";
            case EDIT_ROLE -> "Edit role permissions";
            case ADMIN -> "Full admin rights";
            case VIEW_LOGS -> "View guild logs";
            case EDIT_GUILD_INFO -> "Change guild name/description";
            case CHAT_GUILD -> "Use guild and ally chat";
            case LEVEL_UP -> "Level up the guild";
            case MANAGE_PROJECTS -> "Create/manage guild projects";
            case MANAGE_SKILLS -> "Unlock and manage skill tree";
            case CHAT_OFFICER -> "Use officer-only chat";
        };
    }

    private String toTitleCase(String text) {
        String[] words = text.split("_");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            result.append(words[i].charAt(0))
                  .append(words[i].substring(1).toLowerCase());
        }
        return result.toString();
    }

    // Getters for external access if needed
    public Guild getGuild() { return guild; }
    public Player getCreator() { return creator; }
    public String getRoleName() { return roleName; }
    public int getSelectedPermissions() { return selectedPermissions; }
    public int getPriority() { return priority; }
    public WizardStep getCurrentStep() { return currentStep; }
}
