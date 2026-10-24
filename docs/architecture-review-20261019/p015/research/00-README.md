# P015 多智能体生产编排接入科研与系统架构总报告索引 (00-README)

## 一、元数据与基本信息

- **项目名称**：企业级 AI-Native RAG 知识库与 Hermes 智能体编排平台 (QKnow / Hermes)
- **当前代码基线 (Git Commit)**：`b6516ee3`
- **评审日期**：2026-10-22
- **当前角色**：系统架构契约与最小算法适配员 (System Applicability & Minimum Gate Specialist)
- **唯一待验证假设 (Hypothesis ID: P015-MULTI-AGENT-PRODUCTION-WIRING)**：  
  > “默认单 Agent 路径完全不变；SWARM（SupervisorAgent）与最小 DEBATE 作为按 Bot 配置、默认关闭的编排策略接入 AgentOrchestrator，可测量提升复杂问题质量，p95 延迟 ≤3×、token 成本 ≤3×、默认路径零回归。”

---

## 二、三份法医级研究报告清单与状态

| 阶段 / 报告 | 角色 | 报告绝对路径 | 报告状态 (Status) | 核心凭证与产出 |
|---|---|---|:---:|---|
| **A-theory (理论文献)** | 前沿理论调研员 | `/Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/p015/research/A-theory.md` | **COMPLETE** | `A-theory.ledger.json` (6 个权威文献来源全部 VERIFIED) |
| **B-code-audit (代码法医)** | 代码法医核验员 | `/Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/p015/research/B-code-audit.md` | **COMPLETE** | `B-code-audit.evidence.jsonl` (16 条源码级证据全部 VERIFIED) |
| **C-architecture (架构契约)** | 架构契约适配员 | `/Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/p015/research/C-architecture.md` | **COMPLETE** | `C-architecture.decision.json` (全要素决策完备契约) |

---

## 三、来源总数与验证状态分布

严格落实 AGENTS.md 取证前置铁律（Reading-First Rule），杜绝二手推断与未验证假设：
- **来源总数 (Total Evidences & Sources)**：**22 项**（6 篇顶级学术文献/官方规范 + 16 条生产与测试源码法医凭证）
- **验证通过 (VERIFIED)**：**22 项** (100.0%)
- **部分验证 (PARTIALLY_VERIFIED)**：**0 项**
- **未验证 (NOT_VERIFIED)**：**0 项**

---

## 四、最重要的 5 条核心定论

1. **确凿的“零生产调用”事实与控制面微服务断点**：  
   `SupervisorAgent`、`StructuredDebateCoordinator`、`MoAExpertConsensusEngine` 等在当前生产调用链（Controller → Service → gRPC → AgentOrchestrator）中的**生产调用点数量确凿为 0**；线上流量 100% 运行于单 `ReactAgent`。断流根因在于服务层 `KbAgentConfigServiceImpl` 组装 gRPC `ChatRequest` 时丢弃了控制面 parameters，且 proto 缺失承载字段。
2. **C 类伪仿真组件永久否决令**：  
   代码库中残留的 `StructuredDebateCoordinator`、`MoAExpertConsensusEngine`、`DefaultConsensusArbiterEngine`、`TripartiteDebateArbiterEngine` 内部存在关键词启发式假打分、字符串假 Hash，且严重锁死 1536 维超球面单位向量。真实生产使用阿里 `text-embedding-v4`（1024 维），硬接上述类将直接抛出致命异常。本评审予以**永久性架构否决**，严禁引入生产。
3. **4 个文件最小 Seam 确保默认路径 100% 零回归**：  
   仅需微调 `hermes.proto`（扩展枚举与参数）、`KbAgentConfigServiceImpl.java`（透传配置）、`AgentOrchestrator.java`（策略路由与 Fail-Open 降级）和 `SupervisorAgent.java`（修复跨请求黑板污染），即可完成全链路打通。不改数据库 Schema、不改前端、不增外部依赖。所有未配置或配置非 SWARM 的 Bot 100% 走既有单 Agent 路径，保障零功能与零性能回归。
4. **分阶段两级跳与严格预注册指标**：  
   Phase 1 聚焦 **SWARM 最小接线**（并发 Worker ≤ 3，硬超时 15s），通过 Kahn 拓扑拆解大幅提升复杂问题覆盖面；Phase 2 作为候选探索 **最小三角色真实 LLM 辩论（DEBATE）**。预注册成功门禁锁定：SWARM 质量相对 baseline 必须绝对提升 ≥ 10%，单题无显著劣化（下降 ≤ 1 分），P95 延迟 ≤ 3×（≤ 15s），Token 成本 ≤ 3×（单次 ≤ 0.05 元），降级率 < 5%。
5. **Context Caching 奠定成本护城河**：  
   理论推导与 DeepSeek 官方 API 规约证明，只要保持 Supervisor 与各 Worker 的 System Prompt 及前置上下文严格前缀对齐，即可激活官方 Context Caching（命中价格仅为未命中的 1/7），使多智能体多次 LLM 调用的实际 Token 成本倍率受控在 1.5×~2.2×，坚固守住 3× 成本红线。

---

## 五、准入裁决与实施授权边界

- **Research Gate 准入裁决**：**【全面通过 (RESEARCH_GATE_PASSED)】**。
  已追踪真实项目路径，锁定唯一可证伪假设，完成 22 项全量 VERIFIED 取证，形成了决策完备（Decision-Complete）的实施契约。
- **是否允许直接进入实施**：**【已具备实施就绪性 (Implementation Ready)，等待用户最终批准】**。
  根据 AGENTS.md 实施纪律，首轮只读研究结束，已冻结全部契约。在获得用户针对 4 文件修改范围与评测计划的明确授权前，保持只读，不触碰任何业务代码。
