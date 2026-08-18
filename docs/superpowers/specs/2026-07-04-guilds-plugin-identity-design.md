# Guilds Plugin Identity and Package Consolidation

## Problem

The Paper plugin currently hosts the Guilds subsystem, but the implementation remains under `org.aincraft.guilds` while the public guild API is under `dev.mintychochip.guilds`. The shipped plugin is still identified as `GuildsTerritory`, including its entrypoint, artifact names, documentation, and several runtime identity strings.

## Decision

Consolidate the Guilds implementation under `dev.mintychochip.guilds`, rename the Paper plugin identity to `Guilds`, and retain territory functionality as a separate `dev.mintychochip.territory` domain within the same plugin.

## Package layout

Move the complete implementation tree from:

```text
paper/src/main/java/org/aincraft/guilds/
```

to:

```text
paper/src/main/java/dev/mintychochip/guilds/
```

Preserve its existing subpackages (`commands`, `config`, `database`, `listeners`, `models`, `plot`, `projects`, `services`, and `utils`). Update all Java package declarations and imports, including tests and source-level wiring checks.

Keep territory implementation under `dev.mintychochip.territory`. Territory persistence, web, influence, upkeep, and API contracts are separate domain boundaries and should not be nested beneath `guilds` merely because both ship in one Paper plugin.

## Plugin identity

Rename the entrypoint:

```text
dev.mintychochip.territory.GuildsTerritoryPlugin
```

to:

```text
dev.mintychochip.guilds.GuildsPlugin
```

Update `paper/src/main/resources/plugin.yml` to use:

```yaml
name: Guilds
main: dev.mintychochip.guilds.GuildsPlugin
```

Rename Gradle artifact base names for sources, thin, and shadow jars from `guilds` to `guilds`. Update local run-server comments and wiring accordingly.

## Compatibility policy

This is a clean cutover. Do not retain an `GuildsTerritoryPlugin` compatibility class or an `org.aincraft.guilds` forwarding package. Do not introduce a second Paper plugin. Update all callers, reflection checks, source-path checks, and documentation to the new names.

Preserve Minecraft commands and permissions unless they explicitly encode the old plugin identity. Preserve database schema, table names, and the default `guilds_territory` database name to avoid silently changing existing deployments. The renamed plugin will use Paper's `plugins/Guilds` data directory; documentation must reflect this, with no automatic data-folder copy.

Keep the existing GitHub URL in `plugin.yml`; it is external branding metadata and is not part of the Java/package identity migration.

## Identity strings

Update plugin-facing identity strings:

- README title, artifact paths, run-server references, config path, and package examples.
- Gradle descriptions and comments.
- Plugin configuration comments.
- Web health status from `guilds` to `guilds`.
- Logger and thread names directly tied to the old plugin identity.
- Default Mint client binding from `GuildsTerritory` to `Guilds`.

Do not rename database identifiers solely to remove historical branding.

## Verification

Add or update checks for:

- `plugin.yml` name and main class.
- `GuildsPlugin` class loading and `JavaPlugin` inheritance.
- Absence of `GuildsTerritoryPlugin` and `org.aincraft.guilds` source references.
- `GuildsServices` loading from `dev.mintychochip.guilds`.
- Artifact output named `guilds`.
- Existing Guilds service wiring and territory behavior.
- Focused migration tests, followed by the full Gradle build and test suite.
- A stale-name audit that explicitly allows the retained GitHub URL and database identifiers.
