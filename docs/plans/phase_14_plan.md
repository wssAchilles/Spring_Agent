# Phase 14: 长文本层次化 Chunking 策略、Markdown 结构感知分块与 Parent-Child Small-to-Big 检索闭环方案与实施契约

> **依据**：`AGENTS.md` Research-to-Implementation Gate 规范  
> **前置调研**：学术向智能体 (`6efd105a`) 与工程向智能体 (`7aa457c8`) 并发深度对标（详见 `docs/plans/phase_14_academic_report.md` 与 `docs/plans/phase_14_industrial_report.md`）  
> **阶段状态**：**Planning & Specification (方案已冻结，待用户实施授权)**  
> **核心领域**：结构感知分块器 (`StructureAwareMarkdownSplitter`)、标题栈面包屑注入 (Breadcrumb Header Injection)、表格完整性保护与表头跨块传播 (Table Header Propagation)、代码块与版本号断言保护、Parent-Child Small-to-Big 检索与 Max-Pooling 得分继承、保序装配与 20KB 预算自适应优雅降级  

---

## 一、当前代码与失败机制诊断

### 1.1 真实执行路径与关键调用关系
1. **文本分块路径**：`KmcSyncServiceImpl.saveSegment` -> `SplitterFactory.create` -> `RecursiveSplitter.java` / `TemplateSplitter.java`
   - 当前分块器基于简单的正向正则匹配标点（`"
## "`, `"

"`, `"。"`），缺乏 AST 树形状态机：
     - **表格机械肢解**：遇到 Markdown 表格（`| col1 | col2 |`）时在字符长度用尽处直接切断，导致后续数据行失去表头列信息，沦为无上下文的离散数字；
     - **层级命名空间断裂**：多级标题（如 `# 模块 > ## 配置 > ### 超时参数`）下的正文被切成孤立切片，千问 1536 维 Embedding 编码时丢失上级命名空间，语义稀释严重；
     - **版本号与代码块被误切**：点号切分未做负向前后瞻断言，将 `Spring Boot 3.5.8` 切割为碎片，代码块从中间被切开。
2. **Parent-Child 检索与装填路径**：`RagRetrievalService.retrieve` -> `RagContextBuilder.buildContextWithEmitted`
   - 在 `expandWithParentSegments`（L153-196）中：
     - **分数暴力归零**：查出的父块被硬编码赋予 `score(0.0)`，彻底抹除了子块命中的检索相关度量；
     - **无序置顶打乱精排**：通过 `new ArrayList<>(parents)` 强制将父块插入到上下文列表头部，颠倒了高分精排顺序；
     - **预算截断无优雅降级**：硬上限 20KB（20000 字节）截断逻辑中，若一个大 Parent 超限直接 `break`，导致后续真正高分命中的核心证据被彻底丢失，引发大模型拒答或幻觉。

### 1.2 本阶段唯一待验证假设 (Sole Verifiable Hypothesis)
> **假设 (H-Phase14)**：在 DeepSeek API 唯一生成模型与阿里千问 1536 维 Embedding 唯一向量模型基线下，通过构建**“结构感知分块器 (`StructureAwareMarkdownSplitter`) + Parent-Child Small-to-Big 检索闭环 (Max-Pooling 得分继承 + 严格保序 + 20KB 预算超额优雅降级回 Child)”**：
> 1. 能 100% 保护 Markdown 表格完整性，超长表格自动传播复制表头行，无断头数据行残留；
> 2. 为每个 Markdown 切片注入紧凑面包屑路径（`【章 > 节 > 小节】

`）与 metadata，向量余弦相似度信噪比显著提升；
> 3. 负向断言 100% 保护版本号（如 3.5.8）不被点号截断，代码块保持围栏完整；
> 4. Parent 100% 继承命中的最高子块得分（`Score(Parent) = max(Score(Child))`），彻底消除 `score=0.0`，且严格按照精排顺位保序；
> 5. 同一 Parent 下多子块命中实现聚合去重；当 Parent 超过 20KB 剩余预算时，100% 优雅降级回高分 Child 原文，确保核心事实不被丢弃，全量单测保持 100% 绿灯。

---

## 二、Research Ledger 核心理论与工业对标

- **RL-PHASE14-001 (ICLR 2024 RAPTOR)**：证明了多层级树状检索（Tree-Organized Retrieval）相对平坦切分（Flat Chunking）具有压倒性的信息完整性与检索精度。
- **RL-PHASE14-002 (ACM SIGIR 2020 ColBERT)**：证明了晚期交互与 Max-Pooling 聚合算子能够最大化保留细粒度单元极值匹配信号，屏蔽均值稀释与长度偏置。
- **RL-PHASE14-003 (TACL 2024 Lost in the Middle)**：证明了盲目堆砌无界长上下文会触发注意力 U 型衰减，论证了精细 Child 检索配合最小闭包 Parent 展开的必要性。
- **RL-PHASE14-005 (EMNLP 2023 StructGPT)**：证明了表格数据必须将列头 Schema 与行数据联合线性化注入，否则跨切片断裂会彻底瓦解语义推导。
- **RL-PHASE14-006 (ACM SIGIR 2009 RRF)**：证明了 Max-Pooling 聚合后输入 RRF 的排序单调性与保真度。
- **RL-P14-001 ~ 004 (LlamaIndex / LangChain / Dify / FastGPT / Ragflow)**：确立了 Markdown 标题栈面包屑注入、表格表头跨块传播（Table Header Propagation）、Child 存向量库与 Parent 存主表解耦、Max-Pooling 得分继承及 20KB 预算优雅降级的最佳工程范式。

---

## 三、架构设计与落地契约

### 3.1 模块架构图

```
[文档切分阶段]
Markdown 输入 ──► StructureAwareMarkdownSplitter
                   ├── 标题栈跟踪 ──► 注入【章 > 节 > 小节】面包屑前缀
                   ├── 表格保护   ──► 原子整块保护 / 超长行级复制表头
                   ├── 围栏状态机 ──► 保护 ``` 代码块不被切断
                   └── 正则保护   ──► (?<!\d)\.(?!\d) 保护版本号
                   │
                   ▼ 生成 Parent (1200字完备语义) + Child (200字精细检索)
                   ├── Parent & Child 写入 MySQL kmc_document_segment
                   └── 仅 Child (1536维) 写入 PgVector vector_store

[检索与装配阶段]
Query ──► PgVector 召回 Child 列表 (带得分)
           │
           ▼
RagContextBuilder (Small-to-Big 闭环重构)
  ├── 1. 根据 parent_id 查询 Parent 记录
  ├── 2. Max-Pooling: Parent 继承最高 Child 得分 S(P) = max(S(c))
  ├── 3. 保序映射: 严格按 Child 精排顺位排列，同一 Parent 聚合去重
  └── 4. 20KB 预算装配与降级熔断:
          ├── 未超限: 装填完整 Parent
          ├── Parent超限但Child未超限: 优雅降级装填对应 Child 原文
          └── 超过 20KB 硬限制: 安全截断
```

### 3.2 核心实现细节
1. **`StructureAwareMarkdownSplitter`** (`qknow-framework/qknow-ai`):
   - 基于轻量级行状态机（无外部重型依赖），维护 `Deque<HeadingEntry>` 标题栈；
   - 提取表格块 `tableLines`，若整体未超过 `maxChunkSize` 则作为原子块；若超长，提取前两行表头与分隔线，对每批数据行前置复制表头；
   - 保护三反引号代码块不被从中间切开；
   - 英文句子切分采用正则 `(?<!\d)\.(?!\d)|[。！？!？\n\r]+`，杜绝误切版本号与小数；
   - 支持 `splitParentChild` 方法，生成 Parent（大块完备语境）与 Child（小块高分辨率），并在 Child 注入 `parent_segment_id`。
2. **`SplitterFactory` 扩展** (`qknow-framework/qknow-ai`):
   - 增加常量 `MODE_STRUCTURE_AWARE = "structure_aware"`；
   - 在 `createTemplate` 中针对 `.md` 与 `.markdown` 默认选用结构感知分块器。
3. **`RagContextBuilder` 升级** (`qknow-module-kmc-biz`):
   - 重构 `expandAndPoolScores`：通过 SQL 批量获取 Parent；对每个 Parent 继承其命中子块中的最高得分（Max-Pooling）；
   - 保持精排顺位，消除 `score=0.0` 与无序置顶；
   - 在 20KB（20000 字节）硬预算装填时，若 Parent 超限而其高分 Child 未超限，自动触发优雅降级（Graceful Degradation），回填高分 Child 原文并记录日志。

---

## 四、实施计划与最小修改文件集合

### 4.1 新增文件
1. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/transformer/StructureAwareMarkdownSplitter.java`
2. `backend/tests/src/test/java/tech/qiantong/qknow/ai/transformer/StructureAwareMarkdownSplitterTest.java`
3. `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase14StructureAwareAndParentChildGateTest.java`

### 4.2 修改文件
1. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/transformer/SplitterFactory.java`
   - 添加 `MODE_STRUCTURE_AWARE` 并在模板切分中支持 Markdown 结构感知分块器；
2. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagContextBuilder.java`
   - 重构 `buildContextWithEmitted`，实现 Max-Pooling 得分继承、保序、去重与 20KB 预算超限优雅降级回 Child；
3. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/impl/KmcSyncServiceImpl.java`
   - 适配结构感知父子切块构建。

---

## 五、验证命令与准入判定准则

```bash
# 1. 结构感知分块器专用测试
bash run-with-java21.sh mvn -B -f backend/pom.xml -pl tests -Dtest=tech.qiantong.qknow.ai.transformer.StructureAwareMarkdownSplitterTest test

# 2. Phase 14 核心门禁契约测试 (表格保护、版本号保护、Max-Pooling 得分继承、20KB 优雅降级)
bash run-with-java21.sh mvn -B -f backend/pom.xml -pl tests -Dtest=tech.qiantong.qknow.rag.eval.Phase14StructureAwareAndParentChildGateTest test

# 3. 既有 RagContextBuilder 防退化单测
bash run-with-java21.sh mvn -B -f backend/pom.xml -pl tests -Dtest=tech.qiantong.qknow.rag.RagContextBuilderTest test

# 4. 全量防退化回归测试
bash run-with-java21.sh mvn -B -f backend/pom.xml -pl tests test
```

### 准入判定准则
1. **表格完整性测试**：超长表格分块后，所有切片第一行与第二行必须为完整表头，切片内无孤立断头数据行；
2. **面包屑注入测试**：Markdown 多级标题必须正确注入切片首部与 metadata，层级格式严格为 `【章 > 节 > 小节】`；
3. **版本号防切测试**：输入包含 `Spring Boot 3.5.8` 与 `v2.1.0` 的文本，切分后版本号完整保留，严禁因点号断开；
4. **Parent 得分继承测试**：回溯查出的 Parent 其 `score` 必须严格等于命中它的最高 Child 得分，严禁出现 `score == 0.0`；
5. **预算自适应降级测试**：设置 1000 字节小预算构造场景，当 Parent 超过预算但其 Child 未超过预算时，系统优雅降级吐出 Child，且总字节数严格 $\le 1000$ 字节；
6. **全量回归无退化**：全库 712+ 项单测保持 100% 绿灯！
