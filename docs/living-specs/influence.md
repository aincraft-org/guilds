# Influence — Living Spec

> Status: active  
> Last updated: 2026-08-08  
> Related: `docs/superpowers/specs/2026-08-06-territory-influence-design.md`

## Intent

Provide a New World–style **territory influence race**: rival guilds accrue
influence inside enemy land through activity; at cap they may **declare**; after
a countdown the territory **flips** ownership to the challenger.

Gated on **alliance membership**: both sides must be in (different) alliances;
same-alliance and unaffiliated guilds cannot contest.

Success looks like: pure-domain engine in `common`, restart-safe Postgres state,
thin Paper listeners, optional standing multiplier hook, squaremap/API readout.

## Boundaries

### In scope

- Per-territory attacker bars `[0, cap]`.
- Accrual sources: PvP kill, PvE kill, block break/place, craft.
- Defender push-pull (owner guild activity reduces attacker bars).
- Eligibility rules (governed territory, alliances, cooldown, no active declare).
- Declare → countdown → flip ownership (`governedByGuildId`) + post-flip cooldown.
- Persistence via `PostgresInfluenceStore`; load failure fails closed.
- Read API (`InfluenceService` / web `/api/influence`) and player status UX.
- squaremap influence layer (via **map** domain).

### Out of scope / non-goals

- Actual war/siege battles or combat arenas.
- Paid influence from treasury.
- Passive decay over time.
- Changing guilds DB role keys for renames beyond coordinated workstreams.

## Invariants

1. **Alliance gate:** attacker and owner must each belong to an alliance; alliances must differ.
2. **Bars clamp** to `[0, cap]`; persistence precision per engine (e.g. 2 decimals).
3. **Flip is journaled:** ownership change persisted; crash recovery revalidates.
4. **Post-flip cooldown** blocks new race until expiry; bars reset per design.
5. **No accrual** for ineligible actors; same-alliance PvP does not feed race.
6. **Standing multiplies** attacker accrual when `StandingService` wired — standing owns multipliers.
7. Domain free of Bukkit; Paper only in listeners/commands/formatters.
8. **Load failure fails closed** (no silent empty race that can be exploited).

## Implementation guidance

| Layer | Location |
|-------|----------|
| API contracts | `api/.../influence` |
| Engine + state + Postgres | `common/.../influence` |
| Listeners / status | `paper/.../influence` |
| Map layer | `paper/.../squaremap` (**map**) |

- Ownership flip uses `OwnershipPersister` seam into territory persistence —
  do not write guild tables from the influence package.
- Re-check eligibility at declare time, not only at accrual.
- Keep influence state machine out of standing/economy packages.

### Testing

- Eligibility matrix (unaffiliated, same alliance, cooldown, active declare).
- Accrual + defender push-pull clamping.
- Declare / flip / cooldown restart recovery.
- Standing multiplier applied once at accrual.

### Do not

- Accrue for plot-only logic bypassing territory resolve.
- Auto-declare at cap without explicit declare action.
- Implement siege combat inside this domain.

## Current

### Capability (shipped)

- [x] `InfluenceEngine` / `InfluenceService` pure-domain race
- [x] Configurable sources and caps (`InfluenceConfig` + loader)
- [x] Paper accrual listeners
- [x] Declare / pending flip / ownership flip path
- [x] Postgres influence store + recover
- [x] Standing influence multiplier hook
- [x] Status formatter / status task
- [x] Web GET influence + squaremap influence layer

### Open on the current surface

- [ ] Player-facing declare command discoverability / docs
- [ ] Align any remaining guild/nation vocabulary in user messages (rename workstream)
- [ ] Ops runbook: failed load closed behavior and recovery

### Current notes

Design originally mentioned `influence.json`; **Postgres is sole durable store**
after unified persistence cutover.

## Next

- [ ] External war-plugin read API polish / versioning
- [ ] Admin force-reset / cancel-declare tools if missing from commands
- [ ] Telemetry: flip rate, average race duration

## Future

- [ ] Alliance-owned territory overlay (called out deferred in completeness plan)
- [ ] Paid influence or decay (explicitly non-goal unless product reverses)
- [ ] Siege event integration hooks

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-06 | Alliance membership required both sides | Prevents unaffiliated / internal contests |
| 2026-08-06 | Push-pull defense, no decay | Active defense, not idle timers |
| 2026-08-06 | Explicit declare + countdown | Drama window; not instant flip at 100% |
| 2026-08-06+ | Postgres not JSON for race state | Unified persistence |
| 2026-08-07+ | Standing multiplies foreign influence accrual | Development curve feeds contest power |

## Open questions

- [ ] Should craft accrual require a valued craft API or any craft event?
- [ ] Broadcast/announcement standards on flip across servers?
