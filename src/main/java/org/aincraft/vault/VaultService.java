package org.aincraft.vault;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.multiblock.MultiblockInstance;
import org.aincraft.multiblock.patterns.GuildVaultPattern;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.aincraft.subregion.Subregion;
import org.aincraft.subregion.SubregionService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Service for vault business logic.
 */
@Singleton
public class VaultService {
    private static final String BANK_TYPE = "bank";

    private final VaultRepository vaultRepository;
    private final VaultTransactionRepository transactionRepository;
    private final GuildLifecycleService lifecycleService;
    private final GuildMemberService memberService;
    private final PermissionService permissionService;
    private final SubregionService subregionService;

    @Inject
    public VaultService(VaultRepository vaultRepository,
                        VaultTransactionRepository transactionRepository,
                        GuildLifecycleService lifecycleService,
                        GuildMemberService memberService,
                        PermissionService permissionService,
                        SubregionService subregionService) {
        this.vaultRepository = Objects.requireNonNull(vaultRepository);
        this.transactionRepository = Objects.requireNonNull(transactionRepository);
        this.lifecycleService = Objects.requireNonNull(lifecycleService);
        this.memberService = Objects.requireNonNull(memberService);
        this.permissionService = Objects.requireNonNull(permissionService);
        this.subregionService = Objects.requireNonNull(subregionService);
    }

    /**
     * Creates a vault from a detected multiblock.
     *
     * @param player the player creating the vault
     * @param instance the detected multiblock instance
     * @return result of creation attempt
     */
    public VaultCreationResult createVault(Player player, MultiblockInstance instance) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(instance, "Instance cannot be null");

        // Verify pattern type
        if (!GuildVaultPattern.PATTERN_ID.equals(instance.patternId())) {
            return VaultCreationResult.failure("Invalid multiblock pattern");
        }

        UUID playerId = player.getUniqueId();
        Location origin = instance.origin();

        // Check player is in a guild
        Guild guild = memberService.getPlayerGuild(playerId);
        if (guild == null) {
            return VaultCreationResult.failure("You must be in a guild to create a vault");
        }

        // Check guild doesn't already have a vault
        if (vaultRepository.existsForGuild(guild.getId())) {
            return VaultCreationResult.failure("Your guild already has a vault");
        }

        // Check vault is in a bank subregion
        Optional<Subregion> subregionOpt = subregionService.getSubregionAt(origin);
        if (subregionOpt.isEmpty() || !BANK_TYPE.equals(subregionOpt.get().getType())) {
            return VaultCreationResult.failure("Vaults must be built inside a bank subregion");
        }

        Subregion bankRegion = subregionOpt.get();

        // Verify the bank belongs to the player's guild
        if (!bankRegion.getGuildId().equals(guild.getId())) {
            return VaultCreationResult.failure("This bank does not belong to your guild");
        }

        // Create and save the vault
        Vault vault = new Vault(
                guild.getId(),
                origin.getWorld().getName(),
                origin.getBlockX(),
                origin.getBlockY(),
                origin.getBlockZ(),
                instance.rotation(),
                playerId
        );
        vaultRepository.save(vault);

        return VaultCreationResult.success(vault);
    }

    /**
     * Opens the vault GUI for a player.
     *
     * @param player the player accessing the vault
     * @return result with access permissions
     */
    public VaultAccessResult openVault(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");

        UUID playerId = player.getUniqueId();
        Location loc = player.getLocation();

        // Check player is in a guild
        Guild guild = memberService.getPlayerGuild(playerId);
        if (guild == null) {
            return VaultAccessResult.failure("You are not in a guild");
        }

        // Check guild has a vault
        Optional<Vault> vaultOpt = vaultRepository.findByGuildId(guild.getId());
        if (vaultOpt.isEmpty()) {
            return VaultAccessResult.failure("Your guild does not have a vault");
        }

        Vault vault = vaultOpt.get();

        // Check player is in a bank subregion
        Optional<Subregion> subregionOpt = subregionService.getSubregionAt(loc);
        if (subregionOpt.isEmpty() || !BANK_TYPE.equals(subregionOpt.get().getType())) {
            return VaultAccessResult.failure("You must be in a bank subregion to access the vault");
        }

        // Check bank belongs to player's guild
        if (!subregionOpt.get().getGuildId().equals(guild.getId())) {
            return VaultAccessResult.failure("This is not your guild's bank");
        }

        // Check permissions
        boolean canDeposit = permissionService.hasPermission(guild.getId(), playerId, GuildPermission.VAULT_DEPOSIT);
        boolean canWithdraw = permissionService.hasPermission(guild.getId(), playerId, GuildPermission.VAULT_WITHDRAW);

        // Guild owner always has access
        if (guild.isOwner(playerId)) {
            canDeposit = true;
            canWithdraw = true;
        }

        if (!canDeposit && !canWithdraw) {
            return VaultAccessResult.failure("You don't have permission to access the vault");
        }

        return VaultAccessResult.success(vault, canDeposit, canWithdraw);
    }

    /**
     * Opens vault via chest click at a specific location.
     *
     * @param player the player
     * @param chestLocation the clicked chest location
     * @return result with access permissions
     */
    public VaultAccessResult openVaultByChest(Player player, Location chestLocation) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(chestLocation, "Location cannot be null");

        UUID playerId = player.getUniqueId();

        // Check if this location is a vault origin
        Optional<Vault> vaultOpt = vaultRepository.findByLocation(
                chestLocation.getWorld().getName(),
                chestLocation.getBlockX(),
                chestLocation.getBlockY(),
                chestLocation.getBlockZ()
        );

        if (vaultOpt.isEmpty()) {
            return VaultAccessResult.notVault();
        }

        Vault vault = vaultOpt.get();

        // Check player is in the same guild
        Guild guild = memberService.getPlayerGuild(playerId);
        if (guild == null || !guild.getId().equals(vault.getGuildId())) {
            return VaultAccessResult.failure("This vault belongs to another guild");
        }

        // Check permissions
        boolean canDeposit = permissionService.hasPermission(guild.getId(), playerId, GuildPermission.VAULT_DEPOSIT);
        boolean canWithdraw = permissionService.hasPermission(guild.getId(), playerId, GuildPermission.VAULT_WITHDRAW);

        // Guild owner always has access
        if (guild.isOwner(playerId)) {
            canDeposit = true;
            canWithdraw = true;
        }

        if (!canDeposit && !canWithdraw) {
            return VaultAccessResult.failure("You don't have permission to access the vault");
        }

        return VaultAccessResult.success(vault, canDeposit, canWithdraw);
    }

    /**
     * Gets vault info for a player's guild.
     */
    public Optional<Vault> getGuildVault(UUID playerId) {
        Guild guild = memberService.getPlayerGuild(playerId);
        if (guild == null) {
            return Optional.empty();
        }
        return vaultRepository.findByGuildId(guild.getId());
    }

    /**
     * Gets vault by ID.
     */
    public Optional<Vault> getVaultById(String vaultId) {
        return vaultRepository.findById(vaultId);
    }

    /**
     * Updates vault contents.
     */
    public void updateVaultContents(String vaultId, ItemStack[] contents) {
        vaultRepository.updateContents(vaultId, contents);
    }

    /**
     * Logs a transaction.
     */
    public void logTransaction(VaultTransaction transaction) {
        transactionRepository.log(transaction);
    }

    /**
     * Gets recent transactions for a vault.
     */
    public List<VaultTransaction> getRecentTransactions(String vaultId, int limit) {
        return transactionRepository.findByVaultId(vaultId, limit);
    }

    /**
     * Destroys a vault (called when structure is broken with permission).
     * Deletes vault and transaction history from database.
     *
     * @param vault the vault to destroy
     */
    public void destroyVault(Vault vault) {
        transactionRepository.deleteByVaultId(vault.getId());
        vaultRepository.delete(vault.getId());
    }

    /**
     * Checks if a player can destroy the vault (owner only).
     */
    public boolean canDestroyVault(UUID playerId, Vault vault) {
        Guild guild = lifecycleService.getGuildById(vault.getGuildId());
        return guild != null && guild.isOwner(playerId);
    }

    /**
     * Gets a vault by location (for checking if a block is part of a vault).
     */
    public Optional<Vault> getVaultByLocation(String world, int x, int y, int z) {
        return vaultRepository.findByLocation(world, x, y, z);
    }

    /**
     * Result of vault creation attempt.
     */
    public record VaultCreationResult(boolean success, Vault vault, String errorMessage) {
        public static VaultCreationResult success(Vault vault) {
            return new VaultCreationResult(true, vault, null);
        }

        public static VaultCreationResult failure(String errorMessage) {
            return new VaultCreationResult(false, null, errorMessage);
        }
    }

    /**
     * Result of vault access attempt.
     */
    public record VaultAccessResult(boolean success, boolean isVault, Vault vault, boolean canDeposit,
                                    boolean canWithdraw, String errorMessage) {
        public static VaultAccessResult success(Vault vault, boolean canDeposit, boolean canWithdraw) {
            return new VaultAccessResult(true, true, vault, canDeposit, canWithdraw, null);
        }

        public static VaultAccessResult failure(String errorMessage) {
            return new VaultAccessResult(false, true, null, false, false, errorMessage);
        }

        public static VaultAccessResult notVault() {
            return new VaultAccessResult(false, false, null, false, false, null);
        }
    }
}
