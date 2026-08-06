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
import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.models.TownSpecialization;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.SpecializationService;
import org.aincraft.guilds.services.TownService;
import org.bukkit.entity.Player;

import java.util.Optional;

public class SpecializationBrigadierCommand {

    private final JavaPlugin plugin;
    private final SpecializationService specializationService;
    private final TownService townService;
    private final ResidentService residentService;


    public SpecializationBrigadierCommand(JavaPlugin plugin, SpecializationService specializationService,
                                          TownService townService, ResidentService residentService) {
        this.plugin = plugin;
        this.specializationService = specializationService;
        this.townService = townService;
        this.residentService = residentService;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("specialize")
                .requires(source -> {
                    if (!(source.getSender() instanceof Player player)) {
                        return false;
                    }
                    return player.hasPermission("guilds.command.town.specialize");
                })
                .executes(this::handleShow)
                .then(Commands.literal("reset")
                        .executes(this::handleReset))
                .then(Commands.argument("type", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            Player player = (Player) ctx.getSource().getSender();
                            Town town = getPlayerTown(player);
                            if (town != null) {
                                specializationService.getAvailableSpecializations(town.getId())
                                        .forEach(spec -> builder.suggest(spec.name().toLowerCase()));
                            }
                            return builder.buildFuture();
                        })
                        .executes(this::handleSet))
                .build();
    }

    private int handleShow(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        Player player = (Player) sender;

        Town town = getPlayerTown(player);
        if (town == null) {
            player.sendMessage(Component.text("You are not in a town!", NamedTextColor.RED));
            return 0;
        }

        Optional<TownSpecialization> currentSpec = specializationService.getSpecialization(town.getId());
        if (currentSpec.isPresent()) {
            TownSpecialization spec = currentSpec.get();
            player.sendMessage(Component.text("Current specialization: ", NamedTextColor.GREEN)
                    .append(Component.text(spec.getDisplayName(), NamedTextColor.GOLD)));
            player.sendMessage(Component.text(spec.getDescription(), NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("Your town has no specialization.", NamedTextColor.YELLOW));
        }

        var availableSpecs = specializationService.getAvailableSpecializations(town.getId());
        if (availableSpecs.isEmpty()) {
            player.sendMessage(Component.text("No specializations available at your town's level.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage(Component.text("Available specializations:", NamedTextColor.GREEN));
        for (TownSpecialization spec : availableSpecs) {
            if (!currentSpec.isPresent() || spec != currentSpec.get()) {
                player.sendMessage(Component.text("  - " + spec.getDisplayName() + ": ", NamedTextColor.YELLOW)
                        .append(Component.text(spec.getDescription(), NamedTextColor.GRAY)));
            }
        }

        if (specializationService.canSpecialize(town.getId()) && currentSpec.isEmpty()) {
            player.sendMessage(Component.text("Use /town specialize <type> to set a specialization", NamedTextColor.GOLD));
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleReset(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        Player player = (Player) sender;

        Town town = getPlayerTown(player);
        if (town == null) {
            player.sendMessage(Component.text("You are not in a town!", NamedTextColor.RED));
            return 0;
        }

        Optional<Town> townOpt = townService.getTown(town.getId());
        if (townOpt.isEmpty()) {
            player.sendMessage(Component.text("Failed to access town data", NamedTextColor.RED));
            return 0;
        }

        if (!townOpt.get().getMayorUuid().equals(player.getUniqueId()) && !player.hasPermission("guilds.admin.specialize")) {
            player.sendMessage(Component.text("Only the town mayor can reset specializations!", NamedTextColor.RED));
            return 0;
        }

        specializationService.removeSpecialization(town.getId());
        player.sendMessage(Component.text("Town specialization removed successfully", NamedTextColor.GREEN));

        return Command.SINGLE_SUCCESS;
    }

    private int handleSet(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        Player player = (Player) sender;

        String type = StringArgumentType.getString(ctx, "type").toUpperCase();
        TownSpecialization specialization = specializationService.fromString(type);

        if (specialization == null) {
            player.sendMessage(Component.text("Invalid specialization type!", NamedTextColor.RED));
            return 0;
        }

        Town town = getPlayerTown(player);
        if (town == null) {
            player.sendMessage(Component.text("You are not in a town!", NamedTextColor.RED));
            return 0;
        }

        Optional<Town> townOpt = townService.getTown(town.getId());
        if (townOpt.isEmpty()) {
            player.sendMessage(Component.text("Failed to access town data", NamedTextColor.RED));
            return 0;
        }

        if (!townOpt.get().getMayorUuid().equals(player.getUniqueId()) && !player.hasPermission("guilds.admin.specialize")) {
            player.sendMessage(Component.text("Only the town mayor can set specializations!", NamedTextColor.RED));
            return 0;
        }

        if (!specializationService.canSpecialize(town.getId())) {
            player.sendMessage(Component.text("Your town cannot specialize yet! Requires level 10.", NamedTextColor.RED));
            return 0;
        }

        var availableSpecs = specializationService.getAvailableSpecializations(town.getId());
        if (!availableSpecs.contains(specialization)) {
            player.sendMessage(Component.text("This specialization is not available for your town!", NamedTextColor.RED));
            return 0;
        }

        specializationService.setSpecialization(town.getId(), specialization);
        player.sendMessage(Component.text("Town specialization set to ", NamedTextColor.GREEN)
                .append(Component.text(specialization.getDisplayName(), NamedTextColor.GOLD)));

        return Command.SINGLE_SUCCESS;
    }

    private Town getPlayerTown(Player player) {
        String townName = residentService.getResident(player.getUniqueId())
                .filter(resident -> resident.hasTown())
                .map(org.aincraft.guilds.models.Resident::getTown)
                .orElse(null);

        if (townName == null) {
            return null;
        }

        return townService.getTown(townName).orElse(null);
    }
}