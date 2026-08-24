package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.SettlementFacility;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;
import java.util.Optional;

/** Finds a safe standing location without mutating world blocks. */
public final class SafeLandingResolver {
    private static final int[][] OFFSETS = {{0, 1, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};
    private final Server server;

    public SafeLandingResolver(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public Optional<Location> find(SettlementFacility destination) {
        World world = server.getWorld(destination.worldId());
        if (world == null) return Optional.empty();
        for (int[] offset : OFFSETS) {
            int x = destination.x() + offset[0];
            int y = destination.y() + offset[1];
            int z = destination.z() + offset[2];
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            Block support = world.getBlockAt(x, y - 1, z);
            if (feet.isPassable() && head.isPassable() && !feet.isLiquid()
                    && !head.isLiquid() && !support.isPassable() && !support.isLiquid()) {
                return Optional.of(new Location(world, x + 0.5, y, z + 0.5));
            }
        }
        return Optional.empty();
    }
}
