<template>
  <el-drawer
    v-model="visible"
    title="执行审计与历史快照"
    size="580px"
    direction="rtl"
    custom-class="history-drawer glass-drawer"
    :destroy-on-close="true"
  >
    <div class="history-container" v-loading="loading">
      <div v-if="logList.length === 0" class="empty-state">
        <el-empty description="暂无历史执行凭单记录" />
      </div>

      <div v-else class="log-timeline">
        <div
          v-for="item in logList"
          :key="item.id || item.receiptId"
          class="log-card glass-card"
        >
          <div class="log-header">
            <span class="receipt-id">{{ item.receiptId }}</span>
            <el-tag
              size="small"
              :type="item.status === 'SUCCESS' ? 'success' : 'danger'"
              class="status-tag"
            >
              {{ item.status === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </div>

          <div class="log-meta">
            <span><el-icon><Clock /></el-icon> {{ formatTime(item.createTime) }}</span>
            <span><el-icon><Timer /></el-icon> {{ item.latencyMs }}ms</span>
            <span><el-icon><Coin /></el-icon> {{ item.totalTokens }} Tokens</span>
          </div>

          <div class="log-content-preview">
            <div class="preview-label">输入快照:</div>
            <pre class="json-preview">{{ formatJson(item.inputParams) }}</pre>
          </div>

          <div class="log-content-preview mt-2">
            <div class="preview-label">输出结果:</div>
            <div class="output-preview">{{ item.outputContent || "无输出" }}</div>
          </div>

          <div class="log-actions">
            <el-button
              size="small"
              type="primary"
              link
              icon="RefreshRight"
              @click="handleReplay(item)"
            >
              填回并重放
            </el-button>
            <el-button
              size="small"
              link
              icon="CopyDocument"
              @click="copyText(item.outputContent)"
            >
              复制结果
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref } from "vue";
import { Clock, Timer, Coin, RefreshRight, CopyDocument } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { listApplyExecutions } from "@/api/kac/apply/apply.js";

const visible = ref(false);
const loading = ref(false);
const logList = ref([]);
const currentApplyId = ref(null);

const emit = defineEmits(["replay"]);

function open(applyId) {
  currentApplyId.value = applyId;
  visible.value = true;
  fetchLogs();
}

async function fetchLogs() {
  loading.value = true;
  try {
    const res = await listApplyExecutions({
      applyId: currentApplyId.value,
      pageNum: 1,
      pageSize: 30,
    });
    logList.value = res.data?.rows || res.data || [];
  } catch {
    logList.value = [];
  } finally {
    loading.value = false;
  }
}

function handleReplay(item) {
  try {
    const inputs = typeof item.inputParams === "string" ? JSON.parse(item.inputParams) : item.inputParams;
    emit("replay", inputs);
    visible.value = false;
    ElMessage.success("已将历史入参代入工作台");
  } catch (e) {
    ElMessage.warning("入参解析异常");
  }
}

function copyText(text) {
  if (!text) return;
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success("已复制到剪贴板");
  });
}

function formatJson(str) {
  if (!str) return "{}";
  try {
    return JSON.stringify(typeof str === "string" ? JSON.parse(str) : str, null, 2);
  } catch {
    return str;
  }
}

function formatTime(val) {
  if (!val) return "-";
  const d = new Date(val);
  return d.toLocaleString("zh-CN", { hour12: false });
}

defineExpose({ open });
</script>

<style scoped lang="scss">
.history-container {
  padding: 4px;
}

.log-timeline {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.log-card {
  padding: 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(226, 232, 240, 0.8);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);

  .log-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;

    .receipt-id {
      font-size: 12px;
      font-family: monospace;
      color: #0f172a;
      font-weight: 600;
    }
  }

  .log-meta {
    display: flex;
    gap: 14px;
    font-size: 12px;
    color: #64748b;
    margin-bottom: 10px;

    span {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
  }

  .log-content-preview {
    font-size: 12px;

    .preview-label {
      color: #475569;
      font-weight: 500;
      margin-bottom: 4px;
    }

    .json-preview {
      background: rgba(248, 250, 252, 0.9);
      border: 1px solid #e2e8f0;
      padding: 8px;
      border-radius: 6px;
      font-size: 11px;
      max-height: 90px;
      overflow-y: auto;
      margin: 0;
    }

    .output-preview {
      background: rgba(248, 250, 252, 0.9);
      border: 1px solid #e2e8f0;
      padding: 8px;
      border-radius: 6px;
      font-size: 12px;
      color: #1e293b;
      max-height: 100px;
      overflow-y: auto;
      white-space: pre-wrap;
    }
  }

  .mt-2 {
    margin-top: 8px;
  }

  .log-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 10px;
    padding-top: 8px;
    border-top: 1px dashed rgba(226, 232, 240, 0.8);
  }
}
</style>
