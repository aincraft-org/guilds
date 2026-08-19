package org.aincraft.guilds.services.impl;

import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.territory.persist.SqlSupport;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.GuildBankEnrollmentService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

/** PostgreSQL-backed guild-bank enrollment repository. */
public class GuildBankEnrollmentServiceImpl implements GuildBankEnrollmentService {
    private final DatabaseManager databaseManager;
    private final GuildService guildService;
    private final ResidentService residentService;
    private final Logger logger;

    public GuildBankEnrollmentServiceImpl(DatabaseManager databaseManager, GuildService guildService,
                                          ResidentService residentService, Logger logger) {
        this.databaseManager = databaseManager;
        this.guildService = guildService;
        this.residentService = residentService;
        this.logger = logger;
    }

    @Override
    public CompletionStage<EnrollmentResult> open(UUID playerUuid, String guildId) {
        return CompletableFuture.supplyAsync(() -> {
            if (playerUuid == null || guildId == null || guildId.isBlank()) {
                return EnrollmentResult.NOT_CURRENT_MEMBER;
            }
            Guild guild = guildService.getGuildById(guildId).orElse(null);
            if (guild == null) return EnrollmentResult.GUILD_NOT_FOUND;
            if (!residentService.residentExists(playerUuid)) return EnrollmentResult.PLAYER_NOT_FOUND;
            if (!guild.getResidents().contains(playerUuid)) return EnrollmentResult.NOT_CURRENT_MEMBER;
            String now = LocalDateTime.now().toString();
            try (Connection connection = databaseManager.getConnection()) {
                String sql = SqlSupport.upsertSql(connection, """
                INSERT INTO guild_bank_enrollments (guild_id, player_uuid, active, enrolled_at, updated_at)
                VALUES (?, ?, TRUE, ?, ?)
                """, "guild_id, player_uuid", "active = TRUE, updated_at = EXCLUDED.updated_at");
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, guild.getId());
                    statement.setString(2, playerUuid.toString());
                    statement.setString(3, now);
                    statement.setString(4, now);
                    int updated = statement.executeUpdate();
                    return updated > 0 ? EnrollmentResult.OPENED : EnrollmentResult.NOT_CURRENT_MEMBER;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to open guild-bank enrollment", e);
                return EnrollmentResult.FAILED;
            }
        });
    }

    @Override
    public CompletionStage<Boolean> isEnrolled(UUID playerUuid, String guildId) {
        return CompletableFuture.supplyAsync(() -> {
            if (playerUuid == null || guildId == null) return false;
            Guild guild = guildService.getGuildById(guildId).orElse(null);
            if (guild == null || !guild.getResidents().contains(playerUuid)) return false;
            String sql = "SELECT active FROM guild_bank_enrollments WHERE guild_id = ? AND player_uuid = ?";
            try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, guild.getId());
                statement.setString(2, playerUuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() && result.getBoolean("active");
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to check guild-bank enrollment", e);
                return false;
            }
        });
    }

    @Override
    public CompletionStage<Boolean> deactivateForPlayerGuild(UUID playerUuid, String guildId) {
        return CompletableFuture.supplyAsync(() -> updateActive(playerUuid, guildId));
    }

    @Override
    public CompletionStage<Integer> deactivateForGuild(String guildId) {
        return CompletableFuture.supplyAsync(() -> {
            if (guildId == null) return 0;
            String sql = "UPDATE guild_bank_enrollments SET active = FALSE, updated_at = ? WHERE guild_id = ? AND active = TRUE";
            try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, LocalDateTime.now().toString());
                statement.setString(2, guildId);
                return statement.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to deactivate guild-bank enrollments", e);
                return 0;
            }
        });
    }

    private boolean updateActive(UUID playerUuid, String guildId) {
        if (playerUuid == null || guildId == null) return false;
        String sql = "UPDATE guild_bank_enrollments SET active = FALSE, updated_at = ? WHERE guild_id = ? AND player_uuid = ? AND active = TRUE";
        try (Connection connection = databaseManager.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, LocalDateTime.now().toString());
            statement.setString(2, guildId);
            statement.setString(3, playerUuid.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to deactivate guild-bank enrollment", e);
            return false;
        }
    }
}
