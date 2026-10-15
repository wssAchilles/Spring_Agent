package tech.qiantong.qknow.hermes.swarm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.speculative.DualStreamSpeculativeEngine;
import tech.qiantong.qknow.hermes.agent.speculative.SpeculativeDraftPrefetchReceipt;
import tech.qiantong.qknow.hermes.agent.speculative.ThinkingPatternDfa;
import tech.qiantong.qknow.hermes.swarm.dynamic.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 性能与规模双核架构升级契约测试：
 * 覆盖：
 * 1. 动态拓扑稀疏化自组织激活、千问 1536 维超球面测地大圆弧度量与夏普利动态剪枝
 * 2. 智能体因果时序向量时钟 (Vector Clock) 偏序传递与防失序事件总线
 * 3. DeepSeek 思考流模式识别 DFA 状态机与前向预取待命池秒级命中
 * 4. 纯 Java 21 Record 凭单与常量时间 MessageDigest.isEqual 验签防侧信道
 */
public class DynamicSwarmAndSpeculativeContractTest {

    private static final String TEST_SECRET = "TEST_SECRET_SWARM_SPECULATIVE_2026";

    private float[] generateNormalizedVector(long seed) {
        Random random = new Random(seed);
        float[] v = new float[DynamicTopologyBus.VECTOR_DIMENSION];
        double sumSq = 0.0;
        for (int i = 0; i < DynamicTopologyBus.VECTOR_DIMENSION; i++) {
            v[i] = (float) (random.nextGaussian());
            sumSq += v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < DynamicTopologyBus.VECTOR_DIMENSION; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    @Test
    @DisplayName("契约一：动态拓扑稀疏自组织激活与超球面测地大圆弧契约")
    public void testDynamicTopologyActivationAndSphericalGeodesic() {
        DynamicTopologyBus bus = new DynamicTopologyBus(TEST_SECRET);

        float[] v1 = generateNormalizedVector(5001L);
        float[] v2 = generateNormalizedVector(5002L);
        float[] v3 = generateNormalizedVector(5003L);

        // 1. 验证非单位向量防御拦截
        float[] zeroVec = new float[DynamicTopologyBus.VECTOR_DIMENSION];
        assertThrows(IllegalArgumentException.class, () -> {
            new DynamicTopologyBus.AgentProfile("agent_faulty", "faulty_role", zeroVec);
        }, "非单位向量必须被严格抛出 IllegalArgumentException 拦截");

        // 2. 正常注册 3 个专业智能体
        bus.registerAgent(new DynamicTopologyBus.AgentProfile("agent_sql_expert", "SQL Database Expert", v1));
        bus.registerAgent(new DynamicTopologyBus.AgentProfile("agent_rag_specialist", "RAG Knowledge Specialist", v2));
        bus.registerAgent(new DynamicTopologyBus.AgentProfile("agent_math_solver", "Mathematical Logic Solver", v3));

        // 3. 针对接近 v1 的任务向量激活 Top-2 专家
        String taskId = "task_crm_query_999";
        DynamicTopologyReceipt receipt = bus.activateSubTopology(taskId, v1, 2);

        assertNotNull(receipt);
        assertEquals(taskId, receipt.taskId());
        assertEquals(2, receipt.activatedAgents().size(), "必须精确稀疏化激活 Top-2 专家");
        assertEquals("agent_sql_expert", receipt.activatedAgents().get(0), "与任务向量最契合的智能体必须排在首位");
        assertTrue(receipt.verifySignature(TEST_SECRET), "拓扑激活凭单必须通过常量时间验签");

        // 验证测地大圆弧散度单调性
        float top1Score = receipt.geodesicScores().get("agent_sql_expert");
        assertEquals(0.0f, top1Score, 1e-3, "自身向量的测地散度必须逼近 0.0");
    }

    @Test
    @DisplayName("契约二：夏普利边际贡献动态剪枝与因果时序向量时钟契约")
    public void testShapleyPruningAndCausalVectorClock() {
        DynamicTopologyBus bus = new DynamicTopologyBus(TEST_SECRET);

        float[] v1 = generateNormalizedVector(6001L);
        float[] v2 = generateNormalizedVector(6002L);
        float[] v3 = generateNormalizedVector(6003L);

        bus.registerAgent(new DynamicTopologyBus.AgentProfile("agent_A", "Architect", v1));
        bus.registerAgent(new DynamicTopologyBus.AgentProfile("agent_B", "Worker B", v2));
        bus.registerAgent(new DynamicTopologyBus.AgentProfile("agent_C", "Worker C", v3));

        String taskId = "task_refactor_001";
        bus.activateSubTopology(taskId, v1, 3);
        assertEquals(3, bus.getActiveAgents(taskId).size());

        // 1. 夏普利边际贡献动态剪枝测试（设定阈值 0.15，淘汰低贡献节点 agent_C）
        Map<String, Double> shapleyMap = Map.of(
                "agent_A", 0.60,
                "agent_B", 0.30,
                "agent_C", 0.05
        );
        DynamicTopologyReceipt pruneReceipt = bus.pruneLowContributionAgents(taskId, shapleyMap, 0.15);

        assertNotNull(pruneReceipt);
        assertEquals(2, pruneReceipt.activatedAgents().size());
        assertTrue(pruneReceipt.prunedAgents().contains("agent_C"), "低贡献节点必须被平滑剪枝进入沉睡");
        assertFalse(bus.getActiveAgents(taskId).contains("agent_C"));
        assertTrue(pruneReceipt.verifySignature(TEST_SECRET));

        // 2. 因果向量时钟与偏序事件流测试
        SwarmVectorClock c0 = bus.getAgentClock("agent_A");
        assertEquals(0, c0.getTime("agent_A"));

        // agent_A 发送事件给 agent_B
        SwarmCausalEvent evt1 = bus.createAndPublishEvent("agent_A", "agent_B", "PROPOSAL", "Plan v1");
        assertTrue(evt1.verifySignature(TEST_SECRET));

        SwarmVectorClock clockA1 = bus.getAgentClock("agent_A");
        SwarmVectorClock clockB1 = bus.getAgentClock("agent_B");

        assertEquals(1, clockA1.getTime("agent_A"));
        // agent_B 接收并自增
        assertEquals(1, clockB1.getTime("agent_A"));
        assertEquals(1, clockB1.getTime("agent_B"));

        // 严格满足 happens-before 偏序
        assertTrue(clockA1.happensBefore(clockB1), "事件发送方时钟必须严格 happens-before 接收方合并后时钟");
        assertFalse(clockB1.happensBefore(clockA1));
    }

    @Test
    @DisplayName("契约三：DeepSeek 思考流模式识别 DFA 状态机契约")
    public void testThinkingPatternDfaStreamingMatch() {
        ThinkingPatternDfa dfa = new ThinkingPatternDfa();
        List<String> matchedTables = new ArrayList<>();
        List<String> matchedDocs = new ArrayList<>();

        dfa.registerRule("DB", "(?:查询数据库|table)[:：\\s]+([a-zA-Z0-9_]+)", matchedTables::add);
        dfa.registerRule("DOC", "(?:检索文档|doc)[:：\\s]+([a-zA-Z0-9_]+)", matchedDocs::add);

        // 模拟 DeepSeek API 流式分片推送 reasoning_content
        dfa.feedChunk("用户需要统计活跃账户数据。让我想想...");
        dfa.feedChunk("首先需要去查询数据库: users_table_v2，并读取相关字段。");
        dfa.feedChunk("接下来，参考检索文档: doc_finance_guide_2026 中的规则要求。");
        // 重复内容测试（防重复触发）
        dfa.feedChunk("再次确认查询数据库: users_table_v2 中的权限。");

        assertEquals(1, matchedTables.size(), "相同实体不得重复触发预取");
        assertEquals("users_table_v2", matchedTables.get(0));
        assertEquals(1, matchedDocs.size());
        assertEquals("doc_finance_guide_2026", matchedDocs.get(0));
        assertEquals(2, dfa.getTriggeredMatches().size());
    }

    @Test
    @DisplayName("契约四：双流推测执行与前向预取待命池秒级命中契约")
    public void testDualStreamSpeculativeEngineHitAndReceipt() {
        DualStreamSpeculativeEngine engine = new DualStreamSpeculativeEngine(TEST_SECRET);

        // 注册预取执行器
        AtomicInteger fetcherCallCount = new AtomicInteger(0);
        engine.registerPrefetchFetcher("db", table -> {
            fetcherCallCount.incrementAndGet();
            return "DATABASE_ROWS_FOR_" + table;
        });

        // 注册 System 1 本地轻量草稿规则
        engine.registerSystem1DraftRule("用户信息", "db:users_metadata");

        // 1. System 1 本地快速推测
        List<String> drafts = engine.generateSystem1DraftCandidates("请帮我查看用户信息列表");
        assertEquals(1, drafts.size());
        assertEquals("db:users_metadata", drafts.get(0));

        // 2. System 2 模拟流式注入 DeepSeek 思考流
        engine.feedThinkingChunk("思考分析中... 我们需要查询数据库: order_records 进行二次核实。");

        // 等待微量时间确保异步虚拟线程预取完成
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {}

        // 3. 最终决策执行（命中思考流已预取的待命池）
        DualStreamSpeculativeEngine.ExecutionResult result = engine.resolveExecution(
                "req_008899", "db:order_records", key -> "FALLBACK_SLOW_DATA");

        assertNotNull(result);
        assertEquals("DATABASE_ROWS_FOR_order_records", result.output(), "必须返回预取的真实数据");
        assertTrue(result.speculativeCacheHit(), "必须成功命中推测待命池缓存");
        SpeculativeDraftPrefetchReceipt receipt = result.receipt();
        assertNotNull(receipt);
        assertEquals(1, receipt.cacheHitCount());
        assertTrue(receipt.verifySignature(TEST_SECRET), "推测凭单必须通过常量时间验签");
    }
}
