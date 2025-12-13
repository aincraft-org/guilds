package org.aincraft.towny.commands.arguments;

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
import org.aincraft.towny.models.PlotTypes;
import org.aincraft.towny.services.PlotTypeService;
import org.bukkit.ChatColor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Argument type for plot types with validation and suggestions
 */
public class PlotTypeArgumentType implements CustomArgumentType<String, String> {

    private static final SimpleCommandExceptionType INVALID_PLOT_TYPE =
        new SimpleCommandExceptionType(() -> ChatColor.RED + "Invalid plot type");

    // Default plot types
    private static final List<String> DEFAULT_PLOT_TYPES = Arrays.asList(
        "residential", "shop", "arena", "embassy", "farm", "wilds", "bank", "inn", "jail"
    );

    private final PlotTypeService plotTypeService;

    private PlotTypeArgumentType(PlotTypeService plotTypeService) {
        this.plotTypeService = plotTypeService;
    }

    public static PlotTypeArgumentType plotType(PlotTypeService plotTypeService) {
        return new PlotTypeArgumentType(plotTypeService);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String plotType = reader.readUnquotedString().toLowerCase();

        // Validate that the plot type exists
        if (!isValidPlotType(plotType)) {
            throw INVALID_PLOT_TYPE.createWithContext(reader);
        }

        return plotType;
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    private boolean isValidPlotType(String plotType) {
        // Check if it's a default plot type
        if (DEFAULT_PLOT_TYPES.contains(plotType)) {
            return true;
        }

        // Check if it's a custom plot type in the database
        try {
            return plotTypeService.isPlotTypeRegistered(plotType);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        // Suggest default plot types first
        for (String plotType : DEFAULT_PLOT_TYPES) {
            if (plotType.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(plotType);
            }
        }

        // TODO: Add custom plot type suggestions from database
        // This would require database access which might be expensive for tab completion

        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("residential", "shop", "arena", "farm");
    }

    /**
     * Get the plot type from the command context
     */
    public static String getPlotType(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, String.class);
    }

    /**
     * Check if the plot type is a default type
     */
    public static boolean isDefaultPlotType(String plotType) {
        return DEFAULT_PLOT_TYPES.contains(plotType.toLowerCase());
    }

    /**
     * Get all default plot types
     */
    public static List<String> getDefaultPlotTypes() {
        return List.copyOf(DEFAULT_PLOT_TYPES);
    }
}