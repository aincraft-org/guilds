# Alliance Threshold and Alias Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `/a` as an alias for `/alliance`, require two guilds by default to create an alliance, persist an operator override for that threshold, and rank alliances in `/g top`.

**Architecture:** `/alliance create <name> <guild>` stores a pending `AllianceProposal`. The target mayor runs `/alliance accept <name>`; the alliance is persisted only when accepted guilds meet `alliance.min-guilds` (default 2). `/alliance requirement <count>` updates and saves that config path. `/a` redirects to `/alliance`. `/g top alliances` ranks by member-guild count.

**Tech Stack:** Java, Paper Brigadier commands, Bukkit `FileConfiguration`, JUnit 5, Gradle.

## Global Constraints

- Default `alliance.min-guilds` is `2`.
- Values below `2` are rejected.
- Requirement override is operator-only and persists through the plugin config.
- The existing `/alliance` and `/n` commands remain available.
- Existing command permission conventions must be preserved.

---

### Task 1: Add configuration-backed alliance threshold

**Files:**
- Modify: `guilds-paper/src/main/resources/config.yml`
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/GuildsServices.java`
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/commands/brigadier/AllianceBrigadierCommand.java`
- Test: `guilds-paper/src/test/java/org/aincraft/guilds/commands/AllianceCommandTest.java`

**Interfaces:**
- Produces a command constructor dependency representing the current minimum guild count and a setter that persists `alliance.min-guilds`.

- [ ] **Step 1: Write failing tests**

Add source-contract tests that require the config key, injected threshold dependency, minimum validation, and persisted update path.

- [ ] **Step 2: Run the focused test and confirm failure**

Run: `./gradlew :guilds-paper:test --tests org.aincraft.guilds.commands.AllianceCommandTest`
Expected: FAIL because the config key and command behavior are absent.

- [ ] **Step 3: Add default configuration**

Add:

```yaml
alliance:
  min-guilds: 2
```

Use the plugin’s existing config-loading conventions and do not introduce a second file.

- [ ] **Step 4: Wire the configuration value**

Load `plugin.getConfig().getInt("alliance.min-guilds", 2)`, clamp invalid configured values to the safe default or reject them explicitly at startup, and inject access to the plugin/config into `AllianceBrigadierCommand` so the command uses one source of truth.

- [ ] **Step 5: Implement the create gate**

Before `createAlliance`, require the configured count to be satisfied. Since a new alliance starts with only the creator’s guild, reject creation when the minimum is greater than one and report the required count clearly; use the existing alliance invitation/join flow for additional guilds rather than inventing an unpersisted pre-alliance state.

- [ ] **Step 6: Implement persisted operator override**

Add `/alliance requirement <count>`, restricted to an operator/admin permission consistent with project conventions. Reject values below `2`; otherwise call `plugin.getConfig().set("alliance.min-guilds", count)`, `plugin.saveConfig()`, update the live value, and report that it persists across restarts.

- [ ] **Step 7: Run focused tests**

Run: `./gradlew :guilds-paper:test --tests org.aincraft.guilds.commands.AllianceCommandTest`
Expected: PASS.

### Task 2: Register `/a` alias and cover command wiring

**Files:**
- Modify: `guilds-paper/src/main/java/org/aincraft/guilds/commands/BrigadierCommandRegistry.java`
- Test: `guilds-paper/src/test/java/org/aincraft/guilds/commands/AllianceCommandTest.java`

**Interfaces:**
- `/a` redirects to the canonical `/alliance` command tree.
- `/alliance` and `/n` remain registered.

- [ ] **Step 1: Add failing alias test**

Assert the registry contains `Commands.literal("a")`, redirects to `allianceCommand.buildCommand()`, and preserves the existing `n` redirect.

- [ ] **Step 2: Implement the alias**

Register:

```java
commands.register(Commands.literal("a")
        .redirect(allianceCommand.buildCommand())
        .build());
```

- [ ] **Step 3: Run focused tests**

Run: `./gradlew :guilds-paper:test --tests org.aincraft.guilds.commands.AllianceCommandTest`
Expected: PASS.

### Task 3: Verify command behavior and deployment

**Files:**
- Modify: `guilds-paper/src/test/java/org/aincraft/guilds/commands/AllianceCommandTest.java` only if test coverage gaps remain.

- [ ] **Step 1: Run focused and relevant tests**

Run: `./gradlew :guilds-paper:test --tests org.aincraft.guilds.commands.AllianceCommandTest`
Expected: PASS.

- [ ] **Step 2: Build the plugin**

Run: `./gradlew :guilds-paper:shadowJar`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Restart the supervised server**

Restart `guilds-server`, wait for Paper’s `Done (...)` log, and verify the server remains alive.

- [ ] **Step 4: Exercise commands in-game**

As an operator, run `/alliance requirement 2` and confirm the persistence message. Run `/a` and confirm it reaches the alliance command tree. Attempt `/a create <name>` with fewer than two eligible guilds and confirm the clear rejection message. Test the override with a non-operator and confirm permission denial.
