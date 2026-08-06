# Permission System Refactoring Proposal
**Senior Engineer Review - Guilds Permission Architecture**

---

## Executive Summary

The current permission system has **7 critical architectural problems**:

1. **Dual permission check paths** - blocks vs entities use different logic
2. **Toggle/Permission confusion** - toggles scattered across services
3. **Unused modern enum system** - legacy bitwise system still active
4. **No caching** - every check hits database/complex evaluation
5. **Inconsistent priority ordering** - toggles interrupt permission hierarchy
6. **Dead code** - permissions table rarely used, enum system inactive
7. **Poor separation of concerns** - listeners, services, models all check permissions

**Recommendation**: Phased refactoring to unified permission pipeline with clear layers.

---

## Current Architecture Problems (Detailed)

### Problem 1: Multiple Permission Check Paths

**Current State**:
```
Block Break/Place:
  TownPublicAccessListener.onBlockBreak()
    → permissionService.canDestroy()
      → checkLocationPermission()
        → checkTownToggles() [only public_access]
        → evaluatePlotPermission() [hierarchical check]

Entity Damage:
  TownPublicAccessListener.onEntityDamageByEntity()
    → isPublicAccessAllowed() [LOCAL METHOD!]
      → permissionService.isPublicAccessEnabledAtLocation()
      → isPlayerResidentOfTown() [simple resident check]
      → NO PLOT OWNER CHECK
      → NO PERMISSION FLAG CHECK
```

**Issue**: Entity interactions bypass the entire permission hierarchy.

**Impact**: Players can break blocks but not damage entities, even though they own the plot.

---

### Problem 2: Toggle System Fragmentation

**Current Toggle Check Locations**:
```
public_access   → PermissionServiceImpl.checkTownToggles() (line 314)
pvp             → TownToggleListener.onEntityDamageByEntity()
fire            → TownToggleListener.onBlockIgnite()
explosions      → TownToggleListener.onEntityExplode()
mobs            → TownToggleListener.onCreatureSpawn()
```

**Issue**: Toggles are checked in 2 different places with different priorities:
- `public_access` runs BEFORE permission evaluation (blocks if off)
- Other toggles run in separate listener with different event priority

**Why This Is Bad**:
```java
// Scenario: Player owns plot, public_access = false
// Block break:
checkTownToggles() → public_access = false → DENIED [never checks ownership!]

// Entity damage (currently buggy, will be fixed):
evaluatePlotPermission() → owner check → ALLOWED
```

Toggles should be **orthogonal** to permissions, not **override** them.

---

### Problem 3: Dual Permission API (Bitwise vs Enum)

**Active System** (bitwise):
```java
// Permission.Flag constants
public static final int BUILD = 1 << 0;
public static final int DESTROY = 1 << 1;
// ... 22 different flags

// Usage:
permissionsFlags |= Permission.Flag.BUILD;
if ((permissionsFlags & flag) != 0) { ... }
```

**Inactive System** (enum):
```java
// GuildPermission.java - NEVER USED IN PRODUCTION CODE
public enum GuildPermission {
    BUILD(Category.BUILD, Permission.Flag.BUILD),
    DESTROY(Category.BUILD, Permission.Flag.DESTROY),
    // ...
}

// PermissionSet.java - NEVER USED
// PermissionEvaluationEngine.java - PARTIALLY IMPLEMENTED
```

**Issues**:
- Maintaining two systems doubles maintenance
- Enum system has better type safety but inactive
- Migration path unclear
- Dead code confuses new developers

---

### Problem 4: No Caching Layer

**Current State**:
```java
// PermissionServiceImpl lines 1059-1074 - ALL EMPTY STUBS
@Override
public void cachePermission(...) {
    // TODO: Implement caching
}

@Override
public void invalidateCache(...) {
    // TODO: Implement cache invalidation
}

// PermissionEvaluationEngine has SimplePermissionCache - NEVER INSTANTIATED
```

**Impact**: Every permission check:
1. Queries TownBlock from database (PlotService)
2. Queries Town from database (TownService)
3. Queries residents from database (ResidentService)
4. Runs complex hierarchical evaluation

**Estimated Cost**: 3-5 DB queries per block break/place event.

---

### Problem 5: Inconsistent Priority Ordering

**Documented Priority** (evaluatePlotPermission):
```
1. Global bypass
2. Plot owner
3. Explicit plot permissions
4. Town role permissions
5. Default deny
```

**Actual Priority** (checkLocationPermission flow):
```
0. Town toggle check [public_access ONLY]  ← OVERRIDES EVERYTHING
1. Global bypass
2. Plot owner
3. Explicit plot permissions
4. Town role permissions
5. Default deny
```

**Problem**: Mayor can't build in own town if `public_access = false`.

**Expected Behavior**: Toggles should control **non-resident** access, not override ownership.

---

### Problem 6: Unused Database Table

**permissions table**:
- 9 columns, indexed, migration created
- Query methods exist: getResidentPermissions(), getContextPermissions()
- **Used in**: evaluatePlotPermission() line 887 - but rarely reached

**Why Rarely Used**:
```java
// evaluatePlotPermission() line 880:
if (ownsPlot(residentUuid, plotId)) {
    return new PermissionEvaluationResult(true, "owner", "Plot owner...");
}

// Line 887 - explicit plot permissions check
List<Permission> plotPerms = getResidentPermissions(...);  ← NEVER EXECUTED
// Because owner check returns early
```

**Issue**: Plot owners always bypass explicit permissions, making permissions table useless for plots.

---

### Problem 7: Poor Separation of Concerns

**Current Architecture**:
```
TownPublicAccessListener:
  - Event handling ✓
  - Permission checking ✗ (should delegate fully)
  - Business logic ✗ (isPublicAccessAllowed method)

PermissionService:
  - Permission evaluation ✓
  - Toggle checking ✗ (should be separate service)
  - Database queries ✓
  - Caching ✗ (not implemented)

TownBlock model:
  - Data storage ✓
  - Permission flag manipulation ✓
  - Permission evaluation ✗ (allowsPublicBuild - business logic)
```

**Violations**:
- Listeners contain business logic
- Services mix permissions and toggles
- Models have evaluation methods

---

## Proposed Refactoring Architecture

### Phase 1: Unified Permission Pipeline (High Priority)

**Goal**: Single entry point for all permission checks.

**New Interface**:
```java
public interface PermissionResolver {
    /**
     * Resolve permission at location with full context
     */
    PermissionResult resolve(PermissionContext context);
}

public class PermissionContext {
    private final UUID playerUuid;
    private final Player player; // Optional - for location
    private final PermissionAction action; // BUILD, DESTROY, SWITCH, etc.
    private final Location location;
    private final Material material; // Optional - for specific block type

    // Lazy-loaded
    private TownBlock townBlock;
    private Town town;
    private Resident resident;
}

public class PermissionResult {
    private final boolean allowed;
    private final PermissionSource source; // OWNER, TOWN_MEMBER, EXPLICIT, TOGGLE, DEFAULT
    private final String reason;
    private final List<String> debugInfo; // For /perm command
}

public enum PermissionAction {
    BUILD,
    DESTROY,
    SWITCH,
    ITEM_USE,
    PVP,
    ENTITY_DAMAGE,
    CONTAINER_ACCESS,
    // ... more actions
}
```

**Pipeline Stages**:
```java
public class UnifiedPermissionResolver implements PermissionResolver {

    private final List<PermissionStage> pipeline;

    public UnifiedPermissionResolver() {
        pipeline = List.of(
            new WildernessStage(),      // 1. Check if in town
            new BypassStage(),          // 2. Admin bypass
            new OwnershipStage(),       // 3. Plot owner
            new ExplicitPermStage(),    // 4. Database permissions
            new RolePermStage(),        // 5. Town role (mayor/assistant/resident)
            new ToggleStage(),          // 6. Town toggles (for non-residents)
            new DefaultPermStage()      // 7. Plot type defaults
        );
    }

    @Override
    public PermissionResult resolve(PermissionContext context) {
        for (PermissionStage stage : pipeline) {
            Optional<PermissionResult> result = stage.evaluate(context);
            if (result.isPresent()) {
                return result.get();
            }
        }
        return PermissionResult.deny("No permission granted");
    }
}
```

**Each Stage**:
```java
public interface PermissionStage {
    /**
     * Evaluate this stage
     * @return Optional.of(result) if stage makes decision, Optional.empty() to continue
     */
    Optional<PermissionResult> evaluate(PermissionContext context);
}

// Example implementation:
public class OwnershipStage implements PermissionStage {
    @Override
    public Optional<PermissionResult> evaluate(PermissionContext context) {
        TownBlock block = context.getTownBlock();
        if (block == null) return Optional.empty();

        if (block.isOwnedBy(context.getPlayerUuid())) {
            return Optional.of(PermissionResult.allow(
                PermissionSource.OWNER,
                "Plot owner has full permissions"
            ));
        }

        return Optional.empty(); // Not owner, continue pipeline
    }
}
```

**Benefits**:
- ✓ Clear priority ordering
- ✓ Easy to test (each stage isolated)
- ✓ Easy to debug (log which stage decided)
- ✓ Easy to extend (add new stages)
- ✓ Consistent for all actions (blocks, entities, containers)

---

### Phase 2: Separate Toggle System (Medium Priority)

**Goal**: Toggles control **context**, not override permissions.

**New Service**:
```java
public interface TownToggleService {
    /**
     * Check if action type is allowed by town toggles
     * Returns true if toggle allows OR if player exempt
     */
    boolean isActionAllowed(ToggleType type, Location location, UUID playerUuid);
}

public enum ToggleType {
    PUBLIC_ACCESS,  // Non-residents can interact
    PVP,           // Combat allowed
    FIRE,          // Fire spread allowed
    EXPLOSIONS,    // Explosion damage allowed
    MOBS           // Hostile mobs spawn
}

public class TownToggleServiceImpl implements TownToggleService {
    @Override
    public boolean isActionAllowed(ToggleType type, Location location, UUID playerUuid) {
        Town town = getTownAtLocation(location);
        if (town == null) return true; // Wilderness

        boolean toggleValue = town.getToggle(type);
        if (toggleValue) return true; // Toggle is ON

        // Toggle is OFF - check if player is exempt
        return switch (type) {
            case PUBLIC_ACCESS -> isResidentOrOwner(town, playerUuid, location);
            case PVP -> false; // No exemptions for PvP
            case FIRE -> hasBypassPermission(playerUuid);
            case EXPLOSIONS -> hasBypassPermission(playerUuid);
            case MOBS -> false; // No exemptions
        };
    }
}
```

**Usage in Listeners**:
```java
// TownPublicAccessListener
@EventHandler
public void onBlockBreak(BlockBreakEvent event) {
    PermissionContext ctx = PermissionContext.builder()
        .player(event.getPlayer())
        .action(PermissionAction.DESTROY)
        .location(event.getBlock().getLocation())
        .build();

    // 1. Check permission
    PermissionResult permResult = permissionResolver.resolve(ctx);

    // 2. Check toggle (if not owner/resident)
    if (permResult.getSource() != PermissionSource.OWNER &&
        permResult.getSource() != PermissionSource.TOWN_MEMBER) {

        boolean toggleAllows = toggleService.isActionAllowed(
            ToggleType.PUBLIC_ACCESS,
            ctx.getLocation(),
            ctx.getPlayerUuid()
        );

        if (!toggleAllows) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cThis town is private!");
            return;
        }
    }

    // 3. Apply permission result
    if (!permResult.isAllowed()) {
        event.setCancelled(true);
        event.getPlayer().sendMessage("§c" + permResult.getReason());
    }
}
```

**Benefits**:
- ✓ Clear toggle semantics
- ✓ Toggles don't override ownership
- ✓ Easy to add new toggle types
- ✓ Toggle logic centralized

---

### Phase 3: Caching Layer (Medium Priority)

**Goal**: Reduce database queries.

**Strategy**: Cache at multiple levels.

**Level 1: Permission Result Cache**
```java
public class CachedPermissionResolver implements PermissionResolver {

    private final PermissionResolver delegate;
    private final LoadingCache<PermissionCacheKey, PermissionResult> cache;

    public CachedPermissionResolver(PermissionResolver delegate) {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(10000)
            .build(this::loadPermission);
    }

    @Override
    public PermissionResult resolve(PermissionContext context) {
        PermissionCacheKey key = PermissionCacheKey.from(context);
        return cache.get(key);
    }

    private PermissionResult loadPermission(PermissionCacheKey key) {
        return delegate.resolve(key.toContext());
    }
}

record PermissionCacheKey(
    UUID playerUuid,
    PermissionAction action,
    int chunkX,
    int chunkZ,
    String world
) {
    static PermissionCacheKey from(PermissionContext ctx) {
        Location loc = ctx.getLocation();
        return new PermissionCacheKey(
            ctx.getPlayerUuid(),
            ctx.getAction(),
            loc.getBlockX() >> 4,
            loc.getBlockZ() >> 4,
            loc.getWorld().getName()
        );
    }
}
```

**Level 2: Entity Cache** (already exists - HikariCP, but could use Caffeine)
```java
// Cache TownBlocks, Towns, Residents
private final LoadingCache<ChunkCoord, Optional<TownBlock>> townBlockCache;
private final LoadingCache<String, Optional<Town>> townCache;
private final LoadingCache<UUID, Optional<Resident>> residentCache;
```

**Invalidation Strategy**:
```java
public class PermissionCacheInvalidator {
    @EventHandler
    public void onPlotClaimed(PlotClaimEvent event) {
        cache.invalidate(event.getChunkCoord());
    }

    @EventHandler
    public void onPermissionChange(PermissionChangeEvent event) {
        cache.invalidateAll(k -> k.chunkX == event.getChunkX() && ...);
    }

    @EventHandler
    public void onTownToggleChange(TownToggleEvent event) {
        cache.invalidateAll(k -> k.isInTown(event.getTownId()));
    }
}
```

**Benefits**:
- ✓ 90%+ reduction in DB queries
- ✓ Sub-millisecond permission checks
- ✓ Automatic invalidation on changes

---

### Phase 4: Migrate to Enum System (Low Priority)

**Goal**: Type-safe permissions, better IDE support.

**Migration Path**:

**Step 1**: Make enum system active alongside bitwise
```java
public class Permission {
    // Keep legacy flags for backward compatibility
    @Deprecated
    public static class Flag {
        public static final int BUILD = 1 << 0;
        // ...
    }

    // New enum
    public enum Type {
        BUILD(1 << 0),
        DESTROY(1 << 1),
        SWITCH(1 << 2),
        // ...

        private final int legacyFlag;
        Type(int legacyFlag) { this.legacyFlag = legacyFlag; }

        public int getLegacyFlag() { return legacyFlag; }
    }
}
```

**Step 2**: Update permission checks to use enum
```java
// Old:
if (townBlock.hasPermissionFlag(Permission.Flag.BUILD)) { ... }

// New:
if (townBlock.hasPermission(Permission.Type.BUILD)) { ... }

// Implementation:
public boolean hasPermission(Permission.Type type) {
    return hasPermissionFlag(type.getLegacyFlag());
}
```

**Step 3**: Migrate storage to enum ordinals (breaking change)
```java
// After all code uses enums, change storage:
// Instead of: permissions_flags INTEGER (bitwise)
// Use: permissions TEXT (comma-separated enum names)

// Migration:
UPDATE town_blocks SET permissions = convertBitwiseToEnumList(permissions_flags);
```

**Benefits**:
- ✓ Type safety (compile-time checks)
- ✓ Better autocomplete in IDE
- ✓ Easier to extend (add new permissions)
- ✓ Remove GuildPermission/PermissionSet dead code

---

### Phase 5: Clean Architecture (Low Priority)

**Goal**: Proper separation of concerns.

**New Package Structure**:
```
org.aincraft.guilds.permissions/
├── api/
│   ├── PermissionResolver.java         (interface)
│   ├── PermissionContext.java          (immutable DTO)
│   ├── PermissionResult.java           (immutable result)
│   └── PermissionAction.java           (enum)
├── pipeline/
│   ├── UnifiedPermissionResolver.java  (main implementation)
│   ├── PermissionStage.java            (interface)
│   ├── stages/
│   │   ├── WildernessStage.java
│   │   ├── BypassStage.java
│   │   ├── OwnershipStage.java
│   │   ├── ExplicitPermStage.java
│   │   ├── RolePermStage.java
│   │   ├── ToggleStage.java
│   │   └── DefaultPermStage.java
├── cache/
│   ├── CachedPermissionResolver.java
│   ├── PermissionCacheKey.java
│   └── PermissionCacheInvalidator.java
├── toggle/
│   ├── TownToggleService.java
│   ├── TownToggleServiceImpl.java
│   └── ToggleType.java
└── legacy/
    └── PermissionServiceAdapter.java   (adapts old API to new)
```

**Benefits**:
- ✓ Clear module boundaries
- ✓ Easy to test (mock interfaces)
- ✓ Backward compatible (adapter)
- ✓ Easy to understand for new devs

---

## Migration Strategy

### Immediate Fixes (Zero Refactoring)

**Fix 1**: TownPublicAccessListener entity checks
```java
// Replace isPublicAccessAllowed() with proper permission checks
@EventHandler
public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
    if (!(event.getDamager() instanceof Player)) return;
    Player player = (Player) event.getDamager();

    // Use same logic as block break
    boolean canDamage = permissionService.canBuild(  // or new canDamageEntity()
        player.getUniqueId(),
        event.getEntity().getLocation().getBlockX(),
        event.getEntity().getLocation().getBlockZ(),
        event.getEntity().getWorld().getName()
    );

    if (!canDamage) {
        event.setCancelled(true);
        player.sendMessage("§cYou cannot damage entities here!");
    }
}
```

**Fix 2**: Move public_access toggle check AFTER owner check
```java
// PermissionServiceImpl.checkLocationPermission()
private boolean checkLocationPermission(...) {
    // 1. Get town block
    TownBlock block = getTownBlockAtLocation(...);
    if (block == null) return checkWildernessPermission();

    // 2. Check hierarchical permissions FIRST
    PermissionEvaluationResult result = evaluatePlotPermission(residentUuid, block.getId(), permissionFlag);

    // 3. If allowed by ownership/role, check toggles
    if (result.isAllowed()) {
        // Owner/resident allowed - toggles don't apply
        return true;
    }

    // 4. Not owner/resident - check public_access toggle
    if (!checkTownToggles(block.getTownId(), residentUuid)) {
        return false;
    }

    // 5. Return permission result
    return result.isAllowed();
}
```

### Phased Rollout (Recommended)

**Sprint 1** (1 week):
- Fix immediate bugs (above)
- Add unit tests for permission evaluation
- Document current behavior

**Sprint 2** (2 weeks):
- Implement Phase 1 (Unified Pipeline)
- Run in parallel with old system
- Compare results, fix discrepancies

**Sprint 3** (1 week):
- Switch to new pipeline
- Deprecate old PermissionService methods
- Add adapter for backward compatibility

**Sprint 4** (2 weeks):
- Implement Phase 2 (Toggle Service)
- Migrate listeners to use new toggle service

**Sprint 5** (1 week):
- Implement Phase 3 (Caching)
- Performance testing

**Sprint 6+** (Optional):
- Phase 4 & 5 as time permits

---

## Testing Strategy

### Unit Tests (Critical)

**Pipeline Stage Tests**:
```java
class OwnershipStageTest {
    @Test
    void shouldAllowOwner() {
        TownBlock block = createBlockOwnedBy(PLAYER_UUID);
        PermissionContext ctx = PermissionContext.builder()
            .playerUuid(PLAYER_UUID)
            .action(BUILD)
            .townBlock(block)
            .build();

        OwnershipStage stage = new OwnershipStage();
        Optional<PermissionResult> result = stage.evaluate(ctx);

        assertThat(result).isPresent();
        assertThat(result.get().isAllowed()).isTrue();
        assertThat(result.get().getSource()).isEqualTo(OWNER);
    }

    @Test
    void shouldContinueIfNotOwner() {
        TownBlock block = createBlockOwnedBy(OTHER_UUID);
        PermissionContext ctx = PermissionContext.builder()
            .playerUuid(PLAYER_UUID)
            .townBlock(block)
            .build();

        OwnershipStage stage = new OwnershipStage();
        Optional<PermissionResult> result = stage.evaluate(ctx);

        assertThat(result).isEmpty(); // Continue to next stage
    }
}
```

**Integration Tests**:
```java
class PermissionResolverIntegrationTest extends BaseIntegrationTest {
    @Test
    void shouldAllowOwnerEvenWhenPublicAccessDisabled() {
        // Setup
        Town town = createTown("TestTown");
        town.setToggle("public_access", false);

        TownBlock plot = createPlot(town, CHUNK_X, CHUNK_Z);
        plot.setOwnerId(PLAYER_UUID);

        // Execute
        PermissionResult result = resolver.resolve(
            PermissionContext.builder()
                .playerUuid(PLAYER_UUID)
                .action(BUILD)
                .location(plotLocation)
                .build()
        );

        // Assert
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getSource()).isEqualTo(OWNER);
    }
}
```

### Performance Tests

**Benchmark**:
```java
@Benchmark
public void benchmarkPermissionCheck(Blackhole bh) {
    PermissionResult result = resolver.resolve(testContext);
    bh.consume(result);
}

// Target: < 0.1ms per check with caching
// Current: ~2-5ms per check (3-5 DB queries)
```

---

## Metrics & Success Criteria

**Performance**:
- [ ] Permission check < 0.1ms (cached)
- [ ] Permission check < 5ms (uncached)
- [ ] Database queries reduced by 90%

**Code Quality**:
- [ ] Unit test coverage > 90% for permission code
- [ ] Zero cyclomatic complexity > 10 in permission code
- [ ] Zero duplicated permission check logic

**Functionality**:
- [ ] All permission scenarios tested (owner, resident, outsider, toggles)
- [ ] No regression in existing behavior
- [ ] Consistent behavior across block/entity/container interactions

---

## Risks & Mitigation

**Risk 1**: Breaking backward compatibility
- **Mitigation**: Adapter pattern, run both systems in parallel

**Risk 2**: Performance regression during migration
- **Mitigation**: Benchmark at each phase, roll back if slower

**Risk 3**: Toggle semantics change breaks existing servers
- **Mitigation**: Config migration guide, default to old behavior

**Risk 4**: Complex refactor takes too long
- **Mitigation**: Phased approach, immediate fixes first

---

## Conclusion

**Current system** is functional but has architectural debt:
- Multiple permission check paths
- Toggle/permission confusion
- No caching
- Dead code (enum system)

**Recommended approach**:
1. **Immediate**: Fix entity permission bugs (1 day)
2. **Short-term**: Unified pipeline (2 weeks)
3. **Medium-term**: Toggle service + caching (3 weeks)
4. **Long-term**: Enum migration + clean architecture (optional)

**Total effort**: 6-8 weeks for phases 1-3, or 1 day for immediate fixes only.

**ROI**:
- **Immediate fixes**: Critical bugs fixed
- **Phase 1-3**: 90% fewer bugs, 10x faster, easier to maintain

---

## Appendix: Code Examples

### Current Permission Check (Problematic)
```java
// What happens today:
Player breaks block at (100, 70, 200) in town with public_access=false

1. TownPublicAccessListener.onBlockBreak()
2. permissionService.canDestroy(uuid, 100, 200, "world")
3. checkLocationPermission()
4. checkTownToggles() → public_access = false → RETURN FALSE
5. Event cancelled - even if player owns the plot!
```

### Proposed Permission Check (Fixed)
```java
// What should happen:
Player breaks block at (100, 70, 200) in town with public_access=false

1. TownPublicAccessListener.onBlockBreak()
2. resolver.resolve(context)
3. Pipeline:
   - WildernessStage → no town block → CONTINUE
   - BypassStage → not admin → CONTINUE
   - OwnershipStage → player owns plot → ALLOW (source=OWNER)
4. Event allowed - owner can build despite public_access=false
```

---

**Author**: Senior Engineer Review
**Date**: 2025-01-13
**Status**: PROPOSAL
