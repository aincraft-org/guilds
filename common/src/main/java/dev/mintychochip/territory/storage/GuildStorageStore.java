package dev.mintychochip.territory.storage;

import java.io.IOException;
import java.util.Optional;

/** Durable guild item-bank documents. */
public interface GuildStorageStore {
    /**
     * Loads the bank for a guild.
     *
     * @param guildId owning guild
     * @return the stored document, or empty if none exists
     * @throws IOException if the store cannot be read
     */
    Optional<GuildStorageDocument> load(String guildId) throws IOException;

    /**
     * Writes the bank for a guild.
     *
     * @param document bank contents
     * @throws IOException if the store cannot be written
     */
    void save(GuildStorageDocument document) throws IOException;
}
