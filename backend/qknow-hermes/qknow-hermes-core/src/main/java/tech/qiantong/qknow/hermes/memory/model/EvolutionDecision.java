package tech.qiantong.qknow.hermes.memory.model;

/**
 * 偏好演化仲裁决策状态枚举
 * 对标 Mem0 四状态机并扩展为五态：
 * ADD: 新增偏好
 * UPDATE: 属性补充或同向强化更新
 * SUPERSEDE: 显式矛盾覆盖（将旧偏好标记废弃并建立 SUPERSEDES 边）
 * REJECT: 意态过滤器拦截（假设/反事实陈述）
 * NOOP: 无变化（完全一致或信息量不足）
 */
public enum EvolutionDecision {
    ADD,
    UPDATE,
    SUPERSEDE,
    REJECT,
    NOOP
}
