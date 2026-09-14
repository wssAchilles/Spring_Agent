package tech.qiantong.qknow.ai.pipeline.stage;

import tech.qiantong.qknow.ai.pipeline.context.AiPipelineContext;

/**
 * 管道阶段处理器契约接口 (PipelineStageHandler)
 *
 * 每个阶段处理器独立承担一个横切切面的业务治理逻辑，由 Spring 容器管理，支持自适应动态激活与超时控制。
 *
 * @author qknow
 */
public interface PipelineStageHandler {

    /**
     * 获取当前处理器负责的管道阶段
     *
     * @return 管道阶段枚举
     */
    PipelineStage getStage();

    /**
     * 动态条件激活判定: 决定当前阶段在给定上下文下是否应当激活执行
     *
     * @param context 全生命周期上下文
     * @return true-激活执行; false-动态短路跳过
     */
    default boolean shouldActivate(AiPipelineContext context) {
        return true;
    }

    /**
     * 获取当前阶段的超时配额上限 (毫秒)
     * 默认 3000ms，耗时阶段可覆盖配置
     *
     * @param context 全生命周期上下文
     * @return 超时毫秒数
     */
    default long getStageTimeoutMs(AiPipelineContext context) {
        return 3000L;
    }

    /**
     * 阶段核心处理逻辑
     *
     * @param context 全生命周期上下文
     * @throws Exception 阶段处理异常
     */
    void handle(AiPipelineContext context) throws Exception;
}
