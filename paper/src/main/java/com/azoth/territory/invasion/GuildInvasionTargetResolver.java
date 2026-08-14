package com.azoth.territory.invasion;

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
        Guild guild = guildService.getAllGuilds().stream().filter(g -> g.getName() != null && g.getName().equalsIgnoreCase(requestedName)).findFirst().orElse(null);
        if (guild == null) return rejected("unknown guild");
        List<GuildBlock> claims = plotService.getGuildBlocksInGuild(guild.getName());
        if (claims == null || claims.isEmpty()) return rejected("guild has no claimed plots");
        Location configured = guild.getSpawnLocation();
        if (configured != null) return resolved(guild, configured);
        boolean online = guild.getResidents().stream().anyMatch(uuid -> { var player = Bukkit.getPlayer(uuid); return player != null && player.isOnline(); });
        if (!online) return rejected("guild has no online resident");
        GuildBlock home = guild.getHomeBlock();
        if (home == null) return rejected("guild home is unavailable");
        World world = Bukkit.getWorld(home.getWorld());
        if (world == null) return rejected("target world is unavailable");
        int[] center = home.getCenterCoordinates();
        Location fallback = new Location(center[0] + 0.5, world.getHighestBlockYAt(center[0], center[1]) + 1, center[1] + 0.5, home.getWorld());
        if (!insideClaim(fallback, claims, guild.getId())) return rejected("guild home is outside claim");
        return resolved(guild, fallback);
    }
    private boolean insideClaim(Location location, List<GuildBlock> claims, String guildId) {
        int[] chunk = location.getChunkCoordinates();
        return plotService.getGuildBlock(chunk[0], chunk[1], location.getWorld()).map(block -> guildId.equals(block.getGuildId()) && claims.stream().anyMatch(c -> c.getId().equals(block.getId()))).orElse(false);
    }
    private static Resolution resolved(Guild guild, Location center) { return new Resolution(Optional.of(new ResolvedInvasionTarget(guild.getId(), guild.getName(), center)), null); }
    private static Resolution rejected(String reason) { return new Resolution(Optional.empty(), reason); }
}
