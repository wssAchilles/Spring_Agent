# Phase 72: 具身智能体接触富集型流体-刚体动力学协同、非牛顿流体抓取分注与微观界面多相流控制中枢 实施方案 (Implementation Plan)

## 一、方案背景与执行边界 (Background & Scope)

本方案严格依据 @AGENTS.md 强制门禁规范制定，旨在解决具身机器人在面对开放自由液面流体、高粘度粘弹性非牛顿流体（如紫外固化胶、环氧树脂、导电银浆、化学试剂）时的接触富集型流固耦合 (FSI) 动力学失稳、晃荡飞溅溢出与毛细拉丝挂滴污染三大核心物理瓶颈。

### 1.1 架构模型基线与物理运行环境约束（强制遵从铁律）

1. **唯一生成模型基线**：
   本系统所有生成侧（流体操作工序规划、容器几何与流变学参数解析、自由液面失稳与液击因果推演）**唯一**使用的是 **DeepSeek API**：
   - **DeepSeek-V3 (deepseek-chat)**：轻量高速通用生成模型，负责毫秒级解析宏观流体操作指令、规划容器搬运时序与分注流率参数；
   - **DeepSeek-R1 (deepseek-reasoner)**：深度因果推理模型，负责在发生容器共振晃荡突发飞溅、非牛顿聚合物拉丝失控或倾倒回卷激波液击时，进行微观连续介质流体力学因果推演与安全回退决策。
2. **唯一向量模型基线**：
   本系统所有自由液面波高场、容器多边形几何与流变特征的高维表征**唯一**使用的是 **阿里千问 (Qwen) Embedding**（基准维度 d = 1536，强制嵌入并约束在单位超球面流形 S^1535 上，基于内积余弦测地线大圆弧距离进行跨模态几何度量对齐）。
3. **彻底弃用声明**：
   项目中绝无任何本地部署的大语言模型（如本地部署的 LLaVA、端侧重型多模态模型等），且已彻底弃用 OpenAI/GPT API。系统核心在于**利用轻量微秒级数学降阶算子（等效机械单摆降阶 ROM、幂律流变 CaBER 拉丝断裂时间解析式、自由液面等效重力对齐 HOCBF、Disruptor 4096 槽位无锁并发环形总线）在 Java 21 本地实时执行 1000Hz 闭环，由云端 DeepSeek 与千问 1536 维超球面提供高层意图对齐**。
4. **唯一编译与运行环境 (Java 21 隔离运行铁律)**：
   - 本项目后端全量模块统一且**唯一使用 Java 21** 编译与运行。
   - 宿主 Mac 系统全局环境保持为 Java 17；项目专用 Java 21 绝对路径固定为：/Users/achilles/.sdkman/candidates/java/21.0.5-tem。
   - 所有构建、测试与运行必须局部显式传入环境变量 JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem，严禁污染系统全局环境。

---

## 二、唯一待验证假设 (Unique Falsifiable Hypothesis)

> **核心假设 (H-PHASE72-001)**：  
> 构建**轻量解析流固耦合与自由液面晃荡动力学降阶算子 (FluidDynamicsReducedOperator)、非牛顿流体流变学与防拉丝回抽控制器 (NonNewtonianRheologyGovernor)、自由液面防晃荡防飞溅流形轨迹规划器 (FluidSloshSuppressionPlanner)、1000Hz 实时高频定长无锁流体控制总线 (FluidControlBus)、以及不可变流体操作存证凭单 (FluidManipulationReceipt)**——  
> 1. **等效机械单摆降阶流体动量耦合微秒级推演**：基于连续介质不可压缩势流理论与一阶非线性等效单摆模型（Equivalent Mechanical Pendulum Model），将三维自由表面晃荡流体降阶为刚性容器结合质量-单摆耦合动力学。彻底摒弃显存密集的离线黑盒 CFD 求解器，以纯 Java 21 解析矩阵与闭式常微分方程递推求解，单步自由液面晃荡角 theta_s 与质心位移推演耗时严格 <= 1.0ms（实测平均 <= 150us），预测主晃荡频率与基准相对误差 <= 4.2%；  
> 2. **非牛顿流变学与微秒级反转回抽切断**：支持 Ostwald-de Waele 幂律本构（涵盖牛顿流体 n=1、剪切变稀假塑性流体 n<1 与剪切变稠胀塑性流体 n>1），结合毛细破裂拉伸流变模型（CaBER）动态评估聚合物拉伸断裂临界时间 tau_break。在分注截断时刻触发微秒级“反转微步回抽（Reverse Suck-back）+ 法向瞬时切断（Normal Snip-off）”协同控制，胶液拉丝截断率达 100%，彻底杜绝挂滴与精密焊盘/金线微观污染；  
> 3. **等效重力矢量对齐与自由液面防溢出高阶控制屏障 (Free-Surface HOCBF)**：在空间多轴平移加减速与末端旋转搬运全时程中，主动将容器对称轴与合加速度矢量 g_eff = g - a_ee 实时对齐，并构建容器开口几何边缘高度屏障函数 h_spill(x) = H_lip - H_0 - R * tan(theta_slosh) >= 0。在动态搬运中，液体飞溅溢出拦截率达到 100%；  
> 4. **1000Hz 定长无锁总线与 DEGRADED_SAFE_HOVER 软着陆**：4096 槽位 Disruptor 无锁环形缓冲区实现传感器采集、液面状态推演与伺服指令的纳秒级非阻塞吞吐（写入 <= 50ns）。JitterGuard 监控控制时钟与残余晃荡动能，连续 3 帧抖动（> 2ms）或残余晃荡动能突增时，瞬时切入 DEGRADED_SAFE_HOVER 防溢出悬停软着陆模式，杜绝硬抱闸引发液体飞溅；  
> 5. **不可变流体操作密码学存证**：生成封装会话 ID、容器/工件 ID、分注容积、残余晃荡动能均值、最大液面倾角裕度、非牛顿流变粘度指标、求解耗时、总线状态与 SHA-256 防篡改签名的 Java 21 Record 凭单，验真通过率 100%。

---

## 三、核心契约类与工程落地设计

### 3.1 核心包路径结构
位于 `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/embodied/fluid/`：
- `dto/`
  - `FluidSloshState.java`：Java 21 Record，封装容器几何（内径 R、有效高度 H、开口边缘裕度 H_lip）、充液静止高度 H_0、流体密度 rho、动力粘度 mu、瞬时自由液面晃荡角 theta_s、角速度 theta_dot_s、残余晃荡动能 E_k、末端合加速度 a_ee、反作用力矩 tau_slosh、阿里千问 1536 维超球面形态嵌入向量与时间戳；
  - `LiquidDispensingCommand.java`：Java 21 Record，封装任务 ID、流体介质类型（牛顿流体 / 剪切变稀 / 剪切变稠）、幂律稠度系数 K、流变指数 n、松弛时间 lambda_E、目标分注体积 V_target、针嘴内径 d_nozzle、回抽补偿位移 delta_x_retract、时间戳；
  - `FluidManipulationReceipt.java`：不可变存证凭单 Java 21 Record，封装会话 ID、容器/工件 ID、分注体积、残余晃荡动能均值、最大晃荡角裕度、表观粘度指标、回抽截断率、降阶推演耗时、总线降级标志、时间戳与 SHA-256 密码学防篡改签名，内置自验逻辑；
- `engine/`
  - `FluidDynamicsReducedOperator.java`：轻量解析流固耦合与自由液面晃荡动力学降阶算子；
  - `NonNewtonianRheologyGovernor.java`：非牛顿流体流变学与防拉丝回抽控制器；
  - `FluidSloshSuppressionPlanner.java`：自由液面防晃荡防飞溅流形轨迹规划器；
  - `FluidControlBus.java`：1000Hz 实时高频定长 4096 槽位 Disruptor 无锁并发总线与 DEGRADED_SAFE_HOVER 软着陆降级保护器。

---

## 四、专属契约测试设计 (Phase 72 Contract Tests)

在 `backend/tests/src/test/java/tech/qiantong/qknow/ai/embodied/fluid/Phase72FluidStructureManipulationContractTest.java` 中落地 8 项严苛契约测试：
1. `testReducedOrderSloshingDynamicsMicrosecondPerformance`：验证降阶流体等效单摆推演单步耗时严格 <= 1.0ms（实测 <= 150us），主晃荡频率预测准确且能量守恒误差 <= 4.2%；
2. `testHypersphericalFluidStateEmbeddingNorm`：验证流体状态与自由液面特征严格满足阿里千问 1536 维超球面单位向量归一化不变量（||v||_2 = 1.0 +- 1e-6）；
3. `testNonNewtonianPowerLawRheologyShearThinningAndThickening`：验证幂律流变学本构计算，剪切变稀（n < 1）时粘度随剪切率单调下降，剪切变稠（n > 1）时粘度随剪切率单调上升，且零剪切与无限剪切粘度边界保正；
4. `testCapillaryBreakupAndReverseSuckBackFilamentElimination`：验证在 CaBER 毛细液桥断裂窗口内触发微秒级反转回抽与法向瞬时切断，拉丝消除截断率达 100%，杜绝挂滴污染；
5. `testEquivalentGravityAlignmentAndFreeSurfaceSloshSpillBarrier`：验证机械臂平移加速过程中等效重力矢量主动对齐，且自由液面开口边缘防溢出 HOCBF 屏障生效，飞溅溢出拦截率 100%；
6. `testLockFreeBus1000HzThroughputAndDegradedSafeHoverTrigger`：验证 4096 槽位 Disruptor 无锁环形总线非阻塞高频吞吐（写入 <= 50ns），时钟连续 3 帧抖动（> 2ms）或晃荡动能突增时，自动平稳切入 DEGRADED_SAFE_HOVER 软着陆；
7. `testFluidManipulationReceiptSha256Verification`：验证不可变存证凭单全要素字段完整性与 SHA-256 密码学防篡改签名验真通过率 100%；
8. `testEndToEndFluidDispensingAndSloshSuppressionConvergence`：验证端到端流体运送与分注全流程闭环协同，残余晃荡动能平稳耗散收敛，分注体积精度与防飞溅性能双达标。
