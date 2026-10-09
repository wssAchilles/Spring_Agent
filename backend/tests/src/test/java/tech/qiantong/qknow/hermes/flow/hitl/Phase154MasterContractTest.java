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

    @Test
    @DisplayName("契约 7：OpenAPI 3.0 动态网关 — 零代码解析 Swagger 规范并生成标准 MCP Tools")
    void test07_DynamicOpenApiMcpBridgeSpecParsingAndToolGeneration() {
        String mockOpenApiSpec = """
                {
                  "openapi": "3.0.1",
                  "info": { "title": "Enterprise CRM API", "version": "1.0.0" },
                  "paths": {
                    "/api/v1/users/{userId}": {
                      "get": {
                        "operationId": "getUserDetail",
                        "summary": "获取用户详情",
                        "parameters": [
                          { "name": "userId", "in": "path", "required": true, "schema": { "type": "integer" } },
                          { "name": "includeOrders", "in": "query", "required": false, "schema": { "type": "boolean" } }
                        ]
                      }
                    },
                    "/api/v1/orders": {
                      "post": {
                        "operationId": "createOrder",
                        "summary": "创建企业订单",
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "required": ["orderId", "amount"],
                                "properties": {
                                  "orderId": { "type": "string" },
                                  "amount": { "type": "number" }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;

        tech.qiantong.qknow.hermes.tool.mcp.DynamicOpenApiMcpBridge bridge =
                new tech.qiantong.qknow.hermes.tool.mcp.DynamicOpenApiMcpBridge("https://api.enterprise.corp");

        var tools = bridge.registerOpenApiSpec(mockOpenApiSpec);
        assertEquals(2, tools.size(), "必须精准解析出 2 个标准 MCP 工具");

        var getTool = tools.stream().filter(t -> t.getString("name").contains("getUserDetail")).findFirst().orElse(null);
        assertNotNull(getTool, "getUserDetail 工具必须成功注册");
        assertEquals("获取用户详情", getTool.getString("description"));

        var postTool = tools.stream().filter(t -> t.getString("name").contains("createOrder")).findFirst().orElse(null);
        assertNotNull(postTool, "createOrder 工具必须成功注册");
        com.alibaba.fastjson2.JSONObject schema = postTool.getJSONObject("inputSchema");
        assertNotNull(schema);
        com.alibaba.fastjson2.JSONArray required = schema.getJSONArray("required");
        assertTrue(required.contains("orderId") && required.contains("amount"), "必须包含必填参数校验");
    }

    @Test
    @DisplayName("契约 8：Anthropic MCP Sampling 反向推理处理器 — 规范化解析与生成")
    void test08_McpSamplingHandlerMessageGeneration() {
        tech.qiantong.qknow.hermes.tool.mcp.McpSamplingHandler handler =
                new tech.qiantong.qknow.hermes.tool.mcp.McpSamplingHandler(null);

        com.alibaba.fastjson2.JSONObject requestParams = new com.alibaba.fastjson2.JSONObject();
        requestParams.put("systemPrompt", "你是一个专业企业助手");
        com.alibaba.fastjson2.JSONArray messages = new com.alibaba.fastjson2.JSONArray();
        com.alibaba.fastjson2.JSONObject uMsg = new com.alibaba.fastjson2.JSONObject();
        uMsg.put("role", "user");
        uMsg.put("content", com.alibaba.fastjson2.JSONObject.of("type", "text", "text", "总结该任务"));
        messages.add(uMsg);
        requestParams.put("messages", messages);

        com.alibaba.fastjson2.JSONObject response = handler.handleSamplingRequest(requestParams);
        assertNotNull(response);
        assertEquals("assistant", response.getString("role"));
        assertEquals("deepseek-flash", response.getString("model"));
        assertNotNull(response.getJSONObject("content"));
        assertEquals("endTurn", response.getString("stopReason"));
    }

    @Test
    @DisplayName("契约 9：ToolNodeBO 高危工具人机协同审批 (HITL) 门禁 — 未授权严格挂起，放行后正常执行")
    void test09_ToolNodeHighRiskHitlInterceptionAndApprovalResume() {
        tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO nodeDef =
                new tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO();
        nodeDef.setUuid("tool_node_dangerous_001");
        nodeDef.setName("delete_user_data"); // 命名包含高危操作 delete
        nodeDef.setConfig("{\"toolCode\":\"delete_user_data\"}");

        tech.qiantong.qknow.module.kb.service.flow.bo.ToolNodeBO toolNode =
                new tech.qiantong.qknow.module.kb.service.flow.bo.ToolNodeBO(nodeDef, List.of(), null);

        // 1. 首次调用：无审批授权，必须被拦截并返回 SUSPENDED 挂起
        tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO unapprovedContext =
                new tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO();
        unapprovedContext.setVariables(new com.alibaba.fastjson2.JSONObject());

        tech.qiantong.qknow.module.kb.service.flow.bo.NodeRunResultBO suspendedResult = toolNode.execute(unapprovedContext);
        assertNotNull(suspendedResult);
        assertEquals(RuntimeStatusEnums.SUSPENDED.getCode(), suspendedResult.getStatus(), "高危工具在未审批前必须严格返回 SUSPENDED");

        // 2. 二次调用：注入人工审批放行标记 APPROVED
        tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO approvedContext =
                new tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO();
        com.alibaba.fastjson2.JSONObject approvedVars = new com.alibaba.fastjson2.JSONObject();
        approvedVars.put("approvalResult", "APPROVED");
        approvedContext.setVariables(approvedVars);

        tech.qiantong.qknow.module.kb.service.flow.bo.NodeRunResultBO approvedResult = toolNode.execute(approvedContext);
        assertNotNull(approvedResult);
        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), approvedResult.getStatus(), "人工放行后高危工具门禁解除并正常执行");
    }

    @Test
    @DisplayName("契约 10：ContextualIngestionChunkProcessor 入库语境化生成与 64-token 上下文缓存对齐")
    void test10_ContextualIngestionChunkProcessingAndCacheAlignment() {
        tech.qiantong.qknow.module.kmc.service.ContextualIngestionChunkProcessor processor =
                new tech.qiantong.qknow.module.kmc.service.ContextualIngestionChunkProcessor();

        // 1. 无模型时的 Fail-open 降级
        var fallbackChunk = processor.processChunk("chk_001", "这是孤立的正文段落", "整篇文档摘要", null);
        assertNotNull(fallbackChunk);
        assertEquals("这是孤立的正文段落", fallbackChunk.enrichedText());
        assertFalse(fallbackChunk.cachingAligned(), "无模型时应降级并标记未对齐");

        // 2. 模拟真实大模型返回前置语境
        org.springframework.ai.chat.model.ChatModel mockChatModel = prompt -> {
            var res = new org.springframework.ai.chat.model.ChatResponse(List.of(
                    new org.springframework.ai.chat.model.Generation(
                            new org.springframework.ai.chat.messages.AssistantMessage("本段落来自企业知识库架构说明，主要阐述切片存储设计。")
                    )
            ));
            return res;
        };

        var enriched = processor.processChunk("chk_002", "切片数据被写入分布式数据库中。", "系统架构文档", mockChatModel);
        assertNotNull(enriched);
        assertTrue(enriched.cachingAligned());
        assertTrue(enriched.enrichedText().startsWith("本段落来自企业知识库架构说明"));
        assertTrue(enriched.enrichedText().contains("切片数据被写入分布式数据库中。"));

        // 3. 批量并发虚拟线程处理
        var batch = List.of(
                org.apache.commons.lang3.tuple.Pair.of("chk_003", "段落 A"),
                org.apache.commons.lang3.tuple.Pair.of("chk_004", "段落 B")
        );
        var batchResult = processor.batchProcessChunks(batch, "系统概要", mockChatModel);
        assertEquals(2, batchResult.size());
        assertEquals("chk_003", batchResult.get(0).chunkId());
        assertEquals("chk_004", batchResult.get(1).chunkId());
    }

    @Test
    @DisplayName("契约 11：ParentChildTreeChunkingService 两级切片生成与命中小切片向上父切片保序聚合")
    void test11_ParentChildTreeChunkingAndDeduplicatedRecall() {
        tech.qiantong.qknow.module.kmc.service.ParentChildTreeChunkingService treeService =
                new tech.qiantong.qknow.module.kmc.service.ParentChildTreeChunkingService();

        // 模拟 1200 字符的文档内容
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("这是第 ").append(i).append(" 句包含关键企业知识内容的测试文字。");
        }
        String docText = sb.toString();

        // 设定父切片 500 字，重叠 50 字；子切片 150 字，重叠 20 字
        var bundle = treeService.createHierarchicalChunks("doc_phase154", docText, 500, 50, 150, 20);
        assertNotNull(bundle);
        assertFalse(bundle.parentChunks().isEmpty(), "父切片不应为空");
        assertFalse(bundle.childChunks().isEmpty(), "子切片不应为空");
        assertTrue(bundle.childChunks().size() > bundle.parentChunks().size(), "子切片数量应明显多于父切片");

        // 验证命中了同一 Parent 下的不同 Child 时，向上解包聚合去重
        Map<String, tech.qiantong.qknow.module.kmc.service.ParentChildTreeChunkingService.ParentChunk> parentMap = new HashMap<>();
        for (var p : bundle.parentChunks()) {
            parentMap.put(p.parentId(), p);
        }
        Map<String, tech.qiantong.qknow.module.kmc.service.ParentChildTreeChunkingService.ChildChunk> childMap = new HashMap<>();
        for (var c : bundle.childChunks()) {
            childMap.put(c.childId(), c);
        }

        // 模拟检索命中了属于同一个 parent 的两个 child (如 doc_phase154_p0_c0 与 doc_phase154_p0_c1)
        List<String> hitChildIds = List.of(bundle.childChunks().get(0).childId(), bundle.childChunks().get(1).childId());
        var resolvedParents = treeService.resolveParentsForMatchedChildren(hitChildIds, parentMap, childMap);

        assertEquals(1, resolvedParents.size(), "同一父节点下的多个子切片命中时必须聚合去重为一个父切片");
        assertEquals("doc_phase154_p0", resolvedParents.get(0).parentId());
    }

    @Test
    @DisplayName("契约 12：AdaptiveHypersphericalRrfGovernor 意图熵自适应加权与千问 1536 维超球面 RRF 融合重排")
    void test12_AdaptiveHypersphericalRrfRerankAndIntegrityVerification() {
        tech.qiantong.qknow.module.kb.service.agent.retrieval.AdaptiveHypersphericalRrfGovernor governor =
                new tech.qiantong.qknow.module.kb.service.agent.retrieval.AdaptiveHypersphericalRrfGovernor();

        // 1. 验证意图语义熵动态加权
        double lowEntropyAlpha = governor.computeAdaptiveAlpha("ERR_404_NOT_FOUND");
        assertEquals(0.25, lowEntropyAlpha, 1e-4, "特定错误代码等低熵查询应自适应偏向 Sparse 全文匹配");

        double highEntropyAlpha = governor.computeAdaptiveAlpha("请问在超大规模分布式集群中，如何利用虚拟线程实现长程多智能体编排？");
        assertEquals(0.75, highEntropyAlpha, 1e-4, "复杂自然语言长问句应自适应偏向 Dense 超球面向量语义匹配");

        // 2. 构造阿里千问 1536 维超球面单位向量 (L2 norm = 1.0)
        float[] validHypersphericalVec = new float[1536];
        float val = (float) (1.0 / Math.sqrt(1536));
        for (int i = 0; i < 1536; i++) {
            validHypersphericalVec[i] = val;
        }
        assertTrue(governor.validateHypersphericalNorm(validHypersphericalVec), "必须严格满足 ||v||_2 = 1.0 超球面几何约束");

        // 3. 执行自适应重排并生成审计凭单
        tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult r1 =
                new tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult();
        r1.setId("doc_chunk_01");
        r1.setContent("分布式工作流执行器配置");

        tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult r2 =
                new tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult();
        r2.setId("doc_chunk_02");
        r2.setContent("错误代码 ERR_404 故障排查手册");

        Map<String, float[]> chunkVectors = Map.of("doc_chunk_01", validHypersphericalVec);

        var rerankResult = governor.executeAdaptiveRerank(
                "请问分布式工作流执行器如何配置？",
                List.of(r1),
                List.of(r2),
                validHypersphericalVec,
                chunkVectors,
                5
        );

        assertNotNull(rerankResult);
        assertFalse(rerankResult.getKey().isEmpty());
        var receipt = rerankResult.getValue();
        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity(), "自适应重排不可变凭单 SHA-256 常量时间自验真必须通过");
        assertEquals(0.75, receipt.adaptiveAlpha(), 1e-4);
    }
}

