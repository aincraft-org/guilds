package org.aincraft.guilds.models;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates guild tech tree data and logic.
 * Tracks which tech nodes a guild has unlocked and when.
 */
public class GuildTechData {

    private final Set<String> unlockedNodeIds;
    private final Map<String, LocalDateTime> unlockTimestamps;

    public GuildTechData() {
        this.unlockedNodeIds = new HashSet<>();
        this.unlockTimestamps = new HashMap<>();
    }

    public GuildTechData(Set<String> unlockedNodeIds, Map<String, LocalDateTime> unlockTimestamps) {
        this.unlockedNodeIds = unlockedNodeIds != null ? new HashSet<>(unlockedNodeIds) : new HashSet<>();
        this.unlockTimestamps = unlockTimestamps != null ? new HashMap<>(unlockTimestamps) : new HashMap<>();
    }

    /**
     * Check if a tech node is unlocked
     */
    public boolean isNodeUnlocked(String nodeId) {
        return unlockedNodeIds.contains(nodeId);
    }

    /**
     * Unlock a tech node with the current timestamp
     */
    public void unlockNode(String nodeId) {
        unlockNode(nodeId, LocalDateTime.now());
    }

    /**
     * Unlock a tech node with a specific timestamp
     */
    public void unlockNode(String nodeId, LocalDateTime timestamp) {
        if (nodeId != null && !unlockedNodeIds.contains(nodeId)) {
            unlockedNodeIds.add(nodeId);
            unlockTimestamps.put(nodeId, timestamp);
        }
    }

    /**
     * Get all unlocked tech node IDs
     */
    public Set<String> getUnlockedNodeIds() {
        return Collections.unmodifiableSet(unlockedNodeIds);
    }

    /**
     * Get total number of unlocked tech nodes
     */
    public int getTotalUnlockedNodes() {
        return unlockedNodeIds.size();
    }

    /**
     * Get the timestamp when a node was unlocked
     */
    public LocalDateTime getUnlockTimestamp(String nodeId) {
        return unlockTimestamps.get(nodeId);
    }

    /**
     * Get a copy of all unlock timestamps
     */
    public Map<String, LocalDateTime> getUnlockTimestamps() {
        return new HashMap<>(unlockTimestamps);
    }
}
