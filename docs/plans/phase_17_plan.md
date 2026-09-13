# Phase 17: 多知识库联合检索并发编排、跨库得分校准重排、多租户 RBAC 零泄露隔离与统一全局上下文预算熔断治理实施计划 (P3 + Q0)

> **依据**：`AGENTS.md` Research-to-Implementation Gate 规范  
> **前置调研**：学术向智能体 (`cd48dcd7`) 与工程向智能体 (`586c0a34`) 双向并发深度科研已闭环完成  
> **学术报告**：`docs/plans/phase_17_academic_report.md` (5 篇顶会文献，包含 Cormack et al. RRF 尺度无关性与排序单调性定理、Dean & Barroso 极值排队延迟削峰定理、Fork-Join Queue 对数级加速比证明 $\Theta(N/\ln N)$、软超时截断 $\tau_{\text{soft}} = 2500\text{ms}$ 下信息损失期望有界定理 $\le 0.8\%$、Bell-LaPadula 格与 Goguen-Meseguer 非干涉性零泄露定理)  
> **工程报告**：`docs/plans/phase_17_industrial_report.md` (LlamaIndex MultiIndexRetriever, LangChain EnsembleRetriever, Dify 多库编排模式、Milvus/Qdrant 多租户隔离实践、`MultiKbRetrievalCoordinator` 仓壁隔离与 Fail-Open 超时降级、`CrossKbScoreCalibrator` 全局 RRF 精排与 20KB 预算硬截断、`PermissionFilter` 彻底消除管理员返回 `null` 漏洞与语义缓存权限加盐哈希、三大生产级灾难避坑指南)  
> **方案文档**：`docs/plans/phase_17_plan.md`  
> **唯一模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3 / `deepseek-reasoner` 即 R1），唯一向量模型为 **阿里千问 (Qwen) Embedding (1536维)**，绝无本地大模型，彻底弃用 OpenAI/GPT API。  
> **当前状态**：**DESIGNED / PENDING_USER_APPROVAL**（第一回合只读检查与方案设计完成，待用户明确批准后进入 TDD 实施）

---

## User Review Required

> [!IMPORTANT]
> **本阶段核心创新与生产治理价值**：
> 1. **跨多知识库非阻塞异步并发召回编排器 (`MultiKbRetrievalCoordinator.java`)**：
>    - 彻底根治 `KbAgentConfigServiceImpl:262` 中串行 `forEach` 遍历多知识库导致的延迟随知识库数量 $N$ 呈 $\mathcal{O}(N)$ 线性累加（原 5 库串行耗时 > 5.5s）；
>    - 引入基于 `CompletableFuture` 的反应式异步编排，配备独立线程池仓壁隔离（Bulkhead Isolation: 核心 16, 最大 32, 有界队列 200），阻断单一模块耗尽全局连接池；
>    - 落地 **单库 2500ms 软超时降级 (Fail-Open Soft Timeout)** 与 **全局 3000ms 硬超时截断**：单个慢库故障仅触发本库空列表降级，主流程毫秒级返回其余健康库结果，网关 10s 超时彻底归零；
> 2. **跨库打分校准、全局 RRF 精排与 20KB 统一预算截断治理 (`CrossKbScoreCalibrator.java`)**：
>    - 解决异构知识库由于容量差异（$10^6$ vs $10^3$）与分块粒度带来的**得分分布漂移（Score Distribution Shift）**与大库极值抽样虚高偏差；
>    - 引入置信度底线门禁（Score Floor $\ge 0.40$）+ 倒数排名融合（RRF, $k=60$），实现跨库排序的尺度无关性（Scale-Invariance）与帕累托最优；
>    - 全局内容指纹（MD5）去重，统一跨库溯源格式 `[来源 X] [KB: 知识库名称] 文档名 / segmentId=XXX`；
>    - 严格受限于全局 20KB (20,000 字节) 统一硬预算控制，杜绝多库上下文叠加撑爆大模型 Prompt（解决“大海捞针”迷失与 Token 浪费）；
> 3. **强类型 Fail-Closed 权限漏斗与语义缓存防越权窃密 (`PermissionFilter.java` & `EnhancedSemanticCacheService.java`)**：
>    - 彻底修复原代码中管理员角色判定时直接返回 `null` 导致的跨 Workspace/跨租户越权读取漏洞，签名永远返回非空 `List<Long>`，绝不返回 `null`；
>    - 坚决将 `sales` 移出管理员特权名单；
>    - 实施 **租户与权限强类型三元交集过滤**：$KB_{\text{final}} = KB_{\text{requested}} \cap KB_{\text{workspace}} \cap KB_{\text{authorized}}$，交集为空时构造绝对不命中条件（`1 == 2`）并立即短路（Fail-Closed）；
>    - 语义缓存 Key 全面接入密码学权限指纹（HMAC-SHA256 加盐哈希），杜绝普通员工通过语义缓存时序与内容侧信道命中高权限敏感问答。

---

## Proposed Changes

### Component 1: 跨知识库异步并发召回编排器 (qknow-module-kb)

#### [NEW] [MultiKbRetrievalCoordinator.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/retrieval/MultiKbRetrievalCoordinator.java)
- 基于 `CompletableFuture` 实现多知识库并发分发 (Fork) 与聚合 (Join)；
- 独立仓壁隔离线程池：创建或注入 `multiKbRetrievalExecutor`（核心 16, 最大 32, 有界等待队列 200, 饱和丢弃降级）；
- 单库 2500ms 软超时降级：利用 `completeOnTimeout` 注入空召回 fallback 对象，不阻断主流程；
- 全局 3000ms 硬超时截断：捕获 `TimeoutException` 时提前截断未完成任务并返回部分成功结果；
- 埋点接入 `RagFallbackMonitor.record("multi_kb_coordinator", ...)` 记录降级与超时事件；
- 单库异常安全捕获：记录 error 日志并降级为空结果，不向外抛出异常。

---

### Component 2: 跨库得分校准与全局 RRF 精排引擎 (qknow-module-kb)

#### [NEW] [CrossKbScoreCalibrator.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/retrieval/CrossKbScoreCalibrator.java)
- 前置置信度底线门禁：单切片原始相似度 score < 0.40 直接丢弃，防止噪点库切片强行入选；
- 倒数排名融合算法（RRF, $k=60$）：
  $$RRF\_Score(d) = \sum_{m \in M} \frac{w_m}{60 + r_m(d)}$$
- 全局内容指纹哈希（MD5 归一化去空去标点），消除跨库复制段落的冗余；
- 格式化统一溯源标识：
  `[来源序号] [KB: 知识库名称] 文档名 / segmentId=XXX`
- 全局 20KB (20,000 字节) 硬预算截断控制与优雅降级，超出部分精准截断，日志打印详细使用量。

---

### Component 3: 强类型 Fail-Closed 权限漏斗与语义缓存安全防线 (qknow-module-kmc)

#### [MODIFY] [PermissionFilter.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/PermissionFilter.java)
- 彻底剔除 `sales` 硬编码管理员特权；
- 彻底废除 `return null;` 放行逻辑！重构为强类型强校验：
  - 管理员权限严格限制在当前请求所归属的 `workspaceId` 边界之内；
  - 强制执行三元交集过滤：`targetKbIds ∩ workspaceKbIds ∩ accessibleKbIds`；
  - 若用户无权限或交集为空，返回空列表，`buildPermissionFilter` 构建不可满足的 `1 == 2` 条件，立即短路，绝不触碰向量存储与底层表；
- 提供重载方法支持 `workspaceId` 与 `userId` 组合强鉴权。

#### [MODIFY] [EnhancedSemanticCacheService.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/cache/EnhancedSemanticCacheService.java)
- 升级精确缓存 Key 与语义缓存元数据逻辑：
  - 接入用户权限哈希 `permissionHash`（基于租户 ID、角色 ID 集合、授权知识库列表排序计算的 SHA-256 指纹）；
  - 缓存 Key 格式进化为：`workspaceId + ":" + permissionHash + ":" + botId + ":" + knowledgeIdsHash + ":" + modelName + ":" + sha256(query)`；
  - 彻底阻断同一工作区内不同权限用户共享高敏感缓存问答，消弭时序与内容侧信道。

---

### Component 4: 智能体多知识库配置与检索接入点重构 (qknow-module-kb)

#### [MODIFY] [KbAgentConfigServiceImpl.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/KbAgentConfigServiceImpl.java)
- 废除原有 262 行串行 `knowledgeBaseList.forEach(...)` 同步阻塞调用；
- 注入 `MultiKbRetrievalCoordinator` 与 `CrossKbScoreCalibrator`；
- 执行并发编排召回 -> 跨库 RRF 融合精排与去重 -> 统一 20KB 上下文装配；
- 保持外部 `getRagContextList` 与相关 API 响应结构完全向前兼容。

---

### Component 5: 专属契约门禁测试与零退化回归 (tests)

#### [NEW] [Phase17MultiKbRetrievalAndTenantGateTest.java](file:///Users/achilles/Documents/许子祺/Agent/backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase17MultiKbRetrievalAndTenantGateTest.java)
- 契约验证 1：多库并发加速比与单库 2500ms 软超时降级（单库人为延时 3000ms 时，编排器在 2500ms 内触发软降级，其余库正常返回）；
- 契约验证 2：跨库 RRF 融合与去偏（大库高置信度与小库高排名切片的融合公平性，低于 0.40 的切片被底线门禁拦截）；
- 契约验证 3：全局 20KB 预算截断（模拟多库召回海量文本，验证最终输出上下文严格 $\le 20000$ 字节）；
- 契约验证 4：Fail-Closed 零越权隔离（非租户知识库在交集过滤中被 100% 剔除，空权限返回短路条件，管理员不越权跨租户）；
- 契约验证 5：语义缓存带权限哈希防侧信道（不同权限用户的相同 Query 拥有不同缓存命名空间，不可互相越权命中）。

---

## Verification Plan

### Automated Tests
1. 专属门禁契约测试：
   ```bash
   bash run-with-java21.sh ./mvnw test -Dtest=tech.qiantong.qknow.rag.eval.Phase17MultiKbRetrievalAndTenantGateTest
   ```
2. 全库全量防退化回归测试（确保 731+ 项单测全部 100% 绿灯，0 失败，0 错误）：
   ```bash
   bash run-with-java21.sh ./mvnw test -pl backend/tests
   ```
