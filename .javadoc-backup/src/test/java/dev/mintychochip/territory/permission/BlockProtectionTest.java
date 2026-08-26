package dev.mintychochip.territory.permission;

import dev.mintychochip.guilds.Guild;
import dev.mintychochip.guilds.GuildToggles;
import dev.mintychochip.guilds.MemberPermissions;
import dev.mintychochip.guilds.alliances.Alliance;
import dev.mintychochip.territory.permission.SovereignAction;
import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.Government;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.model.ZoneType;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Block allow/deny through shipped GovernanceRegistry + PermissionRules path,
 * with guild (guild) and alliance (nation) governing bodies.
 */
class BlockProtectionTest {

    private static final MemberPermissions MEMBER_DEFAULT = MemberPermissions.of(List.of(
            SovereignAction.BREAK_BLOCK, SovereignAction.PLACE_BLOCK, SovereignAction.INTERACT));

    private TerritoryRegistry territories;
    private FakeGovernanceSource source;
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

    private static Guild guild(String id, Government government, List<String> members,
                                   GuildToggles toggles) {
        Map<String, MemberPermissions> perms = new java.util.HashMap<>();
        for (String m : members) {
            perms.put(m, MEMBER_DEFAULT);
        }
        return new Guild(id, id, government, members, toggles, perms);
    }

    @BeforeEach
    void setUp() {
        territories = new TerritoryRegistry();
        source = new FakeGovernanceSource();
        governance = new GovernanceRegistry(territories, source);
        protection = new BlockProtection(governance);
    }

    private void registerTerritory(String id, Boundary boundary, Government government) {
        territories.register(new Territory(id, id, "world", boundary, List.of(), ZoneType.WILDERNESS, government));
    }

    private void registerGuildGovernedTerritory(String territoryId, Boundary boundary,
                                                String guildId, Government localGov) {
        territories.register(new Territory(territoryId, territoryId, "world", boundary,
                List.of(), ZoneType.WILDERNESS, localGov, List.of(), guildId));
    }

    @Test
    void uncontainedWilderness_allowsEveryone() {
        assertTrue(protection.canBreak("world", 9000, 9000, "wanderer"));
        assertTrue(protection.canPlace("world", 9000, 9000, "wanderer"));
        assertTrue(protection.canInteract("world", 9000, 9000, "wanderer"));
    }

    @Test
    void anarchyLocalGovernment_noLockdown() {
        registerTerritory("an-land", square(100, 150), Government.anarchy());
        assertTrue(protection.canBreak("world", 125, 125, "anyone"));
        assertFalse(protection.isFireProtected("world", 125, 125));
    }

    @Test
    void territoryLocalMonarchy_seatLockdown() {
        registerTerritory("mon-land", square(0, 50), Government.monarchy("king:1"));
        assertTrue(protection.canBreak("world", 25, 25, "king:1"));
        assertFalse(protection.canBreak("world", 25, 25, "outsider"));
        assertFalse(protection.canPlace("world", 25, 25, "outsider"));
        assertFalse(protection.canInteract("world", 25, 25, "outsider"));
        // Territory-local stays environmentally protected (no guild toggles)
        assertTrue(protection.isFireProtected("world", 25, 25));
        assertTrue(protection.areExplosionsProtected("world", 25, 25));
        assertTrue(protection.blocksMobSpawn("world", 25, 25));
    }

    @Test
    void guildGoverned_authorityPasses_memberHasBasicActions_outsiderDenied() {
        source.putGuild(guild("everfall-town", Government.monarchy("mayor:1"),
                List.of("mayor:1", "resident:1"), GuildToggles.defaults()));
        registerGuildGovernedTerritory("everfall", square(0, 50), "everfall-town", Government.anarchy());

        // Authority (mayor = sovereign seat)
        assertTrue(protection.canBreak("world", 25, 25, "mayor:1"));
        // Member: guilds role default grants break/place/interact
        assertTrue(protection.canBreak("world", 25, 25, "resident:1"));
        assertTrue(protection.canPlace("world", 25, 25, "resident:1"));
        assertTrue(protection.canInteract("world", 25, 25, "resident:1"));
        // Outsider denied (guild not public)
        assertFalse(protection.canBreak("world", 25, 25, "outsider"));
        assertFalse(protection.canPlace("world", 25, 25, "outsider"));
    }

    @Test
    void guildGoverned_memberWithoutGrants_denied() {
        Guild guild = new Guild("everfall-town", "Everfall Town",
                Government.monarchy("mayor:1"), List.of("mayor:1", "resident:1"),
                GuildToggles.defaults(),
                Map.of("mayor:1", MEMBER_DEFAULT, "resident:1", MemberPermissions.none()));
        source.putGuild(guild);
        registerGuildGovernedTerritory("everfall", square(0, 50), "everfall-town", Government.anarchy());

        assertTrue(protection.canBreak("world", 25, 25, "mayor:1"));
        assertFalse(protection.canBreak("world", 25, 25, "resident:1"));
    }

    @Test
    void guildGoverned_anarchyForm_wildForEveryone() {
        // ANARCHY means no permission system: guild-governed land under an
        // anarchy-form guild is wild for members AND outsiders.
        source.putGuild(guild("an-town", Government.anarchy(),
                List.of("resident:1"), GuildToggles.defaults()));
        registerGuildGovernedTerritory("anland", square(0, 50), "an-town", Government.anarchy());

        assertTrue(protection.canBreak("world", 25, 25, "resident:1"));
        assertTrue(protection.canBreak("world", 25, 25, "outsider"));
        assertTrue(protection.canPlace("world", 25, 25, "outsider"));
        assertTrue(protection.canInteract("world", 25, 25, "outsider"));
    }

    @Test
    void guildGoverned_publicGuild_outsiderCanBuildAndInteractButNotBreak() {
        GuildToggles publicToggles = new GuildToggles(false, false, false, true, true);
        source.putGuild(guild("open-town", Government.monarchy("mayor:1"),
                List.of("mayor:1"), publicToggles));
        registerGuildGovernedTerritory("openland", square(50, 100), "open-town", Government.anarchy());

        assertTrue(protection.canPlace("world", 75, 75, "visitor"));
        assertTrue(protection.canInteract("world", 75, 75, "visitor"));
        assertFalse(protection.canBreak("world", 75, 75, "visitor"));
    }

    @Test
    void allianceGoverned_kingPasses_siblingMemberPasses_outsiderDenied() {
        source.putGuild(guild("everfall-town", Government.monarchy("mayor:1"),
                List.of("mayor:1", "resident:1"), GuildToggles.defaults()));
        source.putGuild(guild("sibling-town", Government.monarchy("mayor:2"),
                List.of("mayor:2", "sibling:1"), GuildToggles.defaults()));
        source.putAlliance(new Alliance("northern-pact", "Northern Pact",
                Government.monarchy("king:1"), List.of("everfall-town", "sibling-town")));
        registerGuildGovernedTerritory("everfall", square(0, 50), "everfall-town", Government.anarchy());

        // Alliance authority (king)
        assertTrue(protection.canBreak("world", 25, 25, "king:1"));
        // Member of the governing guild
        assertTrue(protection.canBreak("world", 25, 25, "resident:1"));
        // Member of a sibling nation guild keeps basic rights across the alliance
        assertTrue(protection.canPlace("world", 25, 25, "sibling:1"));
        // Outsider denied
        assertFalse(protection.canBreak("world", 25, 25, "outsider"));
    }

    @Test
    void allowsPvp_followsGuildToggle() {
        // PvP disabled (defaults) — authority may attack, members may not
        source.putGuild(guild("everfall-town", Government.monarchy("mayor:1"),
                List.of("mayor:1", "resident:1"), GuildToggles.defaults()));
        registerGuildGovernedTerritory("everfall", square(0, 50), "everfall-town", Government.anarchy());

        assertTrue(protection.allowsPvp("world", 25, 25, "mayor:1", "resident:1"));
        assertFalse(protection.allowsPvp("world", 25, 25, "resident:1", "mayor:1"));
        assertTrue(protection.allowsPvp("world", 25, 25, "resident:1", "resident:1")); // self
        // Uncontained unrestricted
        assertTrue(protection.allowsPvp("world", 9000, 9000, "a", "b"));
    }

    @Test
    void allowsPvp_enabledToggle_allowsEveryone() {
        source.putGuild(guild("warcamp", Government.monarchy("chief:1"),
                List.of("chief:1", "fighter:1"), new GuildToggles(true, false, false, true, false)));
        registerGuildGovernedTerritory("warcamp", square(50, 100), "warcamp", Government.anarchy());

        assertTrue(protection.allowsPvp("world", 75, 75, "fighter:1", "chief:1"));
        assertTrue(protection.allowsPvp("world", 75, 75, "outsider", "fighter:1"));
    }

    @Test
    void canTeleportInto_membersAndPublicAllowed() {
        source.putGuild(guild("everfall-town", Government.monarchy("mayor:1"),
                List.of("mayor:1", "resident:1"), GuildToggles.defaults()));
        registerGuildGovernedTerritory("everfall", square(0, 50), "everfall-town", Government.anarchy());

        assertTrue(protection.canTeleportInto("world", 25, 25, "mayor:1"));
        assertTrue(protection.canTeleportInto("world", 25, 25, "resident:1"));
        assertFalse(protection.canTeleportInto("world", 25, 25, "outsider"));

        source.putGuild(guild("open-town", Government.monarchy("mayor:1"),
                List.of("mayor:1"), new GuildToggles(false, false, false, true, true)));
        registerGuildGovernedTerritory("openland", square(50, 100), "open-town", Government.anarchy());
        assertTrue(protection.canTeleportInto("world", 75, 75, "visitor"));
    }

    @Test
    void environmentalFlags_followGuildToggles() {
        // fire off, explosions off, mobs on
        source.putGuild(guild("everfall-town", Government.monarchy("mayor:1"),
                List.of("mayor:1"), new GuildToggles(false, false, false, true, false)));
        registerGuildGovernedTerritory("everfall", square(0, 50), "everfall-town", Government.anarchy());

        assertTrue(protection.isFireProtected("world", 25, 25));
        assertTrue(protection.areExplosionsProtected("world", 25, 25));
        assertFalse(protection.blocksMobSpawn("world", 25, 25)); // mobs enabled

        // fire on, explosions on, mobs off
        source.putGuild(guild("wild-town", Government.monarchy("mayor:1"),
                List.of("mayor:1"), new GuildToggles(true, true, true, false, false)));
        registerGuildGovernedTerritory("wildland", square(50, 100), "wild-town", Government.anarchy());

        assertFalse(protection.isFireProtected("world", 75, 75));
        assertFalse(protection.areExplosionsProtected("world", 75, 75));
        assertTrue(protection.blocksMobSpawn("world", 75, 75));
        // Environmental protection (mechanical/boundary) still applies
        assertTrue(protection.isEnvironmentallyProtected("world", 75, 75));
    }

    @Test
    void guildMembershipManage_usesGuildGovernment() {
        source.putGuild(guild("builders", Government.oligarchy(List.of("c1", "c2")),
                List.of("c1", "c2", "m1"), GuildToggles.defaults()));

        assertTrue(protection.allowsForHolder("m1", "c1", SovereignAction.MANAGE_MEMBERSHIP));
        assertTrue(protection.allowsForHolder("m1", "c2", SovereignAction.MANAGE_MEMBERSHIP));
        assertFalse(protection.allowsForHolder("m1", "m1", SovereignAction.MANAGE_MEMBERSHIP));
        assertFalse(protection.allowsForHolder("unknown", "c1", SovereignAction.MANAGE_MEMBERSHIP));
    }

    @Test
    void allowsOnTerritory_formAuthorityOnly() {
        source.putGuild(guild("everfall-town", Government.monarchy("mayor:1"),
                List.of("mayor:1", "resident:1"), GuildToggles.defaults()));
        registerGuildGovernedTerritory("everfall", square(0, 50), "everfall-town", Government.anarchy());

        assertTrue(protection.allowsOnTerritory("everfall", "mayor:1", SovereignAction.SET_POLICY));
        assertFalse(protection.allowsOnTerritory("everfall", "resident:1", SovereignAction.SET_POLICY));
    }
}
