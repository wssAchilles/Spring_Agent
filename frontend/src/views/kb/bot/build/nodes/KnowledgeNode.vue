<template>
  <BaseNodeWrapper
    :id="id"
    node-type="knowledge"
    type-label="知识库"
    :title="getNodeLabel(data, '知识库检索')"
    :is-selected="isSelected"
    :drag-hover-target-id="dragHoverTargetId"
    @delete="$emit('delete', id)"
    @source-action="$emit('source-action', $event, id)"
  >
    <div class="knowledge-node-content">
      <div v-if="kbName" class="kb-badge" :title="kbName">
        <el-icon class="kb-icon"><FolderOpened /></el-icon>
        <span class="kb-name">{{ kbName }}</span>
      </div>
      <div v-else class="kb-empty-tip">未关联知识库 · 点击配置</div>
    </div>
  </BaseNodeWrapper>
</template>

<script setup>
import { computed } from "vue";
import { FolderOpened } from "@element-plus/icons-vue";
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

// 彻底清除常州工学院脏数据，只取用户真实绑定的名称
const kbName = computed(() => {
  const name = getNodeConfigValue(props.data, "knowledgeBaseName", "");
  return name && name.trim() ? name.trim() : "";
});
</script>

<style scoped lang="scss">
.knowledge-node-content {
  .kb-badge {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 3px 8px;
    border-radius: 6px;
    background: rgba(245, 158, 11, 0.15);
    border: 1px solid rgba(245, 158, 11, 0.25);
    font-size: 11px;
    color: #fde68a;
    max-width: 190px;

    .kb-icon {
      font-size: 12px;
      color: #fbbf24;
      flex-shrink: 0;
    }

    .kb-name {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .kb-empty-tip {
    font-size: 11px;
    color: rgba(255, 255, 255, 0.35);
  }
}
</style>
