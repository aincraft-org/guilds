package dev.mintychochip.guilds.commands.brigadier;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.mintychochip.territory.building.BuildingAuthorization;
import dev.mintychochip.territory.building.BuildingConfig;
import dev.mintychochip.territory.building.BuildingPlacementSessions;
import dev.mintychochip.territory.building.FacilityAnchorValidator;
import dev.mintychochip.territory.building.FacilityMutationService;
import dev.mintychochip.territory.building.WaystoneSelections;
import dev.mintychochip.territory.building.WaystoneTravelService;
import dev.mintychochip.territory.model.FacilityType;
import dev.mintychochip.territory.model.SettlementFacility;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.registry.FacilityRegistry;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Brigadier command for {@code /territory building …}.
 */
public final class BuildingCommand {
    /** Logger for this class. */
    private static final Logger LOGGER = Logger.getLogger(BuildingCommand.class.getName());

    /** The sessions. */
    private final BuildingPlacementSessions sessions;
    /** The facilities. */
    private final FacilityRegistry facilities;
    /** The territories. */
    private final TerritoryRegistry territories;
    /** The anchors. */
    private final FacilityAnchorValidator anchors;
    /** The authorization. */
    private final BuildingAuthorization authorization;
    /** The mutations. */
    private final FacilityMutationService mutations;
    /** The config. */
    private final BuildingConfig config;
    /** The selections. */
    private final WaystoneSelections selections;
    /** The travel. */
    private final WaystoneTravelService travel;

    /**
     * Creates a new building command instance.
     * @param sessions the sessions
     * @param facilities the facilities
     * @param territories the territories
     * @param anchors the anchors
     * @param authorization the authorization
     * @param mutations the mutations
     * @param config the config
     * @param selections the selections
     * @param travel the travel
     */
    public BuildingCommand(BuildingPlacementSessions sessions, FacilityRegistry facilities,
                           TerritoryRegistry territories, FacilityAnchorValidator anchors,
                           BuildingAuthorization authorization, FacilityMutationService mutations,
                           BuildingConfig config, WaystoneSelections selections,
                           WaystoneTravelService travel) {
        this.sessions = sessions;
        this.facilities = facilities;
        this.territories = territories;
        this.anchors = anchors;
        this.authorization = authorization;
        this.mutations = mutations;
        this.config = config;
        this.selections = selections;
        this.travel = travel;
    }

    /**
     * Builds the command.
     * @return the result
     */
    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("building")
                .executes(this::usage)
                .then(Commands.literal("create")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggest(builder, "waystone", "trading_post"))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(this::create)
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(this::create)))))
                .then(Commands.literal("cancel").executes(this::cancel))
                .then(Commands.literal("list")
                        .executes(this::list)
                        .then(Commands.argument("territoryId", StringArgumentType.word())
                                .executes(this::list)))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(this::info)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(this::remove)))
                .then(Commands.literal("travel")
                        .then(Commands.argument("destinationId", StringArgumentType.word())
                                .executes(this::travel)))
                .build();
    }

    /**
     * Performs the usage operation.
     * @param ctx the ctx
     * @return the result
     */
    private int usage(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!allowed(sender)) {
            return Command.SINGLE_SUCCESS;
        }
        message(sender, "Usage: /territory building <create|cancel|list|info|remove>",
                NamedTextColor.RED);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Performs the create operation.
     * @param ctx the ctx
     * @return the result
     */
    private int create(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!allowed(sender)) {
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            message(sender, "Players only for building placement.", NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        String typeName = StringArgumentType.getString(ctx, "type");
        String id = StringArgumentType.getString(ctx, "id");
        String name = optionalString(ctx, "name").orElse(id);
        FacilityType type = parseType(typeName);
        if (type == null || !config.supports(type)) {
            message(sender, "Unsupported building type: " + typeName, NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        try {
            sessions.begin(player.getUniqueId(), type, id, name, System.currentTimeMillis());
            message(sender, "Right-click the anchor block for " + id + ".", NamedTextColor.GREEN);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Building placement failed for " + player.getName(), e);
            message(sender, "Building placement failed.", NamedTextColor.RED);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Returns whether cel.
     * @param ctx the ctx
     * @return the result
     */
    private int cancel(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!allowed(sender)) {
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            message(sender, "Players only.", NamedTextColor.RED);
        } else {
            message(sender, sessions.cancel(player.getUniqueId())
                    ? "Building placement cancelled." : "No pending building placement.", NamedTextColor.YELLOW);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Performs the travel operation.
     * @param ctx the ctx
     * @return the result
     */
    private int travel(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!allowed(sender)) {
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            message(sender, "Usage: /territory building travel <destinationId>",
                    NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        String destinationId = StringArgumentType.getString(ctx, "destinationId");
        SettlementFacility origin = selections.origin(player.getUniqueId(), System.currentTimeMillis())
                .flatMap(facilities::get).orElse(null);
        WaystoneTravelService.StartResult result = travel.start(
                player, origin, destinationId, System.currentTimeMillis());
        if (result == WaystoneTravelService.StartResult.STARTED) {
            selections.clear(player.getUniqueId());
        }
        message(sender, result == WaystoneTravelService.StartResult.STARTED
                        ? "Waystone travel warming up." : "Waystone travel failed: " + result,
                result == WaystoneTravelService.StartResult.STARTED
                        ? NamedTextColor.GREEN : NamedTextColor.RED);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Performs the list operation.
     * @param ctx the ctx
     * @return the result
     */
    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!allowed(sender)) {
            return Command.SINGLE_SUCCESS;
        }
        String territoryId = optionalString(ctx, "territoryId").orElseGet(() -> territoryAt(sender).orElse(null));
        if (territoryId == null) {
            message(sender, "Usage: /territory building list <territoryId>", NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        Territory territory = territories.get(territoryId).orElse(null);
        if (territory == null) {
            message(sender, "Unknown territory: " + territoryId, NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player) || !authorization.canManage(player, territory)) {
            message(sender, "You cannot list buildings in that territory.", NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        var matches = facilities.list().stream()
                .filter(facility -> facility.territoryId().equals(territoryId)).toList();
        message(sender, "Buildings in " + territoryId + " (" + matches.size() + "):", NamedTextColor.GOLD);
        matches.forEach(facility -> message(sender, facility.id() + " — " + facility.type()
                + " — " + anchors.validate(facility).status(), NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Performs the info operation.
     * @param ctx the ctx
     * @return the result
     */
    private int info(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!allowed(sender)) {
            return Command.SINGLE_SUCCESS;
        }
        String id = StringArgumentType.getString(ctx, "id");
        SettlementFacility facility = facilities.get(id).orElse(null);
        if (facility == null) {
            message(sender, "Unknown building: " + id, NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        Territory territory = territories.get(facility.territoryId()).orElse(null);
        if (territory == null) {
            message(sender, "Unknown territory for building.", NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player) || !authorization.canManage(player, territory)) {
            message(sender, "You cannot view that building.", NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        message(sender, facility.name() + " (" + facility.id() + ") " + facility.type()
                + " in " + facility.territoryId() + " at " + facility.worldId() + " "
                + facility.x() + "," + facility.y() + "," + facility.z() + " — "
                + anchors.validate(facility).status(), NamedTextColor.GOLD);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Performs the remove operation.
     * @param ctx the ctx
     * @return the result
     */
    private int remove(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!allowed(sender)) {
            return Command.SINGLE_SUCCESS;
        }
        String id = StringArgumentType.getString(ctx, "id");
        SettlementFacility facility = facilities.get(id).orElse(null);
        Territory territory = facility == null ? null : territories.get(facility.territoryId()).orElse(null);
        if (facility == null || territory == null) {
            message(sender, "Unknown building: " + id, NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player) || !authorization.canManage(player, territory)) {
            message(sender, "You cannot remove that building.", NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        try {
            mutations.remove(facility.id());
            message(sender, "Removed building " + facility.id() + ".", NamedTextColor.GREEN);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Building removal failed for " + sender.getName(), e);
            message(sender, "Building removal failed.", NamedTextColor.RED);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Performs the allowed operation.
     * @param sender the sender
     * @return the result
     */
    private boolean allowed(CommandSender sender) {
        if (sender.hasPermission("guilds.territory.building.manage")
                || sender.hasPermission("guilds.territory.admin") || sender.isOp()) {
            return true;
        }
        message(sender, "You cannot manage territory buildings.", NamedTextColor.RED);
        return false;
    }

    /**
     * Performs the territory at operation.
     * @param sender the sender
     * @return the result
     */
    private Optional<String> territoryAt(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return Optional.empty();
        }
        var location = player.getLocation();
        if (location.getWorld() == null) {
            return Optional.empty();
        }
        return territories.resolve(location.getWorld().getName(), location.getBlockX(), location.getBlockZ())
                .territoryId();
    }

    /**
     * Performs the optional string operation.
     * @param ctx the ctx
     * @param name the name
     * @return the result
     */
    private static Optional<String> optionalString(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return Optional.of(StringArgumentType.getString(ctx, name));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Parses the type.
     * @param input the input
     * @return the result
     */
    private static FacilityType parseType(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "waystone" -> FacilityType.WAYSTONE;
            case "trading_post", "trading-post" -> FacilityType.TRADING_POST;
            default -> null;
        };
    }

    /**
     * Performs the suggest operation.
     * @param builder the builder
     * @param  the 
     * @return the result
     */
    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, String... values) {
        String remaining = builder.getRemainingLowerCase();
        for (String value : values) {
            if (value.startsWith(remaining)) {
                builder.suggest(value);
            }
        }
        return builder.buildFuture();
    }

    /**
     * Performs the message operation.
     * @param sender the sender
     * @param text the text
     * @param color the color
     */
    private static void message(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(Component.text(text, color));
    }
}
