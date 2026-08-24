#!/usr/bin/env bash
# Build squaremap Paper + Rust sidecar artifacts for guilds :guilds-test:runServer.
# Applies scripts/patches/squaremap/*.patch on a pinned squaremap commit, builds the
# patched sidecar from source, packages the Paper jar, and installs both under guilds-test/run/.
#
# Prerequisites:
#   - git checkout of squaremap (local clone or SQUAREMAP_ROOT)
#   - bun on PATH (frontend build)
#   - rust/cargo (sidecar build)
#   - java/gradle (Paper jar)
#
# Gradle packaging still expects five rust/backend/* trees for squaremap-backends.json.
# GitHub releases do not publish sidecar assets yet, so non-host targets are copied from
# SQUAREMAP_BACKEND_SEED (default: ../squaremap/rust/backend when present). Only the
# host triple (default x86_64-unknown-linux-gnu) is rebuilt from patched source.
#
# Install is atomic: patch+verify in a staging dir, then stop Paper/sidecar and install both together.
# Stop the supervised guilds-server process first (hub stop name=guilds-server), or set
# SQUAREMAP_STOP_SERVER=1 to SIGTERM the repo-local runServer launcher and clean up
# any orphaned sidecar/session.lock holders by PID (never broad pkill patterns).
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
guilds_run_dir="${repo_root}/guilds-test/run"
session_lock="${guilds_run_dir}/world/session.lock"
supervised_process_name="${GUILDS_SUPERVISOR_NAME:-guilds-server}"
squaremap_root="${SQUAREMAP_ROOT:-${repo_root}/../squaremap}"
# Empty: build the current ../squaremap checkout (the local fork).
# Set SQUAREMAP_COMMIT to a sha to checkout that commit and apply scripts/patches/squaremap.
squaremap_commit="${SQUAREMAP_COMMIT:-}"
target_triple="${SQUAREMAP_TARGET:-x86_64-unknown-linux-gnu}"
backend_seed="${SQUAREMAP_BACKEND_SEED:-}"
squaremap_stop_server="${SQUAREMAP_STOP_SERVER:-0}"

guilds_jar="${repo_root}/guilds-test/run/squaremap-paper-rust-local.jar"
guilds_sidecar="${repo_root}/guilds-test/run/squaremap-server"
provenance="${repo_root}/guilds-test/run/squaremap-local-provenance.json"
patch_dir="${repo_root}/scripts/patches/squaremap"

other_targets=(
  aarch64-unknown-linux-gnu
  x86_64-pc-windows-msvc
  x86_64-apple-darwin
  aarch64-apple-darwin
)

sha256_file() {
  sha256sum "$1" | awk '{print $1}'
}

find_running_sidecar_pids() {
  pgrep -f "${guilds_sidecar} bridge" 2>/dev/null || true
}

find_gradle_runserver_pids() {
  pgrep -f "${repo_root}/gradle/wrapper/gradle-wrapper.jar --no-daemon :guilds-test:runServer" 2>/dev/null || true
}

find_session_lock_pids() {
  if [[ ! -f "${session_lock}" ]]; then
    return 0
  fi
  fuser "${session_lock}" 2>/dev/null | tr ' ' '\n' | sed '/^$/d' || true
}

list_running_pids() {
  {
    find_running_sidecar_pids
    find_gradle_runserver_pids
    find_session_lock_pids
  } | sed '/^$/d' | sort -u
}

is_squaremap_running() {
  [[ -n "$(list_running_pids)" ]]
}

kill_pids_gracefully() {
  local pid
  for pid in "$@"; do
    [[ -n "${pid}" && "${pid}" =~ ^[0-9]+$ ]] || continue
    kill -TERM "${pid}" 2>/dev/null || true
  done
}

print_stop_guidance() {
  echo "Stop the supervised process '${supervised_process_name}' through your process supervisor" >&2
  echo "(for example: hub stop name=${supervised_process_name}), then verify nothing remains:" >&2
  echo "  pgrep -af '${guilds_sidecar} bridge|${repo_root}/gradle/wrapper/gradle-wrapper.jar --no-daemon :guilds-test:runServer'" >&2
  echo "If world/session.lock is still held by an orphaned JVM, inspect the PID with:" >&2
  echo "  fuser ${session_lock}" >&2
  echo "and stop that PID only (kill <pid>), not a broad pkill pattern." >&2
}

stop_squaremap_server() {
  echo "==> stopping ${supervised_process_name} / :guilds-test:runServer for atomic install..."

  local gradle_pids
  gradle_pids="$(find_gradle_runserver_pids)"
  if [[ -n "${gradle_pids}" ]]; then
    echo "==> sending SIGTERM to runServer launcher PID(s): ${gradle_pids//$'\n'/ }"
    kill_pids_gracefully ${gradle_pids//$'\n'/ }
  fi

  local attempt
  for attempt in $(seq 1 30); do
    if ! is_squaremap_running; then
      return 0
    fi
    sleep 1
  done

  local orphan_pids
  orphan_pids="$(list_running_pids | tr '\n' ' ')"
  if [[ -n "${orphan_pids// /}" ]]; then
    echo "==> cleaning up orphaned sidecar/session.lock PID(s): ${orphan_pids}" >&2
    kill_pids_gracefully ${orphan_pids}
    sleep 2
    for attempt in $(seq 1 10); do
      if ! is_squaremap_running; then
        return 0
      fi
      sleep 1
    done
  fi

  echo "ERROR: timed out waiting for squaremap/paper to stop." >&2
  print_stop_guidance
  pgrep -af "${guilds_sidecar} bridge|${repo_root}/gradle/wrapper/gradle-wrapper.jar --no-daemon :guilds-test:runServer" >&2 || true
  exit 1
}

ensure_not_running_or_stop() {
  if ! is_squaremap_running; then
    return 0
  fi
  if [[ "${squaremap_stop_server}" == "1" ]]; then
    stop_squaremap_server
    return 0
  fi
  echo "ERROR: squaremap sidecar or Paper server is still running." >&2
  echo "Install is not atomic while the sidecar binary is in use." >&2
  print_stop_guidance
  echo "Or rerun with SQUAREMAP_STOP_SERVER=1 to stop the repo-local runServer launcher" >&2
  echo "and clean up orphaned sidecar/session.lock holders by PID:" >&2
  echo "  SQUAREMAP_STOP_SERVER=1 ./scripts/build-squaremap-local.sh" >&2
  pgrep -af "${guilds_sidecar} bridge|${repo_root}/gradle/wrapper/gradle-wrapper.jar --no-daemon :guilds-test:runServer" >&2 || true
  exit 1
}

verify_jar_manifest_matches_sidecar() {
  local jar="$1"
  local sidecar="$2"

  python3 - <<PY
import hashlib
import json
import os
import sys
import zipfile

jar = "${jar}"
sidecar = "${sidecar}"
triple = "${target_triple}"

with open(sidecar, "rb") as handle:
    sidecar_sha = hashlib.sha256(handle.read()).hexdigest()
sidecar_size = os.path.getsize(sidecar)

with zipfile.ZipFile(jar) as archive:
    manifest = json.loads(archive.read("squaremap-backends.json"))

entry = manifest["targets"][triple]
if entry["sha256"] != sidecar_sha:
    print("ERROR: jar manifest sha256 does not match sidecar", file=sys.stderr)
    print(f"  manifest: {entry['sha256']}", file=sys.stderr)
    print(f"  sidecar:  {sidecar_sha}", file=sys.stderr)
    sys.exit(1)
if str(entry["length"]) != str(sidecar_size):
    print("ERROR: jar manifest length does not match sidecar", file=sys.stderr)
    print(f"  manifest: {entry['length']}", file=sys.stderr)
    print(f"  sidecar:  {sidecar_size}", file=sys.stderr)
    sys.exit(1)
PY
}

verify_staged_bundle() {
  local staged_jar="$1"
  local staged_sidecar="$2"
  local built_sidecar="$3"
  local staged_sidecar_sha expected_sidecar_sha

  staged_sidecar_sha="$(sha256_file "${staged_sidecar}")"
  expected_sidecar_sha="$(sha256_file "${built_sidecar}")"
  if [[ "${staged_sidecar_sha}" != "${expected_sidecar_sha}" ]]; then
    echo "ERROR: staged sidecar hash does not match the built binary." >&2
    echo "  built:  ${expected_sidecar_sha}" >&2
    echo "  staged: ${staged_sidecar_sha}" >&2
    exit 1
  fi

  verify_jar_manifest_matches_sidecar "${staged_jar}" "${staged_sidecar}"
}

install_verified_bundle() {
  local staged_jar="$1"
  local staged_sidecar="$2"
  local expected_jar_sha expected_sidecar_sha
  local jar_install="${guilds_jar}.install.$$"
  local sidecar_install="${guilds_sidecar}.install.$$"

  expected_jar_sha="$(sha256_file "${staged_jar}")"
  expected_sidecar_sha="$(sha256_file "${staged_sidecar}")"

  ensure_not_running_or_stop
  rm -f "${guilds_sidecar}.new" "${guilds_jar}.install."* "${guilds_sidecar}.install."* 2>/dev/null || true

  install -d "$(dirname "${guilds_jar}")"
  install -m 644 "${staged_jar}" "${jar_install}"
  install -m 755 "${staged_sidecar}" "${sidecar_install}"
  mv -f "${jar_install}" "${guilds_jar}"
  mv -f "${sidecar_install}" "${guilds_sidecar}"

  if [[ "$(sha256_file "${guilds_jar}")" != "${expected_jar_sha}" ]]; then
    echo "ERROR: installed jar hash does not match the verified staged jar." >&2
    exit 1
  fi
  if [[ "$(sha256_file "${guilds_sidecar}")" != "${expected_sidecar_sha}" ]]; then
    echo "ERROR: installed sidecar hash does not match the verified staged sidecar." >&2
    exit 1
  fi

  verify_jar_manifest_matches_sidecar "${guilds_jar}" "${guilds_sidecar}"
}

if [[ ! -d "${squaremap_root}/.git" ]]; then
  echo "squaremap git checkout not found: ${squaremap_root}" >&2
  echo "Clone jpenilla/squaremap beside guilds or set SQUAREMAP_ROOT." >&2
  exit 1
fi

if [[ -n "${squaremap_commit}" ]] && { [[ ! -d "${patch_dir}" ]] || ! compgen -G "${patch_dir}/*.patch" > /dev/null; }; then
  echo "Missing squaremap patches under ${patch_dir} (required when SQUAREMAP_COMMIT is set)" >&2
  exit 1
fi

if ! command -v bun >/dev/null 2>&1; then
  echo "bun is required for the squaremap web frontend build (web/)" >&2
  exit 1
fi

if [[ -z "${backend_seed}" && -d "${repo_root}/../squaremap/rust/backend" ]]; then
  backend_seed="${repo_root}/../squaremap/rust/backend"
fi

resolve_backend_seed() {
  local target="$1"
  local seed_root="$2"
  local dir="${seed_root}/rust-backend-${target}"
  if [[ "${target}" == *windows* ]]; then
    [[ -f "${dir}/squaremap-server-${target}.exe" ]]
  else
    [[ -f "${dir}/squaremap-server-${target}" ]]
  fi
}

stage_backend_layout() {
  local host_sidecar="$1"
  local backend_root="${squaremap_root}/rust/backend"
  local host_dir="${backend_root}/rust-backend-${target_triple}"
  local host_name="squaremap-server-${target_triple}"
  if [[ "${target_triple}" == *windows* ]]; then
    host_name="${host_name}.exe"
  fi

  mkdir -p "${host_dir}"
  install -m 755 "${host_sidecar}" "${host_dir}/${host_name}"
  echo "==> staged patched sidecar at rust/backend/rust-backend-${target_triple}/${host_name}"

  if [[ -z "${backend_seed}" ]]; then
    echo "SQUAREMAP_BACKEND_SEED is unset and ${repo_root}/../squaremap/rust/backend is missing." >&2
    echo "Gradle needs all five backend targets to package squaremap-backends.json." >&2
    echo "Point SQUAREMAP_BACKEND_SEED at a tree containing rust-backend-<triple>/ directories." >&2
    exit 1
  fi

  for target in "${other_targets[@]}"; do
    if [[ "${target}" == "${target_triple}" ]]; then
      continue
    fi
    if ! resolve_backend_seed "${target}" "${backend_seed}"; then
      echo "Missing seeded backend for ${target} under ${backend_seed}" >&2
      exit 1
    fi
    local dest="${backend_root}/rust-backend-${target}"
    local src="${backend_seed}/rust-backend-${target}"
    mkdir -p "${dest}"
    if [[ "$(cd "${src}" && pwd -P)" == "$(cd "${dest}" && pwd -P)" ]]; then
      echo "==> rust/backend/rust-backend-${target} already present (seed is this tree)"
      continue
    fi
    cp -a "${src}/." "${dest}/"
    echo "==> seeded rust/backend/rust-backend-${target} from ${backend_seed}"
  done
}

echo "==> squaremap root: ${squaremap_root}"
echo "==> base commit:    ${squaremap_commit:-<current checkout>}"
echo "==> host triple:   ${target_triple}"
echo "==> backend seed:  ${backend_seed:-<none>}"

(
  cd "${squaremap_root}"
  if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "squaremap working tree is dirty; commit/stash changes before building." >&2
    git status --short >&2
    exit 1
  fi
  if [[ -n "${squaremap_commit}" ]]; then
    git fetch --quiet origin "${squaremap_commit}" 2>/dev/null || true
    git checkout --quiet "${squaremap_commit}"
    for patch in "${patch_dir}"/*.patch; do
      echo "==> applying $(basename "${patch}")"
      git apply --check "${patch}"
      git apply "${patch}"
    done
  else
    echo "==> using squaremap $(git rev-parse --short HEAD) as-is (no checkout, no patches)"
  fi

  echo "==> running Rust unit tests for live snapshot registry bind"
  (
    cd rust
    cargo test -p squaremap-server --lib sibling_in_flight_snapshot_uses_registry_cached_by_the_other_request
    cargo test -p squaremap-server --lib cached_registry_is_bound_to_later_request
    cargo build --release -p squaremap-server
  )

  echo "==> building web frontend"
  (
    cd web
    bun install
    bun run build
  )

  stage_backend_layout "${squaremap_root}/rust/target/release/squaremap-server"

  echo "==> building squaremap Paper jar"
  ./gradlew --no-daemon :squaremap-paper:clean :squaremap-paper:shadowJar
)

jar_path="$(ls -1 "${squaremap_root}"/paper/build/libs/squaremap-paper-mc26.2-*.jar | head -1)"
sidecar_path="${squaremap_root}/rust/target/release/squaremap-server"
staging_dir="$(mktemp -d "${repo_root}/guilds-test/run/.squaremap-install.XXXXXX")"
staged_jar="${staging_dir}/squaremap-paper-rust-local.jar"
staged_sidecar="${staging_dir}/squaremap-server"
cleanup_staging() {
  rm -rf "${staging_dir}"
}
trap cleanup_staging EXIT

echo "==> staging and verifying artifacts before install"
cp "${jar_path}" "${staged_jar}"
install -m 755 "${sidecar_path}" "${staged_sidecar}"

python3 "${repo_root}/scripts/sync-squaremap-backend-manifest.py" \
  "${staged_jar}" \
  "${staged_sidecar}"

verify_staged_bundle "${staged_jar}" "${staged_sidecar}" "${sidecar_path}"

echo "==> installing verified jar + sidecar atomically"
install_verified_bundle "${staged_jar}" "${staged_sidecar}"
cleanup_staging
trap - EXIT

jar_sha256="$(sha256_file "${guilds_jar}")"
sidecar_sha256="$(sha256_file "${guilds_sidecar}")"
sidecar_size="$(stat -c '%s' "${guilds_sidecar}")"
built_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
built_commit="$(git -C "${squaremap_root}" rev-parse HEAD)"
applied_patches="[]"
if [[ -n "${squaremap_commit}" ]]; then
  applied_patches="$(python3 - <<PY
import json, pathlib
print(json.dumps(sorted(p.name for p in pathlib.Path("${patch_dir}").glob("*.patch"))))
PY
)"
fi

python3 - <<PY
import json, pathlib
payload = {
    "built_at": "${built_at}",
    "squaremap_root": "${squaremap_root}",
    "squaremap_commit": "${built_commit}",
    "squaremap_commit_requested": "${squaremap_commit}",
    "target_triple": "${target_triple}",
    "backend_seed": "${backend_seed}",
    "patches": json.loads("""${applied_patches}"""),
    "deployment_verified": True,
    "artifacts": {
        "jar": {
            "path": "${guilds_jar}",
            "sha256": "${jar_sha256}",
        },
        "sidecar": {
            "path": "${guilds_sidecar}",
            "sha256": "${sidecar_sha256}",
            "length": int("${sidecar_size}"),
        },
    },
    "notes": [
        "Built from the local squaremap checkout; live dirty renders bind sibling snapshots to a cached registry.",
        "Sidecar HTTP serves the SPA from SQUAREMAP_WEB_ROOT and tiles from the output root.",
        "Live map tiles are written by the Rust renderer into rust-output/tiles/; do not copy web/tiles there for verification.",
        "Non-host backend triples are copied from backend_seed; only the host triple is rebuilt from source.",
        "Install requires a stopped sidecar; stop guilds-server via the supervisor first, or set SQUAREMAP_STOP_SERVER=1 for PID-targeted shutdown.",
    ],
}
pathlib.Path("${provenance}").write_text(json.dumps(payload, indent=2) + "\\n")
print(json.dumps(payload, indent=2))
PY

echo "==> installed and verified ${guilds_jar}"
echo "==> installed and verified ${guilds_sidecar}"
echo "==> wrote ${provenance}"
if [[ "${squaremap_stop_server}" == "1" ]]; then
  echo "==> server was stopped for install; restart the supervised process (hub start name=${supervised_process_name})"
  echo "    or run: ./gradlew :guilds-test:runServer"
fi
