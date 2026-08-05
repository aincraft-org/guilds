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
import org.aincraft.towny.plot.PlotTypeDefinition;
import org.aincraft.towny.services.PlotTypeService;
import org.bukkit.ChatColor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Argument type for plot types with validation and suggestions
 */
public class PlotTypeArgumentType implements CustomArgumentType<String, String> {

    private static final SimpleCommandExceptionType INVALID_PLOT_TYPE =
        new SimpleCommandExceptionType(() -> ChatColor.RED + "Invalid plot type");

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

        // Validate that the plot type exists in the registry
        if (!plotTypeService.isPlotTypeRegistered(plotType)) {
            throw INVALID_PLOT_TYPE.createWithContext(reader);
        }

        return plotType;
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        // Get all registered plot types from the registry
        try {
            Collection<PlotTypeDefinition> plotTypes = plotTypeService.getAllPlotTypes();
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

    @Override
    public Collection<String> getExamples() {
        try {
            return plotTypeService.getAllPlotTypes().stream()
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