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
import org.aincraft.towny.models.Permission;
import org.bukkit.ChatColor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Argument type for permission roles with validation and suggestions
 */
public class RoleArgumentType implements CustomArgumentType<String, String> {

    private static final SimpleCommandExceptionType INVALID_ROLE =
        new SimpleCommandExceptionType(() -> ChatColor.RED + "Invalid role type");

    // Available permission roles
    private static final List<String> ROLE_TYPES = Arrays.asList(
        "resident", "ally", "outsider", "nation", "mayor", "assistant"
    );

    private RoleArgumentType() {}

    public static RoleArgumentType role() {
        return new RoleArgumentType();
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String role = reader.readUnquotedString().toLowerCase();

        // Validate that the role exists
        if (!isValidRole(role)) {
            throw INVALID_ROLE.createWithContext(reader);
        }

        return role;
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    private boolean isValidRole(String role) {
        return ROLE_TYPES.contains(role.toLowerCase());
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();

        for (String role : ROLE_TYPES) {
            if (role.startsWith(remaining)) {
                builder.suggest(role);
            }
        }

        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("resident", "ally", "outsider", "mayor");
    }

    /**
     * Get the role name from the command context
     */
    public static String getRole(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, String.class);
    }

    /**
     * Get the role enum value from the command context
     */
    public static String getRoleEnum(CommandContext<CommandSourceStack> context, String name) {
        return getRole(context, name);
    }

    /**
     * Get all available role types
     */
    public static List<String> getAllRoleTypes() {
        return List.copyOf(ROLE_TYPES);
    }

    /**
     * Get role display name with proper capitalization
     */
    public static String getDisplayName(String role) {
        return role.substring(0, 1).toUpperCase() + role.substring(1);
    }

    /**
     * Check if a role can be used in plot permissions
     */
    public static boolean isPlotRole(String role) {
        switch (role.toLowerCase()) {
            case "resident":
            case "ally":
            case "outsider":
            case "nation":
                return true;
            default:
                return false;
        }
    }

    /**
     * Check if a role is a town management role
     */
    public static boolean isTownManagementRole(String role) {
        switch (role.toLowerCase()) {
            case "mayor":
            case "assistant":
                return true;
            default:
                return false;
        }
    }
}