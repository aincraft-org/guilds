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
import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.services.TownService;
import org.bukkit.ChatColor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Argument type for town names with validation and suggestions
 */
public class TownArgumentType implements CustomArgumentType<String, String> {

    private static final SimpleCommandExceptionType TOWN_NOT_FOUND =
        new SimpleCommandExceptionType(() -> ChatColor.RED + "Town not found");

    private final TownService townService;

    private TownArgumentType(TownService townService) {
        this.townService = townService;
    }

    public static TownArgumentType town(TownService townService) {
        return new TownArgumentType(townService);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String townName = reader.readUnquotedString();

        // Validate that the town exists
        if (!townService.townExists(townName)) {
            throw TOWN_NOT_FOUND.createWithContext(reader);
        }

        return townName;
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        List<String> townNames = townService.getAllTowns().stream()
            .map(Town::getName)
            .toList();

        for (String townName : townNames) {
            if (townName.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(townName);
            }
        }

        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("mytown", "spawnville", "capital");
    }

    /**
     * Get the town name from the command context
     */
    public static String getTownName(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, String.class);
    }
}