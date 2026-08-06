package org.aincraft.guilds.web;

import org.aincraft.guilds.models.TechTreeNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tech-tree web payload's graph edges must be derived from node
 * prerequisites: one directed edge per prerequisite (source -> target).
 */
class TechTreeEdgesTest {

    @Test
    void edgesFollowPrerequisiteRelations() {
        TechTreeNode a = node("a");
        TechTreeNode b = node("b", "a");
        TechTreeNode c = node("c", "a", "b");
        TechTreeNode leaf = node("leaf");

        List<Map<String, Object>> edges = WebServer.buildTreeEdges(List.of(a, b, c, leaf));

        assertEquals(3, edges.size());
        assertTrue(edges.contains(Map.of("source", "a", "target", "b")));
        assertTrue(edges.contains(Map.of("source", "a", "target", "c")));
        assertTrue(edges.contains(Map.of("source", "b", "target", "c")));
    }

    @Test
    void nullPrerequisitesProduceNoEdges() {
        TechTreeNode a = new TechTreeNode("a");
        assertTrue(WebServer.buildTreeEdges(List.of(a)).isEmpty());
    }

    private static TechTreeNode node(String id, String... prerequisites) {
        TechTreeNode node = new TechTreeNode(id);
        node.setPrerequisites(List.of(prerequisites));
        return node;
    }
}
