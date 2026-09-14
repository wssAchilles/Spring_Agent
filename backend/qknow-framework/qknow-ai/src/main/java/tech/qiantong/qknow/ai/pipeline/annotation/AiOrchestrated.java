package tech.qiantong.qknow.ai.pipeline.annotation;

import java.lang.annotation.*;

/**
 * 声明式 AI 全链路编排切面注解 (@AiOrchestrated)
 *
 * 标注在 Spring 管理的 Service、Agent 或 Controller 方法上，实现零侵入全套治理能力装配。
 *
 * @author qknow
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiOrchestrated {

    /**
     * 编排画像配置名称 (例如: ONLINE_INTERACTIVE, OFFLINE_BATCH, STRICT_AUDIT)
     */
    String profile() default "DEFAULT";

    /**
     * 全局超时时间上限 (毫秒)，默认 30 秒
     */
    long timeoutMs() default 30000L;

    /**
     * 是否开启多智能体 PBFT 共识 (默认 false，引擎会结合 Prompt 语义自适应判定)
     */
    boolean enableConsensus() default false;

    /**
     * 是否开启 Merkle 证据链存证
     */
    boolean enableMerkleAudit() default true;

    /**
     * 是否开启因果拓扑图溯源
     */
    boolean enableCausalGraph() default true;
}
