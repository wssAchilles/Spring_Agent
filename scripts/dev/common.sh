#!/usr/bin/env bash

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNTIME_DIR="$PROJECT_DIR/.runtime/dev"

mkdir -p "$RUNTIME_DIR"

service_pid_file() {
  printf '%s/%s.pid\n' "$RUNTIME_DIR" "$1"
}

service_child_pid_file() {
  printf '%s/%s.child.pid\n' "$RUNTIME_DIR" "$1"
}

service_log_file() {
  printf '%s/%s.log\n' "$RUNTIME_DIR" "$1"
}

start_detached() {
  local log_file="$1"
  local pid_file="$2"
  shift 2

  python3 - "$log_file" "$pid_file" "$@" <<'PY'
import subprocess
import sys

log_path = sys.argv[1]
pid_path = sys.argv[2]
command = sys.argv[3:]

with open(log_path, "ab", buffering=0) as log:
    process = subprocess.Popen(
        command,
        stdin=subprocess.DEVNULL,
        stdout=log,
        stderr=subprocess.STDOUT,
        close_fds=True,
        start_new_session=True,
    )

with open(pid_path, "w", encoding="utf-8") as pid_file:
    pid_file.write(str(process.pid))
PY
}

read_pid() {
  local file="$1"
  if [[ -f "$file" ]]; then
    tr -d '[:space:]' < "$file"
  fi
  return 0
}

is_running() {
  local pid
  pid="$(read_pid "$(service_pid_file "$1")")"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

kill_pid_tree() {
  local pid="$1"
  local label="${2:-PID $pid}"
  if [[ -z "$pid" ]] || ! kill -0 "$pid" 2>/dev/null; then
    return 0
  fi

  # Collect entire descendant tree (children, grandchildren, …)
  local pids=("$pid")
  local queue=("$pid")
  while [[ ${#queue[@]} -gt 0 ]]; do
    local current="${queue[0]}"
    queue=("${queue[@]:1}")
    local children
    children="$(pgrep -P "$current" 2>/dev/null || true)"
    for child in $children; do
      pids+=("$child")
      queue+=("$child")
    done
  done

  # Send SIGTERM to all descendants (youngest first for clean shutdown)
  local i
  for ((i = ${#pids[@]} - 1; i >= 0; i--)); do
    kill "${pids[$i]}" 2>/dev/null || true
  done

  # Wait for graceful shutdown
  for ((i = 1; i <= 20; i++)); do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 0.5
  done

  # Force kill remaining
  if kill -0 "$pid" 2>/dev/null; then
    echo "$label 常规停止失败，强制停止"
    for ((i = ${#pids[@]} - 1; i >= 0; i--)); do
      kill -9 "${pids[$i]}" 2>/dev/null || true
    done
  fi
}

kill_pid() {
  local pid="$1"
  local label="${2:-PID $pid}"
  if [[ -z "$pid" ]] || ! kill -0 "$pid" 2>/dev/null; then
    return 0
  fi

  kill "$pid" 2>/dev/null || true
  local i
  for ((i = 1; i <= 20; i++)); do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 0.5
  done

  if kill -0 "$pid" 2>/dev/null; then
    echo "$label 常规停止失败，强制停止"
    kill -9 "$pid" 2>/dev/null || true
  fi
}

assert_port_available() {
  local port="$1"
  local owner
  owner="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
  if [[ -n "$owner" ]]; then
    echo "端口 $port 已被 PID $owner 占用，请先停止该进程。" >&2
    return 1
  fi
}

release_port() {
  local port="$1"
  local name="${2:-端口 $port}"
  local pids pid i

  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | sort -u || true)"
  if [[ -z "$pids" ]]; then
    echo "$name 未被占用"
    return 0
  fi

  echo "$name 被占用，正在释放：$(tr '\n' ' ' <<< "$pids")"
  while IFS= read -r pid; do
    [[ -z "$pid" ]] && continue
    kill "$pid" 2>/dev/null || true
  done <<< "$pids"

  for ((i = 1; i <= 20; i++)); do
    pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | sort -u || true)"
    [[ -z "$pids" ]] && break
    sleep 0.5
  done

  if [[ -n "$pids" ]]; then
    echo "$name 常规停止失败，强制释放：$(tr '\n' ' ' <<< "$pids")"
    while IFS= read -r pid; do
      [[ -z "$pid" ]] && continue
      kill -9 "$pid" 2>/dev/null || true
    done <<< "$pids"
  fi

  assert_port_available "$port"
}

stop_orphaned_dev_processes() {
  local pids pid count=0

  echo "扫描孤儿进程..."

  # 1) 已知服务进程模式 — 全部杀掉
  local known_patterns=(
    "watch-java.sh"
    "qknow-server/target/qknow-server.jar"
    "qknow-hermes-starter/target/qknow-hermes-starter"
    "mvn.*qknow-server"
    "mvn.*qknow-hermes"
    "mvnw.*qknow-server"
    "mvnw.*qknow-hermes"
  )
  for pattern in "${known_patterns[@]}"; do
    pids="$(pgrep -f "$pattern" 2>/dev/null | sort -u || true)"
    [[ -z "$pids" ]] && continue
    while IFS= read -r pid; do
      [[ -z "$pid" ]] && continue
      [[ "$pid" == "$$" ]] && continue
      kill_pid_tree "$pid" "孤儿进程 $pid"
      ((count++)) || true
    done <<< "$pids"
  done

  # 2) Python 包装器 — start_detached 创建的 python3 - ... <script>
  #    watch-java.sh 会覆盖 PID 文件，导致 Python 包装器成为孤儿
  pids="$(pgrep -f "python3 - .*\.log.*\.pid" 2>/dev/null | sort -u || true)"
  if [[ -z "$pids" ]]; then
    # 回退：匹配项目路径下的 python3 子进程
    pids="$(pgrep -x python3 2>/dev/null || true)"
    if [[ -n "$pids" ]]; then
      local filtered=""
      while IFS= read -r pid; do
        [[ -z "$pid" ]] && continue
        [[ "$pid" == "$$" ]] && continue
        local cmdline
        cmdline="$(ps -p "$pid" -o args= 2>/dev/null || true)"
        if [[ "$cmdline" == *"$PROJECT_DIR"*".log"*".pid"* ]]; then
          filtered+="$pid"$'\n'
        fi
      done <<< "$pids"
      pids="$filtered"
    fi
  fi
  [[ -n "$pids" ]] && while IFS= read -r pid; do
    [[ -z "$pid" ]] && continue
    [[ "$pid" == "$$" ]] && continue
    kill_pid_tree "$pid" "Python 包装器 $pid"
    ((count++)) || true
  done <<< "$pids"

  # 3) 项目相关的 Node/Vite 进程
  pids="$(pgrep -f "node.*$PROJECT_DIR/frontend" 2>/dev/null | sort -u || true)"
  if [[ -n "$pids" ]]; then
    while IFS= read -r pid; do
      [[ -z "$pid" ]] && continue
      [[ "$pid" == "$$" ]] && continue
      kill_pid_tree "$pid" "Node 孤儿 $pid"
      ((count++)) || true
    done <<< "$pids"
  fi

  # 4) 兜底：PPID=1 且 cwd 或 cmd 包含项目路径的进程（真正的孤儿）
  while IFS= read -r pid; do
    [[ -z "$pid" ]] && continue
    [[ "$pid" == "$$" ]] && continue
    local info cwd cmd_line
    info="$(ps -p "$pid" -o args=,cwd= 2>/dev/null || true)"
    [[ -z "$info" ]] && continue
    cwd="$(echo "$info" | awk '{print $NF}')"
    cmd_line="$(echo "$info" | sed 's/ [^ ]*$//')"
    if [[ "$cwd" == *"$PROJECT_DIR"* ]] || [[ "$cmd_line" == *"$PROJECT_DIR"* ]]; then
      kill_pid_tree "$pid" "PPID=1 孤儿 $pid"
      ((count++)) || true
    fi
  done < <(ps -eo pid,ppid | awk '$2 == 1 {print $1}')

  if [[ "$count" -gt 0 ]]; then
    echo "已清理 $count 个孤儿进程"
  else
    echo "未发现孤儿进程"
  fi
}

wait_for_http() {
  local name="$1" url="$2" attempts="${3:-90}"
  local i
  for ((i = 1; i <= attempts; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "$name 启动超时：$url" >&2
  return 1
}

wait_for_tcp() {
  local name="$1" port="$2" attempts="${3:-90}"
  local i
  for ((i = 1; i <= attempts; i++)); do
    if lsof -tiTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "$name 启动超时：端口 $port" >&2
  return 1
}

stop_service() {
  local service="$1"
  local file child_file pid child_pid
  file="$(service_pid_file "$service")"
  child_file="$(service_child_pid_file "$service")"
  pid="$(read_pid "$file")"
  child_pid="$(read_pid "$child_file")"

  kill_pid_tree "$child_pid" "$service 子进程 $child_pid"

  if [[ -z "$pid" ]] || ! kill -0 "$pid" 2>/dev/null; then
    rm -f "$file" "$child_file"
    echo "$service 未运行"
    return 0
  fi

  kill_pid_tree "$pid" "$service 进程 $pid"
  rm -f "$file" "$child_file"
  echo "$service 已停止"
}
