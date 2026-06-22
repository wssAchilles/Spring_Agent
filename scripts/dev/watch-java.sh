#!/usr/bin/env bash
set -u

if [[ $# -lt 4 ]]; then
  echo "用法: $0 <service> <watch-root> <maven-project-list> <jar-path> [KEY=VALUE ...]" >&2
  exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/common.sh"

SERVICE="$1"
WATCH_ROOT="$2"
PROJECT_LIST="$3"
JAR_PATH="$4"
shift 4
ENV_ARGS=("$@")
CHILD_PID=""
LAST_HASH=""

source_hash() {
  local find_args=(
    "$WATCH_ROOT"
    \( -path '*/target' -o -path '*/target/*' -o -path '*/.git' -o -path '*/.git/*' -o -path '*/.runtime' -o -path '*/.runtime/*' \)
    -prune
    -o
    -type f
  )
  if [[ "$SERVICE" == "backend" ]]; then
    find_args+=( ! -path '*/qknow-hermes/*' )
  fi
  find "${find_args[@]}" \
    \( -name '*.java' -o -name '*.xml' -o -name '*.yml' -o -name '*.yaml' -o -name '*.properties' -o -name 'pom.xml' \) \
    ! -name 'dependency-reduced-pom.xml' \
    -exec stat -f '%m %N' {} + 2>/dev/null | sort | shasum | awk '{print $1}'
}

stop_child() {
  local i
  if [[ -z "$CHILD_PID" ]] || ! kill -0 "$CHILD_PID" 2>/dev/null; then
    return 0
  fi
  kill "$CHILD_PID" 2>/dev/null || true
  for ((i = 1; i <= 30; i++)); do
    kill -0 "$CHILD_PID" 2>/dev/null || break
    sleep 0.5
  done
  kill -0 "$CHILD_PID" 2>/dev/null && kill -9 "$CHILD_PID" 2>/dev/null || true
}

cleanup() {
  stop_child
  rm -f "$(service_pid_file "$SERVICE")" "$(service_child_pid_file "$SERVICE")"
}
trap cleanup EXIT INT TERM

echo $$ > "$(service_pid_file "$SERVICE")"

while true; do
  CURRENT_HASH="$(source_hash)"
  if [[ "$CURRENT_HASH" != "$LAST_HASH" ]]; then
    echo "[$(date '+%F %T')] $SERVICE 检测到源码变化，开始构建"
    if mvn -f "$PROJECT_DIR/backend/pom.xml" -pl "$PROJECT_LIST" -am package -DskipTests; then
      if [[ ! -f "$JAR_PATH" ]]; then
        echo "构建成功但未找到 JAR：$JAR_PATH" >&2
      else
        stop_child
        echo "[$(date '+%F %T')] $SERVICE 启动 $JAR_PATH"
        env "${ENV_ARGS[@]}" java -jar "$JAR_PATH" &
        CHILD_PID=$!
        echo "$CHILD_PID" > "$(service_child_pid_file "$SERVICE")"
        LAST_HASH="$(source_hash)"
      fi
    else
      echo "[$(date '+%F %T')] $SERVICE 构建失败，保留当前进程并等待下一次修改" >&2
    fi
  elif [[ -n "$CHILD_PID" ]] && ! kill -0 "$CHILD_PID" 2>/dev/null; then
    echo "[$(date '+%F %T')] $SERVICE 进程已退出，等待源码修改后重试" >&2
  fi
  sleep 1
done
