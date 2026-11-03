# Hermes Harness Stage 18 Task-Aware Harness Router v1 冻结任务书

- 状态：`FROZEN CONTRACT`；仅授权静态离线路由实现与测试，不授权真实 API、在线 PPC 或生产默认启用。
- 基线：`codex/hermes-harness-stage17` / `fc2d92c8`；阶段审计见 `79-harness18-audit.md`。
- 正文 SHA-256：`d0edc3b8eb1aebef04f3911d89d58c918cbcd31680f4b8528a3eb16479d883df`；全文件 SHA-256 以伴生 `.sha256` 为准。

## 一、唯一假设与范围

唯一假设：固定白名单 profile 根据请求可观察的能力标签与显式路由域确定性选中；在 OFF 零行为、SHADOW 输入不变的前提下，ENFORCE 可执行所选 profile；不匹配安全回退 baseline，已选 profile 执行失败保持 Stage 17 fail-close。可证伪条件见 `79` 第四节。该假设不包含模型质量提升、领域学习或自动采纳。

从 `fc2d92c8` 创建 `feat/hermes-harness-stage18`。只改 `qknow-hermes-core` 的 pipeline/runtime、必要的 `AgentOrchestrator` 接线、契约测试与 `79/80/81` 文档。不得修改 Stage 16 的流水线、变异与目标冲突语义，不新增外部依赖；不接真实 API、不接生产默认、不读业务数据库、不做在线 PPC/学习型路由。

## 二、确定性输入与路由表

`HarnessRoutingContext = (requestId, domain, capabilityTag)`。当前 `ChatRequest` 不提供可信 Domain；接线固定填入 `DEFAULT`，它是路由命名空间，不是 KB/taskCategory 或真实业务 Domain。不得用请求 JSON 的 `capabilityTag` 声明覆盖真实能力。路由只在 bridge active 时分类；OFF 在分类前直接返回原 voucher。

能力按请求可观察信号分类：有效 RAG 指非空且非字面 `null` 的预检索正文；独立 TOOL 指 `toolMethodIds` 中显式指定且由 resolver 成功解析的非 `knowledgeQuery`/`knowledgeBase*` 工具；HISTORY 指请求中有非空消息。RAG 自动创建的知识库工具不是独立 TOOL。优先级为 `RAG+TOOL -> COMPOSITE`、`RAG -> RAG`、`TOOL -> TOOL`、`HISTORY -> HISTORY`、否则 `PROMPT_ONLY`。未解析工具不能提升标签；后续内存召回不反向改变本次路由标签。

| Domain | CapabilityTag | profileId | 操作 | routeReason |
|---|---|---|---|---|
| DEFAULT | PROMPT_ONLY | prompt-profile | `CONCISE_SYSTEM_PROMPT` | `MATCHED_CAPABILITY` |
| DEFAULT | HISTORY | history-profile | `ADAPTIVE_MEMORY_WINDOW` | `MATCHED_CAPABILITY` |
| DEFAULT | TOOL | tool-profile | `TOOL_GUIDANCE_SYNTHESIS` | `MATCHED_CAPABILITY` |
| DEFAULT | RAG | baseline | 无变异 | `NO_APPROVED_ROUTE` |
| DEFAULT | COMPOSITE | baseline | 无变异 | `NO_APPROVED_ROUTE` |
| 其它/缺失 | 任意 | baseline | 无变异 | `DOMAIN_MISMATCH` |
| DEFAULT | 未知/缺失 | baseline | 无变异 | `UNKNOWN_CAPABILITY` |

未知 profile ID、未批准 profile、profile 声明的 Domain 或能力与上下文不符，均选择 baseline 并给出可区分的 routeReason，不抛执行失败；不能把它们写成候选已执行。`baseline` 必须永远存在且可用。

## 三、Profile、Registry 与身份契约

只读 `HarnessProfileRegistry` 注册恰好四个内建 profile：`baseline`、`prompt-profile`、`history-profile`、`tool-profile`，版本固定为 `1`，Domain 为 `DEFAULT`，来源为静态代码白名单。非 baseline profile 全部 `approved=true`；baseline 为唯一不应用算子的 profile，`pipeline=null` 和 `capabilityTag=null` 仅对 baseline 合法，后者表示任意能力通配，不能误称 baseline 只覆盖 PROMPT_ONLY，也不能制造违反 Stage 16 禁空约束的假 pipeline。非 baseline 必须有明确标签与非空 pipeline。未批准 profile 可用于拒绝测试，但不得进入默认可路由表。`findApproved` / `listApproved` 不写入、不生成、不隐式批准。Stage 16 的 Prompt→Memory 组合继续合法，但本阶段**不注册**组合 profile。

`HarnessProfile` 至少携带 ID、version、domainScope、`CapabilityTag`、pipeline、approved、source、fingerprint。构造时校验必填值及 baseline 特例，registry 校验 profile ID 和 fingerprint 唯一性，并依赖 `BoundedOperatorPipeline` 的长度、重复算子、同目标冲突与组合白名单校验。profile fingerprint 使用 SHA-256，按固定版本前缀和长度无歧义编码规范化的 ID/version/domain/tag（baseline 用专用空标记）/pipeline fingerprint（baseline 用专用空标记）/approval/source；相同 profile 不随请求变化。禁止把不同元数据但相同 pipeline 的 profile 当作同一身份。

`HarnessRouteDecision` 含所选 profile、routeReason、fallback、routeFingerprint。route fingerprint 使用 SHA-256 对固定版本前缀、domain、tag、所选 profile fingerprint、routeReason、fallback 作规范化编码；**不包含 requestId**，同类路由跨请求得到同一指纹；requestId 单独进入 receipt。router 不调用 LLM、数据库或外部网络。profile 不批准或不匹配时必须回退 baseline，不通过变异来试探合法性。

## 四、运行时与失败语义

`PipelineRuntimeBridge` 由 router 选择 profile，不再硬编码 `REGISTERED_PIPELINE`。`enabled=false`、`mode=OFF` 或无效模式：原样返回 baseline voucher，不分类、不路由、不变异、不写 receipt。SHADOW：执行路由与候选准备以供观察，但实际模型输入和执行 voucher 保持 baseline。ENFORCE：仅选中已批准且匹配的非 baseline profile 时应用 pipeline；正常回退 baseline 不视为执行错误。

**已选 profile 的变异失败**：SHADOW 返回 baseline 并记录失败，ENFORCE 在模型调用前 fail-close，沿用 `FAIL_SAFE` 证据。**双桥同时启用**：维持 Stage 17 原语义，pipeline SHADOW 回退 baseline、pipeline ENFORCE fail-close；不得用 Kernel Bridge 的后续结果覆盖本次已执行 profile 的身份。`AgentOrchestrator` 保持默认 SINGLE 与 pipeline 默认 OFF，不加入生产默认配置。

active 路由 receipt 至少保存：requestId、mode、profileId、profileVersion、profileFingerprint、pipelineFingerprint（baseline 用空值）、routeReason、fallback、routeFingerprint、baseline/candidate/executed voucher fingerprint、preparedSuccess、failClosed、failureCode。正常 baseline 回退必须标记 `fallback=true`，执行失败不能伪装成正常回退。OFF 不产生 receipt；receipt 内不得写提示词正文、密钥或用户数据。

## 五、验证与交付门禁

新增 `HarnessRouterTest`，扩展 `PipelineRuntimeBridgeTest`。覆盖：baseline 存在；三条单算子路由；RAG/COMPOSITE/未知回退；未批准、Domain 不匹配、未知 profile 回退；重复 profile 指纹与同目标冲突拒绝；OFF 零行为；SHADOW 模型输入与 baseline 相同；ENFORCE 选中 profile 真正改变对应输入；双桥冲突维持既有行为；routeReason/fingerprint 在 receipt 可复算；无真实 API。定向命令：

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -am \
  -Dtest='HarnessRouterTest,PipelineRuntimeBridgeTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

当前工作树和提交后的全新本地 APFS 检出各串行运行一次 Java 21 / Maven 3.9.16 单步 `mvn -B -ntp -f backend/pom.xml -pl tests -am clean test`，不手动预生成 proto，不运行 `-Plive`。要求 BUILD SUCCESS、2210+ tests、0 fail/0 error、10+ skipped；提交后检出与原工作树均 clean。失败保留现场，不用两步构建冒充单步通过。`81-harness18-implementation.md` 记录精确 profile/路由表、行为、回退规则、receipt 字段、两次测试总数、提交与检出 HEAD、未做项及 CLOSED 判断。完成前不得宣称真实模型效果或生产可用。
