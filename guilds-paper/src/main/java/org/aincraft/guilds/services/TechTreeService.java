package org.aincraft.guilds.services;

import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.territory.model.FastTravelMode;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for the tech tree system.
 */
public interface TechTreeService {

    /**
     * Sync tech tree node definitions from config to the database.
     */
    void syncConfigToDatabase();

    /**
     * Check if a tech node is unlocked for a guild.
     */
    boolean isTechNodeUnlocked(Guild guild, String nodeId);

    /**
     * Check whether a guild has unlocked a capability node.
     */
    boolean hasCapability(Guild guild, String nodeId);

    /**
     * Read a numeric effect from an unlocked node, returning zero when the node
     * or effect is absent or non-numeric.
     */
    double getNumericEffect(Guild guild, String nodeId, String effectKey);

    /**
     * Get the existing cooldown reduction. It is deliberately scoped to
     * WAYSTONE; other travel modes do not inherit the fast-travel effect.
     */
    double cooldownReduction(Guild guild, FastTravelMode mode);

    /**
     * Unlock a tech node for a guild (deducts tech points, applies effects).
     * @return true if unlock succeeded
     */
    boolean unlockTechNode(Guild guild, String nodeId);

    /**
     * Check if a guild can unlock a given node (prerequisites met + enough tech points).
     */
    boolean canUnlockNode(Guild guild, String nodeId);

    /**
     * Get all nodes whose prerequisites are met for a guild but are not yet unlocked.
     */
    List<TechTreeNode> getAvailableNodes(Guild guild);

    /**
     * Get all tech node definitions.
     */
    List<TechTreeNode> getAllNodes();

    /**
     * Get nodes filtered by branch.
     */
    List<TechTreeNode> getNodesByBranch(TechTreeBranch branch);

    /**
     * Get a single node by ID.
     */
    Optional<TechTreeNode> getNode(String nodeId);

    /**
     * Load a guild's unlocked tech data from the database.
     */
    void loadGuildTechData(Guild guild);

    /**
     * Save a guild's unlocked tech data to the database.
     */
    void saveGuildTechData(Guild guild);

    /**
     * Reload node definitions from config.
     */
    void reloadConfig();
}
