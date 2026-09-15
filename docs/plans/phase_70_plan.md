# Phase 70 实施方案：具身智能体多足/轮臂移动操作全身动力学协同 (Whole-Body Control, WBC)、动态质心动量平衡与非平稳接触抓取中枢

> **版本**：v1.0.0  
> **状态**：**PLAN_PREPARED** (待用户授权批准执行)  
> **核心假设**：`H-PHASE70-001`  
> **唯一生成模型**：DeepSeek API (`deepseek-chat` / `deepseek-reasoner`)  
> **唯一向量模型**：阿里千问 (Qwen) Embedding (1536 维超球面归一化 $\|\mathbf{v}\|_2 = 1.0$)  
> **运行环境**：Java 21 隔离环境 (`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)

---

## 一、当前代码与失败机制 (A. 当前代码与失败机制)

### 1.1 架构模型基线与物理运行环境约束
1. **生成侧唯一 DeepSeek API**：绝无本地大模型权重，采用 DeepSeek-V3 进行微秒/毫秒级全身协同任务参数生成与支撑多边形模式切换，DeepSeek-R1 进行非平稳接触突变与失稳因果推断；
2. **向量侧唯一阿里千问 1536 维超球面单位向量**：宏观移动操作任务语义强制约束在单位超球面流形 $\mathbb{S}^{1535}$，使用大圆弧测地余弦度量，严防任务语义与底层力控平衡脱节；
3. **彻底弃用声明**：绝无本地部署大模型，彻底弃用 OpenAI/GPT API；
4. **编译与运行环境唯一 Java 21 隔离环境**：所有构建、测试与运行显式指定 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`。

### 1.2 既有系统瓶颈与失败模式
1. **底盘-机械臂动力学解耦假设失配**：现存模块（Phase 65, 68, 69）均假定机械臂基座为大地固定基，未建立 6-DoF 浮动基动力学与质心动量耦合。在底盘高速移动或机械臂高速挥动时，惯性力矩相互剧烈干扰导致轨迹跟踪精度急剧恶化；
2. **缺乏动态 ZMP 与全系统质心动量防倾翻边界**：在底盘急加速、急停或急转弯时，由于未建立全系统质心动量矩阵 $\mathbf{A}_G(\mathbf{q})$ 建模，ZMP 瞬态突破支撑多边形导致整机倾翻风险；
3. **移动加减速惯性剪切力导致工件滑脱**：底盘加减速产生巨大达朗贝尔惯性力，长力臂末端切向剪切力瞬间击穿摩擦锥，造成工件滑脱；
4. **多任务分层 QP 优化器奇异点发散超时**：外部迭代数值 QP 在机械臂奇异点处条件数恶化，求解耗时暴涨破坏 1000Hz 硬实时周期；
5. **全要素不可变可审计存证凭单缺失**。

### 1.3 唯一核心待验证假设 (H-PHASE70-001)
构建**质心动量与 ZMP 动态平衡安全边界调节器 (CentroidalMomentumGovernor)、分层二次规划全身控制器 (HierarchicalWbcOptimizer)、非平稳接触力分配器与防滑脱切向阻抗补偿 (NonStationaryContactForceDistributor)、1000Hz 实时微秒级无锁全身控制总线 (WholeBodyControlBus)、以及不可变全身执行存证凭单 (WholeBodyControlReceipt)**：
1. 质心动量 ZMP-HOCBF 动态平衡：在 $d_{\text{margin}} \le 20\text{mm}$ 时毫秒级重分配底盘加速度与反力矩，倾翻拦截率 $100\%$，千问 1536 维超球面意图对齐；
2. 4 级优先级级联解耦与微秒级闭式解析 WBC 求解：Pri 1 平衡防倾翻 $\to$ Pri 2 接触力摩擦锥 $\to$ Pri 3 末端轨迹 $\to$ Pri 4 关节自耗位姿，解析零空间投影与 DLS 阻尼截断，单步求解耗时 $\le 0.5\text{ms}$，高优先任务满足率 $100\%$；
3. 移动加减速惯性剪切动态前馈与防滑脱切向阻抗补偿：底盘加速度前馈预补偿，接触力锁定在库伦摩擦锥内部，底盘 $\pm 2.0\text{m/s}^2$ 冲击下滑脱检出率 $100\%$，$\le 2\text{ms}$ 内补强夹紧力；
4. 1000Hz 无锁总线与 JitterGuard 软着陆：4096 槽位 Disruptor 无锁队列，连续 3 帧抖动（$> 2\text{ms}$）或单帧时延 $> 20\text{ms}$ 自动切入 `DEGRADED_GRAVITY_COMP` 软着陆重力补偿降级；
5. 不可变存证凭单 SHA-256 签名自验通过率 $100\%$。

---

## 二、Research Ledger 概览 (B. Research Ledger)

已在学术研学报告 [`docs/plans/phase_70_academic_report.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/plans/phase_70_academic_report.md) 与工业对标报告 [`docs/plans/phase_70_industrial_report.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/plans/phase_70_industrial_report.md) 中完整记录 12 篇顶尖学术与工业权威文献全部 14 项字段（涵盖 Khatib 1987, Sentis & Khatib 2005, Orin & Goswami 2008, Kuindersma et al. 2016 Atlas WBC, Sleiman et al. 2023 ANYmal, Pinocchio, Drake, OCS2 等）。

---

## 三、可迁移与不可迁移结论 (C. 可迁移与不可迁移结论)

- **直接采纳**：
  - 递归零空间正交投影算子 $\mathbf{N}_k = \mathbf{I} - \mathbf{J}_{k|pre}^\dagger \mathbf{J}_{k|pre}$；
  - 质心动量矩阵 $\mathbf{A}_G(\mathbf{q})$ 与动态 ZMP 解析方程；
  - 库伦摩擦锥内切线性化多棱锥约束；
  - Disruptor 4096 定长无锁环形总线与 JitterGuard 时钟守护。
- **坚决拒绝**：
  - 拒绝在实时回路中运行重量级外部迭代数值 QP 优化器（如 OSQP, IPOPT），改用纯解析零空间投影与 DLS 阻尼奇异值截断；
  - 拒绝底盘与机械臂独立割裂控制；
  - 拒绝无惯性前馈的滞后纯反馈力控。

---

## 四、候选方案比较 (D. 候选方案比较)

统一从正确性、可证伪性、数据需求、延迟、成本、实时性、抗倾翻与防滑脱、生产安全性八大维度评估，唯一选择**方案 4（Phase 70 解析分层 WBC + 质心动量 HOCBF + 微秒级无锁总线）**。

---

## 五、推荐的最小算法与工程类图契约 (E. 推荐的最小算法)

### 5.1 目录与包结构
落地位置：`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/embodied/wbc/`
- `dto/LocomanipulationTaskPriority.java`（枚举：BALANCE_ZMP > CONTACT_FORCE > EE_TRAJECTORY > POSTURE_MIN）
- `dto/WholeBodyState.java`（Java 21 Record：基座 6-DoF 位姿速度、关节角度/角速度、质心位置速度、足端反力、千问 1536 维特征向量）
- `dto/WholeBodyControlReceipt.java`（Java 21 Record：不可变存证凭单，内置 SHA-256 签名）
- `engine/CentroidalMomentumGovernor.java`（质心动量矩阵 CMM 与 ZMP 动态平衡边界调节器，HOCBF 软着陆重分配）
- `engine/HierarchicalWbcOptimizer.java`（4 级优先级解析零空间 WBC 求解器，DLS 阻尼奇异值截断）
- `engine/NonStationaryContactForceDistributor.java`（底盘加减速惯性前馈补偿与库伦摩擦锥内部保持）
- `engine/WholeBodyControlBus.java`（1000Hz 定长 4096 槽位 Disruptor 无锁总线，JitterGuard 时钟抖动熔断）

---

## 六、实验与实现计划 (F. 实验与实现计划)

### 6.1 专属契约单元测试设计（8 项）
落地位置：`backend/tests/src/test/java/tech/qiantong/qknow/ai/embodied/Phase70WholeBodyLocomanipulationContractTest.java`
1. `test01_CentroidalMomentumGovernor_CmmAndDynamicZmpCalculation`：验证质心动量矩阵 CMM 计算准确性，动态 ZMP 解析坐标计算与静态质心投影偏离检测；
2. `test02_CentroidalMomentumGovernor_HocbfTipOverBoundaryIntervention`：验证当 ZMP 逼近边界（$\le 20\text{mm}$）时，HOCBF 毫秒级重分配底盘加速度与反力矩，ZMP 回弹至安全区；
3. `test03_HierarchicalWbcOptimizer_FourPriorityDecoupling`：验证 4 级优先级级联解耦，低优先级任务对高优先级平衡任务的加速度干涉严格等于零（$\mathbf{J}_1 \mathbf{N}_1 \dot{\mathbf{q}}_2 \equiv \mathbf{0}$）；
4. `test04_HierarchicalWbcOptimizer_DlsSingularityRobustness`：在机械臂完全展开奇异点构型下，DLS 激活阻尼因子，关节力矩绝对值硬截断在安全范围（$\le 150\text{Nm}$），单步求解耗时 $\le 0.5\text{ms}$；
5. `test05_NonStationaryContact_ChassisAccelerationFeedforwardCompensation`：在底盘 $\pm 2.0\text{m/s}^2$ 加减速冲击下，惯性剪切力前馈补偿触发，法向力自适应补强；
6. `test06_NonStationaryContact_FrictionConeZeroSlipInvariant`：验证接触状态严格封闭在库伦摩擦锥内部（$\|\mathbf{f}_t\| < \mu f_n$），相对微滑移速度为 0，滑脱发生率严格为 0；
7. `test07_WholeBodyControlBus_HighFrequencyThroughputAndJitterGuard`：4096 槽位无锁总线高频吞吐，模拟连续 3 帧时钟抖动（$> 2\text{ms}$）自动切入 `DEGRADED_GRAVITY_COMP` 软着陆降级模式；
8. `test08_WholeBodyControlReceipt_Sha256TamperProofVerification`：不可变存证凭单 SHA-256 签名自验通过率 $100\%$，篡改字段检出率 $100\%$。

### 6.2 最小实现文件集合
- 契约测试：`backend/tests/src/test/java/tech/qiantong/qknow/ai/embodied/Phase70WholeBodyLocomanipulationContractTest.java`
- DTO 契约：
  - `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/embodied/wbc/dto/LocomanipulationTaskPriority.java`
  - `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/embodied/wbc/dto/WholeBodyState.java`
  - `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/embodied/wbc/dto/WholeBodyControlReceipt.java`
- 引擎组件：
  - `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/embodied/wbc/engine/CentroidalMomentumGovernor.java`
  - `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/embodied/wbc/engine/HierarchicalWbcOptimizer.java`
  - `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/embodied/wbc/engine/NonStationaryContactForceDistributor.java`
  - `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/embodied/wbc/engine/WholeBodyControlBus.java`

### 6.3 验证命令集
```bash
# 1. 局部编译验证 qknow-ai 模块
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn clean install -pl qknow-framework/qknow-ai -DskipTests

# 2. 运行 Phase 70 专属契约单元测试 (8/8 全绿)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests -Dtest=Phase70WholeBodyLocomanipulationContractTest

# 3. 运行全库全量防退化回归测试套件 (突破 1202/1202 全绿)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests

# 4. 前端生产打包构建校验 (0 错误纯净通过)
cd frontend && npm run build:prod
```

---

## 七、风险评估、停止条件与后续授权边界 (G. 风险、停止条件和后续授权边界)

- **残余风险**：地面突发低摩擦打滑导致支撑多边形几何瞬间失效，依靠保守系数 $\mu_{\text{safe}} = 0.7\mu$ 与底盘急刹重心下沉双重自愈；
- **立即停止条件 (RESEARCH_GATE_BLOCKED)**：
  - 单步 WBC 求解耗时连续 3 帧超过 $1.0\text{ms}$；
  - 动态 ZMP 越过支撑多边形安全边界导致倾翻；
  - 接触力突破摩擦锥导致工件脱落；
  - 任何单元测试或全量回归测试失败。
- **实施授权边界**：第一阶段完成只读检查与定向研究，等待用户批准进入第二阶段编码。
