#!/bin/bash
# kill-java.sh — 一键清理所有 Java 进程（含僵尸进程识别与处理）
# 用法: ./scripts/kill-java.sh [--force] [--dry-run]
#   --force     跳过确认直接清理
#   --dry-run   仅扫描不清理

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

FORCE=false
DRY_RUN=false
for arg in "$@"; do
  case "$arg" in
    --force)  FORCE=true ;;
    --dry-run) DRY_RUN=true ;;
  esac
done

SELF_PID=$$

# ─── 扫描阶段 ───

NORMAL_PIDS=()
ZOMBIE_PIDS=()
declare -A PID_STATE PID_CPU PID_CMD PID_PARENT

echo -e "${CYAN}=== Java 进程扫描 ===${NC}"
echo ""

while IFS= read -r line; do
  pid=$(awk '{print $1}' <<< "$line")
  [[ -z "$pid" || "$pid" == "$SELF_PID" ]] && continue

  state=$(awk '{print $2}' <<< "$line")
  cpu=$(awk '{print $3}' <<< "$line")
  ppid=$(awk '{print $4}' <<< "$line")
  cmd=$(cut -d' ' -f5- <<< "$line")

  PID_STATE[$pid]="$state"
  PID_CPU[$pid]="$cpu"
  PID_CMD[$pid]="$cmd"
  PID_PARENT[$pid]="$ppid"

  if [[ "$state" == "Z" ]]; then
    ZOMBIE_PIDS+=("$pid")
  else
    NORMAL_PIDS+=("$pid")
  fi
done < <(ps -eo pid,state,%cpu,ppid,command 2>/dev/null | awk '/[j]ava/ || /[J]ava/' | grep -v "$SELF_PID" || true)

TOTAL=$((${#NORMAL_PIDS[@]} + ${#ZOMBIE_PIDS[@]}))

if [[ "$TOTAL" -eq 0 ]]; then
  echo -e "${GREEN}未发现任何 Java 进程。${NC}"
  exit 0
fi

# ─── 报告阶段 ───

printf "${CYAN}%-8s %-8s %-6s %-8s %-8s %s${NC}\n" "PID" "状态" "CPU%" "PPID" "类型" "命令"
printf "%-8s %-8s %-6s %-8s %-8s %s\n" "-----" "------" "-----" "------" "------" "----"

for pid in "${NORMAL_PIDS[@]}"; do
  type="普通"
  printf "%-8s %-8s %-6s %-8s %-8s %s\n" \
    "$pid" "${PID_STATE[$pid]}" "${PID_CPU[$pid]}" "${PID_PARENT[$pid]}" "$type" "${PID_CMD[$pid]}"
done

for pid in "${ZOMBIE_PIDS[@]}"; do
  printf "${RED}%-8s %-8s %-6s %-8s %-8s %s${NC}\n" \
    "$pid" "${PID_STATE[$pid]}" "${PID_CPU[$pid]}" "${PID_PARENT[$pid]}" "僵尸" "${PID_CMD[$pid]}"
done

echo ""
echo -e "共发现 ${YELLOW}${TOTAL}${NC} 个 Java 进程 — 普通: ${GREEN}${#NORMAL_PIDS[@]}${NC}, 僵尸: ${RED}${#ZOMBIE_PIDS[@]}${NC}"

if $DRY_RUN; then
  echo -e "${YELLOW}[dry-run] 仅扫描，不执行清理。${NC}"
  exit 0
fi

# ─── 确认阶段 ───

if ! $FORCE; then
  echo ""
  read -rp "确认清理以上所有进程? (y/N): " CONFIRM
  if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
    echo "已取消。"
    exit 0
  fi
fi

echo ""

# ─── 清理普通进程（SIGTERM → 等待 → SIGKILL）───

if [[ ${#NORMAL_PIDS[@]} -gt 0 ]]; then
  echo -e "${YELLOW}[1/3] 发送 SIGTERM 给 ${#NORMAL_PIDS[@]} 个普通进程...${NC}"
  for pid in "${NORMAL_PIDS[@]}"; do
    kill -15 "$pid" 2>/dev/null || true
  done

  echo -e "${YELLOW}[2/3] 等待 5 秒优雅退出...${NC}"
  sleep 5

  STILL_ALIVE=()
  for pid in "${NORMAL_PIDS[@]}"; do
    if kill -0 "$pid" 2>/dev/null; then
      STILL_ALIVE+=("$pid")
    fi
  done

  if [[ ${#STILL_ALIVE[@]} -gt 0 ]]; then
    echo -e "${RED}    ${#STILL_ALIVE[@]} 个进程未响应 SIGTERM，发送 SIGKILL...${NC}"
    for pid in "${STILL_ALIVE[@]}"; do
      kill -9 "$pid" 2>/dev/null || true
      echo "    已强杀 PID $pid"
    done
  else
    echo -e "${GREEN}    所有普通进程已优雅退出。${NC}"
  fi
fi

# ─── 清理僵尸进程（需要杀父进程）───

if [[ ${#ZOMBIE_PIDS[@]} -gt 0 ]]; then
  echo ""
  echo -e "${RED}[3/3] 处理 ${#ZOMBIE_PIDS[@]} 个僵尸进程...${NC}"
  for pid in "${ZOMBIE_PIDS[@]}"; do
    ppid="${PID_PARENT[$pid]}"
    if [[ -z "$ppid" || "$ppid" == "1" ]]; then
      echo -e "    僵尸 PID ${pid} 父进程为 init(1)，${YELLOW}需重启系统才能回收${NC}"
      continue
    fi

    # 方式 1: 让父进程回收（SIGCHLD）
    echo -n "    僵尸 PID $pid → 父进程 PPID $ppid: 发送 SIGCHLD..."
    kill -s SIGCHLD "$ppid" 2>/dev/null || true
    sleep 1

    if ! ps -p "$pid" -o state= 2>/dev/null | grep -q 'Z'; then
      echo -e " ${GREEN}已回收${NC}"
      continue
    fi

    # 方式 2: 父进程不响应则杀父
    echo -n " → 父进程未回收，杀掉 PPID $ppid..."
    kill -15 "$ppid" 2>/dev/null || true
    sleep 2
    kill -9 "$ppid" 2>/dev/null || true

    if ! ps -p "$pid" -o state= 2>/dev/null | grep -q 'Z'; then
      echo -e " ${GREEN}已回收${NC}"
    else
      echo -e " ${RED}仍然残留${NC}"
    fi
  done
fi

# ─── 最终报告 ───

echo ""
REMAINING=$(pgrep -f '[j]ava' 2>/dev/null || true)
ZOMBIE_REMAINING=$(ps -eo pid,state 2>/dev/null | awk '/[j]ava|Z/{if($2=="Z") print $1}' || true)

if [[ -z "$REMAINING" && -z "$ZOMBIE_REMAINING" ]]; then
  echo -e "${GREEN}✓ 所有 Java 进程已清除。${NC}"
else
  [[ -n "$REMAINING" ]] && echo -e "${RED}仍有残留普通进程: $REMAINING${NC}"
  [[ -n "$ZOMBIE_REMAINING" ]] && echo -e "${RED}仍有残留僵尸进程: $ZOMBIE_REMAINING${NC}"
  exit 1
fi
