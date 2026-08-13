# Mint-Native Guild Banks and Tax Routing Design

## Goal

Integrate the latest `aincraft-org/mint` API into Azoth Territory so taxes settle asynchronously through Mint, with each guild owning a native Mint treasury account that receives taxes and supports controlled balance, deposit, and withdrawal operations.

## Context

Azoth Territory currently calculates policy taxes in `common` and settles synchronously through `PaymentRail`. The Paper module provides a Vault-backed rail whose treasury destination is a Vault bank named after the territory. Guilds are already integrated into the same plugin and use SQL-backed services, permissions, commands, and migrations.

The current Mint repository is `https://github.com/aincraft-org/mint`, latest inspected commit `cee5b04`. Its published group is `dev.mintychochip.mint`; the API module publishes `mint-api`. The public API is asynchronous and lease-scoped. The relevant primitives are `MintClientReceiver`/`MintClientLease`, `AccountService.ensure(AccountId)`, `LedgerService.balance(AccountId, CurrencyId)`, and `LedgerService.transact(TransactionRequest)`. A transfer is represented by signed `Posting` values in one atomic request. Mint has no bank abstraction; guild banks are namespaced Mint accounts.

## Decisions

1. Use the latest Mint API artifact from the configured GitHub Packages repository. Do not invent a published release version. The Gradle configuration must make the version explicit/configurable and document that the current repository has no public release tag; local integration tests may publish Mint locally and consume that exact version.
2. Obtain a trusted `MintClientLease` from a configured `MintClientReceiver` binding. Do not construct an unbound command lease or access Mint through Vault.
3. Keep existing synchronous Vault and simulation behavior intact. Add an asynchronous tax-reporting path for Mint rather than blocking the Paper main thread or changing every existing caller in one cutover.
4. Route a tax to the governing guild account, not the territory account. The destination is `AccountId.of(NamespaceId.parse("guild:" + guildId))`.
5. Keep the common and API modules free of Mint/Paper classes. Define the async boundary as a pure API contract accepting UUID, guild ID, BigDecimal amount, idempotency key, and returning `CompletionStage<AsyncSettlementResult>`.
6. Add explicit async outcomes for committed, insufficient funds, unavailable, rejected, and reconciliation-required settlement. Map these to distinct `TaxOutcome` values rather than reusing the Vault-specific unavailable outcome.
7. Use `AccountId.player(payerUuid)` for payer accounts. Ensure both payer and guild accounts before transfer.
8. Use a configured Mint `CurrencyId` for the economy currency. Amount conversion must use `BigDecimal` and a documented scale/rounding rule; no binary floating-point transfer amount is sent to Mint.
9. Use a deterministic idempotency key derived from tax event identity (territory, payer, good, amount, and caller-supplied event key where available). Retries reuse the same key.
10. Guild bank commands use existing guild membership and `GuildPermission.DEPOSIT` / `WITHDRAW` checks. Mayors/admins retain existing privileged paths according to the current permission service.
11. The existing SQL `Guild.balance` wallet and plot/contract/resource flows remain unchanged and separate in this first integration. Mint is the source of truth only for the new cash tax/guild-bank account; no existing SQL balance display or purchase flow is silently migrated.

## Architecture
### Pure async contract

Add a small API-module contract with no Mint or Paper dependencies:

- `AsyncTaxSettlement` accepts `(UUID payerId, String guildId, BigDecimal amount, String idempotencyKey)`.
- It returns `CompletionStage<AsyncSettlementResult>`.
- `AsyncSettlementResult` contains a stable status (`COMMITTED`, `INSUFFICIENT_FUNDS`, `UNAVAILABLE`, `REJECTED`, or `RECONCILIATION_REQUIRED`) and optional diagnostic code/receipt data.

The Paper Mint adapter implements this contract. The common tax bridge depends only on the contract and maps statuses to tax outcomes.

### Mint adapter

Add a Paper-side `MintEconomyRail` that depends on Mint API types and a trusted lease provider. It exposes balance/deposit/withdraw operations for commands and implements `AsyncTaxSettlement` for taxes. It ensures accounts, builds one atomic two-posting `TransactionRequest`, invokes the lease’s ledger service, and maps `OperationOutcome` rejections to the pure result type. It never calls `join`, `get`, or other blocking waits on the Paper thread.

### Async tax flow

Extend the common economy boundary with an asynchronous report method that performs the same validation and tax calculation as `reportSale`, then resolves the governing guild ID from `GovernanceRegistry`/`GuildBody`. For Mint mode it invokes the injected pure async contract. Existing synchronous methods remain for Vault/simulation and continue to use `PaymentRail`.

The Paper Bukkit facade exposes `CompletionStage<TaxReport> reportSaleAsync` and `reportCraftAsync`. Existing callers are not silently switched to blocking Mint behavior. The plugin config selects the Mint rail explicitly and fails closed with a clear unavailable outcome when Mint is configured but not bound or the currency is invalid.
### Guild bank command surface

Add a guild bank command under the existing guild command family using the project’s Brigadier conventions:

- `/town bank` or the established equivalent: show the current guild Mint balance.
- `/town bank deposit <amount>`: debit the player Mint account and credit the guild account.
- `/town bank withdraw <amount>`: debit the guild account and credit the player account.

Commands validate sender/player identity, guild membership, positive decimal amount, configured currency, and permission before submitting async work. Completion messages are sent back on the Bukkit scheduler/main thread. Failures distinguish insufficient funds, unavailable Mint, invalid amount, unauthorized operation, and unknown guild.

### Configuration and lifecycle

Add Mint settings to the existing plugin configuration: enabled/mode, API client binding identifier, currency ID, decimal scale, and timeout/diagnostic settings only where supported by the actual API. During plugin enable, resolve the trusted lease/provider and validate the configured currency without blocking the main thread. Guild accounts are provisioned lazily with `AccountService.ensure`; optional startup reconciliation may ensure known guilds but must not block startup.

## Persistence and coexistence

No second Mint money ledger is introduced. Mint is the source of truth for the new cash tax/guild-bank account. Existing SQL `Guild.balance` remains the source of truth for legacy plot purchases, guild contracts, and any resource/progression flow that already uses it; those flows are explicitly out of scope for this integration and are not displayed as the Mint cash balance. Tax event idempotency belongs to Mint’s idempotency key; if the existing tax event path has no stable event key, add one at the async boundary rather than persisting duplicate cash balances locally.

## Error handling

- Missing payer, guild, territory, government, good, or invalid amount returns the existing validation outcome without invoking Mint.
- Missing Mint binding, inactive currency, or unavailable service returns an explicit unavailable outcome.
- Mint `Rejected` results map insufficient funds and authorization/revision/inactive-currency failures without retrying unless the rejection is explicitly transient.
- Exceptional completion is logged with guild, payer, idempotency key, and Mint rejection metadata, but never exposes secrets.
- A committed Mint receipt is treated as settled even if the caller’s response delivery fails; retrying the same idempotency key must return the original receipt.
- No synchronous waiting occurs on Paper’s main thread.

## Testing

1. API/build wiring resolves the exact latest Mint API coordinates from a local Maven repository and compiles against `MintClientReceiver`, `MintClientLease`, `AccountService`, `LedgerService`, account/currency IDs, postings, and transaction requests.
2. Adapter unit tests verify account naming, amount scale conversion, two-posting atomic transfers, deterministic idempotency keys, account provisioning, committed outcomes, insufficient funds, unavailable service, and rejected outcomes.
3. Async tax tests verify a passed tax policy routes to the governing guild account, no tax/no government/invalid input do not call Mint, and completion produces the expected `TaxReport` without blocking callers.
4. Guild command tests verify membership/permission enforcement, async completion messaging, balance display, deposit, withdrawal, and failure messages.
5. Existing common economy, Vault, guild service, and plugin wiring tests continue to pass.
6. A Paper smoke scenario exercises a player tax event and guild bank balance through the configured Mint test runtime, with no main-thread blocking.

## Non-goals

- Implementing Vault bank APIs through Mint.
- Replacing the existing Vault rail for servers that do not enable Mint.
- Local shadow balances, debt, overdrafts, scheduled withdrawals, interest, or guild-bank GUI design.
- Synchronous compatibility wrappers that block Paper’s main thread.
