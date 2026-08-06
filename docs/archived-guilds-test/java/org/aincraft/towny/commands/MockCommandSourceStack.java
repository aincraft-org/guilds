package org.aincraft.towny.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public class MockCommandSourceStack implements CommandSourceStack {
    private final CommandSender sender;
    
    public MockCommandSourceStack(CommandSender sender) {
        this.sender = sender;
    }
    
    @Override
    public @NotNull CommandSender getSender() {
        return sender;
    }
    
    @Override
    public @NotNull Location getLocation() {
        throw new UnsupportedOperationException("Not implemented");
    }
    
    @Override
    public @NotNull Location getAnchor() {
        throw new UnsupportedOperationException("Not implemented");
    }
    
    @Override
    public @NotNull Entity getEntity() {
        throw new UnsupportedOperationException("Not implemented");
    }
    
    @Override
    public @NotNull Vector getFacing() {
        throw new UnsupportedOperationException("Not implemented");
    }
}