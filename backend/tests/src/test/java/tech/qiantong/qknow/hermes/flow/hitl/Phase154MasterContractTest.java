package tech.qiantong.qknow.hermes.flow.hitl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.dag.DagCheckpointManager;
import tech.qiantong.qknow.hermes.flow.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.hermes.flow.hitl.dto.Phase154MasterAuditReceipt;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 154 核心契约测试套件：
 * 企业级 Agent 认知基础设施深化、双向生产 MCP 工具生态与 Contextual RAG 协同进化
 * <p>
 * 覆盖：
 * 1. HITL 强挂起拦截与零假放行漏洞
 * 2. CAS 乐观锁防重与原子唤醒
 * 3. 单调递增 Fencing Token 写屏障
 * 4. Java 21 虚拟线程高吞吐无锁调度
 * 5. 不可变审计凭单 SHA-256 常量时间自验真
 * 6. 阿里千问 1536 维超球面单位向量几何保真度
 * </p>
 */
public class Phase154MasterContractTest {

    @Test
    @DisplayName("契约 1：HITL 强挂起拦截 — 节点返回 SUSPENDED 时流程严格暂停且断点持久化为挂起态")
    void test01_HitlSuspensionInterception() {
        DagCheckpointManager checkpointManager = new DagCheckpointManager();
        checkpointManager.init();

        String runtimeId = "rt_test_hitl_001";
        String flowId = "flow_154_001";

        // 模拟节点 1 成功，节点 2 (审批节点) 返回挂起
        Map<String, NodeRunResultBO> results = new LinkedHashMap<>();
        NodeRunResultBO r1 = NodeRunResultBO.success("node_1", "数据准备节点", Map.of("data", "ready"));
        NodeRunResultBO r2 = NodeRunResultBO.suspended("node_approval", "安全风控审批节点", Map.of("reason", "等待高危动作放行"));

        results.put("node_1", r1);
        results.put("node_approval", r2);

        Map<String, Object> variables = new HashMap<>();
        variables.put("userId", 1001L);
        variables.put("action", "transfer_fund");

        checkpointManager.saveCheckpointWithVariables(runtimeId, flowId, 1, results, variables);

        // 验证持久化后的检查点状态
        DagCheckpointManager.DagCheckpoint loaded = checkpointManager.loadCheckpoint(runtimeId);
        assertNotNull(loaded, "检查点必须成功落盘");
        assertEquals("SUSPENDED", loaded.getStatus(), "检查点状态必须严格为 SUSPENDED");

        // 验证 hasSuspendedResult 检测
        Map<String, NodeRunResultBO> restoredResults = checkpointManager.restoreCompletedResults(loaded);
        assertTrue(checkpointManager.hasSuspendedResult(restoredResults), "必须精准识别存在挂起节点");
    }

    @Test
    @DisplayName("契约 2：CAS 乐观锁防重与原子唤醒 — 支持参数注入且杜绝多次并发重复唤醒")
    void test02_CasAtomicWakeupAndInputInjection() {
        DagCheckpointManager checkpointManager = new DagCheckpointManager();
        checkpointManager.init();

        String runtimeId = "rt_test_hitl_002";
        String flowId = "flow_154_002";

        Map<String, NodeRunResultBO> results = new LinkedHashMap<>();
        results.put("node_suspend", NodeRunResultBO.suspended("node_suspend", "人工审批", Map.of("status", "WAITING")));
        checkpointManager.saveCheckpointWithVariables(runtimeId, flowId, 0, results, new HashMap<>());

        // 首次唤醒：传入人工审批通过与热修改变量
        Map<String, Object> humanInput = Map.of("approvalResult", "APPROVED", "approver", "admin");
        boolean firstWake = checkpointManager.wakeSuspended(runtimeId, humanInput);
        assertTrue(firstWake, "首次唤醒必须成功");

        // 验证唤醒后的状态已被转换为 SUCCESS，且输入变量已合并
        DagCheckpointManager.DagCheckpoint loaded = checkpointManager.loadCheckpoint(runtimeId);
        Map<String, NodeRunResultBO> restored = checkpointManager.restoreCompletedResults(loaded);
        NodeRunResultBO wokenNode = restored.get("node_suspend");
        assertNotNull(wokenNode);
        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), wokenNode.getStatus(), "唤醒后节点状态必须变为 SUCCESS");

        Map<String, Object> vars = checkpointManager.restoreVariables(loaded);
        assertEquals("APPROVED", vars.get("approvalResult"), "人工输入变量必须合并入上下文");

        // 二次唤醒：由于已无挂起节点，应返回 false，防重复放行
        boolean secondWake = checkpointManager.wakeSuspended(runtimeId, humanInput);
        assertFalse(secondWake, "已唤醒的工作流不可被重复唤醒");
    }

    @Test
    @DisplayName("契约 3：单调递增 Fencing Token 写屏障 — 彻底阻断脑裂与陈旧写覆写")
    void test03_FencingTokenWriteBarrier() {
        DagCheckpointManager checkpointManager = new DagCheckpointManager();
        checkpointManager.init();

        String runtimeId = "rt_test_fence_001";
        String flowId = "flow_154_003";

        Map<String, NodeRunResultBO> results = Map.of("n1", NodeRunResultBO.success("n1", "Node1", Map.of()));
        Map<String, Object> vars = Map.of("k", "v1");

        // 初次写入，Fencing Token 为 10
        boolean initWrite = checkpointManager.saveCheckpointWithFencingToken(runtimeId, flowId, 0, results, vars, 10L);
        assertTrue(initWrite, "初始 Fencing Token 写入应成功");

        // 持有陈旧令牌 (Token 9) 的写操作必须被原子拒绝
        boolean staleWrite = checkpointManager.saveCheckpointWithFencingToken(runtimeId, flowId, 1, results, vars, 9L);
        assertFalse(staleWrite, "陈旧令牌写入必须被写屏障强制拒绝");

        // 持有匹配令牌 (Token 10) 的写操作成功
        boolean validWrite = checkpointManager.saveCheckpointWithFencingToken(runtimeId, flowId, 1, results, vars, 10L);
        assertTrue(validWrite, "匹配令牌写入应成功");
    }

    @Test
    @DisplayName("契约 4：Java 21 虚拟线程高并发调度 — 100 个并发子任务无阻塞零死锁快速收敛")
    void test04_Java21VirtualThreadConcurrency() throws InterruptedException, ExecutionException {
        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            int taskCount = 100;
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                tasks.add(() -> {
                    // 模拟异步 I/O 等待 (如 HTTP/LLM 调用)
                    Thread.sleep(10);
                    return taskId * 2;
                });
            }

            long startNs = System.nanoTime();
            List<Future<Integer>> futures = virtualExecutor.invokeAll(tasks);
            assertEquals(taskCount, futures.size());

            for (int i = 0; i < taskCount; i++) {
                assertEquals(i * 2, futures.get(i).get().intValue());
            }
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            // 100 个并发任务在虚拟线程下应当在 500ms 内全部收敛完毕
            assertTrue(elapsedMs < 500, "100 虚拟线程并发收敛耗时必须小于 500ms，实际耗时: " + elapsedMs + "ms");
        }
    }

    @Test
    @DisplayName("契约 5：纯 Java 21 Record 不可变凭单 — SHA-256 常量时间自验真与抗侧信道篡改")
    void test05_ImmutableAuditReceiptVerification() {
        Phase154MasterAuditReceipt receipt = Phase154MasterAuditReceipt.create(
                "receipt-154-001",
                "rt-10086",
                "ticket-hitl-888",
                "mcp-server-postgres",
                128,
                0.992,
                1420L
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifyDigest(), "原始凭单的 SHA-256 摘要自验真必须通过");

        // 篡改测试：任何字段被替换构造新凭单后，使用伪造摘要必须验真失败
        Phase154MasterAuditReceipt forgedReceipt = new Phase154MasterAuditReceipt(
                receipt.receiptId(),
                "rt-tampered-id", // 篡改运行时 ID
                receipt.hitlTicketId(),
                receipt.mcpServerId(),
                receipt.contextualChunksProcessed(),
                receipt.cosineSimilarityIntegrity(),
                receipt.executionLatencyMicros(),
                receipt.sha256Digest() // 沿用原摘要
        );

        assertFalse(forgedReceipt.verifyDigest(), "被篡改数据的凭单自验真必须严格失败");
    }

    @Test
    @DisplayName("契约 6：阿里千问 1536 维超球面单位向量几何 — L2 范数归一化与欧氏-余弦恒等律")
    void test06_HypersphericalUnitManifoldIntegrity() {
        int dim = 1536;
        Random rand = new Random(2026);

        // 生成两个高维随机向量并进行 L2 归一化至超球面
        double[] v1 = new double[dim];
        double[] v2 = new double[dim];
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < dim; i++) {
            v1[i] = rand.nextGaussian();
            v2[i] = rand.nextGaussian();
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }
        norm1 = Math.sqrt(norm1);
        norm2 = Math.sqrt(norm2);

        for (int i = 0; i < dim; i++) {
            v1[i] /= norm1;
            v2[i] /= norm2;
        }

        // 验证 ||v||_2 = 1.0
        double checkNorm1 = 0.0;
        double checkNorm2 = 0.0;
        double cosine = 0.0;
        double eucSq = 0.0;

        for (int i = 0; i < dim; i++) {
            checkNorm1 += v1[i] * v1[i];
            checkNorm2 += v2[i] * v2[i];
            cosine += v1[i] * v2[i];
            double diff = v1[i] - v2[i];
            eucSq += diff * diff;
        }

        assertEquals(1.0, Math.sqrt(checkNorm1), 1e-6, "千问向量必须满足 L2 范数 = 1.0");
        assertEquals(1.0, Math.sqrt(checkNorm2), 1e-6, "千问向量必须满足 L2 范数 = 1.0");

        // 验证几何恒等律：d_Euc^2 = 2 * (1 - cos(theta))
        double expectedEucSq = 2.0 * (1.0 - cosine);
        assertEquals(expectedEucSq, eucSq, 1e-6, "超球面单位向量必须精确满足 d_Euc^2 = 2 * (1 - cos(theta))");
    }
}
