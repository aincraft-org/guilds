# Town Chat Feature - Comprehensive Test Plan

## Feature Overview

The town chat feature allows players to send messages to all online residents of their town using `/guilds chat <message>` or `/guilds tc <message>`.

**Implementation Location**: `GuildsGeneralCommand.java:289-341`

**Key Behaviors**:
- Players must be in a town to use chat
- Messages broadcast to all online town residents
- Format: `[TC] PlayerName: message`
- Messages logged to server console
- Permission: `guilds.general.chat`

---

## Test Suite Structure

### 1. Unit Tests (`GuildsGeneralCommandChatTest.java`)

Mock-based tests for command logic isolation

#### Test Cases

**TC-001: Player Not In Town**
- **Setup**: Player not member of any town
- **Execute**: `/guilds chat hello`
- **Assert**:
  - Error message: "You are not in a town!"
  - Help message shown
  - No message broadcast
  - Command returns true

**TC-002: Missing Message Argument**
- **Setup**: Player in town "TestTown"
- **Execute**: `/guilds chat` (no message)
- **Assert**:
  - Error: "Usage: /guilds chat <message>"
  - Help text shown
  - No message sent
  - Command returns true

**TC-003: Single Word Message**
- **Setup**: Player "Alice" in "TestTown", 2 online residents
- **Execute**: `/guilds chat hello`
- **Assert**:
  - Message formatted: `[TC] Alice: hello`
  - All online town residents receive message
  - Console log entry created
  - Command returns true

**TC-004: Multi-Word Message**
- **Setup**: Player "Bob" in "TestTown"
- **Execute**: `/guilds chat hello world test message`
- **Assert**:
  - Message joined correctly: "hello world test message"
  - Proper formatting applied
  - Broadcast to residents

**TC-005: Message With Special Characters**
- **Setup**: Player in town
- **Execute**: `/guilds chat !@#$%^&*() test`
- **Assert**:
  - Special chars preserved
  - No errors thrown
  - Message delivered

**TC-006: TC Alias**
- **Setup**: Player in town
- **Execute**: `/guilds tc quick message`
- **Assert**:
  - Works identically to `/guilds chat`
  - Same formatting
  - Same broadcast behavior

**TC-007: Only Online Residents Receive Messages**
- **Setup**: Town has 5 residents, 2 online, 3 offline
- **Execute**: Chat message sent
- **Assert**:
  - Only 2 online players receive message
  - Offline players not processed
  - No errors

**TC-008: Sender Receives Own Message**
- **Setup**: Player sends chat message
- **Execute**: Chat command
- **Assert**:
  - Sender also receives formatted message
  - Sender counted as resident

**TC-009: Multiple Towns Isolation**
- **Setup**: 2 towns, each with online residents
- **Execute**: Town A resident sends message
- **Assert**:
  - Only Town A residents receive
  - Town B residents don't receive
  - No cross-contamination

**TC-010: ResidentService Exception Handling**
- **Setup**: Mock ResidentService throws SQLException
- **Execute**: Chat command
- **Assert**:
  - Error caught gracefully
  - Error message sent to player
  - Warning logged
  - No crash

**TC-011: Empty Resident List**
- **Setup**: Town exists but has no residents (edge case)
- **Execute**: Chat command
- **Assert**:
  - No NPE thrown
  - Handles empty list
  - Command completes

**TC-012: Console Sender Rejected**
- **Setup**: Console executes command
- **Execute**: `/guilds chat test`
- **Assert**:
  - Error: "Only players can use this command"
  - Command returns true
  - No crash

**TC-013: Message Formatting Validation**
- **Setup**: Any valid scenario
- **Execute**: Chat command
- **Assert**:
  - ChatColor.AQUA for [TC] tag
  - ChatColor.WHITE for player name
  - ChatColor.GRAY for colon separator
  - ChatColor.RESET for message

**TC-014: Logger Called Correctly**
- **Setup**: Valid chat scenario
- **Execute**: Chat command
- **Assert**:
  - Logger.info called with format: "Town Chat [TownName] PlayerName: message"
  - Log level is INFO

**TC-015: Very Long Messages**
- **Setup**: Player in town
- **Execute**: Message with 500+ characters
- **Assert**:
  - No truncation (or validate Minecraft limits)
  - Full message sent
  - No errors

---

### 2. Integration Tests (`GuildsGeneralCommandChatIntegrationTest.java`)

Full stack tests with MockBukkit

#### Test Cases

**IT-001: End-to-End Message Flow**
- **Setup**: MockBukkit server, 3 online players in same town
- **Execute**: Player1 sends chat
- **Assert**:
  - All 3 players receive message
  - Message appears in chat history
  - Format preserved

**IT-002: Tab Completion**
- **Setup**: Player typing command
- **Execute**: Tab complete `/guilds` with partial input
- **Assert**:
  - "chat" appears in completions
  - "tc" appears in completions
  - Sorted correctly

**IT-003: Permission Check**
- **Setup**: Player without `guilds.general.chat` permission
- **Execute**: Chat command
- **Assert**:
  - Permission denied (if implemented)
  - Or allow based on `guilds.general.*`

**IT-004: Real Resident Service Integration**
- **Setup**: Real database, actual resident records
- **Execute**: Chat command
- **Assert**:
  - Queries execute correctly
  - Residents fetched from DB
  - No mock isolation issues

**IT-005: Concurrent Messages**
- **Setup**: Multiple players send messages simultaneously
- **Execute**: 5 concurrent chat commands
- **Assert**:
  - No race conditions
  - All messages delivered
  - No message loss

**IT-006: Player Join/Leave During Chat**
- **Setup**: Chat in progress, player joins/leaves town
- **Execute**: Sequential operations
- **Assert**:
  - No ConcurrentModificationException
  - Consistent state

---

### 3. Edge Cases & Error Scenarios

**EC-001: Null Player Name**
- Player with null name (shouldn't happen but defensive)

**EC-002: Town Name With Special Characters**
- Town named "Test & Co." sends chat

**EC-003: Database Connection Lost**
- DB goes offline mid-query

**EC-004: Bukkit.getOnlinePlayers() Returns Empty**
- Server with no online players

**EC-005: Resident UUID Mismatch**
- Resident in DB doesn't match online player UUID

---

### 4. Performance Tests (Optional)

**PT-001: Large Town (100+ residents)**
- Measure broadcast time
- Ensure < 100ms

**PT-002: High Message Frequency**
- 50 messages/second stress test
- No memory leaks

---

## Test Data Requirements

### Mock Data
```java
// Player mocks
Player mockSender = mock(Player.class);
when(mockSender.getName()).thenReturn("TestPlayer");
when(mockSender.getUniqueId()).thenReturn(UUID.randomUUID());

// Resident data
List<Resident> residents = Arrays.asList(
    createResident("Player1", town),
    createResident("Player2", town),
    createResident("Player3", town)
);

// Online players
ServerMock server = MockBukkit.mock();
PlayerMock p1 = server.addPlayer("Player1");
PlayerMock p2 = server.addPlayer("Player2");
```

### Database Setup
```sql
INSERT INTO towns (id, name, mayor_uuid) VALUES ('town-1', 'TestTown', 'uuid-1');
INSERT INTO residents (uuid, name, town_name) VALUES
    ('uuid-1', 'Player1', 'TestTown'),
    ('uuid-2', 'Player2', 'TestTown'),
    ('uuid-3', 'Player3', 'TestTown');
```

---

## Coverage Goals

- **Line Coverage**: 100% of handleTownChat() and sendTownChatMessage()
- **Branch Coverage**: All conditionals (null checks, arg length, try/catch)
- **Method Coverage**: Both public command entry and private helper

---

## Mocking Strategy

### Services to Mock
```java
@Mock ResidentService residentService;
@Mock TownService townService;
@Mock PlotService plotService;
@Mock PermissionService permissionService;
@Mock MapCommand mapCommand;
@Mock Logger logger;
```

### Behaviors to Stub
```java
// Happy path
when(residentService.getResidentsInTown("TestTown"))
    .thenReturn(residents);

// Player in town
when(townService.getTownByResident(playerUuid))
    .thenReturn(Optional.of(town));

// Error case
when(residentService.getResidentsInTown(any()))
    .thenThrow(new SQLException("DB Error"));
```

---

## Test Implementation Notes

1. **Use BaseUnitTest** for unit tests (faster, isolated)
2. **Use BaseIntegrationTest** for integration tests (real Bukkit environment)
3. **Follow TestingGuide.md** patterns
4. **Use TestDataBuilder** for complex object creation
5. **Verify message content** with ArgumentCaptor for sendMessage calls
6. **Mock Bukkit.getOnlinePlayers()** carefully (static method)

---

## Assertions Checklist

For each test, verify:
- [ ] Correct return value (true/false)
- [ ] Player messages sent (verify + count)
- [ ] Error messages formatted correctly
- [ ] Logger called with proper format
- [ ] No exceptions thrown
- [ ] Service methods called expected times
- [ ] Broadcast only to correct recipients

---

## Risk Areas

1. **Static Bukkit.getOnlinePlayers()** - Requires PowerMock or MockBukkit
2. **Message formatting** - Color codes, special chars
3. **Concurrent modification** - Iterating online players during join/leave
4. **ResidentService exceptions** - Database failures
5. **Null safety** - Null checks on all inputs

---

## Review Criteria

Before considering tests complete:
- [ ] All TC-### cases pass
- [ ] All IT-### cases pass
- [ ] Edge cases handled
- [ ] 100% method coverage on chat methods
- [ ] No test warnings/errors
- [ ] Tests run in < 5 seconds total
- [ ] CI/CD pipeline passes
- [ ] Code review approved
