package org.aincraft.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.ChatService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.GuildService;
import org.bukkit.entity.Player;

/**
 * Brigadier command for the guild chat system.
 * /tc <message> — send one-off guild chat message
 * /tc (with no args) — toggle guild chat as default channel
 * /townchat — alias for guild chat toggling
 */
public class ChatBrigadierCommand {

    private final JavaPlugin plugin;
    private final ChatService chatService;
    private final GuildService guildService;
    private final ResidentService residentService;


    public ChatBrigadierCommand(JavaPlugin plugin, ChatService chatService,
                                GuildService guildService, ResidentService residentService) {
        this.plugin = plugin;
        this.chatService = chatService;
        this.guildService = guildService;
        this.residentService = residentService;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("tc")
                .requires(source -> source.getSender().hasPermission("guilds.chat.town"))
                .executes(this::handleToggle)
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(this::handleSendMessage))
                .build();
    }

    private int handleToggle(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        boolean newState = !chatService.isGuildChatEnabled(player.getUniqueId());
        chatService.setGuildChatEnabled(player.getUniqueId(), newState);

        if (newState) {
            player.sendMessage("§aTown chat enabled! Your messages will now be sent to your town.");
        } else {
            player.sendMessage("§7Town chat disabled.");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleSendMessage(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        Guild guild = getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        String message = StringArgumentType.getString(ctx, "message");
        chatService.sendGuildChat(guild.getId(), player, message);

        return Command.SINGLE_SUCCESS;
    }

    private Guild getPlayerGuild(Player player) {
        String guildName = residentService.getResident(player.getUniqueId())
                .filter(resident -> resident.hasGuild())
                .map(org.aincraft.guilds.models.Resident::getGuild)
                .orElse(null);

        if (guildName == null) {
            return null;
        }

        return guildService.getGuild(guildName).orElse(null);
    }
}