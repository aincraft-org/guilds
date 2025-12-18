package org.aincraft.subregion;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.GuildsPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Visualizes region boundaries using particles.
 * Shows corners and edges when players hover over regions in lists.
 */
@Singleton
public class RegionVisualizer {
    private static final int VISUALIZATION_DURATION_SECONDS = 10;
    private static final int PARTICLE_DENSITY = 2; // Particles per block

    private final GuildsPlugin plugin;
    private final Map<UUID, ActiveVisualization> activeVisualizations = new ConcurrentHashMap<>();

    @Inject
    public RegionVisualizer(GuildsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Shows a region's boundaries to a player using particles.
     * Displays corners, edges, and center point.
     *
     * @param player the player to show the visualization to
     * @param region the region to visualize
     */
    public void visualizeRegion(Player player, Subregion region) {
        UUID playerId = player.getUniqueId();

        // Cancel existing visualization for this player
        cancelVisualization(playerId);

        // Create visualization task
        BukkitTask task = new BukkitRunnable() {
            private int ticksElapsed = 0;
            private final int maxTicks = VISUALIZATION_DURATION_SECONDS * 20;

            @Override
            public void run() {
                if (!player.isOnline() || ticksElapsed >= maxTicks) {
                    cancel();
                    activeVisualizations.remove(playerId);
                    return;
                }

                // Only show particles every 10 ticks (0.5 seconds) to reduce lag
                if (ticksElapsed % 10 == 0) {
                    showRegionParticles(player, region);
                }

                ticksElapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        activeVisualizations.put(playerId, new ActiveVisualization(region, task));
    }

    /**
     * Cancels any active visualization for a player.
     *
     * @param playerId the player's UUID
     */
    public void cancelVisualization(UUID playerId) {
        ActiveVisualization active = activeVisualizations.remove(playerId);
        if (active != null) {
            active.task().cancel();
        }
    }

    /**
     * Shows particles for a region's boundaries.
     */
    private void showRegionParticles(Player player, Subregion region) {
        org.bukkit.World world = player.getServer().getWorld(region.getWorld());
        if (world == null) return;

        Location min = new Location(world, region.getMinX(), region.getMinY(), region.getMinZ());
        Location max = new Location(world, region.getMaxX(), region.getMaxY(), region.getMaxZ());

        // Show corners
        showCorners(player, min, max);

        // Show edges
        showEdges(player, min, max);

        // Show center
        showCenter(player, min, max);
    }

    /**
     * Shows particles at the 8 corners of the region.
     */
    private void showCorners(Player player, Location min, Location max) {
        double[][] corners = {
            {min.getX(), min.getY(), min.getZ()},
            {max.getX(), min.getY(), min.getZ()},
            {min.getX(), max.getY(), min.getZ()},
            {max.getX(), max.getY(), min.getZ()},
            {min.getX(), min.getY(), max.getZ()},
            {max.getX(), min.getY(), max.getZ()},
            {min.getX(), max.getY(), max.getZ()},
            {max.getX(), max.getY(), max.getZ()}
        };

        for (double[] corner : corners) {
            Location loc = new Location(min.getWorld(), corner[0], corner[1], corner[2]);
            player.spawnParticle(Particle.END_ROD, loc, 3, 0.1, 0.1, 0.1, 0);
        }
    }

    /**
     * Shows particles along the 12 edges of the region.
     */
    private void showEdges(Player player, Location min, Location max) {
        // Bottom face edges
        drawLine(player, min.getX(), min.getY(), min.getZ(), max.getX(), min.getY(), min.getZ());
        drawLine(player, min.getX(), min.getY(), min.getZ(), min.getX(), min.getY(), max.getZ());
        drawLine(player, max.getX(), min.getY(), min.getZ(), max.getX(), min.getY(), max.getZ());
        drawLine(player, min.getX(), min.getY(), max.getZ(), max.getX(), min.getY(), max.getZ());

        // Top face edges
        drawLine(player, min.getX(), max.getY(), min.getZ(), max.getX(), max.getY(), min.getZ());
        drawLine(player, min.getX(), max.getY(), min.getZ(), min.getX(), max.getY(), max.getZ());
        drawLine(player, max.getX(), max.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
        drawLine(player, min.getX(), max.getY(), max.getZ(), max.getX(), max.getY(), max.getZ());

        // Vertical edges
        drawLine(player, min.getX(), min.getY(), min.getZ(), min.getX(), max.getY(), min.getZ());
        drawLine(player, max.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), min.getZ());
        drawLine(player, min.getX(), min.getY(), max.getZ(), min.getX(), max.getY(), max.getZ());
        drawLine(player, max.getX(), min.getY(), max.getZ(), max.getX(), max.getY(), max.getZ());
    }

    /**
     * Shows a particle at the center of the region.
     */
    private void showCenter(Player player, Location min, Location max) {
        double centerX = (min.getX() + max.getX()) / 2.0;
        double centerY = (min.getY() + max.getY()) / 2.0;
        double centerZ = (min.getZ() + max.getZ()) / 2.0;

        Location center = new Location(min.getWorld(), centerX, centerY, centerZ);
        player.spawnParticle(Particle.HAPPY_VILLAGER, center, 5, 0.3, 0.3, 0.3, 0);
    }

    /**
     * Draws a line of particles between two points.
     */
    private void drawLine(Player player, double x1, double y1, double z1, double x2, double y2, double z2) {
        double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2));
        int points = (int) (distance * PARTICLE_DENSITY);

        if (points == 0) return;

        for (int i = 0; i <= points; i++) {
            double ratio = (double) i / points;
            double x = x1 + (x2 - x1) * ratio;
            double y = y1 + (y2 - y1) * ratio;
            double z = z1 + (z2 - z1) * ratio;

            Location loc = new Location(player.getWorld(), x, y, z);
            player.spawnParticle(Particle.FIREWORK, loc, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Holds information about an active visualization.
     */
    private record ActiveVisualization(Subregion region, BukkitTask task) {}
}
