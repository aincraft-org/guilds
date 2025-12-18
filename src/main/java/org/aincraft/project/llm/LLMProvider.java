package org.aincraft.project.llm;

import org.aincraft.project.BuffType;
import java.io.IOException;
import java.util.List;

/**
 * Interface for LLM providers that generate creative project names and descriptions.
 * Implementations handle communication with specific LLM APIs.
 */
public interface LLMProvider {
    /**
     * Generates a batch of project names and descriptions for a buff type.
     *
     * @param buffType the buff type to generate names for
     * @param count number of names to generate (typically 10)
     * @return list of generated project texts
     * @throws IOException if API call fails
     */
    List<ProjectText> generateBatch(BuffType buffType, int count) throws IOException;
}
