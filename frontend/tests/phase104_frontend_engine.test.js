import assert from 'node:assert';

// 测试1: VirtualizedDagCanvasEngine 算法逻辑
function isNodeInViewport(node, viewport, padding = 200) {
  const zoom = viewport.zoom > 0 ? viewport.zoom : 1.0;
  const worldMinX = -viewport.x / zoom - padding;
  const worldMinY = -viewport.y / zoom - padding;
  const worldMaxX = (-viewport.x + viewport.width) / zoom + padding;
  const worldMaxY = (-viewport.y + viewport.height) / zoom + padding;

  const nodeMaxX = node.x + node.width;
  const nodeMaxY = node.y + node.height;

  return (
    nodeMaxX >= worldMinX &&
    node.x <= worldMaxX &&
    nodeMaxY >= worldMinY &&
    node.y <= worldMaxY
  );
}

function getRenderLodLevel(zoom) {
  if (zoom >= 0.75) return 'LOD_0_FULL';
  if (zoom >= 0.4) return 'LOD_1_COMPACT';
  return 'LOD_2_CAPSULE';
}

function calculateAdaptiveBezierPath(sourceX, sourceY, targetX, targetY) {
  const deltaX = Math.abs(targetX - sourceX);
  const offset = Math.max(deltaX / 2, 40);
  const cp1 = { x: sourceX + offset, y: sourceY };
  const cp2 = { x: targetX - offset, y: targetY };
  const path = `M ${sourceX.toFixed(1)} ${sourceY.toFixed(1)} C ${cp1.x.toFixed(1)} ${cp1.y.toFixed(1)}, ${cp2.x.toFixed(1)} ${cp2.y.toFixed(1)}, ${targetX.toFixed(1)} ${targetY.toFixed(1)}`;
  return { path, cp1, cp2 };
}

function isHandleMagnetized(cursorX, cursorY, handleX, handleY, radius = 16) {
  const dx = cursorX - handleX;
  const dy = cursorY - handleY;
  return dx * dx + dy * dy <= radius * radius;
}

// 执行测试1: 视口相交判定
const viewport = { x: 0, y: 0, width: 1200, height: 800, zoom: 1.0 };
const visibleNode = { id: 'n1', x: 200, y: 300, width: 180, height: 60 };
const offscreenNode = { id: 'n2', x: 2500, y: 3000, width: 180, height: 60 };
const paddedVisibleNode = { id: 'n3', x: -150, y: 300, width: 100, height: 60 }; // 在 200px 缓冲垫内

assert.strictEqual(isNodeInViewport(visibleNode, viewport), true, '视口内节点应可见');
assert.strictEqual(isNodeInViewport(offscreenNode, viewport), false, '超远视口外节点应被剔除');
assert.strictEqual(isNodeInViewport(paddedVisibleNode, viewport), true, '缓冲垫内节点应判定为可见');
console.log('✔ [Frontend Engine Test] 视口相交裁剪 AABB 测试通过');

// 执行测试2: 三级 LOD 判定
assert.strictEqual(getRenderLodLevel(1.0), 'LOD_0_FULL', '正常缩放应为 LOD_0');
assert.strictEqual(getRenderLodLevel(0.5), 'LOD_1_COMPACT', '中度缩放应为 LOD_1');
assert.strictEqual(getRenderLodLevel(0.2), 'LOD_2_CAPSULE', '极限大图应降级为 LOD_2');
console.log('✔ [Frontend Engine Test] 三级 LOD 平滑降级测试通过');

// 执行测试3: 贝塞尔曲线与磁吸计算
const bezier = calculateAdaptiveBezierPath(100, 100, 300, 200);
assert.ok(bezier.path.startsWith('M 100.0 100.0 C'), '贝塞尔路径格式正确');
assert.strictEqual(isHandleMagnetized(105, 105, 100, 100, 16), true, '5px 偏移在 16px 磁吸范围内');
assert.strictEqual(isHandleMagnetized(120, 120, 100, 100, 16), false, '超出 16px 磁吸范围不应吸附');
console.log('✔ [Frontend Engine Test] 贝塞尔方程与 Handle 磁吸测试通过');

// 测试4: NodeLevelTimeTravelDebugger 逻辑
function deepFreeze(obj) {
  if (obj === null || typeof obj !== 'object' || Object.isFrozen(obj)) return obj;
  Object.freeze(obj);
  Object.getOwnPropertyNames(obj).forEach(prop => {
    const val = obj[prop];
    if (val !== null && (typeof val === 'object' || typeof val === 'function')) {
      deepFreeze(val);
    }
  });
  return obj;
}

const mockState = { user: 'alice', config: { retries: 3, flag: true } };
const frozenState = deepFreeze(mockState);

assert.strictEqual(Object.isFrozen(frozenState), true);
assert.strictEqual(Object.isFrozen(frozenState.config), true);

assert.throws(() => {
  'use strict';
  frozenState.user = 'bob';
}, TypeError, '尝试修改冻结对象必须抛出 TypeError');

console.log('✔ [Frontend Engine Test] deepFreeze 递归不可变深度冻结测试通过');
console.log('🎉 全部前端引擎核心算法单测 100% 成功！');
