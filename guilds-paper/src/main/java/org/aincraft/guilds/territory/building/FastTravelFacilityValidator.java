package org.aincraft.guilds.territory.building;

import java.util.Objects;
import java.util.Optional;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Location;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.TechTreeService;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Main-thread validation for transport facilities.
 *
 * <p>Every method in this class reads Bukkit world state.  Callers must invoke
 * it on the Paper thread; no result is cached because governance, upgrades,
 * quotas, and spawn locations are live state.</p>
 */
public final class FastTravelFacilityValidator {
    public static final String FAST_TRAVEL_NODE = "fast_travel";
    public static final String BOAT_TRAVEL_NODE = "boat_travel";
    public static final String AIRSHIP_TRAVEL_NODE = "airship_travel";

    private final Server server;
    private final TerritoryRegistry territories;
    private final FacilityRegistry facilities;
    private final GuildService guilds;
    private final TechTreeService techTree;
    private final BuildingConfig config;
    private final FacilityAnchorValidator anchors;

    public FastTravelFacilityValidator(
            Server server,
            TerritoryRegistry territories,
            FacilityRegistry facilities,
            GuildService guilds,
            TechTreeService techTree,
            BuildingConfig config) {
        this(server, territories, facilities, guilds, techTree, config,
                new FacilityAnchorValidator(server, territories, facilities, config));
    }

    public FastTravelFacilityValidator(
            Server server,
            TerritoryRegistry territories,
            FacilityRegistry facilities,
            GuildService guilds,
            TechTreeService techTree,
            BuildingConfig config,
            FacilityAnchorValidator anchors) {
        this.server = Objects.requireNonNull(server, "server");
        this.territories = Objects.requireNonNull(territories, "territories");
        this.facilities = Objects.requireNonNull(facilities, "facilities");
        this.guilds = Objects.requireNonNull(guilds, "guilds");
        this.techTree = Objects.requireNonNull(techTree, "techTree");
        this.config = Objects.requireNonNull(config, "config");
        this.anchors = Objects.requireNonNull(anchors, "anchors");
    }
    public FastTravelFacilityValidator(
            Server server,
            TerritoryRegistry territories,
            FacilityRegistry facilities,
            FacilityAnchorValidator anchors,
            GuildService guilds,
            TechTreeService techTree,
            BuildingConfig config) {
        this(server, territories, facilities, guilds, techTree, config, anchors);
    }


    /**
     * Alternate seam for composition roots that already created the common
     * anchor validator.
     */
    public FastTravelFacilityValidator(
            FacilityAnchorValidator anchors,
            TerritoryRegistry territories,
            FacilityRegistry facilities,
            GuildService guilds,
            TechTreeService techTree,
            BuildingConfig config) {
        this(anchorsServer(anchors), territories, facilities, guilds, techTree, config, anchors);
    }

    /* The common constructor is the supported composition seam.  This helper
       exists only to keep the alternate constructor null-safe. */
    private static Server anchorsServer(FacilityAnchorValidator anchors) {
        return Objects.requireNonNull(anchors, "anchors").server();
    }

    public ValidationResult validateCandidate(
            SettlementFacility candidate, FacilityRegistry candidateRegistry) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(candidateRegistry, "candidateRegistry");
        if (!isTransport(candidate.type())) {
            return valid();
        }

        Territory territory = territories.get(candidate.territoryId()).orElse(null);
        if (territory == null || !territory.worldId().equals(candidate.worldId())
                || territories.resolve(candidate.worldId(), candidate.x(), candidate.z())
                        .territoryId().filter(candidate.territoryId()::equals).isEmpty()) {
            return failure(AnchorStatus.OUTSIDE_TERRITORY,
                    "transport anchor is outside its territory");
        }
        String ownerId = territory.governedByGuildId().orElse(null);
        if (ownerId == null || ownerId.isBlank()) {
            return failure(AnchorStatus.GOVERNANCE_MISSING, "territory has no governing guild");
        }
        Guild owner = guilds.getGuildById(ownerId.trim()).orElse(null);
        if (owner == null) {
            return failure(AnchorStatus.GOVERNANCE_MISSING, "governing guild is unavailable");
        }
        String requiredNode = requiredNode(candidate.type());
        if (!techTree.hasCapability(owner, requiredNode)) {
            return failure(AnchorStatus.CAPABILITY_MISSING,
                    "governing guild lacks " + requiredNode);
        }

        int quota = territory.fastTravelPolicy().quotaFor(candidate.type());
        if (countOwned(candidateRegistry, territory.id(), candidate.type(), ownerId) > quota) {
            return failure(AnchorStatus.QUOTA_EXCEEDED,
                    "facility quota exceeded for " + candidate.type());
        }
        if (isGlobalCardinalityType(candidate.type())
                && countOwnedType(candidateRegistry, candidate.type(), ownerId) > 1) {
            return failure(AnchorStatus.CARDINALITY_EXCEEDED,
                    "only one persisted " + candidate.type() + " is allowed for a guild");
        }

        ValidationResult physical = switch (candidate.type()) {
            case GUILD_CRYSTAL -> validateCrystalSpawn(candidate);
            case TELEPORT_TERMINAL -> validateAnchor(candidate);
            case BOAT -> validateBoat(candidate);
            case AIRSHIP -> validateAirship(candidate);
            default -> valid();
        };
        return physical;
    }

    /**
     * Validates removal invariants without rechecking the removed facility's
     * current world anchor or guild spawn.  A persisted transport record may
     * be inactive after governance or spawn movement and must remain removable.
     */
    public ValidationResult validateRemoval(
            SettlementFacility removed, FacilityRegistry candidateRegistry) {
        Objects.requireNonNull(removed, "removed");
        Objects.requireNonNull(candidateRegistry, "candidateRegistry");
        if (candidateRegistry.get(removed.id()).isPresent()) {
            return failure(AnchorStatus.CARDINALITY_EXCEEDED,
                    "removed facility remains in candidate registry");
        }
        return valid();
    }

    /** Validates a bounded local shoreline window, never a world-scale route. */
    public ValidationResult validateBoatAnchor(org.bukkit.Location anchor) {
        ValidationResult geometry = validateGeometry();
        if (!geometry.valid()) {
            return geometry;
        }
        if (anchor == null || anchor.getWorld() == null) {
            return failure(AnchorStatus.WORLD_UNAVAILABLE, "boat anchor world is unavailable");
        }
        World world = anchor.getWorld();
        int x = anchor.getBlockX();
        int y = anchor.getBlockY();
        int z = anchor.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return failure(AnchorStatus.WORLD_UNAVAILABLE, "boat anchor chunk is unavailable");
        }
        Block anchorBlock = world.getBlockAt(x, y, z);
        if (!config.anchorMaterials(FacilityType.BOAT).contains(anchorBlock.getType())) {
            return failure(AnchorStatus.WRONG_MATERIAL, "boat anchor has the wrong material");
        }
        BuildingConfig.TransportGeometry g = config.transportGeometry();
        if (!hasWaterWindow(world, x, y, z, g.boatEntryRadius(), g.boatEntryWidth(),
                g.clearBoatSpaceHeight())) {
            return failure(AnchorStatus.BOAT_ENTRY_UNAVAILABLE,
                    "no navigable water-entry window is adjacent to the boat anchor");
        }
        return valid();
    }

    /** Validates an anchor, bounded launch platform, and vertical clearance. */
    public ValidationResult validateAirshipAnchor(org.bukkit.Location anchor) {
        ValidationResult geometry = validateGeometry();
        if (!geometry.valid()) {
            return geometry;
        }
        if (anchor == null || anchor.getWorld() == null) {
            return failure(AnchorStatus.WORLD_UNAVAILABLE, "airship anchor world is unavailable");
        }
        World world = anchor.getWorld();
        int x = anchor.getBlockX();
        int y = anchor.getBlockY();
        int z = anchor.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return failure(AnchorStatus.WORLD_UNAVAILABLE, "airship anchor chunk is unavailable");
        }
        Block anchorBlock = world.getBlockAt(x, y, z);
        if (!config.anchorMaterials(FacilityType.AIRSHIP).contains(anchorBlock.getType())) {
            return failure(AnchorStatus.WRONG_MATERIAL, "airship anchor has the wrong material");
        }
        int radius = config.transportGeometry().airshipPlatformRadius();
        if (!hasLaunchPlatform(world, x, y, z, radius)) {
            return failure(AnchorStatus.AIRSHIP_PLATFORM_UNAVAILABLE,
                    "airship launch platform is unavailable");
        }
        int height = config.transportGeometry().airshipVerticalClearanceHeight();
        for (int dy = 1; dy <= height; dy++) {
            Block above = world.getBlockAt(x, y + dy, z);
            if (above == null || above.getType() == null || !above.getType().isAir()) {
                return failure(AnchorStatus.AIRSHIP_CLEARANCE_BLOCKED,
                        "airship vertical clearance is blocked");
            }
        }
        return valid();
    }

    /** Requires exact block/world equality with the current persisted guild spawn. */
    public ValidationResult validateCrystalSpawn(SettlementFacility candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (candidate.type() != FacilityType.GUILD_CRYSTAL) {
            return valid();
        }
        Territory territory = territories.get(candidate.territoryId()).orElse(null);
        String ownerId = territory == null ? null : territory.governedByGuildId().orElse(null);
        if (ownerId == null || ownerId.isBlank()) {
            return failure(AnchorStatus.GOVERNANCE_MISSING, "crystal territory has no governing guild");
        }
        Guild owner = guilds.getGuildById(ownerId.trim()).orElse(null);
        if (owner == null) {
            return failure(AnchorStatus.GOVERNANCE_MISSING, "crystal guild is unavailable");
        }
        Optional<Location> spawn = currentSpawn(owner);
        if (spawn.isEmpty() || !matches(candidate, spawn.get())) {
            return failure(AnchorStatus.SPAWN_MISMATCH,
                    "crystal anchor does not match the guild spawn");
        }
        Location location = spawn.get();
        if (location.getWorld() == null || location.getWorld().isBlank()) {
            return failure(AnchorStatus.SPAWN_MISMATCH, "guild spawn world is unavailable");
        }
        int[] blocks = location.getBlockCoordinates();
        Territory spawnTerritory = territories.resolve(location.getWorld(), blocks[0], blocks[2])
                .territory().orElse(null);
        if (spawnTerritory == null
                || spawnTerritory.governedByGuildId().map(ownerId.trim()::equals).orElse(false) == false) {
            return failure(AnchorStatus.SPAWN_MISMATCH,
                    "guild spawn is not inside a territory governed by the guild");
        }
        return validateAnchor(candidate);
    }

    /** Reconciles current governance, capability, cardinality, anchor, and spawn state. */
    public boolean isActive(SettlementFacility facility) {
        Objects.requireNonNull(facility, "facility");
        if (!isTransport(facility.type())) {
            return anchors.validate(facility).active();
        }
        Territory territory = territories.get(facility.territoryId()).orElse(null);
        if (territory == null || !territory.worldId().equals(facility.worldId())
                || territories.resolve(facility.worldId(), facility.x(), facility.z())
                        .territoryId().filter(facility.territoryId()::equals).isEmpty()) {
            return false;
        }
        String ownerId = territory.governedByGuildId().orElse(null);
        Guild owner = ownerId == null ? null : guilds.getGuildById(ownerId).orElse(null);
        if (owner == null || !techTree.hasCapability(owner, requiredNode(facility.type()))) {
            return false;
        }
        if (isGlobalCardinalityType(facility.type())
                && countOwnedType(facilities, facility.type(), ownerId) > 1) {
            return false;
        }
        ValidationResult physical = switch (facility.type()) {
            case GUILD_CRYSTAL -> validateCrystalSpawn(facility);
            case TELEPORT_TERMINAL -> validateAnchor(facility);
            case BOAT -> validateBoat(facility);
            case AIRSHIP -> validateAirship(facility);
            default -> valid();
        };
        return physical.valid();
    }

    private ValidationResult validateAnchor(SettlementFacility facility) {
        FacilityAnchorValidator.AnchorValidation result = anchors.validate(facility);
        return result.active() ? valid() : failure(result.status(), result.status().name());
    }

    private ValidationResult validateBoat(SettlementFacility facility) {
        World world = server.getWorld(facility.worldId());
        return validateBoatAnchor(world == null ? null : new org.bukkit.Location(
                world, facility.x(), facility.y(), facility.z()));
    }

    private ValidationResult validateAirship(SettlementFacility facility) {
        World world = server.getWorld(facility.worldId());
        return validateAirshipAnchor(world == null ? null : new org.bukkit.Location(
                world, facility.x(), facility.y(), facility.z()));
    }

    private ValidationResult validateGeometry() {
        BuildingConfig.TransportGeometry g = config.transportGeometry();
        if (g == null) {
            return failure(AnchorStatus.MISSING_GEOMETRY, "transport geometry is missing");
        }
        if (g.boatEntryRadius() <= 0 || g.boatEntryWidth() <= 0 || g.clearBoatSpaceHeight() <= 0
                || g.searchChunkRadius() <= 0 || g.searchChunkBudget() <= 0
                || g.airshipPlatformRadius() <= 0 || g.airshipVerticalClearanceHeight() <= 0) {
            return failure(AnchorStatus.INVALID_GEOMETRY, "transport geometry is invalid");
        }
        return valid();
    }

    private Optional<Location> currentSpawn(Guild guild) {
        String name = guild.getName();
        Optional<Location> byName = name == null || name.isBlank()
                ? Optional.empty() : guilds.getGuildSpawn(name);
        if (byName.isPresent()) {
            return byName;
        }
        String id = guild.getId();
        return id == null || id.isBlank() ? Optional.empty() : guilds.getGuildSpawn(id);
    }

    private static boolean matches(SettlementFacility candidate, Location spawn) {
        if (spawn == null || spawn.getWorld() == null) {
            return false;
        }
        int[] block = spawn.getBlockCoordinates();
        return candidate.worldId().equals(spawn.getWorld())
                && candidate.x() == block[0] && candidate.y() == block[1] && candidate.z() == block[2];
    }

    private static boolean hasWaterWindow(World world, int x, int y, int z,
                                          int radius, int width, int clearHeight) {
        int navigableCells = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block water = world.getBlockAt(x + dx, y, z + dz);
                if (water == null || water.getType() != Material.WATER) {
                    continue;
                }
                boolean clear = true;
                for (int dy = 1; dy <= clearHeight; dy++) {
                    Block above = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (above == null || above.getType() == null || !above.getType().isAir()) {
                        clear = false;
                        break;
                    }
                }
                if (clear && ++navigableCells >= width) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasLaunchPlatform(World world, int x, int y, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block platform = world.getBlockAt(x + dx, y - 1, z + dz);
                if (platform != null && platform.getType() != null && platform.getType().isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTransport(FacilityType type) {
        return type == FacilityType.GUILD_CRYSTAL || type == FacilityType.TELEPORT_TERMINAL
                || type == FacilityType.BOAT || type == FacilityType.AIRSHIP;
    }

    private static boolean isGlobalCardinalityType(FacilityType type) {
        return type == FacilityType.GUILD_CRYSTAL || type == FacilityType.TELEPORT_TERMINAL;
    }

    private static String requiredNode(FacilityType type) {
        return switch (type) {
            case GUILD_CRYSTAL, TELEPORT_TERMINAL -> FAST_TRAVEL_NODE;
            case BOAT -> BOAT_TRAVEL_NODE;
            case AIRSHIP -> AIRSHIP_TRAVEL_NODE;
            default -> "";
        };
    }

    private int countOwned(FacilityRegistry registry, String territoryId, FacilityType type, String ownerId) {
        int count = 0;
        for (SettlementFacility facility : registry.list()) {
            if (facility.type() != type || !facility.territoryId().equals(territoryId)) {
                continue;
            }
            if (effectiveOwner(facility).filter(ownerId::equals).isPresent()) {
                count++;
            }
        }
        return count;
    }

    private int countOwnedType(FacilityRegistry registry, FacilityType type, String ownerId) {
        int count = 0;
        for (SettlementFacility facility : registry.list()) {
            if (facility.type() == type && effectiveOwner(facility).filter(ownerId::equals).isPresent()) {
                count++;
            }
        }
        return count;
    }

    private Optional<String> effectiveOwner(SettlementFacility facility) {
        return territories.get(facility.territoryId()).flatMap(Territory::governedByGuildId)
                .map(String::trim).filter(value -> !value.isBlank());
    }

    private static ValidationResult valid() {
        return new ValidationResult(AnchorStatus.ACTIVE, "active");
    }

    private static ValidationResult failure(AnchorStatus status, String message) {
        return new ValidationResult(status, message);
    }

    public record ValidationResult(AnchorStatus status, String message) {
        public ValidationResult {
            Objects.requireNonNull(status, "status");
            message = message == null || message.isBlank() ? status.name() : message;
        }

        public boolean valid() {
            return status == AnchorStatus.ACTIVE;
        }

        public boolean active() {
            return valid();
        }

        public boolean isValid() {
            return valid();
        }

        public AnchorStatus outcome() {
            return status;
        }

        public String category() {
            return status.name();
        }

        public String failureCategory() {
            return category();
        }
    }
}
