package org.aincraft.project;

public record BuffDefinition(
        BuffCategory category,
        double value,
        String displayName
) {
}
