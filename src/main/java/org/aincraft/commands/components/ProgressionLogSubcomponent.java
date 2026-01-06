package org.aincraft.commands.components;


import dev.mintychochip.mint.Mint;
import org.aincraft.progression.ProgressionLog;
import org.aincraft.progression.storage.ProgressionLogRepository;
import org.aincraft.service.GuildMemberService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Displays guild progression history (XP gains, level ups, admin changes).
 * Extracted to support unified /g log command structure.
 */
public class ProgressionLogSubcomponent {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd HH:mm");
    private static final int LOG_PAGE_SIZE = 10;
    private static final int UUID_DISPLAY_LENGTH = 8;

    private final GuildMemberService memberService;
    private final ProgressionLogRepository logRepository;

    public ProgressionLogSubcomponent(GuildMemberService memberService, ProgressionLogRepository logRepository) {
        this.memberService = Objects.requireNonNull(memberService, "Member service cannot be null");
        this.logRepository = Objects.requireNonNull(logRepository, "Log repository cannot be null");
    }

    public boolean execute(Player player, String[] args) {
        org.aincraft.Guild guild = memberService.getPlayerGuild(player.getUniqueId());

        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        int offset = (page - 1) * LOG_PAGE_SIZE;

        List<ProgressionLog> logs = logRepository.findByGuild(guild.getId(), LOG_PAGE_SIZE * page);

        if (logs.isEmpty()) {
            Mint.sendMessage(player, "<neutral>List is empty</neutral>");
            return true;
        }

        Mint.sendMessage(player, "<info>Progression History (Page <primary>" + page + "</primary>)</info>");

        int shown = 0;
        for (int i = offset; i < logs.size() && shown < LOG_PAGE_SIZE; i++) {
            ProgressionLog log = logs.get(i);
            String time = DATE_FORMAT.format(new Date(log.timestamp()));
            String message = formatLogEntry(log);

            Mint.sendMessage(player,
                    "<neutral>" + time + " " + message + "</neutral>");
            shown++;
        }

        if (logs.size() > page * LOG_PAGE_SIZE) {
            Mint.sendMessage(player,
                    "<neutral>Use <secondary>/g log progression " + (page + 1) + "</secondary> for more</neutral>");
        }

        return true;
    }

    private String formatLogEntry(ProgressionLog log) {
        String playerName = "System";
        if (log.playerId() != null) {
            String name = Bukkit.getOfflinePlayer(log.playerId()).getName();
            playerName = (name != null) ? name : log.playerId().toString().substring(0, UUID_DISPLAY_LENGTH);
        }

        return switch (log.action()) {
            case XP_GAIN -> String.format("<success>+%,d XP</success> from %s <neutral>(%s)</neutral>",
                    log.amount(), log.details(), playerName);

            case LEVEL_UP -> String.format("<secondary>★ Level Up!</secondary> Level <primary>%d</primary> <neutral>%s</neutral>",
                    log.amount(), log.details() != null ? log.details() : "");

            case ADMIN_SET_LEVEL -> String.format("<error>[ADMIN]</error> Set level to <primary>%d</primary> by %s",
                    log.amount(), playerName);

            case ADMIN_ADD_XP -> String.format("<error>[ADMIN]</error> <success>+%,d XP</success> added by %s",
                    log.amount(), playerName);

            case ADMIN_SET_XP -> String.format("<error>[ADMIN]</error> Set XP to <success>%,d</success> by %s",
                    log.amount(), playerName);

            case ADMIN_RESET_LEVEL -> String.format("<error>[ADMIN]</error> Level reset to 1 by %s", playerName);

            case ADMIN_RESET_XP -> String.format("<error>[ADMIN]</error> XP reset to 0 by %s", playerName);
        };
    }
}
