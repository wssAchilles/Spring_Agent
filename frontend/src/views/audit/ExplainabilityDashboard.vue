<template>
  <div class="explainability-dashboard" ref="dashboardRef">
    <!-- 顶部 Apple iOS 26 光学玻璃导航栏（双层 blur(50px) + color-dodge，零扩散阴影，零层叠污染） -->
    <header class="dashboard-header ios26-nav-glass">
      <div class="brand-zone">
        <div class="status-orb" />
        <div class="brand-text">
          <h1 class="ios26-text-headline system-title">H-NSCDS 神经符号审计与时间旅行工作台</h1>
          <span class="ios26-text-caption-1 system-subtitle">
            PG STEP JOURNAL · BITEMPORAL HYPERGRAPH · BFOT COUNTEREXAMPLE STUDIO
          </span>
        </div>
      </div>

      <!-- 中部分段视图切换器（胶囊圆角，Headline 之外统一字重 400） -->
      <div class="tab-switcher">
        <button
          type="button"
          class="switch-btn"
          :class="{ active: activeTab === 'topology' }"
          @click="activeTab = 'topology'"
        >
          <el-icon><Share /></el-icon>
          <span>因果拓扑与溯源</span>
        </button>
        <button
          type="button"
          class="switch-btn"
          :class="{ active: activeTab === 'timetravel' }"
          @click="activeTab = 'timetravel'"
        >
          <el-icon><Clock /></el-icon>
          <span>Step Journal 时间旅行分叉</span>
        </button>
        <button
          type="button"
          class="switch-btn"
          :class="{ active: activeTab === 'merkle' }"
          @click="activeTab = 'merkle'"
        >
          <el-icon><Lock /></el-icon>
          <span>RFC 6962 密码学验真</span>
        </button>
        <button
          type="button"
          class="switch-btn"
          :class="{ active: activeTab === 'guardrail' }"
          @click="activeTab = 'guardrail'"
        >
          <el-icon><Odometer /></el-icon>
          <span>安全护栏态势</span>
        </button>
      </div>

      <!-- 右侧控制区: Trace ID 检索与全屏 -->
      <div class="control-zone">
        <div class="trace-input-wrap">
          <el-input
            v-model="inputTraceId"
            size="small"
            placeholder="输入 Trace / Execution ID..."
            class="mono-input"
            clearable
            @keyup.enter="handleTraceSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <button type="button" class="ios26-locate-btn" @click="handleTraceSearch">
            定位
          </button>
        </div>

        <button
          type="button"
          class="action-btn"
          title="切换全屏模式"
          @click="toggleFullScreen"
        >
          <el-icon><FullScreen /></el-icon>
        </button>
      </div>
    </header>

    <!-- 主展示视口区（零 transform/opacity 过渡动画，严防破坏玻璃层叠上下文） -->
    <main class="dashboard-viewport">
      <div v-if="activeTab === 'topology'" class="viewport-item">
        <ExplainabilityTopologyCanvas :key="currentTraceId" :trace-id="currentTraceId" />
      </div>

      <!-- 第 6 章：PgStepJournal 时间旅行分叉工作台 -->
      <div v-else-if="activeTab === 'timetravel'" class="viewport-item scrollable timetravel-workspace">
        <section class="tt-control-card ios26-card-regular">
          <div class="tt-card-header">
            <div>
              <div class="ios26-text-headline">嵌入式 PG Step Journal 因果时间旅行分叉</div>
              <div class="ios26-text-footnote tt-subtitle">
                定位任意历史超步检视 DeltaChannel 增量补丁，并执行零幽灵变量隔离分叉（forkBranchFromJournal）
              </div>
            </div>
            <div class="tt-controls">
              <el-input
                v-model="journalBranchId"
                size="small"
                style="width: 130px"
                placeholder="分支 (main)"
              />
              <button
                type="button"
                class="ios26-locate-btn"
                :disabled="loadingJournal"
                @click="loadStepJournal"
              >
                {{ loadingJournal ? '加载中...' : '拉取步骤日志' }}
              </button>
            </div>
          </div>

          <!-- 超步轨道与反例状态摘要 -->
          <div v-if="journalEntries.length > 0" class="tt-timeline-grid">
            <div class="tt-steps-column">
              <button
                v-for="entry in journalEntries"
                :key="entry.idempotencyKey + '_' + entry.superstep"
                type="button"
                class="tt-step-pill"
                :class="{ selected: selectedSuperstep === entry.superstep }"
                @click="selectJournalStep(entry)"
              >
                <span class="step-badge">S{{ entry.superstep }}</span>
                <span class="step-node">{{ entry.nodeId }}</span>
                <span class="step-status" :class="entry.status === 'COMMITTED' ? 'ok' : 'warn'">
                  {{ entry.status }}
                </span>
              </button>
            </div>

            <div class="tt-inspector-column">
              <div class="inspector-section">
                <div class="ios26-text-caption-1 section-cap">
                  超步 #{{ selectedSuperstep }} 增量通道补丁 (channelDeltas) 与 Fencing Token
                </div>
                <pre class="code-viewer">{{ selectedDeltaJson }}</pre>
              </div>

              <div class="inspector-section">
                <div class="ios26-text-caption-1 section-cap">
                  时间旅行覆盖补丁（JSON，用于无幽灵变量分叉新分支）
                </div>
                <el-input
                  v-model="overrideJsonText"
                  type="textarea"
                  :rows="3"
                  placeholder='{"auditNote": "repaired-at-step"}'
                />
                <div class="fork-action-row">
                  <button
                    type="button"
                    class="ios26-fork-btn"
                    :disabled="forkingBranch"
                    @click="triggerTimeTravelFork"
                  >
                    {{ forkingBranch ? '分叉重构中...' : `从超步 #${selectedSuperstep} 发起时间旅行分叉` }}
                  </button>
                </div>
              </div>

              <div v-if="forkResult" class="inspector-section fork-result-box">
                <div class="ios26-text-subheadline result-title">
                  分叉成功 · 新分支 ID: {{ forkResult.forkBranchId }} (Parent: {{ forkResult.parentBranchId }}@S{{ forkResult.fromSuperstep }})
                </div>
                <pre class="code-viewer">{{ JSON.stringify(forkResult.reconstructedState || {}, null, 2) }}</pre>
              </div>
            </div>
          </div>

          <div v-else class="tt-empty-state">
            <div class="ios26-text-body">
              当前执行轨迹 {{ currentTraceId }}（分支 {{ journalBranchId }}）暂无已持久化 Step Journal 记录
            </div>
            <div class="ios26-text-footnote">
              启动工作流超步执行或输入已运行的 Execution ID 并点击「拉取步骤日志」以检视时间旅行分叉轨道。
            </div>
          </div>
        </section>
      </div>

      <div v-else-if="activeTab === 'merkle'" class="viewport-item scrollable">
        <MerkleProofValidator />
      </div>

      <div v-else-if="activeTab === 'guardrail'" class="viewport-item scrollable">
        <GuardrailDashboard />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Share, Lock, Odometer, Search, FullScreen, Clock } from '@element-plus/icons-vue';
import ExplainabilityTopologyCanvas from './components/ExplainabilityTopologyCanvas.vue';
import MerkleProofValidator from './components/MerkleProofValidator.vue';
import GuardrailDashboard from './components/GuardrailDashboard.vue';
import { getPhdJournalTimeline, forkPhdJournalBranch } from '@/api/audit';

const activeTab = ref<'topology' | 'timetravel' | 'merkle' | 'guardrail'>('topology');

const defaultTraceId = 'exec-studio-1';
const inputTraceId = ref<string>(defaultTraceId);
const currentTraceId = ref<string>(defaultTraceId);
const dashboardRef = ref<HTMLDivElement | null>(null);

// Step Journal 时间旅行分叉状态
const journalBranchId = ref<string>('main');
const loadingJournal = ref<boolean>(false);
const forkingBranch = ref<boolean>(false);
const journalEntries = ref<any[]>([]);
const selectedEntry = ref<any | null>(null);
const selectedSuperstep = ref<number>(1);
const overrideJsonText = ref<string>('{"auditNote": "repaired-in-studio"}');
const forkResult = ref<any | null>(null);

const selectedDeltaJson = computed(() => {
  if (!selectedEntry.value) return '{}';
  return JSON.stringify(
    {
      superstep: selectedEntry.value.superstep,
      nodeId: selectedEntry.value.nodeId,
      fencingToken: selectedEntry.value.fencingToken,
      versionsSeen: selectedEntry.value.versionsSeen,
      channelDeltas: selectedEntry.value.channelDeltas,
    },
    null,
    2
  );
});

function handleTraceSearch() {
  if (inputTraceId.value.trim()) {
    currentTraceId.value = inputTraceId.value.trim();
    if (activeTab.value === 'timetravel') {
      loadStepJournal();
    }
  }
}

function selectJournalStep(entry: any) {
  selectedEntry.value = entry;
  selectedSuperstep.value = Number(entry.superstep || 0);
}

async function loadStepJournal() {
  loadingJournal.value = true;
  forkResult.value = null;
  try {
    const res: any = await getPhdJournalTimeline(currentTraceId.value, journalBranchId.value || 'main');
    const list = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : [];
    journalEntries.value = list;
    if (list.length > 0) {
      selectJournalStep(list[list.length - 1]);
    } else {
      selectedEntry.value = null;
    }
  } catch {
    journalEntries.value = [];
    selectedEntry.value = null;
  } finally {
    loadingJournal.value = false;
  }
}

async function triggerTimeTravelFork() {
  let overrides: Record<string, any> = {};
  try {
    overrides = overrideJsonText.value.trim() ? JSON.parse(overrideJsonText.value) : {};
  } catch {
    ElMessage.error('覆盖补丁必须是合法 JSON 对象');
    return;
  }
  forkingBranch.value = true;
  try {
    const res: any = await forkPhdJournalBranch({
      executionId: currentTraceId.value,
      parentBranchId: journalBranchId.value || 'main',
      fromSuperstep: selectedSuperstep.value,
      stateOverrides: overrides,
    });
    forkResult.value = res?.data || res;
    ElMessage.success('时间旅行分叉完成，已隔离生成无幽灵变量新分支');
  } catch (e: any) {
    ElMessage.error('分叉请求失败: ' + (e?.message || '请确认控制面在线'));
  } finally {
    forkingBranch.value = false;
  }
}

function toggleFullScreen() {
  if (!document.fullscreenElement) {
    dashboardRef.value?.requestFullscreen().catch((err) => {
      console.warn('全屏请求失败:', err);
    });
  } else {
    document.exitFullscreen().catch((err) => {
      console.warn('退出全屏失败:', err);
    });
  }
}

onMounted(() => {
  loadStepJournal();
});
</script>

<style scoped lang="scss">
.explainability-dashboard {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: calc(100vh - 84px);
  background: #f2f2f7;
  color: #000000;
  font-family: var(--ios26-font-family);
  overflow: hidden;
  box-sizing: border-box;
}

/* Apple iOS 26 顶部双层玻璃导航栏（blur 50px + color-dodge，零扩散阴影，容器自身无 z-index 层叠污染） */
.ios26-nav-glass {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: transparent;
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.12);
  flex-shrink: 0;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: 1;
    background: rgba(255, 255, 255, 0.25);
    mix-blend-mode: plus-lighter;
    pointer-events: none;
  }

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    z-index: 2;
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

.brand-zone {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex-shrink: 0;
}

.status-orb {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #34c759;
  flex-shrink: 0;
}

.brand-text .system-title {
  margin: 0;
  font-size: 16px;
  line-height: 21px;
  letter-spacing: -0.43px;
  font-weight: 590;
  color: #000000;
  white-space: nowrap;
}

.brand-text .system-subtitle {
  display: block;
  font-size: 11px;
  line-height: 13px;
  letter-spacing: 0.06px;
  font-weight: 400;
  color: rgba(60, 60, 67, 0.6);
  white-space: nowrap;
}

.tab-switcher {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.65);
  border: 0.5px solid rgba(0, 0, 0, 0.12);
  border-radius: 1000px;
  padding: 3px;
  gap: 2px;
  flex-shrink: 0;
}

.switch-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  background: transparent;
  border: none;
  border-radius: 1000px;
  color: #3d3d3d;
  font-size: 12px;
  font-weight: 400;
  white-space: nowrap;
  flex-shrink: 0;
  cursor: pointer;

  &.active {
    background: #0088ff;
    color: #ffffff;
  }
}

.control-zone {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.trace-input-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ios26-locate-btn {
  padding: 5px 14px;
  border-radius: 1000px;
  border: 0.5px solid #0088ff;
  background: #0088ff;
  color: #ffffff;
  font-size: 13px;
  font-weight: 400;
  white-space: nowrap;
  flex-shrink: 0;
  cursor: pointer;
}

.action-btn {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 0.5px solid rgba(0, 0, 0, 0.12);
  background: #ffffff;
  color: #3d3d3d;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.dashboard-viewport {
  flex: 1;
  position: relative;
  overflow: hidden;
  height: 100%;
}

.viewport-item {
  width: 100%;
  height: 100%;
  position: absolute;
  top: 0;
  left: 0;

  &.scrollable {
    overflow-y: auto;
  }
}

.timetravel-workspace {
  padding: 20px 24px;
  box-sizing: border-box;
}

.ios26-card-regular {
  position: relative;
  overflow: hidden;
  border-radius: 30px;
  padding: 20px 24px;
  background: transparent;
  border: 0.5px solid rgba(0, 0, 0, 0.12);

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

.tt-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.12);
}

.tt-subtitle {
  margin-top: 4px;
  color: rgba(60, 60, 67, 0.6);
  font-weight: 400;
}

.tt-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tt-timeline-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 18px;
  margin-top: 18px;
}

.tt-steps-column {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tt-step-pill {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-radius: 12px;
  border: 0.5px solid rgba(0, 0, 0, 0.12);
  background: #ffffff;
  color: #000000;
  font-size: 13px;
  font-weight: 400;
  cursor: pointer;

  &.selected {
    border-color: #0088ff;
    background: rgba(0, 136, 255, 0.08);
  }
}

.step-badge {
  padding: 2px 8px;
  border-radius: 1000px;
  background: #f2f2f7;
  color: #0088ff;
  font-size: 12px;
  font-weight: 400;
}

.step-status.ok { color: #34c759; }
.step-status.warn { color: #ff8d28; }

.tt-inspector-column {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.inspector-section {
  padding: 14px;
  border-radius: 14px;
  background: #ffffff;
  border: 0.5px solid rgba(0, 0, 0, 0.12);
}

.section-cap {
  margin-bottom: 8px;
  color: rgba(60, 60, 67, 0.6);
  font-weight: 400;
}

.code-viewer {
  margin: 0;
  font-family: "SF Mono", Menlo, monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #000000;
  white-space: pre-wrap;
}

.fork-action-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.ios26-fork-btn {
  padding: 7px 18px;
  border-radius: 1000px;
  border: 0.5px solid #0088ff;
  background: #0088ff;
  color: #ffffff;
  font-size: 13px;
  font-weight: 400;
  cursor: pointer;
}

.fork-result-box {
  border-color: rgba(52, 199, 89, 0.45);
}

.result-title {
  margin-bottom: 8px;
  color: #34c759;
  font-weight: 400;
}

.tt-empty-state {
  padding: 36px 20px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
