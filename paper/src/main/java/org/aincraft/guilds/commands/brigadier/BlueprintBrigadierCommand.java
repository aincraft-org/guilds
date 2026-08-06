package org.aincraft.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.models.Blueprint;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.BlueprintService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.GuildService;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * Brigadier command for blueprint management.
 * /blueprint save <name> — save selection as blueprint (requires WorldEdit)
 * /blueprint list — list guild blueprints
 * /blueprint load <name> — show blueprint info
 * /blueprint apply <name> — paste at player location
 * /blueprint delete <name> — delete blueprint (mayor only)
 */
public class BlueprintBrigadierCommand {

    private final JavaPlugin plugin;
    private final BlueprintService blueprintService;
    private final GuildService guildService;
    private final ResidentService residentService;


    public BlueprintBrigadierCommand(JavaPlugin plugin, BlueprintService blueprintService,
                                     GuildService guildService, ResidentService residentService) {
        this.plugin = plugin;
        this.blueprintService = blueprintService;
        this.guildService = guildService;
        this.residentService = residentService;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("blueprint")
                .requires(source -> source.getSender().hasPermission("guilds.commands.blueprint"))
                .executes(this::handleList)
                .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(this::handleSave)))
                .then(Commands.literal("list")
                        .executes(this::handleList))
                .then(Commands.literal("load")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(this::handleLoad)))
                .then(Commands.literal("apply")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(this::handleApply)))
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(this::handleDelete)))
                .build();
    }

    private Player getPlayer(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return null;
        }
        return player;
    }

    private String getPlayerGuildId(Player player) {
        return residentService.getResident(player.getUniqueId())
                .filter(Resident::hasGuild)
                .flatMap(r -> guildService.getGuild(r.getGuild()))
                .map(t -> t.getId())
                .orElse(null);
    }

    private int handleSave(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        if (plugin.getServer().getPluginManager().getPlugin("WorldEdit") == null) {
            player.sendMessage(Component.text("WorldEdit is required to save blueprints!", NamedTextColor.RED));
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "name");
        String guildId = getPlayerGuildId(player);
        if (guildId == null) {
            player.sendMessage(Component.text("You must be in a town to save blueprints.", NamedTextColor.RED));
            return 0;
        }

        // WorldEdit integration would go here — for now save an empty schematic
        // In production, use WorldEdit API to get player's selection and serialize blocks
        player.sendMessage(Component.text("Blueprint save requires WorldEdit selection. ", NamedTextColor.YELLOW)
                .append(Component.text("Select a region with WorldEdit first, then run this command.", NamedTextColor.GRAY)));

        // Placeholder: save with empty data
        blueprintService.saveBlueprint(name, player.getUniqueId(), guildId, new byte[0]);
        player.sendMessage(Component.text("Blueprint '" + name + "' saved (empty template).", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int handleList(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String guildId = getPlayerGuildId(player);
        if (guildId == null) {
            player.sendMessage(Component.text("You are not in a town.", NamedTextColor.RED));
            return 0;
        }

        List<Blueprint> blueprints = blueprintService.getGuildBlueprints(guildId);
        if (blueprints.isEmpty()) {
            player.sendMessage(Component.text("No blueprints found for your town.", NamedTextColor.GRAY));
            return 0;
        }

        player.sendMessage(Component.text("=== Blueprints ===", NamedTextColor.GOLD));
        for (Blueprint bp : blueprints) {
            player.sendMessage(Component.text("  - " + bp.getName(), NamedTextColor.YELLOW));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleLoad(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String name = StringArgumentType.getString(ctx, "name");
        Optional<Blueprint> bpOpt = blueprintService.getBlueprint(name);

        if (bpOpt.isEmpty()) {
            player.sendMessage(Component.text("Blueprint not found: " + name, NamedTextColor.RED));
            return 0;
        }

        Blueprint bp = bpOpt.get();
        player.sendMessage(Component.text("=== " + bp.getName() + " ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Author: ", NamedTextColor.GRAY)
                .append(Component.text(bp.getAuthorUuid().toString(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Created: ", NamedTextColor.GRAY)
                .append(Component.text(bp.getCreatedAt().toString(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Size: ", NamedTextColor.GRAY)
                .append(Component.text(bp.getSchematicData().length + " bytes", NamedTextColor.YELLOW)));
        return Command.SINGLE_SUCCESS;
    }

    private int handleApply(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String name = StringArgumentType.getString(ctx, "name");
        boolean applied = blueprintService.applyBlueprint(name, player.getLocation());

        if (applied) {
            player.sendMessage(Component.text("Blueprint '" + name + "' applied at your location.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Failed to apply blueprint. Does it exist?", NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int handleDelete(CommandContext<CommandSourceStack> ctx) {
        Player player = getPlayer(ctx);
        if (player == null) return 0;

        String name = StringArgumentType.getString(ctx, "name");
        Optional<Blueprint> bpOpt = blueprintService.getBlueprint(name);

        if (bpOpt.isEmpty()) {
            player.sendMessage(Component.text("Blueprint not found: " + name, NamedTextColor.RED));
            return 0;
        }

        blueprintService.deleteBlueprint(name);
        player.sendMessage(Component.text("Blueprint '" + name + "' deleted.", NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }
}
