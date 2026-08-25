package org.aincraft.guilds.map;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaimLayerTest {

    private static final String WORLD = "world";
    private static final int CENTER_X = 10;
    private static final int CENTER_Z = 20;

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

    @Test
    void classifiesCenterEvenWhenThatChunkIsClaimed() {
        Guild own = new Guild("Alpha", UUID.randomUUID());
        GuildBlock plot = new GuildBlock(CENTER_X, CENTER_Z, WORLD, own.getId());
        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, "Alpha", 1,
                (x, z, world) -> Optional.of(plot),
                id -> Optional.of(own));
        assertEquals(ClaimLayer.Kind.CENTER, layer.cellAt(CENTER_X, CENTER_Z).orElseThrow().kind());
    }

    @Test
    void classifiesUnknownGuildIdAsOtherGuild() {
        GuildBlock plot = new GuildBlock(CENTER_X - 1, CENTER_Z, WORLD, "missing-id");
        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, "Alpha", 1,
                (x, z, world) -> x == plot.getX() && z == plot.getZ() ? Optional.of(plot) : Optional.empty(),
                id -> Optional.empty());
        assertEquals(ClaimLayer.Kind.OTHER_GUILD, layer.cellAt(plot.getX(), plot.getZ()).orElseThrow().kind());
    }

    @Test
    void classifiesClaimedChunkAsOtherWhenViewerHasNoGuild() {
        Guild claimed = new Guild("Alpha", UUID.randomUUID());
        GuildBlock plot = new GuildBlock(CENTER_X + 1, CENTER_Z - 1, WORLD, claimed.getId());
        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, null, 1,
                (x, z, world) -> x == plot.getX() && z == plot.getZ() ? Optional.of(plot) : Optional.empty(),
                id -> Optional.of(claimed));
        assertEquals(ClaimLayer.Kind.OTHER_GUILD, layer.cellAt(plot.getX(), plot.getZ()).orElseThrow().kind());
    }

    @Test
    void coversEveryChunkInTheRadius() {
        ClaimLayer layer = ClaimLayer.classify(
                CENTER_X, CENTER_Z, WORLD, null, 2,
                (x, z, world) -> Optional.empty(),
                id -> Optional.empty());
        assertEquals(5 * 5, layer.cells().size());
        assertEquals(5, layer.size());
        ClaimLayer.Cell corner = layer.cellAt(CENTER_X + 2, CENTER_Z + 2).orElseThrow();
        assertEquals(ClaimLayer.Kind.WILDERNESS, corner.kind());
    }

    @Test
    void rejectsNegativeRadius() {
        assertThrows(IllegalArgumentException.class,
                () -> ClaimLayer.classify(CENTER_X, CENTER_Z, WORLD, null, -1,
                        (x, z, w) -> Optional.empty(), id -> Optional.empty()));
    }
}
