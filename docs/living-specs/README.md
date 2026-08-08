# Living Specs — Domain Catalog Index

> Status: active  
> Last updated: 2026-08-08

One living catalog per product domain. Agents and humans read the relevant
catalog **before** designing or implementing, and update checkboxes when
progress lands. One-shot design docs under `docs/superpowers/` remain
historical detail; these catalogs are the durable north star.

## Domains

| Domain | File | Role |
|--------|------|------|
| **Territory (spatial)** | [territory.md](./territory.md) | Boundaries, zones, resolve, non-overlap |
| **Governance** | [governance.md](./governance.md) | Governments, policies/decrees, land protection, scope |
| **Economy** | [economy.md](./economy.md) | Tax, treasury, facilities, expenses, upkeep |
| **Influence** | [influence.md](./influence.md) | Contest races, declare, flip, cooldowns |
| **Standing** | [standing.md](./standing.md) | Development standing, harvest/influence multipliers |
| **Guilds** | [guilds.md](./guilds.md) | Guilds/alliances, plots, progression, contracts, commands |
| **Guild storage** | [guild-storage.md](./guild-storage.md) | Shared guild item bank at STORAGE facilities |
| **Web API** | [web-api.md](./web-api.md) | Embedded REST, TLS, reverse-proxy, auth token |
| **Map** | [map.md](./map.md) | squaremap layers; admin map editor (planned) |
| **Persistence** | [persistence.md](./persistence.md) | Unified PostgreSQL, shared pool, stores |
| **Platform** | [platform.md](./platform.md) | Build modules, static analysis, CI, git hooks |

## Cross-domain rules

1. **Do not invent a second catalog** for the same domain; update the file above.
2. **Promote** work Future → Next → Current before implementing parked ideas.
3. **Postgres-only** durable state: see [persistence.md](./persistence.md).
4. **Guilds own membership/roles**; territory stores only optional `governedByGuildId`.
5. **Economy owns money movement**; upkeep and tax never bypass `PaymentRail` / `EconomyBridge`.

## How to use

```text
/living-spec          → open or update the domain catalog for the active work
Start a feature       → map task to Current (or promote from Next with agreement)
Ship a slice          → flip checkboxes; bump Last updated; Decisions log if needed
```
