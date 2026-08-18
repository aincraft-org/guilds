package dev.mintychochip.guilds.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import dev.mintychochip.guilds.models.Resident;
import dev.mintychochip.guilds.services.ResidentService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Argument type for resident names with validation and suggestions
 */
public class ResidentArgumentType implements CustomArgumentType<String, String> {

    /** The resident not found constant. */
    private static final SimpleCommandExceptionType RESIDENT_NOT_FOUND =
        new SimpleCommandExceptionType(() -> ChatColor.RED + "Resident not found");

    /** The resident service. */
    private final ResidentService residentService;

    /**
     * Creates a new resident argument type instance.
     * @param residentService the resident service
     */
    private ResidentArgumentType(ResidentService residentService) {
        this.residentService = residentService;
    }

    /**
     * Performs the resident operation.
     * @param residentService the resident service
     * @return the result
     */
    public static ResidentArgumentType resident(ResidentService residentService) {
        return new ResidentArgumentType(residentService);
    }

    /**
     * Performs the parse operation.
     * @param reader the reader
     * @return the result
     * @throws CommandSyntaxException if an error occurs
     */
    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String residentName = reader.readUnquotedString();

        // Validate that the resident exists (either online or in database)
        if (!residentExists(residentName)) {
            throw RESIDENT_NOT_FOUND.createWithContext(reader);
        }

        return residentName;
    }

    /**
     * Returns the native type.
     * @return the result
     */
    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    /**
     * Performs the resident exists operation.
     * @param name the name
     * @return the result
     */
    private boolean residentExists(String name) {
        // Check if player is online
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) {
            return true;
        }

        // Check in resident service database
        try {
            UUID uuid = Bukkit.getOfflinePlayer(name).getUniqueId();
            return residentService.getResident(uuid).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Performs the list suggestions operation.
     * @param context the context
     * @param builder the builder
     * @return the result
     */
    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();

        // Online players first, then offline residents from the database
        // (bounded prefix search; deduplicated, prefix-filtered).
        Set<String> names = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(remaining)) {
                names.add(player.getName());
            }
        }
        for (Resident resident : residentService.searchResidents(remaining, 50)) {
            names.add(resident.getName());
        }
        for (String name : names) {
            builder.suggest(name);
        }

        return builder.buildFuture();
    }

    /**
     * Returns the examples.
     * @return the result
     */
    @Override
    public Collection<String> getExamples() {
        return List.of("player1", "admin", "mayor");
    }

    /**
     * Get the resident name from the command context
     */
    public static String getResidentName(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, String.class);
    }

    /**
     * Get the resident UUID from the command context
     */
    public static UUID getResidentUuid(CommandContext<CommandSourceStack> context, String name) {
        String residentName = context.getArgument(name, String.class);
        Player player = Bukkit.getPlayerExact(residentName);
        return player != null ? player.getUniqueId() : Bukkit.getOfflinePlayer(residentName).getUniqueId();
    }
}