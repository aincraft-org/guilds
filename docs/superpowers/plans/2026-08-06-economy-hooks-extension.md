# Economy Hooks Extension Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Add persisted trading-post/storage facility hooks, explicitly valued crafting-tax reporting, and idempotent Vault/simulation treasury expenses for upkeep and fortification integrations.

**Architecture:** Keep Azoth as a pure-domain economy kernel. `SettlementFacility`/`FacilityRegistry` describe persisted facility locations without owning inventories; `EconomyBridge` handles sale/craft tax and treasury expense contracts; `PaymentRail` owns all money movement, including a distinct treasury debit. Bukkit/Vault classes adapt external events and persistence, while Vault mode remains fail-closed and simulation remains non-monetary.

**Tech Stack:** Java 21, Gradle Kotlin DSL, Paper 1.21.4 API, VaultAPI 1.7, Gson 2.11.0, JUnit 5, Mockito 5.14.2.

## Global Constraints

- Pure `model`, `registry`, `decree`, `economy`, and `persist` logic remains Bukkit/Vault-free except `EconomyConfig`, `VaultTreasury`, `BukkitEconomyBridge`, and plugin wiring.
- External shops/storage plugins own inventories, listings, stock, UI, scheduling, and market valuation.
- `reportCraft` accepts an explicit total gross value; never infer a price from `GoodsCatalog`.
- `PaymentRail.debitTreasury` is distinct from payer settlement; upkeep is never faked as a sale.
- Vault debit uses `bankHas` then `bankWithdraw`; simulation debit changes only its active ledger.
- Expense journal writes `PENDING` before external debit and `DEBITED` only after success. A restart-visible `PENDING` is `RECONCILIATION_REQUIRED` and is never retried automatically.
- Facility and expense files are backward-compatible: missing files load empty; writes use temporary-file replacement with atomic move where supported.
- Write failing tests first, observe the expected failure, implement the smallest passing code, run focused and root tests, then commit each coherent task atomically.
- Do not modify the unrelated `guilds` subproject.

---

### Task 1: Settlement facilities and location registry

**Files:**
- Create: `src/main/java/com/azoth/territory/model/FacilityType.java`
- Create: `src/main/java/com/azoth/territory/model/SettlementFacility.java`
- Create: `src/main/java/com/azoth/territory/registry/FacilityRegistry.java`
- Test: `src/test/java/com/azoth/territory/registry/FacilityRegistryTest.java`

**Interfaces:**
- `enum FacilityType { TRADING_POST, STORAGE }`.
- `record SettlementFacility(String id, String name, String territoryId, FacilityType type, String worldId, int x, int y, int z)`.
- `FacilityRegistry(TerritoryRegistry territories)`.
- `void register(SettlementFacility facility)`; `boolean unregister(String id)`; `Optional<SettlementFacility> get(String id)`; `List<SettlementFacility> list()`; `Optional<SettlementFacility> resolve(String worldId, int x, int y, int z)`; `void replaceAll(Collection<SettlementFacility> facilities)`.

- [ ] **Step 1: Write failing tests.** Cover valid registration and lookup; reject unknown territory; reject a facility whose `worldId/x/z` is outside its territory; reject duplicate id and duplicate location; allow two facility types at different locations; unregister and replaceAll remain atomic on validation failure.

```java
@Test
void resolvesRegisteredFacilityByBlockLocation() {
    TerritoryRegistry territories = new TerritoryRegistry(List.of(territory("t1")));
    FacilityRegistry facilities = new FacilityRegistry(territories);
    SettlementFacility market = new SettlementFacility(
            "market", "Market", "t1", FacilityType.TRADING_POST, "world", 5, 64, 5);
    facilities.register(market);
    assertEquals(Optional.of(market), facilities.resolve("world", 5, 64, 5));
}

@Test
void rejectsFacilityOutsideAssignedTerritory() {
    FacilityRegistry facilities = new FacilityRegistry(new TerritoryRegistry(List.of(territory("t1"))));
    assertThrows(IllegalArgumentException.class, () -> facilities.register(
            new SettlementFacility("bad", "Bad", "t1", FacilityType.STORAGE, "world", 500, 64, 500)));
}
```

- [ ] **Step 2: Run the focused test and verify compilation/failure.**

Run: `./gradlew :test --tests com.azoth.territory.registry.FacilityRegistryTest`

Expected: compilation failure because the facility types/registry do not exist.

- [ ] **Step 3: Implement immutable records and synchronized registry.**

`SettlementFacility` trims and requires nonblank ids, territory/world ids, and non-null type; blank names fall back to the id. `FacilityRegistry.register` requires the territory id to exist, resolves the location through `TerritoryRegistry.resolve(worldId,x,z)`, requires the resolved territory id to match, and rejects any existing facility with the same id or coordinates. Mutations synchronize and validate a complete candidate map before replacing state.

- [ ] **Step 4: Run focused and root tests.**

Run: `./gradlew :test --tests com.azoth.territory.registry.FacilityRegistryTest`

Expected: all facility tests pass. Then run `./gradlew :test`; expected root suite pass.

- [ ] **Step 5: Commit.**

```bash
git add src/main/java/com/azoth/territory/model/FacilityType.java src/main/java/com/azoth/territory/model/SettlementFacility.java src/main/java/com/azoth/territory/registry/FacilityRegistry.java src/test/java/com/azoth/territory/registry/FacilityRegistryTest.java
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Add persisted settlement facility domain hooks"
```

---

### Task 2: Persist facility directory

**Files:**
- Create: `src/main/java/com/azoth/territory/persist/FacilityStore.java`
- Test: `src/test/java/com/azoth/territory/persist/FacilityStoreTest.java`

**Interfaces:**
- `FacilityStore(Path file)`; `Path file()`; `void save(FacilityRegistry registry)`; `void loadInto(FacilityRegistry registry)`.
- JSON root has `version: 1` and a `facilities` array with `id`, `name`, `territoryId`, `type`, `worldId`, `x`, `y`, `z`.

- [ ] **Step 1: Write failing round-trip/missing-file tests.** Save two facility types, load into a fresh registry, compare records; missing file must leave an empty registry; malformed JSON must throw `IOException` rather than silently accepting partial state.

- [ ] **Step 2: Run `./gradlew :test --tests com.azoth.territory.persist.FacilityStoreTest`; observe the missing-class failure.**

- [ ] **Step 3: Implement manual Gson codec and safe writes.** Follow `TerritoryStore` conventions. Save to `<file>.tmp`, close the writer, then replace the target with `ATOMIC_MOVE` and fall back to `REPLACE_EXISTING` when unsupported. Load into a temporary `FacilityRegistry`, then call `replaceAll` so invalid input does not partially mutate the live registry.

- [ ] **Step 4: Run focused and root tests; expected PASS.**

- [ ] **Step 5: Commit.**

```bash
git add src/main/java/com/azoth/territory/persist/FacilityStore.java src/test/java/com/azoth/territory/persist/FacilityStoreTest.java
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Persist settlement facility directory"
```

---

### Task 3: Add treasury debit to payment rails

**Files:**
- Create: `src/main/java/com/azoth/territory/economy/TreasuryDebitStatus.java`
- Create: `src/main/java/com/azoth/territory/economy/TreasuryDebitResult.java`
- Modify: `src/main/java/com/azoth/territory/economy/PaymentRail.java`
- Modify: `src/main/java/com/azoth/territory/economy/SimulationTreasury.java`
- Modify: `src/main/java/com/azoth/territory/economy/VaultTreasury.java`
- Modify: `src/main/java/com/azoth/territory/AzothTerritoryPlugin.java`
- Test: `src/test/java/com/azoth/territory/economy/TreasuryDebitTest.java`
- Test: `src/test/java/com/azoth/territory/economy/VaultTreasuryTest.java`
- Modify: `src/test/java/com/azoth/territory/economy/EconomyBridgeDomainTest.java`
- Modify: `src/test/java/com/azoth/territory/economy/BukkitEconomyBridgeTest.java`

**Interfaces:**
- `enum TreasuryDebitStatus { DEBITED, INSUFFICIENT_FUNDS, VAULT_UNAVAILABLE, INVALID_AMOUNT }`.
- `record TreasuryDebitResult(TreasuryDebitStatus status)` with non-null canonical validation.
- Add `TreasuryDebitResult debitTreasury(String territoryId, double amount)` to `PaymentRail`.
- `SimulationTreasury.activeBalanceOf(String)` remains the active ledger observation; successful debit updates it.
- `VaultTreasury.debitTreasury` must never call player APIs. It checks economy/bank support, existing bank, `bankHas(territoryId, amount)`, then `bankWithdraw(territoryId, amount)`.

- [ ] **Step 1: Write failing tests.** Cover simulation debit success/insufficient/invalid and Vault success, insufficient balance, missing bank, unsupported economy, and failed withdrawal. Verify Vault debit calls only bank methods, never `withdrawPlayer` or `depositPlayer`.

```java
@Test
void simulationDebitReducesActiveTreasury() {
    SimulationTreasury treasury = new SimulationTreasury().credit("t1", 10.0);
    assertEquals(TreasuryDebitStatus.DEBITED,
            treasury.debitTreasury("t1", 4.0).status());
    assertEquals(6.0, treasury.activeBalanceOf("t1"), 1e-9);
}
```

- [ ] **Step 2: Run `./gradlew :test --tests com.azoth.territory.economy.TreasuryDebitTest`; observe missing types/method failure.**

- [ ] **Step 3: Implement debit result and rails.** Validate positive finite amounts. In Vault, bank lookup failure maps to `VAULT_UNAVAILABLE`; `bankHas` failure maps to `INSUFFICIENT_FUNDS`; successful `bankWithdraw` alone yields `DEBITED`. Null responses are failures and never yield `DEBITED`.

- [ ] **Step 4: Run focused economy tests and the root suite; expected PASS.**

- [ ] **Step 5: Commit.**

```bash
git add src/main/java/com/azoth/territory/economy/PaymentRail.java src/main/java/com/azoth/territory/economy/TreasuryDebitStatus.java src/main/java/com/azoth/territory/economy/TreasuryDebitResult.java src/main/java/com/azoth/territory/economy/SimulationTreasury.java src/main/java/com/azoth/territory/economy/VaultTreasury.java src/test/java/com/azoth/territory/economy/TreasuryDebitTest.java src/test/java/com/azoth/territory/economy/VaultTreasuryTest.java
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Add treasury debit operations to payment rails"
```

---

### Task 4: Expense journal and idempotent EconomyBridge API

**Files:**
- Create: `src/main/java/com/azoth/territory/economy/ExpenseKind.java`
- Create: `src/main/java/com/azoth/territory/economy/ExpenseJournalState.java`
- Create: `src/main/java/com/azoth/territory/economy/ExpenseOutcome.java`
- Create: `src/main/java/com/azoth/territory/economy/ExpenseEntry.java`
- Create: `src/main/java/com/azoth/territory/economy/ExpenseReport.java`
- Create: `src/main/java/com/azoth/territory/economy/ExpenseLedger.java`
- Create: `src/main/java/com/azoth/territory/persist/ExpenseStore.java`
- Modify: `src/main/java/com/azoth/territory/economy/EconomyBridge.java`
- Test: `src/test/java/com/azoth/territory/economy/ExpenseLedgerTest.java`
- Test: `src/test/java/com/azoth/territory/persist/ExpenseStoreTest.java`
- Test: `src/test/java/com/azoth/territory/economy/EconomyBridgeExpenseTest.java`

**Interfaces:**
- `ExpenseKind { UPKEEP, FORTIFICATION, OTHER }`.
- `ExpenseJournalState { PENDING, DEBITED, UNKNOWN }`.
- `ExpenseOutcome { DEBITED, ALREADY_APPLIED, NO_TERRITORY, NO_GOVERNMENT, INSUFFICIENT_FUNDS, VAULT_UNAVAILABLE, INVALID_AMOUNT, RECONCILIATION_REQUIRED }`.
- `record ExpenseEntry(String idempotencyKey, String territoryId, ExpenseKind kind, double amount, ExpenseJournalState state, ExpenseOutcome outcome)`.
- `record ExpenseReport(ExpenseOutcome outcome, String territoryId, ExpenseKind kind, double amount, String idempotencyKey)`.
- `ExpenseLedger` stores entries by key and accepts a snapshot sink `Consumer<Collection<ExpenseEntry>>`; it exposes `load`, `find`, `put`, `remove`, and `entries`.
- `ExpenseStore(Path)` exposes `save(Collection<ExpenseEntry>)`, `List<ExpenseEntry> load()`, and `Path file()`; writes `expenses.json` with atomic temp replacement.
- Add an `EconomyBridge` constructor overload accepting `ExpenseLedger`, preserving the existing five-argument constructor with an empty ledger.
- Add `ExpenseReport chargeExpense(String territoryId, ExpenseKind kind, double amount, String idempotencyKey)`.

- [ ] **Step 1: Write failing tests.** Cover no territory/government, invalid amount/key, successful debit, insufficient/unavailable mapping, duplicate successful key without a second rail call, PENDING load mapping to reconciliation-required, and failed debit removal/retry. Store tests cover JSON round-trip and state fields.

```java
@Test
void duplicateSuccessfulExpenseDoesNotDebitAgain() {
    RecordingRail rail = new RecordingRail(TreasuryDebitStatus.DEBITED);
    EconomyBridge bridge = bridgeWithTreasury(rail);
    assertEquals(ExpenseOutcome.DEBITED,
            bridge.chargeExpense("t1", ExpenseKind.UPKEEP, 10.0, "day-1").outcome());
    assertEquals(ExpenseOutcome.ALREADY_APPLIED,
            bridge.chargeExpense("t1", ExpenseKind.UPKEEP, 10.0, "day-1").outcome());
    assertEquals(1, rail.debitCalls);
}

@Test
void pendingJournalEntryIsNeverRetriedAfterRestart() {
    ExpenseLedger ledger = new ExpenseLedger();
    ledger.load(List.of(new ExpenseEntry(
            "day-1", "t1", ExpenseKind.UPKEEP, 10.0,
            ExpenseJournalState.PENDING, ExpenseOutcome.RECONCILIATION_REQUIRED)));
    EconomyBridge bridge = bridgeWithTreasury(new RecordingRail(TreasuryDebitStatus.DEBITED), ledger);
    assertEquals(ExpenseOutcome.RECONCILIATION_REQUIRED,
            bridge.chargeExpense("t1", ExpenseKind.UPKEEP, 10.0, "day-1").outcome());
}
```

- [ ] **Step 2: Run `./gradlew :test --tests com.azoth.territory.economy.EconomyBridgeExpenseTest`; observe missing-class failure.**

- [ ] **Step 3: Implement journal transitions.** Validate territory and assigned government before journaling. Existing `DEBITED` returns `ALREADY_APPLIED`; `PENDING`/`UNKNOWN` returns reconciliation-required. Put `PENDING` through the ledger sink before calling `debitTreasury`; on `DEBITED`, replace with `DEBITED`; on a non-debited rail response, remove the entry. If a sink failure occurs after the rail reports `DEBITED`, retain `PENDING` and return reconciliation-required. Never auto-retry unknown state.

- [ ] **Step 4: Implement Gson `ExpenseStore` and run focused tests.**

- [ ] **Step 5: Run `./gradlew :test` and commit.**

```bash
git add src/main/java/com/azoth/territory/economy/ExpenseKind.java src/main/java/com/azoth/territory/economy/ExpenseJournalState.java src/main/java/com/azoth/territory/economy/ExpenseOutcome.java src/main/java/com/azoth/territory/economy/ExpenseEntry.java src/main/java/com/azoth/territory/economy/ExpenseReport.java src/main/java/com/azoth/territory/economy/ExpenseLedger.java src/main/java/com/azoth/territory/persist/ExpenseStore.java src/main/java/com/azoth/territory/economy/EconomyBridge.java src/test/java/com/azoth/territory/economy/ExpenseLedgerTest.java src/test/java/com/azoth/territory/persist/ExpenseStoreTest.java src/test/java/com/azoth/territory/economy/EconomyBridgeExpenseTest.java
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Add idempotent treasury expense API"
```

---

### Task 5: Sale/craft tax APIs and Bukkit facade

**Files:**
- Modify: `src/main/java/com/azoth/territory/economy/TaxOutcome.java`
- Modify: `src/main/java/com/azoth/territory/economy/EconomyBridge.java`
- Modify: `src/main/java/com/azoth/territory/economy/BukkitEconomyBridge.java`
- Test: `src/test/java/com/azoth/territory/economy/EconomyBridgeCraftTest.java`
- Modify: `src/test/java/com/azoth/territory/economy/BukkitEconomyBridgeTest.java`

**Interfaces:**
- Add `TaxOutcome.INVALID_QUANTITY`.
- Add `TaxReport reportCraft(UUID payerId, String worldId, int blockX, int blockZ, String outputGoodId, int outputQuantity, double grossValue)`.
- Add the matching `OfflinePlayer` method to `BukkitEconomyBridge`.

- [ ] **Step 1: Write failing tests.** Verify positive quantity delegates through the same PASSED-policy tax path as sale; zero/negative quantity returns `INVALID_QUANTITY` without calling the rail; invalid gross returns `INVALID_AMOUNT`; Bukkit facade passes the player's UUID and null payer remains `PAYER_UNAVAILABLE`.

- [ ] **Step 2: Run `./gradlew :test --tests com.azoth.territory.economy.EconomyBridgeCraftTest`; observe missing method/enum failure.**

- [ ] **Step 3: Implement `reportCraft`.** Validate quantity first, then delegate with the explicit total gross value. Do not multiply or price the output; `outputQuantity` is metadata validation only. Add the Bukkit adapter method with identical argument order.

- [ ] **Step 4: Run focused and root tests; expected PASS.**

- [ ] **Step 5: Commit.**

```bash
git add src/main/java/com/azoth/territory/economy/TaxOutcome.java src/main/java/com/azoth/territory/economy/EconomyBridge.java src/main/java/com/azoth/territory/economy/BukkitEconomyBridge.java src/test/java/com/azoth/territory/economy/EconomyBridgeCraftTest.java src/test/java/com/azoth/territory/economy/BukkitEconomyBridgeTest.java
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Add explicitly valued crafting tax hooks"
```

---

### Task 6: Wire facilities and expenses into the plugin

**Files:**
- Modify: `src/main/java/com/azoth/territory/AzothTerritoryPlugin.java`
- Test: `src/test/java/com/azoth/territory/PluginEconomyWiringTest.java`

**Interfaces:**
- Add fields/getters for `FacilityRegistry`, `FacilityStore`, and `ExpenseLedger`/`ExpenseStore` as needed by external integrations.
- Add `BukkitEconomyBridge.resolveFacility(String worldId, int x, int y, int z)` delegating to the facility registry, or expose an equivalent plugin-level lookup with the exact same result type.

- [ ] **Step 1: Write failing wiring tests.** Verify the plugin constructs the facility/expense stores under its data folder, loads empty files safely, and the economy bridge receives the loaded expense ledger before it is exposed. Keep tests metadata/config-oriented unless a Paper mock server is already available.

- [ ] **Step 2: Run the focused plugin test and observe the missing getters/fields.**

- [ ] **Step 3: Wire startup/shutdown.** Create facility and expense stores after loading the territory registry; load them into temporary registries/ledger before external APIs are exposed; log malformed-file errors at `SEVERE`; save both on disable using the stores' atomic writes. Construct `EconomyBridge` with the loaded expense ledger. Preserve Vault-absent `UnavailableRail` behavior and existing web/protection wiring.

- [ ] **Step 4: Run `./gradlew :test` and inspect generated files in a temp-data test; expected PASS.**

- [ ] **Step 5: Commit.**

```bash
git add src/main/java/com/azoth/territory/AzothTerritoryPlugin.java src/main/java/com/azoth/territory/economy/BukkitEconomyBridge.java src/test/java/com/azoth/territory/PluginEconomyWiringTest.java
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Wire facility and expense persistence into plugin"
```

---

### Task 7: Full verification and documentation audit

**Files:** none unless verification exposes a feature defect.

- [ ] **Step 1: Run focused feature suites.**

```bash
./gradlew :test --tests com.azoth.territory.registry.FacilityRegistryTest --tests com.azoth.territory.persist.FacilityStoreTest --tests com.azoth.territory.economy.TreasuryDebitTest --tests com.azoth.territory.economy.EconomyBridgeExpenseTest --tests com.azoth.territory.economy.EconomyBridgeCraftTest
```

Expected: all focused feature tests pass.

- [ ] **Step 2: Run the root build.**

```bash
./gradlew :build --rerun-tasks
```

Expected: `BUILD SUCCESSFUL` for the Azoth root project and the jar contains the updated plugin classes/configuration.

- [ ] **Step 3: Inspect packaged metadata and persistence contracts.**

```bash
unzip -p build/libs/azoth-territory-1.0.0-SNAPSHOT.jar plugin.yml
```

Verify `softdepend: [Vault]` remains present. Read the final facility/expense/Vault sources and confirm: no Vault player calls occur during treasury debit; `DEBITED` requires bank withdrawal success; PENDING expense entries are not auto-retried; craft taxes require explicit gross value; facility locations are territory-bound.

- [ ] **Step 4: Run aggregate build diagnostics.**

```bash
./gradlew build
./gradlew :guilds:compileJava
./gradlew :guilds:compileTestJava
```

If the unrelated `guilds` test compilation remains broken, report that exact blocker without modifying Guilds sources.

- [ ] **Step 5: Commit only any verification-driven fixes.**

Use a narrow atomic commit with a message naming the corrected behavior; do not create a documentation-only mega-commit.
