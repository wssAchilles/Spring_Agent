# Hermes Harness Stage 18 只读架构审计

- 基线：`codex/hermes-harness-stage17` / `fc2d92c8`。
- 范围：任务感知静态路由；不评价真实模型效果，不授权生产默认启用。

## 一、当前执行路径

`AgentOrchestrator.executeAgent` 先取得模型、RAG 上下文并为 RAG 自动装配 `knowledgeBase<id>` 与 `knowledgeQuery` 工具，再建立请求 voucher；随后调用 `PipelineRuntimeBridge.prepare`，最后才装配历史、系统提示词并进入可选的 `HarnessKernelBridge.bridgeExecution`。`CapabilityTag` 当前只在 Kernel Bridge 开启后的 `resolveCapabilityTag` 中解析。Stage 18 路由若在现有 prepare 位置执行，不能假定已有可信标签可直接使用。

## 二、六项审计回答

1. **标签来源**：`HarnessKernelBridge.resolveCapabilityTag` 先读取 `inputParams` / `parameters` 的 JSON 声明，再按 RAG、已挂载工具、HISTORY 推断；这一路径位于 Stage 17 流水线选择之后。请求声明未经能力验证，不能成为 Stage 18 路由授权依据。
2. **Domain 来源**：`ChatRequest` 有 bot/workspace/knowledge ID，但没有经过验证的 `domain` 或 `taskCategory`；记忆分区也不是业务 Domain。Stage 18 仅使用静态路由域 `DEFAULT`，不推断真实业务归属。
3. **Registry 改造**：保留 Stage 16 的 `BoundedOperatorPipeline`、`PipelineMutation` 与 `TargetConflictValidator`，将 Stage 17 桥中硬编码的组合替换为只读 registry 与确定性 router 的决策。baseline 是唯一无变异 profile，不构造非法空 pipeline。
4. **OFF 零行为**：`enabled=false` 或无效模式均须在能力分类、路由、变异、receipt 写入之前返回原 voucher；默认 SINGLE 与现有 Kernel Bridge OFF 路径不变。
5. **失败语义**：未知标签、未知 profile、未批准 profile 或 Domain 不匹配属于正常 baseline 回退；选中合法 profile 后变异失败则 SHADOW 回退、ENFORCE 在模型调用前 fail-close。保留 Stage 17 的双桥规则：SHADOW 回退，ENFORCE fail-close。
6. **证据身份**：active 模式 receipt 分别保存 request、选中 profile ID/version/fingerprint、route reason/fallback、pipeline fingerprint、候选与实际执行 voucher 指纹及 mode；route fingerprint 使用固定规范化字段计算，支持重放核验。OFF 不生成路由 receipt。

## 三、误分类与边界

RAG 自动挂载的知识库工具不能被计为独立 TOOL；否则普通 RAG 会被推成 COMPOSITE。分类只依据真实非空 RAG 正文、显式且可解析的非知识库工具及请求中非空 HISTORY，优先级 `COMPOSITE > RAG > TOOL > HISTORY > PROMPT_ONLY`。未验证的 JSON capability 声明、bot ID、KB ID 均不得提升路由权限。若显式工具无法解析，不能因此选择 TOOL profile。内存管理器后来召回的 HISTORY 不在该前置分类中；因此该分类是请求可观察能力，不声称覆盖执行时所有后续状态。

## 四、架构决策

唯一可证伪假设：在静态批准 profile 集合内，以请求可观察能力和固定默认域进行确定性白名单路由，可在 OFF 不变、SHADOW 不改模型输入的前提下，让 ENFORCE 对适配任务执行相应单算子，同时使不匹配请求安全回退 baseline。若任何受控请求进入未批准 profile、RAG 因自动知识工具误路由、OFF 留下 receipt，或 SHADOW 改变模型输入，则假设不成立。

本阶段不开展文献或模型效果研究；它复用已验收的 Stage 16/17 本地框架契约，验证目标为运行时语义而非性能收益。
