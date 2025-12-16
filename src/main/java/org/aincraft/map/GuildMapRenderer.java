package org.aincraft.map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.storage.ChunkClaimRepository;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Renders a visual guild map showing chunk claims in a grid format.
 * Handles all visualization logic: grid calculation, symbol selection, component assembly.
 */
public class GuildMapRenderer {
    private final GuildService guildService;
    private final ChunkClaimRepository chunkClaimRepository;
    private final GuildColorMapper colorMapper;
    private final SimpleDateFormat dateFormat;

    public GuildMapRenderer(GuildService guildService, ChunkClaimRepository chunkClaimRepository,
                           GuildColorMapper colorMapper) {
        this.guildService = guildService;
        this.chunkClaimRepository = chunkClaimRepository;
        this.colorMapper = colorMapper;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    }

    /**
     * Renders the guild map and sends it to the player.
     *
     * @param player the player to send the map to
     * @param size the map size (1-5, where N = 6+N grid)
     */
    public void renderMap(Player player, int size) {
        Guild playerGuild = guildService.getPlayerGuild(player.getUniqueId());

        // Calculate grid dimensions and boundaries
        int gridSize = 6 + size;
        int radius = gridSize / 2;
        int playerChunkX = player.getLocation().getChunk().getX();
        int playerChunkZ = player.getLocation().getChunk().getZ();

        // Build chunk list to query
        List<ChunkKey> chunksToQuery = buildChunkList(player, gridSize, radius);

        // Batch query all chunk ownership data
        Map<ChunkKey, ChunkClaimData> claimData = chunkClaimRepository.getOwnersForChunks(chunksToQuery);

        // Send header
        sendHeader(player, playerChunkX, playerChunkZ);

        // Render and send each row
        for (int z = -radius; z <= radius; z++) {
            TextComponent.Builder rowBuilder = Component.text();
            for (int x = -radius; x <= radius; x++) {
                ChunkKey chunk = new ChunkKey(player.getWorld().getName(), playerChunkX + x, playerChunkZ + z);
                Component chunkComponent = buildChunkComponent(chunk, claimData, playerGuild, x == 0 && z == 0);
                rowBuilder.append(chunkComponent);
            }
            player.sendMessage(rowBuilder.build());
        }
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
        Component header = Component.text()
            .append(Component.text("═══ Guild Map "))
            .color(NamedTextColor.GOLD)
            .append(Component.text(String.format("[Chunk: %d, %d] ", playerChunkX, playerChunkZ)))
            .color(NamedTextColor.GRAY)
            .append(Component.text("═══"))
            .color(NamedTextColor.GOLD)
            .build();
        player.sendMessage(header);
    }

    /**
     * Builds a single chunk component with appropriate symbol, color, and hover tooltip.
     */
    private Component buildChunkComponent(ChunkKey chunk, Map<ChunkKey, ChunkClaimData> claimData,
                                         Guild playerGuild, boolean isPlayerLocation) {
        if (isPlayerLocation) {
            // Player location: white/aqua @ symbol
            return Component.text("@ ")
                .color(NamedTextColor.AQUA);
        }

        ChunkClaimData data = claimData.get(chunk);
        if (data == null) {
            // Wilderness: dark gray - symbol
            return Component.text("- ")
                .color(NamedTextColor.DARK_GRAY);
        }

        Guild owner = guildService.getGuildById(data.guildId()).orElse(null);
        if (owner == null) {
            return Component.text("? ").color(NamedTextColor.DARK_GRAY);
        }

        String symbol;
        NamedTextColor color;

        // Determine symbol and color
        if (playerGuild != null && owner.getId().equals(playerGuild.getId())) {
            // Own guild: green ■ symbol
            symbol = "■";
            color = NamedTextColor.GREEN;
        } else {
            // Other guild: faction color ▪ symbol
            symbol = "▪";
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
