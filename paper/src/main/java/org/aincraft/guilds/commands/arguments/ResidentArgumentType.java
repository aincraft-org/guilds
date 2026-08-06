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
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

        // Validate that the resident exists (either online or in database)
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
        // Check if player is online
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) {
            return true;
        }

        // Check in resident service database
        try {
            UUID uuid = Bukkit.getOfflinePlayer(name).getUniqueId();
            return residentService.getResident(uuid).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        // Suggest online players first
        List<String> onlinePlayers = Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name.toLowerCase().startsWith(builder.getRemainingLowerCase()))
            .collect(Collectors.toList());

        for (String playerName : onlinePlayers) {
            builder.suggest(playerName);
        }

        // TODO: Add offline resident suggestions from database
        // This would require database access which might be expensive for tab completion

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
     * Get the resident UUID from the command context
     */
    public static UUID getResidentUuid(CommandContext<CommandSourceStack> context, String name) {
        String residentName = context.getArgument(name, String.class);
        Player player = Bukkit.getPlayerExact(residentName);
        return player != null ? player.getUniqueId() : Bukkit.getOfflinePlayer(residentName).getUniqueId();
    }
}