package org.aincraft.commands.components;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import java.util.UUID;
import org.aincraft.claim.ChunkClaimLog;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.TerritoryService;
import org.bukkit.entity.Player;

/**
 * Displays chunk claim/unclaim history for the player's guild.
 */
public class ClaimLogSubcomponent {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd HH:mm");
    private static final int LOG_PAGE_SIZE = 10;
    private static final int UUID_DISPLAY_LENGTH = 8;

    private final GuildMemberService memberService;
    private final TerritoryService territoryService;

    public ClaimLogSubcomponent(GuildMemberService memberService, TerritoryService territoryService) {
        this.memberService = Objects.requireNonNull(memberService, "Member service cannot be null");
        this.territoryService = Objects.requireNonNull(territoryService, "Territory service cannot be null");
    }

    public boolean execute(Player player, String[] args) {
        org.aincraft.Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        UUID guildId = guild.getId();
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        int offset = (page - 1) * LOG_PAGE_SIZE;

        List<ChunkClaimLog> logs = territoryService.getGuildClaimLogs(guildId, LOG_PAGE_SIZE * page);

        if (logs.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING, "No claim history recorded"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Claim History", " (Page " + page + ")"));

        int shown = 0;
        for (int i = offset; i < logs.size() && shown < LOG_PAGE_SIZE; i++) {
            ChunkClaimLog log = logs.get(i);
            String playerName = org.bukkit.Bukkit.getOfflinePlayer(log.playerId()).getName();
            if (playerName == null) playerName = log.playerId().toString().substring(0, UUID_DISPLAY_LENGTH);

            String action = log.action() == ChunkClaimLog.ActionType.CLAIM ? "<green>+</green>" : "<red>-</red>";
            String time = DATE_FORMAT.format(new Date(log.timestamp()));
            String location = "[" + log.chunk().world() + "] " + log.chunk().x() + "/" + log.chunk().z();

            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>" + time + " " + action + " <gold>" + location + "</gold> by " + playerName + "</gray>"));
            shown++;
        }

        if (logs.size() > page * LOG_PAGE_SIZE) {
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>Use <yellow>/g log claim " + (page + 1) + "</yellow> for more</gray>"));
        }

        return true;
    }
}
