# Standing — Living Spec

> Status: active  
> Last updated: 2026-08-08  
> Related: `docs/superpowers/specs/2026-08-07-standing-bonuses-design.md`

## Intent

New World–style **territory development**: activity by the governing guild’s
members inside their territory accrues **standing**, raising a **development
tier** that grants harvest multipliers and multiplies that guild’s **influence
accrual in other territories**.

This is a development curve — not a race: no decay, no declare/flip, keyed per
territory per governing guild.

## Boundaries

### In scope

- Standing `[0, cap]` for `(territoryId, guildId)` where guild is governor.
- Accrual sources: PvP kill, PvE kill, block break (no place/craft spam).
- Configurable tiers via `bonuses.json` (thresholds, harvest + influence multipliers).
- Harvest bonus on block drops and mob drops for governing-guild members in T.
- Influence multiplier read by **influence** engine.
- Pure-domain `StandingEngine` + Postgres store; Paper listeners.
- Admin readout/reset; web GET standing.

### Out of scope / non-goals

- Per-player standing / missions track.
- Decay, cooldowns, wars.
- Mutating influence state machine (read-only multiplier hook only).
- Taxes or treasury effects from tiers (**economy**).
- Block place or crafting as standing sources.

## Invariants

1. **Only governing guild members** accrue and receive harvest bonus in that territory.
2. **No defender push-pull** — owner is sole standing actor.
3. **No decay.**
4. Tier = highest config tier with `threshold <= standing`.
5. Harvest never regenerates full loot tables; adds bonus stacks from canonical
   event drop lists (see harvest listener contract).
6. Load failure fails closed where engine marks unusable.
7. Domain free of Bukkit; listeners in `paper`.

## Implementation guidance

| Layer | Location |
|-------|----------|
| API | `api/.../standing` |
| Engine + config + Postgres | `common/.../standing` |
| Harvest + accrual listeners | `paper/.../standing` |
| Seed resource | `paper` packaged `bonuses.json` |

- Seed `bonuses.json` on first enable without overwriting admin edits.
- Influence asks `StandingService.influenceMultiplierFor(guildId)` — keep API stable.
- Player victims must not grant entity harvest bonuses.

### Testing

- Accrual eligibility; tier thresholds.
- Harvest: tool-aware block drops; no player-victim bonus; mutating priority not MONITOR-only spawn.
- Influence multiplier integration smoke.

### Do not

- Double-apply harvest via world drop + event list.
- Treat standing as personal XP.
- Write influence bars from standing package.

## Current

### Capability (shipped)

- [x] `StandingEngine` / `StandingService`
- [x] `StandingConfig` / loader + packaged `bonuses.json` seed
- [x] Postgres standing store
- [x] Accrual listener wiring
- [x] `HarvestBonusListener` with corrected drop handling (tool-aware, non-player victims)
- [x] Influence multiplier integration
- [x] Web GET standing

### Open on the current surface

- [ ] Admin command completeness for readout/reset (verify vs design)
- [ ] Document mob-drop Looting scope in release notes (canonical event list, not pre-Looting isolation)
- [ ] Ops: what happens when governing guild changes — standing key lifecycle

### Current notes

New-world-completeness plan fixed harvest listener correctness; keep regressions
covered when touching drop code.

## Next

- [ ] External plugin read API polish
- [ ] Metrics: standing gain rate per territory
- [ ] Config schema versioning policy for `bonuses.json`

## Future

- [ ] Per-player standing / missions (separate domain)
- [ ] Crafting or place as sources (only if anti-farm design exists)
- [ ] Alliance-level development track

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-07 | Per-territory per-governing-guild keys | Settlement development, not personal XP |
| 2026-08-07 | No place/craft standing sources | Anti-farm |
| 2026-08-07 | Influence multiplier only, no state coupling | Keep engines separable |
| 2026-08-08 | Harvest uses tool-aware drops + event list mutation | Correct Paper semantics |

## Open questions

- [ ] Reset standing on ownership flip or preserve orphan keys until GC?
- [ ] Should harvest multiplier apply to player-placed crop farms only, or all breaks?
