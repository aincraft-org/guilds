package org.aincraft.skilltree;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.GuildPermission;
import org.aincraft.service.PermissionService;
import org.aincraft.skilltree.storage.GuildSkillTreeRepository;
import org.aincraft.vault.Vault;
import org.aincraft.vault.VaultService;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Service for managing guild skill trees.
 * Handles skill unlocking, respec, and skill point management.
 */
@Singleton
public class SkillTreeService {

    private final GuildSkillTreeRepository repository;
    private final SkillTreeRegistry registry;
    private final PermissionService permissionService;
    private final VaultService vaultService;

    @Inject
    public SkillTreeService(
            GuildSkillTreeRepository repository,
            SkillTreeRegistry registry,
            PermissionService permissionService,
            VaultService vaultService
    ) {
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
        this.registry = Objects.requireNonNull(registry, "Registry cannot be null");
        this.permissionService = Objects.requireNonNull(permissionService, "PermissionService cannot be null");
        this.vaultService = Objects.requireNonNull(vaultService, "VaultService cannot be null");
    }

    /**
     * Gets or creates a skill tree for a guild.
     */
    public GuildSkillTree getOrCreateSkillTree(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        return repository.findByGuildId(guildId)
                .orElseGet(() -> {
                    GuildSkillTree newTree = new GuildSkillTree(guildId);
                    repository.save(newTree);
                    return newTree;
                });
    }

    /**
     * Gets a skill tree for a guild.
     */
    public Optional<GuildSkillTree> getSkillTree(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return repository.findByGuildId(guildId);
    }

    /**
     * Attempts to unlock a skill for a guild.
     * Validates permission, prerequisites, and SP cost.
     */
    public SkillUnlockResult unlockSkill(String guildId, UUID requesterId, String skillId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");
        Objects.requireNonNull(skillId, "Skill ID cannot be null");

        // Check permission
        if (!permissionService.hasPermission(guildId, requesterId, GuildPermission.MANAGE_SKILLS)) {
            return SkillUnlockResult.noPermission();
        }

        // Get skill definition
        Optional<SkillDefinition> skillOpt = registry.getSkill(skillId);
        if (skillOpt.isEmpty()) {
            return SkillUnlockResult.skillNotFound();
        }
        SkillDefinition skill = skillOpt.get();

        // Get skill tree
        GuildSkillTree tree = getOrCreateSkillTree(guildId);

        // Check if already unlocked
        if (tree.hasSkill(skillId)) {
            return SkillUnlockResult.alreadyUnlocked();
        }

        // Check SP
        if (!tree.canAfford(skill.spCost())) {
            return SkillUnlockResult.insufficientSP(tree.getAvailableSkillPoints(), skill.spCost());
        }

        // Check prerequisites
        for (String prereqId : skill.prerequisites()) {
            if (!tree.hasSkill(prereqId)) {
                Optional<SkillDefinition> prereqOpt = registry.getSkill(prereqId);
                String prereqName = prereqOpt.map(SkillDefinition::name).orElse(prereqId);
                return SkillUnlockResult.missingPrerequisites(prereqName);
            }
        }

        // Unlock the skill
        tree.unlockSkill(skillId, skill.spCost());
        repository.save(tree);
        repository.unlockSkill(guildId, skillId);

        return SkillUnlockResult.success(skill);
    }

    /**
     * Checks if a skill can be unlocked (for UI display).
     */
    public boolean canUnlock(String guildId, String skillId) {
        Optional<SkillDefinition> skillOpt = registry.getSkill(skillId);
        if (skillOpt.isEmpty()) return false;

        SkillDefinition skill = skillOpt.get();
        GuildSkillTree tree = getOrCreateSkillTree(guildId);

        if (tree.hasSkill(skillId)) return false;
        if (!tree.canAfford(skill.spCost())) return false;

        for (String prereqId : skill.prerequisites()) {
            if (!tree.hasSkill(prereqId)) return false;
        }

        return true;
    }

    /**
     * Checks if prerequisites are met for a skill (ignoring SP).
     */
    public boolean hasPrerequisites(String guildId, String skillId) {
        Optional<SkillDefinition> skillOpt = registry.getSkill(skillId);
        if (skillOpt.isEmpty()) return false;

        GuildSkillTree tree = getOrCreateSkillTree(guildId);

        for (String prereqId : skillOpt.get().prerequisites()) {
            if (!tree.hasSkill(prereqId)) return false;
        }
        return true;
    }

    /**
     * Attempts to respec a guild's skill tree.
     * Requires respec material in vault.
     */
    public RespecResult respec(String guildId, UUID requesterId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");

        // Check if respec is enabled
        if (!registry.isRespecEnabled()) {
            return RespecResult.failure("Respec is disabled");
        }

        // Check permission
        if (!permissionService.hasPermission(guildId, requesterId, GuildPermission.MANAGE_SKILLS)) {
            return RespecResult.noPermission();
        }

        // Get skill tree
        GuildSkillTree tree = getOrCreateSkillTree(guildId);

        // Check if there are skills to reset
        if (tree.getUnlockedSkillIds().isEmpty()) {
            return RespecResult.noSkillsToReset();
        }

        // Check vault for respec material
        Optional<Vault> vaultOpt = vaultService.getGuildVault(guildId);
        if (vaultOpt.isEmpty()) {
            return RespecResult.failure("No guild vault found");
        }

        Vault vault = vaultOpt.get();
        Material respecMaterial = registry.getRespecMaterial();
        int respecAmount = registry.getRespecAmount();

        // Count material in vault
        int available = countMaterial(vault.getContents(), respecMaterial);
        if (available < respecAmount) {
            String materialName = formatMaterialName(respecMaterial);
            return RespecResult.insufficientMaterials(materialName, available, respecAmount);
        }

        // Deduct material from vault
        ItemStack[] contents = vault.getContents();
        deductMaterial(contents, respecMaterial, respecAmount);
        vault.setContents(contents);
        vaultService.updateVaultContents(vault.getId(), contents);

        // Calculate refunded points
        int refundedPoints = tree.getTotalSkillPointsEarned() - tree.getAvailableSkillPoints();

        // Respec
        tree.respec();
        repository.save(tree);
        repository.deleteAllSkills(guildId);

        return RespecResult.success(refundedPoints);
    }

    /**
     * Awards skill points to a guild.
     */
    public void awardSkillPoints(String guildId, int amount) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        if (amount <= 0) return;

        GuildSkillTree tree = getOrCreateSkillTree(guildId);
        tree.awardSkillPoints(amount);
        repository.save(tree);
    }

    /**
     * Gets all unlocked skills for a guild.
     */
    public Set<String> getUnlockedSkills(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return repository.getUnlockedSkills(guildId);
    }

    /**
     * Deletes all skill tree data for a guild.
     */
    public void deleteSkillTree(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        repository.delete(guildId);
    }

    // Helper methods

    private int countMaterial(ItemStack[] contents, Material material) {
        int count = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void deductMaterial(ItemStack[] contents, Material material, int amount) {
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int taken = Math.min(item.getAmount(), remaining);
                remaining -= taken;
                if (taken >= item.getAmount()) {
                    contents[i] = null;
                } else {
                    item.setAmount(item.getAmount() - taken);
                }
            }
        }
    }

    private String formatMaterialName(Material material) {
        String name = material.name().replace("_", " ").toLowerCase();
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
