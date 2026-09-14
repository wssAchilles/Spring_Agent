package tech.qiantong.qknow.ai.pipeline.stage.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.guardrail.GuardrailPolicyCoordinator;
import tech.qiantong.qknow.ai.guardrail.model.GuardrailDecision;
import tech.qiantong.qknow.ai.pipeline.context.AiPipelineContext;
import tech.qiantong.qknow.ai.pipeline.stage.PipelineStage;
import tech.qiantong.qknow.ai.pipeline.stage.PipelineStageHandler;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 阶段 5 处理器: 输出侧合规门禁 (红线词阻断与幽灵引用自愈)
 *
 * 核心安全阶段: 输出涉暴/政治红线词时必须 Fail-Close 抛出异常阻断；幽灵引用越界时自动清洗重写。
 *
 * @author qknow
 */
@Slf4j
@Component
public class OutputGuardrailStageHandler implements PipelineStageHandler {

    private final GuardrailPolicyCoordinator guardrailCoordinator;

    @Autowired
    public OutputGuardrailStageHandler(GuardrailPolicyCoordinator guardrailCoordinator) {
        this.guardrailCoordinator = guardrailCoordinator;
    }

    @Override
    public PipelineStage getStage() {
        return PipelineStage.OUTPUT_GUARDRAIL;
    }

    @Override
    public boolean shouldActivate(AiPipelineContext context) {
        return context.getRawOutput() != null && !context.getRawOutput().isBlank();
    }

    @Override
    public long getStageTimeoutMs(AiPipelineContext context) {
        return 1000L;
    }

    @Override
    public void handle(AiPipelineContext context) throws Exception {
        String rawOutput = context.getRawOutput();

        Set<String> validChunkIds = context.getAttribute("pipeline.validChunkIds");
        if (validChunkIds == null) {
            validChunkIds = Collections.emptySet();
        }

        List<String> referenceContexts = context.getAttribute("pipeline.referenceContexts");
        if (referenceContexts == null) {
            referenceContexts = Collections.emptyList();
        }

        // 协调输出侧护栏核验
        GuardrailDecision decision = guardrailCoordinator.coordinateOutput(
                rawOutput,
                validChunkIds,
                referenceContexts
        );

        if (!decision.permitted()) {
            log.warn("[OutputGuardrail] 输出触发红线阻断: violation={}, reason={}",
                    decision.violationType(), decision.reason());
            throw new SecurityException("输出触发合规安全红线阻断: " + decision.violationType() + ", " + decision.reason());
        }

        String finalSafe = decision.processedText() != null ? decision.processedText() : rawOutput;
        context.setOutputGuardrailResult(decision, finalSafe);
        context.setExecutionResult(finalSafe);
    }
}
