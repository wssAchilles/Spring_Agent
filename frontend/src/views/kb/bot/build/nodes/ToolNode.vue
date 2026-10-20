<template>
  <BaseNodeWrapper
    :id="id"
    node-type="tool"
    type-label="工具"
    :title="getNodeLabel(data, '工具')"
    :is-selected="isSelected"
    :drag-hover-target-id="dragHoverTargetId"
    @delete="$emit('delete', id)"
    @source-action="$emit('source-action', $event, id)"
  >
    <div class="tool-node-content">
      <div v-if="toolSubtitle" class="tool-subtitle" :title="toolSubtitle">
        {{ toolSubtitle }}
      </div>
      <div v-if="previewList.length" class="tool-params">
        <div v-for="param in previewList" :key="param.name" class="tool-param-chip">
          <span class="param-key">{{ param.name }}</span>
          <span class="param-val">{{ param.value }}</span>
        </div>
        <div v-if="overflowCount > 0" class="tool-more-chip">
          +{{ overflowCount }} 项参数
        </div>
      </div>
      <div v-else-if="!toolSubtitle" class="tool-empty-tip">未配置工具函数</div>
    </div>
  </BaseNodeWrapper>
</template>

<script setup>
import { computed } from "vue";
import BaseNodeWrapper from "./BaseNodeWrapper.vue";
import {
  getNodeLabel,
  getToolNodeSubtitle,
  getToolNodePreviewList,
  getToolNodePreviewOverflowCount,
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

defineEmits(["delete", "source-action"]);

const toolSubtitle = computed(() => getToolNodeSubtitle(props.data));
const previewList = computed(() => getToolNodePreviewList(props.data));
const overflowCount = computed(() => getToolNodePreviewOverflowCount(props.data));
</script>

<style scoped lang="scss">
.tool-node-content {
  .tool-subtitle {
    font-size: 11px;
    color: #f472b6;
    margin-bottom: 4px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    font-weight: 500;
  }

  .tool-params {
    display: flex;
    flex-direction: column;
    gap: 3px;

    .tool-param-chip {
      display: flex;
      justify-content: space-between;
      font-size: 10px;
      padding: 2px 6px;
      border-radius: 4px;
      background: rgba(236, 72, 153, 0.12);

      .param-key {
        color: #fbcfe8;
      }
      .param-val {
        color: rgba(255, 255, 255, 0.5);
      }
    }

    .tool-more-chip {
      font-size: 9px;
      color: #f472b6;
      text-align: right;
    }
  }

  .tool-empty-tip {
    font-size: 11px;
    color: rgba(255, 255, 255, 0.35);
  }
}
</style>
