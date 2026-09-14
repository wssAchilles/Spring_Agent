package tech.qiantong.qknow.ai.audit.model;

import tech.qiantong.qknow.ai.audit.causal.CausalEdge;
import tech.qiantong.qknow.ai.audit.causal.CausalTraceNode;

import java.util.Collections;
import java.util.List;

/**
 * 全链路神经符号可解释性拓扑导出视图模型 (CausalTopologyExportVO)
 *
 * 承载 8 大核心微阶段拓扑节点与因果连线，支持前端 Vue Flow 直接渲染与反向因果溯源。
 *
 * @param traceId 交互 Trace 唯一标识
 * @param tenantId 多租户隔离租户标识
 * @param nodes 拓扑节点集合 (已按时序/拓扑分层排定)
 * @param edges 因果有向边集合
 * @param totalNodes 节点总数
 * @param totalEdges 连线总数
 * @param maxAttributionWeight 链路上最大因果沙普利贡献权重
 * @param exportTimestamp 导出时间戳 (毫秒)
 *
 * @author qknow
 */
public record CausalTopologyExportVO(
        String traceId,
        String tenantId,
        List<CausalTraceNode> nodes,
        List<CausalEdge> edges,
        int totalNodes,
        int totalEdges,
        double maxAttributionWeight,
        long exportTimestamp
) {
    /**
     * 空结果安全兜底工厂方法
     */
    public static CausalTopologyExportVO empty(String traceId, String tenantId) {
        return new CausalTopologyExportVO(
                traceId != null ? traceId : "",
                tenantId != null ? tenantId : "",
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                0,
                0.0,
                System.currentTimeMillis()
        );
    }
}
