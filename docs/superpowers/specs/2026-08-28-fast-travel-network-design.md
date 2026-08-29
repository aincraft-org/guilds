# Fast Travel Network Design

> Status: approved design
> Date: 2026-08-28

## Intent

Add guild-owned fast-travel infrastructure for local crystal travel, allied
crystal travel, boats, and airships without weakening the existing ownership,
territory, or endpoint rules. Each player also has a finite personal travel
currency that is consumed by every fast-travel mode.

Fast travel has four independent gates:

1. **Point construction eligibility** — the facility is allowed in its territory,
   its owning guild has the required upgrade, its quota permits creation, and
   its local physical rule passes.
2. **Endpoint compatibility** — a travel mode can only connect to the same mode.
3. **Authorization and route eligibility** — the traveler, current governance,
   alliance relationship, territory policies, and (for boats) world geometry
   permit the trip.
4. **Personal currency transaction** — the traveler has enough balance for the
   computed cost and a durable reservation/commit succeeds. Failed validation,
   cancellation, and failed arrival do not consume currency.

No cached route, remote-travel upgrade, or guild resource balance may bypass any
of these gates.

## Scope

### In scope

- Registered `GUILD_CRYSTAL`, `TELEPORT_TERMINAL`, `BOAT`, and `AIRSHIP`
  facilities.
- Guild tech-tree capabilities for local crystal travel, remote allied-crystal
  travel, boats, and airships.
- Removal of `/g spawn` and the current hearthstone-item spawn teleport.
- Per-territory fast-travel quotas and cross-territory mode policy.
- Shoreline/open-water validation for boats.
- Launch-platform/open-sky validation for airships.
- Same-guild and same-alliance endpoint authorization.
- Cached connectivity-only boat route checks that do not block the Paper tick.
- Player-bound finite travel-currency wallets, configured starter balances and
  capacity, and gameplay reward providers.
- Global distance and mode cost parameters.
- Durable expiring trip reservations with cancellation, failure, and restart
  recovery.

### Out of scope

- Replacing or changing existing `WAYSTONE` mechanics beyond applying the
  personal travel-currency charge.
- Cross-world travel.
- Retaining a boat route path for sailing animation, previews, or navigation.
- Explicit persisted alliance endpoint-link records.
- A 3D airship corridor/path search.
- Player-facing web UI for editing territory policy unless the existing editor
  is deliberately extended in a later slice.
- Reusing guild upgrade resources or guild money as the personal travel
  currency.

## Facility and mode model

Existing `WAYSTONE` mechanics remain unchanged apart from the explicitly added
personal travel-currency charge. Add these registered facility types:

- `GUILD_CRYSTAL` — the guild’s spawn destination and a remote crystal travel
  endpoint.
- `TELEPORT_TERMINAL` — the guild’s local departure structure.
- `BOAT` — a water-connected transport endpoint.
- `AIRSHIP` — an air transport endpoint.

The travel service maps these facilities to compatibility modes:

| Facility | Compatibility mode | Same-mode destination |
|---|---|---|
| `GUILD_CRYSTAL` | `CRYSTAL` | `GUILD_CRYSTAL` |
| `TELEPORT_TERMINAL` | `LOCAL_TERMINAL` | local terminal flow only |
| `BOAT` | `BOAT` | `BOAT` |
| `AIRSHIP` | `AIRSHIP` | `AIRSHIP` |

`TELEPORT_TERMINAL` is an origin structure for the crystal flow, not a remote
crystal destination. A `GUILD_CRYSTAL` is both a destination and an interactive
departure point so a player arriving at an allied crystal can return or continue
through the same authorized crystal network.

Facilities continue to use `SettlementFacility` and `FacilityRegistry`. Route
paths, water components, and alliance links are not durable facility data.

## Upgrade capabilities

The existing `fast_travel` tech node retains its 50%
`teleport_cooldown_reduction` effect and additionally unlocks local crystal and
terminal infrastructure. Its player-facing definition must describe both
effects. The 50% modifier remains scoped to the existing `WAYSTONE` travel
service; crystal, BOAT, and AIRSHIP cooldowns use their own policies until a
separate perk explicitly extends the modifier. Removing `/g spawn` does not
discard the existing waystone perk effect.

Add these independent nodes beneath the existing infrastructure branch:

- `remote_crystal` — permits a guild’s residents to use another guild’s crystal
  when that guild is in the same alliance and all territory/endpoint checks pass.
- `boat_travel` — gates construction and use of `BOAT` facilities.
- `airship_travel` — gates construction and use of `AIRSHIP` facilities.

The traveler’s guild must hold the required mode capability. The owning guild
must hold the capability required to create and maintain its endpoint. The
remote-crystal node alone never bypasses alliance membership, territory policy,
endpoint activity, or compatibility.

## Personal travel currency

Each player has a persistent, non-transferable travel-currency wallet keyed by
their player UUID. The wallet has a configured maximum balance and a small
configured starter grant when first created. A reward that would exceed the
maximum is clamped; it never creates spendable balance above the cap.

Every fast-travel mode consumes the currency, including existing `WAYSTONE`,
`GUILD_CRYSTAL`, `BOAT`, and `AIRSHIP` travel. The existing
`teleport_cooldown_reduction` effect remains a separate WAYSTONE cooldown
modifier and does not waive or reduce currency cost.

Global configuration supplies a base cost, a distance divisor, and a mode
multiplier. The canonical cost is
`ceil(baseCost + modeMultiplier * distance / distanceDivisor)`. WAYSTONE,
crystal, and AIRSHIP use straight-line endpoint distance. BOAT uses the
validated navigable-water route length returned by the connectivity search.
The route result may retain this scalar length for the active request, but no
boat path is persisted.
Configured reward providers may award currency for quest completion,
exploration milestones, and supported guild activity events. Each award is
attributed to the event actor: quest completion goes to the player whose
contribution crosses the completion threshold, exploration goes to the player
who reaches the milestone, and guild activity goes to its initiating player.
Providers use a stable source/event identity so retries cannot award the same
event twice, and all awards pass through the wallet cap. Reward configuration
is global; it does not grant currency through guild resource or guild-money
balances. An event without a player actor cannot award personal currency.

Currency is reserved only after origin, destination, compatibility,
authorization, route, cost, and landing checks pass. A durable reservation
records the player, trip, amount, and expiry. Starting travel atomically creates
the reservation; arrival commits it, while cancellation, failure, disconnect
recovery, or expiry releases it. A second concurrent trip cannot reserve the
same units.

## Ownership, placement, and cardinality

A transport facility must resolve to a territory governed by a guild. Effective
ownership is derived from the current territory binding at authorization time;
facility records do not contain a second membership authority.

Global cardinality:

- At most one persisted `GUILD_CRYSTAL` record per guild and at most one
  persisted `TELEPORT_TERMINAL` record per guild; inactive records reserve the
  slot.
- The crystal must match the guild’s current persisted spawn location and world.
  Its spawn location must resolve inside a territory governed by that guild.

If a guild spawn moves, the old crystal becomes inactive until explicitly
removed or replaced. If the territory loses governance, its transport facilities
become inactive immediately. A later governance rebind must revalidate the
facility against the new effective owner, upgrade, quota, physical anchor, and
territory policy before it becomes usable; it must not grant access while the
territory is ungoverned.

Repeatable `BOAT` and `AIRSHIP` facilities are limited by territory policy. A
territory can therefore provide one or more transport endpoints per guild in
separate territories without multiplying the guild’s global crystal or terminal.

## Territory fast-travel policy

Add an immutable `FastTravelPolicy` to `Territory` and persist it in the existing
territory JSON/SQL document. The policy contains two independent values:

1. **Per-type quota:** maximum persisted facilities of each type for one
   effective owning guild in that territory.
2. **Cross-territory modes:** the set of transport modes allowed to cross that
   territory boundary.

A quota does not imply cross-territory permission. Cross-territory permission
does not create capacity.

Quota rules:

- Counts are separate per facility type, scoped by
  `(territory, facility type, owning guild)`.
- Inactive but persisted records count toward capacity.
- Creation, deletion/replacement, and explicit reactivation count and revalidate
  the candidate registry atomically before durable save.
- Travel-time authorization does not enforce quotas on an already-existing
  facility. If an operator lowers a quota below the current count, existing
  facilities remain usable when their other checks pass; new creation remains
  blocked until records are manually removed.
- The global one-per-guild crystal and terminal limits also count inactive
  records and remain stronger than any territory quota.

Boundary rules:

- A BOAT, AIRSHIP, or remote CRYSTAL trip must use different territory IDs.
- Both the origin and destination territories must allow that mode in their
  `crossTerritoryModes` policy.
- Local `TELEPORT_TERMINAL` → own `GUILD_CRYSTAL` is allowed within one
  territory. If the terminal and crystal are in different territories, the
  `CRYSTAL` cross-territory policy must allow the trip; same-guild travel does
  not require `remote_crystal`.
- Alliance membership never overrides a territory’s cross-territory policy.

## Construction validation

Construction validation is separate from travel authorization.

### Common checks

Every new transport facility must:

1. Resolve its anchor to a territory.
2. Require a non-empty current governing guild.
3. Confirm the owning guild’s required tech node.
4. Confirm the per-type quota and global cardinality rules.
5. Persist through `FacilityMutationService`’s candidate-registry,
   durable-save, then live-publication sequence.

### Guild crystal

`GUILD_CRYSTAL` placement must match the guild’s current persisted spawn world and
block coordinates. The spawn must resolve inside a territory governed by the
same guild. Moving the spawn does not silently move the facility.

### Teleport terminal

`TELEPORT_TERMINAL` placement must be inside a territory governed by the owning
guild. At most one persisted terminal record is allowed globally per guild;
inactive records reserve the slot. Its interaction sends an eligible resident to
the guild’s own crystal and may expose allied crystal destinations only when
`remote_crystal` is unlocked.

### Boat

`BOAT` placement performs a bounded local shoreline validation. The configured
anchor must have a navigable water-entry window adjacent to it. Placement never
runs a world-scale path search.

The initial navigable-water predicate treats contiguous water surface cells with
clear boat space above them as navigable. Anchor materials, entry-window size,
and any maximum local inspection radius are configuration, not hard-coded
territory rules.

### Airship

`AIRSHIP` placement requires its configured anchor plus a launch platform and
clear sky/vertical clearance near the anchor. It does not perform a 3D route
search. Anchor materials and clearance are configuration.

## Travel authorization and flow

The travel service evaluates checks in this order:

1. Origin exists and is active.
2. Destination exists and is active.
3. Endpoint compatibility matches exactly.
4. Traveler identity and current effective guild membership are resolved.
5. Traveler capability and owning-guild capability are present.
6. Same-guild or same-alliance relationship is valid for the mode.
7. Territory IDs and both boundary policies are valid.
8. Boat connectivity is proven when the mode is BOAT.
9. Destination landing/protection checks pass.
10. The canonical distance and mode-specific currency cost are computed.
11. A durable personal-currency reservation is created atomically.
12. Existing warmup, cancellation, and safe-landing behavior commits the
    reservation on arrival or releases it on cancellation/failure.

If any check before reservation fails, the wallet is unchanged. Authorization
and route checks are rerun when a resumed or delayed trip commits; a changed
governance, alliance, endpoint, policy, or route releases the reservation.

### Local crystal travel

A resident physically interacts with the guild’s one `TELEPORT_TERMINAL` and is
sent to the guild’s `GUILD_CRYSTAL`. This local trip may remain within one
territory and replaces `/g spawn`.

### Remote crystal travel

A resident of guild A may use an interactive crystal owned by guild B only when:

- guild A has `remote_crystal`;
- guild B has an active `GUILD_CRYSTAL` from `fast_travel`;
- guild A and guild B are members of the same current alliance;
- origin and destination territory IDs differ; and
- both territories allow `CRYSTAL` cross-territory travel.

The destination crystal is interactive after arrival. The player may return to
their own crystal or select another eligible allied crystal. All checks rerun on
every departure; arrival never grants a standing exception.

### Boat and airship travel

A resident may use matching active facilities owned by their guild. Alliance
membership extends the network to matching active facilities owned by member
guilds. The traveler’s guild must hold the relevant mode capability, and both
endpoint guilds must have held the capability needed to create their endpoints.

BOAT requires a valid connectivity result. AIRSHIP has no terrain route check in
this design; its endpoint and territory checks still apply.

## Boat connectivity and performance

Boat routing proves connectivity only and stores no route path.

The BOAT route result includes only the scalar navigable distance needed for the
active currency calculation; no route path is retained in durable state.

The route checker uses a lazy two-level index:

1. Capture safe chunk snapshots in bounded main-thread batches using Paper-safe
   world access.
2. Analyze chunk water masks off-thread.
3. Search the chunk graph with bidirectional A\* or equivalent bounded search;
   do not run an unbounded block DFS on the Paper thread.
4. Cache geometry results by world, endpoint pair, and water-topology revision.
5. Invalidate changed chunks, neighboring chunk boundaries, and dependent route
   entries on water-affecting block changes.

A cache stores geometry only. Governance, alliance membership, upgrades,
facility activity, and territory policies are always rechecked immediately
before travel. Quotas are enforced at creation, deletion/replacement, and
explicit reactivation, not on an already-active facility at travel time. An
unloaded, invalidated, or budget-exhausted scan returns pending/unavailable and
cannot start travel.

## Persistence and lifecycle

- New facility enum values serialize through the existing facility document codec;
  old `WAYSTONE`, `TRADING_POST`, `STORAGE`, and `BANK` records remain readable.
- `FastTravelPolicy` is optional in older territory documents and receives a
  validated default when absent.
- Existing tech unlock rows remain the source for new node capabilities. The
  `fast_travel` definition and UI text are updated deliberately rather than
  inventing a second upgrade store.
- No explicit alliance-link table is added.
- Facility removal uses the same atomic durable mutation path as creation.
- Governance, spawn, alliance, and water changes never silently grant stale
  access; they invalidate or fail the relevant runtime checks.
- Player wallets, reward-event idempotency, and trip reservations use the
  existing SQL migration/persistence stream. They are not stored in
  `guild_resources` or guild balance columns.
- Reservation expiry and orphan cleanup run during normal trip recovery and
  plugin startup; cleanup releases funds without granting a travel.

## Interaction and failure behavior

Use the existing physical facility interaction, clickable destination, warmup,
cancellation, and safe-landing patterns. Do not add a command that recreates
`/g spawn` or bypasses the terminal.

Failures must identify the relevant category where possible: missing upgrade,
quota exceeded during construction, ungoverned territory, wrong/inactive anchor,
spawn mismatch, endpoint type mismatch, same-territory remote attempt, policy
denial, non-allied destination, unavailable water route, pending scan, unsafe
landing, or protection denial.
Insufficient personal currency and an expired or unavailable reservation are
distinct failure categories from authorization denial. A rejected, canceled,
or failed trip leaves the wallet unchanged after any reservation release.

A failed durable facility or territory-policy save leaves the live registry or
territory unchanged.

## Implementation seams

Expected integration points:

- `guilds-api/.../model/FacilityType` — new facility enum values and any pure
  `FastTravelPolicy`/mode model.
- `guilds-api/.../model/Territory` — immutable policy field and copy/accessor
  methods.
- `guilds-api/.../registry/FacilityRegistry` — facility cardinality/count helpers
  used by atomic candidate validation.
- `guilds-common/.../persist/PostgresFacilityStore` and territory JSON codec —
  new type/policy serialization with old-document defaults.
- `guilds-paper/.../territory/building` — mode-specific validators, authorization,
  interaction, quota checks, and generalized travel service.
- `guilds-paper/.../models`, `config`, `services`, and `gui` — tech nodes,
  capability queries, upgrade text/layout, and persisted unlock behavior.
- `guilds-paper/.../commands` and listeners — remove `/g spawn` and hearthstone
  spawn flow; expose new physical facility types through existing building UX.
- `guilds-paper/src/main/resources/techtree.yml` and building configuration —
  node definitions, anchor materials, clearance, route limits, and defaults.
- `guilds-paper/.../services` — personal wallet, configurable reward-provider,
  and durable reservation services with atomic debit/commit/release semantics.
- Existing quest, exploration, and guild-activity event seams — idempotent
  currency awards keyed by stable event identity.

Do not introduce a second registry, membership source, persistence backend, or
alliance-link store.

## Verification contract

Pure tests must cover:

- facility-mode compatibility;
- `FastTravelPolicy` validation and independent quota/boundary settings;
- inactive-record counting and lowered-quota behavior;
- old facility/territory document defaults;
- local same-territory exception versus remote cross-territory enforcement.

Paper tests must cover:

- crystal-to-spawn reconciliation and one-per-guild cardinality;
- terminal construction and local interaction;
- removal of command/item spawn paths;
- `fast_travel`’s 50% cooldown modifier applying to `WAYSTONE` only and not
  implicitly affecting crystal, BOAT, or AIRSHIP travel;
- personal-wallet persistence, cap/starter-grant behavior, and reward-event
  idempotency;
- distance/mode cost calculation, including route-aware BOAT distance;
- atomic reservation under concurrent travel requests, cancellation/failure
  release, expiry, and restart recovery;
- boat shoreline and airship launch validation;
- construction quota atomicity and failed-save rollback;
- same-guild and same-alliance authorization;
- remote-crystal return from an allied destination;
- governance/alliance changes invalidating access without relying on stale cache;
- boat connectivity, cache reuse, invalidation, and bounded/unavailable scans;
- insufficient-currency and reservation failure behavior without bypassing
  authorization;

Runtime smoke coverage must exercise real facilities across multiple territories,
including a connected/disconnected water pair, alliance membership changes, spawn
movement, policy changes, quota reduction, and safe landing. The test server
must remain responsive during route validation.

- reward-driven replenishment and finite-wallet depletion across multiple
  players and travel modes.
