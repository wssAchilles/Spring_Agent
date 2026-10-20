import { ref, computed } from "vue";
import { getChatModelDict } from "@/api/ai/myModel/myModel.js";
import { listTool } from "@/api/kb/tool/tool.js";

function normalizeMenuToolIcon(value = "") {
  const normalizedValue = `${value || ""}`.trim();
  if (!normalizedValue || normalizedValue.length > 2) {
    return "🔧";
  }
  return normalizedValue;
}

const llmResponseFormatOptions = [
  { label: "文本", value: "text" },
  { label: "JSON", value: "json_object" },
  { label: "Markdown", value: "markdown" },
];

/**
 * 工作流模型字典、扩展工具列表与添加菜单选项 Composable
 */
export function useWorkflowMeta() {
  const llmModelOptions = ref([]);
  const toolMenuItems = ref([]);
  const toolMenuItemsLoaded = ref(false);
  const toolMenuLoading = ref(false);
  const toolMenuLoadError = ref(false);

  const defaultLlmModelConfig = computed(() => {
    const first = llmModelOptions.value[0];
    return {
      provider: first?.provider || "deepseek",
      model: first?.model || "deepseek-flash",
    };
  });

  const baseAddMenuItems = [
    {
      type: "llm",
      menuType: "basic",
      label: "大模型",
      description: "调用大语言模型进行认知推理或文本生成",
      enabled: true,
    },
    {
      type: "knowledge",
      menuType: "basic",
      label: "知识库检索",
      description: "在知识库超球面向量空间中进行语义检索",
      enabled: true,
    },
    {
      type: "condition",
      menuType: "basic",
      label: "条件分支",
      description: "根据前序节点输出进行条件分支流转",
      enabled: true,
    },
    {
      type: "tool",
      menuType: "basic",
      label: "外部工具",
      description: "调用系统注册的各类企业 MCP 工具",
      enabled: true,
    },
    {
      type: "loop",
      menuType: "basic",
      label: "循环迭代",
      description: "对数组变量进行有界循环子图处理",
      enabled: true,
    },
    {
      type: "reply",
      menuType: "basic",
      label: "直接回复",
      description: "向用户输出最终回答或中间结果",
      enabled: true,
    },
  ];

  function isAddableNodeTypeEnabled(type = "") {
    const normalized = `${type || ""}`.trim().toLowerCase();
    return ["llm", "knowledge", "condition", "tool", "loop", "reply"].includes(
      normalized
    );
  }

  function normalizeToolMenuItem(item = {}) {
    return {
      type: "tool",
      menuType: "tools",
      toolId: item.id || item.toolId,
      name: item.name || item.toolName,
      label: item.name || item.toolName || "扩展工具",
      description: item.description || "企业级 MCP 工具",
      icon: normalizeMenuToolIcon(item.icon),
      enabled: true,
      rawItem: item,
    };
  }

  function normalizeToolMenuList(response = {}) {
    const list = Array.isArray(response)
      ? response
      : response?.rows || response?.data || [];
    return list.map(normalizeToolMenuItem);
  }

  async function ensureToolMenuItemsLoaded(force = false) {
    if (toolMenuItemsLoaded.value && !force) {
      return toolMenuItems.value;
    }
    toolMenuLoading.value = true;
    toolMenuLoadError.value = false;
    try {
      const res = await listTool({ pageNum: 1, pageSize: 100 });
      toolMenuItems.value = normalizeToolMenuList(res);
      toolMenuItemsLoaded.value = true;
      return toolMenuItems.value;
    } catch (e) {
      console.error("加载工具列表失败:", e);
      toolMenuLoadError.value = true;
      return [];
    } finally {
      toolMenuLoading.value = false;
    }
  }

  async function getModelList() {
    try {
      const res = await getChatModelDict();
      const list = Array.isArray(res) ? res : res?.data || [];
      llmModelOptions.value = list.map((item) => ({
        label: item.modelName || item.model,
        value: item.model,
        model: item.model,
        provider: item.provider,
        id: item.id,
      }));
    } catch (e) {
      console.warn("加载模型字典失败，使用 DeepSeek 官方默认值:", e);
      llmModelOptions.value = [
        {
          label: "DeepSeek Flash",
          value: "deepseek-flash",
          model: "deepseek-flash",
          provider: "deepseek",
        },
      ];
    }
  }

  function getMenuItemsForTab(activeTab = "basic") {
    if (activeTab === "tools") {
      return toolMenuItems.value;
    }
    return baseAddMenuItems;
  }

  return {
    llmModelOptions,
    llmResponseFormatOptions,
    defaultLlmModelConfig,
    toolMenuItems,
    toolMenuLoading,
    toolMenuLoadError,
    baseAddMenuItems,
    isAddableNodeTypeEnabled,
    ensureToolMenuItemsLoaded,
    getModelList,
    getMenuItemsForTab,
  };
}
