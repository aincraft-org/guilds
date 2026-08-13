# Mint Guild Banks and Tax Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the latest `aincraft-org/mint` API so taxes settle asynchronously into native `guild:<guildId>` Mint accounts and guild members can inspect, deposit to, and withdraw from those accounts.

**Architecture:** Add pure async settlement contracts in `api`, use them from `common` without Mint/Paper dependencies, and implement them in a Paper-side adapter around a trusted `MintClientLease`. Keep the existing Vault/simulation rails and SQL `Guild.balance` wallet unchanged; Mint is authoritative only for the new cash guild-bank account.

**Tech Stack:** Java 26 project toolchain, Gradle Kotlin DSL, Paper 26.2, Mint API (`dev.mintychochip.mint:mint-api`), JUnit 5, Mockito where existing tests use it, `CompletionStage`, BigDecimal.

## Global Constraints

- Use the latest inspected Mint source commit `cee5b04`; do not invent a published release version.
- Mint API artifact coordinates are `dev.mintychochip.mint:mint-api`; configure a local Maven repository for the exact resolved version and make the version explicit/configurable.
- Use `MintClientReceiver`/`MintClientLease`; never construct an unbound lease.
- Keep `api` and `common` free of Mint/Paper classes; they depend only on pure async settlement types.
- Guild accounts use `AccountId.of(NamespaceId.parse("guild:" + guildId))`.
- Player accounts use `AccountId.player(UUID)`.
- Transfers use one atomic `LedgerService.transact(TransactionRequest)` with signed `Posting` values.
- No `.join()`, `.get()`, or other blocking wait on the Paper main thread.
- Mint is the source of truth only for the new cash guild-bank account; do not add a shadow cash balance.
- Existing SQL `Guild.balance`, plot purchases, guild contracts, and resource/progression flows remain unchanged.
- Existing Vault and simulation behavior remains available and unchanged.
- Money crossing the Mint boundary uses BigDecimal with configured scale and explicit rounding.

---

### Task 1: Isolated Workspace and Mint Dependency Wiring

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `paper/build.gradle.kts`
- Modify: `gradle.properties`
- Modify: `paper/src/main/resources/config.yml`
- Test: `paper/src/test/java/com/azoth/territory/MintDependencyWiringTest.java`

**Interfaces:**
- Produces the compile-time Mint API dependency and configuration keys consumed by Tasks 2–5.
- Produces a deterministic configured Mint version property, not a floating dynamic dependency.

- [x] **Step 1: Write the failing dependency/config test**

Create a test that reads `paper/build.gradle.kts` and the processed plugin configuration contract, asserting the Mint API coordinates, a non-dynamic version property, and required keys `economy.mode`, `economy.mint.currency`, `economy.mint.client-binding`, and `economy.mint.scale`.

- [x] **Step 2: Run test to verify RED**

```bash
./gradlew :paper:test --tests com.azoth.territory.MintDependencyWiringTest
```

Expected: FAIL because the Mint dependency and configuration keys do not exist.

- [x] **Step 3: Add the local Mint Maven repository and dependency**

From `/tmp/aincraft-mint`, run `./gradlew :api:publishMavenPublicationToLocalBuildRepository -Pmint.version=26.8.12.1`. Add `/tmp/aincraft-mint/build/maven-repo` to this project’s repositories and `dev.mintychochip.mint:mint-api:${mintApiVersion}` to the Paper module. Define `mintApiVersion=26.8.12.1` as an explicit Gradle property matching that published artifact; reject dynamic selectors for production dependency resolution.

- [x] **Step 4: Add Mint economy configuration**

Add `economy.mode: VAULT|SIMULATION|MINT`, `economy.mint.currency`, `economy.mint.client-binding`, and `economy.mint.scale` with defaults preserving current Vault behavior. Extend `EconomyConfig` to parse these values without changing the existing default.

- [x] **Step 5: Run the focused test and verify GREEN**

Run the same focused test. Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add settings.gradle.kts paper/build.gradle.kts gradle.properties paper/src/main/resources/config.yml paper/src/test/java/com/azoth/territory/MintDependencyWiringTest.java
git commit -m "build: wire latest Mint API dependency"
```

---

### Task 2: Pure Async Settlement Contract and Outcomes

- Create: `api/src/main/java/com/azoth/territory/economy/AsyncTaxSettlement.java`
- Create: `api/src/main/java/com/azoth/territory/economy/AsyncSettlementResult.java`
- Test: `api/src/test/java/com/azoth/territory/economy/AsyncTaxSettlementContractTest.java`

**Interfaces:**
- `AsyncTaxSettlement.settle(UUID payerId, String guildId, BigDecimal amount, String idempotencyKey)` returns `CompletionStage<AsyncSettlementResult>`.
- `AsyncSettlementResult` contains `Status { COMMITTED, INSUFFICIENT_FUNDS, UNAVAILABLE, REJECTED, RECONCILIATION_REQUIRED }`, optional diagnostic code, and optional receipt identifier.
- No class in `api` or `common` imports Mint, Bukkit, or Paper types.

- [x] **Step 1: Write failing contract tests**

Assert null/blank identifiers are rejected, statuses are stable, result values are immutable, and the pure contract has no dependency on Mint/Paper types. Do not assert `TaxOutcome` mappings here; those belong to the common-module async tax tests in Task 4.

- [x] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :api:test --tests com.azoth.territory.economy.AsyncTaxSettlementContractTest
```

Expected: FAIL because the contract and explicit outcomes do not exist.

- [x] **Step 3: Implement immutable pure types**

Use a Java interface plus record/enum types. Require non-null payer, guild, amount, idempotency key, and status; require positive amount; copy optional diagnostics safely. Do not modify `TaxOutcome` here; its additions and mapping tests belong entirely to Task 4.

- [x] **Step 4: Run focused tests and verify GREEN**

Run the focused API test class. Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add api/src/main/java/com/azoth/territory/economy api/src/test/java/com/azoth/territory/economy/AsyncTaxSettlementContractTest.java
git commit -m "feat: define pure async tax settlement contract"
```

---

### Task 3: Mint Account and Transfer Adapter

**Files:**
- Create: `paper/src/main/java/com/azoth/territory/economy/MintEconomyRail.java`
- Create: `paper/src/main/java/com/azoth/territory/economy/MintOperationResult.java`
- Modify: `paper/src/main/java/com/azoth/territory/economy/EconomyConfig.java`
- Test: `paper/src/test/java/com/azoth/territory/economy/MintEconomyRailTest.java`

**Interfaces:**
- `MintEconomyRail` implements `AsyncTaxSettlement` and exposes `CompletionStage<MintOperationResult> balance(String guildId)`, `deposit(UUID, String, BigDecimal, String)`, and `withdraw(UUID, String, BigDecimal, String)`.
- Account naming is centralized in `MintEconomyRail.guildAccount(String)` and `MintEconomyRail.playerAccount(UUID)`.
- The adapter accepts a trusted `MintClientLease`, configured `CurrencyId`, scale, and logger; it does not obtain an unbound lease.

- [x] **Step 1: Write failing adapter tests**

Cover guild/player account IDs, positive amount canonicalization at configured scale, ensuring both accounts, two signed postings, deterministic idempotency key reuse, committed transfer mapping, insufficient funds mapping, and exceptional completion mapping.

- [x] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :paper:test --tests com.azoth.territory.economy.MintEconomyRailTest
```

Expected: FAIL because the adapter types do not exist.

- [x] **Step 3: Implement account and amount helpers**

Use `AccountId.player(uuid)` and `AccountId.of(NamespaceId.parse("guild:" + guildId))`. Convert at the boundary with BigDecimal, `setScale(configuredScale, RoundingMode.HALF_UP)`, and reject non-positive/overflow values before Mint calls.

Call `lease.accounts().ensure(source)` and `lease.accounts().ensure(destination)` asynchronously. Build `TransactionRequest` with one negative source posting and one positive destination posting using configured `CurrencyId`, reason `azoth.guild-bank.transfer`, and metadata containing guild, direction, and idempotency key. Call `lease.ledger().transact(request)` and map `Committed`/`Rejected`/exceptional outcomes.

Call `lease.accounts().ensure(guildAccount)`, then call `lease.ledger().balance(guildAccount, currency)` and map `BalanceSnapshot.total()`.


- [x] **Step 6: Run focused tests and verify GREEN**

Run the focused test class. Expected: PASS, including assertions that no blocking wait occurs in adapter code.

- [x] **Step 7: Commit**

```bash
git add paper/src/main/java/com/azoth/territory/economy/MintEconomyRail.java paper/src/main/java/com/azoth/territory/economy/MintOperationResult.java paper/src/main/java/com/azoth/territory/economy/EconomyConfig.java paper/src/test/java/com/azoth/territory/economy/MintEconomyRailTest.java
git commit -m "feat: add async Mint guild account rail"
```

---

### Task 4: Async Tax Routing to Governing Guild

**Files:**
- Modify: `common/src/main/java/com/azoth/territory/economy/EconomyBridge.java`
- Modify: `common/src/main/java/com/azoth/territory/economy/TaxOutcome.java`
- Modify: `paper/src/main/java/com/azoth/territory/economy/BukkitEconomyBridge.java`
- Test: `common/src/test/java/com/azoth/territory/economy/EconomyBridgeMintTaxTest.java`

**Interfaces:**
- `EconomyBridge.reportSaleAsync(..., String eventKey, AsyncTaxSettlement settlement)` returns `CompletionStage<TaxReport>`.
- `EconomyBridge.reportCraftAsync(..., String eventKey, AsyncTaxSettlement settlement)` returns `CompletionStage<TaxReport>`.
- Existing synchronous `reportSale` and `reportCraft` remain intact for Vault/simulation.

- [x] **Step 1: Write failing async tax tests**

Test a passed tax policy with a territory governed by guild `g1`, asserting the async settlement receives `g1` rather than the territory ID; test no tax, no government, unknown good, invalid amount, and missing territory do not invoke settlement; test each explicit async status maps correctly.

- [x] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :common:test --tests com.azoth.territory.economy.EconomyBridgeMintTaxTest
```

Expected: FAIL because async tax APIs do not exist.

- [x] **Step 3: Implement validation and guild resolution**

Reuse the same validation order and tax calculation as synchronous reporting. Resolve `governance.resolveForTerritory(territoryId)`, require an assigned `GuildBody`, and pass its `id()` to the pure async settlement. Generate a deterministic event key from the supplied event key plus territory, payer, good, and scaled tax amount when the caller has no stable event ID.

- [x] **Step 4: Map asynchronous completion**

Use `thenApply`/`exceptionally` to map settlement outcomes. Never wait synchronously. Preserve unresolved/reconciliation tracking for reconciliation-required results.

- [x] **Step 5: Expose Bukkit async methods**

Add `reportSaleAsync` and `reportCraftAsync` to `BukkitEconomyBridge`, converting `OfflinePlayer` to UUID and delegating without scheduling or blocking.

- [x] **Step 6: Run focused tests and verify GREEN**

Run the focused common test class. Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add common/src/main/java/com/azoth/territory/economy common/src/test/java/com/azoth/territory/economy/EconomyBridgeMintTaxTest.java paper/src/main/java/com/azoth/territory/economy/BukkitEconomyBridge.java
git commit -m "feat: route async taxes to governing guilds"
```

---

### Task 5: Plugin Lifecycle and Mint Rail Wiring

**Files:**
- Modify: `paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsServices.java`
- Test: `paper/src/test/java/com/azoth/territory/PluginMintWiringTest.java`

**Interfaces:**
- Plugin creates `MintEconomyRail` only when `economy.mode=MINT` and a trusted Mint binding is available.
- `GuildsServices` exposes the Mint rail to bank commands without exposing credentials or raw lease construction.

- [x] **Step 1: Write failing wiring tests**

Test default config still wires Vault/simulation, Mint mode fails closed when the binding is missing, and Mint mode passes the trusted lease/configuration into the guild services composition root.

- [x] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :paper:test --tests com.azoth.territory.PluginMintWiringTest
```

Expected: FAIL because Mint wiring does not exist.

- [x] **Step 3: Resolve the trusted Mint binding**

> Remaining: the host does not yet discover a `MintClientReceiver` through a concrete Paper service binding; MINT mode currently fails closed until that integration is supplied.

Use the actual Mint Paper integration API after inspecting the compiled latest Mint artifact. Resolve the configured binding through Paper service registration or Mint’s documented receiver path; do not instantiate a lease from arbitrary command code. Log only binding availability and currency metadata.

- [x] **Step 4: Wire async tax reporting**

Construct the async tax adapter alongside the existing `EconomyBridge` and expose it through the plugin’s economy facade. Keep existing economy rail selection behavior for non-Mint modes.

- [x] **Step 5: Wire guild services**

Pass the Mint rail into the guild command composition root. Ensure plugin disable closes/unregisters any Mint receiver resources owned by this plugin.

- [x] **Step 6: Run focused tests and verify GREEN**

Run the focused test class. Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add paper/src/main/java/com/azoth/territory/AzothTerritoryPlugin.java paper/src/main/java/org/aincraft/guilds/GuildsServices.java paper/src/test/java/com/azoth/territory/PluginMintWiringTest.java
git commit -m "feat: wire Mint rail into plugin lifecycle"
```

---

### Task 6: Guild Bank Commands and Permissions

**Files:**
- Modify: `paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/commands/BrigadierCommandRegistry.java`
- Test: `paper/src/test/java/org/aincraft/guilds/commands/GuildBankCommandTest.java`

**Interfaces:**
- Commands call `MintEconomyRail.balance`, `deposit`, and `withdraw` only through the injected service.
- Permission checks use existing guild membership plus `DEPOSIT`/`WITHDRAW`; no bypass is granted to ordinary members.
- Existing SQL `Guild.balance` status displays and plot/contract commands are unchanged; `/town bank` is explicitly the Mint cash-bank balance.

- [x] **Step 1: Write failing command tests**

Cover balance display, deposit, withdrawal, missing membership, denied permission, invalid decimal/zero amount, insufficient Mint funds, and async completion message scheduling.

- [x] **Step 2: Run focused tests and verify RED**

```bash
./gradlew :paper:test --tests org.aincraft.guilds.commands.GuildBankCommandTest
```

Expected: FAIL because bank subcommands do not exist.

- [x] **Step 3: Add command grammar**

Add `/town bank`, `/town bank deposit <amount>`, and `/town bank withdraw <amount>` following current Brigadier registration and argument patterns. Use a decimal parser that preserves exact text for BigDecimal conversion.

- [x] **Step 4: Add authorization and transfer calls**

Resolve the player’s guild, check operation permission via `PermissionService`, parse a positive amount, generate a stable command idempotency key, and call the injected rail. Do not touch Bukkit inventory or SQL `Guild.balance`.

- [x] **Step 5: Add main-thread completion messages**

Use the plugin scheduler only for sending command feedback after the `CompletionStage` completes. Report balance, committed transfer, insufficient funds, unavailable Mint, and rejected outcomes distinctly.

- [x] **Step 6: Run focused tests and verify GREEN**

Run the focused command test class. Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java paper/src/main/java/org/aincraft/guilds/commands/BrigadierCommandRegistry.java paper/src/test/java/org/aincraft/guilds/commands/GuildBankCommandTest.java
git commit -m "feat: add Mint-backed guild bank commands"
```

---

### Task 7: Documentation, Integration Tests, and Full Verification

**Files:**
- Modify: `docs/living-specs/economy.md`
- Modify: `docs/living-specs/guild-storage.md`
- Modify: `README.md`
- Create: `paper/src/test/java/com/azoth/territory/MintGuildBankSmokeTest.java`

**Interfaces:**
- Documentation states Mint account names, async restrictions, configuration, explicit coexistence with SQL `Guild.balance`, and non-goals.
- Smoke test exercises tax settlement followed by guild balance lookup through a fake/embedded Mint service implementation.

- [x] **Step 1: Write the failing integration smoke test**

Exercise: configure a territory governed by guild `g1`, report a sale asynchronously, await the returned stage in the test thread only, assert the Mint ledger contains the tax in `guild:g1`, then query the guild bank balance.

- [x] **Step 2: Run focused smoke test and verify RED**

```bash
./gradlew :paper:test --tests com.azoth.territory.MintGuildBankSmokeTest
```

Expected: FAIL until all wiring is present.

- [x] **Step 3: Implement the smoke fixture and documentation**

Use a deterministic in-memory fake implementing the exact Mint interfaces compiled in Tasks 1–5; do not add production-only test methods. Document `MINT` configuration, account naming, async caller contract, SQL wallet coexistence, and the fact that Vault banks are not used in Mint mode.

- [x] **Step 4: Run changed-module tests**

```bash
./gradlew :api:test :common:test :paper:test
```

Expected: PASS with zero failures.

- [x] **Step 5: Run the plugin build path**

```bash
./gradlew :paper:build
```

Expected: PASS and produce `paper/build/libs/azoth-territory-1.1.0.jar`.

- [x] **Step 6: Commit**

```bash
git add docs/living-specs/economy.md docs/living-specs/guild-storage.md README.md paper/src/test/java/com/azoth/territory/MintGuildBankSmokeTest.java
git commit -m "docs: document Mint guild treasury behavior"
```
