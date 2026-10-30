# Harness Stage 6 主干认知内核实施与验收

## 冻结依据

`45-harness6-taskbook.md` 正文 SHA-256：`65f67a740eeb8620edf56f39a6894c39e23ca33d5d4acc41bc9e5d70df649321`；整文件 SHA-256：`a4b1a3a178ed1f8876c542ebeb424b67ce2ea9f2436cb6eb161db30c64e95a0c`。两者均与冻结签名一致。

## 主干文件

包根：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/`。

| 模块 | 新增类 | 职责 |
|---|---|---|
| manifold | `HypersphericalManifoldProjector` | 1024 维归一化、余弦、弦距、测地线、外在质心 |
| manifold | `ReversibleRingColdBuffer` | Hot/Warm/Cold 与可恢复溢出档案 |
| neurosymbolic | `HoareTripleToolGuard` | 参数、权限、结果完整性和敏感输出的前后置反例 |
| neurosymbolic | `LocallyDenseCycleAutomaton` | SHA-256 指纹、先淘汰后计数的 W 窗口拒绝 |
| evolution | `TrajectoryAttributionAnalyzer` | 显式思维链、工具和 RAG 计数的三维退化归因 |
| evolution | `AlgebraicHarnessMutator` | 四个有界不可变 Voucher 算子 |
| evolution | `ParetoFrontierComposer` | 保真度/Token/延迟非支配前沿 |
| kernel | `CapabilityTag` | 五类强类型能力标签 |
| kernel | `CapabilityContextAssembler` | 能力载荷 fail-close 校验与 `ChatRequest` 装配 |
| kernel | `HermesHarnessKernel` | 显式 opt-in 的准备、凭单指纹与组件门面 |

几何实现满足单位向量 `cos(u,v)=u·v`、`d_chord²=2(1-cos)`、`d_geo=acos(clamp(cos,-1,1))`。归一化均值的 `2(1-||mean||)` 对应**平均平方弦距**；不将其误称为一般测地线 Fréchet 目标的已证上界。反向抵消导致质心未定义时 fail-close。冷环活跃容量固定，原件溢出到可恢复档案而非硬删除；该档案内存增长尚未持久化或限额化。

## 验证

- Java 21 隔离编译：`mvn -B -ntp -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -am -DskipTests compile`，`BUILD SUCCESS`。
- 定向契约：`HermesHarnessKernelArchitectureTest`，9 tests、0 fail、0 error、0 skipped。
- 默认全量：`mvn -B -ntp -f backend/pom.xml -pl tests -am clean test`，43 个 reactor 模块 `BUILD SUCCESS`；本次构建时间窗口内 412 个 Surefire suite，共 **2116 tests、0 fail、0 error、8 skipped**。未参与 reactor 的 `qknow-server/target` 留有旧错误 XML，未计入本次结果。
- 默认 Surefire 排除 `live` tag；Stage 5 真实 API 方法已标记 `live`，其余 2 个离线测试仍执行。未运行 `-Plive` 或付费 DeepSeek 探针，默认门禁无需真实 API Key；未对所有测试做断网抓包证明。
- Stage 4 合成 PPC 测试写入模块 `target/test-scratch`，本次全量测试后没有已跟踪证据文件变更。

## 裁定与后续边界

建议将本分支作为**显式 opt-in 的 Stage 6 架构代码**进行代码审查后合入 main；不应宣称生产 `AgentOrchestrator` 已启用这些组件。可进入下一阶段的生产桥接设计，但在学术论文中使用“内在 Fréchet 均值上界”或宣称冷备总内存有界前，须先补正式证明或收窄术语，并为档案设计持久化/容量策略。真实模型效果、消融和付费探针均留待后续独立验收。
