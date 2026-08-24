package org.aincraft.guilds.territory.persist;

import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.Government;
import org.aincraft.guilds.territory.model.Policy;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.model.Zone;
import org.aincraft.guilds.territory.model.ZoneType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Governing-guild binding: JSON round trip and preservation across
 * zone/policy/government mutations.
 */
class TerritoryBindingJsonTest {

    private static Boundary square(int min, int max) {
        return Boundary.ofPolygon(List.of(
                new BlockPos(min, min),
                new BlockPos(max, min),
                new BlockPos(max, max),
                new BlockPos(min, max)
        ));
    }

    @Test
    void binding_roundTripsThroughJson() {
        TerritoryJson codec = new TerritoryJson();
        Territory bound = new Territory("everfall", "Everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town");

        Territory back = codec.fromJsonString(codec.toJson(bound).toString());

        assertEquals(bound, back);
        assertEquals("everfall-town", back.governedByGuildId().orElseThrow());
    }

    @Test
    void binding_absentByDefault() {
        TerritoryJson codec = new TerritoryJson();
        Territory plain = new Territory("freehold", "Freehold", "world", square(0, 100));

        Territory back = codec.fromJsonString(codec.toJson(plain).toString());

        assertFalse(back.governedByGuildId().isPresent());
        assertEquals(plain, back);
    }

    @Test
    void binding_clearedByWithoutGoverningGuild() {
        Territory bound = new Territory("everfall", "Everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town");

        Territory cleared = bound.withoutGoverningGuild();

        assertFalse(cleared.governedByGuildId().isPresent());
        assertTrue(bound.governedByGuildId().isPresent());
    }

    @Test
    void binding_preservedAcrossZoneMutation() {
        Territory bound = new Territory("everfall", "Everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town");

        Territory withZone = bound.withZone(new Zone("plot-1", "Plot 1", ZoneType.CLAIMABLE,
                square(10, 20), 10));
        Territory withoutZone = withZone.withoutZone("plot-1");

        assertEquals("everfall-town", withZone.governedByGuildId().orElseThrow());
        assertEquals("everfall-town", withoutZone.governedByGuildId().orElseThrow());
    }

    @Test
    void binding_preservedAcrossPolicyAndGovernmentMutations() {
        Territory bound = new Territory("everfall", "Everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.monarchy("local:1"), List.of(), "everfall-town");

        Territory withPolicy = bound.proposePolicy("tax", "Tax Reform", "body",
                "local:1", 1_000L);
        Territory rebind = withPolicy.withGovernment(Government.democracy(List.of("a", "b")));

        assertTrue(withPolicy.policy("tax").isPresent());
        assertEquals("everfall-town", rebind.governedByGuildId().orElseThrow());
    }

    @Test
    void binding_preservedAcrossRebind() {
        Territory bound = new Territory("everfall", "Everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "town-a");

        Territory rebound = bound.withGoverningGuild("town-b");

        assertEquals("town-b", rebound.governedByGuildId().orElseThrow());
        assertEquals("town-a", bound.governedByGuildId().orElseThrow());
        assertEquals(List.of(), rebound.policies());
    }

    @Test
    void binding_blankGuildIdTreatsAsUnbound() {
        Territory t = new Territory("everfall", "Everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "  ");
        assertFalse(t.governedByGuildId().isPresent());
    }
}
