# Azoth Territory Security Hardening Design

## 1. Executive summary

The review contains **28 confirmed findings**: **0 critical, 6 high, 15 medium, and 7 low**. The highest risks are:

1. The embedded admin API is enabled, bound to `0.0.0.0`, and unauthenticated when no token is configured (`common/src/main/java/com/azoth/territory/web/WebConfigLoader.java:15-20`, `WebConfig.java:106-109`). An external client can mutate territory state.
2. The build can consume attacker-controlled artifacts: the unauthenticated `/tmp` Maven repository is first in resolution order (`settings.gradle.kts:2-3`, `build.gradle.kts:77-78`), the Gradle wrapper/distribution is not integrity-verified (`gradle/wrapper/gradle-wrapper.properties:5`, `.github/workflows/ci.yml:21-24`, `.github/workflows/nightly.yml:36-54`), and `runServer` executes unverified GitHub JAR downloads (`paper/build.gradle.kts:95-105`).
3. Database connections default to plaintext or certificate-unverified transport (`common/src/main/java/com/azoth/territory/persist/DatabaseSettings.java:54-58`), exposing credentials and data to a network attacker.
4. Nightly release automation grants build steps write access and publishes nightly artifacts as the latest stable release (`.github/workflows/nightly.yml:8-9,20-22,98,107,151-152`).

The recommended **Comprehensive** approach fixes all high and medium findings and the low-cost low findings in one controlled rollout. It reduces both direct exposure and the chance that a future code fix is undermined by the CI/build supply chain. Changes should be staged behind explicit configuration migration and verified before enabling production web access or release publication.

## 2. Threat model

Assume:

- An external attacker can reach a publicly bound web listener, send arbitrary HTTP requests, submit oversized JSON, attempt token/session guesses, or observe error responses and timing.
- A compromised Maven/GitHub/JitPack artifact, Gradle plugin, GitHub Action, wrapper JAR/distribution, or downloaded Paper plugin executes arbitrary code during CI or a developer's `runServer` task.
- A malicious or careless contributor can alter workflows, Gradle properties, submodules, hooks, release metadata, or local files under `/tmp`, and can accidentally commit credentials.
- A compromised Paper plugin or server-side dependency can inspect environment variables, filesystem contents, database credentials, API tokens, and CI-provided secrets.
- Operators may run behind a reverse proxy, migrate from unauthenticated defaults, or use MySQL/PostgreSQL on a separate host.

Security objectives are least privilege, authenticated and authorized state changes, confidentiality/integrity of database traffic and credentials, reproducible verified builds, and release provenance. Availability matters for request-size limits and safe dependency resolution, but changes must not silently disable an existing operator's explicitly configured service.

## 3. Hardening approaches

### Comprehensive — recommended

Fix all six high and fifteen medium findings, plus the seven low findings that are inexpensive and materially reduce exposure: action and wrapper pinning, least-privilege CI job separation, release checksums/signatures, secret-file ignores, authenticated loopback web defaults, strict CORS/cookies, request limits, constant-time token checks, generic errors, TLS/database verification, package relocation, and per-territory command authorization.

**Tradeoff:** largest coordinated change and possible operator migration (web token/bind and database TLS). Requires bootstrapping dependency-verification metadata and testing Shadow relocation against Paper/plugin integrations. **Benefit:** closes chained attack paths rather than treating individual symptoms.

### High-severity first

Fix only high findings: nightly release classification/permissions, wrapper validation, `/tmp` repository removal, `runServer` download verification, secure database transport, and disabled/authenticated web administration.

**Tradeoff:** fastest reduction in catastrophic risk, but leaves token leakage, CORS, request exhaustion, information disclosure, weak release provenance, dependency conflicts, and authorization inconsistencies exploitable. Medium findings can combine with a high finding, so this is a temporary emergency track, not the target state.

### Repository/CI first

Harden workflows, wrapper and dependency verification, repositories, secrets, release provenance, and artifact handling before changing runtime code.

**Tradeoff:** protects builds and releases quickly and may be easiest to review, but the currently exposed unauthenticated web API, insecure database defaults, and source-level authorization gaps remain exploitable in deployed servers. It is appropriate as phase one of Comprehensive, not as the final posture.

**Recommendation:** adopt Comprehensive in two rollout waves: first repository/build and CI controls, then runtime/source controls with explicit operator migration. Keep release publication disabled until the verification gates pass.

## 4. Design for the recommended approach

### CI and release

- **`.github/workflows/ci.yml:1-30` and `.github/workflows/nightly.yml:1-54`:** set workflow/job defaults to `contents: read`; split nightly build/test from release. The build job receives only the minimum package-read credential needed for dependency resolution and never receives `contents: write` or a release PAT. The release job receives `contents: write` only for the upload step and consumes immutable artifacts from the build job.
- **`.github/workflows/ci.yml:15,17,22,27; .github/workflows/nightly.yml:26,31,37`:** pin every third-party action to a full commit SHA and retain a version comment. Pin the wrapper-validation action itself.
- **`.github/workflows/ci.yml:21-24` and `.github/workflows/nightly.yml:36-54`:** run wrapper validation before Gradle setup/build. Fail if the committed wrapper JAR or properties are modified unexpectedly.
- **`gradle/wrapper/gradle-wrapper.properties:5`:** add the official `distributionSha256Sum` for Gradle `9.6.1`; keep `validateDistributionUrl=true`. Generate the value from the trusted Gradle release and review it as a protected change.
- **`.github/workflows/nightly.yml:52-54`:** stop exporting `MINT_PACKAGES_TOKEN` as `GITHUB_TOKEN`. Use a dedicated variable (for example `MINT_PACKAGES_TOKEN`) and a separately scoped actor value; expose it only to the dependency-resolution invocation, not tests or arbitrary build tasks. Do not place release credentials in the build job.
- **`.github/workflows/nightly.yml:98,107,151-152`:** create nightly releases with `prerelease: true` and `make_latest: false`; retain a stable tag/release path separate from `nightly`. Require an explicit protected release step for stable artifacts.
- **`.github/workflows/nightly.yml:126-133`:** generate SHA-256 files for every JAR, sign artifacts with the selected organization-controlled GPG or Sigstore identity, and upload JARs, checksums, signatures, and provenance together. Verify hashes before upload and fail closed.
- **`.github/workflows/ci.yml:27-30`:** upload only reviewed report directories, set short `retention-days`, and exclude arbitrary build output. Review whether `showStandardStreams=true` in the Gradle test configuration emits secrets before retaining reports.

### Build and dependency supply chain

- **`settings.gradle.kts:2-3` and `build.gradle.kts:77-78`:** remove `/tmp/aincraft-mint/build/maven-repo` from default repositories. If local Mint development is required, make it an explicit opt-in property pointing to an access-controlled, non-predictable directory, place it after trusted repositories, and require a reviewed checksum/signature for each consumed artifact.
- **`settings.gradle.kts:7-10`, `build.gradle.kts:82-85`, `gradle.properties`:** remove `gpr.user`/`gpr.key` project-property and `-P` fallbacks and empty-string defaults. Read only a dedicated protected environment variable such as `MINT_PACKAGES_TOKEN`; fail with a clear message when a private repository is needed but the variable is absent. Document use of `~/.gradle/gradle.properties` only if it is not read by project code, and never log the value.
- **Project root (`gradle/verification-metadata.xml`):** generate and commit Gradle dependency verification metadata with checksums/signatures for all approved repositories. Prefer Maven Central/PaperMC or an internal signed repository; remove or tightly gate Sonatype snapshots and JitPack.
- **`paper/build.gradle.kts:59-65`:** relocate every bundled third-party package (including Gson, HikariCP, SLF4J, JDBC drivers, and Mint API where compatible) into an Azoth-owned namespace. Preserve service descriptors and test the shaded artifact against a clean Paper server and known plugin combinations.
- **`paper/build.gradle.kts:95-105`:** replace arbitrary `github(owner, repository, tag, asset)` inputs with an allow-list of exact coordinates and expected SHA-256 values. Verify the downloaded bytes before `run-paper` loads them; reject owner/repository/tag/asset values outside the allow-list. Prefer an internal signed artifact store.

### Repository configuration

- **`.gitignore:1-17`:** add `.env*`, `.envrc`, `*.pem`, `*.key`, `*.p12`, `*.jks`, `*.pkcs12`, `*.keystore`, `local.properties`, `gradle-user.properties`, and narrowly named `secrets*.yml` patterns. Keep the ignore rules from hiding reviewed production configuration accidentally; use a tracked redacted example if needed.
- **`.gitmodules`, `.githooks/pre-commit`, `scripts/install-git-hooks.sh`:** require submodule URLs and revisions to be reviewed, keep hooks advisory rather than the only security control, and add a secret scan/pre-commit check that detects tokens and private keys without treating hook installation as CI enforcement.
- Add CI checks for secret scanning, changed workflow review, dependency verification metadata drift, and unexpected release permissions. Treat `.github/workflows/**`, wrapper files, and dependency repository declarations as protected review surfaces.

### Source code and runtime

- **`common/src/main/java/com/azoth/territory/web/WebConfigLoader.java:15-20`, `WebConfig.java:106-109`, `TerritoryWebServer.java:183-184`:** default `web.enabled` to `false` and `web.bind` to `127.0.0.1`. Enforce the invariant that an enabled server must have a non-blank high-entropy API token; reject startup rather than silently allowing anonymous mutation. When public exposure is intentional, require explicit configuration and document reverse-proxy boundaries. Configure TLS with `TLSv1.2` or newer and an explicit approved cipher/protocol set.
- **`common/src/main/java/com/azoth/territory/persist/DatabaseSettings.java:54-58`:** default database TLS on. For MySQL, remove unconditional `allowPublicKeyRetrieval=true`; only permit it with mandatory TLS, server certificate verification, and a configured CA/trust store. For PostgreSQL, use `sslmode=verify-full` (or `verify-ca` only where hostname verification is impossible) with a configured trust store. Add startup validation for insecure production combinations.
- **`common/src/main/java/com/azoth/territory/web/SessionStore.java:48` and `TerritoryApiHandler.java:213,220`:** compare tokens using `MessageDigest.isEqual` over UTF-8 bytes, with equivalent handling for all bearer/header forms. Preserve blank-token rejection.
- **`common/src/main/java/com/azoth/territory/web/TerritoryApiHandler.java:191-193` and `HttpResponses.java:124-128`:** log full exceptions server-side with a correlation ID and return fixed error bodies (`{"error":"internal"}`); do not send `e.getMessage()` to clients. Apply the same rule to 400 responses where messages can contain storage details.
- **`common/src/main/java/com/azoth/territory/web/WebConfigLoader.java:33-34`:** remove the `changeit` fallback. Require `web.tls.password` and `web.tls.key-password` when TLS is enabled, preferably sourced from an environment-backed secret or secret manager, and fail startup if missing.
- **`common/src/main/java/com/azoth/territory/web/HttpResponses.java:22-25`:** replace wildcard CORS with an allow-list derived from configured origins, validate `Origin`, add `Vary: Origin`, and allow `Authorization`/`X-Api-Token` only for the required integration. Cover OPTIONS/preflight consistently and never reflect an unvalidated origin.
- **`common/src/main/java/com/azoth/territory/web/HttpResponses.java:136-139` and `TerritoryApiHandler.java:235-240`:** enforce a streaming request-body cap (for example 1 MiB) that works for both `Content-Length` and chunked requests before allocating the body; reject oversized bodies with 413. Validate JSON schema, string lengths, collection sizes, and parser depth before mutation.
- **`common/src/main/java/com/azoth/territory/web/HttpResponses.java:60-67`:** set `SameSite=Strict` for the admin session and set `Secure` whenever the actual listener uses HTTPS. Do not infer security solely from an untrusted proxy header; document trusted proxy handling.
- **`paper/src/main/java/com/azoth/territory/building/BuildingCommand.java:101-102,140-175,186-187` and `paper/src/main/java/com/azoth/territory/building/BuildingListener.java:92-94,150-152`:** send generic player-facing failure messages, log details server-side, and apply per-territory authorization to `list` and `info` exactly as `remove` does. Do not reveal IDs, paths, SQL, or exception text.
- **`paper/src/main/java/com/azoth/territory/command/TerritoryCommand.java:525`:** normalize `territory.admin.invasion` to the established `azoth.territory.*` namespace and audit permission declarations/configuration for compatibility. Keep explicit admin impersonation operations auditable and protected by the intended admin permission.

- **`common/src/main/java/com/azoth/territory/web/WebConfigLoader.java:15-20`, `common/src/main/java/com/azoth/territory/web/WebConfig.java:106-109`, `common/src/main/java/com/azoth/territory/web/TerritoryWebServer.java:183-184`:** default `web.enabled` to `false` and `web.bind` to `127.0.0.1`. Enforce the invariant that an enabled server must have a non-blank high-entropy API token; reject startup rather than silently allowing anonymous mutation. When public exposure is intentional, require explicit configuration and document reverse-proxy boundaries. Configure TLS with `TLSv1.2` or newer and an explicit approved cipher/protocol set.

### Verification: CI, repository, and supply chain

- Run the wrapper-validation action and `./gradlew --no-daemon help`; expected result is successful validation and a distribution checksum mismatch failure if the archive is tampered with.
- Run dependency verification in strict mode and inspect `gradle/verification-metadata.xml`; add a test artifact with a changed checksum and confirm resolution fails.
- Use a workflow lint/policy check to assert no floating action tags, no `contents: write` on build/test jobs, no release secret in build environments, and no nightly `make_latest: true` or `prerelease: false`.
- In an isolated CI-like environment, attempt to place a fake artifact under the old `/tmp` path; expected result is that it is never resolved. Confirm the explicit local repository path is inaccessible unless deliberately enabled.
- Run `:paper:runServer` with an altered downloaded JAR; expected result is hash rejection before server startup. Test an unapproved GitHub coordinate and expect a configuration failure.
- Inspect release assets and verify SHA-256 and signature/provenance independently before downloading them.
- Run secret scanning against the repository and a fixture containing `.env`, key store, and Gradle property names; confirm detection and ignore behavior are distinct (ignored files must still be scanned when present in a commit).

### Runtime and source

- Start with default configuration: expected web server is disabled. Set `web.enabled=true` without a token: expected startup failure. Set a token and default bind: expected loopback-only listener; verify an external interface cannot connect.
- Exercise authenticated and unauthenticated API calls, CORS preflight from allowed and denied origins, oversized `Content-Length`, oversized chunked bodies, deeply nested JSON, malformed JSON, and error paths. Expected results are 401/403/413/400 as applicable, generic response bodies, `Vary: Origin`, and no leaked exception text.
- Unit-test token comparison behavior for equal, unequal, null, blank, and differently sized inputs; use a static review or timing harness to confirm no direct `String.equals` remains on secrets.
- Connect to MySQL/PostgreSQL with valid certificates and verify hostname/CA validation. Present a self-signed or wrong-host certificate and confirm startup/connection failure; inspect JDBC URLs to ensure insecure flags are absent.
- Enable TLS with no password and with the old `changeit` assumption; expected result is refusal until an explicit secret is supplied. Verify negotiated protocols/ciphers with a TLS client.
- Test BuildingCommand list/info/remove for a player outside the territory and an authorized manager; verify only authorized results are returned and player messages contain no internal exception text. Test the normalized invasion permission with both old and new permission nodes.
- Build the shaded Paper JAR and run it with a clean server plus a plugin carrying conflicting Gson/Hikari/SLF4J classes; verify Azoth uses relocated classes and service loading still works.

## 6. Risks and rollback considerations

- **Web access migration:** disabling the default web server and requiring a token can surprise operators. Provide a release migration note and an explicit one-time configuration check, but do not reintroduce anonymous fallback. Roll back by restoring the prior configuration values only in a controlled maintenance window; retain authentication and bind restrictions where possible.
- **Database TLS compatibility:** certificate verification may expose missing CA files, hostname mismatches, or legacy database servers. Roll back by reverting the configuration change to a prior verified CA/trust-store pair, not by making plaintext the default. Keep an emergency documented `verify-ca` mode only for a bounded migration window.
- **Dependency verification bootstrap:** initial metadata generation can be noisy and may reject legitimate transitive updates. Review and approve metadata changes as code; rollback by reverting only the metadata commit after confirming artifact provenance, never by disabling verification globally.
- **Shadow relocation:** plugins or reflection may depend on unrelocated package names. Test the shaded runtime before release; if incompatibility appears, revert relocation for the affected library only while retaining verification and documenting the boundary.
- **CI release split:** artifact transfer or token scope changes can interrupt nightly publication. Keep the previous workflow in a reviewed rollback commit, test a draft release in a fork/staging repository, and never restore write permission to the build job merely to unblock a release.
- **RunServer hashes and allow-list:** legitimate upstream updates will require an intentional coordinate/hash review. A failed hash should stop startup; do not add a bypass flag that executes unverified bytes.
- **CORS/cookie tightening:** existing dashboards may stop working until their origin is configured. Roll back by adding the known origin to the allow-list, not by restoring `*`.
- **Request limits and generic errors:** clients may rely on large payloads or detailed messages. Version the API behavior, return stable error codes/correlation IDs, and increase limits only through reviewed configuration if a legitimate use case is demonstrated.

Rollback must preserve evidence and auditability: retain failed CI logs with sensitive values redacted, record the exact artifact hashes and workflow revision, and re-run the verification plan after every rollback or configuration exception. No rollback path should restore the original combination of anonymous public administration, unverified code execution, or plaintext credential transport.
