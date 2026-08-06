package org.aincraft.towny.commands.brigadier;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/**
 * Brigadier implementation of the plot type command
 */
public class PlotTypeBrigadierCommand {

    public PlotTypeBrigadierCommand() {
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("plottype")
            .requires(source -> source.getSender().hasPermission("towny.admin.plottype"))
            .executes(this::showHelp)
            .build();
    }

    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Plot Type Commands ===");
        sender.sendMessage("§7Plot type management subcommands are not yet implemented.");
        return Command.SINGLE_SUCCESS;
    }
}
