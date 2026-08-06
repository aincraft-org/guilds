# Guilds sources moved into the root plugin

Guilds (Towny-style) production code and resources are integrated into the
repository root as part of the single Azoth Territory Paper plugin:

- Java: `src/main/java/org/aincraft/towny/`
- Config defaults: `src/main/resources/guilds-config.yml`, `techtree.yml`
- Lifecycle: started from `com.azoth.territory.AzothTerritoryPlugin`

Do not build or ship a separate `Towny.jar` from this directory for normal use.
Historical design notes and migration docs in this folder are archival.
