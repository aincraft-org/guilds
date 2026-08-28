package org.aincraft.guilds.gui;

import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class GuildUpgradeGraphLayoutTest {

    private TechTreeNode node(String id, String name, TechTreeBranch branch, int cost, List<String> prereqs) {
        TechTreeNode n = new TechTreeNode(id);
        n.setName(name);
        n.setBranch(branch);
        n.setCost(cost);
        n.setPrerequisites(prereqs);
        return n;
    }

    private List<TechTreeNode> sampleNodes() {
        return List.of(
            node("better_storage", "Better Storage", TechTreeBranch.INFRASTRUCTURE, 2, List.of()),
            node("fast_travel", "Fast Travel", TechTreeBranch.INFRASTRUCTURE, 3, List.of("better_storage")),
            node("reinforced_walls", "Reinforced Walls", TechTreeBranch.DEFENSE, 2, List.of()),
            node("market_stall", "Market Stall", TechTreeBranch.COMMERCE, 2, List.of()),
            node("heritage_monument", "Heritage Monument", TechTreeBranch.CULTURE, 2, List.of())
        );
    }

    @Test
    void layoutInjectsSyntheticGuildHearthAndPositionsNodes() {
        List<TechTreeNode> nodes = sampleNodes();
        Map<String, GuildUpgradeGraphLayout.LayoutNode> layout = GuildUpgradeGraphLayout.layout(nodes);

        assertThat(layout).containsKey("guild_hearth");
        GuildUpgradeGraphLayout.LayoutNode hearth = layout.get("guild_hearth");
        assertThat(hearth.x()).isEqualTo(64);
        assertThat(hearth.y()).isEqualTo(64);
        assertThat(hearth.shape()).isEqualTo(GuildUpgradeGraphLayout.ShapeType.CORE);

        for (TechTreeNode n : nodes) {
            assertThat(layout).containsKey(n.getId());
            GuildUpgradeGraphLayout.LayoutNode ln = layout.get(n.getId());
            assertThat(ln.x()).isBetween(8, 120);
            assertThat(ln.y()).isBetween(14, 118);
        }
    }

    @Test
    void edgesConnectRootNodesToGuildHearth() {
        List<TechTreeNode> nodes = sampleNodes();
        List<GuildUpgradeGraphLayout.SplineEdge> edges = GuildUpgradeGraphLayout.edges(nodes);

        assertThat(edges).contains(
            new GuildUpgradeGraphLayout.SplineEdge("guild_hearth", "better_storage"),
            new GuildUpgradeGraphLayout.SplineEdge("guild_hearth", "reinforced_walls"),
            new GuildUpgradeGraphLayout.SplineEdge("guild_hearth", "market_stall"),
            new GuildUpgradeGraphLayout.SplineEdge("guild_hearth", "heritage_monument"),
            new GuildUpgradeGraphLayout.SplineEdge("better_storage", "fast_travel")
        );
    }

    @Test
    void findNodeAtResolvesNodeWithinHitbox() {
        List<TechTreeNode> nodes = sampleNodes();
        Map<String, GuildUpgradeGraphLayout.LayoutNode> layout = GuildUpgradeGraphLayout.layout(nodes);

        GuildUpgradeGraphLayout.LayoutNode found = GuildUpgradeGraphLayout.findNodeAt(layout, 64, 64);
        assertThat(found).isNotNull();
        assertThat(found.id()).isEqualTo("guild_hearth");

        GuildUpgradeGraphLayout.LayoutNode hitEdge = GuildUpgradeGraphLayout.findNodeAt(layout, 69, 64);
        assertThat(hitEdge).isNotNull();
        assertThat(hitEdge.id()).isEqualTo("guild_hearth");

        GuildUpgradeGraphLayout.LayoutNode miss = GuildUpgradeGraphLayout.findNodeAt(layout, 0, 0);
        assertThat(miss).isNull();
    }
}
