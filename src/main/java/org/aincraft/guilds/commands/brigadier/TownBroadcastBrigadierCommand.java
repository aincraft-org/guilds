package org.aincraft.guilds.commands.brigadier;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/**
 * Brigadier implementation of the town broadcast command
 */
public class TownBroadcastBrigadierCommand {

    public TownBroadcastBrigadierCommand() {
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("broadcast")
            .requires(source -> source.getSender().hasPermission("guilds.broadcast"))
            .executes(this::showHelp)
            .build();
    }

    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Town Broadcast Commands ===");
        sender.sendMessage("§7Town broadcast subcommands are not yet implemented.");
        return Command.SINGLE_SUCCESS;
    }
}
