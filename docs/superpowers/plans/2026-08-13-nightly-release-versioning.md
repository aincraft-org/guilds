# Nightly Release Versioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish the latest successful nightly build as a rolling GitHub release with version `26.8.13.<github_run_number>`, while removing the obsolete `v1.1.0` release and tag after replacement verification.

**Architecture:** Keep a stable base version in Gradle and allow CI to override it with a `releaseVersion` project property. A scheduled GitHub Actions workflow computes `26.8.13.${{ github.run_number }}`, builds the existing shadow artifact, and uses the GitHub Releases API to create or update one non-prerelease `nightly` release and its `nightly` tag. The release is explicitly marked latest; the old `v1.1.0` release/tag is removed only in a separately approved cleanup step after the nightly release is verified.

**Tech Stack:** Gradle Kotlin DSL, Java 26, GitHub Actions, GitHub REST Releases API, `curl`, `jq`, existing Paper Shadow JAR packaging.

## Global Constraints

- Do not mix release-workflow changes with the existing uncommitted Vault-removal changes; isolate or commit them separately.
- The published project version must be exactly `26.8.13.<github_run_number>` for nightly runs.
- The rolling release must use the stable tag `nightly` and be non-prerelease so GitHub can designate it as Latest.
- The workflow must be idempotent: reruns update the existing `nightly` release/tag instead of blindly creating duplicates.
- Release writes must use `GITHUB_TOKEN`; never print credentials.
- Delete `v1.1.0` only after the nightly artifact, release, tag, and Latest state have been read back successfully.
- Run the relevant Gradle build/tests before publishing.

---

### Task 1: Make Gradle version CI-overridable

**Files:**
- Modify: `build.gradle.kts:15-17`
- Test/verification: Gradle `properties`/artifact metadata inspection

**Interfaces:**
- Consumes: optional Gradle project property `releaseVersion`.
- Produces: `project.version` equal to `releaseVersion` when supplied, otherwise the existing development base version `26.8.13`.

- [ ] **Step 1: Change the version declaration**

Replace the fixed version with:

```kotlin
version = providers.gradleProperty("releaseVersion").orElse("26.8.13").get()
```

Keep subprojects inheriting `rootProject.version`.

- [ ] **Step 2: Verify both version paths**

Run:

```bash
./gradlew properties | grep '^version:'
./gradlew -PreleaseVersion=26.8.13.184 properties | grep '^version:'
```

Expected output:

```text
version: 26.8.13
version: 26.8.13.184
```

- [ ] **Step 3: Run the build**

Run:

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit the versioning change**

```bash
git add build.gradle.kts
git commit -m "build: make release version CI-overridable"
```

---

### Task 2: Add the rolling nightly workflow

**Files:**
- Create: `.github/workflows/nightly.yml`
- Modify: `gradle.properties` only if the repository chooses to store the base version there instead of `build.gradle.kts`

**Interfaces:**
- Consumes: `github.run_number`, `GITHUB_TOKEN`, Gradle `releaseVersion` property, and `paper/build/libs/guilds-<version>.jar`.
- Produces: a successful build, a `nightly` tag, a non-prerelease `nightly` GitHub release marked latest, and uploaded plugin/source artifacts.

- [ ] **Step 1: Define schedule and permissions**

Use a nightly UTC cron plus manual dispatch:

```yaml
name: Nightly Release

on:
  schedule:
    - cron: "17 2 * * *"
  workflow_dispatch:

permissions:
  contents: write
```

- [ ] **Step 2: Compute the release version**

Set:

```yaml
env:
  RELEASE_VERSION: 26.8.13.${{ github.run_number }}
```

Build with:

```bash
./gradlew --no-daemon -PreleaseVersion="$RELEASE_VERSION" clean test build
```

- [ ] **Step 3: Prepare artifact names**

Copy or reference the generated artifacts without altering their contents:

```text
paper/build/libs/guilds-${RELEASE_VERSION}.jar
paper/build/libs/guilds-${RELEASE_VERSION}-sources.jar
```

Fail if the expected shadow JAR is absent.

- [ ] **Step 4: Implement idempotent release update**

Use `curl --fail-with-body --silent --show-error` and `jq` against:

```text
GET    /repos/${GITHUB_REPOSITORY}/releases/tags/nightly
PATCH  /repos/${GITHUB_REPOSITORY}/releases/{id}
POST   /repos/${GITHUB_REPOSITORY}/releases
PATCH  /repos/${GITHUB_REPOSITORY}/git/refs/tags/nightly
POST   /repos/${GITHUB_REPOSITORY}/git/refs
DELETE /repos/${GITHUB_REPOSITORY}/releases/{id}/assets/{asset_id}
POST   /repos/${GITHUB_REPOSITORY}/releases/{id}/assets?name=...
```

Behavior:

1. Query the `nightly` release by tag.
2. If found, save its release ID and delete only its existing assets.
3. If absent, create the `nightly` tag at `${GITHUB_SHA}` and create a release with:
   - `tag_name: nightly`
   - `name: Nightly ${RELEASE_VERSION}`
   - `body` containing the run URL and commit SHA
   - `draft: false`
   - `prerelease: false`
   - `make_latest: true`
4. If found, update its name/body, set `target_commitish` to `${GITHUB_SHA}`, `draft: false`, `prerelease: false`, and `make_latest: true`.
5. Move the `nightly` tag to `${GITHUB_SHA}` using the refs API. If the ref does not exist, create it.
6. Upload the two artifacts to the release.
7. Read the release back and assert:
   - tag is `nightly`;
   - `prerelease == false`;
   - `draft == false`;
   - `assets` contains both expected files;
   - `make_latest`/latest API state identifies it as latest where supported.

Use a shell trap and temporary files; do not echo the token or authorization headers.

- [ ] **Step 5: Commit the workflow**

```bash
git add .github/workflows/nightly.yml
git commit -m "ci: publish rolling nightly release"
```

---

### Task 3: Inventory and remove explicitly confirmed old releases

**Files:**
- No source files.
- Remote target: GitHub repository `aincraft-org/territories`.

**Interfaces:**
- Consumes: verified `nightly` release and artifact URLs from Task 2.
- Produces: deletion of every release/tag explicitly confirmed for removal; `nightly` remains intact.

- [ ] **Step 1: Inventory all remote releases and tags**

Read the complete release and tag inventories before selecting deletion targets:

```bash
curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  -H 'Accept: application/vnd.github+json' \
  "https://api.github.com/repos/aincraft-org/territories/releases?per_page=100" \
  | jq '[.[] | {id,tag_name,name,draft,prerelease,latest}]'

curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer $GITHUB_TOKEN" \
  -H 'Accept: application/vnd.github+json' \
  "https://api.github.com/repos/aincraft-org/territories/tags?per_page=100" \
  | jq '[.[] | .name]'
```

The currently observed `v1.1.0` release is a candidate, not an assumption that it is the only existing release. Never select `nightly` for deletion.

- [ ] **Step 2: Produce an explicit deletion manifest**

List every release/tag selected for removal, including release ID, release tag, and tag ref. Require operator confirmation of that exact list before any destructive request. If a release has no corresponding tag, include the release ID separately.

- [ ] **Step 3: Read each confirmed target immediately before deletion**

For each confirmed release/tag, fetch its current release and tag metadata again. Abort if the target changed, disappeared, or is `nightly`.

- [ ] **Step 4: Delete each confirmed release and tag**

Delete the release by its verified ID, then delete its corresponding tag ref. Do not delete tags or releases outside the confirmed manifest.

- [ ] **Step 5: Read back remote state**

Expected:

- `GET /releases/tags/nightly` returns the current nightly release.
- The nightly release is non-draft, non-prerelease, and Latest.
- Every confirmed release is absent.
- Every confirmed tag is absent.
- Any unconfirmed releases/tags remain unchanged.

- [ ] **Step 6: Record remote cleanup separately**

Do not create a source commit for remote deletion. Record the inventory, confirmed deletion manifest, release URLs/tags, and deletion verification in the deployment log or final report.

### Task 4: End-to-end verification

**Files:**
- No additional files.

- [ ] **Step 1: Verify local version expansion**

```bash
./gradlew properties | grep '^version:'
./gradlew -PreleaseVersion=26.8.13.184 properties | grep '^version:'
```

- [ ] **Step 2: Verify artifact naming**

```bash
./gradlew --no-daemon -PreleaseVersion=26.8.13.184 :paper:shadowJar
test -f paper/build/libs/guilds-26.8.13.184.jar
```

- [ ] **Step 3: Verify complete tests**

```bash
./gradlew --no-daemon test
```

- [ ] **Step 4: Verify the nightly release remotely**

Read back release metadata and assets; ensure nightly is non-draft, non-prerelease, points at the current commit, and is recognized as Latest.

- [ ] **Step 5: Confirm no accidental release changes**

Check the remote list and ensure only the intended nightly replacement and approved deletion occurred.
