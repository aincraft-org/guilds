# Admin Influence Declaration Design

## Intent

Allow operators to complete a territory influence declaration from a headless Paper console without weakening the existing player authorization path.

## Command Surface

Keep the existing player command unchanged:

```text
/territory declare <territoryId> confirm
```

Add an administrative simulation form:

```text
/territory declare <territoryId> <guildId> <authorityId> confirm
```

The administrative form requires the console, an operator, or `guilds.territory.admin`.

## Authorization and State Flow

The command passes the supplied guild and authority IDs to `InfluenceEngine.declare`. The engine remains the sole authority for validating territory existence, ownership, alliance eligibility, influence cap, active declarations, cooldown, and whether `authorityId` holds authority in the attacking guild government. The command must not bypass or duplicate these checks.

A successful declaration follows the existing countdown and ownership-flip task. The local verification scenario uses an existing authorized attacker UUID and a zero-hour countdown so the normal scheduled tick completes the flip.

## Error Handling

Malformed administrative arguments return an exact usage message. Unauthorized senders receive the existing administrative permission error. Domain rejections display the unchanged `DeclareResult.message()`.

## Verification

Tests cover admin permission and argument routing while preserving player routing. Live verification boots Paper with PostgreSQL, seeds two eligible alliances and a governed territory, sets attacker influence to cap, declares from the console with an authorized holder, then confirms persisted ownership, reset influence state, and cooldown. The complete Gradle suite must remain green.
