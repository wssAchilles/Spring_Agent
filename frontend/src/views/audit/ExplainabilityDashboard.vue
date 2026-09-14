<!--
 * Copyright © 2026 Qiantong Technology Co., Ltd.
 * qKnow Knowledge Platform
 *
 * 神经符号可解释性拓扑与密码学存证大屏容器 (Explainability & Audit Dashboard)
 * 聚合全链路 8 阶段因果有向无环图、RFC 6962 客户端离线验真与安全护栏态势感知
 -->
<template>
  <div class="explainability-dashboard" ref="dashboardRef">
    <!-- 顶部统一钛金毛玻璃导航栏 (Level 3 Glass) -->
    <header class="dashboard-header">
      <div class="brand-zone">
        <div class="status-orb" />
        <div class="brand-text">
          <h1 class="system-title">神经符号可解释性与密码学审计中心</h1>
          <span class="system-subtitle">NEURO-SYMBOLIC EXPLAINABILITY & MERKLE CRYPTOGRAPHIC VERIFICATION</span>
        </div>
      </div>

      <!-- 中部视图切换器 (Segmented Controls) -->
      <div class="tab-switcher">
        <button
          class="switch-btn"
          :class="{ active: activeTab === 'topology' }"
          @click="activeTab = 'topology'"
        >
          <el-icon><Share /></el-icon>
          <span>因果拓扑与溯源</span>
        </button>
        <button
          class="switch-btn"
          :class="{ active: activeTab === 'merkle' }"
          @click="activeTab = 'merkle'"
        >
          <el-icon><Lock /></el-icon>
          <span>RFC 6962 密码学验真</span>
        </button>
        <button
          class="switch-btn"
          :class="{ active: activeTab === 'guardrail' }"
          @click="activeTab = 'guardrail'"
        >
          <el-icon><Odometer /></el-icon>
          <span>安全护栏态势</span>
        </button>
      </div>

      <!-- 右侧控制区: Trace 查询与全屏 -->
      <div class="control-zone">
        <div class="trace-input-wrap">
          <el-input
            v-model="inputTraceId"
            size="small"
            placeholder="输入 Trace ID 检索..."
            class="mono-input"
            clearable
            @keyup.enter="handleTraceSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button size="small" type="primary" plain @click="handleTraceSearch">
            定位
          </el-button>
        </div>

        <el-button
          size="small"
          circle
          class="action-btn"
          :icon="FullScreen"
          title="切换全屏模式"
          @click="toggleFullScreen"
        />
      </div>
    </header>

    <!-- 主展示视口区 -->
    <main class="dashboard-viewport">
      <!-- 视图 1: 因果拓扑图画板 (Keep-Alive 避免重新计算 Dagre 布局) -->
      <Transition name="fade-slide" mode="out-in">
        <div v-if="activeTab === 'topology'" key="topology" class="viewport-item">
          <ExplainabilityTopologyCanvas :key="currentTraceId" :trace-id="currentTraceId" />
        </div>
        <div v-else-if="activeTab === 'merkle'" key="merkle" class="viewport-item scrollable">
          <MerkleProofValidator />
        </div>
        <div v-else-if="activeTab === 'guardrail'" key="guardrail" class="viewport-item scrollable">
          <GuardrailDashboard />
        </div>
      </Transition>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { Share, Lock, Odometer, Search, FullScreen } from '@element-plus/icons-vue';
import ExplainabilityTopologyCanvas from './components/ExplainabilityTopologyCanvas.vue';
import MerkleProofValidator from './components/MerkleProofValidator.vue';
import GuardrailDashboard from './components/GuardrailDashboard.vue';

// 当前激活的视图类型: topology | merkle | guardrail
const activeTab = ref<'topology' | 'merkle' | 'guardrail'>('topology');

// 检索 TraceId
const defaultTraceId = 'tr-deepseek-demo-01';
const inputTraceId = ref<string>(defaultTraceId);
const currentTraceId = ref<string>(defaultTraceId);

const dashboardRef = ref<HTMLDivElement | null>(null);

function handleTraceSearch() {
  if (inputTraceId.value.trim()) {
    currentTraceId.value = inputTraceId.value.trim();
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
</script>

<style scoped>
.explainability-dashboard {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: calc(100vh - 84px);
  background: radial-gradient(circle at 50% 0%, #171d2b 0%, #0c1017 75%, #080a0f 100%);
  color: #e2e8f0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  overflow: hidden;
  box-sizing: border-box;
}

/* 顶部导航控制条 (Level 3 Glass) */
.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: rgba(18, 24, 38, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
  z-index: 10;
  flex-shrink: 0;
}

.brand-zone {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-orb {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 12px rgba(16, 185, 129, 0.8);
  animation: pulseOrb 2s infinite ease-in-out;
}

@keyframes pulseOrb {
  0%, 100% {
    transform: scale(1);
    opacity: 0.8;
  }
  50% {
    transform: scale(1.25);
    opacity: 1;
  }
}

.brand-text .system-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #f8fafc;
  letter-spacing: 0.5px;
}

.brand-text .system-subtitle {
  font-size: 10px;
  color: #64748b;
  letter-spacing: 1px;
  font-family: 'JetBrains Mono', monospace;
}

/* 中部切换分段器 */
.tab-switcher {
  display: flex;
  align-items: center;
  background: rgba(10, 14, 23, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 3px;
  gap: 4px;
}

.switch-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: transparent;
  border: none;
  border-radius: 6px;
  color: #94a3b8;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.switch-btn:hover {
  color: #f8fafc;
  background: rgba(255, 255, 255, 0.05);
}

.switch-btn.active {
  background: rgba(59, 130, 246, 0.15);
  color: #60a5fa;
  box-shadow: inset 0 0 0 1px rgba(96, 165, 250, 0.3);
}

/* 右侧控制区 */
.control-zone {
  display: flex;
  align-items: center;
  gap: 12px;
}

.trace-input-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}

.mono-input :deep(.el-input__wrapper) {
  background: rgba(15, 23, 42, 0.8) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1) inset !important;
  color: #cbd5e1;
  font-family: 'JetBrains Mono', monospace;
}

.mono-input :deep(.el-input__inner) {
  color: #cbd5e1;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
}

.action-btn {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #94a3b8;
  transition: all 0.2s ease;
}

.action-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}

/* 视口展示区 */
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
}

.viewport-item.scrollable {
  overflow-y: auto;
}

/* 优雅视图过渡 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
