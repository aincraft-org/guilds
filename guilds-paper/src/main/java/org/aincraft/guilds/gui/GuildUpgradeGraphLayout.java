package org.aincraft.guilds.gui;

import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GuildUpgradeGraphLayout {

    public static final String HEARTH_ID = "guild_hearth";

    public enum ShapeType {
        CORE, HEXAGON, SHIELD, COIN, DIAMOND
    }

    public record LayoutNode(
            String id,
            String name,
            TechTreeBranch branch,
            ShapeType shape,
            int x,
            int y,
            TechTreeNode rawNode
    ) {}

    public record SplineEdge(String fromId, String toId) {}

    private record Coord(int x, int y) {}

    private static final Map<String, Coord> RADIAL_COORDS = Map.ofEntries(
        // Center
        Map.entry(HEARTH_ID, new Coord(64, 64)),

        // Infrastructure (NW Quadrant)
        Map.entry("better_storage", new Coord(42, 44)),
        Map.entry("fast_travel", new Coord(26, 32)),
        Map.entry("advanced_farming", new Coord(22, 52)),
        Map.entry("auto_sorter", new Coord(10, 64)),

        // Defense (NE Quadrant)
        Map.entry("reinforced_walls", new Coord(86, 44)),
        Map.entry("guard_posts", new Coord(102, 32)),
        Map.entry("siege_shields", new Coord(106, 52)),
        Map.entry("fortification", new Coord(118, 64)),

        // Commerce (SW Quadrant)
        Map.entry("marketplace", new Coord(42, 84)),
        Map.entry("trade_routes", new Coord(22, 76)),
        Map.entry("tax_optimization", new Coord(30, 100)),
        Map.entry("merchant_guild", new Coord(50, 114)),

        // Culture (SE Quadrant)
        Map.entry("guild_banner", new Coord(86, 84)),
        Map.entry("broadcast_tower", new Coord(106, 76)),
        Map.entry("guild_hall", new Coord(98, 100)),
        Map.entry("monument", new Coord(78, 114))
    );

    private GuildUpgradeGraphLayout() {}

    public static ShapeType shapeForBranch(TechTreeBranch branch) {
        if (branch == null) return ShapeType.CORE;
        return switch (branch) {
            case INFRASTRUCTURE -> ShapeType.HEXAGON;
            case DEFENSE -> ShapeType.SHIELD;
            case COMMERCE -> ShapeType.COIN;
            case CULTURE -> ShapeType.DIAMOND;
        };
    }

    public static Map<String, LayoutNode> layout(Collection<TechTreeNode> nodes) {
        Map<String, LayoutNode> result = new LinkedHashMap<>();
        result.put(HEARTH_ID, new LayoutNode(
                HEARTH_ID, "Guild Hearth", null, ShapeType.CORE, 64, 64, null
        ));

        Set<Coord> occupied = new HashSet<>();
        occupied.add(RADIAL_COORDS.get(HEARTH_ID));
        for (TechTreeNode node : nodes) {
            Coord coordinate = RADIAL_COORDS.get(node.getId());
            if (coordinate != null) {
                occupied.add(coordinate);
            }
        }

        List<TechTreeNode> fallbackNodes = new ArrayList<>();
        for (TechTreeNode node : nodes) {
            if (!RADIAL_COORDS.containsKey(node.getId())) {
                fallbackNodes.add(node);
            }
        }
        fallbackNodes.sort(Comparator.comparing(TechTreeNode::getId));

        Map<String, Coord> fallbackCoordinates = new HashMap<>();
        Map<TechTreeBranch, Integer> branchIndexes = new HashMap<>();
        for (TechTreeNode node : fallbackNodes) {
            int index = branchIndexes.getOrDefault(node.getBranch(), 0);
            Coord coordinate = fallbackCoordinate(node, index, occupied);
            fallbackCoordinates.put(node.getId(), coordinate);
            occupied.add(coordinate);
            branchIndexes.put(node.getBranch(), index + 1);
        }

        for (TechTreeNode node : nodes) {
            Coord coordinate = RADIAL_COORDS.get(node.getId());
            if (coordinate == null) {
                coordinate = fallbackCoordinates.get(node.getId());
            }
            result.put(node.getId(), new LayoutNode(
                    node.getId(), node.getName(), node.getBranch(), shapeForBranch(node.getBranch()),
                    coordinate.x(), coordinate.y(), node
            ));
        }

        return Collections.unmodifiableMap(result);
    }

    private static Coord fallbackCoordinate(TechTreeNode node, int ordinal, Set<Coord> occupied) {
        int minX;
        int maxX;
        int minY;
        int maxY;
        switch (node.getBranch()) {
            case INFRASTRUCTURE -> {
                minX = 8;
                maxX = 38;
                minY = 14;
                maxY = 38;
            }
            case DEFENSE -> {
                minX = 90;
                maxX = 120;
                minY = 14;
                maxY = 38;
            }
            case COMMERCE -> {
                minX = 8;
                maxX = 38;
                minY = 90;
                maxY = 120;
            }
            case CULTURE -> {
                minX = 90;
                maxX = 120;
                minY = 90;
                maxY = 120;
            }
            case null, default -> {
                minX = 50;
                maxX = 78;
                minY = 14;
                maxY = 38;
            }
        }

        int columns = (maxX - minX) / 8 + 1;
        int rows = (maxY - minY) / 8 + 1;
        int capacity = columns * rows;
        int start = Math.floorMod(String.valueOf(node.getId()).hashCode() + ordinal, capacity);
        for (int offset = 0; offset < capacity; offset++) {
            int candidateIndex = (start + offset) % capacity;
            Coord candidate = new Coord(
                    minX + (candidateIndex % columns) * 8,
                    minY + (candidateIndex / columns) * 8
            );
            if (isHitboxAvailable(candidate, occupied)) {
                return candidate;
            }
        }

        for (int y = 14; y <= 118; y++) {
            for (int x = 8; x <= 120; x++) {
                Coord candidate = new Coord(x, y);
                if (isHitboxAvailable(candidate, occupied)) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("Tech tree layout canvas capacity exceeded");
    }

    private static boolean isHitboxAvailable(Coord candidate, Set<Coord> occupied) {
        for (Coord existing : occupied) {
            if (Math.abs(candidate.x() - existing.x()) <= 6
                    && Math.abs(candidate.y() - existing.y()) <= 6) {
                return false;
            }
        }
        return true;
    }
    public static List<SplineEdge> edges(Collection<TechTreeNode> nodes) {
        Set<String> knownIds = new HashSet<>();
        knownIds.add(HEARTH_ID);
        for (TechTreeNode node : nodes) {
            knownIds.add(node.getId());
        }

        List<SplineEdge> result = new ArrayList<>();
        for (TechTreeNode node : nodes) {
            List<String> prereqs = node.getEffectivePrerequisites();
            if (prereqs.isEmpty()) {
                result.add(new SplineEdge(HEARTH_ID, node.getId()));
            } else {
                for (String prerequisite : prereqs) {
                    if (knownIds.contains(prerequisite) && knownIds.contains(node.getId())) {
                        result.add(new SplineEdge(prerequisite, node.getId()));
                    }
                }
            }
        }
        return result;
    }

    public static LayoutNode findNodeAt(Map<String, LayoutNode> layout, int x, int y) {
        if (layout == null) return null;
        for (LayoutNode node : layout.values()) {
            if (Math.abs(x - node.x()) <= 6 && Math.abs(y - node.y()) <= 6) {
                return node;
            }
        }
        return null;
    }
}
