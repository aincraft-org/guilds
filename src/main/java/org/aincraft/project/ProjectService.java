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

        // Calculate material progress
        for (Map.Entry<Material, Integer> entry : definition.materials().entrySet()) {
            int current = project.getMaterialContributed(entry.getKey());
            double materialProgress = Math.min(1.0, (double) current / entry.getValue());
            completedRequirements += materialProgress;
        }

        return completedRequirements / totalRequirements;
    }

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

        // Check all materials contributed
        for (Map.Entry<Material, Integer> entry : definition.materials().entrySet()) {
            if (project.getMaterialContributed(entry.getKey()) < entry.getValue()) {
                return false;
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

    public MaterialContributionResult contributeMaterials(String guildId, UUID requesterId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");

        // Check permission
        if (!guildService.hasPermission(guildId, requesterId, GuildPermission.VAULT_WITHDRAW)) {
            return MaterialContributionResult.noPermission();
        }

        // Get active project
        Optional<GuildProject> projectOpt = projectRepository.findActiveByGuildId(guildId);
        if (projectOpt.isEmpty()) {
            return MaterialContributionResult.noActiveProject();
        }

        GuildProject project = projectOpt.get();
        Optional<ProjectDefinition> defOpt = registry.getProject(project.getProjectDefinitionId());
        if (defOpt.isEmpty()) {
            return MaterialContributionResult.failure("Project definition not found");
        }

        ProjectDefinition definition = defOpt.get();

        // Get guild vault
        Optional<Vault> vaultOpt = vaultRepository.findByGuildId(guildId);
        if (vaultOpt.isEmpty()) {
            return MaterialContributionResult.noVault();
        }

        Vault vault = vaultOpt.get();
        ItemStack[] contents = vaultRepository.getFreshContents(vault.getId());

        // Calculate what can be contributed
        Map<Material, Integer> contributed = new HashMap<>();
        boolean anyContribution = false;

        for (Map.Entry<Material, Integer> required : definition.materials().entrySet()) {
            Material material = required.getKey();
            int needed = required.getValue() - project.getMaterialContributed(material);
            if (needed <= 0) continue;

            int taken = takeMaterialsFromVault(contents, material, needed);
            if (taken > 0) {
                contributed.put(material, taken);
                int newTotal = project.getMaterialContributed(material) + taken;
                project.setMaterialContributed(material, newTotal);
                projectRepository.updateMaterialContribution(project.getId(), material, newTotal);

                // Log transaction
                VaultTransaction transaction = new VaultTransaction(
                        vault.getId(),
                        requesterId,
                        VaultTransaction.TransactionType.WITHDRAW,
                        material,
                        taken
                );
                vaultTransactionRepository.log(transaction);

                anyContribution = true;
            }
        }

        if (anyContribution) {
            vaultRepository.updateContents(vault.getId(), contents);
            return MaterialContributionResult.success(contributed);
        }

        return MaterialContributionResult.noMaterialsAvailable();
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

        // Check project is complete
        if (!isProjectComplete(project)) {
            return ProjectCompletionResult.notComplete(calculateProgress(project));
        }

        Optional<ProjectDefinition> defOpt = registry.getProject(project.getProjectDefinitionId());
        if (defOpt.isEmpty()) {
            return ProjectCompletionResult.failure("Project definition not found");
        }

        ProjectDefinition definition = defOpt.get();

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
                definition.buff().category(),
                definition.buff().value(),
                now,
                now + definition.buffDurationMillis()
        );
        buffRepository.save(buff);

        // Mark project complete
        project.setStatus(ProjectStatus.COMPLETED);
        project.setCompletedAt(now);
        projectRepository.updateStatus(project.getId(), ProjectStatus.COMPLETED, now);

        // Refresh project pool for next selection
        poolService.refreshPool(guildId);

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

        // Refresh project pool
        poolService.refreshPool(guildId);

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

    public record MaterialContributionResult(boolean success, Map<Material, Integer> contributed, String errorMessage) {
        public static MaterialContributionResult success(Map<Material, Integer> contributed) {
            return new MaterialContributionResult(true, contributed, null);
        }

        public static MaterialContributionResult noPermission() {
            return new MaterialContributionResult(false, null, "You don't have permission to withdraw from vault");
        }

        public static MaterialContributionResult noActiveProject() {
            return new MaterialContributionResult(false, null, "No active project");
        }

        public static MaterialContributionResult noVault() {
            return new MaterialContributionResult(false, null, "Your guild doesn't have a vault");
        }

        public static MaterialContributionResult noMaterialsAvailable() {
            return new MaterialContributionResult(false, null, "No materials available to contribute");
        }

        public static MaterialContributionResult failure(String message) {
            return new MaterialContributionResult(false, null, message);
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

        public static ProjectCompletionResult failure(String message) {
            return new ProjectCompletionResult(false, null, message, 0);
        }
    }
}
