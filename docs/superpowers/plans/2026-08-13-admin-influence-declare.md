# Admin Influence Declaration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an admin-only, engine-authorized console declaration path and prove a persisted influence ownership flip.

**Architecture:** Extend `TerritoryCommand.declare` with a distinct five-argument admin form while leaving the player form unchanged. Route both forms through `InfluenceEngine.declare`; the engine remains the only domain authorization and eligibility gate.

**Tech Stack:** Java 26, Paper 26.2, JUnit 5, Mockito, PostgreSQL, Gradle Kotlin DSL.

## Global Constraints

- Preserve `/territory declare <territoryId> confirm` for players.
- Add `/territory declare <territoryId> <guildId> <authorityId> confirm` for console, operators, or `azoth.territory.admin`.
- Never bypass `InfluenceEngine.declare` authority validation.
- Use the existing countdown and flip task.

---

### Task 1: Admin declaration command

**Files:**
- Modify: `paper/src/main/java/com/azoth/territory/command/TerritoryCommand.java`
- Create: `paper/src/test/java/com/azoth/territory/command/TerritoryCommandDeclareTest.java`

**Interfaces:**
- Consumes: `InfluenceEngine.declare(String territoryId, String attackerGuildId, String authorityId, long nowEpochMs)`.
- Produces: admin form `/territory declare <territoryId> <guildId> <authorityId> confirm`.

- [ ] **Step 1: Write failing command-routing tests**

Test the admin argument shape, permission rejection, and that the existing player-only form remains selected for three arguments.

- [ ] **Step 2: Run the command test**

Run: `./gradlew :paper:test --tests 'com.azoth.territory.command.TerritoryCommandDeclareTest'`
Expected: FAIL because the admin route does not exist.

- [ ] **Step 3: Implement the minimal route**

At the start of `declare`, detect exactly five arguments ending in `confirm`, require console/op/admin permission, call `engine.declare(args[1], args[2], args[3], System.currentTimeMillis())`, and display the returned message. Keep the existing player branch byte-for-byte behaviorally equivalent.

- [ ] **Step 4: Run command tests**

Run: `./gradlew :paper:test --tests 'com.azoth.territory.command.TerritoryCommandDeclareTest'`
Expected: PASS.

- [ ] **Step 5: Commit command and tests**

Stage only the command and its tests; commit as one behavior unit.

### Task 2: Live persisted flip

**Files:**
- Runtime-only: `paper/run/plugins/AzothTerritory/config.yml`

**Interfaces:**
- Consumes: admin declaration form from Task 1.
- Produces: persisted territory owner `attacker` and post-flip cooldown.

- [ ] **Step 1: Start clean PostgreSQL and Paper**

Use the existing local container command and `./gradlew :paper:runServer`. Seed the schema, owner/attacker guilds, different alliances, authorized attacker authority UUID, and governed `frontier` territory.

- [ ] **Step 2: Set influence and declare**

Run through the Paper console:

```text
territory influence set frontier attacker 100
territory influence frontier
territory declare frontier attacker 22222222-2222-2222-2222-222222222222 confirm
```

Expected: declarable status followed by a successful declaration.

- [ ] **Step 3: Verify normal flip task**

With local countdown zero, wait for the scheduled influence tick. Query `/api/territories/frontier`, `/api/influence`, and PostgreSQL. Expected: owner `attacker`, cleared attacker bars/declaration, and positive cooldown deadline.

- [ ] **Step 4: Run regression suite**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Stop local runtimes**

Stop Paper and PostgreSQL cleanly.
