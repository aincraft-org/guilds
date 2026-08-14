# Economy — Living Spec

> Status: active
> Last updated: 2026-08-12
> Owners: azoth-territory
> Index: [README.md](./README.md)
>
> Related one-shot designs (historical; this catalog is authoritative for intent going forward):
> - `docs/superpowers/specs/2026-08-05-economy-hooks-design.md`
> - `docs/superpowers/specs/2026-08-06-economy-hooks-extension-design.md`
> - `docs/superpowers/plans/2026-08-08-new-world-completeness.md` (upkeep slice)
>
> Sibling domains: [territory](./territory.md), [governance](./governance.md),
> [persistence](./persistence.md), [guild-storage](./guild-storage.md)

## Intent

Azoth Territory owns a **settlement economy kernel**: other plugins report taxable commerce and schedule treasury expenses; Azoth resolves *where* the activity happened, *who* governs that land, *what rate* PASSED policy decrees impose, and *how* money moves — without becoming a shop, auction house, or inventory system.

Success looks like:

- External commerce/crafting/upkeep plugins can integrate via a small, stable public API (`EconomyBridge` / `BukkitEconomyBridge`).
- Tax rates are political artifacts (PASSED `DecreeEffects` on policies), not hard-coded config tables.
- Money movement has a single seam (`PaymentRail`) with fail-closed Vault mode and an explicit non-monetary simulation mode.
- Treasury debits (upkeep, fortification, other) are restart-safe and never double-charge for the same idempotency key.
- Domain code in `api`/`common` stays free of Bukkit and Vault types.

## Boundaries

### In scope

- Public transaction reporting: `reportSale`, `reportCraft` (explicit gross value).
- Tax math from PASSED policies + `GoodsCatalog` good ids.
- Settlement treasury: Vault bank-per-territory, `SimulationTreasury`, or asynchronous Mint guild accounts (`guild:<guildId>`).
- Expense charging: `chargeExpense` with journaled idempotency (`ExpenseLedger` + Postgres).
- Settlement facility **directory** only: `SettlementFacility` / `FacilityRegistry` (`TRADING_POST`, `STORAGE`) as location metadata for integrations.
- Recurring **upkeep** state machine (`UpkeepEngine`) that schedules amounts and calls `chargeExpense` — not a second money path.
- Durable reconciliation queue for stranded tax settlements (payer charged, refund failed).
- Plugin wiring: `economy.mode`, Vault soft-depend, Postgres stores for facilities/expenses/upkeep/reconciliation.

### Out of scope / non-goals

- Native shops, listings, stock, market matching, or shop commands/UI.
- Automatic price discovery or inferring craft value from `GoodsCatalog`.
- Guild-owned item banks / virtual storage inventories (see future guild-storage domain; facilities remain location hooks only).
- Mint cash guild banks are separate from the SQL `Guild.balance` wallet and do not replace legacy plot, contract, or progression flows.
- Combat fortification levels or siege mechanics (expense *kind* may be `FORTIFICATION`; the combat model is not economy’s).
- Cross-server shared treasuries or multi-shard order books.
- Replacing Vault as the real-money ledger when mode is `VAULT`.

## Invariants

Settled law for this domain (plain bullets — do not “checkbox” these):

1. **Single money seam.** All real transfers go through `PaymentRail` (`settle` for payer→treasury tax; `debitTreasury` for treasury→sink expenses). No ad-hoc Vault calls from bridge callers.
2. **One active rail.** Exactly one of VAULT or SIMULATION is authoritative; never dual-write “Azoth ledger + Vault bank” as both truths for balances.
3. **TAXED means both legs done.** `TaxOutcome.TAXED` only when payer was charged *and* treasury credited. Partial failures are net-zero (`SETTLEMENT_FAILED`) or durable unknown (`SETTLEMENT_RECONCILIATION_REQUIRED`) — never silent success.
4. **Pre-transfer outcomes mutate nothing.** `NO_TERRITORY`, `NO_GOVERNMENT`, `NO_TAX`, `UNKNOWN_GOOD`, `INVALID_AMOUNT` / quantity, `PAYER_UNAVAILABLE`, `VAULT_UNAVAILABLE`, `INSUFFICIENT_FUNDS` leave balances unchanged.
5. **Tax only from PASSED policies.** Proposed/rejected policies contribute no rates; rates merge additively via `DecreeEffectsInterpreter.taxRatesFromPolicies`.
6. **No invented prices.** `reportCraft` requires integration-supplied `grossValue`; quantity is metadata only.
7. **Expense idempotency.** Same non-blank key: first successful debit → later calls return `ALREADY_APPLIED` without re-debit. Restart-visible `PENDING` → `RECONCILIATION_REQUIRED`; **never** auto-retry that key (Vault may already have debited).
8. **Governed territory required for expenses.** `chargeExpense` requires a registered territory with an assigned government.
9. **Facilities are metadata.** A facility does not own inventory, access policy, or money; location must resolve inside its declared territory; at most one facility per block location.
10. **Layering.** Pure domain (`common`/`api` economy, model, registry, decree, persist stores’ domain types) never imports Bukkit or Vault. Vault/Bukkit live under `paper/.../economy`.
11. **Postgres is durable store.** Facilities, expenses, upkeep, and reconciliation use the shared remote PostgreSQL pool — no new JSON/SQLite fallback for these.

## Implementation guidance

### Preferred architecture / module seams

| Layer | Responsibility |
|-------|----------------|
| `api` — `PaymentRail`, `SettlementResult`, treasury debit types; `model` facilities; `decree` tax effects; `registry.FacilityRegistry` | Public contracts and pure models |
| `common` — `EconomyBridge`, `TaxCalculator`, `ExpenseLedger`, `SimulationTreasury`, upkeep engine, Postgres `*Store`s | Domain logic + durable stores (no Bukkit) |
| `paper` — `VaultTreasury`, `BukkitEconomyBridge`, `EconomyConfig`, plugin wiring, scheduled upkeep task | Platform adapters |

Flow for a sale (happy path):

```text
commerce plugin → EconomyBridge.reportSale
  → TerritoryRegistry.resolve(world,x,z)
  → GovernanceRegistry.resolveForTerritory
  → PASSED policy tax rates (DecreeEffectsInterpreter)
  → TaxCalculator
  → PaymentRail.settle(payer, territoryId, tax)
  → TaxReport
```

Expense / upkeep:

```text
UpkeepEngine.tick (or external scheduler)
  → EconomyBridge.chargeExpense(territoryId, kind, amount, periodKey)
  → ExpenseLedger claim PENDING
  → PaymentRail.debitTreasury
  → journal DEBITED | remove + failure outcome | RECONCILIATION_REQUIRED
```

### Data ownership and write paths

- **Vault banks** (mode VAULT): territory id = bank id; balances owned by Vault’s economy plugin.
- **Simulation ledger**: in-memory only; resets on restart by design.
- **Expense journal / facilities / upkeep / reconciliation**: PostgreSQL via `PostgresExpenseStore`, `PostgresFacilityStore`, `PostgresUpkeepStore`, `PostgresReconciliationStore`.
- **Tax rates**: not stored as a separate economy table — derived from territory policy documents (Postgres territory JSONB) at report time.

### Error / failure handling

- VAULT mode is **fail-closed** when Vault, bank support, or the territory bank is missing.
- SIMULATION never returns real-money `TAXED` semantics that imply Vault moved funds (use simulation outcomes / ledger only).
- Expense crash window: `PENDING` after claim but before confirmed debit → operators must reconcile manually; do not invent auto-healing that re-debits.
- Malformed external config should not invent money; stores fail loudly or load empty per store contract — never fabricate balances.

### Testing expectations

- Unit-test pure domain without Paper/Vault: bridge tax paths, expense idempotency, simulation rail, facility registry validation, upkeep tick/period keys.
- Mock `PaymentRail` at the bridge; thin Vault tests only at the adapter boundary.
- Prove: invalid inputs mutate nothing; double `chargeExpense` does not double debit; craft rejects non-positive quantity; facility outside territory rejected.

### Do not

- Call Vault `withdrawPlayer` / `bankDeposit` / `bankWithdraw` outside `VaultTreasury`.
- Treat upkeep as a fake “sale” to the treasury.
- Infer craft prices from the goods catalog.
- Add a second expense ledger path that bypasses `EconomyBridge.chargeExpense`.
- Put Bukkit types into `common` economy packages.
- Implement guild inventory storage inside the facility record.

## Current

Active capability surface (shipped or wired) and any open work still on that surface.

### Capability (shipped)

- [x] Mint API dependency wiring and pure asynchronous settlement contract (GitHub Packages repository, pinned version)
- [x] Mint account rail primitives: guild/player accounts, atomic signed transfers, balance lookup
- [x] Async tax bridge entry points route taxes to the governing guild id
- [x] Plugin registers a documented `MintClientReceiver`; received leases inject the Mint rail into territory tax settlement
- [x] `/guild bank` balance/deposit/withdraw command surface (when a trusted Mint rail is available)

- [x] `EconomyBridge.reportSale` — location resolve, government gate, PASSED tax rates, `PaymentRail.settle`
- [x] `TaxCalculator` + `TaxReport` / `TaxOutcome` mapping from settlement status
- [x] `PaymentRail` + `SettlementResult` (including compensation / reconciliation statuses)
- [x] `SimulationTreasury` and `VaultTreasury` (settle + `debitTreasury`)
- [x] `economy.mode` VAULT | SIMULATION and Vault soft-depend wiring
- [x] `DecreeEffects` / `TaxEffect` on `Policy` and `taxRatesFromPolicies` aggregation
- [x] `GoodsCatalog` good-id validation for tax reports
- [x] `BukkitEconomyBridge` OfflinePlayer convenience entry
- [x] Durable tax reconciliation queue + Postgres reconciliation store
- [x] `reportCraft` with explicit `grossValue` and positive quantity validation
- [x] `SettlementFacility` + `FacilityRegistry` + Postgres facility store
- [ ] Active `TRADING_POST` anchors emit `TradingPostInteractEvent`; Paper runtime observation pending
- [x] `chargeExpense` + `ExpenseLedger` (PENDING → DEBITED / failure cleanup) + Postgres expense store
- [x] `UpkeepEngine` recurring assessment via `chargeExpense` + Postgres upkeep store + plugin scheduled tick
- [x] `/territory upkeep` admin/status surface (command integration)

### Open on the current surface

- [ ] Operator-facing reconciliation UX beyond bridge `unresolvedTransactions()` (document or admin command path)
- [ ] Confirm release notes / README economy section lists public integration API for external shop plugins
- [ ] Verify production checklist: Vault bank accounts auto-created (or ops runbook) per territory id

### Current notes

- Design intent originated in economy-hooks + extension specs; upkeep completed as part of new-world-completeness rather than as a separate economy product.
- Facility JSON / expense JSON designs were superseded by **unified Postgres** — do not reintroduce file stores for these.
- Guild storage design depends on `FacilityType.STORAGE` locations but is **not** this domain’s inventory work.
- `TradingPostInteractEvent` is an integration seam, not a marketplace: listings, stock, prices, NPCs, and shop UI remain external.

## Next

Committed near-term once Current open items are stable. Not speculative.

- [ ] First-party **example / adapter notes** for a shop plugin calling `reportSale` at facility or claim location
- [ ] First-party **crafting integration notes** (who supplies `grossValue`, where to hook)
- [ ] Fortification expense consumers: document `ExpenseKind.FORTIFICATION` contract for combat/siege plugins (no fortification model inside economy)
- [ ] Metrics / logging hooks: tax and expense outcomes histogram or structured log fields for ops
- [ ] Goods catalog expansion process (how new taxable goods are registered without breaking existing policies)

## Future

Parked; promote to Next/Current before implementing.

- [ ] Native trading-post behaviors (listings, stock) — only if product direction changes; prefer external plugins
- [ ] Automatic market-price service or craft valuation without integrator input
- [ ] Cross-server or multi-shard treasury federation
- [ ] Auction / order-book systems
- [ ] Personal housing storage economy (explicitly non-goal today)
- [ ] Split economy into a standalone library deployable without Paper (possible later; registry/governance seams would need hosts)

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-05 | Public `EconomyBridge` first; shops remain external | Keep Azoth a tax/treasury kernel, not a commerce plugin |
| 2026-08-05 | Tax rates from PASSED `DecreeEffects` only | Politics owns rates; avoids hard-coded tax tables fighting governance |
| 2026-08-05 | Single `PaymentRail.settle` encapsulating withdraw→deposit→refund | Callers cannot misorder partial Vault APIs; money invariants stay in one place |
| 2026-08-05 | VAULT default, SIMULATION opt-in non-monetary | Production fail-closed on real money; tests/dev without Vault |
| 2026-08-06 | Facilities are location metadata only | External plugins own inventories/UI; avoid dual ownership of items |
| 2026-08-06 | `reportCraft` requires explicit `grossValue` | No silent price invention from catalog |
| 2026-08-06 | `debitTreasury` distinct from `settle` | Upkeep is not a fake sale; no payer account involved |
| 2026-08-06 | Expense journal PENDING then DEBITED; PENDING after restart → reconcile, never auto-retry | Closes Vault/local crash window without double-debit risk |
| 2026-08-06+ | Facilities/expenses/upkeep/reconciliation on shared Postgres | One durable backend with guilds/territories; drop JSON dual-store drift |
| 2026-08-08 | Upkeep schedules call `chargeExpense` only | Reuse idempotent expense path; one money model |

## Open questions

- [ ] Should unresolved tax reconciliations surface in `/territory` subcommands, logs-only, or a web API admin route?
- [ ] Should facility CRUD be admin-command + REST only, or also guild-mayor self-service in-game?
- [ ] Is territory bank account creation automatic on first tax credit, or an explicit ops step when using Vault banks?
- [ ] When a territory’s `governedByGuildId` changes mid-period, does upkeep stay on the territory id (current model) without transferring treasury — confirm ops expectation?
- [ ] Do we want a separate living-spec domain for **guild-storage** now, or keep it Future until storage work starts?
