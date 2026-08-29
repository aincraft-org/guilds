# Task 2 implementation report

## Scope

Implemented only Task 2 from the approved fast-travel-network plan. Existing
facility and territory persistence remains the single JSON/SQL persistence path;
no later task production logic, Gradle configuration, or user-facing docs were
changed.

## Files changed

- `guilds-common/src/main/java/org/aincraft/guilds/territory/persist/TerritoryJson.java`
  - Persists the optional `fastTravelPolicy` object.
  - Sorts facility quota keys and cross-territory mode names deterministically.
  - Applies `FastTravelPolicy.defaults()` only when the legacy field is absent.
  - Rejects malformed present policy objects, non-integer quotas, unknown enum
    names, duplicate modes, and model-invalid values.
- `guilds-common/src/main/resources/sql/migrations/guilds/manifest`
  - Added migration version 31, `fast-travel-currency`.
- `guilds-common/src/main/resources/sql/migrations/guilds/V31__fast-travel-currency.sql`
  - Added portable wallet, award, and reservation tables plus the
    `(status, expires_at)` recovery index.
  - No resident foreign key or guild ownership/active columns were added.
- `guilds-common/src/test/java/org/aincraft/guilds/territory/persist/TerritoryJsonFastTravelTest.java`
  - Added policy round-trip/order, legacy default, and malformed-present-policy
    coverage.
- `guilds-common/src/test/java/org/aincraft/guilds/territory/persist/PostgresFacilityStoreTest.java`
  - Extended persistence coverage to all four new facility enum values and a
    legacy facility document.
- `guilds-common/src/test/java/org/aincraft/guilds/territory/persist/SqlMigrationCatalogTest.java`
  - Updated latest migration expectation from 27 to 31 and asserted the new
    slug/resource.
- `guilds-paper/src/main/resources/sql/travel/`
  - Added the ten requested wallet, award, reservation, expiry, and recovery
    SQL resources, plus `credit-wallet-balance.sql` for capped reward/release
    credits.
  - Resource parameter order is documented by each positional statement:
    wallet select `(playerUuid)`; wallet insert `(playerUuid, balance,
    createdAt, updatedAt)`; wallet debit `(amount, updatedAt, playerUuid,
    amount)`; wallet credit `(amount, maximumBalance, maximumBalance, amount,
    updatedAt, playerUuid)`; award insert `(source, eventId, playerUuid, amount, awardedAt)`;
    reservation insert `(reservationId, tripId, playerUuid, amount, expiresAt,
    createdAt)`; reservation select `(reservationId)`; commit/release/recover
    `(timestamp, reservationId)`; expired select `(nowMillis)`.

## TDD and verification

1. Added the focused policy/facility/catalog tests before implementation.
2. Ran the exact focused command before implementation. It could not reach
   test compilation because the existing `:guilds-test:mintPlugin` dependency
   resolution failed with HTTP 401 from the private GitHub Packages Mint
   repository:

   ```text
   Could not resolve dev.mintychochip.mint:mint-paper:26.8.12.10
   Received status code 401 from server: Unauthorized
   ```

3. Ran the same exact focused command after implementation. It stopped at the
   same environment prerequisite with the same HTTP 401 before compiling or
   executing tests.
4. Ran `git diff --check` successfully.
5. Independently executed the V31 SQL through Python's in-memory SQLite engine
   (with the repository's migration index directive removed for SQLite). Output:

   ```text
   tables: ['player_travel_wallets', 'travel_currency_awards', 'travel_currency_reservations']
   commit-reservation.sql params= 2
   insert-award.sql params= 5
   insert-reservation.sql params= 6
   insert-wallet.sql params= 4
   recover-reservation.sql params= 2
   release-reservation.sql params= 2
   select-expired-reservations.sql params= 1
   select-reservation.sql params= 1
   select-wallet.sql params= 1
   update-wallet-balance.sql params= 4
   ```
6. Added `credit-wallet-balance.sql` for capped positive credits used by
   awards, release, and expiry recovery. Independently executed the capped
   credit and conditional debit statements against SQLite:

   ```text
   credit cap/debit behavior: PASS
   credit-wallet-balance.sql params= 6
   ```

The focused Gradle tests therefore remain unexecuted due to the private Mint
artifact prerequisite. The environment has OpenJDK 25 while the project
requires Java 26; no toolchain or Gradle configuration was changed.

## Commits

- `2151a31` — `feat: persist fast travel policies and currency schema`
- `594fcd4` — `feat: add capped travel wallet credit statement`

## Concerns

- `MINT_PACKAGES_CREDENTIALS`: focused Gradle verification is blocked by the
  private Mint package returning HTTP 401.
- `JDK26_TOOLCHAIN_UNAVAILABLE`: the configured Java 26 toolchain is not
  installed in this environment, so no independent Java test execution was
  possible.
