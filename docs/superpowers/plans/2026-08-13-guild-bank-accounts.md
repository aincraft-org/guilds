# Guild Bank Accounts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add persistent player enrollment and capacity-aware Mint guild-bank operations for guild members, while routing all territory-tax credits through the same asynchronous guild coordinator.

**Architecture:** Add a SQL-backed guild-bank enrollment service with canonical `Guild.getId()` identity. A per-guild asynchronous coordinator serializes enrollment, provisioning, balance-changing operations, withdrawals, and tax credits; positive credits perform an authoritative Mint balance/capacity check before posting. Existing command and tax callers resolve the service dynamically after Mint receiver binding.

**Tech Stack:** Java, Gradle, Paper/Bukkit, PostgreSQL/JDBC, Brigadier, Mint API, JUnit 5, Mockito.

## Global Constraints

- Player cash accounts use `AccountId.player(UUID)`.
- Guild cash accounts use only `AccountId.of(NamespaceId.parse("guild:" + Guild.getId()))`.
- SQL `Guild.balance` remains separate from Mint cash balances.
- Existing Vault and simulation rails remain available.
- All operations remain asynchronous; no `.join()`, `.get()`, or blocking waits on the Paper main thread.
- Enrollment is persisted and idempotent for each `(guild_id, player_uuid)` pair.
- Territory-tax credits do not require payer enrollment.
- Capacity is `max(0, guildLevel) * 1000.00` Mint currency units, converted with `RoundingMode.HALF_UP`.
- Level downgrades affect new credits; existing balances are not forcibly withdrawn.
- All queued operations complete on success, rejection, timeout, or exception; Mint operation timeout defaults to 5000 ms.
- Command transfers use a fresh request UUID; tax retries preserve their stable event identity.

---

### Task 1: Add persistent guild-bank enrollment schema and repository

**Files:**
- Create: `paper/src/main/java/org/aincraft/guilds/database/migration/AddGuildBankEnrollmentMigration.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/database/SchemaInitializer.java`
- Create: `paper/src/main/java/org/aincraft/guilds/services/GuildBankEnrollmentService.java`
- Create: `paper/src/main/java/org/aincraft/guilds/services/impl/GuildBankEnrollmentServiceImpl.java`
- Test: `paper/src/test/java/org/aincraft/guilds/database/migration/AddGuildBankEnrollmentMigrationTest.java`
- Test: `paper/src/test/java/org/aincraft/guilds/services/GuildBankEnrollmentServiceTest.java`
- Test: `paper/src/test/java/org/aincraft/guilds/listeners/GuildBankVillagerListenerTest.java`

**Interfaces:**
- Consumes: `DatabaseMigration`, `DatabaseManager`, `ResidentService`, canonical `Guild.getId()`.
- Produces: migration version `21` and `CompletionStage<EnrollmentResult> open(UUID playerUuid, String guildId)`, `CompletionStage<Boolean> isEnrolled(UUID playerUuid, String guildId)`, plus lifecycle methods `deactivateForPlayerGuild(...)` / `deactivateForGuild(...)`.

- [ ] **Step 1: Write migration tests** proving a v20 database receives the enrollment table, unique `(guild_id, player_uuid)` constraint, active state, timestamps, and schema-migrations row version 21; rerunning is a no-op.
- [ ] **Step 2: Run** `./gradlew --no-daemon :paper:test --tests org.aincraft.guilds.database.migration.AddGuildBankEnrollmentMigrationTest`; expect failure because migration v21 is absent.
- [ ] **Step 3: Implement** `AddGuildBankEnrollmentMigration` with `getVersion() == 21`, idempotent `apply`, `isApplied`, and `markAsApplied` following existing migration conventions. Register it after `AlterResidentLastOnlineMigration` in `SchemaInitializer.registerMigrations()`.
- [ ] **Step 4: Write the enrollment-service tests** for unique enrollment, idempotent reopen, inactive enrollment after leave/removal, and guild-wide deactivation.
- [ ] **Step 5: Run** the focused migration and service tests; expect failures until the service is implemented.
- [ ] **Step 6: Implement** idempotent open only for current membership; deactivation must make subsequent authorization false.
- [ ] **Step 7: Run** the focused tests and require PASS.
- [ ] **Step 8: Commit** with `git add` and `git commit -m "feat: persist guild bank enrollment"`.

### Task 2: Implement canonical capacity and per-guild async coordinator

- Create: `paper/src/main/java/org/aincraft/guilds/services/MintTransferPort.java`
- Create: `paper/src/main/java/org/aincraft/guilds/services/MintGuildBankService.java`
- Create: `paper/src/main/java/com/guilds/territory/economy/GuildBankCapacity.java`
- Modify: `paper/src/main/resources/guilds-config.yml`
- Test: `paper/src/test/java/org/aincraft/guilds/services/MintGuildBankServiceTest.java`

**Interfaces:**
- Consumes: `MintTransferPort`, enrollment coordinator, and guild-level lookup. `MintTransferPort` is a lower-level adapter implemented by `MintEconomyRail` over `MintClientLease`; the bank service depends only on this port and never on the concrete rail.
- Produces: async `openAccount`, `balance`, `deposit`, `withdraw`, and `creditTax` methods; `GuildBankResult` statuses including `COMMITTED`, `CAPACITY_EXCEEDED`, `UNAUTHORIZED`, `UNAVAILABLE`, `INSUFFICIENT_FUNDS`, and `REJECTED`.

- [ ] **Step 1: Write failing tests** for level capacity (`level * 1000.00`), exact-boundary acceptance, overflow rejection without ledger posting, downgrade behavior, concurrent deposit/tax serialization, withdrawal/credit ordering, queue release after Mint timeout/exception, and enrollment/account lifecycle operations sharing the same queue.
- [ ] **Step 2: Run** the focused test; expect failures.
- [ ] **Step 3: Add** `GuildBankCapacity.capacityForLevel(int)` using configured scale and `HALF_UP`; add base/level settings and `economy.mint.operation-timeout-ms: 5000`.
- [ ] **Step 4: Implement** a `ConcurrentHashMap<String, CompletableFuture<Void>>` or equivalent per-guild tail queue. The queue tail must remain blocked until the underlying Mint `CompletionStage` actually completes; a timeout may complete the caller's result as unavailable, but it must not release the next guild operation while the Mint stage can still commit. Attach late completion handlers to settle and release the tail exactly once. If Mint supports cancellation, cancel on timeout; otherwise retain the in-flight barrier and reject/hold later operations until it completes.
- [ ] **Step 5: Implement** positive credit as serialized authoritative balance read, capacity comparison, then Mint atomic posting. Never submit the posting when over capacity. Implement withdrawals, enrollment, and account provisioning in the same queue.
- [ ] **Step 6: Use canonical `Guild.getId()` at the service boundary; reject names or resolve resident names before entering the service.**
- [ ] **Step 7: Run** focused tests and require PASS.
- [ ] **Step 8: Commit** with `git commit -m "feat: add capacity-aware guild bank service"`.

### Task 3: Route taxes through the configured capacity-aware service

**Files:**
- Modify: `common/src/main/java/com/guilds/territory/economy/EconomyBridge.java`
- Modify: `paper/src/main/java/com/guilds/territory/economy/MintEconomyRail.java`
- Modify: `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java`
- Test: `common/src/test/java/com/guilds/territory/economy/EconomyBridgeMintTaxTest.java`
- Test: `paper/src/test/java/com/guilds/territory/economy/MintGuildTaxCapacityTest.java`

**Interfaces:**
- Consumes: configured `AsyncTaxSettlement`, governing `Guild.getId()`, stable event key.
- Produces: no-settlement-argument async facade overloads and capacity-aware `creditTax` delegation.

- [ ] **Step 1: Add failing tests** calling production async sale/craft facades without manually passing a settlement and proving an unenrolled payer still credits the governing guild.
- [ ] **Step 2: Run focused tests; expect failure because the bridge currently requires an explicit settlement.
- [ ] **Step 3: Add overloads that read the configured settlement and preserve explicit injection for tests.
- [ ] **Step 4: Implement `MintGuildBankService.creditTax(...)` as the sole serialized capacity-aware guild credit path using `MintTransferPort`; make the configured `AsyncTaxSettlement` delegate to that service, while `MintEconomyRail` only implements `MintTransferPort` over `MintClientLease` and never calls the service.
- [ ] **Step 5: Run focused common and paper tests; require PASS.
- [ ] **Step 6: Commit** with `git commit -m "feat: route taxes through guild bank capacity"`.
### Task 4: Wire dynamic Mint rail, commands, and guild-bank villager

**Files:**
- Modify: `paper/src/main/java/com/guilds/territory/GuildsTerritoryPlugin.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/GuildsServices.java`
- Modify: `paper/src/main/java/org/aincraft/guilds/commands/brigadier/GuildBrigadierCommand.java`
- Create: `paper/src/main/java/org/aincraft/guilds/listeners/GuildBankVillagerListener.java`
- Modify: `paper/src/main/resources/guilds-config.yml`
- Test: `paper/src/test/java/com/guilds/territory/PluginMintWiringTest.java`
- Test: `paper/src/test/java/org/aincraft/guilds/commands/GuildBankCommandTest.java`
- Test: `paper/src/test/java/org/aincraft/guilds/listeners/GuildBankVillagerListenerTest.java`

**Interfaces:**
- Consumes: runtime Mint rail/service callback and the same `MintGuildBankService` used by commands.
- Produces: mutable/provider-backed bank service used by existing command and villager listeners; exact receiver object retained and unregistered.

- [ ] **Step 1: Add failing tests** for receiver callback updating command/villager bank availability, exact provider unregistration, canonical guild ID resolution, fresh command idempotency keys, and villager interaction behavior.
- [ ] **Step 2: Run** focused tests; expect failures.
- [ ] **Step 3: Replace final nullable rail fields with a volatile provider/service reference or setter. Construct `GuildBrigadierCommand` with the dynamic bank service, not the 8-argument null-rail overload.
- [ ] **Step 4: Add** `/guild bank open`; require current guild membership, resolve `Guild.getId()`, and asynchronously call enrollment/provisioning. Require enrollment for balance/deposit/withdraw.
- [ ] **Step 5: Generate** `UUID.randomUUID()` per command transfer request; do not reuse amount-based keys. Preserve stable tax keys.
- [ ] **Step 6: Implement** `GuildBankVillagerListener` on `PlayerInteractEntityEvent`. Only handle right-clicks on configured or explicitly tagged `Villager` entities; cancel the interaction, resolve the player's current canonical guild, and invoke the same idempotent `openAccount` service as `/guild bank open`. Never create a separate account or bypass membership checks. Use a configurable name/tag from `guilds-config.yml`, defaulting to a `GUILD_BANK` scoreboard tag.
- [ ] **Step 7: Register the villager listener with GuildsServices and ensure all completion messages are scheduled on the Bukkit main thread.
- [ ] **Step 8: Store** the exact `MintClientReceiver` instance in a field and unregister that exact provider on disable. Bind the same service into EconomyBridge and GuildsServices.
- [ ] **Step 9: Run** focused tests and require PASS.
- [ ] **Step 10: Commit** with `git commit -m "feat: expose enrolled guild bank commands and villager"`.

### Task 5: Verify configuration, integration, and regressions

**Files:**
- Modify: `paper/src/main/resources/config.yml`
- Modify: `paper/src/main/resources/guilds-config.yml`
- Modify: `docs/living-specs/economy.md`
- Add/update: Mint Paper integration profile documentation/configuration for `GuildsTerritory`.
- Tests: existing API/common/paper suites and a real registered receiver fake-lease integration test.

**Interfaces:**
- Consumes: all previous tasks.
- Produces: tested end-to-end guild account lifecycle and tax settlement.

- [ ] **Step 1: Add** Mint integration profile configuration naming `GuildsTerritory` with the accepted client/profile/currency scope required by Mint’s `IntegrationProfileConfig`; do not rely only on Guilds’s client-binding setting.
- [ ] **Step 2: Add** an end-to-end fake receiver/service test that registers the receiver, binds a fake lease, opens a player account, deposits to `guild:<Guild.getId()>`, and credits tax from an unenrolled payer while asserting the fake ledger posting.
- [ ] **Step 3: Run** `./gradlew --no-daemon :api:test :common:test :paper:test :paper:build`; require `BUILD SUCCESSFUL`.
- [ ] **Step 4: Run** `git diff --check` and inspect every changed file for canonical IDs, timeout handling, no blocking waits, and separation from SQL `Guild.balance`.
- [ ] **Step 5: Commit** with `git commit -m "feat: add enrolled capacity-aware Mint guild banks"` and push only after all end-to-end assertions pass.
