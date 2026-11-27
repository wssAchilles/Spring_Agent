package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.ai.federated.LocalMemoryDistiller;
import tech.qiantong.qknow.ai.immune.dto.AntigenDTO;
import tech.qiantong.qknow.ai.immune.engine.AntigenExtractor;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.ReflectiveAgent;
import tech.qiantong.qknow.hermes.agent.ReflectiveConfig;
import tech.qiantong.qknow.hermes.agent.debate.engine.NashConfidenceWeightedJudge;
import tech.qiantong.qknow.hermes.judge.AiJudgeService;
import tech.qiantong.qknow.hermes.judge.JudgeResult;
import tech.qiantong.qknow.hermes.prefix.PrefixCacheStabilityAuditor;
import tech.qiantong.qknow.hermes.prefix.PrefixCacheStabilityReceipt;
import tech.qiantong.qknow.hermes.proto.ChatEvent;
import tech.qiantong.qknow.hermes.proto.ChatMessage;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelFinished;
import tech.qiantong.qknow.hermes.rag.causal.SteinerCausalSubgraphPruner;
import tech.qiantong.qknow.mcp.core.model.CallToolResult;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcRequest;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcResponse;
import tech.qiantong.qknow.mcp.server.config.McpBuiltinProviderDelegates;
import tech.qiantong.qknow.mcp.server.config.McpServerAutoConfiguration;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 博士论文代际升级 · 阶段 0 核心契约测试套件
 * 覆盖：
 * 1. S^1023 欧氏弦度量公理与大圆三点三角不等式反例修复验证；
 * 2. 千问 1024 维真实 EmbeddingModel SPI 委托与零 Random 局部敏感特征哈希语义连续性验证；
 * 3. ReflectiveAgent 零 SystemPrompt 篡改（64-token 缓存前缀不变性）与常量时间验签凭单验证；
 * 4. MCP 四大内置 Provider Spring Delegate 自动绑定验证。
 */
class Phase0PhdFoundationPurgeContractTest {

    private static final int DIM = 1024;

    @Test
    @DisplayName("0.1 S^1023 弦度量公理：大圆三点 (0, pi/4, pi/2) 必须严格满足三角不等式 d(u,v) <= d(u,z) + d(z,v)")
    void test01_SteinerChordMetricSatisfiesStrictTriangleInequalityOnGreatCircleCounterexample() {
        float[] u = new float[DIM];
        float[] z = new float[DIM];
        float[] v = new float[DIM];

        u[0] = 1.0f;
        z[0] = (float) (Math.sqrt(2.0) / 2.0);
        z[1] = (float) (Math.sqrt(2.0) / 2.0);
        v[1] = 1.0f;

        double dUZ = SteinerCausalSubgraphPruner.computeHypersphericalDistance(u, z);
        double dZV = SteinerCausalSubgraphPruner.computeHypersphericalDistance(z, v);
        double dUV = SteinerCausalSubgraphPruner.computeHypersphericalDistance(u, v);

        // 正交单位向量的真实欧氏弦长必须等于 sqrt(2)
        assertEquals(Math.sqrt(2.0), dUV, 1e-5, "正交单位向量的 S^1023 弦距离必须严格为 sqrt(2)");
        // 严格三角不等式（不允许任何 1.5 倍放缩作弊系数）
        assertTrue(dUV <= dUZ + dZV + 1e-9,
                String.format("三角不等式破裂: d(u,v)=%.6f > d(u,z)+d(z,v)=%.6f", dUV, dUZ + dZV));
    }

    @Test
    @DisplayName("0.2 真实千问 EmbeddingModel SPI 委托与离线局部敏感特征连续性（杜绝 Random(hash) 正交白噪声）")
    void test02_EmbeddingModelDelegationAndSemanticContinuityWithoutRandomNoise() {
        AtomicInteger realModelCalls = new AtomicInteger(0);
        EmbeddingModel mockQwenModel = mock(EmbeddingModel.class);
        when(mockQwenModel.embed(anyString())).thenAnswer(inv -> {
            realModelCalls.incrementAndGet();
            float[] vec = new float[DIM];
            vec[0] = 0.6f;
            vec[1] = 0.8f;
            return vec;
        });

        // 1. 注入真实 EmbeddingModel 时，必须 100% 委托千问模型而非本地伪造
        NashConfidenceWeightedJudge wiredJudge = new NashConfidenceWeightedJudge(mockQwenModel);
        double[] embedded = wiredJudge.embedTextWithQwen("测试千问真实委托");
        assertEquals(1, realModelCalls.get(), "注入 EmbeddingModel 后必须调用真实千问接口");
        assertEquals(0.6, embedded[0], 1e-5);
        assertEquals(0.8, embedded[1], 1e-5);

        AntigenExtractor wiredExtractor = new AntigenExtractor(mockQwenModel);
        AntigenDTO antigen = wiredExtractor.extractAntigen("正常查询文本");
        assertEquals(2, realModelCalls.get(), "AntigenExtractor 注入 EmbeddingModel 后必须调用真实接口");
        assertEquals(0.6f, antigen.getSemanticEmbedding()[0], 1e-5f);

        LocalMemoryDistiller wiredDistiller = new LocalMemoryDistiller(mockQwenModel);
        LocalMemoryDistiller.LocalInsight insight = wiredDistiller.distill("agent-1", "锚点问题", List.of("记忆1"), null);
        assertEquals(3, realModelCalls.get(), "LocalMemoryDistiller 注入 EmbeddingModel 后必须调用真实接口");
        assertEquals(DIM, insight.embeddingVector().length);

        // 2. 离线单测兜底模式：近似语义文本必须具备显著正余弦相似度（旧版 Random(text.hashCode()) 期望为 0）
        NashConfidenceWeightedJudge offlineJudge = new NashConfidenceWeightedJudge();
        double[] a = offlineJudge.embedTextWithQwen("大额信贷审批合规风控与三级复核制度");
        double[] b = offlineJudge.embedTextWithQwen("大额信贷审批安全风控与三级复核规范");
        double sim = offlineJudge.computeGeodesicInnerProduct(a, b);
        assertTrue(sim > 0.65, "高度重合文本的离线特征哈希内积必须 > 0.65（证明已根除 Random(hashCode) 白噪声），实际: " + sim);
    }

    @Test
    @DisplayName("0.3 ReflectiveAgent 多轮重试必须保持 SystemPrompt 字节级恒等，并签发常量时间前缀审计凭单")
    void test03_ReflectiveAgentPreservesSystemPromptPrefixAndAudits64TokenBlocks() {
        AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
        AiJudgeService judgeService = mock(AiJudgeService.class);
        ReflectiveConfig config = new ReflectiveConfig();
        config.setEnabled(true);
        config.setMaxRetries(1);

        ReflectiveAgent reflectiveAgent = new ReflectiveAgent(orchestrator, judgeService, config);
        String invariantSystemPrompt = "你是一个严格遵守形式化契约的科学智能体。".repeat(20);

        ChatRequest originalRequest = ChatRequest.newBuilder()
                .setRequestId("req-prefix-001")
                .setQuestion("推导超球面弦度量与测地度量的等价界")
                .setSystemPrompt(invariantSystemPrompt)
                .build();

        when(orchestrator.chat(any(ChatRequest.class))).thenReturn(Flux.just(
                ChatEvent.newBuilder()
                        .setRequestId("req-prefix-001")
                        .setFinished(ModelFinished.newBuilder().setFullText("初稿推导略").build())
                        .build()
        ));

        when(judgeService.judge(anyString(), anyString(), anyString()))
                .thenReturn(new JudgeResult(0.4, 0.5, 0.4, 0.43, false, "未给出球冠半径紧上界证明"))
                .thenReturn(new JudgeResult(0.95, 0.95, 0.95, 0.95, true, "证明完备"));

        reflectiveAgent.chat(originalRequest).blockLast();

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(orchestrator, times(2)).chat(captor.capture());

        ChatRequest round1 = captor.getAllValues().get(0);
        ChatRequest round2 = captor.getAllValues().get(1);

        // 核心铁律：第 2 轮 SystemPrompt 必须与第 1 轮字节级 100% 相等，绝不允许拼接破坏 DeepSeek 缓存前缀！
        assertEquals(invariantSystemPrompt, round1.getSystemPrompt());
        assertEquals(invariantSystemPrompt, round2.getSystemPrompt(),
                "ReflectiveAgent 重试严禁篡改 SystemPrompt 破坏 DeepSeek 1M Context Cache 前缀！");

        // 批判反馈必须通过对话历史尾部追加传递
        List<ChatMessage> round2History = round2.getHistoryList();
        assertFalse(round2History.isEmpty(), "重试轮次必须将上轮回答与批判意见追加至 history 尾部");
        boolean hasCritiqueInHistory = round2History.stream()
                .anyMatch(m -> m.getContent().contains("未给出球冠半径紧上界证明")
                        && m.getContent().contains("reflection_critique"));
        assertTrue(hasCritiqueInHistory, "history 尾部必须包含结构化 reflection_critique 与评审意见");

        // 验证 64-token 块前缀不变性密码学凭单
        PrefixCacheStabilityAuditor auditor = new PrefixCacheStabilityAuditor("phd-prefix-hmac-secret-key");
        List<String> metaTools = List.of("mcp_discover_tools", "mcp_load_tool_schema", "mcp_exec_code_plan", "hippocampal_subgraph_probe");
        PrefixCacheStabilityReceipt r1 = auditor.auditRound("req-prefix-001", 1, round1.getSystemPrompt(), metaTools, null);
        PrefixCacheStabilityReceipt r2 = auditor.auditRound("req-prefix-001", 2, round2.getSystemPrompt(), metaTools, r1.systemAndToolsSha256());

        assertTrue(r2.prefixPreserved(), "系统提示词与静态元工具未变时 prefixPreserved 必须为 true");
        assertTrue(r2.aligned64TokenBlocks() >= 1, "稳定对齐的 64-token 缓存块数量必须 >= 1");
        assertTrue(auditor.verifyReceipt(r2), "常量时间 HMAC 验签必须通过");

        // 篡改单字节后常量时间验签必须拒绝
        PrefixCacheStabilityReceipt tampered = new PrefixCacheStabilityReceipt(
                r2.requestId(), r2.roundIndex(), r2.systemAndToolsSha256(),
                r2.aligned64TokenBlocks() + 1, r2.prefixPreserved(), r2.hmacSignature()
        );
        assertFalse(auditor.verifyReceipt(tampered), "篡改凭单字段后常量时间验签必须返回 false");
    }

    @Test
    @DisplayName("0.4 McpServerAutoConfiguration 支持注入 McpBuiltinProviderDelegates，四大内置工具真实响应就绪")
    void test04_McpBuiltinProvidersWiringReady() {
        McpBuiltinProviderDelegates delegates = new McpBuiltinProviderDelegates(
                params -> CallToolResult.text("KB召回命中: " + params.get("query")),
                cypher -> CallToolResult.text("KG节点命中: " + cypher),
                (dsId, sql) -> CallToolResult.text("SQL只读结果: " + dsId + " -> " + sql),
                (lang, code) -> CallToolResult.text("沙箱受限执行完成: " + lang)
        );

        McpServerAutoConfiguration autoConfig = new McpServerAutoConfiguration();
        McpServerRegistry registry = autoConfig.mcpServerRegistry(
                autoConfig.quadDefenseSecurityPipeline(autoConfig.mcpTransientLeaseManager()),
                delegates
        );

        JsonRpcResponse kbResp = registry.handleRequest(new JsonRpcRequest(
                1, "tools/call", Map.of("name", "qknow_kb_search", "arguments", Map.of("corpusId", 1L, "query", "超球面流形"))));
        CallToolResult kbRes = (CallToolResult) kbResp.result();
        assertFalse(kbRes.isError(), "绑定委托后 qknow_kb_search 不应报错");
        assertTrue(kbRes.getFirstText().contains("超球面流形"));

        JsonRpcResponse kgResp = registry.handleRequest(new JsonRpcRequest(
                2, "tools/call", Map.of("name", "qknow_kg_query", "arguments", Map.of("cypherQuery", "MATCH (n:HippoEntity) RETURN n LIMIT 5"))));
        CallToolResult kgRes = (CallToolResult) kgResp.result();
        assertFalse(kgRes.isError(), "绑定委托后 qknow_kg_query 不应报错");

        JsonRpcResponse sqlResp = registry.handleRequest(new JsonRpcRequest(
                3, "tools/call", Map.of("name", "qknow_sql_query", "arguments", Map.of("dsId", "pg_main", "sql", "SELECT id FROM kb_doc"))));
        CallToolResult sqlRes = (CallToolResult) sqlResp.result();
        assertFalse(sqlRes.isError(), "绑定委托后 qknow_sql_query 不应报错");
        assertTrue(sqlRes.getFirstText().contains("LIMIT 1000"), "只读 SQL 守卫自动追加 LIMIT 1000");
    }
}
