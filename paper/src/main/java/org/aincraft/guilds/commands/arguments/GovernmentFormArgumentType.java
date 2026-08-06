package org.aincraft.guilds.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.azoth.territory.model.GovernmentForm;
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

    private static final SimpleCommandExceptionType INVALID_FORM =
            new SimpleCommandExceptionType(() -> ChatColor.RED + "Invalid governance form (MONARCHY, OLIGARCHY, DEMOCRACY, ANARCHY)");

    private static final List<String> FORMS = List.of(
            "MONARCHY", "OLIGARCHY", "DEMOCRACY", "ANARCHY"
    );

    private GovernmentFormArgumentType() {
    }

    public static GovernmentFormArgumentType form() {
        return new GovernmentFormArgumentType();
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String raw = StringArgumentType.word().parse(reader);
        String key = raw.toUpperCase(Locale.ROOT);
        if (!FORMS.contains(key)) {
            throw INVALID_FORM.createWithContext(reader);
        }
        return key;
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

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
