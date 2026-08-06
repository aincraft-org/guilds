package org.aincraft.guilds.utils;

import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.PlotTypes;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PlotService;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.awt.Color;

/**
 * Utility class for rendering ASCII maps of guild claims
 */
public class MapRenderer {

    private final GuildService guildService;
    private final PlotService plotService;

    // Map symbols for different terrain types
    private static final char WILDERNESS = '-';
    private static final char GUILD_BLOCK = '+';
    private static final char PLAYER_LOCATION = '●'; // Bullet point for better visibility
    private static final char GUILD_SPAWN = 'S';

    // Colors for map display (using chat color codes)
    private static final String COLOR_WILDERNESS = "§2"; // Dark Green
    private static final String COLOR_GUILD_BLOCK = "§e"; // Yellow
    private static final String COLOR_PLAYER_LOCATION = "§a"; // Green
    private static final String COLOR_GUILD_SPAWN = "§6"; // Gold
    private static final String COLOR_RESET = "§f";

    // Map size in chunks (default 11x11 = 121 chunks total)
    private static final int DEFAULT_MAP_SIZE = 11;
    private static final int MAP_RADIUS = DEFAULT_MAP_SIZE / 2;

    public MapRenderer(GuildService guildService, PlotService plotService) {
        this.guildService = guildService;
        this.plotService = plotService;
    }

    /**
     * Render an ASCII map centered on the player's location
     * @param playerChunkX Player's chunk X coordinate
     * @param playerChunkZ Player's chunk Z coordinate
     * @param world World name
     * @param playerGuild Player's guild name (can be null)
     * @return Rendered map as a list of strings
     */
    public List<String> renderMap(int playerChunkX, int playerChunkZ, String world, String playerGuild) {
        List<String> mapLines = new ArrayList<>();

        // Add stylized header
        mapLines.add("§6╔═══════════════════════════════════════╗");
        mapLines.add("§6║        §e§lGUILDS WORLD MAP§r§6            ║");
        mapLines.add("§6╠═══════════════════════════════════════╣");
        mapLines.add("§6║ §fCenter: §a" + String.format("%-6d", playerChunkX) + "§f, §a" + String.format("%-6d", playerChunkZ) + "§6          ║");
        mapLines.add("§6║ §fWorld:  §b" + String.format("%-20s", world) + " §6║");
        mapLines.add("§6╚═══════════════════════════════════════╝");
        mapLines.add("");

        // Create a grid of guild blocks
        Map<MapPoint, Character> mapGrid = new HashMap<>();
        Map<MapPoint, String> colorGrid = new HashMap<>();

        // Fill the map grid
        for (int z = -MAP_RADIUS; z <= MAP_RADIUS; z++) {
            for (int x = -MAP_RADIUS; x <= MAP_RADIUS; x++) {
                MapPoint point = new MapPoint(x, z);
                int chunkX = playerChunkX + x;
                int chunkZ = playerChunkZ + z;

                // Check if this is the player's location
                if (x == 0 && z == 0) {
                    mapGrid.put(point, PLAYER_LOCATION);
                    colorGrid.put(point, COLOR_PLAYER_LOCATION);
                    continue;
                }

                // Check if there's a guild block at this location
                Optional<GuildBlock> guildBlock = plotService.getGuildBlock(chunkX, chunkZ, world);
                if (guildBlock.isPresent()) {
                    GuildBlock block = guildBlock.get();
                    mapGrid.put(point, GUILD_BLOCK);

                    // Color based on ownership
                    String color = getColorForGuildBlock(block, playerGuild);
                    colorGrid.put(point, color);
                } else {
                    // Wilderness
                    mapGrid.put(point, WILDERNESS);
                    colorGrid.put(point, COLOR_WILDERNESS);
                }
            }
        }

        // Add coordinate axis labels
        mapLines.add("    §8" + getHorizontalAxis(playerChunkX));

        // Render the map row by row with row numbers
        for (int z = -MAP_RADIUS; z <= MAP_RADIUS; z++) {
            StringBuilder row = new StringBuilder();

            // Add left coordinate
            row.append("§8").append(String.format("%3d", playerChunkZ + z)).append(" §r");

            for (int x = -MAP_RADIUS; x <= MAP_RADIUS; x++) {
                MapPoint point = new MapPoint(x, z);
                char symbol = mapGrid.getOrDefault(point, WILDERNESS);
                String color = colorGrid.getOrDefault(point, COLOR_WILDERNESS);
                row.append(color).append(symbol); // Single character
            }

            // Add right coordinate
            row.append(" §8").append(String.format("%3d", playerChunkZ + z));

            mapLines.add(row.toString());
        }

        // Bottom axis
        mapLines.add("    §8" + getHorizontalAxis(playerChunkX));

        // Add stylized legend
        mapLines.add("");
        mapLines.add("§6╔═══════════════════════════════════════╗");
        mapLines.add("§6║              §e§lLEGEND§r§6                  ║");
        mapLines.add("§6╠═══════════════════════════════════════╣");
        mapLines.add("§6║ " + COLOR_PLAYER_LOCATION + "●§6  Your Location                    ║");
        mapLines.add("§6║ " + COLOR_WILDERNESS + "─§6  Wilderness (unclaimed)           ║");
        mapLines.add("§6║ " + "§a" + "+§6  Your Town                        ║");
        mapLines.add("§6║ " + "§e" + "+§6  Other Towns                      ║");
        mapLines.add("§6╚═══════════════════════════════════════╝");

        return mapLines;
    }

    /**
     * Generate horizontal axis labels
     * @param centerX Center X coordinate
     * @return Formatted axis string
     */
    private String getHorizontalAxis(int centerX) {
        StringBuilder axis = new StringBuilder();
        for (int x = -MAP_RADIUS; x <= MAP_RADIUS; x++) {
            int coord = centerX + x;
            if (x == 0) {
                axis.append("§a").append(Math.abs(coord) % 10);
            } else {
                axis.append(Math.abs(coord) % 10);
            }
        }
        return axis.toString();
    }

    /**
     * Get the appropriate color for a guild block
     * @param guildBlock The guild block
     * @param playerGuild Player's guild (can be null)
     * @return Color code for the guild block
     */
    private String getColorForGuildBlock(GuildBlock guildBlock, String playerGuild) {
        // Get guild information
        String guildId = guildBlock.getGuildId();
        Optional<Guild> guild = guildService.getGuildById(guildId);

        if (guild.isEmpty()) {
            return COLOR_GUILD_BLOCK; // Default yellow
        }

        Guild blockGuild = guild.get();

        // Check if this is the player's own guild
        if (playerGuild != null && playerGuild.equals(blockGuild.getName())) {
            return "§a"; // Green for own guild
        }

        // Check if it's personally owned
        if (guildBlock.hasOwner()) {
            return "§b"; // Aqua for personal plots
        }

        // Different colors based on plot type
        switch (guildBlock.getPlotType()) {
            case PlotTypes.SHOP:
                return "§6"; // Gold for shops
            case PlotTypes.FARM:
                return "§e"; // Yellow for farms
            case PlotTypes.BANK:
                return "§c"; // Red for banks
            case PlotTypes.INN:
                return "§d"; // Purple for inns
            default:
                return COLOR_GUILD_BLOCK; // Default yellow
        }
    }

    /**
     * Render a compact map (smaller size)
     * @param playerChunkX Player's chunk X coordinate
     * @param playerChunkZ Player's chunk Z coordinate
     * @param world World name
     * @param playerGuild Player's guild name (can be null)
     * @return Compact map as a list of strings
     */
    public List<String> renderCompactMap(int playerChunkX, int playerChunkZ, String world, String playerGuild) {
        List<String> mapLines = new ArrayList<>();
        int compactSize = 7;
        int compactRadius = compactSize / 2;

        // Compact header
        mapLines.add("§6╔═══════════════════╗");
        mapLines.add("§6║   §e§lQUICK MAP§r§6      ║");
        mapLines.add("§6╚═══════════════════╝");

        // Create compact grid
        for (int z = -compactRadius; z <= compactRadius; z++) {
            StringBuilder row = new StringBuilder();
            row.append("  "); // Indent for centering

            for (int x = -compactRadius; x <= compactRadius; x++) {
                int chunkX = playerChunkX + x;
                int chunkZ = playerChunkZ + z;

                if (x == 0 && z == 0) {
                    row.append(COLOR_PLAYER_LOCATION).append(PLAYER_LOCATION);
                } else if (plotService.getGuildBlock(chunkX, chunkZ, world).isPresent()) {
                    Optional<GuildBlock> guildBlock = plotService.getGuildBlock(chunkX, chunkZ, world);
                    String color = guildBlock.map(block -> getColorForGuildBlock(block, playerGuild))
                                          .orElse(COLOR_GUILD_BLOCK);
                    row.append(color).append(GUILD_BLOCK);
                } else {
                    row.append(COLOR_WILDERNESS).append(WILDERNESS);
                }
            }
            row.append(COLOR_RESET);
            mapLines.add(row.toString());
        }

        mapLines.add("");
        mapLines.add("  §8[" + playerChunkX + ", " + playerChunkZ + "]");

        return mapLines;
    }

    /**
     * Helper class to represent points on the map grid
     */
    private static class MapPoint {
        final int x;
        final int z;

        MapPoint(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            MapPoint mapPoint = (MapPoint) obj;
            return x == mapPoint.x && z == mapPoint.z;
        }

        @Override
        public int hashCode() {
            return 31 * x + z;
        }
    }

    /**
     * Get a text summary of the surrounding area
     * @param playerChunkX Player's chunk X coordinate
     * @param playerChunkZ Player's chunk Z coordinate
     * @param world World name
     * @param playerGuild Player's guild name (can be null)
     * @return Area summary as a string
     */
    public String getAreaSummary(int playerChunkX, int playerChunkZ, String world, String playerGuild) {
        int radius = 3; // Check 3x3 chunks around player
        int wildernessCount = 0;
        int ownGuildBlocks = 0;
        int otherGuildBlocks = 0;
        String nearbyGuild = null;

        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                int chunkX = playerChunkX + x;
                int chunkZ = playerChunkZ + z;

                Optional<GuildBlock> guildBlock = plotService.getGuildBlock(chunkX, chunkZ, world);
                if (guildBlock.isPresent()) {
                    GuildBlock block = guildBlock.get();
                    String blockGuildId = block.getGuildId();

                    Optional<Guild> blockGuild = guildService.getGuildById(blockGuildId);
                    if (blockGuild.isPresent()) {
                        String blockGuildName = blockGuild.get().getName();

                        if (playerGuild != null && playerGuild.equals(blockGuildName)) {
                            ownGuildBlocks++;
                        } else {
                            otherGuildBlocks++;
                            nearbyGuild = blockGuildName;
                        }
                    }
                } else {
                    wildernessCount++;
                }
            }
        }

        StringBuilder summary = new StringBuilder();
        summary.append("§eArea Summary (").append(radius * 2 + 1).append("x").append(radius * 2 + 1).append(" chunks): ");

        if (wildernessCount > 0) {
            summary.append(COLOR_WILDERNESS).append(wildernessCount).append(" wilderness ");
        }

        if (ownGuildBlocks > 0) {
            summary.append("§a").append(ownGuildBlocks).append(" your town ");
        }

        if (otherGuildBlocks > 0) {
            summary.append("§6").append(otherGuildBlocks).append(" other guilds");
            if (nearbyGuild != null) {
                summary.append(" (near ").append(nearbyGuild).append(")");
            }
        }

        return summary.toString();
    }
}