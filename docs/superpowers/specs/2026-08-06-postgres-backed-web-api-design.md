# Postgres-Backed Web API Design

Date: 2026-08-06
Status: Approved

## Background

The territory web submodule (`common/.../web/`) serves the map UI at `/` and a
REST API under `/api/*`. Today its data source is the plugin's in-memory
`TerritoryRegistry`, persisted to a local `territories.json` file via
`TerritoryStore`. That couples the map/API to a single Minecraft server's disk.

Goal: hook the REST API into a **remote PostgreSQL** database as its durable
store, so the web thing (map UI + `/api/*`) serves Postgres-backed data instead
of the JSON file. The existing embedded API surface is retained — no external
service contract exists today, and the map UI fetches same-origin `/api/*`.
Repository/store interfaces are isolated so a future standalone deployment of
the API remains possible without reworking the data layer.

## Architecture

Keep the embedded `TerritoryWebServer` + `TerritoryApiHandler` as the REST API.
Replace the persistence seam behind it:

```
TerritoryApiHandler ──► TerritoryRegistry (in-memory, gameplay)
                              │
                              ▼
                    TerritoryRepository (interface)      [new seam]
                     ├── TerritoryStore        (JSON file — existing)
                     └── PostgresTerritoryRepository (remote Postgres — new)
```

- `TerritoryRegistry` stays the single in-memory truth for gameplay (spatial
  `resolve`, block protection) and for API reads.
- Mutations (`PUT`/`DELETE` on `/api/territories*`, `/territory save`,
  plugin disable) persist through the repository.
- **API mutations commit storage before memory**: `TerritoryApiHandler`
  applies the mutation to a staged copy of the registry
  (`new TerritoryRegistry()` + `replaceAll(registry.list())`), calls
  `store.save(staged)`, and only on success swaps the live registry with
  `replaceAll(staged.list())`. A failed remote save returns HTTP 500 and
  leaves the live registry untouched — memory can never drift ahead of
  PostgreSQL (the old `persistQuietly` best-effort path is removed). The
  stage → save → replace sequence runs under a handler-level `mutationLock`
  so concurrent requests serialize and cannot clobber each other's commits.
- The plugin picks the implementation at enable: `database.enabled: true` →
  Postgres; otherwise the existing JSON store. There is **no silent fallback**:
  if Postgres is configured but unreachable, the plugin logs SEVERE, loads no
  territory data, and the web submodule does not start (fail loud — the web
  thing must never silently serve JSON when Postgres was requested).

## Components (all in `common/`, Paper-free)

### 1. `TerritoryRepository` (interface, `com.guilds.territory.persist`)

```java
public interface TerritoryRepository extends AutoCloseable {
    void loadInto(TerritoryRegistry registry) throws IOException; // boot / reload
    void save(TerritoryRegistry registry) throws IOException;     // full replace
    @Override void close();                                       // no-op for JSON
}
```

`TerritoryStore` becomes an implementation (adds `implements TerritoryRepository`
and a no-op `close()`). `Supplier<TerritoryStore>` parameters in
`TerritoryWebServer` / `TerritoryApiHandler` widen to
`Supplier<TerritoryRepository>` — test call sites keep compiling unchanged.

### 2. `DatabaseSettings` + `DatabaseSettingsLoader`

Bukkit-free config mirroring `WebConfigLoader.fromValues` (flattened
`Map<String,Object>`). Reads `database.*` from `config.yml`:

| Key | Default | Meaning |
|-----|---------|---------|
| `database.enabled` | `false` | use Postgres instead of JSON |
| `database.host` | `127.0.0.1` | remote host |
| `database.port` | `5432` | remote port |
| `database.name` | `guilds_territory` | database name |
| `database.user` | `guilds` | role |
| `database.password` | `""` | password |
| `database.ssl` | `false` | require TLS (`sslmode=require`) |
| `database.pool-size` | `10` | HikariCP max pool size |
| `database.jdbc-url` | `""` | override: full JDBC URL wins over host/port/name/ssl |

### 3. `PostgresTerritoryRepository`

- HikariCP `DataSource` (same pool settings as guilds: maxLifetime 10 min,
  idleTimeout 5 min, connectionTimeout 30 s, `SELECT 1` validation).
- Schema init on construction (idempotent):

```sql
CREATE TABLE IF NOT EXISTS territories (
    id  TEXT PRIMARY KEY,
    doc JSONB NOT NULL
);
```

- `loadInto`: `SELECT doc FROM territories` → `TerritoryJson.fromJson` each row
  → `registry.replaceAll(list)` (same semantics as `TerritoryStore.loadInto`).
- `save`: one transaction — `DELETE FROM territories` then batched
  `INSERT (id, doc) VALUES (?, ?::jsonb)` from `TerritoryJson.toJson(t)`.
- `close`: closes the Hikari pool.
- SQL errors surface as `IOException` so callers need no new exception type.

### 4. Dependencies (`common/build.gradle.kts`)

- `api("com.zaxxer:HikariCP:5.1.0")` — `api` so `paper`'s guilds code keeps
  compiling; drop the duplicate declaration from `paper/build.gradle.kts`.
- `implementation("org.postgresql:postgresql:42.7.13")` — runtime only; shaded
  into the plugin JAR by paper's shadowJar (latest 42.7.x on Maven Central as
  of 2026-08-06).

### 5. Plugin wiring (`GuildsTerritoryPlugin`)

- Field type `TerritoryStore store` → `TerritoryRepository store`.
- Enable: build `DatabaseSettings`; if enabled, construct
  `PostgresTerritoryRepository` — a failure throws, so `store` stays null and
  the web submodule is gated on `store != null`. `TerritoryStore` is only ever
  constructed when `database.enabled` is false.
- Web server construction passes `new TerritoryJson()` instead of
  `store.json()` (the repository seam doesn't expose the codec).
- Disable: `store.save(registry)` then `store.close()` (both guarded on
  `store != null`).
- `getStore()` return type widens to `TerritoryRepository`.

## Error handling

- API mutation persistence failure: the staged save throws → HTTP 500 with
  the message; the live registry is never modified, so memory and PostgreSQL
  cannot diverge (regression-tested in the web server suite).
- Boot with Postgres configured but unreachable: SEVERE log naming the JDBC
  URL, `store` stays null, no territory data is loaded, and the web submodule
  is not started. The plugin itself keeps running (gameplay listeners still
  attach with an empty registry) so the failure is visible, not silent.
- Corrupt row during `loadInto` fails the load loudly (matches JSON behavior).

## Testing

- `DatabaseSettingsLoaderTest` (common): defaults, overrides, jdbc-url
  precedence, ssl flag → `sslmode=require` derivation.
- `PostgresTerritoryRepositoryTest` (common): integration test gated on
  `GUILDS_TEST_JDBC_URL` env var (JUnit assumption; skipped when unset) —
  schema idempotency, save→load round trip, full-replace semantics on second
  save, close releases the pool.
- Existing web tests keep using the JSON store through the widened
  `Supplier<TerritoryRepository>` seam.
- New web regression test: a `TerritoryRepository` stub whose `save` throws
  `IOException` — `PUT /api/territories/{id}` and `DELETE` must return 500
  **and** leave the live registry unchanged (no memory/DB divergence).
- Verification: `./gradlew build` (all modules); optionally run the gated
  integration test against a throwaway Postgres container if Docker is
  available.

## Out of scope

- Guilds' SQLite database, `influence.json`, facilities, expenses: their
  storage is untouched (the API's influence endpoints read the in-memory
  engine, which still loads from JSON).
- Standalone (out-of-process) API deployment: the repository seam makes it
  possible later; no new deployable is built now.
- Migrations of existing `territories.json` data into Postgres: operator can
  `PUT /api/territories/{id}` per territory or import later; a one-shot
  importer is a follow-up if needed.
- Normalized relational schema (zones/polygons/policies as tables): the JSON
  document format is intentional and stable for web-map tooling; JSONB keeps
  it queryable.
