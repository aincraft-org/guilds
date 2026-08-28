package org.aincraft.guilds.gui;

import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Map.entry("guard_towers", new Coord(102, 32)),
        Map.entry("healing_aura", new Coord(106, 52)),
        Map.entry("turret_system", new Coord(118, 64)),

        // Commerce (SW Quadrant)
        Map.entry("market_stall", new Coord(42, 84)),
        Map.entry("bulk_trading", new Coord(22, 76)),
        Map.entry("merchant_caravan", new Coord(30, 100)),
        Map.entry("trade_empire", new Coord(50, 114)),

        // Culture (SE Quadrant)
        Map.entry("heritage_monument", new Coord(86, 84)),
        Map.entry("grand_library", new Coord(106, 76)),
        Map.entry("festival_grounds", new Coord(98, 100)),
        Map.entry("cultural_nexus", new Coord(78, 114))
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

        // 1. Inject Synthetic Guild Hearth
        result.put(HEARTH_ID, new LayoutNode(
                HEARTH_ID, "Guild Hearth", null, ShapeType.CORE, 64, 64, null
        ));

        // 2. Map Dynamic Nodes
        for (TechTreeNode node : nodes) {
            Coord c = RADIAL_COORDS.getOrDefault(node.getId(), new Coord(64, 64));
            ShapeType shape = shapeForBranch(node.getBranch());
            result.put(node.getId(), new LayoutNode(
                    node.getId(), node.getName(), node.getBranch(), shape, c.x(), c.y(), node
            ));
        }

        return Collections.unmodifiableMap(result);
    }

    public static List<SplineEdge> edges(Collection<TechTreeNode> nodes) {
        List<SplineEdge> result = new ArrayList<>();
        for (TechTreeNode node : nodes) {
            List<String> prereqs = node.getEffectivePrerequisites();
            if (prereqs.isEmpty()) {
                result.add(new SplineEdge(HEARTH_ID, node.getId()));
            } else {
                for (String p : prereqs) {
                    result.add(new SplineEdge(p, node.getId()));
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
