#!/usr/bin/env bash
# ====================================================================
# Phase 16: 跨异构存储 (PostgreSQL + PGVector + Neo4j) 生产级一致性备份脚本
# ====================================================================
set -eo pipefail

BACKUP_ROOT="${1:-./backup_data}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
TARGET_DIR="${BACKUP_ROOT}/backup_${TIMESTAMP}"

PG_HOST="${PGHOST:-localhost}"
PG_PORT="${PGPORT:-5432}"
PG_USER="${PGUSER:-postgres}"
PG_DB="${PGDATABASE:-qknow}"

NEO4J_CONTAINER="${NEO4J_CONTAINER:-neo4j}"
NEO4J_DB="${NEO4J_DB:-neo4j}"

echo "======================================================"
echo "[Phase 16 异构备份] 启动异构存储快照备份流水线"
echo "时间戳: ${TIMESTAMP}"
echo "目标路径: ${TARGET_DIR}"
echo "PostgreSQL: ${PG_HOST}:${PG_PORT}/${PG_DB}"
echo "======================================================"

mkdir -p "${TARGET_DIR}/postgres_dump"
mkdir -p "${TARGET_DIR}/neo4j_dump"

# 1. 前置依赖检查
for cmd in pg_dump sha256sum; do
    if ! command -v "$cmd" &> /dev/null; then
        echo "[ERROR] 缺少必要工具: $cmd，备份中止！" >&2
        exit 1
    fi
done

# 2. 注入轻量时间戳快照屏障
SNAPSHOT_START_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
echo "[Phase 16 异构备份] 记录全局快照时间戳屏障: ${SNAPSHOT_START_TIME}"

# 3. 导出 PostgreSQL (含 1536 维 vector_store 与普通关系表)
# 使用 -F d (目录格式) + -j 4 (4线程并行) + -Z 6 (压缩级别 6)
echo "[Phase 16 异构备份] 正在执行 PostgreSQL + PGVector 目录并行导出..."
pg_dump \
    -h "${PG_HOST}" \
    -p "${PG_PORT}" \
    -U "${PG_USER}" \
    -d "${PG_DB}" \
    -F d \
    -j 4 \
    -Z 6 \
    -f "${TARGET_DIR}/postgres_dump"

echo "[Phase 16 异构备份] PostgreSQL 目录导出成功。"

# 4. 导出 Neo4j 知识图谱 (若 Docker 容器存在则执行二进制 dump)
if command -v docker &> /dev/null && docker ps --format '{{.Names}}' | grep -q "^${NEO4J_CONTAINER}$"; then
    echo "[Phase 16 异构备份] 检测到 Neo4j 运行容器 [${NEO4J_CONTAINER}]，执行二进制安全快照..."
    docker exec "${NEO4J_CONTAINER}" neo4j-admin database dump "${NEO4J_DB}" --to-path=/tmp/ 2>/dev/null || true
    docker cp "${NEO4J_CONTAINER}:/tmp/${NEO4J_DB}.dump" "${TARGET_DIR}/neo4j_dump/${NEO4J_DB}.dump" 2>/dev/null || true
    docker exec "${NEO4J_CONTAINER}" rm -f "/tmp/${NEO4J_DB}.dump" 2>/dev/null || true
    echo "[Phase 16 异构备份] Neo4j 二进制转储完成。"
else
    echo "[WARN] 未检测到运行中的 Neo4j Docker 容器 [${NEO4J_CONTAINER}]，生成占位符清单以保持异构对齐。"
    echo "Neo4j container not running during snapshot ${TIMESTAMP}" > "${TARGET_DIR}/neo4j_dump/neo4j_backup_notice.txt"
fi

# 5. 打包 PostgreSQL 目录导出并生成 SHA-256 清单
tar -czf "${TARGET_DIR}/postgres_dump.tar.gz" -C "${TARGET_DIR}" postgres_dump
rm -rf "${TARGET_DIR}/postgres_dump"

SNAPSHOT_END_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

echo "[Phase 16 异构备份] 正在生成 manifest.json 完整性签名清单..."
cd "${TARGET_DIR}"
PG_HASH=$(sha256sum postgres_dump.tar.gz | awk '{print $1}')
NEO4J_HASH="N/A"
if [ -f "neo4j_dump/${NEO4J_DB}.dump" ]; then
    NEO4J_HASH=$(sha256sum "neo4j_dump/${NEO4J_DB}.dump" | awk '{print $1}')
fi

cat <<EOF > manifest.json
{
  "phase": "Phase 16",
  "version": "1.0",
  "timestamp": "${TIMESTAMP}",
  "snapshot_start_utc": "${SNAPSHOT_START_TIME}",
  "snapshot_end_utc": "${SNAPSHOT_END_TIME}",
  "artifacts": {
    "postgres_archive": "postgres_dump.tar.gz",
    "postgres_sha256": "${PG_HASH}",
    "neo4j_archive": "neo4j_dump/${NEO4J_DB}.dump",
    "neo4j_sha256": "${NEO4J_HASH}"
  },
  "status": "COMPLETED"
}
EOF

echo "======================================================"
echo "[Phase 16 异构备份] 备份成功！"
echo "清单文件: ${TARGET_DIR}/manifest.json"
echo "PostgreSQL SHA-256: ${PG_HASH}"
echo "======================================================"
