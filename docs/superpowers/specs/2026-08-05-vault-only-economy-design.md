# Vault-only economy design

## Goal

Make Vault the only runtime source of money for the merged Azoth Territory and Guilds plugins. Neither plugin may create or credit a virtual balance when Vault is unavailable.

## Scope

- Remove `SIMULATION` from Azoth runtime economy configuration.
- Remove `SimulationTreasury` from Azoth plugin startup and production wiring.
- Remove the Guilds economy service's persisted-town-balance fallback when Vault is unavailable.
- Keep `PaymentRail`, `EconomyBridge`, and `VaultTreasury` as Azoth's settlement boundary.
- Keep Vault as a soft dependency so both plugins can load without Vault.
- When Vault or its provider is unavailable, use an unavailable path and return `VAULT_UNAVAILABLE` without charging or crediting anyone.
- Remove `SIMULATED_TAXED` and simulation-only constructor state if no remaining caller requires them.
- Update current configuration, tests, and user-facing documentation to describe Vault-only behavior.

## Runtime flow

1. Azoth startup resolves the Vault economy provider.
2. A valid provider creates `VaultTreasury` and passes it to `EconomyBridge`.
3. Missing Vault, missing provider, or unsupported bank operations select `UnavailableRail`.
4. `EconomyBridge.reportSale(...)` returns `VAULT_UNAVAILABLE` before settlement when the rail is unavailable.
5. `VaultTreasury` performs withdraw-first settlement, deposits into the territory Vault bank, refunds on deposit failure, and records reconciliation-required failures.
6. Guilds economy operations require a Vault provider; they do not mutate `Town.balance` as an alternative money source.

No simulation ledger or persisted-town-balance fallback is reachable from plugin runtime.

## Error and safety invariants

- No successful tax report without a real Vault payer charge.
- No territory or town balance is created by a missing Vault provider.
- Invalid, untaxed, unknown-good, uncontained, and ungoverned sales remain non-settling outcomes.
- Vault bank provisioning failures remain `VAULT_UNAVAILABLE`.
- Compensation failure remains `RECONCILIATION_REQUIRED` and is not reported as success.
- Guilds economy actions fail safely when Vault is absent instead of mutating stored town balances.

## Compatibility

The public Bukkit-facing Azoth sale adapter remains unchanged. Domain code continues to avoid Bukkit/Vault types except in `BukkitEconomyBridge` and `VaultTreasury`. Existing `PaymentRail` abstractions remain so settlement behavior can be tested without a live server. Guilds' existing economy service API remains available, but its money movement is Vault-backed only.

## Verification

- Compile and run economy tests covering Vault settlement, unavailable Vault behavior, tax outcome mapping, plugin wiring, and Guilds Vault-only behavior.
- Run the full root test suite when the repository's existing economy WIP test sources are complete.
- Build both production plugin artifacts and confirm Vault remains declared in Gradle dependencies and plugin metadata.
