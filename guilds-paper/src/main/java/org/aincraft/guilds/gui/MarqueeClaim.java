package org.aincraft.guilds.gui;

import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;

import java.util.UUID;

public final class MarqueeClaim {

    private MarqueeClaim() {
    }

    public record Result(int claimed, int skipped, boolean allowed) {
        public static Result denied() {
            return new Result(0, 0, false);
        }
    }

    public static Result commit(PlotService plots, PermissionService permissions, UUID player,
                                String viewerGuild, String world,
                                int minX, int maxX, int minZ, int maxZ) {
        if (!permissions.canClaimForGuild(player, viewerGuild)) {
            return Result.denied();
        }
        int claimed = 0;
        int skipped = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (plots.getGuildBlock(x, z, world).isPresent()) {
                    skipped++;
                } else if (plots.claimGuildBlock(x, z, world, viewerGuild)) {
                    claimed++;
                } else {
                    skipped++;
                }
            }
        }
        return new Result(claimed, skipped, true);
    }
}
