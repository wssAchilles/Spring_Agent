# Phase 03: 算法与机制优化 (检索加速与工具调用治理)

> **前置要求**: 依据 `AGENTS.md` Research-to-Implementation Gate 门禁标准制定。
> **目标阶段**: 解决 RAG-v2 系统在高并发下的底层算力瓶颈 (N0) 以及大模型 Agent 陷入死循环的失控风险 (R0)。

## 1. 文献支撑与技术选型依据

### 1.1 学术理论依据 (Academic Evidence)
*   **Agent 上下文爆炸与马尔可夫死锁**: 学术界指出，大模型工具调用失败极易导致“感知-推理-行动”循环陷入局部最优解（马尔可夫死锁）。同时，无节制地将长尾输出塞入上下文会导致注意力平方级复杂度爆炸，引发“中间迷失”。必须引入**状态机断路器 (Circuit Breaker)** 与 **快照截断策略**。
*   **高维向量计算的 JVM 灾难**: 在处理 MaxSim 等密集型相似度计算时，Java 堆内存分配极易引发 GC 停顿（长尾延迟），且对象数组在物理内存中的非连续性会彻底破坏 CPU 预取，导致 L1/L2 缓存未命中率激增。
*   **SIMD 原生加速**: 引入底层语言（Rust），通过一维扁平数组完美对齐缓存行，并显式调用处理器的单指令多数据流（SIMD/AVX2）指令，实现计算降维打击。

### 1.2 工业界落地实践 (Engineering Practices)
*   **防守型 Agent 工程 (Defensive Engineering)**: 对标 LangChain 与大厂架构，弃用单一超时，改用全局端到端时间预算 (End-to-End Budget)。引入 **Spill (卸载) 模式**，当工具返回超长数据 (如 > 10KB) 时，将其落盘至临时区，仅向 LLM 返回文件 URI 摘要，杜绝上下文雪崩；并通过参数 Hash 比对进行语义死循环熔断。
*   **FFM API 与零拷贝 (Zero-Copy)**: 针对 Java/Rust 的边界开销，坚决弃用老旧的 JNI 和 `GetPrimitiveArrayCritical` (会导致 Stop-the-World)。改用 JDK 21+ 引入的 **Project Panama (FFM API)**。通过堆外直接内存（`DirectByteBuffer` / `Arena`）分配向量空间，将裸指针直接透传给 Rust，实现内存零拷贝。通过粗粒度批处理 (Batch Processing) 彻底均摊跨界调用的上下文切换成本。

---

## 2. 模块接口与数据流设计

### 2.1 Agent 工具治理数据流 (Defensive Tool Executor)
`LLM 发起调用` -> `Executor 拦截参数` -> `检查 Budget 与 Hash 循环历史` -> `触发熔断 或 执行工具` -> `获取结果` -> `长度超限则 Spill(落盘换 URI)，否则截断(Head-Tail)` -> `返回给 LLM`。

### 2.2 FFM 零拷贝向量加速数据流 (Zero-Copy SIMD)
`Java 初始化全局 Arena` -> `将候选 Doc 向量写入直接内存` -> `Java MethodHandle 调用 Rust FFI` -> `透传 MemorySegment (指针) 与大小` -> `Rust 侧直接转化为 &[f32] 并执行 AVX2 并发点积` -> `就地覆写 topK 结果` -> `Java 侧直接读取`。

---

## 3. 细化编码待办事项 (TDD To-Dos)

#### [NEW] `backend/src/main/java/tech/qiantong/qknow/agent/DefensiveToolExecutor.java`
*   **任务**: 包装原有的工具执行器。引入 `Timeout` 预算机制；引入参数 Hash 记录表检测 `Semantic Loop` (死循环)；实现结果的 `Head-Tail Truncation` 与 `Spill to Disk` 逻辑。

#### [MODIFY] `backend/src/main/java/tech/qiantong/qknow/hermes/search/ColbertNative.java`
*   **任务**: 全面重构旧版 JNI。基于 JDK FFM API (`java.lang.foreign.*`) 编写底层桥接。在堆外分配向量数组，通过 `Linker` 绑定 Rust 导出的 `maxsim_batch` 函数。

#### [MODIFY] `backend/rust/src/lib.rs` (Native Compute Engine)
*   **任务**: 编写无 GC 的 Rust 动态库 (`cdylib`)。暴露标准的 C ABI。接收裸指针后，使用 `std::arch` 汇编级宏（如 `_mm256_fmadd_ps`）显式并发执行 MaxSim 并打分。

---

## 4. 验收准则与测试规范 (Acceptance Criteria)

### 4.1 单元测试准则 (TDD Unit Tests)
*   **`DefensiveToolExecutorTest.java`**: 
    1. 模拟 LLM 连续 3 次传入完全相同的参数报错，断言第 4 次触发 `CircuitBreakerException`。
    2. 模拟工具返回 5MB 大小的日志字符串，断言返回给大模型的上下文被成功截断为 `<...[truncated]...>` 或 `FileURI`，且总长度受控。
*   **`ColbertFFITest.java`**: 针对 5000 条长文本的向量数组，断言 FFM 调用的计算结果与 Java 纯双层循环的计算分数误差绝对值 `< 1e-4`。

### 4.2 集成测试与流水线准则 (Integration Gate)
*   **N0 基准测试 (Benchmark)**: 在 GitHub Actions 中执行 JMH 压测。基于 Rust SIMD + FFM API 的单次批量查询延迟 (p95) 必须比纯 Java 实现**至少加速 5 倍以上**。若不满足该基准，根据门禁原则将拒绝采用原生加速，回退至纯 Java 方案。
