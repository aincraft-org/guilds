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
 * Argument type for permission names with validation and suggestions
 */
public class PermissionArgumentType implements CustomArgumentType<String, String> {

    private static final SimpleCommandExceptionType INVALID_PERMISSION =
        new SimpleCommandExceptionType(() -> ChatColor.RED + "Invalid permission type");

    // Available permission types
    private static final List<String> PERMISSION_TYPES = Arrays.asList(
        "build", "destroy", "switch", "item_use", "claim", "unclaim", "spawn", "set_spawn",
        "invite", "kick", "promote", "demote", "withdraw", "deposit", "plot_perm",
        "plot_set", "plot_owner", "admin", "bypass"
    );

    private PermissionArgumentType() {}

    public static PermissionArgumentType permission() {
        return new PermissionArgumentType();
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String permission = reader.readUnquotedString().toLowerCase();

        // Convert underscores to spaces for convenience
        permission = permission.replace("_", "");

        // Validate that the permission exists
        if (!isValidPermission(permission)) {
            throw INVALID_PERMISSION.createWithContext(reader);
        }

        return permission;
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    private boolean isValidPermission(String permission) {
        for (String validPermission : PERMISSION_TYPES) {
            if (validPermission.replace("_", "").equals(permission)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase().replace("_", "");

        for (String permission : PERMISSION_TYPES) {
            String cleanPermission = permission.replace("_", "");
            if (cleanPermission.startsWith(remaining)) {
                builder.suggest(permission);
            }
        }

        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("build", "destroy", "switch", "item_use");
    }

    /**
     * Get the permission name from the command context
     */
    public static String getPermission(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, String.class);
    }

    /**
     * Get the permission flag value from the command context
     */
    public static int getPermissionFlag(CommandContext<CommandSourceStack> context, String name) {
        String permission = getPermission(context, name);
        return getFlagFromName(permission);
    }

    /**
     * Convert permission name to flag value
     */
    public static int getFlagFromName(String permissionName) {
        switch (permissionName.toLowerCase().replace("_", "")) {
            case "build": return Permission.Flag.BUILD;
            case "destroy": return Permission.Flag.DESTROY;
            case "switch": return Permission.Flag.SWITCH;
            case "itemuse": case "item_use": return Permission.Flag.ITEM_USE;
            case "claim": return Permission.Flag.CLAIM;
            case "unclaim": return Permission.Flag.UNCLAIM;
            case "spawn": return Permission.Flag.SPAWN;
            case "setspawn": case "set_spawn": return Permission.Flag.SET_SPAWN;
            case "invite": return Permission.Flag.INVITE;
            case "kick": return Permission.Flag.KICK;
            case "promote": return Permission.Flag.PROMOTE;
            case "demote": return Permission.Flag.DEMOTE;
            case "withdraw": return Permission.Flag.WITHDRAW;
            case "deposit": return Permission.Flag.DEPOSIT;
            case "plotperm": case "plot_perm": return Permission.Flag.PLOT_PERM;
            case "plotset": case "plot_set": return Permission.Flag.PLOT_SET;
            case "plotowner": case "plot_owner": return Permission.Flag.PLOT_OWNER;
            case "admin": return Permission.Flag.ADMIN;
            case "bypass": return Permission.Flag.BYPASS;
            default: return -1;
        }
    }

    /**
     * Get all available permission types
     */
    public static List<String> getAllPermissionTypes() {
        return List.copyOf(PERMISSION_TYPES);
    }

    /**
     * Get permission display name
     */
    public static String getDisplayName(String permission) {
        return permission.replace("_", " ");
    }
}