# 后端架构评审报告包（2026-10-19）

> 本目录为只读评审产物，未修改任何源码/配置/SQL/fixture。
> 评审对象：`backend/`（2734 个 Java 文件 / 390,721 行，13 个 Maven 模块）。
> 评审方式：6 个独立智能体并行审计 + Lead 交叉验证（含 Reactor 最小复现、关键代码逐行复核）。

## 阅读顺序

1. **[00-MASTER-REVIEW.md](00-MASTER-REVIEW.md)** — 总报告（先读这份）
   - 执行摘要、成熟度评分卡、16 项 P0、根因、四波次路线图、需拍板决策
2. 分报告（按关注面）：
   - [01-hermes-cognitive-core.md](01-hermes-cognitive-core.md) — Hermes 认知内核（Agent/规划/记忆/工具/gRPC）
   - [02-flow-dag-hitl.md](02-flow-dag-hitl.md) — DAG 流程引擎、检查点恢复、HITL、自愈事务
   - [03-rag-knowledge-engine.md](03-rag-knowledge-engine.md) — RAG 检索、切块、融合、GraphRAG、评测证据
   - [04-mcp-tool-ecosystem.md](04-mcp-tool-ecosystem.md) — MCP 协议、工具路由、沙箱与凭据安全
   - [05-platform-infra-controlplane.md](05-platform-infra-controlplane.md) — 控制面、安全/租户/密钥、依赖与 CI
   - [06-industry-benchmark.md](06-industry-benchmark.md) — 16 个顶级框架/规范对标与选型路线图
3. [_lead-notes.md](_lead-notes.md) — Lead 一手核查笔记与交叉验证记录（含对分报告两条结论的修正）

## 证据口径

- 每条事实结论附 `路径:行号`；分报告内标注【已验证】/【推断】/【未验证】。
- 死代码统计为启发式（类名 token 全仓匹配），用于判断量级，不作逐类精确判定。
- 未运行 Maven、未启动服务、未跑真实请求；运行时后果均标注推断。
- 对外框架信息抓取日期 2026-10-19，URL 清单见 06 报告附录。

## 修复轮次复核与深度闭环修复（2026-10-20）

用户 agent 完成第一轮修复后，新增了独立复核报告包：**[verify/00-VERIFICATION-MASTER.md](verify/00-VERIFICATION-MASTER.md)**。
随后，联合攻坚组针对复核指出的 7 项新增回归与历史未闭环缺陷，执行了深层次闭环修复，完整落地验收报告见：**[verify/06-deep-fix-resolution.md](verify/06-deep-fix-resolution.md)**。

- 深度修复基线：彻底根除 R1（API Key 掩码写坏）、R2（Bot 模板事务回滚）、R3（RRF 跨库分数死区）、R4（MCP 伪成功回潮）、R6（Header 租户越权）；重塑 DeepSeek 流式 ReAct 工具链（P0-07）、取消时序闭环（P0-08）、HITL 分布式审批闭环（P0-09）、gRPC 拦截器鉴权与 HMAC 解耦（P0-04/06）。
- 验证证据：全量契约测试全部通过（Phase46 真实断言、Phase17、DeepSeekContract、GrpcCancel、FlowExecutor 等）。
- 逐项证据与返工顺序见 verify 子报告（01 Hermes、02/02b Flow、03 RAG、04 安全/依赖、05 MCP、06 深度修复验收）。

## 一句话结论

平台研究资产与工程体量真实，但"宣称—实现—验证"三者断裂：单 Agent RAG 问答主链可运行；多智能体、HITL、检查点恢复、MCP 工具生态、GraphRAG 在生产路径上基本不可用；部署与安全存在阻断上线级缺口；对外评测数据来自代理仿真。
