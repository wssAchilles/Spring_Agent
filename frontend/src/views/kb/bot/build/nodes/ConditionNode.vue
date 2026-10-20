<template>
  <BaseNodeWrapper
    :id="id"
    node-type="condition"
    type-label="条件"
    :title="getNodeLabel(data, '条件分支')"
    :is-selected="isSelected"
    :drag-hover-target-id="dragHoverTargetId"
    @delete="$emit('delete', id)"
  >
    <div class="condition-node-content">
      <div class="condition-branch-list">
        <div
          v-for="(caseItem, index) in cases"
          :key="caseItem.id || index"
          class="condition-branch-row"
        >
          <span class="condition-branch-name">
            {{ getConditionCaseLabel(index) }}
          </span>
          <div class="condition-branch-output">
            <span class="condition-branch-tag">
              {{ getConditionBranchLabel(index, cases.length) }}
            </span>
            <div class="condition-branch-action">
              <Handle
                type="source"
                :id="getConditionHandleId(caseItem)"
                :position="Position.Right"
                :connectable-start="false"
                class="handle condition-branch-handle"
              />
              <div
                class="condition-branch-add-btn"
                title="从该分支连线"
                @click.stop.prevent
                @pointerdown.stop.prevent="
                  $emit('sourceAction', $event, id, getConditionHandleId(caseItem))
                "
              >
                <el-icon><Plus /></el-icon>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 覆盖默认 handles 插槽：只保留左侧 Target Handle，右侧 Source Handle 已集成在各分支中 -->
    <template #handles>
      <Handle
        type="target"
        :position="Position.Left"
        class="handle"
        :class="{ 'handle-drop-target': dragHoverTargetId === id }"
      />
    </template>
  </BaseNodeWrapper>
</template>

<script setup>
import { computed } from "vue";
import { Handle, Position } from "@vue-flow/core";
import { Plus } from "@element-plus/icons-vue";
import BaseNodeWrapper from "./BaseNodeWrapper.vue";
import {
  getNodeLabel,
  getConditionCases,
  getConditionCaseLabel,
  getConditionBranchLabel,
  getConditionHandleId,
} from "../utils/workflowHelpers.js";

const props = defineProps({
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

defineEmits(["delete", "sourceAction"]);

const cases = computed(() => getConditionCases(props.data));
</script>

<style scoped lang="scss">
.condition-node-content {
  .condition-branch-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-top: 4px;

    .condition-branch-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 4px 8px;
      border-radius: 6px;
      background: rgba(168, 85, 247, 0.1);
      border: 1px solid rgba(168, 85, 247, 0.2);

      .condition-branch-name {
        font-size: 11px;
        color: #e9d5ff;
        font-weight: 500;
      }

      .condition-branch-output {
        display: flex;
        align-items: center;
        gap: 6px;

        .condition-branch-tag {
          font-size: 10px;
          color: #c084fc;
          background: rgba(168, 85, 247, 0.2);
          padding: 1px 5px;
          border-radius: 4px;
        }

        .condition-branch-action {
          position: relative;
          width: 22px;
          height: 22px;
          display: flex;
          align-items: center;
          justify-content: center;

          :deep(.vue-flow__handle) {
            position: absolute;
            right: -6px;
            top: 50%;
            transform: translateY(-50%);
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: #a855f7;
            border: 2px solid #0f172a;
            z-index: 2;
          }

          .condition-branch-add-btn {
            position: relative;
            width: 20px;
            height: 20px;
            border-radius: 50%;
            background: #a855f7;
            color: #fff;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 11px;
            cursor: pointer;
            box-shadow: 0 2px 6px rgba(168, 85, 247, 0.4);
            transition: all 0.2s ease;

            &:hover {
              transform: scale(1.15);
              background: #9333ea;
            }
          }
        }
      }
    }
  }
}
</style>
