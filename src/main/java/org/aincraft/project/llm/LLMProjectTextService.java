package org.aincraft.project.llm;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.config.GuildsConfig;
import org.aincraft.project.BuffType;
import org.bukkit.configuration.ConfigurationSection;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for LLM-powered project text generation with caching and rate limiting.
 * Async background generation with configurable rate limiting and cache thresholds.
 */
@Singleton
public class LLMProjectTextService {
    private static final int BATCH_SIZE = 10;
    private static final int CACHE_THRESHOLD = 20;
    private static final int KEEP_COUNT = 100;

    private final LLMProvider provider;
    private final LLMProjectTextRepository repository;
    private final GuildsConfig config;
    private final Logger logger;
    private final Executor backgroundExecutor;
    private final Semaphore rateLimiter;
    private final long rateLimitCooldownMs;

    @Inject
    public LLMProjectTextService(
            LLMProvider provider,
            LLMProjectTextRepository repository,
            GuildsConfig config,
            @com.google.inject.name.Named("guilds") Logger logger) {
        this.provider = Objects.requireNonNull(provider, "Provider cannot be null");
        this.repository = Objects.requireNonNull(repository, "Repository cannot be null");
        this.config = Objects.requireNonNull(config, "Config cannot be null");
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null");

        // Single-thread executor for background generation
        this.backgroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable, "LLM-ProjectText-Generator");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

        // Rate limiter: 1 concurrent generation
        this.rateLimiter = new Semaphore(1);

        // Configurable cooldown between batches
        long cooldownSeconds = config.getPlugin().getConfig().getLong("llm.rate-limit-cooldown-seconds", 60);
        this.rateLimitCooldownMs = cooldownSeconds * 1000;

        logger.info("LLMProjectTextService initialized with rate limit cooldown: " + cooldownSeconds + "s");
    }

    /**
     * Gets project text for a buff type, using cache or queuing generation if needed.
     * Returns null if cache miss and no text available.
     *
     * @param buffType the buff type to get text for
     * @return project text from cache, or null if unavailable
     */
    public ProjectText getProjectText(BuffType buffType) {
        Objects.requireNonNull(buffType, "BuffType cannot be null");

        // Try to get from cache
        Optional<ProjectText> cached = repository.getRandomCachedText(buffType);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Queue generation if cache is low
        int cacheSize = repository.getCacheSize(buffType);
        if (cacheSize < CACHE_THRESHOLD) {
            backgroundExecutor.execute(() -> generateAndCache(buffType));
        }

        return null;
    }

    /**
     * Generates and caches a batch of project texts.
     * Uses rate limiting to prevent API flooding.
     */
    private void generateAndCache(BuffType buffType) {
        try {
            // Acquire rate limiting permit
            if (!rateLimiter.tryAcquire()) {
                logger.fine("Rate limit reached for " + buffType + ", skipping generation");
                return;
            }

            try {
                logger.fine("Generating project texts for " + buffType);
                List<ProjectText> batch = provider.generateBatch(buffType, BATCH_SIZE);

                if (batch.isEmpty()) {
                    logger.warning("No project texts generated for " + buffType);
                    return;
                }

                repository.saveAll(buffType, batch);
                logger.fine("Cached " + batch.size() + " project texts for " + buffType);

                // Cleanup old entries
                repository.deleteOldEntries(buffType, KEEP_COUNT);

            } finally {
                // Release permit and apply cooldown
                Thread.sleep(rateLimitCooldownMs);
                rateLimiter.release();
            }

        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to generate project texts for " + buffType, e);
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Project text generation interrupted for " + buffType, e);
            Thread.currentThread().interrupt();
        }
    }
}
