package org.aincraft.guilds.map;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Classifies nearby chunks as wilderness, the viewer's guild, another guild, or the center cell.
 */
public final class ClaimLayer {

    /** Defines the values of kind. */
    public enum Kind {
        /** The wilderness constant. */
        WILDERNESS,
        /** The own guild constant. */
        OWN_GUILD,
        /** The other guild constant. */
        OTHER_GUILD,
        /** The center constant. */
        CENTER
    }

    /** Immutable data carrier for cell. */
    public record Cell(int chunkX, int chunkZ, Kind kind, String guildName) {
        /** Creates a new cell instance without an explicit guild name. */
        public Cell(int chunkX, int chunkZ, Kind kind) {
            this(chunkX, chunkZ, kind, null);
        }

        /** Creates a new cell instance. */
        public Cell {
            Objects.requireNonNull(kind, "kind");
        }
    }

    /** Defines operations for plot lookup. */
    @FunctionalInterface
    public interface PlotLookup {
        /**
         * Performs the plot at operation.
         * @param chunkX the chunk x
         * @param chunkZ the chunk z
         * @param world the world
         * @return the result
         */
        Optional<GuildBlock> plotAt(int chunkX, int chunkZ, String world);
    }

    /** Defines operations for guild lookup. */
    @FunctionalInterface
    public interface GuildLookup {
        /**
         * Performs the by id operation.
         * @param guildId the guild id
         * @return the result
         */
        Optional<Guild> byId(String guildId);
    }

    /** The center chunk x. */
    private final int centerChunkX;
    /** The center chunk z. */
    private final int centerChunkZ;
    /** The world. */
    private final String world;
    /** The radius. */
    private final int radius;
    /** The cells. */
    private final List<Cell> cells;

    /**
     * Creates a new claim layer instance.
     * @param centerChunkX the center chunk x
     * @param centerChunkZ the center chunk z
     * @param world the world
     * @param radius the radius
     * @param cells the cells
     */
    private ClaimLayer(int centerChunkX, int centerChunkZ, String world, int radius, List<Cell> cells) {
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.world = world;
        this.radius = radius;
        this.cells = cells;
    }

    /**
     * Performs the classify operation.
     * @param centerChunkX the center chunk x
     * @param centerChunkZ the center chunk z
     * @param world the world
     * @param viewerGuild the viewer guild
     * @param radius the radius
     * @param plots the plots
     * @param guilds the guilds
     * @return the result
     */
    public static ClaimLayer classify(
            int centerChunkX,
            int centerChunkZ,
            String world,
            String viewerGuild,
            int radius,
            PlotLookup plots,
            GuildLookup guilds) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be >= 0");
        }
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(plots, "plots");
        Objects.requireNonNull(guilds, "guilds");

        List<Cell> cells = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                cells.add(classifyCell(chunkX, chunkZ, world, viewerGuild, plots, guilds));
            }
        }
        return new ClaimLayer(centerChunkX, centerChunkZ, world, radius, List.copyOf(cells));
    }

    /**
     * Performs the classify cell operation.
     * @param chunkX the chunk x
     * @param chunkZ the chunk z
     * @param world the world
     * @param viewerGuild the viewer guild
     * @param plots the plots
     * @param guilds the guilds
     * @return the classified cell
     */
    private static Cell classifyCell(
            int chunkX,
            int chunkZ,
            String world,
            String viewerGuild,
            PlotLookup plots,
            GuildLookup guilds) {
        Optional<GuildBlock> plot = plots.plotAt(chunkX, chunkZ, world);
        if (plot.isEmpty()) {
            return new Cell(chunkX, chunkZ, Kind.WILDERNESS, null);
        }
        Optional<Guild> guild = guilds.byId(plot.get().getGuildId());
        String name = guild.map(Guild::getName).orElse(null);
        if (guild.isPresent() && viewerGuild != null && viewerGuild.equals(name)) {
            return new Cell(chunkX, chunkZ, Kind.OWN_GUILD, name);
        }
        return new Cell(chunkX, chunkZ, Kind.OTHER_GUILD, name);
    }
    /**
     * Performs the center chunk x operation.
     * @return the result
     */
    public int centerChunkX() {
        return centerChunkX;
    }

    /**
     * Performs the center chunk z operation.
     * @return the result
     */
    public int centerChunkZ() {
        return centerChunkZ;
    }

    /**
     * Performs the world operation.
     * @return the result
     */
    public String world() {
        return world;
    }

    /**
     * Performs the radius operation.
     * @return the result
     */
    public int radius() {
        return radius;
    }

    /**
     * Performs the size operation.
     * @return the result
     */
    public int size() {
        return radius * 2 + 1;
    }

    /**
     * Performs the cells operation.
     * @return the result
     */
    public List<Cell> cells() {
        return cells;
    }

    /**
     * Performs the cell at operation.
     * @param chunkX the chunk x
     * @param chunkZ the chunk z
     * @return the result
     */
    public Optional<Cell> cellAt(int chunkX, int chunkZ) {
        for (Cell cell : cells) {
            if (cell.chunkX() == chunkX && cell.chunkZ() == chunkZ) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }
}
