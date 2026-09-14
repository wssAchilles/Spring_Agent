import request from '@/utils/request';

/**
 * 密码学存证与全链路因果拓扑 API 接口定义
 */

export interface ProofElement {
  hash: string;
  isLeft: boolean;
}

export interface VerifyProofRequest {
  merkleRoot: string;
  leafHash: string;
  proofPath: ProofElement[];
}

export interface CausalTraceNodeVO {
  nodeId: string;
  traceId: string;
  nodeType: string;
  timestamp: number;
  inputHash: string;
  outputHash: string;
  attributionWeight: number;
  securityAuditStatus: string;
  description: string;
}

export interface CausalEdgeVO {
  sourceId: string;
  targetId: string;
  relationType: string;
  weight: number;
}

export interface CausalTopologyExportVO {
  traceId: string;
  tenantId: string;
  nodes: CausalTraceNodeVO[];
  edges: CausalEdgeVO[];
  totalNodes: number;
  totalEdges: number;
  maxAttributionWeight: number;
  exportTimestamp: number;
}

export interface HourlyRiskPointVO {
  hourLabel: string;
  interceptionCount: number;
  averageFaithfulness: number;
}

export interface GuardrailMetricsVO {
  totalRequests: number;
  sanitizedCount: number;
  blockedCount: number;
  redactionRate: number;
  averageFaithfulness: number;
  piiTypeDistribution: Record<string, number>;
  hourlyRiskEvents: HourlyRiskPointVO[];
  timestamp: number;
}

/**
 * 1. 导出指定 Trace 的全链路因果拓扑图
 */
export function getTopology(traceId: string) {
  return request({
    url: `/api/v1/audit/topology/${traceId}`,
    method: 'get'
  });
}

/**
 * 2. 导出指定 Trace 的逆向因果回溯节点 ID 列表
 */
export function getCausalAttribution(traceId: string, targetId?: string) {
  return request({
    url: `/api/v1/audit/topology/${traceId}/attribution`,
    method: 'get',
    params: { targetId }
  });
}

/**
 * 3. 导出安全护栏态势感知大屏监控指标
 */
export function getGuardrailMetrics() {
  return request({
    url: '/api/v1/audit/guardrail/metrics',
    method: 'get'
  });
}

/**
 * 4. 服务端辅助密码学验真 API
 */
export function verifyProofServer(data: VerifyProofRequest) {
  return request({
    url: '/api/v1/audit/verify-proof',
    method: 'post',
    data
  });
}
