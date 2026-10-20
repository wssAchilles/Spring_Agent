<template>
  <div class="streaming-reasoning-view" :class="{ 'is-streaming': isStreaming }">
    <!-- 1. DeepSeek 思维链 (reasoning_content) 折叠/展开卡片 -->
    <div v-if="hasReasoning" class="reasoning-card">
      <div
        class="reasoning-header"
        @click="toggleExpand"
      >
        <div class="reasoning-title-group">
          <!-- 动态呼吸光球 (流式思考中呼吸，结束常亮) -->
          <span
            ref="pulseDotRef"
            class="reasoning-pulse-dot"
            :class="{ active: isStreaming }"
          ></span>
          <span class="reasoning-title-text">
            {{ isStreaming ? '正在深度思考中...' : '已完成深度思考' }}
          </span>
          <span v-if="displayDuration" class="reasoning-duration-badge">
            用时 {{ displayDuration }} 秒
          </span>
        </div>

        <div class="reasoning-toggle-btn">
          <span class="toggle-text">{{ isExpanded ? '收起思考过程' : '展开思考过程' }}</span>
          <el-icon
            ref="arrowIconRef"
            class="toggle-arrow"
            :class="{ 'is-rotated': isExpanded }"
          >
            <ArrowDown />
          </el-icon>
        </div>
      </div>

      <!-- 思考过程折叠容器 (GSAP 驱动平滑展开) -->
      <div
        ref="reasoningBodyRef"
        class="reasoning-body"
        :style="{ display: isExpanded ? 'block' : 'none' }"
      >
        <div class="reasoning-content-inner">
          <div class="reasoning-markdown-text">
            {{ reasoningContent }}
          </div>
          <!-- 流式思考打字光标 -->
          <span v-if="isStreaming" ref="cursorRef" class="reasoning-type-cursor"></span>
        </div>
      </div>
    </div>

    <!-- 2. 最终回复内容正文 -->
    <div v-if="content || isStreaming" class="streaming-content-body">
      <MarkdownView
        v-if="content"
        class="chatflow-markdown-render"
        :content="content"
      />
      <div v-else-if="isStreaming && !hasReasoning" class="streaming-placeholder">
        <span class="streaming-loading-dots">正在生成回复</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { ArrowDown } from '@element-plus/icons-vue';
import MarkdownView from '@/components/MarkdownView/index.vue';
import gsap from 'gsap';

const props = defineProps({
  reasoningContent: {
    type: String,
    default: '',
  },
  content: {
    type: String,
    default: '',
  },
  isStreaming: {
    type: Boolean,
    default: false,
  },
  duration: {
    type: Number,
    default: 0,
  },
});

const isExpanded = ref(true);
const pulseDotRef = ref(null);
const arrowIconRef = ref(null);
const reasoningBodyRef = ref(null);
const cursorRef = ref(null);

let pulseTween = null;
let cursorTween = null;
let collapseTween = null;

const hasReasoning = computed(() => {
  return Boolean(props.reasoningContent && props.reasoningContent.trim().length > 0);
});

const displayDuration = computed(() => {
  if (props.duration > 0) {
    return props.duration.toFixed(1);
  }
  return '';
});

// 思考光斑脉冲
function initPulseAnimation() {
  if (pulseDotRef.value && props.isStreaming) {
    if (pulseTween) pulseTween.kill();
    pulseTween = gsap.to(pulseDotRef.value, {
      opacity: 0.35,
      scale: 0.85,
      duration: 0.75,
      repeat: -1,
      yoyo: true,
      ease: 'sine.inOut',
    });
  } else if (pulseTween) {
    pulseTween.kill();
    pulseTween = null;
    if (pulseDotRef.value) {
      gsap.set(pulseDotRef.value, { clearProps: 'opacity,scale' });
    }
  }
}

// 打字机闪烁光标
function initCursorAnimation() {
  if (cursorRef.value && props.isStreaming) {
    if (cursorTween) cursorTween.kill();
    cursorTween = gsap.to(cursorRef.value, {
      opacity: 0,
      duration: 0.5,
      repeat: -1,
      yoyo: true,
      ease: 'steps(1)',
    });
  } else if (cursorTween) {
    cursorTween.kill();
    cursorTween = null;
  }
}

// 展开/收起平滑切换 (符合 Apple 欠阻尼弹簧质感与无层叠上下文铁律)
function toggleExpand() {
  isExpanded.value = !isExpanded.value;
  nextTick(() => {
    const el = reasoningBodyRef.value;
    if (!el) return;

    if (collapseTween) collapseTween.kill();

    if (isExpanded.value) {
      el.style.display = 'block';
      const fullHeight = el.scrollHeight;
      collapseTween = gsap.fromTo(
        el,
        { height: 0, opacity: 0 },
        {
          height: fullHeight,
          opacity: 1,
          duration: 0.35,
          ease: 'power2.out',
          onComplete() {
            // 展开完成清除 height 与 transform，保持自适应并避免孤立层叠上下文
            el.style.height = 'auto';
            gsap.set(el, { clearProps: 'transform,willChange' });
          },
        }
      );
    } else {
      const curHeight = el.scrollHeight;
      collapseTween = gsap.fromTo(
        el,
        { height: curHeight, opacity: 1 },
        {
          height: 0,
          opacity: 0,
          duration: 0.28,
          ease: 'power2.in',
          onComplete() {
            el.style.display = 'none';
            gsap.set(el, { clearProps: 'transform,height,willChange' });
          },
        }
      );
    }
  });
}

watch(
  () => props.isStreaming,
  () => {
    initPulseAnimation();
    initCursorAnimation();
  }
);

onMounted(() => {
  initPulseAnimation();
  initCursorAnimation();
});

onBeforeUnmount(() => {
  if (pulseTween) pulseTween.kill();
  if (cursorTween) cursorTween.kill();
  if (collapseTween) collapseTween.kill();
});
</script>

<style scoped lang="scss">
.streaming-reasoning-view {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;

  /* 思考链双层液态玻璃卡片 */
  .reasoning-card {
    border-radius: 10px;
    background: rgba(30, 41, 59, 0.45);
    backdrop-filter: blur(24px);
    -webkit-backdrop-filter: blur(24px);
    border: 1px solid rgba(255, 255, 255, 0.08);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.18), inset 0 1px 0 rgba(255, 255, 255, 0.08);
    overflow: hidden;
    transition: border-color 0.25s ease, background 0.25s ease;

    &:hover {
      border-color: rgba(99, 102, 241, 0.35);
      background: rgba(30, 41, 59, 0.55);
    }

    .reasoning-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 9px 14px;
      cursor: pointer;
      user-select: none;
      background: rgba(255, 255, 255, 0.02);

      .reasoning-title-group {
        display: flex;
        align-items: center;
        gap: 8px;

        .reasoning-pulse-dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background: #38bdf8;
          box-shadow: 0 0 8px rgba(56, 189, 248, 0.6);

          &.active {
            background: #a855f7;
            box-shadow: 0 0 10px rgba(168, 85, 247, 0.85);
          }
        }

        .reasoning-title-text {
          font-size: 12px;
          font-weight: 600;
          color: rgba(241, 245, 249, 0.9);
          letter-spacing: 0.3px;
        }

        .reasoning-duration-badge {
          font-size: 11px;
          color: rgba(148, 163, 184, 0.8);
          background: rgba(255, 255, 255, 0.06);
          padding: 1px 6px;
          border-radius: 6px;
        }
      }

      .reasoning-toggle-btn {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 11px;
        color: rgba(148, 163, 184, 0.8);

        .toggle-arrow {
          font-size: 11px;
          transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);

          &.is-rotated {
            transform: rotate(180deg);
          }
        }

        &:hover {
          color: #e2e8f0;
        }
      }
    }

    .reasoning-body {
      overflow: hidden;
      border-top: 1px solid rgba(255, 255, 255, 0.05);

      .reasoning-content-inner {
        padding: 12px 14px;
        font-size: 12.5px;
        line-height: 1.65;
        color: rgba(203, 213, 225, 0.85);
        white-space: pre-wrap;
        word-break: break-word;

        .reasoning-markdown-text {
          display: inline;
        }

        .reasoning-type-cursor {
          display: inline-block;
          width: 2px;
          height: 14px;
          background: #60a5fa;
          margin-left: 3px;
          vertical-align: text-bottom;
        }
      }
    }
  }

  /* 正文渲染容器 */
  .streaming-content-body {
    width: 100%;

    .streaming-placeholder {
      font-size: 13px;
      color: rgba(148, 163, 184, 0.8);
      font-style: italic;
    }
  }
}
</style>
