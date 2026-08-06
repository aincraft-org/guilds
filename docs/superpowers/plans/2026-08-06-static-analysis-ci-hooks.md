# Static Analysis CI and Git Hooks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Error Prone, SpotBugs, PMD, and Checkstyle mandatory Gradle quality gates, run them in GitHub Actions, and expose the same `check` gate through an opt-in local pre-commit hook.

**Architecture:** The existing root `build.gradle.kts` owns one analyzer policy applied to every Java subproject. Checkstyle and PMD consume checked-in XML rules under `config/`; SpotBugs and Error Prone use their standard detectors and pinned tool dependencies. GitHub Actions and the versioned `.githooks/pre-commit` both invoke Gradle lifecycle tasks rather than maintaining duplicate analyzer commands.

**Tech Stack:** Gradle 9.6.1 Kotlin DSL, Java 21 toolchain, Error Prone Gradle plugin 5.1.0, SpotBugs Gradle plugin 6.5.10, PMD 7.26.0, Checkstyle 13.9.0, GitHub Actions, POSIX shell.

## Global Constraints

- Apply the analyzers to `api`, `common`, and `paper`; the root project has no Java sources.
- Pin `net.ltgt.errorprone` plugin `5.1.0`, `error_prone_core` `2.50.0`, `com.github.spotbugs` plugin `6.5.10`, SpotBugs engine `4.10.3`, PMD `7.26.0`, and Checkstyle `13.9.0`.
- Analyzer violations fail the build; do not set `ignoreFailures` to true and do not add a blanket baseline.
- Keep the existing Java 21 toolchain and test configuration unchanged.
- Leave all pre-existing persistence and other worktree changes untouched.
- Use GitHub Actions because `origin` is `https://github.com/mintychochip/azoth-territory.git`.
- The pre-commit hook is opt-in and must not mutate Git configuration merely by cloning the repository.

---

### Task 1: Add centralized Gradle quality gates

**Files:**
- Modify: `build.gradle.kts:1-36`
- Create: `config/checkstyle/checkstyle.xml`
- Create: `config/pmd/pmd.xml`

**Interfaces:**
- Produces `:api:checkstyleMain`, `:api:pmdMain`, `:api:spotbugsMain`, and corresponding test tasks; the same task names exist under `:common` and `:paper`.
- Produces `errorprone` dependencies for every Java subproject and enables Error Prone on source-set Java compilation.
- Produces XML and HTML reports under each module's existing `build/reports` directory.

- [ ] **Step 1: Extend the root plugin declarations**

Add this `plugins` block before the existing root imports and leave the existing `Test` import and project metadata intact:

```kotlin
plugins {
    id("net.ltgt.errorprone") version "5.1.0" apply false
    id("com.github.spotbugs") version "6.5.10" apply false
}
```

- [ ] **Step 2: Apply the four analyzers to each Java subproject**

At the start of the existing `subprojects` block, immediately after `apply(plugin = "java")`, add:

```kotlin
    apply(plugin = "checkstyle")
    apply(plugin = "pmd")
    apply(plugin = "net.ltgt.errorprone")
    apply(plugin = "com.github.spotbugs")
```

- [ ] **Step 3: Add Error Prone dependency and compiler policy**

Add these imports at the top of `build.gradle.kts`:

```kotlin
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.tasks.compile.JavaCompile
```

Inside `subprojects`, after the Java toolchain configuration, add:

```kotlin
    dependencies {
        add("errorprone", "com.google.errorprone:error_prone_core:2.50.0")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.errorprone {
            disableWarningsInGeneratedCode = true
        }
    }
```

- [ ] **Step 4: Configure Checkstyle and PMD with checked-in rules**

Inside `subprojects`, add:

```kotlin
    extensions.configure<CheckstyleExtension> {
        toolVersion = "13.9.0"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        isIgnoreFailures = false
    }

    extensions.configure<PmdExtension> {
        toolVersion = "7.26.0"
        ruleSetFiles = rootProject.files("config/pmd/pmd.xml")
        ruleSets = emptyList()
        isConsoleOutput = true
        isIgnoreFailures = false
    }

    tasks.withType<Checkstyle>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.withType<Pmd>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
```

- [ ] **Step 5: Configure SpotBugs with pinned engine and reports**

Inside `subprojects`, add:

```kotlin
    extensions.configure<SpotBugsExtension> {
        toolVersion.set("4.10.3")
        ignoreFailures.set(false)
        effort.set(Effort.MAX)
        reportLevel.set(Confidence.DEFAULT)
        showStackTraces.set(true)
    }

    tasks.withType<SpotBugsTask>().configureEach {
        reports {
            create("xml") {
                required.set(true)
            }
            create("html") {
                required.set(true)
            }
        }
    }
```

If the pinned plugin exposes one of these `Property` members as Kotlin assignment syntax rather than `.set`, use the equivalent property assignment from the plugin's documented Kotlin DSL without changing the values or failure behavior.

- [ ] **Step 6: Write the Checkstyle policy**

Create `config/checkstyle/checkstyle.xml` with this complete configuration:

```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
    "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
    "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
    <property name="charset" value="UTF-8"/>
    <module name="TreeWalker">
        <module name="AvoidStarImport"/>
        <module name="RedundantImport"/>
        <module name="UnusedImports"/>
        <module name="NeedBraces"/>
    </module>
</module>
```

- [ ] **Step 7: Write the PMD policy**

Create `config/pmd/pmd.xml` with this complete configuration:

```xml
<?xml version="1.0"?>
<ruleset name="Azoth PMD rules"
         xmlns="http://pmd.sourceforge.net/ruleset/2.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://pmd.sourceforge.net/ruleset/2.0.0 https://pmd.github.io/pmd-7.0.0/ruleset_xml_schema.xsd">
    <description>Correctness rules enforced for all Azoth Java modules.</description>
    <rule ref="category/java/errorprone.xml/EmptyCatchBlock"/>
    <rule ref="category/java/errorprone.xml/EmptyFinallyBlock"/>
    <rule ref="category/java/errorprone.xml/EmptyIfStmt"/>
    <rule ref="category/java/errorprone.xml/EmptyWhileStmt"/>
    <rule ref="category/java/errorprone.xml/EqualsNull"/>
    <rule ref="category/java/errorprone.xml/MissingBreakInSwitch"/>
    <rule ref="category/java/errorprone.xml/OverrideBothEqualsAndHashcode"/>
    <rule ref="category/java/errorprone.xml/UnnecessaryBooleanAssertion"/>
    <rule ref="category/java/errorprone.xml/UnnecessaryCaseChange"/>
    <rule ref="category/java/errorprone.xml/UnnecessaryReturn"/>
    <rule ref="category/java/errorprone.xml/UseEqualsToCompareStrings"/>
</ruleset>
```

- [ ] **Step 8: Run the analyzer task graph before changing source code**

Run:

```bash
./gradlew --no-daemon :api:tasks --all :common:tasks --all :paper:tasks --quiet
./gradlew --no-daemon check --continue
```

Expected: analyzer task names appear under all three modules; `check --continue` executes all analyzer tasks and prints any concrete violations. Do not weaken the rules to hide a report. Fix only reported source defects or a demonstrable false positive, keeping any source fix in the same quality-gates commit.

- [ ] **Step 9: Verify the quality-gate unit**

Run:

```bash
./gradlew --no-daemon check
```

Expected: `BUILD SUCCESSFUL`, with no analyzer violations and reports under `api/build/reports`, `common/build/reports`, and `paper/build/reports`.

- [ ] **Step 10: Commit the quality-gate unit**

Review the staged paths and commit only the root Gradle wiring, analyzer configs, and source fixes required to make those gates pass:

```bash
git add build.gradle.kts config/checkstyle/checkstyle.xml config/pmd/pmd.xml
# If analyzer output identified source fixes, stage each reported source path explicitly.
git diff --cached --check
git commit -m "build: enforce Java static analysis"
```

No unrelated worktree files are staged.

---

### Task 2: Add GitHub Actions enforcement

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Pull requests and pushes invoke the same Gradle `build` graph as local verification.
- Failed jobs retain analyzer reports for inspection.

- [ ] **Step 1: Create the workflow**

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
  pull_request:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Check out source
        uses: actions/checkout@v4
      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
      - name: Build and verify
        run: ./gradlew --no-daemon build
      - name: Upload analysis reports on failure
        if: ${{ failure() }}
        uses: actions/upload-artifact@v4
        with:
          name: quality-reports
          path: "**/build/reports/**"
          if-no-files-found: ignore
```

- [ ] **Step 2: Validate workflow syntax and paths**

Run:

```bash
bash -n gradlew
python3 - <<'PY'
import pathlib
path = pathlib.Path(".github/workflows/ci.yml")
text = path.read_text()
assert "./gradlew --no-daemon build" in text
assert "actions/setup-java@v4" in text
assert "actions/upload-artifact@v4" in text
PY
```

Expected: both commands exit successfully. The Python check validates the workflow's required build, Java, and report-upload steps without introducing a project test dependency.

- [ ] **Step 3: Commit the CI unit**

```bash
git add .github/workflows/ci.yml
git diff --cached --check
git commit -m "ci: run Gradle quality gates"
```

---

### Task 3: Add opt-in local pre-commit enforcement

**Files:**
- Create: `.githooks/pre-commit`
- Create: `scripts/install-git-hooks.sh`
- Modify: `README.md` after the existing Build section

**Interfaces:**
- `scripts/install-git-hooks.sh` configures the current repository's `core.hooksPath` to `.githooks`.
- `.githooks/pre-commit` runs `./gradlew --no-daemon check` from the repository root and returns Gradle's exit code.

- [ ] **Step 1: Create the pre-commit hook**

Create `.githooks/pre-commit`:

```bash
#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

if [[ ! -x ./gradlew ]]; then
    printf '%s\n' 'pre-commit: ./gradlew is missing or not executable' >&2
    exit 1
fi

exec ./gradlew --no-daemon check
```

- [ ] **Step 2: Create the installer**

Create `scripts/install-git-hooks.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
git -C "$repo_root" config --local core.hooksPath .githooks
printf 'Installed repository hooks at %s/.githooks\n' "$repo_root"
```

Make both scripts executable:

```bash
chmod +x .githooks/pre-commit scripts/install-git-hooks.sh
```

- [ ] **Step 3: Document installation and removal**

Add this section after the README's Build commands:

```markdown
### Local pre-commit checks

Install the repository-managed pre-commit hook once per clone:

```bash
./scripts/install-git-hooks.sh
```

The hook runs `./gradlew --no-daemon check`, including Error Prone, SpotBugs,
PMD, Checkstyle, and the test suite. To remove the repository-local hook
configuration:

```bash
git config --local --unset core.hooksPath
```
```

- [ ] **Step 4: Validate hook scripts without changing the active repository config**

Run:

```bash
bash -n .githooks/pre-commit scripts/install-git-hooks.sh
```

Expected: both scripts pass shell syntax validation. Do not execute the installer in the working repository; its documented behavior intentionally changes local Git configuration.

- [ ] **Step 5: Commit the hook unit**

```bash
git add .githooks/pre-commit scripts/install-git-hooks.sh README.md
git diff --cached --check
git commit -m "chore: add local quality pre-commit hook"
```

---

### Task 4: Run end-to-end verification

**Files:**
- Verify: `build.gradle.kts`, `config/checkstyle/checkstyle.xml`, `config/pmd/pmd.xml`, `.github/workflows/ci.yml`, `.githooks/pre-commit`, `scripts/install-git-hooks.sh`, `README.md`

- [ ] **Step 1: Verify all lifecycle tasks and reports**

Run:

```bash
./gradlew --no-daemon check
./gradlew --no-daemon build
```

Expected: both commands finish with `BUILD SUCCESSFUL`; `build` creates the existing Paper shadow artifact and all configured analyzer reports exist beneath the module build directories.

- [ ] **Step 2: Test hook rejection in a disposable clone**

Create a temporary clone from the committed implementation, install the hook there, and add a temporary star import to one Java file:

```bash
tmp_dir="$(mktemp -d)"
git clone --no-local . "$tmp_dir/repo"
git -C "$tmp_dir/repo" config user.email quality-check@example.invalid
git -C "$tmp_dir/repo" config user.name quality-check
git -C "$tmp_dir/repo" config core.hooksPath .githooks
cd "$tmp_dir/repo"
python3 - <<'PY'
from pathlib import Path

path = Path("api/src/main/java/com/azoth/territory/model/Boundary.java")
text = path.read_text()
package_end = text.index("\n", text.index("package "))
path.write_text(text[:package_end + 1] + "import java.util.*;\n" + text[package_end + 1:])
PY
git add api/src/main/java/com/azoth/territory/model/Boundary.java
set +e
./.githooks/pre-commit
hook_status=$?
set -e
test "$hook_status" -ne 0
rm -rf "$tmp_dir"
cd -
```

Expected: the hook returns nonzero because Checkstyle rejects `AvoidStarImport`; the main worktree is unchanged.

- [ ] **Step 3: Inspect final scope**

Run:

```bash
git status --short
```

Expected: only the user's pre-existing unrelated worktree changes remain, if any; no generated reports, temporary files, or unrelated source edits are present.

- [ ] **Step 4: Mark the implementation complete**

Record the exact successful Gradle commands and report locations in the final response. Do not claim CI execution unless a GitHub Actions run is directly observed; local workflow syntax and Gradle behavior are the verified scope.
