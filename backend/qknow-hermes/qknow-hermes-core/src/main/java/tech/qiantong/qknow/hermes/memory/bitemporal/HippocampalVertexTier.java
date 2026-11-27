package tech.qiantong.qknow.hermes.memory.bitemporal;

/**
 * 双时态超球面记忆超图四层（含持久错题本子类）异构顶点层级枚举（定义 5.1 / 论文第 4 章）。
 */
public enum HippocampalVertexTier {
    /** 稀疏语义层：细粒度开放信息抽取的短语/命名实体节点 */
    PHRASE_ENTITY,
    /** 稠密情节与段落层：完整文档切片与对话情节节点（与 PHRASE_ENTITY 构成 HippoRAG 2 二分图） */
    PASSAGE_EPISODE,
    /** 超球面社区层：睡眠期在 S^1023 上经球面 k-Means 凝聚的高阶法则质心节点 */
    SPHERICAL_COMMUNITY,
    /** 推理编译知识页层：带双向链接的结构化编译 Wiki 页面 */
    COMPILED_WIKI,
    /** 持久错题本层：记录历史推理陷阱与负例约束的排斥势场节点 */
    ERROR_BOOK
}
