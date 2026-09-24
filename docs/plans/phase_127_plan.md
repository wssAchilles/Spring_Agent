# Phase 127 实施方案与技术契约
## 拓扑子图因果推断剪枝、时空衰减超球面流形对齐与打字机无损同步中枢
### (Phase 127 Implementation Plan & Technical Contract)

> **归档路径**：`docs/plans/phase_127_plan.md`  
> **所属阶段**：第六演进阶段 (Phase 125 ~ Phase 128) 第三步骤  
> **所属核心支柱**：支柱三：高保真 RAG 知识引擎与多模态图谱 (Advanced RAG & Multimodal Knowledge)  
> **顶刊学术对齐**：ESWA 手稿 Section 4.2 图谱子图推理与 Section 8 题型细分实验基线；彻底杜绝子图爆炸、旧知识倒挂与打字机断流乱码  
> **模型基线**：唯一生成模型为 DeepSeek API（主干模型参数化思考 `thinking: {"type": "enabled"}`）；唯一向量模型阿里千问 1536 维超球面；Java 21 隔离环境。

---

## 一、 核心目标与交付清单

1. **实现不可变存证凭单 `GraphRagCausalAlignmentReceipt.java`**：
   - 纯 Java 21 Record 格式，封装查询哈希、种子实体、剪枝后子图规模、命题数、平均时空得分、耗时微秒与 SHA-256 密码学自签名及 `verifySignature` 验真方法；
2. **实现时空衰减超球面流形对齐器 `SpatiotemporalManifoldAligner.java`**：
   - 阿里千问 1536 维超球面单位向量测地角距离计算（8 路循环展开优化，单次 $\le 20\mu\text{s}$）；
   - 结合知识有效区间 $[t_{\text{start}}, t_{\text{end}}]$ 与指数半衰期衰减 $\exp(-\lambda \Delta t)$；
   - 过期规则在 $\Delta t \ge 5\tau_{1/2}$ 时得分严格 $\le 0.03125$，杜绝历史倒挂；
3. **实现 2-跳局部 PPR 因果剪枝与命题拓扑排序器 `CausalSubgraphPruner.java`**：
   - 种子实体展开，2-跳以内 PPR 迭代收敛，节点严格钳位在 $\le 16$；
   - Kahn 因果拓扑排序，将子图投影为结构化有向因果命题链，Token 压缩 $\ge 75\%$，幻觉率显著压制；
4. **实现带实体锚定的无损流式打字机管道 `GraphStreamLosslessTypewriter.java`**：
   - 实体命题锚定帧嵌入 SSE 流，维护单调序列号；
   - 支持网络闪断断点重放与残差无损补全；
5. **编写专属契约测试并验证 `Phase127GraphRagCausalAlignmentContractTest.java`**：
   - 覆盖 8 项硬核指标，实现 100% 绿灯，0 破坏性变更，保持历史向下兼容。

---

## 二、 验证与构建命令

```bash
# 1. 编译并本地安装 qknow-hermes-core
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn clean install -DskipTests -f backend/qknow-hermes/qknow-hermes-core/pom.xml

# 2. 运行 Phase 127 专属契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -Dtest=Phase127GraphRagCausalAlignmentContractTest -f backend/tests/pom.xml

# 3. 运行 Phase 121 ~ Phase 127 全量联合回归测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -Dtest=Phase121SwarmDelegationContractTest,Phase122McpSecuritySandboxContractTest,Phase123HierarchicalGraphRagContractTest,Phase124WorkflowHitlGovernorContractTest,Phase125ReActStrictWindowContractTest,Phase126McpLeaseLivenessContractTest,Phase127GraphRagCausalAlignmentContractTest,DagCheckpointManagerTest -f backend/tests/pom.xml
```
