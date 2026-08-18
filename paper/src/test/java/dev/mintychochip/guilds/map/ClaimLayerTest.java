package dev.mintychochip.guilds.map;

import de.flog99.mapgui.Screen;
import dev.mintychochip.guilds.gui.GuildClaimScreen;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.GuildBlock;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for claim layer. */
class ClaimLayerTest {

    /** The world constant. */
    private static final String WORLD = "world";
    /** The center x constant. */
    private static final int CENTER_X = 10;
    /** The center z constant. */
    private static final int CENTER_Z = 20;

    /** Performs the classifies wilderness when no plot exists operation. */
    @Test
    void classifiesWildernessWhenNoPlotExists() {
        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, "Alpha", 1,
                (x, z, world) -> Optional.empty(),
                id -> Optional.empty());

        ClaimLayer.Cell wilderness = layer.cellAt(CENTER_X + 1, CENTER_Z).orElseThrow();
        assertEquals(ClaimLayer.Kind.WILDERNESS, wilderness.kind());
        assertEquals(CENTER_X + 1, wilderness.chunkX());
        assertEquals(CENTER_Z, wilderness.chunkZ());
    }

    /** Performs the classifies own guild when plot guild matches viewer operation. */
    @Test
    void classifiesOwnGuildWhenPlotGuildMatchesViewer() {
        Guild own = new Guild("Alpha", UUID.randomUUID());
        GuildBlock plot = new GuildBlock(CENTER_X + 1, CENTER_Z, WORLD, own.getId());

        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, "Alpha", 1,
                (x, z, world) -> x == plot.getX() && z == plot.getZ() && WORLD.equals(world)
                        ? Optional.of(plot) : Optional.empty(),
                id -> own.getId().equals(id) ? Optional.of(own) : Optional.empty());

        assertEquals(ClaimLayer.Kind.OWN_GUILD, layer.cellAt(plot.getX(), plot.getZ()).orElseThrow().kind());
    }

    /** Performs the classifies other guild when plot guild differs from viewer operation. */
    @Test
    void classifiesOtherGuildWhenPlotGuildDiffersFromViewer() {
        Guild other = new Guild("Beta", UUID.randomUUID());
        GuildBlock plot = new GuildBlock(CENTER_X, CENTER_Z + 1, WORLD, other.getId());

        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, "Alpha", 1,
                (x, z, world) -> x == plot.getX() && z == plot.getZ() && WORLD.equals(world)
                        ? Optional.of(plot) : Optional.empty(),
                id -> other.getId().equals(id) ? Optional.of(other) : Optional.empty());

        assertEquals(ClaimLayer.Kind.OTHER_GUILD, layer.cellAt(plot.getX(), plot.getZ()).orElseThrow().kind());
    }

    /** Performs the classifies center even when that chunk is claimed operation. */
    @Test
    void classifiesCenterEvenWhenThatChunkIsClaimed() {
        Guild own = new Guild("Alpha", UUID.randomUUID());
        GuildBlock plot = new GuildBlock(CENTER_X, CENTER_Z, WORLD, own.getId());

        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, "Alpha", 1,
                (x, z, world) -> Optional.of(plot),
                id -> Optional.of(own));

        ClaimLayer.Cell center = layer.cellAt(CENTER_X, CENTER_Z).orElseThrow();
        assertEquals(ClaimLayer.Kind.CENTER, center.kind());
        assertEquals(CENTER_X, center.chunkX());
        assertEquals(CENTER_Z, center.chunkZ());
    }

    /** Performs the classifies unknown guild id as other guild operation. */
    @Test
    void classifiesUnknownGuildIdAsOtherGuild() {
        GuildBlock plot = new GuildBlock(CENTER_X - 1, CENTER_Z, WORLD, "missing-id");

        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, "Alpha", 1,
                (x, z, world) -> x == plot.getX() && z == plot.getZ()
                        ? Optional.of(plot) : Optional.empty(),
                id -> Optional.empty());

        assertEquals(ClaimLayer.Kind.OTHER_GUILD, layer.cellAt(plot.getX(), plot.getZ()).orElseThrow().kind());
    }

    /** Performs the classifies claimed chunk as other when viewer has no guild operation. */
    @Test
    void classifiesClaimedChunkAsOtherWhenViewerHasNoGuild() {
        Guild claimed = new Guild("Alpha", UUID.randomUUID());
        GuildBlock plot = new GuildBlock(CENTER_X + 1, CENTER_Z - 1, WORLD, claimed.getId());

        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, null, 1,
                (x, z, world) -> x == plot.getX() && z == plot.getZ()
                        ? Optional.of(plot) : Optional.empty(),
                id -> claimed.getId().equals(id) ? Optional.of(claimed) : Optional.empty());

        assertEquals(ClaimLayer.Kind.OTHER_GUILD, layer.cellAt(plot.getX(), plot.getZ()).orElseThrow().kind());
    }

    /** Performs the covers every chunk in the radius operation. */
    @Test
    void coversEveryChunkInTheRadius() {
        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, "Alpha", 2,
                (x, z, world) -> Optional.empty(),
                id -> Optional.empty());

        assertEquals(25, layer.cells().size());
        assertTrue(layer.cellAt(CENTER_X - 2, CENTER_Z - 2).isPresent());
        assertTrue(layer.cellAt(CENTER_X + 2, CENTER_Z + 2).isPresent());
    }

    /** Performs the retired ascii renderer class is absent operation. */
    @Test
    void retiredAsciiRendererClassIsAbsent() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("dev.mintychochip.guilds.utils.MapRenderer"));
    }

    /**
     * Performs the claim screen is amap gui screen that paints the classifier operation.
     * @throws Exception if an error occurs
     */
    @Test
    void claimScreenIsAMapGuiScreenThatPaintsTheClassifier() throws Exception {
        assertTrue(Screen.class.isAssignableFrom(GuildClaimScreen.class));
        assertEquals(ClaimLayer.class, GuildClaimScreen.class
                .getDeclaredMethod("currentLayer")
                .getReturnType());
    }
}
