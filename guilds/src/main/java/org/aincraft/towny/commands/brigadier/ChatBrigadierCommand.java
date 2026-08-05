package org.aincraft.towny.commands.brigadier;

import com.google.inject.Inject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.ChatService;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.bukkit.entity.Player;

/**
 * Brigadier command for the town chat system.
 * /tc <message> — send one-off town chat message
 * /tc (with no args) — toggle town chat as default channel
 * /townchat — alias for town chat toggling
 */
public class ChatBrigadierCommand {

    private final TownyPlugin plugin;
    private final ChatService chatService;
    private final TownService townService;
    private final ResidentService residentService;

    @Inject
    public ChatBrigadierCommand(TownyPlugin plugin, ChatService chatService,
                                TownService townService, ResidentService residentService) {
        this.plugin = plugin;
        this.chatService = chatService;
        this.townService = townService;
        this.residentService = residentService;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("tc")
                .requires(source -> source.getSender().hasPermission("towny.chat.town"))
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

        boolean newState = !chatService.isTownChatEnabled(player.getUniqueId());
        chatService.setTownChatEnabled(player.getUniqueId(), newState);
        
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

        Town town = getPlayerTown(player);
        if (town == null) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        String message = StringArgumentType.getString(ctx, "message");
        chatService.sendTownChat(town.getId(), player, message);
        
        return Command.SINGLE_SUCCESS;
    }

    private Town getPlayerTown(Player player) {
        String townName = residentService.getResident(player.getUniqueId())
                .filter(resident -> resident.hasTown())
                .map(org.aincraft.towny.models.Resident::getTown)
                .orElse(null);

        if (townName == null) {
            return null;
        }

        return townService.getTown(townName).orElse(null);
    }
}