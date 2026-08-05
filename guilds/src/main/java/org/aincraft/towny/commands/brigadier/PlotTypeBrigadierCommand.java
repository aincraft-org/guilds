package org.aincraft.towny.commands.brigadier;

import com.google.inject.Inject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.services.*;

/**
 * Brigadier implementation of the plot type command
 */
public class PlotTypeBrigadierCommand {

    private final TownyPlugin plugin;
    private final ResidentService residentService;
    private final TownService townService;
    private final PlotService plotService;
    private final PermissionService permissionService;

    @Inject
    public PlotTypeBrigadierCommand(TownyPlugin plugin, ResidentService residentService,
                                   TownService townService, PlotService plotService,
                                   PermissionService permissionService) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.townService = townService;
        this.plotService = plotService;
        this.permissionService = permissionService;
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
        sender.sendMessage("§f/plottype list§7 - List all plot types");
        sender.sendMessage("§f/plottype info <type>§7 - Show plot type information");
        sender.sendMessage("§f/plottype create <type> <name>§7 - Create new plot type");
        sender.sendMessage("§f/plottype delete <type>§7 - Delete plot type");
        return Command.SINGLE_SUCCESS;
    }
}