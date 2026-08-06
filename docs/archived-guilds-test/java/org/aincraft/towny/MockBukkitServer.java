package org.aincraft.towny;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * MockBukkit server wrapper for testing
 */
public class MockBukkitServer {

    private static ServerMock server;

    /**
     * Create a new MockBukkit server instance
     */
    public static ServerMock create() {
        server = MockBukkit.mock();
        return server;
    }

    /**
     * Get the current server instance
     */
    public static ServerMock getServer() {
        return server;
    }

    /**
     * Create a test world
     */
    public static World createWorld(String name) {
        WorldMock world = new WorldMock();
        world.setName(name);
        server.addWorld(world);
        return world;
    }

    /**
     * Add a test player to the server
     */
    public static Player addPlayer(String name) {
        PlayerMock player = new PlayerMock(server, name);
        server.addPlayer(player);
        return player;
    }

    /**
     * Unmock the server (cleanup)
     */
    public static void unmock() {
        if (server != null) {
            MockBukkit.unmock();
            server = null;
        }
    }
}