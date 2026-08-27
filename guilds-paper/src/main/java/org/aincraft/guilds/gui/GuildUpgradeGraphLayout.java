package org.aincraft.guilds.gui;

import org.aincraft.guilds.models.TechTreeNode;
import java.util.Collection;

/** Pure viewport-relative graph geometry for guild upgrade MapGUI, Mirrored from modularjobs UpgradeTreeGraphLayout. */
final class GuildUpgradeGraphLayout {

    static final int CELL = 14;
    private static final int MAX_PAN = 112;

    private GuildUpgradeGraphLayout() {}

    static Origin origin(int x, int y, int width, int height, Collection<TechTreeNode> nodes) {
        int minX = nodes.stream().mapToInt(TechTreeNode::getPositionX).min().orElse(0);
        int maxX = nodes.stream().mapToInt(TechTreeNode::getPositionX).max().orElse(0);
        int minY = nodes.stream().mapToInt(TechTreeNode::getPositionY).min().orElse(0);
        int maxY = nodes.stream().mapToInt(TechTreeNode::getPositionY).max().orElse(0);
        int graphWidth = Math.max(CELL, (maxX - minX + 1) * CELL);
        int graphHeight = Math.max(CELL, (maxY - minY + 1) * CELL);
        return new Origin(
                x + (width - graphWidth) / 2 - minX * CELL,
                y + (height - graphHeight) / 2 - minY * CELL);
    }

    static int nodeX(Origin origin, TechTreeNode node) {
        return origin.x() + node.getPositionX() * CELL;
    }

    static int nodeY(Origin origin, TechTreeNode node) {
        return origin.y() + node.getPositionY() * CELL;
    }

    static int clampPan(int value) {
        return Math.max(-MAX_PAN, Math.min(MAX_PAN, value));
    }

    record Origin(int x, int y) {}
}
