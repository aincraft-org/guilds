package org.aincraft.guilds.gui;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;

import java.util.Optional;
import java.util.UUID;

public final class MarqueeUnclaim {

    private MarqueeUnclaim() {
    }

    public record Result(int unclaimed, int skipped, boolean allowed) {
        public static Result denied() {
            return new Result(0, 0, false);
        }
    }

    public static Result commit(PlotService plots, GuildService guilds, PermissionService permissions,
                                UUID player, String viewerGuild, String world,
                                int minX, int maxX, int minZ, int maxZ) {
        if (viewerGuild == null
                || !permissions.hasPermission(player, "unclaim", "guild", viewerGuild)) {
            return Result.denied();
        }

        int unclaimed = 0;
        int skipped = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Optional<GuildBlock> block = plots.getGuildBlock(x, z, world);
                if (block.isEmpty()) {
                    skipped++;
                    continue;
                }

                Optional<Guild> owner = guilds.getGuildById(block.get().getGuildId());
                if (owner.isEmpty() || !viewerGuild.equals(owner.get().getName())) {
                    skipped++;
                } else if (plots.unclaimGuildBlock(x, z, world)) {
                    unclaimed++;
                } else {
                    skipped++;
                }
            }
        }
        return new Result(unclaimed, skipped, true);
    }
}
