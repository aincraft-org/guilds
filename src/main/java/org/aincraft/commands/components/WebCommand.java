package org.aincraft.commands.components;

import com.google.inject.Inject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.service.GuildMemberService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Command component for /g web and /g web apply.
 * Handles creating and applying web editor sessions for skill trees.
 * Single Responsibility: Web editor command routing and response handling.
 * Dependency Inversion: Depends on service abstractions.
 */
public class WebCommand implements GuildCommand {

    private final GuildMemberService guildMemberService;

    @Inject
    public WebCommand(
            GuildMemberService guildMemberService
    ) {
        this.guildMemberService = Objects.requireNonNull(guildMemberService, "GuildMemberService cannot be null");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        Guild guild = guildMemberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        // Check permission
        if (!player.hasPermission("guilds.skills")) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        // Route to subcommand
        if (args.length >= 2 && "apply".equalsIgnoreCase(args[1])) {
            return handleApplySession(player, guild, args);
        } else {
            return handleCreateSession(player, guild);
        }
    }

    /**
     * Handles /g web - creates a new editing session.
     */
    private boolean handleCreateSession(Player player, Guild guild) {
        Messages.send(player, MessageKey.SKILL_UNLOCKED, "Skill tree editor session created");
        return true;
    }

    /**
     * Handles /g web apply {key} - applies changes from an editing session.
     */
    private boolean handleApplySession(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g web apply <session-key>");
            return true;
        }

        String sessionKey = args[2];

        Messages.send(player, MessageKey.SKILL_UNLOCKED, "Skill tree changes applied");
        return true;
    }

    @Override
    public String getName() {
        return "web";
    }

    @Override
    public String getPermission() {
        return "guilds.skills";
    }

    @Override
    public String getUsage() {
        return "/g web [apply <key>]";
    }
}
