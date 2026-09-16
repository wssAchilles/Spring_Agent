<template>
  <el-drawer
    v-model="visible"
    :size="isFullScreen ? '100%' : '720px'"
    direction="rtl"
    :with-header="false"
    custom-class="app-runner-drawer glass-drawer"
    :destroy-on-close="false"
  >
    <div class="runner-container">
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
            </div>
            <p class="app-desc">{{ currentApp?.description || "知识应用执行中枢" }}</p>
          </div>
        </div>

        <div class="header-actions">
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
          <el-tooltip :content="isFullScreen ? '还原' : '全屏'" placement="bottom">
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

      <!-- 主体交互内容区 -->
      <div class="runner-body">
        <!-- 上部分：参数配置与预设场景 -->
        <section class="config-section glass-card">
          <div class="section-title-row">
            <span class="section-title"><el-icon><Operation /></el-icon> 运行入参配置</span>
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

          <div class="form-wrapper">
            <DynamicParamForm
              :schema="currentApp?.inputSchema"
              v-model="formInputs"
            />
          </div>

          <div class="submit-action-bar">
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
              <el-icon><VideoPause /></el-icon>
              中止生成
            </el-button>
          </div>
        </section>

        <!-- 下部分：实时流式输出与结果展示 -->
        <section class="output-section glass-card" v-if="hasRunEver || isRunning">
          <div class="output-header">
            <span class="section-title">
              <el-icon><Cpu /></el-icon> 实时推理与结果视窗
            </span>
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
                  <span>深度思考过程 (Reasoning Chain)</span>
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
import { computed, nextTick, reactive, ref } from "vue";
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
} from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import DynamicParamForm from "@/views/kac/components/schemaForm/DynamicParamForm.vue";
import RunHistoryDrawer from "@/views/kac/components/history/RunHistoryDrawer.vue";
import { runApply } from "@/api/kac/apply/apply.js";
import { defaultStreamingEngine } from "@/utils/streaming-markdown-engine.js";

const visible = ref(false);
const isFullScreen = ref(false);
const isRunning = ref(false);
const hasRunEver = ref(false);
const currentApp = ref(null);
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
  visible.value = true;

  // 根据应用类型注入预设场景
  setupPresets(app);
}

function setupPresets(app) {
  const name = app?.name || "";
  if (name.contains ? name.contains("摘要") : name.includes("摘要")) {
    presetScenarios.value = [
      {
        label: "AI-Native 架构长文摘要",
        inputs: {
          documentContent: "企业级 AI-Native 知识库平台以企业多源知识资产为核心，打通高保真 RAG 混合检索、多智能体协同编排与企业级 MCP 工具生态。系统通过引入统一的应用中心运行时，支持动态 Schema 参数解析与不可篡改审计凭单沉淀，显著降低生产环境的不确定性与工程调试成本。",
          summaryLength: "中等 (300-500字精炼版)",
        },
      },
    ];
  } else if (name.includes("周报") || name.includes("日报")) {
    presetScenarios.value = [
      {
        label: "研发冲刺周报",
        inputs: {
          workItems: "1. 根治概念配置缺少 color 字段及模型市场 NPE\n2. 全面重构应用中心前后端交互与即时运行抽屉\n3. 落地 SSE 流式输出与审计凭单机制",
          challenges: "跨模块依赖循环需要谨慎解耦，保障全量测试零退化",
          nextPlan: "推进批量并发跑批管线与长任务导出功能",
        },
      },
    ];
  } else if (name.includes("文章")) {
    presetScenarios.value = [
      {
        label: "企业智能升级前沿",
        inputs: {
          topic: "大模型时代企业私域知识库的技术演进路径",
          style: "科技前沿综述",
          requirements: "重点阐述 GraphRAG 与 Agentic 工作流在降低大模型幻觉中的关键作用",
        },
      },
    ];
  } else if (name.includes("检索") || name.includes("问答")) {
    presetScenarios.value = [
      {
        label: "平台核心支柱质询",
        inputs: {
          question: "Knowledge Hub 的四大战略攻坚支柱分别是什么？",
          query: "系统如何保证知识检索与大模型调用的安全性？",
          keyword: "CrossKbScoreCalibrator",
        },
      },
    ];
  } else {
    presetScenarios.value = [
      {
        label: "标准测试用例",
        inputs: { query: "请执行本应用的示范分析流程，并给出专业报告。" },
      },
    ];
  }
}

function applyPreset(preset) {
  formInputs.value = { ...formInputs.value, ...preset.inputs };
  ElMessage.success(`已代入「${preset.label}」预设场景参数`);
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
  background: #f8fafc;
  color: #0f172a;
}

.runner-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.8);

  .header-left {
    display: flex;
    align-items: center;
    gap: 14px;

    .app-avatar {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      background: rgba(0, 82, 255, 0.08);
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .app-meta {
      .title-row {
        display: flex;
        align-items: center;
        gap: 10px;

        h2 {
          font-size: 16px;
          font-weight: 600;
          color: #0f172a;
          margin: 0;
        }

        .status-indicator {
          font-size: 11px;
          color: #10b981;
          display: inline-flex;
          align-items: center;
          gap: 5px;

          i {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: #10b981;
            box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.25);
          }
        }
      }

      .app-desc {
        font-size: 12px;
        color: #64748b;
        margin: 3px 0 0 0;
      }
    }
  }

  .header-actions {
    display: flex;
    gap: 8px;

    .action-btn {
      background: rgba(255, 255, 255, 0.9);
      border: 1px solid #e2e8f0;
      color: #475569;

      &:hover {
        color: #0052ff;
        border-color: #0052ff;
      }
    }
  }
}

.runner-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.glass-card {
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(226, 232, 240, 0.85);
  box-shadow: 0 4px 20px -2px rgba(15, 23, 42, 0.05);
  border-radius: 12px;
  padding: 18px 20px;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;

  .section-title {
    font-size: 14px;
    font-weight: 600;
    color: #1e293b;
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .presets-row {
    display: flex;
    align-items: center;
    gap: 8px;

    .preset-label {
      font-size: 12px;
      color: #64748b;
    }

    .preset-tag {
      cursor: pointer;
      background: rgba(0, 82, 255, 0.06);
      color: #0052ff;
      border: 1px solid rgba(0, 82, 255, 0.2);
      transition: all 0.2s;

      &:hover {
        background: #0052ff;
        color: #fff;
      }
    }
  }
}

.submit-action-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;

  .run-submit-btn {
    padding: 10px 28px;
    border-radius: 8px;
    font-weight: 500;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    transition: all 0.2s;

    &.glass-btn-primary {
      background: #0052ff;
      border: 1px solid #0052ff;
      box-shadow: 0 4px 12px rgba(0, 82, 255, 0.25);

      &:hover {
        background: #0045d8;
      }
    }
  }
}

.output-section {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .output-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
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
      background: rgba(248, 250, 252, 0.9);
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
    border-radius: 8px;
    padding: 16px;
    min-height: 200px;
    max-height: 480px;
    overflow-y: auto;

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
</style>
