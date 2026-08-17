# Guilds — Living Spec

> Status: active  
> Last updated: 2026-08-17  
> Related: archived docs under `docs/archived-guilds/docs/`;  
> `docs/superpowers/specs/2026-08-07-guild-contracts-design.md`

## Intent

Provide the **player organization layer**: guilds (guilds) and alliances, with
plots, residents, roles/permissions, chat, progression (levels, resources, tech
tree, specializations, quests), and service APIs that feed **governance**.

Ships **in the same Paper plugin JAR** as Guilds Territory — one main class,
shared PostgreSQL, composition root `GuildsServices` / `GuildsGovernanceSource`.
The portable domain and JDBC implementations live in `api` / `common`; Paper
keeps only Bukkit glue.

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
- SQL schema + migrations under `common` guilds database package (Postgres).

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
7. No Bukkit types in `api`/`common`. Portable guilds models, Bukkit-free
   service contracts, JDBC implementations, schema/migrations, and project/level
   helpers live in `api`/`common`. Paper hosts only Bukkit-facing guilds
   (commands, listeners, GUIs, YAML loaders, chat/broadcast/hearthstone,
   inventory resources, Mint adapters). Territory governance snapshots
   (`dev.mintychochip.guilds.Guild` etc.) stay separate from mutable
   `...models.Guild` entities.

## Implementation guidance

| Area | Location |
|------|----------|
| Portable models + service contracts | `api/.../dev.mintychochip.guilds.models` and `...services` |
| JDBC impls, schema, project/level helpers | `common/.../dev.mintychochip.guilds` |
| Bukkit commands / listeners / GUI / YAML | `paper/.../dev.mintychochip.guilds` |
| Governance bridge | `GuildsGovernanceSource` (paper) |
| Composition | `GuildsServices` from `GuildsPlugin` |

- Prefer service interfaces for contracts/levels so territory domain stays free of guild SQL.
- Runtime guilds SQL lives under `common/src/main/resources/dev/mintychochip/guilds/sql` and is
  executed with `NamedSql` named parameters. Do not re-embed full query text in Java.
- When changing form or permission defaults, update **governance** living spec and form matrices together.
- Historical MockBukkit suite is archived under `docs/archived-guilds-test/` — portable tests live in `api`/`common`; Bukkit tests stay in paper.

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
- [x] `/guilds building` / `/guild building` register guild-owned anchors in a region
- [x] `/guildsmap` / `/map` opens a MapGUI map-item claim screen (ASCII chat renderer retired)
- [x] Plot type system and handlers
- [x] Permission service + public access / toggle listeners
- [x] `GuildsGovernanceSource` for territory governance
- [x] Level deposit / upgrade progression (XP-only; skill points = guild level)
- [x] Tech tree projects (one active at a time) / specialization / quest services
- [x] Guild contracts service + migration (`GuildContractService`)
- [x] Integrated enable path from `GuildsPlugin`
- [x] Portable guilds domain + JDBC slice published from `api`/`common`

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
| (merge) | Guilds live inside guilds Paper module | Single deployable; shared DB and governance |
| (core) | Guilds + alliances replace nation vocabulary in domain | Clearer federal model |
| 2026-08-07 | Contracts as service API first | Escrow correctness before UX |
| 2026-08-06+ | Postgres only for guilds schema | Unified persistence |
| 2026-08-17 | Portable guilds slice in `api`/`common` | Paper-free reuse; paper stays Bukkit glue |
| 2026-08-17 | `/guildsmap` uses FloG99 MapGUI Screen | Replace ASCII chat grid; compileOnly + join-classpath, do not shade |
| 2026-08-17 | Buildings commanded under `/guilds building` | Territories are regions; guilds own the anchors inside them |

## Open questions

- [ ] Keep `/guild*` command roots permanently for UX, or alias-migrate to `/guild*`?
- [ ] Should guild `balance` integrate with Vault / territory treasury?
- [ ] When is **guild-storage** promoted from design to Next?
