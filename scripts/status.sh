#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/dev/common.sh"

print_service() {
  local service="$1" port="$2"
  local pid child state="stopped"
  pid="$(read_pid "$(service_pid_file "$service")")"
  child="$(read_pid "$(service_child_pid_file "$service")")"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    state="running"
  fi
  printf '%-10s %-8s supervisor=%-8s child=%-8s port=%s\n' "$service" "$state" "${pid:--}" "${child:--}" "$port"
}

print_service frontend 80
print_service backend 8099
print_service hermes 9090

echo
docker compose -f "$PROJECT_DIR/docker-compose.yml" ps postgres redis

