package org.aincraft.guilds.commands.arguments;

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
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.ResidentService;
import org.bukkit.ChatColor;

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

    private static final SimpleCommandExceptionType RESIDENT_NOT_FOUND =
        new SimpleCommandExceptionType(() -> ChatColor.RED + "Resident not found");

    private final ResidentService residentService;

    private ResidentArgumentType(ResidentService residentService) {
        this.residentService = residentService;
    }

    public static ResidentArgumentType resident(ResidentService residentService) {
        return new ResidentArgumentType(residentService);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String residentName = reader.readUnquotedString();

        // Validate against the persistent resident database; Bukkit's live
        // player list is not an identity source for offline residents.
        if (!residentExists(residentName)) {
            throw RESIDENT_NOT_FOUND.createWithContext(reader);
        }

        return residentName;
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    private boolean residentExists(String name) {
        try {
            return residentService.getResident(name).isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();

        // Resident names come from persistent Guilds data so offline residents
        // are suggested exactly like players currently online.
        Set<String> names = new LinkedHashSet<>();
        for (Resident resident : residentService.searchResidents(remaining, 50)) {
            names.add(resident.getName());
        }
        for (String name : names) {
            builder.suggest(name);
        }

        return builder.buildFuture();
    }

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
     * Resolve a resident argument through the persistent resident service.
     * This deliberately does not ask Bukkit for a live or cached player.
     */
    public UUID resolveResidentUuid(CommandContext<CommandSourceStack> context, String name) {
        String residentName = context.getArgument(name, String.class);
        return residentService.getResident(residentName).map(Resident::getUuid).orElse(null);
    }
}