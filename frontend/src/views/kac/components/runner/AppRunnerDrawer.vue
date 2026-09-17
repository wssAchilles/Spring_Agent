<template>
  <el-drawer
    v-model="visible"
    :size="isFullScreen ? '100%' : '820px'"
    direction="rtl"
    :with-header="false"
    class="app-runner-drawer glass-drawer"
    custom-class="app-runner-drawer glass-drawer"
    :destroy-on-close="false"
    append-to-body
    :lock-scroll="true"
  >
    <div class="runner-container" @keydown="handleGlobalKeydown">
      <!-- 抽屉头部 Header -->
      <header class="runner-header glass-header">
        <div class="header-left">
          <div class="app-avatar">
            <el-icon :size="24" color="#0052ff">
              <component :is="getIconComponent(currentApp?.icon)" />
            </el-icon>
          </div>
          <div class="app-meta">
            <div class="title-row">
              <h2>{{ currentApp?.name || "应用工作台" }}</h2>
              <span class="status-indicator online">
                <i></i> 在线可运行
              </span>
              <span class="industry-chip" v-if="currentApp?.industry || currentApp?.type">
                {{ currentApp?.industry || currentApp?.type }}
              </span>
              <span class="compliance-chip" v-if="currentApp?.complianceLevel">
                {{ currentApp?.complianceLevel }}
              </span>
            </div>
            <p class="app-desc">{{ currentApp?.description || "知识应用执行中枢与认知决策平台" }}</p>
          </div>
        </div>

        <div class="header-actions">
          <el-tooltip content="重置所有参数" placement="bottom">
            <el-button
              v-ripple
              circle
              size="small"
              class="action-btn"
              @click="resetInputs"
            >
              <el-icon><RefreshRight /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="执行历史" placement="bottom">
            <el-button
              v-ripple
              circle
              size="small"
              class="action-btn"
              @click="openHistory"
            >
              <el-icon><Clock /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip :content="isFullScreen ? '还原' : '全屏沉浸'" placement="bottom">
            <el-button
              v-ripple
              circle
              size="small"
              class="action-btn"
              @click="isFullScreen = !isFullScreen"
            >
              <el-icon><FullScreen v-if="!isFullScreen" /><Aim v-else /></el-icon>
            </el-button>
          </el-tooltip>
          <el-button
            v-ripple
            circle
            size="small"
            class="action-btn close-btn"
            @click="visible = false"
          >
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </header>

      <!-- 主体交互内容区 (垂直自适应防溢出容器) -->
      <div class="runner-body" ref="runnerBodyRef">
        <!-- 上部分：业务入参配置与认知超参微调中枢 -->
        <section class="config-section glass-card">
          <div class="section-title-row">
            <div class="title-left">
              <span class="section-title">
                <el-icon><Operation /></el-icon>
                业务入参与执行配置
              </span>
              <span class="section-subtitle">注入业务上下文或针对性需求指令</span>
            </div>

            <!-- 快捷场景标签胶囊组 -->
            <div v-if="presetScenarios.length" class="presets-row">
              <span class="preset-label">快捷场景代入:</span>
              <el-tag
                v-for="(preset, idx) in presetScenarios"
                :key="idx"
                size="small"
                class="preset-tag"
                @click="applyPreset(preset)"
              >
                {{ preset.label }}
              </el-tag>
            </div>
          </div>

          <!-- 动态表单工作区 -->
          <div class="form-wrapper">
            <DynamicParamForm
              :schema="currentApp?.inputSchema"
              v-model="formInputs"
            />
          </div>

          <!-- 认知推理与 RAG 检索策略微调面板 (折叠式高阶卡片) -->
          <div class="advanced-params-panel">
            <div class="advanced-toggle-bar" @click="showAdvanced = !showAdvanced">
              <div class="toggle-title">
                <el-icon><Setting /></el-icon>
                <span>认知模型与知识检索超参微调 (可选)</span>
                <span class="toggle-badge">{{ showAdvanced ? '收起配置' : '展开微调' }}</span>
              </div>
              <div class="toggle-summary" v-if="!showAdvanced">
                <span class="summary-pill">温度: {{ advancedParams.temperature }}</span>
                <span class="summary-pill">R1思考: {{ advancedParams.reasoningEffort ? '开启' : '关闭' }}</span>
                <span class="summary-pill">千问RAG阈值: {{ advancedParams.ragThreshold }}</span>
                <span class="summary-pill">最大Tokens: {{ advancedParams.maxTokens }}</span>
              </div>
              <el-icon class="toggle-arrow" :class="{ 'is-active': showAdvanced }">
                <ArrowDown v-if="!showAdvanced" />
                <ArrowUp v-else />
              </el-icon>
            </div>

            <div class="advanced-content" v-show="showAdvanced">
              <div class="params-row">
                <div class="param-col">
                  <div class="param-label-row">
                    <span class="p-label">推理随机度 (Temperature): {{ advancedParams.temperature }}</span>
                    <span class="p-tip">0.0 严谨确定性 ↔ 1.0 创意发散</span>
                  </div>
                  <el-slider
                    v-model="advancedParams.temperature"
                    :min="0"
                    :max="1"
                    :step="0.05"
                    class="glass-slider"
                  />
                </div>

                <div class="param-col">
                  <div class="param-label-row">
                    <span class="p-label">千问 RAG 超球面召回阈值: {{ advancedParams.ragThreshold }}</span>
                    <span class="p-tip">过滤无关噪音，严密对齐领域知识</span>
                  </div>
                  <el-slider
                    v-model="advancedParams.ragThreshold"
                    :min="0.5"
                    :max="0.95"
                    :step="0.05"
                    class="glass-slider"
                  />
                </div>
              </div>

              <div class="params-row mt-3">
                <div class="param-col param-col-inline">
                  <span class="p-label">DeepSeek R1 深度思维链 (CoT):</span>
                  <el-switch
                    v-model="advancedParams.reasoningEffort"
                    inline-prompt
                    active-text="开启"
                    inactive-text="关闭"
                  />
                </div>

                <div class="param-col param-col-inline">
                  <span class="p-label">最大输出 Token 预算:</span>
                  <el-select
                    v-model="advancedParams.maxTokens"
                    size="small"
                    style="width: 140px"
                    class="glass-select"
                  >
                    <el-option label="1024 tokens" :value="1024" />
                    <el-option label="2048 tokens" :value="2048" />
                    <el-option label="4096 tokens" :value="4096" />
                    <el-option label="8192 tokens" :value="8192" />
                  </el-select>
                </div>
              </div>
            </div>
          </div>

          <!-- 操作动作栏 -->
          <div class="submit-action-bar">
            <div class="action-left-info">
              <span class="runtime-tag" v-if="currentApp?.executionMode">
                <el-icon><Cpu /></el-icon> {{ currentApp?.executionMode }}
              </span>
              <span class="runtime-tip">
                支持通过 <kbd>Ctrl</kbd> + <kbd>Enter</kbd> 快捷启动
              </span>
            </div>

            <div class="action-right-buttons">
              <el-button
                v-if="!isRunning"
                v-ripple
                size="large"
                class="reset-btn glass-btn"
                @click="resetInputs"
              >
                <el-icon><RefreshRight /></el-icon>
                重置
              </el-button>

              <el-button
                v-if="!isRunning"
                v-ripple
                type="primary"
                size="large"
                class="run-submit-btn glass-btn-primary"
                @click="startRun"
              >
                <el-icon><VideoPlay /></el-icon>
                立即运行
              </el-button>
              <el-button
                v-else
                v-ripple
                type="danger"
                size="large"
                class="run-submit-btn stop-btn"
                @click="stopRun"
              >
                <el-icon class="is-loading"><Loading /></el-icon>
                中止生成
              </el-button>
            </div>
          </div>
        </section>

        <!-- 下部分（未执行形态）：AI-Native 认知执行流水线蓝图与能力规格看板 (2x2 防溢出排版) -->
        <section class="guide-section glass-card" v-if="!hasRunEver && !isRunning">
          <!-- 流水线 DAG 状态栏 -->
          <div class="pipeline-header">
            <div class="pipeline-title-group">
              <span class="section-title">
                <el-icon><Connection /></el-icon>
                AI-Native 认知执行流水线 (Pipeline DAG)
              </span>
              <span class="section-subtitle">端到端自闭环认知推理与凭单审计留存</span>
            </div>
            <span class="pipeline-status-badge">
              <span class="pulse-dot"></span> 全链路就绪待触发
            </span>
          </div>

          <!-- 2x2 网格排版，杜绝横向撑破与右溢出 -->
          <div class="pipeline-steps-grid">
            <div class="pipeline-step-card active">
              <div class="step-card-header">
                <span class="step-num">01</span>
                <span class="step-phase-tag">阶段一 · 输入校验</span>
                <span class="step-status-tag ready">就绪</span>
              </div>
              <div class="step-content">
                <div class="step-title">入参结构校验与业务对齐</div>
                <div class="step-desc">动态 Schema 键值对过滤、敏感校验与业务上下文融合注入</div>
              </div>
            </div>

            <div class="pipeline-step-card">
              <div class="step-card-header">
                <span class="step-num">02</span>
                <span class="step-phase-tag">阶段二 · 知识检索</span>
                <span class="step-status-tag pending">待触发</span>
              </div>
              <div class="step-content">
                <div class="step-title">千问 1536 维超球面召回</div>
                <div class="step-desc">本地高保真向量语义召回、知识图谱关联对齐与跨库评分重排</div>
              </div>
            </div>

            <div class="pipeline-step-card">
              <div class="step-card-header">
                <span class="step-num">03</span>
                <span class="step-phase-tag">阶段三 · 认知推理</span>
                <span class="step-status-tag pending">待触发</span>
              </div>
              <div class="step-content">
                <div class="step-title">DeepSeek 深度认知推理</div>
                <div class="step-desc">DeepSeek R1 深度思维链规划、复杂场景决策分析与高保真流式生成</div>
              </div>
            </div>

            <div class="pipeline-step-card">
              <div class="step-card-header">
                <span class="step-num">04</span>
                <span class="step-phase-tag">阶段四 · 合规存证</span>
                <span class="step-status-tag pending">待入库</span>
              </div>
              <div class="step-content">
                <div class="step-title">密码学凭单存证留存</div>
                <div class="step-desc">生成包含输入哈希、耗时指标与 SHA-256 签名不可篡改审计凭单</div>
              </div>
            </div>
          </div>

          <!-- 平台运行环境与规格指标矩阵 (2x2 网格) -->
          <div class="specs-grid">
            <div class="spec-card">
              <div class="spec-icon-wrap model-icon">
                <el-icon><Cpu /></el-icon>
              </div>
              <div class="spec-info">
                <div class="spec-label">唯一生成模型基线</div>
                <div class="spec-value">DeepSeek API (V3 / R1 旗舰推理)</div>
              </div>
            </div>

            <div class="spec-card">
              <div class="spec-icon-wrap vector-icon">
                <el-icon><DataAnalysis /></el-icon>
              </div>
              <div class="spec-info">
                <div class="spec-label">唯一高维向量底座</div>
                <div class="spec-value">阿里千问 1536 维超球面向量</div>
              </div>
            </div>

            <div class="spec-card">
              <div class="spec-icon-wrap stream-icon">
                <el-icon><Lightning /></el-icon>
              </div>
              <div class="spec-info">
                <div class="spec-label">流式打字机传输通道</div>
                <div class="spec-value">SSE 极低时延流式打字机总线</div>
              </div>
            </div>

            <div class="spec-card">
              <div class="spec-icon-wrap receipt-icon">
                <el-icon><DocumentChecked /></el-icon>
              </div>
              <div class="spec-info">
                <div class="spec-label">不可篡改审计凭单</div>
                <div class="spec-value">SHA-256 密码学不可篡改存证凭据</div>
              </div>
            </div>
          </div>

          <!-- 典型业务演练案例快捷卡片组 -->
          <div class="quick-scenarios-section" v-if="presetScenarios.length">
            <div class="scenarios-header">
              <span class="scenarios-title">
                <el-icon><Opportunity /></el-icon>
                典型业务场景示范 (点击卡片可快速代入参数并测试)
              </span>
            </div>
            <div class="quick-scenarios-grid">
              <div
                v-for="(preset, idx) in presetScenarios"
                :key="idx"
                class="quick-scenario-card"
                @click="applyPreset(preset)"
              >
                <div class="scenario-card-header">
                  <span class="scenario-name">{{ preset.label }}</span>
                  <el-button link size="small" type="primary" class="apply-btn">
                    一键代入 <el-icon><ArrowRight /></el-icon>
                  </el-button>
                </div>
                <p class="scenario-snippet">
                  {{ getPresetSnippet(preset.inputs) }}
                </p>
              </div>
            </div>
          </div>
        </section>

        <!-- 下部分（执行中/已执行形态）：实时流式输出与结果展示 -->
        <section class="output-section glass-card" v-if="hasRunEver || isRunning">
          <div class="output-header">
            <div class="output-title-group">
              <span class="section-title">
                <el-icon><Cpu /></el-icon>
                实时推理与结果视窗
              </span>
              <span class="running-status-tag" v-if="isRunning">
                <span class="pulse-dot"></span> 流式生成中
              </span>
              <span class="completed-status-tag" v-else>
                <el-icon><CircleCheck /></el-icon> 推理完毕
              </span>
            </div>

            <div class="output-actions" v-if="outputContent">
              <el-button
                v-ripple
                size="small"
                link
                icon="CopyDocument"
                @click="copyResult"
              >
                复制全文
              </el-button>
              <el-button
                v-ripple
                size="small"
                link
                icon="Download"
                @click="downloadMarkdown"
              >
                导出报告
              </el-button>
            </div>
          </div>

          <!-- DeepSeek R1 深度思考链折叠面板 -->
          <el-collapse v-if="thinkingContent" v-model="activeCollapse" class="thinking-collapse">
            <el-collapse-item name="thinking">
              <template #title>
                <div class="collapse-title">
                  <el-icon class="is-loading" v-if="isRunning && !outputContent"><Loading /></el-icon>
                  <el-icon v-else><Opportunity /></el-icon>
                  <span>DeepSeek R1 深度思维链 (Reasoning Chain)</span>
                </div>
              </template>
              <div class="thinking-box">
                <pre class="thinking-text">{{ thinkingContent }}</pre>
              </div>
            </el-collapse-item>
          </el-collapse>

          <!-- 知识库召回来源提示 -->
          <div v-if="ragSources.length" class="rag-sources-bar">
            <span class="rag-label">命中本地知识库:</span>
            <el-tag
              v-for="(source, i) in ragSources"
              :key="i"
              size="small"
              type="info"
              class="rag-tag"
            >
              知识库 #{{ source.kbId }} (相关度: {{ (source.score || 0.85).toFixed(2) }})
            </el-tag>
          </div>

          <!-- 实时生成文本正文 (打字机效果与富文本渲染) -->
          <div class="terminal-content" ref="terminalRef" @click="handleTerminalClick">
            <div v-if="!outputContent && isRunning" class="running-indicator">
              <span class="dot-flashing"></span>
              <span class="text">正在与 DeepSeek API 深度对齐并流式推理中...</span>
            </div>

            <div v-else class="markdown-body" v-html="renderedOutputHtml"></div>
          </div>

          <!-- 底部指标卡片 -->
          <footer class="output-footer" v-if="metrics.totalTokens || metrics.latencyMs">
            <div class="metrics-grid">
              <span class="metric-item">
                <el-icon><Timer /></el-icon> 耗时: <strong>{{ metrics.latencyMs }}ms</strong>
              </span>
              <span class="metric-item">
                <el-icon><Coin /></el-icon> Token: <strong>{{ metrics.totalTokens }}</strong> (输入: {{ metrics.promptTokens }} / 输出: {{ metrics.completionTokens }})
              </span>
              <span class="metric-item receipt-badge" v-if="metrics.receiptId">
                凭单: <code>{{ metrics.receiptId }}</code>
              </span>
            </div>
          </footer>
        </section>
      </div>
    </div>

    <!-- 历史抽屉子组件 -->
    <RunHistoryDrawer ref="historyDrawerRef" @replay="handleReplayInputs" />
  </el-drawer>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from "vue";
import {
  Operation,
  VideoPlay,
  VideoPause,
  Cpu,
  CopyDocument,
  Download,
  Clock,
  FullScreen,
  Aim,
  Close,
  Loading,
  Opportunity,
  Timer,
  Coin,
  Edit,
  Search,
  Document,
  Connection,
  ChatDotRound,
  Calendar,
  DataAnalysis,
  Right,
  ArrowRight,
  Setting,
  ArrowDown,
  ArrowUp,
  CircleCheck,
  RefreshRight,
  Lightning,
  DocumentChecked,
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import DynamicParamForm from "@/views/kac/components/schemaForm/DynamicParamForm.vue";
import RunHistoryDrawer from "@/views/kac/components/history/RunHistoryDrawer.vue";
import { runApply } from "@/api/kac/apply/apply.js";
import { defaultStreamingEngine } from "@/utils/streaming-markdown-engine.js";

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  appData: {
    type: Object,
    default: null,
  },
});

const emit = defineEmits(["update:modelValue"]);

const visible = ref(false);
const isFullScreen = ref(false);
const isRunning = ref(false);
const hasRunEver = ref(false);
const currentApp = ref(null);
const showAdvanced = ref(false);
const runnerBodyRef = ref(null);

// 高阶认知推理超参微调
const advancedParams = reactive({
  temperature: 0.3,
  reasoningEffort: true,
  ragThreshold: 0.80,
  maxTokens: 2048,
});

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      open(props.appData);
    } else {
      visible.value = false;
    }
  }
);

watch(visible, (val) => {
  emit("update:modelValue", val);
});

const formInputs = ref({});
const activeCollapse = ref(["thinking"]);

const thinkingContent = ref("");
const outputContent = ref("");
const ragSources = ref([]);

const renderedOutputHtml = computed(() => {
  if (!outputContent.value) return "";
  const res = defaultStreamingEngine.renderStream(outputContent.value, !isRunning.value);
  return res.html || "";
});

const metrics = reactive({
  latencyMs: 0,
  promptTokens: 0,
  completionTokens: 0,
  totalTokens: 0,
  receiptId: "",
});

const terminalRef = ref(null);
const historyDrawerRef = ref(null);
let abortController = null;

const iconMap = {
  Edit,
  Search,
  Document,
  Connection,
  Aim,
  ChatDotRound,
  Calendar,
  DataAnalysis,
};

function getIconComponent(iconName) {
  return iconMap[iconName] || Document;
}

// 预设场景库
const presetScenarios = ref([]);

function open(app) {
  currentApp.value = app;
  formInputs.value = {};
  thinkingContent.value = "";
  outputContent.value = "";
  ragSources.value = [];
  metrics.latencyMs = 0;
  metrics.promptTokens = 0;
  metrics.completionTokens = 0;
  metrics.totalTokens = 0;
  metrics.receiptId = "";
  hasRunEver.value = false;
  showAdvanced.value = false;
  visible.value = true;

  // 根据应用类型注入丰富预设场景
  setupPresets(app);

  // 保证打开抽屉时光标与视口始终在最顶部，杜绝上推隐藏
  nextTick(() => {
    if (runnerBodyRef.value) {
      runnerBodyRef.value.scrollTop = 0;
    }
  });
}

function setupPresets(app) {
  const name = app?.name || "";
  const industry = app?.industry || app?.type || "";

  if (name.includes("风控") || industry.includes("金融")) {
    presetScenarios.value = [
      {
        label: "大额离岸异常交易审计",
        inputs: {
          scene: "跨境对公大额划转",
          accountFeatures: "新设自贸区账户，开户不足7天累计出境资金超 800 万美元，夜间多笔突发分流",
          complianceLevel: "高风险穿透核查 (深度阻断)",
          query: "针对新设立账户短期内高密、大额离岸跨境汇款行为，调取反洗钱可疑交易知识图谱，输出全流程合规审计核验与资金冻结建议报告。",
        },
      },
      {
        label: "小微信贷流水欺诈侦测",
        inputs: {
          scene: "供应链普惠金融信贷预审",
          accountFeatures: "对公进项增值税发票与银行实际流水时序不吻合，疑似虚假循环贸易做流水",
          complianceLevel: "严格审查级",
          query: "深度核查该企业近期上下游交易对手关系网，排查是否存在关联空壳公司自循环过桥，研判信贷欺诈风险。",
        },
      },
    ];
  } else if (name.includes("医疗") || industry.includes("医疗")) {
    presetScenarios.value = [
      {
        label: "罕见合并症用药配伍禁忌",
        inputs: {
          disease: "慢性肾功能不全 4 期合并急性心力衰竭",
          medicationPlan: "拟联用强心苷类、保钾利尿剂与新型降糖药 SGLT2i",
          auditStandard: "三甲医院临床用药指南核心规范",
          query: "排查该用药方案中可能诱发的高钾血症及电解质失衡风险，检索权威临床指南给出剂量调整与监护方案。",
        },
      },
      {
        label: "疑难病历内涵质控抽查",
        inputs: {
          disease: "重症肺炎合并脓毒症休克",
          medicationPlan: "抗感染方案升级至碳青霉烯类，但未按规范在24小时内留取病原学微生物培养标本",
          auditStandard: "三级公立医院绩效考核内涵质控标准",
          query: "审查病历书写合规性，标注未闭环环节并给出主治医生质控缺陷扣分提示与整改意见。",
        },
      },
    ];
  } else if (name.includes("制造") || industry.includes("制造")) {
    presetScenarios.value = [
      {
        label: "精密半导体引脚虚焊诊断",
        inputs: {
          defectType: "BGA 阵列局部微观空洞率超标 (28%)",
          sensorData: "X-ray 三维断层成像及热阻测试温升异常，回流焊峰值温度245℃，保温时间不足",
          query: "结合表面微观力学与回流焊温控工艺知识库，精准推导虚焊与空洞机理，给出炉温曲线补偿参数。",
        },
      },
      {
        label: "工业机械轴承微裂纹预警",
        inputs: {
          defectType: "主传动轴外圈高频冲击谐波震颤",
          sensorData: "加速度传感器有效值超标3.2倍，伴随微观声发射异常能量脉冲",
          query: "根据轴承退化振动频谱知识图谱，计算剩余有效运行寿命 (RUL) 并生成预防性维护工单。",
        },
      },
    ];
  } else if (name.includes("政务") || industry.includes("政务")) {
    presetScenarios.value = [
      {
        label: "规范性公文合规性审查",
        inputs: {
          documentTitle: "关于进一步优化企业营商环境若干措施的实施细则 (送审稿)",
          checkPoints: "对照上位法《优化营商环境条例》、党政公文格式国标及反垄断公平竞争审查要求",
          query: "全面审查该规范性公文草案的政治口径、权责边界、格式规约与条文逻辑，列出修改对照表。",
        },
      },
    ];
  } else if (name.includes("教育") || industry.includes("教育")) {
    presetScenarios.value = [
      {
        label: "学情薄弱点归因与梯度组卷",
        inputs: {
          subject: "高中数学 (解析几何与向量融合)",
          studentProfile: "椭圆标准方程熟练，但在定点定值与多变量代数变形转化模块得分率低于35%",
          query: "依托认知知识图谱定位思维断点，针对性生成3道难度呈阶梯上升的变式诊断题与思维导引。",
        },
      },
    ];
  } else if (name.includes("跨境") || industry.includes("电商")) {
    presetScenarios.value = [
      {
        label: "北美站点外观专利侵权预警",
        inputs: {
          targetPlatform: "Amazon US & TikTok Shop US",
          productFeatures: "一款自带磁吸无线充电支架的桌面多功能音箱，外观轮廓与USPTO既有专利高度近似",
          query: "检索知识库中的海外知识产权判例，评估被下架封店风险概率，并提出局部外观规避设计方案。",
        },
      },
    ];
  } else if (name.includes("摘要")) {
    presetScenarios.value = [
      {
        label: "AI-Native 平台战略架构长文摘要",
        inputs: {
          documentContent: "企业级 AI-Native 知识库平台以企业多源知识资产为核心，打通高保真 RAG 混合检索、多智能体协同编排与企业级 MCP 工具生态。系统通过引入统一的应用中心运行时，支持动态 Schema 参数解析与不可篡改审计凭单沉淀，显著降低生产环境的不确定性与工程调试成本。",
          summaryLength: "中等 (300-500字精炼版)",
          query: "请对上述 AI-Native 架构长文提取三大核心技术支柱，并以结构化 Markdown 形式输出提炼报告。",
        },
      },
    ];
  } else {
    presetScenarios.value = [
      {
        label: "标准业务示范演练",
        inputs: {
          query: "请结合当前应用所绑定的专业知识库与推理模型，执行标准分析流程，输出具备决策价值的专业报告。",
        },
      },
    ];
  }
}

function getPresetSnippet(inputs) {
  if (!inputs) return "点击快速将典型参数注入表单...";
  const val = inputs.query || inputs.documentContent || Object.values(inputs)[0] || "";
  return val.length > 80 ? val.substring(0, 80) + "..." : val;
}

function applyPreset(preset) {
  formInputs.value = { ...formInputs.value, ...preset.inputs };
  ElMessage.success(`已代入「${preset.label}」示范参数`);
  nextTick(() => {
    runnerBodyRef.value?.scrollTo({ top: 0, behavior: "smooth" });
  });
}

function resetInputs() {
  formInputs.value = {};
  setupPresets(currentApp.value);
  ElMessage.info("已清空输入参数并恢复初始状态");
}

function handleGlobalKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
    if (visible.value && !isRunning.value) {
      e.preventDefault();
      startRun();
    }
  }
}

async function startRun() {
  if (!currentApp.value?.id) {
    ElMessage.warning("应用信息异常");
    return;
  }

  isRunning.value = true;
  hasRunEver.value = true;
  thinkingContent.value = "正在解析应用输入参数与上下文，规划推理步骤并构建知识关联网络...\n";
  outputContent.value = "";
  ragSources.value = [];
  abortController = new AbortController();

  try {
    const res = await runApply({
      applyId: currentApp.value.id,
      inputs: formInputs.value,
      stream: false,
      modelOverrides: {
        temperature: advancedParams.temperature,
        reasoningEffort: advancedParams.reasoningEffort,
        ragThreshold: advancedParams.ragThreshold,
        maxTokens: advancedParams.maxTokens,
      },
    });

    const data = res.data;
    if (data) {
      if (data.reasoningContent) {
        thinkingContent.value += data.reasoningContent;
      }
      outputContent.value = data.outputContent || "";
      ragSources.value = data.ragSources || [];
      metrics.latencyMs = data.latencyMs || 0;
      metrics.promptTokens = data.promptTokens || 0;
      metrics.completionTokens = data.completionTokens || 0;
      metrics.totalTokens = data.totalTokens || 0;
      metrics.receiptId = data.receiptId || "";
    }
  } catch (err) {
    ElMessage.error(err.message || "运行遇到异常");
  } finally {
    isRunning.value = false;
    scrollToBottom();
  }
}

function stopRun() {
  if (abortController) {
    abortController.abort();
  }
  isRunning.value = false;
  ElMessage.info("已中止生成");
}

function scrollToBottom() {
  nextTick(() => {
    if (terminalRef.value) {
      terminalRef.value.scrollTop = terminalRef.value.scrollHeight;
    }
  });
}

function copyResult() {
  if (!outputContent.value) return;
  navigator.clipboard.writeText(outputContent.value).then(() => {
    ElMessage.success("已复制全文到剪贴板");
  });
}

function downloadMarkdown() {
  if (!outputContent.value) return;
  const blob = new Blob([outputContent.value], { type: "text/markdown;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `${currentApp.value?.name || "执行报告"}_${Date.now()}.md`;
  a.click();
  URL.revokeObjectURL(url);
  ElMessage.success("报告已导出");
}

function handleTerminalClick(e) {
  const target = e.target;
  if (target && target.classList && target.classList.contains("copy-code-action")) {
    const rawCode = decodeURIComponent(target.getAttribute("data-copy") || "");
    if (rawCode) {
      navigator.clipboard.writeText(rawCode).then(() => {
        ElMessage.success("代码片段已复制到剪贴板");
      }).catch(() => {
        ElMessage.warning("复制失败，请手动选择复制");
      });
    }
  }
}

function openHistory() {
  historyDrawerRef.value?.open(currentApp.value?.id);
}

function handleReplayInputs(inputs) {
  formInputs.value = { ...inputs };
}

defineExpose({ open });
</script>

<style scoped lang="scss">
.app-runner-drawer {
  background: #f8fafc;
}

.runner-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100%;
  background: #f8fafc;
  color: #0f172a;
  outline: none;
  box-sizing: border-box;
  overflow: hidden;
}

.runner-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.85);
  flex-shrink: 0;
  width: 100%;
  box-sizing: border-box;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
    flex: 1;

    .app-avatar {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: linear-gradient(135deg, rgba(0, 82, 255, 0.1) 0%, rgba(0, 82, 255, 0.04) 100%);
      border: 1px solid rgba(0, 82, 255, 0.15);
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 2px 8px rgba(0, 82, 255, 0.06);
      flex-shrink: 0;
    }

    .app-meta {
      min-width: 0;
      flex: 1;

      .title-row {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 8px;

        h2 {
          font-size: 16px;
          font-weight: 600;
          color: #0f172a;
          margin: 0;
          letter-spacing: -0.2px;
        }

        .status-indicator {
          font-size: 11px;
          color: #10b981;
          display: inline-flex;
          align-items: center;
          gap: 5px;
          background: rgba(16, 185, 129, 0.08);
          border: 1px solid rgba(16, 185, 129, 0.25);
          padding: 2px 8px;
          border-radius: 12px;
          font-weight: 500;

          i {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: #10b981;
            box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.25);
          }
        }

        .industry-chip {
          font-size: 11px;
          color: #0052ff;
          background: rgba(0, 82, 255, 0.06);
          border: 1px solid rgba(0, 82, 255, 0.18);
          padding: 2px 8px;
          border-radius: 12px;
          font-weight: 500;
        }

        .compliance-chip {
          font-size: 11px;
          color: #475569;
          background: rgba(241, 245, 249, 0.8);
          border: 1px solid rgba(203, 213, 225, 0.8);
          padding: 2px 8px;
          border-radius: 12px;
          font-weight: 500;
        }
      }

      .app-desc {
        font-size: 12px;
        color: #64748b;
        margin: 3px 0 0 0;
        line-height: 1.4;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }
  }

  .header-actions {
    display: flex;
    gap: 8px;
    flex-shrink: 0;

    .action-btn {
      background: rgba(255, 255, 255, 0.9);
      border: 1px solid #cbd5e1;
      color: #475569;
      transition: all 0.2s;

      &:hover {
        color: #0052ff;
        border-color: #0052ff;
        background: #ffffff;
      }
    }
  }
}

.runner-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  box-sizing: border-box;
}

.glass-card {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 4px 20px -2px rgba(15, 23, 42, 0.05);
  border-radius: 12px;
  padding: 16px 18px;
  width: 100%;
  box-sizing: border-box;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;

  .title-left {
    display: flex;
    align-items: center;
    gap: 8px;

    .section-title {
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }

    .section-subtitle {
      font-size: 11.5px;
      color: #94a3b8;
    }
  }

  .presets-row {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;

    .preset-label {
      font-size: 11.5px;
      color: #64748b;
      font-weight: 500;
    }

    .preset-tag {
      cursor: pointer;
      background: rgba(0, 82, 255, 0.06);
      color: #0052ff;
      border: 1px solid rgba(0, 82, 255, 0.2);
      border-radius: 6px;
      transition: all 0.2s ease;
      font-weight: 500;

      &:hover {
        background: #0052ff;
        color: #fff;
        transform: translateY(-1px);
        box-shadow: 0 2px 8px rgba(0, 82, 255, 0.25);
      }
    }
  }
}

.form-wrapper {
  margin-bottom: 12px;
  width: 100%;
}

/* 高级超参微调折叠面板 */
.advanced-params-panel {
  background: rgba(248, 250, 252, 0.75);
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 10px;
  margin-bottom: 12px;
  overflow: hidden;
  transition: all 0.2s ease;
  width: 100%;
  box-sizing: border-box;

  .advanced-toggle-bar {
    padding: 9px 12px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    cursor: pointer;
    user-select: none;
    background: rgba(241, 245, 249, 0.5);

    &:hover {
      background: rgba(241, 245, 249, 0.85);
    }

    .toggle-title {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      font-weight: 600;
      color: #334155;

      .toggle-badge {
        font-size: 10.5px;
        color: #0052ff;
        background: rgba(0, 82, 255, 0.08);
        padding: 1px 6px;
        border-radius: 4px;
        font-weight: 500;
      }
    }

    .toggle-summary {
      display: flex;
      align-items: center;
      gap: 6px;

      .summary-pill {
        font-size: 10.5px;
        color: #64748b;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        padding: 1px 5px;
        border-radius: 4px;
      }
    }

    .toggle-arrow {
      color: #94a3b8;
      font-size: 12px;
      transition: transform 0.2s ease;

      &.is-active {
        color: #0052ff;
      }
    }
  }

  .advanced-content {
    padding: 12px 14px;
    background: #ffffff;
    border-top: 1px solid rgba(226, 232, 240, 0.8);

    .params-row {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 16px;

      &.mt-3 {
        margin-top: 12px;
      }

      .param-col {
        display: flex;
        flex-direction: column;
        gap: 4px;
        min-width: 0;

        &.param-col-inline {
          flex-direction: row;
          align-items: center;
          justify-content: space-between;
          background: #f8fafc;
          padding: 6px 10px;
          border-radius: 6px;
          border: 1px solid #e2e8f0;
        }

        .param-label-row {
          display: flex;
          justify-content: space-between;
          align-items: center;

          .p-label {
            font-size: 11.5px;
            font-weight: 600;
            color: #1e293b;
          }

          .p-tip {
            font-size: 10.5px;
            color: #94a3b8;
          }
        }
      }
    }
  }
}

.submit-action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 2px;
  width: 100%;
  box-sizing: border-box;

  .action-left-info {
    display: flex;
    align-items: center;
    gap: 10px;

    .runtime-tag {
      font-size: 11.5px;
      color: #0052ff;
      background: rgba(0, 82, 255, 0.06);
      border: 1px solid rgba(0, 82, 255, 0.2);
      padding: 3px 8px;
      border-radius: 6px;
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-weight: 500;
    }

    .runtime-tip {
      font-size: 11.5px;
      color: #94a3b8;

      kbd {
        padding: 1px 4px;
        font-size: 10.5px;
        color: #475569;
        background: #f1f5f9;
        border: 1px solid #cbd5e1;
        border-radius: 4px;
      }
    }
  }

  .action-right-buttons {
    display: flex;
    align-items: center;
    gap: 8px;

    .reset-btn {
      padding: 8px 14px;
      border-radius: 8px;
      font-weight: 500;
      color: #64748b;
      border: 1px solid #cbd5e1;

      &:hover {
        color: #0f172a;
        border-color: #94a3b8;
      }
    }

    .run-submit-btn {
      padding: 8px 28px;
      border-radius: 8px;
      font-weight: 600;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all 0.25s ease;

      &.glass-btn-primary {
        background: linear-gradient(135deg, #0052ff 0%, #0045d8 100%);
        border: 1px solid #0052ff;
        box-shadow: 0 4px 14px rgba(0, 82, 255, 0.35);

        &:hover {
          background: linear-gradient(135deg, #0045d8 0%, #0036aa 100%);
          transform: translateY(-1px);
          box-shadow: 0 6px 18px rgba(0, 82, 255, 0.4);
        }
      }
    }
  }
}

/* 下半部分：未运行形态下的流水线与规格看板 (2x2 自适应网格防溢出) */
.guide-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: rgba(255, 255, 255, 0.88);
  width: 100%;
  box-sizing: border-box;

  .pipeline-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;

    .pipeline-title-group {
      display: flex;
      align-items: baseline;
      gap: 8px;

      .section-title {
        font-size: 14px;
        font-weight: 600;
        color: #0f172a;
        display: inline-flex;
        align-items: center;
        gap: 6px;
      }

      .section-subtitle {
        font-size: 11.5px;
        color: #94a3b8;
      }
    }

    .pipeline-status-badge {
      font-size: 11px;
      color: #10b981;
      display: inline-flex;
      align-items: center;
      gap: 5px;
      background: rgba(16, 185, 129, 0.08);
      border: 1px solid rgba(16, 185, 129, 0.25);
      padding: 2px 8px;
      border-radius: 12px;
      font-weight: 500;

      .pulse-dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: #10b981;
        box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.25);
      }
    }
  }

  /* 2x2 网格，彻底消除横向撑破与溢出 */
  .pipeline-steps-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    width: 100%;
    box-sizing: border-box;

    .pipeline-step-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      padding: 12px;
      display: flex;
      flex-direction: column;
      gap: 6px;
      min-width: 0;
      box-sizing: border-box;
      transition: all 0.2s ease;
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.02);

      &.active {
        border-color: rgba(0, 82, 255, 0.35);
        background: linear-gradient(180deg, #ffffff 0%, rgba(0, 82, 255, 0.02) 100%);
        box-shadow: 0 3px 10px rgba(0, 82, 255, 0.06);

        .step-num {
          color: #0052ff;
          background: rgba(0, 82, 255, 0.08);
        }
      }

      .step-card-header {
        display: flex;
        align-items: center;
        gap: 6px;

        .step-num {
          width: 20px;
          height: 20px;
          border-radius: 5px;
          background: #f1f5f9;
          color: #64748b;
          font-size: 10.5px;
          font-weight: 700;
          display: flex;
          align-items: center;
          justify-content: center;
          font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
          flex-shrink: 0;
        }

        .step-phase-tag {
          font-size: 11px;
          color: #475569;
          font-weight: 600;
          flex: 1;
        }

        .step-status-tag {
          font-size: 10px;
          padding: 1px 5px;
          border-radius: 4px;
          font-weight: 500;
          flex-shrink: 0;

          &.ready {
            background: rgba(16, 185, 129, 0.1);
            color: #10b981;
          }

          &.pending {
            background: #f1f5f9;
            color: #94a3b8;
          }
        }
      }

      .step-content {
        min-width: 0;

        .step-title {
          font-size: 12px;
          font-weight: 600;
          color: #1e293b;
          line-height: 1.35;
        }

        .step-desc {
          font-size: 11px;
          color: #94a3b8;
          line-height: 1.4;
          margin-top: 2px;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }
      }
    }
  }

  /* 2x2 规格卡片 */
  .specs-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    width: 100%;
    box-sizing: border-box;

    .spec-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      padding: 10px 12px;
      display: flex;
      align-items: center;
      gap: 10px;
      min-width: 0;
      box-sizing: border-box;
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.02);

      .spec-icon-wrap {
        width: 34px;
        height: 34px;
        border-radius: 8px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 17px;
        flex-shrink: 0;

        &.model-icon {
          background: rgba(0, 82, 255, 0.08);
          color: #0052ff;
        }

        &.vector-icon {
          background: rgba(16, 185, 129, 0.08);
          color: #10b981;
        }

        &.stream-icon {
          background: rgba(245, 158, 11, 0.08);
          color: #f59e0b;
        }

        &.receipt-icon {
          background: rgba(99, 102, 241, 0.08);
          color: #6366f1;
        }
      }

      .spec-info {
        display: flex;
        flex-direction: column;
        gap: 2px;
        min-width: 0;
        flex: 1;

        .spec-label {
          font-size: 10.5px;
          color: #94a3b8;
        }

        .spec-value {
          font-size: 11.5px;
          font-weight: 600;
          color: #1e293b;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }
    }
  }

  .quick-scenarios-section {
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 100%;
    box-sizing: border-box;

    .scenarios-header {
      .scenarios-title {
        font-size: 12.5px;
        font-weight: 600;
        color: #334155;
        display: inline-flex;
        align-items: center;
        gap: 6px;
      }
    }

    .quick-scenarios-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 10px;
      width: 100%;
      box-sizing: border-box;

      .quick-scenario-card {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 10px;
        padding: 10px 12px;
        cursor: pointer;
        transition: all 0.2s ease;
        display: flex;
        flex-direction: column;
        gap: 5px;
        min-width: 0;
        box-sizing: border-box;

        &:hover {
          border-color: #0052ff;
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(0, 82, 255, 0.1);

          .scenario-card-header .scenario-name {
            color: #0052ff;
          }
        }

        .scenario-card-header {
          display: flex;
          justify-content: space-between;
          align-items: center;

          .scenario-name {
            font-size: 12.5px;
            font-weight: 600;
            color: #1e293b;
            transition: color 0.2s;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }

          .apply-btn {
            font-size: 11px;
            padding: 0;
            flex-shrink: 0;
          }
        }

        .scenario-snippet {
          font-size: 11px;
          color: #64748b;
          line-height: 1.45;
          margin: 0;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }
      }
    }
  }
}

/* 运行态输出结果窗口 */
.output-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
  width: 100%;
  box-sizing: border-box;

  .output-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .output-title-group {
      display: flex;
      align-items: center;
      gap: 10px;

      .section-title {
        font-size: 14px;
        font-weight: 600;
        color: #0f172a;
        display: inline-flex;
        align-items: center;
        gap: 6px;
      }

      .running-status-tag {
        font-size: 11px;
        color: #0052ff;
        background: rgba(0, 82, 255, 0.08);
        border: 1px solid rgba(0, 82, 255, 0.25);
        padding: 2px 8px;
        border-radius: 10px;
        display: inline-flex;
        align-items: center;
        gap: 5px;
        font-weight: 500;

        .pulse-dot {
          width: 6px;
          height: 6px;
          border-radius: 50%;
          background: #0052ff;
          animation: pulseAnimation 1.5s infinite;
        }
      }

      .completed-status-tag {
        font-size: 11px;
        color: #10b981;
        background: rgba(16, 185, 129, 0.08);
        border: 1px solid rgba(16, 185, 129, 0.25);
        padding: 2px 8px;
        border-radius: 10px;
        display: inline-flex;
        align-items: center;
        gap: 4px;
        font-weight: 500;
      }
    }
  }

  .thinking-collapse {
    border: none;
    background: transparent;

    :deep(.el-collapse-item__header) {
      background: rgba(241, 245, 249, 0.7);
      border-radius: 8px;
      padding: 0 12px;
      height: 36px;
      border: 1px solid rgba(226, 232, 240, 0.8);
      font-size: 12px;
      color: #475569;
    }

    :deep(.el-collapse-item__wrap) {
      background: transparent;
      border: none;
    }

    .thinking-box {
      margin-top: 8px;
      background: rgba(248, 250, 252, 0.95);
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 12px;

      .thinking-text {
        font-size: 12px;
        color: #475569;
        font-family: inherit;
        white-space: pre-wrap;
        margin: 0;
        line-height: 1.6;
      }
    }
  }

  .rag-sources-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;

    .rag-label {
      color: #64748b;
    }

    .rag-tag {
      background: rgba(15, 23, 42, 0.05);
      border: 1px solid #cbd5e1;
      color: #334155;
    }
  }

  .terminal-content {
    background: rgba(255, 255, 255, 0.95);
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    padding: 16px;
    min-height: 240px;
    max-height: 520px;
    overflow-y: auto;
    box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.02);

    .running-indicator {
      display: flex;
      align-items: center;
      gap: 12px;
      color: #64748b;
      font-size: 13px;
      padding: 20px 0;
    }

    .markdown-body {
      font-size: 14px;
      line-height: 1.75;
      color: #1e293b;
      word-break: break-word;

      h1, h2, h3, h4, h5, h6 {
        color: #0f172a;
        font-weight: 600;
        margin-top: 20px;
        margin-bottom: 12px;
        line-height: 1.35;
      }

      h1 {
        font-size: 20px;
        border-bottom: 1px solid #e2e8f0;
        padding-bottom: 8px;
      }

      h2 {
        font-size: 17px;
        border-bottom: 1px solid #f1f5f9;
        padding-bottom: 6px;
      }

      h3 {
        font-size: 15px;
      }

      h4 {
        font-size: 14px;
      }

      p {
        margin: 10px 0;
        line-height: 1.75;
      }

      strong {
        color: #0f172a;
        font-weight: 600;
      }

      em {
        color: #334155;
        font-style: italic;
      }

      blockquote {
        margin: 12px 0;
        padding: 10px 16px;
        border-left: 3.5px solid #0052ff;
        background: rgba(0, 82, 255, 0.04);
        border-radius: 0 8px 8px 0;
        color: #475569;
        font-size: 13.5px;

        p {
          margin: 4px 0;
        }
      }

      ul, ol {
        margin: 10px 0;
        padding-left: 24px;

        li {
          margin: 6px 0;
          line-height: 1.7;
        }
      }

      ul li {
        list-style-type: disc;
      }

      ol li {
        list-style-type: decimal;
      }

      code:not(.hljs) {
        font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
        font-size: 12.5px;
        background: #f1f5f9;
        color: #0f172a;
        padding: 2px 6px;
        border-radius: 4px;
        border: 1px solid #e2e8f0;
      }

      .hljs-wrapper {
        position: relative;
        background: #0f172a;
        border-radius: 8px;
        margin: 14px 0;
        overflow: hidden;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);

        .code-block-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 6px 14px;
          background: rgba(255, 255, 255, 0.06);
          border-bottom: 1px solid rgba(255, 255, 255, 0.08);

          .code-lang-badge {
            font-size: 11px;
            font-weight: 600;
            color: #94a3b8;
            text-transform: uppercase;
            letter-spacing: 0.5px;
          }

          .copy-code-action {
            font-size: 11px;
            color: #cbd5e1;
            background: rgba(255, 255, 255, 0.1);
            padding: 2px 8px;
            border-radius: 4px;
            cursor: pointer;
            user-select: none;
            transition: all 0.2s ease;

            &:hover {
              background: #0052ff;
              color: #ffffff;
            }
          }
        }

        code.hljs {
          display: block;
          padding: 12px 16px;
          overflow-x: auto;
          font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
          font-size: 13px;
          line-height: 1.6;
          color: #f8fafc;
          background: transparent;
        }
      }

      table {
        width: 100%;
        border-collapse: collapse;
        margin: 14px 0;
        font-size: 13px;

        th, td {
          border: 1px solid #e2e8f0;
          padding: 8px 12px;
          text-align: left;
        }

        th {
          background: #f8fafc;
          font-weight: 600;
          color: #0f172a;
        }

        tr:nth-child(even) {
          background: #f8fafc;
        }
      }

      hr {
        border: none;
        border-top: 1px solid #e2e8f0;
        margin: 20px 0;
      }
    }
  }

  .output-footer {
    padding-top: 10px;
    border-top: 1px dashed #e2e8f0;

    .metrics-grid {
      display: flex;
      align-items: center;
      gap: 18px;
      font-size: 12px;
      color: #64748b;

      .metric-item {
        display: inline-flex;
        align-items: center;
        gap: 4px;

        strong {
          color: #0f172a;
        }

        code {
          background: #f1f5f9;
          padding: 2px 6px;
          border-radius: 4px;
          color: #0052ff;
        }
      }
    }
  }
}

@keyframes pulseAnimation {
  0% {
    box-shadow: 0 0 0 0 rgba(0, 82, 255, 0.4);
  }
  70% {
    box-shadow: 0 0 0 6px rgba(0, 82, 255, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(0, 82, 255, 0);
  }
}
</style>

<style lang="scss">
.app-runner-drawer {
  background: #f8fafc !important;
  max-width: 100vw !important;
  overflow: hidden !important;

  .el-drawer__body {
    padding: 0 !important;
    overflow: hidden !important;
  }
}

.history-drawer {
  background: #f8fafc !important;

  .el-drawer__header {
    margin-bottom: 0 !important;
    padding: 16px 20px !important;
    border-bottom: 1px solid #e2e8f0;
  }

  .el-drawer__body {
    padding: 16px 20px !important;
  }
}
</style>
