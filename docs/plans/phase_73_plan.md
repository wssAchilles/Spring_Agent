# Phase 73: 具身多智能体长程装配作业的形式化时序逻辑 (LTL) 验证、模型检测与可微策略综合中枢 实施方案 (Implementation Plan)

## 一、方案背景与执行边界 (Background & Scope)

本方案严格依据 @AGENTS.md 强制门禁规范制定，旨在解决多工业机器人协同执行长程精密装配作业时面临的高维离散-连续状态爆炸、工序时序逻辑偏序错乱、狭窄作业区资源竞争死锁/活锁、以及传统模型检测无法嵌入 1000Hz 硬实时闭环四大核心技术瓶颈。

### 1.1 架构模型基线与物理运行环境约束（强制遵从铁律）

1. **唯一生成模型基线**：
   本系统所有生成侧（长程装配时序规范自动生成、原子命题映射、时序反例因果链推演与策略自愈重规划）**唯一**使用的是 **DeepSeek API**：
   - **DeepSeek-V3 (deepseek-chat)**：极速模型，负责毫秒级自然语言/装配工单向线性时序逻辑 (LTL) 规范编译、原子命题字典提取与技能树节点动态对齐；
   - **DeepSeek-R1 (deepseek-reasoner)**：深度因果推理模型，负责在发生治具空间死锁、工位排队冲突或工具卡死反例时，执行形式化归因与反事实策略重规划。
2. **唯一向量模型基线**：
   本系统所有装配工位几何拓扑、机械臂多轴位姿流形与形式化命题符号的联合表征**唯一**使用的是 **阿里千问 (Qwen) Embedding**（基准维度 d = 1536，强制嵌入并约束在单位超球面流形 S^1535 上，基于内积余弦测地线大圆弧距离进行跨模态几何度量对齐）。
3. **彻底弃用声明**：
   项目中绝无任何本地部署的大语言模型（如本地部署的 LLaVA、端侧重型多模态模型等），且已彻底弃用 OpenAI/GPT API。系统核心在于**利用离线预编译极小乘积有限自动机、在线 O(1) 二维数组无锁查表、平滑 Log-Sum-Exp 可微 STL 空间鲁棒度梯度修正、Disruptor 4096 槽位无锁并发环形总线在 Java 21 本地实时执行 1000Hz 闭环，由云端 DeepSeek 与千问 1536 维超球面提供离线规范编译与高层意图对齐**。
4. **唯一编译与运行环境 (Java 21 隔离运行铁律)**：
   - 本项目后端全量模块统一且**唯一使用 Java 21** 编译与运行。
   - 宿主 Mac 系统全局环境保持为 Java 17；项目专用 Java 21 绝对路径固定为：/Users/achilles/.sdkman/candidates/java/21.0.5-tem。
   - 所有构建、测试与运行必须局部显式传入环境变量 JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem，严禁污染系统全局环境。

---

## 二、唯一待验证假设 (Unique Falsifiable Hypothesis)

> **核心假设 (H-PHASE73-001)**：  
> 构建**乘积有限状态自动机轻量解析模型检测器 (ProductAutomatonModelChecker)、可微时空逻辑 (STL) 鲁棒度在线监控与梯度引导修正器 (DifferentiableStlRobustnessGovernor)、反例引导自愈与无死锁装配策略综合器 (DeadlockFreePolicySynthesizer)、1000Hz 实时高频定长 4096 槽位 Disruptor 无锁时序验证控制总线 (FormalVerificationControlBus)、以及不可变形式化验证存证凭单 (FormalVerificationReceipt)**——  
> 1. **离线极小化乘积自动机预编译与在线微秒级无锁查表**：将上游离散技能与多智能体连续位姿抽象为极小原子命题集 AP，全局 LTL 长程装配规范离线编译为极小乘积自动机（状态数 N <= 64）。纯 Java 21 本地实现 O(1) 二维数组无锁查表，单步状态转移合法性判定耗时严格 <= 1.0ms（实测平均 <= 150us），状态跃迁合法性校验拦截率达到 100%；  
> 2. **可微 STL 平滑鲁棒度监控与纳秒级梯度力矩/速度前馈引导**：采用 Log-Sum-Exp 平滑软近似（Softmin LSE）替代不可导的 min/max 运算，实时推演空间鲁棒度 rho_tilde 及其对末端位姿的解析雅可比梯度。在系统逼近时序违反临界点（rho_tilde < 0.05）时，毫秒内输出修正速度与前馈力矩，有效遏制时序违规发生，违规率降低 95% 以上；  
> 3. **毫秒级反例前缀因果解析与无死锁切向避让策略自愈**：在发生治具口排队竞争或工具等待死锁时，自愈综合器在 <= 2ms 内截获反例前缀（Counterexample Prefix），动态重分配时序优先级并生成切向退出避让流形，死锁解除率达到 100%，杜绝产线停机；  
> 4. **1000Hz 定长无锁总线与 DEGRADED_SAFE_STANDSTILL 软着陆**：4096 槽位 Disruptor 无锁环形缓冲区实现传感器采集、时序验证与执行器指令的纳秒级非阻塞吞吐（写入 <= 50ns）。JitterGuard 监控控制时钟与非法跃迁尝试，连续 3 帧抖动（> 2ms）或发生非法转移尝试时，瞬时切入 DEGRADED_SAFE_STANDSTILL / DEGRADED_SAFE_HOVER 安全软着陆模式，杜绝硬冲击破坏工件；  
> 5. **不可变形式化验证密码学存证**：生成封装凭单唯一 ID、会话 ID、工位治具 ID、LTL 规范哈希、DFA 状态序列哈希、瞬时 STL 鲁棒度指标、死锁自愈标记、检测耗时、总线状态与 SHA-256 防篡改签名的 Java 21 Record 凭单，验真通过率 100%。

---

## 三、核心契约类与工程落地设计

### 3.1 核心包路径结构
位于 `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/embodied/formal/`：
- `dto/`
  - `LtlSpecificationFormula.java`：Java 21 Record，封装时序逻辑规范 ID、原始 LTL 表达式、原子命题字典、总状态数、DFA 状态转移表、接受状态集合、初始状态与时间戳；
  - `MultiAgentAssemblyState.java`：Java 21 Record，封装工位 ID、当前 DFA 状态、激活原子命题位图、多臂末端位姿矩阵、外部接触力矩矩阵、瞬时平滑 STL 鲁棒度、阿里千问 1536 维超球面单位向量与时间戳；
  - `FormalVerificationReceipt.java`：不可变存证凭单 Java 21 Record，封装凭单唯一 ID、会话 ID、工位 ID、LTL 规范哈希、DFA 路径哈希、瞬时 STL 鲁棒度、最小鲁棒度裕度、死锁自愈标记、单步耗时、总线状态、时间戳与 SHA-256 密码学自签名；
- `engine/`
  - `ProductAutomatonModelChecker.java`：乘积有限状态自动机轻量解析模型检测器，实现 O(1) 二维数组无锁查表与非法状态跃迁 100% 拦截；
  - `DifferentiableStlRobustnessGovernor.java`：可微时空逻辑 (STL) 平滑鲁棒度在线监控与梯度引导修正器；
  - `DeadlockFreePolicySynthesizer.java`：反例引导自愈与无死锁装配策略综合器；
  - `FormalVerificationControlBus.java`：1000Hz 实时高频定长 4096 槽位 Disruptor 无锁时序验证控制总线与 DEGRADED_SAFE_STANDSTILL 软着陆降级保护器。

---

## 四、专属契约测试设计 (Phase 73 Contract Tests)

在 `backend/tests/src/test/java/tech/qiantong/qknow/ai/embodied/Phase73FormalLtlAssemblyVerificationContractTest.java` 中落地 8 项严苛契约测试：
1. `testProductAutomatonMicrosecondStepValidation`：验证乘积有限自动机单步状态转移无锁查表耗时严格 <= 1.0ms（实测 <= 150us），合法转移正确推进且接受态判定准确；
2. `testIllegalStateTransitionInterception`：验证在发生非法时序跃迁（如未紧固即触发吊运、未抓取即插入）时，模型检测器拦截率达到 100%，系统被限制在安全陷阱态或当前态；
3. `testHypersphericalAssemblyStateEmbeddingNorm`：验证多智能体装配时空状态特征向量严格满足阿里千问 1536 维超球面单位向量归一化不变量（||v||_2 = 1.0 +- 1e-6）；
4. `testSmoothStlRobustnessSoftminDifferentiability`：验证基于 Softmin Log-Sum-Exp 的平滑 STL 鲁棒度连续可微，逼近误差有界于 ln(m)/beta，且解析梯度方向严格指向空间安全裕度增加方向；
5. `testDifferentiableStlGradientVelocityCorrection`：验证当系统逼近时序违反临界点（rho_tilde < 0.05）时，梯度引导修正器毫秒级输出前馈修正速度，成功阻断时序违规；
6. `testCounterexampleGuidedDeadlockResolution`：验证在多智能体狭窄工位发生资源互斥死锁前夕，自愈综合器在 <= 2ms 内捕获反例前缀并重分配优先级和切向避让流形，死锁解除率 100%；
7. `testLockFreeBus1000HzThroughputAndDegradedStandstillTrigger`：验证 4096 槽位 Disruptor 无锁环形总线非阻塞高频吞吐（写入 <= 50ns），时钟连续 3 帧抖动（> 2ms）或非法跃迁尝试时，自动平稳切入 DEGRADED_SAFE_STANDSTILL 软着陆；
8. `testFormalVerificationReceiptSha256Verification`：验证不可变存证凭单全要素字段完整性与 SHA-256 密码学防篡改签名验真通过率 100%。
