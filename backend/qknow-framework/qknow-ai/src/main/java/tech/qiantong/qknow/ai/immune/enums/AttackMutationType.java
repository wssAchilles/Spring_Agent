package tech.qiantong.qknow.ai.immune.enums;

/**
 * 红队对抗攻击变异类型枚举
 *
 * @author Achilles
 * @since Phase 45
 */
public enum AttackMutationType {
    /**
     * 编码与隐蔽混淆变异（如 Base64 嵌套、零宽字符插入）
     */
    ENCODING_OBFUSCATION,

    /**
     * 角色扮演催眠变异（如假想世界、虚拟故事前缀设定）
     */
    ROLEPLAY_HYPNOSIS,

    /**
     * 间接间谍指令潜伏注入变异（如文档切片尾部潜伏偷渡指令）
     */
    INDIRECT_SPY_INJECTION,

    /**
     * 指令覆盖与目标劫持变异（如 Ignore previous instructions 变种）
     */
    INSTRUCTION_OVERRIDE
}
