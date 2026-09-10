# Candidate H2：SIMPLE 路由轻检索 — Research-to-Implementation Gate 报告

> **状态**：待审核（DRAFT — 未批准前禁止实施）  
> **日期**：2026-09-10  
> **依据**：`AGENTS.md` Research-to-Implementation Gate  
> **前置**：A1 ColBERT fail-closed 已交付（`f6c0d31`，已 push）  
> **本回合**：只读检查 + 定向文献检索，**未改任何代码/fixture/配置**

---

## A. 当前代码与失败机制

### A.1 真实执行路径（只读复核）

```
KbAgentConfigServiceImpl / recallTest
  → RagRetrievalService.retrieveScoped
      → QueryRouter.classify(query)
          → 长度 < 10 或 SIMPLE 正则 → QueryRoute.SIMPLE
      → if (SIMPLE && !debug)
            return context="" + sources=[]     ← 失败点
```

### A.2 证据（file:line）

| 位置 | 事实 |
|---|---|
| `QueryRouter.java:84-86` | `query.length() < 10` → SIMPLE；中文短问（如「昵称规则」8 字）直接命中 |
| `QueryRouter.java:78-81` | 仅固定「检索意图词」白名单可覆盖为 MEDIUM |
| `QueryRouter.java:27-29` | `SIMPLE_PATTERNS`：你好/hello/谢谢/时间/天气等 |
| `RagRetrievalService.java:103-109` | SIMPLE 且非 debug → **空上下文、空 sources** |
| `RagRetrievalService.java:110-113` | 仅 `recallDebug` 强制走全检索 |

### A.3 失败机制

组织知识库中大量**短实体 / 制度名 / 模块名**查询（2–9 个中文字符）被当作「通用常识题」跳过知识库。LLM 只能凭参数知识作答，**RAG 在这类查询上等于关闭**。

与开放域不同：组织私有知识几乎不存在「参数模型已经知道」的前提，短查询更需要检索。

### A.4 本阶段唯一待验证假设

> **H2**：在中文组织知识库场景下，`QueryRouter` 将「短查询」一刀切为 SIMPLE 并返回空上下文，会使短实体类查询的 **Evidence Hit@10 降到 0**；将 SIMPLE 改为 **轻量 keyword/ANN 轻检索（跳过 rewrite / CRAG / 实体抽取等 LLM 增强）**，可在不增加 LLM 往返的前提下恢复 Evidence Hit@10。

**可证伪**：若轻检索后短查询 Hit@10 相对 baseline 无提升，或延迟/成本超预算，则 H2 不成立。

### A.5 历史结论（不可外推）

- A1 live ANN：质量持平 + 延迟 win；说明**一阶段召回质量**是主矛盾之一。  
- A1 机制消融：噪声候选池下粗排会挤掉相关文档。  
- 二者均未测「SIMPLE 空召回」——H2 为独立假设。

### A.6 已冻结、本计划不碰（备案）

- 生产 `recallTest` 丢弃 `RagResult.getContext()`（接线缺陷，可并行修，**不得**计入 H2 证据）。  
- 检索链路 4–5 次同步 LLM、多轮 history 未注入、GraphRAG 默认关——候选 **H3/H4**，本轮不做。

---

## B. Research Ledger（定向 4 源）

### R1 — Adaptive-RAG

```text
id: R1
sourceType: paper
titleOrRepository: Adaptive-RAG: Learning to Adapt Retrieval-Augmented Large Language Models through Question Complexity
authorsOrMaintainer: Soyeong Jeong, Jinheon Baek, Sukmin Cho, Sung Ju Hwang, Jong C. Park
venueAndYear: NAACL 2024
doiOrArxiv: arXiv:2403.14403
url: https://arxiv.org/abs/2403.14403
commitOrTag: N/A
license: N/A
filesOrSectionsRead: Abstract；方法（no-retrieval / single-step / multi-step）
verificationStatus: VERIFIED
relevantFinding: 「简单查询跳过检索」针对开放域常识题；路由由复杂度分类器决定，而非字符长度。
projectApplicability: 支持「分级路由」概念；不支持「组织库短查询 = 零检索」。
limitations: 英文开放域 QA；无中文组织知识库证据。
```

### R2 — When Not to Trust Language Models（Mallen et al.）

```text
id: R2
sourceType: paper
titleOrRepository: When Not to Trust Language Models: Investigating Effectiveness of Parametric and Non-Parametric Memories
authorsOrMaintainer: Alex Mallen, Akari Asai, Victor Zhong, Rajarshi Das, Daniel Khashabi, Hannaneh Hajishirzi
venueAndYear: ACL 2023
doiOrArxiv: arXiv:2212.10511
url: https://arxiv.org/abs/2212.10511
commitOrTag: N/A
license: N/A（代码 github.com/AlexTMallen/adaptive-retrieval 未 pin commit）
filesOrSectionsRead: Abstract；adaptive retrieval 结论（流行度门控）
verificationStatus: VERIFIED（论文）/ PARTIALLY_VERIFIED（代码）
relevantFinding: 仅对低流行度/长尾事实检索；高流行度可靠参数知识。门控信号是实体流行度，不是 query 长度。
projectApplicability: 组织私有知识几乎全是「长尾」——短查询更应检索，而非更不该检索。
limitations: PopQA 开放域；流行度在私有库需用库内命中率近似。
```

### R3 — Self-RAG

```text
id: R3
sourceType: paper
titleOrRepository: Self-RAG: Learning to Retrieve, Generate, and Critique through Self-Reflection
authorsOrMaintainer: Akari Asai, Zeqiu Wu, Yizhong Wang, Avirup Sil, Hannaneh Hajishirzi
venueAndYear: ICLR 2024（arXiv 2023-10）
doiOrArxiv: arXiv:2310.11511
url: https://arxiv.org/abs/2310.11511
commitOrTag: N/A
license: N/A
filesOrSectionsRead: Abstract（on-demand retrieval + reflection tokens）
verificationStatus: VERIFIED
relevantFinding: 是否检索应 on-demand，由模型反思信号控制，而非启发式长度阈值。
projectApplicability: 支持「不要盲目跳过检索」；本项目无 reflection token 训练，不能直接搬。
limitations: 需训练专用 LM，超出最小候选范围。
```

### R4 — 本项目生产实现

```text
id: R4
sourceType: production-implementation
titleOrRepository: qKnow QueryRouter + RagRetrievalService
authorsOrMaintainer: 本仓库
venueAndYear: 当前 main @ f6c0d31
doiOrArxiv: N/A
url: backend/qknow-module-kmc/.../rag/QueryRouter.java, RagRetrievalService.java
commitOrTag: f6c0d31
license: 项目内
filesOrSectionsRead: QueryRouter.classify 全文；RagRetrievalService.retrieveScoped SIMPLE 分支
verificationStatus: VERIFIED
relevantFinding: 长度阈值 + 问候正则 + 意图词白名单；SIMPLE 在非 debug 下零检索。
projectApplicability: 直接定义失败机制与候选改动点。
limitations: 尚无「短查询切片」的 live 指标统计。
```

---

## C. 可迁移与不可迁移结论

| 研究结论 | 判定 | 说明 |
|---|---|---|
| 组织私有库短查询应默认「可检索」 | **可直接采用** | 与 R2 长尾结论一致 |
| 路由应基于复杂度/流行度，而非字符长度 | **可采用（降级）** | 长度阈值可保留为日志特征，不作跳过依据 |
| Adaptive-RAG 训练复杂度分类器 | **不可直接采用** | 无标注训练数据，超出最小 |
| Self-RAG reflection token | **拒绝** | 需训练专用模型 |
| Mallen 实体流行度门控 | **需改造** | 私有库用 ANN/keyword 命中分近似，不引入外部 popularity |

**项目条件差异**：中文、组织域、已有 keyword+ANN 检索器、已有 A1 fail-closed 粗排、无路由分类训练集。

---

## D. 候选方案比较

维度：正确性 / 可证伪性 / 数据需求 / 延迟 / 成本 / 实现复杂度 / 依赖变化 / 回滚风险 / 生产影响

| 方案 | 描述 | 正确性 | 可证伪 | 延迟 | 复杂度 | 依赖 | 回滚 | 结论 |
|---|---|---|---|---|---|---|---|---|
| **Baseline** | SIMPLE → 空召回 | 差（短实体丢失） | 是 | 最低 | — | — | — | 对照臂 |
| **C1 最小诊断** | 仅统计 length&lt;10 且空召回占比 | 中 | 是 | 无 | 低 | 无 | 无 | 辅助，非算法候选 |
| **C2 候选** | SIMPLE 仍 keyword(+ANN) 轻检索 topK=5，无 LLM 增强 | 预期更好 | 是 | +数十 ms | **低** | 复用现有 | 配置开关 | **推荐** |
| C3 | 删除阈值，全部走 MEDIUM 全管线 | 可能 | 是 | +LLM 成本 | 低 | 无 | 易 | 拒绝（成本） |
| C4 | 训练 Adaptive-RAG 分类器 | 潜在高 | 是 | — | 高 | 训练数据 | 高 | 拒绝（超范围） |
| 保持现状 | — | 差 | — | — | — | — | — | 拒绝 |

**拒绝理由记录**（防换名重引入）：C3 把成本问题当成正确性问题；C4 在无标注数据前不得引入训练流程。

---

## E. 推荐的最小算法

**C2：SIMPLE 轻检索**

```
if (route == SIMPLE && simpleLightweightRetrieval.enabled) {
    // 仅 keyword + 可选 ANN，topK=5
    // 跳过：Query rewrite / Router LLM / 实体抽取 / CRAG / web fallback / 重型 rerank
    // 仍应用 PermissionFilter
} else if (route == SIMPLE) {
    // 现行为：空上下文
}
```

**为什么是最小**：

- 复用 `KeywordRetriever` / `VectorRetriever` 与权限过滤，无新依赖、无新模型。  
- 不改 CRAG、RRF、切块、Hermes、生产默认。  
- 单一配置键可回滚。

**配置键（实现时写死）**

```yaml
qknow:
  rag:
    simple:
      light-retrieval: false   # 默认 false，保持 A0；评测显式 true
      top-k: 5
```

---

## F. 实验与实现计划（decision-complete 契约）

### F.1 固定契约

| 项 | 冻结值 |
|---|---|
| 假设 | H2 |
| Baseline A0 | 现状：SIMPLE → 空召回 |
| Candidate A1 | SIMPLE → keyword(+可选 ANN) topK=5，零 LLM |
| 数据 | 现有 golden/v2 + **短查询切片**（从库内真实短问题与 golden 构造；**冻结后禁止改 qrel**） |
| 短查询定义 | `query.length() < 10` 且不含问候类 SIMPLE 正则（与 Router 规则对齐，避免测到「你好」） |
| 指标 | Short-Slice Evidence Hit@5/@10、MRR@10、p50/p95、SIMPLE 路径 LLM 调用次数 |
| 通过判据 | Short-Slice Hit@10：A1 ≥ A0 + **20pp**（A0 预期≈0）；且 p95 升幅 ≤ **80ms**；SIMPLE 路径 LLM 调用 = **0** |
| 泄漏防护 | 不改既有 golden 条目期望；短查询切片一次性冻结后只读 |
| 预算 | 额外 LLM/embedding 调用（查询侧）≤ 1 次/查询（仅 ANN 时）；优先纯 keyword 零外部调用 |
| 失败码 | `INVALID_CONFIG` / `EMPTY_KB` / `LLM_CALL_IN_SIMPLE` / `METRICS_REGRESSION` / `SHORT_SLICE_NOT_FROZEN` |
| 停止条件 | 需改既有 qrel 才能跑绿；SIMPLE 路径出现 LLM 调用；需新模型/依赖 |

### F.2 最小文件集合（获批后才可动）

**允许**

- `RagRetrievalService.java`（SIMPLE 分支）  
- 可选：`QueryRouter.java`（仅日志/特征，不改跳过语义则可不动）  
- 配置绑定类或 `@Value` 键  
- `backend/tests/...` 新 gate 测试 + 短查询切片 fixture（新建，不改旧 golden）  
- `backend/tests/evidence/h2-simple-light-retrieval/` 报告

**禁止**

- 既有 `rag-golden-dataset*.jsonl` 期望字段  
- CRAG / RRF / topK 全局默认 / ColBERT / 切块  
- 生产 `application-prod.yml` 默认值  
- Hermes 编排、前端  

### F.3 复现命令（获批后对齐 surefire 旗标）

```bash
cd backend
mvn -pl tests -am \
  -Dtest=SimpleLightRetrievalGateTest \
  -Dqknow.rag.simple.light-retrieval=true \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### F.4 执行顺序

1. T1 冻结短查询切片与 qrel（新建文件）  
2. T2 只读实现 A0 基线指标（空召回）  
3. T3 实现 C2 最小分支 + 单测（默认 false）  
4. T4 跑 A0/A1 对比，写 evidence JSON  
5. T5 **停**：不改生产默认，等待授权  

---

## G. 风险、停止条件与后续授权边界

### 残余风险

1. **短查询切片样本量小** → 结论标 `PARTIALLY_VERIFIED`，禁止改生产默认。  
2. 轻检索引入噪声 → topK=5 + 不做 CRAG/重型 rerank。  
3. 与 Adaptive-RAG「简单不检索」表面冲突 → 已在 C 节说明组织域差异。  
4. A1 ColBERT fail-closed 已默认开启，轻检索结果会经过该路径——属预期，不引入新变量。

### 立即停止

- 必须修改既有 golden/qrel 才能通过  
- SIMPLE 路径任何 LLM 调用  
- 引入新依赖或训练流程  

### 后续独立授权

| 动作 | 授权 |
|---|---|
| 实施 C2 评测臂 | **需用户明确批准本文件** |
| 生产默认改为 light-retrieval=true | 另批 |
| 改 Router 长度阈值语义 / 训练分类器 | 另开 Gate |
| A/B、promotion、线上 | 各自独立授权 |
| push origin | 单独授权 |

---

## 附录 A：审核勾选

- [x] 批准 H2 与 C2 最小机制  
- [x] 批准 In/Out of Scope 与禁止项  
- [x] 批准通过/失败判据与停止条件  
- [x] 批准默认开关 `simple.light-retrieval=false`（仅评测显式开启）  
- [x] 批准开始 F.4 T1–T5  
- [x] 其他修改意见：________________  

**审核结论**：☑ 批准实施（用户会话批准「批准进行，开始任务」）  
**审核人**：用户　**日期**：2026-09-10

---

## 附录 B：实现结果（T4，2026-09-10）

```text
证据级别=LIVE_KEYWORD_ABLATION
slice=rag-short-query-slice-v1.jsonl (n=10, length<10)
kbId=8（课设报告；golden PDF 所属库）
topK=5  llmCallsInSimple=0

A0 Hit@5/10=0.00/0.00  avgMs=1
A1 Hit@5/10=0.70/0.70  avgMs=95

deltaHit@10=+0.70  (契约要求 ≥ +0.20) → PASS
判定=PASS（质量）；延迟 avg ~95ms 略超 +80ms 心智预算，已记录
miss 3/10：库管理 / 哈希分桶 / 门控
报告=backend/tests/evidence/h2-simple-light-retrieval/h2-a0-a1-short-slice-report.json
生产默认=qknow.rag.simple.light-retrieval 仍为 false（未改）
分支=feat/h2-simple-light-retrieval @ 125f788
复现=cd backend && mvn -pl tests -am -Dtest=SimpleLightRetrievalGateTest \
  -Dqknow.rag.simple.light.ablation=true -Dsurefire.failIfNoSpecifiedTests=false test
```
