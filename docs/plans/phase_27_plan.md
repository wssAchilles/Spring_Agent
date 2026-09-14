# Phase 27 决策完备实施方案：多模态复杂文档智能解析引擎、版面拓扑感知与跨模态对齐感知体系 (Multimodal Layout-Aware Parsing, Vision-Augmented Chunking & Cross-Modal Grounding Engine)

> **拟归档路径**：`docs/plans/phase_27_plan.md`  
> **依据规范**：`AGENTS.md` Research-to-Implementation Gate 强制规范  
> **前置依赖**：Phase 01 ~ Phase 26 全量交付通过（测试 802/802 100% 绿灯，前端生产构建通过）  
> **理论依据**：`docs/plans/phase_27_academic_report.md`（版面几何空间拓扑单元建模、递归扩展 XY-Cut 空间投影算法、定理 1.1 分栏文本流防穿透引理 $\mathcal{O}(\exp(-D_{\text{gap}}/\sigma))$ 证明、定理 2.1 表格网格代数拓扑结构还原单射性定理 $H(T \mid \Phi(T)) = 0$ 构造性证明、跨页表格因果一致性边界方程、视觉语义边界层次分块相对于固定滑动窗口的信息熵增益推导、跨模态对比对齐 InfoNCE 互信息下界严格推导 $I(X_{\text{vis}}; X_{\text{text}}) \ge \log(K) - \mathcal{L}_{\text{InfoNCE}}$、以及视觉图表锚定消除“如图所示”问答幻觉发生率的指数抑制定理 Theorem 3.1 证明）  
> **工业对标**：`docs/plans/phase_27_industrial_report.md`（MinerU 5 阶段流水线、Marker 垂直投影空白槽分栏算法、PP-Structure v2 SLANet 表格拓扑无损还原、PyMuPDF 高速几何抽取、Unstructured.io 标题与原子块切片，复盘规避“双栏按物理 Y 轴横向交替穿插严重破坏语义”、“复杂合并单元格表格切碎导致财务指标张冠李戴”、“超大 CAD 矢量图未做 DPI 与并发限制导致宿主机 OOM Killer 强杀业务主进程”三大生产灾难）  
> **唯一模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3 / `deepseek-reasoner` 即 R1），唯一向量模型为 **阿里千问 (Qwen) Embedding (1536维)**，绝无本地大模型，彻底弃用 OpenAI/GPT API。  
> **环境隔离铁律**：后端全量模块统一且唯一使用 **Java 21** 编译与运行，绝对路径固定为 `/Users/achilles/.sdkman/candidates/java/21.0.5-tem`，Maven 执行强制局部传入 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`。

---

## 一、方案全景与架构拓扑

```text
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                          Phase 27 多模态复杂文档智能解析与感知全景架构                            │
│                                                                                                  │
│  [ 原始复杂文档 (PDF/Word/扫描件) ] ──> [ 阶段 1: 文档预检分类器 (字符密度/扫描件双轨判定) ]        │
│         │                                                                                        │
│         ▼                                                                                        │
│  [ DocumentLayoutReadingOrderResolver ] (版面空间拓扑偏序与阅读序重构引擎)                        │
│         ├── 通栏标题/横幅优先切除，消除垂直投影死锁                                              │
│         ├── 垂直投影直方图空白槽 (Vertical Gutter) 判定多栏几何边界                             │
│         └── 阅读序有向无环图 (DAG) 拓扑排序，严格保证跨栏防穿透 (Theorem 1.1)                    │
│         │                                                                                        │
│         ▼                                                                                        │
│  [ TableGridTopologyReconstructor ] (高保真表格代数网格拓扑重构器)                              │
│         ├── 满覆盖与无重叠原子网格矩阵 M_{R x C} 离散化映射                                      │
│         ├── 单元格跨行 (rowspan) 与跨列 (colspan) 复合网格合并                                    │
│         ├── 复杂表格输出标准 HTML <table>，简单表格输出 Markdown Table，满足单射还原 (Theorem 2.1)│
│         └── 跨页表格 (Cross-Page Table) 列边界豪斯多夫距离校验与表头因果一致性拼接                │
│         │                                                                                        │
│         ▼                                                                                        │
│  [ CrossModalAnchorService ] (跨模态图表抽取与视觉语义锚定器)                                    │
│         ├── 提取图表/插图区域高清切片，持久化并生成唯一元数据占位符                               │
│         ├── 基于 DeepSeek API 生成结构化图表语义摘要 (> [图表摘要])                              │
│         └── 消除“如图所示”悬空代词，实现对幻觉发生率的指数级压制 (Theorem 3.1)                   │
│         │                                                                                        │
│         ▼                                                                                        │
│  [ LayoutAwareDocumentSplitter ] (版面拓扑感知切片适配器)                                        │
│         ├── 标题字号/视觉留白/原子块边界驱动层次分块，最大化信息熵增益 (Lemma 4.1)               │
│         ├── 表格与图表标记为原子不可分割块 (Atomic Blocks)                                        │
│         └── 无缝对接 Phase 14 StructureAwareMarkdownSplitter 与 Phase 25 响应式微批流水线        │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、唯一核心待验证假设 (H-PHASE27-001)

> **唯一核心待验证假设 (H-PHASE27-001)**：  
> 构建**基于递归扩展 XY-Cut 空间拓扑投影与有向无环图（DAG）拓扑排序的版面阅读序重构引擎、基于二维代数网格与扫描线算法的高保真表格拓扑单射还原引擎、基于视觉语义边界驱动的层次化分块器，以及基于 DeepSeek 视觉实体锚定（Visual Grounding & Chart Summary）的跨模态对齐感知流水线**：  
> 1. **阅读序拓扑防穿透**：空间几何拓扑排序严格满足有向无环图（DAG）无环终止性，且跨栏穿透错误率随栏间隙满足指数衰减界，将双栏/多栏文档的阅读序错误率从当前 Tika 基线的 $\ge 42.0\%$ 压制至 $\le 2.5\%$；  
> 2. **表格结构无损单射**：二维网格代数拓扑在保持满覆盖且无重叠公理下，转换至标准 HTML/Markdown 时满足信息论零条件熵损失（$H(T \mid \Phi(T)) = 0$），跨行跨列准确率 $\ge 95.0\%$，跨页表格依据列切分边界豪斯多夫距离 $\text{dist}_{\mathcal{H}} \le 0.03W$ 实现因果一致性合并；  
> 3. **跨模态抗幻觉锚定**：图表切片抽取与 DeepSeek 视觉增强摘要反向回填正文切片，对“如图所示”悬空指代问答的幻觉发生率由 $\ge 65.0\%$ 指数级压制至 $\le 5.0\%$；  
> 4. **无缝赋能下游切片**：输出具备版面拓扑的高保真 Markdown，无缝注入 Phase 14 的 `StructureAwareMarkdownSplitter` 与 Phase 25 的微批流，在端到端复杂文档问答上实现事实检索召回率 NDCG@10 提升 $\ge 18.0\%$。

---

## 三、拟变动文件清单 (Proposed Changes)

### 3.1 数据传输对象与领域模型 (DTO & Domain Models)
#### [NEW] [`ParsedDocumentResult.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/parser/dto/ParsedDocumentResult.java)
- 高保真文档解析统一产物领域对象：包含 `taskId`, `fullMarkdown`, `elements`, `tables`, `figures`, `metrics`。

#### [NEW] [`ParsedLayoutElementDTO.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/parser/dto/ParsedLayoutElementDTO.java)
- 版面元素实体：包含几何包围框 `bbox`（归一化 $[x_1, y_1, x_2, y_2]$）、语义类别 `ElementType`（`TITLE`, `TEXT`, `TABLE`, `FIGURE`, `HEADER`, `FOOTER`）、页码与拓扑阅读序 `readingOrder`。

#### [NEW] [`ExtractedTableDTO.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/parser/dto/ExtractedTableDTO.java)
- 提取表格实体：包含原始网格行列尺寸 $R \times C$、HTML 表格内容（含 `rowspan`/`colspan`）、Markdown 表格内容、跨行跨列标记及跨页连续表头元数据。

#### [NEW] [`ExtractedFigureDTO.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/parser/dto/ExtractedFigureDTO.java)
- 提取图表实体：包含图表唯一 ID、几何坐标、持久化图像 URI、图表文字说明（Caption）及 DeepSeek 视觉语义增强摘要。

---

### 3.2 核心算法与控制组件 (Core Algorithms & Controllers)
#### [NEW] [`DocumentLayoutReadingOrderResolver.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/parser/layout/DocumentLayoutReadingOrderResolver.java)
- 纯 Java 21 高性能版面空间拓扑阅读序重构引擎：
  - 启发式垂直投影直方图空白槽（Gutter）判定与多栏分割；
  - 通栏标题与横幅元素优先分离，消除垂直投影死锁；
  - 基于 Kahn 算法的阅读序 DAG 拓扑排序，严格保证跨栏防穿透定理（Theorem 1.1）。

#### [NEW] [`TableGridTopologyReconstructor.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/parser/table/TableGridTopologyReconstructor.java)
- 二维网格代数拓扑重构与单射转换器：
  - 动态扫描线网格占用跟踪算法（Dynamic Raster-Scan Algorithm）；
  - 复合表头与跨行跨列（`rowspan`/`colspan`）标准 HTML `<table>` 映射（Theorem 2.1）；
  - 跨页续表列几何边界豪斯多夫距离校验与表头因果一致性拼接。

#### [NEW] [`CrossModalAnchorService.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/parser/vision/CrossModalAnchorService.java)
- 跨模态图表实体抽取与视觉语义锚定服务：
  - 图表占位符 `![fig_id](uri)` 注入；
  - 基于 DeepSeek API 生成结构化图表摘要，并以 `> **【图表语义增强】**` 格式反向回填；
  - 消除悬空代词指代，严格压制图表幻觉发生率（Theorem 3.1）。

#### [NEW] [`LayoutAwareDocumentSplitter.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/parser/splitter/LayoutAwareDocumentSplitter.java)
- 版面感知层次切片适配器：
  - 标题层级与视觉留白边界驱动分块，最大化互信息增益（Lemma 4.1）；
  - 将表格与图表定义为不可拆碎的原子块（Atomic Blocks）；
  - 无缝注入 Phase 14 的 `StructureAwareMarkdownSplitter` 与 `KmcDocumentSegmentDO`。

#### [NEW] [`MultimodalParsingGatewayClient.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/parser/gateway/MultimodalParsingGatewayClient.java)
- 多语言异构微服务网关客户端与本地安全降级门禁：
  - 负责与外部独立部署的解析 Worker 微服务通信（REST/gRPC）；
  - 单页 150 DPI 限制与 OOM 熔断保护；
  - 当外部解析微服务不可用或单页超时（15s）时，自动 Fail-Open 回退至本地安全解析策略。

---

### 3.3 测试驱动开发与自动化门禁 (TDD & Contract Testing)
#### [NEW] [`Phase27MultimodalDocumentContractTest.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase27MultimodalDocumentContractTest.java)
- 覆盖 10 项严密专项契约测试：
  1. `contract01_readingOrderDAG_correctlyResolvesMultiColumnReadingFlow`：双栏排版准确分流，0 跨栏横向交错穿插
  2. `contract02_columnNonCrossing_guaranteesBoundedSeparationBound`：栏间隙在扰动下严格满足防穿透定理，拓扑排序无环终止
  3. `contract03_tableGridTopology_reconstructsMarkdownAndHtmlLosslessly`：复合表头与跨行跨列单元格无损单射转录 HTML
  4. `contract04_crossPageTableStitching_preservesColumnHeaderConsistency`：跨页续表识别并自动继承首页面表头，防止数据孤岛
  5. `contract05_crossModalAnchoring_extractsFiguresAndInjectsPlaceholders`：图表与插图精准锚定正文上下文，生成标准元数据占位符
  6. `contract06_visualGroundingFidelity_suppressesChartHallucination`：结合图表描述显著压制图表问答幻觉，事实保真度支持率 $\ge 0.90$
  7. `contract07_layoutAwareSplitter_alignsWithHeadingHierarchy`：版面感知分块严格遵守标题级次与视觉边界，不拆散逻辑块
  8. `contract08_polyglotProtocol_handlesStreamingDocumentParsingPayloads`：多语言解析中间件协议流式交互与超时软降级（Fail-Open）
  9. `contract09_oomProtectionGuard_abortsBombPdfGracefully`：超大异常/畸形文档触发安全熔断保护，释放资源并优雅降级
  10. `contract10_endToEndMultimodalPipeline_producesStructuredSegments`：端到端复杂文档解析输出高质量富文本切片，无缝接入向量与图谱

#### [NEW] [`backend/tests/fixtures/multimodal-layout-sample.json`](file:///Users/achilles/Documents/许子祺/Agent/backend/tests/fixtures/multimodal-layout-sample.json)
- 固化的高保真多模态版面、双栏文本流、复杂跨行跨列表格与图表实测测试集。

---

## 四、验证计划 (Verification Plan)

### 1. Phase 27 专属契约测试
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn test -am -pl tests -Dtest=Phase27MultimodalDocumentContractTest -Dsurefire.failIfNoSpecifiedTests=false
```
预期：10/10 项专项契约 100% 绿灯。

### 2. 后端全量防退化回归测试
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn test -am -pl tests
```
预期：802 既有测试 + 10 本阶段契约测试 = 812 项测试 100% 绿灯（0 失败 0 错误）。

### 3. 前端生产构建验证
```bash
cd frontend && npm run build:prod
```
预期：0 错误通过。
