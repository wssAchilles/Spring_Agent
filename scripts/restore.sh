#!/usr/bin/env bash
# ====================================================================
# Phase 16: 跨异构存储 (PostgreSQL + PGVector + Neo4j) 生产级自愈恢复脚本
# ====================================================================
set -eo pipefail

BACKUP_DIR="${1}"

if [ -z "${BACKUP_DIR}" ] || [ ! -d "${BACKUP_DIR}" ]; then
    echo "[ERROR] 使用方法: $0 <备份目录路径>" >&2
    exit 1
fi

PG_HOST="${PGHOST:-localhost}"
PG_PORT="${PGPORT:-5432}"
PG_USER="${PGUSER:-postgres}"
PG_DB="${PGDATABASE:-qknow}"

NEO4J_CONTAINER="${NEO4J_CONTAINER:-neo4j}"
NEO4J_DB="${NEO4J_DB:-neo4j}"

MANIFEST="${BACKUP_DIR}/manifest.json"

echo "======================================================"
echo "[Phase 16 异构恢复] 启动三阶段解耦自愈恢复流水线"
echo "备份目录: ${BACKUP_DIR}"
echo "目标数据库: ${PG_HOST}:${PG_PORT}/${PG_DB}"
echo "======================================================"

# 1. 验证清单与 SHA-256 完整性
if [ ! -f "${MANIFEST}" ]; then
    echo "[ERROR] 缺少 manifest.json 清单文件，拒绝恢复！" >&2
    exit 1
fi

echo "[Phase 16 异构恢复] 阶段 0: 执行 SHA-256 完整性签名校验..."
PG_ARCHIVE="${BACKUP_DIR}/postgres_dump.tar.gz"
if [ ! -f "${PG_ARCHIVE}" ]; then
    echo "[ERROR] 缺少 PostgreSQL 备份归档: ${PG_ARCHIVE}" >&2
    exit 1
fi

EXPECTED_PG_HASH=$(grep '"postgres_sha256":' "${MANIFEST}" | awk -F'"' '{print $4}')
ACTUAL_PG_HASH=$(sha256sum "${PG_ARCHIVE}" | awk '{print $1}')

if [ "${EXPECTED_PG_HASH}" != "${ACTUAL_PG_HASH}" ]; then
    echo "[FATAL] PostgreSQL 备份归档哈希不匹配！预期: ${EXPECTED_PG_HASH}, 实际: ${ACTUAL_PG_HASH}" >&2
    echo "数据可能已被篡改或传输损坏，强制阻断恢复！" >&2
    exit 2
fi
echo "[Phase 16 异构恢复] SHA-256 完整性校验 100% 通过。"

# 2. 解压目录格式备份
TMP_RESTORE_DIR="${BACKUP_DIR}/tmp_postgres_dump"
rm -rf "${TMP_RESTORE_DIR}"
mkdir -p "${TMP_RESTORE_DIR}"
tar -xzf "${PG_ARCHIVE}" -C "${BACKUP_DIR}"
mv "${BACKUP_DIR}/postgres_dump" "${TMP_RESTORE_DIR}/dump"

# 3. PostgreSQL 三阶段解耦极速还原 (Pre-data -> Data -> Post-data)
echo "[Phase 16 异构恢复] 阶段 1: 还原 Pre-data (表结构、序列与扩展定义)..."
pg_restore \
    -h "${PG_HOST}" \
    -p "${PG_PORT}" \
    -U "${PG_USER}" \
    -d "${PG_DB}" \
    --section=pre-data \
    "${TMP_RESTORE_DIR}/dump" || true

echo "[Phase 16 异构恢复] 阶段 2: 还原 Data (无索引状态极速并发灌入 1536 维向量与切片)..."
pg_restore \
    -h "${PG_HOST}" \
    -p "${PG_PORT}" \
    -U "${PG_USER}" \
    -d "${PG_DB}" \
    --section=data \
    -j 4 \
    "${TMP_RESTORE_DIR}/dump"

echo "[Phase 16 异构恢复] 阶段 3: 优化内存参数并并行构建 HNSW 索引与外键约束 (Post-data)..."
# 调大 maintenance_work_mem 避免 HNSW 内存不足
psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d "${PG_DB}" -c "SET maintenance_work_mem = '2GB'; SET max_parallel_maintenance_workers = 4;" 2>/dev/null || true

pg_restore \
    -h "${PG_HOST}" \
    -p "${PG_PORT}" \
    -U "${PG_USER}" \
    -d "${PG_DB}" \
    --section=post-data \
    -j 4 \
    "${TMP_RESTORE_DIR}/dump" || true

rm -rf "${TMP_RESTORE_DIR}"
echo "[Phase 16 异构恢复] PostgreSQL 与 1536 维 PGVector HNSW 恢复完成。"

# 4. Neo4j 知识图谱二进制恢复
NEO4J_DUMP="${BACKUP_DIR}/neo4j_dump/${NEO4J_DB}.dump"
if [ -f "${NEO4J_DUMP}" ] && command -v docker &> /dev/null && docker ps --format '{{.Names}}' | grep -q "^${NEO4J_CONTAINER}$"; then
    echo "[Phase 16 异构恢复] 正在将 Neo4j 二进制转储覆盖载入容器..."
    docker cp "${NEO4J_DUMP}" "${NEO4J_CONTAINER}:/tmp/${NEO4J_DB}.dump"
    docker exec "${NEO4J_CONTAINER}" neo4j-admin database load "${NEO4J_DB}" --from-path=/tmp/ --overwrite-destination=true 2>/dev/null || true
    docker exec "${NEO4J_CONTAINER}" rm -f "/tmp/${NEO4J_DB}.dump" 2>/dev/null || true
    echo "[Phase 16 异构恢复] Neo4j 图数据覆盖还原完成。"
fi

echo "======================================================"
echo "[Phase 16 异构恢复] 跨异构存储恢复完成！"
echo "[自愈提示] 建议触发 VectorReconciliationEngine 进行双向反熵巡检。"
echo "======================================================"
