package dev.mintychochip.guilds.commands.brigadier;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

/**
 * Brigadier implementation of the guild broadcast command
 */
public class GuildBroadcastBrigadierCommand {

    /** Creates a new guild broadcast brigadier command instance. */
    public GuildBroadcastBrigadierCommand() {
    }

    /**
     * Builds the command.
     * @return the result
     */
    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("broadcast")
            .requires(source -> source.getSender().hasPermission("guilds.broadcast"))
            .executes(this::showHelp)
            .build();
    }

    /**
     * Performs the show help operation.
     * @param ctx the ctx
     * @return the result
     */
    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Guild Broadcast Commands ===");
        sender.sendMessage("§7Guild broadcast subcommands are not yet implemented.");
        return Command.SINGLE_SUCCESS;
    }
}
