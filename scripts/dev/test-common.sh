#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "$0")" && pwd)/common.sh"

test "$RUNTIME_DIR" = "$PROJECT_DIR/.runtime/dev"
test "$(service_pid_file backend)" = "$PROJECT_DIR/.runtime/dev/backend.pid"
test "$(service_child_pid_file hermes)" = "$PROJECT_DIR/.runtime/dev/hermes.child.pid"
test "$(service_log_file hermes)" = "$PROJECT_DIR/.runtime/dev/hermes.log"
