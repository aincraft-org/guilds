package org.aincraft.guilds.config;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

/**
 * Configuration properties for test environment
 * Loads configuration from application-test.properties without Spring Boot
 */
public class TestConfig {

    private DatabaseConfig datasource = new DatabaseConfig();
    private PluginConfig plugin = new PluginConfig();
    private MockBukkitConfig mockbukkit = new MockBukkitConfig();
    private TestDataConfig data = new TestDataConfig();
    private LoggingConfig logging = new LoggingConfig();
    private PerformanceConfig performance = new PerformanceConfig();
    private SecurityConfig security = new SecurityConfig();
    private IntegrationConfig integration = new IntegrationConfig();
    private CoverageConfig coverage = new CoverageConfig();
    private OutputConfig output = new OutputConfig();

    /**
     * Load test configuration from application-test.properties
     */
    public static TestConfig load() {
        TestConfig config = new TestConfig();
        Properties properties = new Properties();

        try (InputStream input = TestConfig.class.getClassLoader()
                .getResourceAsStream("application-test.properties")) {

            if (input == null) {
                System.err.println("Warning: application-test.properties not found, using defaults");
                return config;
            }

            properties.load(input);

            // Load database configuration
            DatabaseConfig db = config.getDatasource();
            db.setDriverClassName(properties.getProperty("test.datasource.driver-class-name", "org.h2.Driver"));
            db.setUrl(properties.getProperty("test.datasource.url", "jdbc:h2:mem:guilds_test;DB_CLOSE_DELAY=-1;MODE=MySQL"));
            db.setUsername(properties.getProperty("test.datasource.username", "sa"));
            db.setPassword(properties.getProperty("test.datasource.password", ""));
            db.getHikari().setMaximumPoolSize(
                Integer.parseInt(properties.getProperty("test.datasource.hikari.maximum-pool-size", "5")));
            db.getHikari().setMinimumIdle(
                Integer.parseInt(properties.getProperty("test.datasource.hikari.minimum-idle", "1")));

            // Load plugin configuration
            PluginConfig plugin = config.getPlugin();
            plugin.setName(properties.getProperty("test.plugin.name", "Guilds"));
            plugin.setVersion(properties.getProperty("test.plugin.version", "1.0.0-SNAPSHOT"));
            plugin.setAuthor(properties.getProperty("test.plugin.author", "TestAuthor"));
            plugin.setDatabasePrefix(properties.getProperty("test.plugin.database-prefix", "guilds_test_"));

            // Load MockBukkit configuration
            MockBukkitConfig mockbukkit = config.getMockbukkit();
            mockbukkit.setWorldName(properties.getProperty("test.mockbukkit.world-name", "test_world"));
            mockbukkit.setWorldEnvironment(properties.getProperty("test.mockbukkit.world-environment", "NORMAL"));
            mockbukkit.setWorldSeed(
                Long.parseLong(properties.getProperty("test.mockbukkit.world-seed", "12345")));
            mockbukkit.setPlayerName(properties.getProperty("test.mockbukkit.player-name", "TestPlayer"));
            mockbukkit.setPlayerOp(
                Boolean.parseBoolean(properties.getProperty("test.mockbukkit.player-op", "false")));

            // Load data configuration
            TestDataConfig data = config.getData();
            data.getTown().setDefaultName(properties.getProperty("test.town.default-name", "TestTown"));
            data.getTown().setDefaultMayor(properties.getProperty("test.town.default-mayor", "TestMayor"));
            data.getTown().setDefaultResidents(
                Integer.parseInt(properties.getProperty("test.town.default-residents", "5")));
            data.getPlot().setDefaultPrice(
                Double.parseDouble(properties.getProperty("test.plot.default-price", "1000.0")));
            data.getPlot().setDefaultX(
                Integer.parseInt(properties.getProperty("test.plot.default-x", "10")));
            data.getPlot().setDefaultZ(
                Integer.parseInt(properties.getProperty("test.plot.default-z", "20")));
            data.getPlot().setDefaultWorld(properties.getProperty("test.plot.default-world", "test_world"));

            // Load performance configuration
            PerformanceConfig performance = config.getPerformance();
            performance.setTestTimeout(
                Duration.ofSeconds(Integer.parseInt(properties.getProperty("test.test.timeout.seconds", "30"))));
            performance.setDatabaseConnectionTimeout(
                Duration.ofMillis(Integer.parseInt(properties.getProperty("test.database.connection.timeout.milliseconds", "5000"))));

            // Load integration configuration
            IntegrationConfig integration = config.getIntegration();
            integration.setEnabled(
                Boolean.parseBoolean(properties.getProperty("test.integration.enabled", "false")));
            integration.setDatabaseCleanup(
                Boolean.parseBoolean(properties.getProperty("test.integration.database.cleanup", "true")));
            integration.setMockExternalServices(
                Boolean.parseBoolean(properties.getProperty("test.integration.mock.external.services", "true")));

        } catch (IOException e) {
            System.err.println("Error loading test configuration: " + e.getMessage());
            e.printStackTrace();
        }

        return config;
    }

    // Getters and setters
    public DatabaseConfig getDatasource() { return datasource; }
    public void setDatasource(DatabaseConfig datasource) { this.datasource = datasource; }

    public PluginConfig getPlugin() { return plugin; }
    public void setPlugin(PluginConfig plugin) { this.plugin = plugin; }

    public MockBukkitConfig getMockbukkit() { return mockbukkit; }
    public void setMockbukkit(MockBukkitConfig mockbukkit) { this.mockbukkit = mockbukkit; }

    public TestDataConfig getData() { return data; }
    public void setData(TestDataConfig data) { this.data = data; }

    public LoggingConfig getLogging() { return logging; }
    public void setLogging(LoggingConfig logging) { this.logging = logging; }

    public PerformanceConfig getPerformance() { return performance; }
    public void setPerformance(PerformanceConfig performance) { this.performance = performance; }

    public SecurityConfig getSecurity() { return security; }
    public void setSecurity(SecurityConfig security) { this.security = security; }

    public IntegrationConfig getIntegration() { return integration; }
    public void setIntegration(IntegrationConfig integration) { this.integration = integration; }

    public CoverageConfig getCoverage() { return coverage; }
    public void setCoverage(CoverageConfig coverage) { this.coverage = coverage; }

    public OutputConfig getOutput() { return output; }
    public void setOutput(OutputConfig output) { this.output = output; }

    /**
     * Database configuration for tests
     */
    public static class DatabaseConfig {
        private String driverClassName = "org.h2.Driver";
        private String url = "jdbc:h2:mem:guilds_test;DB_CLOSE_DELAY=-1;MODE=MySQL";
        private String username = "sa";
        private String password = "";
        private HikariConfig hikari = new HikariConfig();

        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public HikariConfig getHikari() { return hikari; }
        public void setHikari(HikariConfig hikari) { this.hikari = hikari; }
    }

    /**
     * HikariCP configuration for tests
     */
    public static class HikariConfig {
        private int maximumPoolSize = 5;
        private int minimumIdle = 1;
        private Duration idleTimeout = Duration.ofSeconds(30);
        private String poolName = "TestHikariPool";

        public int getMaximumPoolSize() { return maximumPoolSize; }
        public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }

        public int getMinimumIdle() { return minimumIdle; }
        public void setMinimumIdle(int minimumIdle) { this.minimumIdle = minimumIdle; }

        public Duration getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(Duration idleTimeout) { this.idleTimeout = idleTimeout; }

        public String getPoolName() { return poolName; }
        public void setPoolName(String poolName) { this.poolName = poolName; }
    }

    /**
     * Plugin configuration for tests
     */
    public static class PluginConfig {
        private String name = "Guilds";
        private String version = "1.0.0-SNAPSHOT";
        private String author = "TestAuthor";
        private String databasePrefix = "guilds_test_";

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        public String getDatabasePrefix() { return databasePrefix; }
        public void setDatabasePrefix(String databasePrefix) { this.databasePrefix = databasePrefix; }
    }

    /**
     * MockBukkit configuration for tests
     */
    public static class MockBukkitConfig {
        private String worldName = "test_world";
        private String worldEnvironment = "NORMAL";
        private long worldSeed = 12345L;
        private String playerName = "TestPlayer";
        private boolean playerOp = false;

        public String getWorldName() { return worldName; }
        public void setWorldName(String worldName) { this.worldName = worldName; }

        public String getWorldEnvironment() { return worldEnvironment; }
        public void setWorldEnvironment(String worldEnvironment) { this.worldEnvironment = worldEnvironment; }

        public long getWorldSeed() { return worldSeed; }
        public void setWorldSeed(long worldSeed) { this.worldSeed = worldSeed; }

        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }

        public boolean isPlayerOp() { return playerOp; }
        public void setPlayerOp(boolean playerOp) { this.playerOp = playerOp; }
    }

    /**
     * Test data configuration
     */
    public static class TestDataConfig {
        private TownConfig town = new TownConfig();
        private PlotConfig plot = new PlotConfig();

        public TownConfig getTown() { return town; }
        public void setTown(TownConfig town) { this.town = town; }

        public PlotConfig getPlot() { return plot; }
        public void setPlot(PlotConfig plot) { this.plot = plot; }
    }

    public static class TownConfig {
        private String defaultName = "TestTown";
        private String defaultMayor = "TestMayor";
        private int defaultResidents = 5;

        public String getDefaultName() { return defaultName; }
        public void setDefaultName(String defaultName) { this.defaultName = defaultName; }

        public String getDefaultMayor() { return defaultMayor; }
        public void setDefaultMayor(String defaultMayor) { this.defaultMayor = defaultMayor; }

        public int getDefaultResidents() { return defaultResidents; }
        public void setDefaultResidents(int defaultResidents) { this.defaultResidents = defaultResidents; }
    }

    public static class PlotConfig {
        private double defaultPrice = 1000.0;
        private int defaultX = 10;
        private int defaultZ = 20;
        private String defaultWorld = "test_world";

        public double getDefaultPrice() { return defaultPrice; }
        public void setDefaultPrice(double defaultPrice) { this.defaultPrice = defaultPrice; }

        public int getDefaultX() { return defaultX; }
        public void setDefaultX(int defaultX) { this.defaultX = defaultX; }

        public int getDefaultZ() { return defaultZ; }
        public void setDefaultZ(int defaultZ) { this.defaultZ = defaultZ; }

        public String getDefaultWorld() { return defaultWorld; }
        public void setDefaultWorld(String defaultWorld) { this.defaultWorld = defaultWorld; }
    }

    /**
     * Logging configuration for tests
     */
    public static class LoggingConfig {
        private String levelRoot = "WARN";
        private String levelAincraftGuilds = "DEBUG";
        private String levelHibernate = "ERROR";
        private String levelSpring = "ERROR";

        public String getLevelRoot() { return levelRoot; }
        public void setLevelRoot(String levelRoot) { this.levelRoot = levelRoot; }

        public String getLevelAincraftGuilds() { return levelAincraftGuilds; }
        public void setLevelAincraftGuilds(String levelAincraftGuilds) { this.levelAincraftGuilds = levelAincraftGuilds; }

        public String getLevelHibernate() { return levelHibernate; }
        public void setLevelHibernate(String levelHibernate) { this.levelHibernate = levelHibernate; }

        public String getLevelSpring() { return levelSpring; }
        public void setLevelSpring(String levelSpring) { this.levelSpring = levelSpring; }
    }

    /**
     * Performance configuration for tests
     */
    public static class PerformanceConfig {
        private Duration testTimeout = Duration.ofSeconds(30);
        private Duration databaseConnectionTimeout = Duration.ofMillis(5000);
        private Duration mockInitializationTimeout = Duration.ofMillis(3000);

        public Duration getTestTimeout() { return testTimeout; }
        public void setTestTimeout(Duration testTimeout) { this.testTimeout = testTimeout; }

        public int getTestTimeoutSeconds() { return (int) testTimeout.getSeconds(); }
        public void setTestTimeoutSeconds(int seconds) { this.testTimeout = Duration.ofSeconds(seconds); }

        public Duration getDatabaseConnectionTimeout() { return databaseConnectionTimeout; }
        public void setDatabaseConnectionTimeout(Duration databaseConnectionTimeout) { this.databaseConnectionTimeout = databaseConnectionTimeout; }

        public Duration getMockInitializationTimeout() { return mockInitializationTimeout; }
        public void setMockInitializationTimeout(Duration mockInitializationTimeout) { this.mockInitializationTimeout = mockInitializationTimeout; }
    }

    /**
     * Security configuration for tests
     */
    public static class SecurityConfig {
        private String encryptionKey = "test-encryption-key-32-chars";
        private String jwtSecret = "test-jwt-secret-key-64-characters-long-enough-for-hs256-algorithm";
        private int sessionTimeout = 3600;

        public String getEncryptionKey() { return encryptionKey; }
        public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }

        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }

        public int getSessionTimeout() { return sessionTimeout; }
        public void setSessionTimeout(int sessionTimeout) { this.sessionTimeout = sessionTimeout; }
    }

    /**
     * Integration configuration for tests
     */
    public static class IntegrationConfig {
        private boolean enabled = false;
        private boolean databaseCleanup = true;
        private boolean mockExternalServices = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isDatabaseCleanup() { return databaseCleanup; }
        public void setDatabaseCleanup(boolean databaseCleanup) { this.databaseCleanup = databaseCleanup; }

        public boolean isMockExternalServices() { return mockExternalServices; }
        public void setMockExternalServices(boolean mockExternalServices) { this.mockExternalServices = mockExternalServices; }
    }

    /**
     * Coverage configuration for tests
     */
    public static class CoverageConfig {
        private int minimumLines = 80;
        private int minimumBranches = 70;
        private String[] excludedClasses = new String[]{"**/*Test*", "**/Test*"};

        public int getMinimumLines() { return minimumLines; }
        public void setMinimumLines(int minimumLines) { this.minimumLines = minimumLines; }

        public int getMinimumBranches() { return minimumBranches; }
        public void setMinimumBranches(int minimumBranches) { this.minimumBranches = minimumBranches; }

        public String[] getExcludedClasses() { return excludedClasses; }
        public void setExcludedClasses(String[] excludedClasses) { this.excludedClasses = excludedClasses; }
    }

    /**
     * Output configuration for tests
     */
    public static class OutputConfig {
        private String directory = "build/test-results";
        private boolean includeExceptions = true;
        private boolean includeSystemOut = true;
        private boolean includeSystemErr = true;

        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }

        public boolean isIncludeExceptions() { return includeExceptions; }
        public void setIncludeExceptions(boolean includeExceptions) { this.includeExceptions = includeExceptions; }

        public boolean isIncludeSystemOut() { return includeSystemOut; }
        public void setIncludeSystemOut(boolean includeSystemOut) { this.includeSystemOut = includeSystemOut; }

        public boolean isIncludeSystemErr() { return includeSystemErr; }
        public void setIncludeSystemErr(boolean includeSystemErr) { this.includeSystemErr = includeSystemErr; }
    }
}