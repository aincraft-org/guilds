package org.aincraft.guilds.services;

import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.models.Town;

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
     * Check if a tech node is unlocked for a town.
     */
    boolean isTechNodeUnlocked(Town town, String nodeId);

    /**
     * Unlock a tech node for a town (deducts tech points, applies effects).
     * @return true if unlock succeeded
     */
    boolean unlockTechNode(Town town, String nodeId);

    /**
     * Check if a town can unlock a given node (prerequisites met + enough tech points).
     */
    boolean canUnlockNode(Town town, String nodeId);

    /**
     * Get all nodes whose prerequisites are met for a town but are not yet unlocked.
     */
    List<TechTreeNode> getAvailableNodes(Town town);

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
     * Load a town's unlocked tech data from the database.
     */
    void loadTownTechData(Town town);

    /**
     * Save a town's unlocked tech data to the database.
     */
    void saveTownTechData(Town town);

    /**
     * Reload node definitions from config.
     */
    void reloadConfig();
}
