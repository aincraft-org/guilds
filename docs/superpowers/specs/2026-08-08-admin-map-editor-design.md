# Admin Map Editor Design

Date: 2026-08-08  
Status: Approved (brainstorm)

## Background

Azoth Territory already has:

- **Public live map** via squaremap (`TerritorySquaremapBridge` layers for
  territories, zones, influence). Display-only; soft dependency.
- **REST API** on the embedded territory web server (`TerritoryWebServer` /
  `TerritoryApiHandler`): `GET`/`PUT`/`DELETE` `/api/territories*`, spatial
  resolve, optional `web.api-token` (`X-Api-Token` / Bearer).
- **Domain model**: territories with outer `Boundary` (polygon block XZ and/or
  chunk sets), nested zones (`WILDERNESS` / `CLAIMABLE`), non-overlap rules in
  `TerritoryRegistry` / `Territory`.

Admins currently create or reshape land by crafting JSON against the API (or
DB). There is no interactive draw UI.

## Goals

- Admin-only **map editor** to draw and edit **territories and nested zones**
  on a web map, without forking or patching squaremap.
- Drawing **medium is chunks**, not free block coordinates.
- Tools: **polygon** (chunk-corner snap), **paint**, **rect**, **erase**, plus
  delete-shape with confirm.
- **Basemap**: squaremap live tiles under the editor.
- Auth: paste existing API token once → **server session cookie** (short TTL).
- Saves go through existing persistence path so squaremap layers refresh (~5s).

## Non-goals (v1)

- Editing or forking squaremap’s jar / web frontend.
- Player-facing draw tools or non-admin roles.
- Auto-save, collaborative multi-admin merge, undo history stack.
- In-game wand/chunk tools (possible later hybrid).
- Playwright E2E of Leaflet gestures; tile visual regression.
- New spatial rules beyond existing registry/model validation.

## Decisions (locked)

| Topic | Choice |
|-------|--------|
| Placement | Separate admin editor (B), not inside squaremap UI |
| Scope | Territories + nested zones |
| Tools | Polygon + paint + rect + erase (chunk medium) |
| Basemap | squaremap tile URLs (configurable) |
| Auth | Token → HttpOnly session cookie |
| Implementation | Leaflet SPA served by territory web server |

## Architecture

```
Admin browser
    │
    │  GET  /editor/                 static Leaflet app
    │  POST /api/session             api-token → HttpOnly session cookie
    │  DELETE /api/session           logout
    │  GET  /api/territories
    │  PUT  /api/territories/{id}
    │  DELETE /api/territories/{id}
    ▼
TerritoryWebServer (JDK HttpServer, existing port / web config)
    │
    ├── Static: /editor/* from classpath resources
    ├── SessionStore (in-memory, TTL)
    └── TerritoryApiHandler
            → stage → PostgresTerritoryStore → TerritoryRegistry
                      │
                      │  existing ~5s refresh
                      ▼
            TerritorySquaremapBridge → squaremap layers (public map)

Basemap (read-only):
  Leaflet TileLayer → squaremap tile base URL (config)
  Editor does not generate Minecraft tiles.
```

### Ownership

| Piece | Owner |
|-------|--------|
| Draw UI, chunk snap, draft tree | Static editor (our code) |
| Session + REST + persist | `common` web + registry |
| Public map + tiles | squaremap (unchanged) |
| Overlap / zone rules | Existing domain on save |

## Components

### UI layout

- **Left sidebar**: world selector; territory → zone tree; + Territory / + Zone.
- **Center map**: squaremap tiles + chunk grid overlay; toolbar Select, Polygon,
  Paint, Rect, Erase; Save; chunk coordinate readout.
- **Right inspector**: id, name, zone type (when zone selected), chunk/vertex
  counts, Delete….

### Server (`common`, Paper-free)

1. **`SessionStore`**  
   - Validate login body against configured `web.api-token`.  
   - Issue opaque session id; store expiry.  
   - Lookup / invalidate.  
   - Default TTL e.g. 8 hours (configurable).

2. **Session API**  
   - `POST /api/session` — body `{ "token": "..." }` → `Set-Cookie:
     AZOTH_SESSION=<id>; HttpOnly; Path=/; SameSite=Lax` (and `Secure` when
     TLS / proxy-secure).  
   - `DELETE /api/session` — clear cookie and store entry.  
   - Mutating and protected routes accept **either** existing token headers
     **or** a valid `AZOTH_SESSION` cookie (no regression for scripted clients).  
   - If `web.api-token` is empty, auth is off (existing behavior): session
     login is unnecessary; editor may skip the login modal and call the API
     without a cookie.

3. **Static handler**  
   - Serve `/editor/` and `/editor/**` from classpath (e.g.
     `com/azoth/territory/web/static/editor/`).  
   - `index.html` + JS/CSS modules; Leaflet via CDN or vendored assets
     (prefer vendored or pinned CDN documented in README).

4. **`WebConfig` extensions**  
   - `web.squaremap-tile-base-url` (required for basemap; empty → grid only +
     banner).  
   - `web.session-ttl-seconds` (default `28800`).  
   - Existing: host, port, `api-token`, CORS, TLS, proxy.

5. **`TerritoryApiHandler`**  
   - Wire session into `authorized()`; no change to stage→save→replace
     persistence or domain validation beyond auth.

### Frontend modules (static JS)

| Module | Responsibility |
|--------|----------------|
| `map.js` | Leaflet map, tile layer, chunk grid, world pan/zoom |
| `tools.js` | Select, Polygon (chunk-corner snap), Paint, Rect, Erase |
| `model.js` | Client draft tree of territories/zones; dirty tracking |
| `api.js` | Session + territories; `credentials: 'include'` |
| `ui.js` | Sidebar, inspector, toolbar, toasts, login modal |

### Chunk medium (geometry)

- All tools operate on the **chunk grid** (16×16 blocks). UI never asks for
  free block precision.
- **Polygon**: vertices snap to chunk **corners**. When serializing to API
  polygon form, use block coordinates `x = cx * 16`, `z = cz * 16` (and close
  consistently with existing `Boundary` expectations).
- **Paint / rect**: toggle or set whole `ChunkPos` entries in the boundary’s
  chunk set (and/or derive an equivalent chunk-aligned polygon when preferred;
  both representations remain valid union in the model).
- **Erase**:
  - Chunk erase: remove chunks from the active territory/zone boundary.
  - Vertex erase: remove a snapped polygon vertex while editing.
  - Delete shape: remove whole territory (`DELETE`) or zone (rewrite parent
    without zone, then `PUT`), with confirm.

## Save flow

1. Editor loads `GET /api/territories` after session login.  
2. Admin edits draft in memory; **explicit Save** only (no auto-save).  
3. Save unit = **one territory document** (outer boundary + all nested zones)
   via `PUT /api/territories/{id}` using existing JSON shape.  
4. Optional: save all dirty territories sequentially.  
5. Warn on leave if dirty.  
6. Success: clear dirty; squaremap bridge picks up registry changes on next
   refresh.  
7. Delete territory: `DELETE /api/territories/{id}`. Delete zone: PUT parent.

### Validation

| Layer | Behavior |
|-------|----------|
| Client (soft) | ≥3 polygon vertices or ≥1 chunk; zone tools need selected territory; optional warn if zone appears outside parent |
| Server (hard) | Existing: territories non-overlap same world; zones non-overlap; polygon ≥3 verts → `IllegalArgumentException` → HTTP 400 |
| Auth | Missing/expired session or token → 401 |

No new spatial rule engine in v1.

### Error handling (UI)

| Condition | UI |
|-----------|-----|
| 400 | Show server message; keep draft |
| 401 | Re-login modal; keep draft in memory |
| 404 on delete | Refresh list from GET |
| Network / 5xx | Error banner; draft preserved |
| Tiles fail | Chunk grid remains; “basemap unavailable” banner |
| Empty boundary | Client blocks save |

## Configuration sketch

```yaml
web:
  enabled: true
  host: 0.0.0.0
  port: 8765
  api-token: "change-me"
  # Base URL for squaremap tiles (no trailing path assumptions documented
  # against the pinned squaremap version used by runServer).
  squaremap-tile-base-url: "http://localhost:8080"
  session-ttl-seconds: 28800
```

Exact tile URL template (z/x/y vs squaremap’s scheme) is fixed during
implementation against the pinned squaremap 1.3.15 layout and recorded in
README.

## Testing

### Automated (Gradle, `common`)

- **SessionStore**: valid token creates session; wrong token rejected; expiry
  invalidates; logout removes.
- **HttpServer integration** (extend patterns from `TerritoryWebServerTest`):
  login → cookie → `PUT` territory → `GET` reflects; no cookie/token → 401 on
  mutate when token configured; header token still works without cookie.
- **Static editor**: `GET /editor/` (and index) return 200 HTML; linked assets
  resolve (no full browser).
- **Geometry helpers** (if pure Java helpers are extracted): chunk corner ↔
  block `×16` consistent with `Boundary`.
- Existing registry overlap tests remain the authority for domain rules.

### Manual smoke (`./gradlew :paper:runServer`)

1. Open `http://localhost:8765/editor/`, login with configured api-token.  
2. Basemap tiles when squaremap is up; banner if not.  
3. Draw territory (polygon + paint), add zone, erase, Save → Postgres +
   squaremap layers within ~5s.  
4. Force overlap → 400 message; draft retained.  
5. Logout / expired session → re-login required.

### Out of scope for v1 tests

- Full Playwright draw E2E.  
- Visual regression of tiles.  
- Concurrent multi-admin merge (existing mutation lock; last successful write
  wins per request).

## Security notes

- Session cookie: HttpOnly; Secure when request is HTTPS / reverse-proxy secure;
  SameSite=Lax.  
- Session store is process-local (restart clears sessions).  
- Editor is not a substitute for network isolation: set a strong `api-token`
  and do not expose the web port publicly without TLS/proxy controls.  
- GET list of territories may still require auth when `api-token` is set
  (existing `needsAuth` behavior); session satisfies that the same as the
  token.

## Implementation sketch (for planning)

Suggested order (not a full task plan):

1. SessionStore + API + auth wiring + tests.  
2. Static `/editor/` shell + login + list territories (read-only map).  
3. Tile layer + chunk grid + world filter.  
4. Tools: polygon, paint, rect, erase; draft model.  
5. Save / delete wired to API; client validation messages.  
6. Config + README (`/editor/`, tile URL, session).  
7. Manual smoke on runServer.

## Success criteria

- Admin can create and edit a territory and nested zones entirely in the
  browser using chunk-snapped tools, without hand-writing JSON.  
- No squaremap fork or web asset overwrite.  
- Saves persist to PostgreSQL via existing API path and appear on the public
  squaremap layers.  
- Unauthenticated mutators are rejected when `api-token` is configured.
