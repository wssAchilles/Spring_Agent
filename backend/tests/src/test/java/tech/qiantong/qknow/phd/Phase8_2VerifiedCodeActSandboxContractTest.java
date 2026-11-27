package tech.qiantong.qknow.phd;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tech.qiantong.qknow.ai.code.guard.AstSecurityInspector;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertex;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertexTier;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.BiTemporalCausalEvolutionRetriever;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.PredicateCardinality;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ReadOnlyDatasetView;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.TemporalAnchorQueryParser;
import tech.qiantong.qknow.hermes.tool.codeact.CodeActExecutionPlan;
import tech.qiantong.qknow.hermes.tool.codeact.CodeActProgrammaticBridge;
import tech.qiantong.qknow.hermes.tool.codeact.SecurityLatticeLevel;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActSandboxRuntime;
import tech.qiantong.qknow.hermes.tool.hoare.HoareToolContractGateway;
import tech.qiantong.qknow.hermes.tool.sandbox.EnterpriseCodeSandboxRuntime;
import tech.qiantong.qknow.hermes.transaction.frontier.ToolSideEffectTier;
import tech.qiantong.qknow.mcp.core.codemode.CodeModePlanAst;
import tech.qiantong.qknow.mcp.core.codemode.CodeModePlanSandboxExecutor;
import tech.qiantong.qknow.mcp.router.McpHierarchicalVoronoiRouter;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 8.2 安全格验证型 CodeAct 沙盒与主干静态 4 元工具断点修复契约测试（验证定理 8.2）。
 */
class Phase8_2VerifiedCodeActSandboxContractTest {

    private static float[] unitVec(String seed) {
        return LocalitySensitiveFeatureHasher.projectToUnitSphereFloat(seed, 1024);
    }

    @Test
    void tier0MathEvaluatorFixesUnaryMinusAndScientificNotationWithMacVerification() {
        try (VerifiedCodeActSandboxRuntime runtime = new VerifiedCodeActSandboxRuntime()) {
            VerifiedCodeActReceipt r1 = runtime.executeMath("math-neg", "-3.5 * (2 + 4)");
            assertTrue(r1.isSuccess());
            assertEquals(-21.0, Double.parseDouble(r1.stdout()), 1e-6);
            assertEquals("IN_PROCESS_MATH_DETERMINISTIC", r1.isolationAttestation().isolationLevel());
            assertEquals(SecurityLatticeLevel.PURE_ARITH, r1.isolationAttestation().effectiveLatticeLevel());
            assertTrue(r1.verifyMac("hermes-codeact-sandbox-secret"));

            VerifiedCodeActReceipt r2 = runtime.executeMath("math-sci", "1.5e-3 + 2.5e-3");
            assertTrue(r2.isSuccess());
            assertEquals(0.004, Double.parseDouble(r2.stdout()), 1e-8);

            VerifiedCodeActReceipt r3 = runtime.executeMath("math-paren-neg", "-(10 - 3) / 2");
            assertTrue(r3.isSuccess());
            assertEquals(-3.5, Double.parseDouble(r3.stdout()), 1e-6);
        }
    }

    @Test
    void astSecurityLatticeBlocksDangerousImportsMagicAttributesAndDynamicCalls() {
        try (VerifiedCodeActSandboxRuntime runtime = new VerifiedCodeActSandboxRuntime()) {
            List<String> maliciousPayloads = List.of(
                    "import os\nprint(os.listdir('/'))",
                    "x = 1; import subprocess\nprint(x)",
                    "print(().__class__.__bases__[0].__subclasses__())",
                    "eval('1 + 1')",
                    "open('/etc/passwd').read()"
            );
            for (int i = 0; i < maliciousPayloads.size(); i++) {
                VerifiedCodeActReceipt receipt = runtime.executePython(
                        "sec-block-" + i, maliciousPayloads.get(i), null, null
                );
                assertFalse(receipt.isSuccess(), "恶意载荷必须被静态安全格拦截: " + maliciousPayloads.get(i));
                assertFalse(receipt.isolationAttestation().astInspectorPassed());
                assertEquals(-403, receipt.exitCode());
                assertEquals(SecurityLatticeLevel.HOST_PRIV, receipt.isolationAttestation().effectiveLatticeLevel());
                assertTrue(receipt.verifyMac("hermes-codeact-sandbox-secret"));
            }
        }
    }

    @Test
    void tier1PythonSandboxExecutesReadOnlyDatasetSanitizesEnvKillsInfiniteLoopAndTruncatesOutput() {
        try (VerifiedCodeActSandboxRuntime runtime = new VerifiedCodeActSandboxRuntime()) {
            // 1. Default-Deny 敏感凭据清洗验证
            Map<String, String> cleanEnv = runtime.sanitizeEnvironment(Map.of(
                    "DEEPSEEK_API_KEY", "sk-secret-deepseek-999",
                    "TONGYI_API_KEY", "sk-qwen-888",
                    "AWS_SECRET_ACCESS_KEY", "aws-priv",
                    "SAFE_TAG", "hermes_v3"
            ));
            assertFalse(cleanEnv.containsKey("DEEPSEEK_API_KEY"));
            assertFalse(cleanEnv.containsKey("TONGYI_API_KEY"));
            assertFalse(cleanEnv.containsKey("AWS_SECRET_ACCESS_KEY"));
            assertEquals("hermes_v3", cleanEnv.get("SAFE_TAG"));

            // 2. 构建 Phase 8.1 只读视图并校验诚实隔离与真实聚合计算
            BiTemporalHippocampalGraphEngine engine = new BiTemporalHippocampalGraphEngine("p82-hippo");
            long baseMs = LocalDate.of(2025, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            engine.upsertVertex(new HippocampalVertex("u1", "User", HippocampalVertexTier.PHRASE_ENTITY, unitVec("User")));
            for (int i = 1; i <= 4; i++) {
                String city = "City_" + i;
                engine.upsertVertex(new HippocampalVertex(city, city, HippocampalVertexTier.PHRASE_ENTITY, unitVec(city)));
                engine.ingestCausalFactEdge("u1", city, "VISITED", PredicateCardinality.COUNTABLE_EVENT,
                        unitVec("visit " + city), baseMs + i * 86400_000L, 100L + i, "s" + i, null);
            }
            BiTemporalCausalEvolutionRetriever retriever = new BiTemporalCausalEvolutionRetriever(
                    engine, new UnitSphereEmbeddingResolver(), new TemporalAnchorQueryParser(), "p82-hippo"
            );
            CodeActProgrammaticBridge bridge = new CodeActProgrammaticBridge(
                    runtime, retriever, new HoareToolContractGateway("p82-hoare")
            );
            VerifiedCodeActReceipt calcReceipt = bridge.queryTemporalAndExecuteCode(
                    "q-cnt",
                    "From 2025-01-01 to 2025-02-01 User VISITED history",
                    baseMs + 30L * 86400_000L,
                    "print(f'count={len(DATASET_ROWS)},score_sum={sum(float(r[9]) for r in DATASET_ROWS):.2f}')"
            );
            assertTrue(calcReceipt.isSuccess(), "stderr=" + calcReceipt.stderr());
            assertTrue(calcReceipt.stdout().contains("count=4"), "只读视图 Python 精确计数应为 4: " + calcReceipt.stdout());
            assertTrue(calcReceipt.verifyMac("hermes-codeact-sandbox-secret"));

            // 诚实隔离断言：在 macOS 上绝不虚报 LINUX_NSJAIL_CGROUPS 或 readOnlyFsEnforced=true
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("mac")) {
                assertNotEquals("LINUX_NSJAIL_CGROUPS", calcReceipt.isolationAttestation().isolationLevel());
                assertFalse(calcReceipt.isolationAttestation().readOnlyFsEnforced());
            }

            // 篡改标准输出后 HMAC 验签必须失败
            VerifiedCodeActReceipt tampered = new VerifiedCodeActReceipt(
                    calcReceipt.receiptId(), calcReceipt.planId(), calcReceipt.executionTier(),
                    calcReceipt.language(), calcReceipt.scriptSha256(), calcReceipt.exitCode(),
                    "count=999", calcReceipt.stderr(), calcReceipt.stdoutSha256(), calcReceipt.stderrSha256(),
                    calcReceipt.truncated(), calcReceipt.timedOut(), calcReceipt.executionLatencyMicros(),
                    calcReceipt.isolationAttestation(), calcReceipt.timestampEpochMs(), calcReceipt.hmacSignature()
            );
            assertFalse(tampered.verifyMac("hermes-codeact-sandbox-secret"));

            // 3. 看门狗超时强杀测试
            var tightQuota = new EnterpriseCodeSandboxRuntime.SandboxResourceQuota(
                    600L, 128L * 1024 * 1024, 600_000L, 64 * 1024, true, true
            );
            long tStart = System.currentTimeMillis();
            VerifiedCodeActReceipt timeoutReceipt = runtime.executePython(
                    "loop-timeout", "while True:\n    pass", null, tightQuota
            );
            long elapsed = System.currentTimeMillis() - tStart;
            assertTrue(timeoutReceipt.timedOut(), "无限循环必须被看门狗强杀标记 timedOut=true");
            assertFalse(timeoutReceipt.isSuccess());
            assertTrue(elapsed < 4000L, "看门狗强杀应在 4s 内返回，实际耗时=" + elapsed);

            // 4. 64KB 真字节流截断防管道死锁测试
            VerifiedCodeActReceipt truncReceipt = runtime.executePython(
                    "huge-stdout", "print('A' * 100000)", null, null
            );
            assertTrue(truncReceipt.isSuccess());
            assertTrue(truncReceipt.truncated(), "超出 64KB 输出必须触发截断并排空流防死锁");
        }
    }

    @Test
    void codeActBridgeEnforcesBufferableLatticeAndCodeModeExecutorRunsRealRawScript() {
        try (VerifiedCodeActSandboxRuntime runtime = new VerifiedCodeActSandboxRuntime()) {
            BiTemporalHippocampalGraphEngine engine = new BiTemporalHippocampalGraphEngine("p82-b");
            BiTemporalCausalEvolutionRetriever retriever = new BiTemporalCausalEvolutionRetriever(
                    engine, new UnitSphereEmbeddingResolver(), new TemporalAnchorQueryParser(), "p82-b"
            );
            CodeActProgrammaticBridge bridge = new CodeActProgrammaticBridge(
                    runtime, retriever, new HoareToolContractGateway("p82-b")
            );

            // 非 BUFFERABLE 工具必须抛 SecurityException 拦截
            assertThrows(SecurityException.class, () -> bridge.invokeReadOnlyToolSafely(
                    "req-risk", "delete_records", ToolSideEffectTier.IRREVERSIBLE, Map.of(), args -> "NOPE"
            ));
            assertEquals("OK_READ", bridge.invokeReadOnlyToolSafely(
                    "req-ok", "read_table", ToolSideEffectTier.BUFFERABLE, Map.of(), args -> "OK_READ"
            ));

            // CodeModePlanSandboxExecutor 注入真实回调后 RawScriptStep 真实执行并返回计算结果
            CodeModePlanSandboxExecutor executor = new CodeModePlanSandboxExecutor(
                    new McpHierarchicalVoronoiRouter("t-codeact"),
                    new AstSecurityInspector()::inspect,
                    runtime.toRawScriptStepCallback()
            );
            var ast = new CodeModePlanAst.SequenceBlock(
                    List.of(new CodeModePlanAst.RawScriptStep("s1", "python", "print(17 * 23)")),
                    "s1"
            );
            var execRes = executor.executePlan(ast);
            assertTrue(execRes.success());
            assertEquals(1, execRes.executedToolCalls());
            @SuppressWarnings("unchecked")
            Map<String, Object> stepOut = (Map<String, Object>) execRes.finalOutput();
            assertEquals(Boolean.TRUE, stepOut.get("success"));
            assertTrue(String.valueOf(stepOut.get("stdout")).contains("391"));
        }
    }

    @Test
    void agentOrchestratorStaticFourMetaToolsDispatchToRealEnginesWithoutMismatch() {
        AgentOrchestrator orchestrator = new AgentOrchestrator(null, null, null);
        BiTemporalHippocampalGraphEngine hippo = new BiTemporalHippocampalGraphEngine("p82-orch");
        hippo.upsertVertex(new HippocampalVertex("v_corp", "Acme AI", HippocampalVertexTier.PHRASE_ENTITY, unitVec("Acme AI")));
        hippo.upsertVertex(new HippocampalVertex("v_loc", "Shanghai", HippocampalVertexTier.PHRASE_ENTITY, unitVec("Shanghai")));
        long t0 = LocalDate.of(2025, 3, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        hippo.ingestCausalFactEdge("v_corp", "v_loc", "LOCATED_IN", PredicateCardinality.FUNCTIONAL,
                unitVec("Acme AI located in Shanghai"), t0, 100L, "doc1", null);

        orchestrator.setBiTemporalHippocampalGraphEngine(hippo);
        orchestrator.setSupernetControlTakeoverEnabled(true);
        var voronoi = new McpHierarchicalVoronoiRouter("t-orch");
        try (VerifiedCodeActSandboxRuntime runtime = new VerifiedCodeActSandboxRuntime()) {
            orchestrator.setVerifiedCodeActSandboxRuntime(runtime);
            orchestrator.setCodeModePlanSandboxExecutor(new CodeModePlanSandboxExecutor(
                    voronoi, new AstSecurityInspector()::inspect, runtime.toRawScriptStepCallback()
            ));

            ToolCallback sampleTool = new ToolCallback() {
                private final ToolDefinition def = ToolDefinition.builder()
                        .name("table_lookup")
                        .description("读取业务表元信息")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"table\":{\"type\":\"string\"}}}")
                        .build();

                @Override
                public ToolDefinition getToolDefinition() {
                    return def;
                }

                @Override
                public String call(String toolInput) {
                    return "ok";
                }
            };

            List<ToolCallback> metaTools = orchestrator.previewLockedStaticMetaToolCallbacks("req-meta-82", List.of(sampleTool));
            assertEquals(CodeModePlanSandboxExecutor.STATIC_META_TOOL_IDS.size(), metaTools.size());

            // 1. mcp_load_tool_schema 真实展开延迟工具 Schema
            String schemaOut = metaTools.get(1).call("{\"toolName\":\"table_lookup\"}");
            JSONObject schemaJson = JSON.parseObject(schemaOut);
            assertEquals(Boolean.TRUE, schemaJson.getBoolean("found"));
            assertEquals("table_lookup", schemaJson.getString("name"));

            // 2. mcp_exec_code_plan 真实执行 Tier-1 Python 并记录 VerifiedCodeActReceipt
            String codeOut = metaTools.get(2).call("{\"language\":\"python\",\"code\":\"print(6 * 7)\"}");
            JSONObject codeJson = JSON.parseObject(codeOut);
            assertEquals(Boolean.TRUE, codeJson.getBoolean("success"));
            assertTrue(codeJson.getString("stdout").contains("42"));
            assertEquals(1, orchestrator.getLatestCodeActReceipts("req-meta-82").size());

            // 3. hippocampal_subgraph_probe 真实调用 Phase 8.1 双时态因果演化检索
            String hippoOut = metaTools.get(3).call("{\"query\":\"Acme AI located Shanghai 2025\"}");
            JSONObject hippoJson = JSON.parseObject(hippoOut);
            assertNotNull(hippoJson.getString("receiptId"));
            assertFalse(hippoJson.getJSONArray("causalSteps").isEmpty(), "必须返回真实因果演化事实链");
        }
    }
}
