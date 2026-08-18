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
import dev.mintychochip.guilds.plot.PlotTypeDefinition;
import dev.mintychochip.guilds.plot.PlotTypeRegistry;
import org.bukkit.ChatColor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Argument type for plot types with validation and suggestions
 */
public class PlotTypeArgumentType implements CustomArgumentType<String, String> {

    /** The invalid plot type constant. */
    private static final SimpleCommandExceptionType INVALID_PLOT_TYPE =
        new SimpleCommandExceptionType(() -> ChatColor.RED + "Invalid plot type");

    /** The plot type registry. */
    private final PlotTypeRegistry plotTypeRegistry;

    /**
     * Creates a new plot type argument type instance.
     * @param plotTypeRegistry the plot type registry
     */
    private PlotTypeArgumentType(PlotTypeRegistry plotTypeRegistry) {
        this.plotTypeRegistry = plotTypeRegistry;
    }

    /**
     * Performs the plot type operation.
     * @param plotTypeRegistry the plot type registry
     * @return the result
     */
    public static PlotTypeArgumentType plotType(PlotTypeRegistry plotTypeRegistry) {
        return new PlotTypeArgumentType(plotTypeRegistry);
    }

    /**
     * Performs the parse operation.
     * @param reader the reader
     * @return the result
     * @throws CommandSyntaxException if an error occurs
     */
    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String plotType = reader.readUnquotedString().toLowerCase();

        // Validate that the plot type exists in the registry
        if (!plotTypeRegistry.isPlotTypeRegistered(plotType)) {
            throw INVALID_PLOT_TYPE.createWithContext(reader);
        }

        return plotType;
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
        // Get all registered plot types from the registry
        try {
            Collection<PlotTypeDefinition> plotTypes = plotTypeRegistry.getAllPlotTypes();
            for (PlotTypeDefinition plotType : plotTypes) {
                if (plotType.isEnabled() && plotType.getTypeName().toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(plotType.getTypeName());
                }
            }
        } catch (Exception e) {
            // Fallback silently if registry not available
        }

        return builder.buildFuture();
    }

    /**
     * Returns the examples.
     * @return the result
     */
    @Override
    public Collection<String> getExamples() {
        try {
            return plotTypeRegistry.getAllPlotTypes().stream()
                .filter(PlotTypeDefinition::isEnabled)
                .map(PlotTypeDefinition::getTypeName)
                .limit(4)
                .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of("resident", "farm", "bank", "storage");
        }
    }

    /**
     * Get the plot type from the command context
     */
    public static String getPlotType(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, String.class);
    }
}