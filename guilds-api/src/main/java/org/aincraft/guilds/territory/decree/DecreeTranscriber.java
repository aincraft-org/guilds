package org.aincraft.guilds.territory.decree;

/**
 * Pluggable English → structured {@link DecreeEffects} transcription.
 * <p>
 * Production may call an external LLM; tests inject a deterministic implementation
 * that still returns the same schema the interpreter consumes.
 */
@FunctionalInterface
public interface DecreeTranscriber {
    /**
     * Transcribe free-text English decree/law prose into structured effects.
     *
     * @param english English body of the decree or law
     * @param catalog goods catalog used to resolve categories/names to stable ids
     * @return structured effects (possibly empty if the text has no recognized tax effects)
     */
    DecreeEffects transcribe(String english, GoodsCatalog catalog);
}
