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
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.GuildService;
import org.bukkit.ChatColor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Argument type for guild names with validation and suggestions
 */
public class GuildArgumentType implements CustomArgumentType<String, String> {

    /** The guild not found constant. */
    private static final SimpleCommandExceptionType GUILD_NOT_FOUND =
        new SimpleCommandExceptionType(() -> ChatColor.RED + "Guild not found");

    /** The guild service. */
    private final GuildService guildService;

    /**
     * Creates a new guild argument type instance.
     * @param guildService the guild service
     */
    private GuildArgumentType(GuildService guildService) {
        this.guildService = guildService;
    }

    /**
     * Performs the guild operation.
     * @param guildService the guild service
     * @return the result
     */
    public static GuildArgumentType guild(GuildService guildService) {
        return new GuildArgumentType(guildService);
    }

    /**
     * Performs the parse operation.
     * @param reader the reader
     * @return the result
     * @throws CommandSyntaxException if an error occurs
     */
    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String guildName = reader.readUnquotedString();

        // Validate that the guild exists
        if (!guildService.guildExists(guildName)) {
            throw GUILD_NOT_FOUND.createWithContext(reader);
        }

        return guildName;
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
     * Performs the list suggestions operation.
     * @param context the context
     * @param builder the builder
     * @return the result
     */
    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        List<String> guildNames = guildService.getAllGuilds().stream()
            .map(Guild::getName)
            .toList();

        for (String guildName : guildNames) {
            if (guildName.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(guildName);
            }
        }

        return builder.buildFuture();
    }

    /**
     * Returns the examples.
     * @return the result
     */
    @Override
    public Collection<String> getExamples() {
        return List.of("myguild", "spawnville", "capital");
    }

    /**
     * Get the guild name from the command context
     */
    public static String getGuildName(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, String.class);
    }
}