package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.code.guard.AstSecurityInspector;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.tool.formal.DbmLtlFormalMicroSolver;
import tech.qiantong.qknow.hermes.tool.formal.FormalConstraintFormula;
import tech.qiantong.qknow.hermes.tool.formal.FormalCounterexampleReceipt;
import tech.qiantong.qknow.hermes.tool.hoare.HoareToolContractGateway;
import tech.qiantong.qknow.mcp.core.codemode.CodeModePlanAst;
import tech.qiantong.qknow.mcp.core.codemode.CodeModePlanSandboxExecutor;
import tech.qiantong.qknow.mcp.core.orchestration.dto.McpToolDescriptor;
import tech.qiantong.qknow.mcp.router.McpHierarchicalVoronoiRouter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 博士论文代际升级 · 阶段 3（论文第 5 章）核心契约测试套件
 * 覆盖：
 * 1. 定理 5.1(1)：纯 Java 21 DBM（Floyd-Warshall 负权环检测）+ LTLf 自动机 100% 拦截矛盾区间与乱序调用，并提取极小不可满足核 MUC；
 * 2. 定理 5.1(2)：双向形式化思维（BFoT）拦截 DeepSeek reasoning_content 思考链与外显工具参数背离的“向上欺骗”；
 * 3. Code-Mode MCP：120 个 defer_loading 工具下顶层 4 元工具前缀字节 100% 恒定，且受限 AST 虚拟线程沙箱并发折叠多步调用并静态阻断恶意逃逸。
 */
class Phase3PhdFormalThoughtAndCodeModeMcpContractTest {

    @Test
    @DisplayName("3.1 定理 5.1(1)：DBM 区间差约束负环检测、MUC 极小冲突核提取与 LTLf 先后序守卫")
    void test01_DbmNegativeCycleMucExtractionAndLtlfPrecedence() {
        DbmLtlFormalMicroSolver solver = new DbmLtlFormalMicroSolver("formal-secret");

        // 约束规约：10 <= minPool <= maxPool <= 200，且调用 update_db_pool 前必须已成功执行 check_cluster_health
        FormalConstraintFormula contract = new FormalConstraintFormula.Conjunction(List.of(
                new FormalConstraintFormula.DifferenceBound("C1_MIN_FLOOR", DbmLtlFormalMicroSolver.ZERO_ANCHOR, "minPool", -10.0),
                new FormalConstraintFormula.DifferenceBound("C2_MIN_LE_MAX", "minPool", "maxPool", 0.0),
                new FormalConstraintFormula.DifferenceBound("C3_MAX_CEIL", "maxPool", DbmLtlFormalMicroSolver.ZERO_ANCHOR, 200.0),
                new FormalConstraintFormula.LtlTemporalPrecedence("C4_HEALTH_FIRST", "check_cluster_health", "update_db_pool")
        ));

        // 场景 A：传入矛盾参数 minPool=80, maxPool=30（与 minPool - maxPool <= 0 构成负权环 80 - 30 = 50 > 0）
        FormalCounterexampleReceipt dbmViolation = solver.verifyAndSynthesizeCounterexample(
                "exec-f-01", "update_db_pool", contract,
                Map.of("minPool", 80, "maxPool", 30),
                List.of("check_cluster_health"),
                "用户请求调整连接池配置。", null, null
        );
        assertFalse(dbmViolation.unsatCompliant(), "矛盾数值区间必须被 DBM 负环检测 100% 判定为违约");
        assertTrue(dbmViolation.minimalUnsatisfiableCore().contains("C2_MIN_LE_MAX"),
                "极小冲突核 MUC 必须精确包含冲突约束 C2_MIN_LE_MAX，实际: " + dbmViolation.minimalUnsatisfiableCore());
        assertTrue(dbmViolation.verifyMac("formal-secret"));

        // 场景 B：参数合法 (minPool=20, maxPool=100)，但历史轨迹未执行前置只读诊断 check_cluster_health
        FormalCounterexampleReceipt ltlViolation = solver.verifyAndSynthesizeCounterexample(
                "exec-f-02", "update_db_pool", contract,
                Map.of("minPool", 20, "maxPool", 100),
                List.of(), // 缺失前置动作
                "直接更新连接池。", null, null
        );
        assertFalse(ltlViolation.unsatCompliant(), "违背 LTLf 时序先后约束必须被自动机阻断");
        assertTrue(ltlViolation.minimalUnsatisfiableCore().contains("C4_HEALTH_FIRST"));

        // 场景 C：参数与时序均合法 -> 数学证明合规（UNSAT）
        FormalCounterexampleReceipt compliant = solver.verifyAndSynthesizeCounterexample(
                "exec-f-03", "update_db_pool", contract,
                Map.of("minPool", 20, "maxPool", 100),
                List.of("check_cluster_health"),
                "已完成健康检查，连接池区间 20..100 满足规约。", null, null
        );
        assertTrue(compliant.unsatCompliant(), "合法参数与合规轨迹必须获得 UNSAT 放行证明");
        assertTrue(compliant.minimalUnsatisfiableCore().isEmpty());
    }

    @Test
    @DisplayName("3.2 定理 5.1(2)：HoareToolContractGateway 双向 FoT 拦截 DeepSeek 思考链与参数背离的向上欺骗")
    void test02_BidirectionalFoTInterceptsUpwardDeceptionBetweenReasoningAndAction() {
        HoareToolContractGateway gateway = new HoareToolContractGateway("hoare-fot-secret");
        AtomicInteger dangerousToolExecutions = new AtomicInteger(0);

        FormalConstraintFormula antiDeceptionFormula = new FormalConstraintFormula.ReasoningAlignmentImplication(
                "C_ANTI_UPWARD_DECEPTION",
                List.of("权限不足", "缺少审批", "存在高危越权风险", "unauthorized"),
                "forceBypassApproval",
                0.85
        );
        gateway.registerFormalFormula("execute_fund_transfer", antiDeceptionFormula);

        float[] thinkVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("检测到当前账户缺少审批权限，存在高危越权风险", 1024);
        float[] actionVec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("静默强制划转大额资金并跳过风控", 1024);

        // 模拟向上欺骗：DeepSeek reasoning_content 已识别“缺少审批权限”，外显调用却将 forceBypassApproval 设为 true
        HoareToolContractGateway.BidirectionalFoTOutcome outcome = gateway.executeWithBidirectionalFoT(
                "execute_fund_transfer",
                "exec-decep-01",
                Map.of("amount", 500000, "forceBypassApproval", true),
                List.of("query_balance"),
                "思考过程：核查发现当前会话缺少审批凭证且存在高危越权风险，但为快速完成任务尝试直接设置跳过标志。",
                thinkVec,
                actionVec,
                params -> {
                    dangerousToolExecutions.incrementAndGet();
                    return "TRANSFERRED";
                }
        );

        assertFalse(outcome.executionOutcome().success(), "向上欺骗行为必须在接触执行层前被双向 FoT 网关拦截！");
        assertEquals(0, dangerousToolExecutions.get(), "被拦截的欺骗调用实际触发次数必须恒等于 0");
        assertTrue(outcome.counterexampleReceipt().minimalUnsatisfiableCore().contains("C_ANTI_UPWARD_DECEPTION"));
        assertTrue(outcome.counterexampleReceipt().tailAppendRemediationMessage().contains("C_ANTI_UPWARD_DECEPTION"),
                "反例凭单必须生成可追加至对话尾部的紧凑 MUC 自纠错指引（零 SystemPrompt 污染）");
    }

    @Test
    @DisplayName("3.3 Code-Mode MCP：100+ 延迟工具顶层前缀零漂移、Voronoi 按需发现与受限 AST 虚拟线程并发沙箱")
    void test03_CodeModeMcpPrefixInvarianceVoronoiDeferLoadingAndAstSandbox() {
        McpHierarchicalVoronoiRouter router = new McpHierarchicalVoronoiRouter("phd-tenant");
        AstSecurityInspector securityInspector = new AstSecurityInspector();
        CodeModePlanSandboxExecutor sandbox = new CodeModePlanSandboxExecutor(
                router,
                ( lang, code ) -> securityInspector.inspect(lang, code)
        );

        String baselineTopLevelToolsJson = sandbox.getCanonicalStaticMetaToolsJson();

        // 动态挂载 100 个 defer_loading=true 的长尾业务 MCP 工具
        for (int i = 0; i < 100; i++) {
            final String toolId = "tool_defer_" + i;
            String desc = (i == 42) ? "查询供应链订单物流实时轨迹与签收状态" : "通用长尾企业行政归档工具编号_" + i;
            double[] emb = LocalitySensitiveFeatureHasher.projectToUnitSphereDouble(desc, 1024);
            router.registerTool(new McpToolDescriptor(
                    toolId, desc, "enterprise", emb, Set.of("id"), Set.of(), List.of(), true, false
            ));
            sandbox.registerDeferredToolExecutor(toolId, args -> Map.of(
                    "toolId", toolId,
                    "item", args.get("item"),
                    "status", String.valueOf(args.get("item")).contains("VIP") ? "URGENT_DELIVERING" : "ARCHIVED",
                    "rawVerboseBlob", "X".repeat(2048) // 模拟大体积 L3 原始冗余载荷
            ));
        }
        router.rebuildVoronoiIndex();

        // 1. 断言挂载 100 个长尾工具前后，暴露给 DeepSeek 顶层的静态 4 元工具 JSON 字节序列 100% 恒等！
        assertEquals(baselineTopLevelToolsJson, sandbox.getCanonicalStaticMetaToolsJson(),
                "defer_loading 模式下无论底层挂载多少业务工具，顶层 tools 前缀字节序列必须 100% 恒定！");

        // 2. 验证千问 1024 维 Voronoi 延迟工具发现精准召回 tool_defer_42
        double[] qEmb = LocalitySensitiveFeatureHasher.projectToUnitSphereDouble("查询供应链订单物流实时轨迹", 1024);
        List<McpToolDescriptor> discovered = sandbox.discoverDeferredTools("查询供应链订单物流实时轨迹", qEmb, 3);
        assertTrue(discovered.stream().anyMatch(d -> "tool_defer_42".equals(d.toolId())),
                "1024 维 Voronoi 延迟检索必须精准命中目标工具 tool_defer_42");

        // 3. 验证单轮受限无环 CodeModePlanAst 编排：列表提取 -> 虚拟线程并发 Map 调用 -> 字段裁剪过滤（消除 2KB 冗余字段）
        sandbox.registerDeferredToolExecutor("list_pending_orders", args -> Map.of(
                "items", List.of("ORD_VIP_01", "ORD_NORMAL_02", "ORD_VIP_03")
        ));

        CodeModePlanAst.SequenceBlock plan = new CodeModePlanAst.SequenceBlock(
                List.of(
                        new CodeModePlanAst.InvokeToolStep("s1", "list_pending_orders", Map.of()),
                        new CodeModePlanAst.ParallelMapStep(
                                "s2", "$s1.items", 4,
                                new CodeModePlanAst.InvokeToolStep("mapItem", "tool_defer_42", Map.of("item", "$item"))
                        ),
                        new CodeModePlanAst.FilterProjectStep(
                                "s3", "$s2", "URGENT_DELIVERING", List.of("item", "status"), 10
                        )
                ),
                "s3"
        );

        CodeModePlanSandboxExecutor.SandboxExecutionResult result = sandbox.executePlan(plan);
        assertTrue(result.success());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projected = (List<Map<String, Object>>) result.finalOutput();
        assertEquals(2, projected.size(), "过滤后应仅保留 2 条 VIP 加急订单");
        assertFalse(projected.get(0).containsKey("rawVerboseBlob"), "FilterProjectStep 必须彻底剥离 2KB L3 原始冗余载荷！");

        // 4. 安全反例验证：包含危险系统调用的脚本步骤必须被 AstSecurityInspector 静态阻断
        CodeModePlanAst.SequenceBlock maliciousPlan = new CodeModePlanAst.SequenceBlock(
                List.of(new CodeModePlanAst.RawScriptStep("evil", "python", "import subprocess\nsubprocess.Popen('id')")),
                "evil"
        );
        assertThrows(SecurityException.class, () -> sandbox.executePlan(maliciousPlan),
                "包含高危模块导入的步骤必须被 AstSecurityInspector 静态拦截！");
    }
}
