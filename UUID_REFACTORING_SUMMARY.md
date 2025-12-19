# Guild ID UUID Refactoring - Implementation Summary

## Current Status: FOUNDATION COMPLETE - READY FOR SYSTEMATIC ROLLOUT

### Completed Changes

#### 1. Guild.java (COMPLETE) ✅
**Location:** `src/main/java/org/aincraft/Guild.java`

**Changes Made:**
- Changed `private final String id` to `private final UUID id` (Line 16)
- Made constructor private with boolean `isNew` parameter (Line 51)
- Removed two public constructors
- Added `create(String name, String description, UUID ownerId): Optional<Guild>` factory method
  - Validates input (no null/empty name, no null ownerId)
  - Returns Optional.empty() on validation failure
  - Generates new UUID and current timestamp
  - Adds owner to members
- Added `restore(UUID id, String name, String description, UUID ownerId, long createdAt, int maxMembers, String color): Optional<Guild>` factory method
  - Used for database restoration
  - Validates input (no null values, maxMembers >= 1)
  - Returns Optional.empty() on validation failure
  - Does NOT add owner to members (assumes loaded from DB)
- Updated `getId()` return type from String to UUID (Line 278)
- Updated `equals()` to use `id.equals()` instead of `Objects.equals()` (Line 394)
- Updated `hashCode()` to use `id.hashCode()` directly (Line 404)

**Key Implementation Details:**
- No exceptions thrown from factory methods - validation returns Optional.empty()
- Exception handling wraps UUID parsing in try-catch
- Database schema unchanged (VARCHAR(36) stores UUID.toString())

---

#### 2. GuildRepository Interface (COMPLETE) ✅
**Location:** `src/main/java/org/aincraft/storage/GuildRepository.java`

**Changes Made:**
- Added `import java.util.UUID`
- Updated method signatures:
  - `delete(String guildId)` → `delete(UUID guildId)` (Line 14)
  - `findById(String guildId)` → `findById(UUID guildId)` (Line 15)

---

#### 3. JdbcGuildRepository (COMPLETE) ✅
**Location:** `src/main/java/org/aincraft/database/repository/JdbcGuildRepository.java`

**Changes Made:**
- Updated `save()` method:
  - `ps.setString(1, guild.getId())` → `ps.setString(1, guild.getId().toString())` (Line 48)

- Updated `delete()` method:
  - Signature: `delete(String guildId)` → `delete(UUID guildId)` (Line 79)
  - `ps.setString(1, guildId)` → `ps.setString(1, guildId.toString())` (Line 84)

- Updated `findById()` method:
  - Signature: `findById(String guildId)` → `findById(UUID guildId)` (Line 92)
  - `ps.setString(1, guildId)` → `ps.setString(1, guildId.toString())` (Line 97)

- Updated `mapRowToGuild()` method (Lines 147-179):
  - Parse UUID from database: `UUID id = UUID.fromString(idStr)`
  - Use new `Guild.restore()` factory method with validation
  - Handle Optional.empty() case
  - Added try-catch for `IllegalArgumentException` when parsing UUID

**Error Handling:**
- Throws SQLException if Guild.restore() returns empty Optional
- Throws SQLException if UUID parsing fails with detailed message

---

#### 4. GuildService Updates (PARTIAL) ✅
**Location:** `src/main/java/org/aincraft/GuildService.java`

**Changes Made:**
- `createGuild()` method (Lines 87-92):
  - Changed from `new Guild(name, description, ownerId)` to `Guild.create(name, description, ownerId)`
  - Added Optional.empty() check, returns null on validation failure
  - More robust null-safe creation

- `deleteGuild()` method (Line 117):
  - Signature changed: `deleteGuild(String guildId, UUID requesterId)` → `deleteGuild(UUID guildId, UUID requesterId)`
  - Updated to pass UUID to `guildRepository.findById(guildId)`
  - All downstream repository calls now receive UUID

- `joinGuild()` method (Line 153):
  - Signature changed: `joinGuild(String guildId, UUID playerId)` → `joinGuild(UUID guildId, UUID playerId)`
  - All downstream repository calls now receive UUID

---

### Files Modified Summary

| File | Type | Status | Key Changes |
|------|------|--------|------------|
| Guild.java | Core Domain | ✅ COMPLETE | UUID id, factory methods, private constructor |
| GuildRepository.java | Interface | ✅ COMPLETE | Method signatures to UUID |
| JdbcGuildRepository.java | Implementation | ✅ COMPLETE | UUID handling, toString/fromString conversions |
| GuildService.java | Service | ✅ PARTIAL | 3 methods updated, remaining need UUID params |

---

### Remaining Work Required

This refactoring requires systematic updates to ~60 files organized in 5 phases:

#### PHASE 1: Repository Interfaces (8 files)
```
- GuildMemberRepository
- GuildRoleRepository
- ChunkClaimRepository
- GuildRelationshipRepository
- InviteRepository
- MemberRoleRepository
- GuildSkillTreeRepository
- GuildProjectRepository
```

#### PHASE 2: JDBC Implementations (30+ files)
All JDBC repository implementations need UUID handling for:
- Parameter binding: `guildId.toString()`
- Result set reading: `UUID.fromString(rs.getString())`
- Exception handling for invalid UUIDs

#### PHASE 3: Service Classes (15 files)
- GuildService (complete deleteGuild/joinGuild overloads)
- GuildLifecycleService
- GuildRoleService
- PermissionService
- TerritoryService
- SpawnService
- And 9 others

#### PHASE 4: Command Components (20 files)
Most components should work as-is since they pass `guild.getId()` which already returns UUID.

#### PHASE 5: Test Files (8 files)
- InMemoryGuildRepository
- Test implementations
- Mock repositories

---

### Breaking Changes / Compatibility Notes

1. **API Changes:**
   - `Guild.getId()` now returns `UUID` instead of `String`
   - Repository methods now accept `UUID guildId` instead of `String guildId`
   - Service methods will accept `UUID guildId` instead of `String guildId`
   - `Guild.create()` and `Guild.restore()` return `Optional<Guild>` instead of throwing exceptions

2. **Database:**
   - No schema changes (VARCHAR(36) already sized for UUIDs)
   - UUIDs stored as strings via `.toString()`
   - Parsing from DB via `UUID.fromString()`

3. **Null Safety:**
   - Factory methods return Optional instead of throwing exceptions
   - Callers must handle `.isEmpty()` case
   - Validation happens in factory methods, not constructors

4. **Null Comparisons:**
   - UUID uses proper `.equals()` implementation
   - UUID is immutable and thread-safe
   - Can be used as HashMap keys reliably

---

### Compilation & Testing Strategy

**Run after each phase:**
```bash
./gradlew build        # Full compilation
./gradlew compileJava # Faster compile check
```

**After all phases:**
```bash
./gradlew test         # Run all tests
```

---

### Code Quality Improvements

✅ **SOLID Principles Applied:**
- **Single Responsibility**: Guild only manages its data, factories only validate
- **Open/Closed**: Factory methods closed for modification, open for extension
- **Liskov Substitution**: Optional<Guild> maintains contract
- **Interface Segregation**: Repository interface clean and focused
- **Dependency Inversion**: Services depend on Repository abstraction

✅ **Better Null Safety:**
- Optional instead of exceptions
- Validation at factory level
- Clear failure modes (empty Optional)
- No surprise NPEs from constructors

✅ **Improved Testability:**
- Factory methods allow easy mock creation
- Optional makes null cases explicit
- UUID is deterministic and immutable

---

### Estimated Remaining Effort

| Phase | Files | Effort | Complexity |
|-------|-------|--------|-----------|
| 1 | 8 | 30 min | Low |
| 2 | 30 | 90 min | Low (repetitive) |
| 3 | 15 | 45 min | Medium |
| 4 | 20 | 15 min | Low (verification) |
| 5 | 8 | 30 min | Medium |
| **TOTAL** | **~60** | **~3 hours** | **Low-Medium** |

---

### Next Steps

1. Review the completed changes above
2. Follow the Implementation Guide (UUID_REFACTORING_IMPLEMENTATION_GUIDE.md)
3. Update Phase 1 repository interfaces
4. Run `./gradlew build` and fix compilation errors in Phase 2
5. Continue systematically through all phases
6. Run full test suite: `./gradlew test`

---

### Key Patterns to Remember

**Repository Interface Pattern:**
```java
// Before
void delete(String guildId);

// After
void delete(UUID guildId);
```

**JDBC Implementation Pattern:**
```java
// Before
ps.setString(1, guildId);
String id = rs.getString("guild_id");

// After
ps.setString(1, guildId.toString());
UUID id = UUID.fromString(rs.getString("guild_id"));
```

**Service Pattern:**
```java
// Before
public void doSomething(String guildId) { ... }

// After
public void doSomething(UUID guildId) { ... }
```

**Guild Creation Pattern:**
```java
// Before - used constructor
Guild guild = new Guild(name, desc, ownerId);

// After - use factory
Optional<Guild> opt = Guild.create(name, desc, ownerId);
if (opt.isEmpty()) return null;
Guild guild = opt.get();
```

---

### Validation Checklist

After completing refactoring:
- [ ] All Repository interfaces use UUID guildId
- [ ] All JDBC implementations handle UUID conversion
- [ ] All Service classes accept UUID guildId
- [ ] Guild.getId() returns UUID everywhere
- [ ] No compilation errors
- [ ] All tests pass
- [ ] No raw String guild IDs in parameters
- [ ] Database reads/writes still work
- [ ] Command components work correctly
- [ ] No NPEs from constructor calls (all now use factories)

