package dev.mintychochip.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.ChatService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.GuildService;
import org.bukkit.entity.Player;

/**
 * Brigadier command for the guild chat system.
 * /tc <message> — send one-off guild chat message
 * /tc (with no args) — toggle guild chat as default channel
 * /guildchat — alias for guild chat toggling
 */
public class ChatBrigadierCommand {

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The chat service. */
    private final ChatService chatService;
    /** The guild service. */
    private final GuildService guildService;
    /** The resident service. */
    private final ResidentService residentService;


    /**
     * Creates a new chat brigadier command instance.
     * @param plugin the plugin
     * @param chatService the chat service
     * @param guildService the guild service
     * @param residentService the resident service
     */
    public ChatBrigadierCommand(JavaPlugin plugin, ChatService chatService,
                                GuildService guildService, ResidentService residentService) {
        this.plugin = plugin;
        this.chatService = chatService;
        this.guildService = guildService;
        this.residentService = residentService;
    }

    /**
     * Builds the command.
     * @return the result
     */
    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("tc")
                .requires(source -> source.getSender().hasPermission("guilds.chat.guild"))
                .executes(this::handleToggle)
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(this::handleSendMessage))
                .build();
    }

    /**
     * Handles the toggle.
     * @param ctx the ctx
     * @return the result
     */
    private int handleToggle(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        boolean newState = !chatService.isGuildChatEnabled(player.getUniqueId());
        chatService.setGuildChatEnabled(player.getUniqueId(), newState);

        if (newState) {
            player.sendMessage("§aGuild chat enabled! Your messages will now be sent to your guild.");
        } else {
            player.sendMessage("§7Guild chat disabled.");
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the send message.
     * @param ctx the ctx
     * @return the result
     */
    private int handleSendMessage(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        Guild guild = getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        String message = StringArgumentType.getString(ctx, "message");
        chatService.sendGuildChat(guild.getId(), player, message);

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Returns the player guild.
     * @param player the player
     * @return the result
     */
    private Guild getPlayerGuild(Player player) {
        String guildName = residentService.getResident(player.getUniqueId())
                .filter(resident -> resident.hasGuild())
                .map(dev.mintychochip.guilds.models.Resident::getGuild)
                .orElse(null);

        if (guildName == null) {
            return null;
        }

        return guildService.getGuild(guildName).orElse(null);
    }
}