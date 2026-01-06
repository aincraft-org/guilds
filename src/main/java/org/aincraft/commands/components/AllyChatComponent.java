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
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have permission to use guild chat.</error>");
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild.</error>");
            return true;
        }

        if (!guild.isOwner(player.getUniqueId()) &&
            !permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.CHAT_GUILD)) {
            Mint.sendMessage(player, "<error>You don't have permission to use guild chat.</error>");
            return true;
        }

        if (args.length == 0) {
            ChatMode newMode = chatModeService.toggleMode(player.getUniqueId(), ChatMode.ALLY);

            if (newMode == ChatMode.ALLY) {
                Mint.sendMessage(player, "<success>Ally chat enabled.</success>");
            } else {
                Mint.sendMessage(player, "<neutral>Ally chat disabled.</neutral>");
            }
            return true;
        }

        String message = String.join(" ", args);

        ChatMode originalMode = chatModeService.getMode(player.getUniqueId());
        chatModeService.setMode(player.getUniqueId(), ChatMode.ALLY);

        player.chat(message);

        chatModeService.setMode(player.getUniqueId(), originalMode);

        return true;
    }
}
