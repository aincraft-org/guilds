package org.aincraft.towny.cache;

import org.aincraft.towny.models.TownyPermission;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simple in-memory cache for permission checks
 * Thread-safe with automatic cleanup
 */
public class SimplePermissionCache {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;
    private static final long CACHE_TTL_MINUTES = 5;
    private static final int MAX_CACHE_SIZE = 10000;

    private static class CacheEntry {
        final boolean result;
        final long timestamp;

        CacheEntry(boolean result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > (CACHE_TTL_MINUTES * 60 * 1000);
        }
    }

    public SimplePermissionCache() {
        // Schedule cleanup every minute
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "permission-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * Create cache key for permission check
     */
    private String createKey(UUID residentUuid, String contextKey, TownyPermission permission) {
        return residentUuid.toString() + ":" + contextKey + ":" + permission.name();
    }

    /**
     * Get cached permission result
     */
    public Optional<Boolean> getCachedPermission(UUID residentUuid, String contextKey, TownyPermission permission) {
        String key = createKey(residentUuid, contextKey, permission);
        CacheEntry entry = cache.get(key);

        if (entry != null && !entry.isExpired()) {
            return Optional.of(entry.result);
        }

        // Remove expired entry
        if (entry != null) {
            cache.remove(key);
        }

        return Optional.empty();
    }

    /**
     * Cache a permission result
     */
    public void cachePermission(UUID residentUuid, String contextKey, TownyPermission permission, boolean result) {
        // Prevent cache from growing too large
        if (cache.size() >= MAX_CACHE_SIZE) {
            cleanup();
            if (cache.size() >= MAX_CACHE_SIZE) {
                cache.clear(); // Emergency clear
            }
        }

        String key = createKey(residentUuid, contextKey, permission);
        cache.put(key, new CacheEntry(result));
    }

    /**
     * Invalidate all cached permissions for a resident
     */
    public void invalidateResident(UUID residentUuid) {
        String uuidString = residentUuid.toString();
        cache.entrySet().removeIf(entry -> entry.getKey().startsWith(uuidString + ":"));
    }

    /**
     * Invalidate all cached permissions for a context
     */
    public void invalidateContext(String contextKey) {
        cache.entrySet().removeIf(entry -> entry.getKey().contains(":" + contextKey + ":"));
    }

    /**
     * Remove expired entries
     */
    private void cleanup() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Clear all cache entries
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        int expiredCount = (int) cache.values().stream().filter(CacheEntry::isExpired).count();
        return new CacheStats(cache.size(), expiredCount);
    }

    /**
     * Shutdown the cleanup executor
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Cache statistics
     */
    public static class CacheStats {
        private final int totalEntries;
        private final int expiredEntries;

        public CacheStats(int totalEntries, int expiredEntries) {
            this.totalEntries = totalEntries;
            this.expiredEntries = expiredEntries;
        }

        public int getTotalEntries() {
            return totalEntries;
        }

        public int getExpiredEntries() {
            return expiredEntries;
        }

        public int getActiveEntries() {
            return totalEntries - expiredEntries;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{total=%d, active=%d, expired=%d}",
                               totalEntries, getActiveEntries(), expiredEntries);
        }
    }
}