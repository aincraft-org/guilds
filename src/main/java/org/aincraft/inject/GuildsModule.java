package org.aincraft.inject;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.File;
import java.sql.SQLException;
import java.util.logging.Logger;
import org.aincraft.GuildDefaultPermissionsService;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseConfig;
import org.aincraft.database.DatabaseType;
import org.aincraft.database.DirectConnectionProvider;
import org.aincraft.database.HikariConnectionProvider;
import org.aincraft.database.SchemaManager;
import org.aincraft.database.repository.JdbcChunkClaimRepository;
import org.aincraft.database.repository.JdbcGuildDefaultPermissionsRepository;
import org.aincraft.database.repository.JdbcGuildMemberRepository;
import org.aincraft.database.repository.JdbcGuildRelationshipRepository;
import org.aincraft.database.repository.JdbcGuildRepository;
import org.aincraft.database.repository.JdbcGuildRoleRepository;
import org.aincraft.database.repository.JdbcInviteRepository;
import org.aincraft.database.repository.JdbcMemberRegionRoleRepository;
import org.aincraft.database.repository.JdbcMemberRoleRepository;
import org.aincraft.database.repository.JdbcPlayerGuildMapping;
import org.aincraft.database.repository.JdbcRegionPermissionRepository;
import org.aincraft.database.repository.JdbcRegionRoleRepository;
import org.aincraft.database.repository.JdbcRegionTypeLimitRepository;
import org.aincraft.database.repository.JdbcSubregionRepository;
import org.aincraft.database.repository.JdbcVaultRepository;
import org.aincraft.database.repository.JdbcVaultTransactionRepository;
import org.aincraft.database.repository.JdbcGuildProgressionRepository;
import org.aincraft.database.repository.JdbcProgressionLogRepository;
import org.aincraft.database.repository.JdbcGuildProjectRepository;
import org.aincraft.database.repository.JdbcActiveBuffRepository;
import org.aincraft.database.repository.JdbcChunkClaimLogRepository;
import org.aincraft.database.repository.JdbcGuildProjectPoolRepository;
import org.aincraft.database.repository.JdbcGuildDefaultRoleAssignmentRepository;
import org.aincraft.GuildService;
import org.aincraft.GuildsPlugin;
import org.aincraft.role.DefaultRoleRegistry;
import org.aincraft.role.DefaultRoleAssignmentLoader;
import org.aincraft.role.GuildDefaultRoleAssignmentRepository;
import org.aincraft.role.GuildDefaultRoleAssignmentService;
import org.aincraft.role.CompositeGuildRoleRepository;
import org.aincraft.InviteService;
import org.aincraft.RelationshipService;
import org.aincraft.claim.AutoClaimListener;
import org.aincraft.claim.AutoClaimManager;
import org.aincraft.claim.ChunkClaimLogRepository;
import org.aincraft.claim.ClaimEntryNotifier;
import org.aincraft.claim.ClaimMovementTracker;
import org.aincraft.commands.components.AcceptComponent;
import org.aincraft.commands.components.AdminComponent;
import org.aincraft.commands.components.AllyComponent;
import org.aincraft.commands.components.AutoComponent;
import org.aincraft.commands.components.ClaimComponent;
import org.aincraft.commands.components.ColorComponent;
import org.aincraft.commands.components.CreateComponent;
import org.aincraft.commands.components.DeclineComponent;
import org.aincraft.commands.components.DescriptionComponent;
import org.aincraft.commands.components.DisbandComponent;
import org.aincraft.commands.components.EnemyComponent;
import org.aincraft.commands.components.InfoComponent;
import org.aincraft.commands.components.InviteComponent;
import org.aincraft.commands.components.InvitesComponent;
import org.aincraft.commands.components.JoinComponent;
import org.aincraft.commands.components.KickComponent;
import org.aincraft.commands.components.LeaveComponent;
import org.aincraft.commands.components.ListComponent;
import org.aincraft.commands.components.LogComponent;
import org.aincraft.commands.components.MapComponent;
import org.aincraft.commands.components.MemberComponent;
import org.aincraft.commands.components.NameComponent;
import org.aincraft.commands.components.NeutralComponent;
import org.aincraft.commands.components.region.RegionComponent;
import org.aincraft.commands.components.region.RegionBasicComponent;
import org.aincraft.commands.components.region.RegionCommandHelper;
import org.aincraft.commands.components.region.RegionOwnerComponent;
import org.aincraft.commands.components.region.RegionPermissionComponent;
import org.aincraft.commands.components.region.RegionSelectionComponent;
import org.aincraft.commands.components.region.RegionTypeComponent;
import org.aincraft.commands.components.RoleComponent;
import org.aincraft.commands.components.SetspawnComponent;
import org.aincraft.commands.components.SpawnComponent;
import org.aincraft.commands.components.ToggleComponent;
import org.aincraft.commands.components.UnclaimComponent;
import org.aincraft.chat.ChatModeService;
import org.aincraft.chat.GuildChatListener;
import org.aincraft.commands.components.GuildChatComponent;
import org.aincraft.commands.components.AllyChatComponent;
import org.aincraft.commands.components.OfficerChatComponent;
import org.aincraft.config.GuildsConfig;
import org.aincraft.listeners.GuildProtectionListener;
import org.aincraft.multiblock.MultiblockListener;
import org.aincraft.multiblock.MultiblockRegistry;
import org.aincraft.multiblock.MultiblockService;
import org.aincraft.service.ChunkClaimService;
import org.aincraft.service.GuildPermissionService;
import org.aincraft.service.GuildRoleService;
import org.aincraft.service.GuildSpawnService;
import org.aincraft.storage.ChunkClaimRepository;
import org.aincraft.storage.GuildDefaultPermissionsRepository;
import org.aincraft.storage.GuildMemberRepository;
import org.aincraft.storage.GuildRelationshipRepository;
import org.aincraft.storage.GuildRepository;
import org.aincraft.storage.GuildRoleRepository;
import org.aincraft.storage.InviteRepository;
import org.aincraft.storage.MemberRoleRepository;
import org.aincraft.storage.PlayerGuildMapping;
import org.aincraft.subregion.MemberRegionRoleRepository;
import org.aincraft.subregion.RegionEntryNotifier;
import org.aincraft.subregion.RegionMovementTracker;
import org.aincraft.subregion.RegionPermissionRepository;
import org.aincraft.subregion.RegionPermissionService;
import org.aincraft.subregion.RegionRoleRepository;
import org.aincraft.subregion.RegionTypeLimitRepository;
import org.aincraft.subregion.SelectionManager;
import org.aincraft.subregion.SelectionVisualizer;
import org.aincraft.subregion.SelectionVisualizerListener;
import org.aincraft.subregion.RegionVisualizer;
import org.aincraft.subregion.SubregionRepository;
import org.aincraft.subregion.SubregionService;
import org.aincraft.subregion.SubregionTypeRegistry;
import org.aincraft.vault.VaultHandler;
import org.aincraft.vault.VaultRepository;
import org.aincraft.vault.VaultService;
import org.aincraft.vault.VaultTransactionRepository;
import org.aincraft.vault.gui.SharedVaultInventoryManager;
import org.aincraft.vault.gui.VaultGUIListener;
import org.aincraft.progression.ProgressionConfig;
import org.aincraft.progression.ProgressionService;
import org.aincraft.progression.storage.GuildProgressionRepository;
import org.aincraft.progression.storage.ProgressionLogRepository;
import org.aincraft.progression.listeners.ProgressionXpListener;
import org.aincraft.progression.listeners.ProgressionPlaytimeTask;
import org.aincraft.progression.MaterialRegistry;
import org.aincraft.progression.ProceduralCostGenerator;
import org.aincraft.commands.components.LevelUpComponent;
import org.aincraft.commands.components.ProjectComponent;
import org.aincraft.project.*;
import org.aincraft.project.storage.*;
import org.aincraft.project.listeners.*;
import org.aincraft.project.llm.*;
import org.aincraft.database.repository.JdbcLLMProjectTextRepository;
import org.aincraft.database.repository.JdbcGuildSkillTreeRepository;
import org.aincraft.skilltree.storage.GuildSkillTreeRepository;
import org.aincraft.skilltree.SkillTreeRegistry;
import org.aincraft.skilltree.SkillTreeService;
import org.aincraft.skilltree.SkillBuffProvider;
import org.aincraft.commands.components.SkillsComponent;
import org.bukkit.plugin.Plugin;

public class GuildsModule extends AbstractModule {
    private final GuildsPlugin plugin;

    public GuildsModule(GuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        bind(GuildsPlugin.class).toInstance(plugin);
        bind(Plugin.class).toInstance(plugin);
        bind(Logger.class).annotatedWith(com.google.inject.name.Names.named("guilds")).toInstance(plugin.getLogger());

        // Database abstraction layer - repositories using JDBC
        bind(GuildRepository.class).to(JdbcGuildRepository.class).in(Singleton.class);
        bind(PlayerGuildMapping.class).to(JdbcPlayerGuildMapping.class).in(Singleton.class);
        bind(GuildMemberRepository.class).to(JdbcGuildMemberRepository.class).in(Singleton.class);
        bind(GuildRoleRepository.class).annotatedWith(com.google.inject.name.Names.named("persisted")).to(JdbcGuildRoleRepository.class).in(Singleton.class);
        bind(GuildRoleRepository.class).to(CompositeGuildRoleRepository.class).in(Singleton.class);
        bind(MemberRoleRepository.class).to(JdbcMemberRoleRepository.class).in(Singleton.class);
        bind(ChunkClaimRepository.class).to(JdbcChunkClaimRepository.class).in(Singleton.class);
        bind(GuildRelationshipRepository.class).to(JdbcGuildRelationshipRepository.class).in(Singleton.class);
        bind(GuildDefaultPermissionsRepository.class).to(JdbcGuildDefaultPermissionsRepository.class).in(Singleton.class);

        // Subregion bindings - using JDBC implementations
        bind(SubregionRepository.class).to(JdbcSubregionRepository.class).in(Singleton.class);
        bind(SubregionTypeRegistry.class).in(Singleton.class);
        bind(SubregionService.class).in(Singleton.class);
        bind(SelectionVisualizer.class).in(Singleton.class);
        bind(RegionVisualizer.class).in(Singleton.class);
        bind(SelectionManager.class).in(Singleton.class);
        bind(SelectionVisualizerListener.class).in(Singleton.class);
        bind(RegionMovementTracker.class).in(Singleton.class);
        bind(RegionEntryNotifier.class).in(Singleton.class);
        bind(RegionPermissionRepository.class).to(JdbcRegionPermissionRepository.class).in(Singleton.class);
        bind(RegionPermissionService.class).in(Singleton.class);
        bind(RegionRoleRepository.class).to(JdbcRegionRoleRepository.class).in(Singleton.class);
        bind(MemberRegionRoleRepository.class).to(JdbcMemberRegionRoleRepository.class).in(Singleton.class);
        bind(RegionTypeLimitRepository.class).to(JdbcRegionTypeLimitRepository.class).in(Singleton.class);

        // Claim tracking bindings
        bind(ClaimMovementTracker.class).in(Singleton.class);
        bind(ClaimEntryNotifier.class).in(Singleton.class);

        // Auto-claim/unclaim system
        bind(AutoClaimManager.class).in(Singleton.class);
        bind(AutoClaimListener.class).in(Singleton.class);

        // Claim logging system - using JDBC implementation
        bind(ChunkClaimLogRepository.class).to(JdbcChunkClaimLogRepository.class).in(Singleton.class);

        // Invite system
        bind(InviteRepository.class).to(JdbcInviteRepository.class).in(Singleton.class);
        bind(InviteService.class).in(Singleton.class);

        // Services
        bind(GuildService.class).in(Singleton.class);
        bind(RelationshipService.class).in(Singleton.class);
        bind(GuildDefaultPermissionsService.class).in(Singleton.class);

        // Guild lifecycle service (used by progression system)
        bind(org.aincraft.service.GuildLifecycleService.class).in(Singleton.class);
        bind(org.aincraft.service.PermissionService.class).in(Singleton.class);
        bind(org.aincraft.service.GuildMemberService.class).in(Singleton.class);
        bind(org.aincraft.service.TerritoryService.class).in(Singleton.class);
        bind(org.aincraft.service.SpawnService.class).in(Singleton.class);

        // Old extracted services (may need review)
        bind(ChunkClaimService.class).in(Singleton.class);
        bind(GuildSpawnService.class).in(Singleton.class);
        bind(GuildRoleService.class).in(Singleton.class);
        bind(GuildPermissionService.class).in(Singleton.class);

        // Chat system
        bind(ChatModeService.class).in(Singleton.class);
        bind(GuildChatListener.class).in(Singleton.class);

        // Listeners
        bind(GuildProtectionListener.class).in(Singleton.class);

        // Multiblock system
        bind(MultiblockRegistry.class).in(Singleton.class);
        bind(MultiblockService.class).in(Singleton.class);
        bind(MultiblockListener.class).in(Singleton.class);

        // Vault system - using JDBC implementations
        bind(VaultRepository.class).to(JdbcVaultRepository.class).in(Singleton.class);
        bind(VaultTransactionRepository.class).to(JdbcVaultTransactionRepository.class).in(Singleton.class);
        bind(VaultService.class).in(Singleton.class);
        bind(VaultHandler.class).in(Singleton.class);
        bind(SharedVaultInventoryManager.class).in(Singleton.class);
        bind(VaultGUIListener.class).in(Singleton.class);

        // Configuration
        bind(GuildsConfig.class).in(Singleton.class);

        // Default roles system
        bind(DefaultRoleRegistry.class).in(Singleton.class);
        bind(DefaultRoleAssignmentLoader.class).in(Singleton.class);
        bind(GuildDefaultRoleAssignmentRepository.class).to(JdbcGuildDefaultRoleAssignmentRepository.class).in(Singleton.class);
        bind(GuildDefaultRoleAssignmentService.class).in(Singleton.class);

        // Progression system - using JDBC implementations
        bind(GuildProgressionRepository.class).to(JdbcGuildProgressionRepository.class).in(Singleton.class);
        bind(ProgressionLogRepository.class).to(JdbcProgressionLogRepository.class).in(Singleton.class);
        bind(ProgressionService.class).in(Singleton.class);
        bind(ProgressionConfig.class).in(Singleton.class);
        bind(ProgressionXpListener.class).in(Singleton.class);
        bind(ProgressionPlaytimeTask.class).in(Singleton.class);
        bind(MaterialRegistry.class).in(Singleton.class);
        bind(ProceduralCostGenerator.class).in(Singleton.class);

        // Command components
        bind(CreateComponent.class).in(Singleton.class);
        bind(JoinComponent.class).in(Singleton.class);
        bind(LeaveComponent.class).in(Singleton.class);
        bind(DisbandComponent.class).in(Singleton.class);
        bind(InfoComponent.class).in(Singleton.class);
        bind(ListComponent.class).in(Singleton.class);
        bind(SpawnComponent.class).in(Singleton.class);
        bind(SetspawnComponent.class).in(Singleton.class);
        bind(ColorComponent.class).in(Singleton.class);
        bind(DescriptionComponent.class).in(Singleton.class);
        bind(NameComponent.class).in(Singleton.class);
        bind(ToggleComponent.class).in(Singleton.class);
        bind(MapComponent.class).in(Singleton.class);
        bind(KickComponent.class).in(Singleton.class);
        bind(ClaimComponent.class).in(Singleton.class);
        bind(UnclaimComponent.class).in(Singleton.class);
        bind(AutoComponent.class).in(Singleton.class);
        bind(InviteComponent.class).in(Singleton.class);
        bind(AcceptComponent.class).in(Singleton.class);
        bind(DeclineComponent.class).in(Singleton.class);
        bind(InvitesComponent.class).in(Singleton.class);
        bind(RoleComponent.class).in(Singleton.class);
        bind(MemberComponent.class).in(Singleton.class);

        // Region component and subcomponents
        bind(RegionCommandHelper.class).in(Singleton.class);
        bind(RegionSelectionComponent.class).in(Singleton.class);
        bind(RegionBasicComponent.class).in(Singleton.class);
        bind(RegionTypeComponent.class).in(Singleton.class);
        bind(RegionOwnerComponent.class).in(Singleton.class);
        bind(RegionPermissionComponent.class).in(Singleton.class);
        bind(RegionComponent.class).in(Singleton.class);

        bind(AllyComponent.class).in(Singleton.class);
        bind(EnemyComponent.class).in(Singleton.class);
        bind(NeutralComponent.class).in(Singleton.class);
        bind(GuildChatComponent.class).in(Singleton.class);
        bind(AllyChatComponent.class).in(Singleton.class);
        bind(OfficerChatComponent.class).in(Singleton.class);
        bind(AdminComponent.class).in(Singleton.class);
        bind(LevelUpComponent.class).in(Singleton.class);
        bind(ProjectComponent.class).in(Singleton.class);
        bind(LogComponent.class).in(Singleton.class);

        // Project system - using JDBC implementations
        bind(GuildProjectRepository.class).to(JdbcGuildProjectRepository.class).in(Singleton.class);
        bind(GuildProjectPoolRepository.class).to(JdbcGuildProjectPoolRepository.class).in(Singleton.class);
        bind(ActiveBuffRepository.class).to(JdbcActiveBuffRepository.class).in(Singleton.class);
        bind(ProjectRegistry.class).in(Singleton.class);
        bind(ProjectGenerator.class).in(Singleton.class);
        bind(ProjectPoolService.class).in(Singleton.class);
        bind(ProjectService.class).in(Singleton.class);
        bind(BuffCategoryRegistry.class).in(Singleton.class);
        bind(BuffApplicationService.class).in(Singleton.class);
        bind(QuestProgressListener.class).in(Singleton.class);

        // LLM Project Text system
        bind(LLMProjectTextRepository.class).to(JdbcLLMProjectTextRepository.class).in(Singleton.class);
        bind(LLMProjectTextService.class).in(Singleton.class);

        // Skill Tree system
        bind(GuildSkillTreeRepository.class).to(JdbcGuildSkillTreeRepository.class).in(Singleton.class);
        bind(SkillTreeRegistry.class).in(Singleton.class);
        bind(SkillTreeService.class).in(Singleton.class);
        bind(SkillBuffProvider.class).in(Singleton.class);
        bind(SkillsComponent.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    DatabaseConfig provideDatabaseConfig() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            boolean created = dataFolder.mkdirs();
            if (!created) {
                plugin.getLogger().warning("Failed to create plugin data folder: " + dataFolder.getAbsolutePath());
            }
        }

        var config = plugin.getConfig();
        var dbSection = config.getConfigurationSection("database");

        // Get file path from config or default
        String filePath = dbSection != null ? dbSection.getString("file-path", "guilds.db") : "guilds.db";
        File dbFile = new File(dataFolder, filePath);
        // Use forward slashes for SQLite compatibility across platforms
        String absolutePath = dbFile.getAbsolutePath().replace("\\", "/");

        if (dbSection == null) {
            // Default to SQLite if no database section exists
            plugin.getLogger().info("No database config found, using SQLite at: " + absolutePath);
            return new DatabaseConfig.Builder(DatabaseType.SQLITE)
                .filePath(absolutePath)
                .build();
        }

        // Parse config
        var dbConfig = DatabaseConfig.fromConfig(dbSection);

        // For file-based databases, use resolved absolute path
        if (dbConfig.getType().isFileBased()) {
            plugin.getLogger().info("Using " + dbConfig.getType() + " database at: " + absolutePath);
            return new DatabaseConfig.Builder(dbConfig.getType())
                .filePath(absolutePath)
                .maxPoolSize(dbConfig.getMaxPoolSize())
                .minIdle(dbConfig.getMinIdle())
                .connectionTimeout(dbConfig.getConnectionTimeout())
                .idleTimeout(dbConfig.getIdleTimeout())
                .maxLifetime(dbConfig.getMaxLifetime())
                .build();
        }

        plugin.getLogger().info("Using " + dbConfig.getType() + " database at: " +
            dbConfig.getHost() + ":" + dbConfig.getPort() + "/" + dbConfig.getDatabase());
        return dbConfig;
    }

    @Provides
    @Singleton
    @Named("databasePath")
    String provideDatabasePath(DatabaseConfig config) {
        return config.getFilePath();
    }

    @Provides
    @Singleton
    ConnectionProvider provideConnectionProvider(DatabaseConfig config) {
        ConnectionProvider provider;
        if (config.getType() == DatabaseType.SQLITE) {
            provider = new DirectConnectionProvider(config);
        } else {
            provider = new HikariConnectionProvider(config);
        }
        try {
            provider.initialize();
            plugin.getLogger().info("Database connection initialized for " + config.getType());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database connection", e);
        }
        return provider;
    }

    @Provides
    @Singleton
    SchemaManager provideSchemaManager(ConnectionProvider connectionProvider, @com.google.inject.name.Named("guilds") Logger logger) {
        SchemaManager schemaManager = new SchemaManager(connectionProvider, logger);
        try {
            schemaManager.initializeSchema();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
        return schemaManager;
    }

    @Provides
    @Singleton
    LLMProvider provideLLMProvider(GuildsConfig config, @com.google.inject.name.Named("guilds") Logger logger) {
        var llmConfig = config.getPlugin().getConfig().getConfigurationSection("llm");

        // If LLM not configured or disabled, return a no-op provider
        if (llmConfig == null || !llmConfig.getBoolean("enabled", false)) {
            logger.info("LLM integration disabled or not configured");
            return new NoOpLLMProvider(logger);
        }

        String provider = llmConfig.getString("provider", "groq");

        return switch (provider.toLowerCase()) {
            case "groq" -> {
                var groqConfig = llmConfig.getConfigurationSection("groq");
                if (groqConfig == null) {
                    logger.warning("Groq config missing, falling back to no-op provider");
                    yield new NoOpLLMProvider(logger);
                }

                String apiKey = groqConfig.getString("api-key", "");
                if (apiKey.isBlank()) {
                    apiKey = System.getenv("GROQ_API_KEY") != null ? System.getenv("GROQ_API_KEY") : "";
                }

                if (apiKey.isBlank()) {
                    logger.warning("Groq API key not configured, falling back to no-op provider");
                    yield new NoOpLLMProvider(logger);
                }

                String model = groqConfig.getString("model", "llama-3.1-8b-instant");
                logger.info("LLM provider initialized: Groq (" + model + ")");
                yield new GroqProvider(apiKey, model, logger);
            }
            case "huggingface" -> {
                var hfConfig = llmConfig.getConfigurationSection("huggingface");
                if (hfConfig == null) {
                    logger.warning("HuggingFace config missing, falling back to no-op provider");
                    yield new NoOpLLMProvider(logger);
                }

                String apiKey = hfConfig.getString("api-key", "");
                if (apiKey.isBlank()) {
                    apiKey = System.getenv("HUGGINGFACE_API_KEY") != null ? System.getenv("HUGGINGFACE_API_KEY") : "";
                }

                if (apiKey.isBlank()) {
                    logger.warning("HuggingFace API key not configured, falling back to no-op provider");
                    yield new NoOpLLMProvider(logger);
                }

                String model = hfConfig.getString("model", "microsoft/Phi-3-mini-4k-instruct");
                logger.info("LLM provider initialized: HuggingFace (" + model + ")");
                yield new HuggingFaceProvider(apiKey, model, logger);
            }
            default -> {
                logger.warning("Unknown LLM provider: " + provider + ", falling back to no-op provider");
                yield new NoOpLLMProvider(logger);
            }
        };
    }
}
