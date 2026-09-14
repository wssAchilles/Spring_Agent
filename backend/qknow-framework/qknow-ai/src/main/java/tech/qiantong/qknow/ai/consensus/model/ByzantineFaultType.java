package tech.qiantong.qknow.ai.consensus.model;

/**
 * 拜占庭故障类型定义
 */
public enum ByzantineFaultType {
    /**
     * 正常诚实节点
     */
    NONE,

    /**
     * 崩溃挂死、超时未响应或返回空内容
     */
    CRASH_STOP,

    /**
     * 语义离群、胡言乱语或虚构事实（无对抗性）
     */
    NOISY_OUTLIER,

    /**
     * 恶意越狱提示词注入或对抗性攻击
     */
    ADVERSARIAL_INJECTION
}
