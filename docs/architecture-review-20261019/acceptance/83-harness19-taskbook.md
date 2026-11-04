# Hermes Harness Stage 19 Profile 生命周期与会话级路由任务书

- 状态：`FROZEN CONTRACT`；仅授权离线、确定性、内存内生命周期与会话路由实现，不授权真实 API、在线 PPC 或生产默认启用。
- 基线：`feat/hermes-harness-stage18` / `0390761e`；审计见 `82-harness19-audit.md`。
- 正文 SHA-256：`08bcdf19fac431eeddc12100b96b390227536590cae4d1297641579c2b79126c`；全文件 SHA-256 以伴生 `.sha256` 为准。

## 一、唯一假设与范围

唯一假设：在固定 Stage 18 profile 白名单上，Profile 生命周期状态和带 TTL 的 `(sessionId, capabilityTag, domain)` 绑定可以保持 ENFORCE 会话级路由一致，并在过期、退役、容量或无匹配时安全回退；OFF 保持零行为变化，SHADOW 只观察不绑定。该假设不包含模型效果、自动学习、在线 PPC、真实业务 Domain 或生产可用性。

从 `0390761e` 创建 `feat/hermes-harness-stage19`。只改 `qknow-hermes-core` 的 pipeline/runtime、必要的 `AgentOrchestrator` 会话字段传递、契约测试与 `82/83/84` 文档。不修改 `BoundedOperatorPipeline` 语义，不改变 Stage 18 路由表，不新增外部依赖、数据库表、在线 PPC、真实 API 或生产默认配置。

## 二、Profile 生命周期与身份

新增 `HarnessProfileStatus`：`ACTIVE`、`DEPRECATED`、`RETIRED`。

`HarnessProfile` 保持不可变 record，字段为：`profileId`、`version`、`domainScope`、`CapabilityTag`、`pipeline`、`approved`、`source`、`status`。构造约束：baseline 永远 `approved=true`、`status=ACTIVE`、无 capability/pipeline；非 baseline 必须有 capability 与 pipeline；`RETIRED` 不得被新会话选择；`DEPRECATED` 不参与新会话选择。fingerprint 使用 SHA-256，长度前缀编码且包含 version、status 与现有身份字段；相同静态身份稳定，不同状态或版本必然不同。Registry 仍只读、静态、不得运行时创建或自动修改 Profile。

默认仍恰好四个 profile：`baseline`、`prompt-profile`、`history-profile`、`tool-profile`，全部 ACTIVE、版本 `1`、域 `DEFAULT`、静态白名单。Stage 18 路由表保持不变。

## 三、会话绑定契约

新增不可变 `HarnessSessionKey`：`sessionId`、`capabilityTag`、`domain`，三者均非空；绑定键严格为三元组，不能只用 sessionId。新增不可变 `HarnessSessionBinding`：key、profileId、profileVersion、profileFingerprint、boundAt、expiresAt；不得包含 prompt、密钥或用户数据。

`HarnessSessionBinder` 使用线程安全内存结构（ConcurrentHashMap 或等价实现），不使用会导致虚拟线程 pinning 的长持有 synchronized，不持久化数据库。默认 TTL 为 30 分钟，默认最大绑定数为 1024，Clock 可注入。绑定器负责查找、建立、失效、TTL 清理与有界淘汰，不修改 Registry。并发同一 key 必须最终只有一个 profile 绑定；不同 key 必须隔离。

新增 `HarnessBindingAction`：`NONE`、`PINNED`、`REUSED`、`REBOUND`、`FALLBACK`、`EXPIRED`。无 sessionId 时按请求级路由，动作 `NONE`，不创建绑定。

## 四、生命周期判定

新增 `HarnessProfileLifecycle`，返回 `ACTIVE_SELECTABLE`、`DEPRECATED_EXISTING_ONLY`、`RETIRED`、`UNAPPROVED`。

- ACTIVE：可被新会话选择，已有绑定可继续使用。
- DEPRECATED：不可被新会话选择；已有绑定在 TTL 内可复用，TTL 后重新选择。
- RETIRED：不可被任何新选择；已有绑定立即失效，当前请求回退或 REBOUND 到可用 ACTIVE profile。
- UNAPPROVED：永远不可路由，回退并记录原因。

## 五、OFF、SHADOW、ENFORCE

- OFF：在分类和 Router 之前直接返回 baseline；不查 Binder、不绑定、不写 receipt，保持 Stage 18 行为。
- SHADOW：执行 Router 与候选准备，写 route receipt；不建立或更新绑定，实际执行 baseline；候选准备失败回退 baseline。
- ENFORCE：有 sessionId 时按三元组查找/建立绑定；无 sessionId 时按请求级路由且不绑定。无绑定选择 ACTIVE 且 approved profile 并 `PINNED`；有效绑定 `REUSED`；DEPRECATED 绑定在 TTL 内可复用；RETIRED 绑定立即失效并尝试 `REBOUND`；过期绑定记录 `EXPIRED` 后重新选择；无匹配或无可用 profile 回退 baseline。绑定失败回退 baseline并记录原因。

与 Stage 17 Kernel Bridge 冲突时维持既有语义：SHADOW 回退，ENFORCE 在模型调用前 fail-close。正常 profile 回退不视为执行失败；已选 profile 变异失败仍按既有失败语义处理。

## 六、路由、绑定与证据字段

`HarnessRouter` 继续使用 Stage 18 固定路由表，只负责请求级候选决策；生命周期和绑定由显式 runtime 组件处理。profile 退役、过期、未批准、域/能力不匹配、绑定失败均必须有稳定的 `routeReason` 或 `failureCode`，不得静默覆盖。

Receipt 至少保留 Stage 18 字段并扩展：`sessionId`（可空）、`capabilityTag`、`domain`、`profileStatus`、`bindingAction`。完整字段为：requestId、sessionId、mode、capabilityTag、domain、profileId、profileVersion、profileFingerprint、profileStatus、bindingAction、routeReason、fallback、routeFingerprint、baselineFingerprint、candidateFingerprint、executedFingerprint、preparedSuccess、failClosed、failureCode。禁止记录 prompt 正文、密钥或用户数据；routeFingerprint 可复算，且不含 requestId。

## 七、测试与交付门禁

新增 `HarnessSessionBinderTest`、`HarnessProfileLifecycleTest`，扩展 `HarnessRouterTest`、`PipelineRuntimeBridgeTest`。至少覆盖：OFF 零路由/零绑定/零 receipt；SHADOW 不绑定；首次 PINNED；同 key REUSED；同 session 不同 capability 隔离；不同 session 隔离；无 sessionId NONE；ACTIVE 可选；DEPRECATED 新会话不可选、已有绑定 TTL 内可复用、过期重选；RETIRED 立即失效并 REBOUND/FALLBACK；UNAPPROVED、域不匹配、无可用 profile 回退；TTL、容量淘汰、并发同 key 单一绑定；fingerprint 含版本/状态；Stage 18 测试不回归；不调用真实 API。

定向命令：

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -am \
  -Dtest='HarnessSessionBinderTest,HarnessProfileLifecycleTest,HarnessRouterTest,PipelineRuntimeBridgeTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

全量命令：

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl tests -am clean test
```

要求 BUILD SUCCESS、0 fail、0 error，精确记录 tests/skipped；提交后全新本地 APFS 检出同 HEAD 单步 clean test 通过且工作区 clean。若 proto 间歇故障重现，保全日志并将根因保持 `UNPROVEN`，不得用重试或预构建宣称修复。

## 八、明确不做与边界

不做在线 PPC、自动 Profile 生成/采纳、真实模型实验、Stage 15 preflight、DeepInfer C++/CUDA、投机解码、SWA 记忆重写、学习型 Router、生产默认接入、数据库持久化、多域业务映射或新的算子语义。完成后只能宣称：生命周期与会话级路由契约在离线、静态白名单、内存绑定范围内通过；不能宣称真实模型效果或生产可用。

