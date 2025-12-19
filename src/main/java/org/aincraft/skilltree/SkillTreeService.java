package org.aincraft.skilltree;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.skilltree.storage.GuildSkillTreeRepository;
import org.aincraft.vault.Vault;
import org.aincraft.vault.VaultService;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for skill tree operations.
 * Orchestrates skill unlocks, respecs, and skill point awards.
 * Single Responsibility: Skill tree business logic.
 * Dependency Inversion: Depends on abstractions via constructor injection.
 *
 * Note: Buff effects are calculated dynamically by BuffApplicationService based on
 * unlocked skills, not directly managed by this service. This keeps skill unlocking
 * separate from buff application concerns.
 */
@Singleton
public class SkillTreeService {
    private final GuildSkillTreeRepository repository;
    private final SkillTreeRegistry registry;
    private final VaultService vaultService;

    @Inject
    public SkillTreeService(GuildSkillTreeRepository repository,
                            SkillTreeRegistry registry,
                            VaultService vaultService) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
        this.registry = Objects.requireNonNull(registry, "Registry cannot be null");
        this.vaultService = Objects.requireNonNull(vaultService, "Vault service cannot be null");
    }

    /**
     * Attempts to unlock a skill for a guild.
     * Validates prerequisites, checks SP, and saves state.
     * Buff effects are calculated dynamically by BuffApplicationService.
     *
     * @param guildId the guild ID
     * @param skillId the skill ID to unlock
     * @return result indicating success or specific failure reason
     */
    public SkillUnlockResult unlockSkill(UUID guildId, String skillId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(skillId, "Skill ID cannot be null");

        // Check if skill exists
        Optional<SkillDefinition> skillOpt = registry.getSkill(skillId);
        if (skillOpt.isEmpty()) {
            return SkillUnlockResult.skillNotFound(skillId);
        }
        SkillDefinition skill = skillOpt.get();

        // Get or create skill tree
        GuildSkillTree tree = getOrCreateSkillTree(guildId);

        // Check if already unlocked
        if (tree.isUnlocked(skillId)) {
            return SkillUnlockResult.alreadyUnlocked(skillId);
        }

        // Check prerequisites
        if (skill.hasPrerequisites()) {
            for (String prereqId : skill.prerequisites()) {
                if (!tree.isUnlocked(prereqId)) {
                    return SkillUnlockResult.missingPrerequisite(skillId, prereqId);
                }
            }
        }

        // Check SP cost
        if (tree.getAvailableSp() < skill.spCost()) {
            return SkillUnlockResult.insufficientSp(tree.getAvailableSp(), skill.spCost());
        }

        // Unlock the skill
        tree.unlockSkill(skill);

        // Save state (buff effects are calculated dynamically by BuffApplicationService)
        repository.save(tree);
        repository.unlockSkill(guildId, skillId, System.currentTimeMillis());

        return SkillUnlockResult.success(skill);
    }

    /**
     * Attempts to respec a guild's skill tree.
     * Validates respec is enabled, consumes materials from vault, and resets tree.
     *
     * @param guildId the guild ID
     * @param requesterId the player requesting the respec
     * @param vault the guild vault to consume materials from
     * @return result indicating success or specific failure reason
     */
    public RespecResult respec(UUID guildId, UUID requesterId, Vault vault) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");
        Objects.requireNonNull(vault, "Vault cannot be null");

        // Check if respec is enabled
        RespecConfig config = registry.getRespecConfig();
        if (!config.enabled()) {
            return RespecResult.disabled();
        }

        // Get skill tree
        GuildSkillTree tree = getOrCreateSkillTree(guildId);

        // Check if there are skills to reset (optional optimization)
        if (tree.getUnlockedSkills().isEmpty()) {
            return RespecResult.failure("No skills to reset");
        }

        // Try to consume materials from vault
        ItemStack[] contents = vault.getContents();
        int availableAmount = countMaterial(contents, config.material());

        if (availableAmount < config.amount()) {
            return RespecResult.insufficientMaterials(
                config.material().name(),
                availableAmount,
                config.amount()
            );
        }

        // Consume materials
        deductMaterial(contents, config.material(), config.amount());
        vault.setContents(contents);

        // Save vault changes
        try {
            vaultService.updateVaultContents(vault.getId(), contents);
        } catch (Exception e) {
            return RespecResult.materialConsumptionFailed();
        }

        // Calculate SP that will be restored
        int spRestored = tree.getTotalSpEarned() - tree.getAvailableSp();

        // Perform respec (buff effects are recalculated dynamically by BuffApplicationService)
        tree.respec();
        repository.save(tree);
        repository.clearUnlockedSkills(guildId);

        return RespecResult.success(spRestored);
    }

    /**
     * Awards skill points to a guild (typically from progression levelups).
     *
     * @param guildId the guild ID
     * @param amount the amount of skill points to award
     */
    public void awardSkillPoints(UUID guildId, int amount) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        if (amount <= 0) {
            return;
        }

        GuildSkillTree tree = getOrCreateSkillTree(guildId);
        tree.awardSkillPoints(amount);
        repository.save(tree);
    }

    /**
     * Retrieves a guild's skill tree, creating a new one if it doesn't exist.
     *
     * @param guildId the guild ID
     * @return the guild's skill tree (never null)
     */
    public GuildSkillTree getOrCreateSkillTree(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        Optional<GuildSkillTree> treeOpt = repository.findByGuildId(guildId);
        if (treeOpt.isPresent()) {
            return treeOpt.get();
        }

        // Create new skill tree
        GuildSkillTree newTree = new GuildSkillTree(guildId);
        repository.save(newTree);
        return newTree;
    }

    /**
     * Retrieves a guild's skill tree if it exists.
     *
     * @param guildId the guild ID
     * @return the guild's skill tree, or empty Optional
     */
    public Optional<GuildSkillTree> getSkillTree(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return repository.findByGuildId(guildId);
    }

    /**
     * Counts the available amount of a material in vault contents.
     *
     * @param contents the vault contents
     * @param material the material to count
     * @return total amount of the material available
     */
    private int countMaterial(ItemStack[] contents, org.bukkit.Material material) {
        int count = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Removes a specific amount of a material from vault contents.
     *
     * @param contents the vault contents (modified in place)
     * @param material the material to remove
     * @param amount the amount to remove
     */
    private void deductMaterial(ItemStack[] contents, org.bukkit.Material material, int amount) {
        int remaining = amount;

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int toRemove = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - toRemove);

                if (item.getAmount() <= 0) {
                    contents[i] = null;
                }

                remaining -= toRemove;
            }
        }
    }
}
