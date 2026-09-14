package tech.qiantong.qknow.ai.pipeline.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.pipeline.annotation.AiOrchestrated;
import tech.qiantong.qknow.ai.pipeline.context.AiPipelineContext;
import tech.qiantong.qknow.ai.pipeline.context.AiPipelineContextHolder;
import tech.qiantong.qknow.ai.pipeline.engine.AiPipelineEngine;

/**
 * 全局零侵入 AI 编排 AOP 核心切面 (GlobalAiOrchestrationAspect)
 *
 * 负责无感拦截标注了 @AiOrchestrated 的方法，自动装配生命周期上下文、重入防卫并驱动管道引擎。
 * 最高优先级 (@Order(1)) 确保在安全拦截与业务事务之前执行。
 *
 * @author qknow
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class GlobalAiOrchestrationAspect {

    private final AiPipelineEngine pipelineEngine;

    @Autowired
    public GlobalAiOrchestrationAspect(AiPipelineEngine pipelineEngine) {
        this.pipelineEngine = pipelineEngine;
    }

    @Around("@annotation(aiOrchestrated)")
    public Object orchestrateMethod(ProceedingJoinPoint joinPoint, AiOrchestrated aiOrchestrated) throws Throwable {
        AiPipelineContext currentContext = AiPipelineContextHolder.get();

        // 1. 防递归自拦截与重入死锁防卫:
        // 若当前线程已存在上下文且调用深度 > 0，说明是管道内部（如自纠错、内部模型调用）触发的二次调用，
        // 此时绝不重复启动全量编排管道，直接透传 proceed() 执行底层方法，杜绝 StackOverflowError。
        if (currentContext != null && currentContext.getReentrancyDepth().get() > 0) {
            log.debug("[AiAspect] 检测到管道内部重入调用 (depth={}), 直接放行 proceed()",
                    currentContext.getReentrancyDepth().get());
            return joinPoint.proceed();
        }

        // 2. 提取入参中的原始 Prompt 与租户信息
        String rawPrompt = extractPrompt(joinPoint, aiOrchestrated);
        String tenantId = extractTenantId(joinPoint, aiOrchestrated);

        // 3. 构建新的全生命周期上下文
        AiPipelineContext context = AiPipelineContext.builder()
                .tenantId(tenantId)
                .profile(aiOrchestrated.profile())
                .rawPrompt(rawPrompt)
                .globalTimeoutMs(aiOrchestrated.timeoutMs())
                .build();

        // 4. 将注解配置的开关下发至上下文扩展属性
        context.setAttribute("config.enableConsensus", aiOrchestrated.enableConsensus());
        context.setAttribute("config.enableMerkleAudit", aiOrchestrated.enableMerkleAudit());
        context.setAttribute("config.enableCausalGraph", aiOrchestrated.enableCausalGraph());

        // 5. 绑定 AutoCloseable 语法糖作用域，绝对确保在 finally 中显式清除 ThreadLocal
        try (var scope = AiPipelineContextHolder.open(context)) {
            // 标记进入深度
            context.getReentrancyDepth().incrementAndGet();

            // 驱动全阶段编排管道引擎执行，将 joinPoint.proceed() 封装为模型执行回调
            pipelineEngine.executePipeline(context, () -> {
                try {
                    // 若前置阶段对 Prompt 进行了 PII 脱敏或合规改写，动态替换方法入参
                    Object[] modifiedArgs = maybeRewriteArgs(joinPoint.getArgs(), context.getSanitizedPrompt());
                    return joinPoint.proceed(modifiedArgs);
                } catch (Throwable t) {
                    if (t instanceof Exception e) {
                        throw e;
                    }
                    throw new RuntimeException(t);
                }
            });

            // 返回最终合规且可能包含降级/脱敏标记的处理结果
            return context.getExecutionResult();
        } finally {
            if (context != null) {
                context.getReentrancyDepth().decrementAndGet();
            }
        }
    }

    private String extractPrompt(ProceedingJoinPoint joinPoint, AiOrchestrated annotation) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "";
        }
        // 默认策略: 查找第一个非空字符串作为 Prompt
        for (Object arg : args) {
            if (arg instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return args[0] != null ? args[0].toString() : "";
    }

    private String extractTenantId(ProceedingJoinPoint joinPoint, AiOrchestrated annotation) {
        // 可扩展结合当前租户上下文或请求头提取
        return "TENANT-SYS-001";
    }

    private Object[] maybeRewriteArgs(Object[] originalArgs, String sanitizedPrompt) {
        if (originalArgs == null || originalArgs.length == 0 || sanitizedPrompt == null) {
            return originalArgs;
        }
        Object[] newArgs = new Object[originalArgs.length];
        System.arraycopy(originalArgs, 0, newArgs, 0, originalArgs.length);
        for (int i = 0; i < newArgs.length; i++) {
            if (newArgs[i] instanceof String) {
                newArgs[i] = sanitizedPrompt;
                break;
            }
        }
        return newArgs;
    }
}
