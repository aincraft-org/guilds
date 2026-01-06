package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.chat.ChatModeService;
import org.aincraft.chat.ChatModeService.ChatMode;
import org.aincraft.commands.GuildCommand;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for officer chat command (/oc).
 * No args: Toggle officer chat mode.
 * With args: Send one-time message to officer chat.
 *
 * Officers are guild members with CHAT_OFFICER permission.
 * Only officers can send and receive officer-level messages.
 */
public class OfficerChatComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final PermissionService permissionService;
    private final ChatModeService chatModeService;

    @Inject
    public OfficerChatComponent(GuildMemberService memberService, PermissionService permissionService,
                               ChatModeService chatModeService) {
        this.memberService = memberService;
        this.permissionService = permissionService;
        this.chatModeService = chatModeService;
    }

    @Override
    public String getName() {
        return "oc";
    }

    @Override
    public String getPermission() {
        return "guilds.chat";
    }

    @Override
    public String getUsage() {
        return "/oc [message]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have permission to use officer chat</error>");
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        if (!guild.isOwner(player.getUniqueId()) &&
            !permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.CHAT_OFFICER)) {
            Mint.sendMessage(player, "<error>You don't have permission to use officer chat</error>");
            return true;
        }

        if (args.length == 0) {
            ChatMode newMode = chatModeService.toggleMode(player.getUniqueId(), ChatMode.OFFICER);

            if (newMode == ChatMode.OFFICER) {
                Mint.sendMessage(player, "<success>Officer chat enabled</success>");
            } else {
                Mint.sendMessage(player, "<info>Officer chat disabled</info>");
            }
            return true;
        }

        String message = String.join(" ", args);

        ChatMode originalMode = chatModeService.getMode(player.getUniqueId());
        chatModeService.setMode(player.getUniqueId(), ChatMode.OFFICER);

        player.chat(message);

        chatModeService.setMode(player.getUniqueId(), originalMode);

        return true;
    }
}
