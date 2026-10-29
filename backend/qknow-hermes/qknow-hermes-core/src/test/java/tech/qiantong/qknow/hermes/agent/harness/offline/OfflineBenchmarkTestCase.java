package tech.qiantong.qknow.hermes.agent.harness.offline;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 离线小样本基准任务用例契约（不可变 Java 21 Record）。
 *
 * <p>用于在离线受控实验中评估 Harness 候选变异，杜绝业务数据泄漏与在线生产影响。</p>
 */
public record OfflineBenchmarkTestCase(
        String taskId,
        TaskCategory category,
        String question,
        Map<String, Object> inputParams,
        List<String> availableTools,
        String expectedToolName,
        int minContextTokensThreshold
) {
    public enum TaskCategory {
        KNOWLEDGE_QA,          // 知识库精确语义检索与实体抽取
        MULTI_TOOL_SELECTION,  // 多工具依赖推理与选择
        TOOL_PARAM_GUARD,      // 工具参数纠错与异常容错
        CONTEXT_HISTORY_RECALL,// 复杂多轮历史上下文理解与代词消解
        COMPOSITE_REASONING    // 结构化输出与前缀缓存敏感型长回答
    }

    public OfflineBenchmarkTestCase {
        taskId = Objects.requireNonNull(taskId, "taskId 不能为空");
        category = Objects.requireNonNull(category, "category 不能为空");
        question = Objects.requireNonNull(question, "question 不能为空");
        inputParams = inputParams == null ? Map.of() : Map.copyOf(inputParams);
        availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
    }

    /**
     * 创建官方冻结的 5 个代表性离线小样本基准任务用例集。
     */
    public static List<OfflineBenchmarkTestCase> createDefaultSuite() {
        return List.of(
                new OfflineBenchmarkTestCase(
                        "kb-qa-01",
                        TaskCategory.KNOWLEDGE_QA,
                        "请根据企业规范，查询订单支付系统的超时重试次数与幂等键生成规则。",
                        Map.of("domain", "middleware", "strict", true),
                        List.of("knowledgeBase_MiddleWare_Spec"),
                        "knowledgeBase_MiddleWare_Spec",
                        120
                ),
                new OfflineBenchmarkTestCase(
                        "tool-route-02",
                        TaskCategory.MULTI_TOOL_SELECTION,
                        "客户反映交易扣款成功但未生成凭单，请核查流水状态并查询结算账户余额。",
                        Map.of("txId", "TX-20261029-8891"),
                        List.of("banking_transaction_query", "banking_account_balance", "banking_settlement_remedy"),
                        "banking_transaction_query",
                        150
                ),
                new OfflineBenchmarkTestCase(
                        "param-guard-03",
                        TaskCategory.TOOL_PARAM_GUARD,
                        "输入非标准日期格式参数，验证工具参数前置约束是否引导自纠降级。",
                        Map.of("rawDate", "2026/10/29 25:99:99"),
                        List.of("calendar_event_creator"),
                        "calendar_event_creator",
                        100
                ),
                new OfflineBenchmarkTestCase(
                        "ctx-history-04",
                        TaskCategory.CONTEXT_HISTORY_RECALL,
                        "针对前文提到的方案 A 的最担忧瓶颈，业界在网络数据面上有何绕过优化方案？",
                        Map.of("referentTarget", "service_mesh_latency", "historyTurns", 2),
                        List.of(),
                        "",
                        200
                ),
                new OfflineBenchmarkTestCase(
                        "comp-rag-tool-05",
                        TaskCategory.COMPOSITE_REASONING,
                        "生成一份微服务架构演进评估报告，包含 4 列标准 Markdown 对比矩阵与 200 字总结。",
                        Map.of("format", "markdown_table", "columns", 4),
                        List.of("knowledgeBase_Arch_Spec"),
                        "knowledgeBase_Arch_Spec",
                        300
                )
        );
    }
}
