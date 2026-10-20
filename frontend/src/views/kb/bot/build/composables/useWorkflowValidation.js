import {
  getNodeLabel,
  getReplyContent,
  getReplyOutputs,
  getLoopNodes,
} from "../utils/workflowHelpers.js";

/**
 * 遍历图与嵌套循环节点进行配置校验
 */
export function walkValidationNodes(nodeList = [], visitor = () => true) {
  const normalizedNodeList = Array.isArray(nodeList) ? nodeList : [];

  for (const node of normalizedNodeList) {
    if (!node || !node.type) {
      continue;
    }

    if (visitor(node) === false) {
      return false;
    }

    if (node.type !== "loop") {
      continue;
    }

    const loopNodes = getLoopNodes(node.data).filter(
      (loopNode) =>
        loopNode &&
        loopNode.type &&
        loopNode.type !== "loop-start" &&
        loopNode.id !== "loop-entry"
    );

    if (!walkValidationNodes(loopNodes, visitor)) {
      return false;
    }
  }

  return true;
}

/**
 * 校验工作流完整性（包含开始节点、回复节点、必要参数等）
 * @param {Array} nodes 节点数组
 * @param {Array} edges 连线数组
 * @param {Object} options 校验选项 { isChatflow: boolean }
 * @returns {{ valid: boolean, message?: string }}
 */
export function validateWorkflowTopology(nodes = [], edges = [], options = {}) {
  const { isChatflow = false } = options;
  const startNodes = nodes.filter((n) => n.type === "start");
  const replyNodes = nodes.filter((n) => n.type === "reply");

  if (startNodes.length === 0) {
    return { valid: false, message: "流程必须包含开始节点" };
  }

  if (replyNodes.length === 0) {
    return { valid: false, message: "流程必须包含回复节点" };
  }

  const invalidReplyNode = replyNodes.find((node) =>
    isChatflow
      ? !`${getReplyContent(node.data) || ""}`.trim()
      : !getReplyOutputs(node.data).length &&
        !`${getReplyContent(node.data) || ""}`.trim()
  );

  if (invalidReplyNode) {
    return {
      valid: false,
      message: isChatflow
        ? `回复节点“${getNodeLabel(invalidReplyNode.data, "回复")}”请填写回复内容`
        : `输出节点“${getNodeLabel(invalidReplyNode.data, "输出")}”请至少配置一个输出变量`,
    };
  }

  return { valid: true };
}

/**
 * 检查是否有环路（DAG 环路检测，基于 Kahn 拓扑排序算法）
 */
export function checkHasCycles(nodes = [], edges = []) {
  const inDegree = new Map();
  const adj = new Map();

  nodes.forEach((n) => {
    inDegree.set(n.id, 0);
    adj.set(n.id, []);
  });

  edges.forEach((e) => {
    if (adj.has(e.source) && inDegree.has(e.target)) {
      adj.get(e.source).push(e.target);
      inDegree.set(e.target, inDegree.get(e.target) + 1);
    }
  });

  const queue = [];
  inDegree.forEach((deg, id) => {
    if (deg === 0) queue.push(id);
  });

  let visitedCount = 0;
  while (queue.length > 0) {
    const curr = queue.shift();
    visitedCount++;
    (adj.get(curr) || []).forEach((nextId) => {
      inDegree.set(nextId, inDegree.get(nextId) - 1);
      if (inDegree.get(nextId) === 0) {
        queue.push(nextId);
      }
    });
  }

  return visitedCount !== nodes.length;
}
