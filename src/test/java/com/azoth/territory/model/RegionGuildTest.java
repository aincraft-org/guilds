package com.azoth.territory.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Region guilds are formed with an assigned government (not ANARCHY).
 */
class RegionGuildTest {

    @Test
    void form_requiresAssignedGovernment() {
        Government monarchy = Government.monarchy("player:guild-master");
        RegionGuild guild = RegionGuild.form("iron-hand", "Iron Hand", monarchy);

        assertEquals("iron-hand", guild.id());
        assertEquals("Iron Hand", guild.name());
        assertEquals(GovernmentForm.MONARCHY, guild.governmentForm());
        assertEquals(monarchy, guild.government());
        assertTrue(guild.government().isAssigned());
        assertTrue(guild.memberIds().isEmpty());
    }

    @Test
    void form_withMembers_preservesOrderAndDropsBlanks() {
        RegionGuild guild = RegionGuild.form(
                "builders",
                "Builders Guild",
                Government.oligarchy(List.of("c1", "c2", "c3")),
                Arrays.asList("player:a", "  ", "player:b", null, "player:a")
        );

        assertEquals(GovernmentForm.OLIGARCHY, guild.governmentForm());
        assertEquals(List.of("player:a", "player:b"), guild.memberIds());
    }

    @Test
    void form_rejectsNoneOrNullGovernment() {
        assertThrows(IllegalArgumentException.class,
                () -> RegionGuild.form("g1", "G", Government.anarchy()));
        assertThrows(IllegalArgumentException.class,
                () -> RegionGuild.form("g1", "G", null));
    }

    @Test
    void form_rejectsBlankId() {
        assertThrows(IllegalArgumentException.class,
                () -> RegionGuild.form("  ", "Name", Government.monarchy("d1")));
        assertThrows(IllegalArgumentException.class,
                () -> RegionGuild.form(null, "Name", Government.monarchy("d1")));
    }

    @Test
    void blankName_defaultsToId() {
        RegionGuild guild = RegionGuild.form("lone-guild", "  ", Government.oligarchy(List.of("c1", "c2")));
        assertEquals("lone-guild", guild.name());
    }

    @Test
    void withGovernment_replacesAssignedGovernment() {
        RegionGuild guild = RegionGuild.form(
                "g", "G", Government.monarchy("k1"), List.of("m1")
        );
        RegionGuild next = guild.withGovernment(Government.democracy(List.of("r1", "r2")));
        assertEquals(GovernmentForm.DEMOCRACY, next.governmentForm());
        assertEquals(List.of("m1"), next.memberIds());
        assertEquals("g", next.id());
    }

    @Test
    void withGovernment_rejectsNone() {
        RegionGuild guild = RegionGuild.form("g", "G", Government.monarchy("k1"));
        assertThrows(IllegalArgumentException.class,
                () -> guild.withGovernment(Government.anarchy()));
    }

    @Test
    void withMember_addsAndIsIdempotent() {
        RegionGuild guild = RegionGuild.form("g", "G", Government.oligarchy(List.of("c1", "c2")));
        RegionGuild with = guild.withMember("player:1").withMember("player:1");
        assertEquals(List.of("player:1"), with.memberIds());
        assertFalse(guild.memberIds().contains("player:1"));
    }

    @Test
    void equals_byValue() {
        Government g = Government.monarchy("k");
        RegionGuild a = RegionGuild.form("id", "Name", g, List.of("m1"));
        RegionGuild b = RegionGuild.form("id", "Name", g, List.of("m1"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
