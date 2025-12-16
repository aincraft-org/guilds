package org.aincraft.commands;

import org.bukkit.command.CommandSender;

/**
 * Interface for guild command components.
 * Each component handles a specific guild operation.
 */
public interface GuildCommand {
    /**
     * Executes the command.
     *
     * @param sender the command sender
     * @param args the command arguments (includes command name at index 0)
     * @return true if command succeeded, false otherwise
     */
    boolean execute(CommandSender sender, String[] args);

    /**
     * Gets the name of this command (e.g., "create", "join").
     *
     * @return the command name
     */
    String getName();

    /**
     * Gets the permission node required for this command.
     *
     * @return the permission node
     */
    String getPermission();

    /**
     * Gets the usage string for this command.
     *
     * @return the usage string
     */
    String getUsage();
}
