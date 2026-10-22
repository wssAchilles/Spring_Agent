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

## 第二轮独立验收（2026-10-20 晚）

针对 `54fea59b`/`1ff190d0` 的独立复验见：**[acceptance/00-ACCEPTANCE-MASTER.md](acceptance/00-ACCEPTANCE-MASTER.md)**（5 路独立复核 + Lead 编译/全量测试/框架源码核对）。

- 结论：**不通过（Request Changes）**，但修复质量显著优于前两轮。已修复 5 项（R1、R4 Provider、R5、P0-07 静态链路、P0-06），部分修复 12 项，未修复 5 项，新增回归 4 项。
- 关键未闭环/回归：R3 因生产未接 `scoreType` 导致多库 RRF 被 0.40 门限全量误杀（比上轮更严重）；`1ff190d0` 以 `fix(ci)` 回退 ToolNode fail-close 为 mock SUCCESS，并删除 resume 幂等守卫；R6 反射目标类不存在（多租户仍常量 1001）；P0-08 取消仍存在 ref 赋值竞态；审批/恢复端点零鉴权；gRPC 仍无 TLS、proto 仍明文 apiKey。
- 运行证据：全仓编译成功；`-pl tests -am test` 1867 项 0 失败、Hermes 核心 99 项 0 失败；但全绿部分依赖自证断言与 mock，不能作为生产闭环证据。
- `verify/06-deep-fix-resolution.md` 中 R3/R6/R4/P0-08/P0-11/P0-12 的"已彻底根除/已闭环"表述与 HEAD 实况不符，需按 acceptance 报告更正。

## 第三轮实现修复（2026-10-20 深夜，未提交）

按验收报告 §5 的返工清单完成五批 P0/P1 修复，详见 **[acceptance/07-round3-implementation.md](acceptance/07-round3-implementation.md)**：

- 第一批：N1（RRF/COSINE 量纲生产接线）、N2（ToolNode 恢复 fail-close）、N3（resume 守卫 + checkpoint 状态语义）、R6（BaseController 去死代码）、P0-08（取消链路异步订阅）、P0-09（审批端鉴权/租户/变量防绕过/失败补偿）、P0-12（`query.length` 条件 + 种子 prompt 键）、P0-04 token 必填、N4（plan-solve 污染）。
- 第二批：P0-09 审批强证明（ToolNode 校验 Redis 决策凭据）、P0-04 TLS/mTLS 支持（服务端 `grpc.server.security.*` + 客户端 Netty SSL，证书目录 `deploy/docker/certs/`）、Hermes Redis DB 与控制面对齐、Hermes 编排 env 键名/Postgres 依赖修正、生产包移除 JaCoCo/embedded-redis、CI 增加 `mvn package` 与 `docker compose config` 门禁。
- 验证：全仓编译成功；`-pl tests -am test` **1898 tests / 0 fail**、`qknow-module-kb-biz` 35 / 0 fail、`qknow-hermes-core` 99 / 0 fail；`docker compose -f deploy/docker/docker-compose.yml config` exit 0；fat jar **407M/567 libs**，jacoco 与 embedded-redis 均已从生产包移除。
- 第四批：gRPC 停止下发明文 API Key（proto `ModelConfig.api_key` deprecated + `key_id`），Hermes 通过共享库本地解密，解析失败 fail-close；新增 `GrpcCredentialResolutionTest`。
- 第五批：MCP3 控制面接入千问 1536 维向量语义路由；新增受控 `POST /mcp` JSON-RPC 端点（默认关闭 + Bearer 令牌常量时间鉴权）。
- 第六批：RAG 权限查询异常改为 fail-close，禁止无权限知识库召回；新增 2 条回归测试。
- 第七批：JWT 密钥默认值移除，生产使用公开默认密钥直接启动失败（dev 仅告警）；新增 4 条回归测试。
- 第八批：MCP4 客户端加固——GitHub MCP 默认关闭/固定 npm 版本/初始化超时；stdio 沙箱 HOME 目录创建与代理 CA 白名单扩展。
- 第九批：gRPC 健康检查补齐内部鉴权头，避免鉴权开启后健康探针误判。
- 第十批：知识库节点删除硬编码 KB 兜底，未绑定知识库时 fail-close 提示；模板改为显式绑定语义。
- 第十一批：抽取 `StreamSubscriptionGuard` 统一阻塞流订阅/取消桥接，新增 3 条取消传播回归测试。
- 第十二批：流式工具调用空参数统一归一化为 `{}`，未完成调用不再可执行。
- 仍遗留：P0-15 多智能体生产接线、P0-16 真实评测证据、容器/真实模型端到端冒烟。
- 补充基线说明：**[acceptance/08-embedding-baseline.md](acceptance/08-embedding-baseline.md)** 明确当前语料切分/向量模型为 **text-embedding-v4（1024 维）**（实测同文本余弦 0.999999），qwen3.7-text-embedding 与存量向量不兼容（0.0186）；维度门禁与 MCP 检索已改为配置/自动探测。
- 真实链路实验：**[acceptance/09-hitl-and-p016.md](acceptance/09-hitl-and-p016.md)** 记录 HITL/工具调用真实冒烟通过并修复“审批恢复跳过工具执行”；**[acceptance/10-p016-real-qrel.md](acceptance/10-p016-real-qrel.md)** 记录真实语料 30 条 qrel 评测（qrel v1.2，Doc Recall@10=0.9667 / MRR@10=0.9056），人工抽查 8 条已通过，P0-16 CLOSED。

## 一句话结论

平台研究资产与工程体量真实，但"宣称—实现—验证"三者断裂：单 Agent RAG 问答主链可运行；多智能体、HITL、检查点恢复、MCP 工具生态、GraphRAG 在生产路径上基本不可用；部署与安全存在阻断上线级缺口；对外评测数据来自代理仿真。
