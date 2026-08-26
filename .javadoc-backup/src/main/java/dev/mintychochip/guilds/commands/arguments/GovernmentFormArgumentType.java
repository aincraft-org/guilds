package dev.mintychochip.guilds.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.mintychochip.territory.model.GovernmentForm;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.bukkit.ChatColor;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Argument type for governance forms (MONARCHY / OLIGARCHY / DEMOCRACY / ANARCHY)
 * with validation and suggestions.
 */
public class GovernmentFormArgumentType implements CustomArgumentType<String, String> {

    /** The invalid form constant. */
    private static final SimpleCommandExceptionType INVALID_FORM =
            new SimpleCommandExceptionType(() -> ChatColor.RED + "Invalid governance form (MONARCHY, OLIGARCHY, DEMOCRACY, ANARCHY)");

    /** The forms constant. */
    private static final List<String> FORMS = List.of(
            "MONARCHY", "OLIGARCHY", "DEMOCRACY", "ANARCHY"
    );

    /** Creates a new government form argument type instance. */
    private GovernmentFormArgumentType() {
    }

    /**
     * Performs the form operation.
     * @return the result
     */
    public static GovernmentFormArgumentType form() {
        return new GovernmentFormArgumentType();
    }

    /**
     * Performs the parse operation.
     * @param reader the reader
     * @return the result
     * @throws CommandSyntaxException if an error occurs
     */
    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String raw = StringArgumentType.word().parse(reader);
        String key = raw.toUpperCase(Locale.ROOT);
        if (!FORMS.contains(key)) {
            throw INVALID_FORM.createWithContext(reader);
        }
        return key;
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
        String remaining = builder.getRemaining().toUpperCase(Locale.ROOT);
        for (String form : FORMS) {
            if (form.startsWith(remaining)) {
                builder.suggest(form);
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
        return List.of("MONARCHY", "OLIGARCHY", "DEMOCRACY");
    }

    /**
     * Get the parsed form from the command context.
     */
    public static GovernmentForm getForm(CommandContext<CommandSourceStack> context, String name) {
        return GovernmentForm.fromString(context.getArgument(name, String.class));
    }
}
