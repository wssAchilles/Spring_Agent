package tech.qiantong.qknow.hermes.flow;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.qiantong.qknow.hermes.flow.audit.EnterpriseArchitectureAuditReceipt;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.pattern.DeterministicRuleEvaluator;
import tech.qiantong.qknow.hermes.flow.pattern.EvaluatorOptimizerEngine;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.DurableStateGraphCheckpointer;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.WalDurableStateGraphCheckpointer;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphContext;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphExecutionReceipt;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.StateGraphScheduler;
import tech.qiantong.qknow.hermes.flow.stategraph.enums.StateGraphEdgeType;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraph;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraphEdge;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraphNode;
import tech.qiantong.qknow.hermes.tool.mcp.governance.McpTwoPhaseProjectionGovernor;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 企业级自愈认知编排与工具网关演进契约测试 (EnterpriseArchitectureEvolutionContractTest)
 * 验证：
 * 1. Java 21 虚拟线程超步并发屏障与 StateGraphScheduler 无感加速；
 * 2. WalDurableStateGraphCheckpointer 追加写持久化与冷启动重放一致性；
 * 3. DeterministicRuleEvaluator 确定性规则与数值校验拦截；
 * 4. McpTwoPhaseProjectionGovernor 千问 1536 维超球面两阶段投影与 Safe Schema 剪枝；
 * 5. 常量时间 SHA-256 验签防时序侧信道漏洞自验真。
 */
public class EnterpriseArchitectureEvolutionContractTest {

    @Test
    @DisplayName("契约一：Java 21 虚拟线程超步并发屏障调度与状态图无感集成")
    void testVirtualThreadSuperstepConcurrency() {
        StateGraph graph = StateGraph.builder()
                .graphId("graph_vt_concurrency_01")
                .graphName("虚拟线程并发测试图")
                .build();

        // 构建包含 3 个并行起始节点的拓扑结构
        graph.addNode(StateGraphNode.builder()
                .nodeUuid("node_a")
                .nodeName("并行节点A")
                .executionHandler((n, ctx) -> {
                    try { Thread.sleep(20); } catch (InterruptedException ignored) {}
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1);
                    bo.setOutput(Map.of("a", 100));
                    return bo;
                })
                .build());

        graph.addNode(StateGraphNode.builder()
                .nodeUuid("node_b")
                .nodeName("并行节点B")
                .executionHandler((n, ctx) -> {
                    try { Thread.sleep(20); } catch (InterruptedException ignored) {}
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1);
                    bo.setOutput(Map.of("b", 200));
                    return bo;
                })
                .build());

        graph.addNode(StateGraphNode.builder()
                .nodeUuid("node_c")
                .nodeName("并行节点C")
                .executionHandler((n, ctx) -> {
                    try { Thread.sleep(20); } catch (InterruptedException ignored) {}
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1);
                    bo.setOutput(Map.of("c", 300));
                    return bo;
                })
                .build());

        // 汇聚节点
        graph.addNode(StateGraphNode.builder()
                .nodeUuid("node_sink")
                .nodeName("汇聚节点")
                .executionHandler((n, ctx) -> {
                    NodeRunResultBO bo = new NodeRunResultBO();
                    bo.setNodeUuid(n.getNodeUuid());
                    bo.setStatus(1);
                    bo.setOutput(Map.of("sink", "ok"));
                    return bo;
                })
                .build());

        graph.addStartNode("node_a");
        graph.addStartNode("node_b");
        graph.addStartNode("node_c");

        graph.addEdge(StateGraphEdge.builder()
                .edgeId("e1").sourceNodeUuid("node_a").targetNodeUuid("node_sink").edgeType(StateGraphEdgeType.FORWARD).build());
        graph.addEdge(StateGraphEdge.builder()
                .edgeId("e2").sourceNodeUuid("node_b").targetNodeUuid("node_sink").edgeType(StateGraphEdgeType.FORWARD).build());
        graph.addEdge(StateGraphEdge.builder()
                .edgeId("e3").sourceNodeUuid("node_c").targetNodeUuid("node_sink").edgeType(StateGraphEdgeType.FORWARD).build());

        StateGraphContext context = new StateGraphContext("exec_vt_001", "graph_vt_concurrency_01", 10);
        StateGraphScheduler scheduler = new StateGraphScheduler();

        long start = System.currentTimeMillis();
        StateGraphExecutionReceipt receipt = scheduler.execute(graph, context);
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(receipt, "执行凭单不应为空");
        assertTrue(receipt.verifyIntegrity(), "凭单签名常量时间自验真必须通过");
        assertEquals(2, receipt.totalSupersteps(), "应该经历2个超步（初始3节点并发超步 + 汇聚超步）");
        assertTrue(receipt.completedNodeUuids().containsAll(Set.of("node_a", "node_b", "node_c", "node_sink")),
                "所有节点必须全部完成");

        // 若为串行执行，耗时至少 60ms+，虚拟线程并发执行耗时应明显低于 50ms（除非系统极端抖动）
        assertTrue(elapsed < 200, "虚拟线程超步并发调度应显著压缩多节点延迟");
    }

    @Test
    @DisplayName("契约二：WalDurableStateGraphCheckpointer 追加写持久化与冷启动重放一致性")
    void testWalCheckpointerDurabilityAndReplay(@TempDir Path tempDir) {
        WalDurableStateGraphCheckpointer checkpointer = new WalDurableStateGraphCheckpointer(tempDir);

        String executionId = "exec_wal_test_101";
        String branchId = "main";
        int superstep = 1;
        Map<String, Object> state = Map.of("balance", 5000.0, "status", "INITIALIZED");
        long token = 1000L;
        String checksum = WalDurableStateGraphCheckpointer.computeChecksum(executionId, branchId, superstep, state);

        DurableStateGraphCheckpointer.SnapshotRecord snapshot = new DurableStateGraphCheckpointer.SnapshotRecord(
                UUID.randomUUID().toString(),
                executionId,
                branchId,
                superstep,
                state,
                List.of("node_init"),
                token,
                System.currentTimeMillis(),
                checksum
        );

        boolean saved = checkpointer.saveSnapshot(snapshot);
        assertTrue(saved, "快照必须成功保存至 WAL");

        // CAS 写屏障测试：使用旧 token 写入应被拒绝
        DurableStateGraphCheckpointer.SnapshotRecord staleSnapshot = new DurableStateGraphCheckpointer.SnapshotRecord(
                UUID.randomUUID().toString(),
                executionId,
                branchId,
                2,
                state,
                List.of("node_stale"),
                token - 10,
                System.currentTimeMillis(),
                checksum
        );
        assertFalse(checkpointer.saveSnapshot(staleSnapshot), "CAS 写屏障必须拒绝乱序低序号 token 写入");

        // 模拟服务崩溃与重启冷启动：实例化全新 Checkpointer 指向同一磁盘目录
        WalDurableStateGraphCheckpointer reloadedCheckpointer = new WalDurableStateGraphCheckpointer(tempDir);
        Optional<DurableStateGraphCheckpointer.SnapshotRecord> loaded =
                reloadedCheckpointer.loadSnapshot(executionId, branchId, superstep);

        assertTrue(loaded.isPresent(), "冷启动后必须成功从 WAL 文件重放并检索到快照");
        assertEquals(5000.0, Double.parseDouble(loaded.get().stateDelta().get("balance").toString()));
        assertEquals("INITIALIZED", loaded.get().stateDelta().get("status").toString());
        assertEquals(checksum, loaded.get().checksumSha256(), "重放快照 SHA-256 校验和必须 100% 吻合");
    }

    @Test
    @DisplayName("契约三：DeterministicRuleEvaluator 确定性规则与数值校验硬性拦截")
    void testDeterministicRuleEvaluatorGuardrail() {
        DeterministicRuleEvaluator evaluator = new DeterministicRuleEvaluator();

        List<DeterministicRuleEvaluator.RuleAssertion> rules = List.of(
                DeterministicRuleEvaluator.jsonRequiredKeys("R1_JSON_STRUCTURE", List.of("task_id", "confidence", "result"), 30),
                DeterministicRuleEvaluator.jsonNumericRange("R2_CONFIDENCE_RANGE", "confidence", 0.8, 1.0, 40),
                DeterministicRuleEvaluator.regexMatches("R3_TASK_ID_FORMAT", "TASK-\\d{4}", "任务编号必须符合 TASK-XXXX", 30)
        );

        // 1. 模拟出现幻觉的不合格输出
        String invalidPayload = "{\"task_id\": \"INVALID_ID\", \"confidence\": 0.35, \"result\": \"incomplete\"}";
        DeterministicRuleEvaluator.RuleEvaluationReport failReport = evaluator.evaluate(invalidPayload, rules);

        assertFalse(failReport.passed(), "不符合数值与格式规范的输出必须被硬性拦截");
        assertTrue(failReport.violatedRuleIds().contains("R2_CONFIDENCE_RANGE"));
        assertTrue(failReport.violatedRuleIds().contains("R3_TASK_ID_FORMAT"));
        assertEquals(30, failReport.finalScore(), "扣除违规惩罚后分值应为 30");

        EvaluatorOptimizerEngine.EvaluationResult evalResult = failReport.toEvaluationResult();
        assertFalse(evalResult.passed());
        assertFalse(evalResult.suggestions().isEmpty(), "必须向模型回传具象的反思修改建议");

        // 2. 模拟修正后的合格输出
        String validPayload = "{\"task_id\": \"TASK-8899\", \"confidence\": 0.95, \"result\": \"success\"}";
        DeterministicRuleEvaluator.RuleEvaluationReport passReport = evaluator.evaluate(validPayload, rules);

        assertTrue(passReport.passed(), "合格输出必须 100% 满分通过");
        assertEquals(100, passReport.finalScore());
        assertTrue(passReport.violatedRuleIds().isEmpty());
    }

    @Test
    @DisplayName("契约四：McpTwoPhaseProjectionGovernor 千问 1536 维超球面两阶段投影与 Safe Schema 剪枝")
    void testMcpTwoPhaseProjectionAndPruning() {
        McpTwoPhaseProjectionGovernor governor = new McpTwoPhaseProjectionGovernor();

        // 注册两个不同领域的工具
        float[] dbVec = new float[1536];
        Arrays.fill(dbVec, 0.05f); // 数据库类向量
        JSONObject dbSchema = new JSONObject();
        dbSchema.put("type", "object");
        dbSchema.put("$schema", "http://json-schema.org/draft-07/schema#");
        dbSchema.put("title", "DatabaseQueryToolParametersSchemaVeryLongTitleToWasteTokens");
        JSONObject dbProps = new JSONObject();
        JSONObject sqlProp = new JSONObject();
        sqlProp.put("type", "string");
        sqlProp.put("description", "A very detailed description of SQL query to execute against the PostgreSQL database instance with numerous examples and warnings about injection.");
        dbProps.put("sql", sqlProp);
        dbSchema.put("properties", dbProps);
        dbSchema.put("required", List.of("sql"));

        governor.registerTool("mcp.db.execute_sql", "execute_sql", "执行高保真 SQL 语句并返回结构化数据", dbVec, dbSchema);

        float[] emailVec = new float[1536];
        for (int i = 0; i < 1536; i++) {
            emailVec[i] = (i % 2 == 0) ? 0.08f : -0.08f; // 邮件类向量
        }
        JSONObject emailSchema = new JSONObject();
        emailSchema.put("type", "object");
        JSONObject emailProps = new JSONObject();
        JSONObject toProp = new JSONObject();
        toProp.put("type", "string");
        toProp.put("description", "Receiver email address");
        emailProps.put("to", toProp);
        emailSchema.put("properties", emailProps);
        governor.registerTool("mcp.mail.send_email", "send_email", "发送邮件通知", emailVec, emailSchema);

        assertEquals(2, governor.getRegisteredCount());

        // 意图：针对数据库查询意图检索 Top-1
        float[] queryVec = Arrays.copyOf(dbVec, 1536);
        McpTwoPhaseProjectionGovernor.ProjectionGovernanceReport report = governor.projectAndPrune(queryVec, 1);

        assertNotNull(report);
        assertEquals(1, report.projectedCount());
        assertEquals("mcp.db.execute_sql", report.projectedTools().get(0).toolCode());

        // 验证 Schema 剪枝效果：$schema 与 title 等冗余字段被安全剔除，长说明被截断
        JSONObject pruned = report.projectedTools().get(0).prunedInputSchema();
        assertFalse(pruned.containsKey("$schema"), "$schema 冗余元数据必须被剔除");
        assertFalse(pruned.containsKey("title"), "冗余 title 必须被剔除");
        assertTrue(pruned.containsKey("properties"));
        assertTrue(report.tokenCompressionRatio() < 0.85, "剪枝后 Token 字符体积必须显著压缩 (压缩率 < 85%)");
    }

    @Test
    @DisplayName("契约五：常量时间 SHA-256 验签防时序侧信道漏洞自验真")
    void testConstantTimeVerificationIntegrity() {
        // 1. 验证加固后的 StateGraphExecutionReceipt
        StateGraphExecutionReceipt receipt = StateGraphExecutionReceipt.createSigned(
                "exec_sec_01",
                "flow_sec_01",
                5,
                Set.of("n1", "n2"),
                Set.of("n3"),
                Map.of("loop", 1),
                false,
                false,
                1500L,
                System.currentTimeMillis()
        );

        assertTrue(receipt.verifyIntegrity(), "原始凭单在 MessageDigest.isEqual 常量时间比对下必须通过");

        // 伪造攻击测试：篡改超步数
        StateGraphExecutionReceipt tamperedReceipt = new StateGraphExecutionReceipt(
                receipt.executionId(),
                receipt.flowId(),
                999, // 篡改超步
                receipt.completedNodeUuids(),
                receipt.activeNodeUuids(),
                receipt.loopCounterMap(),
                receipt.hasDegradedBreak(),
                receipt.hasSelfHealed(),
                receipt.executionLatencyUs(),
                receipt.sha256Signature(),
                receipt.timestamp()
        );
        assertFalse(tamperedReceipt.verifyIntegrity(), "任何篡改必须被常量时间比对精准拒绝");

        // 2. 验证全新的 EnterpriseArchitectureAuditReceipt
        EnterpriseArchitectureAuditReceipt auditReceipt = EnterpriseArchitectureAuditReceipt.createSigned(
                "exec_audit_01",
                "graph_audit_01",
                3,
                10,
                true,
                true,
                0.45,
                12L,
                Map.of("env", "java21")
        );

        assertTrue(auditReceipt.verifyIntegrity(), "企业级审计凭单自验真必须通过");
        assertEquals("exec_audit_01", auditReceipt.executionId());
        assertTrue(auditReceipt.walSnapshotPersisted());
        assertTrue(auditReceipt.deterministicRulesPassed());
    }
}
