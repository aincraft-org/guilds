# Guilds — Living Spec

> Status: active  
> Last updated: 2026-08-29
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
- Chat, broadcasts, public-access listeners, and durable player progression.
- Levels (XP-only) / project skill points / tech-tree projects / resource bank / specializations / quests.
- Guild contracts service API (escrow materials for level costs).
- Brigadier command surface (`/guild*`, `/alliance*`, plot/perm/map, …).
- Config: `guilds-config.yml`, `techtree.yml`, level definitions.
- SQL schema + migrations under guilds database package (Postgres).
- Fast-travel capabilities, transport-facility cardinality, and governance/alliance authorization.
- Player-bound fast-travel currency, idempotent reward sources, and travel cancellation/refund behavior.

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
7. No Bukkit types in pure territory `guilds-api`/`guilds-common`; guilds code lives under
   `guilds-paper/.../org.aincraft.guilds` and may use Paper APIs.
8. Player identity and membership lookups use persisted UUID/resident records;
   Bukkit live-player objects are only required for delivery or physical
   interactions.
9. Offline residents must remain eligible for data-only operations such as
   role, alliance, and bank-account checks.
10. Fast-travel permissions are derived from the initiating player's persisted
    resident/guild identity, the guild's capabilities, current alliance state,
    and the endpoint territories; live player objects are delivery-only.
11. `GUILD_CRYSTAL`, `TELEPORT_TERMINAL`, `BOAT`, and `AIRSHIP` records are
    transport facilities, not membership records. Crystal and terminal
    cardinality is enforced for each guild; inactive records remain durable.
12. Travel currency is player-bound and finite. Reservation, commit, release,
    expiry, cancellation, and insufficient-balance outcomes do not debit a
    different player or the guild's resource bank.
13. Progression, exploration, and project-completion rewards require an
    attributable player and stable event id; actorless events do not award.
    Guild resources and guild money remain separate from this travel currency.

## Implementation guidance

| Area | Location |
|------|----------|
| Services | `guilds-paper/.../org.aincraft.guilds.services` (+ `impl`) |
| Models | `guilds-paper/.../org.aincraft.guilds.models` |
| Commands | `guilds-paper/.../org.aincraft.guilds.commands` |
| DB / migrations | `guilds-paper/.../org.aincraft.guilds.database` |
| Governance bridge | `GuildsGovernanceSource` |
| Composition | `GuildsServices` from `GuildsPlugin` |

- Prefer service interfaces for contracts/levels so territory domain stays free of guild SQL.
- When changing form or permission defaults, update **governance** living spec and form matrices together.
- Historical MockBukkit suite is archived under `docs/archived-guilds-test/` — new tests should live with the guilds-paper module.

### Testing

- Permission evaluation and plot form matrices.
- Deposit/upgrade atomicity and refund paths.
- Contract create/fulfill/cancel escrow balances.
- GovernanceSource snapshot correctness for forms and member sets.
- Travel authorization, capability/cardinality matrices, and same-guild versus allied endpoint behavior.
- Reward actor attribution and idempotent travel-currency outcomes, including cancellation and depletion.

### Do not

- Split guilds into a second plugin JAR without an explicit product decision.
- Reintroduce SQLite `guilds.db` beside Postgres.
- Let `/guildlevel deposit` skip persisting `upgrade_progress` (known historical footgun — contracts path fixed via direct SQL).

## Current

### Capability (shipped)

- [x] Guild / alliance models and services on shared Postgres
- [x] Brigadier commands (guild, alliance, plot, perm, chat, map, quests, tech, …)
- [x] Alliance creation requires a second guild's mayor to accept (`/alliance create <name> <guild>` + `/alliance accept <name>`); default `alliance.min-guilds: 2` with operator override `/alliance requirement`
- [x] `/a` aliases `/alliance` (existing `/n` retained)
- [x] `/g top alliances` and `/guilds top alliances` rank alliances by member-guild count
- [x] Plot type system and handlers
- [x] Permission service + public access / toggle listeners
- [x] `GuildsGovernanceSource` for territory governance
- [x] Level deposit / upgrade progression (XP-only; skill points = guild level)
- [x] Tech tree projects (one active at a time) / specialization / quest services
- [x] Guild contracts service + migration (`GuildContractService`)
- [x] Integrated enable path from `GuildsPlugin`
- [x] Offline resident name resolution uses the persistent resident store
- [x] Fast-travel capability nodes: `fast_travel`, `remote_crystal`, `boat_travel`, and `airship_travel`
- [x] Crystal and teleport-terminal cardinality plus per-territory transport quotas (owned by **territory**)
- [x] Alliance-aware transport authorization and player-bound travel currency wiring
- [x] Actor-attributed quest, territory-entry, and project-completion reward seams
- [x] `/g spawn` and hearthstone bypasses removed; `/g setspawn` remains persisted spawn input for crystal reconciliation

### Open on the current surface

- [ ] Contracts: player commands / events / inventory sourcing (API-only today)
- [ ] Vocabulary cleanup: remaining “guild”/“nation” user-facing strings → guild/alliance
- [ ] Permission dual-engine agreement tests with territory listeners (**governance**)
- [ ] Archive vs revive strategy for MockBukkit suite documentation

### Current notes

Nation vocabulary is retired in territory docs; command names may still say
`/guild*` for player familiarity — product choice to document. `/g new` mirrors
`/g create` for the familiar alias surface.

Fast travel does not turn guild resources or the guild `balance` field into a
player wallet. The shared travel currency is bound to the initiating player;
configured quest, territory-entry, and successful project-completion events
replenish that wallet only when a player actor and stable event id are present.
Reservation, commit, release, expiry, and cancellation keep insufficient funds
and failed travel from silently consuming currency.

## Next

- [ ] Guild contract player UX (list/post/fulfill commands) when prioritized
- [ ] Align form-default land rights with scope-aware **governance** implementation
- [ ] Fast-travel edge-case hardening as bugs appear; Paper multi-territory and live transport smoke remains open

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
| 2026-08-26 | Alliances start as pending proposals until the configured guild count has accepted | Prevents one-guild alliances while keeping `/alliance create` usable |
| 2026-08-29 | Transport capabilities and rewards use persisted governance/player identity; travel currency stays separate from guild resources and money | Prevent bypasses and preserve clear ownership of durable balances |
| 2026-08-29 | `/g spawn` and hearthstone bypasses were removed; `/g setspawn` remains spawn input for crystal reconciliation | One governed fast-travel path, with explicit persisted spawn compatibility |

## Open questions

- [ ] Keep `/guild*` command roots permanently for UX, or alias-migrate to `/guild*`?
- [ ] Should guild `balance` integrate with Vault / territory treasury?
- [ ] When is **guild-storage** promoted from design to Next?
