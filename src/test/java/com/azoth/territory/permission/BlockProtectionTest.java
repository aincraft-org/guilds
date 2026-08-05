package com.azoth.territory.permission;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.RegionGuild;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.TerritoryAlliance;
import com.azoth.territory.model.ZoneType;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Block allow/deny through shipped GovernanceRegistry + PermissionRules path.
 */
class BlockProtectionTest {

    private TerritoryRegistry territories;
    private GovernanceRegistry governance;
    private BlockProtection protection;

    private static Boundary square(int min, int max) {
        return Boundary.ofPolygon(List.of(
                new BlockPos(min, min),
                new BlockPos(max, min),
                new BlockPos(max, max),
                new BlockPos(min, max)
        ));
    }

    @BeforeEach
    void setUp() {
        territories = new TerritoryRegistry();
        governance = new GovernanceRegistry(territories);
        protection = new BlockProtection(governance);
    }

    @Test
    void uncontained_allowsAnyone() {
        assertTrue(protection.canBreak("world", 0, 0, "stranger"));
        assertTrue(protection.canPlace("world", 0, 0, "stranger"));
    }

    @Test
    void anarchyTerritory_noFormalLockdown_allowsAnyone() {
        territories.register(new Territory(
                "free", "Free", "world", square(0, 50),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));
        assertTrue(protection.canBreak("world", 10, 10, "outsider"));
        assertTrue(protection.canPlace("world", 10, 10, "outsider"));
        assertFalse(protection.allowsOnTerritory("free", "outsider", SovereignAction.SET_POLICY));
    }

    @Test
    void monarchyAlliance_sovereignCanBreakOutsiderCannot() {
        territories.register(new Territory(
                "crownlands", "Crownlands", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));
        governance.putAlliance(TerritoryAlliance.form(
                "crown-pact",
                "Crown Pact",
                Government.monarchy("king:arthur"),
                List.of("crownlands")
        ));

        assertTrue(protection.canBreak("world", 40, 40, "king:arthur"));
        assertTrue(protection.canPlace("world", 40, 40, "king:arthur"));
        assertFalse(protection.canBreak("world", 40, 40, "peasant:bob"));
        assertFalse(protection.canPlace("world", 40, 40, "peasant:bob"));
        assertTrue(protection.allowsOnTerritory(
                "crownlands", "king:arthur", SovereignAction.SET_POLICY));
        assertFalse(protection.allowsOnTerritory(
                "crownlands", "peasant:bob", SovereignAction.MANAGE_MEMBERSHIP));
    }

    @Test
    void oligarchyLocal_councilorCanOutsiderCannot() {
        territories.register(new Territory(
                "council-land", "Council", "world", square(0, 80),
                List.of(), ZoneType.WILDERNESS,
                Government.oligarchy(List.of("c1", "c2", "c3"))
        ));

        assertTrue(protection.canBreak("world", 20, 20, "c2"));
        assertFalse(protection.canBreak("world", 20, 20, "raider"));
        assertTrue(protection.canPlace("world", 20, 20, "c1"));
        assertFalse(protection.canPlace("world", 20, 20, "raider"));
    }

    @Test
    void democracy_representativeCanBreak() {
        territories.register(new Territory(
                "free-city", "Free City", "world", square(0, 60),
                List.of(), ZoneType.WILDERNESS,
                Government.democracy(3, List.of("r1", "r2"), null)
        ));
        assertTrue(protection.canBreak("world", 5, 5, "r1"));
        assertTrue(protection.canPlace("world", 5, 5, "r2"));
        assertFalse(protection.canBreak("world", 5, 5, "tourist"));
    }

    @Test
    void guildMembershipManage_usesGuildGovernment() {
        governance.putGuild(RegionGuild.form(
                "builders",
                "Builders",
                Government.oligarchy(List.of("c1", "c2")),
                List.of("player:member")
        ));
        assertTrue(protection.allowsForHolder(
                "player:member", "c1", SovereignAction.MANAGE_MEMBERSHIP));
        assertFalse(protection.allowsForHolder(
                "player:member", "player:member", SovereignAction.MANAGE_MEMBERSHIP));
        assertFalse(protection.allowsForHolder(
                "player:member", "outsider", SovereignAction.MANAGE_MEMBERSHIP));
    }

    @Test
    void environmental_uncontained_notProtected() {
        assertFalse(protection.isEnvironmentallyProtected("world", 0, 0));
    }

    @Test
    void environmental_anarchyTerritory_notProtected() {
        territories.register(new Territory(
                "free", "Free", "world", square(0, 50),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));
        assertFalse(protection.isEnvironmentallyProtected("world", 10, 10));
    }

    @Test
    void environmental_monarchyLocal_protected() {
        territories.register(new Territory(
                "crown", "Crown", "world", square(0, 80),
                List.of(), ZoneType.WILDERNESS, Government.monarchy("king:1")
        ));
        assertTrue(protection.isEnvironmentallyProtected("world", 20, 20));
        // Outside boundary remains unprotected
        assertFalse(protection.isEnvironmentallyProtected("world", 200, 200));
    }

    @Test
    void environmental_oligarchyLocal_protected() {
        territories.register(new Territory(
                "council-land", "Council", "world", square(0, 80),
                List.of(), ZoneType.WILDERNESS,
                Government.oligarchy(List.of("c1", "c2", "c3"))
        ));
        assertTrue(protection.isEnvironmentallyProtected("world", 15, 15));
    }

    @Test
    void environmental_allianceOverridesAnarchyLocal_protected() {
        territories.register(new Territory(
                "crownlands", "Crownlands", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));
        // Local is anarchy → not protected until alliance is assigned
        assertFalse(protection.isEnvironmentallyProtected("world", 40, 40));

        governance.putAlliance(TerritoryAlliance.form(
                "crown-pact",
                "Crown Pact",
                Government.monarchy("king:arthur"),
                List.of("crownlands")
        ));
        assertTrue(protection.isEnvironmentallyProtected("world", 40, 40));
    }

    @Test
    void environmental_democracyLocal_protected() {
        territories.register(new Territory(
                "free-city", "Free City", "world", square(0, 60),
                List.of(), ZoneType.WILDERNESS,
                Government.democracy(3, List.of("r1", "r2"), null)
        ));
        assertTrue(protection.isEnvironmentallyProtected("world", 5, 5));
    }

    @Test
    void mobSpawn_uncontainedAndAnarchy_notBlocked() {
        assertFalse(protection.blocksMobSpawn("world", 0, 0));

        territories.register(new Territory(
                "free", "Free", "world", square(0, 50),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));
        assertFalse(protection.blocksMobSpawn("world", 10, 10));
    }

    @Test
    void mobSpawn_assignedLocal_blocked() {
        territories.register(new Territory(
                "crown", "Crown", "world", square(0, 80),
                List.of(), ZoneType.WILDERNESS, Government.monarchy("king:1")
        ));
        assertTrue(protection.blocksMobSpawn("world", 20, 20));
        assertFalse(protection.blocksMobSpawn("world", 200, 200));
    }

    @Test
    void mobSpawn_oligarchyLocal_blocked() {
        territories.register(new Territory(
                "council-land", "Council", "world", square(0, 80),
                List.of(), ZoneType.WILDERNESS,
                Government.oligarchy(List.of("c1", "c2", "c3"))
        ));
        assertTrue(protection.blocksMobSpawn("world", 15, 15));
    }

    @Test
    void mobSpawn_allianceGoverned_blocked() {
        territories.register(new Territory(
                "crownlands", "Crownlands", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));
        assertFalse(protection.blocksMobSpawn("world", 40, 40));

        governance.putAlliance(TerritoryAlliance.form(
                "crown-pact",
                "Crown Pact",
                Government.monarchy("king:arthur"),
                List.of("crownlands")
        ));
        assertTrue(protection.blocksMobSpawn("world", 40, 40));
    }

    @Test
    void entityGrief_uncontainedAndAnarchy_notBlocked() {
        assertFalse(protection.blocksEntityGrief("world", 0, 0));

        territories.register(new Territory(
                "free", "Free", "world", square(0, 50),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));
        assertFalse(protection.blocksEntityGrief("world", 10, 10));
    }

    @Test
    void entityGrief_assignedLocal_blocked() {
        territories.register(new Territory(
                "crown", "Crown", "world", square(0, 80),
                List.of(), ZoneType.WILDERNESS, Government.monarchy("king:1")
        ));
        assertTrue(protection.blocksEntityGrief("world", 20, 20));
        // Outside remains unrestricted
        assertFalse(protection.blocksEntityGrief("world", 200, 200));
    }

    @Test
    void entityGrief_allianceGoverned_blocked() {
        territories.register(new Territory(
                "crownlands", "Crownlands", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));
        assertFalse(protection.blocksEntityGrief("world", 40, 40));

        governance.putAlliance(TerritoryAlliance.form(
                "crown-pact",
                "Crown Pact",
                Government.monarchy("king:arthur"),
                List.of("crownlands")
        ));
        assertTrue(protection.blocksEntityGrief("world", 40, 40));
    }

    @Test
    void spawnAndEntityGrief_shareAssignedEligibilityWithEnvironmental() {
        territories.register(new Territory(
                "crown", "Crown", "world", square(0, 50),
                List.of(), ZoneType.WILDERNESS, Government.monarchy("king:1")
        ));
        // Same resolve path: assigned → all three true; uncontained → all false
        assertTrue(protection.isEnvironmentallyProtected("world", 10, 10));
        assertTrue(protection.blocksMobSpawn("world", 10, 10));
        assertTrue(protection.blocksEntityGrief("world", 10, 10));
        assertFalse(protection.isEnvironmentallyProtected("world", 999, 999));
        assertFalse(protection.blocksMobSpawn("world", 999, 999));
        assertFalse(protection.blocksEntityGrief("world", 999, 999));
    }
}
