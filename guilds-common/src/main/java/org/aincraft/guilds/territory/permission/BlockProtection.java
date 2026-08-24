package org.aincraft.guilds.territory.permission;

import org.aincraft.guilds.territory.model.GovernmentForm;

import java.util.Objects;
import java.util.Optional;

/**
 * Domain block break/place and environmental protection using spatial resolve
 * and the governing body (guild, alliance, or territory-local).
 * <p>
 * Player break/place rules (layered, governance-first):
 * <ul>
 *   <li>Uncontained wilderness (no territory) — allow (no governing body).</li>
 *   <li>Territory/alliance with {@code ANARCHY} or unassigned government — allow
 *       (no formal authority grants apply; no seat-based lockdown).</li>
 *   <li>Assigned government — formal authority holders (the form's electorate:
 *       sovereign / councilors / representatives) always pass. Guild-governed
 *       land (guild or nation) then falls through to the guilds permission
 *       model: members are evaluated by their effective permissions (global
 *       bypass → explicit grants → role defaults granting the basic build
 *       actions), alliance sibling-guild members keep their basic rights across
 *       the alliance, and outsiders are allowed only when the guild is public
 *       (build/interact, never break). Territory-local government stays a pure
 *       seat lockdown for non-authority actors.</li>
 * </ul>
 * Environmental protection:
 * <ul>
 *   <li>Fire burn/spread/ignite — {@link #isFireProtected}: governed land with
 *       fire disabled by the governing guild's toggle (territory-local stays protected).</li>
 *   <li>Explosions — {@link #areExplosionsProtected}: same, via the explosions toggle.</li>
 *   <li>Natural/hostile mob spawn — {@link #blocksMobSpawn}: same, via the mobs toggle.</li>
 *   <li>Entity-caused block change and crop trampling — {@link #blocksEntityGrief}.</li>
 *   <li>Mechanical transfers (hoppers) and cross-boundary pushes — {@link #isEnvironmentallyProtected}.</li>
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
     * Whether {@code actorId} may interact with a block at the world location
     * (containers, doors, buttons, levers, beds, …).
     */
    public boolean canInteract(String worldId, int blockX, int blockZ, String actorId) {
        return canModify(worldId, blockX, blockZ, actorId, SovereignAction.INTERACT);
    }

    /**
     * Whether {@code actorId} may interact with an entity at the world location
     * (armor stands, item frames, animals with leads, vehicles, …).
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
     * Uncontained land and anarchy stay unrestricted. Under an assigned
     * government, formal authority holders may always attack; on guild-governed
     * land everyone else may fight when the governing guild's PvP toggle is on.
     * Territory-local government stays conservative (authority attackers only).
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
        if (PermissionRules.allows(body.government(), attackerId, SovereignAction.INTERACT)) {
            return true;
        }
        Optional<GuildBody> guild = governance.governingGuildAt(worldId, blockX, blockZ);
        return guild.map(g -> g.toggles().pvpEnabled()).orElse(false);
    }

    /**
     * Whether {@code actorId} may be teleported into the territory (spawn/home/tpa
     * commands, plugins, portals/gateways). Authority holders and guild members
     * are never blocked from their own land; public guilds admit outsiders.
     * <p>
     * Only applies to player-forced teleports. Respawns to a player's own bed /
     * respawn anchor never fire a {@code PlayerTeleportEvent}, so setting spawn
     * inside claims stays free. Foreign-territory home registration is not
     * expressible in the current model (no per-player home store).
     */
    public boolean canTeleportInto(String worldId, int blockX, int blockZ, String actorId) {
        GoverningBody body = governance.resolveAt(worldId, blockX, blockZ);
        if (body.kind() == GoverningBody.Kind.NONE || !body.hasAssignedGovernment()) {
            return true;
        }
        if (PermissionRules.allows(body.government(), actorId, SovereignAction.INTERACT)) {
            return true;
        }
        Optional<GuildBody> guild = governance.governingGuildAt(worldId, blockX, blockZ);
        if (guild.isEmpty()) {
            return false;
        }
        return guild.get().containsMember(actorId) || guild.get().isPublic();
    }

    /**
     * Whether fire may be blocked from burning, spreading, or igniting at this
     * location. Governed land is fire-protected unless the governing guild's
     * fire toggle is enabled (territory-local stays protected).
     */
    public boolean isFireProtected(String worldId, int blockX, int blockZ) {
        if (!isAssignedGoverned(worldId, blockX, blockZ)) {
            return false;
        }
        return governance.governingGuildAt(worldId, blockX, blockZ)
                .map(g -> !g.toggles().fireEnabled())
                .orElse(true);
    }

    /**
     * Whether explosions are blocked from damaging this location. Governed land
     * is explosion-protected unless the governing guild's explosions toggle is
     * enabled (territory-local stays protected).
     */
    public boolean areExplosionsProtected(String worldId, int blockX, int blockZ) {
        if (!isAssignedGoverned(worldId, blockX, blockZ)) {
            return false;
        }
        return governance.governingGuildAt(worldId, blockX, blockZ)
                .map(g -> !g.toggles().explosionsEnabled())
                .orElse(true);
    }

    /**
     * Whether blocks at the world location are environmentally protected
     * (mechanical transfers, boundary crossings, entity grief). True only when
     * an assigned (non-anarchy) government governs the coordinate.
     */
    public boolean isEnvironmentallyProtected(String worldId, int blockX, int blockZ) {
        return isAssignedGoverned(worldId, blockX, blockZ);
    }

    /**
     * Whether natural/hostile mob spawning should be denied at this location.
     * Governed land blocks spawns unless the governing guild's mobs toggle is
     * enabled (territory-local stays protected). Listener maps Bukkit spawn
     * reasons; eggs/spawners/commands stay unrestricted.
     */
    public boolean blocksMobSpawn(String worldId, int blockX, int blockZ) {
        if (!isAssignedGoverned(worldId, blockX, blockZ)) {
            return false;
        }
        return governance.governingGuildAt(worldId, blockX, blockZ)
                .map(g -> !g.toggles().mobsEnabled())
                .orElse(true);
    }

    /**
     * Whether entity-caused block changes and crop trampling should be denied here
     * (enderman/wither-style pickup/break, farmland physical change).
     */
    public boolean blocksEntityGrief(String worldId, int blockX, int blockZ) {
        return isAssignedGoverned(worldId, blockX, blockZ);
    }

    /**
     * Location under an assigned (non-anarchy) government.
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
        // ANARCHY government (territory-local or guild): no permission system
        // at all — land is wild for everyone.
        if (body.government().form() == GovernmentForm.ANARCHY) {
            return true;
        }
        // Governance layer: the form's electorate always passes
        if (PermissionRules.allows(body.government(), actorId, action)) {
            return true;
        }
        Optional<GuildBody> governing = governance.governingGuildAt(worldId, blockX, blockZ);
        if (governing.isEmpty()) {
            // Territory-local government: seat-only lockdown
            return false;
        }
        GuildBody guild = governing.get();
        Optional<MemberPermissions> perms = guild.permissionsOf(actorId);
        if (perms.isPresent()) {
            return perms.get().allows(action);
        }
        // Alliance sibling-guild members keep their basic rights across the alliance.
        if (body.kind() == GoverningBody.Kind.ALLIANCE) {
            AllianceBody alliance = body.allianceBody().orElseThrow();
            for (String guildId : alliance.memberGuildIds()) {
                Optional<GuildBody> sibling = governance.source().guild(guildId);
                if (sibling.isEmpty()) {
                    continue;
                }
                Optional<MemberPermissions> siblingPerms = sibling.get().permissionsOf(actorId);
                if (siblingPerms.isPresent()) {
                    return siblingPerms.get().allows(action);
                }
            }
        }
        // Outsider: public guilds mirror guilds guild-owned plot defaults
        // (build/switch/item-use allowed, destroy not).
        return guild.isPublic() && action != SovereignAction.BREAK_BLOCK;
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
