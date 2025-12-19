# UUID Guild ID Refactoring - Complete Checklist

## Statistics
- **Total String guildId occurrences remaining: 376**
- **Files already updated: 4**
- **Files remaining to update: ~56**

---

## PHASE 1: Repository Interfaces (Priority: CRITICAL)
These are the blocking changes that will cascade to all implementations.

### [ ] 1.1 GuildMemberRepository.java
**File:** `src/main/java/org/aincraft/storage/GuildMemberRepository.java`
```java
// Update all methods with String guildId parameter
- addMember(String guildId, UUID playerId, MemberPermissions permissions)
- removeMember(String guildId, UUID playerId)
- removeAllMembers(String guildId)
- getMemberPermissions(String guildId, UUID playerId)
- updatePermissions(String guildId, UUID playerId, MemberPermissions permissions)
- getMemberJoinDate(String guildId, UUID playerId)
- And any others
```

### [ ] 1.2 GuildRoleRepository.java
**File:** `src/main/java/org/aincraft/storage/GuildRoleRepository.java`
```java
// Update all methods with String guildId
- save(GuildRole role) - role.guildId needs to accept UUID
- findByGuildAndName(String guildId, String name)
- deleteAllByGuild(String guildId)
- findByGuild(String guildId)
```

### [ ] 1.3 MemberRoleRepository.java
**File:** `src/main/java/org/aincraft/storage/MemberRoleRepository.java`
```java
// Update all String guildId parameters to UUID
- assignRole(String guildId, UUID playerId, String roleId)
- removeAllMemberRoles(String guildId, UUID playerId)
- removeAllByGuild(String guildId)
```

### [ ] 1.4 ChunkClaimRepository.java
**File:** `src/main/java/org/aincraft/storage/ChunkClaimRepository.java`
```java
// Update all String guildId to UUID
- claim(String guildId, ChunkKey chunk)
- unclaim(String guildId, ChunkKey chunk)
- unclaimAll(String guildId)
- getGuildClaims(String guildId)
- isClaimedBy(ChunkKey chunk, String guildId)
```

### [ ] 1.5 GuildRelationshipRepository.java
**File:** `src/main/java/org/aincraft/storage/GuildRelationshipRepository.java`
```java
// Update all String guildId to UUID
- save(GuildRelationship relationship)
- delete(String relationshipId)
- findByGuild(String guildId)
- findByGuilds(String guildId1, String guildId2)
- deleteAllByGuild(String guildId)
```

### [ ] 1.6 InviteRepository.java
**File:** `src/main/java/org/aincraft/storage/InviteRepository.java`
```java
// Update all String guildId to UUID
- save(GuildInvite invite)
- findByGuildId(String guildId)
- findActiveInvite(String guildId, UUID inviteeId)
- countPendingInvites(String guildId)
- deleteByGuildId(String guildId)
```

### [ ] 1.7 GuildSkillTreeRepository.java
**File:** `src/main/java/org/aincraft/skilltree/storage/GuildSkillTreeRepository.java`
```java
// Update all String guildId to UUID
- save(GuildSkillTree skillTree)
- findByGuildId(String guildId)
- delete(String guildId)
- unlockSkill(String guildId, String skillId)
- getUnlockedSkills(String guildId)
- deleteAllSkills(String guildId)
```

### [ ] 1.8 Other Repository Interfaces
Check and update any others with String guildId parameters:
- GuildProjectRepository
- GuildProjectPoolRepository
- ChunkClaimLogRepository
- VaultRepository
- VaultTransactionRepository
- And any others identified by grep

---

## PHASE 2: JDBC Repository Implementations (Priority: HIGH)
Update all implementations to handle UUID conversions.

### Conversion Patterns Required

**For Parameter Binding:**
```java
// BEFORE
ps.setString(index, guildId);

// AFTER
ps.setString(index, guildId.toString());
```

**For Result Reading:**
```java
// BEFORE
String guildId = rs.getString("guild_id");

// AFTER
UUID guildId = UUID.fromString(rs.getString("guild_id"));
// WITH ERROR HANDLING:
try {
    UUID guildId = UUID.fromString(rs.getString("guild_id"));
} catch (IllegalArgumentException e) {
    throw new SQLException("Invalid UUID format for guild_id", e);
}
```

### [ ] 2.1 JdbcGuildMemberRepository.java
```java
// Update method signatures and UUID conversions
// Check save(), delete(), findByGuild(), getGuildMembers(), addMember(), etc.
```

### [ ] 2.2 JdbcGuildRoleRepository.java
```java
// Update all UUID conversions and method signatures
```

### [ ] 2.3 JdbcMemberRoleRepository.java
```java
// Update all UUID conversions
```

### [ ] 2.4 JdbcChunkClaimRepository.java
```java
// Update for UUID guildId
// Key methods: claim(), unclaim(), unclaimAll(), getGuildClaims(), isClaimedBy()
```

### [ ] 2.5 JdbcGuildRelationshipRepository.java
```java
// Update for UUID guildId in all methods
```

### [ ] 2.6 JdbcInviteRepository.java
```java
// Update for UUID guildId
// Key methods: findByGuildId(), findActiveInvite(), countPendingInvites(), deleteByGuildId()
```

### [ ] 2.7 JdbcGuildSkillTreeRepository.java
```java
// Update for UUID guildId
// Key methods: findByGuildId(), delete(), unlockSkill(), getUnlockedSkills()
```

### [ ] 2.8 JdbcGuildProjectRepository.java
```java
// Update for UUID guildId if used
```

### [ ] 2.9 JdbcGuildProjectPoolRepository.java
```java
// Update for UUID guildId if used
// Key: setGuildCreatedAt(UUID guildId, long timestamp)
```

### [ ] 2.10 JdbcGuildProgressionRepository.java
```java
// Update for UUID guildId
```

### [ ] 2.11 JdbcChunkClaimLogRepository.java
```java
// Update for UUID guildId
// Key: findByGuildId(), save() with guild_id parameter
```

### [ ] 2.12 JdbcSubregionRepository.java
```java
// Update for UUID guildId
```

### [ ] 2.13 JdbcVaultRepository.java
```java
// Update for UUID guildId
// Key: findByGuildId(), save() with guild_id
```

### [ ] 2.14 JdbcVaultTransactionRepository.java
```java
// Update for UUID guildId
```

### [ ] 2.15 Additional JDBC Files (As Identified)
- JdbcMemberRegionRoleRepository
- JdbcRegionPermissionRepository
- JdbcRegionTypeLimitRepository
- JdbcRegionRoleRepository
- JdbcLLMProjectTextRepository
- JdbcGuildDefaultPermissionsRepository
- JdbcActiveBuffRepository
- JdbcProgressionLogRepository
- (And any others with String guildId)

---

## PHASE 3: Service Classes (Priority: HIGH)

### [ ] 3.1 GuildService.java (PARTIAL - COMPLETE REMAINING)
**Remaining methods to update:**
```java
- leaveGuild(String guildId, ...) → leaveGuild(UUID guildId, ...)
- getGuildById(String guildId) → getGuildById(UUID guildId)
- getGuildMembers(String guildId) → getGuildMembers(UUID guildId)
- getGuildRoles(String guildId) → getGuildRoles(UUID guildId)
- updateGuildDescription(String guildId, ...) → updateGuildDescription(UUID guildId, ...)
- updateGuildName(String guildId, ...) → updateGuildName(UUID guildId, ...)
- setSpawn(String guildId, ...) → setSpawn(UUID guildId, ...)
- getSpawn(String guildId) → getSpawn(UUID guildId)
- clearSpawn(String guildId) → clearSpawn(UUID guildId)
- toggleExplosions(String guildId) → toggleExplosions(UUID guildId)
- toggleFire(String guildId) → toggleFire(UUID guildId)
- togglePublic(String guildId) → togglePublic(UUID guildId)
- And any others
```

### [ ] 3.2 GuildLifecycleService.java
```java
// Update createGuild() to use Guild.create() factory
// Update deleteGuild(String guildId, ...) → deleteGuild(UUID guildId, ...)
// Update all other methods with String guildId to UUID
```

### [ ] 3.3 GuildRoleService.java
```java
// Update all methods:
- getGuildRoles(String guildId) → getGuildRoles(UUID guildId)
- getRoleByName(String guildId, String name) → getRoleByName(UUID guildId, String name)
- createRole(String guildId, ...) → createRole(UUID guildId, ...)
- deleteRole(String guildId, String roleId) → deleteRole(UUID guildId, String roleId)
- updateRolePermissions(String guildId, ...) → updateRolePermissions(UUID guildId, ...)
- assignDefaultRole(String guildId, UUID playerId) → assignDefaultRole(UUID guildId, UUID playerId)
- createDefaultRoleForGuild(Guild guild) - Already OK (uses guild.getId())
- removeAllMemberRoles(String guildId, ...) → removeAllMemberRoles(UUID guildId, ...)
- deleteAllGuildRoles(String guildId) → deleteAllGuildRoles(UUID guildId)
```

### [ ] 3.4 PermissionService.java
```java
// Update all String guildId parameters to UUID
```

### [ ] 3.5 TerritoryService.java
```java
// Update:
- unclaimAll(String guildId) → unclaimAll(UUID guildId)
- getChunkOwner(ChunkKey chunk) - already returns Guild (OK)
```

### [ ] 3.6 SpawnService.java
```java
// Update all methods with String guildId to UUID
```

### [ ] 3.7 GuildMemberService.java
```java
// Update all String guildId parameters to UUID
```

### [ ] 3.8 SubregionService.java
```java
// Update all String guildId parameters to UUID
// Key: getGuildSubregions(String guildId) → getGuildSubregions(UUID guildId)
// And others
```

### [ ] 3.9 SkillTreeService.java
```java
// Update all String guildId parameters to UUID
// Key: methods accepting guildId
```

### [ ] 3.10 VaultService.java
```java
// Update all String guildId parameters to UUID
```

### [ ] 3.11 RelationshipService.java
```java
// Update all String guildId parameters to UUID
// Key: proposeAlliance(), acceptAlliance(), rejectAlliance(), breakAlliance(), etc.
```

### [ ] 3.12 GuildChatListener.java
```java
// Update if it accepts String guildId parameters
```

### [ ] 3.13 Other Services
```java
// Check these for String guildId:
- GuildDefaultPermissionsService
- MultiblockService
- ProgressionService
- And any others identified
```

---

## PHASE 4: Command Components (Priority: MEDIUM - mostly verification)

### [ ] 4.1 InfoComponent.java
```java
// Verify: guild.getId() is now UUID
// Check: subregionService.getTypeUsage(guild.getId(), ...) passes UUID
// Check: progressionService.getOrCreateProgression(guild.getId()) passes UUID
```

### [ ] 4.2 AllyComponent.java
```java
// Verify: guild.getId() calls pass UUID
// Check: relationshipService calls use UUID
// Likely already OK once services are updated
```

### [ ] 4.3 RoleComponent.java
```java
// Verify: roleService.getGuildRoles(guild.getId()) passes UUID
// Check: other guild.getId() usages
```

### [ ] 4.4 AdminComponent.java
```java
// Verify: lifecycleService.deleteGuild(guild.getId(), ...) passes UUID
// Check: any other guild.getId() calls
```

### [ ] 4.5 All Other Components
```
- CreateComponent
- JoinComponent
- LeaveComponent
- DisbandComponent
- ListComponent
- SpawnComponent
- SetspawnComponent
- ColorComponent
- DescriptionComponent
- NameComponent
- ToggleComponent
- MapComponent
- KickComponent
- ClaimComponent
- UnclaimComponent
- AutoComponent
- InviteComponent
- AcceptComponent
- DeclineComponent
- InvitesComponent
- MemberComponent
- RegionComponent
- VaultComponent
- LogComponent
- EnemyComponent
- NeutralComponent
- GuildChatComponent
- AllyChatComponent
- LevelUpComponent
- ProjectComponent
- SkillsComponent

// Most should just work once services are updated
// Just verify guild.getId() flows correctly as UUID
```

---

## PHASE 5: Test Files & Test Repositories (Priority: MEDIUM)

### [ ] 5.1 InMemoryGuildRepository.java
```java
// File: src/test/java/org/aincraft/storage/InMemoryGuildRepository.java
// Update interface implementation:
- delete(UUID guildId)
- findById(UUID guildId)
```

### [ ] 5.2 GuildTest.java
```java
// File: src/test/java/org/aincraft/GuildTest.java
// Update:
- Guild creation to use Guild.create() factory method
- Handle Optional<Guild> return
// Likely need minimal changes
```

### [ ] 5.3 GuildServiceTest.java / Integration Tests
```java
// Update test setup and method calls to use UUID guildId
```

### [ ] 5.4 Repository Tests
```java
// Test classes for repositories
- GuildRepositoryTest
- GuildMemberRepositoryTest
- ChunkClaimRepositoryTest
- And others

// Update to use UUID parameters
```

### [ ] 5.5 Mock Repositories
```java
// Any mock implementations in tests
// Update to use UUID guildId
```

---

## PHASE 6: Verification & Final Testing

### [ ] 6.1 Compilation Check
```bash
./gradlew clean build
```

### [ ] 6.2 Run All Tests
```bash
./gradlew test
```

### [ ] 6.3 Check for String Guild IDs
```bash
grep -r "String guildId" src/main/java --include="*.java" | grep -v "//" | wc -l
# Should be 0 (or only in comments)
```

### [ ] 6.4 Verify No Raw String Guild IDs
```bash
# Check no calls like: guildService.deleteGuild("some-id", ...)
grep -r 'deleteGuild("' src/main/java --include="*.java"
# Should find 0 results
```

### [ ] 6.5 Database Tests
```bash
# Run database integration tests
./gradlew test --tests "*Jdbc*"
```

### [ ] 6.6 Server Run Test
```bash
# Start the server and verify guilds work
# Check:
- Guild creation works
- Saving/loading from database
- Command components work
- No NPEs in logs
```

---

## Success Criteria Checklist

- [ ] No compilation errors: `./gradlew build` succeeds
- [ ] All tests pass: `./gradlew test` passes
- [ ] Guild.getId() returns UUID everywhere
- [ ] All Repository interfaces use UUID guildId
- [ ] All JDBC implementations handle UUID.toString()/fromString()
- [ ] All Service methods accept UUID guildId
- [ ] No String guild IDs in parameter lists (except database operations)
- [ ] No exceptions from Guild constructor (all use factories)
- [ ] Database reads/writes work correctly
- [ ] Command components execute without errors
- [ ] No NPEs in server logs
- [ ] Guild creation works end-to-end
- [ ] Guild persistence works (save/load)
- [ ] All guild operations work (claim, unclaim, roles, etc.)

---

## Troubleshooting

### Common Issues

**Issue: "Cannot find symbol: method deleteGuild(String, UUID)"**
- Solution: Update the service method signature to UUID guildId
- Check Phase 3 for the service method

**Issue: "UUID.fromString() throws IllegalArgumentException"**
- Solution: Add try-catch when parsing from database
- Database corruption or invalid data in guild_id column

**Issue: "guild.getId() returns String but expects UUID"**
- Solution: Should not happen - Guild.java was updated
- Verify Guild.java changes were applied correctly

**Issue: "NullPointerException in factory method"**
- Solution: Factory methods return Optional.empty() on validation failure
- Check calling code handles Optional.empty()

**Issue: Tests fail with "guild.getId() type mismatch"**
- Solution: Update test to handle UUID instead of String
- Check Phase 5 for test file updates

---

## Estimated Effort Per Phase

| Phase | Task | Files | Est. Time | Difficulty |
|-------|------|-------|-----------|-----------|
| 1 | Interface updates | 8 | 30 min | Low |
| 2 | JDBC implementations | 30 | 90 min | Low |
| 3 | Service classes | 15 | 45 min | Medium |
| 4 | Components verification | 20 | 15 min | Low |
| 5 | Test updates | 8 | 30 min | Medium |
| 6 | Final testing | - | 30 min | High |
| **TOTAL** | **All** | **~81** | **~3.5 hrs** | **Low-Med** |

---

## Notes for Implementer

1. **Work systematically**: Don't skip around - follow phase order
2. **Compile frequently**: Run `./gradlew build` after each major component
3. **Git commits**: Commit after each phase for easier rollback
4. **IDE refactoring**: Use IDE "Change Signature" feature when available
5. **Search patterns**: Use grep to find remaining String guildId occurrences
6. **Pattern consistency**: Follow the patterns shown in Phase 2/3 examples
7. **Database**: No schema changes needed - VARCHAR(36) works for UUID strings
8. **Testing**: Comprehensive tests will catch most issues

