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
 * Brigadier implementation of the town broadcast command
 */
public class TownBroadcastBrigadierCommand {

    private final TownyPlugin plugin;
    private final ResidentService residentService;
    private final TownService townService;
    private final PlotService plotService;
    private final PermissionService permissionService;

    @Inject
    public TownBroadcastBrigadierCommand(TownyPlugin plugin, ResidentService residentService,
                                        TownService townService, PlotService plotService,
                                        PermissionService permissionService) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.townService = townService;
        this.plotService = plotService;
        this.permissionService = permissionService;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("broadcast")
            .requires(source -> source.getSender().hasPermission("towny.broadcast"))
            .executes(this::showHelp)
            .build();
    }

    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Town Broadcast Commands ===");
        sender.sendMessage("§f/broadcast create <type> <message>§7 - Create broadcast message");
        sender.sendMessage("§f/broadcast announce <message>§7 - Send announcement");
        sender.sendMessage("§f/broadcast welcome <message>§7 - Set welcome message");
        sender.sendMessage("§f/broadcast list§7 - List broadcasts");
        sender.sendMessage("§f/broadcast read [id]§7 - Read broadcast");
        return Command.SINGLE_SUCCESS;
    }
}