<template>
  <div class="node-config-drawer-container">
    <div
      class="drawer-header"
      :class="{
        'is-start': ['start', 'llm', 'reply'].includes(selectedNode?.type),
      }"
    >
      <div class="drawer-header-main">
        <div class="drawer-title">
          <div class="drawer-icon" :class="selectedNode?.type">
            <component :is="nodeIconComponent" />
          </div>
          <el-input
            v-if="selectedNode"
            :model-value="getNodeLabel(selectedNode.data)"
            class="node-config-header-name"
            placeholder="输入节点名称"
            @update:model-value="
              $emit('update-field', { field: 'label', value: $event })
            "
          />
        </div>
      </div>
      <el-icon class="close-icon" @click="$emit('close')"><Close /></el-icon>
    </div>

    <div class="drawer-content">
      <!-- 开始节点表单 -->
      <StartNodeConfigForm
        v-if="selectedNode?.type === 'start'"
        :fields="selectedStartFieldCards"
        @addField="$emit('open-start-field-dialog')"
        @editField="$emit('open-start-field-dialog', $event)"
        @removeField="$emit('remove-start-field', $event)"
      />

      <!-- LLM 节点表单 -->
      <LlmNodeConfigForm
        v-else-if="selectedNode?.type === 'llm'"
        :node="selectedLlmNodeView"
        :model-options="llmModelOptions"
        :response-format-options="llmResponseFormatOptions"
        :available-context-variable-groups="selectedLlmContextVariableGroups"
        :has-upstream-context-sources="selectedLlmHasUpstreamContextSources"
        :dialog-append-to="dialogAppendTo || 'body'"
        @updateField="$emit('update-field', $event)"
        @updateMessage="$emit('update-llm-message', $event)"
        @addMessage="$emit('add-llm-message')"
        @removeMessage="$emit('remove-llm-message', $event)"
      />

      <!-- 对话流回复表单 -->
      <ChatflowReplyNodeConfigForm
        v-else-if="selectedNode?.type === 'reply' && isChatflowWorkflow"
        :content="selectedReplyContent"
        :available-context-variable-groups="selectedReplyContextVariableGroups"
        :has-upstream-context-sources="selectedReplyHasUpstreamContextSources"
        @updateContent="$emit('update-reply-content', $event)"
      />

      <!-- 工作流输出表单 -->
      <ReplyNodeConfigForm
        v-else-if="selectedNode?.type === 'reply'"
        :outputs="selectedReplyOutputs"
        :available-context-variable-groups="selectedReplyContextVariableGroups"
        :has-upstream-context-sources="selectedReplyHasUpstreamContextSources"
        @addOutput="$emit('add-reply-output')"
        @updateOutput="$emit('update-reply-output', $event)"
        @removeOutput="$emit('remove-reply-output', $event)"
      />

      <!-- 工具节点表单 -->
      <ToolNodeConfigForm
        v-else-if="selectedNode?.type === 'tool'"
        :tool-id="getNodeConfigValue(selectedNode?.data, 'toolId', '')"
        :tool-name="getNodeConfigValue(selectedNode?.data, 'toolName', '')"
        :tool-source="getNodeConfigValue(selectedNode?.data, 'toolSource', '')"
        :tool-description="getNodeConfigValue(selectedNode?.data, 'toolDescription', '')"
        :description="getNodeDescription(selectedNode?.data)"
        :outputs="selectedToolOutputs"
        @updateField="$emit('update-field', $event)"
        @addOutput="$emit('add-tool-output')"
        @updateOutput="$emit('update-tool-output', $event)"
        @removeOutput="$emit('remove-tool-output', $event)"
      />

      <!-- 知识库节点表单 -->
      <KnowledgeNodeConfigForm
        v-else-if="selectedNode?.type === 'knowledge' || selectedNode?.type === 'knowledge-retrieval'"
        :node="selectedNode?.data"
        :description="getNodeDescription(selectedNode?.data)"
        @updateField="$emit('update-field', $event)"
        @updateConfig="$emit('update-knowledge-config', $event)"
      />

      <!-- 条件分支表单 -->
      <ConditionNodeConfigForm
        v-else-if="selectedNode?.type === 'condition'"
        :cases="selectedConditionCases"
        @updateCase="$emit('update-condition-case', $event)"
        @addCase="$emit('add-condition-case')"
        @removeCase="$emit('remove-condition-case', $event)"
      />

      <!-- 循环节点表单 -->
      <LoopNodeConfigForm
        v-else-if="selectedNode?.type === 'loop'"
        :item-alias="getNodeConfigValue(selectedNode?.data, 'itemAlias', '')"
        :max-iterations="Number(getNodeConfigValue(selectedNode?.data, 'maxIterations', 1)) || 1"
        @updateField="$emit('update-field', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { Close, Cpu, ChatDotRound, FolderOpened, Service, Operation, Refresh } from "@element-plus/icons-vue";
import {
  getNodeLabel,
  getNodeDescription,
  getNodeConfigValue,
} from "../../utils/workflowHelpers.js";

import StartNodeConfigForm from "../nodeConfig/StartNodeConfigForm.vue";
import LlmNodeConfigForm from "../nodeConfig/LlmNodeConfigForm.vue";
import ChatflowReplyNodeConfigForm from "../nodeConfig/ChatflowReplyNodeConfigForm.vue";
import ReplyNodeConfigForm from "../nodeConfig/ReplyNodeConfigForm.vue";
import ConditionNodeConfigForm from "../nodeConfig/ConditionNodeConfigForm.vue";
import LoopNodeConfigForm from "../nodeConfig/LoopNodeConfigForm.vue";
import ToolNodeConfigForm from "../nodeConfig/ToolNodeConfigForm.vue";
import KnowledgeNodeConfigForm from "../nodeConfig/KnowledgeNodeConfigForm.vue";

const props = defineProps({
  selectedNode: {
    type: Object,
    default: null,
  },
  isChatflowWorkflow: {
    type: Boolean,
    default: false,
  },
  selectedStartFieldCards: {
    type: Array,
    default: () => [],
  },
  selectedLlmNodeView: {
    type: Object,
    default: () => ({}),
  },
  llmModelOptions: {
    type: Array,
    default: () => [],
  },
  llmResponseFormatOptions: {
    type: Array,
    default: () => [],
  },
  selectedLlmContextVariableGroups: {
    type: Array,
    default: () => [],
  },
  selectedLlmHasUpstreamContextSources: {
    type: Boolean,
    default: false,
  },
  selectedReplyContent: {
    type: String,
    default: "",
  },
  selectedReplyOutputs: {
    type: Array,
    default: () => [],
  },
  selectedReplyContextVariableGroups: {
    type: Array,
    default: () => [],
  },
  selectedReplyHasUpstreamContextSources: {
    type: Boolean,
    default: false,
  },
  selectedToolOutputs: {
    type: Array,
    default: () => [],
  },
  selectedConditionCases: {
    type: Array,
    default: () => [],
  },
  dialogAppendTo: {
    type: [Object, String],
    default: "body",
  },
});

defineEmits([
  "close",
  "update-field",
  "open-start-field-dialog",
  "remove-start-field",
  "update-llm-message",
  "add-llm-message",
  "remove-llm-message",
  "update-reply-content",
  "add-reply-output",
  "update-reply-output",
  "remove-reply-output",
  "add-tool-output",
  "update-tool-output",
  "remove-tool-output",
  "update-knowledge-config",
  "update-condition-case",
  "add-condition-case",
  "remove-condition-case",
]);

const nodeIconComponent = computed(() => {
  switch (props.selectedNode?.type) {
    case "start": return Operation;
    case "llm": return Cpu;
    case "reply": return ChatDotRound;
    case "knowledge":
    case "knowledge-retrieval": return FolderOpened;
    case "tool": return Service;
    case "condition": return Operation;
    case "loop": return Refresh;
    default: return Cpu;
  }
});
</script>

<style scoped lang="scss">
.node-config-drawer-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}
</style>
