# UUID Guild ID Refactoring - Complete Implementation Guide

## Overview
This document provides step-by-step instructions for completing the Guild ID refactoring from String to UUID across the entire codebase.

## Already Completed (Foundation)
✅ Guild.java - UUID id with factory methods `create()` and `restore()`
✅ GuildRepository interface - Updated to use UUID parameters
✅ JdbcGuildRepository - Updated save/delete/findById
✅ GuildService.createGuild() and deleteGuild() - Updated to use Guild.create() and UUID
✅ GuildService.joinGuild() - Updated to use UUID guildId

## Phase 1: Repository Interfaces (String → UUID for guildId)

### Files to Update:
1. **GuildMemberRepository.java**
   - Change: `addMember(String guildId, ...)` → `addMember(UUID guildId, ...)`
   - Change: `removeMember(String guildId, ...)` → `removeMember(UUID guildId, ...)`
   - Change: `removeAllMembers(String guildId)` → `removeAllMembers(UUID guildId)`
   - Change: `getMemberPermissions(String guildId, ...)` → `getMemberPermissions(UUID guildId, ...)`
   - Change: `updatePermissions(String guildId, ...)` → `updatePermissions(UUID guildId, ...)`
   - All other methods with String guildId

2. **GuildRoleRepository.java**
   - Change all methods accepting `String guildId` to `UUID guildId`

3. **ChunkClaimRepository.java**
   - Change: `claim(String guildId, ...)` → `claim(UUID guildId, ...)`
   - Change: `unclaim(String guildId, ...)` → `unclaim(UUID guildId, ...)`
   - Change: `unclaimAll(String guildId)` → `unclaimAll(UUID guildId)`
   - All other methods

4. **GuildRelationshipRepository.java**
   - Change all methods accepting `String guildId` to `UUID guildId`

5. **InviteRepository.java**
   - Change: `findByGuildId(String guildId)` → `findByGuildId(UUID guildId)`
   - Change: `findActiveInvite(String guildId, ...)` → `findActiveInvite(UUID guildId, ...)`
   - Change: `countPendingInvites(String guildId)` → `countPendingInvites(UUID guildId)`
   - Change: `deleteByGuildId(String guildId)` → `deleteByGuildId(UUID guildId)`

6. **MemberRoleRepository.java**
   - Change all methods accepting `String guildId` to `UUID guildId`

7. **GuildSkillTreeRepository.java**
   - Change: `findByGuildId(String guildId)` → `findByGuildId(UUID guildId)`
   - Change: `delete(String guildId)` → `delete(UUID guildId)`
   - Change: `unlockSkill(String guildId, ...)` → `unlockSkill(UUID guildId, ...)`
   - All other methods

8. **GuildProjectRepository.java** & **GuildProjectPoolRepository.java**
   - Change all methods accepting `String guildId` to `UUID guildId`

9. **Other Repositories** (ChunkClaimLogRepository, VaultRepository, etc.)
   - Change all methods accepting `String guildId` to `UUID guildId`

### Pattern for Updates:
```java
// BEFORE
public interface XyzRepository {
    void someMethod(String guildId, ...);
}

// AFTER
public interface XyzRepository {
    void someMethod(UUID guildId, ...);
}
```

## Phase 2: JDBC Implementations

For each JDBC implementation, follow this pattern:

### Pattern: Update Parameter Binding
```java
// BEFORE
ps.setString(1, guildId);

// AFTER
ps.setString(1, guildId.toString());
```

### Pattern: Update Parameter Reading
```java
// BEFORE
String guildId = rs.getString("guild_id");

// AFTER
UUID guildId = UUID.fromString(rs.getString("guild_id"));
```

### Specific Files to Update (30+):
- JdbcGuildMemberRepository
- JdbcGuildRoleRepository
- JdbcChunkClaimRepository
- JdbcGuildRelationshipRepository
- JdbcInviteRepository
- JdbcMemberRoleRepository
- JdbcGuildSkillTreeRepository
- JdbcGuildProgressionRepository
- JdbcGuildProjectRepository
- JdbcGuildProjectPoolRepository
- JdbcChunkClaimLogRepository
- JdbcSubregionRepository
- JdbcVaultRepository
- JdbcMemberRegionRoleRepository
- All other JDBC repo implementations

### General JDBC Update Steps:
1. Update method signature: `String guildId` → `UUID guildId`
2. Change all `ps.setString(index, guildId)` to `ps.setString(index, guildId.toString())`
3. Change all `rs.getString("guild_id")` to `UUID.fromString(rs.getString("guild_id"))`
4. Add try-catch for `IllegalArgumentException` when parsing UUID strings from DB
5. Update variable declarations: `String guildId = ...` → `UUID guildId = ...`

## Phase 3: Service Classes

Update all methods accepting `String guildId`:

### Files:
- GuildService.java (already partially done)
- GuildLifecycleService.java
- GuildRoleService.java
- PermissionService.java
- TerritoryService.java
- SpawnService.java
- GuildMemberService.java
- SubregionService.java
- SkillTreeService.java
- VaultService.java
- GuildChatListener.java
- RelationshipService.java
- And all other service classes

### Pattern:
```java
// BEFORE
public void someMethod(String guildId, ...) {
    repository.doSomething(guildId);
}

// AFTER
public void someMethod(UUID guildId, ...) {
    repository.doSomething(guildId);
}
```

## Phase 4: Command Components

These should mostly work as-is since they only call `guild.getId()` which now returns UUID.

### Files to Verify/Update:
- InfoComponent.java
- AllyComponent.java
- RoleComponent.java
- AdminComponent.java
- All other components

**These typically just need verification** - the UUID will flow through naturally.

## Phase 5: Test Files

### Files to Update:
- InMemoryGuildRepository.java
- All test classes using Guild or repositories

### Pattern:
```java
// BEFORE
public Optional<Guild> findById(String guildId) {
    return storage.get(guildId);
}

// AFTER
public Optional<Guild> findById(UUID guildId) {
    return storage.get(guildId);
}
```

## Database Notes

No database schema changes needed:
- guild.id columns already VARCHAR(36)
- Store UUIDs as strings using `.toString()`
- Parse from database using `UUID.fromString()`

## Compilation & Testing

### Step 1: Update Phase 1 (Interfaces)
Run: `gradle build` - Should show errors pointing to Phase 2

### Step 2: Update Phase 2 (JDBC Implementations)
Run: `gradle build` - Should show errors pointing to Phase 3

### Step 3: Update Phase 3 (Services)
Run: `gradle build` - Should show errors in components/tests

### Step 4: Update Phase 4 (Components)
Run: `gradle build` - Should show errors in tests

### Step 5: Update Phase 5 (Tests)
Run: `gradle test` - Should pass

## Quick Reference: Method Signature Changes

```java
// GuildService patterns
public boolean joinGuild(UUID guildId, UUID playerId)
public boolean leaveGuild(UUID guildId, UUID playerId)
public Guild getGuildById(UUID guildId)
public void unclaimAllChunks(UUID guildId, UUID requesterId)

// Repository patterns
void delete(UUID guildId)
Optional<Guild> findById(UUID guildId)
Optional<GuildMember> getMemberPermissions(UUID guildId, UUID playerId)
void addMember(UUID guildId, UUID playerId, MemberPermissions perms)

// JDBC patterns
ps.setString(index, guildId.toString())
UUID id = UUID.fromString(rs.getString("id"))
```

## Risk Mitigation

- Test incrementally after each phase
- Run `gradle build` frequently to catch errors early
- Keep git branch for reference of old code
- Use IDE refactor "Change Signature" when available
- Consider creating wrapper methods temporarily for backwards compatibility if needed

## Estimated Effort

- Phase 1 (Interfaces): 30 min - 8 files
- Phase 2 (JDBC): 60 min - 30 files with repetitive patterns
- Phase 3 (Services): 45 min - 15 files
- Phase 4 (Components): 15 min - verification mainly
- Phase 5 (Tests): 30 min - 8 files
- Total: ~3 hours

## Success Criteria

- [x] Guild.getId() returns UUID
- [ ] All Repository interfaces accept UUID guildId
- [ ] All JDBC implementations handle UUID conversions
- [ ] All Service methods accept UUID guildId
- [ ] All tests pass with gradle test
- [ ] No compilation errors
- [ ] No runtime type mismatches

## Notes

- UUID comparisons use `.equals()` not `==`
- UUID is immutable and final
- UUID has good equals/hashCode implementation
- UUID serialization via `.toString()` is standard
