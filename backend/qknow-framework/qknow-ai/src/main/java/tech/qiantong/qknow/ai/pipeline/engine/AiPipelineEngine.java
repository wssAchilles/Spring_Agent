package tech.qiantong.qknow.ai.pipeline.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.pipeline.context.AiPipelineContext;
import tech.qiantong.qknow.ai.pipeline.stage.ModelExecutionCallback;
import tech.qiantong.qknow.ai.pipeline.stage.PipelineStage;
import tech.qiantong.qknow.ai.pipeline.stage.PipelineStageHandler;

import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * 阶段化 AI 编排管道调度引擎 (AiPipelineEngine)
 *
 * 依据拓扑偏序严格按 Order 推进 7 大阶段，支持阶段动态短路跳过、
 * 阶段超时预算防护与双轨容灾门禁 (Fail-Close / Fail-Open)。
 *
 * @author qknow
 */
@Slf4j
@Component
public class AiPipelineEngine {

    private final List<PipelineStageHandler> sortedHandlers = new ArrayList<>();

    @Autowired
    public AiPipelineEngine(List<PipelineStageHandler> handlers) {
        if (handlers != null) {
            this.sortedHandlers.addAll(handlers);
            this.sortedHandlers.sort(Comparator.comparingInt(h -> h.getStage().getOrder()));
        }
    }

    /**
     * 驱动全流程编排管道执行
     *
     * @param context 全生命周期上下文
     * @param modelCallback 模型执行回调 (承接 proceed 或网关代理)
     * @throws Exception 业务阻断或核心故障异常
     */
    public void executePipeline(AiPipelineContext context, ModelExecutionCallback modelCallback) throws Exception {
        boolean modelExecuted = false;

        // 遍历所有已注册的阶段处理器
        for (PipelineStageHandler handler : sortedHandlers) {
            // 1. 全局截止时间 Deadline 检查
            if (context.isExpired()) {
                log.warn("[PipelineEngine] 请求已超时熔断: traceId={}, remainingMs={}",
                        context.getTraceId(), context.getRemainingTimeMs());
                throw new TimeoutException("全局执行时间已耗尽 (Deadline Expired)");
            }

            // 2. 检查是否应当在当前处理器前执行 MODEL_EXECUTION (Order 400)
            if (!modelExecuted && handler.getStage().getOrder() > PipelineStage.MODEL_EXECUTION.getOrder()) {
                executeModelStage(context, modelCallback);
                modelExecuted = true;
            }

            PipelineStage stage = handler.getStage();

            // 3. 动态条件激活判定
            if (!handler.shouldActivate(context)) {
                log.debug("[PipelineEngine] 阶段自适应跳过: stage={}", stage);
                context.setAttribute(stage.name() + ".skipped", true);
                continue;
            }

            // 4. 执行阶段处理器并实施容灾门禁
            try {
                long stageStart = System.currentTimeMillis();
                handler.handle(context);
                long cost = System.currentTimeMillis() - stageStart;
                log.debug("[PipelineEngine] 阶段执行成功: stage={}, cost={}ms", stage, cost);
            } catch (Exception ex) {
                if (stage.isFailClose()) {
                    log.error("[PipelineEngine] 核心安全阶段发生异常，执行 Fail-Close 阻断: stage={}, error={}",
                            stage, ex.getMessage());
                    throw ex;
                } else {
                    log.warn("[PipelineEngine] 非核心阶段发生异常，执行 Fail-Open 降级: stage={}, error={}",
                            stage, ex.getMessage());
                    context.setAttribute(stage.name() + ".degraded", true);
                }
            }
        }

        // 若全部处理器执行完毕后 modelCallback 仍未执行，在此执行
        if (!modelExecuted) {
            executeModelStage(context, modelCallback);
        }
    }

    private void executeModelStage(AiPipelineContext context, ModelExecutionCallback callback) throws Exception {
        if (context.isExpired()) {
            throw new TimeoutException("模型执行前检测到超时熔断 (Deadline Expired)");
        }

        log.debug("[PipelineEngine] 执行核心模型调用阶段 (MODEL_EXECUTION)");
        try {
            Object result = callback.call();
            context.setExecutionResult(result);
        } catch (Exception e) {
            log.error("[PipelineEngine] 核心模型调用阶段发生异常: {}", e.getMessage());
            throw e;
        } catch (Throwable t) {
            log.error("[PipelineEngine] 核心模型调用发生未受检严重错误: {}", t.getMessage());
            throw new RuntimeException(t);
        }
    }
}
