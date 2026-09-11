#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${ACTION_GUARD_SMOKE_LOG_DIR:-$ROOT_DIR/.tmp/action-guard-smoke}"
LOG_FILE="$LOG_DIR/demo-smoke.log"
SMOKE_PROFILE="${ACTION_GUARD_SMOKE_PROFILE:-}"

mkdir -p "$LOG_DIR"

echo "演示冒烟模式：${SMOKE_PROFILE:-默认远程基础设施}"
echo "log file: $LOG_FILE"

cd "$ROOT_DIR"

if [[ "${ACTION_GUARD_SMOKE_BUILD_FIRST:-true}" == "true" ]]; then
  mvn -q -pl examples/action-guard-demo -am install -DskipTests
fi

if [[ -n "$SMOKE_PROFILE" ]]; then
  mvn -q -f examples/action-guard-demo/pom.xml spring-boot:run \
    -Dspring-boot.run.profiles="$SMOKE_PROFILE" >"$LOG_FILE" 2>&1
else
  mvn -q -f examples/action-guard-demo/pom.xml spring-boot:run >"$LOG_FILE" 2>&1
fi

if grep -q "status=SUCCESS" "$LOG_FILE"; then
  echo "demo smoke success"
  tail -n 20 "$LOG_FILE"
  exit 0
fi

echo "demo smoke failed: SUCCESS marker not found"
tail -n 80 "$LOG_FILE"
exit 1
