<template>
  <g class="workflow-photon-edge" :class="{ 'is-selected': selected, 'is-animated': animated }">
    <!-- SVG 滤镜与渐变定义 (单例复用) -->
    <defs>
      <linearGradient :id="`photon-grad-${id}`" x1="0%" y1="0%" x2="100%" y2="0%">
        <stop offset="0%" stop-color="#6366f1" stop-opacity="0.2" />
        <stop offset="50%" stop-color="#a855f7" stop-opacity="0.95" />
        <stop offset="100%" stop-color="#38bdf8" stop-opacity="0.3" />
      </linearGradient>
      <filter :id="`photon-glow-${id}`" x="-20%" y="-20%" width="140%" height="140%">
        <feGaussianBlur stdDeviation="2.5" result="glow" />
        <feComposite in="SourceGraphic" in2="glow" operator="over" />
      </filter>
    </defs>

    <!-- 1. 底层交互感应宽轨 (不可见，便于鼠标点击捕获) -->
    <path
      :d="edgePath"
      class="edge-interaction-path"
      fill="none"
      stroke="transparent"
      stroke-width="24"
    />

    <!-- 2. 底层静态结构轨 (半透明微弱底色) -->
    <path
      :d="edgePath"
      class="edge-base-track"
      fill="none"
      :stroke="selected ? 'rgba(99, 102, 241, 0.85)' : 'rgba(148, 163, 184, 0.28)'"
      :stroke-width="selected ? 2.5 : 2"
    />

    <!-- 3. 顶层高能光子流动脉冲轨 (GSAP 驱动 strokeDashoffset 粒子穿梭) -->
    <path
      ref="photonPulseRef"
      :d="edgePath"
      class="edge-photon-pulse"
      fill="none"
      :stroke="`url(#photon-grad-${id})`"
      :filter="`url(#photon-glow-${id})`"
      stroke-width="3"
      stroke-linecap="round"
      stroke-dasharray="12 36"
    />

    <!-- 4. 连线中心操作加号按钮 (Apple 液态玻璃触感) -->
    <g
      v-if="centerX && centerY"
      ref="addBtnRef"
      class="edge-add-button-group"
      :transform="`translate(${centerX}, ${centerY})`"
      @mouseenter="handleBtnEnter"
      @mouseleave="handleBtnLeave"
      @click.stop="$emit('edgeAddClick', props, $event)"
    >
      <!-- 发光扩散底盘 -->
      <circle cx="0" cy="0" r="14" class="edge-btn-outer-glow" />
      <!-- 双层液态玻璃主体 -->
      <circle cx="0" cy="0" r="11" class="edge-btn-glass-body" />
      <!-- 居中加号图标 -->
      <text
        x="0"
        y="4"
        text-anchor="middle"
        class="edge-btn-plus-icon"
      >
        +
      </text>
    </g>
  </g>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount, watch } from 'vue';
import { getBezierPath } from '@vue-flow/core';
import gsap from 'gsap';

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
  sourceX: {
    type: Number,
    required: true,
  },
  sourceY: {
    type: Number,
    required: true,
  },
  targetX: {
    type: Number,
    required: true,
  },
  targetY: {
    type: Number,
    required: true,
  },
  sourcePosition: {
    type: String,
    default: 'right',
  },
  targetPosition: {
    type: String,
    default: 'left',
  },
  selected: {
    type: Boolean,
    default: false,
  },
  animated: {
    type: Boolean,
    default: true,
  },
  data: {
    type: Object,
    default: () => ({}),
  },
});

defineEmits(['edgeAddClick']);

const photonPulseRef = ref(null);
const addBtnRef = ref(null);
let pulseTween = null;

// 动态计算平滑三次贝塞尔曲线路径及中点
const pathData = computed(() => {
  return getBezierPath({
    sourceX: props.sourceX,
    sourceY: props.sourceY,
    sourcePosition: props.sourcePosition,
    targetX: props.targetX,
    targetY: props.targetY,
    targetPosition: props.targetPosition,
  });
});

const edgePath = computed(() => pathData.value[0]);
const centerX = computed(() => pathData.value[1]);
const centerY = computed(() => pathData.value[2]);

// 启动 GSAP 光子脉冲平滑动画
function initPhotonAnimation() {
  if (pulseTween) {
    pulseTween.kill();
    pulseTween = null;
  }

  if (photonPulseRef.value) {
    // 采用负 offset 实现从 source 向 target 方向单向高能跃迁
    pulseTween = gsap.to(photonPulseRef.value, {
      strokeDashoffset: -96,
      duration: 1.8,
      ease: 'none',
      repeat: -1,
    });
  }
}

onMounted(() => {
  initPhotonAnimation();
});

watch(
  () => props.animated,
  (val) => {
    if (val) {
      initPhotonAnimation();
    } else if (pulseTween) {
      pulseTween.pause();
    }
  }
);

// 加号按钮悬停弹簧动效
function handleBtnEnter() {
  if (addBtnRef.value) {
    gsap.to(addBtnRef.value, {
      scale: 1.25,
      duration: 0.2,
      ease: 'back.out(2)',
    });
  }
}

function handleBtnLeave() {
  if (addBtnRef.value) {
    gsap.to(addBtnRef.value, {
      scale: 1,
      duration: 0.22,
      ease: 'power2.out',
      onComplete() {
        if (addBtnRef.value) {
          gsap.set(addBtnRef.value, { clearProps: 'transform,scale' });
        }
      },
    });
  }
}

onBeforeUnmount(() => {
  if (pulseTween) {
    pulseTween.kill();
    pulseTween = null;
  }
  if (addBtnRef.value) {
    gsap.killTweensOf(addBtnRef.value);
  }
});
</script>

<style scoped lang="scss">
.workflow-photon-edge {
  pointer-events: visibleStroke;
  cursor: pointer;

  .edge-interaction-path {
    cursor: pointer;
    pointer-events: stroke;
  }

  .edge-base-track {
    transition: stroke 0.3s ease, stroke-width 0.2s ease;
  }

  &:hover .edge-base-track {
    stroke: rgba(168, 85, 247, 0.7);
    stroke-width: 2.5;
  }

  &.is-selected .edge-base-track {
    stroke: #6366f1;
    stroke-width: 2.8;
  }

  .edge-photon-pulse {
    pointer-events: none;
    opacity: 0.92;
  }

  /* 加号按钮样式 */
  .edge-add-button-group {
    cursor: pointer;
    transform-origin: 0 0;
    pointer-events: all;

    .edge-btn-outer-glow {
      fill: rgba(99, 102, 241, 0.15);
      transition: fill 0.25s ease, r 0.25s ease;
    }

    .edge-btn-glass-body {
      fill: rgba(30, 41, 59, 0.88);
      stroke: rgba(255, 255, 255, 0.28);
      stroke-width: 1.2;
      filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.45));
      transition: stroke 0.2s ease, fill 0.2s ease;
    }

    .edge-btn-plus-icon {
      font-size: 15px;
      font-weight: 700;
      fill: #e2e8f0;
      user-select: none;
      pointer-events: none;
    }

    &:hover {
      .edge-btn-outer-glow {
        fill: rgba(168, 85, 247, 0.35);
        r: 16;
      }

      .edge-btn-glass-body {
        fill: rgba(49, 46, 129, 0.95);
        stroke: rgba(192, 132, 252, 0.85);
      }

      .edge-btn-plus-icon {
        fill: #ffffff;
      }
    }
  }
}
</style>
