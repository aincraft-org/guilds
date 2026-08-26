package dev.mintychochip.guilds.models;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumer of the portable guilds domain types published from {@code api}.
 * Constructs real shipped models and asserts the values that were passed in.
 */
class GuildDomainConsumerTest {

    /** Performs the constructs guild resident and permission set with passed values operation. */
    @Test
    void constructsGuildResidentAndPermissionSetWithPassedValues() {
        UUID mayor = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Guild guild = new Guild("Iron League", mayor);
        guild.setId("guild-iron");
        guild.setGuildLevel(4);
        guild.setTechPoints(7);
        guild.setActiveProjectId("better_storage");

        Resident resident = new Resident(mayor, "MayorAda");
        resident.joinGuild("Iron League");
        resident.setPermission("guilds.mayor", true);

        PermissionSet permissions = new PermissionSet();
        permissions.grantPermission(GuildPermission.BUILD);
        permissions.grantPermission(GuildPermission.CLAIM);

        assertEquals("Iron League", guild.getName());
        assertEquals(mayor, guild.getMayorUuid());
        assertEquals("guild-iron", guild.getId());
        assertEquals(4, guild.getGuildLevel());
        assertEquals(7, guild.getTechPoints());
        assertEquals("better_storage", guild.getActiveProjectId());
        assertTrue(guild.isResident(mayor));

        assertEquals(mayor, resident.getUuid());
        assertEquals("MayorAda", resident.getName());
        assertEquals("Iron League", resident.getGuild());
        assertTrue(resident.hasPermission("guilds.mayor"));

        assertTrue(permissions.hasPermission(GuildPermission.BUILD));
        assertTrue(permissions.hasPermission(GuildPermission.CLAIM));
        assertFalse(permissions.hasPermission(GuildPermission.KICK));
    }
}
