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

Multi-module Gradle layout (`api` / `common` / `paper`):

- **`api/`** — public API: value models (`com.azoth.territory.model`), decree
  effects (`…decree`), registries (`…registry`), and contracts
  (`…permission` / `…economy` interfaces and DTOs). Pure Java; no Bukkit types.
- **`common/`** — Paper-free shared implementation: persistence
  (`…persist`), economy (`…economy`), governance logic (`…permission`),
  and the JDK HTTP web submodule (`…web`, with its map UI under
  `common/src/main/resources/web/`).
- **`paper/`** — the single Paper plugin: main class, listeners, commands,
  Vault/economy bridges, and the integrated Guilds subsystem
  (`org.aincraft.guilds`, including `plugin.yml` / `config.yml` /
  `guilds-config.yml` / `techtree.yml`).

Produces the single Paper plugin JAR:
`paper/build/libs/azoth-territory-1.0.0-SNAPSHOT.jar`
(shadow/fat jar with Guilds runtime libraries: HikariCP, SQLite, Caffeine, Javalin).

```bash
./gradlew test
```

### Integrated Guilds subsystem

Guilds production sources live under the `paper/` module tree
(`paper/src/main/java/org/aincraft/guilds/`) and ship in the **same** plugin
artifact as Azoth Territory. There is one `plugin.yml`, one main class
(`com.azoth.territory.AzothTerritoryPlugin`), and that main enables both
territory behavior and the guilds subsystem (commands via Paper Brigadier,
listeners, plain constructor-wired services via the `GuildsServices`
composition root).

Guilds defaults are packaged as `guilds-config.yml` and `techtree.yml` so they
do not overwrite the territory `config.yml`. The historical `guilds/` directory
was fully merged into the root `src/` tree and removed; the archived MockBukkit
test suite lives under `docs/archived-guilds-test/` and the historical Guilds
docs/plans under `docs/archived-guilds/docs/` for reference.

## Spatial rules

1. **Territories must not overlap** in the same world (register/API/load reject with an error). Sharing an edge or corner is OK (adjacent is fine). Different worlds may use the same coordinates.
2. **Zones inside a territory must not overlap** each other (same edge-touch rule). Enforced when constructing a territory or adding a zone.
3. Location must be inside a territory boundary for that world to resolve.
4. At most one named zone should contain a point; if none match → territory `defaultZoneType` (usually `WILDERNESS`).
5. Outside every territory → uncontained / no zone type.

## Government / sovereignty

Governments are first-class through the guilds subsystem: **towns are the local
governments** and **nations are the alliance entities**. The territory layer
records only an optional binding (`governedByGuildId`); all governance data
lives in the guilds database (`guilds.db`).

Resolution (via `GovernanceRegistry` + `GovernanceSource`, implemented by
`GuildsGovernanceSource`):

- Territory → the bound town's **nation** if the town is a nation member, else
  the **town** itself, else the territory-local government attachment.
- Holder → the first guild (town) listing them as a resident.
- World location → spatial `TerritoryRegistry.resolve` then territory resolution above.

Each guild (town) and alliance (nation) picks a **governance form** (`/town
government <form>`, `/nation government <form>`, mayor/king only). Seats are
derived live from role holders — the governance form IS the permission
structure:

| Form | Town (guild) seats | Nation (alliance) seats |
|------|--------------------|-------------------------|
| `ANARCHY` | none | none |
| `MONARCHY` | mayor → `SOVEREIGN` | king → `SOVEREIGN` |
| `OLIGARCHY` | mayor + assistants → `COUNCILOR` | king + ministers → `COUNCILOR` |
| `DEMOCRACY` | every resident → `REPRESENTATIVE` | every member-town mayor → `REPRESENTATIVE` |

### Policies (propose → vote/decree → PASSED/REJECTED)

```java
// Territory-local government (no guild binding): seats are persisted directly
Territory t = new Territory("crownlands", "Crownlands", "world", boundary)
    .withGovernment(Government.monarchy("player:uuid"));
t = t.proposePolicy("tax", "Tax Reform", "…", "player:uuid", System.currentTimeMillis());
t = t.decreePolicy("tax", "player:uuid", true, System.currentTimeMillis());
// t.policy("tax").status() == PASSED
```

For guild/alliance-bound territories, policy operations go through the
governance registry so the **derived** government (town/nation form + roles)
gates proposals, votes, and decrees:

```java
// Town picks MONARCHY → the mayor is the sovereign and may decree;
// /territory govern everfall everfall-town binds the territory.
governance.proposePolicy("everfall", "tax", "Tax Reform", "…", "mayor-uuid", now);
Policy passed = governance.decreePolicy("everfall", "tax", "mayor-uuid", true, now);
```

Ineligible proposers/voters throw. Policy content is **decision data only** (no world enforcement yet).

Persisted in `territories.json` as `"government"` + `"policies"` + optional
`"governedByGuildId"`.

### Guilds, alliances, and permissions

The standalone in-memory `RegionGuild`/`TerritoryAlliance` models are gone.
The territory layer consumes DTO snapshots (`GuildBody`, `AllianceBody`) via
`GovernanceSource`; the guilds subsystem materializes them from
`TownService`/`NationService` + the permissions table. There is one source of
truth: the guilds database.

**Formal authority** (`SovereignAction`: `MANAGE_MEMBERSHIP`, `SET_POLICY`, `BREAK_BLOCK`, `PLACE_BLOCK`, `INTERACT`)
- `ANARCHY` — no formal grants
- `MONARCHY` — the filled sovereign seat
- `OLIGARCHY` / `DEMOCRACY` — each filled authority-role seat holder

**Block protection** (`BlockProtection.canBreak` / `canPlace` / `canInteract` / `canInteractWithEntity` / `allowsPvp` / `canTeleportInto` / `crossesBoundary`), layered:
- Uncontained wilderness → allow
- `ANARCHY` government → no seat-based lockdown (allow); still no formal policy authority
- Assigned government → **formal authority holders always pass**; guild-governed
  land then falls through to the guilds permission model:
  - members (residents of the governing town; for nations, any member-town
    resident) are evaluated by their effective permissions — global `bypass`,
    explicit town-context grants, then the role default granting every member
    the basic build actions (break/place/switch/item-use);
  - outsiders are denied unless the town is **public**, in which case they may
    build/interact but never break (mirroring guilds town-owned plot defaults);
  - territory-local government stays a pure seat lockdown.

**Environmental flags follow the governing town's toggles** (`isFireProtected`,
`areExplosionsProtected`, `blocksMobSpawn`): governed land is protected when the
toggle is off (fire/explosions) or on (mobs); territory-local stays protected
regardless. `isEnvironmentallyProtected` (mechanical transfers, boundary
crossings, entity grief) still gates on assigned government alone. PvP follows
the town's `pvp` toggle, with authority holders always able to defend.

```java
GovernanceSource source = guilds.getGovernanceSource(); // towns + nations
GovernanceRegistry gov = new GovernanceRegistry(registry, source);
BlockProtection blocks = new BlockProtection(gov);
blocks.canBreak("world", x, z, "resident-uuid"); // true for town members
blocks.canBreak("world", x, z, "outsider");      // false in a closed town
```

Paper listeners are wired on enable (`ProtectionListener` + `InteractionProtectionListener`):

| Concern | Domain API | Notes |
|---------|------------|--------|
| Block break/place | `canBreak` / `canPlace` | Actor = player UUID string |
| Block interact (chests, doors, buttons, levers, beds, furnaces, hoppers) | `canInteract` | Includes container open (InventoryOpen). Right-click on blocks |
| Entity interact (item frames, armor stands, paintings, vehicles, leash) | `canInteractWithEntity` | Place + break + rotate + equip |
| Fire burn/spread/ignite | `isFireProtected` | Governed land with the town's fire toggle off (territory-local always) |
| Explosions | `areExplosionsProtected` | Governed land with the town's explosions toggle off (territory-local always) |
| Piston push / fluid flow into claims | `crossesBoundary` | Blocked crossing in/out of governed land |
| Hopper / dropper steals, item pickup | `isEnvironmentallyProtected` | Mechanical actors have no authority → denied in governed land |
| Natural/hostile mob spawn | `blocksMobSpawn` | Governed land with the town's mobs toggle on (territory-local always); eggs/spawners/commands unrestricted |
| Entity block change, crop trample | `blocksEntityGrief` | Enderman/wither/farmland |
| Player PvP / friendly-fire | `allowsPvp` | Town's pvp toggle for members; authority holders always may; uncontained/anarchy unrestricted |
| Animal kill / pet damage | `canInteract` on victim | Animals/tameables/villagers/armor stands only; hostile mobs stay killable |
| Forced teleport / spawn / home-setting into claims | `canTeleportInto` | COMMAND/PLUGIN/portal/pearl causes; authority + members exempt; public towns admit outsiders; respawn-to-bed never fires this event |

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
      "governedByGuildId": "everfall-town",
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
