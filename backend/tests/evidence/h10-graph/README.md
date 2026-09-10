# H10 GraphRAG Environment Log (incremental)

## 2026-09-10 probe

- Neo4j **已运行**（docker-compose，HTTP 7474 / bolt 7687，auth neo4j/neo4jpass123）
- 节点 **7**，边 **4**
- Labels: Knowledge, Technology, KgNode, Community
- 样例实体：特斯拉 / 埃隆·马斯克 / 电动车（demo），**未覆盖** 人工智能/分布式/大数据 等 golden 文档实体
- 结论：`graph.enabled` 保持 **false**。当前图无法支撑 PPR/GraphRAG 对 golden 的增量验证。
- 打开图验证的前置：对 kmc 文档跑实体抽取同步（`GraphRagSyncService` / 实体 LLM），再跑 Holdout Hit@10 A/B。

### 下一步（需另批或继续授权）

1. 开启 `qknow.rag.query-entity` 或离线脚本抽实体 → 写 Neo4j  
2. `qknow.rag.graph.enabled=true` + PPR  
3. 与 vector-only 对比 Hit@10  

本日志边跑边写，避免额度/会话中断丢结果。
