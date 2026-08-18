# Mint runServer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `:paper:runServer` optionally download the Mint Paper plugin from an explicitly configured GitHub release asset while preserving the existing squaremap-only path.

**Architecture:** Keep the run-paper task as the single download coordinator. Read four optional Gradle properties (`mintPluginOwner`, `mintPluginRepository`, `mintPluginTag`, `mintPluginAsset`), reject partial configuration during Gradle configuration, and conditionally call the existing `downloadPlugins.github` API. Document the command and test the build-script contract using the project’s existing source-inspection test style.

**Tech Stack:** Kotlin Gradle DSL, Gradle run-paper 3.0.2, JUnit 5, Java source-inspection tests.

## Global Constraints

- Do not invent Mint GitHub repository, tag, or asset coordinates.
- Preserve the existing squaremap `v1.3.15` / `squaremap-paper-mc26.2-1.3.15.jar` download.
- All four Mint properties absent means unchanged runServer behavior.
- Any partial Mint property group fails clearly during Gradle configuration.
- Mint download failures must propagate; never silently fall back.
- Do not change Vault, simulation, or production Mint wiring.

---

### Task 1: Add failing runServer configuration tests

**Files:**
- Modify: `paper/src/test/java/com/guilds/territory/PluginMintWiringTest.java`
- Test target: `paper/build.gradle.kts`

**Interfaces:**
- Consumes: the build-script text loaded by the existing `PluginMintWiringTest` helper.
- Produces: focused assertions defining explicit Mint property names, conditional GitHub download wiring, partial-configuration validation, and preserved squaremap coordinates.

- [ ] **Step 1: Inspect the existing test class and add four behavior tests**

Add tests with these observable assertions:

```java
@Test
void runServerUsesExplicitMintGithubCoordinates() {
    String build = read("paper/build.gradle.kts");
    assertTrue(build.contains("mintPluginOwner"));
    assertTrue(build.contains("mintPluginRepository"));
    assertTrue(build.contains("mintPluginTag"));
    assertTrue(build.contains("mintPluginAsset"));
    assertTrue(build.contains("downloadPlugins.github"));
}

@Test
void runServerRejectsPartialMintCoordinates() {
    String build = read("paper/build.gradle.kts");
    assertTrue(build.contains("Mint plugin coordinates must be provided together"));
    assertTrue(build.contains("mintPluginOwner"));
    assertTrue(build.contains("mintPluginAsset"));
}

@Test
void runServerDoesNotInventMintCoordinates() {
    String build = read("paper/build.gradle.kts");
    assertFalse(build.contains("github(\"aincraft-org\""));
    assertFalse(build.contains("mint-paper"));
}

@Test
void runServerPreservesSquaremapDownload() {
    String build = read("paper/build.gradle.kts");
    assertTrue(build.contains("github(\"jpenilla\", \"squaremap\", \"v1.3.15\", \"squaremap-paper-mc26.2-1.3.15.jar\")"));
}
```

Use the existing imports/style; do not add production code yet.

- [ ] **Step 2: Run the focused test and verify the expected RED result**

Run:

```bash
./gradlew :paper:test --tests com.guilds.territory.PluginMintWiringTest
```

Expected: the new runServer assertions fail because the current build script has no Mint plugin properties or conditional download.

---

### Task 2: Implement property-driven Mint download

**Files:**
- Modify: `paper/build.gradle.kts:80-86`

**Interfaces:**
- Consumes: the four Gradle project properties from Task 1.
- Produces: configuration-time validation and optional `downloadPlugins.github(owner, repository, tag, asset)` invocation.

- [ ] **Step 1: Add property reads immediately before `tasks.runServer`**

Use nullable Gradle property reads and avoid dynamic defaults:

```kotlin
val mintPluginOwner = providers.gradleProperty("mintPluginOwner").orNull
val mintPluginRepository = providers.gradleProperty("mintPluginRepository").orNull
val mintPluginTag = providers.gradleProperty("mintPluginTag").orNull
val mintPluginAsset = providers.gradleProperty("mintPluginAsset").orNull
val mintPluginCoordinates = listOf(
    mintPluginOwner,
    mintPluginRepository,
    mintPluginTag,
    mintPluginAsset,
)
require(mintPluginCoordinates.all { it == null } || mintPluginCoordinates.all { !it.isNullOrBlank() }) {
    "Mint plugin coordinates must be provided together: " +
        "mintPluginOwner, mintPluginRepository, mintPluginTag, mintPluginAsset"
}
```

- [ ] **Step 2: Keep squaremap and add conditional Mint download**

Inside the existing `downloadPlugins` block retain:

```kotlin
github("jpenilla", "squaremap", "v1.3.15", "squaremap-paper-mc26.2-1.3.15.jar")
```

Then add:

```kotlin
if (mintPluginOwner != null) {
    github(
        mintPluginOwner,
        mintPluginRepository!!,
        mintPluginTag!!,
        mintPluginAsset!!,
    )
}
```

The null assertions are safe because the preceding `require` guarantees all-or-none and the branch checks the first property.

- [ ] **Step 3: Run the focused test and verify GREEN**

Run:

```bash
./gradlew :paper:test --tests com.guilds.territory.PluginMintWiringTest
```

Expected: all tests in `PluginMintWiringTest` pass.

- [ ] **Step 4: Verify Gradle configuration behavior for no and partial properties**

Run the unchanged path:

```bash
./gradlew :paper:tasks --quiet
```

Expected: success.

Run a partial configuration:

```bash
./gradlew :paper:tasks --quiet -PmintPluginOwner=example
```

Expected: configuration failure containing `Mint plugin coordinates must be provided together` and the four property names.

---

### Task 3: Document Mint runServer usage

**Files:**
- Modify: `README.md:49-59`

**Interfaces:**
- Consumes: the exact Gradle property names implemented in Task 2.
- Produces: operator-facing instructions distinguishing Mint API dependency from Mint runtime plugin download.

- [ ] **Step 1: Add a Mint runServer note after the existing runServer command**

Add concise documentation:

```markdown
To load the Mint server plugin for Mint economy mode, provide its published
GitHub release coordinates explicitly. The Mint API dependency alone does not
install the server plugin:

```bash
./gradlew :paper:runServer \\
  -PmintPluginOwner=OWNER \\
  -PmintPluginRepository=REPOSITORY \\
  -PmintPluginTag=TAG \\
  -PmintPluginAsset=PLUGIN_JAR
```

All four properties are required together. The repository, tag, and asset are
intentionally not guessed because Mint release metadata may be private or
project-specific. Omitting all four keeps the normal Paper/squaremap server
path unchanged.
```
```

Place this before the existing EULA/database paragraph so the runtime prerequisite is visible with the server instructions.

- [ ] **Step 2: Run the focused test again**

Run:

```bash
./gradlew :paper:test --tests com.guilds.territory.PluginMintWiringTest
```

Expected: PASS.

---

### Task 4: Run final verification and smoke checks

**Files:**
- No new files.

- [ ] **Step 1: Run the complete test suite**

Run:

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL with all existing tests passing.

- [ ] **Step 2: Run the non-Mint server configuration smoke check**

Run:

```bash
./gradlew :paper:runServer --dry-run
```

Expected: task graph configures successfully without Mint coordinates and still includes the existing runServer task path.

- [ ] **Step 3: Check the final diff for scope**

Run:

```bash
git diff --check
```

Expected: no whitespace errors. Confirm only `paper/build.gradle.kts`, `README.md`, and the focused test changed beyond the already-written design/plan docs.
