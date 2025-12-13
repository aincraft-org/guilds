package org.aincraft.towny.base;

import org.aincraft.towny.MockBukkitServer;
import org.aincraft.towny.config.TestConfig;
import org.aincraft.towny.utils.TestDatabaseHelper;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mock;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Base class for integration tests
 * Provides common setup and teardown for database and MockBukkit server
 * Designed for Bukkit plugin testing using JUnit 5, Mockito, and MockBukkit
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    protected TestConfig testConfig;
    protected DataSource dataSource;
    protected Connection testConnection;
    protected Plugin mockPlugin;

    // MockBukkit server and world
    protected org.bukkit.Server mockServer;
    protected org.bukkit.World mockWorld;
    protected org.bukkit.entity.Player mockPlayer;

    @BeforeAll
    void setupGlobal() throws SQLException {
        // Load test configuration
        testConfig = TestConfig.load();

        // Validate test configuration
        validateTestConfiguration();

        // Initialize database connection
        initializeDatabaseConnection();

        // Setup MockBukkit server once for all tests
        setupMockBukkitServer();

        // Create mock plugin
        mockPlugin = createMockPlugin();
    }

    @BeforeEach
    void setUp() {
        // Verify test environment is ready
        verifyTestEnvironment();

        // Setup per-test player if needed
        setupTestPlayer();
    }

    @AfterEach
    void tearDown() {
        // Clean up test data if configured
        if (testConfig.getIntegration().isDatabaseCleanup()) {
            cleanupTestData();
        }
    }

    @AfterAll
    void cleanupGlobal() {
        // Cleanup MockBukkit server
        cleanupMockBukkitServer();

        // Close database connection
        closeDatabaseConnection();
    }

    /**
     * Validates the test configuration
     */
    private void validateTestConfiguration() {
        if (testConfig == null) {
            throw new IllegalStateException("TestConfig is not properly initialized");
        }

        // Validate database configuration
        if (testConfig.getDatasource().getUrl() == null || testConfig.getDatasource().getUrl().isEmpty()) {
            throw new IllegalStateException("Database URL is not configured for tests");
        }

        // Validate MockBukkit configuration
        if (testConfig.getMockbukkit().getWorldName() == null || testConfig.getMockbukkit().getWorldName().isEmpty()) {
            throw new IllegalStateException("MockBukkit world name is not configured");
        }
    }

    /**
     * Initialize database connection for tests
     */
    private void initializeDatabaseConnection() throws SQLException {
        // Use direct connection for testing without DataSource
        String url = testConfig.getDatasource().getUrl();
        String username = testConfig.getDatasource().getUsername();
        String password = testConfig.getDatasource().getPassword();

        try {
            testConnection = DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            throw new SQLException("Failed to establish database connection: " + e.getMessage(), e);
        }
    }

    /**
     * Setup MockBukkit server for tests
     */
    private void setupMockBukkitServer() {
        long startTime = System.currentTimeMillis();
        long timeout = testConfig.getPerformance().getMockInitializationTimeout().toMillis();

        try {
            MockBukkitServer.create();

            // Create test world
            MockBukkitServer.createWorld(testConfig.getMockbukkit().getWorldName());

            // Add test player
            MockBukkitServer.addPlayer(testConfig.getMockbukkit().getPlayerName());

        } catch (Exception e) {
            if (System.currentTimeMillis() - startTime >= timeout) {
                throw new RuntimeException("Failed to initialize MockBukkit server within timeout", e);
            }
            throw new RuntimeException("Failed to initialize MockBukkit server", e);
        }
    }

    /**
     * Verify that the test environment is ready
     */
    private void verifyTestEnvironment() {
        // Verify database connection is still valid
        try {
            if (testConnection == null || testConnection.isClosed()) {
                throw new IllegalStateException("Database connection is not valid");
            }

            // Simple test query
            testConnection.createStatement().execute("SELECT 1");

        } catch (SQLException e) {
            throw new IllegalStateException("Database connection verification failed", e);
        }

        // Verify MockBukkit server is running
        if (MockBukkitServer.getServer() == null) {
            throw new IllegalStateException("MockBukkit server is not initialized");
        }
    }

    /**
     * Clean up MockBukkit server
     */
    private void cleanupMockBukkitServer() {
        try {
            MockBukkitServer.unmock();
        } catch (Exception e) {
            // Log error but don't fail the test
            System.err.println("Error cleaning up MockBukkit server: " + e.getMessage());
        }
    }

    /**
     * Clean up test data from database
     */
    private void cleanupTestData() {
        if (testConnection != null) {
            try {
                // Clean up test data in proper order to respect foreign key constraints
                String[] cleanupQueries = {
                    "DELETE FROM " + testConfig.getPlugin().getDatabasePrefix() + "town_blocks",
                    "DELETE FROM " + testConfig.getPlugin().getDatabasePrefix() + "towns",
                    "DELETE FROM " + testConfig.getPlugin().getDatabasePrefix() + "residents",
                    "DELETE FROM " + testConfig.getPlugin().getDatabasePrefix() + "permissions"
                };

                for (String query : cleanupQueries) {
                    try {
                        testConnection.createStatement().executeUpdate(query);
                    } catch (SQLException e) {
                        // Table might not exist, ignore
                    }
                }

                testConnection.commit();

            } catch (SQLException e) {
                // Log error but don't fail the test
                System.err.println("Error cleaning up test data: " + e.getMessage());
            }
        }
    }

    /**
     * Get test timeout in seconds
     */
    protected int getTestTimeoutSeconds() {
        return (int) testConfig.getPerformance().getTestTimeout().getSeconds();
    }

    /**
     * Execute database operation with error handling
     */
    protected void executeDatabaseUpdate(String sql) throws SQLException {
        try {
            testConnection.createStatement().executeUpdate(sql);
        } catch (SQLException e) {
            throw new SQLException("Failed to execute database update: " + sql, e);
        }
    }

    /**
     * Check if integration tests are enabled
     */
    protected boolean isIntegrationEnabled() {
        return testConfig.getIntegration().isEnabled();
    }

    /**
     * Skip test if integration is disabled
     */
    protected void assumeIntegrationEnabled() {
        if (!isIntegrationEnabled()) {
            Assumptions.assumeTrue(false, "Integration tests are disabled");
        }
    }

    /**
     * Create a mock plugin instance for testing
     */
    protected org.bukkit.plugin.Plugin createMockPlugin() {
        org.bukkit.plugin.Plugin plugin = org.mockito.Mockito.mock(org.bukkit.plugin.Plugin.class);

        org.mockito.Mockito.when(plugin.getName()).thenReturn(getPluginName());
        org.mockito.Mockito.when(plugin.isEnabled()).thenReturn(true);
        org.mockito.Mockito.when(plugin.getServer()).thenReturn(mockServer);

        return plugin;
    }

    /**
     * Setup test player with MockBukkit
     */
    private void setupTestPlayer() {
        if (mockPlayer == null) {
            mockPlayer = MockBukkitServer.addPlayer(testConfig.getMockbukkit().getPlayerName());
        }
    }

    /**
     * Close database connection
     */
    private void closeDatabaseConnection() {
        if (testConnection != null) {
            try {
                testConnection.close();
            } catch (SQLException e) {
                System.err.println("Error closing database connection: " + e.getMessage());
            }
        }
    }

    /**
     * Get MockBukkit world name for tests
     */
    protected String getMockBukkitWorldName() {
        return testConfig.getMockbukkit().getWorldName();
    }

    /**
     * Get plugin name for tests
     */
    protected String getPluginName() {
        return testConfig.getPlugin().getName();
    }
}