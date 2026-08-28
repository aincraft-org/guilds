package org.aincraft.guilds.models;

import java.util.List;
import java.util.Map;

/**
 * Represents a single node in the tech tree.
 * Nodes have prerequisites, costs, effects, and a GUI position.
 */
public class TechTreeNode {

    private final String id;
    private String name;
    private String description;
    private TechTreeBranch branch;
    private int cost;
    private String parent;
    private List<String> prerequisites;
    private Map<String, Object> effects;
    private int positionX;
    private int positionY;

    public TechTreeNode(String id) {
        this.id = id;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TechTreeBranch getBranch() {
        return branch;
    }

    public void setBranch(TechTreeBranch branch) {
        this.branch = branch;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = Math.max(0, cost);
    }

    public List<String> getPrerequisites() {
        return prerequisites;
    }

    public void setPrerequisites(List<String> prerequisites) {
        this.prerequisites = prerequisites;
    }

    public String getParent() {
        if (parent != null && !parent.isBlank()) return parent;
        if (prerequisites != null && !prerequisites.isEmpty()) return prerequisites.get(0);
        return null;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    /**
     * The effective prerequisite list, falling back to the singleton {@code parent} field when
     * {@code prerequisites} is unset or empty. Mirrors {@link #getParent()}'s fallback direction
     * so configs that only ever set {@code parent:} (no {@code prerequisites:} list) still count
     * as having a prerequisite — e.g. for graph edge rendering.
     */
    public List<String> getEffectivePrerequisites() {
        if (prerequisites != null && !prerequisites.isEmpty()) return prerequisites;
        if (parent != null && !parent.isBlank()) return List.of(parent);
        return List.of();
    }



    public Map<String, Object> getEffects() {
        return effects;
    }

    public void setEffects(Map<String, Object> effects) {
        this.effects = effects;
    }

    public int getPositionX() {
        return positionX;
    }

    public void setPositionX(int positionX) {
        this.positionX = positionX;
    }

    public int getPositionY() {
        return positionY;
    }

    public void setPositionY(int positionY) {
        this.positionY = positionY;
    }

    /**
     * Get the GUI slot index (row-major, 9 columns)
     */
    public int getSlot() {
        return positionY * 9 + positionX;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id.equals(((TechTreeNode) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "TechTreeNode{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", branch=" + branch +
                ", cost=" + cost +
                '}';
    }
}
