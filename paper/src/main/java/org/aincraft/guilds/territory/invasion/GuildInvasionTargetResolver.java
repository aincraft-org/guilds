package org.aincraft.guilds.territory.invasion;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.models.Location;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PlotService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import java.util.List;
import java.util.Optional;

public final class GuildInvasionTargetResolver {
    public record ResolvedInvasionTarget(String guildId, String guildName, Location center) {}
    public record Resolution(Optional<ResolvedInvasionTarget> target, String rejection) {
        public boolean isResolved() { return target.isPresent(); }
        public boolean isRejected() { return target.isEmpty(); }
    }
    private final GuildService guildService;
    private final PlotService plotService;
    public GuildInvasionTargetResolver(GuildService guildService, PlotService plotService) { this.guildService = guildService; this.plotService = plotService; }
    public Resolution resolve(String requestedName) {
        if (requestedName == null) return rejected("unknown guild");
        List<Guild> matches = guildService.getAllGuilds().stream()
                .filter(g -> g.getName() != null && g.getName().equalsIgnoreCase(requestedName))
                .toList();
        if (matches.size() != 1) return rejected(matches.isEmpty() ? "unknown guild" : "ambiguous guild name");
        Guild guild = matches.getFirst();
        List<GuildBlock> claims = plotService.getGuildBlocksInGuild(guild.getName());
        if (claims == null || claims.isEmpty()) return rejected("guild has no claimed plots");
        boolean online = guild.getResidents().stream().anyMatch(uuid -> {
            var player = Bukkit.getPlayer(uuid);
            return player != null && player.isOnline();
        });
        if (!online) return rejected("guild has no online resident");
        Location configured = guild.getSpawnLocation();
        if (configured != null && isEligible(configured, claims, guild.getId())) {
            return resolved(guild, configured);
        }
        GuildBlock home = guild.getHomeBlock();
        if (home == null) return rejected(configured == null ? "guild home is unavailable" : "guild spawn is outside claim");
        World world = Bukkit.getWorld(home.getWorld());
        if (world == null) return rejected("target world is unavailable");
        int[] center = home.getCenterCoordinates();
        if (!ownsChunk(center[0] >> 4, center[1] >> 4, home.getWorld(), claims, guild.getId())) {
            return rejected("guild home is outside claim");
        }
        int y = world.getHighestBlockYAt(center[0], center[1]);
        Location fallback = new Location(center[0] + 0.5, y + 1, center[1] + 0.5, home.getWorld());
        return resolved(guild, fallback);
    }

    private boolean isEligible(Location location, List<GuildBlock> claims, String guildId) {
        if (Bukkit.getWorld(location.getWorld()) == null) return false;
        int[] chunk = location.getChunkCoordinates();
        return ownsChunk(chunk[0], chunk[1], location.getWorld(), claims, guildId);
    }

    private boolean ownsChunk(int chunkX, int chunkZ, String world, List<GuildBlock> claims, String guildId) {
        return plotService.getGuildBlock(chunkX, chunkZ, world)
                .filter(block -> guildId.equals(block.getGuildId()))
                .filter(block -> claims.stream().anyMatch(claim -> claim.getId().equals(block.getId())))
                .isPresent();
    }

    private static Resolution resolved(Guild guild, Location center) {
        return new Resolution(Optional.of(new ResolvedInvasionTarget(guild.getId(), guild.getName(), center)), null);
    }

    private static Resolution rejected(String reason) { return new Resolution(Optional.empty(), reason); }
}
