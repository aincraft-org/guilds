package org.aincraft.guilds.gui;

import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private List<TechTreeNode> configuredNodes() {
        return List.of(
            node("better_storage", "Better Storage", TechTreeBranch.INFRASTRUCTURE, 2, List.of()),
            node("fast_travel", "Fast Travel", TechTreeBranch.INFRASTRUCTURE, 3, List.of("better_storage")),
            node("advanced_farming", "Advanced Farming", TechTreeBranch.INFRASTRUCTURE, 3, List.of("better_storage")),
            node("auto_sorter", "Auto Sorter", TechTreeBranch.INFRASTRUCTURE, 5,
                    List.of("fast_travel", "advanced_farming")),
            node("reinforced_walls", "Reinforced Walls", TechTreeBranch.DEFENSE, 2, List.of()),
            node("guard_posts", "Guard Posts", TechTreeBranch.DEFENSE, 3, List.of("reinforced_walls")),
            node("siege_shields", "Siege Shields", TechTreeBranch.DEFENSE, 4, List.of("guard_posts")),
            node("fortification", "Fortification", TechTreeBranch.DEFENSE, 6, List.of("siege_shields")),
            node("marketplace", "Marketplace", TechTreeBranch.COMMERCE, 2, List.of()),
            node("trade_routes", "Trade Routes", TechTreeBranch.COMMERCE, 3, List.of("marketplace")),
            node("tax_optimization", "Tax Optimization", TechTreeBranch.COMMERCE, 4, List.of("trade_routes")),
            node("merchant_guild", "Merchant Guild", TechTreeBranch.COMMERCE, 6, List.of("tax_optimization")),
            node("guild_banner", "Guild Banner", TechTreeBranch.CULTURE, 1, List.of()),
            node("broadcast_tower", "Broadcast Tower", TechTreeBranch.CULTURE, 3, List.of("guild_banner")),
            node("guild_hall", "Guild Hall", TechTreeBranch.CULTURE, 4, List.of("broadcast_tower")),
            node("monument", "Monument", TechTreeBranch.CULTURE, 6, List.of("guild_hall"))
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

    @Test
    void layoutMapsEveryConfiguredNodeToDistinctPositionAndEdgesRemainConnected() {
        List<TechTreeNode> nodes = configuredNodes();
        Map<String, GuildUpgradeGraphLayout.LayoutNode> layout = GuildUpgradeGraphLayout.layout(nodes);

        Set<String> positions = new HashSet<>();
        for (TechTreeNode node : nodes) {
            GuildUpgradeGraphLayout.LayoutNode layoutNode = layout.get(node.getId());
            assertThat(layoutNode).isNotNull();
            assertThat(positions.add(layoutNode.x() + ":" + layoutNode.y())).isTrue();
            assertThat(layoutNode.x() == 64 && layoutNode.y() == 64).isFalse();
        }

        assertThat(positions).hasSize(nodes.size());
        assertThat(GuildUpgradeGraphLayout.edges(nodes)).contains(
                new GuildUpgradeGraphLayout.SplineEdge("guild_hearth", "better_storage"),
                new GuildUpgradeGraphLayout.SplineEdge("better_storage", "advanced_farming"),
                new GuildUpgradeGraphLayout.SplineEdge("fast_travel", "auto_sorter"),
                new GuildUpgradeGraphLayout.SplineEdge("reinforced_walls", "guard_posts"),
                new GuildUpgradeGraphLayout.SplineEdge("guard_posts", "siege_shields"),
                new GuildUpgradeGraphLayout.SplineEdge("siege_shields", "fortification"),
                new GuildUpgradeGraphLayout.SplineEdge("marketplace", "trade_routes"),
                new GuildUpgradeGraphLayout.SplineEdge("trade_routes", "tax_optimization"),
                new GuildUpgradeGraphLayout.SplineEdge("tax_optimization", "merchant_guild"),
                new GuildUpgradeGraphLayout.SplineEdge("guild_banner", "broadcast_tower"),
                new GuildUpgradeGraphLayout.SplineEdge("broadcast_tower", "guild_hall"),
                new GuildUpgradeGraphLayout.SplineEdge("guild_hall", "monument")
        );
    }
    @Test
    void fallbackPlacementUsesDistinctCoordinatesForLargeCustomBranch() {
        List<TechTreeNode> nodes = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            nodes.add(node("custom_infrastructure_" + index, "Custom " + index,
                    TechTreeBranch.INFRASTRUCTURE, 1, List.of()));
        }

        Map<String, GuildUpgradeGraphLayout.LayoutNode> layout = GuildUpgradeGraphLayout.layout(nodes);
        Set<String> positions = new HashSet<>();
        for (TechTreeNode node : nodes) {
            GuildUpgradeGraphLayout.LayoutNode layoutNode = layout.get(node.getId());
            assertThat(layoutNode).isNotNull();
            assertThat(positions.add(layoutNode.x() + ":" + layoutNode.y())).isTrue();
            assertThat(layoutNode.x()).isBetween(8, 120);
            assertThat(layoutNode.y()).isBetween(14, 118);
            assertThat(layoutNode.x() == 64 && layoutNode.y() == 64).isFalse();
        }
        assertThat(positions).hasSize(nodes.size());
    }
}

