package com.azoth.territory.persist;

import com.azoth.territory.registry.TerritoryRegistry;

import java.io.IOException;

/**
 * Durable store for the territory registry.
 * <p>
 * Implementations: {@link TerritoryStore} (JSON file) and
 * {@link PostgresTerritoryRepository} (remote PostgreSQL). Callers — the web
 * API and plugin persistence — depend only on this seam, so the store can be
 * swapped or extracted into a standalone service without touching them.
 */
public interface TerritoryRepository extends AutoCloseable {
    /** Replace the registry contents from durable storage. */
    void loadInto(TerritoryRegistry registry) throws IOException;

    /** Persist the full registry (atomic replace). */
    void save(TerritoryRegistry registry) throws IOException;

    /** Release backing resources; no-op for file-backed stores. */
    @Override
    void close();
}
