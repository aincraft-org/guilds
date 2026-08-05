package org.aincraft.towny.base;

import org.aincraft.towny.MockBukkitServer;
import org.aincraft.towny.config.TestConfig;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Base class for unit tests
 * Provides common mock setup and configuration for Bukkit plugin unit tests
 * Uses JUnit 5, Mockito, and MockBukkit for Bukkit-specific testing
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class BaseUnitTest {

    // Test configuration loaded from properties
    protected TestConfig testConfig;

    // Common test objects
    protected World mockWorld;
    protected Player mockPlayer;
    protected org.bukkit.Server mockServer;

    @BeforeEach
    void setUp() {
        // Initialize test configuration
        testConfig = TestConfig.load();

        // Initialize mocks
        MockitoAnnotations.openMocks(this);

        // Setup MockBukkit if needed
        setupMockBukkit();

        // Additional setup for subclasses
        setup();
    }

    @AfterEach
    void tearDown() {
        // Additional cleanup for subclasses
        cleanup();

        // Cleanup MockBukkit
        cleanupMockBukkit();

        // Reset mocks to prevent test pollution
        org.mockito.Mockito.reset();
    }

    /**
     * Setup MockBukkit server if required
     */
    private void setupMockBukkit() {
        if (requiresMockBukkit()) {
            MockBukkitServer.create();
            mockServer = MockBukkitServer.getServer();
            mockWorld = MockBukkitServer.createWorld(testConfig.getData().getPlot().getDefaultWorld());
            mockPlayer = MockBukkitServer.addPlayer(testConfig.getMockbukkit().getPlayerName());
        }
    }

    /**
     * Cleanup MockBukkit server
     */
    private void cleanupMockBukkit() {
        if (requiresMockBukkit()) {
            MockBukkitServer.unmock();
            mockServer = null;
            mockWorld = null;
            mockPlayer = null;
        }
    }

    /**
     * Override this to return true if the test needs MockBukkit setup
     */
    protected boolean requiresMockBukkit() {
        return false;
    }

    /**
     * Additional setup for subclasses
     * Override this method in subclasses to provide specific setup logic
     */
    protected void setup() {
        // Default implementation does nothing
    }

    /**
     * Additional cleanup for subclasses
     * Override this method in subclasses to provide specific cleanup logic
     */
    protected void cleanup() {
        // Default implementation does nothing
    }

    /**
     * Get test timeout in seconds
     */
    protected int getTestTimeoutSeconds() {
        return testConfig.getPerformance().getTestTimeoutSeconds();
    }

    /**
     * Get default town name for tests
     */
    protected String getDefaultTownName() {
        return testConfig.getData().getTown().getDefaultName();
    }

    /**
     * Get default mayor name for tests
     */
    protected String getDefaultMayorName() {
        return testConfig.getData().getTown().getDefaultMayor();
    }

    /**
     * Get default plot price for tests
     */
    protected double getDefaultPlotPrice() {
        return testConfig.getData().getPlot().getDefaultPrice();
    }

    /**
     * Get default plot coordinates for tests
     */
    protected int[] getDefaultPlotCoordinates() {
        return new int[]{
            testConfig.getData().getPlot().getDefaultX(),
            testConfig.getData().getPlot().getDefaultZ()
        };
    }

    /**
     * Get default world name for tests
     */
    protected String getDefaultWorldName() {
        return testConfig.getData().getPlot().getDefaultWorld();
    }

    /**
     * Get database prefix for tests
     */
    protected String getDatabasePrefix() {
        return testConfig.getPlugin().getDatabasePrefix();
    }

    /**
     * Get plugin name for tests
     */
    protected String getPluginName() {
        return testConfig.getPlugin().getName();
    }

    /**
     * Get plugin version for tests
     */
    protected String getPluginVersion() {
        return testConfig.getPlugin().getVersion();
    }

    /**
     * Get MockBukkit world name for tests
     */
    protected String getMockBukkitWorldName() {
        return testConfig.getMockbukkit().getWorldName();
    }

    /**
     * Get MockBukkit player name for tests
     */
    protected String getMockBukkitPlayerName() {
        return testConfig.getMockbukkit().getPlayerName();
    }

    /**
     * Check if MockBukkit player should be OP
     */
    protected boolean isMockBukkitPlayerOp() {
        return testConfig.getMockbukkit().isPlayerOp();
    }

    /**
     * Validate that test configuration is properly initialized
     */
    protected void validateTestConfiguration() {
        if (testConfig == null) {
            throw new IllegalStateException("TestConfig is not properly initialized");
        }
    }

    /**
     * Sleep for a specified duration (useful for async tests)
     */
    protected void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Create a mock plugin instance for testing
     */
    protected org.bukkit.plugin.Plugin createMockPlugin() {
        org.bukkit.plugin.Plugin plugin = org.mockito.Mockito.mock(org.bukkit.plugin.Plugin.class);
        org.mockito.Mockito.when(plugin.getName()).thenReturn(getPluginName());
        org.mockito.Mockito.when(plugin.isEnabled()).thenReturn(true);
        return plugin;
    }
}