import request from '@/utils/request';

// 获取图谱数据
export function getGraphData() {
  return request({ url: '/kg/graph/data', method: 'get' });
}

// 添加节点
export function addNode(data) {
  return request({ url: '/kg/graph/node', method: 'post', data });
}

// 添加边
export function addEdge(data) {
  return request({ url: '/kg/graph/edge', method: 'post', data });
}

// 执行社区检测
export function detectCommunities(workspaceId = 1001) {
  return request({ url: '/kg/graph/communities/detect', method: 'post', params: { workspaceId } });
}

// 获取已检测的社区列表
export function getCommunities(workspaceId = 1001) {
  return request({ url: '/kg/graph/communities', method: 'get', params: { workspaceId } });
}
