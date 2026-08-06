# Vault-only economy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove all runtime non-Vault money paths from Azoth Territory and Guilds while preserving safe unavailable-provider behavior.

**Architecture:** Azoth will always construct `VaultTreasury` when a Vault provider is available and otherwise use its unavailable rail. `EconomyBridge` will no longer carry simulation state or emit simulated tax outcomes. Guilds’ `EconomyServiceImpl` will retain its Vault API but return safe zero/false/no-op results when Vault is unavailable instead of mutating `Town.balance`.

**Tech Stack:** Java 21 root plugin, Java 26 Guilds subproject, Gradle 9.6.1, Paper 1.21.4 API, VaultAPI, JUnit 5, Mockito.

## Global Constraints

- Vault remains a soft dependency in plugin metadata.
- Vault is the only runtime money source.
- Missing Vault/provider must never mint or persist virtual money.
- Keep `PaymentRail` as the settlement seam for domain tests.
- Preserve user WIP in the main checkout; all edits occur in `../azoth-territory-vault-only`.

---

### Task 1: Lock Vault-only Azoth behavior with tests

**Files:**
- Modify: `src/test/java/com/azoth/territory/economy/EconomyBridgeDomainTest.java`
- Modify: `src/test/java/com/azoth/territory/economy/BukkitEconomyBridgeTest.java`
- Delete: `src/test/java/com/azoth/territory/economy/EconomyConfigTest.java`
- Delete: `src/test/java/com/azoth/territory/economy/SimulationTreasuryTest.java`

**Interfaces:**
- Consumes: `EconomyBridge`, `TaxOutcome`, `PaymentRail`.
- Produces: tests that require the four-argument bridge and map unavailable settlement to `VAULT_UNAVAILABLE`.

- [x] **Step 1: Remove simulation-specific test expectations.** Remove tests for `economy.mode: SIMULATION`, `SIMULATED_TAXED`, and `SimulationTreasury`; update bridge callers to the Vault-only constructor.
- [x] **Step 2: Run the focused economy tests.**

```bash
./gradlew :test --tests 'com.azoth.territory.economy.EconomyBridgeDomainTest' --tests 'com.azoth.territory.economy.VaultTreasuryTest' --tests 'com.azoth.territory.economy.BukkitEconomyBridgeTest'
```

Expected: focused Azoth economy tests pass after the production cutover.
- [x] **Step 3: Keep the test changes with the Azoth cutover commit.**

### Task 2: Remove Azoth simulation runtime/configuration

**Files:**
- Modify: `src/main/java/com/azoth/territory/AzothTerritoryPlugin.java`
- Modify: `src/main/java/com/azoth/territory/economy/EconomyBridge.java`
- Delete: `src/main/java/com/azoth/territory/economy/EconomyConfig.java`
- Modify: `src/main/java/com/azoth/territory/economy/TaxOutcome.java`
- Delete: `src/main/java/com/azoth/territory/economy/SimulationTreasury.java`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Consumes: the tests from Task 1.
- Produces: `new EconomyBridge(registry, governance, goods, rail)` with no simulation flag, no selectable economy mode, and no `SIMULATED_TAXED` outcome.

- [x] **Step 1: Remove the simulation branch from plugin startup.** Always resolve Vault; choose `VaultTreasury` when available and `UnavailableRail` otherwise; pass the four-argument `EconomyBridge` constructor.
- [x] **Step 2: Remove simulation state and outcome mapping.** Delete `simulationMode`, `EconomyConfig`, and `TaxOutcome.SIMULATED_TAXED`.
- [x] **Step 3: Remove the simulation config block.** Economy is Vault-backed by implementation; no selectable simulation mode remains in `config.yml`.
- [x] **Step 4: Run focused Azoth economy tests and fix only contract failures.**

```bash
./gradlew test --tests 'com.azoth.territory.economy.*' --tests 'com.azoth.territory.PluginEconomyWiringTest'
```

Expected: Azoth economy tests pass.
- [ ] **Step 5: Commit the Azoth cutover.**

```bash
git add src/main src/test/java/com/azoth/territory
git commit -m "fix: make Azoth economy Vault-only"
```

### Task 3: Remove Guilds town-balance fallback with tests

**Files:**
- Modify: `guilds/src/main/java/org/aincraft/towny/services/impl/EconomyServiceImpl.java`
- Modify: `guilds/src/test/java/org/aincraft/towny/services/EconomyServiceImplTest.java`

**Interfaces:**
- Consumes: `EconomyServiceImpl` and the Vault `Economy` API.
- Produces: Vault-unavailable player/town operations that return zero/false/no-op without calling `Town.addFunds`, `Town.withdrawFunds`, or `TownService.updateTown`.

- [x] **Step 1: Add focused tests for no fallback.** Cover `depositTown`, `withdrawTown`, `getTownBalance`, and `townHas` with Vault unavailable; assert safe return values and no database interaction.
- [ ] **Step 2: Run the focused Guilds test class.**

```bash
./gradlew :guilds:test --tests 'org.aincraft.towny.services.EconomyServiceImplTest'
```

Blocked by unrelated active Guilds tests with stale API/fixture compile errors.
- [x] **Step 3: Remove fallback branches.** Make town operations no-op without Vault, make `getTownBalance` return `0.0`, and make `townHas` false without Vault. Change the no-Vault startup message to identify economy as unavailable.
- [ ] **Step 4: Run the focused Guilds test class again.** The rewritten test is present but cannot compile until the unrelated active Guilds test sources are repaired.
- [ ] **Step 5: Commit the Guilds cutover.**

```bash
git add guilds/src/main/java/org/aincraft/towny/services/impl/EconomyServiceImpl.java guilds/src/test/java/org/aincraft/towny/services/EconomyServiceImplTest.java
git commit -m "fix: remove Guilds town balance economy fallback"
```

### Task 4: Update documentation and verify artifacts

**Files:**
- Modify: `guilds/README.md`
- Modify: `guilds/src/main/resources/config.yml`
- Modify: `docs/superpowers/plans/2026-08-05-vault-only-economy.md`

**Interfaces:**
- Consumes: Vault-only runtime behavior from Tasks 2–3.
- Produces: accurate user-facing setup and no selectable simulation or persisted-balance instructions.

- [x] **Step 1: Update current README/config wording.** State that Vault is required for money movement and missing Vault disables settlement; remove non-Vault economy configuration.
- [x] **Step 2: Run production assembly.**

```bash
./gradlew :jar :guilds:jar
```

Expected: root and Guilds production artifacts build successfully.
- [x] **Step 3: Run focused Azoth tests and inspect Vault metadata.**

```bash
./gradlew :test --tests 'com.azoth.territory.economy.EconomyBridgeDomainTest' --tests 'com.azoth.territory.economy.VaultTreasuryTest' --tests 'com.azoth.territory.economy.BukkitEconomyBridgeTest'
```

Expected: focused Azoth tests pass. The repository’s known unrelated Guilds test compilation failures prevent a full `./gradlew test`.
- [ ] **Step 4: Commit docs and verify clean feature worktree.**
```
