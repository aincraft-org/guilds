package org.aincraft.guilds.territory.worldguard;

import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.ChunkPos;
import org.aincraft.guilds.territory.model.Territory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure (Bukkit/WorldGuard-free) computation of which WorldGuard regions a
 * {@link Territory} should have.
 *
 * <p>Kept free of WorldGuard/Bukkit types so the mapping logic is
 * unit-testable without a live server or the WorldGuard plugin installed;
 * {@link TerritoryWorldGuardBridge} turns these specs into real
 * {@code ProtectedRegion}s.</p>
 *
 * <p>A territory with a polygon boundary becomes one polygonal region. A
 * territory with a chunk-set boundary becomes one cuboid region per chunk
 * (full world height) rather than one merged outline polygon —
 * WorldGuard's {@code ProtectedPolygonalRegion} cannot represent holes, so
 * per-chunk cuboids sidestep that limitation entirely (at the cost of more,
 * smaller regions). Boundaries that combine both (rare) get both.</p>
 */
final class TerritoryRegionPlanner {

    /** Prefix for every region id this bridge manages, so refresh can find and remove stale ones safely. */
    static final String ID_PREFIX = "guilds-";

    private TerritoryRegionPlanner() {
    }

    /** One desired WorldGuard region: either a chunk cuboid or a polygon, never both. */
    record RegionSpec(String id, ChunkPos chunk, List<BlockPos> polygon, Set<UUID> owners) {

        boolean isCuboid() {
            return chunk != null;
        }
    }

    /** Computes every region a territory should have, given its current owning guild's members. */
    static List<RegionSpec> plan(Territory territory, Set<UUID> owners) {
        List<RegionSpec> specs = new ArrayList<>();
        String base = ID_PREFIX + keyPart(territory.id());
        if (territory.boundary().hasPolygon()) {
            specs.add(new RegionSpec(base, null, territory.boundary().polygon(), owners));
        }
        if (territory.boundary().hasChunks()) {
            for (ChunkPos chunk : territory.boundary().chunks()) {
                String id = base + "-" + chunk.chunkX() + "_" + chunk.chunkZ();
                specs.add(new RegionSpec(id, chunk, null, owners));
            }
        }
        return specs;
    }

    /**
     * A stable, order-independent signature of a region's shape and owners.
     * Used to skip re-applying a region to WorldGuard when nothing changed.
     */
    static String signatureOf(RegionSpec spec) {
        StringBuilder sb = new StringBuilder();
        if (spec.isCuboid()) {
            sb.append("cuboid:").append(spec.chunk().chunkX()).append(',').append(spec.chunk().chunkZ());
        } else {
            sb.append("polygon:");
            for (BlockPos v : spec.polygon()) {
                sb.append(v.x()).append(':').append(v.z()).append(';');
            }
        }
        sb.append("|owners:");
        spec.owners().stream().map(UUID::toString).sorted().forEach(id -> sb.append(id).append(','));
        return sb.toString();
    }

    /**
     * WorldGuard region ids only allow {@code [a-z0-9_-]} (case-folded). Territory
     * ids may contain other characters (spaces, apostrophes), so map every
     * disallowed character to {@code '_'}.
     */
    static String keyPart(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        return out.length() == 0 ? "unnamed" : out.toString();
    }
}
