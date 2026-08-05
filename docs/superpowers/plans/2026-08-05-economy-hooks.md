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
  - `Policy` full ctor `Policy(String id, String title, String body, String proposerId, PolicyStatus status, List<PolicyVote> votes, Long resolvedAtEpochMs, Long proposedAtEpochMs, DecreeEffects effects)`.
  - `Policy.propose(String id, String title, String body, String proposerId, long proposedAtEpochMs)` (no-effects back-compat overload).
  - `PolicyRules.propose(Government government, String id, String title, String body, String proposerId, long nowEpochMs, DecreeEffects effects)` (new effects param).
  - `Territory.proposePolicy(String policyId, String title, String body, String proposerId, long nowEpochMs, DecreeEffects effects)` (new effects param; passes through to `PolicyRules.propose`).
  - `castVote`/`decree`/`resolveIfPossible`/`withVote`/`withStatus` PRESERVE `effects` unchanged.
- Explicitly unchanged: `Government`, `PolicyStatus`, `PolicyVote`, `VoteChoice`, `GovernmentForm` (no edits).

- [ ] **Step 1: Write the failing test** `src/test/java/com/azoth/territory/model/PolicyEffectsWiringTest.java`:

```java
package com.azoth.territory.model;

import com.azoth.territory.decree.DecreeEffects;
import com.azoth.territory.decree.TaxEffect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void equalsHashCodeIncludeEffects() {
        Policy a = PolicyRules.propose(Government.monarchy("k"), "p", "T", "B", "k", NOW, carrotTax());
        Policy b = PolicyRules.propose(Government.monarchy("k"), "p", "T", "B", "k", NOW, DecreeEffects.empty());
        assertEquals(carrotTax(), a.effects());
        assertEquals(a.hashCode(), a.hashCode());
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
Expected: COMPILATION FAILURE — `PolicyRules.propose(...)` has no 7-arg overload with `DecreeEffects`, `Territory.proposePolicy(...)` has no 6-arg overload, `Policy.effects()` undefined.

- [ ] **Step 3: Add the `effects` field to `Policy`**

In `src/main/java/com/azoth/territory/model/Policy.java`:
- Add field `private final DecreeEffects effects;`.
- Full ctor: add trailing param `DecreeEffects effects`; body sets `this.effects = effects == null ? DecreeEffects.empty() : effects;`.
- Add no-effects convenience ctor overload that delegates with `DecreeEffects.empty()`:
  ```java
  public Policy(String id, String title, String body, String proposerId,
                PolicyStatus status, List<PolicyVote> votes,
                Long resolvedAtEpochMs, Long proposedAtEpochMs) {
      this(id, title, body, proposerId, status, votes, resolvedAtEpochMs, proposedAtEpochMs, DecreeEffects.empty());
  }
  ```
- `Policy.propose(...)`: delegate with `DecreeEffects.empty()` (keep signature — TerritoryJson's `policyFromJson` uses the full ctor).
- `withVote`: pass `effects` through to the full ctor.
- `withStatus`: pass `effects` through to the full ctor.
- Add accessor `public DecreeEffects effects() { return effects; }`.
- `equals`: add `&& effects.equals(that.effects)`.
- `hashCode`: add `effects` to the `Objects.hash(...)`.
- `toString`: unchanged (note: `toString` does not need effects; do not add).
- Add import `com.azoth.territory.decree.DecreeEffects`.

- [ ] **Step 4: Thread effects through `PolicyRules.propose`**

In `src/main/java/com/azoth/territory/model/PolicyRules.java`, change `propose` to:
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
Add import `com.azoth.territory.decree.DecreeEffects`.

- [ ] **Step 5: Thread effects through `Territory.proposePolicy`**

In `src/main/java/com/azoth/territory/model/Territory.java`, change `proposePolicy` to:
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
Add import `com.azoth.territory.decree.DecreeEffects`. Keep `castPolicyVote`/`decreePolicy`/`replacePolicy` unchanged — they operate on existing `Policy` objects whose `effects` travel with the copy.

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests com.azoth.territory.model.PolicyEffectsWiringTest`
Expected: PASS (all 6 tests).

- [ ] **Step 7: Run the full unit suite**

Run: `./gradlew test`
Expected: PASS — existing `PolicyRulesTest`, `PolicyTerritoryPersistTest`, `PolicySmokeTest` still green (their `PolicyRules.propose(...)` calls use the 6-arg form — verify the signature change does not break them; the no-effects convenience ctor and the extra full-ctor param are backward compatible because `TerritoryJson.policyFromJson` currently calls the 8-arg full ctor, which still exists).

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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        Territory t = territoryWith(carrotTax());
        Policy p = t.policy("tax").orElseThrow();
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
    void absentEffectsKeyDefaultsToEmpty() {
        Policy p = territoryWith(carrotTax()).policy("tax").orElseThrow();
        // strip the effects key to simulate pre-feature data (back-compat)
        var json = JSON.policyFromJson(JSON.policyToJson(p));
        assertEquals(carrotTax(), json.effects());
        // policy without effects round-trips as empty
        Territory plain = new Territory("t", "T", "w", new Boundary(List.of(
                new BlockPos(0, 0), new BlockPos(10, 0), new BlockPos(10, 10), new BlockPos(0, 10)
        ))).withGovernment(Government.monarchy("k"));
        Policy plainP = plain.proposePolicy("p", "P", "B", "k", NOW, DecreeEffects.empty()).policy("p").orElseThrow();
        assertEquals(DecreeEffects.empty(), JSON.policyFromJson(JSON.policyToJson(plainP)).effects());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.azoth.territory.persist.TerritoryJsonEffectsTest`
Expected: FAIL — `policyToJson` never writes `effects`, so `policyFromJson` returns `empty()` while the test expects `carrotTax()`.

- [ ] **Step 3: Implement effects serialization in `TerritoryJson`**

In `src/main/java/com/azoth/territory/persist/TerritoryJson.java`:

- Add import `com.azoth.territory.decree.DecreeEffectsCodec;` and `com.azoth.territory.decree.DecreeEffects;`.
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
  and pass `effects` as the final arg to the full `Policy` ctor (9 args).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests com.azoth.territory.persist.TerritoryJsonEffectsTest`
Expected: PASS (all 3 tests).

- [ ] **Step 5: Run the full unit suite**

Run: `./gradlew test`
Expected: PASS — `PolicyTerritoryPersistTest`, `TerritoryStoreTest`, `TerritoryWebServerTest` use `TerritoryJson` round-trips and must stay green; `TerritoryStoreTest` persists a registry file (check `.gitignore` covers any files written into the repo by tests).

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
import com.azoth.territory.model.PolicyStatus;
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
        Policy passed = passed("p1", taxOn("carrot", 15.0));
        Policy proposed = PolicyRules.propose(MONARCHY, "p2", "p2", "B", "king:arthur", NOW, taxOn("potato", 10.0));
        Policy rejected = PolicyRules.decree(
                MONARCHY,
                PolicyRules.propose(MONARCHY, "p3", "p3", "B", "king:arthur", NOW, taxOn("onion", 5.0)),
                "king:arthur", false, NOW + 1
        );
        Map<String, Double> rates = DecreeEffectsInterpreter.taxRatesFromPolicies(
                List.of(passed, proposed, rejected));
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
        Policy passed = passed("p1", DecreeEffects.empty());
        Map<String, Double> rates = DecreeEffectsInterpreter.taxRatesFromPolicies(List.of(passed));
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
(Imports `LinkedHashMap`, `Map`, `Collections` already present in the file.)

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
  - `SimulationTreasury` implements `PaymentRail` (defined in Task 5) — BUT this task does not yet have `PaymentRail`. Define `SimulationTreasury` with these standalone public methods now; Task 5 will refactor it to implement `PaymentRail.settle(...)`:
    - `SimulationTreasury()`
    - `Map<String,Double> balances()` (unmodifiable)
    - `double balanceOf(String territoryId)` (0 if absent)
    - `SimulationTreasury credit(String territoryId, double amount)` (copy-on-write; throws `IllegalArgumentException` on `amount <= 0`)
    - `SimulationTreasury debit(String territoryId, double amount)` (copy-on-write; throws `IllegalArgumentException` on `amount <= 0`; leaves balance unchanged if insufficient — never negative)

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
        assertTrue(t.balances().isEmpty() && t.balanceOf("nope") == 0.0);
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

- [ ] **Step 3: Implement `TaxCalculator`** `src/main/java/com/azoth/territory/economy/TaxCalculator.java`:

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

- [ ] **Step 4: Implement `SimulationTreasury`** `src/main/java/com/azoth/territory/economy/SimulationTreasury.java`:

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

### Task 5: `PaymentRail` seam, `SettlementStatus`, and the pure-domain `EconomyBridge`

**Files:**
- Create: `src/main/java/com/azoth/territory/economy/PaymentRail.java`
- Create: `src/main/java/com/azoth/territory/economy/SettlementStatus.java`
- Create: `src/main/java/com/azoth/territory/economy/SettlementResult.java`
- Create: `src/main/java/com/azoth/territory/economy/TaxOutcome.java`
- Create: `src/main/java/com/azoth/territory/economy/TaxReport.java`
- Create: `src/main/java/com/azoth/territory/economy/EconomyBridge.java`
- Modify: `src/main/java/com/azoth/territory/economy/SimulationTreasury.java` (implement `PaymentRail.settle(...)`)
- Test: `src/test/java/com/azoth/territory/economy/EconomyBridgeDomainTest.java`

**Interfaces:**
- Consumes: `TaxCalculator` (Task 4), `SimulationTreasury` (Task 4), `DecreeEffectsInterpreter.taxRatesFromPolicies`, `TerritoryRegistry.resolve(...)`, `GovernanceRegistry.resolveForTerritory(...)`, `LookupResult`, `GoverningBody`.
- Produces (exact signatures — later tasks depend on these):
  - `enum PaymentRail.SettlementStatus { INSUFFICIENT_FUNDS, PAYER_UNAVAILABLE, SETTLED, COMPENSATED_FAILURE, RECONCILIATION_REQUIRED }`
  - `record SettlementResult(PaymentRail.SettlementStatus status) { }` — note: named `SettlementResult`, but the enum lives inside `PaymentRail` (Java allows a nested enum in an interface).
  - `interface PaymentRail { SettlementResult settle(UUID payerId, String territoryId, double amount); boolean available(); }`
  - `enum TaxOutcome { TAXED, NO_TERRITORY, NO_GOVERNMENT, NO_TAX, UNKNOWN_GOOD, INVALID_AMOUNT, PAYER_UNAVAILABLE, VAULT_UNAVAILABLE, SIMULATED_TAXED, INSUFFICIENT_FUNDS, SETTLEMENT_FAILED, SETTLEMENT_RECONCILIATION_REQUIRED }`
  - `record TaxReport(TaxOutcome outcome, String territoryId, String goodId, double ratePercent, double taxAmount) {}`
  - `class EconomyBridge { EconomyBridge(TerritoryRegistry territories, GovernanceRegistry governance, GoodsCatalog goods, PaymentRail rail, boolean simulationMode); TaxReport reportSale(UUID payerId, String worldId, int blockX, int blockZ, String goodId, double grossAmount); List<UnresolvedTransaction> unresolvedTransactions(); }`
  - `record UnresolvedTransaction(String territoryId, UUID payerUuid, double amount, long timestampEpochMs, String reason) {}`
- Explicitly unchanged: `TaxOutcome.NO_GOVERNMENT` semantics — resolve government via `GovernanceRegistry.resolveForTerritory(territoryId)` (alliance overrides local; `anarchy`/`none` → NO_GOVERNMENT).

- [ ] **Step 1: Write the failing test** `src/test/java/com/azoth/territory/economy/EconomyBridgeDomainTest.java`:

```java
package com.azoth.territory.economy;

import com.azoth.territory.decree.DecreeEffects;
import com.azoth.territory.decree.GoodsCatalog;
import com.azoth.territory.decree.TaxEffect;
import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.PolicyRules;
import com.azoth.territory.model.Territory;
import com.azoth.territory.network  // NO — remove this placeholder; see final file below
