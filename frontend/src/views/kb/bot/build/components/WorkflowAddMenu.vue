<template>
  <div v-if="visible" class="add-node-menu-wrapper">
    <!-- 背景透明遮罩层 -->
    <div class="add-node-menu-overlay" @click="$emit('close')"></div>

    <!-- 菜单浮层卡片 -->
    <div
      ref="menuCardRef"
      class="add-node-menu glass-card"
      :style="{ left: position.x + 'px', top: position.y + 'px' }"
      @click.stop
    >
      <div class="add-node-menu-header">
        <span class="menu-title">添加流程节点</span>
        <el-icon class="close-btn" @click="$emit('close')"><Close /></el-icon>
      </div>

      <div class="add-node-menu-tabs">
        <button
          v-for="tabItem in tabs"
          :key="tabItem.key"
          type="button"
          class="add-node-menu-tab"
          :class="{ 'is-active': activeTab === tabItem.key }"
          @click.stop="$emit('tab-change', tabItem.key)"
        >
          {{ tabItem.label }}
        </button>
      </div>

      <div class="add-node-menu-list">
        <div
          v-for="item in items"
          :key="item.key || item.type || item.name"
          class="add-node-menu-item"
          :class="{ 'is-disabled': !item.enabled }"
          @click.stop="item.enabled ? $emit('select', item) : null"
        >
          <div class="node-icon-box" :class="`${item.type}-icon`">
            <component :is="item.iconComponent || defaultNodeIcon(item.type)" />
          </div>
          <div class="node-info">
            <div class="node-label-row">
              <span class="label-text">{{ item.label }}</span>
              <span v-if="!item.enabled" class="status-badge">开发中</span>
            </div>
            <div class="node-desc">{{ item.description }}</div>
          </div>
        </div>

        <div v-if="!items.length" class="add-node-menu-empty">
          <span>暂无可选节点</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from "vue";
import { Close, Cpu, ChatDotRound, FolderOpened, Service, Operation, Refresh } from "@element-plus/icons-vue";
import gsap from "gsap";

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  position: {
    type: Object,
    default: () => ({ x: 100, y: 100 }),
  },
  tabs: {
    type: Array,
    default: () => [
      { key: "basic", label: "基础节点" },
      { key: "tools", label: "扩展工具" },
    ],
  },
  activeTab: {
    type: String,
    default: "basic",
  },
  items: {
    type: Array,
    default: () => [],
  },
});

defineEmits(["close", "tab-change", "select"]);

const menuCardRef = ref(null);

function defaultNodeIcon(type) {
  switch (type) {
    case "llm": return Cpu;
    case "reply": return ChatDotRound;
    case "knowledge":
    case "knowledge-retrieval": return FolderOpened;
    case "tool": return Service;
    case "condition": return Operation;
    case "loop": return Refresh;
    default: return Cpu;
  }
}

watch(
  () => props.visible,
  (newVal) => {
    if (newVal) {
      setTimeout(() => {
        if (menuCardRef.value) {
          gsap.from(menuCardRef.value, {
            scale: 0.92,
            opacity: 0,
            y: 10,
            duration: 0.22,
            ease: "back.out(1.7)",
          });
        }
      }, 0);
    }
  }
);
</script>

<style scoped lang="scss">
.add-node-menu-wrapper {
  position: absolute;
  inset: 0;
  z-index: 999;
  pointer-events: none;
}

.add-node-menu-overlay {
  position: absolute;
  inset: 0;
  pointer-events: auto;
  background: transparent;
}

.add-node-menu {
  position: absolute;
  pointer-events: auto;
  width: 280px;
  max-height: 420px;
  display: flex;
  flex-direction: column;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.88);
  backdrop-filter: blur(50px);
  -webkit-backdrop-filter: blur(50px);
  border: 1px solid rgba(255, 255, 255, 0.14);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.45), 0 0 20px rgba(99, 102, 241, 0.15);
  overflow: hidden;

  .add-node-menu-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px 8px;

    .menu-title {
      font-size: 13px;
      font-weight: 600;
      color: #f1f5f9;
    }

    .close-btn {
      cursor: pointer;
      font-size: 14px;
      color: rgba(255, 255, 255, 0.45);
      transition: color 0.2s;

      &:hover {
        color: #fff;
      }
    }
  }

  .add-node-menu-tabs {
    display: flex;
    padding: 0 12px 8px;
    gap: 6px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);

    .add-node-menu-tab {
      flex: 1;
      padding: 6px 0;
      background: transparent;
      border: none;
      border-radius: 6px;
      font-size: 12px;
      font-weight: 500;
      color: rgba(255, 255, 255, 0.6);
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        color: #fff;
        background: rgba(255, 255, 255, 0.05);
      }

      &.is-active {
        color: #6366f1;
        background: rgba(99, 102, 241, 0.15);
        font-weight: 600;
      }
    }
  }

  .add-node-menu-list {
    flex: 1;
    overflow-y: auto;
    padding: 8px 10px;
    display: flex;
    flex-direction: column;
    gap: 4px;

    .add-node-menu-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 10px;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.18s ease;

      &:hover:not(.is-disabled) {
        background: rgba(255, 255, 255, 0.08);
        transform: translateX(2px);
      }

      &.is-disabled {
        opacity: 0.45;
        cursor: not-allowed;
      }

      .node-icon-box {
        width: 30px;
        height: 30px;
        border-radius: 8px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 16px;
        flex-shrink: 0;
        background: rgba(255, 255, 255, 0.08);
        color: #94a3b8;

        &.llm-icon { background: rgba(99, 102, 241, 0.2); color: #818cf8; }
        &.reply-icon { background: rgba(14, 165, 233, 0.18); color: #38bdf8; }
        &.knowledge-icon,
        &.knowledge-retrieval-icon { background: rgba(245, 158, 11, 0.18); color: #fbbf24; }
        &.tool-icon { background: rgba(236, 72, 153, 0.18); color: #f472b6; }
        &.condition-icon { background: rgba(168, 85, 247, 0.18); color: #c084fc; }
        &.loop-icon { background: rgba(20, 184, 166, 0.18); color: #2dd4bf; }
      }

      .node-info {
        flex: 1;
        overflow: hidden;

        .node-label-row {
          display: flex;
          align-items: center;
          gap: 6px;

          .label-text {
            font-size: 13px;
            font-weight: 500;
            color: #f8fafc;
          }

          .status-badge {
            font-size: 10px;
            padding: 1px 4px;
            border-radius: 4px;
            background: rgba(255, 255, 255, 0.1);
            color: rgba(255, 255, 255, 0.5);
          }
        }

        .node-desc {
          font-size: 11px;
          color: rgba(255, 255, 255, 0.45);
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }
    }

    .add-node-menu-empty {
      padding: 20px;
      text-align: center;
      font-size: 12px;
      color: rgba(255, 255, 255, 0.4);
    }
  }
}
</style>
