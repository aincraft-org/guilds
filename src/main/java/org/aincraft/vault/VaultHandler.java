package org.aincraft.vault;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.mintychochip.mint.Mint;
import org.aincraft.multiblock.MultiblockInstance;
import org.aincraft.multiblock.events.MultiblockBreakEvent;
import org.aincraft.multiblock.events.MultiblockFormEvent;
import org.aincraft.multiblock.patterns.GuildVaultPattern;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * Handles vault-specific multiblock events.
 */
@Singleton
public class VaultHandler implements Listener {
    private final VaultService vaultService;

    @Inject
    public VaultHandler(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVaultForm(MultiblockFormEvent event) {
        if (!GuildVaultPattern.PATTERN_ID.equals(event.getPatternId())) {
            return;
        }

        Player player = event.getPlayer();
        MultiblockInstance instance = event.getInstance();

        VaultService.VaultCreationResult result = vaultService.createVault(player, instance);

        if (result.success()) {
            Mint.sendMessage(player, "<success>Vault <accent>opened</accent></success>");
        } else {
            Mint.sendMessage(player, "<error>Guild not found: <accent>" + result.errorMessage() + "</accent></error>");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVaultBreak(MultiblockBreakEvent event) {
        if (!GuildVaultPattern.PATTERN_ID.equals(event.getPatternId())) {
            return;
        }

        Player player = event.getPlayer();
        MultiblockInstance instance = event.getInstance();
        Location origin = instance.origin();

        // Find the vault at this location
        vaultService.getVaultByLocation(
                origin.getWorld().getName(),
                origin.getBlockX(),
                origin.getBlockY(),
                origin.getBlockZ()
        ).ifPresent(vault -> {
            // Check if player can destroy the vault
            if (!vaultService.canDestroyVault(player.getUniqueId(), vault)) {
                Mint.sendMessage(player, "<error>Only vault owners can destroy this vault</error>");
                event.setCancelled(true);
                return;
            }

            // Drop vault contents at the location
            ItemStack[] contents = vault.getContents();
            if (contents != null) {
                for (ItemStack item : contents) {
                    if (item != null && !item.getType().isAir()) {
                        origin.getWorld().dropItemNaturally(origin, item);
                    }
                }
            }

            // Delete the vault
            vaultService.destroyVault(vault);
            Mint.sendMessage(player, "<warning>Vault items dropped</warning>");
        });
    }
}
