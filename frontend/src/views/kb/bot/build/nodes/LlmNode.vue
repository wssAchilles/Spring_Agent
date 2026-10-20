<template>
  <BaseNodeWrapper
    :id="id"
    node-type="llm"
    type-label="LLM"
    :title="getNodeLabel(data, 'LLM')"
    :is-selected="isSelected"
    :drag-hover-target-id="dragHoverTargetId"
    @delete="$emit('delete', id)"
    @source-action="$emit('source-action', $event, id)"
  >
    <div class="llm-node-content">
      <div v-if="modelName" class="llm-model-badge">
        <span class="model-name">{{ modelName }}</span>
        <span v-if="hasThinking" class="thinking-pill">Thinking</span>
      </div>
      <div v-else class="llm-empty-tip">未配置模型</div>
    </div>
  </BaseNodeWrapper>
</template>

<script setup>
import { computed } from "vue";
import BaseNodeWrapper from "./BaseNodeWrapper.vue";
import { getNodeLabel, getNodeConfigValue } from "../utils/workflowHelpers.js";

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

defineEmits(["delete", "source-action"]);

const modelName = computed(() => getNodeConfigValue(props.data, "model", ""));
const hasThinking = computed(() => {
  const extra = getNodeConfigValue(props.data, "extra_body", null);
  return Boolean(extra?.thinking?.type === "enabled");
});
</script>

<style scoped lang="scss">
.llm-node-content {
  .llm-model-badge {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 3px 8px;
    border-radius: 6px;
    background: rgba(99, 102, 241, 0.15);
    border: 1px solid rgba(99, 102, 241, 0.25);
    font-size: 11px;
    color: #c7d2fe;

    .model-name {
      font-weight: 500;
      max-width: 130px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .thinking-pill {
      font-size: 9px;
      padding: 1px 4px;
      border-radius: 4px;
      background: rgba(168, 85, 247, 0.3);
      color: #e9d5ff;
      font-weight: 600;
      letter-spacing: 0.3px;
    }
  }

  .llm-empty-tip {
    font-size: 11px;
    color: rgba(255, 255, 255, 0.35);
  }
}
</style>
