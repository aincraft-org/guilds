package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.chat.ChatModeService;
import org.aincraft.chat.ChatModeService.ChatMode;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for ally chat command (/ac).
 * No args: Toggle ally chat mode.
 * With args: Send one-time message to ally chat.
 */
public class AllyChatComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final PermissionService permissionService;
    private final ChatModeService chatModeService;

    @Inject
    public AllyChatComponent(GuildMemberService memberService, PermissionService permissionService,
                            ChatModeService chatModeService) {
        this.memberService = memberService;
        this.permissionService = permissionService;
        this.chatModeService = chatModeService;
    }

    @Override
    public String getName() {
        return "ac";
    }

    @Override
    public String getPermission() {
        return "guilds.chat";
    }

    @Override
    public String getUsage() {
        return "/ac [message]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                "You don't have permission to use ally chat"));
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                "You are not in a guild"));
            return true;
        }

        // Check CHAT_GUILD permission (owners bypass)
        if (!guild.isOwner(player.getUniqueId()) &&
            !permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.CHAT_GUILD)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                "You don't have permission to use ally chat"));
            return true;
        }

        // No message - toggle mode
        if (args.length == 0) {
            ChatMode newMode = chatModeService.toggleMode(player.getUniqueId(), ChatMode.ALLY);

            if (newMode == ChatMode.ALLY) {
                player.sendMessage(MessageFormatter.deserialize(
                    "<aqua>Ally chat enabled</aqua>"));
            } else {
                player.sendMessage(MessageFormatter.deserialize(
                    "<gray>Ally chat disabled</gray>"));
            }
            return true;
        }

        // Has message - send one-time message without toggling
        String message = String.join(" ", args);

        // Temporarily set mode to ALLY
        ChatMode originalMode = chatModeService.getMode(player.getUniqueId());
        chatModeService.setMode(player.getUniqueId(), ChatMode.ALLY);

        // Trigger chat event
        player.chat(message);

        // Restore original mode
        chatModeService.setMode(player.getUniqueId(), originalMode);

        return true;
    }
}
