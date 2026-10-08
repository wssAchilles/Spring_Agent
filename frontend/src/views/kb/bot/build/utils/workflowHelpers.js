import {
  cloneNodeData,
  createStructuredNodeData,
  getNodeConfig,
  getNodeConfigValue,
  getNodeDescription,
  getNodeInput,
  getNodeLabel,
  getNodeOutput,
  normalizeTemplateReferenceSegment,
  omitObjectKeys,
  setNodeConfigValue,
} from "./nodeData.js";

// ==========================================
// 1. 核心常量与枚举选项
// ==========================================
export const TOOL_NODE_TYPE = "tool";
export const LOOP_OUTER_TARGET_HANDLE_ID = "loop-node-target";
export const LOOP_OUTER_SOURCE_HANDLE_ID = "loop-node-source";

export const CHATFLOW_DEFAULT_START_FIELD_ID = "chatflow-default-query-field";
export const CHATFLOW_LEGACY_DEFAULT_START_FIELD_ID = "chatflow-default-user-field";
export const START_FIELD_NAME_PATTERN = /^[A-Za-z_][A-Za-z0-9_.]*$/;

export const startFieldTypeOptions = [
  {
    value: "text",
    label: "文本",
    valueType: "string",
    icon: "T",
    defaultMaxLength: 48,
    supportsMaxLength: true,
  },
  {
    value: "paragraph",
    label: "段落",
    valueType: "string",
    icon: "P",
    defaultMaxLength: 200,
    supportsMaxLength: true,
  },
];

// ==========================================
// 2. 局部种子计数器与 ID 构造函数
// ==========================================
let nodeSeed = 0;
let conditionCaseSeed = 0;
let startFieldSeed = 0;
let llmMessageSeed = 0;
let replyOutputSeed = 0;
let toolOutputSeed = 0;
let loopStepSeed = 0;
let edgeSeed = 0;

export function buildGeneratedId(prefix = "node", seed = 0) {
  return normalizeTemplateReferenceSegment(
    `${prefix}_${Date.now()}_${seed}`,
    prefix
  );
}

export function buildNodeId(prefix = "node") {
  nodeSeed += 1;
  return buildGeneratedId(prefix, nodeSeed);
}

export function buildCaseId(prefix = "case") {
  conditionCaseSeed += 1;
  return `${prefix}-${Date.now()}-${conditionCaseSeed}`;
}

export function buildStartFieldId(prefix = "start-field") {
  startFieldSeed += 1;
  return `${prefix}-${Date.now()}-${startFieldSeed}`;
}

export function buildReplyOutputId(prefix = "reply-output") {
  replyOutputSeed += 1;
  return `${prefix}-${Date.now()}-${replyOutputSeed}`;
}

export function buildToolOutputId(prefix = "tool-output") {
  toolOutputSeed += 1;
  return `${prefix}-${Date.now()}-${toolOutputSeed}`;
}

export function buildLlmMessageId(prefix = "llm-message") {
  llmMessageSeed += 1;
  return `${prefix}-${Date.now()}-${llmMessageSeed}`;
}

export function buildLoopStepId(prefix = "loop-node") {
  loopStepSeed += 1;
  return buildGeneratedId(prefix, loopStepSeed);
}

export function createEdgeId(source, target, sourceHandle, targetHandle) {
  edgeSeed += 1;
  return [
    "e",
    source,
    sourceHandle || "source",
    target,
    targetHandle || "target",
    edgeSeed,
  ]
    .filter(Boolean)
    .join("-");
}

// ==========================================
// 3. 通用辅助函数（路径规范化、提供商解析等）
// ==========================================
export function normalizeLoopPath(loopPath = []) {
  return Array.isArray(loopPath) ? loopPath.filter(Boolean) : [];
}

export function areLoopPathsEqual(leftPath = [], rightPath = []) {
  const left = normalizeLoopPath(leftPath);
  const right = normalizeLoopPath(rightPath);

  if (left.length !== right.length) {
    return false;
  }

  return left.every((item, index) => item === right[index]);
}

export function normalizeModelProviderValue(value, fallback = "") {
  const parseProviderValue = (targetValue) => {
    if (targetValue === undefined || targetValue === null) {
      return null;
    }
    if (typeof targetValue === "number") {
      return targetValue;
    }
    const trimmed = `${targetValue}`.trim();
    if (!trimmed) {
      return null;
    }
    const parsed = Number(trimmed);
    return Number.isFinite(parsed) ? parsed : trimmed;
  };

  const parsedValue = parseProviderValue(value);
  return parsedValue !== null ? parsedValue : fallback;
}

export function getNodeTypeLabel(nodeType) {
  const labels = {
    start: "开始",
    llm: "LLM",
    reply: "回复",
    tool: "工具",
    condition: "条件分支",
    loop: "循环",
  };

  return labels[nodeType] || "节点";
}

// ==========================================
// 4. Start 开始节点相关函数
// ==========================================
export function getStartFieldTypeMeta(type = "text") {
  return (
    startFieldTypeOptions.find((item) => item.value === type) ||
    startFieldTypeOptions[0]
  );
}

export function createStartField(overrides = {}) {
  const meta = getStartFieldTypeMeta(overrides.type);
  const normalizedMaxLength = Number(overrides.maxLength);

  return {
    id: overrides.id || buildStartFieldId(meta.value),
    type: meta.value,
    name: overrides.name || "",
    label: overrides.label || "",
    maxLength: meta.supportsMaxLength
      ? Number.isFinite(normalizedMaxLength) && normalizedMaxLength > 0
        ? normalizedMaxLength
        : meta.defaultMaxLength
      : null,
    defaultValue:
      overrides.defaultValue === undefined || overrides.defaultValue === null
        ? ""
        : `${overrides.defaultValue}`,
    required:
      overrides.required === undefined ? true : Boolean(overrides.required),
    readonly: Boolean(overrides.readonly),
  };
}

export function createChatflowDefaultStartField(overrides = {}) {
  return createStartField({
    ...overrides,
    id: CHATFLOW_DEFAULT_START_FIELD_ID,
    type: "text",
    name: "query",
    label: "用户",
    maxLength: 48,
    defaultValue: "",
    required: true,
    readonly: true,
  });
}

export function isReadonlyStartField(field = {}) {
  return Boolean(field?.readonly);
}

export function isChatflowDefaultStartField(field = {}) {
  const fieldId = `${field?.id || ""}`.trim();
  const fieldName = `${field?.name || ""}`.trim().toLowerCase();

  return (
    fieldId === CHATFLOW_DEFAULT_START_FIELD_ID ||
    fieldId === CHATFLOW_LEGACY_DEFAULT_START_FIELD_ID ||
    fieldName === "query" ||
    fieldName === "user"
  );
}

export function normalizeStartField(field = {}) {
  const meta = getStartFieldTypeMeta(field.type);
  const nextField = createStartField(field);

  return {
    ...nextField,
    name: nextField.name.trim(),
    label: nextField.label.trim(),
    maxLength: meta.supportsMaxLength
      ? Number(nextField.maxLength) > 0
        ? Number(nextField.maxLength)
        : meta.defaultMaxLength
      : null,
  };
}

export function normalizeStartFields(fields = [], isChatflow = false) {
  const normalizedFields = (Array.isArray(fields) ? fields : [])
    .filter(Boolean)
    .map((field) => normalizeStartField(field));

  if (!isChatflow) {
    return normalizedFields;
  }

  const customFields = [];
  let defaultFieldSource = null;

  normalizedFields.forEach((field) => {
    if (isChatflowDefaultStartField(field)) {
      if (!defaultFieldSource) {
        defaultFieldSource = field;
      }
      return;
    }
    customFields.push(field);
  });

  return [
    createChatflowDefaultStartField(defaultFieldSource || {}),
    ...customFields,
  ];
}

export function normalizeStartNodeData(data = {}, isChatflow = false) {
  const structuredInput = getNodeInput(data);
  const rawFields = structuredInput.length
    ? structuredInput
    : Array.isArray(data.fields)
    ? data.fields
    : Array.isArray(data.inputFields)
    ? data.inputFields
    : [];
  const config = {
    ...omitObjectKeys(data, [
      "input",
      "config",
      "output",
      "fields",
      "inputFields",
    ]),
    ...getNodeConfig(data),
    label: getNodeLabel(data, "开始"),
    description: getNodeDescription(data),
  };

  return createStructuredNodeData({
    input: normalizeStartFields(rawFields, isChatflow),
    config,
    output: getNodeOutput(data),
  });
}

export function getStartFields(data = {}, isChatflow = false) {
  return getNodeInput(normalizeStartNodeData(data, isChatflow));
}

export function getStartFieldValueTypeLabel(type = "text") {
  return getStartFieldTypeMeta(type).valueType;
}

export function getStartFieldDisplayLabel(field = {}) {
  return field.label || field.name || "未命名字段";
}

export function getStartFieldPreview(data = {}, limit = 3, isChatflow = false) {
  return getStartFields(data, isChatflow).slice(0, limit);
}

export function getStartFieldOverflowCount(data = {}, limit = 3, isChatflow = false) {
  return Math.max(0, getStartFields(data, isChatflow).length - limit);
}

// ==========================================
// 5. Condition 条件分支节点相关函数
// ==========================================
export function createConditionCase(isElse = false, overrides = {}) {
  return {
    id: buildCaseId(isElse ? "else" : "case"),
    expression: "",
    isElse,
    ...overrides,
  };
}

export function normalizeConditionData(data = {}) {
  let rawCases = Array.isArray(getNodeConfigValue(data, "cases", null))
    ? getNodeConfigValue(data, "cases", [])
    : Array.isArray(data.cases)
    ? data.cases
    : [];

  // 若无 cases，尝试从后端的 conditions 字段适配
  if (!rawCases.length) {
    const rawConditions = Array.isArray(getNodeConfigValue(data, "conditions", null))
      ? getNodeConfigValue(data, "conditions", [])
      : Array.isArray(data.conditions)
      ? data.conditions
      : [];
    if (rawConditions.length) {
      rawCases = rawConditions.map((item, idx) => {
        let cid = item.id;
        if (item.targetHandle) {
          cid = item.targetHandle.replace(/^condition-case-/, "");
        }
        if (!cid) {
          cid = idx === rawConditions.length - 1 ? "fast" : `case-${idx + 1}`;
        }
        if (cid && /^case[_-]([a-zA-Z0-9]+)$/.test(cid)) {
          cid = cid.replace(/^case[_-]/, "");
        }
        const cleanTargetHandle = item.targetHandle || `condition-case-${cid}`;
        return {
          id: cid,
          targetHandle: cleanTargetHandle,
          expression: item.expression || "",
          label: item.label || "",
          isElse: Boolean(
            item.isElse ||
            cid.includes("else") ||
            cid.includes("fast") ||
            (idx === rawConditions.length - 1 && (!item.conditions || item.conditions.length === 0))
          ),
        };
      });
    }
  }

  const normalizedCases = rawCases.filter(Boolean).map((item, idx) => {
    let cid = item.id;
    if (item.targetHandle) {
      cid = item.targetHandle.replace(/^condition-case-/, "");
    }
    if (cid && /^case[_-]([a-zA-Z0-9]+)$/.test(cid)) {
      cid = cid.replace(/^case[_-]/, "");
    }
    const finalId = cid || (item.isElse ? "else" : `case-${idx + 1}`);
    const cleanTargetHandle = item.targetHandle || `condition-case-${finalId}`;
    return {
      id: finalId,
      targetHandle: cleanTargetHandle,
      expression: item.expression || "",
      label: item.label || "",
      isElse: Boolean(item.isElse),
    };
  });

  const branchCases = normalizedCases.filter((item) => !item.isElse);
  if (!branchCases.length) {
    branchCases.push(
      createConditionCase(false, { id: "deep", targetHandle: "condition-case-deep", expression: data.expression || "" })
    );
  }

  const elseCase =
    normalizedCases.find((item) => item.isElse) || createConditionCase(true, { id: "fast", targetHandle: "condition-case-fast" });

  return createStructuredNodeData({
    input: getNodeInput(data),
    config: {
      ...omitObjectKeys(data, [
        "input",
        "config",
        "output",
        "cases",
        "conditions",
        "expression",
        "label",
      ]),
      ...getNodeConfig(data),
      label: getNodeLabel(data, "条件分支"),
      cases: [...branchCases, elseCase],
    },
    output: getNodeOutput(data),
  });
}

export function getConditionCases(data = {}) {
  return getNodeConfigValue(normalizeConditionData(data), "cases", []);
}

export function getConditionCaseLabel(index) {
  return `CASE ${index + 1}`;
}

export function getConditionBranchLabel(index, total) {
  if (index === 0) return "IF";
  if (index === total - 1) return "ELSE";
  return "ELIF";
}

export function getConditionHandleId(caseItemOrId) {
  if (!caseItemOrId) return "condition-case-default";
  let target = "";
  if (typeof caseItemOrId === "object") {
    target = caseItemOrId.targetHandle || caseItemOrId.id || "";
  } else {
    target = String(caseItemOrId);
  }
  if (!target) return "condition-case-default";
  if (target.startsWith("condition-case-")) {
    return target;
  }
  if (/^case[_-]([a-zA-Z0-9]+)$/.test(target)) {
    target = target.replace(/^case[_-]/, "");
  }
  return `condition-case-${target}`;
}

export function getConditionDefaultSourceHandle(data = {}) {
  const firstCase = getConditionCases(data)[0];
  return firstCase ? getConditionHandleId(firstCase) : null;
}

// ==========================================
// 6. Reply 回复节点相关函数
// ==========================================
export function createReplyOutput(overrides = {}) {
  const binding =
    overrides.binding && typeof overrides.binding === "object"
      ? overrides.binding
      : {};

  return {
    id: overrides.id || buildReplyOutputId(),
    name: `${overrides.name || overrides.variableName || ""}`,
    sourceNodeId: `${overrides.sourceNodeId || binding.sourceNodeId || ""}`,
    sourceNodeLabel: `${
      overrides.sourceNodeLabel || binding.sourceNodeLabel || ""
    }`,
    sourceNodeType: `${
      overrides.sourceNodeType || binding.sourceNodeType || ""
    }`,
    variableKey: `${overrides.variableKey || binding.variableKey || ""}`,
    variableLabel: `${
      overrides.variableLabel ||
      binding.variableLabel ||
      overrides.variableKey ||
      binding.variableKey ||
      ""
    }`,
    valueType: `${overrides.valueType || binding.valueType || ""}`,
    path: `${
      overrides.path ||
      binding.path ||
      overrides.variableKey ||
      binding.variableKey ||
      ""
    }`,
  };
}

export function normalizeReplyOutput(output = {}) {
  const nextOutput = createReplyOutput(output);

  return {
    ...nextOutput,
    name: nextOutput.name.trim(),
    sourceNodeId: nextOutput.sourceNodeId.trim(),
    sourceNodeLabel: nextOutput.sourceNodeLabel.trim(),
    sourceNodeType: nextOutput.sourceNodeType.trim(),
    variableKey: nextOutput.variableKey.trim(),
    variableLabel: (nextOutput.variableLabel || nextOutput.variableKey).trim(),
    valueType: nextOutput.valueType.trim(),
    path: (nextOutput.path || nextOutput.variableKey).trim(),
  };
}

export function getReplyNodeLabel(data = {}, fallback = "输出") {
  const label = `${getNodeLabel(data, "") || ""}`.trim();

  if (!label || label === "直接回复") {
    return fallback;
  }

  return label;
}

export function buildReplyPreviewReferenceSegment(value = "", fallback = "node") {
  const normalized = `${value || ""}`
    .trim()
    .replace(/[^A-Za-z0-9_]+/g, "_")
    .replace(/^_+|_+$/g, "");
  const nextValue = normalized || fallback;

  return /^[0-9]/.test(nextValue) ? `node_${nextValue}` : nextValue;
}

export function buildReplyPreviewVariableReferenceKey(
  sourceSegment = "",
  variablePath = ""
) {
  return `${sourceSegment || ""}::${variablePath || ""}`;
}

export function getReplyPreviewVariableFallbackLabel(
  variablePath = "",
  sourceSegment = ""
) {
  const normalizedPath = `${variablePath || ""}`.trim();

  if (!normalizedPath) {
    return `${sourceSegment || ""}`.trim();
  }

  const pathSegments = normalizedPath.split(".").filter(Boolean);
  return pathSegments[pathSegments.length - 1] || normalizedPath;
}

export function getReplyPreviewText(content = "", limit = 30) {
  const normalizedContent = `${content || ""}`.replace(/\s+/g, " ").trim();

  if (!normalizedContent) {
    return "";
  }

  return normalizedContent.length > limit
    ? `${normalizedContent.slice(0, limit)}...`
    : normalizedContent;
}

export function normalizeReplyNodeData(data = {}) {
  const structuredOutput = getNodeOutput(data);
  const rawOutputs = structuredOutput.length
    ? structuredOutput
    : Array.isArray(data.outputs)
    ? data.outputs
    : Array.isArray(data.outputVariables)
    ? data.outputVariables
    : [];
  const config = {
    ...omitObjectKeys(data, [
      "input",
      "config",
      "output",
      "outputs",
      "outputVariables",
      "content",
      "description",
      "label",
    ]),
    ...getNodeConfig(data),
    label: getReplyNodeLabel(data),
    description: getNodeDescription(data),
    content:
      typeof getNodeConfigValue(data, "content", "") === "string"
        ? getNodeConfigValue(data, "content", "")
        : "",
  };

  return createStructuredNodeData({
    input: getNodeInput(data),
    config,
    output: rawOutputs
      .filter(Boolean)
      .map((output) => normalizeReplyOutput(output)),
  });
}

export function getReplyContent(data = {}) {
  return `${
    getNodeConfigValue(normalizeReplyNodeData(data), "content", "") || ""
  }`;
}

export function getReplyOutputs(data = {}) {
  return getNodeOutput(normalizeReplyNodeData(data));
}

export function getReplyContextVariables(data = {}) {
  return getReplyOutputs(data)
    .map((output) => {
      const outputName = `${output.name || ""}`.trim();

      if (!outputName) {
        return null;
      }

      return {
        variableKey: outputName,
        variableLabel: outputName,
        valueType: `${output.valueType || ""}`.trim(),
        path: outputName,
      };
    })
    .filter(Boolean);
}

export function getReplyNodePreviewItems(data = {}, isChatflow = false) {
  if (isChatflow) {
    return getReplyPreviewText(getReplyContent(data));
  }

  const replyOutputs = getReplyOutputs(data);

  if (replyOutputs.length) {
    const firstOutput = replyOutputs[0];
    const variableKey = `${
      firstOutput.variableLabel || firstOutput.variableKey || ""
    }`.trim();

    if (variableKey) {
      return variableKey;
    }

    if (firstOutput.name) {
      return firstOutput.name;
    }
  }

  const legacyContent = `${getReplyContent(data) || ""}`.trim();
  return legacyContent ? `@${legacyContent}` : "";
}

export function getReplyNodePreviewList(data = {}, limit = null, isChatflow = false) {
  let previewList = [];

  if (isChatflow) {
    const replyPreviewText = getReplyPreviewText(getReplyContent(data));
    previewList = replyPreviewText ? [replyPreviewText] : [];
  } else {
    const replyOutputs = getReplyOutputs(data);

    if (replyOutputs.length) {
      previewList = replyOutputs
        .map((output) => {
          const variableKey = `${
            output.variableLabel || output.variableKey || ""
          }`.trim();

          if (variableKey) {
            return variableKey;
          }

          return `${output.name || ""}`.trim();
        })
        .filter(Boolean);
    } else {
      const legacyPreviewText = `${
        getReplyNodePreviewItems(data, false) || ""
      }`.trim();
      previewList = legacyPreviewText ? [legacyPreviewText] : [];
    }
  }

  if (Number.isFinite(limit)) {
    return previewList.slice(0, Math.max(0, Math.floor(limit)));
  }

  return previewList;
}

export function getReplyNodePreviewOverflowCount(data = {}, limit = 3, isChatflow = false) {
  return Math.max(0, getReplyNodePreviewList(data, null, isChatflow).length - limit);
}

export function getReplyNodePreviewSegments(
  data = {},
  nodeId = "",
  optionMap = new Map(),
  isChatflow = false
) {
  if (!isChatflow) {
    return [];
  }

  const content = `${getReplyContent(data) || ""}`;

  if (!content.trim()) {
    return [];
  }

  const variablePattern = /\{\{\s*([A-Za-z0-9_]+)\.([A-Za-z0-9_.]+)\s*\}\}/g;
  const segments = [];
  let lastIndex = 0;
  let match = variablePattern.exec(content);

  while (match) {
    const [rawValue, sourceSegment, variablePath] = match;

    if (match.index > lastIndex) {
      segments.push({
        type: "text",
        value: content.slice(lastIndex, match.index),
      });
    }

    const binding = optionMap.get(
      buildReplyPreviewVariableReferenceKey(sourceSegment, variablePath)
    );

    segments.push({
      type: "variable",
      rawValue,
      display:
        binding?.variableLabel ||
        binding?.variableKey ||
        getReplyPreviewVariableFallbackLabel(variablePath, sourceSegment),
    });

    lastIndex = match.index + rawValue.length;
    match = variablePattern.exec(content);
  }

  if (lastIndex < content.length) {
    segments.push({
      type: "text",
      value: content.slice(lastIndex),
    });
  }

  return segments.length ? segments : [{ type: "text", value: content }];
}

// ==========================================
// 7. Tool 工具节点相关函数
// ==========================================
export function createToolOutput(overrides = {}) {
  const variableKey =
    `${overrides.variableKey || overrides.path || "result"}`.trim() || "result";

  return {
    id: overrides.id || buildToolOutputId(),
    variableKey,
    variableLabel:
      `${overrides.variableLabel || variableKey}`.trim() || variableKey,
    valueType: `${overrides.valueType || "string"}`.trim() || "string",
    path: `${overrides.path || variableKey}`.trim() || variableKey,
  };
}

export function normalizeToolOutput(output = {}) {
  const nextOutput = createToolOutput(output);

  return {
    ...nextOutput,
    variableKey: nextOutput.variableKey.trim() || "result",
    variableLabel:
      (nextOutput.variableLabel || nextOutput.variableKey).trim() ||
      nextOutput.variableKey,
    valueType: nextOutput.valueType.trim() || "string",
    path:
      (nextOutput.path || nextOutput.variableKey).trim() ||
      nextOutput.variableKey,
  };
}

export function buildDefaultToolOutputs() {
  return [
    normalizeToolOutput({
      variableKey: "result",
      variableLabel: "result",
      valueType: "string",
      path: "result",
    }),
  ];
}

export function normalizeToolNodeData(data = {}) {
  const structuredOutput = getNodeOutput(data);
  const hasExplicitOutput =
    structuredOutput.length > 0 ||
    Array.isArray(data.output) ||
    Array.isArray(data.outputs) ||
    Array.isArray(data.outputVariables);
  const rawOutputs = structuredOutput.length
    ? structuredOutput
    : Array.isArray(data.output)
    ? data.output
    : Array.isArray(data.outputs)
    ? data.outputs
    : Array.isArray(data.outputVariables)
    ? data.outputVariables
    : [];
  const config = {
    ...omitObjectKeys(data, [
      "input",
      "config",
      "output",
      "outputs",
      "outputVariables",
      "label",
      "description",
      "toolId",
      "toolName",
      "toolDescription",
      "toolSource",
      "toolIcon",
    ]),
    ...getNodeConfig(data),
    label:
      getNodeLabel(data, getNodeConfigValue(data, "toolName", "工具")) ||
      "工具",
    description:
      getNodeDescription(data) ||
      `${getNodeConfigValue(data, "toolDescription", "") || ""}`.trim(),
    toolId: `${getNodeConfigValue(data, "toolId", "") || ""}`.trim(),
    toolName:
      `${
        getNodeConfigValue(data, "toolName", getNodeLabel(data, "工具")) || ""
      }`.trim() || "工具",
    toolDescription: `${
      getNodeConfigValue(data, "toolDescription", "") || ""
    }`.trim(),
    toolSource: `${getNodeConfigValue(data, "toolSource", "") || ""}`.trim(),
    toolIcon: `${getNodeConfigValue(data, "toolIcon", "") || ""}`.trim(),
  };

  return createStructuredNodeData({
    input: getNodeInput(data),
    config,
    output: hasExplicitOutput
      ? rawOutputs.filter(Boolean).map((item) => normalizeToolOutput(item))
      : buildDefaultToolOutputs(),
  });
}

export function getToolOutputs(data = {}) {
  return getNodeOutput(normalizeToolNodeData(data));
}

export function getToolContextVariables(data = {}) {
  return getToolOutputs(data)
    .map((output) => {
      const variableKey = `${output.variableKey || output.path || ""}`.trim();

      if (!variableKey) {
        return null;
      }

      return {
        variableKey,
        variableLabel: `${
          output.variableLabel || output.variableKey || variableKey
        }`.trim(),
        valueType: `${output.valueType || ""}`.trim() || "string",
        path: `${output.path || variableKey}`.trim() || variableKey,
      };
    })
    .filter(Boolean);
}

export function getToolNodeLabel(data = {}, fallback = "工具") {
  return (
    `${
      getNodeLabel(data, getNodeConfigValue(data, "toolName", fallback)) || ""
    }`.trim() || fallback
  );
}

export function getToolNodeSubtitle(data = {}) {
  const toolSource = `${
    getNodeConfigValue(data, "toolSource", "") || ""
  }`.trim();
  return toolSource;
}

export function getToolNodePreviewList(data = {}, limit = null) {
  const previewList = getToolOutputs(data)
    .map((output) => {
      const variableLabel = `${
        output.variableLabel || output.variableKey || output.path || ""
      }`.trim();

      return variableLabel;
    })
    .filter(Boolean);

  if (Number.isFinite(limit)) {
    return previewList.slice(0, Math.max(0, Math.floor(limit)));
  }

  return previewList;
}

export function getToolNodePreviewOverflowCount(data = {}, limit = 3) {
  return Math.max(0, getToolNodePreviewList(data).length - limit);
}

// ==========================================
// 8. LLM 模型节点相关函数
// ==========================================
export function normalizeLlmMessageRole(role = "user") {
  const normalizedRole = String(role || "").toLowerCase();
  return normalizedRole === "assistant" ? "assistant" : "user";
}

export function createLlmMessage(overrides = {}) {
  return {
    id: overrides.id || buildLlmMessageId(),
    role: normalizeLlmMessageRole(overrides.role),
    content: overrides.content || "",
  };
}

export function normalizeLlmOutputItem(output = {}) {
  return {
    variableKey:
      `${output.variableKey || output.path || "text"}`.trim() || "text",
    variableLabel:
      `${
        output.variableLabel || output.variableKey || output.path || "text"
      }`.trim() || "text",
    valueType: `${output.valueType || ""}`.trim(),
    path: `${output.path || output.variableKey || "text"}`.trim() || "text",
  };
}

export function buildLlmOutputItems(config = {}) {
  const outputs = [
    {
      variableKey: "text",
      variableLabel: "text",
      valueType: "string",
      path: "text",
    },
  ];

  if (config.reasoningTagEnabled) {
    outputs.push({
      variableKey: "reasoning_content",
      variableLabel: "reasoning_content",
      valueType: "string",
      path: "reasoning_content",
    });
  }

  if (
    Boolean(config.structuredOutputEnabled) ||
    config.responseFormat === "json_object"
  ) {
    outputs.push({
      variableKey: "json",
      variableLabel: "json",
      valueType: "object",
      path: "json",
    });
  }

  return outputs.map((item) => normalizeLlmOutputItem(item));
}

export function normalizeLlmNodeData(data = {}, defaultModelConfig = { provider: "", model: "" }) {
  const structuredInput = getNodeInput(data);
  const rawMessages = structuredInput.length
    ? structuredInput
    : Array.isArray(data.messages)
    ? data.messages
    : [];
  const normalizeNumber = (value, fallback) => {
    const nextValue = Number(value);
    return Number.isFinite(nextValue) ? nextValue : fallback;
  };
  const configuredModel = `${
    getNodeConfigValue(data, "model", "") || ""
  }`.trim();
  const configuredProvider = normalizeModelProviderValue(
    getNodeConfigValue(data, "provider", "")
  );
  const config = {
    ...omitObjectKeys(data, [
      "input",
      "config",
      "output",
      "messages",
      "contextVariables",
      "label",
      "description",
      "provider",
      "model",
      "prompt",
      "temperature",
      "maxTokens",
      "topP",
      "logprobs",
      "topLogprobs",
      "frequencyPenalty",
      "responseFormat",
      "stopSequences",
      "memoryEnabled",
      "visionEnabled",
      "reasoningTagEnabled",
      "structuredOutputEnabled",
      "retryEnabled",
      "errorStrategy",
    ]),
    ...getNodeConfig(data),
    label: getNodeLabel(data, "LLM"),
    description: getNodeDescription(data),
    provider: configuredModel
      ? configuredProvider
      : normalizeModelProviderValue(defaultModelConfig.provider),
    model: configuredModel || defaultModelConfig.model || "",
    prompt: getNodeConfigValue(data, "prompt", ""),
    temperature: normalizeNumber(getNodeConfigValue(data, "temperature", 1), 1),
    maxTokens: Math.max(
      1,
      Math.round(
        normalizeNumber(getNodeConfigValue(data, "maxTokens", 4096), 4096)
      )
    ),
    topP: normalizeNumber(getNodeConfigValue(data, "topP", 1), 1),
    logprobs: Boolean(getNodeConfigValue(data, "logprobs", false)),
    topLogprobs: Math.max(
      0,
      Math.round(normalizeNumber(getNodeConfigValue(data, "topLogprobs", 0), 0))
    ),
    frequencyPenalty: normalizeNumber(
      getNodeConfigValue(data, "frequencyPenalty", 0),
      0
    ),
    responseFormat:
      typeof getNodeConfigValue(data, "responseFormat", "") === "string"
        ? getNodeConfigValue(data, "responseFormat", "")
        : "",
    stopSequences:
      typeof getNodeConfigValue(data, "stopSequences", "") === "string"
        ? getNodeConfigValue(data, "stopSequences", "")
        : "",
    memoryEnabled: Boolean(getNodeConfigValue(data, "memoryEnabled", false)),
    visionEnabled: Boolean(getNodeConfigValue(data, "visionEnabled", false)),
    reasoningTagEnabled: Boolean(
      getNodeConfigValue(data, "reasoningTagEnabled", false)
    ),
    structuredOutputEnabled: Boolean(
      getNodeConfigValue(data, "structuredOutputEnabled", false)
    ),
    retryEnabled: Boolean(getNodeConfigValue(data, "retryEnabled", false)),
    errorStrategy: getNodeConfigValue(data, "errorStrategy", "none") || "none",
  };
  const structuredOutput = getNodeOutput(data);

  return createStructuredNodeData({
    input: rawMessages
      .filter(Boolean)
      .map((message) => createLlmMessage(message)),
    config,
    output: structuredOutput.length
      ? structuredOutput
          .filter(Boolean)
          .map((item) => normalizeLlmOutputItem(item))
      : buildLlmOutputItems(config),
  });
}

export function getLlmOutputs(data = {}, defaultModelConfig = { provider: "", model: "" }) {
  return getNodeOutput(normalizeLlmNodeData(data, defaultModelConfig));
}

// ==========================================
// 9. Loop 循环子图与通用节点处理函数
// ==========================================
export function createLoopFlowEdge(connection) {
  return {
    ...connection,
    id:
      connection.id ||
      createEdgeId(
        connection.source,
        connection.target,
        connection.sourceHandle,
        connection.targetHandle
      ),
    type: "default",
  };
}

export function createLoopEntryNode(overrides = {}) {
  return {
    id: "loop-entry",
    type: "loop-start",
    position: overrides.position || { x: 24, y: 46 },
    draggable: false,
    selectable: false,
    ...overrides,
    data: createStructuredNodeData({
      config: {
        label: getNodeLabel(overrides.data, "开始"),
      },
    }),
  };
}

export function createLoopFlowNodeData(
  nodeType = "llm",
  index = 0,
  overrides = {},
  defaultModelConfig = { provider: "", model: "" }
) {
  if (nodeType === "condition") {
    return normalizeConditionData({
      label: overrides.label || `条件分支 ${index + 1}`,
      ...overrides,
    });
  }

  if (nodeType === "reply") {
    return normalizeReplyNodeData({
      label: overrides.label || `回复 ${index + 1}`,
      output: [],
      ...overrides,
    });
  }

  if (nodeType === "loop") {
    return normalizeLoopData({
      label: overrides.label || `循环 ${index + 1}`,
      description: getNodeDescription(overrides),
      ...overrides,
    });
  }

  return normalizeLlmNodeData(
    {
      label: overrides.label || `LLM ${index + 1}`,
      ...defaultModelConfig,
      description: getNodeDescription(overrides),
      prompt: "",
      ...overrides,
    },
    defaultModelConfig
  );
}

export function createLoopFlowNode(
  nodeType = "llm",
  index = 0,
  overrides = {},
  defaultModelConfig = { provider: "", model: "" }
) {
  return {
    id: overrides.id || buildLoopStepId(nodeType),
    type: nodeType,
    position: overrides.position || { x: 124 + index * 188, y: 42 },
    data: createLoopFlowNodeData(nodeType, index, overrides.data || {}, defaultModelConfig),
    ...overrides,
  };
}

export function normalizeLoopFlowNode(
  node,
  index = 0,
  defaultModelConfig = { provider: "", model: "" }
) {
  if (!node || node.type === "loop-start" || node.id === "loop-entry") {
    return createLoopEntryNode(node || {});
  }

  const nextType = node.type || "llm";
  const nextNode = {
    id: node.id || buildLoopStepId(nextType),
    type: nextType,
    position: node.position || { x: 124 + index * 188, y: 42 },
    data: cloneNodeData(node.data || {}),
  };

  if (nextType === "condition") {
    nextNode.data = normalizeConditionData(nextNode.data);
    nextNode.data = setNodeConfigValue(
      nextNode.data,
      "label",
      getNodeLabel(nextNode.data, `条件分支 ${index + 1}`)
    );
    return nextNode;
  }

  if (nextType === "loop") {
    nextNode.data = normalizeLoopData(nextNode.data);
    nextNode.data = setNodeConfigValue(
      nextNode.data,
      "label",
      getNodeLabel(nextNode.data, `循环 ${index + 1}`)
    );
    return nextNode;
  }

  if (nextType === "reply") {
    nextNode.data = normalizeReplyNodeData({
      label: getReplyNodeLabel(nextNode.data, `输出 ${index + 1}`),
      ...nextNode.data,
    });
    return nextNode;
  }

  nextNode.data = normalizeLlmNodeData(
    {
      label: getNodeLabel(nextNode.data, `LLM ${index + 1}`),
      provider: getNodeConfigValue(
        nextNode.data,
        "provider",
        defaultModelConfig.provider
      ),
      model: getNodeConfigValue(nextNode.data, "model", defaultModelConfig.model),
      description: getNodeDescription(nextNode.data),
      prompt: getNodeConfigValue(nextNode.data, "prompt", ""),
      ...nextNode.data,
    },
    defaultModelConfig
  );
  return nextNode;
}

export function buildLoopNodesFromLegacySteps(
  steps = [],
  defaultModelConfig = { provider: "", model: "" }
) {
  return [
    createLoopEntryNode(),
    ...steps.map((step, index) =>
      normalizeLoopFlowNode(
        createLoopFlowNode(step.type || "llm", index, {
          data: createLoopFlowNodeData(step.type || "llm", index, {
            label:
              step.label ||
              `${getNodeTypeLabel(step.type || "llm")} ${index + 1}`,
          }, defaultModelConfig),
        }, defaultModelConfig),
        index,
        defaultModelConfig
      )
    ),
  ];
}

export function buildLoopEdgesFromNodes(loopNodes = []) {
  const sequence = loopNodes.filter(Boolean);
  const nextEdges = [];

  for (let index = 0; index < sequence.length - 1; index += 1) {
    const currentNode = sequence[index];
    const targetNode = sequence[index + 1];
    const connection = {
      source: currentNode.id,
      target: targetNode.id,
    };

    if (currentNode.type === "condition") {
      connection.sourceHandle = getConditionDefaultSourceHandle(
        currentNode.data
      );
    }

    nextEdges.push(createLoopFlowEdge(connection));
  }

  return nextEdges;
}

export function normalizeLoopData(data = {}, defaultModelConfig = { provider: "", model: "" }) {
  const legacySteps = Array.isArray(data?.steps)
    ? data.steps
    : Array.isArray(getNodeConfigValue(data, "steps", null))
    ? getNodeConfigValue(data, "steps", [])
    : [];
  const rawLoopNodes = Array.isArray(
    getNodeConfigValue(data, "loopNodes", null)
  )
    ? getNodeConfigValue(data, "loopNodes", [])
    : Array.isArray(data.loopNodes)
    ? data.loopNodes
    : [];
  const rawLoopEdges = Array.isArray(
    getNodeConfigValue(data, "loopEdges", null)
  )
    ? getNodeConfigValue(data, "loopEdges", [])
    : Array.isArray(data.loopEdges)
    ? data.loopEdges
    : [];
  let nextLoopNodes = Array.isArray(rawLoopNodes)
    ? cloneNodeData(rawLoopNodes)
    : [];
  let nextLoopEdges = Array.isArray(rawLoopEdges)
    ? cloneNodeData(rawLoopEdges)
    : [];

  if (
    !nextLoopNodes.length &&
    Array.isArray(legacySteps) &&
    legacySteps.length
  ) {
    nextLoopNodes = buildLoopNodesFromLegacySteps(legacySteps, defaultModelConfig);
    nextLoopEdges = buildLoopEdgesFromNodes(nextLoopNodes);
  }

  const entryNode = nextLoopNodes.find(
    (node) => node?.type === "loop-start" || node?.id === "loop-entry"
  );
  const bodyNodes = nextLoopNodes
    .filter((node) => node && node.id !== (entryNode?.id || "loop-entry"))
    .map((node, index) => normalizeLoopFlowNode(node, index, defaultModelConfig));
  const normalizedLoopNodes = [
    normalizeLoopFlowNode(entryNode, 0, defaultModelConfig),
    ...bodyNodes,
  ];
  const loopNodeIds = new Set(normalizedLoopNodes.map((node) => node.id));
  const normalizedLoopEdges = nextLoopEdges
    .filter(
      (edge) =>
        edge && loopNodeIds.has(edge.source) && loopNodeIds.has(edge.target)
    )
    .map((edge) => createLoopFlowEdge(edge));
  const maxIterations = Number(getNodeConfigValue(data, "maxIterations", 10));

  return createStructuredNodeData({
    input: getNodeInput(data),
    config: {
      ...omitObjectKeys(data, [
        "input",
        "config",
        "output",
        "steps",
        "loopNodes",
        "loopEdges",
        "label",
        "description",
        "itemAlias",
        "maxIterations",
      ]),
      ...getNodeConfig(data),
      label: getNodeLabel(data, "循环"),
      description: getNodeDescription(data),
      itemAlias: getNodeConfigValue(data, "itemAlias", "item") || "item",
      maxIterations:
        Number.isFinite(maxIterations) && maxIterations > 0
          ? maxIterations
          : 10,
      loopNodes: normalizedLoopNodes,
      loopEdges: normalizedLoopEdges,
    },
    output: getNodeOutput(data),
  });
}

export function getLoopNodes(data = {}) {
  return getNodeConfigValue(normalizeLoopData(data), "loopNodes", []);
}

export function getLoopEdges(data = {}) {
  return getNodeConfigValue(normalizeLoopData(data), "loopEdges", []);
}

export function getLoopCanvasLayout(compactMode = true) {
  return compactMode
    ? {
        minWidth: 292,
        minHeight: 118,
        paddingTop: 18,
        paddingRight: 26,
        paddingBottom: 18,
        paddingLeft: 18,
      }
    : {
        minWidth: 680,
        minHeight: 300,
        paddingTop: 30,
        paddingRight: 52,
        paddingBottom: 34,
        paddingLeft: 34,
      };
}

export function getLoopCanvasNodeSize(node, compactMode = true) {
  if (node?.type === "loop-start" || node?.id === "loop-entry") {
    return compactMode ? { width: 36, height: 36 } : { width: 150, height: 56 };
  }

  if (node?.type === "condition") {
    const caseCount = Math.max(2, getConditionCases(node?.data).length);
    return {
      width: 260,
      height: 93 + caseCount * 32,
    };
  }

  if (node?.type === "reply") {
    return { width: 200, height: 112 };
  }

  if (node?.type === "loop") {
    const metrics = getLoopCanvasMetrics(node?.data || {}, true);
    return {
      width: metrics.width + 32,
      height: metrics.height + 60,
    };
  }

  return { width: 200, height: 112 };
}

export function getLoopCanvasMetrics(data = {}, compactMode = true) {
  const layout = getLoopCanvasLayout(compactMode);
  const loopNodes = getLoopNodes(data);

  let maxRight = layout.paddingLeft;
  let maxBottom = layout.paddingTop;

  loopNodes.forEach((node) => {
    const size = getLoopCanvasNodeSize(node, compactMode);
    const positionX = Number(node?.position?.x) || 0;
    const positionY = Number(node?.position?.y) || 0;

    maxRight = Math.max(maxRight, positionX + size.width);
    maxBottom = Math.max(maxBottom, positionY + size.height);
  });

  return {
    width: Math.max(layout.minWidth, Math.ceil(maxRight + layout.paddingRight)),
    height: Math.max(
      layout.minHeight,
      Math.ceil(maxBottom + layout.paddingBottom)
    ),
  };
}

export function getLoopNodeStyle(data = {}) {
  const metrics = getLoopCanvasMetrics(data, true);

  return {
    width: `${metrics.width + 32}px`,
    height: `${metrics.height + 60}px`,
  };
}

export function getLoopEditorSurfaceStyle(data = {}) {
  const metrics = getLoopCanvasMetrics(data, true);

  return {
    width: `${metrics.width}px`,
    height: `${metrics.height}px`,
  };
}

export function getDefaultSourceHandleId(node) {
  if (!node) {
    return undefined;
  }

  if (node.type === "condition") {
    return getConditionDefaultSourceHandle(node.data);
  }

  if (node.type === "loop") {
    return LOOP_OUTER_SOURCE_HANDLE_ID;
  }

  return undefined;
}

export function getDefaultTargetHandleId(node) {
  if (!node) {
    return undefined;
  }

  if (node.type === "loop") {
    return LOOP_OUTER_TARGET_HANDLE_ID;
  }

  return undefined;
}

export function normalizeNodeData(
  type,
  data = {},
  isChatflow = false,
  defaultModelConfig = { provider: "", model: "" }
) {
  if (type === "start") {
    return normalizeStartNodeData(data, isChatflow);
  }

  if (type === "llm") {
    return normalizeLlmNodeData(data, defaultModelConfig);
  }

  if (type === TOOL_NODE_TYPE) {
    return normalizeToolNodeData(data);
  }

  if (type === "condition") {
    return normalizeConditionData(data);
  }

  if (type === "reply") {
    return normalizeReplyNodeData(data);
  }

  if (type === "loop") {
    return normalizeLoopData(data, defaultModelConfig);
  }

  return data;
}
