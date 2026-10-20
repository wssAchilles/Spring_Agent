<template>
  <BaseNodeWrapper
    :id="id"
    node-type="reply"
    :type-label="isChatflow ? '回复' : '输出'"
    :title="getNodeLabel(data, isChatflow ? '直接回复' : '输出')"
    :is-selected="isSelected"
    :drag-hover-target-id="dragHoverTargetId"
    @delete="$emit('delete', id)"
    @source-action="$emit('source-action', $event, id)"
  >
    <div class="reply-node-content">
      <div v-if="contentPreview" class="reply-preview-text" :title="contentPreview">
        {{ contentPreview }}
      </div>
      <div v-else-if="outputs.length" class="reply-outputs">
        <span v-for="item in outputs" :key="item.variableKey" class="reply-output-tag">
          {{ item.variableLabel || item.variableKey }}
        </span>
      </div>
      <div v-else class="reply-empty-tip">点击配置输出内容</div>
    </div>
  </BaseNodeWrapper>
</template>

<script setup>
import { computed } from "vue";
import BaseNodeWrapper from "./BaseNodeWrapper.vue";
import {
  getNodeLabel,
  getReplyContent,
  getReplyOutputs,
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
  isChatflow: {
    type: Boolean,
    default: false,
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

const contentPreview = computed(() => {
  const c = getReplyContent(props.data);
  return c ? String(c).trim() : "";
});

const outputs = computed(() => getReplyOutputs(props.data));
</script>

<style scoped lang="scss">
.reply-node-content {
  .reply-preview-text {
    font-size: 11px;
    color: #bae6fd;
    background: rgba(14, 165, 233, 0.12);
    border: 1px solid rgba(14, 165, 233, 0.2);
    padding: 4px 8px;
    border-radius: 6px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 190px;
  }

  .reply-outputs {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;

    .reply-output-tag {
      font-size: 10px;
      padding: 2px 6px;
      border-radius: 4px;
      background: rgba(14, 165, 233, 0.15);
      color: #38bdf8;
    }
  }

  .reply-empty-tip {
    font-size: 11px;
    color: rgba(255, 255, 255, 0.35);
  }
}
</style>
