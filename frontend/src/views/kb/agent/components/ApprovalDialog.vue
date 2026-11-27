<template>
  <el-dialog
    v-model="visible"
    width="680px"
    :close-on-click-modal="false"
    class="ios26-plan-pr-dialog"
    :show-close="true"
  >
    <template #header>
      <div class="ios26-pr-header">
        <span class="ios26-pr-pill">Plan-as-PR · Frontier Epoch Gate</span>
        <span class="ios26-text-headline pr-headline">高危副作用动作变更审阅台</span>
      </div>
    </template>

    <div class="ios26-pr-body">
      <!-- 顶部队列栏：支持切换待审批工单并刷新真实状态 -->
      <div class="pr-toolbar ios26-glass-layer-thin">
        <span class="ios26-text-footnote toolbar-status">
          待审阻塞动作：{{ pendingList.length }} 项（IRREVERSIBLE 零泄漏挂起拦截）
        </span>
        <button type="button" class="ios26-pill-action" :disabled="fetchingList" @click="fetchPendingQueue">
          {{ fetchingList ? '同步中...' : '刷新队列' }}
        </button>
      </div>

      <!-- 若存在激活工单：展示 Plan-as-PR 副作用级别、纪元水位与形式化校验前置状态 -->
      <div v-if="currentApproval" class="pr-detail-panel ios26-glass-layer-regular">
        <div class="pr-alert-row">
          <el-icon :size="22" class="tier-icon" :class="tierClass">
            <WarningFilled />
          </el-icon>
          <div class="pr-alert-text">
            <div class="ios26-text-headline">动作目标：{{ currentApproval.nodeId || '未知工具节点' }}</div>
            <div class="ios26-text-footnote alert-desc">
              {{ currentApproval.reason || '当前分支触及物理不可逆或需人工确认边界，在人工批准前已严格锁定外部副作用释放。' }}
            </div>
          </div>
          <span class="tier-badge" :class="tierClass">{{ sideEffectTierLabel }}</span>
        </div>

        <div class="pr-meta-grid">
          <div class="meta-cell">
            <span class="ios26-text-caption-1">分支 / 工作流 ID</span>
            <span class="ios26-text-subheadline mono-val">{{ currentApproval.flowId || '-' }}</span>
          </div>
          <div class="meta-cell">
            <span class="ios26-text-caption-1">请求 / 事务分支</span>
            <span class="ios26-text-subheadline mono-val">{{ currentApproval.requestId || '-' }}</span>
          </div>
          <div class="meta-cell">
            <span class="ios26-text-caption-1">资源 URI 与纪元水位</span>
            <span class="ios26-text-subheadline mono-val">
              {{ currentApproval.resourceUri || 'default://workspace' }} (Epoch #{{ currentApproval.epoch ?? '—' }})
            </span>
          </div>
          <div class="meta-cell">
            <span class="ios26-text-caption-1">BFoT 形式化约束核验</span>
            <span class="ios26-text-subheadline formal-ok">
              {{ currentApproval.formalGuardStatus || 'DBM + LTLf Pre-Check Ready' }}
            </span>
          </div>
        </div>

        <!-- Plan-as-PR 参数与状态预期变更 Diff 预览 -->
        <div class="pr-diff-box">
          <div class="ios26-text-caption-1 diff-title">Plan-as-PR 执行前 / 批准后状态变更预览 (Speculative Diff)</div>
          <pre class="diff-code">- pre_commit:  tier={{ sideEffectTierLabel }} | status=SUSPENDED_ZERO_LEAKAGE
+ post_approve: frontier(r) >= epoch({{ currentApproval.epoch ?? 1 }}) -> RELEASE_AND_SIGN_HMAC</pre>
        </div>

        <!-- 当有多条待审记录时可点选切换 -->
        <div v-if="pendingList.length > 1" class="pending-selector">
          <button
            v-for="item in pendingList"
            :key="item.approvalKey"
            type="button"
            class="selector-chip"
            :class="{ active: currentApproval.approvalKey === item.approvalKey }"
            @click="currentApproval = item"
          >
            {{ item.nodeId || item.approvalKey }}
          </button>
        </div>

        <div v-if="showRejectReason" class="reject-reason-box">
          <el-input
            v-model="rejectReason"
            type="textarea"
            :rows="2"
            placeholder="请输入拒绝及触发逆序补偿回滚的审计理由..."
          />
        </div>
      </div>

      <!-- 优雅空态：无待审动作 -->
      <div v-else class="pr-empty-panel ios26-glass-layer-thin">
        <div class="ios26-text-body">当前无挂起的 IRREVERSIBLE 高危动作</div>
        <div class="ios26-text-footnote">所有投机与持久化分支均处于 Frontier 水位安全可控区间。</div>
      </div>
    </div>

    <template #footer>
      <div class="ios26-pr-footer">
        <button
          v-if="currentApproval && !showRejectReason"
          type="button"
          class="ios26-btn-danger-outline"
          @click="showRejectReason = true"
        >
          驳回并补偿回滚
        </button>
        <button
          v-else-if="currentApproval"
          type="button"
          class="ios26-btn-danger"
          :disabled="loading"
          @click="handleReject"
        >
          {{ loading ? '执行补偿...' : '确认驳回' }}
        </button>
        <button
          v-if="currentApproval"
          type="button"
          class="ios26-btn-primary"
          :disabled="loading"
          @click="handleApprove"
        >
          {{ loading ? '推进纪元...' : '批准签发释放' }}
        </button>
        <button v-else type="button" class="ios26-btn-neutral" @click="visible = false">
          关闭
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { WarningFilled } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import request from '@/utils/request';
import { getPendingApprovals } from '@/api/audit';

const props = defineProps({
  modelValue: Boolean,
  approval: Object,
});
const emit = defineEmits(['update:modelValue', 'approved', 'rejected', 'pending-count']);

const visible = ref(false);
const loading = ref(false);
const fetchingList = ref(false);
const showRejectReason = ref(false);
const rejectReason = ref('');
const currentApproval = ref(null);
const pendingList = ref([]);

const sideEffectTierLabel = computed(() => {
  return currentApproval.value?.sideEffectTier || 'IRREVERSIBLE';
});

const tierClass = computed(() => {
  const tier = sideEffectTierLabel.value;
  if (tier === 'IRREVERSIBLE') return 'tier-red';
  if (tier === 'EXTERNALIZED_REVERSIBLE') return 'tier-orange';
  return 'tier-blue';
});

async function fetchPendingQueue() {
  fetchingList.value = true;
  try {
    const res = await getPendingApprovals();
    const list = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : [];
    pendingList.value = list;
    emit('pending-count', list.length);
    if (!currentApproval.value && list.length > 0) {
      currentApproval.value = list[0];
    }
  } catch {
    // 后端未开启或无鉴权上下文时保持安静空态，严禁伪造审批单
    pendingList.value = props.approval ? [props.approval] : [];
    emit('pending-count', pendingList.value.length);
  } finally {
    fetchingList.value = false;
  }
}

watch(
  () => props.modelValue,
  (val) => {
    visible.value = val;
    if (val) {
      showRejectReason.value = false;
      rejectReason.value = '';
      if (props.approval) {
        currentApproval.value = props.approval;
      }
      fetchPendingQueue();
    }
  }
);

watch(visible, (val) => {
  emit('update:modelValue', val);
});

async function handleApprove() {
  if (!currentApproval.value) return;
  loading.value = true;
  try {
    await request({
      url: '/hermes/approval/approve',
      method: 'post',
      data: currentApproval.value,
    });
    ElMessage.success('已签发 Frontier 审批放行并推进纪元');
    emit('approved', currentApproval.value);
    pendingList.value = pendingList.value.filter(
      (i) => i.approvalKey !== currentApproval.value.approvalKey
    );
    emit('pending-count', pendingList.value.length);
    currentApproval.value = pendingList.value[0] || null;
    if (!currentApproval.value) {
      visible.value = false;
    }
  } catch (e) {
    ElMessage.error('审批失败: ' + (e.message || '未知错误'));
  } finally {
    loading.value = false;
  }
}

async function handleReject() {
  if (!currentApproval.value) return;
  loading.value = true;
  try {
    await request({
      url: '/hermes/approval/reject',
      method: 'post',
      data: {
        ...currentApproval.value,
        reason: rejectReason.value || '人工驳回并执行逆序补偿',
      },
    });
    ElMessage.success('已驳回并转入三级冷备逆序补偿');
    emit('rejected', currentApproval.value);
    pendingList.value = pendingList.value.filter(
      (i) => i.approvalKey !== currentApproval.value.approvalKey
    );
    emit('pending-count', pendingList.value.length);
    currentApproval.value = pendingList.value[0] || null;
    showRejectReason.value = false;
    rejectReason.value = '';
    if (!currentApproval.value) {
      visible.value = false;
    }
  } catch (e) {
    ElMessage.error('拒绝失败: ' + (e.message || '未知错误'));
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  fetchPendingQueue();
});

defineExpose({
  show: (approval) => {
    currentApproval.value = approval || null;
    visible.value = true;
    fetchPendingQueue();
  },
  refreshQueue: fetchPendingQueue,
});
</script>

<style scoped lang="scss">
.ios26-pr-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ios26-pr-pill {
  display: inline-block;
  width: fit-content;
  padding: 2px 10px;
  border-radius: 1000px;
  background: rgba(0, 136, 255, 0.12);
  color: var(--ios26-color-blue, #0088ff);
  font-size: 11px;
  font-weight: 400;
  letter-spacing: 0.06px;
}

.pr-headline {
  font-size: 17px;
  line-height: 22px;
  letter-spacing: -0.43px;
  font-weight: 590;
  color: #000000;
}

.ios26-pr-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  font-family: var(--ios26-font-family);
}

/* iOS 26 严格双层材质：高光层 + 50px color-dodge 模糊层，零投影，零层叠上下文污染 */
.ios26-glass-layer-thin {
  position: relative;
  border-radius: 28px;
  overflow: hidden;
  border: 0.5px solid rgba(0, 0, 0, 0.12);
  background: transparent;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: 1;
    border-radius: inherit;
    background: rgba(255, 255, 255, 0.05);
    pointer-events: none;
  }

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    z-index: 2;
    border-radius: inherit;
    backdrop-filter: blur(50px);
    -webkit-backdrop-filter: blur(50px);
    background: rgba(255, 255, 255, 0.4);
    mix-blend-mode: color-dodge;
    pointer-events: none;
  }

  > :where(*) {
    position: relative;
    z-index: 3;
  }
}

.ios26-glass-layer-regular {
  position: relative;
  border-radius: 30px;
  overflow: hidden;
  border: 0.5px solid rgba(0, 0, 0, 0.12);
  padding: 18px;
  background: transparent;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: 1;
    border-radius: inherit;
    background: rgba(255, 255, 255, 0.25);
    mix-blend-mode: plus-lighter;
    pointer-events: none;
  }

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    z-index: 2;
    border-radius: inherit;
    backdrop-filter: blur(50px);
    -webkit-backdrop-filter: blur(50px);
    background: rgba(255, 255, 255, 0.6);
    mix-blend-mode: color-dodge;
    pointer-events: none;
  }

  > :where(*) {
    position: relative;
    z-index: 3;
  }
}

.pr-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
}

.toolbar-status {
  color: #3d3d3d;
  font-weight: 400;
}

.ios26-pill-action {
  padding: 4px 12px;
  border-radius: 1000px;
  border: 0.5px solid rgba(0, 0, 0, 0.12);
  background: #ffffff;
  color: #0088ff;
  font-size: 12px;
  font-weight: 400;
  cursor: pointer;
}

.pr-alert-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.12);
}

.tier-icon.tier-red { color: #ff383c; }
.tier-icon.tier-orange { color: #ff8d28; }
.tier-icon.tier-blue { color: #0088ff; }

.pr-alert-text {
  flex: 1;
  .alert-desc {
    margin-top: 4px;
    color: #3d3d3d;
    font-weight: 400;
  }
}

.tier-badge {
  padding: 3px 10px;
  border-radius: 1000px;
  font-size: 11px;
  font-weight: 400;
  letter-spacing: 0.06px;

  &.tier-red { background: rgba(255, 56, 60, 0.12); color: #ff383c; }
  &.tier-orange { background: rgba(255, 141, 40, 0.14); color: #ff8d28; }
  &.tier-blue { background: rgba(0, 136, 255, 0.12); color: #0088ff; }
}

.pr-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-top: 14px;
}

.meta-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.65);
  border: 0.5px solid rgba(0, 0, 0, 0.08);
}

.mono-val {
  font-family: "SF Mono", Menlo, monospace;
  font-size: 12px;
  color: #000000;
  font-weight: 400;
  word-break: break-all;
}

.formal-ok {
  font-size: 12px;
  color: #34c759;
  font-weight: 400;
}

.pr-diff-box {
  margin-top: 14px;
  padding: 12px;
  border-radius: 12px;
  background: #ffffff;
  border: 0.5px solid rgba(0, 0, 0, 0.12);

  .diff-title {
    margin-bottom: 6px;
    color: #3d3d3d;
  }

  .diff-code {
    margin: 0;
    font-family: "SF Mono", Menlo, monospace;
    font-size: 11px;
    line-height: 1.55;
    color: #000000;
    white-space: pre-wrap;
  }
}

.pending-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.selector-chip {
  padding: 4px 10px;
  border-radius: 1000px;
  border: 0.5px solid rgba(0, 0, 0, 0.12);
  background: #ffffff;
  color: #3d3d3d;
  font-size: 12px;
  font-weight: 400;
  cursor: pointer;

  &.active {
    background: #0088ff;
    color: #ffffff;
    border-color: #0088ff;
  }
}

.reject-reason-box {
  margin-top: 12px;
}

.pr-empty-panel {
  padding: 28px 20px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ios26-pr-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.ios26-btn-primary,
.ios26-btn-danger,
.ios26-btn-danger-outline,
.ios26-btn-neutral {
  padding: 8px 18px;
  border-radius: 1000px;
  font-size: 14px;
  font-weight: 400;
  cursor: pointer;
  border: 0.5px solid rgba(0, 0, 0, 0.12);
}

.ios26-btn-primary {
  background: #0088ff;
  color: #ffffff;
  border-color: #0088ff;
}

.ios26-btn-danger {
  background: #ff383c;
  color: #ffffff;
  border-color: #ff383c;
}

.ios26-btn-danger-outline {
  background: #ffffff;
  color: #ff383c;
  border-color: rgba(255, 56, 60, 0.4);
}

.ios26-btn-neutral {
  background: #ffffff;
  color: #000000;
}
</style>
