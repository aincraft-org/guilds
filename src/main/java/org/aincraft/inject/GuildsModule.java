package org.aincraft.inject;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.aincraft.GuildManager;
import org.aincraft.GuildService;
import org.aincraft.GuildsPlugin;
import org.aincraft.listeners.GuildProtectionListener;
import org.aincraft.storage.GuildRepository;
import org.aincraft.storage.GuildMemberRepository;
import org.aincraft.storage.GuildRoleRepository;
import org.aincraft.storage.MemberRoleRepository;
import org.aincraft.storage.ChunkClaimRepository;
import org.aincraft.storage.PlayerGuildMapping;
import org.aincraft.storage.SQLiteGuildRepository;
import org.aincraft.storage.SQLiteGuildMemberRepository;
import org.aincraft.storage.SQLiteGuildRoleRepository;
import org.aincraft.storage.SQLiteMemberRoleRepository;
import org.aincraft.storage.SQLiteChunkClaimRepository;
import org.aincraft.storage.SQLitePlayerGuildMapping;
import org.aincraft.subregion.SelectionManager;
import org.aincraft.subregion.SQLiteSubregionRepository;
import org.aincraft.subregion.SubregionRepository;
import org.aincraft.subregion.SubregionService;
import org.aincraft.subregion.SubregionTypeRegistry;
import org.aincraft.subregion.RegionMovementTracker;
import org.aincraft.subregion.RegionEntryNotifier;

import java.io.File;

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

        // Subregion bindings
        bind(SubregionRepository.class).to(SQLiteSubregionRepository.class).in(Singleton.class);
        bind(SubregionTypeRegistry.class).in(Singleton.class);
        bind(SubregionService.class).in(Singleton.class);
        bind(SelectionManager.class).in(Singleton.class);
        bind(RegionMovementTracker.class).in(Singleton.class);
        bind(RegionEntryNotifier.class).in(Singleton.class);

        // Services
        bind(GuildService.class).in(Singleton.class);
        bind(GuildManager.class).in(Singleton.class);

        // Listeners
        bind(GuildProtectionListener.class).in(Singleton.class);
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
