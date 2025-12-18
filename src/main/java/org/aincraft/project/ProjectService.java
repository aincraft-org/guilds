package org.aincraft.project;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildService;
import org.aincraft.project.storage.ActiveBuffRepository;
import org.aincraft.project.storage.GuildProjectRepository;
import org.aincraft.vault.Vault;
import org.aincraft.vault.VaultRepository;
import org.aincraft.vault.VaultTransaction;
import org.aincraft.vault.VaultTransactionRepository;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

@Singleton
public class ProjectService {

    private final GuildProjectRepository projectRepository;
    private final ActiveBuffRepository buffRepository;
    private final ProjectRegistry registry;
    private final ProjectPoolService poolService;
    private final GuildService guildService;
    private final VaultRepository vaultRepository;
    private final VaultTransactionRepository vaultTransactionRepository;

    @Inject
    public ProjectService(
            GuildProjectRepository projectRepository,
            ActiveBuffRepository buffRepository,
            ProjectRegistry registry,
            ProjectPoolService poolService,
            GuildService guildService,
            VaultRepository vaultRepository,
            VaultTransactionRepository vaultTransactionRepository
    ) {
        this.projectRepository = Objects.requireNonNull(projectRepository);
        this.buffRepository = Objects.requireNonNull(buffRepository);
        this.registry = Objects.requireNonNull(registry);
        this.poolService = Objects.requireNonNull(poolService);
        this.guildService = Objects.requireNonNull(guildService);
        this.vaultRepository = Objects.requireNonNull(vaultRepository);
        this.vaultTransactionRepository = Objects.requireNonNull(vaultTransactionRepository);
    }

    public ProjectStartResult startProject(String guildId, UUID requesterId, String projectDefId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");
        Objects.requireNonNull(projectDefId, "Project definition ID cannot be null");

        // Check permission
        if (!guildService.hasPermission(guildId, requesterId, GuildPermission.MANAGE_PROJECTS)) {
            return ProjectStartResult.noPermission();
        }

        // Check if guild already has active project
        Optional<GuildProject> existingProject = projectRepository.findActiveByGuildId(guildId);
        if (existingProject.isPresent()) {
            return ProjectStartResult.alreadyActive();
        }

        // Get guild level for project availability check
        Guild guild = guildService.getGuildById(guildId);
        if (guild == null) {
            return ProjectStartResult.failure("Guild not found");
        }

        // Check project is available in pool
        int guildLevel = getGuildLevel(guildId);
        if (!poolService.isProjectAvailable(guildId, guildLevel, projectDefId)) {
            return ProjectStartResult.notAvailable();
        }

        // Get project definition
        Optional<ProjectDefinition> definitionOpt = registry.getProject(projectDefId);
        if (definitionOpt.isEmpty()) {
            return ProjectStartResult.failure("Project definition not found");
        }

        ProjectDefinition definition = definitionOpt.get();

        // Check level requirement
        if (guildLevel < definition.requiredLevel()) {
            return ProjectStartResult.levelTooLow(definition.requiredLevel());
        }

        // Create new project
        GuildProject project = new GuildProject(
                UUID.randomUUID().toString(),
                guildId,
                projectDefId,
                ProjectStatus.IN_PROGRESS,
                new HashMap<>(),
                new HashMap<>(),
                System.currentTimeMillis(),
                null
        );

        projectRepository.save(project);
        return ProjectStartResult.success(project);
    }

    public Optional<GuildProject> getActiveProject(String guildId) {
        return projectRepository.findActiveByGuildId(guildId);
    }

    /**
     * Calculates overall project progress based on:
     * 1. Quest completion percentage
     * 2. Material availability in vault (not contribution tracking)
     *
     * @param project the project to check
     * @return progress from 0.0 to 1.0
     */
    public double calculateProgress(GuildProject project) {
        Optional<ProjectDefinition> defOpt = registry.getProject(project.getProjectDefinitionId());
        if (defOpt.isEmpty()) {
            return 0.0;
        }

        ProjectDefinition definition = defOpt.get();
        int totalRequirements = definition.quests().size() + definition.materials().size();
        if (totalRequirements == 0) {
            return 1.0;
        }

        double completedRequirements = 0;

        // Calculate quest progress
        for (QuestRequirement quest : definition.quests()) {
            long current = project.getQuestProgress(quest.id());
            double questProgress = Math.min(1.0, (double) current / quest.targetCount());
            completedRequirements += questProgress;
        }

        // Calculate material progress based on vault contents
        Optional<Vault> vaultOpt = vaultRepository.findByGuildId(project.getGuildId());
        if (vaultOpt.isPresent()) {
            Vault vault = vaultOpt.get();
            ItemStack[] contents = vaultRepository.getFreshContents(vault.getId());

            for (Map.Entry<Material, Integer> entry : definition.materials().entrySet()) {
                int inVault = countMaterialInVault(contents, entry.getKey());
                int required = entry.getValue();
                double materialProgress = Math.min(1.0, (double) inVault / required);
                completedRequirements += materialProgress;
            }
        }
        // If no vault, materials count as 0% complete

        return completedRequirements / totalRequirements;
    }

    /**
     * Checks if a project is complete by verifying:
     * 1. All quests are complete
     * 2. All required materials are currently in the vault
     *
     * @param project the project to check
     * @return true if all requirements met and materials in vault
     */
    public boolean isProjectComplete(GuildProject project) {
        Optional<ProjectDefinition> defOpt = registry.getProject(project.getProjectDefinitionId());
        if (defOpt.isEmpty()) {
            return false;
        }

        ProjectDefinition definition = defOpt.get();

        // Check all quests complete
        for (QuestRequirement quest : definition.quests()) {
            if (project.getQuestProgress(quest.id()) < quest.targetCount()) {
                return false;
            }
        }

        // Check all materials currently in vault
        Optional<Vault> vaultOpt = vaultRepository.findByGuildId(project.getGuildId());
        if (vaultOpt.isEmpty()) {
            return definition.materials().isEmpty(); // If no vault but materials needed, not complete
        }

        Vault vault = vaultOpt.get();
        ItemStack[] contents = vaultRepository.getFreshContents(vault.getId());

        for (Map.Entry<Material, Integer> entry : definition.materials().entrySet()) {
            int inVault = countMaterialInVault(contents, entry.getKey());
            if (inVault < entry.getValue()) {
                return false; // Not enough materials in vault
            }
        }

        return true;
    }

    public void recordQuestProgress(String guildId, QuestType type, String targetId, long amount) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(type, "Quest type cannot be null");
        Objects.requireNonNull(targetId, "Target ID cannot be null");

        Optional<GuildProject> projectOpt = projectRepository.findActiveByGuildId(guildId);
        if (projectOpt.isEmpty()) {
            return;
        }

        GuildProject project = projectOpt.get();
        Optional<ProjectDefinition> defOpt = registry.getProject(project.getProjectDefinitionId());
        if (defOpt.isEmpty()) {
            return;
        }

        ProjectDefinition definition = defOpt.get();

        // Find matching quests and update progress
        for (QuestRequirement quest : definition.quests()) {
            if (quest.type() == type && quest.targetId().equalsIgnoreCase(targetId)) {
                long current = project.getQuestProgress(quest.id());
                long newCount = Math.min(current + amount, quest.targetCount());
                project.setQuestProgress(quest.id(), newCount);
                projectRepository.updateQuestProgress(project.getId(), quest.id(), newCount);
            }
        }
    }

    /**
     * @deprecated Material contributions are no longer tracked. Materials are taken from vault
     *             atomically when project is completed via {@link #completeProject(String, UUID)}.
     *             This method is kept for backward compatibility but should not be used.
     */
    @Deprecated
    public MaterialContributionResult contributeMaterials(String guildId, UUID requesterId) {
        return MaterialContributionResult.failure("Material contributions are no longer supported. " +
                "Materials will be taken from vault when you complete the project.");
    }

    /**
     * Calculates how many of each required material are currently available in the guild vault.
     * Returns total amounts in vault (not "still needed" amounts).
     *
     * @param guildId the guild ID
     * @return map of materials to total vault amounts, or empty map if no active project or vault
     */
    public Map<Material, Integer> calculateAvailableMaterials(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        // Get active project
        Optional<GuildProject> projectOpt = projectRepository.findActiveByGuildId(guildId);
        if (projectOpt.isEmpty()) {
            return Map.of();
        }

        GuildProject project = projectOpt.get();
        Optional<ProjectDefinition> defOpt = registry.getProject(project.getProjectDefinitionId());
        if (defOpt.isEmpty()) {
            return Map.of();
        }

        ProjectDefinition definition = defOpt.get();

        // Get guild vault
        Optional<Vault> vaultOpt = vaultRepository.findByGuildId(guildId);
        if (vaultOpt.isEmpty()) {
            return Map.of();
        }

        Vault vault = vaultOpt.get();
        ItemStack[] contents = vaultRepository.getFreshContents(vault.getId());

        // Count total materials in vault for each required material
        Map<Material, Integer> available = new HashMap<>();
        for (Map.Entry<Material, Integer> required : definition.materials().entrySet()) {
            Material material = required.getKey();
            int inVault = countMaterialInVault(contents, material);
            available.put(material, inVault);
        }

        return available;
    }

    /**
     * Counts how many of a specific material are in the vault contents.
     */
    private int countMaterialInVault(ItemStack[] contents, Material material) {
        int count = 0;
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private int takeMaterialsFromVault(ItemStack[] contents, Material material, int maxAmount) {
        int taken = 0;
        for (int i = 0; i < contents.length && taken < maxAmount; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.getType() == material) {
                int toTake = Math.min(stack.getAmount(), maxAmount - taken);
                taken += toTake;
                if (toTake >= stack.getAmount()) {
                    contents[i] = null;
                } else {
                    stack.setAmount(stack.getAmount() - toTake);
                }
            }
        }
        return taken;
    }

    /**
     * Completes a project by:
     * 1. Verifying all quests complete and materials in vault
     * 2. Taking materials from vault atomically
     * 3. Activating the project buff
     * 4. Marking project as complete
     *
     * @param guildId guild ID
     * @param requesterId player requesting completion
     * @return result with buff or error
     */
    public ProjectCompletionResult completeProject(String guildId, UUID requesterId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");

        // Check permission
        if (!guildService.hasPermission(guildId, requesterId, GuildPermission.MANAGE_PROJECTS)) {
            return ProjectCompletionResult.noPermission();
        }

        // Get active project
        Optional<GuildProject> projectOpt = projectRepository.findActiveByGuildId(guildId);
        if (projectOpt.isEmpty()) {
            return ProjectCompletionResult.noActiveProject();
        }

        GuildProject project = projectOpt.get();
        Optional<ProjectDefinition> defOpt = registry.getProject(project.getProjectDefinitionId());
        if (defOpt.isEmpty()) {
            return ProjectCompletionResult.failure("Project definition not found");
        }

        ProjectDefinition definition = defOpt.get();

        // Verify all quests complete
        for (QuestRequirement quest : definition.quests()) {
            if (project.getQuestProgress(quest.id()) < quest.targetCount()) {
                return ProjectCompletionResult.notComplete(calculateProgress(project));
            }
        }

        // Get vault and verify materials available
        Optional<Vault> vaultOpt = vaultRepository.findByGuildId(guildId);
        if (vaultOpt.isEmpty() && !definition.materials().isEmpty()) {
            return ProjectCompletionResult.failure("Guild vault not found");
        }

        // ATOMIC MATERIAL CONSUMPTION - verify and take materials
        if (!definition.materials().isEmpty()) {
            Vault vault = vaultOpt.get();
            ItemStack[] contents = vaultRepository.getFreshContents(vault.getId());

            // First pass: verify ALL materials available
            for (Map.Entry<Material, Integer> entry : definition.materials().entrySet()) {
                int inVault = countMaterialInVault(contents, entry.getKey());
                if (inVault < entry.getValue()) {
                    return ProjectCompletionResult.missingMaterials(
                            entry.getKey(),
                            inVault,
                            entry.getValue()
                    );
                }
            }

            // Second pass: take all materials atomically
            for (Map.Entry<Material, Integer> entry : definition.materials().entrySet()) {
                Material material = entry.getKey();
                int required = entry.getValue();

                int taken = takeMaterialsFromVault(contents, material, required);
                if (taken != required) {
                    // This should never happen due to first pass verification
                    return ProjectCompletionResult.failure(
                            "Failed to take materials from vault (expected " + required +
                            " but took " + taken + " of " + material.name() + ")"
                    );
                }

                // Log transaction for each material type
                VaultTransaction transaction = new VaultTransaction(
                        vault.getId(),
                        requesterId,
                        VaultTransaction.TransactionType.WITHDRAW,
                        material,
                        taken
                );
                vaultTransactionRepository.log(transaction);
            }

            // Save vault contents with materials removed
            vaultRepository.updateContents(vault.getId(), contents);
        }

        // Delete any existing active buff for this guild (one buff at a time rule)
        buffRepository.findActiveByGuildId(guildId).ifPresent(existingBuff -> {
            buffRepository.delete(existingBuff.id());
        });

        // Create and activate buff
        long now = System.currentTimeMillis();
        ActiveBuff buff = new ActiveBuff(
                UUID.randomUUID().toString(),
                guildId,
                project.getProjectDefinitionId(),
                definition.buff().categoryId(),
                definition.buff().value(),
                now,
                now + definition.buffDurationMillis()
        );
        buffRepository.save(buff);

        // Mark project complete
        project.setStatus(ProjectStatus.COMPLETED);
        project.setCompletedAt(now);
        projectRepository.updateStatus(project.getId(), ProjectStatus.COMPLETED, now);

        return ProjectCompletionResult.success(buff);
    }

    public boolean abandonProject(String guildId, UUID requesterId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");

        // Check permission
        if (!guildService.hasPermission(guildId, requesterId, GuildPermission.MANAGE_PROJECTS)) {
            return false;
        }

        // Get active project
        Optional<GuildProject> projectOpt = projectRepository.findActiveByGuildId(guildId);
        if (projectOpt.isEmpty()) {
            return false;
        }

        GuildProject project = projectOpt.get();

        // Mark as abandoned and delete (progress wipes on abandon)
        projectRepository.delete(project.getId());

        return true;
    }

    public Optional<ActiveBuff> getActiveBuff(String guildId) {
        return buffRepository.findActiveByGuildId(guildId);
    }

    public List<ActiveBuff> getAllBuffs(String guildId) {
        return buffRepository.findByGuildId(guildId);
    }

    public List<ProjectDefinition> getAvailableProjects(String guildId) {
        int guildLevel = getGuildLevel(guildId);
        return poolService.getAvailableProjects(guildId, guildLevel);
    }

    private int getGuildLevel(String guildId) {
        // TODO: integrate with ProgressionService properly
        // For now return 1 as default
        return 1;
    }

    // Result classes

    public record ProjectStartResult(boolean success, GuildProject project, String errorMessage, int requiredLevel) {
        public static ProjectStartResult success(GuildProject project) {
            return new ProjectStartResult(true, project, null, 0);
        }

        public static ProjectStartResult noPermission() {
            return new ProjectStartResult(false, null, "You don't have permission to manage projects", 0);
        }

        public static ProjectStartResult alreadyActive() {
            return new ProjectStartResult(false, null, "Your guild already has an active project", 0);
        }

        public static ProjectStartResult notAvailable() {
            return new ProjectStartResult(false, null, "This project is not available in your current pool", 0);
        }

        public static ProjectStartResult levelTooLow(int required) {
            return new ProjectStartResult(false, null, "Your guild needs to be level " + required, required);
        }

        public static ProjectStartResult failure(String message) {
            return new ProjectStartResult(false, null, message, 0);
        }
    }

    public record MaterialContributionResult(boolean success, Map<Material, Integer> contributed, String errorMessage, Map<Material, Integer> missing) {
        public static MaterialContributionResult success(Map<Material, Integer> contributed) {
            return new MaterialContributionResult(true, contributed, null, null);
        }

        public static MaterialContributionResult noPermission() {
            return new MaterialContributionResult(false, null, "You don't have permission to withdraw from vault", null);
        }

        public static MaterialContributionResult noActiveProject() {
            return new MaterialContributionResult(false, null, "No active project", null);
        }

        public static MaterialContributionResult noVault() {
            return new MaterialContributionResult(false, null, "Your guild doesn't have a vault", null);
        }

        public static MaterialContributionResult insufficientMaterials(Map<Material, Integer> missing) {
            StringBuilder msg = new StringBuilder("Missing materials from vault: ");
            for (Map.Entry<Material, Integer> entry : missing.entrySet()) {
                msg.append(entry.getValue()).append("x ").append(entry.getKey().name()).append(", ");
            }
            return new MaterialContributionResult(false, null, msg.toString(), missing);
        }

        public static MaterialContributionResult failure(String message) {
            return new MaterialContributionResult(false, null, message, null);
        }
    }

    public record ProjectCompletionResult(boolean success, ActiveBuff buff, String errorMessage, double progress) {
        public static ProjectCompletionResult success(ActiveBuff buff) {
            return new ProjectCompletionResult(true, buff, null, 1.0);
        }

        public static ProjectCompletionResult noPermission() {
            return new ProjectCompletionResult(false, null, "You don't have permission to manage projects", 0);
        }

        public static ProjectCompletionResult noActiveProject() {
            return new ProjectCompletionResult(false, null, "No active project", 0);
        }

        public static ProjectCompletionResult notComplete(double progress) {
            return new ProjectCompletionResult(false, null, "Project is not complete yet", progress);
        }

        public static ProjectCompletionResult missingMaterials(Material material, int inVault, int required) {
            String msg = String.format("Missing materials: Need %d %s but only %d in vault",
                    required, material.name(), inVault);
            return new ProjectCompletionResult(false, null, msg, 0);
        }

        public static ProjectCompletionResult failure(String message) {
            return new ProjectCompletionResult(false, null, message, 0);
        }
    }
}
