# Phase 74: 具身多智能体柔性装配线因果数字孪生、多保真度混合仿真闭环与实时异常自愈中枢 实施方案 (Implementation Plan)

## 一、方案背景与执行边界 (Background & Scope)

本方案严格依据 @AGENTS.md 强制门禁规范制定，旨在解决汽车动力总成、航空发动机与高压电池模组等复杂高端制造柔性装配线上面临的四大核心挑战：
1. **多工位复杂物料流与因果混淆**：下游工位过扭矩、卡塞等伴生表象经常被误判为单点硬件故障，引发全线盲目急停；
2. **数字孪生仿真精度与 1000Hz 硬实时闭环割裂**：高保真非光滑多体接触仿真计算延迟高达数十毫秒，而极低保真降阶模型存在累积漂移；
3. **自愈纠偏缺乏高阶动力学安全硬约束**：启发式回退与激进微调容易与相邻工位夹具或输送带发生二次刚性干涉碰撞；
4. **数字孪生总线抗抖动与保底软着陆真空**：网络抖动或突发阻抗超标时传统 E-STOP 刚性抱闸剧烈冲击易损毁高精工件与减速器。

### 1.1 架构模型基线与物理运行环境约束（强制遵从铁律）

1. **唯一生成模型基线**：
   本系统所有生成侧（产线因果拓扑定义、结构方程 SCM 编译、反事实根因因果链长推演与自愈动作策略综合）**唯一**使用的是 **DeepSeek API**：
   - **DeepSeek-V3 (deepseek-chat)**：极速模型，负责毫秒级自然语言装配工艺向因果 DAG 拓扑映射与工位依赖字典初始化；
   - **DeepSeek-R1 (deepseek-reasoner)**：深度因果推理模型，负责在发生跨工位多源复杂耦合卡阻时，执行深度因果反事实溯源与全局自愈策略参数推导。
2. **唯一向量模型基线**：
   本系统所有装配工位几何拓扑、机械臂多轴位姿流形与数字孪生几何状态的联合表征**唯一**使用的是 **阿里千问 (Qwen) Embedding**（基准维度 d = 1536，强制嵌入并约束在单位超球面流形 S^1535 上，基于内积余弦测地线大圆弧距离进行跨模态度量对齐）。
3. **彻底弃用声明**：
   项目中绝无任何本地部署的大语言模型（如本地部署的 LLaVA、端侧小模型等），且已彻底弃用 OpenAI/GPT API。系统核心在于**利用纯内存因果拓扑矩阵、微秒级无锁因果反事实逆推、低保真/高保真混合物理动力学残差平滑加权、高阶控制屏障证书 (HOCBF) 硬安全投影、Disruptor 4096 槽位无锁并发环形总线在 Java 21 本地实时执行 1000Hz 闭环，由云端 DeepSeek 与千问 1536 维超球面提供离线规范编译与复杂长链决策支持**。
4. **唯一编译与运行环境 (Java 21 隔离运行铁律)**：
   - 本项目后端全量模块统一且**唯一使用 Java 21** 编译与运行。
   - 宿主 Mac 系统全局环境保持为 Java 17；项目专用 Java 21 绝对路径固定为：/Users/achilles/.sdkman/candidates/java/21.0.5-tem。
   - 所有构建、测试与运行必须局部显式传入环境变量 JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem，严禁污染系统全局环境。

---

## 二、唯一待验证假设 (Unique Falsifiable Hypothesis)

> **核心假设 (H-PHASE74-001)**：  
> 构建**装配线因果拓扑与结构因果模型引擎 (AssemblyCausalInferenceEngine)、多保真度混合物理仿真调度器 (MultiFidelitySimulationGovernor)、因果反事实装配异常自愈规划器 (CounterfactualSelfHealingPlanner)、1000Hz 实时高频定长 4096 槽位 Disruptor 无锁数字孪生控制总线 (DigitalTwinRealtimeBus)、以及不可变因果数字孪生密码学存证凭单 (CausalDigitalTwinReceipt)**——  
> 1. **全线因果 DAG 建模与毫秒级反事实溯源**：维护 30+ 关键工位节点依赖因果 DAG 图。在装配发生异常时，基于结构因果模型 (SCM) 在 1.0ms 内完成因果反推（Abduction）与反事实逆推，准确辨识前道上料超差、治具定位偏转、夹爪力矩欠驱动等根本原因，根因辨识率 >= 98%，有效隔离下游伴生症状；  
> 2. **多保真度混合物理仿真残差自适应平滑切换**：低保真降阶动力学模型 (LF-ROM, 步长 <= 100us) 负责 1000Hz 实时预测，高保真刚柔接触动力学仿真器 (HF-Sim) 负责关键接触瞬态校准。通过瞬态残差置信度加权与李雅普诺夫平滑过渡函数，保证加速度与力矩输出 C^2 平滑连续，杜绝模型突变切换引发的控制啸叫，数字孪生保真度稳定保持 >= 99%；  
> 3. **反事实自愈策略与高阶控制屏障证书 (HOCBF) 硬安全门禁**：自愈规划器在线生成微米/毫米级位姿微调、柔顺力矩补偿与输送节拍等待指令，并由 HOCBF 进行微秒级非侵入式二次规划安全投影滤波。在保证绝对无碰撞干涉的前提下，自愈成功率 >= 95%，全线异常停线率削减 90% 以上；  
> 4. **1000Hz 定长无锁总线与 DEGRADED_LINE_HOLD 柔顺防撞**：4096 槽位 Disruptor 无锁环形缓冲区实现多工位传感器采集、孪生状态同步与自愈指令下发的纳秒级非阻塞吞吐（写入 <= 50ns）。JitterGuard 监控连续 3 帧时钟抖动（> 2ms）或物理阻抗异常时，瞬时切入 DEGRADED_LINE_HOLD 柔顺悬停保底模式，杜绝硬冲击损伤工件与设备；  
> 5. **不可变因果数字孪生密码学存证**：生成封装凭单唯一 ID、会话 ID、产线 ID、因果图哈希、模型保真度级别、残差指标、自愈决策向量、单步耗时、总线状态与 SHA-256 防篡改签名的 Java 21 Record 凭单，完整性自验通过率 100%。

---

## 三、核心契约类与工程落地设计

### 3.1 核心包路径结构
位于 `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/embodied/digitaltwin/`：
- `dto/`
  - `AssemblyCausalGraph.java`：Java 21 Record，封装产线 ID、30+ 工位节点名称列表、因果有向边邻接矩阵、结构方程参数集与编译时间戳；
  - `DigitalTwinFrameState.java`：Java 21 Record，封装产线 ID、当前工步、各工位末端位姿矩阵、各工位接触力矩矩阵、当前保真度层级、瞬时残差指标、阿里千问 1536 维超球面单位特征向量与时间戳；
  - `CausalDigitalTwinReceipt.java`：不可变存证凭单 Java 21 Record，封装凭单唯一 ID、会话 ID、产线 ID、因果图哈希、根因工位 ID、保真度级别、残差指标、自愈决策摘要、单步耗时、总线状态、时间戳与 SHA-256 密码学防篡改自签名；
- `engine/`
  - `AssemblyCausalInferenceEngine.java`：装配线因果拓扑与结构因果模型引擎，在线 1.0ms 内完成根因溯源与伴生症状隔离；
  - `MultiFidelitySimulationGovernor.java`：多保真度混合物理仿真调度器，实现低保真 LF-ROM 极速推演与李雅普诺夫连续平滑切换；
  - `CounterfactualSelfHealingPlanner.java`：因果反事实装配异常自愈规划器，融合 HOCBF 高阶控制屏障硬安全门禁；
  - `DigitalTwinRealtimeBus.java`：1000Hz 实时高频定长 4096 槽位 Disruptor 无锁数字孪生控制总线与 DEGRADED_LINE_HOLD 柔顺防撞保护器。

---

## 四、专属契约测试设计 (Phase 74 Contract Tests)

在 `backend/tests/src/test/java/tech/qiantong/qknow/ai/embodied/Phase74CausalDigitalTwinAssemblyContractTest.java` 中落地 8 项严苛契约测试：
1. `testAssemblyCausalGraphTopologyValidation`：验证装配线因果 DAG 拓扑满足无环性（有向无环条件），节点覆盖 30+ 关键工位且因果邻接矩阵构造正确；
2. `testMicrosecondCounterfactualRootCauseInference`：验证结构因果模型在发生复杂装配异常时，单步因果溯源耗时严格 <= 1.0ms（实测 <= 200us），准确区分上游根因并隔离下游伴生症状，根因辨识率 >= 98%；
3. `testHypersphericalDigitalTwinStateEmbeddingNorm`：验证数字孪生状态特征向量严格满足阿里千问 1536 维超球面单位向量归一化不变量（||v||_2 = 1.0 +- 1e-6）；
4. `testMultiFidelityLyapunovBoundedErrorConvergence`：验证多保真度混合仿真闭环跟踪误差满足李雅普诺夫指数衰减与一致最终有界 (UUB) 不等式，极限误差有界且保真度 >= 99%；
5. `testSmoothTransitionBetweenHighAndLowFidelity`：验证在高低保真模型切换瞬态，李雅普诺夫连续加权律输出力矩与加速度保持 C^2 连续，无高频抖振与力矩阶跃爆表；
6. `testCounterfactualSelfHealingWithHocbfSafetyGate`：验证反事实自愈规划器在线生成微米/毫米位姿与力矩补偿，HOCBF 硬安全门禁 100% 拦截越界激进动作，零次生碰撞且自愈成功率 >= 95%；
7. `testDisruptorBus1000HzThroughputAndDegradedHoldTrigger`：验证 4096 槽位 Disruptor 无锁环形总线纳秒级高频非阻塞吞吐（写入 <= 50ns），连续 3 帧时钟抖动（> 2ms）或严重物理阻抗异常时，自动切入 DEGRADED_LINE_HOLD 恒力柔顺悬停；
8. `testCausalDigitalTwinReceiptSha256Verification`：验证不可变因果存证凭单全要素字段完整性与 SHA-256 密码学防篡改签名自验通过率 100%。
