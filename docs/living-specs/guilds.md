# Guilds — Living Spec

> Status: active  
> Last updated: 2026-08-19  
> Related: archived docs under `docs/archived-guilds/docs/`;  
> `docs/superpowers/specs/2026-08-07-guild-contracts-design.md`

## Intent

Provide the **player organization layer**: guilds (guilds) and alliances, with
plots, residents, roles/permissions, chat, progression (levels, resources, tech
tree, specializations, quests), and service APIs that feed **governance**.

Ships **in the same Paper plugin JAR** as Guilds — one main class,
shared PostgreSQL, composition root `GuildsServices` / `GuildsGovernanceSource`.

Success looks like: players run guilds through Brigadier commands; territory
protection and influence see consistent membership DTOs; progression and
contracts are durable and transactional where money/items move.

## Boundaries

### In scope

- Guild and alliance lifecycle, membership, roles, toggles.
- Plot claim/ownership/types and plot permissions.
- Permission evaluation for guild context (feeds land with **governance**).
- Chat, broadcasts, hearthstone, public-access listeners.
- Levels (XP-only) / project skill points / tech-tree projects / resource bank / specializations / quests.
- Guild contracts service API (escrow materials for level costs).
- Brigadier command surface (`/guild*`, `/alliance*`, plot/perm/map, …).
- Config: `guilds-config.yml`, `techtree.yml`, level definitions.
- SQL schema + migrations under guilds database package (Postgres).

### Out of scope / non-goals

- Spatial territory polygons (**territory**).
- Vault settlement tax (**economy**) — guild `balance` is guilds-internal currency field unless explicitly bridged.
- Shared item bank UI (**guild-storage**).
- Influence race engine (**influence**).

## Invariants

1. **One membership source of truth** in Postgres guilds schema.
2. A guild is in **at most one alliance** (enforce; do not rely on “first match”).
3. **Governance DTOs** (`GuildBody` / `AllianceBody`) must not grant permissions
   to non-members (stale map keys denied) — coordinate with **governance** hardening.
4. Progression deposits that remove inventory must **refund on failed DB write**.
5. Level upgrade rechecks locked row; consumes XP progress once; skill points equal current level.
6. Contract escrow: debit on post; release on fulfill; refund on cancel; no post
   without affordability.
7. No Bukkit types in pure territory `api`/`common`; guilds code lives under
   `paper/.../org.aincraft.guilds` and may use Paper APIs.

## Implementation guidance

| Area | Location |
|------|----------|
| Services | `paper/.../org.aincraft.guilds.services` (+ `impl`) |
| Models | `paper/.../org.aincraft.guilds.models` |
| Commands | `paper/.../org.aincraft.guilds.commands` |
| DB / migrations | `paper/.../org.aincraft.guilds.database` |
| Governance bridge | `GuildsGovernanceSource` |
| Composition | `GuildsServices` from `GuildsPlugin` |

- Prefer service interfaces for contracts/levels so territory domain stays free of guild SQL.
- When changing form or permission defaults, update **governance** living spec and form matrices together.
- Historical MockBukkit suite is archived under `docs/archived-guilds-test/` — new tests should live with the paper module.

### Testing

- Permission evaluation and plot form matrices.
- Deposit/upgrade atomicity and refund paths.
- Contract create/fulfill/cancel escrow balances.
- GovernanceSource snapshot correctness for forms and member sets.

### Do not

- Split guilds into a second plugin JAR without an explicit product decision.
- Reintroduce SQLite `guilds.db` beside Postgres.
- Let `/guildlevel deposit` skip persisting `upgrade_progress` (known historical footgun — contracts path fixed via direct SQL).

## Current

### Capability (shipped)

- [x] Guild / alliance models and services on shared Postgres
- [x] Brigadier commands (guild, alliance, plot, perm, chat, map, quests, tech, …)
- [x] Plot type system and handlers
- [x] Permission service + public access / toggle listeners
- [x] `GuildsGovernanceSource` for territory governance
- [x] Level deposit / upgrade progression (XP-only; skill points = guild level)
- [x] Tech tree projects (one active at a time) / specialization / quest services
- [x] Guild contracts service + migration (`GuildContractService`)
- [x] Integrated enable path from `GuildsPlugin`

### Open on the current surface

- [ ] Contracts: player commands / events / inventory sourcing (API-only today)
- [ ] Vocabulary cleanup: remaining “guild”/“nation” user-facing strings → guild/alliance
- [ ] Permission dual-engine agreement tests with territory listeners (**governance**)
- [ ] Archive vs revive strategy for MockBukkit suite documentation

### Current notes

Nation vocabulary is retired in territory docs; command names may still say
`/guild*` for player familiarity — product choice to document.

## Next

- [ ] Guild contract player UX (list/post/fulfill commands) when prioritized
- [ ] Align form-default land rights with scope-aware **governance** implementation
- [ ] Hearthstone / movement edge-case hardening as bugs appear

## Future

- [ ] Elected alliance delegates
- [ ] Cross-guild diplomacy beyond alliances
- [ ] Full separation of guilds into optional module (if ever)

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| (merge) | Guilds live inside the Paper plugin | Single deployable; shared DB and governance |
| 2026-08-19 | Plugin identity is Guilds, not Azoth Territory | One Paper name, jar, package root, and data folder |
| (core) | Guilds + alliances replace nation vocabulary in domain | Clearer federal model |
| 2026-08-07 | Contracts as service API first | Escrow correctness before UX |
| 2026-08-06+ | Postgres only for guilds schema | Unified persistence |

## Open questions

- [ ] Keep `/guild*` command roots permanently for UX, or alias-migrate to `/guild*`?
- [ ] Should guild `balance` integrate with Vault / territory treasury?
- [ ] When is **guild-storage** promoted from design to Next?
