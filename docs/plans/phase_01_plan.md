# Phase 01: RAG 评估地基建设 (Q1 & Q2)

> **前置要求**: 依据 `AGENTS.md` Research-to-Implementation Gate 门禁标准制定。
> **目标阶段**: RAG-v2 的基础，涵盖 Q1 (Holdout 扩容与分层) 与 Q2 (生成侧评估)。

## 1. 文献支撑与技术选型依据

### 1.1 学术理论依据 (Academic Evidence)
根据顶级会议论文 (ARES, RAGAS, CRUD-RAG) 的前沿理论，当前系统的常规 Hit@10 指标已在小样本上过拟合失效。
*   **难负例挖掘 (Hard Negative Mining)**: 高质量的验证集必须具有区分度。通过注入处于决策边界的“难负例”（例如 BM25 分数极高但语义无关的干扰项），能够打破 Hit@10 的分数饱和现象，暴露出弱检索器的缺陷。
*   **主张分解算法 (Claim-Decomposition)**: 为了精准捕捉生成侧的“幻觉”，克服粗粒度打分容易产生混淆的缺点，采用 RAGAS 提出的主张分解算法。大模型生成的一大段回复将被拆解为多个原子断言 (Claims)，并严格对比检索上下文来计算忠实度 (Faithfulness) 和引用准确率。
*   **文档分层与指代消解 (CDCR)**: 引入 CRUD-RAG 的分类理论，强制在测试集中添加跨文档（Cross-document）与包含代词的测试用例。

### 1.2 工业界落地实践 (Engineering Practices)
*   **流水线门禁 (CI/CD Quality Gate)**: 黄金数据集 (Golden Dataset) 必须抛弃 Excel 维护，转为像代码一样被版本控制。并将评测框架（如 DeepEval / Ragas）接入 GitHub Actions，设置指标红线（如 `Faithfulness > 0.85`）。只要发生指标回归，强行拦截 PR 阻断合码。
*   **降本增效的可观测架构**: 引入原生兼容 OTel 协议的 **Arize Phoenix** 作为链路追踪和指标大盘；弃用昂贵的闭源模型进行打分，改为本地私有化部署的小模型裁判（Model-as-a-Judge，如 Llama-3-8B-Instruct），降低 90% 的评测成本。
*   **Q0 脱敏清洗入库**: 在线上的真实查询数据流入测试库之前，进行严格的 NER 脱敏和指代消解，并通过聚类抽样以防止数据倾斜。

---

## 2. 模块接口与数据流设计

### 2.1 Golden Dataset 数据结构定义
```json
{
  "id": "q_001",
  "query": "经过脱敏与重写的独立疑问句",
  "category": "cross-doc",
  "expected_contexts": ["doc_uuid_1", "doc_uuid_2"],
  "hard_negatives": ["doc_uuid_3", "doc_uuid_4"], // 强制注入的干扰陷阱
  "expected_output_claims": ["断言1", "断言2"]
}
```

### 2.2 评估数据流拓扑
1. **触发流**: 开发者提交包含检索或 Prompt 修改的 PR -> CI 拉取最新的 Golden Dataset (带难负例)。
2. **执行流**: 遍历测试用例 -> 发起 RAG 检索并生成回复 -> 核心链路指标通过 OTel 异步上报至 Arize Phoenix。
3. **评测流**: 调用本地裁判大模型运行 `ClaimFaithfulnessEvaluator` -> 将生成文本拆解为多个 Claim -> 分别与 `expected_contexts` 比对映射。
4. **决策流**: 聚合输出 `d-Hit@10` 和 `Citation Accuracy` 分数 -> 若低于基线，触发 Exit Code 1 阻断 CI。

---

## 3. 细化编码待办事项 (TDD To-Dos)

#### [NEW] `deploy/sql/postgresql/05-eval-dataset.sql`
*   **任务**: 定义用于容纳包含 `hard_negatives` 干扰项的 Golden Dataset 数据库表结构，并写入首批抽样的基准测试数据。

#### [NEW] `backend/src/main/java/tech/qiantong/qknow/hermes/eval/ClaimFaithfulnessEvaluator.java`
*   **任务**: 实现学术理论中的主张分解 (Claim-Decomposition) 算法。包含拆解 Prompt 构造、LLM 结果解析，以及严格计算基于支持 Claims 比例的忠实度分数。

#### [NEW] `scripts/build_hard_negatives.py`
*   **任务**: 自动化构建脚本，离线扫描已有知识库，针对给定的黄金问题，挖掘出最容易混淆的 Top-5 干扰文档写入 `hard_negatives` 字段。

#### [NEW] `.github/workflows/rag_eval_gate.yml`
*   **任务**: 编写 GitHub Actions 流水线，在 Pull Request 时触发评测命令，对退化现象执行硬拦截。

---

## 4. 验收准则与测试规范 (Acceptance Criteria)

### 4.1 单元测试准则 (TDD Unit Tests)
*   **`ClaimFaithfulnessEvaluatorTest.java`**: 必须注入两段“已知含幻觉文本”与“完全忠实文本”进行 Mock。断言算法能正确拆解出目标数量的 Claims，且最终得分计算的绝对误差控制在允许范围内。
*   **`build_hard_negatives.py` 测试**: 验证脚本是否能够在不遗漏真值的前提下，成功选取出具备 BM25 高分但语义不相关文档作为干扰项。

### 4.2 集成测试与流水线准则 (Integration & CI Gate)
*   **陷阱区分度**: 运行 `./scripts/get_eval.sh` 时，混入难负例后的 `d-Hit@10` 必须能被有效拉低，打破基线分数饱和（如 Hit@10=1.00 的假象）。
*   **CI 拦截闭环**: 伪造一次退化的模型结果，验证 CI/CD 流程必定失败（Failed）并正确阻断。
*   **裁判模型可信度校验**: 抽样跑出 20 条测试结果数据，其机器评分与人工 SME 盲评的 **Kappa 一致性系数必须 $\ge$ 0.75**，否则判定评测机制本身不合格。
