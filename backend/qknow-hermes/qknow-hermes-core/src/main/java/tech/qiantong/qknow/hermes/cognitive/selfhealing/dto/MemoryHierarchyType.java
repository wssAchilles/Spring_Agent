package tech.qiantong.qknow.hermes.cognitive.selfhealing.dto;

/**
 * 动态分层记忆流形层级枚举
 */
public enum MemoryHierarchyType {
    WORKING,    // 工作记忆 (最近对话原文字符)
    EPISODIC,   // 情节记忆 (段落级事件聚合与语义摘要)
    SEMANTIC    // 概念语义记忆 (长期持久化领域规则与核心约束)
}
