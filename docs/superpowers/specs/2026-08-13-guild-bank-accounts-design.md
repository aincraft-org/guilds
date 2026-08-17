# Guild Bank Accounts Design

## Goal

Add explicit player enrollment in guild banks while keeping Mint authoritative for cash balances. Players open access with `/guild bank open`; guild level controls the total guild-bank capacity; automatic territory taxes credit governing guilds without requiring payer enrollment.

## Scope and invariants

- Scope is the guild subsystem, not territory ownership or territory persistence.
- Player cash accounts use `AccountId.player(UUID)`.
- Guild cash accounts use only `AccountId.of(NamespaceId.parse("guild:" + Guild.getId()))`; `Guild.getId()` is the canonical and sole Mint guild-account identity.
- SQL `Guild.balance` remains separate from Mint cash balances.
- Existing Vault and simulation rails remain available.
- All operations remain asynchronous; no `.join()`, `.get()`, or blocking waits on the Paper main thread.
- Enrollment is persisted and idempotent for each `(guild_id, player_uuid)` pair.
- Player deposits and withdrawals require enrollment and existing guild permissions.
- Territory-tax credits do not require payer enrollment; outsiders may generate valid guild taxes.
- Every operation resolves one canonical guild identity: `Guild.getId()`. Resident-facing guild names must be resolved to that guild before any bank or Mint rail call; names must never be used as Mint account keys.
- Guild cash accounts use only `AccountId.of(NamespaceId.parse("guild:" + Guild.getId()))`.
- Guild capacity applies to total Mint guild balance.
- Capacity overflow is rejected before submitting a Mint credit; no over-cap posting is committed.
- Every guild-bank operation—enrollment, account provisioning, balance, deposits, withdrawals, and tax credits—enters the same per-guild serialized coordinator.

## Architecture

### Enrollment and bank service

Introduce a guild-bank service boundary responsible for enrollment, access checks, balance queries, player transfers, and guild credits. The service persists enrollment in SQL with a uniqueness constraint and exposes asynchronous results rather than blocking APIs.

`/guild bank open` checks current guild membership, records the player-guild enrollment idempotently, and provisions the Mint player and guild accounts. Account provisioning is not the enrollment itself. The balance command and player transfers reject unenrolled players.

### Capacity and concurrency coordinator

The bank service owns per-guild serialization for all account lifecycle and balance-changing operations. Enrollment and account provisioning, deposits, withdrawals, and tax credits enter the same guild queue. A positive credit reads the authoritative Mint guild balance, derives the guild's configured capacity, and only then submits the atomic Mint transaction. If balance plus credit exceeds capacity, the service returns a capacity-rejected result and submits no Mint posting. A withdrawal and a concurrent credit cannot observe or create an invalid ordering.

### Tax integration

The configured Mint async settlement used by `EconomyBridge` delegates guild credits to the bank service. The bridge must be able to use its configured settlement without callers manually passing a rail. Tax settlement remains asynchronous and maps capacity rejection to the existing rejected/failed tax outcome. It never checks the tax payer's enrollment.

### Enrollment lifecycle

Opening is idempotent only while the player is a current member of the canonical guild. Leaving or being removed from a guild immediately makes the enrollment inactive or otherwise unusable; every bank authorization rechecks current membership and canonical `Guild.getId()`. Rejoining requires an explicit `/guild bank open` call again. Guild deletion deactivates all enrollments for that guild and prevents further player transfers; the Mint guild account is not silently merged or deleted by this feature. Mint lease revocation makes all pending and new operations unavailable without blocking the main thread.

### Runtime wiring

Retain the exact registered `MintClientReceiver` instance and unregister that instance on plugin disable. When Mint invokes the receiver, create the runtime Mint rail/service and update the already-created guild command/service references through a mutable provider or setter. Rebuilding the guild subsystem in the callback is not required.

## Command contract

- `/guild bank open`: player-only; requires guild membership; idempotently enrolls the player and asynchronously provisions accounts.
- `/guild bank`: player-only; requires enrollment; asynchronously returns the guild Mint balance.
- `/guild bank deposit <amount>`: requires enrollment and deposit permission; routes the player's Mint account to the guild account through the capacity coordinator.
- `/guild bank withdraw <amount>`: requires enrollment and withdraw permission; routes the guild account to the player's Mint account.
- All completion messages are scheduled back onto the Bukkit main thread.

## Capacity configuration

Capacity is an explicit guild-level property, expressed in the configured Mint currency at the configured decimal scale. The default capacity is `1000.00` currency units at guild level 1. Each level increases capacity by `1000.00` units, so `capacity(level) = max(0, level) * 1000.00`; level 1 is `1000.00`, level 2 is `2000.00`, and so on. The value is converted to Mint's configured scale with `RoundingMode.HALF_UP`.

Guild-level capacity is authoritative. Tech-tree storage upgrades do not change cash capacity in this feature; they remain independent resource/storage progression. A level downgrade immediately applies the lower capacity to new credits. Existing balances are not forcibly withdrawn; if a downgraded balance is already above the new capacity, all further positive credits are rejected until withdrawals bring it below the limit. The capacity decision is made inside the serialized per-guild operation immediately before posting.

Every queued operation must complete its future on success, rejection, timeout, or exception before the next operation is released. Mint stages use a bounded timeout configured by `economy.mint.operation-timeout-ms` (default `5000`); timeout maps to unavailable and does not strand the guild queue.

## Failure behavior

- Missing Mint binding: unavailable result; no fallback from MINT mode to Vault or simulation.
- Missing enrollment: authorization failure for player bank access.
- Missing guild membership: account opening and bank commands fail without side effects.
- Insufficient Mint funds: no committed transfer.
- Capacity exceeded: no credit transaction submitted; tax result is rejected/failed.
- Duplicate idempotency key: preserve Mint's idempotent behavior.
- Lease revocation or Mint errors: unavailable/rejected result according to existing adapter mapping.

## Verification

Add tests for:

1. Idempotent enrollment persistence.
2. Account opening requiring guild membership.
3. Deposit/withdraw authorization requiring enrollment.
4. Tax credit succeeding for an unenrolled payer.
5. Total-capacity boundary and overflow rejection.
6. Concurrent positive credits serialized so the guild balance never exceeds capacity.
7. No Mint posting submitted on capacity rejection.
8. Runtime receiver wiring updating both tax settlement and guild-bank commands.
9. Existing Vault/simulation behavior remaining unchanged.
