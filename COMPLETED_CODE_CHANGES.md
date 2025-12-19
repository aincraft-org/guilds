# Guild ID UUID Refactoring - Completed Code Changes

## Summary
This document shows all code changes that have been completed for the UUID refactoring.

---

## 1. Guild.java

### Location
`src/main/java/org/aincraft/Guild.java`

### Change 1.1: Domain Type Change (Line 16)
```java
// BEFORE
private final String id;

// AFTER
private final UUID id;
```

### Change 1.2: Constructor Replacement (Lines 38-67)
```java
// BEFORE
public Guild(String name, String description, UUID ownerId) {
    this.id = UUID.randomUUID().toString();
    // ... rest of constructor
}

public Guild(String id, String name, String description, UUID ownerId, long createdAt, int maxMembers, String color) {
    this.id = Objects.requireNonNull(id, "Guild ID cannot be null");
    // ... rest of constructor
}

// AFTER
private Guild(UUID id, String name, String description, UUID ownerId, long createdAt, int maxMembers, String color, boolean isNew) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.ownerId = ownerId;
    this.members = new ArrayList<>();
    if (isNew) {
        this.members.add(ownerId);
    }
    this.createdAt = createdAt;
    this.maxMembers = maxMembers;
    this.maxChunks = DEFAULT_MAX_CHUNKS;
    this.color = color;
    this.allowExplosions = true;
    this.allowFire = true;
    this.isPublic = false;
}
```

### Change 1.3: Factory Methods Added (Lines 69-120)
```java
/**
 * Factory method to create a new Guild with validation.
 * Generates a new UUID and current timestamp.
 */
public static java.util.Optional<Guild> create(String name, String description, UUID ownerId) {
    try {
        if (name == null || name.trim().isEmpty()) {
            return java.util.Optional.empty();
        }
        if (ownerId == null) {
            return java.util.Optional.empty();
        }

        UUID guildId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Guild guild = new Guild(guildId, name, description, ownerId, now, DEFAULT_MAX_MEMBERS, null, true);
        return java.util.Optional.of(guild);
    } catch (Exception e) {
        return java.util.Optional.empty();
    }
}

/**
 * Factory method to restore a Guild from database storage with validation.
 */
public static java.util.Optional<Guild> restore(UUID id, String name, String description, UUID ownerId, long createdAt, int maxMembers, String color) {
    try {
        if (id == null || name == null || name.trim().isEmpty() || ownerId == null || maxMembers < MIN_MAX_MEMBERS) {
            return java.util.Optional.empty();
        }

        Guild guild = new Guild(id, name, description, ownerId, createdAt, maxMembers, color, false);
        return java.util.Optional.of(guild);
    } catch (Exception e) {
        return java.util.Optional.empty();
    }
}
```

### Change 1.4: Return Type Change (Line 278)
```java
// BEFORE
public String getId() {
    return id;
}

// AFTER
public UUID getId() {
    return id;
}
```

### Change 1.5: Equals/HashCode Update (Lines 390-405)
```java
// BEFORE
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Guild)) return false;
    Guild guild = (Guild) o;
    return Objects.equals(id, guild.id);
}

@Override
public int hashCode() {
    return Objects.hash(id);
}

// AFTER
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Guild)) return false;
    Guild guild = (Guild) o;
    return id.equals(guild.id);
}

@Override
public int hashCode() {
    return id.hashCode();
}
```

---

## 2. GuildRepository.java

### Location
`src/main/java/org/aincraft/storage/GuildRepository.java`

### Change 2.1: Add Import
```java
// ADDED
import java.util.UUID;
```

### Change 2.2: Method Signature Updates (Lines 14-15)
```java
// BEFORE
void delete(String guildId);
Optional<Guild> findById(String guildId);

// AFTER
void delete(UUID guildId);
Optional<Guild> findById(UUID guildId);
```

---

## 3. JdbcGuildRepository.java

### Location
`src/main/java/org/aincraft/database/repository/JdbcGuildRepository.java`

### Change 3.1: Save Method Update (Line 48)
```java
// BEFORE
ps.setString(1, guild.getId());

// AFTER
ps.setString(1, guild.getId().toString());
```

### Change 3.2: Delete Method Signature and Implementation (Lines 79-84)
```java
// BEFORE
@Override
public void delete(String guildId) {
    Objects.requireNonNull(guildId, "Guild ID cannot be null");
    // ...
    ps.setString(1, guildId);
    // ...
}

// AFTER
@Override
public void delete(UUID guildId) {
    Objects.requireNonNull(guildId, "Guild ID cannot be null");
    // ...
    ps.setString(1, guildId.toString());
    // ...
}
```

### Change 3.3: FindById Method Signature and Implementation (Lines 92-98)
```java
// BEFORE
@Override
public Optional<Guild> findById(String guildId) {
    Objects.requireNonNull(guildId, "Guild ID cannot be null");
    // ...
    ps.setString(1, guildId);
    // ...
}

// AFTER
@Override
public Optional<Guild> findById(UUID guildId) {
    Objects.requireNonNull(guildId, "Guild ID cannot be null");
    // ...
    ps.setString(1, guildId.toString());
    // ...
}
```

### Change 3.4: MapRowToGuild Method Complete Rewrite (Lines 147-179)
```java
// BEFORE
private Guild mapRowToGuild(ResultSet rs) throws SQLException {
    String id = rs.getString("id");
    String name = rs.getString("name");
    String description = rs.getString("description");
    String ownerId = rs.getString("owner_id");
    long createdAt = rs.getLong("created_at");
    int maxMembers = rs.getInt("max_members");
    String membersJson = rs.getString("members");
    String color = rs.getString("color");

    Guild guild = new Guild(id, name, description, UUID.fromString(ownerId), createdAt, maxMembers, color);

    restoreMembers(guild, membersJson);
    restoreSpawn(guild, rs);
    restoreHomeblock(guild, rs);

    guild.setExplosionsAllowed(getBoolean(rs, "allow_explosions", true));
    guild.setFireAllowed(getBoolean(rs, "allow_fire", true));
    guild.setPublic(getBoolean(rs, "is_public", false));

    return guild;
}

// AFTER
private Guild mapRowToGuild(ResultSet rs) throws SQLException {
    String idStr = rs.getString("id");
    String name = rs.getString("name");
    String description = rs.getString("description");
    String ownerIdStr = rs.getString("owner_id");
    long createdAt = rs.getLong("created_at");
    int maxMembers = rs.getInt("max_members");
    String membersJson = rs.getString("members");
    String color = rs.getString("color");

    try {
        UUID id = UUID.fromString(idStr);
        UUID ownerId = UUID.fromString(ownerIdStr);

        java.util.Optional<Guild> guildOpt = Guild.restore(id, name, description, ownerId, createdAt, maxMembers, color);
        if (guildOpt.isEmpty()) {
            throw new SQLException("Failed to create guild from database row - validation failed");
        }

        Guild guild = guildOpt.get();
        restoreMembers(guild, membersJson);
        restoreSpawn(guild, rs);
        restoreHomeblock(guild, rs);

        guild.setExplosionsAllowed(getBoolean(rs, "allow_explosions", true));
        guild.setFireAllowed(getBoolean(rs, "allow_fire", true));
        guild.setPublic(getBoolean(rs, "is_public", false));

        return guild;
    } catch (IllegalArgumentException e) {
        throw new SQLException("Failed to parse guild UUID from database", e);
    }
}
```

---

## 4. GuildService.java

### Location
`src/main/java/org/aincraft/GuildService.java`

### Change 4.1: CreateGuild Method Update (Lines 87-92)
```java
// BEFORE
Guild guild = new Guild(name, description, ownerId);
guildRepository.save(guild);
// ...

// AFTER
Optional<Guild> guildOpt = Guild.create(name, description, ownerId);
if (guildOpt.isEmpty()) {
    return null;
}

Guild guild = guildOpt.get();
guildRepository.save(guild);
// ...
```

### Change 4.2: DeleteGuild Method Signature Update (Line 117)
```java
// BEFORE
public boolean deleteGuild(String guildId, UUID requesterId) {

// AFTER
public boolean deleteGuild(UUID guildId, UUID requesterId) {
```

### Change 4.3: JoinGuild Method Signature Update (Line 153)
```java
// BEFORE
public boolean joinGuild(String guildId, UUID playerId) {

// AFTER
public boolean joinGuild(UUID guildId, UUID playerId) {
```

---

## Summary of Changes by File

| File | Changes | Type | Complexity |
|------|---------|------|-----------|
| Guild.java | 5 major changes | Domain Model | High |
| GuildRepository.java | 1 import + 2 method signatures | Interface | Low |
| JdbcGuildRepository.java | 4 changes across 4 methods | Implementation | Medium |
| GuildService.java | 3 method updates | Service | Medium |
| **TOTAL** | **13 changes** | **Mixed** | **Medium** |

---

## All Changes Are:

✅ **Type-Safe** - Using UUID domain type instead of String
✅ **Null-Safe** - Using Optional pattern for factory methods
✅ **Validated** - Input validation at factory level
✅ **Database-Compatible** - UUID stored as VARCHAR(36) string
✅ **Backward-Incompatible** - Breaking changes intentional (type upgrade)

---

## Files Still Needing Changes

Approximately 56+ files remain:

1. **Repository Interfaces** (8 files) - String guildId → UUID guildId
2. **JDBC Implementations** (30+ files) - UUID conversion patterns
3. **Service Classes** (15 files) - Method signature updates
4. **Components** (20 files) - Mostly verification, should work as-is
5. **Tests** (8 files) - Test implementation updates

See `UUID_REFACTORING_CHECKLIST.md` for complete list.

---

## Testing the Changes

After all changes are complete:

```bash
# Verify no compilation errors
./gradlew clean build

# Run all tests
./gradlew test

# Check for remaining String guildId in code
grep -r "String guildId" src/main/java --include="*.java" | grep -v "//"
# Should return 0 results
```

---

## Key Patterns for Remaining Work

### Repository Interface Pattern
```java
// From this
void delete(String guildId);

// To this
void delete(UUID guildId);
```

### JDBC Parameter Pattern
```java
// From this
ps.setString(index, guildId);

// To this
ps.setString(index, guildId.toString());
```

### JDBC Result Pattern
```java
// From this
String guildId = rs.getString("guild_id");

// To this
UUID guildId = UUID.fromString(rs.getString("guild_id"));
```

### Service Method Pattern
```java
// From this
public void doSomething(String guildId) { ... }

// To this
public void doSomething(UUID guildId) { ... }
```

---

## Validation Checklist

- [x] Guild.java refactored to UUID with factories
- [x] GuildRepository interface updated
- [x] JdbcGuildRepository fully updated
- [x] GuildService key methods updated
- [ ] Remaining repositories interfaces updated (56 files)
- [ ] All JDBC implementations updated
- [ ] All service methods updated
- [ ] All tests updated
- [ ] Full compilation passes
- [ ] All tests pass
- [ ] No String guildId parameters in main code

