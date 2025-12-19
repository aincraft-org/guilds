package org.aincraft.progression;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.Guild;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.progression.storage.GuildProgressionRepository;
import org.aincraft.progression.storage.ProgressionLogRepository;
import org.aincraft.skilltree.SkillTreeRegistry;
import org.aincraft.skilltree.SkillTreeService;
import org.aincraft.vault.Vault;
import org.aincraft.vault.VaultService;
import org.aincraft.vault.VaultTransaction;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.UUID;

/**
 * Service layer for guild progression operations.
 * Single Responsibility: Guild progression business logic.
 * Dependency Inversion: Depends on abstractions via constructor injection.
 */
@Singleton
public class ProgressionService {
    private final GuildProgressionRepository progressionRepository;
    private final ProgressionLogRepository logRepository;
    private final GuildLifecycleService lifecycleService;
    private final VaultService vaultService;
    private final ProgressionConfig config;
    private final ProceduralCostGenerator costGenerator;
    private final SkillTreeService skillTreeService;
    private final SkillTreeRegistry skillTreeRegistry;
    private final Map<String, LevelUpCost> costCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Inject
    public ProgressionService(GuildProgressionRepository progressionRepository,
                              ProgressionLogRepository logRepository,
                              GuildLifecycleService lifecycleService,
                              VaultService vaultService,
                              ProgressionConfig config,
                              ProceduralCostGenerator costGenerator,
                              SkillTreeService skillTreeService,
                              SkillTreeRegistry skillTreeRegistry) {
        this.progressionRepository = Objects.requireNonNull(progressionRepository, "Progression repository cannot be null");
        this.logRepository = Objects.requireNonNull(logRepository, "Log repository cannot be null");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "Lifecycle service cannot be null");
        this.vaultService = Objects.requireNonNull(vaultService, "Vault service cannot be null");
        this.config = Objects.requireNonNull(config, "Config cannot be null");
        this.costGenerator = Objects.requireNonNull(costGenerator, "Cost generator cannot be null");
        this.skillTreeService = Objects.requireNonNull(skillTreeService, "Skill tree service cannot be null");
        this.skillTreeRegistry = Objects.requireNonNull(skillTreeRegistry, "Skill tree registry cannot be null");
    }

    /**
     * Awards XP to a guild and records player contribution.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID earning the XP
     * @param source the source of the XP
     * @param baseAmount the base amount of XP before multipliers
     */
    public void awardXp(UUID guildId, UUID playerId, XpSource source, long baseAmount) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(source, "XP source cannot be null");

        if (baseAmount <= 0) {
            return;
        }

        // Get or create progression
        GuildProgression progression = getOrCreateProgression(guildId);

        // Check if at max level
        if (progression.getLevel() >= config.getMaxLevel()) {
            return; // No XP gain at max level
        }

        // Add XP
        progression.addXp(baseAmount);

        // Save progression
        progressionRepository.save(progression);

        // Record contribution
        progressionRepository.recordContribution(guildId, playerId, baseAmount);

        // Log XP gain
        logRepository.log(new ProgressionLog(
            guildId,
            playerId,
            ProgressionLog.ActionType.XP_GAIN,
            baseAmount,
            source.name()
        ));
    }

    /**
     * Calculates the XP required to reach a specific level.
     * Formula: base_xp * (growth_factor ^ (level - 1))
     *
     * @param level the target level
     * @return the total XP required to reach that level from previous level
     */
    public long calculateXpRequired(int level) {
        if (level <= 1) {
            return 0L;
        }

        double baseXp = config.getBaseXp();
        double growthFactor = config.getGrowthFactor();

        return (long) (baseXp * Math.pow(growthFactor, level - 2));
    }

    /**
     * Attempts to level up a guild.
     * Checks XP requirements, material costs, vault contents, and permissions.
     *
     * @param guildId the guild ID
     * @param requesterId the player requesting the level-up
     * @return result indicating success or specific failure reason
     */
    public LevelUpResult attemptLevelUp(UUID guildId, UUID requesterId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");

        // Get guild
        Guild guild = lifecycleService.getGuildById(guildId);
        if (guild == null) {
            return LevelUpResult.failure("Guild not found");
        }

        // Get progression
        GuildProgression progression = getOrCreateProgression(guildId);

        // Check max level
        if (progression.getLevel() >= config.getMaxLevel()) {
            return LevelUpResult.maxLevelReached();
        }

        // Check XP requirement
        long xpRequired = calculateXpRequired(progression.getLevel() + 1);
        if (!progression.canLevelUp(xpRequired)) {
            return LevelUpResult.insufficientXp(progression.getCurrentXp(), xpRequired);
        }

        // Calculate material costs
        LevelUpCost cost = calculateLevelUpCost(guildId, progression.getLevel());

        // Get vault
        Optional<Vault> vaultOpt = vaultService.getGuildVault(requesterId);
        if (vaultOpt.isEmpty()) {
            return LevelUpResult.noVault();
        }

        Vault vault = vaultOpt.get();

        // Check if vault has required materials
        Map<Material, Integer> vaultContents = countVaultMaterials(vault.getContents());

        for (Map.Entry<Material, Integer> entry : cost.getMaterials().entrySet()) {
            Material material = entry.getKey();
            int required = entry.getValue();
            int available = vaultContents.getOrDefault(material, 0);

            if (available < required) {
                return LevelUpResult.insufficientMaterials(
                    material.name(),
                    available,
                    required
                );
            }
        }

        // Deduct materials from vault
        ItemStack[] contents = vault.getContents();
        for (Map.Entry<Material, Integer> entry : cost.getMaterials().entrySet()) {
            deductMaterial(contents, entry.getKey(), entry.getValue());

            // Log transaction
            VaultTransaction tx = new VaultTransaction(
                vault.getId(),
                requesterId,
                VaultTransaction.TransactionType.WITHDRAW,
                entry.getKey(),
                entry.getValue()
            );
            vaultService.logTransaction(tx);
        }

        // Save vault contents
        vault.setContents(contents);
        vaultService.updateVaultContents(vault.getId(), contents);

        // Level up
        progression.levelUp(xpRequired);
        progressionRepository.save(progression);

        // Update guild capacities
        int newMaxMembers = calculateMaxMembers(progression.getLevel());
        int newMaxChunks = calculateMaxChunks(progression.getLevel());
        lifecycleService.updateGuildCapacities(guildId, newMaxMembers, newMaxChunks);

        // Log level up
        logRepository.log(new ProgressionLog(
            guildId,
            requesterId,
            ProgressionLog.ActionType.LEVEL_UP,
            progression.getLevel(),
            String.format("+%d members, +%d chunks",
                newMaxMembers - calculateMaxMembers(progression.getLevel() - 1),
                newMaxChunks - calculateMaxChunks(progression.getLevel() - 1))
        ));

        // Clear cost cache for this level (no longer needed)
        clearCostCache(guildId, progression.getLevel() - 1);

        return LevelUpResult.success(progression.getLevel());
    }

    /**
     * Calculates the material costs for leveling up from a specific level.
     * Uses procedural generation based on guild ID for unique, deterministic costs.
     * Results are cached for performance.
     *
     * @param guildId the guild ID
     * @param currentLevel the current level
     * @return the level-up cost
     */
    public LevelUpCost calculateLevelUpCost(UUID guildId, int currentLevel) {
        String cacheKey = guildId + ":" + currentLevel;
        return costCache.computeIfAbsent(cacheKey,
            k -> costGenerator.generateCost(guildId, currentLevel));
    }

    /**
     * Clears cached cost for a specific guild and level.
     * Called after successful level-up to free memory.
     *
     * @param guildId the guild ID
     * @param level the level
     */
    private void clearCostCache(UUID guildId, int level) {
        costCache.remove(guildId + ":" + level);
    }

    /**
     * Calculates the maximum members for a given level.
     * Formula: base + (members_per_level * (level - 1))
     *
     * @param level the guild level
     * @return the maximum members
     */
    public int calculateMaxMembers(int level) {
        return config.getBaseMaxMembers() + (config.getMembersPerLevel() * (level - 1));
    }

    /**
     * Calculates the maximum chunks for a given level.
     * Formula: base + (chunks_per_level * (level - 1))
     *
     * @param level the guild level
     * @return the maximum chunks
     */
    public int calculateMaxChunks(int level) {
        return config.getBaseMaxChunks() + (config.getChunksPerLevel() * (level - 1));
    }

    /**
     * Gets or creates a guild's progression state.
     *
     * @param guildId the guild ID
     * @return the progression
     */
    public GuildProgression getOrCreateProgression(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        return progressionRepository.findByGuildId(guildId)
                .orElseGet(() -> {
                    GuildProgression newProgression = new GuildProgression(guildId);
                    progressionRepository.save(newProgression);
                    return newProgression;
                });
    }

    /**
     * Gets the progression for a guild.
     *
     * @param guildId the guild ID
     * @return optional progression
     */
    public Optional<GuildProgression> getProgression(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return progressionRepository.findByGuildId(guildId);
    }

    /**
     * Gets the top XP contributors for a guild.
     *
     * @param guildId the guild ID
     * @param limit maximum number of contributors
     * @return map of player UUID to XP contributed
     */
    public Map<UUID, Long> getTopContributors(UUID guildId, int limit) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return progressionRepository.getTopContributors(guildId, limit);
    }

    /**
     * Gets a player's total XP contribution to their guild.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID
     * @return the total XP contributed
     */
    public long getPlayerContribution(UUID guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        return progressionRepository.getPlayerContribution(guildId, playerId);
    }

    /**
     * Deletes all progression data for a guild.
     *
     * @param guildId the guild ID
     */
    public void deleteProgression(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        progressionRepository.delete(guildId);
        progressionRepository.deleteContributions(guildId);
    }

    /**
     * Sets a guild's level directly (admin command).
     * Updates guild capacities accordingly.
     *
     * @param guildId the guild ID
     * @param level the new level
     * @param adminId the UUID of the admin performing this action
     */
    public void setLevel(UUID guildId, int level, UUID adminId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(adminId, "Admin ID cannot be null");

        if (level < 1) {
            throw new IllegalArgumentException("Level must be at least 1");
        }

        if (level > config.getMaxLevel()) {
            throw new IllegalArgumentException("Level cannot exceed max level: " + config.getMaxLevel());
        }

        GuildProgression progression = getOrCreateProgression(guildId);
        progression.setLevel(level);
        progression.setCurrentXp(0L); // Reset XP when setting level
        progressionRepository.save(progression);

        // Update guild capacities
        int newMaxMembers = calculateMaxMembers(level);
        int newMaxChunks = calculateMaxChunks(level);
        lifecycleService.updateGuildCapacities(guildId, newMaxMembers, newMaxChunks);

        // Log admin action
        logRepository.log(new ProgressionLog(
            guildId,
            adminId,
            ProgressionLog.ActionType.ADMIN_SET_LEVEL,
            level,
            "XP reset to 0"
        ));
    }

    /**
     * Adds XP directly to a guild (admin command).
     * Does not record player contribution.
     * Automatically levels up if XP exceeds requirements.
     *
     * @param guildId the guild ID
     * @param amount the amount of XP to add
     * @param adminId the UUID of the admin performing this action
     */
    public void addXp(UUID guildId, long amount, UUID adminId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(adminId, "Admin ID cannot be null");

        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        GuildProgression progression = getOrCreateProgression(guildId);
        progression.addXp(amount);

        // Process automatic level-ups
        processAutoLevelUps(guildId, progression);

        progressionRepository.save(progression);

        // Log admin action
        logRepository.log(new ProgressionLog(
            guildId,
            adminId,
            ProgressionLog.ActionType.ADMIN_ADD_XP,
            amount,
            null
        ));
    }

    /**
     * Sets a guild's XP directly (admin command).
     * Does not record player contribution.
     * Automatically levels up if XP exceeds requirements.
     *
     * @param guildId the guild ID
     * @param xp the new XP amount
     * @param adminId the UUID of the admin performing this action
     */
    public void setXp(UUID guildId, long xp, UUID adminId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(adminId, "Admin ID cannot be null");

        if (xp < 0) {
            throw new IllegalArgumentException("XP cannot be negative");
        }

        GuildProgression progression = getOrCreateProgression(guildId);
        progression.setCurrentXp(xp);

        // Process automatic level-ups
        processAutoLevelUps(guildId, progression);

        progressionRepository.save(progression);

        // Log admin action
        logRepository.log(new ProgressionLog(
            guildId,
            adminId,
            ProgressionLog.ActionType.ADMIN_SET_XP,
            xp,
            null
        ));
    }

    /**
     * Processes automatic level-ups when XP exceeds requirements.
     * Continues leveling up until XP is below the next level requirement or max level is reached.
     *
     * @param guildId the guild ID
     * @param progression the guild progression
     */
    private void processAutoLevelUps(UUID guildId, GuildProgression progression) {
        while (progression.getLevel() < config.getMaxLevel()) {
            long xpRequired = calculateXpRequired(progression.getLevel() + 1);

            if (progression.getCurrentXp() >= xpRequired) {
                // Level up
                progression.levelUp(xpRequired);

                // Update guild capacities
                int newMaxMembers = calculateMaxMembers(progression.getLevel());
                int newMaxChunks = calculateMaxChunks(progression.getLevel());
                lifecycleService.updateGuildCapacities(guildId, newMaxMembers, newMaxChunks);

                // Award skill points
                int spToAward = skillTreeRegistry.getSpPerLevel();
                skillTreeService.awardSkillPoints(guildId, spToAward);
            } else {
                break;
            }
        }
    }

    /**
     * Counts materials in vault contents.
     *
     * @param contents the vault contents
     * @return map of material to total amount
     */
    private Map<Material, Integer> countVaultMaterials(ItemStack[] contents) {
        Map<Material, Integer> counts = new HashMap<>();

        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                Material material = item.getType();
                counts.merge(material, item.getAmount(), Integer::sum);
            }
        }

        return counts;
    }

    /**
     * Deducts a specific amount of a material from vault contents.
     *
     * @param contents the vault contents (modified in place)
     * @param material the material to deduct
     * @param amount the amount to deduct
     */
    private void deductMaterial(ItemStack[] contents, Material material, int amount) {
        int remaining = amount;

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];

            if (item != null && item.getType() == material) {
                int taken = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - taken);
                remaining -= taken;

                if (item.getAmount() <= 0) {
                    contents[i] = null;
                }
            }
        }
    }
}
