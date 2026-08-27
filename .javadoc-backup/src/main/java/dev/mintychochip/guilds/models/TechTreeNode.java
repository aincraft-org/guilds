package dev.mintychochip.guilds.models;

import java.util.List;
import java.util.Map;

/**
 * Represents a single node in the tech tree.
 * Nodes have prerequisites, costs, effects, and a GUI position.
 */
public class TechTreeNode {

    /** The id. */
    private final String id;
    /** The name. */
    private String name;
    /** The description. */
    private String description;
    /** The branch. */
    private TechTreeBranch branch;
    /** The cost. */
    private int cost;
    /** The prerequisites. */
    private List<String> prerequisites;
    /** The effects. */
    private Map<String, Object> effects;
    /** The position x. */
    private int positionX;
    /** The position y. */
    private int positionY;

    /**
     * Creates a new tech tree node instance.
     * @param id the id
     */
    public TechTreeNode(String id) {
        this.id = id;
    }

    // Getters and Setters

    /**
     * Returns the id.
     * @return the result
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the name.
     * @return the result
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the description.
     * @return the result
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     * @param description the description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the branch.
     * @return the result
     */
    public TechTreeBranch getBranch() {
        return branch;
    }

    /**
     * Sets the branch.
     * @param branch the branch
     */
    public void setBranch(TechTreeBranch branch) {
        this.branch = branch;
    }

    /**
     * Returns the cost.
     * @return the result
     */
    public int getCost() {
        return cost;
    }

    /**
     * Sets the cost.
     * @param cost the cost
     */
    public void setCost(int cost) {
        this.cost = Math.max(0, cost);
    }

    /**
     * Returns the prerequisites.
     * @return the result
     */
    public List<String> getPrerequisites() {
        return prerequisites;
    }

    /**
     * Sets the prerequisites.
     * @param prerequisites the prerequisites
     */
    public void setPrerequisites(List<String> prerequisites) {
        this.prerequisites = prerequisites;
    }

    /**
     * Returns the effects.
     * @return the result
     */
    public Map<String, Object> getEffects() {
        return effects;
    }

    /**
     * Sets the effects.
     * @param effects the effects
     */
    public void setEffects(Map<String, Object> effects) {
        this.effects = effects;
    }

    /**
     * Returns the position x.
     * @return the result
     */
    public int getPositionX() {
        return positionX;
    }

    /**
     * Sets the position x.
     * @param positionX the position x
     */
    public void setPositionX(int positionX) {
        this.positionX = positionX;
    }

    /**
     * Returns the position y.
     * @return the result
     */
    public int getPositionY() {
        return positionY;
    }

    /**
     * Sets the position y.
     * @param positionY the position y
     */
    public void setPositionY(int positionY) {
        this.positionY = positionY;
    }

    /**
     * Get the GUI slot index (row-major, 9 columns)
     */
    public int getSlot() {
        return positionY * 9 + positionX;
    }

    /**
     * Indicates whether another object is equal to this one.
     * @param o the o
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id.equals(((TechTreeNode) o).id);
    }

    /** Returns a hash code for this object. */
    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Returns a string representation of this object. */
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
