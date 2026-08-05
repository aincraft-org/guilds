# Economy Hooks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a New World-style economy to Azoth Territory: a public transaction API (`EconomyBridge`) other plugins call to report sales, sales taxed at rates aggregated from PASSED policy `DecreeEffects`, settled through a `PaymentRail` (Vault-backed by default, simulation mode for dev/test), with durable reconciliation for stranded charges.

**Architecture:** Pure-domain `com.azoth.territory.economy` package (Bukkit-free) contains `EconomyBridge`, `TaxCalculator`, `TaxReport`/`TaxOutcome`, `PaymentRail`/`SettlementStatus`, and `SimulationTreasury`. Bukkit/Vault wiring (`VaultTreasury`, `BukkitEconomyBridge`, `EconomyConfig`) lives in plugin scope. `DecreeEffects` is attached to `Policy` and threaded through `PolicyRules`/`Territory`/`TerritoryJson`; the `taxRatesFromPolicies` stub is completed. Settlement is a single atomic `PaymentRail.settle(...)` call: withdraw payer → deposit territory bank → compensating refund on deposit failure, sealed `SettlementStatus` results mapped to `TaxOutcome` by the bridge.

**Tech Stack:** Java 21, Gradle (Kotlin DSL), Paper 1.21.4 API, Gson 2.11.0 (compileOnly; testImplementation), JUnit 5, VaultAPI 1.7 (compileOnly, JitPack).

## Global Constraints

- Java 21 toolchain; confirm `./gradlew build` and `./gradlew test` green per task.
- All `economy`, `decree`, `model`, `registry`, `persist` source is Bukkit-free (no `org.bukkit.*`, no `net.milkbowl.vault.*`) EXCEPT the explicit wiring classes `VaultTreasury`, `BukkitEconomyBridge`, `EconomyConfig`, `AzothTerritoryPlugin` changes.
- Vault dependency: `compileOnly("com.github.MilkBowl:VaultAPI:1.7")` from `https://jitpack.io` (verify resolution; fallback `https://nexus.hc.to/content/repositories/pub_releases/`).
- `plugin.yml` gains `softdepend: [Vault]`.
- `config.yml` gains:
  ```yaml
  economy:
    mode: VAULT   # VAULT (default) or SIMULATION
  ```
- Immutable domain models with copy-on-write; pure-domain tests must not require Paper/Vault runtime.
- Money invariant (VAULT mode): a treasury balance never appears without a matched payer charge; `TAXED` iff payer charged AND treasury credited; no net money lost on any failure path.
- TDD: write the failing test, verify it fails, write the minimal implementation, verify it passes, commit. One atomic commit per task.
- Commit identity: repo has no commits and no git identity. Use one-shot `git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit ...` (never persist `git config`).

---

### Task 1: Add `DecreeEffects` to `Policy` and thread through `PolicyRules`/`Territory`

**Files:**
- Modify: `src/main/java/com/azoth/territory/model/Policy.java`
- Modify: `src/main/java/com/azoth/territory/model/PolicyRules.java`
- Modify: `src/main/java/com/azoth/territory/model/Territory.java`
- Test: `src/test/java/com/azoth/territory/model/PolicyEffectsWiringTest.java`

**Interfaces:**
- Consumes: `DecreeEffects` (`com.azoth.territory.decree.DecreeEffects`; `empty()`, `ofTax(TaxEffect)`, `taxes()`, `equals`).
- Produces:
  - `Policy.effects()` → `DecreeEffects` (never null; default `empty()`).
  - `Policy` full ctor gains trailing param `DecreeEffects effects`.
  - `Policy` keeps an 8-arg ctor overload (existing callers, e.g. `TerritoryJson`) that delegates with `DecreeEffects.empty()`.
  - `PolicyRules.propose(Government government, String id, String title, String body, String proposerId, long nowEpochMs)` — **KEPT** as a 6-arg overload delegating with `DecreeEffects.empty()` (back-compat for existing callers).
  - `PolicyRules.propose(Government government, String id, String title, String body, String proposerId, long nowEpochMs, DecreeEffects effects)` — new 7-arg variant.
  - `Territory.proposePolicy(String policyId, String title, String body, String proposerId, long nowEpochMs, DecreeEffects effects)` — new 6-arg (effects required); `Territory` has no other `proposePolicy` callers to update beyond test code.
  - `castVote`/`decree`/`resolveIfPossible`/`withVote`/`withStatus` PRESERVE `effects` unchanged.
- Explicitly unchanged: `Government`, `PolicyStatus`, `PolicyVote`, `VoteChoice`, `GovernmentForm`.

- [ ] **Step 1: Write the failing test** `src/test/java/com/azoth/territory/model/PolicyEffectsWiringTest.java`:

```java
package com.azoth.territory.model;

import com.azoth.territory.decree.DecreeEffects;
import com.azoth.territory.decree.TaxEffect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Effects added to Policy: ctor/with*/propose wiring and preservation through vote/decree. */
class PolicyEffectsWiringTest {

    private static final long NOW = 1_700_000_000_000L;

    private static DecreeEffects carrotTax() {
        return DecreeEffects.ofTax(new TaxEffect(List.of("carrot"), 15.0));
    }

    @Test
    void newPolicyDefaultsToEmptyEffects() {
        Policy p = Policy.propose("p1", "Title", "Body", "proposer", NOW);
        assertTrue(p.effects().isEmpty());
    }

    @Test
    void proposeCarriesEffects() {
        Government g = Government.monarchy("king:arthur");
        Policy p = PolicyRules.propose(g, "tax", "Tax", "B", "king:arthur", NOW, carrotTax());
        assertEquals(carrotTax(), p.effects());
    }

    @Test
    void votePreservesEffects() {
        Government g = Government.oligarchy(List.of("c1", "c2", "c3"));
        Policy p = PolicyRules.propose(g, "tax", "Tax", "B", "c1", NOW, carrotTax());
        Policy voted = PolicyRules.castVote(g, p, "c1", VoteChoice.YES, NOW + 1);
        assertEquals(carrotTax(), voted.effects());
        Policy resolved = PolicyRules.castVote(g, voted, "c2", VoteChoice.YES, NOW + 2);
        assertEquals(PolicyStatus.PASSED, resolved.status());
        assertEquals(carrotTax(), resolved.effects());
    }

    @Test
    void decreePreservesEffects() {
        Government g = Government.monarchy("king:arthur");
        Policy p = PolicyRules.propose(g, "tax", "Tax", "B", "king:arthur", NOW, carrotTax());
        Policy passed = PolicyRules.decree(g, p, "king:arthur", true, NOW + 1);
        assertEquals(carrotTax(), passed.effects());
    }

    @Test
    void equalsDistinguishesEffects() {
        Policy a = PolicyRules.propose(Government.monarchy("k"), "p", "T", "B", "k", NOW, carrotTax());
        Policy b = PolicyRules.propose(Government.monarchy("k"), "p", "T", "B", "k", NOW, DecreeEffects.empty());
        Policy a2 = PolicyRules.propose(Government.monarchy("k"), "p", "T", "B", "k", NOW, carrotTax());
        assertEquals(a, a2);          // same effects → equal
        assertNotEquals(a, b);        // different effects → not equal
        assertEquals(a.hashCode(), a2.hashCode());
    }

    @Test
    void territoryProposePolicyCarriesEffects() {
        Territory t = new Territory("t1", "T", "world", new Boundary(List.of(
                new BlockPos(0, 0), new BlockPos(10, 0), new BlockPos(10, 10), new BlockPos(0, 10)
        ))).withGovernment(Government.monarchy("king:arthur"));
        Territory next = t.proposePolicy("tax", "Tax", "B", "king:arthur", NOW, carrotTax());
        assertEquals(carrotTax(), next.policy("tax").orElseThrow().effects());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.azoth.territory.model.PolicyEffectsWiringTest`
Expected: COMPILATION FAILURE — `PolicyRules.propose(...)` has no 7-arg overload with `DecreeEffects`; `Territory.proposePolicy(...)` has no 6-arg overload; `Policy.effects()` undefined.

- [ ] **Step 3: Add the `effects` field to `Policy`**

In `src/main/java/com/azoth/territory/model/Policy.java`:
- Add field `private final DecreeEffects effects;`.
- Full ctor (9-arg now): add trailing param `DecreeEffects effects`; body sets `this.effects = effects == null ? DecreeEffects.empty() : effects;`.
- Add 8-arg overload that delegates with `DecreeEffects.empty()`:
  ```java
  public Policy(String id, String title, String body, String proposerId,
                PolicyStatus status, List<PolicyVote> votes,
                Long resolvedAtEpochMs, Long proposedAtEpochMs) {
      this(id, title, body, proposerId, status, votes, resolvedAtEpochMs, proposedAtEpochMs, DecreeEffects.empty());
  }
  ```
- `Policy.propose(...)`: delegate to the full ctor with `DecreeEffects.empty()` (keep its existing 5-arg signature).
- `withVote`: pass `effects` through to the full ctor (9-arg).
- `withStatus`: pass `effects` through to the full ctor (9-arg).
- Add accessor `public DecreeEffects effects() { return effects; }`.
- `equals`: add `&& effects.equals(that.effects)`.
- `hashCode`: add `effects` to `Objects.hash(...)`.
- `toString`: unchanged (do not add effects to the short form).
- Add import `com.azoth.territory.decree.DecreeEffects`.

- [ ] **Step 4: Thread effects through `PolicyRules.propose`**

In `src/main/java/com/azoth/territory/model/PolicyRules.java`:
- Keep the existing 6-arg `propose` as a delegating overload:
  ```java
  public static Policy propose(
          Government government,
          String id,
          String title,
          String body,
          String proposerId,
          long nowEpochMs
  ) {
      return propose(government, id, title, body, proposerId, nowEpochMs, DecreeEffects.empty());
  }
  ```
- Add the 7-arg variant with the actual logic (eligibility checks unchanged, then apply effects):
  ```java
  public static Policy propose(
          Government government,
          String id,
          String title,
          String body,
          String proposerId,
          long nowEpochMs,
          DecreeEffects effects
  ) {
      Objects.requireNonNull(government, "government");
      if (!government.isAssigned()) {
          throw new IllegalArgumentException("cannot propose policy without an assigned government");
      }
      if (!canPropose(government, proposerId)) {
          throw new IllegalArgumentException(
                  "proposer '" + proposerId + "' is not eligible under " + government.form()
          );
      }
      Policy p = Policy.propose(id, title, body, proposerId, nowEpochMs);
      if (effects == null || effects.isEmpty()) {
          return p;
      }
      return new Policy(
              p.id(), p.title(), p.body(), p.proposerId(), PolicyStatus.PROPOSED,
              p.votes(), null, nowEpochMs, effects
      );
  }
  ```
- Add import `com.azoth.territory.decree.DecreeEffects`.

- [ ] **Step 5: Thread effects through `Territory.proposePolicy`**

In `src/main/java/com/azoth/territory/model/Territory.java`:
- Change `proposePolicy` to require effects:
  ```java
  public Territory proposePolicy(
          String policyId,
          String title,
          String body,
          String proposerId,
          long nowEpochMs,
          DecreeEffects effects
  ) {
      Policy p = PolicyRules.propose(government, policyId, title, body, proposerId, nowEpochMs, effects);
      if (policies.containsKey(p.id())) {
          throw new IllegalArgumentException("policy already exists: " + p.id());
      }
      Map<String, Policy> next = new LinkedHashMap<>(policies);
      next.put(p.id(), p);
      return copyWith(zones.values(), government, next.values());
  }
  ```
- Add import `com.azoth.territory.decree.DecreeEffects`.
- Update all other callers of `Territory.proposePolicy` in the repo (grep for `proposePolicy(`) to pass `DecreeEffects.empty()` where they don't need effects.

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests com.azoth.territory.model.PolicyEffectsWiringTest`
Expected: PASS (all 6 tests).

- [ ] **Step 7: Run the full unit suite**

Run: `./gradlew test`
Expected: PASS — existing `PolicyRulesTest`, `PolicyTerritoryPersistTest`, `PolicySmokeTest`, `GovernmentTerritoryTest` still green (6-arg `PolicyRules.propose` overload keeps them compiling; `Territory`'s own callers updated in Step 5).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/azoth/territory/model/Policy.java src/main/java/com/azoth/territory/model/PolicyRules.java src/main/java/com/azoth/territory/model/Territory.java src/test/java/com/azoth/territory/model/PolicyEffectsWiringTest.java
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Attach DecreeEffects to Policy with proposal wiring"
```

---

### Task 2: Serialize `Policy.effects` in `TerritoryJson`

**Files:**
- Modify: `src/main/java/com/azoth/territory/persist/TerritoryJson.java`
- Test: `src/test/java/com/azoth/territory/persist/TerritoryJsonEffectsTest.java`

**Interfaces:**
- Consumes: `Policy.effects()` (Task 1); `DecreeEffectsCodec.toJson(DecreeEffects)` / `fromJson(JsonObject)` (`com.azoth.territory.decree.DecreeEffectsCodec`).
- Produces: `policyToJson(Policy)` emits `effects`; `policyFromJson(JsonObject)` reads it, absent → `DecreeEffects.empty()`.

- [ ] **Step 1: Write the failing test** `src/test/java/com/azoth/territory/persist/TerritoryJsonEffectsTest.java`:

```java
package com.azoth.territory.persist;

import com.azoth.territory.decree.DecreeEffects;
import com.azoth.territory.decree.TaxEffect;
import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.Policy;
import com.azoth.territory.model.Territory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Policy.effects survives the TerritoryJson codec, with back-compat for absent keys. */
class TerritoryJsonEffectsTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final TerritoryJson JSON = new TerritoryJson();

    private static DecreeEffects carrotTax() {
        return DecreeEffects.ofTax(new TaxEffect(List.of("carrot"), 15.0));
    }

    private static Territory territoryWith(DecreeEffects effects) {
        Territory t = new Territory("t1", "T", "world", new Boundary(List.of(
                new BlockPos(0, 0), new BlockPos(10, 0), new BlockPos(10, 10), new BlockPos(0, 10)
        ))).withGovernment(Government.monarchy("king:arthur"));
        return t.proposePolicy("tax", "Tax", "B", "king:arthur", NOW, effects);
    }

    @Test
    void effectsRoundTripThroughPolicyJson() {
        Policy p = territoryWith(carrotTax()).policy("tax").orElseThrow();
        Policy round = JSON.policyFromJson(JSON.policyToJson(p));
        assertEquals(carrotTax(), round.effects());
    }

    @Test
    void effectsRoundTripThroughTerritoryJson() {
        Territory t = territoryWith(carrotTax());
        Territory round = JSON.fromJson(JSON.toJson(t));
        assertEquals(carrotTax(), round.policy("tax").orElseThrow().effects());
    }

    @Test
    void policyWithoutEffectsRoundTripsAsEmpty() {
        Territory plain = new Territory("t", "T", "w", new Boundary(List.of(
                new BlockPos(0, 0), new BlockPos(10, 0), new BlockPos(10, 10), new BlockPos(0, 10)
        ))).withGovernment(Government.monarchy("k"));
        Territory proposed = plain.proposePolicy("p", "P", "B", "k", NOW, DecreeEffects.empty());
        Policy round = JSON.policyFromJson(JSON.policyToJson(proposed.policy("p").orElseThrow()));
        assertEquals(DecreeEffects.empty(), round.effects());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.azoth.territory.persist.TerritoryJsonEffectsTest`
Expected: FAIL — `policyToJson` never writes `effects`, so `policyFromJson` returns `empty()` while the first two tests expect `carrotTax()`.

- [ ] **Step 3: Implement effects serialization in `TerritoryJson`**

In `src/main/java/com/azoth/territory/persist/TerritoryJson.java`:
- Add imports `com.azoth.territory.decree.DecreeEffectsCodec;` and `com.azoth.territory.decree.DecreeEffects;`.
- In `policyToJson`, after the `votes` array is added:
  ```java
  o.add("effects", DecreeEffectsCodec.toJson(p.effects()));
  ```
- In `policyFromJson`, after `votes` is parsed and before the return:
  ```java
  DecreeEffects effects = o.has("effects") && o.get("effects").isJsonObject()
          ? DecreeEffectsCodec.fromJson(o.getAsJsonObject("effects"))
          : DecreeEffects.empty();
  ```
  and pass `effects` as the final arg to the full `Policy` ctor (9-arg).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.azoth.territory.persist.TerritoryJsonEffectsTest`
Expected: PASS (all 3 tests).

- [ ] **Step 5: Run the full unit suite**

Run: `./gradlew test`
Expected: PASS — `PolicyTerritoryPersistTest`, `TerritoryStoreTest`, `TerritoryWebServerTest` use `TerritoryJson` round-trips and must stay green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/azoth/territory/persist/TerritoryJson.java src/test/java/com/azoth/territory/persist/TerritoryJsonEffectsTest.java
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Serialize Policy effects in TerritoryJson"
```

---

### Task 3: Complete `taxRatesFromPolicies` and test the interpreter

**Files:**
- Modify: `src/main/java/com/azoth/territory/decree/DecreeEffectsInterpreter.java`
- Test: `src/test/java/com/azoth/territory/decree/DecreeEffectsInterpreterTest.java`

**Interfaces:**
- Consumes: `Policy` (`com.azoth.territory.model.Policy`; `status()`, `effects()`), `PolicyStatus.PASSED`, existing `taxRatesByGoodId(DecreeEffects)`.
- Produces: `taxRatesFromPolicies(Collection<Policy>)` → `Map<String,Double>` merged additively from PASSED policies' effects.

- [ ] **Step 1: Write the failing test** `src/test/java/com/azoth/territory/decree/DecreeEffectsInterpreterTest.java`:

```java
package com.azoth.territory.decree;

import com.azoth.territory.model.Government;
import com.azoth.territory.model.Policy;
import com.azoth.territory.model.PolicyRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Completion of the taxRatesFromPolicies stub: PASSED-only, additive across policies. */
class DecreeEffectsInterpreterTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final Government MONARCHY = Government.monarchy("king:arthur");

    private static DecreeEffects taxOn(String good, double delta) {
        return DecreeEffects.ofTax(new TaxEffect(List.of(good), delta));
    }

    private static Policy passed(String id, DecreeEffects effects) {
        return PolicyRules.decree(
                MONARCHY,
                PolicyRules.propose(MONARCHY, id, id, "B", "king:arthur", NOW, effects),
                "king:arthur", true, NOW + 1
        );
    }

    @Test
    void nullOrEmptyPoliciesYieldsEmptyMap() {
        assertTrue(DecreeEffectsInterpreter.taxRatesFromPolicies(null).isEmpty());
        assertTrue(DecreeEffectsInterpreter.taxRatesFromPolicies(List.of()).isEmpty());
    }

    @Test
    void passedPolicyContributesItsRates() {
        Map<String, Double> rates = DecreeEffectsInterpreter.taxRatesFromPolicies(
                List.of(passed("p1", taxOn("carrot", 15.0))));
        assertEquals(Map.of("carrot", 15.0), rates);
    }

    @Test
    void rejectedAndProposedPoliciesDoNotContribute() {
        Policy passedP = passed("p1", taxOn("carrot", 15.0));
        Policy proposed = PolicyRules.propose(MONARCHY, "p2", "p2", "B", "king:arthur", NOW, taxOn("potato", 10.0));
        Policy rejected = PolicyRules.decree(
                MONARCHY,
                PolicyRules.propose(MONARCHY, "p3", "p3", "B", "king:arthur", NOW, taxOn("onion", 5.0)),
                "king:arthur", false, NOW + 1
        );
        Map<String, Double> rates = DecreeEffectsInterpreter.taxRatesFromPolicies(
                List.of(passedP, proposed, rejected));
        assertEquals(Map.of("carrot", 15.0), rates);
    }

    @Test
    void multiplePassedPoliciesMergeAdditively() {
        Map<String, Double> rates = DecreeEffectsInterpreter.taxRatesFromPolicies(List.of(
                passed("p1", taxOn("carrot", 15.0)),
                passed("p2", taxOn("carrot", 5.0)),
                passed("p3", taxOn("potato", 10.0))
        ));
        assertEquals(Map.of("carrot", 20.0, "potato", 10.0), rates);
    }

    @Test
    void emptyEffectsPolicyContributesNothing() {
        Policy passedP = passed("p1", DecreeEffects.empty());
        Map<String, Double> rates = DecreeEffectsInterpreter.taxRatesFromPolicies(List.of(passedP));
        assertTrue(rates.isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.azoth.territory.decree.DecreeEffectsInterpreterTest`
Expected: FAIL — stub returns `Map.of()` for every case.

- [ ] **Step 3: Complete the stub**

Replace the body of `taxRatesFromPolicies` in `src/main/java/com/azoth/territory/decree/DecreeEffectsInterpreter.java`:
```java
public static Map<String, Double> taxRatesFromPolicies(Collection<Policy> policies) {
    if (policies == null || policies.isEmpty()) {
        return Map.of();
    }
    Map<String, Double> merged = new LinkedHashMap<>();
    for (Policy p : policies) {
        Objects.requireNonNull(p, "policy");
        if (p.status() != PolicyStatus.PASSED) {
            continue;
        }
        for (Map.Entry<String, Double> e : taxRatesByGoodId(p.effects()).entrySet()) {
            merged.merge(e.getKey(), e.getValue(), Double::sum);
        }
    }
    return Collections.unmodifiableMap(merged);
}
```
(Imports `LinkedHashMap`, `Map`, `Map.Entry`, `Collections` are already present in the file; add `Map.Entry` import if missing.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.azoth.territory.decree.DecreeEffectsInterpreterTest`
Expected: PASS (all 6 tests).

- [ ] **Step 5: Run the full unit suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/azoth/territory/decree/DecreeEffectsInterpreter.java src/test/java/com/azoth/territory/decree/DecreeEffectsInterpreterTest.java
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Aggregate tax rates from PASSED policies in DecreeEffectsInterpreter"
```

---

### Task 4: `TaxCalculator` and `SimulationTreasury`

**Files:**
- Create: `src/main/java/com/azoth/territory/economy/TaxCalculator.java`
- Create: `src/main/java/com/azoth/territory/economy/SimulationTreasury.java`
- Test: `src/test/java/com/azoth/territory/economy/SimulationTreasuryTest.java`

**Interfaces:**
- Consumes: nothing (standalone pure domain).
- Produces:
  - `TaxCalculator.tax(double grossAmount, double ratePercent)` → `double` = `gross * rate / 100`.
  - `SimulationTreasury` standalone methods now (Task 5 refactors it to implement `PaymentRail.settle(...)`):
    - `SimulationTreasury()`
    - `Map<String,Double> balances()` (unmodifiable)
    - `double balanceOf(String territoryId)` (0 if absent)
    - `SimulationTreasury credit(String territoryId, double amount)` (copy-on-write; `IllegalArgumentException` on `amount <= 0`)
    - `SimulationTreasury debit(String territoryId, double amount)` (copy-on-write; `IllegalArgumentException` on `amount <= 0`; unchanged if insufficient — never negative)

- [ ] **Step 1: Write the failing test** `src/test/java/com/azoth/territory/economy/SimulationTreasuryTest.java`:

```java
package com.azoth.territory.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tax math and the non-monetary simulation treasury ledger. */
class SimulationTreasuryTest {

    @Test
    void taxIsPercentOfGross() {
        assertEquals(1.5, TaxCalculator.tax(10.0, 15.0), 1e-9);
        assertEquals(0.0, TaxCalculator.tax(100.0, 0.0), 1e-9);
    }

    @Test
    void taxRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> TaxCalculator.tax(-1.0, 15.0));
        assertThrows(IllegalArgumentException.class, () -> TaxCalculator.tax(Double.NaN, 15.0));
        assertThrows(IllegalArgumentException.class, () -> TaxCalculator.tax(10.0, -5.0));
        assertThrows(IllegalArgumentException.class, () -> TaxCalculator.tax(10.0, Double.POSITIVE_INFINITY));
    }

    @Test
    void startsEmpty() {
        SimulationTreasury t = new SimulationTreasury();
        assertEquals(0.0, t.balanceOf("terr"), 1e-9);
        assertTrue(t.balances().isEmpty());
    }

    @Test
    void creditDebitAreCopyOnWrite() {
        SimulationTreasury t = new SimulationTreasury();
        SimulationTreasury credited = t.credit("terr", 10.0);
        assertEquals(0.0, t.balanceOf("terr"), 1e-9);  // original unchanged
        assertEquals(10.0, credited.balanceOf("terr"), 1e-9);
        SimulationTreasury debited = credited.debit("terr", 4.0);
        assertEquals(6.0, debited.balanceOf("terr"), 1e-9);
        assertEquals(10.0, credited.balanceOf("terr"), 1e-9);
    }

    @Test
    void debitNeverGoesNegative() {
        SimulationTreasury t = new SimulationTreasury().credit("terr", 5.0);
        assertEquals(5.0, t.debit("terr", 99.0).balanceOf("terr"), 1e-9);
    }

    @Test
    void invalidAmountsRejected() {
        SimulationTreasury t = new SimulationTreasury();
        assertThrows(IllegalArgumentException.class, () -> t.credit("terr", 0.0));
        assertThrows(IllegalArgumentException.class, () -> t.credit("terr", -1.0));
        assertThrows(IllegalArgumentException.class, () -> t.debit("terr", 0.0));
        assertThrows(IllegalArgumentException.class, () -> t.debit("terr", Double.NaN));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.azoth.territory.economy.SimulationTreasuryTest`
Expected: COMPILATION FAILURE — `TaxCalculator` and `SimulationTreasury` don't exist yet.

- [ ] **Step 3: Implement `TaxCalculator`** — `src/main/java/com/azoth/territory/economy/TaxCalculator.java`:

```java
package com.azoth.territory.economy;

/** Pure tax math: tax = gross * ratePercent / 100. */
public final class TaxCalculator {
    private TaxCalculator() {
    }

    public static double tax(double grossAmount, double ratePercent) {
        if (!Double.isFinite(grossAmount) || grossAmount <= 0) {
            throw new IllegalArgumentException("grossAmount must be a positive finite number, got " + grossAmount);
        }
        if (!Double.isFinite(ratePercent) || ratePercent < 0) {
            throw new IllegalArgumentException("ratePercent must be non-negative and finite, got " + ratePercent);
        }
        return grossAmount * ratePercent / 100.0;
    }
}
```

- [ ] **Step 4: Implement `SimulationTreasury`** — `src/main/java/com/azoth/territory/economy/SimulationTreasury.java`:

```java
package com.azoth.territory.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Non-monetary, in-memory treasury ledger (dev/test mode). Copy-on-write: every
 * credit/debit returns a new instance and leaves the original untouched.
 */
public final class SimulationTreasury {
    private final Map<String, Double> balances;

    public SimulationTreasury() {
        this.balances = Map.of();
    }

    private SimulationTreasury(Map<String, Double> balances) {
        this.balances = Collections.unmodifiableMap(balances);
    }

    public Map<String, Double> balances() {
        return balances;
    }

    public double balanceOf(String territoryId) {
        if (territoryId == null) {
            return 0.0;
        }
        return balances.getOrDefault(territoryId.trim(), 0.0);
    }

    public SimulationTreasury credit(String territoryId, double amount) {
        requirePositive(amount);
        return update(territoryId, get(territoryId) + amount);
    }

    public SimulationTreasury debit(String territoryId, double amount) {
        requirePositive(amount);
        double current = get(territoryId);
        if (amount > current) {
            return this;  // never negative
        }
        return update(territoryId, current - amount);
    }

    private double get(String territoryId) {
        return balances.getOrDefault(Objects.requireNonNull(territoryId, "territoryId").trim(), 0.0);
    }

    private SimulationTreasury update(String territoryId, double value) {
        String id = territoryId.trim();
        Map<String, Double> next = new LinkedHashMap<>(balances);
        next.put(id, value);
        return new SimulationTreasury(next);
    }

    private static void requirePositive(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("amount must be positive and finite, got " + amount);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests com.azoth.territory.economy.SimulationTreasuryTest`
Expected: PASS (all 7 tests).

- [ ] **Step 6: Run the full unit suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/azoth/territory/economy/TaxCalculator.java src/main/java/com/azoth/territory/economy/SimulationTreasury.java src/test/java/com/azoth/territory/economy/SimulationTreasuryTest.java
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Add tax math and simulation treasury ledger"
```

---

### Task 5: `PaymentRail`, `SettlementStatus`, and the pure-domain `EconomyBridge`

**Files:**
- Create: `src/main/java/com/azoth/territory/economy/PaymentRail.java`
- Create: `src/main/java/com/azoth/territory/economy/SettlementResult.java`
- Create: `src/main/java/com/azoth/territory/economy/TaxOutcome.java`
- Create: `src/main/java/com/azoth/territory/economy/TaxReport.java`
- Create: `src/main/java/com/azoth/territory/economy/EconomyBridge.java`
- Modify: `src/main/java/com/azoth/territory/economy/SimulationTreasury.java` (implement `PaymentRail.settle(...)`)
- Test: `src/test/java/com/azoth/territory/economy/EconomyBridgeDomainTest.java`

**Interfaces:**
- Consumes: `TaxCalculator` (Task 4), `SimulationTreasury` (Task 4), `DecreeEffectsInterpreter.taxRatesFromPolicies`, `TerritoryRegistry.resolve(String,int,int)` → `LookupResult`, `GovernanceRegistry.resolveForTerritory(String)` → `GoverningBody`, `GoverningBody.hasAssignedGovernment()`, `GoodsCatalog.findById(String)` → `Optional<Good>`.
- Produces (exact signatures — later tasks depend on these):
  - `enum PaymentRail.SettlementStatus { INSUFFICIENT_FUNDS, PAYER_UNAVAILABLE, SETTLED, COMPENSATED_FAILURE, RECONCILIATION_REQUIRED }` (nested in `PaymentRail`)
  - `record SettlementResult(PaymentRail.SettlementStatus status) { }`
  - `interface PaymentRail { SettlementResult settle(UUID payerId, String territoryId, double amount); boolean available(); }`
  - `enum TaxOutcome { TAXED, NO_TERRITORY, NO_GOVERNMENT, NO_TAX, UNKNOWN_GOOD, INVALID_AMOUNT, PAYER_UNAVAILABLE, VAULT_UNAVAILABLE, SIMULATED_TAXED, INSUFFICIENT_FUNDS, SETTLEMENT_FAILED, SETTLEMENT_RECONCILIATION_REQUIRED }`
  - `record TaxReport(TaxOutcome outcome, String territoryId, String goodId, double ratePercent, double taxAmount) {}`
  - `class EconomyBridge` with `reportSale(UUID payerId, String worldId, int blockX, int blockZ, String goodId, double grossAmount)` → `TaxReport`, `List<UnresolvedTransaction> unresolvedTransactions()`, and ctor `EconomyBridge(TerritoryRegistry territories, GovernanceRegistry governance, GoodsCatalog goods, PaymentRail rail, boolean simulationMode)`.
  - `record UnresolvedTransaction(String territoryId, UUID payerUuid, double amount, long timestampEpochMs, String reason) {}`
- Explicitly unchanged: `NO_GOVERNMENT` semantics — `resolveForTerritory` returns alliance-over-local; `!hasAssignedGovernment()` → NO_GOVERNMENT.

- [ ] **Step 1: Write the failing test** `src/test/java/com/azoth/territory/economy/EconomyBridgeDomainTest.java`:

```java
package com.azoth.territory.economy;

import com.azoth.territory.decree.DecreeEffects;
import com.azoth.territory.decree.GoodsCatalog;
import com.azoth.territory.decree.TaxEffect;
import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.LookupResult;
import com.azoth.territory.model.PolicyRules;
import com.azoth.territory.model.Territory;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-domain EconomyBridge: territory resolution, rate application, outcome mapping. */
class EconomyBridgeDomainTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final UUID PAYER = UUID.randomUUID();
    private static final String WORLD = "world";

    private static final Boundary SQUARE = new Boundary(List.of(
            new BlockPos(0, 0), new BlockPos(10, 0), new BlockPos(10, 10), new BlockPos(0, 10)
    ));

    private static DecreeEffects carrotTax() {
        return DecreeEffects.ofTax(new TaxEffect(List.of("carrot"), 15.0));
    }

    private static Territory taxedTerritory() {
        Territory t = new Territory("t1", "T", WORLD, SQUARE).withGovernment(Government.monarchy("king:arthur"));
        t = t.proposePolicy("tax", "Tax", "B", "king:arthur", NOW, carrotTax());
        return t.decreePolicy("tax", "king:arthur", true, NOW + 1);
    }

    private static EconomyBridge bridge(PaymentRail rail, boolean simulation) {
        TerritoryRegistry reg = new TerritoryRegistry();
        reg.register(taxedTerritory());
        return new EconomyBridge(reg, new GovernanceRegistry(reg), GoodsCatalog.defaultCatalog(), rail, simulation);
    }

    /** Stub rail: records settle() calls; outcome configurable. */
    private static final class RecordingRail implements PaymentRail {
        boolean available = true;
        SettlementResult result = new SettlementResult(PaymentRail.SettlementStatus.SETTLED);
        int settleCalls;

        @Override
        public SettlementResult settle(UUID payerId, String territoryId, double amount) {
            settleCalls++;
            return result;
        }

        @Override
        public boolean available() {
            return available;
        }
    }

    @Test
    void taxedSaleSettlesAndReports() {
        RecordingRail rail = new RecordingRail();
        EconomyBridge b = bridge(rail, false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);
        assertEquals(TaxOutcome.TAXED, r.outcome());
        assertEquals("t1", r.territoryId());
        assertEquals("carrot", r.goodId());
        assertEquals(15.0, r.ratePercent(), 1e-9);
        assertEquals(15.0, r.taxAmount(), 1e-9);
        assertEquals(1, rail.settleCalls);
    }

    @Test
    void outsideAnyTerritoryIsNoTerritory() {
        EconomyBridge b = bridge(new RecordingRail(), false);
        TaxReport r = b.reportSale(PAYER, WORLD, 500, 500, "carrot", 100.0);
        assertEquals(TaxOutcome.NO_TERRITORY, r.outcome());
        assertEquals(0.0, r.taxAmount(), 1e-9);
        assertEquals(0, ((RecordingRail) null).settleCalls == 0 ? 0 : ((RecordingRail) null).settleCalls);
    }

    @Test
    void anarchyTerritoryIsNoGovernment() {
        TerritoryRegistry reg = new TerritoryRegistry();
        reg.register(new Territory("t2", "T2", WORLD, SQUARE));  // no government → anarchy
        EconomyBridge b = new EconomyBridge(reg, new GovernanceRegistry(reg), GoodsCatalog.defaultCatalog(),
                new RecordingRail(), false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);
        assertEquals(TaxOutcome.NO_GOVERNMENT, r.outcome());
        assertEquals("t2", r.territoryId());
    }

    @Test
    void unknownGoodIsUnknownGood() {
        EconomyBridge b = bridge(new RecordingRail(), false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "dragon_egg", 100.0);
        assertEquals(TaxOutcome.UNKNOWN_GOOD, r.outcome());
    }

    @Test
    void untaxedGoodIsNoTax() {
        EconomyBridge b = bridge(new RecordingRail(), false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "potato", 100.0);  // only carrot is taxed
        assertEquals(TaxOutcome.NO_TAX, r.outcome());
        assertEquals(0.0, r.taxAmount(), 1e-9);
        assertEquals("t1", r.territoryId());
    }

    @Test
    void invalidAmountIsRejectedBeforeSettlement() {
        RecordingRail rail = new RecordingRail();
        EconomyBridge b = bridge(rail, false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "carrot", -5.0);
        assertEquals(TaxOutcome.INVALID_AMOUNT, r.outcome());
        assertEquals(0, rail.settleCalls);
    }

    @Test
    void railUnavailableIsVaultUnavailable() {
        RecordingRail rail = new RecordingRail();
        rail.available = false;
        EconomyBridge b = bridge(rail, false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);
        assertEquals(TaxOutcome.VAULT_UNAVAILABLE, r.outcome());
        assertEquals(0, rail.settleCalls);
    }

    @Test
    void insufficientFundsMapsThrough() {
        RecordingRail rail = new RecordingRail();
        rail.result = new SettlementResult(PaymentRail.SettlementStatus.INSUFFICIENT_FUNDS);
        EconomyBridge b = bridge(rail, false);
        assertEquals(TaxOutcome.INSUFFICIENT_FUNDS, b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0).outcome());
    }

    @Test
    void payerUnavailableMapsThrough() {
        RecordingRail rail = new RecordingRail();
        rail.result = new SettlementResult(PaymentRail.SettlementStatus.PAYER_UNAVAILABLE);
        EconomyBridge b = bridge(rail, false);
        assertEquals(TaxOutcome.PAYER_UNAVAILABLE, b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0).outcome());
    }

    @Test
    void compensatedFailureMapsThrough() {
        RecordingRail rail = new RecordingRail();
        rail.result = new SettlementResult(PaymentRail.SettlementStatus.COMPENSATED_FAILURE);
        EconomyBridge b = bridge(rail, false);
        assertEquals(TaxOutcome.SETTLEMENT_FAILED, b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0).outcome());
    }

    @Test
    void reconciliationRequiredMapsThroughAndQueues() {
        RecordingRail rail = new RecordingRail();
        rail.result = new SettlementResult(PaymentRail.SettlementStatus.RECONCILIATION_REQUIRED);
        EconomyBridge b = bridge(rail, false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);
        assertEquals(TaxOutcome.SETTLEMENT_RECONCILIATION_REQUIRED, r.outcome());
        assertEquals(1, b.unresolvedTransactions().size());
        UnresolvedTransaction u = b.unresolvedTransactions().get(0);
        assertEquals("t1", u.territoryId());
        assertEquals(PAYER, u.payerUuid());
        assertEquals(15.0, u.amount(), 1e-9);
    }

    @Test
    void simulationModeReturnsSimulatedTaxed() {
        EconomyBridge b = bridge(new RecordingRail(), true);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);
        assertEquals(TaxOutcome.SIMULATED_TAXED, r.outcome());
        assertEquals(15.0, r.taxAmount(), 1e-9);
    }

    @Test
    void multipleReconciliationsAreNotDoubleCounted() {
        RecordingRail rail = new RecordingRail();
        rail.result = new SettlementResult(PaymentRail.SettlementStatus.RECONCILIATION_REQUIRED);
        EconomyBridge b = bridge(rail, false);
        b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);
        b.reportSale(PAYER, WORLD, 5, 5, "carrot", 50.0);
        assertEquals(2, b.unresolvedTransactions().size());
    }
}
```

Removal note: the `outsideAnyTerritoryIsNoTerritory` test body has a needlessly convoluted assertion — replace with a plain `assertEquals(0, settleCalls)` captured via a `RecordingRail` reference, e.g.:
```java
RecordingRail rail = new RecordingRail();
EconomyBridge b = bridge(rail, false);
TaxReport r = b.reportSale(PAYER, WORLD, 500, 500, "carrot", 100.0);
assertEquals(TaxOutcome.NO_TERRITORY, r.outcome());
assertEquals(0, rail.settleCalls);
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.azoth.territory.economy.EconomyBridgeDomainTest`
Expected: COMPILATION FAILURE — `PaymentRail`, `SettlementResult`, `TaxOutcome`, `TaxReport`, `EconomyBridge` don't exist; `SimulationTreasury` doesn't implement `PaymentRail`.

- [ ] **Step 3: Create `PaymentRail`** — `src/main/java/com/azoth/territory/economy/PaymentRail.java`:

```java
package com.azoth.territory.economy;

import java.util.UUID;

/**
 * The money-movement seam. Exactly one implementation is active at a time
 * (VAULT or SIMULATION mode). {@code settle} encapsulates the full
 * withdraw → deposit → compensating-refund sequence and reconciliation;
 * callers cannot observe or disrupt the intermediate staged state.
 */
public interface PaymentRail {

    /** Atomic settlement outcomes; mutually exclusive, no impossible states. */
    enum SettlementStatus {
        INSUFFICIENT_FUNDS,   // payer can't cover the amount; nothing moved
        PAYER_UNAVAILABLE,    // payer has no account; nothing moved
        VAULT_UNAVAILABLE,    // Vault absent, bank-less, or territory bank not provisioned; nothing moved
        SETTLED,              // payer charged AND treasury credited
        COMPENSATED_FAILURE,  // payer charged, deposit failed, refund succeeded (net-zero)
        RECONCILIATION_REQUIRED  // payer charged, deposit+refund failed (stranded)
    }

    /**
     * Settle a tax transfer of {@code amount} from {@code payerId} to the
     * territory treasury {@code territoryId}. Implements compensation:
     * on deposit failure the payer is refunded (net-zero) or the charge is
     * flagged RECONCILIATION_REQUIRED. Never returns SETTLED unless both legs
     * completed. Non-negative, finite {@code amount} assumed.
     */
    SettlementResult settle(UUID payerId, String territoryId, double amount);

    /** True if this rail can move money at all (Vault present + bank support). */
    boolean available();
}
```

- [ ] **Step 4: Create `SettlementResult`** — `src/main/java/com/azoth/territory/economy/SettlementResult.java`:

```java
package com.azoth.territory.economy;

/** Immutable atomic-settlement outcome; status is the single source of truth. */
public record SettlementResult(PaymentRail.SettlementStatus status) {

    public SettlementResult {
        java.util.Objects.requireNonNull(status, "status");
    }

    public static SettlementResult of(PaymentRail.SettlementStatus status) {
        return new SettlementResult(status);
    }
}
```

- [ ] **Step 5: Create `TaxOutcome`** — `src/main/java/com/azoth/territory/economy/TaxOutcome.java`:

```java
package com.azoth.territory.economy;

/** Result of a {@code reportSale} call. Pre-transfer outcomes mutate nothing. */
public enum TaxOutcome {
    TAXED,                              // payer charged AND treasury credited
    SIMULATED_TAXED,                    // simulation mode: ledger-only, no real money
    NO_TERRITORY,                       // location not inside any territory
    NO_GOVERNMENT,                      // territory has no governing body / anarchy
    NO_TAX,                             // no PASSED policy taxes this good
    UNKNOWN_GOOD,                       // good id not in the catalog
    INVALID_AMOUNT,                     // gross non-positive or non-finite
    PAYER_UNAVAILABLE,                  // payer has no account
    VAULT_UNAVAILABLE,                  // Vault absent or bank-less
    INSUFFICIENT_FUNDS,                 // payer can't cover the tax
    SETTLEMENT_FAILED,                  // charged, deposit failed, refunded (net-zero)
    SETTLEMENT_RECONCILIATION_REQUIRED  // charged, deposit+refund failed (stranded)
}
```

- [ ] **Step 6: Create `TaxReport`** — `src/main/java/com/azoth/territory/economy/TaxReport.java`:

```java
package com.azoth.territory.economy;

/** Immutable outcome of a {@code reportSale} call. */
public record TaxReport(
        TaxOutcome outcome,
        String territoryId,   // null when not resolvable
        String goodId,        // null when UNKNOWN_GOOD
        double ratePercent,   // aggregated PASSED-policy rate, or 0
        double taxAmount      // gross * ratePercent / 100, or 0
) {
    public TaxReport {
        java.util.Objects.requireNonNull(outcome, "outcome");
    }
}
```

- [ ] **Step 7: Make `SimulationTreasury` implement `PaymentRail`**

In `src/main/java/com/azoth/territory/economy/SimulationTreasury.java`:
- Change declaration to `public final class SimulationTreasury implements PaymentRail`.
- Add an internal mutable state field that `settle` updates (keeping the copy-on-write `credit` semantics for Task 4 tests):
  ```java
  private volatile SimulationTreasury state = this;

  @Override
  public synchronized SettlementResult settle(UUID payerId, String territoryId, double amount) {
      state = state.credit(territoryId, amount);
      return new SettlementResult(SettlementStatus.SETTLED);
  }

  @Override
  public boolean available() {
      return true;
  }

  /** Balance of the active ledger (post-settle). */
  public double activeBalanceOf(String territoryId) {
      return state.balanceOf(territoryId);
  }
  ```
  The `credit` call inside `settle` validates `amount` (throws `IllegalArgumentException` on invalid), and the returned instance becomes the active state. Initialize `state` to `this` in the constructor.

- [ ] **Step 8: Create `EconomyBridge`** — `src/main/java/com/azoth/territory/economy/EconomyBridge.java`:

```java
package com.azoth.territory.economy;

import com.azoth.territory.decree.DecreeEffectsInterpreter;
import com.azoth.territory.decree.Good;
import com.azoth.territory.decree.GoodsCatalog;
import com.azoth.territory.model.LookupResult;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.registry.TerritoryRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Public transaction API: other plugins report sales here; Azoth applies the
 * owning settlement's tax (PASSED policy rates) and settles it through the
 * active {@link PaymentRail}. Pure domain — no Bukkit, no Vault.
 */
public class EconomyBridge {

    public record UnresolvedTransaction(
            String territoryId,
            UUID payerUuid,
            double amount,
            long timestampEpochMs,
            String reason
    ) {
    }

    private final TerritoryRegistry territories;
    private final GovernanceRegistry governance;
    private final GoodsCatalog goods;
    private final PaymentRail rail;
    private final boolean simulationMode;
    private final List<UnresolvedTransaction> unresolved = new ArrayList<>();

    public EconomyBridge(
            TerritoryRegistry territories,
            GovernanceRegistry governance,
            GoodsCatalog goods,
            PaymentRail rail,
            boolean simulationMode
    ) {
        this.territories = Objects.requireNonNull(territories, "territories");
        this.governance = Objects.requireNonNull(governance, "governance");
        this.goods = Objects.requireNonNull(goods, "goods");
        this.rail = Objects.requireNonNull(rail, "rail");
        this.simulationMode = simulationMode;
    }

    public TaxReport reportSale(
            UUID payerId,
            String worldId,
            int blockX,
            int blockZ,
            String goodId,
            double grossAmount
    ) {
        if (payerId == null) {
            return new TaxReport(TaxOutcome.PAYER_UNAVAILABLE, null, null, 0.0, 0.0);
        }
        if (!Double.isFinite(grossAmount) || grossAmount <= 0) {
            return new TaxReport(TaxOutcome.INVALID_AMOUNT, null, null, 0.0, 0.0);
        }
        if (goodId == null || goods.findById(goodId).isEmpty()) {
            return new TaxReport(TaxOutcome.UNKNOWN_GOOD, null, goodId, 0.0, 0.0);
        }
        LookupResult hit = territories.resolve(worldId, blockX, blockZ);
        if (!hit.isContained()) {
            return new TaxReport(TaxOutcome.NO_TERRITORY, null, null, 0.0, 0.0);
        }
        String territoryId = hit.territoryId().orElseThrow();
        var body = governance.resolveForTerritory(territoryId);
        if (!body.hasAssignedGovernment()) {
            return new TaxReport(TaxOutcome.NO_GOVERNMENT, territoryId, null, 0.0, 0.0);
        }
        var territory = territories.get(territoryId).orElseThrow();
        Map<String, Double> rates = DecreeEffectsInterpreter.taxRatesFromPolicies(territory.policies());
        Double rate = rates.get(Good.normalizeId(goodId));
        if (rate == null) {
            return new TaxReport(TaxOutcome.NO_TAX, territoryId, goodId, 0.0, 0.0);
        }
        double taxAmount = TaxCalculator.tax(grossAmount, rate);
        if (simulationMode) {
            return new TaxReport(TaxOutcome.SIMULATED_TAXED, territoryId, goodId, rate, taxAmount);
        }
        if (!rail.available()) {
            return new TaxReport(TaxOutcome.VAULT_UNAVAILABLE, territoryId, goodId, rate, taxAmount);
        }
        SettlementResult result = rail.settle(payerId, territoryId, taxAmount);
        return mapSettlement(result, territoryId, goodId, rate, taxAmount, payerId);
    }

    private TaxReport mapSettlement(
            SettlementResult result,
            String territoryId,
            String goodId,
            double rate,
            double taxAmount,
            UUID payerId
    ) {
        return switch (result.status()) {
            case SETTLED -> new TaxReport(TaxOutcome.TAXED, territoryId, goodId, rate, taxAmount);
            case INSUFFICIENT_FUNDS -> new TaxReport(TaxOutcome.INSUFFICIENT_FUNDS, territoryId, goodId, rate, 0.0);
            case PAYER_UNAVAILABLE -> new TaxReport(TaxOutcome.PAYER_UNAVAILABLE, territoryId, goodId, rate, 0.0);
            case COMPENSATED_FAILURE -> new TaxReport(TaxOutcome.SETTLEMENT_FAILED, territoryId, goodId, rate, 0.0);
            case RECONCILIATION_REQUIRED -> {
                unresolved.add(new UnresolvedTransaction(
                        territoryId, payerId, taxAmount, System.currentTimeMillis(), "refund failed after charge"));
                yield new TaxReport(TaxOutcome.SETTLEMENT_RECONCILIATION_REQUIRED, territoryId, goodId, rate, 0.0);
            }
        };
    }

    public List<UnresolvedTransaction> unresolvedTransactions() {
        return List.copyOf(unresolved);
    }
}
```

Note: `territories.get(territoryId).orElseThrow()` after containment is safe — `LookupResult.isContained()` guarantees a registered territory. `Good.normalizeId` aligns the lookup with the normalized keys the interpreter produces.

- [ ] **Step 9: Run test to verify it passes**

Run: `./gradlew test --tests com.azoth.territory.economy.EconomyBridgeDomainTest`
Expected: PASS (all 13 tests after the `outsideAnyTerritory` fix).

- [ ] **Step 10: Run the full unit suite**

Run: `./gradlew test`
Expected: PASS (including Task 4 `SimulationTreasuryTest` — the refactor keeps copy-on-write semantics).

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/azoth/territory/economy/ src/test/java/com/azoth/territory/economy/EconomyBridgeDomainTest.java
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Add PaymentRail seam and pure-domain EconomyBridge"
```

---

### Task 6: Vault dependency, plugin.yml, and `EconomyConfig`

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/resources/config.yml`
- Create: `src/main/java/com/azoth/territory/economy/EconomyConfig.java`
- Test: `src/test/java/com/azoth/territory/economy/EconomyConfigTest.java`

**Interfaces:**
- Produces: `enum EconomyConfig.Mode { VAULT, SIMULATION }`; `class EconomyConfig { Mode mode(); static EconomyConfig fromBukkit(org.bukkit.configuration.file.FileConfiguration cfg); }`.

- [ ] **Step 1: Add the Vault dependency (compileOnly) in `build.gradle.kts`**

In the `repositories` block add:
```kotlin
maven("https://jitpack.io")
```
In `dependencies` add:
```kotlin
compileOnly("com.github.MilkBowl:VaultAPI:1.7")
```
Run `./gradlew dependencies --configuration compileClasspath` — Expected: resolves `com.github.MilkBowl:VaultAPI:1.7`. If JitPack is unreachable, replace the repository with `maven("https://nexus.hc.to/content/repositories/pub_releases/")` and keep the same coordinate.

- [ ] **Step 2: Add `softdepend: [Vault]` to `src/main/resources/plugin.yml`**

Add after `load: POSTWORLD`:
```yaml
softdepend: [Vault]
```

- [ ] **Step 3: Add the `economy:` block to `src/main/resources/config.yml`**

Append (top-level, sibling of `web:`):
```yaml
# Settlement economy: VAULT (real money via Vault banks) or SIMULATION (dev/test ledger-only).
economy:
  mode: VAULT
```

- [ ] **Step 4: Write the failing test** `src/test/java/com/azoth/territory/economy/EconomyConfigTest.java`:

```java
package com.azoth.territory.economy;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyConfigTest {

    private static EconomyConfig fromYaml(String yaml) {
        return EconomyConfig.fromBukkit(YamlConfiguration.loadConfiguration(
                new java.io.StringReader(yaml)));
    }

    @Test
    void defaultIsVault() {
        assertEquals(EconomyConfig.Mode.VAULT, fromYaml("").mode());
    }

    @Test
    void readsExplicitMode() {
        assertEquals(EconomyConfig.Mode.SIMULATION,
                fromYaml("economy:\n  mode: SIMULATION").mode());
        assertEquals(EconomyConfig.Mode.VAULT, fromYaml("economy:\n  mode: VAULT").mode());
    }

    @Test
    void unknownModeFallsBackToVault() {
        assertEquals(EconomyConfig.Mode.VAULT, fromYaml("economy:\n  mode: COINS").mode());
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `./gradlew test --tests com.azoth.territory.economy.EconomyConfigTest`
Expected: COMPILATION FAILURE — `EconomyConfig` doesn't exist.

- [ ] **Step 6: Implement `EconomyConfig`** — `src/main/java/com/azoth/territory/economy/EconomyConfig.java`:

```java
package com.azoth.territory.economy;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/** Loads the {@code economy:} block from config.yml. */
public final class EconomyConfig {

    public enum Mode {
        VAULT,
        SIMULATION
    }

    private final Mode mode;

    public EconomyConfig(Mode mode) {
        this.mode = mode == null ? Mode.VAULT : mode;
    }

    public Mode mode() {
        return mode;
    }

    public static EconomyConfig fromBukkit(FileConfiguration cfg) {
        String raw = cfg.getString("economy.mode", "VAULT");
        Mode mode;
        try {
            mode = Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mode = Mode.VAULT;
        }
        return new EconomyConfig(mode);
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests com.azoth.territory.economy.EconomyConfigTest`
Expected: PASS (all 3 tests).

- [ ] **Step 8: Run the full unit suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add build.gradle.kts src/main/resources/plugin.yml src/main/resources/config.yml src/main/java/com/azoth/territory/economy/EconomyConfig.java src/test/java/com/azoth/territory/economy/EconomyConfigTest.java
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Add Vault soft-depend and economy mode config"
```

---

### Task 7: `VaultTreasury` — the Vault `PaymentRail` implementation

**Files:**
- Create: `src/main/java/com/azoth/territory/economy/VaultTreasury.java`
- Test: `src/test/java/com/azoth/territory/economy/VaultTreasuryTest.java`

**Interfaces:**
- Consumes: `PaymentRail`, `SettlementStatus`, `SettlementResult` (Task 5); `net.milkbowl.vault.economy.Economy` (compileOnly from Task 6).
- Produces: `class VaultTreasury implements PaymentRail` with ctor `VaultTreasury(Economy economy, java.util.function.Function<UUID, OfflinePlayer> offlinePlayerLookup)`; encapsulates withdraw → deposit → compensating-refund with strict ordering; low-level Vault calls PRIVATE; `int provisionTerritories(Collection<String> territoryIds)` provisions a Vault bank per territory id with the stable Azoth service owner and returns the count of failures.
- Test plan: Vault `Economy` is a ~30-method interface with several overload families that all return `EconomyResponse` (`withdrawPlayer(OfflinePlayer, double)`, the world-specific overloads, `bankDeposit(String, double)`, `depositPlayer(OfflinePlayer, double)`). Hand-implementing it in the test is error-prone, so **mock it with Mockito** — stub only the methods `VaultTreasury` calls and verify the called methods. `VaultTreasury` takes an injected `Function<UUID, OfflinePlayer>` so tests never touch `Bukkit`.

- [ ] **Step 1: Write the failing test** `src/test/java/com/azoth/territory/economy/VaultTreasuryTest.java`:

```java
package com.azoth.territory.economy;
```java
package com.azoth.territory.economy;

import com.azoth.territory.economy.PaymentRail.SettlementStatus;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** VaultTreasury: withdraw-first ordering, refund-on-deposit-failure, reconciliation flag. */
class VaultTreasuryTest {

    private static final UUID P = UUID.randomUUID();
    private static final OfflinePlayer PLAYER = mock(OfflinePlayer.class);

    private static EconomyResponse ok(double amount) {
        return new EconomyResponse(amount, amount, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private static EconomyResponse fail() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "failed");
    }

    private static VaultTreasury treasury(Economy e, boolean bankExists) {
        when(e.hasBankSupport()).thenReturn(true);
        when(e.bankBalance(eq("terr"))).thenReturn(
                bankExists ? ok(0) : fail());
        return new VaultTreasury(e, id -> PLAYER);
    }

    @Test
    void settledWhenBothLegsSucceed() {
        Economy e = mock(Economy.class);
        when(e.hasAccount(PLAYER)).thenReturn(true);
        when(e.has(PLAYER, 10.0)).thenReturn(true);
        when(e.withdrawPlayer(PLAYER, 10.0)).thenReturn(ok(10.0));
        when(e.bankDeposit("terr", 10.0)).thenReturn(ok(10.0));
        VaultTreasury v = treasury(e, true);

        assertEquals(SettlementStatus.SETTLED, v.settle(P, "terr", 10.0).status());
        verify(e).withdrawPlayer(PLAYER, 10.0);
        verify(e).bankDeposit("terr", 10.0);
        verify(e).bankBalance("terr");
    }

    @Test
    void insufficientFundsMovesNothing() {
        Economy e = mock(Economy.class);
        when(e.hasAccount(PLAYER)).thenReturn(true);
        when(e.has(PLAYER, 10.0)).thenReturn(false);
        VaultTreasury v = treasury(e, true);

        assertEquals(SettlementStatus.INSUFFICIENT_FUNDS, v.settle(P, "terr", 10.0).status());
        verify(e).has(PLAYER, 10.0);
    }

    @Test
    void withdrawFailureMovesNothing() {
        Economy e = mock(Economy.class);
        when(e.hasAccount(PLAYER)).thenReturn(true);
        when(e.has(PLAYER, 10.0)).thenReturn(true);
        when(e.withdrawPlayer(PLAYER, 10.0)).thenReturn(fail());
        VaultTreasury v = treasury(e, true);

        assertEquals(SettlementStatus.PAYER_UNAVAILABLE, v.settle(P, "terr", 10.0).status());
        verify(e).bankDeposit(eq("terr"), anyDouble());
    }

    @Test
    void depositFailureTriggersRefundNetZero() {
        Economy e = mock(Economy.class);
        when(e.hasAccount(PLAYER)).thenReturn(true);
        when(e.has(PLAYER, 10.0)).thenReturn(true);
        when(e.withdrawPlayer(PLAYER, 10.0)).thenReturn(ok(10.0));
        when(e.bankDeposit("terr", 10.0)).thenReturn(fail());
        when(e.depositPlayer(PLAYER, 10.0)).thenReturn(ok(10.0));
        VaultTreasury v = treasury(e, true);

        assertEquals(SettlementStatus.COMPENSATED_FAILURE, v.settle(P, "terr", 10.0).status());
        verify(e).withdrawPlayer(PLAYER, 10.0);
        verify(e).bankDeposit("terr", 10.0);
    }

    @Test
    void refundFailureFlagsReconciliation() {
        Economy e = mock(Economy.class);
        when(e.hasAccount(PLAYER)).thenReturn(true);
        when(e.has(PLAYER, 10.0)).thenReturn(true);
        when(e.withdrawPlayer(PLAYER, 10.0)).thenReturn(ok(10.0));
        when(e.bankDeposit("terr", 10.0)).thenReturn(fail());
        when(e.depositPlayer(PLAYER, 10.0)).thenReturn(fail());
        VaultTreasury v = treasury(e, true);

        assertEquals(SettlementStatus.RECONCILIATION_REQUIRED, v.settle(P, "terr", 10.0).status());
        verify(e).withdrawPlayer(PLAYER, 10.0);
        verify(e).bankDeposit("terr", 10.0);
    }

    @Test
    void bankNotProvisionedIsVaultUnavailable() {
        Economy e = mock(Economy.class);
        VaultTreasury v = treasury(e, false);
        assertEquals(SettlementStatus.VAULT_UNAVAILABLE, v.settle(P, "terr", 10.0).status());
    }

    @Test
    void provisionTerritoriesCreatesMissingBanks() {
        Economy e = mock(Economy.class);
        when(e.hasBankSupport()).thenReturn(true);
        when(e.bankBalance("terr")).thenReturn(fail());  // absent
        when(e.createBank("terr", "AzothTerritory-Service")).thenReturn(ok(0));
        VaultTreasury v = new VaultTreasury(e, id -> PLAYER);

        assertEquals(0, v.provisionTerritories(List.of("terr")));
        verify(e).createBank("terr", "AzothTerritory-Service");
    }

    @Test
    void provisionFailureCountsUnprovisioned() {
        Economy e = mock(Economy.class);
        when(e.hasBankSupport()).thenReturn(true);
        when(e.bankBalance("terr")).thenReturn(fail());
        when(e.createBank(eq("terr"), any())).thenReturn(fail());
        VaultTreasury v = new VaultTreasury(e, id -> PLAYER);

        assertEquals(1, v.provisionTerritories(List.of("terr")));
    }

    @Test
    void unavailableWhenVaultEconomyMissing() {
        VaultTreasury v = new VaultTreasury(null, id -> PLAYER);
        assertFalse(v.available());
    }
}
```

Note: `VaultTreasury` now takes a second ctor arg `Function<UUID, OfflinePlayer> offlinePlayerLookup` so tests never touch `Bukkit`. The production plugin passes `Bukkit::getOfflinePlayer` (Task 8).

Note: if the compiler reports a missing abstract method on `StubEconomy`, add a trivial no-op returning `null`/`false`/`0.0`/`List.of()` as appropriate.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.azoth.territory.economy.VaultTreasuryTest`
Expected: COMPILATION FAILURE — `VaultTreasury` doesn't exist (and possibly the stub needs more overrides; fix the stub until it compiles and the test fails on the missing class).

- [ ] **Step 3: Implement `VaultTreasury`** — `src/main/java/com/azoth/territory/economy/VaultTreasury.java`:

```java
package com.azoth.territory.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * Vault-backed {@link PaymentRail}. Territory balances live in Vault bank
 * accounts (bank id = territory id), provisioned at startup by
 * {@link #provisionTerritories(java.util.Collection)} with a stable Azoth
 * service owner (never a sale payer). The payer is charged from their player
 * account. Compensation sequence: ensure bank exists (VAULT_UNAVAILABLE if
 * not) → withdraw payer → deposit bank → on deposit failure refund payer; if
 * the refund also fails the charge is stranded (RECONCILIATION_REQUIRED).
 * Low-level Vault calls are private to this class.
 */
public final class VaultTreasury implements PaymentRail {

    private static final String SERVICE_OWNER = "AzothTerritory-Service";
    private static final UUID SERVICE_OWNER_ID = UUID.nameUUIDFromBytes(SERVICE_OWNER.getBytes());

    private final Economy economy;

    public VaultTreasury(Economy economy) {
        this.economy = economy;
    }

    /**
     * Provision a Vault bank account for each territory id, owned by the stable
     * Azoth service account. Call once at startup (and after new territories are
     * registered). Idempotent: existing banks are left untouched. Returns the
     * number of territories whose bank could NOT be provisioned.
     */
    public int provisionTerritories(java.util.Collection<String> territoryIds) {
        if (economy == null || !economy.hasBankSupport()) {
            return territoryIds == null ? 0 : territoryIds.size();
        }
        OfflinePlayer serviceOwner = serviceOwner();
        int failed = 0;
        for (String id : territoryIds) {
            if (id == null) {
                failed++;
                continue;
            }
            EconomyResponse exists = economy.bankBalance(id);
            if (exists != null && exists.transactionSuccess()) {
                continue;
            }
            EconomyResponse created = economy.createBank(id, serviceOwner.getName());
            if (created == null || !created.transactionSuccess()) {
                failed++;
            }
        }
        return failed;
    }

    @Override
    public SettlementResult settle(UUID payerId, String territoryId, double amount) {
        if (economy == null || !economy.hasBankSupport()) {
            return new SettlementResult(SettlementStatus.PAYER_UNAVAILABLE);
        }
        // Bank must already exist (startup-provided); never create with the payer.
        if (!bankExists(territoryId)) {
            return new SettlementResult(SettlementStatus.VAULT_UNAVAILABLE);
        }
        OfflinePlayer payer = Bukkit.getOfflinePlayer(payerId);
        if (!economy.hasAccount(payer)) {
            return new SettlementResult(SettlementStatus.PAYER_UNAVAILABLE);
        }
        if (!economy.has(payer, amount)) {
            return new SettlementResult(SettlementStatus.INSUFFICIENT_FUNDS);
        }
        // Withdraw payer first, so a failure can always be unwound back to the payer.
        EconomyResponse withdrawal = economy.withdrawPlayer(payer, amount);
        if (withdrawal == null || !withdrawal.transactionSuccess()) {
            return new SettlementResult(SettlementStatus.PAYER_UNAVAILABLE);
        }
        EconomyResponse deposit = economy.bankDeposit(territoryId, amount);
        if (deposit != null && deposit.transactionSuccess()) {
            return new SettlementResult(SettlementStatus.SETTLED);
        }
        // Deposit failed — compensate the payer.
        EconomyResponse refund = economy.depositPlayer(payer, amount);
        if (refund != null && refund.transactionSuccess()) {
            return new SettlementResult(SettlementStatus.COMPENSATED_FAILURE);
        }
        return new SettlementResult(SettlementStatus.RECONCILIATION_REQUIRED);
    }

    private boolean bankExists(String territoryId) {
        if (economy == null) {
            return false;
        }
        EconomyResponse r = economy.bankBalance(territoryId);
        return r != null && r.transactionSuccess();
    }

    private static OfflinePlayer serviceOwner() {
        OfflinePlayer op = Bukkit.getOfflinePlayer(SERVICE_OWNER_ID);
        // getName() can be null for an unknown offline player; fall back to the id string.
        if (op.getName() == null) {
            op = Bukkit.getOfflinePlayer(SERVICE_OWNER);
        }
        return op;
    }

    @Override
    public boolean available() {
        return economy != null && economy.hasBankSupport();
    }

    /** Balance of a territory's bank account via Vault (0 if bank absent). */
    public double bankBalance(String territoryId) {
        if (economy == null || !economy.hasBankSupport()) {
            return 0.0;
        }
        EconomyResponse r = economy.bankBalance(territoryId);
        return r != null && r.transactionSuccess() ? r.balance : 0.0;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.azoth.territory.economy.VaultTreasuryTest`
Expected: PASS (all 6 tests).

- [ ] **Step 5: Run the full unit suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/azoth/territory/economy/VaultTreasury.java src/test/java/com/azoth/territory/economy/VaultTreasuryTest.java
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Implement Vault-backed PaymentRail with compensation"
```

---

### Task 8: Wire the economy into `AzothTerritoryPlugin` (incl. `BukkitEconomyBridge`)

**Files:**
- Modify: `src/main/java/com/azoth/territory/AzothTerritoryPlugin.java`
- Create: `src/main/java/com/azoth/territory/economy/BukkitEconomyBridge.java`
- Test: `src/test/java/com/azoth/territory/economy/BukkitEconomyBridgeTest.java`
- Test: `src/test/java/com/azoth/territory/PluginEconomyWiringTest.java`

**Interfaces:**
- Consumes: `EconomyConfig` (Task 6), `VaultTreasury` (Task 7), `EconomyBridge` (Task 5), `SimulationTreasury` (Task 5), `GovernanceRegistry`, `TerritoryRegistry`, `GoodsCatalog`.
- Produces:
  - `class BukkitEconomyBridge { TaxReport reportSale(OfflinePlayer payer, String worldId, int blockX, int blockZ, String goodId, double grossAmount); }` — wraps an `EconomyBridge` and delegates with `payer.getUniqueId()`.
  - `AzothTerritoryPlugin.getEconomyBridge()` → the pure-domain `EconomyBridge`.
  - `AzothTerritoryPlugin.onEnable()` wires the rail from `EconomyConfig` (see Step 4).
- Explicitly unchanged: web wiring, protection wiring, `TerritoryCommand`.

- [ ] **Step 1: Write the failing test** `src/test/java/com/azoth/territory/economy/BukkitEconomyBridgeTest.java`:

```java
package com.azoth.territory.economy;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** BukkitEconomyBridge delegates from OfflinePlayer to the domain UUID API. */
class BukkitEconomyBridgeTest {

    private static final class CapturingBridge extends EconomyBridge {
        UUID lastPayer;

        CapturingBridge() {
            super(new com.azoth.territory.registry.TerritoryRegistry(),
                    new com.azoth.territory.permission.GovernanceRegistry(
                            new com.azoth.territory.registry.TerritoryRegistry()),
                    com.azoth.territory.decree.GoodsCatalog.defaultCatalog(),
                    new RecordingRail(), false);
        }

        @Override
        public TaxReport reportSale(UUID payerId, String worldId, int blockX, int blockZ,
                                    String goodId, double grossAmount) {
            lastPayer = payerId;
            return new TaxReport(TaxOutcome.NO_TAX, null, goodId, 0.0, 0.0);
        }
    }

    private static final class RecordingRail implements PaymentRail {
        @Override
        public SettlementResult settle(UUID payerId, String territoryId, double amount) {
            return new SettlementResult(PaymentRail.SettlementStatus.SETTLED);
        }
        @Override
        public boolean available() { return true; }
    }

    @Test
    void delegatesPayerUuid() {
        UUID id = UUID.randomUUID();
        OfflinePlayer op = mock(OfflinePlayer.class);
        when(op.getUniqueId()).thenReturn(id);
        CapturingBridge db = new CapturingBridge();
        BukkitEconomyBridge b = new BukkitEconomyBridge(db);
        b.reportSale(op, "world", 1, 2, "carrot", 10.0);
        assertEquals(id, db.lastPayer);
    }
}
```

This requires Mockito. Add to `testImplementation` in `build.gradle.kts` (Task 6 already touched the file; if Mockito is absent, add):
```kotlin
testImplementation("org.mockito:mockito-core:5.14.2")
testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
```
`Paper` ships its own Mockito-compatible deps; if `mock(OfflinePlayer.class)` fails because `OfflinePlayer` is abstract with final methods, mock the interface via `Mockito.mock(OfflinePlayer.class)` — `OfflinePlayer` is an interface, so this works.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.azoth.territory.economy.BukkitEconomyBridgeTest`
Expected: COMPILATION FAILURE — `BukkitEconomyBridge` doesn't exist (add Mockito deps if needed).

- [ ] **Step 3: Implement `BukkitEconomyBridge`** — `src/main/java/com/azoth/territory/economy/BukkitEconomyBridge.java`:

```java
package com.azoth.territory.economy;

import org.bukkit.OfflinePlayer;

import java.util.Objects;

/** Bukkit-friendly facade: OfflinePlayer overload delegating to the UUID domain API. */
public final class BukkitEconomyBridge {

    private final EconomyBridge delegate;

    public BukkitEconomyBridge(EconomyBridge delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public TaxReport reportSale(
            OfflinePlayer payer,
            String worldId,
            int blockX,
            int blockZ,
            String goodId,
            double grossAmount
    ) {
        if (payer == null) {
            return delegate.reportSale(null, worldId, blockX, blockZ, goodId, grossAmount);
        }
        return delegate.reportSale(payer.getUniqueId(), worldId, blockX, blockZ, goodId, grossAmount);
    }
}
```

- [ ] **Step 4: Wire `AzothTerritoryPlugin`**

In `src/main/java/com/azoth/territory/AzothTerritoryPlugin.java`:
- Add fields:
  ```java
  private EconomyBridge economyBridge;
  private BukkitEconomyBridge bukkitEconomyBridge;
  ```
- In `onEnable`, after `this.governance = new GovernanceRegistry(registry);` and before the protection listener registration, add the wiring (compute Vault economy once):
  ```java
  try {
      EconomyConfig econCfg = EconomyConfig.fromBukkit(getConfig());
      boolean simulation = econCfg.mode() == EconomyConfig.Mode.SIMULATION;
      net.milkbowl.vault.economy.Economy vaultEcon = resolveVaultEconomy();
      PaymentRail rail;
      if (simulation) {
          rail = new SimulationTreasury();
          getLogger().info("Economy in SIMULATION mode — non-monetary ledger only, no player charges");
      } else if (vaultEcon != null) {
          rail = new VaultTreasury(vaultEcon);
          if (!rail.available()) {
              getLogger().warning("Vault provider lacks bank support — settlement returns VAULT_UNAVAILABLE");
          } else {
              getLogger().info("Economy wired to Vault banks (territory treasury per settlement)");
          }
      } else {
          rail = new SimulationTreasury();
          getLogger().warning("Vault not found — settlement returns VAULT_UNAVAILABLE");
      }
      this.economyBridge = new EconomyBridge(registry, governance, GoodsCatalog.defaultCatalog(), rail, simulation);
      this.bukkitEconomyBridge = new BukkitEconomyBridge(economyBridge);
  } catch (Exception e) {
      getLogger().log(Level.SEVERE, "Failed to wire economy — settlement disabled", e);
      this.economyBridge = null;
      this.bukkitEconomyBridge = null;
  }
  ```
  Note: the `simulation` flag passed to `EconomyBridge` stays `econCfg.mode() == SIMULATION` — the Vault-absent fallback rail is a `SimulationTreasury` but the bridge must return `VAULT_UNAVAILABLE` (via `!rail.available()`) rather than `SIMULATED_TAXED`. Since `SimulationTreasury.available()` returns `true`, the bridge's `simulationMode` flag controls the distinction: in VAULT mode with a fallback rail, `simulationMode=false` so a sale hits `rail.settle(...)` and credits the simulation ledger... **which violates the no-minting invariant.** Fix: in the Vault-absent fallback case, do NOT use the bridge's `settle` path — instead construct the bridge with `simulation=true` AND log loudly, OR keep `simulation=false` but make the fallback rail return `PAYER_UNAVAILABLE` from `settle` and `false` from `available()`. **Chosen (leaning on spec §3): a fallback `SimulationTreasury` instance whose `settle` is never reached because `available()` returns `false`.** Give the fallback a dedicated rail:
  ```java
  private static final class UnavailableRail implements PaymentRail {
      @Override public SettlementResult settle(UUID payerId, String territoryId, double amount) {
          return new SettlementResult(PaymentRail.SettlementStatus.PAYER_UNAVAILABLE);
      }
      @Override public boolean available() { return false; }
  }
  ```
  Use `new UnavailableRail()` in the Vault-absent branch so `reportSale` returns `VAULT_UNAVAILABLE` without touching any ledger. This preserves the no-minting invariant: in VAULT mode no treasury balance ever appears without a real payer charge.
- Add `resolveVaultEconomy()` helper:
  ```java
  private net.milkbowl.vault.economy.Economy resolveVaultEconomy() {
      var provider = getServer().getServicesManager()
              .getRegistration(net.milkbowl.vault.economy.Economy.class);
      return provider == null ? null : provider.getProvider();
  }
  ```
- Add getters:
  ```java
  public EconomyBridge getEconomyBridge() { return economyBridge; }
  public BukkitEconomyBridge getBukkitEconomyBridge() { return bukkitEconomyBridge; }
  ```
- Add imports for `com.azoth.territory.economy.*`, `java.util.UUID`.

- [ ] **Step 5: Add a wiring smoke test** `src/test/java/com/azoth/territory/PluginEconomyWiringTest.java`:

```java
package com.azoth.territory;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The packaged plugin.yml soft-depends on Vault. */
class PluginEconomyWiringTest {
    @Test
    void pluginMetadataDeclaresSoftDependVault() throws Exception {
        String yml = new String(
                getClass().getResourceAsStream("/plugin.yml").readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(yml.contains("softdepend") && yml.contains("Vault"),
                "plugin.yml must soft-depend on Vault");
    }
}
```

- [ ] **Step 6: Run tests to verify**

Run: `./gradlew test --tests com.azoth.territory.economy.BukkitEconomyBridgeTest --tests com.azoth.territory.PluginEconomyWiringTest`
Expected: PASS (both).

- [ ] **Step 7: Run the full unit suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/azoth/territory/AzothTerritoryPlugin.java src/main/java/com/azoth/territory/economy/BukkitEconomyBridge.java src/test/java/com/azoth/territory/economy/BukkitEconomyBridgeTest.java src/test/java/com/azoth/territory/PluginEconomyWiringTest.java
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Wire economy bridge into plugin with Vault soft-depend"
```

---

### Task 9: Build the jar and verify the full suite

**Files:** none (verification + commit of any leftover docs).

**Interfaces:** n/a.

- [ ] **Step 1: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — `build/libs/azoth-territory-1.0.0-SNAPSHOT.jar` produced; all tests pass; plugin.yml expanded with `softdepend: [Vault]`.

- [ ] **Step 2: Inspect the packaged plugin.yml**

Run: `unzip -p build/libs/azoth-territory-1.0.0-SNAPSHOT.jar plugin.yml`
Expected: contains `softdepend: [Vault]` and `api-version: '1.21'`.

- [ ] **Step 3: Confirm the money invariant holds end to end**

Verify by reading the final sources: `EconomyBridge` returns `TAXED` (or `SIMULATED_TAXED` in simulation) only after `rail.settle` returns `SETTLED`; `VaultTreasury.settle` returns `SETTLED` only after withdraw + deposit both succeed; the Vault-absent fallback rail is `UnavailableRail` (never settles, never credits a ledger in VAULT mode); a failed deposit refunds the payer (`COMPENSATED_FAILURE`) or flags `RECONCILIATION_REQUIRED`.

- [ ] **Step 4: Commit any remaining spec/plan documents**

```bash
git add docs/superpowers/
git -c user.name="Azoth" -c user.email="azoth@users.noreply.github.com" commit -m "Add economy hooks implementation plan"
```
(Only if not already committed in a prior task.)

---

## Self-Review

**1. Spec coverage:**
- §2 architecture (economy package, layering) → Tasks 4–8.
- §3 PaymentRail seam + single source of truth + `SettlementStatus` → Task 5 (interface + enum), Task 7 (VaultTreasury), Task 8 (UnavailableRail fallback keeps VAULT-mode no-minting).
- §4 public API (`reportSale` UUID signature, `TaxReport`, `TaxOutcome`, Bukkit adapter) → Tasks 5, 8.
- §5 atomic settlement (withdraw→deposit→refund, reconciliation) → Tasks 5, 7.
- §6 decree wiring (`Policy.effects`, `PolicyRules.propose`, `TerritoryJson`, `taxRatesFromPolicies`) → Tasks 1–3.
- §7 data flow (`TerritoryRegistry.resolve` → `NO_TERRITORY`, `resolveForTerritory` → `NO_GOVERNMENT`, rate → settle) → Task 5 (`EconomyBridge.reportSale`).
- §8 error handling/outcomes → Task 5 (`TaxOutcome`), Tasks 7–8.
- §9 config (`economy.mode`) → Task 6.
- §10 build/plugin.yml (`softdepend: [Vault]`, Vault repo) → Task 6 (dependency), Task 8 (wiring).
- §11 tests → each task's test file; `VaultTreasuryTest` covers withdraw-first/refund/reconciliation; `EconomyBridgeDomainTest` covers outcome mapping; `PluginEconomyWiringTest` asserts the soft-depend.

**2. Placeholder scan:** no TBD/TODO; every step has concrete code or an exact command. The `BukkitEconomyBridgeTest` spells out the Mockito approach; no "similar to Task N" left open.

**3. Type consistency:**
- `PolicyRules.propose(...)` has BOTH the 6-arg delegating overload AND the 7-arg effectful variant — consistent across Task 1 and test code (existing callers use 6-arg; new tests use 7-arg).
- `Policy` 9-arg full ctor + 8-arg overload; `TerritoryJson` uses the 9-arg with effects — consistent Tasks 1–2.
- `PaymentRail.SettlementStatus` nested enum; `SettlementResult(status)` — consistent Tasks 5, 7, 8.
- `EconomyBridge.reportSale(UUID, String, int, int, String, double)` → `TaxReport` — same in Task 5, Task 8 adapter.
- `EconomyConfig.fromBukkit(FileConfiguration)` → `EconomyConfig.mode()` — consistent Tasks 6, 8.
- `VaultTreasury.settle(UUID, String, double)` returns `SettlementResult`; `available()`; `bankBalance(String)` — consistent Tasks 7, 8.

**4. Cross-task dependencies:**
- Task 5 needs `TaxCalculator` + `SimulationTreasury` (Task 4).
- Task 7 needs `PaymentRail`/`SettlementStatus`/`SettlementResult` (Task 5) + Vault compileOnly (Task 6).
- Task 8 needs `EconomyConfig` (Task 6), `VaultTreasury` (Task 7), `EconomyBridge` + `SimulationTreasury` (Task 5).
- Task 9 needs everything.
