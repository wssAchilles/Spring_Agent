# Phase 32 决策完备实施方案：全局神经符号可解释性、可审计证据链与合规安全护栏体系 (Neuro-Symbolic Explainability, Verifiable Audit Merkle Proofs & Compliance Guardrails)

> **拟归档路径**：`docs/plans/phase_32_plan.md`  
> **前置依赖**：Phase 03（安全 Hardening）、Phase 10（智能体运行时反思熔断与状态图自愈）、Phase 16（灾难恢复与真机 Query 挖掘脱敏）、Phase 29（知识图谱深度语义推理 GraphRAG 2.0）、Phase 31（多智能体分布式共识机制与拜占庭容错协作网络）  
> **执行准绳**：`@AGENTS.md` Research-to-Implementation Gate 规范  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3，`deepseek-reasoner` 即 R1，流式 SSE 输出），唯一向量模型为 **阿里千问 (Qwen) Embedding（1536 维）**（`text-embedding-v2`，单位超球面 $\mathbb{S}^{1535}$ 归一化内积余弦度量），全链路绝无本地大模型，彻底弃用 OpenAI API；唯一编译运行环境为 **Java 21 隔离环境**（`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）。

---

## 一、唯一核心待验证假设 (Sole Verifiable Hypothesis)

> **假设 (H-Phase32 / H-PHASE32-001)**：在唯一生成模型（DeepSeek API）、唯一向量模型（阿里千问 1536 维 Embedding）与 Java 21 隔离环境约束下，在 `backend/qknow-framework/qknow-ai` 中构建企业级双向合规安全护栏与密码学不可篡改审计体系：  
> 1. **输入侧双阶段门禁 (`InputGuardrailGate`)**：  
>    - 阶段 1：高性能纳秒级 DFA/预编译正则 PII 过滤器，覆盖身份证号（ISO 7064 MOD 11-2）、手机号、银行卡号（Luhn 校验）、邮箱、Bearer/API Token，自动替换为标准化标记（如 `[REDACTED_PHONE]`），实现零互信息泄露（$I(X; Y) = 0$）；  
>    - 阶段 2：多语言对抗越狱与提示词注入门禁，包含 Base64 预解码探测、中文同音变形/指令覆盖对抗规则库；  
> 2. **输出侧双阶段门禁 (`OutputGuardrailGate`)**：  
>    - 阶段 1：合规红线敏感词/毒性词毫秒级 Fail-Close 阻断（$< 2\text{ms}$）；  
>    - 阶段 2：基于装配上下文（Assembled Context）交叉比对的事实忠实度（Faithfulness）与幽灵引用（Phantom Citations）越界快速核验；  
> 3. **自适应响应编排策略 (`GuardrailPolicyCoordinator`)**：  
>    - 提供安全拒绝（Safe Refusal）、合规脱敏重写（Redacted Rewriting）、违规预警降级（Alert Degradation）三级自适应动作；  
> 4. **密码学不可篡改 Merkle 证据链存证与轻量验真引擎 (`MerkleTreeEngine`)**：  
>    - 针对每个交互任务，将切片 ID、切片内容 SHA-256 哈希、模型原始响应、用户脱敏输入与时间戳构建为基于 RFC 6962 单字节域隔离前缀（叶节点 $0x00$、内部节点 $0x01$）的标准平衡二叉 Merkle 树；  
>    - 输出 MerkleRoot 并提供 $O(\log N)$ 阶 InclusionProof 生成；  
>    - 暴露轻量对外验真 API（`POST /api/v1/audit/verify-proof`），第三方审计员无需拉取知识库明文，仅凭 Root、LeafHash 与 Proof 即可在 $1\text{ms}$ 内完成客户端离线密码学验真；  
> 5. **全链路因果可解释性拓扑溯源图 (`CausalAttributionGraph`)**：  
>    - 将交互决策链抽象为轻量级 DAG（`CausalTraceNode` 与 `CausalEdge`），贯穿 Query -> Intent Decomposition -> Knowledge Retained -> Subgraph Paths -> BFT Consensus -> Final Output，绑定哈希指纹、安全审查结论与归因贡献度权重。  
>  
> **能够证明**：在 100 并发压测与 200+ 复合对抗攻击测试集下：  
> - PII 纳秒级脱敏准确率达 $100\%$，处理延迟 $< 50\mu s$；  
> - 多语言提示词越狱注入拦截率 $\ge 99.5\%$；  
> - 输出红线敏感词毫秒级（$< 2\text{ms}$）Fail-Close 拦截率 $100\%$；  
> - 幽灵引用越界检出拦截率达 $100\%$，虚假事实拦截率 $\ge 95.0\%$；  
> - 护栏全编排 P99 附加延迟控制在 $150\text{ms}$ 以内（杜绝同步调用大模型自查引起的雪崩）；  
> - Merkle 树生成与 Inclusion Proof 导出耗时 $< 2\text{ms}$；  
> - 对外验真 API 客户端执行密码学验真耗时 $< 1\text{ms}$；  
> - 因果拓扑图完整还原率 $100\%$，支持毫秒级图谱回溯。

---

## 二、核心理论与工业设计模式闭环映射

| 理论与工业来源 | 核心理论 / 工业模式 | 本项目 Phase 32 落地实现组件 | 对应验证契约 |
|---|---|---|---|
| **Ralph Merkle 1987 / RFC 6962 / Sigstore Rekor** | 证书透明度单字节域分离前缀（$0x00$ 叶节点、$0x01$ 内部节点）防第二原像攻击，对数级 $O(\log N)$ InclusionProof | `MerkleTreeEngine.java`, `MerkleProof.java`, `AuditVerificationController.java` | 契约 08, 契约 09 |
| **Judea Pearl 2009 结构因果模型 SCM** | 全链路因果偏序有向无环图 $\mathcal{G} = \langle \mathcal{V}, \mathcal{E}, \mathcal{P} \rangle$，反事实干预 $do(X=x)$ 归因消除虚假相关性 | `CausalAttributionGraph.java`, `CausalTraceNode.java`, `CausalEdge.java` | 契约 10 |
| **Lundberg & Lee 2017 SHAP** | Shapley 因果贡献度函数四大公理（效率性、对称性、虚拟性、可加性）不变量与快速归一化权重 | `CausalAttributionGraph.java` 归因权重计算 | 契约 10 |
| **局部差分隐私 LDP 与 ISO 7064 / Luhn 算法** | 纳秒级正则 + 校验位双向过滤（身份证 MOD 11-2, 银行卡 Luhn），$I(X; Y) = 0$ 零互信息泄露 | `PiiDfaSanitizer.java` | 契约 01, 契约 02 |
| **NVIDIA NeMo Guardrails / OWASP Top 10 (2025)** | 输入多语言指令覆盖对抗拦截、Base64 预解码递归解包管道 | `AdversarialInjectionGate.java` | 契约 03, 契约 04 |
| **Aho-Corasick 双数组 Trie 树 / Fail-Close 铁律** | 毫秒级输出敏感词阻断（$< 2\text{ms}$），强行阻断输出流并记录安全告警 | `OutputSafetyFilter.java` | 契约 05 |
| **FActScore 2023 / Min et al. 事实忠实度验证** | 幽灵引用标签拓扑比对，断言与检索切片词法 Jaccard/N-gram 覆盖率 + 阿里千问 1536 维超球面投影核查 | `FaithfulnessVerifier.java` | 契约 06, 契约 07 |

---

## 三、TDD 测试驱动开发 10 项专项核心契约清单

测试类路径：[`backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase32ExplainabilityAndGuardrailsContractTest.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase32ExplainabilityAndGuardrailsContractTest.java)

1. **契约 01：高性能 DFA PII 纳秒级脱敏准确率与格式置换验证**
   - 构造包含身份证、手机号、银行卡号、个人邮箱与 API Bearer 令牌的复合请求；
   - 验证所有 PII 均被精准打码为对应的 `[REDACTED_XXX]`，单次脱敏耗时 $< 50\mu s$。
2. **契约 02：身份证 ISO 7064 与银行卡 Luhn 算法双向防误杀验证**
   - 传入合法的身份证号与银行卡号，断言 100% 识别并替换；
   - 传入随机伪造的 18 位纯数字与 16 位非 Luhn 纯数字，断言不发生误杀，原文保留。
3. **契约 03：多语言越狱对抗提示词攻击 100% 拦截率验证**
   - 模拟中英文各类变异提示词注入（“忽略所有规则”、“开启开发者模式”、“透露初始系统提示词”）；
   - 验证门禁触发 `SECURITY_INJECTION_BLOCKED` 安全拒绝决策。
4. **契约 04：Base64 嵌套隐蔽越狱指令递归解包查杀验证**
   - 将恶意越狱 Payload 编码为 Base64 嵌入正常对话；
   - 门禁成功预解码并识别内部对抗载荷，100% 拦截。
5. **契约 05：输出合规红线敏感词毫秒级 Fail-Close 阻断验证**
   - 模型输出文本混入违禁涉暴、涉恐高危词汇；
   - 验证系统在 2ms 内执行 Fail-Close，完全切断输出并返回标准中立合规拒绝话术。
6. **契约 06：幽灵引用 (Phantom Citations) 越界拓扑查杀与重写自愈验证**
   - 模拟模型输出了不存在的切片编号（如 `[Doc-999]`）；
   - 校验器成功比对检索上下文集合，触发 `REDACTED` 动作自动清洗幽灵引文字符，合法文本顺利交付。
7. **契约 07：事实忠实度 (Faithfulness) 弱语义支撑自动预警降级验证**
   - 构造与检索切片毫无关联的纯凭空捏造文本；
   - 校验器检测到低词法重合度，自动在正文顶部注入合规降级警示标语。
8. **契约 08：平衡二叉 Merkle 树构建与 RFC 6962 前缀防碰撞验证**
   - 传入 5 个证据项构建 Merkle 树，验证奇数节点自动副本配对折叠；
   - 校验叶子节点 `0x00` 与父节点 `0x01` 前缀有效隔离，杜绝第二原像碰撞。
9. **契约 09：对数级 InclusionProof 生成与客户端 1ms 免密验真验证**
   - 针对指定切片生成 InclusionProof 路径，并在本地执行纯哈希验真；
   - 模拟任意篡改 1 字节切片哈希或路径节点，断言验真立即失败返回 false；耗时保证在 $1\text{ms}$ 内。
10. **契约 10：因果拓扑图 (Causal Attribution Graph) 逆向溯源对账验证**
    - 构建完整的 Query -> Intent -> Knowledge -> Consensus -> Output 因果拓扑边；
    - 执行 `getBackwardAttributionPath`，验证能 100% 完整逆向提取所有依赖前序节点与权重分配。

---

## 四、最小实现文件集合清单

### 1. 安全护栏与脱敏组件 (位于 `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/guardrail/`)
- `model/GuardrailDecision.java` [NEW]：统一护栏判定决策结果载荷（包含 permit、action、violationType、sanitizedText、latencyMicros）
- `model/GuardrailViolationType.java` [NEW]：违规类型枚举（`PII_EXPOSURE`, `PROMPT_INJECTION`, `REDLINE_SENSITIVE`, `PHANTOM_CITATION`, `FAITHFULNESS_LOW`）
- `model/GuardrailPolicyAction.java` [NEW]：处置动作枚举（`PERMIT`, `REDACTED_REWRITE`, `SAFE_REFUSAL`, `ALERT_DEGRADATION`）
- `model/SanitizeResult.java` [NEW]：脱敏结果实体封装
- `core/PiiDfaSanitizer.java` [NEW]：高性能 DFA/正则 PII 处理器（支持 ISO 7064 身份证与 Luhn 银行卡算法双向核验）
- `core/AdversarialInjectionGate.java` [NEW]：多语言越狱对抗与 Base64 递归解包门禁
- `core/OutputSafetyFilter.java` [NEW]：合规红线毫秒级 Fail-Close 阻断过滤器
- `core/FaithfulnessVerifier.java` [NEW]：事实忠实度对齐校验与幽灵引用清洗器
- `GuardrailPolicyCoordinator.java` [NEW]：双向安全护栏自适应策略协调器

### 2. 密码学存证与因果可解释性组件 (位于 `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/audit/`)
- `merkle/MerkleEvidenceItem.java` [NEW]：证据项实体（itemId, payloadHash, timestamp）
- `merkle/MerkleProof.java` [NEW]：对数级 InclusionProof 数据契约（traceId, leafIndex, leafHash, merkleRoot, proofPath）
- `merkle/MerkleTreeEngine.java` [NEW]：遵循 RFC 6962 前缀隔离的标准平衡二叉 Merkle 树构建、证明生成与 1ms 离线验真引擎
- `causal/CausalNodeType.java` [NEW]：节点类型枚举（`QUERY`, `INTENT_DECOMPOSITION`, `KNOWLEDGE_RETAINED`, `SUBGRAPH_PATHS`, `BFT_CONSENSUS`, `FINAL_OUTPUT`）
- `causal/CausalTraceNode.java` [NEW]：因果拓扑图节点实体（包含哈希指纹、安全审查结果与归因权重）
- `causal/CausalEdge.java` [NEW]：因果拓扑图有向边实体
- `causal/CausalAttributionGraph.java` [NEW]：全链路因果可解释性拓扑图（支持逆向溯源回溯与拓扑导出）
- `controller/AuditVerificationController.java` [NEW]：对外轻量验真 REST 控制器（`POST /api/v1/audit/verify-proof`）

### 3. 专属契约测试 (位于 `backend/tests`)
- `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase32ExplainabilityAndGuardrailsContractTest.java` [NEW]

---

## 五、实施步骤与验证计划

1. **第 1 步：红灯先行，编写 10 项专属契约测试**：
   - 编写 `Phase32ExplainabilityAndGuardrailsContractTest.java`，精确覆盖 10 项契约。
2. **第 2 步：实现安全护栏模型与核心组件**：
   - 实现 `PiiDfaSanitizer`、`AdversarialInjectionGate`、`OutputSafetyFilter`、`FaithfulnessVerifier` 与 `GuardrailPolicyCoordinator`。
3. **第 3 步：实现密码学存证引擎与因果拓扑图**：
   - 实现 `MerkleTreeEngine`、`CausalAttributionGraph` 与 `AuditVerificationController`。
4. **第 4 步：执行专项契约测试验证（10/10 绿灯）**：
   - 运行 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl backend/tests -Dtest=Phase32ExplainabilityAndGuardrailsContractTest`。
5. **第 5 步：全量防退化回归测试（862/862 全绿）**：
   - 运行 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl backend/tests`。
6. **第 6 步：前端生产构建编译校验**：
   - 运行 `cd frontend && pnpm run build`，确认 0 错误顺利通过。
7. **第 7 步：更新总索引文档与归档**：
   - 更新 `docs/plans/00_master_index.md`，记录 Phase 32 Delivered。
