package tech.qiantong.qknow.hermes.agent.debate.dto;

/**
 * 智能体生态位角色枚举类 (Agent Role Niche Type)
 * <p>
 * 基于 Lotka-Volterra 生态位分化模型，定义多智能体协同流水线中的五大核心功能生态位。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public enum AgentRoleNicheType {

    /**
     * 业务解构与需求澄清专家 (Analyst)
     */
    ANALYST("需求解构分析师", "负责宏观业务解构、需求语义抽取与约束边界定义"),

    /**
     * 核心代码生成与架构实现专家 (Coder)
     */
    CODER("核心开发工程师", "负责领域代码编写、算法实现与接口落地"),

    /**
     * 代码评审与静态规约校验专家 (Reviewer)
     */
    REVIEWER("代码评审专家", "负责代码静态扫描、风格检查与安全边界审核"),

    /**
     * 对抗反思与边界攻击专家 (Critic)
     */
    CRITIC("对抗质询专家", "负责红蓝对抗、极端边界反例推演与缺陷挑战"),

    /**
     * 终审裁决与纳什均衡仲裁专家 (Arbitrator)
     */
    ARBITRATOR("终审仲裁专家", "负责多方冲突裁决、香农熵监控与收敛平衡"),

    /**
     * 业务代表专家 (Business)
     */
    BUSINESS("业务代表专家", "负责业务利益诉求、交付时效与核心产出价值主张"),

    /**
     * 风险控制专家 (Risk Control)
     */
    RISK_CONTROL("风险控制专家", "负责合规风险识别、财务资金安全与风险控制红线"),

    /**
     * 法务合规专家 (Legal Compliance)
     */
    LEGAL("法务合规专家", "负责法律条款审核、合规边界防御与违约责任审查"),

    /**
     * 技术架构专家 (Architecture)
     */
    ARCHITECTURE("技术架构专家", "负责技术可行性评估、系统稳定性与架构演进审查");

    private final String title;
    private final String description;

    AgentRoleNicheType(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
