# Web API — Living Spec

> Status: active  
> Last updated: 2026-08-08  
> Related: `docs/superpowers/specs/2026-08-06-postgres-backed-web-api-design.md`,  
> `docs/superpowers/specs/2026-08-08-admin-map-editor-design.md` (editor hosts here)

## Intent

Expose an **embedded HTTP(S) REST API** (JDK `HttpServer` / `HttpsServer`) so
tooling and maps can read/write territory data and read influence/standing
without joining Minecraft as a player.

Success looks like: same-origin `/api/*` for clients; Postgres-backed mutations
that never leave memory ahead of durable commit; reverse-proxy and optional TLS
safe for real deployments; token auth on mutating routes.

## Boundaries

### In scope

- `TerritoryWebServer` lifecycle, bind address, TLS keystore options.
- `TerritoryApiHandler` routes: health, meta, territories CRUD, resolve,
  influence GET, standing GET.
- Optional `web.api-token` (header / Bearer) for protected methods.
- Reverse-proxy awareness (`X-Forwarded-*`, `public-base-url`).
- Mutation order: stage registry copy → Postgres save → replace in-memory.
- Future admin editor static hosting + session endpoints (**map** domain UX).

### Out of scope / non-goals

- Becoming a general multi-tenant SaaS control plane.
- Replacing squaremap’s public tile UI (editor may *use* tiles).
- GraphQL / gRPC.
- Player session auth via Minecraft accounts (token is ops secret today).

## Invariants

1. **Commit storage before memory** on mutations (no diverge on failed write).
2. **Serialize mutations** (lock) so concurrent PUT/DELETE cannot clobber.
3. Health/meta remain reachable for probes as designed (auth rules documented).
4. Domain models stay free of servlet frameworks — JDK HTTP only unless decided.
5. No second persistence backend behind the API.

## Implementation guidance

| Piece | Location |
|-------|----------|
| Server / config | `guilds-common/.../web` |
| Handler | `TerritoryApiHandler` |
| Plugin start/stop | `GuildsPlugin` |
| Config | `web.*` in plugin config |

- Prefer small JSON contracts stable for squaremap bridge consumers and future editor.
- Influence/standing suppliers optional — empty when subsystems down.
- When adding editor routes, keep session store TTL short; never log raw tokens.

### Testing

- Auth required on mutating methods when token configured.
- Mutation failure leaves registry unchanged.
- Resolve query parity with `TerritoryRegistry.resolve`.

### Do not

- Reintroduce `TerritoryRepository` supplier dual backend.
- Serve gameplay-authoritative writes that skip registry validation.
- Embed large SPA frameworks without size/ops discussion.

## Current

### Capability (shipped)

- [x] Embedded HTTP(S) server + `WebConfig`
- [x] Reverse proxy support
- [x] REST: health, meta, territories GET/PUT/DELETE, resolve
- [x] Influence + standing GET
- [x] API token auth for protected routes
- [x] Postgres-backed territory mutations (stage → save → replace)

### Open on the current surface

- [ ] OpenAPI / documented schema for external tools
- [ ] Consistent error body shape for validation failures
- [ ] Rate limiting / abuse notes for public binds

### Current notes

Interactive public map is **squaremap**, not a custom root UI on this port
(README: REST API only for the embedded server’s product role; editor will add
`/editor` later).

## Next

- [ ] Admin map editor hosting: static `/editor/*`, `POST/DELETE /api/session` (**map**)
- [ ] Session cookie auth alongside token for browser editor
- [ ] Stronger structured error codes for overlap/geometry (**territory**)

## Future

- [ ] Standalone deploy of API without Paper (possible; needs host for registries)
- [ ] Write APIs for facilities/upkeep admin
- [ ] OAuth / per-user admin identities

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| (core) | JDK HttpServer embedded in plugin | Zero extra process for ops |
| 2026-08-06 | Postgres-backed API data | Multi-server / durable truth |
| 2026-08-06 | Stage → save → replace memory | Never claim success on failed durable write |
| 2026-08-08 | Admin editor as separate SPA on same server | Avoid forking squaremap |

## Open questions

- [ ] Default bind: localhost-only vs public with mandatory token?
- [ ] CORS policy if editor and API split origins in some deploys?
