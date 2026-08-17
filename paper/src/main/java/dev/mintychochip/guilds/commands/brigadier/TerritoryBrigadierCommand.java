package dev.mintychochip.guilds.commands.brigadier;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/** Paper Brigadier adapter for the territory command behavior. */
public final class TerritoryBrigadierCommand {
    /** The behavior. */
    private final TerritoryCommand behavior;

    /**
     * Creates a new territory brigadier command instance.
     * @param behavior the behavior
     */
    public TerritoryBrigadierCommand(TerritoryCommand behavior) {
        this.behavior = behavior;
    }

    /**
     * Builds the command.
     * @return the result
     */
    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        var root = Commands.literal("territory")
                .executes(ctx -> ok(behavior.lookupHere(sender(ctx))))
                .then(Commands.literal("lookup").executes(ctx -> ok(behavior.lookupHere(sender(ctx)))))
                .then(Commands.literal("here").executes(ctx -> ok(behavior.lookupHere(sender(ctx)))))
                .then(Commands.literal("list").executes(ctx -> ok(behavior.list(sender(ctx)))))
                .then(Commands.literal("reload").executes(ctx -> ok(behavior.reload(sender(ctx)))))
                .then(Commands.literal("save").executes(ctx -> ok(behavior.save(sender(ctx)))))
                .then(Commands.literal("web").executes(ctx -> ok(behavior.webStatus(sender(ctx)))))
                .then(govern())
                .then(influence())
                .then(declare())
                .then(upkeep())
                .then(standing())
                .then(invasion())
                .then(Commands.literal("building")
                        .executes(TerritoryBrigadierCommand::buildingMoved)
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                .executes(TerritoryBrigadierCommand::buildingMoved)));
        return root.build();
    }

    /**
     * Performs the govern operation.
     * @return the result
     */
    private LiteralArgumentBuilder<CommandSourceStack> govern() {
        return Commands.literal("govern")
                .then(Commands.argument("territoryId", StringArgumentType.word())
                        .then(Commands.argument("guildId", StringArgumentType.word())
                                .executes(ctx -> ok(behavior.govern(sender(ctx),
                                        StringArgumentType.getString(ctx, "territoryId"),
                                        StringArgumentType.getString(ctx, "guildId"))))));
    }

    /**
     * Performs the influence operation.
     * @return the result
     */
    private LiteralArgumentBuilder<CommandSourceStack> influence() {
        return Commands.literal("influence")
                .executes(ctx -> ok(behavior.influence(sender(ctx), null)))
                .then(Commands.literal("set")
                        .then(Commands.argument("territoryId", StringArgumentType.word())
                                .then(Commands.argument("guildId", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .executes(ctx -> ok(behavior.influenceSet(sender(ctx),
                                                        StringArgumentType.getString(ctx, "territoryId"),
                                                        StringArgumentType.getString(ctx, "guildId"),
                                                        StringArgumentType.getString(ctx, "value"))))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("territoryId", StringArgumentType.word())
                                .executes(ctx -> ok(behavior.influenceReset(sender(ctx),
                                        StringArgumentType.getString(ctx, "territoryId"))))))
                .then(Commands.argument("territoryId", StringArgumentType.word())
                        .executes(ctx -> ok(behavior.influence(sender(ctx),
                                StringArgumentType.getString(ctx, "territoryId")))));
    }

    /**
     * Performs the declare operation.
     * @return the result
     */
    private LiteralArgumentBuilder<CommandSourceStack> declare() {
        return Commands.literal("declare")
                .then(Commands.literal("cancel")
                        .then(Commands.argument("territoryId", StringArgumentType.word())
                                .executes(ctx -> ok(behavior.declareCancel(sender(ctx),
                                        StringArgumentType.getString(ctx, "territoryId"))))))
                .then(Commands.argument("territoryId", StringArgumentType.word())
                        .executes(ctx -> ok(behavior.declarePrompt(sender(ctx),
                                StringArgumentType.getString(ctx, "territoryId"))))
                        .then(Commands.literal("confirm")
                                .executes(ctx -> ok(behavior.declareConfirm(sender(ctx),
                                        StringArgumentType.getString(ctx, "territoryId"))))));
    }

    /**
     * Performs the upkeep operation.
     * @return the result
     */
    private LiteralArgumentBuilder<CommandSourceStack> upkeep() {
        return Commands.literal("upkeep")
                .executes(ctx -> ok(behavior.upkeep(sender(ctx), null)))
                .then(Commands.argument("territoryId", StringArgumentType.word())
                        .executes(ctx -> ok(behavior.upkeep(sender(ctx),
                                StringArgumentType.getString(ctx, "territoryId")))));
    }

    /**
     * Performs the standing operation.
     * @return the result
     */
    private LiteralArgumentBuilder<CommandSourceStack> standing() {
        return Commands.literal("standing")
                .executes(ctx -> ok(behavior.standing(sender(ctx), null)))
                .then(Commands.literal("set")
                        .then(Commands.argument("territoryId", StringArgumentType.word())
                                .then(Commands.argument("guildId", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .executes(ctx -> ok(behavior.standingSet(sender(ctx),
                                                        StringArgumentType.getString(ctx, "territoryId"),
                                                        StringArgumentType.getString(ctx, "guildId"),
                                                        StringArgumentType.getString(ctx, "value"))))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("territoryId", StringArgumentType.word())
                                .executes(ctx -> ok(behavior.standingReset(sender(ctx),
                                        StringArgumentType.getString(ctx, "territoryId"))))))
                .then(Commands.argument("territoryId", StringArgumentType.word())
                        .executes(ctx -> ok(behavior.standing(sender(ctx),
                                StringArgumentType.getString(ctx, "territoryId")))));
    }

    /**
     * Performs the invasion operation.
     * @return the result
     */
    private LiteralArgumentBuilder<CommandSourceStack> invasion() {
        return Commands.literal("invasion")
                .then(Commands.literal("start")
                        .then(Commands.argument("guild", StringArgumentType.greedyString())
                                .executes(ctx -> ok(behavior.invasionStart(sender(ctx),
                                        StringArgumentType.getString(ctx, "guild"))))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("guild", StringArgumentType.greedyString())
                                .executes(ctx -> ok(behavior.invasionStop(sender(ctx),
                                        StringArgumentType.getString(ctx, "guild"))))))
                .then(Commands.literal("status")
                        .then(Commands.argument("guild", StringArgumentType.greedyString())
                                .executes(ctx -> ok(behavior.invasionStatus(sender(ctx),
                                        StringArgumentType.getString(ctx, "guild"))))));
    }

    /**
     * Performs the sender operation.
     * @param ctx the ctx
     * @return the result
     */
    private static int buildingMoved(CommandContext<CommandSourceStack> ctx) {
        sender(ctx).sendMessage(Component.text(
                "Guilds own buildings in a region. Use /guilds building <create|cancel|list|info|remove>.",
                NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private static CommandSender sender(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender();
    }

    /**
     * Performs the ok operation.
     * @param ignored the ignored
     * @return the result
     */
    private static int ok(boolean ignored) {
        return Command.SINGLE_SUCCESS;
    }
}
