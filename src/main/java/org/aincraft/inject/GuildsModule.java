package org.aincraft.inject;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.File;
import org.aincraft.GuildDefaultPermissionsService;
import org.aincraft.GuildService;
import org.aincraft.GuildsPlugin;
import org.aincraft.InviteService;
import org.aincraft.RelationshipService;
import org.aincraft.claim.AutoClaimListener;
import org.aincraft.claim.AutoClaimManager;
import org.aincraft.claim.AutoUnclaimListener;
import org.aincraft.claim.ChunkClaimLogRepository;
import org.aincraft.claim.ClaimEntryNotifier;
import org.aincraft.claim.ClaimMovementTracker;
import org.aincraft.claim.SQLiteChunkClaimLogRepository;
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
import org.aincraft.commands.components.MapComponent;
import org.aincraft.commands.components.MemberComponent;
import org.aincraft.commands.components.NameComponent;
import org.aincraft.commands.components.NeutralComponent;
import org.aincraft.commands.components.RoleComponent;
import org.aincraft.commands.components.SetspawnComponent;
import org.aincraft.commands.components.SpawnComponent;
import org.aincraft.commands.components.ToggleComponent;
import org.aincraft.commands.components.UnclaimComponent;
import org.aincraft.chat.ChatModeService;
import org.aincraft.chat.GuildChatListener;
import org.aincraft.commands.components.GuildChatComponent;
import org.aincraft.commands.components.AllyChatComponent;
import org.aincraft.config.GuildsConfig;
import org.aincraft.listeners.GuildProtectionListener;
import org.aincraft.multiblock.MultiblockListener;
import org.aincraft.multiblock.MultiblockRegistry;
import org.aincraft.multiblock.MultiblockService;
import org.aincraft.role.gui.RoleCreationGUIListener;
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
import org.aincraft.storage.SQLiteChunkClaimRepository;
import org.aincraft.storage.SQLiteGuildDefaultPermissionsRepository;
import org.aincraft.storage.SQLiteGuildMemberRepository;
import org.aincraft.storage.SQLiteGuildRelationshipRepository;
import org.aincraft.storage.SQLiteGuildRepository;
import org.aincraft.storage.SQLiteGuildRoleRepository;
import org.aincraft.storage.SQLiteInviteRepository;
import org.aincraft.storage.SQLiteMemberRoleRepository;
import org.aincraft.storage.SQLitePlayerGuildMapping;
import org.aincraft.subregion.MemberRegionRoleRepository;
import org.aincraft.subregion.RegionEntryNotifier;
import org.aincraft.subregion.RegionMovementTracker;
import org.aincraft.subregion.RegionPermissionRepository;
import org.aincraft.subregion.RegionPermissionService;
import org.aincraft.subregion.RegionRoleRepository;
import org.aincraft.subregion.RegionTypeLimitRepository;
import org.aincraft.subregion.SQLiteMemberRegionRoleRepository;
import org.aincraft.subregion.SQLiteRegionPermissionRepository;
import org.aincraft.subregion.SQLiteRegionRoleRepository;
import org.aincraft.subregion.SQLiteRegionTypeLimitRepository;
import org.aincraft.subregion.SQLiteSubregionRepository;
import org.aincraft.subregion.SelectionManager;
import org.aincraft.subregion.SelectionVisualizer;
import org.aincraft.subregion.SelectionVisualizerListener;
import org.aincraft.subregion.SubregionRepository;
import org.aincraft.subregion.SubregionService;
import org.aincraft.subregion.SubregionTypeRegistry;
import org.aincraft.vault.SQLiteVaultRepository;
import org.aincraft.vault.SQLiteVaultTransactionRepository;
import org.aincraft.vault.VaultHandler;
import org.aincraft.vault.VaultRepository;
import org.aincraft.vault.VaultService;
import org.aincraft.vault.VaultTransactionRepository;
import org.aincraft.vault.gui.VaultGUIListener;
import org.aincraft.progression.ProgressionConfig;
import org.aincraft.progression.ProgressionService;
import org.aincraft.progression.storage.GuildProgressionRepository;
import org.aincraft.progression.storage.SQLiteGuildProgressionRepository;
import org.aincraft.progression.listeners.ProgressionXpListener;
import org.aincraft.progression.listeners.ProgressionPlaytimeTask;
import org.aincraft.commands.components.LevelComponent;
import org.aincraft.commands.components.LevelUpComponent;

public class GuildsModule extends AbstractModule {
    private final GuildsPlugin plugin;

    public GuildsModule(GuildsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        bind(GuildsPlugin.class).toInstance(plugin);

        bind(GuildRepository.class).to(SQLiteGuildRepository.class).in(Singleton.class);
        bind(PlayerGuildMapping.class).to(SQLitePlayerGuildMapping.class).in(Singleton.class);
        bind(GuildMemberRepository.class).to(SQLiteGuildMemberRepository.class).in(Singleton.class);
        bind(GuildRoleRepository.class).to(SQLiteGuildRoleRepository.class).in(Singleton.class);
        bind(MemberRoleRepository.class).to(SQLiteMemberRoleRepository.class).in(Singleton.class);
        bind(ChunkClaimRepository.class).to(SQLiteChunkClaimRepository.class).in(Singleton.class);
        bind(GuildRelationshipRepository.class).to(SQLiteGuildRelationshipRepository.class).in(Singleton.class);
        bind(GuildDefaultPermissionsRepository.class).to(SQLiteGuildDefaultPermissionsRepository.class).in(Singleton.class);

        // Subregion bindings
        bind(SubregionRepository.class).to(SQLiteSubregionRepository.class).in(Singleton.class);
        bind(SubregionTypeRegistry.class).in(Singleton.class);
        bind(SubregionService.class).in(Singleton.class);
        bind(SelectionVisualizer.class).in(Singleton.class);
        bind(SelectionManager.class).in(Singleton.class);
        bind(SelectionVisualizerListener.class).in(Singleton.class);
        bind(RegionMovementTracker.class).in(Singleton.class);
        bind(RegionEntryNotifier.class).in(Singleton.class);
        bind(RegionPermissionRepository.class).to(SQLiteRegionPermissionRepository.class).in(Singleton.class);
        bind(RegionPermissionService.class).in(Singleton.class);
        bind(RegionRoleRepository.class).to(SQLiteRegionRoleRepository.class).in(Singleton.class);
        bind(MemberRegionRoleRepository.class).to(SQLiteMemberRegionRoleRepository.class).in(Singleton.class);
        bind(RegionTypeLimitRepository.class).to(SQLiteRegionTypeLimitRepository.class).in(Singleton.class);

        // Claim tracking bindings
        bind(ClaimMovementTracker.class).in(Singleton.class);
        bind(ClaimEntryNotifier.class).in(Singleton.class);

        // Auto-claim/unclaim system
        bind(AutoClaimManager.class).in(Singleton.class);
        bind(AutoClaimListener.class).in(Singleton.class);
        bind(AutoUnclaimListener.class).in(Singleton.class);

        // Claim logging system
        bind(ChunkClaimLogRepository.class).to(SQLiteChunkClaimLogRepository.class).in(Singleton.class);

        // Invite system
        bind(InviteRepository.class).to(SQLiteInviteRepository.class).in(Singleton.class);
        bind(InviteService.class).in(Singleton.class);

        // Services
        bind(GuildService.class).in(Singleton.class);
        bind(RelationshipService.class).in(Singleton.class);
        bind(GuildDefaultPermissionsService.class).in(Singleton.class);

        // New extracted services
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

        // Vault system
        bind(VaultRepository.class).to(SQLiteVaultRepository.class).in(Singleton.class);
        bind(VaultTransactionRepository.class).to(SQLiteVaultTransactionRepository.class).in(Singleton.class);
        bind(VaultService.class).in(Singleton.class);
        bind(VaultHandler.class).in(Singleton.class);
        bind(VaultGUIListener.class).in(Singleton.class);

        // Role creation wizard
        bind(RoleCreationGUIListener.class).in(Singleton.class);

        // Configuration
        bind(GuildsConfig.class).in(Singleton.class);

        // Progression system
        bind(GuildProgressionRepository.class).to(SQLiteGuildProgressionRepository.class).in(Singleton.class);
        bind(ProgressionService.class).in(Singleton.class);
        bind(ProgressionConfig.class).in(Singleton.class);
        bind(ProgressionXpListener.class).in(Singleton.class);
        bind(ProgressionPlaytimeTask.class).in(Singleton.class);

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
        bind(AllyComponent.class).in(Singleton.class);
        bind(EnemyComponent.class).in(Singleton.class);
        bind(NeutralComponent.class).in(Singleton.class);
        bind(GuildChatComponent.class).in(Singleton.class);
        bind(AllyChatComponent.class).in(Singleton.class);
        bind(AdminComponent.class).in(Singleton.class);
        bind(LevelComponent.class).in(Singleton.class);
        bind(LevelUpComponent.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    @Named("databasePath")
    String provideDatabasePath() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            boolean created = dataFolder.mkdirs();
            if (!created) {
                plugin.getLogger().warning("Failed to create plugin data folder: " + dataFolder.getAbsolutePath());
            }
        }
        File dbFile = new File(dataFolder, "guilds.db");
        // Use forward slashes for SQLite compatibility across platforms
        String dbPath = dbFile.getAbsolutePath().replace("\\", "/");
        plugin.getLogger().info("Database path: " + dbPath);
        return dbPath;
    }
}
