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
- Modify: `src/test/java/com/azoth/territory/economy/EconomyConfigTest.java`
- Modify: `src/test/java/com/azoth/territory/economy/EconomyBridgeDomainTest.java`
- Modify: `src/test/java/com/azoth/territory/PluginEconomyWiringTest.java`
- Test: `src/test/java/com/azoth/territory/economy/`

**Interfaces:**
- Consumes: `EconomyConfig`, `EconomyBridge`, `TaxOutcome`, `PaymentRail`.
- Produces: failing assertions that simulation configuration and simulated tax outcomes are no longer available, while unavailable Vault remains safe.

- [ ] **Step 1: Remove simulation-specific test expectations.** Replace tests that parse `economy.mode: SIMULATION` or expect `SIMULATED_TAXED` with tests asserting the config is Vault-only and missing Vault maps to `VAULT_UNAVAILABLE`.
- [ ] **Step 2: Run the focused economy tests.**

```bash
./gradlew test --tests 'com.azoth.territory.economy.EconomyConfigTest' --tests 'com.azoth.territory.economy.EconomyBridgeDomainTest'
```

Expected: compilation/test failure because production still exposes simulation mode and the tests intentionally assert the Vault-only contract.
- [ ] **Step 3: Commit the red tests only.**

```bash
git add src/test/java/com/azoth/territory/economy/EconomyConfigTest.java src/test/java/com/azoth/territory/economy/EconomyBridgeDomainTest.java src/test/java/com/azoth/territory/PluginEconomyWiringTest.java
git commit -m "test: require Vault-only economy behavior"
```

### Task 2: Remove Azoth simulation runtime/configuration

**Files:**
- Modify: `src/main/java/com/azoth/territory/AzothTerritoryPlugin.java`
- Modify: `src/main/java/com/azoth/territory/economy/EconomyBridge.java`
- Modify: `src/main/java/com/azoth/territory/economy/EconomyConfig.java`
- Modify: `src/main/java/com/azoth/territory/economy/TaxOutcome.java`
- Delete: `src/main/java/com/azoth/territory/economy/SimulationTreasury.java`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Consumes: the tests from Task 1.
- Produces: `new EconomyBridge(registry, governance, goods, rail)` with no simulation flag; `EconomyConfig` representing Vault-only configuration; no `SIMULATED_TAXED` outcome.

- [ ] **Step 1: Remove the simulation branch from plugin startup.** Always resolve Vault; choose `VaultTreasury` when available and `UnavailableRail` otherwise; pass the four-argument `EconomyBridge` constructor.
- [ ] **Step 2: Remove simulation state and outcome mapping.** Delete `simulationMode`, `Mode.SIMULATION`, and `TaxOutcome.SIMULATED_TAXED`.
- [ ] **Step 3: Remove the simulation config block.** Keep only a comment/config marker that economy is Vault-backed; do not expose a selectable simulation mode.
- [ ] **Step 4: Run focused Azoth economy tests and fix only contract failures.**

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
- Consumes: `EconomyServiceImpl`, `TownService`, Vault `Economy` API.
- Produces: Vault-unavailable player/town operations that return zero/false/no-op without calling `Town.addFunds`, `Town.withdrawFunds`, or `TownService.updateTown`.

- [ ] **Step 1: Add focused tests for no fallback.** Cover `depositTown`, `withdrawTown`, `getTownBalance`, and `townHas` with Vault unavailable; assert no town mutation and safe return values.
- [ ] **Step 2: Run the focused Guilds test class.**

```bash
./gradlew :guilds:test --tests 'org.aincraft.towny.services.EconomyServiceImplTest'
```

Expected: tests fail because the current implementation mutates/reads `Town.balance` without Vault.
- [ ] **Step 3: Remove fallback branches.** Make `depositTown`/`withdrawTown` no-op without Vault, make `getTownBalance` return `0.0`, and retain `townHas` as a comparison against that safe balance. Change the no-Vault startup message to identify economy as unavailable.
- [ ] **Step 4: Run the focused Guilds test class again.** Expected: the new Vault-only tests pass; unrelated stale Guilds tests remain outside this focused scope.
- [ ] **Step 5: Commit the Guilds cutover.**

```bash
git add guilds/src/main/java/org/aincraft/towny/services/impl/EconomyServiceImpl.java guilds/src/test/java/org/aincraft/towny/services/EconomyServiceImplTest.java
git commit -m "fix: remove Guilds town balance economy fallback"
```

### Task 4: Update documentation and verify artifacts

**Files:**
- Modify: `README.md`
- Modify: `src/main/resources/config.yml` if Task 2 changes its economy section
- Modify: `docs/superpowers/specs/2026-08-05-economy-hooks-design.md` only if current behavior claims become false

**Interfaces:**
- Consumes: Vault-only runtime behavior from Tasks 2–3.
- Produces: accurate user-facing setup and no simulation instructions.

- [ ] **Step 1: Update current README/config wording.** State that Vault is required for money movement and missing Vault disables settlement; remove simulation instructions.
- [ ] **Step 2: Run production assembly.**

```bash
./gradlew assemble
```

Expected: root and Guilds production artifacts build successfully.
- [ ] **Step 3: Run focused economy tests and inspect Vault metadata.**

```bash
./gradlew test --tests 'com.azoth.territory.economy.*' --tests 'com.azoth.territory.PluginEconomyWiringTest'
```

Expected: focused Azoth tests pass. The repository’s known unrelated Guilds test compilation failures may prevent a full `./gradlew test`.
- [ ] **Step 4: Commit docs and verify clean feature worktree.**

```bash
git add README.md src/main/resources/config.yml docs/superpowers/specs/2026-08-05-economy-hooks-design.md
git commit -m "docs: describe Vault-only economy setup"
git status --short
```
