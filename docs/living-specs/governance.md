# Governance — Living Spec

> Status: active  
> Last updated: 2026-08-17  
> Related: `docs/superpowers/specs/2026-08-08-scope-aware-governance-invariants-design.md`

## Intent

Make **who rules land and what they may decree** player-comprehensible and
fail-closed:

> A **guild** government controls its guild; an **alliance** government controls
> what guilds do together.

Territory records an optional guild binding; membership, roles, and forms live
in the **guilds** subsystem and flow in as DTO snapshots (`GuildBody`,
`AllianceBody`) via `GovernanceSource`.

Success looks like: protection, policy, and influence all resolve government
through one registry; invalid membership/electorate/bindings never silently
become anarchy or wilderness; federal vs local scope is explicit.

## Boundaries

### In scope

- Government forms: `ANARCHY`, `MONARCHY`, `OLIGARCHY`, `DEMOCRACY`.
- Seats / electorate derivation for guild and alliance scopes.
- Policies: propose → vote/decree → `PASSED` / `REJECTED` as decision records.
  Structured decree-effect payloads are parked until a wiring path exists.
- `GovernanceRegistry` + `GovernanceSource` resolution for territory, member,
  world location.
- Formal authority (`SovereignAction`) and land protection (`BlockProtection`).
- Paper listeners that enforce protection on block/entity/environment events.
- Invariant hardening and **scope-aware** local vs alliance model (design approved).

### Out of scope / non-goals

- Guild command UX, plots, tech tree, storage inventory (**guilds** / **guild-storage**).
- Tax settlement and treasuries (**economy**).
- Influence race state machine (**influence**).
- Company-level friendly-fire identity or personal home registration (called out
  in README as not expressible yet).

## Invariants

1. **Single membership source:** guilds DB via `GovernanceSource`; no parallel
   RegionGuild/TerritoryAlliance truth.
2. **Binding fail-closed (target):** present but unresolvable `governedByGuildId`
   is unresolved/deny — not silent fallthrough to permissive wilderness.
3. **ANARCHY local:** no formal land permission system; land is wild for
   members and outsiders for formal gates (environmental flags per form rules).
4. **Plot ownership absolute** under every assigned local form (guild domain
   supplies plot facts; protection honors them).
5. **Alliance form does not replace local land ownership** under the target
   federal model (current code still has alliance-supremacy paths — see Current open).
6. **Policy effects** only apply when status is PASSED (economy tax path).
7. Pure domain permission/governance types: no Bukkit in `api`/`common`.
8. **Invalid governments rejected at boundaries** (target): no silent null→anarchy
   factories for production paths.

## Implementation guidance

| Concern | Location |
|---------|----------|
| Forms, seats, policies | `api/.../model` (`Government`, `Policy`, `PolicyRules`) |
| Goods catalog (economy) | `api/.../economy` |
| DTOs / rules | `api/.../permission` |
| Registry + BlockProtection | `common/.../permission` |
| Guild adapter | `paper/.../guilds/GuildsGovernanceSource` |
| Listeners | `paper/.../listener/ProtectionListener`, `InteractionProtectionListener` |

### Target federal model (approved design)

- Introduce explicit `GovernanceContext` with local body + optional alliance
  rather than a single “effective body” that overwrites the guild with its alliance.
- Local scope owns membership admin, local policy, guild commons, plots.
- Alliance scope owns alliance membership, alliance policy, influence declare
  gates, federal strategy — **not** automatic local block rights for alliance officers.
- Sibling alliance members get local rights only via public guild or explicit grants.

### Testing

- Form × action matrices for local land.
- Unresolvable binding deny paths.
- Policy propose/vote/decree eligibility.
- Listener coverage for break/place/interact/environment/PvP/teleport gates.

### Do not

- Reintroduce standalone in-memory nation/guild models as authority.
- Grant all `SovereignAction`s from a single unchecked seat check once scope
  splits (design: action-aware rules).
- Treat DB read failure as “empty permissions / default monarchy” without
  surfacing fail-closed behavior (hardening goal).

## Current

### Capability (shipped)

- [x] Government forms and seat roles on guild/alliance via guilds
- [x] Policy propose / vote / decree on territory-local and governance registry paths
- [x] `GovernanceRegistry` resolve for territory / member / location
- [x] `BlockProtection` layered land + environment gates
- [x] Paper protection listeners wired on enable
- [x] Guild toggles influence fire/explosions/mobs/pvp on governed land
- [x] Public-guild outsider place/interact (never break) behavior

### Open on the current surface (known gaps / hardening)

- [ ] Scope-aware `GovernanceContext` (local vs alliance) — design approved, not landed
- [ ] Stop alliance form from rewriting local plot/commons rights
- [ ] Alliance officers do not inherit local land authority by default
- [ ] Action-aware `PermissionRules.allows(SovereignAction)` (stop ignoring action)
- [ ] `GuildBody.permissionsOf` membership check before map lookup
- [ ] Fail-closed unresolvable `governedByGuildId`
- [ ] Defensive validation on `Government` / body factories (no silent anarchy)
- [ ] DB failure ≠ empty grants / silent monarchy default
- [ ] Single-alliance membership invariant enforced in data or domain
- [ ] Dual listener agreement (territory protection vs guild public-access) documented and tested together

### Current notes

Baseline tests encode some **current** alliance-supremacy behavior; scope-aware
work must revise tests deliberately, not preserve them by accident.
See design § “Current Architecture Findings”.

## Next

- [ ] Implement scope-aware governance plan (`docs/superpowers/plans/2026-08-08-scope-aware-governance-invariants.md` when executing)
- [ ] Split formal authority vs land authority cleanly in public API docs
- [ ] Align README government tables with post-hardening reality after ship

## Future

- [ ] Elected alliance `delegate` beyond mayor default
- [ ] Company / party friendly-fire identity
- [ ] Per-player home registration into claims
- [ ] Re-attach structured decree effects (`DecreeEffects` / tax payload) once a player or plugin path exists
- [ ] Policy world-enforcement beyond tax effects (buffs, toggles-as-decree)

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| (core) | Guilds = local governments; alliances = federal | Match New World-ish mental model; one membership DB |
| (core) | Territory only stores optional guild binding | Avoid dual governance stores |
| (core) | Form IS permission structure for formal seats | Fewer parallel rank systems |
| 2026-08-08 | Approve scope-aware federal model + invariant hardening | Current single “effective body” is wrong for players |
| 2026-08-08 | Unresolvable binding deny, not anarchy fallback | Fail closed on corruption/stale ids |
| 2026-08-17 | Park decree effects until wired | Domain payload and transcriber had no command/runtime creation path |

## Open questions

- [ ] Exact API break surface for `resolveForTerritory` callers after context split?
- [ ] Which environmental flags remain territory-local-government-always-protected?
- [ ] Migration path for territories currently relying on alliance land supremacy?
