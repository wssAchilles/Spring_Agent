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

/**
 * 阶段 1 处理器: 输入侧合规安全门禁 (PII 脱敏与对抗注入拦截)
 *
 * 核心安全阶段，采用 Fail-Close 策略，一旦检测到注入违规直接抛出异常中断。
 *
 * @author qknow
 */
@Slf4j
@Component
public class InputGuardrailStageHandler implements PipelineStageHandler {

    private final GuardrailPolicyCoordinator guardrailCoordinator;

    @Autowired
    public InputGuardrailStageHandler(GuardrailPolicyCoordinator guardrailCoordinator) {
        this.guardrailCoordinator = guardrailCoordinator;
    }

    @Override
    public PipelineStage getStage() {
        return PipelineStage.INPUT_GUARDRAIL;
    }

    @Override
    public boolean shouldActivate(AiPipelineContext context) {
        return true;
    }

    @Override
    public long getStageTimeoutMs(AiPipelineContext context) {
        return 1000L;
    }

    @Override
    public void handle(AiPipelineContext context) throws Exception {
        String rawPrompt = context.getRawPrompt();
        if (rawPrompt == null || rawPrompt.isBlank()) {
            return;
        }

        // 调用 GuardrailPolicyCoordinator 协调输入侧审查
        GuardrailDecision decision = guardrailCoordinator.coordinateInput(rawPrompt);
        context.setInputGuardrailDecision(decision);

        // 如果未通过审查 (!permitted)，执行 Fail-Close 阻断抛出异常
        if (!decision.permitted()) {
            log.warn("[InputGuardrail] 请求触发安全阻断: violation={}, reason={}, tenantId={}",
                    decision.violationType(), decision.reason(), context.getTenantId());
            throw new SecurityException("输入触发合规安全阻断: " + decision.violationType() + ", " + decision.reason());
        }

        // 绑定已脱敏处理的 Prompt
        String sanitized = decision.processedText() != null ? decision.processedText() : rawPrompt;
        context.updateSanitizedPrompt(sanitized, Collections.emptyMap());
    }
}
