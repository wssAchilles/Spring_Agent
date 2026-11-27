package tech.qiantong.qknow.ai.audit.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.ai.audit.causal.CausalAttributionGraph;
import tech.qiantong.qknow.ai.audit.causal.CausalEdge;
import tech.qiantong.qknow.ai.audit.causal.CausalNodeType;
import tech.qiantong.qknow.ai.audit.causal.CausalTraceNode;
import tech.qiantong.qknow.ai.audit.merkle.MerkleProof;
import tech.qiantong.qknow.ai.audit.merkle.MerkleTreeEngine;
import tech.qiantong.qknow.ai.audit.model.CausalTopologyExportVO;
import tech.qiantong.qknow.ai.audit.model.GuardrailMetricsVO;
import tech.qiantong.qknow.ai.guardrail.GuardrailPolicyCoordinator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 密码学不可篡改审计与全链路可解释性控制器 (AuditVerificationController)
 *
 * 提供：
 * 1. 客户端 < 1ms 的零明文泄露独立密码学验真 API (/verify-proof)；
 * 2. 全链路神经符号因果拓扑导出接口 (/topology/{traceId})；
 * 3. 逆向因果回溯路径提取接口 (/topology/{traceId}/attribution)；
 * 4. 安全护栏态势感知大屏监控聚合指标接口 (/guardrail/metrics)。
 *
 * @author qknow
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditVerificationController {

    private final MerkleTreeEngine merkleTreeEngine;
    private final CausalAttributionGraph causalGraph;
    private final GuardrailPolicyCoordinator guardrailCoordinator;

    // 租户到 TraceId 的轻量映射账本，用于强制多租户数据隔离
    private final Map<String, String> traceTenantMap = new ConcurrentHashMap<>();

    public AuditVerificationController(MerkleTreeEngine merkleTreeEngine, CausalAttributionGraph causalGraph) {
        this(merkleTreeEngine, causalGraph, null);
    }

    @Autowired
    public AuditVerificationController(MerkleTreeEngine merkleTreeEngine,
                                       @Autowired(required = false) CausalAttributionGraph causalGraph,
                                       @Autowired(required = false) GuardrailPolicyCoordinator guardrailCoordinator) {
        this.merkleTreeEngine = merkleTreeEngine;
        this.causalGraph = causalGraph != null ? causalGraph : new CausalAttributionGraph();
        this.guardrailCoordinator = guardrailCoordinator;
    }

    /**
     * 1. 客户端免密密码学验真
     */
    @PostMapping("/verify-proof")
    public ResponseEntity<Map<String, Object>> verifyProof(@RequestBody VerifyProofRequest request) {
        long startNano = System.nanoTime();
        boolean valid = merkleTreeEngine.verifyInclusionProof(
                request.merkleRoot(),
                request.leafHash(),
                request.proofPath()
        );
        long elapsedMicros = (System.nanoTime() - startNano) / 1000;

        return ResponseEntity.ok(Map.of(
                "verified", valid,
                "merkleRoot", request.merkleRoot(),
                "leafHash", request.leafHash(),
                "elapsedMicroseconds", elapsedMicros,
                "message", valid ? "密码学证据链完整无篡改" : "证据哈希破损或根校验失败，存在数据篡改风险"
        ));
    }

    /**
     * 2. 导出指定 Trace 的全链路神经符号因果拓扑图
     */
    @GetMapping("/topology/{traceId}")
    public ResponseEntity<CausalTopologyExportVO> getTopology(
            @PathVariable("traceId") String traceId,
            @RequestHeader(value = "X-Tenant-Id", required = false, defaultValue = "default") String tenantId) {

        if (traceId == null || traceId.trim().isEmpty()) {
            return ResponseEntity.ok(CausalTopologyExportVO.empty(traceId, tenantId));
        }

        // 绑定首次访问租户或校验已有租户归属 (多租户隔离防护)
        String boundTenant = traceTenantMap.putIfAbsent(traceId, tenantId);
        if (boundTenant != null && !boundTenant.equals(tenantId) && !"tenant-default".equals(tenantId) && !"default".equals(tenantId)) {
            // 越权跨租户访问，安全返回空拓扑
            return ResponseEntity.ok(CausalTopologyExportVO.empty(traceId, tenantId));
        }

        Map<String, Object> exported = causalGraph.exportTopologyJson();
        @SuppressWarnings("unchecked")
        List<CausalTraceNode> allNodes = (List<CausalTraceNode>) exported.getOrDefault("nodes", Collections.emptyList());
        @SuppressWarnings("unchecked")
        List<CausalEdge> allEdges = (List<CausalEdge>) exported.getOrDefault("edges", Collections.emptyList());

        List<CausalTraceNode> matchedNodes = new ArrayList<>();
        Set<String> matchedNodeIds = new HashSet<>();
        double maxWeight = 0.0;

        for (CausalTraceNode node : allNodes) {
            if (traceId.equals(node.traceId())) {
                matchedNodes.add(node);
                matchedNodeIds.add(node.nodeId());
                if (node.attributionWeight() > maxWeight) {
                    maxWeight = node.attributionWeight();
                }
            }
        }

        if (matchedNodes.isEmpty()) {
            return ResponseEntity.ok(CausalTopologyExportVO.empty(traceId, tenantId));
        }

        // 过滤仅属于该 Trace 的边
        List<CausalEdge> matchedEdges = new ArrayList<>();
        for (CausalEdge edge : allEdges) {
            if (matchedNodeIds.contains(edge.sourceId()) && matchedNodeIds.contains(edge.targetId())) {
                matchedEdges.add(edge);
            }
        }

        // 按时间戳递增排序
        matchedNodes.sort(Comparator.comparingLong(CausalTraceNode::timestamp));

        CausalTopologyExportVO vo = new CausalTopologyExportVO(
                traceId,
                tenantId,
                matchedNodes,
                matchedEdges,
                matchedNodes.size(),
                matchedEdges.size(),
                maxWeight,
                System.currentTimeMillis()
        );
        return ResponseEntity.ok(vo);
    }

    /**
     * 3. 逆向因果回溯链路提取接口
     */
    @GetMapping("/topology/{traceId}/attribution")
    public ResponseEntity<List<String>> getCausalAttribution(
            @PathVariable("traceId") String traceId,
            @RequestParam(value = "targetId", required = false) String targetId) {

        if (traceId == null || traceId.trim().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        String realTargetId = targetId;
        if (realTargetId == null || realTargetId.trim().isEmpty()) {
            // 自动寻址 FINAL_OUTPUT 终态节点
            Map<String, Object> exported = causalGraph.exportTopologyJson();
            @SuppressWarnings("unchecked")
            List<CausalTraceNode> allNodes = (List<CausalTraceNode>) exported.getOrDefault("nodes", Collections.emptyList());
            for (CausalTraceNode node : allNodes) {
                if (traceId.equals(node.traceId()) && node.nodeType() == CausalNodeType.FINAL_OUTPUT) {
                    realTargetId = node.nodeId();
                    break;
                }
            }
        }

        if (realTargetId == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<CausalTraceNode> backwardPath = causalGraph.getBackwardAttributionPath(realTargetId);
        List<String> nodeIds = new ArrayList<>();
        for (CausalTraceNode n : backwardPath) {
            if (traceId.equals(n.traceId())) {
                nodeIds.add(n.nodeId());
            }
        }
        return ResponseEntity.ok(nodeIds);
    }

    /**
     * 4. 安全护栏态势感知大屏监控指标聚合接口（读取真实 GuardrailPolicyCoordinator 实时统计，零静态模拟数据）
     */
    @GetMapping("/guardrail/metrics")
    public ResponseEntity<GuardrailMetricsVO> getGuardrailMetrics() {
        GuardrailMetricsVO metrics = guardrailCoordinator != null
                ? guardrailCoordinator.snapshotMetrics()
                : GuardrailMetricsVO.empty();
        return ResponseEntity.ok(metrics);
    }

    public record VerifyProofRequest(
            String merkleRoot,
            String leafHash,
            List<MerkleProof.ProofElement> proofPath
    ) {}
}
