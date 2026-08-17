package dev.mintychochip.territory.building;

import dev.mintychochip.territory.model.FacilityType;
import dev.mintychochip.territory.model.SettlementFacility;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.registry.FacilityRegistry;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BuildingCommand {
    private static final Logger LOGGER = Logger.getLogger(BuildingCommand.class.getName());

    private final BuildingPlacementSessions sessions;
    private final FacilityRegistry facilities;
    private final TerritoryRegistry territories;
    private final FacilityAnchorValidator anchors;
    private final BuildingAuthorization authorization;
    private final FacilityMutationService mutations;
    private final BuildingConfig config;
    private final WaystoneSelections selections;
    private final WaystoneTravelService travel;

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

    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("azoth.territory.building.manage")
                && !sender.hasPermission("azoth.territory.admin") && !sender.isOp()) {
            message(sender, "You cannot manage territory buildings.", NamedTextColor.RED);
            return true;
        }
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, label, args);
            case "cancel" -> cancel(sender);
            case "list" -> list(sender, args);
            case "info" -> info(sender, label, args);
            case "remove" -> remove(sender, label, args);
            case "travel" -> travel(sender, label, args);
            default -> { usage(sender, label); yield true; }
        };
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("create", "cancel", "list", "info", "remove", "travel").stream()
                    .filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && "create".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("waystone", "trading_post", "storage").stream()
                    .filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private boolean create(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            message(sender, "Players only for building placement.", NamedTextColor.RED);
            return true;
        }
        if (args.length < 3) {
            message(sender, "Usage: /guilds building create <waystone|trading_post|storage> <id> [name]",
                    NamedTextColor.RED);
            return true;
        }
        FacilityType type = parseType(args[1]);
        if (type == null || !config.supports(type)) {
            message(sender, "Unsupported building type: " + args[1], NamedTextColor.RED);
            return true;
        }
        String name = args.length > 3
                ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : args[2];
        try {
            sessions.begin(player.getUniqueId(), type, args[2], name, System.currentTimeMillis());
            message(sender, "Right-click the anchor block for " + args[2] + ".", NamedTextColor.GREEN);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Building placement failed for " + player.getName(), e);
            message(sender, "Building placement failed.", NamedTextColor.RED);
        }
        return true;
    }

    private boolean cancel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "Players only.", NamedTextColor.RED);
        } else {
            message(sender, sessions.cancel(player.getUniqueId())
                    ? "Building placement cancelled." : "No pending building placement.", NamedTextColor.YELLOW);
        }
        return true;
    }

    private boolean travel(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length < 2) {
            message(sender, "Usage: /" + label + " building travel <destinationId>",
                    NamedTextColor.RED);
            return true;
        }
        SettlementFacility origin = selections.origin(player.getUniqueId(), System.currentTimeMillis())
                .flatMap(facilities::get).orElse(null);
        WaystoneTravelService.StartResult result = travel.start(
                player, origin, args[1], System.currentTimeMillis());
        if (result == WaystoneTravelService.StartResult.STARTED) {
            selections.clear(player.getUniqueId());
        }
        message(sender, result == WaystoneTravelService.StartResult.STARTED
                        ? "Waystone travel warming up." : "Waystone travel failed: " + result,
                result == WaystoneTravelService.StartResult.STARTED
                        ? NamedTextColor.GREEN : NamedTextColor.RED);
        return true;
    }

    private boolean list(CommandSender sender, String[] args) {
        String territoryId = args.length > 1 ? args[1] : territoryAt(sender).orElse(null);
        if (territoryId == null) {
            message(sender, "Usage: /guilds building list <territoryId>", NamedTextColor.RED);
            return true;
        }
        Territory territory = territories.get(territoryId).orElse(null);
        if (territory == null) {
            message(sender, "Unknown territory: " + territoryId, NamedTextColor.RED);
            return true;
        }
        if (!(sender instanceof Player player) || !authorization.canManage(player, territory)) {
            message(sender, "You cannot list buildings in that territory.", NamedTextColor.RED);
            return true;
        }
        List<SettlementFacility> matches = facilities.list().stream()
                .filter(facility -> facility.territoryId().equals(territoryId)).toList();
        message(sender, "Buildings in " + territoryId + " (" + matches.size() + "):", NamedTextColor.GOLD);
        matches.forEach(facility -> message(sender, facility.id() + " — " + facility.type()
                + " — " + anchors.validate(facility).status(), NamedTextColor.YELLOW));
        return true;
    }

    private boolean info(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            message(sender, "Usage: /" + label + " building info <id>", NamedTextColor.RED);
            return true;
        }
        SettlementFacility facility = facilities.get(args[1]).orElse(null);
        if (facility == null) {
            message(sender, "Unknown building: " + args[1], NamedTextColor.RED);
            return true;
        }
        Territory territory = territories.get(facility.territoryId()).orElse(null);
        if (territory == null) {
            message(sender, "Unknown territory for building.", NamedTextColor.RED);
            return true;
        }
        if (!(sender instanceof Player player) || !authorization.canManage(player, territory)) {
            message(sender, "You cannot view that building.", NamedTextColor.RED);
            return true;
        }
        message(sender, facility.name() + " (" + facility.id() + ") " + facility.type()
                + " in " + facility.territoryId() + " at " + facility.worldId() + " "
                + facility.x() + "," + facility.y() + "," + facility.z() + " — "
                + anchors.validate(facility).status(), NamedTextColor.GOLD);
        return true;
    }

    private boolean remove(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            message(sender, "Usage: /" + label + " building remove <id>", NamedTextColor.RED);
            return true;
        }
        SettlementFacility facility = facilities.get(args[1]).orElse(null);
        Territory territory = facility == null ? null : territories.get(facility.territoryId()).orElse(null);
        if (facility == null || territory == null) {
            message(sender, "Unknown building: " + args[1], NamedTextColor.RED);
            return true;
        }
        if (!(sender instanceof Player player) || !authorization.canManage(player, territory)) {
            message(sender, "You cannot remove that building.", NamedTextColor.RED);
            return true;
        }
        try {
            mutations.remove(facility.id());
            message(sender, "Removed building " + facility.id() + ".", NamedTextColor.GREEN);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Building removal failed for " + sender.getName(), e);
            message(sender, "Building removal failed.", NamedTextColor.RED);
        }
        return true;
    }

    private Optional<String> territoryAt(CommandSender sender) {
        if (!(sender instanceof Player player)) return Optional.empty();
        var location = player.getLocation();
        if (location.getWorld() == null) return Optional.empty();
        return territories.resolve(location.getWorld().getName(), location.getBlockX(), location.getBlockZ())
                .territoryId();
    }

    private static FacilityType parseType(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "waystone" -> FacilityType.WAYSTONE;
            case "trading_post", "trading-post" -> FacilityType.TRADING_POST;
            case "storage" -> FacilityType.STORAGE;
            default -> null;
        };
    }

    private static void usage(CommandSender sender, String label) {
        message(sender, "Usage: /" + label + " building <create|cancel|list|info|remove>",
                NamedTextColor.RED);
    }

    private static void message(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(Component.text(text, color));
    }
}
