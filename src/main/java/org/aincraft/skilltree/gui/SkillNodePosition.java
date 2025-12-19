package org.aincraft.skilltree.gui;

/**
 * Immutable position record for a skill node in the skill tree GUI.
 * Represents the (row, column) coordinates where a skill should be displayed
 * in the chest inventory (9 columns wide, 6 rows max).
 *
 * Single Responsibility: Represent skill node layout position.
 *
 * @param skillId the unique skill identifier
 * @param row the row index (0-5 in a 6-row chest)
 * @param column the column index (0-8 in a 9-column chest)
 */
public record SkillNodePosition(String skillId, int row, int column) {
    /**
     * Compact constructor that validates coordinates.
     */
    public SkillNodePosition {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("Skill ID cannot be null or blank");
        }
        if (row < 0 || row > 5) {
            throw new IllegalArgumentException("Row must be between 0-5, got: " + row);
        }
        if (column < 0 || column > 8) {
            throw new IllegalArgumentException("Column must be between 0-8, got: " + column);
        }
    }

    /**
     * Converts the position to a flat inventory slot number.
     * Slot = row * 9 + column
     *
     * @return the inventory slot (0-53)
     */
    public int toSlot() {
        return row * 9 + column;
    }
}
