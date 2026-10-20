import {
  getConditionCases,
  getReplyNodePreviewList,
  getReplyNodePreviewOverflowCount,
  getToolNodePreviewList,
  getToolNodePreviewOverflowCount,
  getToolNodeSubtitle,
  getStartFields,
  getStartFieldOverflowCount,
  getLoopCanvasMetrics,
  cloneNodeData,
} from "../utils/workflowHelpers.js";

/**
 * 获取节点估算尺寸
 * @param {Object} nodeLike 节点对象
 * @param {Boolean} isChatflow 是否为对话流
 * @returns {{width: number, height: number}} 节点宽高
 */
export function getNodeApproxSize(nodeLike = {}, isChatflow = false) {
  const nodeType = nodeLike?.type || "llm";

  if (nodeType === "loop") {
    const metrics = getLoopCanvasMetrics(nodeLike?.data || {}, true);
    return {
      width: metrics.width + 32,
      height: metrics.height + 60,
    };
  }

  if (nodeType === "condition") {
    const caseCount = Math.max(2, getConditionCases(nodeLike?.data).length);
    return {
      width: 260,
      height: 96 + caseCount * 28,
    };
  }

  if (nodeType === "reply") {
    if (isChatflow) {
      return { width: 220, height: 122 };
    }

    const previewCount = Math.min(
      3,
      getReplyNodePreviewList(nodeLike?.data).length
    );
    const overflowCount = getReplyNodePreviewOverflowCount(nodeLike?.data, 3);

    return {
      width: 220,
      height: Math.max(
        122,
        96 + previewCount * 20 + (overflowCount > 0 ? 20 : 0)
      ),
    };
  }

  if (nodeType === "tool") {
    const previewCount = Math.min(
      3,
      getToolNodePreviewList(nodeLike?.data).length
    );
    const overflowCount = getToolNodePreviewOverflowCount(nodeLike?.data, 3);
    const hasSubtitle = Boolean(getToolNodeSubtitle(nodeLike?.data));

    return {
      width: 220,
      height: Math.max(
        122,
        96 +
          (hasSubtitle ? 26 : 0) +
          previewCount * 20 +
          (overflowCount > 0 ? 20 : 0)
      ),
    };
  }

  if (nodeType === "start") {
    const previewCount = Math.min(3, getStartFields(nodeLike?.data).length);
    const overflowCount = getStartFieldOverflowCount(nodeLike?.data, 3);
    const extraHeight = previewCount * 26 + (overflowCount > 0 ? 22 : 0);

    return {
      width: 220,
      height: 118 + extraHeight + (previewCount === 0 ? 18 : 0),
    };
  }

  return { width: 220, height: 128 };
}

/**
 * 获取节点包围盒
 * @param {Object} nodeLike 节点对象
 * @param {Object} position 节点位置
 * @param {Boolean} isChatflow 是否为对话流
 * @returns {{left: number, top: number, width: number, height: number, right: number, bottom: number}} 包围盒坐标
 */
export function getNodeBox(
  nodeLike = {},
  position = nodeLike?.position || { x: 0, y: 0 },
  isChatflow = false
) {
  const size = getNodeApproxSize(nodeLike, isChatflow);

  return {
    left: Number(position?.x) || 0,
    top: Number(position?.y) || 0,
    width: size.width,
    height: size.height,
    right: (Number(position?.x) || 0) + size.width,
    bottom: (Number(position?.y) || 0) + size.height,
  };
}

/**
 * 获取水平插入间距
 * @param {Object} sourceNode 源节点
 * @returns {number} 间距数值
 */
export function getInsertSpacingX(sourceNode = {}) {
  if (sourceNode?.type === "start") {
    return 96;
  }
  if (sourceNode?.type === "condition") {
    return 84;
  }
  return 72;
}

/**
 * 获取指定源节点特定 Handle 的直接后继目标节点
 * @param {string} sourceNodeId 源节点 ID
 * @param {string|null} sourceHandleId 源 Handle ID
 * @param {Array} nodeList 节点列表
 * @param {Array} edgeList 边列表
 * @returns {Array} 目标节点列表
 */
export function getOutgoingTargetNodes(
  sourceNodeId,
  sourceHandleId = null,
  nodeList = [],
  edgeList = []
) {
  const normalizedHandleId = sourceHandleId || null;

  return edgeList
    .filter(
      (edge) =>
        edge.source === sourceNodeId &&
        (edge.sourceHandle || null) === normalizedHandleId &&
        edge.target
    )
    .map((edge) => nodeList.find((node) => node.id === edge.target))
    .filter(Boolean);
}

/**
 * 计算分支兄弟节点的基准排版位置
 */
export function getSiblingBasePosition(
  sourceNode,
  candidateNode,
  sourceHandleId = null,
  nodeList = [],
  edgeList = [],
  isChatflow = false
) {
  const siblingTargets = getOutgoingTargetNodes(
    sourceNode.id,
    sourceHandleId,
    nodeList,
    edgeList
  );

  if (!siblingTargets.length) {
    return null;
  }

  const anchorNode = [...siblingTargets].sort(
    (left, right) =>
      left.position.x - right.position.x || left.position.y - right.position.y
  )[0];

  const lowestNode = siblingTargets.reduce((currentLowest, node) => {
    if (!currentLowest) {
      return node;
    }
    return getNodeBox(node, node.position, isChatflow).bottom >
      getNodeBox(currentLowest, currentLowest.position, isChatflow).bottom
      ? node
      : currentLowest;
  }, null);

  const siblingGapY = 40;

  return {
    x: anchorNode.position.x,
    y: getNodeBox(lowestNode, lowestNode.position, isChatflow).bottom + siblingGapY,
  };
}

/**
 * 计算顺序后继节点的基准排版位置
 */
export function getSequentialBasePosition(
  sourceNode,
  candidateNode,
  sourceHandleId = null,
  isChatflow = false
) {
  const sourceSize = getNodeApproxSize(sourceNode, isChatflow);
  const candidateSize = getNodeApproxSize(candidateNode, isChatflow);
  const alignTop =
    sourceHandleId || sourceNode.type === "condition"
      ? sourceNode.position.y
      : sourceNode.position.y +
        Math.round((sourceSize.height - candidateSize.height) / 2);

  return {
    x:
      sourceNode.position.x +
      sourceSize.width +
      getInsertSpacingX(sourceNode),
    y: alignTop,
  };
}

/**
 * 计算连线中间插入节点的基准位置
 */
export function getEdgeInsertBasePosition(
  sourceNode,
  targetNode,
  candidateNode,
  isChatflow = false
) {
  const sourceBox = getNodeBox(sourceNode, sourceNode.position, isChatflow);
  const targetBox = getNodeBox(targetNode, targetNode.position, isChatflow);
  const candidateSize = getNodeApproxSize(candidateNode, isChatflow);
  const gapX = getInsertSpacingX(sourceNode);
  const minX = sourceBox.right + gapX;
  const maxX = targetBox.left - candidateSize.width - gapX;
  const centeredX = Math.round(
    (sourceBox.right + targetBox.left - candidateSize.width) / 2
  );
  const sourceCenterY = sourceBox.top + sourceBox.height / 2;
  const targetCenterY = targetBox.top + targetBox.height / 2;

  return {
    x: maxX >= minX ? Math.max(minX, Math.min(centeredX, maxX)) : minX,
    y: Math.round(
      (sourceCenterY + targetCenterY) / 2 - candidateSize.height / 2
    ),
  };
}

/**
 * 判断两个包围盒是否相交重叠
 */
export function isBoxOverlapping(boxA, boxB, gapX = 40, gapY = 32) {
  return (
    boxA.left < boxB.right + gapX &&
    boxA.right + gapX > boxB.left &&
    boxA.top < boxB.bottom + gapY &&
    boxA.bottom + gapY > boxB.top
  );
}

/**
 * 递归收集所有下游节点 ID
 */
export function collectDownstreamNodeIds(edgeList = [], startNodeIds = []) {
  const visited = new Set();
  const queue = Array.isArray(startNodeIds)
    ? startNodeIds.filter(Boolean)
    : [startNodeIds].filter(Boolean);

  while (queue.length) {
    const currentId = queue.shift();
    if (!currentId || visited.has(currentId)) {
      continue;
    }

    visited.add(currentId);
    edgeList.forEach((edge) => {
      if (
        edge.source === currentId &&
        edge.target &&
        !visited.has(edge.target)
      ) {
        queue.push(edge.target);
      }
    });
  }

  return visited;
}

/**
 * 水平平移下游节点
 */
export function shiftNodesHorizontally(
  nodeList = [],
  nodeIds = new Set(),
  deltaX = 0
) {
  if (!deltaX) {
    return cloneNodeData(nodeList);
  }

  return cloneNodeData(nodeList).map((node) =>
    nodeIds.has(node.id)
      ? {
          ...node,
          position: {
            ...node.position,
            x: node.position.x + deltaX,
          },
        }
      : node
  );
}

/**
 * 自动为新插入节点腾出水平物理空间（避让下游节点）
 */
export function makeInsertionRoom(
  nodeList = [],
  edgeList = [],
  candidateNode,
  candidatePosition,
  options = {},
  isChatflow = false
) {
  let nextNodes = cloneNodeData(nodeList);
  const excludedIds = new Set((options.excludeIds || []).filter(Boolean));
  const candidateBox = () => getNodeBox(candidateNode, candidatePosition, isChatflow);

  if (options.targetNodeId) {
    const targetNode = nextNodes.find(
      (node) => node.id === options.targetNodeId
    );
    if (targetNode) {
      const targetBox = getNodeBox(targetNode, targetNode.position, isChatflow);
      const requiredShift = Math.ceil(
        candidateBox().right + 40 - targetBox.left
      );

      if (requiredShift > 0) {
        const downstreamNodeIds = collectDownstreamNodeIds(edgeList, [
          targetNode.id,
        ]);
        downstreamNodeIds.add(targetNode.id);
        nextNodes = shiftNodesHorizontally(
          nextNodes,
          downstreamNodeIds,
          requiredShift
        );
      }
    }
  }

  let attempt = 0;
  while (attempt < 14) {
    const blockingNode = nextNodes.find((node) => {
      if (!node || excludedIds.has(node.id)) {
        return false;
      }

      return isBoxOverlapping(candidateBox(), getNodeBox(node, node.position, isChatflow));
    });

    if (!blockingNode) {
      break;
    }

    const blockingBox = getNodeBox(blockingNode, blockingNode.position, isChatflow);
    const requiredShift = Math.max(
      40,
      Math.ceil(candidateBox().right + 40 - blockingBox.left)
    );
    const downstreamNodeIds = collectDownstreamNodeIds(edgeList, [
      blockingNode.id,
    ]);
    downstreamNodeIds.add(blockingNode.id);
    nextNodes = shiftNodesHorizontally(
      nextNodes,
      downstreamNodeIds,
      requiredShift
    );
    attempt += 1;
  }

  return nextNodes;
}

/**
 * 调整目标坐标以防止重叠
 */
export function adjustPositionToAvoidOverlap(
  position,
  newNodeId,
  nodeType = "llm",
  nodeData = {},
  nodeList = [],
  excludeIds = [],
  isChatflow = false
) {
  const minGapX = 40;
  const minGapY = 32;
  const maxAttempts = 18;
  const excludedIdSet = new Set([newNodeId, ...excludeIds].filter(Boolean));
  const candidateNode = {
    id: newNodeId,
    type: nodeType,
    data: nodeData,
  };
  const candidateSize = getNodeApproxSize(candidateNode, isChatflow);
  let adjustedPosition = {
    x: Number(position?.x) || 0,
    y: Number(position?.y) || 0,
  };
  let attemptCount = 0;

  while (attemptCount < maxAttempts) {
    const candidateBox = {
      left: adjustedPosition.x,
      top: adjustedPosition.y,
      right: adjustedPosition.x + candidateSize.width,
      bottom: adjustedPosition.y + candidateSize.height,
    };

    const overlappingNode = nodeList.find((node) => {
      if (!node || excludedIdSet.has(node.id)) {
        return false;
      }

      return isBoxOverlapping(
        candidateBox,
        getNodeBox(node, node.position, isChatflow),
        minGapX,
        minGapY
      );
    });

    if (!overlappingNode) {
      return adjustedPosition;
    }

    attemptCount += 1;
    adjustedPosition = {
      x:
        attemptCount % 4 === 0
          ? adjustedPosition.x + Math.round(minGapX * 1.5)
          : adjustedPosition.x,
      y: adjustedPosition.y + minGapY,
    };
  }

  return adjustedPosition;
}
