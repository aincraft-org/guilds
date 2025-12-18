package org.aincraft.map;

import com.google.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.RelationType;
import org.aincraft.RelationshipService;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.aincraft.storage.ChunkClaimRepository;
import org.bukkit.entity.Player;

/**
 * Renders a visual guild map showing chunk claims in a grid format.
 * Handles all visualization logic: grid calculation, symbol selection, component assembly.
 */
public class GuildMapRenderer {
    private static final int BASE_GRID_SIZE = 6;
    private static final float COMPASS_NORTH_THRESHOLD = 22.5f;
    private static final float COMPASS_NORTHEAST_THRESHOLD = 67.5f;
    private static final float COMPASS_EAST_THRESHOLD = 112.5f;
    private static final float COMPASS_SOUTHEAST_THRESHOLD = 157.5f;
    private static final float COMPASS_SOUTH_THRESHOLD = 202.5f;
    private static final float COMPASS_SOUTHWEST_THRESHOLD = 247.5f;
    private static final float COMPASS_WEST_THRESHOLD = 292.5f;
    private static final float COMPASS_NORTHWEST_THRESHOLD = 337.5f;

    private final GuildLifecycleService lifecycleService;
    private final GuildMemberService memberService;
    private final ChunkClaimRepository chunkClaimRepository;
    private final GuildColorMapper colorMapper;
    private final RelationshipService relationshipService;
    private final SimpleDateFormat dateFormat;

    @Inject
    public GuildMapRenderer(GuildLifecycleService lifecycleService, GuildMemberService memberService,
                           ChunkClaimRepository chunkClaimRepository, GuildColorMapper colorMapper,
                           RelationshipService relationshipService) {
        this.lifecycleService = lifecycleService;
        this.memberService = memberService;
        this.chunkClaimRepository = chunkClaimRepository;
        this.colorMapper = colorMapper;
        this.relationshipService = relationshipService;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    }

    /**
     * Renders the guild map and sends it to the player.
     *
     * @param player the player to send the map to
     * @param size the map size (1-5, where N = 6+N grid)
     */
    public void renderMap(Player player, int size) {
        Guild playerGuild = memberService.getPlayerGuild(player.getUniqueId());

        // Calculate grid dimensions and boundaries
        int gridSize = BASE_GRID_SIZE + size;
        int radius = gridSize / 2;
        int playerChunkX = player.getLocation().getChunk().getX();
        int playerChunkZ = player.getLocation().getChunk().getZ();

        // Build chunk list to query
        List<ChunkKey> chunksToQuery = buildChunkList(player, gridSize, radius);

        // Batch query all chunk ownership data
        Map<ChunkKey, ChunkClaimData> claimData = chunkClaimRepository.getOwnersForChunks(chunksToQuery);

        // Send header
        sendHeader(player, playerChunkX, playerChunkZ);

        // Render and send each row with compass directions
        for (int z = -radius; z <= radius; z++) {
            TextComponent.Builder rowBuilder = Component.text();

            // Add compass direction indicator (N, S, or center)
            if (z == -radius) {
                rowBuilder.append(Component.text("N ").color(NamedTextColor.GOLD));
            } else if (z == radius) {
                rowBuilder.append(Component.text("S ").color(NamedTextColor.GOLD));
            } else if (z == 0) {
                rowBuilder.append(Component.text("* ").color(NamedTextColor.AQUA));
            } else {
                rowBuilder.append(Component.text("  "));
            }

            // Build chunk row
            for (int x = -radius; x <= radius; x++) {
                ChunkKey chunk = new ChunkKey(player.getWorld().getName(), playerChunkX + x, playerChunkZ + z);
                Component chunkComponent = buildChunkComponent(chunk, claimData, playerGuild, x == 0 && z == 0);
                rowBuilder.append(chunkComponent);
            }

            // Add end compass direction indicator (E or W)
            if (z == -radius || z == radius) {
                // Corner rows don't need end marker
            } else if (z == 0) {
                rowBuilder.append(Component.text("*").color(NamedTextColor.AQUA));
            }

            player.sendMessage(rowBuilder.build());
        }

        // Send column compass indicators (E/W)
        sendCompassFooter(player, radius);

        // Send footer with legend
        sendLegend(player);
    }

    /**
     * Sends compass footer with E/W indicators for columns.
     */
    private void sendCompassFooter(Player player, int radius) {
        TextComponent.Builder footer = Component.text();
        footer.append(Component.text("W ").color(NamedTextColor.GOLD));

        for (int x = -radius; x <= radius; x++) {
            if (x == -radius) {
                footer.append(Component.text("  "));
            } else if (x == 0) {
                footer.append(Component.text("* ").color(NamedTextColor.AQUA));
            } else if (x == radius) {
                footer.append(Component.text("  "));
            } else {
                footer.append(Component.text("  "));
            }
        }

        footer.append(Component.text("E").color(NamedTextColor.GOLD));
        player.sendMessage(footer.build());
    }

    /**
     * Sends legend showing symbol meanings.
     */
    private void sendLegend(Player player) {
        Component legend = Component.text()
            .append(Component.text("└─ "))
            .color(NamedTextColor.GOLD)
            .append(Component.text(MapSymbols.PLAYER).color(NamedTextColor.AQUA))
            .append(Component.text("=You  "))
            .color(NamedTextColor.GRAY)
            .append(Component.text(MapSymbols.OWN_GUILD).color(NamedTextColor.GREEN))
            .append(Component.text("=Guild  "))
            .color(NamedTextColor.GRAY)
            .append(Component.text(MapSymbols.OTHER_GUILD).color(NamedTextColor.YELLOW))
            .append(Component.text("=Other  "))
            .color(NamedTextColor.GRAY)
            .append(Component.text(MapSymbols.WILDERNESS).color(NamedTextColor.DARK_GRAY))
            .append(Component.text("=Wild  "))
            .color(NamedTextColor.GRAY)
            .append(Component.text("*=Center"))
            .color(NamedTextColor.GRAY)
            .build();
        player.sendMessage(legend);
    }

    /**
     * Builds list of all chunks in the map grid.
     */
    private List<ChunkKey> buildChunkList(Player player, int gridSize, int radius) {
        List<ChunkKey> chunks = new ArrayList<>();
        int playerChunkX = player.getLocation().getChunk().getX();
        int playerChunkZ = player.getLocation().getChunk().getZ();
        String worldName = player.getWorld().getName();

        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                chunks.add(new ChunkKey(worldName, playerChunkX + x, playerChunkZ + z));
            }
        }

        return chunks;
    }

    /**
     * Sends header with map title and player coordinates.
     */
    private void sendHeader(Player player, int playerChunkX, int playerChunkZ) {
        // Get player direction
        String direction = getCompassDirection(player.getYaw());

        Component header = Component.text()
            .append(Component.text("┌─ Guild Map "))
            .color(NamedTextColor.GOLD)
            .append(Component.text(String.format("[%s | %d, %d] ", direction, playerChunkX, playerChunkZ)))
            .color(NamedTextColor.AQUA)
            .append(Component.text("─┐"))
            .color(NamedTextColor.GOLD)
            .build();
        player.sendMessage(header);
    }

    /**
     * Gets compass direction from player yaw (0-360 degrees).
     */
    private String getCompassDirection(float yaw) {
        // Normalize yaw to 0-360
        yaw = (yaw % 360 + 360) % 360;

        if (yaw >= COMPASS_NORTHWEST_THRESHOLD || yaw < COMPASS_NORTH_THRESHOLD) return "N";      // North
        if (yaw >= COMPASS_NORTH_THRESHOLD && yaw < COMPASS_NORTHEAST_THRESHOLD) return "NE";     // Northeast
        if (yaw >= COMPASS_NORTHEAST_THRESHOLD && yaw < COMPASS_EAST_THRESHOLD) return "E";     // East
        if (yaw >= COMPASS_EAST_THRESHOLD && yaw < COMPASS_SOUTHEAST_THRESHOLD) return "SE";   // Southeast
        if (yaw >= COMPASS_SOUTHEAST_THRESHOLD && yaw < COMPASS_SOUTH_THRESHOLD) return "S";    // South
        if (yaw >= COMPASS_SOUTH_THRESHOLD && yaw < COMPASS_SOUTHWEST_THRESHOLD) return "SW";   // Southwest
        if (yaw >= COMPASS_SOUTHWEST_THRESHOLD && yaw < COMPASS_WEST_THRESHOLD) return "W";    // West
        if (yaw >= COMPASS_WEST_THRESHOLD && yaw < COMPASS_NORTHWEST_THRESHOLD) return "NW";   // Northwest
        return "?";
    }

    /**
     * Builds a single chunk component with appropriate symbol, color, and hover tooltip.
     */
    private Component buildChunkComponent(ChunkKey chunk, Map<ChunkKey, ChunkClaimData> claimData,
                                         Guild playerGuild, boolean isPlayerLocation) {
        if (isPlayerLocation) {
            // Player location: aqua @ symbol
            return Component.text(MapSymbols.PLAYER + " ")
                .color(NamedTextColor.AQUA);
        }

        ChunkClaimData data = claimData.get(chunk);
        if (data == null) {
            // Wilderness: dark gray - symbol
            return Component.text(MapSymbols.WILDERNESS + " ")
                .color(NamedTextColor.DARK_GRAY);
        }

        Guild owner = lifecycleService.getGuildById(data.guildId());
        if (owner == null) {
            return Component.text(MapSymbols.UNKNOWN + " ").color(NamedTextColor.DARK_GRAY);
        }

        String symbol;
        NamedTextColor color;

        // Determine symbol and color based on relationship
        if (playerGuild != null && owner.getId().equals(playerGuild.getId())) {
            // Own guild: green ■ symbol
            symbol = MapSymbols.OWN_GUILD;
            color = NamedTextColor.GREEN;
        } else if (playerGuild != null) {
            RelationType relation = relationshipService.getRelationType(playerGuild.getId(), owner.getId());
            if (relation == RelationType.ALLY) {
                // Allied guild: blue ◆ symbol
                symbol = "◆";
                color = NamedTextColor.BLUE;
            } else if (relation == RelationType.ENEMY) {
                // Enemy guild: red ▲ symbol
                symbol = "▲";
                color = NamedTextColor.RED;
            } else {
                // Neutral/other guild: faction color ▪ symbol
                symbol = MapSymbols.OTHER_GUILD;
                String guildColor = colorMapper.getColorForGuild(owner.getId());
                color = parseColor(guildColor);
            }
        } else {
            // No player guild: faction color ▪ symbol
            symbol = MapSymbols.OTHER_GUILD;
            String guildColor = colorMapper.getColorForGuild(owner.getId());
            color = parseColor(guildColor);
        }

        // Build tooltip
        Component tooltip = buildTooltip(owner, data);

        // Build component with hover event
        return Component.text(symbol + " ")
            .color(color)
            .hoverEvent(HoverEvent.showText(tooltip));
    }

    /**
     * Builds hover tooltip showing guild info.
     */
    private Component buildTooltip(Guild guild, ChunkClaimData claimData) {
        String claimer = claimer(claimData.claimedBy());
        String claimDate = dateFormat.format(new Date(claimData.claimedAt()));

        return Component.text()
            .append(Component.text("Guild: ").color(NamedTextColor.YELLOW))
            .append(Component.text(guild.getName()).color(NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text("Claimed by: ").color(NamedTextColor.YELLOW))
            .append(Component.text(claimer).color(NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Date: ").color(NamedTextColor.YELLOW))
            .append(Component.text(claimDate).color(NamedTextColor.GRAY))
            .build();
    }

    /**
     * Gets player name from UUID, with fallback to UUID string.
     */
    private String claimer(UUID uuid) {
        var player = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    /**
     * Converts color name string to NamedTextColor.
     */
    private NamedTextColor parseColor(String colorName) {
        return switch (colorName) {
            case "aqua" -> NamedTextColor.AQUA;
            case "blue" -> NamedTextColor.BLUE;
            case "gold" -> NamedTextColor.GOLD;
            case "green" -> NamedTextColor.GREEN;
            case "light_purple" -> NamedTextColor.LIGHT_PURPLE;
            case "red" -> NamedTextColor.RED;
            case "yellow" -> NamedTextColor.YELLOW;
            case "dark_aqua" -> NamedTextColor.DARK_AQUA;
            case "dark_blue" -> NamedTextColor.DARK_BLUE;
            case "dark_green" -> NamedTextColor.DARK_GREEN;
            case "dark_purple" -> NamedTextColor.DARK_PURPLE;
            case "dark_red" -> NamedTextColor.DARK_RED;
            default -> NamedTextColor.WHITE;
        };
    }
}
