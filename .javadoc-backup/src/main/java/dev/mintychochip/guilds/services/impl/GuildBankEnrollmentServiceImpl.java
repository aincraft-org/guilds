package dev.mintychochip.guilds.services.impl;

import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.GuildBankEnrollmentService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.sql.NamedSql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

/** PostgreSQL-backed guild-bank enrollment repository. */
public class GuildBankEnrollmentServiceImpl implements GuildBankEnrollmentService {
    /** The sql constant. */
    private static final NamedSql SQL = NamedSql.guilds();

    /** The database manager. */
    private final DatabaseManager databaseManager;
    /** The guild service. */
    private final GuildService guildService;
    /** The resident service. */
    private final ResidentService residentService;
    /** The logger. */
    private final Logger logger;

    /**
     * Creates a new guild bank enrollment service impl instance.
     * @param databaseManager the database manager
     * @param guildService the guild service
     * @param residentService the resident service
     * @param logger the logger
     */
    public GuildBankEnrollmentServiceImpl(DatabaseManager databaseManager, GuildService guildService,
                                          ResidentService residentService, Logger logger) {
        this.databaseManager = databaseManager;
        this.guildService = guildService;
        this.residentService = residentService;
        this.logger = logger;
    }

    /**
     * Performs the open operation.
     * @param playerUuid the player uuid
     * @param guildId the guild id
     * @return the result
     */
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
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = SQL.prepare(connection, "bank/open-enrollment.sql", Map.of(
                         "guild_id", guild.getId(),
                         "player_uuid", playerUuid.toString(),
                         "now", now))) {
                int updated = statement.executeUpdate();
                return updated == 1 ? EnrollmentResult.OPENED : EnrollmentResult.NOT_CURRENT_MEMBER;
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to open guild-bank enrollment", e);
                return EnrollmentResult.FAILED;
            }
        });
    }

    /**
     * Returns whether enrolled.
     * @param playerUuid the player uuid
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public CompletionStage<Boolean> isEnrolled(UUID playerUuid, String guildId) {
        return CompletableFuture.supplyAsync(() -> {
            if (playerUuid == null || guildId == null) return false;
            Guild guild = guildService.getGuildById(guildId).orElse(null);
            if (guild == null || !guild.getResidents().contains(playerUuid)) return false;
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = SQL.prepare(connection, "bank/select-active.sql", Map.of(
                         "guild_id", guild.getId(),
                         "player_uuid", playerUuid.toString()))) {
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() && result.getBoolean("active");
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to check guild-bank enrollment", e);
                return false;
            }
        });
    }

    /**
     * Performs the deactivate for player guild operation.
     * @param playerUuid the player uuid
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public CompletionStage<Boolean> deactivateForPlayerGuild(UUID playerUuid, String guildId) {
        return CompletableFuture.supplyAsync(() -> updateActive(playerUuid, guildId));
    }

    /**
     * Performs the deactivate for guild operation.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public CompletionStage<Integer> deactivateForGuild(String guildId) {
        return CompletableFuture.supplyAsync(() -> {
            if (guildId == null) return 0;
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = SQL.prepare(connection, "bank/deactivate-guild.sql", Map.of(
                         "updated_at", LocalDateTime.now().toString(),
                         "guild_id", guildId))) {
                return statement.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to deactivate guild-bank enrollments", e);
                return 0;
            }
        });
    }

    /**
     * Updates the active.
     * @param playerUuid the player uuid
     * @param guildId the guild id
     * @return the result
     */
    private boolean updateActive(UUID playerUuid, String guildId) {
        if (playerUuid == null || guildId == null) return false;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "bank/deactivate-player.sql", Map.of(
                     "updated_at", LocalDateTime.now().toString(),
                     "guild_id", guildId,
                     "player_uuid", playerUuid.toString()))) {
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to deactivate guild-bank enrollment", e);
            return false;
        }
    }
}
