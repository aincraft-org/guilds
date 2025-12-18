package org.aincraft.project.llm;

import org.aincraft.project.BuffType;

import java.util.List;
import java.util.Optional;

/**
 * Repository for caching and retrieving LLM-generated project texts.
 * Supports batch saving and retrieval operations.
 */
public interface LLMProjectTextRepository {
    /**
     * Retrieves a random cached project text for a buff type.
     *
     * @param buffType the buff type to query
     * @return optional containing a random project text, or empty if no cache exists
     */
    Optional<ProjectText> getRandomCachedText(BuffType buffType);

    /**
     * Gets the number of cached entries for a buff type.
     *
     * @param buffType the buff type to query
     * @return number of cached texts for this buff type
     */
    int getCacheSize(BuffType buffType);

    /**
     * Saves multiple project texts to cache.
     *
     * @param buffType the buff type these texts are for
     * @param texts list of project texts to save
     */
    void saveAll(BuffType buffType, List<ProjectText> texts);

    /**
     * Deletes old entries, keeping only the most recent ones.
     * Useful for cache maintenance to prevent unbounded growth.
     *
     * @param buffType the buff type to clean
     * @param keepCount number of most recent entries to keep
     */
    void deleteOldEntries(BuffType buffType, int keepCount);
}
