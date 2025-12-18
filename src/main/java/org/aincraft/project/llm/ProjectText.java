package org.aincraft.project.llm;

import java.util.Objects;

/**
 * Immutable record containing LLM-generated project name and description.
 * Supports caching and reuse of creative project text.
 */
public record ProjectText(String name, String description) {
    /**
     * Compact constructor for null validation.
     */
    public ProjectText {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(description, "Description cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("Description cannot be blank");
        }
    }
}
