package dev.mintychochip.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;
import dev.mintychochip.guilds.services.PermissionService;
import dev.mintychochip.guilds.services.PlotService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.GuildService;

/**
 * Brigadier implementation of the guild perm command (NEW)
 */
public class GuildPermBrigadierCommand {

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The resident service. */
    private final ResidentService residentService;
    /** The guild service. */
    private final GuildService guildService;
    /** The plot service. */
    private final PlotService plotService;
    /** The permission service. */
    private final PermissionService permissionService;


    /**
     * Creates a new guild perm brigadier command instance.
     * @param plugin the plugin
     * @param residentService the resident service
     * @param guildService the guild service
     * @param plotService the plot service
     * @param permissionService the permission service
     */
    public GuildPermBrigadierCommand(JavaPlugin plugin, ResidentService residentService,
                                   GuildService guildService, PlotService plotService,
                                   PermissionService permissionService) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.guildService = guildService;
        this.plotService = plotService;
        this.permissionService = permissionService;
    }

    /**
     * Builds the command.
     * @return the result
     */
    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("guildperm")
            .requires(source -> source.getSender().hasPermission("guilds.admin.perm"))
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
        sender.sendMessage("§e=== Guild Permission Commands ===");
        sender.sendMessage("§f/guildperm set <role> <perms>§7 - Set role permissions");
        sender.sendMessage("§f/guildperm add <role> <perms>§7 - Add permissions to role");
        sender.sendMessage("§f/guildperm remove <role> <perms>§7 - Remove permissions from role");
        sender.sendMessage("§f/guildperm list [role]§7 - List permissions for role");
        sender.sendMessage("§f/guildperm reset <role>§7 - Reset role permissions");
        return Command.SINGLE_SUCCESS;
    }
}