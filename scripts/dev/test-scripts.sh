#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$PROJECT_DIR"

bash -n scripts/start.sh scripts/stop.sh scripts/status.sh
grep -q 'npm --prefix.*run dev' scripts/start.sh
grep -q 'start_detached' scripts/start.sh
grep -q 'start_new_session=True' scripts/dev/common.sh
grep -q 'watch-java.sh.*backend' <(tr '\n' ' ' < scripts/start.sh)
grep -q 'watch-java.sh.*hermes' <(tr '\n' ' ' < scripts/start.sh)
grep -q 'release_port 80' scripts/start.sh
grep -q 'release_port 8099' scripts/start.sh
grep -q 'release_port 9090' scripts/start.sh
grep -q 'stop_orphaned_dev_processes' scripts/start.sh
grep -q 'stop_orphaned_dev_processes' scripts/stop.sh
grep -q 'tail -n 120 -F "$(service_log_file frontend)"' scripts/start.sh
grep -q "path '.*/target'" scripts/dev/watch-java.sh
grep -q -- '-prune' scripts/dev/watch-java.sh
grep -q "dependency-reduced-pom.xml" scripts/dev/watch-java.sh
grep -q 'LAST_HASH="$(source_hash)"' scripts/dev/watch-java.sh
grep -q 'return 0' scripts/dev/common.sh
grep -q 'child_pid="$(read_pid "$child_file")"' scripts/dev/common.sh
grep -q 'kill_pid "$child_pid"' scripts/dev/common.sh
grep -q 'qknow-server/target/qknow-server.jar' scripts/dev/common.sh
! grep -q 'docker compose up.*frontend' scripts/start.sh
