#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/dev/common.sh"

stop_service frontend
stop_service hermes
stop_service backend
stop_orphaned_dev_processes
