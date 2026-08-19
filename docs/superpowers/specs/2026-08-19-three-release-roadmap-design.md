# Guilds Three-Release Product Roadmap

## Purpose

Evolve Guilds from a broad collection of territory foundations into one coherent New World-inspired Minecraft loop:

```text
form guild
→ govern territory
→ develop settlement
→ contest territory
→ fight war
→ recover and improve
```

The sequence is dependency-ordered rather than domain-ordered. Release 1 makes authority safe and finishes existing player surfaces. Release 2 gives settlements persistent cooperative progression. Release 3 connects influence and settlement investment to a complete territorial war.

## Product boundaries

This roadmap preserves the current architecture:

- `api` owns Paper-free public contracts and value types.
- `common` owns Paper-free rules, state machines, and SQL persistence.
- `paper` owns commands, listeners, scheduling, world interaction, and UI.
- Guilds remains one Paper plugin and one durable SQL authority.
- Guilds owns membership and roles.
- Territory owns spatial resolution.
- Economy owns money movement through `PaymentRail` and `EconomyBridge`.
- Every durable mutation commits to SQL before it becomes authoritative in memory or the world.

The following remain outside this three-release horizon:

- persistent Covenant/Marauder/Syndicate-style factions;
- native auction house or order book;
- personal housing and trophies;
- complex treaties and embargoes;
- multi-server wars or cross-shard economy;
- OAuth-based web administration;
- extracting Guilds subsystems into separate deployables.

## Release 1 — Stable Government

### Goal

Make the current guild and territory system safe, operable, and usable through complete player-facing flows.

### Build and operational health

- Restore a green `./gradlew --no-daemon check` on the supported JDK.
- Fix concrete source diagnostics, including implicit system-time-zone use.
- Treat the observed Error Prone `StringConcatToTextBlock` exception as a toolchain compatibility failure unless a minimal reproduction proves a source defect; pin or configure a compatible JDK/Error Prone combination rather than disguising an analyzer crash as an application fix.
- Add a health surface that verifies the SQL pool and critical schema objects.
- Document backup, restore, and legacy JSON/SQLite import procedures.

### Scope-aware governance

Introduce an explicit governance context containing:

- the territory-local governing guild, if resolvable;
- an optional alliance used for federal actions;
- the action scope being evaluated.

The authorization rules are:

- Local guild government controls local plots, commons, facilities, and settlement operations.
- Alliance government controls alliance membership, alliance policy, influence declarations, and future federal strategy.
- Alliance officers do not inherit local guild land rights merely because their alliance governs the broader territory.
- Sibling-alliance residents receive local rights only through public-access rules or explicit grants.
- Permission decisions inspect the requested `SovereignAction`; one seat check cannot grant every action.
- Stored permission entries are ignored unless the subject is a current member in the relevant scope.
- An unresolved governing-guild binding denies governed operations and reports the fault.
- Database failure never becomes empty permissions, public access, or a default government.
- A guild may belong to at most one alliance, enforced by durable constraints and service validation.

Territory protection and Guilds public-access listeners must agree on the same decision. Their combined behavior is tested for every government form and relevant action.

### Player-facing guild contracts

Complete the existing contract service with:

- list, post, fulfill, and cancel commands;
- inventory material sourcing and escrow;
- affordability checks before item removal;
- transactional fulfillment and cancellation;
- expiry processing;
- participant notifications;
- durable history and operator inspection.

A failed mutation refunds removed items. Retrying a committed operation cannot debit or release escrow twice.

### Administrative recovery

Expose supported recovery paths for:

- unresolved economy reconciliations;
- influence force-reset and declaration cancellation;
- territory-standing read and reset;
- failed governance resolution;
- database health status.

Every administrative mutation records actor, target, timestamp, and outcome.

### Runtime verification

Exercise these behaviors on an actual Paper server:

- guild and alliance lifecycle;
- government changes and permission boundaries;
- territory creation, save, reload, and overlap rejection;
- facility anchor restoration;
- waystone travel, cooldown, and movement/damage cancellation;
- trading-post interaction events;
- influence declaration, cooldown, and ownership flip;
- contract escrow, fulfillment, cancellation, and refund;
- restart persistence and interrupted-operation recovery.

### Acceptance criteria

- `./gradlew --no-daemon check` passes on the documented JDK.
- Authorization matrices pass for every government form, action, and local/alliance scope.
- Unresolvable or unavailable authority data grants no access.
- The database enforces single-alliance membership.
- Contract materials cannot be duplicated or lost across retries and failures.
- The Paper runtime smoke checklist passes.
- README and living specs describe actual shipped behavior.

## Release 2 — Living Settlements

### Goal

Turn territories from protected map regions into places that players develop and recover together.

### Personal territory standing

Track standing per player and territory. Standing is separate from guild-owned territory development.

- Approved missions and territory activities award standing through one service.
- Standing tiers and thresholds are configurable and durable.
- Defined milestones allow one reward selection from a configured set.
- Reward choices are idempotent and auditable.
- Ownership flips preserve personal standing by default; ownership-specific benefits are recalculated against the new government.

Initial reward categories may include:

- reduced local waystone cooldown or cost;
- gathering bonuses;
- reduced local market or crafting fees;
- increased settlement-storage access;
- cosmetic titles;

Each reward must have one observable effect. Do not ship inert reward entries.

### Town projects

Settlement project boards accept configured contributions toward one or more active projects.

- Projects declare material, currency, activity, and prerequisite requirements.
- Contributions persist contributor totals and project progress atomically.
- Failed inventory or currency writes refund the contributor.
- Completion is committed once, emits an event, and applies the project effect once.
- Operators can inspect, pause, cancel, and recover projects.

### Facility progression

Add durable facility tiers for:

- trading posts;
- storage;
- waystones;
- crafting/refining stations;
- fortifications.

Facility tiers can affect only explicit, testable mechanics:

- storage capacity;
- crafting/refining availability;
- travel cost and cooldown;
- territory bonuses;
- upkeep assessment;
- Release 3 defensive capability.

The exact anchor remains the functional interaction point. Surrounding roleplay construction remains unrestricted by building-shape validation.

### Guild warehouse

Implement shared item storage at active `STORAGE` facilities:

- durable item aggregate and schema;
- inventory UI;
- deposit and withdrawal services;
- role and explicit-permission gates;
- capacity derived from facility tier;
- immutable withdrawal audit log;
- contract material sourcing;
- fail-closed behavior when the facility is inactive or persistence is unavailable.

A storage facility becoming inactive blocks new access but does not delete inventory.

### Recurring invasion lifecycle

Extend administrator-triggered invasions into a player-operable cycle:

```text
threat grows
→ warning
→ invasion
→ defense reward or settlement damage
→ repair project
→ recovery
```

Add:

- configurable threat accrual and eligibility;
- deterministic scheduling and cooldowns;
- advance warnings;
- participation tracking;
- defense rewards;
- facility damage or downgrade;
- repair projects;
- administrator start, cancel, reschedule, and recovery tools.

Interrupted active invasions remain cancelled on restart unless a separate resumable-runtime design is approved. Repair must provide an ordinary player path out of persistent damage.

### Acceptance criteria

- A player can earn standing and select a durable reward with an observable effect.
- A guild can fund and complete a project that upgrades a facility exactly once.
- Warehouse operations are durable, capacity-bound, permission-gated, and audited.
- Contracts can source warehouse materials without duplication or partial debit.
- An automatically scheduled invasion can damage a settlement, and players can restore it through a repair project.
- Every facility tier changes observable gameplay and affects configured upkeep where applicable.

## Release 3 — Territorial Conflict

### Goal

Connect influence, settlement development, guild organization, and fortification through a complete territorial war loop.

### War lifecycle

```text
influence threshold
→ declaration
→ preparation
→ roster lock
→ scheduled battle
→ committed result
→ territory transfer or defense
→ cooldown
```

Implement:

- declaration eligibility from the existing influence engine;
- a configurable preparation window;
- attacker and defender rosters;
- team-size, guild/alliance membership, substitution, and reconnect rules;
- scheduled start, administrative cancellation, and failure recovery;
- durable battle state and war history.

Only one active or scheduled war may own a territory's war slot. Retried schedulers and restart recovery cannot start the same battle twice.

### Minecraft-native battle objectives

Use control-point gameplay rather than copying New World combat mechanics verbatim:

1. Attackers capture configured outdoor points.
2. Capturing the required points unlocks the fort objective.
3. Attackers enter or breach the fort under configured protection rules.
4. Attackers capture the final command point.
5. Defenders win when the battle timer expires first.

The Paper runtime owns region detection and presentation. A Paper-free state machine owns objective ordering, progress, timers, and outcomes.

Required runtime behavior:

- objective bossbars and action bars;
- deterministic capture progress;
- respawn waves;
- protected staging areas;
- war-scoped friendly-fire identity;
- participation and AFK policy;
- bounded cleanup on victory, cancellation, disable, and restart.

### Fortification

Settlement projects and the existing expense path fund:

- fort gates or objective defenses;
- defender respawn timing;
- defender supply access;
- capture resistance;
- repair after war.

All fortification expenses use `EconomyBridge.chargeExpense` with stable idempotency keys. No second treasury or debit path is permitted.

### War resolution

A battle result is persisted before territory ownership changes. Applying the result is idempotent and reconciles:

- governing guild binding;
- influence state and cooldown;
- local policies;
- upkeep responsibility;
- facility damage;
- standing reward applicability;
- squaremap and API projections.

Default transfer semantics:

- personal territory standing survives;
- facilities remain but may carry battle damage;
- policies owned by the previous local government become inactive;
- upkeep remains keyed to the territory and is assessed against the new government from the next defined period;
- influence resets and enters cooldown.

### Minimum diplomacy

Add only the relationships required for war behavior:

- allied;
- neutral;
- hostile;
- truce.

Use them for declaration eligibility, roster participation, and friendly-fire identity. Defer treaties, embargoes, and broader diplomatic simulation.

### Acceptance criteria

- Influence can produce a scheduled war without direct database edits.
- Battle state and outcomes survive restart without duplicate starts or duplicate ownership transfer.
- Exactly one committed result controls each war.
- Territory transfer updates all dependent systems consistently or enters an operator-visible reconciliation state.
- Fortification and settlement investment measurably affect battle behavior.
- Runtime verification covers attacker victory, defender victory, cancellation, participant reconnect, disable, and restart recovery.

## Cross-release engineering requirements

### Persistence and idempotency

- SQL remains the only durable authority.
- Money movement uses the existing economy rail.
- Item removal and durable credit occur transactionally or use explicit compensation.
- Scheduled work uses stable period or operation identifiers.
- Restart recovery is specified for every non-terminal state.

### Security

- Authorization resolves current membership, role, scope, and action at mutation time.
- Cached identity cannot independently authorize a durable mutation.
- Invalid or unavailable authority data fails closed.
- Administrative operations are auditable.

### Testing

- Domain state machines and calculations receive deterministic unit tests.
- SQL invariants receive integration tests against each supported database dialect where behavior differs.
- Paper listeners and commands receive focused behavior tests.
- Each release ends with a real Paper runtime smoke test for world-facing behavior.

### Documentation

At each release boundary:

- update the affected living specs;
- remove stale checked or unchecked claims;
- document public integration events and services;
- provide operator recovery steps;
- record intentional scope and compatibility decisions.

## Sequencing rationale

Release 1 is a prerequisite: war, storage, projects, and settlement authority cannot safely build on ambiguous local/alliance permissions or fail-open resolution. Release 2 creates reasons to own, improve, defend, and repair territory. Release 3 then makes those investments strategically meaningful through territorial conflict. This order minimizes parallel authority models and ensures each release delivers one complete player loop rather than another disconnected API surface.
