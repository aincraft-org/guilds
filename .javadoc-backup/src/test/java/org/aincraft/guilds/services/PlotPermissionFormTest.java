package org.aincraft.guilds.services;

import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.Government;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.model.ZoneType;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import org.aincraft.guilds.GuildsServiceTestFixture;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.services.impl.PermissionServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Government-form semantics of the plot gate (PermissionService location
 * checks — the second half of the AND with BlockProtection):
 * <ul>
 *   <li>ANARCHY — no permission system: everyone may act on guild land.</li>
 *   <li>DEMOCRACY — citizens share the land: resident build defaults apply.</li>
 *   <li>MONARCHY / OLIGARCHY — government-controlled: members need explicit
 *       grants for build/destroy; switch/item-use defaults stay.</li>
 *   <li>Plot ownership is honored under every form.</li>
 * </ul>
 */
class PlotPermissionFormTest {

    private static final String WORLD = "world";

    @TempDir
    Path tempDir;

    private GuildsServiceTestFixture.Services services;
    private PlotService plots;
    private GuildService guilds;
    private ResidentService residents;
    private PermissionService permissions;
    private PermissionServiceImpl permissionImpl;

    private UUID mayor;
    private UUID alice;
    private UUID carol;
    private UUID bob;
    private UUID outsider;

    private org.aincraft.guilds.models.Alliance pact;

    @BeforeEach
    void setUp() {
        services = GuildsServiceTestFixture.create(tempDir);
        plots = services.plotService();
        guilds = services.guildService();
        residents = services.residentService();
        permissions = services.permissionService();
        permissionImpl = (PermissionServiceImpl) permissions;

        mayor = UUID.randomUUID();
        alice = UUID.randomUUID();
        carol = UUID.randomUUID();
        bob = UUID.randomUUID();
        outsider = UUID.randomUUID();

        residents.createResident(mayor, "mayor");
        residents.createResident(alice, "alice");
        residents.createResident(carol, "carol");
        residents.createResident(bob, "bob");
        residents.createResident(outsider, "outsider");
        guilds.createGuild("Alpha", mayor);
        guilds.createGuild("Beta", mayor);
        assertTrue(guilds.addResidentToGuild("Alpha", alice));
        assertTrue(guilds.addResidentToGuild("Alpha", carol));
        assertTrue(guilds.addResidentToGuild("Beta", bob));
    }

    /** Alpha and Beta joined into alliance "Pact" (capital Alpha). */
    private void joinAlliance() {
        services.allianceService().createAlliance("Pact", guilds.getGuild("Alpha").orElseThrow(), mayor);
        pact = services.allianceService().getAllAlliances().get(0);
        services.allianceService().addGuild(pact, guilds.getGuild("Beta").orElseThrow().getId());
    }

    @AfterEach
    void tearDown() {
        if (services != null) {
            services.databaseManager().shutdown();
        }
    }

    /** Guild-owned plot at chunk (0,0) — blocks 0..15. */
    private GuildBlock guildOwnedPlot() {
        return plots.createGuildBlock(0, 0, WORLD, "Alpha");
    }

    private void setForm(String form) throws Exception {
        String guildId = guilds.getGuild("Alpha").orElseThrow().getId();
        try (Connection connection = services.databaseManager().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE guilds SET governance_form = ? WHERE id = ?")) {
            statement.setString(1, form);
            statement.setString(2, guildId);
            statement.executeUpdate();
        }
    }

    private void setPublic(boolean open) throws Exception {
        String guildId = guilds.getGuild("Alpha").orElseThrow().getId();
        try (Connection connection = services.databaseManager().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE guilds SET public_enabled = ? WHERE id = ?")) {
            statement.setBoolean(1, open);
            statement.setString(2, guildId);
            statement.executeUpdate();
        }
    }

    private void setAllianceForm(String form) throws Exception {
        try (Connection connection = services.databaseManager().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE alliances SET governance_form = ? WHERE id = ?")) {
            statement.setString(1, form);
            statement.setString(2, pact.getId());
            statement.executeUpdate();
        }
    }

    private void wireTerritory(String guildId, int min, int max) {
        TerritoryRegistry registry = new TerritoryRegistry();
        registry.register(new Territory("everfall", "Everfall", WORLD,
                Boundary.ofPolygon(List.of(
                        new BlockPos(min, min), new BlockPos(max, min),
                        new BlockPos(max, max), new BlockPos(min, max))),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), guildId));
        permissionImpl.setTerritoryRegistry(registry);
    }

    // ---- MONARCHY (default form): government-controlled land ----

    @Test
    void monarchyMember_cannotModifyGuildOwnedLandWithoutGrant() {
        guildOwnedPlot();
        assertFalse(permissions.canBuild(alice, 3, 3, WORLD));
        assertFalse(permissions.canDestroy(alice, 3, 3, WORLD));
    }

    @Test
    void monarchyMember_switchDefaultStays() {
        guildOwnedPlot();
        assertTrue(permissions.canSwitch(alice, 3, 3, WORLD),
                "switch/item-use defaults stay so towns remain usable");
    }

    @Test
    void monarchyMember_withExplicitGrant_canBuild() {
        guildOwnedPlot();
        assertTrue(permissions.grantPermission(alice, "build", "town", "Alpha", true));
        assertTrue(permissions.canBuild(alice, 3, 3, WORLD));
        assertFalse(permissions.canDestroy(alice, 3, 3, WORLD), "grant covers only the named flag");
    }

    @Test
    void monarchyOwner_buildsOnOwnedPlot_otherMemberDoesNot() {
        GuildBlock plot = guildOwnedPlot();
        plot.setOwnerId(alice);
        plots.updateGuildBlock(plot);

        assertTrue(permissions.canBuild(alice, 3, 3, WORLD), "ownership is honored under every form");
        assertTrue(permissions.canDestroy(alice, 3, 3, WORLD), "owner has absolute rights");
        assertFalse(permissions.canBuild(carol, 3, 3, WORLD), "fellow member needs a grant");
    }

    @Test
    void monarchyOutsider_cannotModifyGuildOwnedPlot() {
        guildOwnedPlot();
        assertFalse(permissions.canBuild(outsider, 3, 3, WORLD));
        assertFalse(permissions.canSwitch(outsider, 3, 3, WORLD));
    }

    // ---- DEMOCRACY: citizens share the land ----

    @Test
    void democracyMember_buildsOnGuildOwnedLand() throws Exception {
        guildOwnedPlot();
        setForm("DEMOCRACY");
        assertTrue(permissions.canBuild(alice, 3, 3, WORLD));
    }

    @Test
    void democracyOutsider_getsDefaultPlotPermissions() throws Exception {
        guildOwnedPlot();
        setForm("DEMOCRACY");
        setPublic(true); // the toggle gate admits non-residents only to public towns
        assertTrue(permissions.canBuild(outsider, 3, 3, WORLD),
                "democracy shares the commons (default plot permissions)");
    }

    // ---- ANARCHY: no permission system ----

    @Test
    void anarchy_everyoneActsOnGuildLand() throws Exception {
        guildOwnedPlot();
        setForm("ANARCHY");
        assertTrue(permissions.canBuild(alice, 3, 3, WORLD));
        assertTrue(permissions.canBuild(outsider, 3, 3, WORLD));
        assertTrue(permissions.canDestroy(outsider, 3, 3, WORLD));
    }

    // ---- Alliance-governed land follows the ALLIANCE's form ----

    @Test
    void allianceMonarchy_memberAndSiblingNeedGrants() {
        joinAlliance();
        guildOwnedPlot(); // Alpha's plot, inside the Pact

        assertFalse(permissions.canBuild(alice, 3, 3, WORLD), "Alpha member: no build default under monarchy");
        assertFalse(permissions.canBuild(bob, 3, 3, WORLD), "sibling-guild member: no build default under monarchy");
        assertTrue(permissions.canSwitch(bob, 3, 3, WORLD), "switch default stays for alliance members");

        // A grant in the sibling's OWN guild context is honored on alliance land.
        assertTrue(permissions.grantPermission(bob, "build", "town", "Beta", true));
        assertTrue(permissions.canBuild(bob, 3, 3, WORLD));
    }

    @Test
    void allianceDemocracy_membersAndSiblingsShareTheLand() throws Exception {
        joinAlliance();
        guildOwnedPlot();
        setAllianceForm("DEMOCRACY");

        assertTrue(permissions.canBuild(alice, 3, 3, WORLD), "member builds under democracy");
        assertTrue(permissions.canBuild(bob, 3, 3, WORLD), "sibling-guild member shares the commons");
    }

    @Test
    void allianceAnarchy_wildForEveryone() throws Exception {
        joinAlliance();
        guildOwnedPlot();
        setAllianceForm("ANARCHY");

        assertTrue(permissions.canBuild(alice, 3, 3, WORLD));
        assertTrue(permissions.canBuild(bob, 3, 3, WORLD));
        assertTrue(permissions.canBuild(outsider, 3, 3, WORLD));
        assertTrue(permissions.canDestroy(outsider, 3, 3, WORLD));
    }

    @Test
    void allianceMonarchy_plotOwnershipStillHonored() {
        joinAlliance();
        GuildBlock plot = guildOwnedPlot();
        plot.setOwnerId(bob);
        plots.updateGuildBlock(plot);

        assertTrue(permissions.canBuild(bob, 3, 3, WORLD), "owner keeps absolute rights under an alliance monarchy");
        assertFalse(permissions.canBuild(alice, 3, 3, WORLD));
    }

    // ---- Territory chunks without plot rows follow the same form policy ----

    @Test
    void noPlotTerritoryChunk_followsGovernmentForm() throws Exception {
        String guildId = guilds.getGuild("Alpha").orElseThrow().getId();
        wireTerritory(guildId, 0, 200); // block (50,50) -> chunk (3,3), no plot row

        assertFalse(permissions.canBuild(alice, 50, 50, WORLD), "monarchy: no default build on unclaimed chunks");
        assertTrue(permissions.canSwitch(alice, 50, 50, WORLD), "switch default stays");
        assertFalse(permissions.canBuild(outsider, 50, 50, WORLD), "closed guild: outsider denied");

        setForm("DEMOCRACY");
        assertTrue(permissions.canBuild(alice, 50, 50, WORLD));

        setForm("ANARCHY");
        assertTrue(permissions.canBuild(outsider, 50, 50, WORLD), "anarchy: wild even without a plot row");
    }

    @Test
    void noPlotTerritoryChunk_publicGuildFallbackForOutsiders() throws Exception {
        String guildId = guilds.getGuild("Alpha").orElseThrow().getId();
        wireTerritory(guildId, 0, 200);
        try (Connection connection = services.databaseManager().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE guilds SET public_enabled = ? WHERE id = ?")) {
            statement.setBoolean(1, true);
            statement.setString(2, guildId);
            statement.executeUpdate();
        }

        assertTrue(permissions.canBuild(outsider, 50, 50, WORLD), "public guild: build allowed");
        assertFalse(permissions.canDestroy(outsider, 50, 50, WORLD), "public guild: never break");
    }
}
