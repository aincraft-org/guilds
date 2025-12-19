# Skill Tree System - File Reference Guide

## Complete File Locations

### Domain Models (org.aincraft.skilltree)

1. **SkillBranch.java**
   - Location: `src/main/java/org/aincraft/skilltree/SkillBranch.java`
   - Lines: 15
   - Type: Enum
   - Values: ECONOMY, TERRITORY, COMBAT

2. **SkillEffect.java**
   - Location: `src/main/java/org/aincraft/skilltree/SkillEffect.java`
   - Lines: 17
   - Type: Record
   - Fields: category (String), value (double), displayName (String)

3. **SkillDefinition.java**
   - Location: `src/main/java/org/aincraft/skilltree/SkillDefinition.java`
   - Lines: 61
   - Type: Record (immutable config)
   - Fields: id, name, description, branch, spCost, prerequisites, effect
   - Methods: hasPrerequisites(), isPrerequisite(String)

4. **RespecConfig.java**
   - Location: `src/main/java/org/aincraft/skilltree/RespecConfig.java`
   - Lines: 27
   - Type: Record
   - Fields: enabled (boolean), material (Material), amount (int)

5. **GuildSkillTree.java**
   - Location: `src/main/java/org/aincraft/skilltree/GuildSkillTree.java`
   - Lines: 200
   - Type: Mutable entity class
   - Constructor: new GuildSkillTree(UUID guildId)
   - Alt Constructor: new GuildSkillTree(UUID guildId, int availableSp, int totalSpEarned, Set<String> unlockedSkills)
   - Key Methods:
     - awardSkillPoints(int amount)
     - canUnlock(SkillDefinition skill) : boolean
     - unlockSkill(SkillDefinition skill) : void
     - respec() : void
     - isUnlocked(String skillId) : boolean
   - Getters: getGuildId(), getAvailableSp(), getTotalSpEarned(), getUnlockedSkills()

6. **SkillUnlockResult.java**
   - Location: `src/main/java/org/aincraft/skilltree/SkillUnlockResult.java`
   - Lines: 97
   - Type: Record (immutable result)
   - Fields: success (boolean), message (String), skill (SkillDefinition)
   - Factory Methods:
     - success(SkillDefinition skill) : SkillUnlockResult
     - failure(String message) : SkillUnlockResult
     - insufficientSp(int available, int required) : SkillUnlockResult
     - missingPrerequisite(String skillId, String missingPrereq) : SkillUnlockResult
     - alreadyUnlocked(String skillId) : SkillUnlockResult
     - skillNotFound(String skillId) : SkillUnlockResult

7. **RespecResult.java**
   - Location: `src/main/java/org/aincraft/skilltree/RespecResult.java`
   - Lines: 98
   - Type: Record (immutable result)
   - Fields: success (boolean), message (String), spRestored (int)
   - Factory Methods:
     - success(int spRestored) : RespecResult
     - failure(String message) : RespecResult
     - disabled() : RespecResult
     - insufficientMaterials(String material, int have, int need) : RespecResult
     - materialConsumptionFailed() : RespecResult
     - vaultAccessFailed() : RespecResult
     - vaultNotFound() : RespecResult

### Persistence Layer

8. **GuildSkillTreeRepository.java** (Interface)
   - Location: `src/main/java/org/aincraft/skilltree/storage/GuildSkillTreeRepository.java`
   - Lines: 55
   - Package: org.aincraft.skilltree.storage
   - Methods:
     - save(GuildSkillTree tree) : void
     - findByGuildId(UUID guildId) : Optional<GuildSkillTree>
     - delete(UUID guildId) : void
     - unlockSkill(UUID guildId, String skillId, long unlockedAt) : void
     - clearUnlockedSkills(UUID guildId) : void
     - getUnlockedSkills(UUID guildId) : Set<String>

9. **JdbcGuildSkillTreeRepository.java** (Implementation)
   - Location: `src/main/java/org/aincraft/database/repository/JdbcGuildSkillTreeRepository.java`
   - Lines: 200
   - Package: org.aincraft.database.repository
   - Annotation: @Singleton
   - Constructor: @Inject public JdbcGuildSkillTreeRepository(ConnectionProvider connectionProvider)
   - Implements: GuildSkillTreeRepository
   - Database Support: SQLite, MySQL, PostgreSQL, H2, MariaDB
   - Key Implementation Details:
     - Uses Sql.upsertGuildSkillTree(dbType) for UPSERT operations
     - Loads unlocked skills on findByGuildId()
     - Proper UUID/String conversion at boundaries

### Configuration & Registry

10. **SkillTreeRegistry.java**
    - Location: `src/main/java/org/aincraft/skilltree/SkillTreeRegistry.java`
    - Lines: 300
    - Annotation: @Singleton
    - Constructor: @Inject public SkillTreeRegistry(GuildsPlugin plugin)
    - Key Methods:
      - loadFromConfig() : void (loads from config.yml skill-tree section)
      - getSkill(String id) : Optional<SkillDefinition>
      - getAllSkills() : Collection<SkillDefinition>
      - getSkillsByBranch(SkillBranch branch) : List<SkillDefinition>
      - getSpPerLevel() : int
      - getRespecConfig() : RespecConfig
      - hasSkill(String id) : boolean
      - getSkillCount() : int
    - Validates: Prerequisite chains, no circular dependencies

### Service Layer

11. **SkillBuffProvider.java**
    - Location: `src/main/java/org/aincraft/skilltree/SkillBuffProvider.java`
    - Lines: 32
    - Annotation: @Singleton
    - Key Method:
      - getSkillBonusValue(UUID guildId, String categoryId) : double
    - Returns: Bonus value from unlocked skills for a buff category
    - Used by: BuffApplicationService.getCombinedBuffValue()

12. **SkillTreeService.java**
    - Location: `src/main/java/org/aincraft/skilltree/SkillTreeService.java`
    - Lines: 230
    - Annotation: @Singleton
    - Dependencies (injected):
      - GuildSkillTreeRepository repository
      - SkillTreeRegistry registry
      - VaultService vaultService
    - Constructor: @Inject public SkillTreeService(...)
    - Key Public Methods:
      - unlockSkill(UUID guildId, String skillId) : SkillUnlockResult
      - respec(UUID guildId, UUID requesterId, Vault vault) : RespecResult
      - awardSkillPoints(UUID guildId, int amount) : void
      - getOrCreateSkillTree(UUID guildId) : GuildSkillTree
      - getSkillTree(UUID guildId) : Optional<GuildSkillTree>
    - Private Methods:
      - countMaterial(ItemStack[] contents, Material material) : int
      - deductMaterial(ItemStack[] contents, Material material, int amount) : void

### Integration Point

13. **ProgressionService.java** (Modified)
    - Location: `src/main/java/org/aincraft/progression/ProgressionService.java`
    - Changes:
      - Added imports:
        - org.aincraft.skilltree.SkillTreeService
        - org.aincraft.skilltree.SkillTreeRegistry
      - Added constructor parameters:
        - SkillTreeService skillTreeService
        - SkillTreeRegistry skillTreeRegistry
      - Modified processAutoLevelUps() method:
        ```java
        // Award skill points
        int spToAward = skillTreeRegistry.getSpPerLevel();
        skillTreeService.awardSkillPoints(guildId, spToAward);
        ```
    - Location in method: After lifecycleService.updateGuildCapacities()

### Dependency Injection

14. **GuildsModule.java** (Modified)
    - Location: `src/main/java/org/aincraft/inject/GuildsModule.java`
    - Changes:
      - Added import: org.aincraft.database.repository.JdbcGuildSkillTreeRepository
      - Skill tree bindings (already configured):
        ```java
        bind(GuildSkillTreeRepository.class).to(JdbcGuildSkillTreeRepository.class).in(Singleton.class);
        bind(SkillTreeRegistry.class).in(Singleton.class);
        bind(SkillTreeService.class).in(Singleton.class);
        bind(SkillBuffProvider.class).in(Singleton.class);
        bind(SkillsComponent.class).in(Singleton.class);
        ```
    - Line numbers: ~312-316

## Database Schema Reference

The following tables are used (already exist in Sql.java):

### guild_skill_trees
```sql
CREATE TABLE guild_skill_trees (
    guild_id TEXT/VARCHAR(36) PRIMARY KEY,
    available_sp INTEGER NOT NULL DEFAULT 0,
    total_sp_earned INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
)
```

### guild_unlocked_skills
```sql
CREATE TABLE guild_unlocked_skills (
    guild_id TEXT/VARCHAR(36) NOT NULL,
    skill_id TEXT/VARCHAR(64) NOT NULL,
    unlocked_at BIGINT NOT NULL,
    PRIMARY KEY (guild_id, skill_id),
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
)
```

## Configuration Section (config.yml)

```yaml
skill-tree:
  sp-per-level: 1
  respec:
    enabled: true
    material: EMERALD
    amount: 64
  skills:
    [skill definitions here]
```

## Quick Integration Reference

### To Award Skill Points (called from ProgressionService.processAutoLevelUps)
```java
int spToAward = skillTreeRegistry.getSpPerLevel();
skillTreeService.awardSkillPoints(guildId, spToAward);
```

### To Unlock a Skill
```java
SkillUnlockResult result = skillTreeService.unlockSkill(guildId, skillId);
if (result.success()) {
    // Handle success
}
```

### To Respec
```java
RespecResult result = skillTreeService.respec(guildId, requesterId, vault);
if (result.success()) {
    // Handle respec - SP restored: result.spRestored()
}
```

### To Check Unlocked Skills
```java
Optional<GuildSkillTree> treeOpt = skillTreeService.getSkillTree(guildId);
if (treeOpt.isPresent()) {
    Set<String> unlockedIds = treeOpt.get().getUnlockedSkills();
    // Process unlocked skills
}
```

## Class Relationships

```
GuildSkillTree (entity)
    └─ contains Set<String> unlockedSkills
    └─ has UUID guildId

SkillUnlockResult (record)
    └─ has SkillDefinition skill (on success)
    └─ has String message

RespecResult (record)
    └─ has int spRestored

SkillDefinition (record)
    └─ has SkillBranch branch
    └─ has SkillEffect effect
    └─ has List<String> prerequisites

SkillEffect (record)
    └─ has String category
    └─ has double value

SkillTreeService
    └─ uses GuildSkillTreeRepository
    └─ uses SkillTreeRegistry
    └─ uses VaultService

SkillTreeRegistry
    └─ loads Map<String, SkillDefinition> skills
    └─ provides RespecConfig

GuildSkillTreeRepository (interface)
    └─ implemented by JdbcGuildSkillTreeRepository

BuffApplicationService
    └─ uses SkillBuffProvider.getSkillBonusValue()
```

## Summary

- **12 new files** implementing complete skill tree system
- **2 modified files** for integration (ProgressionService, GuildsModule)
- **~1,500 lines** of well-documented production code
- **All SOLID principles** applied throughout
- **Multi-database support** (SQLite, MySQL, PostgreSQL, H2, MariaDB)
- **Zero compilation errors** in skill tree code
- **Complete Javadoc** documentation on all public APIs
