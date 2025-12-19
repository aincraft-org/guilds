# Guild ID Refactoring - Complete Scope Analysis

## Summary
This refactoring changes guild IDs from `String` to `UUID` throughout the codebase. Requires systematic updates across 50+ files.

## Completed
1. ✅ Guild.java - Changed `id: String` to `id: UUID`, added factory methods `create()` and `restore()`, made constructor private
2. ✅ GuildRepository interface - Updated method signatures to use `UUID guildId`
3. ✅ JdbcGuildRepository - Updated save/delete/findById to use UUID with toString() conversion

## Critical Files Requiring Changes - PHASE 1 (Core Services)

### GuildService.java
**Lines to update:**
- Line 87: `Guild guild = new Guild(name, description, ownerId);` → Use `Guild.create()`
- Line 112: `deleteGuild(String guildId, UUID requesterId)` → Change to `UUID guildId`
- Line 116: `guildRepository.findById(guildId)` → Pass UUID
- Line 129: `memberRepository.removeAllMembers(guildId)` → Pass UUID
- Line 132: `chunkClaimRepository.unclaimAll(guildId)` → Pass UUID
- Line 136: `guildRepository.delete(guildId)` → Pass UUID
- Line 148: `joinGuild(String guildId, UUID playerId)` → Change to `UUID guildId`
- And many more String guildId parameters throughout

### GuildLifecycleService.java
**Lines to update:**
- Line 71: `Guild guild = new Guild(name, description, ownerId);` → Use `Guild.create()`
- Line 77: `GuildRole defaultRole = new GuildRole(guild.getId(), ...)` → Already UUID
- Line 83: `poolRepository.setGuildCreatedAt(guild.getId(), ...)` → Already UUID
- Line 95: `deleteGuild(String guildId, UUID requesterId)` → Change to `UUID guildId`
- Line 99: `guildRepository.findById(guildId)` → Pass UUID

## Critical Files Requiring Changes - PHASE 2 (Repository Interfaces & Implementations)

### Repository Interfaces that accept String guildId:
1. GuildMemberRepository - `addMember(String guildId, UUID playerId, ...)`
2. GuildRoleRepository - `findByGuildAndName(String guildId, String name)`
3. ChunkClaimRepository - `unclaimAll(String guildId)`
4. GuildRelationshipRepository - `deleteAllByGuild(String guildId)`
5. InviteRepository - `deleteByGuildId(String guildId)`, `findByGuildId(String guildId)`
6. MemberRoleRepository - Methods accepting `String guildId`
7. GuildSkillTreeRepository - `findByGuildId(String guildId)`
8. ProjectRepository classes - Methods accepting `String guildId`

### JDBC Implementation Files:
- JdbcGuildMemberRepository
- JdbcGuildRoleRepository
- JdbcChunkClaimRepository
- JdbcGuildRelationshipRepository
- JdbcInviteRepository
- JdbcMemberRoleRepository
- JdbcGuildSkillTreeRepository
- And other JDBC repos

## Critical Files Requiring Changes - PHASE 3 (Command Components)

These files call `guild.getId()` and pass it to services that need UUID:
1. InfoComponent.java - Line 247: `subregionService.getTypeUsage(guild.getId(), ...)`
2. AllyComponent.java - Multiple lines passing `guild.getId()`
3. RoleComponent.java - Passing `guild.getId()` to roleService
4. AdminComponent.java - Passing `guild.getId()` to lifecycleService
5. All other command components that use `guild.getId()`

## Critical Files Requiring Changes - PHASE 4 (Supporting Services)

1. SubregionService - Methods accepting `String guildId`
2. SkillTreeService - Methods accepting `String guildId`
3. GuildRoleService - Methods accepting `String guildId`
4. VaultService - Methods accepting `String guildId`
5. Other service classes

## Critical Files Requiring Changes - PHASE 5 (Tests)

1. InMemoryGuildRepository - Implement UUID in Repository interface
2. Test classes using Guild.create() or old constructor
3. Mock repositories accepting UUID

## Strategy

### Approach: Bottom-up
1. Update all Repository interfaces first (String → UUID)
2. Update all JDBC implementations
3. Update all Service classes
4. Update test repositories
5. Update Command components (these should then just work with UUID)
6. Run full test suite
7. Fix compilation errors incrementally

### Key Points
- All `.getId()` calls on Guild now return UUID (no changes needed at call sites)
- All method parameters need String → UUID conversion
- Database stores UUIDs as VARCHAR(36) with `.toString()` conversion
- Repository `findById()` and `delete()` now take UUID parameters
- All JDBC methods must use `uuid.toString()` when setting parameters

## Files Count Estimate
- Repository interfaces: 8
- JDBC implementations: 12
- Service classes: 15
- Command components: 20
- Test files: 8
- **Total: ~60 files need modifications**

## Compilation Risk Assessment
- **High Risk**: Major breaking changes to signatures
- **Mitigation**: Update systematically, running compilation after each phase
- **Estimated Work**: 2-3 hour complete refactoring + testing
