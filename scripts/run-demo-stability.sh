#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO_POM="$ROOT_DIR/examples/message-guard-demo/pom.xml"
LOG_ROOT="${ACTION_GUARD_STABILITY_LOG_DIR:-$ROOT_DIR/.tmp/action-guard-stability}"
RUNS="${ACTION_GUARD_STABILITY_RUNS:-10}"
PARALLELISM="${ACTION_GUARD_STABILITY_PARALLELISM:-3}"
BUILD_FIRST="${ACTION_GUARD_STABILITY_BUILD_FIRST:-true}"

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn is required but not found in PATH" >&2
  exit 1
fi

if ! [[ "$RUNS" =~ ^[0-9]+$ ]] || ! [[ "$PARALLELISM" =~ ^[0-9]+$ ]] || [ "$RUNS" -le 0 ] || [ "$PARALLELISM" -le 0 ]; then
  echo "ACTION_GUARD_STABILITY_RUNS and ACTION_GUARD_STABILITY_PARALLELISM must be positive integers" >&2
  exit 1
fi

mkdir -p "$LOG_ROOT"
RUN_DIR="$LOG_ROOT/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RUN_DIR"

echo "stability run dir: $RUN_DIR"
echo "runs=$RUNS parallelism=$PARALLELISM"
echo "demo database mode: H2 file (override with DEMO_H2_PATH if needed)"

if [ "$BUILD_FIRST" = "true" ]; then
  echo "building demo dependencies once before burst run..."
  mvn -q -pl examples/message-guard-demo -am -DskipTests compile -f "$ROOT_DIR/pom.xml"
fi

run_one() {
  local index="$1"
  local log_file="$RUN_DIR/run-${index}.log"
  (
    cd "$ROOT_DIR"
    mvn -q -f "$DEMO_POM" spring-boot:run >"$log_file" 2>&1
  )
}

active_jobs=0
for index in $(seq 1 "$RUNS"); do
  run_one "$index" &
  active_jobs=$((active_jobs + 1))
  if [ "$active_jobs" -ge "$PARALLELISM" ]; then
    wait -n || true
    active_jobs=$((active_jobs - 1))
  fi
done
wait || true

success_count=0
failure_count=0

for index in $(seq 1 "$RUNS"); do
  log_file="$RUN_DIR/run-${index}.log"
  if grep -q '^status=SUCCESS$' "$log_file"; then
    success_count=$((success_count + 1))
  else
    failure_count=$((failure_count + 1))
    echo "run-${index} failed, see $log_file"
  fi
done

echo "success_count=$success_count"
echo "failure_count=$failure_count"
echo "logs=$RUN_DIR"

if [ "$failure_count" -gt 0 ]; then
  exit 1
fi
