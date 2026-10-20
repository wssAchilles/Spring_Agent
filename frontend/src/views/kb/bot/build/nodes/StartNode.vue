<template>
  <BaseNodeWrapper
    :id="id"
    node-type="start"
    type-label="开始"
    :title="getNodeLabel(data, '开始')"
    :can-delete="false"
    :is-selected="isSelected"
    :drag-hover-target-id="dragHoverTargetId"
    @source-action="$emit('source-action', $event, id)"
  >
    <div v-if="fields.length" class="start-node-fields">
      <div
        v-for="field in fields"
        :key="field.id || field.name"
        class="start-node-field-row"
      >
        <span class="start-node-field-name">{{ getStartFieldDisplayLabel(field) }}</span>
        <span class="start-node-field-type">{{ getStartFieldValueTypeLabel(field.type) }}</span>
      </div>
      <div v-if="overflowCount > 0" class="start-node-field-more">
        +{{ overflowCount }} 个输入字段
      </div>
    </div>
    <div v-else class="start-node-empty">
      <span class="empty-dot"></span>
      <span>暂无输入变量 · 点击配置</span>
    </div>
  </BaseNodeWrapper>
</template>

<script setup>
import { computed } from "vue";
import BaseNodeWrapper from "./BaseNodeWrapper.vue";
import {
  getNodeLabel,
  getStartFieldPreview,
  getStartFieldDisplayLabel,
  getStartFieldValueTypeLabel,
  getStartFieldOverflowCount,
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

defineEmits(["source-action"]);

const fields = computed(() => getStartFieldPreview(props.data));
const overflowCount = computed(() => getStartFieldOverflowCount(props.data));
</script>

<style scoped lang="scss">
.start-node-fields {
  display: flex;
  flex-direction: column;
  gap: 4px;

  .start-node-field-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 3px 6px;
    border-radius: 6px;
    background: rgba(255, 255, 255, 0.04);
    font-size: 11px;

    .start-node-field-name {
      color: #e2e8f0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .start-node-field-type {
      color: rgba(255, 255, 255, 0.45);
      font-size: 10px;
    }
  }

  .start-node-field-more {
    font-size: 10px;
    color: #818cf8;
    text-align: right;
    margin-top: 2px;
  }
}

.start-node-empty {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.4);
  padding: 6px 0;

  .empty-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #22c55e;
  }
}
</style>
