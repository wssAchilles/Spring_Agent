package tech.qiantong.qknow.hermes.cognitive.dto;

/**
 * 两级递阶工具自省反思动作记录
 * 遵循 Phase 84 定理 1.2 HMDP 因果自愈
 *
 * @param type                 反思动作类型
 * @param currentRetryCount    当前微观重试轮次
 * @param causalErrorFeature   因果错误特征描述
 * @param canonicalFingerprint 规范化 SHA-256 参数指纹
 * @param revisedArgumentsJson 微调修正后的参数 JSON
 * @param fallbackToolName     宏观降级备选工具名
 * @param hitlPrompt           人机协同澄清提示词
 */
public record ReflexionAction(
        ReflexionType type,
        int currentRetryCount,
        String causalErrorFeature,
        String canonicalFingerprint,
        String revisedArgumentsJson,
        String fallbackToolName,
        String hitlPrompt
) {
    public enum ReflexionType {
        /** 微观单步原地参数自适应微调重试 */
        MICRO_RETRY_ADAPTIVE,
        /** 宏观任务图级状态回退与备选工具切换 */
        MACRO_TOOL_FALLBACK,
        /** 触发人机协同审批与用户澄清 */
        HITL_ASK_USER
    }
}
