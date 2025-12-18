package org.aincraft.project.llm;

import org.aincraft.project.BuffType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * No-operation LLM provider that returns empty results.
 * Used when LLM integration is disabled or misconfigured.
 */
public class NoOpLLMProvider implements LLMProvider {
    private final Logger logger;

    public NoOpLLMProvider(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null");
    }

    @Override
    public List<ProjectText> generateBatch(BuffType buffType, int count) throws IOException {
        // No-op: return empty list
        return Collections.emptyList();
    }
}
