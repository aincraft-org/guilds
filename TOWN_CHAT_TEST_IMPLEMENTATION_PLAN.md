# Town Chat Test Implementation Plan

**Agent Task**: Implement comprehensive test suite for town chat feature

---

## Prerequisites

1. Read `TOWN_CHAT_TEST_PLAN.md` for test case specifications
2. Read `src/test/java/org/aincraft/towny/TestingGuide.md` for testing patterns
3. Review existing test: `src/test/java/org/aincraft/towny/commands/PlotCommandTest.java`
4. Understand target implementation: `TownyGeneralCommand.java:289-341`

---

## Implementation Steps

### Phase 1: Setup Test Infrastructure

**File**: `src/test/java/org/aincraft/towny/commands/TownyGeneralCommandChatTest.java`

```java
package org.aincraft.towny.commands;

import org.aincraft.towny.base.BaseUnitTest;
import org.aincraft.towny.services.*;
import org.aincraft.towny.models.*;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.*;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Town Chat Command - Unit Tests")
class TownyGeneralCommandChatTest extends BaseUnitTest {

    @Mock private TownyPlugin plugin;
    @Mock private ResidentService residentService;
    @Mock private TownService townService;
    @Mock private PlotService plotService;
    @Mock private PermissionService permissionService;
    @Mock private MapCommand mapCommand;
    @Mock private Logger logger;
    @Mock private Command command;

    private TownyGeneralCommand townyCommand;
    private Player mockPlayer;
    private UUID playerUuid;
    private List<Resident> townResidents;

    @BeforeEach
    void setUp() {
        // Initialize mocks
        playerUuid = UUID.randomUUID();
        mockPlayer = mock(Player.class);
        when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        when(mockPlayer.getName()).thenReturn("TestPlayer");

        // Mock plugin logger
        when(plugin.getLogger()).thenReturn(logger);

        // Create command instance
        townyCommand = new TownyGeneralCommand(
            plugin, residentService, townService,
            plotService, permissionService, mapCommand
        );

        // Setup default town residents
        townResidents = createTestResidents();
    }

    private List<Resident> createTestResidents() {
        // Helper to create test data
        List<Resident> residents = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Resident r = new Resident();
            r.setUuid(UUID.randomUUID());
            r.setName("Player" + i);
            r.setTownName("TestTown");
            residents.add(r);
        }
        return residents;
    }

    // Tests go here...
}
```

**Actions**:
1. Create test file in correct package
2. Extend BaseUnitTest
3. Setup all required mocks
4. Create helper methods for test data generation
5. Implement setUp() with common mock behaviors

---

### Phase 2: Implement Core Unit Tests (TC-001 to TC-015)

**For each test case in TOWN_CHAT_TEST_PLAN.md:**

#### Example: TC-001 - Player Not In Town

```java
@Test
@DisplayName("TC-001: Should reject chat when player not in town")
void shouldRejectChatWhenPlayerNotInTown() {
    // Given - player not in any town
    when(townService.getTownByResident(playerUuid))
        .thenReturn(Optional.empty());

    String[] args = {"chat", "hello"};

    // When
    boolean result = townyCommand.onCommand(mockPlayer, command, "towny", args);

    // Then
    assertThat(result).isTrue();
    verify(mockPlayer).sendMessage(contains("You are not in a town!"));
    verify(mockPlayer).sendMessage(contains("/town create"));
    verify(residentService, never()).getResidentsInTown(any());
}
```

#### Example: TC-003 - Single Word Message

```java
@Test
@DisplayName("TC-003: Should send single word message to all town residents")
void shouldSendSingleWordMessageToTownResidents() {
    // Given - player in town
    Town testTown = new Town("TestTown", UUID.randomUUID());
    when(townService.getTownByResident(playerUuid))
        .thenReturn(Optional.of(testTown));
    when(residentService.getResidentsInTown("TestTown"))
        .thenReturn(townResidents);

    // Mock online players
    PlayerMock player1 = createOnlinePlayer(townResidents.get(0).getUuid());
    PlayerMock player2 = createOnlinePlayer(townResidents.get(1).getUuid());
    mockBukkit.setOnlinePlayers(player1, player2);

    String[] args = {"chat", "hello"};

    // When
    boolean result = townyCommand.onCommand(mockPlayer, command, "towny", args);

    // Then
    assertThat(result).isTrue();

    // Verify message format
    ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
    verify(player1).sendMessage(messageCaptor.capture());

    String sentMessage = messageCaptor.getValue();
    assertThat(sentMessage).contains("[TC]");
    assertThat(sentMessage).contains("TestPlayer");
    assertThat(sentMessage).contains("hello");
    assertThat(sentMessage).startsWith(ChatColor.AQUA + "[TC]");

    // Verify logger called
    verify(logger).info(contains("Town Chat [TestTown] TestPlayer: hello"));
}
```

**Implementation Checklist**:
- [ ] TC-001: Player not in town
- [ ] TC-002: Missing message argument
- [ ] TC-003: Single word message
- [ ] TC-004: Multi-word message
- [ ] TC-005: Special characters
- [ ] TC-006: TC alias works
- [ ] TC-007: Only online residents receive
- [ ] TC-008: Sender receives own message
- [ ] TC-009: Multiple towns isolation
- [ ] TC-010: ResidentService exception
- [ ] TC-011: Empty resident list
- [ ] TC-012: Console sender rejected
- [ ] TC-013: Message formatting validation
- [ ] TC-014: Logger called correctly
- [ ] TC-015: Very long messages

---

### Phase 3: Integration Tests

**File**: `src/test/java/org/aincraft/towny/commands/TownyGeneralCommandChatIntegrationTest.java`

```java
package org.aincraft.towny.commands;

import org.aincraft.towny.base.BaseIntegrationTest;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Town Chat Command - Integration Tests")
class TownyGeneralCommandChatIntegrationTest extends BaseIntegrationTest {

    private TownyGeneralCommand townyCommand;
    private PlayerMock player1, player2, player3;

    @BeforeEach
    void setUp() {
        // Use real services from BaseIntegrationTest
        townyCommand = injector.getInstance(TownyGeneralCommand.class);

        // Create test town and residents
        createTestTownWithResidents();
    }

    private void createTestTownWithResidents() {
        // Setup database with real data
        // Create town, add residents, etc.
    }

    // IT-001 through IT-006 tests here...
}
```

**Implementation Checklist**:
- [ ] IT-001: End-to-end message flow
- [ ] IT-002: Tab completion
- [ ] IT-003: Permission check
- [ ] IT-004: Real resident service
- [ ] IT-005: Concurrent messages (optional)
- [ ] IT-006: Player join/leave (optional)

---

### Phase 4: Edge Cases

Add edge case tests to unit test file:

```java
@Nested
@DisplayName("Edge Cases")
class EdgeCaseTests {

    @Test
    @DisplayName("EC-001: Handle null player name gracefully")
    void shouldHandleNullPlayerName() {
        when(mockPlayer.getName()).thenReturn(null);
        // Test continues...
    }

    // EC-002 through EC-005...
}
```

**Implementation Checklist**:
- [ ] EC-001: Null player name
- [ ] EC-002: Town name special chars
- [ ] EC-003: Database connection lost
- [ ] EC-004: Empty online players
- [ ] EC-005: UUID mismatch

---

### Phase 5: Test Utilities

**Create helper file**: `src/test/java/org/aincraft/towny/commands/ChatTestHelpers.java`

```java
package org.aincraft.towny.commands;

import org.aincraft.towny.models.Resident;
import org.bukkit.entity.Player;
import java.util.*;

public class ChatTestHelpers {

    public static List<Resident> createResidents(int count, String townName) {
        List<Resident> residents = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Resident r = new Resident();
            r.setUuid(UUID.randomUUID());
            r.setName("Player" + i);
            r.setTownName(townName);
            residents.add(r);
        }
        return residents;
    }

    public static void assertChatMessageFormat(String message,
                                               String expectedPlayer,
                                               String expectedText) {
        assert message.contains("[TC]") : "Missing [TC] tag";
        assert message.contains(expectedPlayer) : "Missing player name";
        assert message.contains(expectedText) : "Missing message text";
        // Color code validation...
    }

    public static Player mockOnlinePlayer(UUID uuid, String name) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(uuid);
        when(p.getName()).thenReturn(name);
        return p;
    }
}
```

---

### Phase 6: Verification & Cleanup

**Final checks**:

1. **Run all tests**:
   ```bash
   ./gradlew test --tests "*TownyGeneralCommandChat*"
   ```

2. **Verify coverage**:
   ```bash
   ./gradlew jacocoTestReport
   ```
   - Check `handleTownChat()` is 100% covered
   - Check `sendTownChatMessage()` is 100% covered

3. **Check test output**:
   - All tests pass ✓
   - No warnings or deprecation notices
   - Execution time < 5 seconds

4. **Code quality**:
   - No @Disabled tests
   - All @DisplayName annotations present
   - Proper @Nested grouping used
   - Comments explain complex setups

5. **Documentation**:
   - Add javadoc to test class
   - Reference TOWN_CHAT_TEST_PLAN.md in class comment
   - Note any deviations from plan

---

## Mock Bukkit Considerations

### Mocking Static Methods

Since `Bukkit.getOnlinePlayers()` is static, you need MockBukkit's ServerMock:

```java
// In BaseIntegrationTest (already available)
ServerMock server = MockBukkit.getServer();
PlayerMock p1 = server.addPlayer("Player1");
PlayerMock p2 = server.addPlayer("Player2");

// In unit tests, you may need to use PowerMock or refactor
// Recommendation: Extract Bukkit.getOnlinePlayers() to injectable service
```

### Alternative Approach (Refactoring)

If mocking static methods is too complex, consider refactoring:

```java
// Create PlayerRegistry service
public interface PlayerRegistry {
    Collection<? extends Player> getOnlinePlayers();
}

// Bukkit implementation
public class BukkitPlayerRegistry implements PlayerRegistry {
    public Collection<? extends Player> getOnlinePlayers() {
        return Bukkit.getOnlinePlayers();
    }
}

// Inject into TownyGeneralCommand
// Then tests can mock PlayerRegistry instead
```

**Note**: Check with user if refactoring is acceptable or if tests should work with existing code.

---

## File Structure

```
src/test/java/org/aincraft/towny/
├── commands/
│   ├── TownyGeneralCommandChatTest.java          (Unit tests)
│   ├── TownyGeneralCommandChatIntegrationTest.java (Integration tests)
│   └── ChatTestHelpers.java                      (Test utilities)
└── TOWN_CHAT_TEST_PLAN.md                        (Reference doc)
```

---

## Acceptance Criteria

Tests are complete when:

✅ All 15 unit test cases (TC-001 to TC-015) implemented and passing
✅ All 4-6 integration tests (IT-001 to IT-006) implemented and passing
✅ All 5 edge cases (EC-001 to EC-005) implemented and passing
✅ 100% line coverage on chat methods
✅ 100% branch coverage on conditionals
✅ No flaky tests (run 5 times successfully)
✅ Tests complete in < 5 seconds
✅ No Mockito warnings
✅ Code follows TestingGuide.md patterns
✅ Proper assertions using AssertJ
✅ All tests have @DisplayName annotations

---

## Common Pitfalls to Avoid

1. **Don't use String.contains() for ChatColor validation** - Color codes may not display in assertions
2. **Don't assume Bukkit.getOnlinePlayers() is easily mockable** - Use MockBukkit or refactor
3. **Don't forget ArgumentCaptor for sendMessage verification** - Direct string matching fails with colors
4. **Don't skip cleanup in @AfterEach** - Prevents test pollution
5. **Don't mock what BaseUnitTest/BaseIntegrationTest already provides** - Check parent classes first

---

## Success Metrics

- **Test Count**: 25+ tests (15 unit + 6 integration + 5 edge)
- **Coverage**: 100% of chat methods
- **Execution Time**: < 5 seconds
- **Failure Rate**: 0%
- **Code Quality**: No Sonar violations

---

## Agent Execution Instructions

1. **Start**: Read both plan files completely
2. **Create**: Test file structure as specified
3. **Implement**: Tests in order (Phase 1 → Phase 6)
4. **Verify**: Run after each phase
5. **Report**: Coverage metrics and any blockers
6. **Complete**: All acceptance criteria met
