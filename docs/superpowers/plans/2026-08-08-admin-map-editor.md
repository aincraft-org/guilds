# Admin Map Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship an admin Leaflet map editor at `/editor/` that draws chunk-snapped territories and zones, authenticates via API-token → `GUILDS_SESSION` cookie, saves through existing `PUT`/`DELETE /api/territories*`, and uses squaremap tiles as basemap — without forking squaremap.

**Architecture:** Extend `TerritoryWebServer` with `SessionStore`, session routes, and static `/editor/*` assets. Frontend is vanilla JS modules + Leaflet (CDN pinned). Geometry is chunk-medium; polygons serialize as block coords at `chunk×16`. Domain validation stays on the existing registry path.

**Tech Stack:** Java 21, JDK `HttpServer`, JUnit 5, Leaflet 1.9.4, existing Gson territory JSON, squaremap tiles at `{base}/tiles/{world}/{z}/{x}_{y}.png` (tileSize 512, CRS.Simple like squaremap).

**Spec:** `docs/superpowers/specs/2026-08-08-admin-map-editor-design.md`

---

## File map

| Path | Role |
|------|------|
| `common/.../web/SessionStore.java` | In-memory sessions (id → expiry) |
| `common/.../web/WebConfig.java` | + `squaremapTileBaseUrl`, `sessionTtlSeconds` |
| `common/.../web/WebConfigLoader.java` | Load new keys |
| `common/.../web/TerritoryApiHandler.java` | Session login/logout; cookie auth |
| `common/.../web/HttpResponses.java` | Cookie helpers; credentials CORS if needed |
| `common/.../web/StaticFileHandler.java` | Serve classpath `/editor/**` |
| `common/.../web/TerritoryWebServer.java` | Register static + pass SessionStore |
| `common/.../web/static/editor/*` | `index.html`, `css/editor.css`, `js/*.js` |
| `paper/.../resources/config.yml` | New web keys + comments |
| `README.md` | Document `/editor/`, tiles, session |
| Tests under `common/src/test/java/com/guilds/territory/web/` | SessionStore, session auth, static |

Tile template (squaremap 1.3.15):  
`{squaremapTileBaseUrl}/tiles/{worldName}/{z}/{x}_{y}.png`  
with world folder names like `minecraft_overworld` from squaremap settings. Editor maps Bukkit world name `world` → configurable map; default `world` → `minecraft_overworld` via simple mapping in meta or config (v1: `web.squaremap-world-map` optional JSON/object, or hardcode `world`→`minecraft_overworld` and allow override string in config `web.squaremap-world: minecraft_overworld` for the primary world; multi-world: derive `minecraft_` + name with `:` → `_`).

---

### Task 1: SessionStore

**Files:**
- Create: `common/src/main/java/com/guilds/territory/web/SessionStore.java`
- Test: `common/src/test/java/com/guilds/territory/web/SessionStoreTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.guilds.territory.web;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SessionStoreTest {
    @Test
    void createRejectsWrongToken() {
        SessionStore store = new SessionStore("secret", 3600, Clock.systemUTC());
        assertTrue(store.create("nope").isEmpty());
    }

    @Test
    void createAcceptsTokenAndValidatesUntilExpiry() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        SessionStore store = new SessionStore("secret", 60, clock);
        String id = store.create("secret").orElseThrow();
        assertTrue(store.isValid(id));
        now.set(now.get().plusSeconds(61));
        assertFalse(store.isValid(id));
    }

    @Test
    void invalidateRemovesSession() {
        SessionStore store = new SessionStore("secret", 3600, Clock.systemUTC());
        String id = store.create("secret").orElseThrow();
        store.invalidate(id);
        assertFalse(store.isValid(id));
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (class missing)**

```bash
./gradlew :common:test --tests com.guilds.territory.web.SessionStoreTest
```

- [ ] **Step 3: Implement SessionStore**

```java
package com.guilds.territory.web;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionStore {
    public static final String COOKIE_NAME = "GUILDS_SESSION";

    private final String expectedToken;
    private final long ttlSeconds;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

    public SessionStore(String expectedToken, long ttlSeconds, Clock clock) {
        this.expectedToken = expectedToken == null ? "" : expectedToken;
        if (ttlSeconds < 1) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
        this.ttlSeconds = ttlSeconds;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public Optional<String> create(String presentedToken) {
        if (expectedToken.isBlank() || presentedToken == null || !expectedToken.equals(presentedToken)) {
            return Optional.empty();
        }
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String id = HexFormat.of().formatHex(bytes);
        sessions.put(id, clock.instant().plusSeconds(ttlSeconds));
        return Optional.of(id);
    }

    public boolean isValid(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        Instant exp = sessions.get(sessionId);
        if (exp == null) {
            return false;
        }
        if (clock.instant().isAfter(exp)) {
            sessions.remove(sessionId, exp);
            return false;
        }
        return true;
    }

    public void invalidate(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }
}
```

- [ ] **Step 4: Run tests — PASS**

- [ ] **Step 5: Commit** `feat: add SessionStore for admin map editor`

---

### Task 2: WebConfig + loader fields

**Files:**
- Modify: `WebConfig.java`, `WebConfigLoader.java`, all `new WebConfig(...)` call sites
- Modify: `paper/src/main/resources/config.yml`

- [ ] **Step 1: Add fields** `squaremapTileBaseUrl` (String, default `""`), `sessionTtlSeconds` (long, default `28800`) to `WebConfig` constructor, accessors, and `defaults()`.
- [ ] **Step 2: Load** `web.squaremap-tile-base-url`, `web.session-ttl-seconds` in `WebConfigLoader`.
- [ ] **Step 3: Update every test `new WebConfig(...)`** to pass the two new args (or use a test helper).
- [ ] **Step 4: Document keys in config.yml**
- [ ] **Step 5: `./gradlew :common:test` green; commit** `feat: web config for session TTL and squaremap tiles`

---

### Task 3: Session HTTP + cookie auth

**Files:**
- Modify: `TerritoryApiHandler.java`, `HttpResponses.java`, `TerritoryWebServer.java`
- Test: extend `TerritoryWebServerTest` or new `SessionAuthWebTest.java`

- [ ] **Step 1: Cookie helpers** on `HttpResponses`: parse `Cookie` header for `GUILDS_SESSION`; build `Set-Cookie` with HttpOnly, Path=/, SameSite=Lax, Max-Age, Secure when `proxy.isSecure(exchange)`.
- [ ] **Step 2: Construct handler with `SessionStore`** (nullable/empty when no token).
- [ ] **Step 3: Routes**
  - `POST /api/session` body `{"token":"..."}` → 200 + Set-Cookie or 401
  - `DELETE /api/session` → invalidate + clear cookie, 204
  - `needsAuth`: exclude `POST /session` from requiring existing session (login is public when token configured); still require token *in body* for create
- [ ] **Step 4: `authorized()`** also true if valid session cookie
- [ ] **Step 5: `GET /api/meta`** include `squaremapTileBaseUrl`, `authRequired`, `sessionTtlSeconds` for the editor
- [ ] **Step 6: Integration test** with `api-token: secret`:
  - PUT without auth → 401
  - POST session with secret → Set-Cookie
  - PUT with Cookie → 200
  - DELETE session → subsequent PUT 401
  - X-Api-Token still works without cookie
- [ ] **Step 7: Commit** `feat: session cookie auth for territory web API`

---

### Task 4: Static `/editor` handler

**Files:**
- Create: `StaticFileHandler.java`
- Create: `common/src/main/resources/com/guilds/territory/web/static/editor/index.html` (minimal shell)
- Modify: `TerritoryWebServer` register context `/editor`
- Test: GET `/editor/` and `/editor/index.html` → 200 HTML

Classpath root: `com/guilds/territory/web/static/editor/`.  
Map request path `/editor` or `/editor/` → `index.html`; `/editor/js/api.js` → resource `.../editor/js/api.js`.  
Reject `..` path segments. Content-Types: html, css, js, svg, map.

- [ ] **Steps:** failing test → implement → pass → commit `feat: serve admin editor static assets`

---

### Task 5: Editor shell UI (login + load territories)

**Files under** `common/src/main/resources/com/guilds/territory/web/static/editor/`:
- `index.html`, `css/editor.css`
- `js/api.js`, `js/model.js`, `js/ui.js`, `js/app.js`

- [ ] Login modal when `meta.authRequired`; `POST /api/session` with credentials include
- [ ] Fetch territories; sidebar list; empty map div
- [ ] Status banner for errors
- [ ] Commit `feat: admin editor shell with login and territory list`

---

### Task 6: Leaflet map + squaremap tiles + chunk grid

**Files:** `js/map.js`, update `app.js`  
Leaflet 1.9.4 from pinned CDN in index.html.

Coordinate system (match squaremap):
- `CRS.Simple`
- scale from max zoom (default max native 3): `scale = 1/2^maxZoom`
- `toLatLng(x,z) = LatLng(pixelsToMeters(-z), pixelsToMeters(x))`
- `toPoint(latlng) = (metersToPixels(lng), metersToPixels(-lat))`
- Tile URL: `${base}/tiles/${squaremapWorld}/{z}/{x}_{y}.png`, tileSize 512
- World name mapping: Bukkit `world` → `minecraft_overworld`; else `minecraft_` + name with `:`/`/` → `_`

Chunk grid: Canvas/SVG overlay at high zoom showing 16-block cells (optional density by zoom).

- [ ] Commit `feat: editor basemap tiles and chunk coordinates`

---

### Task 7: Draw tools + draft model

**Files:** `js/tools.js`, `js/model.js`, `js/ui.js`

Tools (active territory or zone boundary):
- **Select** — pick shape on map / tree
- **Polygon** — click chunk corners; double-click/Enter finish; verts at chunk corners only
- **Paint** — click toggles chunk in set
- **Rect** — drag chunk-aligned rectangle → union into chunk set
- **Erase** — remove chunks under cursor; vertex click removes polygon vert; Delete shape button with confirm

Serialization for save:
```json
"boundary": {
  "polygon": [{"x": cx*16, "z": cz*16}, ...],
  "chunks": [{"x": cx, "z": cz}, ...]
}
```
Use whichever the draft holds; empty side omitted as `[]`.

- [ ] Commit `feat: chunk-snapped draw tools for map editor`

---

### Task 8: Save / delete + inspector

- Save selected territory → `PUT /api/territories/{id}` full document
- Delete territory → `DELETE`
- Delete zone → mutate draft zones array → PUT parent
- 400/401/5xx handling per spec
- Dirty flag + beforeunload warn
- Commit `feat: save and delete from admin map editor`

---

### Task 9: README + polish

- Document `/editor/`, config keys, tile base URL example, session cookie
- Ensure CORS headers allow `Credentials` only if needed (same-origin preferred)
- Full `./gradlew check` (or `:common:test` + `:paper:test` as project standard)
- Commit `docs: document admin map editor`

---

## Self-review vs spec

| Spec item | Task |
|-----------|------|
| Separate admin editor / no fork | 4–9 |
| Territories + zones | 5, 7–8 |
| Chunk medium tools + erase | 7 |
| squaremap tiles | 6 |
| Session cookie auth | 1–3 |
| Existing PUT/DELETE persist | 8 |
| Config + meta | 2–3 |
| Automated session/static tests | 1, 3, 4 |
| Manual smoke | Task 9 notes |

---

## Execution

Implement tasks in order with TDD for Java pieces, frequent atomic commits, green tests after each task.
