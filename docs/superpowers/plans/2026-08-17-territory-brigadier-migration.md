# Territory Command Brigadier Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the remaining Bukkit `CommandExecutor`/`TabCompleter` territory command wiring with Paper Brigadier while preserving the complete `/territory` command surface.

**Architecture:** Add a `TerritoryBrigadierCommand` adapter that builds the root Brigadier tree and delegates callbacks to the existing territory behavior. Register the root and aliases through `BrigadierCommandRegistry`; remove legacy executor wiring from `GuildsPlugin`; keep `plugin.yml` metadata unless verification proves it redundant.

**Tech Stack:** Java, Paper 26.2 Brigadier API, Gradle Kotlin DSL, JUnit 5, Mockito.

## Global Constraints

- Preserve `/territory`, `/guildsterritory`, and `/at`.
- Preserve all current subcommands, messages, permission checks, subsystem checks, and building delegation.
- Use greedy strings where parser validation would change domain-specific error messages.
- Do not add a compatibility executor or tab-completer shim.
- Skip formatters, linters, and project-wide test suites during implementation; run focused verification once at the end.

---

### Task 1: Extract a reusable territory dispatcher boundary

**Files:**
- Modify: `paper/src/main/java/dev/mintychochip/territory/command/TerritoryCommand.java`
- Test: `paper/src/test/java/dev/mintychochip/territory/command/TerritoryCommandUpkeepTest.java`
- Test: `paper/src/test/java/dev/mintychochip/territory/command/TerritoryCommandStandingTest.java`
- Test: `paper/src/test/java/dev/mintychochip/territory/command/TerritoryCommandInvasionTest.java`

**Interfaces:**
- Produces `execute(CommandSender, String, String[])` or equivalent package-visible behavior entry point for the Brigadier adapter.
- Removes dependence on Bukkit `Command` objects from the reusable execution path.

- [ ] Add a package-visible execution method accepting sender, label, and arguments, preserving the current switch behavior.
- [ ] Keep a temporary test-compatible `onCommand` only if existing tests require it while migration is staged; otherwise update tests to call the new boundary.
- [ ] Ensure building delegation receives the same label and sliced arguments as before.
- [ ] Run the three focused territory command test classes and confirm existing behavior remains unchanged.

Run: `./gradlew :paper:test --tests 'dev.mintychochip.territory.command.TerritoryCommand*Test'`
Expected: existing tests pass.

### Task 2: Build the Territory Brigadier command tree

**Files:**
- Create: `paper/src/main/java/dev/mintychochip/territory/command/TerritoryBrigadierCommand.java`
- Test: `paper/src/test/java/dev/mintychochip/territory/command/TerritoryBrigadierCommandTest.java`

**Interfaces:**
- Consumes `GuildsPlugin` and the dispatcher boundary from `TerritoryCommand`.
- Produces `LiteralCommandNode<CommandSourceStack> buildCommand()`.

- [ ] Write tests asserting the root literal is `territory`, top-level literals include every current subcommand, and `invasion` suggests `start`, `stop`, and `status`.
- [ ] Write a callback test that executes `territory upkeep everfall` through Brigadier and verifies the sender receives the existing output.
- [ ] Implement the root node and callbacks using `CommandSourceStack.getSender()`.
- [ ] Add `here` as an alias/redirect to the lookup behavior if the current command treats it as equivalent to `lookup`.
- [ ] Add argument nodes for existing command shapes, favoring greedy strings for territory/guild values and a double argument only where current numeric parsing behavior can be preserved.
- [ ] Add suggestion providers matching existing top-level and invasion completion behavior; delegate building suggestions to `BuildingCommand.complete`.
- [ ] Run the focused Brigadier test class.

Run: `./gradlew :paper:test --tests 'dev.mintychochip.territory.command.TerritoryBrigadierCommandTest'`
Expected: PASS.

### Task 3: Register Brigadier commands and remove executor wiring

**Files:**
- Modify: `paper/src/main/java/dev/mintychochip/guilds/commands/BrigadierCommandRegistry.java`
- Modify: `paper/src/main/java/dev/mintychochip/guilds/GuildsPlugin.java`
- Modify: `paper/src/main/resources/plugin.yml`
- Test: `paper/src/test/java/dev/mintychochip/PluginMintWiringTest.java`

**Interfaces:**
- Registry constructor accepts `TerritoryBrigadierCommand`.
- Registry registers `territory` and redirects `guildsterritory` and `at` to its root.

- [ ] Add the territory command dependency to the registry and register root plus aliases using existing `Commands.literal(...).redirect(...)` patterns.
- [ ] Construct the territory Brigadier command in plugin initialization and remove `getCommand("territory")` executor/tab-completer setup.
- [ ] Preserve `plugin.yml` command metadata unless the focused build/runtime verification demonstrates it is unnecessary; if retained, update usage text to include the actual command surface only if this does not alter compatibility.
- [ ] Update wiring tests to assert no legacy executor registration is required and the registry receives the territory command.
- [ ] Run the plugin wiring test.

Run: `./gradlew :paper:test --tests 'dev.mintychochip.PluginMintWiringTest'`
Expected: PASS.

### Task 4: Verify the complete migration

**Files:**
- Modify: `paper/src/test/java/dev/mintychochip/territory/command/TerritoryBrigadierCommandTest.java` if verification exposes uncovered behavior.
- Modify: `paper/src/main/resources/plugin.yml` only if runtime verification proves metadata must change.

- [ ] Run all focused territory command tests.
- [ ] Run the paper module test task.
- [ ] Build the paper artifact to catch Paper Brigadier API and registration errors.
- [ ] If available, launch the local Paper server and verify `/territory`, `/guildsterritory`, and `/at` appear in the command tree and execute `lookup`/`list` without legacy executor warnings.
- [ ] Review the final diff for retained `CommandExecutor`, `TabCompleter`, `setExecutor`, or `setTabCompleter` references.

Run: `./gradlew :paper:test :paper:build`
Expected: PASS; no legacy territory executor wiring remains.
