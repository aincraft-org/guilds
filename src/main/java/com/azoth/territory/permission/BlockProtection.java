package com.azoth.territory.permission;

import java.util.Objects;

/**
 * Domain block break/place and environmental protection using spatial resolve + form-based authority.
 * <p>
 * Player break/place rules:
 * <ul>
 *   <li>Uncontained wilderness (no territory) — allow (no governing body).</li>
 *   <li>Territory/alliance with {@code ANARCHY} or unassigned government — allow
 *       (no formal authority grants apply; no seat-based lockdown).</li>
 *   <li>Assigned government — only filled authority-seat holders may break/place;
 *       outsiders are denied.</li>
 * </ul>
 * Environmental protection (assigned non-anarchy territory-local or alliance government only;
 * uncontained and anarchy stay unrestricted):
 * <ul>
 *   <li>Fire burn/spread/ignite and explosions — {@link #isEnvironmentallyProtected}</li>
 *   <li>Natural/hostile mob spawn — {@link #blocksMobSpawn}</li>
 *   <li>Entity-caused block change and crop trampling — {@link #blocksEntityGrief}</li>
 * </ul>
 * Paper/Bukkit listeners should call these methods; they are pure domain.
 * Spawn-reason filtering (eggs, spawners, commands stay unrestricted) lives at the listener edge.
 */
public final class BlockProtection {
    private final GovernanceRegistry governance;

    public BlockProtection(GovernanceRegistry governance) {
        this.governance = Objects.requireNonNull(governance, "governance");
    }

    public GovernanceRegistry governance() {
        return governance;
    }

    /**
     * Whether {@code actorId} may break a block at the world location.
     */
    public boolean canBreak(String worldId, int blockX, int blockZ, String actorId) {
        return canModify(worldId, blockX, blockZ, actorId, SovereignAction.BREAK_BLOCK);
    }

    /**
     * Whether {@code actorId} may place a block at the world location.
     */
    public boolean canPlace(String worldId, int blockX, int blockZ, String actorId) {
        return canModify(worldId, blockX, blockZ, actorId, SovereignAction.PLACE_BLOCK);
    }

    /**
     * Whether blocks at the world location are environmentally protected
     * (fire burn/spread and explosions must not destroy or ignite them).
     * <p>
     * True only when an assigned (non-anarchy) territory-local or alliance government
     * governs the coordinate. Uncontained wilderness and anarchy land return false.
     */
    public boolean isEnvironmentallyProtected(String worldId, int blockX, int blockZ) {
        return isAssignedGoverned(worldId, blockX, blockZ);
    }

    /**
     * Whether {@code actorId} may interact with a block at the world location
     * (containers, doors, buttons, levers, beds, …). Same form-authority gate as
     * break/place; granted via {@link SovereignAction#INTERACT}.
     */
    public boolean canInteract(String worldId, int blockX, int blockZ, String actorId) {
        return canModify(worldId, blockX, blockZ, actorId, SovereignAction.INTERACT);
    }

    /**
     * Whether {@code actorId} may interact with an entity at the world location
     * (armor stands, item frames, animals with leads, vehicles, …).
     * Same form-authority gate as block interaction.
     */
    public boolean canInteractWithEntity(String worldId, int blockX, int blockZ, String actorId) {
        return canModify(worldId, blockX, blockZ, actorId, SovereignAction.INTERACT);
    }

    /**
     * Whether a claim boundary separates the two world locations — used for
     * cross-boundary pushes (pistons) and flows (lava/water) moving blocks or
     * entities out of governed land. No cross-boundary change is allowed.
     */
    public boolean crossesBoundary(String worldId, int fromX, int fromZ, int toX, int toZ) {
        return isEnvironmentallyProtected(worldId, fromX, fromZ) != isEnvironmentallyProtected(worldId, toX, toZ);
    }

    /**
     * Whether {@code attackerId} may damage {@code victimId} on a governed territory
     * (PvP and friendly-fire by territory).
     * <p>
     * Conservative model: inside governed (assigned non-anarchy) territory, damage
     * between any players is denied unless the attacker has formal authority. No
     * member/company distinction exists in the current data model; per-group flags
     * are future work. Uncontained land stays unrestricted.
     */
    public boolean allowsPvp(String worldId, int blockX, int blockZ, String attackerId, String victimId) {
        if (attackerId == null || victimId == null
                || attackerId.isBlank() || victimId.isBlank()) {
            return false;
        }
        if (attackerId.equals(victimId)) {
            return true;
        }
        GoverningBody body = governance.resolveAt(worldId, blockX, blockZ);
        if (body.kind() == GoverningBody.Kind.NONE || !body.hasAssignedGovernment()) {
            return true;
        }
        return PermissionRules.allows(body.government(), attackerId, SovereignAction.INTERACT);
    }

    /**
     * Whether {@code actorId} may be teleported into the territory (spawn/home/tpa
     * commands, plugins, portals/gateways). Denied for outsiders; authority holders
     * (owners) are never blocked from their own land.
     * <p>
     * Only applies to player-forced teleports. Respawns to a player's own bed /
     * respawn anchor never fire a {@code PlayerTeleportEvent}, so setting spawn
     * inside claims stays free. Foreign-territory home registration is not
     * expressible in the current model (no per-player home store); command-level
     * home/spawn plugins are covered via the {@code COMMAND}/{@code PLUGIN} causes.
     */
    public boolean canTeleportInto(String worldId, int blockX, int blockZ, String actorId) {
        GoverningBody body = governance.resolveAt(worldId, blockX, blockZ);
        if (body.kind() == GoverningBody.Kind.NONE || !body.hasAssignedGovernment()) {
            return true;
        }
        return PermissionRules.allows(body.government(), actorId, SovereignAction.INTERACT);
    }

    /**
     * Whether natural/hostile mob spawning should be denied at this location.
     * Same assigned-government eligibility as environmental protection.
     * Listener maps Bukkit spawn reasons; eggs/spawners/commands stay unrestricted.
     */
    public boolean blocksMobSpawn(String worldId, int blockX, int blockZ) {
        return isAssignedGoverned(worldId, blockX, blockZ);
    }

    /**
     * Whether entity-caused block changes and crop trampling should be denied here
     * (enderman/wither-style pickup/break, farmland physical change).
     * Same assigned-government eligibility as environmental protection.
     */
    public boolean blocksEntityGrief(String worldId, int blockX, int blockZ) {
        return isAssignedGoverned(worldId, blockX, blockZ);
    }

    /**
     * Location under an assigned (non-anarchy) territory-local or alliance government.
     */
    private boolean isAssignedGoverned(String worldId, int blockX, int blockZ) {
        GoverningBody body = governance.resolveAt(worldId, blockX, blockZ);
        if (body.kind() == GoverningBody.Kind.NONE) {
            return false;
        }
        return body.hasAssignedGovernment();
    }

    private boolean canModify(
            String worldId,
            int blockX,
            int blockZ,
            String actorId,
            SovereignAction action
    ) {
        GoverningBody body = governance.resolveAt(worldId, blockX, blockZ);
        if (body.kind() == GoverningBody.Kind.NONE) {
            return true;
        }
        if (!body.hasAssignedGovernment()) {
            // ANARCHY local government: no formal protection grants
            return true;
        }
        return PermissionRules.allows(body.government(), actorId, action);
    }

    /**
     * Formal authority for a sovereign action under the body governing a territory.
     */
    public boolean allowsOnTerritory(String territoryId, String actorId, SovereignAction action) {
        GoverningBody body = governance.resolveForTerritory(territoryId);
        return PermissionRules.allows(body.government(), actorId, action);
    }

    /**
     * Formal authority under the guild governing a holder (membership manage / policy).
     */
    public boolean allowsForHolder(String holderId, String actorId, SovereignAction action) {
        GoverningBody body = governance.resolveForHolder(holderId);
        return PermissionRules.allows(body.government(), actorId, action);
    }
}
