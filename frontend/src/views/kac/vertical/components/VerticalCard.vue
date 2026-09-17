<template>
  <div class="vertical-card-grid">
    <div
      v-for="(item, index) in data"
      :key="item.id || index"
      class="vertical-card glass-card clickable-card"
      @click="handleExperience(item)"
    >
      <div class="vertical-card-inner">
        <!-- 头部：行业徽标、名称与合规等级 -->
        <div class="card-header-row">
          <div class="industry-badge" :class="getIndustryClass(item.industry || item.type)">
            <el-icon class="badge-icon"><OfficeBuilding /></el-icon>
            <span>{{ item.industry || item.type || '垂直行业' }}</span>
          </div>
          <span class="compliance-tag">
            <span class="pulse-dot"></span>
            {{ getComplianceLabel(item) }}
          </span>
        </div>

        <!-- 应用标题与简介 -->
        <div class="card-title-section">
          <h3 class="app-title" :title="item.name">{{ item.name }}</h3>
          <p class="app-desc" :title="item.description">{{ item.description || '提供该垂直行业的专业级 AI Agent 决策与推理能力。' }}</p>
        </div>

        <!-- 关键业务 KPI 指标条 (工业级专属特色) -->
        <div class="kpi-metrics-panel" v-if="parseKpi(item).length > 0">
          <div class="kpi-metrics-title">核心业务效益指标 (KPI)</div>
          <div class="kpi-items-row">
            <div
              v-for="(kpi, kIndex) in parseKpi(item)"
              :key="kIndex"
              class="kpi-box"
            >
              <span class="kpi-value">{{ kpi.value }}</span>
              <span class="kpi-label">{{ kpi.label }}</span>
            </div>
          </div>
        </div>

        <!-- 标签与引擎模式 -->
        <div class="card-tags-row">
          <div class="tags-group">
            <span
              v-for="(tag, tIdx) in parseTags(item)"
              :key="tIdx"
              class="industry-tag"
            >
              # {{ tag }}
            </span>
          </div>
          <span class="engine-mode-pill">
            <el-icon class="mr3"><Cpu /></el-icon>
            {{ getEngineLabel(item.executionMode) }}
          </span>
        </div>

        <!-- 底部悬浮操作坞 -->
        <div class="card-action-dock" @click.stop>
          <el-button
            v-ripple
            class="glass-btn primary-action-btn"
            type="primary"
            @click="handleExperience(item)"
          >
            <el-icon class="mr4"><VideoPlay /></el-icon>
            立即演练
          </el-button>
          <el-button
            v-ripple
            class="glass-btn secondary-action-btn"
            @click="handleDetail(item)"
          >
            <el-icon class="mr4"><Document /></el-icon>
            方案白皮书
          </el-button>
        </div>
      </div>
    </div>

    <!-- 挂载工业级 AppRunnerDrawer 执行抽屉 -->
    <AppRunnerDrawer
      v-model="runnerVisible"
      :app-data="selectedApp"
    />
  </div>
</template>

<script setup name="VerticalCard">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { OfficeBuilding, Cpu, VideoPlay, Document } from "@element-plus/icons-vue";
import AppRunnerDrawer from "@/views/kac/components/runner/AppRunnerDrawer.vue";

const props = defineProps({
  data: {
    type: Array,
    default: () => [],
  },
});

const router = useRouter();
const runnerVisible = ref(false);
const selectedApp = ref(null);

function handleExperience(item) {
  selectedApp.value = item;
  runnerVisible.value = true;
}

function handleDetail(item) {
  router.push({
    path: "/kac/horizontal/horizontalDetail",
    query: { id: item.id },
  });
}

function parseKpi(item) {
  if (!item.kpiMetrics) return [];
  if (Array.isArray(item.kpiMetrics)) return item.kpiMetrics;
  try {
    const parsed = JSON.parse(item.kpiMetrics);
    return Array.isArray(parsed) ? parsed : [];
  } catch (e) {
    return [];
  }
}

function parseTags(item) {
  if (!item.tags) return [item.type || "垂直应用"];
  if (Array.isArray(item.tags)) {
    return item.tags.map((t) => (typeof t === "object" ? t.name : t));
  }
  try {
    const parsed = JSON.parse(item.tags);
    if (Array.isArray(parsed)) {
      return parsed.map((t) => (typeof t === "object" ? t.name : t));
    }
  } catch (e) {}
  return [item.type || "垂直行业"];
}

function getIndustryClass(ind) {
  const map = {
    金融: "ind-finance",
    金融科技: "ind-finance",
    医疗: "ind-medical",
    智慧医疗: "ind-medical",
    制造: "ind-manufacture",
    智能制造: "ind-manufacture",
    教育: "ind-education",
    智慧教育: "ind-education",
    政务: "ind-gov",
    数字政务: "ind-gov",
    跨境: "ind-crossborder",
    跨境电商: "ind-crossborder",
    水利: "ind-water",
    智慧水利: "ind-water",
    能源: "ind-energy",
    智慧能源: "ind-energy",
  };
  return map[ind] || "ind-default";
}

function getComplianceLabel(item) {
  const map = {
    金融科技: "金融审计合规级",
    智慧医疗: "三甲质控遵循级",
    智能制造: "工业级零误报",
    智慧教育: "课标认知图谱级",
    数字政务: "党政国标审校级",
    跨境电商: "海外合规防侵权",
    智慧水利: "防汛推演权威级",
    智慧能源: "电网调度微秒级",
  };
  return map[item.industry] || "垂直行业生产级";
}

function getEngineLabel(mode) {
  if (mode === "HERMES_AGENT") return "Hermes 认知智能体";
  if (mode === "HERMES_DAG") return "Hermes DAG 工作流";
  return "AI-Native RAG 引擎";
}
</script>

<style scoped lang="scss">
.vertical-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 20px;
  width: 100%;
}

.vertical-card {
  position: relative;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(226, 232, 240, 0.85);
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.04), 0 2px 6px -1px rgba(0, 0, 0, 0.02);
  transition: all 0.28s cubic-bezier(0.16, 1, 0.3, 1);
  cursor: pointer;
  overflow: hidden;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 16px 32px -4px rgba(15, 23, 42, 0.08), 0 6px 12px -2px rgba(15, 23, 42, 0.04);
    border-color: rgba(59, 130, 246, 0.45);
  }
}

.vertical-card-inner {
  padding: 22px 24px;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.industry-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.2px;

  &.ind-finance {
    background: rgba(16, 185, 129, 0.12);
    color: #059669;
    border: 1px solid rgba(16, 185, 129, 0.25);
  }
  &.ind-medical {
    background: rgba(239, 68, 68, 0.1);
    color: #dc2626;
    border: 1px solid rgba(239, 68, 68, 0.22);
  }
  &.ind-manufacture {
    background: rgba(245, 158, 11, 0.12);
    color: #d97706;
    border: 1px solid rgba(245, 158, 11, 0.25);
  }
  &.ind-education {
    background: rgba(147, 51, 234, 0.1);
    color: #7c3aed;
    border: 1px solid rgba(147, 51, 234, 0.22);
  }
  &.ind-gov {
    background: rgba(79, 70, 229, 0.1);
    color: #4f46e5;
    border: 1px solid rgba(79, 70, 229, 0.22);
  }
  &.ind-crossborder {
    background: rgba(236, 72, 153, 0.1);
    color: #db2777;
    border: 1px solid rgba(236, 72, 153, 0.22);
  }
  &.ind-water, &.ind-energy, &.ind-default {
    background: rgba(2, 132, 199, 0.1);
    color: #0284c7;
    border: 1px solid rgba(2, 132, 199, 0.22);
  }
}

.compliance-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 500;
  color: #475569;
  background: rgba(241, 245, 249, 0.8);
  padding: 3px 8px;
  border-radius: 6px;
  border: 1px solid rgba(203, 213, 225, 0.6);
}

.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: #10b981;
  box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7);
  animation: pulse-ring 1.8s infinite cubic-bezier(0.66, 0, 0, 1);
}

@keyframes pulse-ring {
  to {
    box-shadow: 0 0 0 5px rgba(16, 185, 129, 0);
  }
}

.card-title-section {
  margin-bottom: 16px;

  .app-title {
    font-size: 17px;
    font-weight: 700;
    color: #0f172a;
    line-height: 1.35;
    margin: 0 0 6px 0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .app-desc {
    font-size: 13px;
    color: #64748b;
    line-height: 1.55;
    margin: 0;
    height: 40px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

/* 工业级核心业务 KPI 指标条 */
.kpi-metrics-panel {
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.85) 0%, rgba(241, 245, 249, 0.7) 100%);
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 12px;
  padding: 10px 14px;
  margin-bottom: 16px;

  .kpi-metrics-title {
    font-size: 11px;
    font-weight: 600;
    color: #64748b;
    margin-bottom: 8px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  .kpi-items-row {
    display: flex;
    justify-content: space-between;
    gap: 8px;
  }

  .kpi-box {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: flex-start;

    .kpi-value {
      font-size: 15px;
      font-weight: 700;
      color: #0f172a;
      font-feature-settings: "tnum";
      line-height: 1.2;
    }

    .kpi-label {
      font-size: 11px;
      color: #94a3b8;
      margin-top: 2px;
      white-space: nowrap;
    }
  }
}

.card-tags-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;

  .tags-group {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .industry-tag {
    font-size: 11px;
    color: #64748b;
    background: rgba(241, 245, 249, 0.7);
    padding: 2px 7px;
    border-radius: 4px;
  }

  .engine-mode-pill {
    display: inline-flex;
    align-items: center;
    font-size: 11px;
    color: #3b82f6;
    font-weight: 500;
    background: rgba(59, 130, 246, 0.08);
    padding: 3px 8px;
    border-radius: 6px;
    border: 1px solid rgba(59, 130, 246, 0.15);
  }
}

.card-action-dock {
  display: flex;
  gap: 10px;
  margin-top: auto;

  .primary-action-btn {
    flex: 1;
    height: 34px;
    border-radius: 8px;
    font-weight: 600;
    font-size: 13px;
    background: #2563eb;
    border-color: #2563eb;
    transition: all 0.2s ease;

    &:hover {
      background: #1d4ed8;
      transform: scale(1.02);
    }
  }

  .secondary-action-btn {
    flex: 1;
    height: 34px;
    border-radius: 8px;
    font-size: 13px;
    color: #475569;
    background: rgba(255, 255, 255, 0.85);
    border: 1px solid #cbd5e1;
    transition: all 0.2s ease;

    &:hover {
      background: #f8fafc;
      color: #0f172a;
      border-color: #94a3b8;
    }
  }
}

.mr3 {
  margin-right: 3px;
}
.mr4 {
  margin-right: 4px;
}
</style>
