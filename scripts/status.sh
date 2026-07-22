#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/dev/common.sh"

if [[ -f "$PROJECT_DIR/.env" ]]; then
  set -a
  source "$PROJECT_DIR/.env"
  set +a
fi

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

print_postgres() {
  local jdbc_url="${POSTGRESQL_URL:-jdbc:postgresql://127.0.0.1:5432/ai_agent}"
  local without_prefix="${jdbc_url#jdbc:postgresql://}"
  local without_params="${without_prefix%%\?*}"
  local host_port="${without_params%%/*}"
  local database="${without_params#*/}"
  local host="${host_port%%:*}"
  local port="5432"
  local user="${POSTGRESQL_USERNAME:-achilles}"
  local state="unknown"

  if [[ "$host_port" == *:* ]]; then
    port="${host_port##*:}"
  fi
  if [[ "$database" == "$without_params" ]]; then
    database="ai_agent"
  fi

  if command -v pg_isready >/dev/null 2>&1; then
    if PGPASSWORD="${POSTGRESQL_PASSWORD:-}" pg_isready -h "$host" -p "$port" -U "$user" -d "$database" >/dev/null 2>&1; then
      state="running"
    else
      state="stopped"
    fi
  elif command -v psql >/dev/null 2>&1; then
    if PGPASSWORD="${POSTGRESQL_PASSWORD:-}" psql -h "$host" -p "$port" -U "$user" -d "$database" -X -q -c "select 1" >/dev/null 2>&1; then
      state="running"
    else
      state="stopped"
    fi
  fi

  printf '%-10s %-8s host=%s port=%s db=%s user=%s\n' postgres "$state" "$host" "$port" "$database" "$user"
}

print_service frontend 80
print_service backend 8099
print_service hermes 9090
print_postgres

echo
docker compose -f "$PROJECT_DIR/docker-compose.yml" ps neo4j
