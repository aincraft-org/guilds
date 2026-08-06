package org.aincraft.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.TownService;

/**
 * Brigadier implementation of the town perm command (NEW)
 */
public class TownPermBrigadierCommand {

    private final JavaPlugin plugin;
    private final ResidentService residentService;
    private final TownService townService;
    private final PlotService plotService;
    private final PermissionService permissionService;


    public TownPermBrigadierCommand(JavaPlugin plugin, ResidentService residentService,
                                   TownService townService, PlotService plotService,
                                   PermissionService permissionService) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.townService = townService;
        this.plotService = plotService;
        this.permissionService = permissionService;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("townperm")
            .requires(source -> source.getSender().hasPermission("guilds.admin.perm"))
            .executes(this::showHelp)
            .build();
    }

    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Town Permission Commands ===");
        sender.sendMessage("§f/townperm set <role> <perms>§7 - Set role permissions");
        sender.sendMessage("§f/townperm add <role> <perms>§7 - Add permissions to role");
        sender.sendMessage("§f/townperm remove <role> <perms>§7 - Remove permissions from role");
        sender.sendMessage("§f/townperm list [role]§7 - List permissions for role");
        sender.sendMessage("§f/townperm reset <role>§7 - Reset role permissions");
        return Command.SINGLE_SUCCESS;
    }
}