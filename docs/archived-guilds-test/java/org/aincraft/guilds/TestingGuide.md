# Guilds Plugin Testing Guide

This guide provides comprehensive information about testing the Guilds Bukkit plugin using modern testing practices with MockBukkit, JUnit 5, and Mockito.

## 🚨 MIGRATION NOTICE

The testing infrastructure has been recently refactored to follow senior development best practices. Some older patterns have been deprecated:

- **Deprecated**: `TestUtilities` class with static state
- **Use instead**: `BaseUnitTest`, `BaseIntegrationTest`, `TestObjectFactory`, and `TestDataBuilder`

## 🎯 BUKKIT-SPECIFIC TESTING

This testing suite is specifically designed for **Bukkit plugin development** and uses:

- **JUnit 5**: Modern testing framework
- **MockBukkit**: Bukkit server mocking for plugin testing
- **Mockito**: Advanced mocking framework for unit tests
- **H2 Database**: In-memory database for testing
- **No Spring**: Pure Java/Bukkit testing without Spring Boot dependencies

## Table of Contents

- [Overview](#overview)
- [New Testing Architecture](#new-testing-architecture)
- [Setup](#setup)
- [Test Structure](#test-structure)
- [Writing Tests](#writing-tests)
- [Test Configuration](#test-configuration)
- [Object Factory Pattern](#object-factory-pattern)
- [Builder Pattern](#builder-pattern)
- [Database Testing](#database-testing)
- [Best Practices](#best-practices)
- [Examples](#examples)
- [Migration Guide](#migration-guide)

## Overview

The Guilds testing suite is designed with the following principles:

- **Test Isolation**: Each test runs in isolation with proper setup/teardown
- **Thread Safety**: All test utilities are thread-safe
- **Configuration Management**: Centralized test configuration with environment-specific settings
- **Extensibility**: Easy to extend with new test utilities and patterns
- **Maintainability**: Clear separation of concerns and reusable components

## New Testing Architecture

### Base Test Classes

#### BaseUnitTest
```java
@ExtendWith(MockitoExtension.class)
public class YourTest extends BaseUnitTest {
    // Your unit tests here
}
```

- Provides basic unit test infrastructure for Bukkit plugins
- Includes Mockito setup with LENIENT settings
- Access to test configuration via `testConfig`
- Optional MockBukkit setup (override `requiresMockBukkit()`)
- Automatic mock initialization and cleanup

#### BaseIntegrationTest
```java
public class YourIntegrationTest extends BaseIntegrationTest {
    // Your integration tests here
}
```

- Full MockBukkit server setup/teardown
- Database connection management (H2 in-memory)
- Bukkit plugin mock creation
- Test data cleanup between tests
- No Spring dependencies - pure Bukkit testing

### Test Configuration

All test configuration is centralized in `application-test.properties` and accessible via `TestConfig`:

```java
// In base classes, testConfig is automatically loaded
String worldName = testConfig.getMockbukkit().getWorldName();
String dbUrl = testConfig.getDatasource().getUrl();

// Manual loading if needed
TestConfig config = TestConfig.load();
```

### Bukkit-Specific Testing Patterns

#### Command Testing
```java
class PlotCommandTest extends BaseIntegrationTest {
    @Test
    void shouldHandlePlotCommand() {
        // MockBukkit provides mockPlayer and mockWorld automatically
        String[] args = {"claim"};

        when(plotService.canResidentClaimPlot(any(), anyInt(), anyInt(), anyString()))
           .thenReturn(true);

        boolean result = plotCommand.onCommand(mockPlayer, mockCommand, "plot", args);

        assertThat(result).isTrue();
        verify(mockPlayer).sendMessage(ChatColor.GREEN + "Plot claimed successfully!");
    }
}
```

#### Event Testing
```java
class PlayerMoveListenerTest extends BaseIntegrationTest {
    @Test
    void shouldHandlePlayerMove() {
        // Mock player movement
        Location from = new Location(mockWorld, 0, 64, 0);
        Location to = new Location(mockWorld, 16, 64, 16);
        PlayerMoveEvent event = new PlayerMoveEvent(mockPlayer, from, to);

        // Test event handling
        playerMoveListener.onPlayerMove(event);

        assertThat(event.isCancelled()).isFalse();
    }
}
```

#### Database Integration Testing
```java
class PlotServiceTest extends BaseIntegrationTest {
    @Test
    void shouldPersistPlotToDatabase() throws SQLException {
        // Database connection is automatically available
        TownBlock plot = TestObjectFactory.createTestTownBlock();

        boolean result = plotService.savePlot(plot);

        assertThat(result).isTrue();
        // Database cleanup is automatic
    }
}
```

## Object Factory Pattern

### TestObjectFactory

Use `TestObjectFactory` for creating standard test objects with realistic data:

```java
// Create basic test objects
TownBlock townBlock = TestObjectFactory.createTestTownBlock();
Town town = TestObjectFactory.createTestTown();
Player player = TestObjectFactory.createTestPlayer("TestPlayer");

// Create objects with specific parameters
TownBlock customBlock = TestObjectFactory.createTestTownBlock(10, 20, "world", "town", uuid);
Player opPlayer = TestObjectFactory.createTestOpPlayer("Admin");

// Create collections of test objects
Set<TownBlock> adjacentBlocks = TestObjectFactory.createAdjacentTownBlocks("world", "town", 10, 20);
Map<String, TownBlock> permissionBlocks = TestObjectFactory.createTownBlocksWithVariousPermissions("world", "town");
```

### Benefits:
- **Consistent**: Always creates objects with proper defaults
- **Thread-safe**: Can be used in parallel tests
- **Validated**: Includes input validation and error handling
- **Comprehensive**: Covers various scenarios and edge cases

## Builder Pattern

### TestDataBuilder

Use `TestDataBuilder` for complex test object creation with fluent interface:

```java
TownBlock block = TestDataBuilder.aTownBlock()
    .withCoordinates(10, 20)
    .inWorld("test_world")
    .belongingToTown("my_town")
    .ownedBy(playerUuid)
    .asShop()
    .forSale(5000.0)
    .withAllBuildPermissions()
    .withCustomName("Premium Shop")
    .claimedNow()
    .build();
```

### Builder Features:
- **Fluent API**: Method chaining for readable tests
- **Validation**: Input validation during building
- **Convenience Methods**: Shortcuts for common configurations
- **Type Safety**: Compile-time checking of configurations

## Database Testing

### TestDatabaseHelper

Use `TestDatabaseHelper` for database-related test operations:

```java
// Test database availability
boolean available = TestDatabaseHelper.isDatabaseAvailable(dataSource);

// Wait for database
boolean ready = TestDatabaseHelper.waitForDatabase(dataSource, 5000);

// Clean up test data
TestDatabaseHelper.cleanupTestData(connection, testConfig);

// Verify database state
DatabaseVerificationResult result = TestDatabaseHelper.verifyDatabaseState(connection, testConfig);
```

### Database Features:
- **Cross-database**: Works with H2, MySQL, PostgreSQL, SQLite
- **Safe Operations**: Proper constraint handling
- **Verification**: Database state validation
- **Cleanup**: Automatic test data removal

## Test Structure

```
src/test/java/org/aincraft/guilds/
├── base/                          # Base test classes
│   ├── BaseUnitTest.java         # Base unit test infrastructure
│   └── BaseIntegrationTest.java  # Base integration test infrastructure
├── config/                       # Test configuration
│   └── TestConfig.java           # Configuration properties
├── factory/                      # Test object creation
│   ├── TestObjectFactory.java    # Factory methods for test objects
│   └── TestDataBuilder.java      # Builder pattern for test data
├── utils/                        # Test utilities
│   └── TestDatabaseHelper.java   # Database testing utilities
├── models/                       # Model tests
│   ├── TownBlockTest.java        # TownBlock model tests
│   └── TownTest.java             # Town model tests
├── services/                     # Service tests
│   ├── PlotServiceTest.java      # Plot service tests
│   └── PermissionServiceTest.java # Permission service tests
├── commands/                     # Command tests
│   └── PlotCommandTest.java      # Plot command tests
├── database/migration/           # Database tests
│   └── SchemaMigrationTest.java  # Migration tests
├── MockBukkitServer.java         # MockBukkit server wrapper
├── TestUtilities.java            # Legacy utilities (deprecated)
└── TestingGuide.md               # This guide
```

## Setup

### Dependencies

The testing setup includes these key dependencies:

```kotlin
// Testing
testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
testImplementation("org.mockito:mockito-core:5.11.0")

// MockBukkit for Bukkit testing
testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.127.2")

// Additional testing utilities
testImplementation("org.assertj:assertj-core:3.25.3")
testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")

// H2 in-memory database for testing
testImplementation("com.h2database:h2:2.2.224")
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "org.aincraft.guilds.models.TownBlockTest"

# Run tests with coverage report
./gradlew test jacocoTestReport
```

## Test Structure

```
src/test/java/org/aincraft/guilds/
├── models/
│   └── TownBlockTest.java
├── services/
│   ├── PlotServiceTest.java
│   └── PermissionServiceTest.java
├── commands/
│   └── PlotCommandTest.java
├── database/
│   └── migration/
│       └── SchemaMigrationTest.java
├── TestUtilities.java
├── MockBukkitServer.java
└── TestingGuide.md
```

## Writing Tests

### Test Class Structure

```java
@ExtendWith(MockitoExtension.class)
class YourTestClass {

    @Mock
    private DependencyService dependencyService;

    @InjectMocks
    private YourClass yourClass;

    @BeforeEach
    void setUp() {
        // Setup common test data
    }

    @Test
    @DisplayName("Should do something correctly")
    void shouldDoSomethingCorrectly() {
        // Given - setup test conditions

        // When - execute the method being tested

        // Then - verify the results
        assertThat(result).isEqualTo(expected);
    }
}
```

### Using TestUtilities

The `TestUtilities` class provides common helpers:

```java
// Create test objects
TownBlock townBlock = TestUtilities.createTestTownBlock();
TownBlock ownedPlot = TestUtilities.createTestTownBlockForSale(1000.0);
Player player = TestUtilities.createTestPlayerAtChunk(10, 20);

// Test permissions
TestUtilities.assertHasPermission(townBlock, Permission.Flag.BUILD);
TestUtilities.assertDoesNotHavePermission(townBlock, Permission.Flag.ADMIN);
TestUtilities.assertHasAllPermissions(townBlock, Permission.Flag.BUILD, Permission.Flag.DESTROY);

// Get services
PlotService plotService = TestUtilities.getService(PlotService.class);
PlotCommand plotCommand = TestUtilities.getPlotCommand();
```

## MockBukkit Usage

### Basic Server Setup

```java
@BeforeEach
void setUp() {
    MockBukkitServer.create();

    // Create test world
    World world = MockBukkitServer.createWorld("test_world");

    // Add test player
    Player player = MockBukkitServer.addPlayer("TestPlayer");
    when(player.getUniqueId()).thenReturn(testUuid);

    // Setup location
    Location location = new Location(world, 0, 64, 0);
    when(player.getLocation()).thenReturn(location);
}
```

### Advanced Mocking

```java
// Mock chunk
Chunk chunk = mock(Chunk.class);
when(chunk.getX()).thenReturn(10);
when(chunk.getZ()).thenReturn(20);
when(player.getChunk()).thenReturn(chunk);

// Mock inventory
PlayerInventory inventory = mock(PlayerInventory.class);
when(player.getInventory()).thenReturn(inventory);
when(inventory.contains(itemStack)).thenReturn(true);
```

## Best Practices

### 1. Test Naming

```java
// Good: Descriptive and clear
@Test
@DisplayName("Should claim plot successfully when no conflicts exist")
void shouldClaimPlotSuccessfullyWhenNoConflictsExist() {
    // test implementation
}

// Bad: Vague name
@Test
void testClaim() {
    // test implementation
}
```

### 2. Given-When-Then Pattern

```java
@Test
void shouldClaimPlotSuccessfully() {
    // Given - Setup test conditions
    TownBlock existingPlot = createTestPlot();
    when(plotService.townBlockExists(x, z, world)).thenReturn(false);

    // When - Execute the method
    boolean result = plotService.claimPlot(x, z, world, townName);

    // Then - Verify results
    assertThat(result).isTrue();
    verify(plotService).claimPlot(x, z, world, townName);
}
```

### 3. Mock Verification

```java
// Good: Verify specific interactions
verify(plotService).claimPlot(eq(x), eq(z), eq(world), eq(townName));
verify(player).sendMessage(eq(ChatColor.GREEN + "Plot claimed successfully!"));

// Good: Verify interaction count
verify(plotService, times(1)).claimPlot(anyInt(), anyInt(), anyString(), anyString());

// Bad: Verify all interactions
verify(plotService); // Too broad, hard to maintain
```

### 4. Assertion Libraries

```java
// Use AssertJ for readable assertions
assertThat(result).isTrue()
    .isNotEqualTo(false)
    .hasMessage("Plot should be claimed successfully");

// Use parameterized tests for multiple scenarios
@ParameterizedTest
@ValueSource(strings = {"build", "destroy", "switch", "item_use"})
void shouldGrantPermission(String permission) {
    // test implementation
}
```

### 5. Test Isolation

```java
@BeforeEach
void setUp() {
    // Fresh setup for each test
    mockServer = MockBukkitServer.create();
    player = mockServer.addPlayer("TestPlayer");
}

@AfterEach
void tearDown() {
    // Clean up after each test
    MockBukkitServer.unmock();
}
```

## Examples

### Model Testing Example

```java
@Test
@DisplayName("Should handle bitwise permissions correctly")
void shouldHandleBitwisePermissionsCorrectly() {
    // Given
    TownBlock townBlock = new TownBlock(10, 20, "world", "townId");
    int buildAndDestroy = Permission.Flag.BUILD | Permission.Flag.DESTROY;

    // When
    townBlock.setPermissionsFlags(buildAndDestroy);

    // Then
    assertThat(townBlock.hasPermissionFlag(Permission.Flag.BUILD)).isTrue();
    assertThat(townBlock.hasPermissionFlag(Permission.Flag.DESTROY)).isTrue();
    assertThat(townBlock.hasPermissionFlag(Permission.Flag.SWITCH)).isFalse();
}
```

### Service Testing Example

```java
@Test
@DisplayName("Should create town block successfully")
void shouldCreateTownBlockSuccessfully() throws SQLException {
    // Given
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
    when(preparedStatement.executeUpdate()).thenReturn(1);

    // When
    TownBlock result = plotService.createTownBlock(10, 20, "world", "town");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getX()).isEqualTo(10);
    assertThat(result.getZ()).isEqualTo(20);
    assertThat(result.getWorld()).isEqualTo("world");
    assertThat(result.getTownId()).isEqualTo("town");
}
```

### Command Testing Example

```java
@Test
@DisplayName("Should claim plot successfully")
void shouldClaimPlotSuccessfully() {
    // Given
    String[] args = {"claim"};
    when(plotService.townBlockExists(10, 20, "world")).thenReturn(false);
    when(plotService.canResidentClaimPlot(playerUuid, 10, 20, "world")).thenReturn(true);
    when(plotService.claimPlotForResident(playerUuid, 10, 20, "world")).thenReturn(true);

    // When
    boolean result = plotCommand.onCommand(player, command, "plot", args);

    // Then
    assertThat(result).isTrue();
    verify(player).sendMessage(ChatColor.GREEN + "Plot claimed successfully!");
    verify(plotService).claimPlotForResident(playerUuid, 10, 20, "world");
}
```

### Integration Testing Example

```java
@Test
@DisplayName("Should initialize database schema")
void shouldInitializeDatabaseSchema() throws SQLException {
    // Given
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);

    // When
    schemaInitializer.initialize(connection);

    // Then
    verify(statement, times(4)).executeUpdate(); // Initial schema + 4 migrations
    verify(connection).close();
}
```

### Database Testing Example

```java
@Test
@DisplayName("Should handle SQL exceptions gracefully")
void shouldHandleSQLExceptionsGracefully() {
    // Given
    when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

    // When & Then
    assertThatThrownBy(() -> plotService.getTownBlock(10, 20, "world"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to get town block");
}
```

### Parameterized Testing Example

```java
@ParameterizedTest
@ValueSource(strings = {"0", "100", "1000", "-1"})
void shouldHandlePriceValidation(double price) {
    // Given
    TownBlock townBlock = new TownBlock(10, 20, "world", "town");

    // When
    townBlock.setPrice(price);

    // Then
    if (price < 0) {
        assertThat(townBlock.getPrice()).isEqualTo(0.0); // Should not allow negative
    } else {
        assertThat(townBlock.getPrice()).isEqualTo(price);
    }
    assertThat(townBlock.isForSale()).isEqualTo(price > 0);
}
```

## Running Tests with Coverage

### Generate Coverage Report

```bash
# Run tests with coverage
./gradlew test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

### Coverage Goals

Aim for:
- **Model Classes**: 90-100% coverage
- **Service Classes**: 80-90% coverage
- **Command Classes**: 85-95% coverage
- **Database Layer**: 75-85% coverage

## Continuous Integration

### GitHub Actions Example

```yaml
name: Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 21
      uses: actions/setup-java@v3
      with:
        java-version: '21'
        distribution: 'temurin'
    - name: Run tests
      run: ./gradlew test
    - name: Generate coverage report
      run: ./gradlew jacocoTestReport
```

### Pre-commit Hooks

```bash
#!/bin/sh
# .git/hooks/pre-commit

echo "Running tests..."
./gradlew test

if [ $? -ne 0 ]; then
    echo "Tests failed!"
    exit 1
fi
```

## Tips and Tricks

### 1. Test Data Builders

```java
public class TownBlockTestDataBuilder {
    private int x = 0;
    private int z = 0;
    private String world = "test_world";
    private String townId = "test_town";
    private UUID ownerId = null;
    private String plotType = TownBlock.PlotType.DEFAULT;
    private double price = 0.0;
    private int permissionsFlags = Permission.Flag.DEFAULT_PLOT;

    public TownBlockTestDataBuilder withCoordinates(int x, int z) {
        this.x = x;
        this.z = z;
        return this;
    }

    public TownBlockTestDataBuilder withOwner(UUID ownerId) {
        this.ownerId = ownerId;
        return this;
    }

    public TownBlockTestDataBuilder forSale(double price) {
        this.price = price;
        return this;
    }

    public TownBlock build() {
        TownBlock townBlock = new TownBlock(x, z, world, townId);
        townBlock.setOwnerId(ownerId);
        townBlock.setPlotType(plotType);
        townBlock.setPrice(price);
        townBlock.setPermissionsFlags(permissionsFlags);
        return townBlock;
    }
}

// Usage
TownBlock testPlot = new TownBlockTestDataBuilder()
    .withCoordinates(10, 20)
    .withOwner(playerUuid)
    .forSale(1000.0)
    .build();
```

### 2. Custom Assertions

```java
public class TownBlockAssertions {
    public static void assertThatPlotHasPermissions(TownBlock townBlock, int expectedFlags) {
        assertThat(townBlock.getPermissionsFlags() & expectedFlags)
            .isEqualTo(expectedFlags);
    }

    public static void assertThatPlotAllowsAllBuildActions(TownBlock townBlock) {
        assertThat(townBlock.hasPermissionFlag(Permission.Flag.BUILD)).isTrue();
        assertThat(townBlock.hasPermissionFlag(Permission.Flag.DESTROY)).isTrue();
        assertThat(townBlock.hasPermissionFlag(Permission.Flag.SWITCH)).isTrue();
        assertThat(townBlock.hasPermissionFlag(Permission.Flag.ITEM_USE)).isTrue();
    }
}
```

### 3. Test Configuration

```java
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("TownBlock Permission Tests")
class TownBlockPermissionTest {

    @Test
    @Order(1)
    @DisplayName("Test basic permissions")
    void testBasicPermissions() {
        // test implementation
    }

    @Test
    @Order(2)
    @DisplayName("Test permission combinations")
    void testPermissionCombinations() {
        // test implementation
    }
}
```

This testing guide provides a comprehensive framework for testing all aspects of your Guilds plugin, from individual model methods to complex command interactions.

## Migration Guide

### From Legacy TestUtilities to New Architecture

#### Before (Legacy):
```java
class OldTest {
    @Test
    void testSomething() {
        TownBlock block = TestUtilities.createTestTownBlock();
        Player player = TestUtilities.createTestPlayerAtChunk(10, 20);
        // Test implementation
    }
}
```

#### After (New Architecture):
```java
class NewTest extends BaseUnitTest {
    @Test
    void testSomething() {
        TownBlock block = TestObjectFactory.createTestTownBlock();
        Player player = TestObjectFactory.createTestPlayerAtChunk(10, 20, null);
        // Test implementation
    }
}
```

### Migration Checklist

1. **Update Test Classes**:
   - Extend `BaseUnitTest` for unit tests
   - Extend `BaseIntegrationTest` for integration tests
   - Remove `@ExtendWith(MockitoExtension.class)` (handled by base class)

2. **Replace Static Calls**:
   - Replace `TestUtilities.createTestTownBlock()` with `TestObjectFactory.createTestTownBlock()`
   - Use `TestDataBuilder.aTownBlock()` for complex configurations

3. **Update Configuration**:
   - Inject `TestConfig` instead of hardcoding values
   - Use configuration methods like `getDefaultTownName()`

4. **Database Tests**:
   - Use `TestDatabaseHelper` for database operations
   - Implement proper cleanup in `@AfterEach`

5. **MockBukkit Setup**:
   - Remove manual MockBukkit setup from tests
   - Use base class infrastructure

### Key Differences

| Feature | Legacy | New |
|---------|--------|-----|
| Test Base | Manual setup | `BaseUnitTest`/`BaseIntegrationTest` |
| Object Creation | Static methods | Factory + Builder pattern |
| Configuration | Hardcoded values | Centralized `TestConfig` |
| Database | Manual cleanup | Automatic with `TestDatabaseHelper` |
| Thread Safety | Not guaranteed | Thread-safe by design |
| Error Handling | Basic | Comprehensive with validation |

## Troubleshooting

### Common Issues

#### Database Connection Issues
```java
// Ensure database is available before running tests
@Test
void databaseTest() {
    assumeTrue(TestDatabaseHelper.isDatabaseAvailable(dataSource));
    // Test implementation
}
```

#### MockBukkit Setup Issues
```java
// Use integration test for MockBukkit tests
class CommandTest extends BaseIntegrationTest {
    // MockBukkit is automatically set up
}
```

#### Test Isolation Issues
```java
// Ensure proper cleanup
@AfterEach
void cleanup() {
    if (testConfig.getIntegration().isDatabaseCleanup()) {
        TestDatabaseHelper.cleanupTestData(connection, testConfig);
    }
}
```

### Getting Help

- Check test logs for configuration issues
- Verify `application-test.properties` is correct
- Use `validateTestConfiguration()` in base classes
- Review migration guide for deprecated patterns

## Future Improvements

Planned enhancements to the testing infrastructure:

1. **Performance Testing**: Automated performance benchmarks
2. **Load Testing**: Concurrent test execution validation
3. **Contract Testing**: API contract validation
4. **Property-Based Testing**: Generative test scenarios
5. **Test Metrics**: Coverage and quality metrics
6. **CI/CD Integration**: Automated test pipeline improvements

---

This refactored testing infrastructure provides a solid foundation for maintaining high-quality tests while following modern development best practices. The combination of base classes, factory patterns, builder patterns, and comprehensive utilities makes testing more efficient and reliable.