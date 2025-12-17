package org.aincraft.role.gui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Chest GUI for creating guild roles with permission selection, priority input, and confirmation.
 */
public class RoleCreationGUI implements InventoryHolder {
    private final Guild guild;
    private final Player creator;
    private final String roleName;
    private final GuildService guildService;
    private final List<GuildPermission> grantablePermissions;
    private int selectedPermissions;
    private int priority;
    private WizardStep currentStep;
    private final Inventory inventory;

    private static final int INVENTORY_SIZE = 54;

    public RoleCreationGUI(Guild guild, Player creator, String roleName, GuildService guildService) {
        this.guild = guild;
        this.creator = creator;
        this.roleName = roleName;
        this.guildService = guildService;
        this.selectedPermissions = 0;
        this.priority = 0;
        this.currentStep = WizardStep.PERMISSION_SELECTION;

        // Calculate grantable permissions once at creation
        this.grantablePermissions = new ArrayList<>();
        for (GuildPermission perm : GuildPermission.values()) {
            if (guildService.hasPermission(guild.getId(), creator.getUniqueId(), perm)) {
                this.grantablePermissions.add(perm);
            }
        }

        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE,
                Component.text("Create Role: " + roleName).color(NamedTextColor.BLACK));
        renderInventory();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Guild getGuild() {
        return guild;
    }

    public Player getCreator() {
        return creator;
    }

    public String getRoleName() {
        return roleName;
    }

    public int getSelectedPermissions() {
        return selectedPermissions;
    }

    public int getPriority() {
        return priority;
    }

    public WizardStep getCurrentStep() {
        return currentStep;
    }

    public void togglePermission(GuildPermission perm) {
        selectedPermissions ^= perm.getBit();
    }

    public void setPriority(int priority) {
        this.priority = Math.max(0, priority);
    }

    public void setStep(WizardStep step) {
        this.currentStep = step;
        renderInventory();
    }

    public List<GuildPermission> getGrantablePermissions() {
        return grantablePermissions;
    }

    public void renderInventory() {
        inventory.clear();

        switch (currentStep) {
            case PERMISSION_SELECTION -> renderPermissionStep();
            case PRIORITY_INPUT -> renderPriorityStep();
            case CONFIRMATION -> renderConfirmationStep();
        }
    }

    private void renderPermissionStep() {
        // Add center divider line
        for (int row = 1; row < 6; row++) {
            int slot = row * 9 + 4;
            inventory.setItem(slot, createGlassPane(Material.GRAY_STAINED_GLASS_PANE));
        }

        // Separate into disabled and enabled lists, maintaining order
        List<GuildPermission> disabledPerms = new ArrayList<>();
        List<GuildPermission> enabledPerms = new ArrayList<>();

        for (GuildPermission perm : grantablePermissions) {
            if ((selectedPermissions & perm.getBit()) != 0) {
                enabledPerms.add(perm);
            } else {
                disabledPerms.add(perm);
            }
        }

        // Place disabled on left (columns 0-3, filling top to bottom)
        int index = 0;
        for (GuildPermission perm : disabledPerms) {
            int row = 1 + index / 4;
            int col = index % 4;
            int slot = row * 9 + col;

            ItemStack permItem = createPermissionItem(perm, false);
            inventory.setItem(slot, permItem);
            index++;
        }

        // Place enabled on right (columns 5-8, filling top to bottom)
        index = 0;
        for (GuildPermission perm : enabledPerms) {
            int row = 1 + index / 4;
            int col = 5 + (index % 4);
            int slot = row * 9 + col;

            ItemStack permItem = createPermissionItem(perm, true);
            inventory.setItem(slot, permItem);
            index++;
        }

        // Add controls (row 6)
        inventory.setItem(45, createButton(Material.BARRIER, "Cancel"));
        inventory.setItem(53, createButton(Material.LIME_WOOL, "Next"));
    }

    private void renderPriorityStep() {
        // Priority display (center)
        ItemStack priorityDisplay = new ItemStack(Material.PAPER);
        ItemMeta meta = priorityDisplay.getItemMeta();
        meta.displayName(Component.text("Priority: " + priority).color(NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Higher = more authority").color(NamedTextColor.GRAY));
        meta.lore(lore);
        priorityDisplay.setItemMeta(meta);
        inventory.setItem(13, priorityDisplay);

        // Decrease buttons
        inventory.setItem(20, createButton(Material.RED_CONCRETE, "-10"));
        inventory.setItem(21, createButton(Material.RED_TERRACOTTA, "-1"));

        // Increase buttons
        inventory.setItem(23, createButton(Material.GREEN_TERRACOTTA, "+1"));
        inventory.setItem(24, createButton(Material.GREEN_CONCRETE, "+10"));

        // Navigation
        inventory.setItem(45, createButton(Material.YELLOW_WOOL, "Back"));
        inventory.setItem(49, createButton(Material.GRAY_WOOL, "Skip"));
        inventory.setItem(53, createButton(Material.LIME_WOOL, "Next"));
    }

    private void renderConfirmationStep() {
        // Role name
        ItemStack nameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = nameItem.getItemMeta();
        meta.displayName(Component.text("Role Name: " + roleName).color(NamedTextColor.GOLD));
        nameItem.setItemMeta(meta);
        inventory.setItem(11, nameItem);

        // Permissions summary
        ItemStack permItem = new ItemStack(Material.BOOK);
        meta = permItem.getItemMeta();
        meta.displayName(Component.text("Permissions").color(NamedTextColor.GOLD));
        List<Component> permLore = new ArrayList<>();
        int count = 0;
        for (GuildPermission perm : GuildPermission.values()) {
            if ((selectedPermissions & perm.getBit()) != 0) {
                permLore.add(Component.text("• " + perm.name()).color(NamedTextColor.GREEN));
                count++;
            }
        }
        if (count == 0) {
            permLore.add(Component.text("None").color(NamedTextColor.GRAY));
        }
        meta.lore(permLore);
        permItem.setItemMeta(meta);
        inventory.setItem(13, permItem);

        // Priority display
        ItemStack priorityItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        meta = priorityItem.getItemMeta();
        meta.displayName(Component.text("Priority: " + priority).color(NamedTextColor.GOLD));
        priorityItem.setItemMeta(meta);
        inventory.setItem(15, priorityItem);

        // Navigation
        inventory.setItem(45, createButton(Material.YELLOW_WOOL, "Back"));
        inventory.setItem(53, createButton(Material.EMERALD, "Create Role!"));
    }

    private ItemStack createPermissionItem(GuildPermission perm, boolean enabled) {
        Material material = getPermissionMaterial(perm);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String symbol = enabled ? "✓" : "✗";
        NamedTextColor color = enabled ? NamedTextColor.GREEN : NamedTextColor.RED;
        meta.displayName(Component.text(symbol + " " + perm.name()).color(color));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(enabled ? "Click to disable" : "Click to enable").color(NamedTextColor.GRAY));
        lore.add(Component.text(getPermissionDescription(perm)).color(NamedTextColor.DARK_GRAY));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private Material getPermissionMaterial(GuildPermission perm) {
        return switch (perm) {
            case BUILD -> Material.DIAMOND_PICKAXE;
            case DESTROY -> Material.TNT;
            case INTERACT -> Material.OAK_DOOR;
            case CLAIM -> Material.GOLD_BLOCK;
            case UNCLAIM -> Material.COBBLESTONE;
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
        };
    }

    private ItemStack createButton(Material material, String text) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(text).color(NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGlassPane(Material material) {
        return new ItemStack(material);
    }

    private String getPermissionDescription(GuildPermission perm) {
        return switch (perm) {
            case BUILD -> "Place blocks in territory";
            case DESTROY -> "Break blocks in territory";
            case INTERACT -> "Use doors, buttons, chests";
            case CLAIM -> "Claim chunks for guild";
            case UNCLAIM -> "Unclaim guild chunks";
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
        };
    }
}
