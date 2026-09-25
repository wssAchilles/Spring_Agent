<template>
  <div class="database-mcp-inspect-widget">
    <!-- 顶部状态栏 (单色暗黑钛金毛玻璃) -->
    <div class="widget-header">
      <div class="header-left">
        <span class="status-indicator live-pulse"></span>
        <span class="widget-title">DATABASE MCP INSPECT // PHASE 139</span>
      </div>
      <div class="header-right">
        <span class="sandbox-badge">
          AST SANDBOX ACTIVE // LIMIT &amp; TENANT INJECTED
        </span>
      </div>
    </div>

    <!-- 核心指标网格 -->
    <div class="metrics-grid">
      <!-- SQL 语法分析耗时 -->
      <div class="metric-card">
        <div class="card-label">SQL AST 语法解析耗时</div>
        <div class="card-value-row">
          <span class="value-highlight">{{ astLatencyMs.toFixed(2) }}</span>
          <span class="value-cap">ms (&lt;= 1.0ms 门限)</span>
        </div>
        <div class="progress-bar-track">
          <div
            class="progress-bar-fill fill-cyan"
            :style="{ width: `${Math.min(100, (astLatencyMs / 1.0) * 100)}%` }"
          ></div>
        </div>
      </div>

      <!-- 高危 DDL/DML 拦截率 -->
      <div class="metric-card">
        <div class="card-label">高危 DDL/无约束 DML 拦截率</div>
        <div class="card-value-row">
          <span class="value-highlight">100.0%</span>
          <span class="value-cap">(Default-Deny)</span>
        </div>
        <div class="status-chip-row">
          <span class="chip chip-verified">
            <i class="el-icon-check"></i> 零删库与全表误删风险
          </span>
        </div>
      </div>

      <!-- 双向事务回滚耗时 -->
      <div class="metric-card">
        <div class="card-label">双向 Undo 逆向补偿耗时</div>
        <div class="card-value-row">
          <span class="value-highlight">{{ rollbackLatencyMs.toFixed(1) }}</span>
          <span class="value-cap">ms (&lt;= 15ms 门限)</span>
        </div>
        <div class="progress-bar-track">
          <div
            class="progress-bar-fill fill-emerald"
            :style="{ width: `${Math.min(100, (rollbackLatencyMs / 15.0) * 100)}%` }"
          ></div>
        </div>
      </div>

      <!-- HikariCP 连接池健康态 -->
      <div class="metric-card">
        <div class="card-label">HikariCP 活跃/空闲连接池</div>
        <div class="card-value-row">
          <span class="value-highlight">28</span>
          <span class="value-cap">/ 30 可用连接</span>
        </div>
        <div class="status-chip-row">
          <span class="chip chip-verified">
            <i class="el-icon-check"></i> 慢查询熔断安全稳态
          </span>
        </div>
      </div>
    </div>

    <!-- SQL AST 安全重写与比对视图 -->
    <div class="sql-compare-section">
      <div class="section-title-row">
        <span class="section-title">SQL AST 语法树安全审查与动态重写对比</span>
        <span class="audit-status-tag" :class="isBlocked ? 'tag-danger' : 'tag-success'">
          {{ isBlocked ? 'REJECTED (高危拦截)' : 'ALLOWED (安全重写放行)' }}
        </span>
      </div>
      <div class="compare-boxes-grid">
        <div class="sql-box">
          <div class="box-label">Agent 原始生成 SQL:</div>
          <pre class="code-view code-orig">{{ currentOrigSql }}</pre>
        </div>
        <div class="sql-box">
          <div class="box-label">沙箱重写执行 SQL (自动注入多租户与 LIMIT):</div>
          <pre class="code-view code-rewritten" :class="{ 'code-blocked': isBlocked }">
{{ isBlocked ? ('[SECURITY_SANDBOX_REJECTED] ' + blockReason) : currentRewrittenSql }}
          </pre>
        </div>
      </div>
    </div>

    <!-- 双向事务 Undo Log 逆向补偿时间轴 -->
    <div class="undo-timeline-section">
      <div class="section-title-row">
        <span class="section-title">双向事务 (UNDO LOG) 镜像捕获与逆向补偿流水线</span>
        <span class="section-sub">事务 ID: {{ currentTxId }}</span>
      </div>
      <div class="timeline-logs-row">
        <div
          v-for="(log, idx) in undoLogs"
          :key="idx"
          class="undo-log-item"
          :class="{ 'item-compensated': isRolledBack }"
        >
          <div class="log-badge">#{{ idx + 1 }} {{ log.op }}</div>
          <div class="log-body">
            <div class="log-table">目标表: {{ log.table }}</div>
            <div class="log-undo-sql">补偿 SQL: <code>{{ log.undoSql }}</code></div>
          </div>
        </div>
        <div v-if="undoLogs.length === 0" class="empty-logs">
          当前只读查询无事务副作用，未生成 Undo Log
        </div>
      </div>
    </div>

    <!-- 纯 Java 21 Record 密码学事务凭单面板 -->
    <div class="receipt-audit-panel">
      <div class="audit-header">
        <span class="audit-title">数据库 MCP 事务执行与沙箱存证凭单</span>
        <span class="signature-chip">
          <span class="shield-icon">🛡️</span> SHA-256 常量时间自验真通过
        </span>
      </div>
      <div class="audit-grid">
        <div class="audit-item">
          <span class="k">凭单编号:</span>
          <span class="v code-font">{{ currentReceiptId }}</span>
        </div>
        <div class="audit-item">
          <span class="k">当前租户:</span>
          <span class="v code-font">{{ currentTenantId }}</span>
        </div>
        <div class="audit-item">
          <span class="k">回滚状态:</span>
          <span class="v" :class="isRolledBack ? 'text-amber' : 'text-emerald'">
            {{ isRolledBack ? '已触发 LIFO 逆向补偿' : '未触发回滚 (稳态提交)' }}
          </span>
        </div>
        <div class="audit-item">
          <span class="k">时间戳:</span>
          <span class="v">{{ new Date(receiptTimestamp).toLocaleTimeString() }}</span>
        </div>
      </div>
    </div>

    <!-- 交互模拟工具箱 -->
    <div class="widget-actions">
      <button class="titanium-btn" @click="simulateDdlAttack" title="模拟高危 DDL (DROP TABLE) 拦截">
        <span class="btn-icon">🛑</span> 模拟高危 DDL 拦截
      </button>
      <button class="titanium-btn" @click="simulateUnconstrainedDml" title="模拟无 WHERE 条件危险 DELETE 拦截">
        <span class="btn-icon">⚠️</span> 模拟全表误删拦截
      </button>
      <button class="titanium-btn" @click="simulateBiDirectionalRollback" title="模拟跨源写入异常与双向 Undo 回滚">
        <span class="btn-icon">🔄</span> 模拟双向事务回滚
      </button>
      <button class="titanium-btn btn-secondary" @click="resetToBaseline" title="重置基线状态">
        重置基线
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const astLatencyMs = ref(0.12)
const rollbackLatencyMs = ref(4.5)
const isBlocked = ref(false)
const blockReason = ref('')
const isRolledBack = ref(false)
const currentTxId = ref('TX-FINANCIAL-9081A')
const currentTenantId = ref('tenant_enterprise_01')
const receiptTimestamp = ref(Date.now())
const currentReceiptId = ref('RCP-DB-TX-4C9D2E1A0F8B7C3E')

const currentOrigSql = ref(
  "SELECT order_id, amount, status FROM t_enterprise_order WHERE amount > 10000 ORDER BY create_time DESC"
)
const currentRewrittenSql = ref(
  "SELECT order_id, amount, status FROM t_enterprise_order WHERE (tenant_id = 'tenant_enterprise_01') AND amount > 10000 ORDER BY create_time DESC LIMIT 1000"
)

const undoLogs = ref([])

// 模拟高危 DDL 拦截
function simulateDdlAttack() {
  isBlocked.value = true
  blockReason.value = '严禁执行 DDL 高危表结构变更操作 (DROP TABLE)！'
  currentOrigSql.value = 'DROP TABLE t_enterprise_order;'
  currentRewrittenSql.value = ''
  astLatencyMs.value = 0.08
  currentReceiptId.value = 'RCP-DB-TX-' + Math.random().toString(36).substring(2, 10).toUpperCase() + Math.random().toString(36).substring(2, 10).toUpperCase()
  receiptTimestamp.value = Date.now()
}

// 模拟全表误删拦截
function simulateUnconstrainedDml() {
  isBlocked.value = true
  blockReason.value = '严禁执行无 WHERE 条件的全表修改或删除！'
  currentOrigSql.value = 'DELETE FROM t_user_workspace_draft;'
  currentRewrittenSql.value = ''
  astLatencyMs.value = 0.09
  currentReceiptId.value = 'RCP-DB-TX-' + Math.random().toString(36).substring(2, 10).toUpperCase() + Math.random().toString(36).substring(2, 10).toUpperCase()
  receiptTimestamp.value = Date.now()
}

// 模拟双向事务回滚
function simulateBiDirectionalRollback() {
  isBlocked.value = false
  currentOrigSql.value = "INSERT INTO t_payment_record (payment_id, user_id, amount) VALUES ('PAY_889', 'U1001', 5000);"
  currentRewrittenSql.value = "INSERT INTO t_payment_record (payment_id, user_id, amount) VALUES ('PAY_889', 'U1001', 5000) WHERE tenant_id = 'tenant_enterprise_01';"
  undoLogs.value = [
    { op: 'INSERT', table: 't_payment_record', undoSql: "DELETE FROM t_payment_record WHERE payment_id = 'PAY_889'" },
    { op: 'UPDATE', table: 't_user_balance', undoSql: "UPDATE t_user_balance SET balance = 15000 WHERE user_id = 'U1001'" }
  ]
  isRolledBack.value = true
  rollbackLatencyMs.value = 3.8 + Math.random() * 2.0
  currentReceiptId.value = 'RCP-DB-TX-' + Math.random().toString(36).substring(2, 10).toUpperCase() + Math.random().toString(36).substring(2, 10).toUpperCase()
  receiptTimestamp.value = Date.now()
}

function resetToBaseline() {
  isBlocked.value = false
  blockReason.value = ''
  isRolledBack.value = false
  astLatencyMs.value = 0.12
  rollbackLatencyMs.value = 4.5
  currentTxId.value = 'TX-FINANCIAL-9081A'
  currentTenantId.value = 'tenant_enterprise_01'
  currentOrigSql.value = "SELECT order_id, amount, status FROM t_enterprise_order WHERE amount > 10000 ORDER BY create_time DESC"
  currentRewrittenSql.value = "SELECT order_id, amount, status FROM t_enterprise_order WHERE (tenant_id = 'tenant_enterprise_01') AND amount > 10000 ORDER BY create_time DESC LIMIT 1000"
  undoLogs.value = []
  currentReceiptId.value = 'RCP-DB-TX-4C9D2E1A0F8B7C3E'
  receiptTimestamp.value = Date.now()
}
</script>

<style scoped>
.database-mcp-inspect-widget {
  background: rgba(10, 10, 12, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 20px;
  color: #ededed;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  margin-bottom: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.45);
}

/* 顶部状态栏 */
.widget-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  margin-bottom: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #10b981;
}

.live-pulse {
  box-shadow: 0 0 10px #10b981;
  animation: pulse-ring 2s infinite cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes pulse-ring {
  0% { transform: scale(0.95); opacity: 0.8; }
  50% { transform: scale(1.3); opacity: 1; box-shadow: 0 0 16px #10b981; }
  100% { transform: scale(0.95); opacity: 0.8; }
}

.widget-title {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #f3f4f6;
}

.sandbox-badge {
  font-size: 11px;
  padding: 4px 10px;
  border-radius: 6px;
  background: rgba(16, 185, 129, 0.12);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.3);
  font-weight: 600;
  letter-spacing: 0.04em;
  font-family: monospace;
}

/* 核心指标网格 */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.metric-card {
  background: rgba(18, 18, 22, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 12px 14px;
}

.card-label {
  font-size: 11px;
  color: #9ca3af;
  margin-bottom: 6px;
}

.card-value-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 8px;
}

.value-highlight {
  font-size: 20px;
  font-weight: 700;
  color: #ffffff;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.value-cap {
  font-size: 11px;
  color: #6b7280;
}

.progress-bar-track {
  width: 100%;
  height: 4px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 2px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  transition: width 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.fill-emerald {
  background: linear-gradient(90deg, #10b981, #34d399);
}

.fill-cyan {
  background: linear-gradient(90deg, #06b6d4, #22d3ee);
}

.chip-verified {
  font-size: 10px;
  color: #34d399;
  background: rgba(16, 185, 129, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
}

/* SQL 对比视图 */
.sql-compare-section {
  background: rgba(18, 18, 22, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 16px;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-title {
  font-size: 12px;
  font-weight: 700;
  color: #e5e7eb;
  letter-spacing: 0.05em;
}

.audit-status-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
}

.tag-success {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.tag-danger {
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.compare-boxes-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.sql-box {
  background: rgba(12, 12, 16, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 10px;
}

.box-label {
  font-size: 10px;
  color: #9ca3af;
  margin-bottom: 6px;
}

.code-view {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.4;
}

.code-orig {
  color: #e5e7eb;
}

.code-rewritten {
  color: #6ee7b7;
}

.code-blocked {
  color: #f87171 !important;
}

/* Undo Log 时间轴 */
.undo-timeline-section {
  background: rgba(14, 14, 18, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 16px;
}

.section-sub {
  font-size: 11px;
  color: #9ca3af;
  font-family: monospace;
}

.timeline-logs-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.undo-log-item {
  background: rgba(25, 25, 32, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  padding: 8px 10px;
  display: flex;
  gap: 10px;
  align-items: center;
}

.undo-log-item.item-compensated {
  border-color: rgba(245, 158, 11, 0.3);
  background: rgba(245, 158, 11, 0.08);
}

.log-badge {
  font-size: 10px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
}

.log-body {
  font-size: 11px;
}

.log-table {
  color: #9ca3af;
  font-size: 10px;
}

.log-undo-sql code {
  color: #fde68a;
  font-family: monospace;
}

.empty-logs {
  font-size: 11px;
  color: #6b7280;
  padding: 8px 0;
}

/* 凭单面板 */
.receipt-audit-panel {
  background: rgba(18, 18, 22, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 16px;
}

.audit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.audit-title {
  font-size: 11px;
  font-weight: 600;
  color: #9ca3af;
  letter-spacing: 0.04em;
}

.signature-chip {
  font-size: 11px;
  color: #fbbf24;
  display: flex;
  align-items: center;
  gap: 4px;
}

.audit-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px;
  font-size: 11px;
}

.audit-item {
  display: flex;
  gap: 6px;
}

.audit-item .k {
  color: #6b7280;
}

.audit-item .v {
  color: #d1d5db;
}

.code-font {
  font-family: monospace;
}

.text-emerald {
  color: #34d399 !important;
}

.text-amber {
  color: #fbbf24 !important;
}

/* 按钮工具条 */
.widget-actions {
  display: flex;
  gap: 10px;
}

.titanium-btn {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  color: #ffffff;
  padding: 8px 14px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.titanium-btn:hover {
  background: rgba(255, 255, 255, 0.14);
  border-color: rgba(255, 255, 255, 0.25);
  transform: translateY(-1px);
}

.btn-secondary {
  background: transparent;
  border-color: rgba(255, 255, 255, 0.08);
  color: #9ca3af;
}

.btn-secondary:hover {
  color: #e5e7eb;
}
</style>
