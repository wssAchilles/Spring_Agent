# Hermes Harness Stage 19 审计：Profile 生命周期与会话级路由

## 一、基线

- Stage 18 交付基线：`feat/hermes-harness-stage18` / `0390761e`。
- Stage 18 已验证四个静态 profile、确定性能力路由、`PipelineRuntimeBridge` 的 OFF/SHADOW/ENFORCE 语义及默认 OFF。
- Stage 18 全新本地检出单步测试为 `2220 / 0 / 0 / 10`；真实 API 与生产默认均未启用。
- 原工作树 proto 间歇生成故障的根因仍为 `UNPROVEN`，本阶段不处理、不归因、不用重试掩盖。

## 二、唯一待验证假设

在不改变 Stage 18 路由表和 `BoundedOperatorPipeline` 语义的前提下，Profile 生命周期状态与带 TTL 的 `(sessionId, capabilityTag, domain)` 绑定可以使 ENFORCE 在同一会话能力维度内保持一致，并在过期、退役、容量和无匹配时安全回退；OFF 与 SHADOW 不建立绑定且不改变默认执行。

可证伪条件：同一绑定键在有效 TTL 内选择不同 profile；退役 profile 继续执行；不同 capability 或不同 session 互相污染；OFF/SHADOW 写入绑定；过期或容量边界超出约束；回退缺少稳定原因。

## 三、架构缺口

Stage 18 的 `HarnessProfile` 只有静态版本与批准标志，Router 每次按请求独立选择，Bridge 没有会话绑定、生命周期状态或 TTL。直接把状态写入 Router 会混淆只读注册与运行时状态，也会让同一 session 的不同能力互相污染。因此本阶段将 Profile 注册保持只读，新增显式生命周期判定和内存内绑定器，并在 Bridge 处按模式接入。

## 四、实施边界

只修改 `qknow-hermes-core` 的 pipeline/runtime、必要的 `AgentOrchestrator` 传递字段、契约测试和 82/83/84 文档。不新增数据库表、Maven 依赖、在线 PPC、自动 profile、真实模型调用或生产默认配置；不改变 Stage 18 路由表、流水线语义和既有 receipt 字段含义。

## 五、验收门禁

必须完成生命周期、绑定键隔离、TTL、容量、并发、回退和 receipt 契约测试；定向与全量测试在 Java 21 下通过；提交后全新 APFS 检出单步 `clean test` 通过且工作区 clean。proto 间歇故障若重现，现场保全并保持 `UNPROVEN`。

