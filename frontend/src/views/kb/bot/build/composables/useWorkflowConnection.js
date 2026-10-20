import {
  getDefaultSourceHandleId,
  getDefaultTargetHandleId,
} from "../utils/workflowHelpers.js";

let edgeSeed = 0;

/**
 * 生成唯一的边 ID
 */
export function createEdgeId(source, target, sourceHandle, targetHandle) {
  edgeSeed += 1;
  return [
    "e",
    source,
    sourceHandle || "source",
    target,
    targetHandle || "target",
    Date.now(),
    edgeSeed,
  ].join("-");
}

/**
 * 包装为默认样式的边对象
 */
export function createDefaultEdge(connection) {
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

/**
 * 规范化连线句柄
 */
export function normalizeConnection(connection = {}, nodeList = []) {
  if (!connection.source || !connection.target) {
    return connection;
  }

  const sourceNode = nodeList.find((node) => node.id === connection.source);
  const targetNode = nodeList.find((node) => node.id === connection.target);

  return {
    ...connection,
    sourceHandle:
      connection.sourceHandle ||
      getDefaultSourceHandleId(sourceNode) ||
      undefined,
    targetHandle:
      connection.targetHandle ||
      getDefaultTargetHandleId(targetNode) ||
      undefined,
  };
}

/**
 * 校验是否为重复连线
 */
export function isDuplicatedEdge(connection, edgeList = []) {
  return edgeList.some(
    (edge) =>
      edge.source === connection.source &&
      edge.target === connection.target &&
      (edge.sourceHandle || null) === (connection.sourceHandle || null) &&
      (edge.targetHandle || null) === (connection.targetHandle || null)
  );
}

/**
 * 校验连线合法性（防止自环、条件分支检查等）
 */
export function validateConnection(connection, nodeList = []) {
  if (!connection.source || !connection.target) {
    return { valid: false, message: "连线源或目标缺失" };
  }

  if (connection.source === connection.target) {
    return { valid: false, message: "禁止节点自环连接" };
  }

  const sourceNode = nodeList.find((node) => node.id === connection.source);
  if (sourceNode?.type === "condition" && !connection.sourceHandle) {
    return { valid: false, message: "条件分支需要从具体的分支出口发起连接" };
  }

  return { valid: true };
}

/**
 * 获取视口坐标
 */
export function getPointerPositionInFlow(event, vueFlowInstance) {
  if (!vueFlowInstance || !event) {
    return { x: 0, y: 0 };
  }
  return vueFlowInstance.project({
    x: event.clientX,
    y: event.clientY,
  });
}
