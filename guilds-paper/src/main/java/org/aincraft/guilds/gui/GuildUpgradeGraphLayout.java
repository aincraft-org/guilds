package org.aincraft.guilds.gui;

import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure viewport-relative graph geometry for the guild upgrade MapGUI.
 *
 * <p>Nodes are grouped into one lane per {@link TechTreeBranch} (in enum order) instead of using
 * raw {@code position-x}/{@code position-y} directly. This keeps every branch the same width on
 * screen regardless of how wide its sub-tree happens to be in {@code techtree.yml}, producing an
 * even 4-column grid rather than the lopsided spacing raw yml coordinates would otherwise produce.
 */
final class GuildUpgradeGraphLayout {

    static final int CELL = 14;
    /** Reserved above the grid for branch column headers. */
    static final int TOP_MARGIN = 20;
    /** Reserved below the grid for the footer and edge-scroll breathing room. */
    static final int BOTTOM_MARGIN = 20;

    private GuildUpgradeGraphLayout() {
    }

    /** A node's position on the branch-lane grid, in cell units. */
    record Slot(int col, int row) {
    }

    record Origin(int x, int y) {
    }

    /** One directed prerequisite -> dependent link to be drawn as a graph edge. */
    record Edge(String fromId, String toId) {
    }

    /**
     * Every prerequisite edge across the whole tree — one {@link Edge} per prerequisite of
     * every node, not just each node's first/primary parent. A node with N prerequisites
     * contributes N edges. Prerequisite ids that don't resolve to a node actually present in
     * {@code nodes} are silently skipped (matches yml data that references a removed/renamed
     * node).
     *
     * <p>Uses {@link TechTreeNode#getEffectivePrerequisites()} rather than the raw
     * {@code prerequisites} list, so a node configured with only a singleton {@code parent:}
     * field (no explicit {@code prerequisites:} list) still gets its edge drawn.
     */
    static List<Edge> edges(Collection<TechTreeNode> nodes) {
        Set<String> knownIds = new HashSet<>();
        for (TechTreeNode n : nodes) {
            knownIds.add(n.getId());
        }
        List<Edge> result = new ArrayList<>();
        for (TechTreeNode node : nodes) {
            for (String prereqId : node.getEffectivePrerequisites()) {
                if (knownIds.contains(prereqId)) {
                    result.add(new Edge(prereqId, node.getId()));
                }
            }
        }
        return result;
    }

    /**
     * Cells needed per branch lane so that no branch's sub-tree can ever overflow into the
     * next branch's lane: the widest per-branch relative-x span ({@code maxX - minX + 1}) across
     * every branch present in {@code nodes}, with a floor of 1. Every lane uses this same width
     * (rather than each branch's own, possibly narrower, span) so the grid stays an even
     * N-column layout with uniformly spaced headers, per the class doc — a single wide branch
     * just widens every lane equally instead of colliding with its neighbor.
     */
    static int laneCells(Collection<TechTreeNode> nodes) {
        int widest = 1;
        for (TechTreeBranch branch : TechTreeBranch.values()) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            for (TechTreeNode n : nodes) {
                if (n.getBranch() != branch) continue;
                minX = Math.min(minX, n.getPositionX());
                maxX = Math.max(maxX, n.getPositionX());
            }
            if (minX == Integer.MAX_VALUE) continue;
            widest = Math.max(widest, maxX - minX + 1);
        }
        return widest;
    }

    /** Assigns every node a {@link Slot}, one lane per branch, normalized within that lane. */
    static Map<String, Slot> slots(Collection<TechTreeNode> nodes) {
        int laneCells = laneCells(nodes);
        Map<String, Slot> result = new HashMap<>();
        TechTreeBranch[] branches = TechTreeBranch.values();
        for (int b = 0; b < branches.length; b++) {
            TechTreeBranch branch = branches[b];
            int minX = Integer.MAX_VALUE;
            for (TechTreeNode n : nodes) {
                if (n.getBranch() == branch) minX = Math.min(minX, n.getPositionX());
            }
            if (minX == Integer.MAX_VALUE) continue;
            for (TechTreeNode n : nodes) {
                if (n.getBranch() != branch) continue;
                int col = b * laneCells + (n.getPositionX() - minX);
                result.put(n.getId(), new Slot(col, n.getPositionY()));
            }
        }
        return result;
    }

    /** Column index (in cells) where a branch's lane starts, given the lane width from {@link #laneCells}. */
    static int laneStart(TechTreeBranch branch, int laneCells) {
        TechTreeBranch[] branches = TechTreeBranch.values();
        for (int i = 0; i < branches.length; i++) {
            if (branches[i] == branch) return i * laneCells;
        }
        return 0;
    }

    static Origin origin(int panX, int panY, int width, int height, Map<String, Slot> slots) {
        int maxCol = 0;
        int maxRow = 0;
        for (Slot slot : slots.values()) {
            maxCol = Math.max(maxCol, slot.col());
            maxRow = Math.max(maxRow, slot.row());
        }
        int graphWidth = Math.max(CELL, (maxCol + 1) * CELL);
        int graphHeight = Math.max(CELL, (maxRow + 1) * CELL);
        int usableHeight = Math.max(CELL, height - TOP_MARGIN - BOTTOM_MARGIN);
        int baseX = (width - graphWidth) / 2;
        int baseY = TOP_MARGIN + Math.max(0, (usableHeight - graphHeight) / 2);
        return new Origin(baseX + panX, baseY + panY);
    }

    static int nodeX(Origin origin, Slot slot) {
        return origin.x() + slot.col() * CELL;
    }

    static int nodeY(Origin origin, Slot slot) {
        return origin.y() + slot.row() * CELL;
    }

    static int clampPan(int value, int maxPan) {
        int limit = Math.max(0, maxPan);
        return Math.max(-limit, Math.min(limit, value));
    }

    /**
     * Number of grid rows spanned by the given slots.
     */
    static int maxRow(Map<String, Slot> slots) {
        int maxRow = 0;
        for (Slot slot : slots.values()) {
            maxRow = Math.max(maxRow, slot.row());
        }
        return maxRow;
    }

    /**
     * Vertical extent of the grid in pixels: (rows + 1) * CELL.
     */
    static int graphHeightPx(Map<String, Slot> slots) {
        return (maxRow(slots) + 1) * CELL;
    }

    /**
     * Horizontal extent of the grid in pixels: (columns + 1) * CELL.
     */
    static int graphWidthPx(Map<String, Slot> slots) {
        int maxCol = 0;
        for (Slot slot : slots.values()) {
            maxCol = Math.max(maxCol, slot.col());
        }
        return (maxCol + 1) * CELL;
    }

    /**
     * True when the grid is wider than the canvas and horizontal panning can reveal more content.
     */
    static boolean horizontalOverflow(Map<String, Slot> slots, int width) {
        return graphWidthPx(slots) > Math.max(CELL, width);
    }

    /**
     * Symmetric horizontal pan travel available when the grid overflows; 0 when it fits.
     */
    static int horizontalPanMaxPx(Map<String, Slot> slots, int width) {
        int usableWidth = Math.max(CELL, width);
        return Math.max(0, (graphWidthPx(slots) - usableWidth + 1) / 2);
    }

    /**
     * True when the grid is taller than the usable viewport (canvas height minus the
     * reserved top/bottom margins) — i.e. when a right-edge vertical scrollbar is
     * actually needed.
     */
    static boolean verticalOverflow(Map<String, Slot> slots, int height) {
        int usableHeight = Math.max(CELL, height - TOP_MARGIN - BOTTOM_MARGIN);
        return graphHeightPx(slots) > usableHeight;
    }

    /**
     * Symmetric pan travel (in pixels) available when the grid overflows; 0 when it fits.
     */
    static int verticalPanMaxPx(Map<String, Slot> slots, int height) {
        int usableHeight = Math.max(CELL, height - TOP_MARGIN - BOTTOM_MARGIN);
        return Math.max(0, (graphHeightPx(slots) - usableHeight + 1) / 2);
    }
}
