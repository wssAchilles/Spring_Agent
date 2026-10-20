<template>
  <div
    ref="nodeCardRef"
    class="custom-node"
    :class="[
      `${nodeType}-node`,
      { selected: isSelected, 'has-hover': isHovered }
    ]"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
  >
    <!-- 玻璃光晕与光学高光扫光层（无层叠上下文） -->
    <div class="node-specular-highlight"></div>

    <!-- 删除按钮 -->
    <button
      v-if="canDelete"
      type="button"
      class="node-delete-btn"
      title="删除节点"
      @click.stop.prevent="$emit('delete', id)"
      @pointerdown.stop.prevent
    >
      <el-icon><Delete /></el-icon>
    </button>

    <!-- 顶部类型徽标 -->
    <div class="node-top">
      <span class="node-type-label">{{ typeLabel }}</span>
    </div>

    <!-- 节点主体容器 -->
    <div class="node-body">
      <div class="node-header">
        <div class="node-icon-wrapper" :class="`${nodeType}-icon`">
          <slot name="icon">
            <el-icon><component :is="defaultIconComponent" /></el-icon>
          </slot>
        </div>
        <div class="node-title" :title="title">
          {{ title }}
        </div>
      </div>

      <!-- 节点专属内容插槽 -->
      <slot />
    </div>

    <!-- 连线锚点与加号操作插槽 -->
    <slot name="handles">
      <Handle
        type="target"
        :position="Position.Left"
        class="handle"
        :class="{ 'handle-drop-target': dragHoverTargetId === id }"
      />
      <Handle
        type="source"
        :position="Position.Right"
        :connectable-start="false"
        class="handle node-source-handle"
      />
      <div
        ref="addBtnRef"
        class="node-add-btn"
        title="拖拽连线或添加下游节点"
        @click.stop.prevent
        @pointerdown.stop.prevent="$emit('source-action', $event, id)"
        @mouseenter="handleAddBtnEnter"
        @mouseleave="handleAddBtnLeave"
      >
        <el-icon><Plus /></el-icon>
      </div>
    </slot>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import { Handle, Position } from "@vue-flow/core";
import { Delete, Plus, Service, ChatDotRound, FolderOpened, Operation, Refresh, Cpu } from "@element-plus/icons-vue";
import gsap from "gsap";

import { animateEnter } from "@/composables/useGsapMotion";

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
  nodeType: {
    type: String,
    default: "llm",
  },
  typeLabel: {
    type: String,
    default: "节点",
  },
  title: {
    type: String,
    default: "未命名节点",
  },
  canDelete: {
    type: Boolean,
    default: true,
  },
  isSelected: {
    type: Boolean,
    default: false,
  },
  dragHoverTargetId: {
    type: String,
    default: null,
  },
});

defineEmits(["delete", "source-action"]);

const nodeCardRef = ref(null);
const addBtnRef = ref(null);
const isHovered = ref(false);

const defaultIconComponent = computed(() => {
  switch (props.nodeType) {
    case "start":
      return Operation;
    case "llm":
      return Cpu;
    case "reply":
      return ChatDotRound;
    case "knowledge":
    case "knowledge-retrieval":
      return FolderOpened;
    case "tool":
      return Service;
    case "condition":
      return Operation;
    case "loop":
      return Refresh;
    default:
      return Cpu;
  }
});

// GSAP 安全入场动画（严格执行 clearProps，遵守无层叠上下文铁律）
onMounted(() => {
  if (nodeCardRef.value) {
    animateEnter(nodeCardRef.value, {
      fromY: 10,
      fromScale: 0.95,
      duration: 0.28,
      ease: "power2.out",
    });
  }
});

// 悬停微交互：保持本体无持久 transform，通过 CSS box-shadow 产生光晕扩散
function handleMouseEnter() {
  isHovered.value = true;
}

function handleMouseLeave() {
  isHovered.value = false;
}

// GSAP 连线加号悬停脉冲
function handleAddBtnEnter() {
  if (addBtnRef.value) {
    gsap.to(addBtnRef.value, {
      scale: 1.2,
      duration: 0.18,
      ease: "back.out(2)",
    });
  }
}

function handleAddBtnLeave() {
  if (addBtnRef.value) {
    gsap.to(addBtnRef.value, {
      scale: 1,
      duration: 0.2,
      ease: "power2.out",
      onComplete() {
        if (addBtnRef.value) {
          gsap.set(addBtnRef.value, { clearProps: "transform,scale" });
        }
      },
    });
  }
}

onBeforeUnmount(() => {
  if (nodeCardRef.value) {
    gsap.killTweensOf(nodeCardRef.value);
  }
  if (addBtnRef.value) {
    gsap.killTweensOf(addBtnRef.value);
  }
});
</script>

<style scoped lang="scss">
@use "sass:color";

.custom-node {
  position: relative;
  width: 220px;
  min-height: 110px;
  border-radius: 14px;
  padding: 12px 14px;
  color: #f1f5f9;
  cursor: pointer;
  user-select: none;
  transition: border-color 0.25s ease, box-shadow 0.25s ease;

  /* Apple iOS 26 双层液态玻璃质感 */
  background: rgba(22, 27, 34, 0.78);
  backdrop-filter: blur(50px);
  -webkit-backdrop-filter: blur(50px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.28), inset 0 1px 0 rgba(255, 255, 255, 0.14);

  &:hover {
    border-color: rgba(99, 102, 241, 0.45);
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.36), 0 0 16px rgba(99, 102, 241, 0.18), inset 0 1px 0 rgba(255, 255, 255, 0.22);
  }

  &.selected {
    border-color: #6366f1;
    box-shadow: 0 14px 36px rgba(0, 0, 0, 0.42), 0 0 24px rgba(99, 102, 241, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.3);
  }

  /* 节点顶部类型微标 */
  .node-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;

    .node-type-label {
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.5px;
      text-transform: uppercase;
      color: rgba(255, 255, 255, 0.5);
    }
  }

  /* 节点删除按钮 */
  .node-delete-btn {
    position: absolute;
    top: 8px;
    right: 8px;
    width: 22px;
    height: 22px;
    border-radius: 6px;
    border: none;
    background: rgba(255, 255, 255, 0.08);
    color: rgba(255, 255, 255, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    font-size: 12px;
    transition: all 0.2s ease;

    &:hover {
      background: rgba(239, 68, 68, 0.25);
      color: #ef4444;
    }
  }

  /* 头部图标与标题 */
  .node-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;

    .node-icon-wrapper {
      width: 28px;
      height: 28px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 15px;
      background: rgba(255, 255, 255, 0.08);
      color: #94a3b8;

      &.start-icon {
        background: rgba(34, 197, 94, 0.18);
        color: #22c55e;
      }
      &.llm-icon {
        background: rgba(99, 102, 241, 0.2);
        color: #818cf8;
      }
      &.reply-icon {
        background: rgba(14, 165, 233, 0.18);
        color: #38bdf8;
      }
      &.knowledge-icon,
      &.knowledge-retrieval-icon {
        background: rgba(245, 158, 11, 0.18);
        color: #fbbf24;
      }
      &.tool-icon {
        background: rgba(236, 72, 153, 0.18);
        color: #f472b6;
      }
      &.condition-icon {
        background: rgba(168, 85, 247, 0.18);
        color: #c084fc;
      }
      &.loop-icon {
        background: rgba(20, 184, 166, 0.18);
        color: #2dd4bf;
      }
    }

    .node-title {
      font-size: 13px;
      font-weight: 600;
      color: #f8fafc;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      flex: 1;
    }
  }

  /* 锚点 Handle 样式 */
  :deep(.vue-flow__handle) {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: #6366f1;
    border: 2px solid #0f172a;
    transition: transform 0.2s ease, background-color 0.2s ease;

    &:hover {
      transform: scale(1.4);
      background: #818cf8;
    }

    &.handle-drop-target {
      transform: scale(1.6);
      background: #22c55e;
      box-shadow: 0 0 10px rgba(34, 197, 94, 0.6);
    }
  }

  /* 加号添加按钮 */
  .node-add-btn {
    position: absolute;
    right: -13px;
    top: 50%;
    transform: translateY(-50%);
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: #6366f1;
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(99, 102, 241, 0.4);
    z-index: 10;
    transition: background-color 0.2s ease;

    &:hover {
      background: #4f46e5;
    }
  }
}
</style>
