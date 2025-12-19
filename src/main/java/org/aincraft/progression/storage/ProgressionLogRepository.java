package org.aincraft.progression.storage;

import org.aincraft.progression.ProgressionLog;

import java.util.List;
import java.util.UUID;

/**
 * Repository for managing progression log entries.
 */
public interface ProgressionLogRepository {
    /**
     * Logs a progression event.
     *
     * @param entry the log entry to save
     */
    void log(ProgressionLog entry);

    /**
     * Finds recent progression log entries for a guild.
     *
     * @param guildId the guild ID
     * @param limit maximum number of entries to return
     * @return list of log entries, newest first
     */
    List<ProgressionLog> findByGuild(UUID guildId, int limit);

    /**
     * Deletes all log entries for a guild.
     * Called when a guild is deleted.
     *
     * @param guildId the guild ID
     */
    void deleteByGuildId(UUID guildId);
}
