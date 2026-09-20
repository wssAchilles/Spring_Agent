# Phase 123 实施计划与工程契约：超长文档层次化理解、图谱子图推理与 GraphRAG 深度融合中枢

## 一、当前代码与失败机制

### 1. 真实执行路径与瓶颈
当前系统的 RAG 流程主要基于传统的向量相似度 Top-$K$ 召回与基础父子切片展开（Phase 113），在面对企业级超长文档与高密度知识图谱时，存在三大核心失败机制：
- **失败机制 1（跨章节断裂）**：缺乏树状多尺度拓扑表达，跨章节的长程依赖切片在缺乏上层章节宏观语义锚点的情况下被过滤，导致信息不完整；
- **失败机制 2（图谱组合爆炸）**：基于跳数的盲目扩展导致节点数激增（$> 1000$ 节点），淹没核心因果链路并撑爆 LLM 上下文；
- **失败机制 3（事实漂移无源引用）**：生成侧未设立基于图谱因果拓扑的一致性门禁，模型自由生成时出现捏造三元组事实的幻觉。

### 2. 本阶段唯一待验证假设 (123-H1)
> **假设 123-H1**：在阿里千问 1536 维超球面空间中，采用四级金字塔树状文档流形（Document -> Section -> Paragraph -> Chunk）配合测地内积加权局部 PPR 平稳分布与 Steiner 最小因果树剪枝，能在将图谱子图节点规模严格钳位在 $K \le 15$（噪声抑制比 $\ge 90\%$）的同时，结合 $\tau_{\text{ground}} \ge 0.88$ 的反幻觉接地门禁，实现对虚假断言与关系倒置的 100% 物理拦截，全链路保持端到端毫秒级高保真响应。

---

## 二、候选方案比较

| 维度 | Baseline (当前实现) | 方案 A: 简单增加上下文窗口与多跳过滤 | 方案 B (推荐): 四级树金字塔 + Steiner 树 + 接地门禁 | 方案 C: 保持现状 |
|---|---|---|---|---|
| **正确性与保真度** | 一般 (长程割裂，无接地门禁) | 较差 (大窗口引发注意力迷失) | **极高 (多尺度拓扑 + 严格接地门禁)** | 一般 |
| **可证伪性** | 弱 (缺乏细粒度核验凭单) | 弱 | **完备 (三元组事实哈希 + 不可变存证)** | 弱 |
| **图谱子图规模** | 不稳定 (可能组合爆炸) | 极大 ($> 500$ 节点) | **极紧凑 ($\le 15$ 节点，噪声抑制 $\ge 90\%$)** | 不稳定 |
| **大模型 API 成本** | 偏高 (冗余 tokens) | 极高 (窗口撑满) | **极低 (经 Steiner 树与摘要压缩)** | 偏高 |
| **幻觉拦截率** | $0.0\%$ (无门禁) | $0.0\%$ | **$100.0\%$ (单项违规即被门禁阻断)** | $0.0\%$ |
| **依赖变化** | 现有依赖 | 增加外部重排模型 | **零新增外部依赖，纯 Java 21 原生实现** | 无 |
| **回滚风险** | 低 | 高 | **极低 (纯新增独立组件，接口平滑解耦)** | 无 |

**拒绝方案 A 的理由**：简单依赖增大上下文窗口和外部沉重模型，不仅成本成倍激增，而且在长文本中存在“迷失在中间”（Lost in the Middle）现象，且无法从根本上阻断模型在输出时的逻辑幻觉。

---

## 三、推荐的最小算法实现规范

遵循最小算法选择原则，100% 复用项目既有 Java 21、千问 1536 维超球面与 DeepSeek API 基线：

1. **`HierarchicalPyramidDocumentChunker.java`**：
   - 建立四级金字塔树：Level 0 (Document) -> Level 1 (Section) -> Level 2 (Paragraph) -> Level 3 (Atomic Chunk)；
   - 自底向上递归质心聚合，自顶向下分支定界剪枝（Branch & Bound），保证长程宏观语义不丢失。
2. **`SteinerCausalSubgraphEngine.java`**：
   - 测地加权局部 PPR 动力学迭代（$\alpha = 0.85$ 快速收敛）；
   - Kou-Markowsky-Berman (KMB) 2-近似 Steiner 最小因果树算法，将跨多实体的因果子图节点严格压缩在 $\le 15$ 以内。
3. **`GraphFactGroundingGate.java`**：
   - 双轨因果骨架注入与事实三元组提取；
   - 严格落实 $\tau_{\text{ground}} \ge 0.88$ 接地门禁，阻断幻觉输出。
4. **`HierarchicalGraphRagReceipt.java`**：
   - 纯 Java 21 Record 格式，封装多尺度层级路径、Steiner 子图拓扑哈希、PPR 熵、接地得分及 HMAC-SHA256 密码学签名，提供常量时间自验真方法。
5. **`HierarchicalGraphRagMetacenter.java`**：
   - 统一调度总控中枢，提供高并发线程安全的单一受控门面。

---

## 四、专属契约测试套件设计（8 项严苛契约测试）

在 `backend/tests` 模块新建 `tech.qiantong.qknow.ai.rag.hierarchical.Phase123HierarchicalGraphRagContractTest.java`：

1. **契约 1**：四级金字塔树状文档切分与自底向上语义质心聚合，祖先层级路径继承完整率 100%；
2. **契约 2**：自顶向下分支定界剪枝，跨章节长程语义召回率 100%，非相关路径剪枝率 $\ge 75\%$；
3. **契约 3**：阿里千问 1536 维超球面测地内积加权局部 PPR 迭代 15 步平稳收敛，收敛残差 $\le 0.10$；
4. **契约 4**：KMB 2-近似 Steiner 最小因果树剪枝，节点规模严格钳制在 $|V_S| \le 15$，噪声抑制比 $\ge 90\%$；
5. **契约 5**：双轨因果骨架 Prompt 构建保真度，DeepSeek 双轨结构化注入无损对齐；
6. **契约 6**：图谱事实保真度与反幻觉接地门禁，非事实断言与关系倒置 100% 物理阻断（$S_{\text{ground}} < 0.88$）；
7. **契约 7**：不可变存证凭单 `HierarchicalGraphRagReceipt` HMAC-SHA256 自签名与常量时间验真耗时 $\le 25\mu\text{s}$，单比特篡改 100% 拦截；
8. **契约 8**：端到端长文档检索 + 图谱子图推理 + 事实接地闭环决策全链路畅通。

---

## 五、复现命令与验证步骤

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn clean test -Dtest=Phase123HierarchicalGraphRagContractTest -pl tests -am -Dsurefire.failIfNoSpecifiedTests=false
```
跨模块联合回归：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn test -Dtest="Phase*ContractTest" -pl tests -am -Dsurefire.failIfNoSpecifiedTests=false
```
前端验证：
```bash
cd /Users/achilles/Documents/许子祺/Agent/frontend && npm run build:prod
```

---

## 六、风险、停止条件与授权边界

### 1. 立即停止条件 (Immediate Stop Conditions)
- 经 Steiner 剪枝后的图谱节点数突破 15 个上限，发生组合爆炸（定理 1.2 证伪）；
- 注入明显虚假的三元组时，接地评分未降至 0.88 以下发生逃逸漏网（定理 1.3 证伪）；
- 四级金字塔构建在大文档（100k+ 字）下耗时超过 50ms。

### 2. 授权边界
- 当前阶段为第一回合只读研读、理论证明与计划拟定；
- 严禁在未经用户明确批准前修改生产业务代码或运行破坏性变更。
