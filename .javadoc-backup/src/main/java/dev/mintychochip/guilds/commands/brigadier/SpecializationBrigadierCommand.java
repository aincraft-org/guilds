package dev.mintychochip.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.GuildSpecialization;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.SpecializationService;
import dev.mintychochip.guilds.services.GuildService;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Command handler for specialization brigadier. */
public class SpecializationBrigadierCommand {

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The specialization service. */
    private final SpecializationService specializationService;
    /** The guild service. */
    private final GuildService guildService;
    /** The resident service. */
    private final ResidentService residentService;


    /**
     * Creates a new specialization brigadier command instance.
     * @param plugin the plugin
     * @param specializationService the specialization service
     * @param guildService the guild service
     * @param residentService the resident service
     */
    public SpecializationBrigadierCommand(JavaPlugin plugin, SpecializationService specializationService,
                                          GuildService guildService, ResidentService residentService) {
        this.plugin = plugin;
        this.specializationService = specializationService;
        this.guildService = guildService;
        this.residentService = residentService;
    }

    /**
     * Builds the command.
     * @return the result
     */
    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("specialize")
                .requires(source -> {
                    if (!(source.getSender() instanceof Player player)) {
                        return false;
                    }
                    return player.hasPermission("guilds.command.guild.specialize");
                })
                .executes(this::handleShow)
                .then(Commands.literal("reset")
                        .executes(this::handleReset))
                .then(Commands.argument("type", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                return builder.buildFuture();
                            }
                            Guild guild = getPlayerGuild(player);
                            if (guild != null) {
                                specializationService.getAvailableSpecializations(guild.getId())
                                        .forEach(spec -> builder.suggest(spec.name().toLowerCase()));
                            }
                            return builder.buildFuture();
                        })
                        .executes(this::handleSet))
                .build();
    }

    /**
     * Handles the show.
     * @param ctx the ctx
     * @return the result
     */
    private int handleShow(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            return 0;
        }

        Guild guild = getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage(Component.text("You are not in a guild!", NamedTextColor.RED));
            return 0;
        }

        Optional<GuildSpecialization> currentSpec = specializationService.getSpecialization(guild.getId());
        if (currentSpec.isPresent()) {
            GuildSpecialization spec = currentSpec.get();
            player.sendMessage(Component.text("Current specialization: ", NamedTextColor.GREEN)
                    .append(Component.text(spec.getDisplayName(), NamedTextColor.GOLD)));
            player.sendMessage(Component.text(spec.getDescription(), NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("Your guild has no specialization.", NamedTextColor.YELLOW));
        }

        var availableSpecs = specializationService.getAvailableSpecializations(guild.getId());
        if (availableSpecs.isEmpty()) {
            player.sendMessage(Component.text("No specializations available at your guild's level.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage(Component.text("Available specializations:", NamedTextColor.GREEN));
        for (GuildSpecialization spec : availableSpecs) {
            if (!currentSpec.isPresent() || spec != currentSpec.get()) {
                player.sendMessage(Component.text("  - " + spec.getDisplayName() + ": ", NamedTextColor.YELLOW)
                        .append(Component.text(spec.getDescription(), NamedTextColor.GRAY)));
            }
        }

        if (specializationService.canSpecialize(guild.getId()) && currentSpec.isEmpty()) {
            player.sendMessage(Component.text("Use /guild specialize <type> to set a specialization", NamedTextColor.GOLD));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the reset.
     * @param ctx the ctx
     * @return the result
     */
    private int handleReset(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            return 0;
        }

        Guild guild = getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage(Component.text("You are not in a guild!", NamedTextColor.RED));
            return 0;
        }

        Optional<Guild> guildOpt = guildService.getGuild(guild.getId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(Component.text("Failed to access guild data", NamedTextColor.RED));
            return 0;
        }

        if (!guildOpt.get().getMayorUuid().equals(player.getUniqueId()) && !player.hasPermission("guilds.admin.specialize")) {
            player.sendMessage(Component.text("Only the guild mayor can reset specializations!", NamedTextColor.RED));
            return 0;
        }

        specializationService.removeSpecialization(guild.getId());
        player.sendMessage(Component.text("Guild specialization removed successfully", NamedTextColor.GREEN));

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Handles the set.
     * @param ctx the ctx
     * @return the result
     */
    private int handleSet(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            return 0;
        }

        String type = StringArgumentType.getString(ctx, "type").toUpperCase();
        GuildSpecialization specialization = specializationService.fromString(type);

        if (specialization == null) {
            player.sendMessage(Component.text("Invalid specialization type!", NamedTextColor.RED));
            return 0;
        }

        Guild guild = getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage(Component.text("You are not in a guild!", NamedTextColor.RED));
            return 0;
        }

        Optional<Guild> guildOpt = guildService.getGuild(guild.getId());
        if (guildOpt.isEmpty()) {
            player.sendMessage(Component.text("Failed to access guild data", NamedTextColor.RED));
            return 0;
        }

        if (!guildOpt.get().getMayorUuid().equals(player.getUniqueId()) && !player.hasPermission("guilds.admin.specialize")) {
            player.sendMessage(Component.text("Only the guild mayor can set specializations!", NamedTextColor.RED));
            return 0;
        }

        if (!specializationService.canSpecialize(guild.getId())) {
            player.sendMessage(Component.text("Your guild cannot specialize yet! Requires level 10.", NamedTextColor.RED));
            return 0;
        }

        var availableSpecs = specializationService.getAvailableSpecializations(guild.getId());
        if (!availableSpecs.contains(specialization)) {
            player.sendMessage(Component.text("This specialization is not available for your guild!", NamedTextColor.RED));
            return 0;
        }

        specializationService.setSpecialization(guild.getId(), specialization);
        player.sendMessage(Component.text("Guild specialization set to ", NamedTextColor.GREEN)
                .append(Component.text(specialization.getDisplayName(), NamedTextColor.GOLD)));

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Returns the player guild.
     * @param player the player
     * @return the result
     */
    private Guild getPlayerGuild(Player player) {
        String guildName = residentService.getResident(player.getUniqueId())
                .filter(resident -> resident.hasGuild())
                .map(dev.mintychochip.guilds.models.Resident::getGuild)
                .orElse(null);

        if (guildName == null) {
            return null;
        }

        return guildService.getGuild(guildName).orElse(null);
    }
}