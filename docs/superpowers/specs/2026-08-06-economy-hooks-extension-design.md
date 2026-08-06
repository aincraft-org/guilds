# Economy Hooks Extension Design

## Goal

Extend the existing Vault-backed economy bridge so settlement integrations can provide:

- persisted trading-post and storage-facility location hooks;
- transaction tax hooks for shop sales and explicitly valued crafting;
- treasury-funded upkeep and fortification expenses with durable journaled debit state and restart-safe idempotency.

This is an integration layer, not a native shop, inventory, or scheduled upkeep subsystem.

## Current state

The repository already contains:

- `EconomyBridge.reportSale(...)` and `BukkitEconomyBridge`;
- `PaymentRail.settle(...)`, `VaultTreasury`, and `SimulationTreasury`;
- PASSED-policy `TaxEffect` aggregation through `DecreeEffectsInterpreter`;
- Vault/Simulation configuration and durable reconciliation persistence.

There is no native facility directory, crafting tax API, treasury debit operation, expense ledger, or upkeep model.

## Decisions

### Facility directory

Add a pure-domain `SettlementFacility` record:

- `id`, `name`;
- `territoryId`;
- `FacilityType` (`TRADING_POST` or `STORAGE`);
- `worldId`, integer block `x/y/z`.

`FacilityRegistry` stores immutable facility records with synchronized register/unregister/replace operations, validates that each facility's territory exists and the location resolves inside that territory, and rejects duplicate locations. A location can resolve to at most one facility. External plugins own inventories, listings, stock, and UI. `facilities.json` persists the directory; missing files load as empty for backward compatibility.

The plugin exposes the registry. A Bukkit-facing lookup accepts world and block coordinates and returns the facility record, allowing shop/storage plugins to identify settlement-owned locations before applying their own access rules.

### Transaction taxes

Keep `EconomyBridge.reportSale(UUID, world, x, z, goodId, grossAmount)` as the generic shop-sale contract.

Add:

```java
TaxReport reportCraft(
    UUID payerId,
    String worldId,
    int blockX,
    int blockZ,
    String outputGoodId,
    int outputQuantity,
    double grossValue
)
```

`grossValue` is the total transaction value supplied by the integration. `outputQuantity` must be positive and is metadata for the report; no price is inferred from `GoodsCatalog`. Crafting integrations call this API from their own `CraftItemEvent` handling. Azoth does not charge a guessed value when no valuation provider exists.

`BukkitEconomyBridge` gains the equivalent `OfflinePlayer` methods. Existing sale behavior and `TaxReport` outcomes remain compatible.

### Treasury expenses

Extend `PaymentRail` with a distinct operation:

```java
TreasuryDebitResult debitTreasury(String territoryId, double amount);
```

`TreasuryDebitResult` is a record containing a non-null `TreasuryDebitStatus`. `TreasuryDebitStatus` is `DEBITED`, `INSUFFICIENT_FUNDS`, `VAULT_UNAVAILABLE`, or `INVALID_AMOUNT`.

This is not a payer settlement. Vault implementation calls the territory bank's `bankWithdraw`; simulation removes the amount from its active in-memory ledger. A debit succeeds only when the full amount is withdrawn.

Add pure-domain values:

- `ExpenseKind`: `UPKEEP`, `FORTIFICATION`, `OTHER`;
- `ExpenseJournalState`: `PENDING`, `DEBITED`, `UNKNOWN`;
- `ExpenseOutcome`: `DEBITED`, `ALREADY_APPLIED`, `NO_TERRITORY`, `NO_GOVERNMENT`, `INSUFFICIENT_FUNDS`, `VAULT_UNAVAILABLE`, `INVALID_AMOUNT`, `RECONCILIATION_REQUIRED`;
- `ExpenseReport`: outcome, territory id, kind, amount, idempotency key.

Add:

```java
ExpenseReport chargeExpense(
    String territoryId,
    ExpenseKind kind,
    double amount,
    String idempotencyKey
)
```

The bridge requires a nonblank idempotency key and a registered territory with an assigned government. It first writes a `PENDING` journal entry, then calls the rail, then atomically replaces that entry with `DEBITED` only after a successful debit. Reusing a `DEBITED` key returns `ALREADY_APPLIED` without calling the rail. A failed rail response removes the entry after recording the failure. If restart finds `PENDING`, the outcome is `RECONCILIATION_REQUIRED` and the key is never retried automatically: the external bank may have been debited while local persistence was interrupted. This explicit unknown state closes the crash window between Vault mutation and local journal persistence. Failed attempts are otherwise retryable. The expense ledger persists to `expenses.json`, including key, territory, kind, amount, state, and outcome. Existing records are loaded before external integrations can charge expenses.
`ExpenseStore` writes a temporary file and replaces `expenses.json` with an atomic move where the filesystem supports it, falling back to a regular replace. This makes each local journal transition durable without claiming an atomic transaction across Vault and disk.

No automatic scheduler or fortification-level model is introduced. A scheduler or fortification plugin supplies timing and amounts through this API.

### Failure handling

- VAULT mode remains fail-closed when Vault, bank support, or the territory bank is unavailable.
- Simulation mode is explicitly non-monetary and debits only the active simulation ledger.
- Vault debit failures never report `DEBITED`.
- A malformed facility or expense file logs a load error and starts with an empty in-memory collection; it never silently invents money.
- Existing reconciliation behavior remains unchanged.

## Persistence formats

`facilities.json`:

```json
{
  "version": 1,
  "facilities": [
    {
      "id": "market",
      "name": "Market",
      "territoryId": "t1",
      "type": "TRADING_POST",
      "worldId": "world",
      "x": 10,
      "y": 64,
      "z": 10
    }
  ]
}
```

`expenses.json`:

```json
{
  "version": 1,
  "expenses": [
    {
      "idempotencyKey": "t1-upkeep-2026-08-06",
      "territoryId": "t1",
      "kind": "UPKEEP",
      "amount": 100.0,
      "state": "DEBITED",
      "outcome": "DEBITED"
    }
  ]
}
```

Both files are written under the plugin data folder and loaded as empty when absent.

## Verification

Tests must prove:

1. facility registration rejects unknown/outside/duplicate locations and location lookup works;
2. facilities persist and reload;
3. sale and craft APIs apply the same PASSED-policy tax path, reject invalid quantity/value, and delegate payer UUIDs through Bukkit;
4. Vault debit calls bank withdrawal, maps insufficient/unavailable responses, and never uses payer APIs;
5. simulation debit changes only active simulation state and never uses Vault;
6. expense idempotency prevents a second debit and survives store reload;
7. plugin wiring loads/saves facility and expense stores;
8. root tests/build pass, with any unrelated aggregate-module blockers reported separately.

## Out of scope

- native inventories, listings, market matching, shop commands, or UI;
- automatic price discovery or market-price configuration;
- automatic daily upkeep scheduling;
- fortification state/levels or combat mechanics;
- CraftItemEvent valuation without an explicit integration-provided gross value.
