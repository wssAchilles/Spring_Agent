import {
  getNodeLabel,
  getNodeTypeLabel,
  getNodeConfigValue,
  getStartFields,
  getStartFieldValueTypeLabel,
  getLlmOutputs,
  getReplyContent,
  getReplyContextVariables,
  getToolContextVariables,
  normalizeLoopPath,
  TOOL_NODE_TYPE,
} from "../utils/workflowHelpers.js";

/**
 * 构造上下文源节点唯一标识符
 * @param {string} nodeId 节点 ID
 * @param {Array} graphPath 循环子图路径
 * @returns {string} 唯一标识符
 */
export function buildContextSourceNodeId(nodeId = "", graphPath = []) {
  const normalizedNodeId = `${nodeId || ""}`.trim();

  if (!normalizedNodeId) {
    return "";
  }

  const segments = [...normalizeLoopPath(graphPath), normalizedNodeId].filter(
    Boolean
  );

  return segments.join("::");
}

/**
 * 遍历图结构收集指定目标节点的所有上游节点上下文源
 * @param {string} targetNodeId 目标节点 ID
 * @param {Object} graphContext 图上下文 { nodeList, edgeList, graphPath, workflowData }
 * @returns {Array} 上游上下文源列表
 */
export function collectUpstreamContextSources(targetNodeId = "", graphContext = null) {
  const normalizedTargetNodeId = `${targetNodeId || ""}`.trim();

  if (!normalizedTargetNodeId || !graphContext) {
    return [];
  }

  const nodeList = Array.isArray(graphContext.nodeList)
    ? graphContext.nodeList
    : [];
  const edgeList = Array.isArray(graphContext.edgeList)
    ? graphContext.edgeList
    : [];
  const queue = [normalizedTargetNodeId];
  const visitedSourceIds = new Set([normalizedTargetNodeId]);
  const sources = [];

  while (queue.length) {
    const currentTargetId = queue.shift();
    const incomingEdges = edgeList.filter(
      (edge) =>
        `${edge?.target || ""}`.trim() === currentTargetId &&
        `${edge?.source || ""}`.trim()
    );

    incomingEdges.forEach((edge) => {
      const sourceNodeId = `${edge.source || ""}`.trim();

      if (!sourceNodeId || visitedSourceIds.has(sourceNodeId)) {
        return;
      }

      visitedSourceIds.add(sourceNodeId);

      const sourceNode = nodeList.find((node) => node?.id === sourceNodeId);

      if (!sourceNode) {
        return;
      }

      sources.push({
        node: sourceNode,
        graphPath: graphContext.graphPath,
        workflowData: graphContext.workflowData,
      });
      queue.push(sourceNodeId);
    });
  }

  return sources;
}

/**
 * 合并去重多个上下文源列表
 */
export function mergeContextSources(...sourceLists) {
  const sourceMap = new Map();

  sourceLists
    .flat()
    .filter(Boolean)
    .forEach((source) => {
      const sourceKey = buildContextSourceNodeId(
        source?.node?.id,
        source?.graphPath
      );

      if (!sourceKey || sourceMap.has(sourceKey)) {
        return;
      }

      sourceMap.set(sourceKey, source);
    });

  return [...sourceMap.values()];
}

/**
 * 获取循环节点内部迭代入口变量
 */
export function getLoopEntryContextVariables(workflowData = null) {
  const itemAlias =
    `${
      getNodeConfigValue(workflowData, "itemAlias", "item") || "item"
    }`.trim() || "item";

  return [
    {
      variableKey: itemAlias,
      valueType: "item",
      path: itemAlias,
    },
  ];
}

/**
 * 获取指定上下文源节点的输出变量清单
 */
export function getContextVariablesForSource(source = {}, isChatflow = false) {
  const node = source?.node;

  if (!node) {
    return [];
  }

  if (node.type === "start") {
    return getStartFields(node.data)
      .filter((field) => `${field?.name || ""}`.trim())
      .map((field) => ({
        variableKey: `${field.name || ""}`.trim(),
        variableLabel: `${field.label || field.name || ""}`.trim(),
        valueType: getStartFieldValueTypeLabel(field.type),
        path: `${field.name || ""}`.trim(),
      }));
  }

  if (node.type === "loop-start" || node.id === "loop-entry") {
    return getLoopEntryContextVariables(source.workflowData).map((item) => ({
      ...item,
      variableLabel: `${item.variableLabel || item.variableKey || ""}`.trim(),
    }));
  }

  if (node.type === "llm") {
    return getLlmOutputs(node.data).map((item) => ({
      variableKey: `${item.variableKey || ""}`.trim(),
      variableLabel: `${item.variableLabel || item.variableKey || ""}`.trim(),
      valueType: `${item.valueType || ""}`.trim(),
      path: `${item.path || item.variableKey || ""}`.trim(),
    }));
  }

  if (node.type === TOOL_NODE_TYPE) {
    return getToolContextVariables(node.data);
  }

  if (node.type === "condition") {
    return [
      {
        variableKey: "branch_index",
        variableLabel: "命中分支索引",
        valueType: "number",
        path: "branch_index",
      },
      {
        variableKey: "branch_label",
        variableLabel: "命中分支名称",
        valueType: "string",
        path: "branch_label",
      },
    ];
  }

  if (node.type === "knowledge" || node.type === "knowledge-retrieval") {
    const outputs = Array.isArray(node.data?.output) ? node.data.output : [];
    if (outputs.length) {
      return outputs.map((item) => ({
        variableKey: `${item.variableKey || ""}`.trim(),
        variableLabel: `${item.variableLabel || item.variableKey || ""}`.trim(),
        valueType: `${item.valueType || "string"}`.trim(),
        path: `${item.path || item.variableKey || ""}`.trim(),
      }));
    }
    return [
      {
        variableKey: "text",
        variableLabel: "召回文本",
        valueType: "string",
        path: "text",
      },
      {
        variableKey: "chunks",
        variableLabel: "知识切片列表",
        valueType: "array",
        path: "chunks",
      },
    ];
  }

  if (node.type === "reply") {
    const replyContent = `${getReplyContent(node.data) || ""}`.trim();

    if (isChatflow) {
      return replyContent
        ? [
            {
              variableKey: "text",
              variableLabel: "回复内容",
              valueType: "string",
              path: "text",
            },
          ]
        : [];
    }

    const replyOutputs = getReplyContextVariables(node.data);

    if (replyOutputs.length) {
      return replyOutputs;
    }

    return replyContent
      ? [
          {
            variableKey: "text",
            variableLabel: "回复内容",
            valueType: "string",
            path: "text",
          },
        ]
      : [];
  }

  return [];
}

/**
 * 获取上下文源节点的人类可读标签
 */
export function getContextSourceLabel(source = {}) {
  const node = source?.node;

  if (!node) {
    return "上游节点";
  }

  return getNodeLabel(node?.data, getNodeTypeLabel(node.type || ""));
}

/**
 * 构建可供前端变量选择器展示的变量分组对象
 */
export function buildContextVariableGroup(source = {}, isChatflow = false) {
  const node = source?.node;

  if (!node?.id) {
    return null;
  }

  const normalizedNodeType =
    node.type === "loop-start" || node.id === "loop-entry"
      ? "loop-entry"
      : `${node.type || ""}`.trim();
  const sourceNodeId = buildContextSourceNodeId(node.id, source.graphPath);
  const sourceNodeLabel = getContextSourceLabel(source);
  const variables = getContextVariablesForSource(source, isChatflow)
    .map((item) => {
      const variableKey = `${item.variableKey || ""}`.trim();
      const path = `${item.path || variableKey}`.trim();

      if (!variableKey || !path) {
        return null;
      }

      return {
        sourceNodeId,
        sourceNodeLabel,
        sourceNodeType: normalizedNodeType,
        variableKey,
        variableLabel: `${item.variableLabel || variableKey}`.trim(),
        valueType: `${item.valueType || ""}`.trim(),
        path,
      };
    })
    .filter(Boolean);

  if (!variables.length) {
    return null;
  }

  return {
    sourceNodeId,
    sourceNodeLabel,
    sourceNodeType: normalizedNodeType,
    variables,
  };
}
