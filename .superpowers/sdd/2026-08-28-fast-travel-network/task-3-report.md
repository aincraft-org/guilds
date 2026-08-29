# Task 3 implementation report

## Scope

Implemented only Task 3 from the approved fast-travel-network plan. This adds the
validated global personal travel-currency policy, canonical cost calculation,
and asynchronous SQL-backed wallet/reward/reservation service. No Task 4-10
production wiring, Gradle/toolchain configuration, guild resources, guild money,
or unrelated worktree files were changed.

## Files changed

- `guilds-paper/src/main/java/org/aincraft/guilds/config/TravelCurrencyConfig.java`
  - Added validated immutable starter/cap/base-cost/divisor/multiplier,
    reservation-duration, and reward configuration.
  - Added the required defaults and mode/reward lookups.
- `guilds-paper/src/main/java/org/aincraft/guilds/config/TravelCurrencyConfigLoader.java`
  - Added Bukkit configuration loading with strict integer/finite-decimal
    parsing and required validation.
- `guilds-paper/src/main/java/org/aincraft/guilds/territory/building/FastTravelCostCalculator.java`
  - Added the pure canonical `ceil(base + multiplier * distance / divisor)`
    calculator with finite/non-negative input checks and checked long conversion.
- `guilds-paper/src/main/java/org/aincraft/guilds/services/travel/TravelCurrencyRewardSource.java`
  - Added exactly `QUEST_COMPLETION`, `EXPLORATION_MILESTONE`, and
    `GUILD_ACTIVITY` sources.
- `guilds-paper/src/main/java/org/aincraft/guilds/services/travel/WalletSnapshot.java`
  - Added the immutable player wallet snapshot.
- `guilds-paper/src/main/java/org/aincraft/guilds/services/travel/TravelCurrencyService.java`
  - Added the asynchronous durable API, statuses, and result records.
- `guilds-paper/src/main/java/org/aincraft/guilds/services/impl/TravelCurrencyServiceImpl.java`
  - Added off-thread wallet access, atomic starter-wallet creation, conditional
    debit reservations, durable trip uniqueness, capped idempotent awards,
    idempotent commit/release, expiry recovery, and in-memory attempt cleanup.
  - All JDBC operations use `DatabaseManager` transaction helpers and named
    classpath SQL resources; wallet state is not advanced before transaction
    success.
- `guilds-paper/src/main/resources/sql/travel/select-award.sql`
  - Added durable award-identity lookup used for duplicate classification.
- `guilds-paper/src/main/resources/sql/travel/select-reservation-by-trip.sql`
  - Added durable trip-identity lookup used for duplicate classification.
- `guilds-paper/src/main/resources/guilds-config.yml`
  - Added the explicit personal travel-currency defaults.
- `guilds-paper/src/test/java/org/aincraft/guilds/config/TravelCurrencyConfigLoaderTest.java`
  - Added default, custom-value, and invalid-value coverage.
- `guilds-paper/src/test/java/org/aincraft/guilds/territory/building/FastTravelCostCalculatorTest.java`
  - Added mode multiplier, canonical ceil, invalid-distance, and overflow coverage.
- `guilds-paper/src/test/java/org/aincraft/guilds/services/TravelCurrencyServiceImplTest.java`
  - Added starter/cap, concurrent overspend, rejected reservation, duplicate
    award/trip, commit/release idempotency, and expiry-recovery coverage.

## TDD and verification

1. Added the config and cost focused tests before implementation.
2. Ran the exact focused command before implementation. It was blocked before
   test compilation by the existing private Mint dependency:

   ```text
   ./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.config.TravelCurrencyConfigLoaderTest' --tests 'org.aincraft.guilds.territory.building.FastTravelCostCalculatorTest' --tests 'org.aincraft.guilds.services.TravelCurrencyServiceImplTest'
   BUILD FAILED
   Could not resolve dev.mintychochip.mint:mint-paper:26.8.12.10.
   Received status code 401 from server: Unauthorized
   ```

3. Implemented the production classes and service tests, then ran the same
   focused command. It reached the same environment blocker before compiling
   or executing tests:

   ```text
   ./gradlew :guilds-paper:test --tests 'org.aincraft.guilds.config.TravelCurrencyConfigLoaderTest' --tests 'org.aincraft.guilds.territory.building.FastTravelCostCalculatorTest' --tests 'org.aincraft.guilds.services.TravelCurrencyServiceImplTest'
   BUILD FAILED
   Could not resolve dev.mintychochip.mint:mint-paper:26.8.12.10.
   Received status code 401 from server: Unauthorized
   ```

4. Attempted independent Java compilation, but this environment has Java 25
   runtime only and no `javac`; the project requires Java 26.
5. Ran `git diff --cached --check` successfully before committing.
6. Independently exercised the V31 schema and travel SQL with Python's in-memory
   SQLite engine. The conditional debit, reservation insert, capped credit,
   recovery update, and resulting wallet/status transitions passed:

   ```text
   wallet_balance: 10
   reservation_status: RELEASED
   ```

   Parameter-count check:

   ```text
   commit-reservation.sql: params=2
   credit-wallet-balance.sql: params=9
   insert-award.sql: params=5
   insert-reservation.sql: params=6
   insert-wallet.sql: params=4
   recover-reservation.sql: params=2
   release-reservation.sql: params=2
   select-award.sql: params=2
   select-expired-reservations.sql: params=1
   select-reservation-by-trip.sql: params=1
   select-reservation.sql: params=1
   select-wallet.sql: params=1
   update-wallet-balance.sql: params=4
   ```

## Commit

- `94ea38c` — `feat: add durable travel currency service`

## Concerns

- `MINT_PACKAGES_CREDENTIALS`: the focused Gradle command cannot resolve the
  private `mint-paper` artifact and receives HTTP 401.
- `JDK26_TOOLCHAIN_UNAVAILABLE`: the configured Java 26 toolchain is not
  installed, and no `javac` is available for independent Java compilation.
- `FOCUSED_JAVA_TESTS_UNEXECUTED`: the focused JUnit classes could not reach
  compilation/execution because of the dependency blocker above.
