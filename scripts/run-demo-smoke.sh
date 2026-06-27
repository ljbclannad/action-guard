#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${ACTION_GUARD_SMOKE_LOG_DIR:-$ROOT_DIR/.tmp/action-guard-smoke}"
LOG_FILE="$LOG_DIR/demo-smoke.log"

mkdir -p "$LOG_DIR"

echo "demo smoke mode: H2 file + RabbitMQ"
echo "log file: $LOG_FILE"

cd "$ROOT_DIR"

if [[ "${ACTION_GUARD_SMOKE_BUILD_FIRST:-true}" == "true" ]]; then
  mvn -q -pl examples/message-guard-demo -am compile
fi

mvn -q -f examples/message-guard-demo/pom.xml spring-boot:run >"$LOG_FILE" 2>&1

if grep -q "status=SUCCESS" "$LOG_FILE"; then
  echo "demo smoke success"
  tail -n 20 "$LOG_FILE"
  exit 0
fi

echo "demo smoke failed: SUCCESS marker not found"
tail -n 80 "$LOG_FILE"
exit 1
