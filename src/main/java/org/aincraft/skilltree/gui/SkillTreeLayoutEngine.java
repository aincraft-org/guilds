package org.aincraft.skilltree.gui;

import org.aincraft.skilltree.SkillDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DAG layout engine for positioning skills in a chest GUI using topological sorting
 * and layering (Sugiyama framework approach).
 *
 * Analyzes skill prerequisites to build a directed acyclic graph (DAG), assigns
 * layers based on dependency depth, and distributes nodes across 9 columns with
 * minimum spacing to fit within a 6-row chest inventory.
 *
 * Single Responsibility: Skill tree graph layout calculation.
 */
public class SkillTreeLayoutEngine {
    private static final int COLUMNS = 9;
    private static final int ROWS = 6;
    private static final int MIN_COLUMN_GAP = 1;
    private static final int MIN_ROW_GAP = 0; // Skills in same layer share row

    private final Map<String, SkillDefinition> skillMap;
    private final Map<String, Set<String>> dependents; // skill -> skills that depend on it

    /**
     * Creates a layout engine for the given skills.
     *
     * @param skills collection of skill definitions
     * @throws IllegalArgumentException if skills is null or contains null
     */
    public SkillTreeLayoutEngine(Collection<SkillDefinition> skills) {
        Objects.requireNonNull(skills, "Skills collection cannot be null");

        this.skillMap = new HashMap<>();
        this.dependents = new HashMap<>();

        // Build skill map and initialize dependents
        for (SkillDefinition skill : skills) {
            Objects.requireNonNull(skill, "Skill definition cannot be null");
            skillMap.put(skill.id(), skill);
            dependents.put(skill.id(), new HashSet<>());
        }

        // Build reverse edges (dependents)
        for (SkillDefinition skill : skills) {
            for (String prereqId : skill.prerequisites()) {
                if (skillMap.containsKey(prereqId)) {
                    dependents.get(prereqId).add(skill.id());
                }
            }
        }
    }

    /**
     * Calculates layout positions for all skills.
     * Returns a map of skill ID to node position with minimal edge crossings.
     *
     * @return map of skill ID to position (row, column)
     */
    public Map<String, SkillNodePosition> calculateLayout() {
        // Phase 1: Assign layers (topological levels)
        Map<String, Integer> layers = assignLayers();

        // Phase 2: Group skills by layer
        Map<Integer, List<String>> layerGroups = groupByLayer(layers);

        // Phase 3: Assign columns within each layer
        Map<String, Integer> columnAssignments = assignColumns(layerGroups);

        // Phase 4: Convert to positions
        Map<String, SkillNodePosition> positions = new HashMap<>();
        for (String skillId : skillMap.keySet()) {
            int row = layers.get(skillId);
            int column = columnAssignments.getOrDefault(skillId, 0);

            // Clamp to valid bounds
            row = Math.min(row, ROWS - 1);
            column = Math.min(column, COLUMNS - 1);

            positions.put(skillId, new SkillNodePosition(skillId, row, column));
        }

        return positions;
    }

    /**
     * Assigns layer numbers to each skill using longest path method.
     * Skills with no prerequisites are in layer 0. Dependents are placed
     * in the layer number equal to their deepest prerequisite's layer + 1.
     *
     * @return map of skill ID to layer number
     */
    private Map<String, Integer> assignLayers() {
        Map<String, Integer> layers = new HashMap<>();
        Set<String> visited = new HashSet<>();

        for (String skillId : skillMap.keySet()) {
            if (!visited.contains(skillId)) {
                computeLayer(skillId, layers, visited);
            }
        }

        return layers;
    }

    /**
     * Computes the layer for a skill using depth-first traversal.
     * Layer = max(layers of prerequisites) + 1, or 0 if no prerequisites.
     *
     * @param skillId the skill ID to compute
     * @param layers map to store results
     * @param visited set of already computed skills
     * @return the layer number
     */
    private int computeLayer(String skillId, Map<String, Integer> layers, Set<String> visited) {
        if (layers.containsKey(skillId)) {
            return layers.get(skillId);
        }

        SkillDefinition skill = skillMap.get(skillId);
        if (skill == null || skill.prerequisites().isEmpty()) {
            layers.put(skillId, 0);
            visited.add(skillId);
            return 0;
        }

        int maxPrereqLayer = 0;
        for (String prereqId : skill.prerequisites()) {
            if (skillMap.containsKey(prereqId)) {
                int prereqLayer = computeLayer(prereqId, layers, visited);
                maxPrereqLayer = Math.max(maxPrereqLayer, prereqLayer);
            }
        }

        int layer = maxPrereqLayer + 1;
        layers.put(skillId, layer);
        visited.add(skillId);
        return layer;
    }

    /**
     * Groups skills by their assigned layer.
     *
     * @param layers map of skill ID to layer number
     * @return map of layer number to sorted skill IDs
     */
    private Map<Integer, List<String>> groupByLayer(Map<String, Integer> layers) {
        Map<Integer, List<String>> groups = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry : layers.entrySet()) {
            String skillId = entry.getKey();
            int layer = entry.getValue();

            groups.computeIfAbsent(layer, k -> new ArrayList<>()).add(skillId);
        }

        // Sort skills within each layer for consistent positioning
        for (List<String> group : groups.values()) {
            group.sort(Comparator.naturalOrder());
        }

        return groups;
    }

    /**
     * Assigns column positions to skills within their layers.
     * Spreads skills across the 9-column width with minimum spacing.
     * Center-aligns skills within each layer.
     *
     * @param layerGroups map of layer to skill IDs
     * @return map of skill ID to column assignment
     */
    private Map<String, Integer> assignColumns(Map<Integer, List<String>> layerGroups) {
        Map<String, Integer> columns = new HashMap<>();

        for (List<String> layer : layerGroups.values()) {
            if (layer.isEmpty()) {
                continue;
            }

            // Calculate spacing for this layer
            int skillCount = layer.size();
            List<Integer> positions = distributePositions(skillCount, COLUMNS);

            for (int i = 0; i < layer.size(); i++) {
                columns.put(layer.get(i), positions.get(i));
            }
        }

        return columns;
    }

    /**
     * Distributes skill positions across columns with equal spacing.
     * Centers the distribution when possible.
     *
     * @param count number of skills to position
     * @param maxColumns available columns
     * @return list of column indices in order
     */
    private List<Integer> distributePositions(int count, int maxColumns) {
        List<Integer> positions = new ArrayList<>();

        if (count == 0) {
            return positions;
        }

        if (count >= maxColumns) {
            // All columns used
            for (int i = 0; i < maxColumns; i++) {
                positions.add(i);
            }
            return positions;
        }

        // Calculate spacing
        int availableSpace = maxColumns - count;
        int gapSize = availableSpace / (count + 1);
        int extraGaps = availableSpace % (count + 1);

        int col = 0;
        for (int i = 0; i < count; i++) {
            col += gapSize + (i < extraGaps ? 1 : 0);
            positions.add(col);
            col++;
        }

        return positions;
    }

    /**
     * Gets maximum layer number (for determining GUI height needed).
     *
     * @return maximum layer or -1 if no skills
     */
    public int getMaxLayer() {
        if (skillMap.isEmpty()) {
            return -1;
        }

        Map<String, Integer> layers = assignLayers();
        return layers.values().stream().max(Comparator.naturalOrder()).orElse(0);
    }
}
