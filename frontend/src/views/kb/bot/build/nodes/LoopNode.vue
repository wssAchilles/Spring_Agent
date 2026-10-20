<template>
  <div
    class="custom-node loop-node"
    :class="{ selected: isSelected }"
    :style="getLoopNodeStyle(data)"
  >
    <button
      type="button"
      class="node-delete-btn"
      title="删除循环节点"
      @click.stop.prevent="$emit('delete', id)"
      @pointerdown.stop.prevent
    >
      <el-icon><Delete /></el-icon>
    </button>

    <div class="loop-node-shell">
      <div class="loop-node-header">
        <div class="loop-node-icon">
          <span class="loop-icon-glyph">&#8734;</span>
        </div>
        <div class="loop-node-title">
          {{ getNodeLabel(data, "循环") }}
        </div>
      </div>

      <!-- 子画布表面容器 (nodrag nopan 阻断事件穿透) -->
      <div
        class="loop-editor-surface nodrag nopan"
        :style="getLoopEditorSurfaceStyle(data)"
        @pointerdown.stop
        @click.stop
      >
        <slot />
      </div>
    </div>

    <Handle
      :id="LOOP_OUTER_TARGET_HANDLE_ID"
      type="target"
      :position="Position.Left"
      class="handle"
      :class="{ 'handle-drop-target': dragHoverTargetId === id }"
    />
    <Handle
      :id="LOOP_OUTER_SOURCE_HANDLE_ID"
      type="source"
      :position="Position.Right"
      :connectable-start="false"
      class="handle node-source-handle"
    />
    <div
      class="node-add-btn"
      title="从循环节点连线"
      @click.stop.prevent
      @pointerdown.stop.prevent="
        $emit('source-action', $event, id, LOOP_OUTER_SOURCE_HANDLE_ID)
      "
    >
      <el-icon><Plus /></el-icon>
    </div>
  </div>
</template>

<script setup>
import { Handle, Position } from "@vue-flow/core";
import { Delete, Plus } from "@element-plus/icons-vue";
import {
  getNodeLabel,
  getLoopNodeStyle,
  getLoopEditorSurfaceStyle,
  LOOP_OUTER_TARGET_HANDLE_ID,
  LOOP_OUTER_SOURCE_HANDLE_ID,
} from "../utils/workflowHelpers.js";

defineProps({
  id: {
    type: String,
    required: true,
  },
  data: {
    type: Object,
    default: () => ({}),
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
</script>

<style scoped lang="scss">
.loop-node {
  position: relative;
  border-radius: 16px;
  padding: 12px;
  background: rgba(15, 23, 42, 0.85);
  backdrop-filter: blur(50px);
  -webkit-backdrop-filter: blur(50px);
  border: 1px solid rgba(45, 212, 191, 0.25);
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.12);
  transition: all 0.25s ease;

  &:hover {
    border-color: rgba(45, 212, 191, 0.5);
    box-shadow: 0 16px 42px rgba(0, 0, 0, 0.45), 0 0 20px rgba(45, 212, 191, 0.2);
  }

  &.selected {
    border-color: #2dd4bf;
    box-shadow: 0 18px 48px rgba(0, 0, 0, 0.5), 0 0 28px rgba(45, 212, 191, 0.35);
  }

  .node-delete-btn {
    position: absolute;
    top: 10px;
    right: 10px;
    width: 24px;
    height: 24px;
    border-radius: 6px;
    border: none;
    background: rgba(255, 255, 255, 0.08);
    color: rgba(255, 255, 255, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    z-index: 10;
    transition: all 0.2s ease;

    &:hover {
      background: rgba(239, 68, 68, 0.25);
      color: #ef4444;
    }
  }

  .loop-node-shell {
    display: flex;
    flex-direction: column;
    height: 100%;

    .loop-node-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 10px;

      .loop-node-icon {
        width: 26px;
        height: 26px;
        border-radius: 6px;
        background: rgba(45, 212, 191, 0.2);
        color: #2dd4bf;
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: bold;
        font-size: 16px;
      }

      .loop-node-title {
        font-size: 13px;
        font-weight: 600;
        color: #f1f5f9;
      }
    }

    .loop-editor-surface {
      flex: 1;
      border-radius: 12px;
      overflow: hidden;
      background: rgba(10, 15, 26, 0.65);
      border: 1px solid rgba(255, 255, 255, 0.08);
    }
  }

  .node-add-btn {
    position: absolute;
    right: -13px;
    top: 50%;
    transform: translateY(-50%);
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: #2dd4bf;
    color: #0f172a;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(45, 212, 191, 0.4);
    z-index: 10;
    transition: all 0.2s ease;

    &:hover {
      transform: translateY(-50%) scale(1.2);
      background: #14b8a6;
      color: #fff;
    }
  }
}
</style>
