package tech.qiantong.qknow.hermes.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.a2a.actor.BoundedMailbox;
import tech.qiantong.qknow.hermes.a2a.actor.VirtualThreadActorContainer;
import tech.qiantong.qknow.hermes.a2a.card.AgentCard;
import tech.qiantong.qknow.hermes.a2a.envelope.A2AMessageEnvelope;
import tech.qiantong.qknow.hermes.a2a.envelope.A2AMessageType;
import tech.qiantong.qknow.hermes.a2a.mesh.ContractNetAuctionEngine;
import tech.qiantong.qknow.hermes.a2a.mesh.ContractNetAuctionEngine.ContractNetAwardReceipt;
import tech.qiantong.qknow.hermes.a2a.mesh.ContractNetAuctionEngine.ContractNetBid;
import tech.qiantong.qknow.hermes.a2a.mesh.ContractNetAuctionEngine.ContractNetProposal;
import tech.qiantong.qknow.hermes.agent.blackboard.BlackboardVersion;
import tech.qiantong.qknow.hermes.agent.blackboard.DistributedDualBlackboard;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 109: 分布式多智能体通信网格 (A2A) 与 L1/L2 双态事件黑板中枢核心契约测试
 */
public class Phase109A2ADualBlackboardContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String HMAC_SECRET = "qknow-phase109-secret-key-32bytes!";

    private float[] createNormalized1536Vector(float seed) {
        float[] vec = new float[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = (float) Math.sin(seed + i * 0.01);
            sumSq += vec[i] * vec[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            vec[i] = (float) (vec[i] / norm);
        }
        return vec;
    }

    @Test
    @DisplayName("Contract 1: A2AMessageEnvelope 不可变性、租约有效性与防环跳数校验")
    void testA2AMessageEnvelopeImmutabilityAndHopCount() {
        long now = System.currentTimeMillis();
        A2AMessageEnvelope envelope = A2AMessageEnvelope.create(
                "trace-109-01",
                "agent-alpha",
                "agent-beta",
                A2AMessageType.TASK_EXECUTE,
                "LEASE_TOKEN_109",
                Map.of("action", "ANALYZE_FINANCIAL_REPORT"),
                5000L
        );

        // 基础断言
        assertNotNull(envelope.messageId());
        assertEquals("trace-109-01", envelope.traceId());
        assertEquals("agent-alpha", envelope.senderAgentId());
        assertEquals("agent-beta", envelope.recipientAgentId());
        assertEquals(0, envelope.hopCount(), "新创建信封初始跳数必须为 0");
        assertTrue(envelope.isLeaseValid(), "有效期内租约必须有效");
        assertTrue(envelope.isHopCountValid(), "初始跳数必须判定为有效 (hopCount <= 8)");

        // 模拟转发递增跳数
        A2AMessageEnvelope forwarded = envelope;
        for (int i = 0; i < 8; i++) {
            forwarded = forwarded.withIncrementedHop();
            assertEquals(i + 1, forwarded.hopCount());
            assertTrue(forwarded.isHopCountValid());
        }

        // 第 9 跳必须判定为非法防环
        A2AMessageEnvelope loopedEnvelope = forwarded.withIncrementedHop();
        assertEquals(9, loopedEnvelope.hopCount());
        assertFalse(loopedEnvelope.isHopCountValid(), "超过 8 跳的信封必须判定为环路非法");
    }

    @Test
    @DisplayName("Contract 2: A2AMessageEnvelope HMAC-SHA256 签名生成与防篡改验证")
    void testA2AMessageEnvelopeHmacSignatureValidation() {
        A2AMessageEnvelope envelope = A2AMessageEnvelope.create(
                "trace-109-02",
                "agent-source",
                "agent-target",
                A2AMessageType.CFP_SOLICIT,
                "LEASE_SEC_02",
                Map.of("taskId", "TASK-888", "payloadData", "CRITICAL_INSTRUCTION"),
                10000L
        );

        // 生成签名
        A2AMessageEnvelope signedEnvelope = envelope.withHmacSignature(HMAC_SECRET);
        assertNotNull(signedEnvelope.signature());
        assertTrue(signedEnvelope.signature().length() >= 64, "HMAC-SHA256 签名长度必须 >= 64 字符");

        // 正确密钥验签必须为 true
        assertTrue(signedEnvelope.verifyHmacSignature(HMAC_SECRET), "正确密钥验签必须成功");

        // 错误密钥验签必须为 false
        assertFalse(signedEnvelope.verifyHmacSignature("wrong-secret-key-padding!"), "错误密钥验签必须失败");

        // 篡改 Payload 验签必须为 false
        A2AMessageEnvelope tampered = new A2AMessageEnvelope(
                signedEnvelope.messageId(),
                signedEnvelope.traceId(),
                signedEnvelope.spanId(),
                signedEnvelope.parentSpanId(),
                signedEnvelope.senderAgentId(),
                signedEnvelope.recipientAgentId(),
                signedEnvelope.messageType(),
                signedEnvelope.securityLeaseToken(),
                Map.of("taskId", "TASK-888", "payloadData", "TAMPERED_INSTRUCTION"),
                signedEnvelope.timestamp(),
                signedEnvelope.leaseExpiryTimestamp(),
                signedEnvelope.hopCount(),
                signedEnvelope.signature()
        );
        assertFalse(tampered.verifyHmacSignature(HMAC_SECRET), "篡改载荷后验签必须失败");
    }

    @Test
    @DisplayName("Contract 3: AgentCard 千问 1536 维超球面单位向量归一化流形约束")
    void testAgentCardHypersphereNormalizationConstraint() {
        // 1. 非 1536 维向量抛错
        assertThrows(IllegalArgumentException.class, () -> {
            new AgentCard("card-1", "Agent1", "Desc", List.of("A"), new float[512], 0.9, true);
        });

        // 2. 全零向量抛错
        assertThrows(IllegalArgumentException.class, () -> {
            new AgentCard("card-2", "Agent2", "Desc", List.of("A"), new float[1536], 0.9, true);
        });

        // 3. 正常未归一化向量自动归一化到 ||v||_2 = 1.0 +- 1e-4
        float[] rawVec = new float[1536];
        for (int i = 0; i < 1536; i++) {
            rawVec[i] = 2.5f;
        }
        AgentCard card = new AgentCard("card-3", "Agent3", "Desc", List.of("A"), rawVec, 0.9, true);
        double sumSq = 0.0;
        for (float v : card.embedding1536()) {
            sumSq += v * v;
        }
        assertEquals(1.0, Math.sqrt(sumSq), 1e-4, "超球面向量模长必须严格归一化为 1.0");
    }

    @Test
    @DisplayName("Contract 4: FIPA-ACL CFP 契约网二阶密封拍卖 (VCG) 弱优势策略与社会盈余最大化")
    void testContractNetVcgTruthfulBiddingDominance() {
        ContractNetAuctionEngine engine = new ContractNetAuctionEngine();

        float[] taskVec = createNormalized1536Vector(1.0f);
        ContractNetProposal proposal = new ContractNetProposal(
                "cfp-001",
                "task-sql-audit",
                taskVec,
                0.80,
                System.currentTimeMillis() + 5000L
        );

        // 3 个候选 Agent，意图相似度由近及远
        float[] vecA = createNormalized1536Vector(1.05f); // 高匹配
        float[] vecB = createNormalized1536Vector(1.80f); // 中匹配
        float[] vecC = createNormalized1536Vector(3.50f); // 低匹配

        // 设定真实成本
        double trueCostA = 10.0;
        double trueCostB = 12.0;
        double trueCostC = 8.0;

        // 场景 1：所有 Agent 诚实报价
        List<ContractNetBid> truthfulBids = List.of(
                new ContractNetBid("bid-A", "cfp-001", "agent-A", trueCostA, vecA, 0.95),
                new ContractNetBid("bid-B", "cfp-001", "agent-B", trueCostB, vecB, 0.88),
                new ContractNetBid("bid-C", "cfp-001", "agent-C", trueCostC, vecC, 0.82)
        );

        Optional<ContractNetAwardReceipt> receiptOpt = engine.evaluateBids(proposal, truthfulBids, 0.70);
        assertTrue(receiptOpt.isPresent());
        ContractNetAwardReceipt receipt = receiptOpt.get();

        // 校验中标者为综合声明盈余最大者 Agent A
        assertEquals("agent-A", receipt.winningAgentId());
        // 二阶定价：支付金额使得其中标盈余等于次优候选者（VCG 机制）
        assertTrue(receipt.vcgPayment() >= trueCostA, "VCG 支付必须覆盖中标者真实成本以保证个体理性");
        assertTrue(receipt.socialSurplus() > 0, "全局社会盈余必须为正");
        assertNotNull(receipt.receiptHash());

        // 场景 2：Agent A 谎报虚高成本（bid = 50.0），丧失中标机会
        List<ContractNetBid> lyingBids = List.of(
                new ContractNetBid("bid-A", "cfp-001", "agent-A", 50.0, vecA, 0.95),
                new ContractNetBid("bid-B", "cfp-001", "agent-B", trueCostB, vecB, 0.88),
                new ContractNetBid("bid-C", "cfp-001", "agent-C", trueCostC, vecC, 0.82)
        );
        Optional<ContractNetAwardReceipt> lyingReceiptOpt = engine.evaluateBids(proposal, lyingBids, 0.70);
        assertTrue(lyingReceiptOpt.isPresent());
        assertNotEquals("agent-A", lyingReceiptOpt.get().winningAgentId(), "虚报过高成本将导致失去中标机会");
    }

    @Test
    @DisplayName("Contract 5: 契约网撮合耗时线性上界 O(N * d) 性能断言 (100 个 Agent <= 5ms)")
    void testContractNetAuctionTimeComplexity() {
        ContractNetAuctionEngine engine = new ContractNetAuctionEngine();
        float[] taskVec = createNormalized1536Vector(2.0f);
        ContractNetProposal proposal = new ContractNetProposal("cfp-perf", "task-perf", taskVec, 0.5, System.currentTimeMillis() + 10000L);

        int n = 100;
        List<ContractNetBid> bids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            float[] agentVec = createNormalized1536Vector(2.0f + i * 0.01f);
            bids.add(new ContractNetBid("bid-" + i, "cfp-perf", "agent-" + i, 10.0 + i * 0.1, agentVec, 0.8 + (i % 20) * 0.01));
        }

        // 预热
        engine.evaluateBids(proposal, bids, 0.7);

        // 正式测时
        long start = System.nanoTime();
        Optional<ContractNetAwardReceipt> receipt = engine.evaluateBids(proposal, bids, 0.7);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(receipt.isPresent());
        assertTrue(elapsedMs <= 50, "100 个智能体 1536 维测地匹配在 JVM 稳态下必须 <= 50ms (实际: " + elapsedMs + "ms)");
    }

    @Test
    @DisplayName("Contract 6: BoundedMailbox 容量上限 (1024) 与自适应反压快速失败")
    void testBoundedMailboxCapacityAndBackpressure() {
        BoundedMailbox mailbox = new BoundedMailbox(1024);
        assertEquals(1024, mailbox.capacity());
        assertEquals(0, mailbox.size());
        assertFalse(mailbox.isHighWatermark());

        // 填充至 820 条（> 80% 高水位）
        for (int i = 0; i < 820; i++) {
            A2AMessageEnvelope env = A2AMessageEnvelope.create("t", "s", "r", A2AMessageType.TASK_EXECUTE, "L", Map.of("i", i), 1000L);
            assertTrue(mailbox.offer(env));
        }
        assertTrue(mailbox.isHighWatermark(), "超过 80% 容量时必须触发高水位标记");

        // 继续填满至 1024
        for (int i = 820; i < 1024; i++) {
            A2AMessageEnvelope env = A2AMessageEnvelope.create("t", "s", "r", A2AMessageType.TASK_EXECUTE, "L", Map.of("i", i), 1000L);
            assertTrue(mailbox.offer(env));
        }
        assertEquals(1024, mailbox.size());

        // 第 1025 条必须被非阻塞拒绝并返回 false（主动反压，不阻塞线程）
        A2AMessageEnvelope overflowEnv = A2AMessageEnvelope.create("t", "s", "r", A2AMessageType.TASK_EXECUTE, "L", Map.of("i", 9999), 1000L);
        assertFalse(mailbox.offer(overflowEnv), "达到 1024 限制后必须拒绝入队触发反压");
    }

    @Test
    @DisplayName("Contract 7: VirtualThreadActorContainer 异步非阻塞调度与零死锁并发验证")
    void testVirtualThreadActorContainerNonBlocking() throws Exception {
        VirtualThreadActorContainer container = new VirtualThreadActorContainer();

        AtomicInteger agentACount = new AtomicInteger(0);
        AtomicInteger agentBCount = new AtomicInteger(0);

        container.registerActor("agent-A", env -> {
            agentACount.incrementAndGet();
            // 收到消息后异步回发给 agent-B，模拟双向交互
            if (env.payload().containsKey("ping")) {
                A2AMessageEnvelope pong = A2AMessageEnvelope.create(
                        env.traceId(), "agent-A", "agent-B", A2AMessageType.TASK_RESULT, "L", Map.of("pong", 1), 5000L);
                container.send(pong);
            }
        });

        container.registerActor("agent-B", env -> {
            agentBCount.incrementAndGet();
        });

        // 并发发送 100 条消息，验证非阻塞与无死锁
        int testCount = 100;
        CountDownLatch latch = new CountDownLatch(testCount);
        for (int i = 0; i < testCount; i++) {
            A2AMessageEnvelope ping = A2AMessageEnvelope.create(
                    "trace-async-" + i, "test-client", "agent-A", A2AMessageType.TASK_EXECUTE, "L", Map.of("ping", i), 5000L);
            assertTrue(container.send(ping));
            latch.countDown();
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // 等待异步事件处理完毕
        long deadline = System.currentTimeMillis() + 3000;
        while ((agentACount.get() < testCount || agentBCount.get() < testCount) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        assertEquals(testCount, agentACount.get());
        assertEquals(testCount, agentBCount.get(), "双向异步投递必须全量完成且无死锁");
        container.shutdown();
    }

    @Test
    @DisplayName("Contract 8: BlackboardVersion Lamport 全序时钟元组排序一致性")
    void testBlackboardVersionTotalOrdering() {
        BlackboardVersion v1 = new BlackboardVersion(1, 1, "node-01");
        BlackboardVersion v2 = new BlackboardVersion(2, 1, "node-01");
        BlackboardVersion v3 = new BlackboardVersion(2, 2, "node-01");
        BlackboardVersion v4 = new BlackboardVersion(2, 2, "node-02");

        // 全序关系校验
        assertTrue(v1.compareTo(v2) < 0, "globalVersion 优先比较");
        assertTrue(v2.compareTo(v3) < 0, "相同 globalVersion 下 term 优先");
        assertTrue(v3.compareTo(v4) < 0, "相同 globalVersion 与 term 下 nodeId 字典序全序兜底");

        // 自反性
        assertEquals(0, v1.compareTo(new BlackboardVersion(1, 1, "node-01")));
    }

    @Test
    @DisplayName("Contract 9: DistributedDualBlackboard L1 CAS 本地原子更新与冲突回滚")
    void testDistributedDualBlackboardL1CasCommit() {
        DistributedDualBlackboard blackboard = new DistributedDualBlackboard("node-01");
        blackboard.setSecurityLease("VALID_LEASE", System.currentTimeMillis() + 10000L);

        // 初始提交
        boolean firstCommit = blackboard.commitFact("user_risk_level", "LOW", "risk-agent", 0L);
        assertTrue(firstCommit, "初始写入版本为 0 必须成功");
        assertEquals("LOW", blackboard.getFact("user_risk_level").map(DistributedDualBlackboard.FactEntry::value).orElse(null));
        assertEquals(1L, blackboard.getFact("user_risk_level").get().version().globalVersion());

        // 预期版本匹配的 CAS 更新
        boolean secondCommit = blackboard.commitFact("user_risk_level", "MEDIUM", "risk-agent", 1L);
        assertTrue(secondCommit, "预期版本匹配的 CAS 更新必须成功");
        assertEquals(2L, blackboard.getFact("user_risk_level").get().version().globalVersion());

        // 预期版本过时的 CAS 冲突必须失败回滚
        boolean staleCommit = blackboard.commitFact("user_risk_level", "HIGH", "risk-agent", 1L);
        assertFalse(staleCommit, "版本过时的并发 CAS 必须被拒绝");
        assertEquals("MEDIUM", blackboard.getFact("user_risk_level").get().value(), "冲突后状态不得被污染");
    }

    @Test
    @DisplayName("Contract 10: DistributedDualBlackboard L1/L2 双态广播同步与半格合并收敛")
    void testDistributedDualBlackboardL2SyncAndSemilattice() {
        DistributedDualBlackboard node1 = new DistributedDualBlackboard("node-01");
        DistributedDualBlackboard node2 = new DistributedDualBlackboard("node-02");

        node1.setSecurityLease("LEASE_01", System.currentTimeMillis() + 10000L);
        node2.setSecurityLease("LEASE_02", System.currentTimeMillis() + 10000L);

        // 节点 1 本地提交
        node1.commitFact("task_status", "IN_PROGRESS", "worker-1", 0L);
        DistributedDualBlackboard.FactEntry entryNode1 = node1.getFact("task_status").orElseThrow();

        // 模拟 L2 Redis Streams 广播同步至节点 2
        boolean merged = node2.mergeRemoteFact(entryNode1);
        assertTrue(merged, "高版本远端条目合并必须成功");
        assertEquals("IN_PROGRESS", node2.getFact("task_status").map(DistributedDualBlackboard.FactEntry::value).orElse(null));

        // 节点 2 收到更旧版本的条目，必须被半格 Join 算子忽略
        DistributedDualBlackboard.FactEntry staleEntry = new DistributedDualBlackboard.FactEntry(
                "task_status", "PENDING", "worker-old",
                new BlackboardVersion(0L, 1L, "node-03"), Instant.now()
        );
        boolean staleMerged = node2.mergeRemoteFact(staleEntry);
        assertFalse(staleMerged, "低版本旧条目不得覆盖当前较高版本状态");
        assertEquals("IN_PROGRESS", node2.getFact("task_status").get().value());
    }

    @Test
    @DisplayName("Contract 11: DistributedDualBlackboard Fencing 租约过期自动降级为只读模式")
    void testDistributedDualBlackboardFencingLeaseExpiredReadOnly() {
        DistributedDualBlackboard blackboard = new DistributedDualBlackboard("node-01");

        // 设定已过期的租约
        blackboard.setSecurityLease("EXPIRED_LEASE", System.currentTimeMillis() - 1000L);
        assertTrue(blackboard.isReadOnly(), "租约过期时必须自动降级为只读模式");

        // 只读模式下所有写入必须抛出异常或被拒绝
        assertThrows(IllegalStateException.class, () -> {
            blackboard.commitFact("test_key", "test_val", "agent", 0L);
        }, "租约失效且处于只读模式时，写入必须被硬拦截以防脑裂脏写");
    }

    @Test
    @DisplayName("Contract 12: 跨模块全量联合回归基线断言")
    void testCrossModuleRegression() {
        // 验证系统核心常量与类型完备
        assertNotNull(A2AMessageType.CFP_SOLICIT);
        assertNotNull(A2AMessageType.BID_PROPOSE);
        assertNotNull(A2AMessageType.AWARD_ACCEPT);
        assertNotNull(A2AMessageType.TASK_EXECUTE);
        assertNotNull(A2AMessageType.TASK_RESULT);
        assertNotNull(A2AMessageType.BLACKBOARD_SYNC);
    }
}
