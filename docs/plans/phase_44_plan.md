# Phase 44 实施方案：认知负荷感知自适应交互与动态多模态信息呈现中枢

## 一、唯一待验证假设

**H-PHASE44-001**：
在企业级多智能体协同、RAG 问答与数据分析的高并发交互中，通过构建轻量纯内存认知负荷估算引擎（`CognitiveLoadEstimator`）、基于 Cowan $4 \pm 1$ 工作记忆容量定理的自适应呈现策略控制器（`AdaptivePresentationGovernor`）、以及渐进式多模态结构化卡片装配器（`DynamicMultimodalComposer`），能够在数学上实现：
1. 毫秒级精准评估用户即时认知负荷分值 $CL \in [0.0, 1.0]$ 与四态等级（`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`），算法耗时 $\le 1\text{ms}$；
2. 依据定理 1.1，在任意负荷状态下向用户首屏呈现的非折叠信息组块数严格受控于 $K_{chunks} \le 4$；在 `CRITICAL` 极端高压或告警状态下，严格只呈现 1 项唯一核心行动单（$K_{chunks} = 1$），彻底阻断认知隧道效应与操作失误；
3. 依据定理 2.1，低负荷下自动平滑释放 Markdown 深度分析与丰富 ECharts 图表，高负荷下复杂图表被高光指标卡与极简行动单单调占优，信息呈现效率与视觉捕获耗时优化 $\ge 60\%$；
4. 端到端负荷估算、阻尼决策与多模态组件结构化组装耗时在单次请求下满足 MTTC $\le 50\text{ms}$，在负荷计算异常时具备无感 Fail-Open 平滑降级。

---

## 二、架构设计与核心组件规范

### 2.1 模块路径结构
代码落地位于模块：`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/presentation/`
```
tech.qiantong.qknow.ai.presentation/
├── dto/
│   ├── UserCognitiveStateDTO.java             # 用户交互行为与认知状态快照 DTO
│   ├── MultimodalPresentationBlockDTO.java    # 结构化多模态展示块 DTO (卡片定义)
│   ├── PresentationPlanVO.java                # 自适应呈现综合规划视图 VO
│   └── AdaptiveStreamChunkDTO.java            # 流式传输自适应增量切片 DTO
├── enums/
│   ├── CognitiveLoadLevel.java                # 认知负荷四态枚举 (LOW, MEDIUM, HIGH, CRITICAL)
│   └── PresentationBlockType.java             # 多模态展示块类型枚举
└── engine/
    ├── CognitiveLoadEstimator.java            # 即时认知负荷毫秒级估算器
    ├── AdaptivePresentationGovernor.java      # Cowan 4±1 认知阻尼与模式控制器
    ├── DynamicMultimodalComposer.java         # 动态多模态组件合成器
    └── PresentationStreamCoordinator.java     # 端到端流式呈现调度总控器
```

### 2.2 核心契约接口与算法规范

1. **`CognitiveLoadEstimator`**：
   - 估算方程：$CL = 0.35 \cdot \Phi_{len} + 0.25 \cdot \Phi_{cad} + 0.20 \cdot \Phi_{ent} + 0.20 \cdot \Phi_{urg}$；
   - 动态映射：
     - $[0.0, 0.35) \to \text{LOW}$
     - $[0.35, 0.65) \to \text{MEDIUM}$
     - $[0.65, 0.85) \to \text{HIGH}$
     - $[0.85, 1.00] \to \text{CRITICAL}$；
   - 算法复杂度 $\mathcal{O}(1)$，耗时严格 $\le 1\text{ms}$。

2. **`AdaptivePresentationGovernor`**：
   - 模式决策：
     - `LOW`：全量开放（最多 4 个活跃组块，允许复杂图表与深度文本）；
     - `MEDIUM`：平衡摘要（最多 3 个活跃组块，次要文本默认折叠）；
     - `HIGH`：关键指标（最多 2 个活跃组块，抑制复杂图表，保留关键指标）；
     - `CRITICAL`：极简行动（严格 1 个活跃组块，仅呈现唯一 `ACTION_BANNER`）；
   - 核心定理 1.1：首屏活跃组块数 $K_{chunks} \le 4$ 恒成立。

3. **`DynamicMultimodalComposer`**：
   - 支持 5 大标准展示块类型：
     - `TEXT_MARKDOWN`：排版精良的 Markdown 核心要点；
     - `METRIC_CARD`：单色钛金高光指标卡（数值、单位、环比）；
     - `ECHART_SPEC`：自适应 ECharts JSON 配置（折线、柱状、饼图）；
     - `STEP_FLOW`：排查或执行步骤流（步骤号、状态、描述）；
     - `ACTION_BANNER`：高对比度单色警报与确认单；
   - 遵循 `.shared/ui-ux-pro-max` 设计规范。

4. **`PresentationStreamCoordinator`**：
   - 统筹端到端流程：用户认知状态收集 -> 负荷计算 -> 呈现阻尼决策 -> 多模态卡片生成 -> 前端规划 VO 输出；
   - 具备 Fail-Open 容灾降级：若异常则默认回退至 `MEDIUM` 平衡模式，端到端耗时 $\le 50\text{ms}$。

---

## 三、10 项严苛契约测试设计 (Contract Test Suite)

在 `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase44CognitiveLoadAdaptivePresentationContractTest.java` 中落地 10 项严苛契约测试：

1. **`test01_CognitiveLoadEstimatorContinuousScoreAccuracy`**：
   验证不同上下文长度、停顿时间与紧急度输入下，计算出的连续认知负荷得分严格有界在 $[0.0, 1.0]$，且高压下数值严格高于低压基线。
2. **`test02_CognitiveLoadEstimatorDiscreteLevelMapping`**：
   验证四态区间映射精准无缝：$< 0.35$ 对应 `LOW`，$0.35 \sim 0.65$ 对应 `MEDIUM`，$0.65 \sim 0.85$ 对应 `HIGH`，$\ge 0.85$ 对应 `CRITICAL`。
3. **`test03_CowanWorkingMemoryBoundInvariant` (定理 1.1)**：
   验证在全部 4 种负荷状态下，经 Governor 阻尼裁决后，向用户首屏呈现的活跃非折叠展示块数量严格满足 $K_{chunks} \le 4$。
4. **`test04_CriticalStateStrictSingleActionInvariant` (定理 1.1)**：
   验证在 `CRITICAL` 极端负荷状态下，活跃展示块数量严格为 1，且该块类型必须为 `ACTION_BANNER`，其余复杂组件 100% 自动折叠或过滤。
5. **`test05_HighLoadChartSuppressionParetoDominance` (定理 2.1)**：
   验证在 `HIGH` 负荷下，系统自动抑制高开销的复杂 ECharts 图表，自适应替换为高光指标卡与极简摘要，满足帕累托占优决策。
6. **`test06_LowLoadFullExplorationRichMultimodal` (定理 2.1)**：
   验证在 `LOW` 负荷下，系统自动释放全部丰富模态（包含深度文本、多指标卡与 ECharts 图表），折叠比例为 0。
7. **`test07_DynamicMultimodalComposerCardIntegrity`**：
   验证 Composer 组装出的 `METRIC_CARD`、`ECHART_SPEC`、`STEP_FLOW` 均包含有效合规的 JSON 配置与展示属性，无格式残缺。
8. **`test08_PresentationStreamCoordinatorEndToEndWorkflow`**：
   模拟用户交互行为端到端流经 Coordinator，验证完整输出包含认知等级、决策模式、阻尼卡片列表与总耗时。
9. **`test09_FailOpenGracefulDegradationOnException`**：
   验证当输入异常（如全空属性或非法数值）时，Coordinator 自动触发 Fail-Open 保护，优雅降级为 `MEDIUM` 平衡呈现模式，系统永不崩溃。
10. **`test10_PresentationEngineSubFiftyMillisLatency`**：
    压力测试在连续 50 次并发评估与卡片组装下，端到端单次耗时均严格满足 MTTC $\le 50\text{ms}$（预期实测 $\le 5\text{ms}$）。

---

## 四、实施计划与文件边界

### 4.1 新增与修改文件清单
- **文档与报告**：
  - `docs/plans/phase_44_academic_report.md` [NEW]
  - `docs/plans/phase_44_industrial_report.md` [NEW]
  - `docs/plans/phase_44_plan.md` [NEW]
  - `docs/plans/00_master_index.md` [MODIFY]
- **核心组件**（`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/presentation/`）：
  - `enums/CognitiveLoadLevel.java` [NEW]
  - `enums/PresentationBlockType.java` [NEW]
  - `dto/UserCognitiveStateDTO.java` [NEW]
  - `dto/MultimodalPresentationBlockDTO.java` [NEW]
  - `dto/PresentationPlanVO.java` [NEW]
  - `dto/AdaptiveStreamChunkDTO.java` [NEW]
  - `engine/CognitiveLoadEstimator.java` [NEW]
  - `engine/AdaptivePresentationGovernor.java` [NEW]
  - `engine/DynamicMultimodalComposer.java` [NEW]
  - `engine/PresentationStreamCoordinator.java` [NEW]
- **契约测试**：
  - `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase44CognitiveLoadAdaptivePresentationContractTest.java` [NEW]

### 4.2 严禁修改的文件边界
- 严禁修改其他既有 Phase 的业务代码与既有 972 项单测；
- 严禁修改 Java 21 隔离环境规范；
- 严禁使用未授权的大模型 API 或本地模型。

### 4.3 验证命令
```bash
# 1. 编译与契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -Dtest=Phase44CognitiveLoadAdaptivePresentationContractTest -pl tests

# 2. 后端全量防退化回归测试 (972+10=982 项)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests

# 3. 前端生产构建校验
cd frontend && npm run build:prod
```
