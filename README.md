# Azoth Territory

Paper plugin for large map **territories** with nested **Wilderness** and **Claimable** zones — inspired by New World / LokaMC style regions (town claims / siege systems are out of scope).

## Features

- **Territories** with id, name, world, and large outer boundaries
- Boundaries as **polygons** (block XZ vertices), **chunk sets**, or both (union)
- **Zones** nested under a territory: `WILDERNESS` and `CLAIMABLE`
- Spatial **resolve(world, x, z)** → territory + zone type (or uncontained)
- JSON **save/load** (`plugins/AzothTerritory/territories.json`)
- Admin command: `/territory [lookup|list|reload|save|web]`
- **Embedded web submodule** (JDK `HttpServer` / `HttpsServer`):
  - Map UI at `/` (canvas viewer over chunk/polygon boundaries)
  - REST API under `/api/*`
  - Optional **TLS** via PKCS12/JKS keystore
  - **Reverse-proxy** aware (`X-Forwarded-Proto/Host/For`, `X-Real-IP`, `public-base-url`)

## Build

```bash
./gradlew build
```

Produces `build/libs/azoth-territory-1.0.0-SNAPSHOT.jar`.

```bash
./gradlew test
```

### Merged Guilds plugin

The repository also contains the `guilds` Gradle subproject, a separately packaged
Paper plugin for Towny-style towns, nations, plots, permissions, quests, and tech
trees. Its source and full git history are under `guilds/`.

```bash
./gradlew :guilds:compileJava
./gradlew :guilds:shadowJar
```

The packaged artifact is `guilds/build/libs/Towny.jar`. The merged build uses the
Foojay toolchain resolver to provision Java 26 for Guilds while Azoth Territory
continues to target Java 21. The imported Guilds test sources include stale tests
against removed APIs; production compilation and packaging are verified separately.

## Spatial rules

1. **Territories must not overlap** in the same world (register/API/load reject with an error). Sharing an edge or corner is OK (adjacent is fine). Different worlds may use the same coordinates.
2. **Zones inside a territory must not overlap** each other (same edge-touch rule). Enforced when constructing a territory or adding a zone.
3. Location must be inside a territory boundary for that world to resolve.
4. At most one named zone should contain a point; if none match → territory `defaultZoneType` (usually `WILDERNESS`).
5. Outside every territory → uncontained / no zone type.

## Government / sovereignty

Governments are first-class at **region guild** and **territory alliance** formation
(`RegionGuild.form(...)`, `TerritoryAlliance.form(...)` — must pick an assigned form, not `ANARCHY`).
Territories may still carry an optional government attachment for local sovereignty.

Each territory may have **at most one** government attachment (default `ANARCHY`).
Holder ids are **opaque strings** (player UUID, company id, faction id, …) for later wiring.

Only forms that differ in decision mechanics are included (no flavor renames of the same structure).

| Form | Seats | Policy decision |
|------|-------|-----------------|
| `ANARCHY` | — | Cannot adopt policies |
| `MONARCHY` | 1 `SOVEREIGN` | **Decree** by sovereign |
| `OLIGARCHY` | 2+ `COUNCILOR` | **Majority** of filled council seats |
| `DEMOCRACY` | 1+ `REPRESENTATIVE` | **Majority** of filled representatives (optional terms) |

### Policies (propose → vote/decree → PASSED/REJECTED)

```java
Territory t = new Territory("crownlands", "Crownlands", "world", boundary)
    .withGovernment(Government.monarchy("player:uuid"));

// Monarchy: decree
t = t.proposePolicy("tax", "Tax Reform", "…", "player:uuid", System.currentTimeMillis());
t = t.decreePolicy("tax", "player:uuid", true, System.currentTimeMillis());
// t.policy("tax").status() == PASSED

// Oligarchy / democracy: multi-seat vote (auto-resolves on majority)
t = t.withGovernment(Government.oligarchy(List.of("c1", "c2", "c3")));
t = t.proposePolicy("wall", "Build Wall", "…", "c1", now);
t = t.castPolicyVote("wall", "c1", VoteChoice.YES, now);
t = t.castPolicyVote("wall", "c2", VoteChoice.YES, now); // → PASSED when yes > filled/2
```

Ineligible proposers/voters throw. Policy content is **decision data only** (no world enforcement yet).

Persisted in `territories.json` as `"government"` + `"policies": [{ id, title, body, proposerId, status, votes… }]`.

### Guilds, alliances, and permissions

In-memory wiring via `GovernanceRegistry` + form-based `PermissionRules` / `BlockProtection`
(package `com.azoth.territory.permission`).

**Resolution**
- Territory → first alliance that lists it (by alliance id), else the territory's local government
- Holder → first guild that lists them as a member
- World location → spatial `TerritoryRegistry.resolve` then territory resolution above

**Formal authority** (`SovereignAction`: `MANAGE_MEMBERSHIP`, `SET_POLICY`, `BREAK_BLOCK`, `PLACE_BLOCK`, `INTERACT`)
- `ANARCHY` — no formal grants
- `MONARCHY` — filled sovereign seat only
- `OLIGARCHY` / `DEMOCRACY` — each filled authority-role seat holder

**Block protection** (`BlockProtection.canBreak` / `canPlace` / `canInteract` / `canInteractWithEntity` / `allowsPvp` / `canTeleportInto` / `crossesBoundary`)
- Uncontained wilderness → allow
- Assigned government → only formal authority holders; outsiders denied
- Local `ANARCHY` → no seat-based lockdown (allow); still no formal policy authority

```java
GovernanceRegistry gov = new GovernanceRegistry(registry);
gov.putAlliance(TerritoryAlliance.form("pact", "Pact",
    Government.monarchy("king:1"), List.of("everfall")));
BlockProtection blocks = new BlockProtection(gov);
blocks.canBreak("world", x, z, "king:1");   // true
blocks.canBreak("world", x, z, "outsider"); // false
```

Paper listeners are wired on enable (`ProtectionListener` + `InteractionProtectionListener`):

| Concern | Domain API | Notes |
|---------|------------|--------|
| Block break/place | `canBreak` / `canPlace` | Actor = player UUID string |
| Block interact (chests, doors, buttons, levers, beds, furnaces, hoppers) | `canInteract` | Includes container open (InventoryOpen). Right-click on blocks |
| Entity interact (item frames, armor stands, paintings, vehicles, leash) | `canInteractWithEntity` | Place + break + rotate + equip |
| Fire burn/spread/ignite, explosions | `isEnvironmentallyProtected` | Assigned non-anarchy only |
| Piston push / fluid flow into claims | `crossesBoundary` | Blocked crossing in/out of governed land |
| Hopper / dropper steals, item pickup | (environmental) | Mechanical actors have no authority → denied in governed land |
| Natural/hostile mob spawn | `blocksMobSpawn` | Eggs/spawners/commands unrestricted |
| Entity block change, crop trample | `blocksEntityGrief` | Enderman/wither/farmland |
| Player PvP / friendly-fire | `allowsPvp` | Denied inside governed land for non-authority attackers; uncontained/anarchy unrestricted |
| Animal kill / pet damage | `canInteract` on victim | Animals/tameables/villagers/armor stands only; hostile mobs stay killable |
| Forced teleport / spawn / home-setting into claims | `canTeleportInto` | COMMAND/PLUGIN/portal/pearl causes; owners exempt; respawn-to-bed never fires this event |

Uncontained wilderness and anarchy-governed land stay unrestricted for environmental flags, block interaction, PvP, and teleport gates.

Company-level friendly fire and per-player home registration are **not expressible in the current model** (no company identity or home store); those remain future work.

## Data format (sketch)

```json
{
  "version": 1,
  "territories": [
    {
      "id": "everfall",
      "name": "Everfall",
      "world": "world",
      "defaultZoneType": "WILDERNESS",
      "boundary": {
        "polygon": [{"x": 0, "z": 0}, {"x": 1000, "z": 0}, {"x": 1000, "z": 1000}, {"x": 0, "z": 1000}],
        "chunks": []
      },
      "zones": [
        {
          "id": "plot-1",
          "name": "Plot 1",
          "type": "CLAIMABLE",
          "priority": 10,
          "boundary": {
            "polygon": [{"x": 100, "z": 100}, {"x": 200, "z": 100}, {"x": 200, "z": 200}, {"x": 100, "z": 200}],
            "chunks": []
          }
        }
      ]
    }
  ]
}
```

## API (in-plugin)

```java
TerritoryRegistry registry = plugin.getRegistry();
LookupResult r = registry.resolve("world", blockX, blockZ);
if (r.isContained()) {
    String territoryId = r.territoryId().orElseThrow();
    ZoneType type = r.zoneType().orElseThrow(); // WILDERNESS or CLAIMABLE
}
```

Domain packages under `com.azoth.territory.model` / `registry` / `persist` are free of Bukkit types for unit testing. The web package (`com.azoth.territory.web`) uses only the JDK HTTP server + domain types (plus Gson).

## Web submodule

Enabled by default on port **8765** (`config.yml` → `web`).

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Map UI |
| GET | `/api/health` | Liveness + territory count |
| GET | `/api/meta` | Public origin, scheme, proxy/TLS flags |
| GET | `/api/territories` | Full registry JSON |
| GET | `/api/territories/{id}` | One territory |
| PUT | `/api/territories/{id}` | Create/update (persists to disk) |
| DELETE | `/api/territories/{id}` | Remove |
| GET | `/api/resolve?world=&x=&z=` | Spatial lookup |

### Reverse proxy (recommended TLS)

Leave `web.tls.enabled: false` and terminate HTTPS on nginx/Caddy:

```nginx
location / {
    proxy_pass http://127.0.0.1:8765;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Real-IP $remote_addr;
}
```

Set `web.public-base-url: "https://map.example.com"` if you want a fixed public origin, and `web.bind: 127.0.0.1` so only the proxy can reach the plugin.

### Direct TLS

```yaml
web:
  tls:
    enabled: true
    keystore: keystore.p12   # relative to plugin data folder
    keystore-type: PKCS12
    password: changeit
```

### API token

If `web.api-token` is non-empty, send `X-Api-Token: <token>` or `Authorization: Bearer <token>` on API calls (health/meta stay open).
