package tech.qiantong.qknow.ai.pipeline.stage;

/**
 * AI 编排管道阶段枚举 (PipelineStage)
 *
 * 严格定义 7 大核心生命周期阶段及其默认容灾行为与拓扑偏序。
 * 遵循 Java 21 规范，所有中文注释。
 *
 * @author qknow
 */
public enum PipelineStage {

    /**
     * 阶段 1: 输入侧合规安全门禁 (PII 脱敏、多语言越狱注入探测)
     * 容灾策略: 核心安全阶段，必须 Fail-Close (违规直接抛出合规阻断异常)
     */
    INPUT_GUARDRAIL(100, true),

    /**
     * 阶段 2: SLA 延迟与成本感知路由选路
     * 容灾策略: 路由失败时兜底至默认主渠道 (Fail-Open)
     */
    SLA_ROUTING(200, false),

    /**
     * 阶段 3: 多智能体共识动态激活与仲裁
     * 容灾策略: 简单问答条件跳过；共识超时降级为单智能体提案
     */
    CONSENSUS_ACTIVATION(300, false),

    /**
     * 阶段 4: 模型生成与核心业务逻辑代理执行 (承接底层 proceed 或 ModelGateway)
     * 容灾策略: 业务核心阶段，受熔断器与重试器保护 (Fail-Close)
     */
    MODEL_EXECUTION(400, true),

    /**
     * 阶段 5: 输出侧合规门禁 (红线敏感词阻断、幽灵引用查杀自愈、事实忠实度对齐)
     * 容灾策略: 核心安全阶段，红线词 Fail-Close；幽灵引用自愈重写
     */
    OUTPUT_GUARDRAIL(500, true),

    /**
     * 阶段 6: 密码学不可篡改 Merkle 证据链存证
     * 容灾策略: 旁路审计阶段，发生异常记录报警日志并 Fail-Open 降级放行
     */
    MERKLE_AUDIT(600, false),

    /**
     * 阶段 7: 全链路因果可解释性拓扑溯源图构建
     * 容灾策略: 旁路观测阶段，发生异常记录报警日志并 Fail-Open 降级放行
     */
    CAUSAL_GRAPH(700, false);

    private final int order;
    private final boolean failClose;

    PipelineStage(int order, boolean failClose) {
        this.order = order;
        this.failClose = failClose;
    }

    public int getOrder() {
        return order;
    }

    public boolean isFailClose() {
        return failClose;
    }
}
