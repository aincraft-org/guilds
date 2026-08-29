package org.aincraft.guilds.gui;

import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertTrue(layout.containsKey("guild_hearth"));
        GuildUpgradeGraphLayout.LayoutNode hearth = layout.get("guild_hearth");
        assertEquals(64, hearth.x());
        assertEquals(64, hearth.y());
        assertEquals(GuildUpgradeGraphLayout.ShapeType.CORE, hearth.shape());

        for (TechTreeNode n : nodes) {
            assertTrue(layout.containsKey(n.getId()));
            GuildUpgradeGraphLayout.LayoutNode ln = layout.get(n.getId());
            assertTrue(ln.x() >= 10 && ln.x() <= 118);
            assertTrue(ln.y() >= 22 && ln.y() <= 114);
        }
    }

    @Test
    void edgesConnectRootNodesToGuildHearth() {
        List<TechTreeNode> nodes = sampleNodes();
        List<GuildUpgradeGraphLayout.SplineEdge> edges = GuildUpgradeGraphLayout.edges(nodes);

        assertTrue(edges.containsAll(List.of(
            new GuildUpgradeGraphLayout.SplineEdge("guild_hearth", "better_storage"),
            new GuildUpgradeGraphLayout.SplineEdge("guild_hearth", "reinforced_walls"),
            new GuildUpgradeGraphLayout.SplineEdge("guild_hearth", "market_stall"),
            new GuildUpgradeGraphLayout.SplineEdge("guild_hearth", "heritage_monument"),
            new GuildUpgradeGraphLayout.SplineEdge("better_storage", "fast_travel")
        )));
    }

    @Test
    void findNodeAtResolvesNodeWithinHitbox() {
        List<TechTreeNode> nodes = sampleNodes();
        Map<String, GuildUpgradeGraphLayout.LayoutNode> layout = GuildUpgradeGraphLayout.layout(nodes);

        GuildUpgradeGraphLayout.LayoutNode found = GuildUpgradeGraphLayout.findNodeAt(layout, 64, 64);
        assertNotNull(found);
        assertEquals("guild_hearth", found.id());

        GuildUpgradeGraphLayout.LayoutNode hitEdge = GuildUpgradeGraphLayout.findNodeAt(layout, 69, 64);
        assertNotNull(hitEdge);
        assertEquals("guild_hearth", hitEdge.id());

        GuildUpgradeGraphLayout.LayoutNode miss = GuildUpgradeGraphLayout.findNodeAt(layout, 0, 0);
        assertNull(miss);
    }

    @Test
    void layoutMapsEveryConfiguredNodeToDistinctPositionAndEdgesRemainConnected() {
        List<TechTreeNode> nodes = configuredNodes();
        Map<String, GuildUpgradeGraphLayout.LayoutNode> layout = GuildUpgradeGraphLayout.layout(nodes);

        Set<String> positions = new HashSet<>();
        for (TechTreeNode node : nodes) {
            GuildUpgradeGraphLayout.LayoutNode layoutNode = layout.get(node.getId());
            assertNotNull(layoutNode);
            assertTrue(positions.add(layoutNode.x() + ":" + layoutNode.y()));
            assertFalse(layoutNode.x() == 64 && layoutNode.y() == 64);
        }

        assertEquals(nodes.size(), positions.size());
        assertTrue(GuildUpgradeGraphLayout.edges(nodes).containsAll(List.of(
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
        )));
    }
    @Test
    void fastTravelInfrastructureNodesHaveDistinctHitboxes() {
        List<TechTreeNode> nodes = new ArrayList<>(configuredNodes());
        nodes.add(node("remote_crystal", "Remote Crystal", TechTreeBranch.INFRASTRUCTURE, 3,
                List.of("fast_travel")));
        nodes.add(node("boat_travel", "Boat Travel", TechTreeBranch.INFRASTRUCTURE, 3,
                List.of("fast_travel")));
        nodes.add(node("airship_travel", "Airship Travel", TechTreeBranch.INFRASTRUCTURE, 4,
                List.of("fast_travel")));

        Map<String, GuildUpgradeGraphLayout.LayoutNode> layout =
                GuildUpgradeGraphLayout.layout(nodes);
        Set<String> positions = new HashSet<>();
        for (String id : List.of("remote_crystal", "boat_travel", "airship_travel")) {
            GuildUpgradeGraphLayout.LayoutNode placed = layout.get(id);
            assertNotNull(placed);
            assertTrue(positions.add(placed.x() + ":" + placed.y()));
            for (GuildUpgradeGraphLayout.LayoutNode existing : layout.values()) {
                if (!existing.id().equals(id)) {
                    assertFalse(Math.abs(placed.x() - existing.x()) <= 6
                                    && Math.abs(placed.y() - existing.y()) <= 6,
                            id + " overlaps " + existing.id());
                }
            }
        }
    }


    @Test
    void fallbackPlacementKeepsLargeCustomBranchHitboxesDisjoint() {
        List<TechTreeNode> nodes = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            nodes.add(node("custom_infrastructure_" + index, "Custom " + index,
                    TechTreeBranch.INFRASTRUCTURE, 1, List.of()));
        }

        Map<String, GuildUpgradeGraphLayout.LayoutNode> layout = GuildUpgradeGraphLayout.layout(nodes);
        Set<String> positions = new HashSet<>();
        for (TechTreeNode node : nodes) {
            GuildUpgradeGraphLayout.LayoutNode layoutNode = layout.get(node.getId());
            assertNotNull(layoutNode);
            assertTrue(positions.add(layoutNode.x() + ":" + layoutNode.y()));
            assertTrue(layoutNode.x() >= 10 && layoutNode.x() <= 118);
            assertTrue(layoutNode.y() >= 22 && layoutNode.y() <= 114);
            assertFalse(layoutNode.x() == 64 && layoutNode.y() == 64);
        }
        assertEquals(nodes.size(), positions.size());
        List<GuildUpgradeGraphLayout.LayoutNode> placed = new ArrayList<>(layout.values());
        for (int first = 0; first < placed.size(); first++) {
            for (int second = first + 1; second < placed.size(); second++) {
                GuildUpgradeGraphLayout.LayoutNode a = placed.get(first);
                GuildUpgradeGraphLayout.LayoutNode b = placed.get(second);
                boolean hitboxesOverlap = Math.abs(a.x() - b.x()) <= 6
                        && Math.abs(a.y() - b.y()) <= 6;
                assertFalse(hitboxesOverlap, "%s and %s overlap".formatted(a.id(), b.id()));
            }
        }
    }
    @Test
    void fallbackPlacementStaysWithinViewportBoundsAcrossBranches() {
        List<TechTreeNode> nodes = new ArrayList<>();
        for (TechTreeBranch branch : TechTreeBranch.values()) {
            for (int index = 0; index < 17; index++) {
                TechTreeNode dynamic = new TechTreeNode(
                        "boundary_" + branch.name().toLowerCase() + "_" + index);
                dynamic.setName("Boundary " + branch.name() + " " + index);
                dynamic.setBranch(branch);
                dynamic.setCost(1);
                dynamic.setPrerequisites(List.of());
                nodes.add(dynamic);
            }
        }

        Map<String, GuildUpgradeGraphLayout.LayoutNode> layout =
                GuildUpgradeGraphLayout.layout(nodes);
        for (TechTreeNode node : nodes) {
            GuildUpgradeGraphLayout.LayoutNode layoutNode = layout.get(node.getId());
            assertNotNull(layoutNode);
            assertTrue(layoutNode.x() >= 10 && layoutNode.x() <= 118);
            assertTrue(layoutNode.y() >= 22 && layoutNode.y() <= 114);
        }
    }

    @Test
    void nullBranchUsesInvalidShapeInsteadOfHearthCore() {
        TechTreeNode malformed = new TechTreeNode("malformed_null_branch");
        malformed.setName("Malformed branch");
        malformed.setCost(1);
        malformed.setPrerequisites(List.of());

        GuildUpgradeGraphLayout.LayoutNode layoutNode =
                GuildUpgradeGraphLayout.layout(List.of(malformed)).get(malformed.getId());

        assertNotNull(layoutNode);
        assertNull(malformed.getBranch());
        assertEquals(GuildUpgradeGraphLayout.ShapeType.INVALID, layoutNode.shape());
        assertFalse(layoutNode.shape() == GuildUpgradeGraphLayout.ShapeType.CORE);
        assertTrue(layoutNode.x() >= 10 && layoutNode.x() <= 118);
        assertTrue(layoutNode.y() >= 22 && layoutNode.y() <= 114);
    }
}
