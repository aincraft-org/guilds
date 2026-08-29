package org.aincraft.guilds.models;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechTreeNodeTest {

    @Test
    void getParent_prefersExplicitParentFieldOverPrerequisites() {
        TechTreeNode node = new TechTreeNode("child");
        node.setParent("explicit_parent");
        node.setPrerequisites(List.of("other_a", "other_b"));

        assertEquals("explicit_parent", node.getParent());
    }

    @Test
    void getParent_fallsBackToFirstPrerequisite_whenParentUnset() {
        TechTreeNode node = new TechTreeNode("child");
        node.setPrerequisites(List.of("first", "second"));

        assertEquals("first", node.getParent());
    }

    @Test
    void getParent_isNull_whenNeitherParentNorPrerequisitesSet() {
        TechTreeNode node = new TechTreeNode("root");

        assertNull(node.getParent());
    }

    @Test
    void getEffectivePrerequisites_returnsPrerequisitesList_whenNonEmpty() {
        TechTreeNode node = new TechTreeNode("child");
        node.setParent("ignored");
        node.setPrerequisites(List.of("a", "b"));

        assertEquals(List.of("a", "b"), node.getEffectivePrerequisites());
    }

    @Test
    void getEffectivePrerequisites_fallsBackToParent_whenPrerequisitesNullOrEmpty() {
        TechTreeNode nullPrereqs = new TechTreeNode("child1");
        nullPrereqs.setParent("root");

        TechTreeNode emptyPrereqs = new TechTreeNode("child2");
        emptyPrereqs.setParent("root");
        emptyPrereqs.setPrerequisites(List.of());

        assertEquals(List.of("root"), nullPrereqs.getEffectivePrerequisites());
        assertEquals(List.of("root"), emptyPrereqs.getEffectivePrerequisites());
    }

    @Test
    void getEffectivePrerequisites_isEmpty_whenNeitherParentNorPrerequisitesSet() {
        TechTreeNode node = new TechTreeNode("root");

        assertTrue(node.getEffectivePrerequisites().isEmpty());
    }
}
