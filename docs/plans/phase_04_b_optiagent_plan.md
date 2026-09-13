# Phase 04-B: 运筹优化智能体 (OptiAgent) 与 Gurobi 引擎集成

本文件是 Phase 04-B 阶段的严格实施契约。基于用户提供的 Gurobi WLS 许可证，本阶段旨在结合唯一的生成侧大脑（DeepSeek API），赋予系统处理复杂数学规划与运筹决策的能力。

## A. 当前代码与失败机制

**当前执行路径与痛点**：
目前系统（通过 DeepSeek API）能够出色地处理自然语言语义与检索问答。但在面对企业级核心决策（如：预算分配约束、物流调度、生产排期等需要精准数学规划的场景）时，大语言模型的自回归机制会导致严重的“数学幻觉（Mathematical Hallucination）”，无法保证约束的绝对满足与解的最优性。
**本阶段唯一待验证假设**：
通过引入 Python 侧车微服务挂载 Gurobi WLS 求解引擎，并在系统中编排“翻译 -> 代码生成 -> 沙盒求解 -> 结果反刍”的 OptiAgent 工具链，能够使 DeepSeek 精确解决业务约束问题，且彻底杜绝复杂决策幻觉。

---

## B. Research Ledger (运筹学交叉与工程部署)

| id | sourceType | titleOrRepository | authorsOrMaintainer | venueAndYear | verificationStatus | relevantFinding | projectApplicability | limitations |
|---|---|---|---|---|---|---|---|---|
| 1 | paper | OptiGuide: LLMs for Supply Chain Optimization | Microsoft Research | 2023 | VERIFIED | LLM 作为代码生成器编写 Gurobi/Python 代码，通过工具执行求得最优解。 | 直接支撑 OptiAgent 的运行机制。 | 依赖底层的独立求解环境。 |
| 2 | official-doc | Gurobi WLS Documentation | Gurobi | 2024 | VERIFIED | 无需安装完整客户端，只需通过 API 配置 Access ID、Secret 即可拉起云端授权。 | 解决 Java 端依赖本地 C++ 动态库的痛点。 | 必须保证网络连接通畅。 |
| 3 | official-code| gurobipy (Python API) | Gurobi | 2024 | VERIFIED | `pip install gurobipy` 内置了所有底层二进制依赖，并且可以利用 Python `with` 语法自动管控内存释放。 | 完美契合免环境安装的微服务需求。 | Java 官方包难以做到同等轻量。 |

---

## C. 可迁移与不可迁移结论

**可直接采用的方案：**
1. **OptiAgent 范式 (Translator -> Coder -> Solver)**：不在 Prompt 中强迫 DeepSeek 直接输出最优解，而是让 DeepSeek 输出一段使用 `gurobipy` 的 Python 建模代码，发送给专门的执行侧车。
2. **全局 Env 共享单例 (WLS Lifecycle)**：在应用启动时拉起全局唯一的 `gurobipy.Env(empty=True)` 并在代码中动态注入 WLS 凭证，避免高并发下反复请求授权服务器导致的限流与数百毫秒网络延迟。

**必须拒绝的方案：**
1. **纯 Java (Spring Boot) 直接集成**：Gurobi 未将核心库接入 Maven Central，且 Java API 强依赖本地物理机安装的 `libGurobiJni.dylib/so`。部署太重，完全违背云原生与跨平台开发原则。
2. **基于模型本身参数的数学推导**：坚决摒弃让 LLM 做约束计算，它只负责理解人类语言并将其抽象为数学公式。

---

## D. 候选方案比较

| 维度 | 纯 Java JNI 集成 | Python 侧车 (FastAPI + gurobipy) |
|---|---|---|
| **部署与依赖** | 极重，需要宿主机物理安装 Gurobi 环境。 | **极轻**，`pip install` 直接打包了全部底层 C 库。 |
| **代码与内存安全** | 易因未显式 `dispose()` 导致 Native 层内存泄漏。 | **极高**，通过 `with gp.Model()` 上下文语法彻底防侧漏。 |
| **WLS 鉴权管理** | 需要在物理机挂载 `gurobi.lic` 文件。 | **灵活**，直接通过代码无感注入 `.env` 凭证。 |
| **架构解耦性** | 与 Spring Boot 强耦合，容易引发进程崩溃。 | **完美隔离**，计算密集型任务剥离到独立微服务进程。 |
| **结论** | 拒绝 | **推荐使用** |

---

## E. 推荐的最小算法与架构

**核心架构：Python Sidecar 运筹引擎 + DeepSeek Tool Calling**

1. **Python 侧车微服务 (`opti_server.py`)**：
   - 使用 FastAPI 暴露 HTTP 接口。
   - 启动时初始化全局共享的 WLS 授权环境（`gp.Env`）。
   - 接收 DeepSeek 编写的 Python 运筹代码片段，在受限的安全沙盒（`exec`）中注入 `env=global_env` 并运行。
2. **自动反事实推理 (IIS Error Recovery)**：
   - 运筹引擎捕获到不可行（Infeasible）状态时，自动触发 `Model.computeIIS()`。
   - 将相互冲突的约束方程提取并回传，交由 DeepSeek 翻译为人类语言：“您的预算限制（最大10万）与运力需求（最少5车）产生冲突”。
3. **Java 端 Agent 接入**：
   - 注册新的 Tool: `GurobiOptimizerTool`。
   - 配置给 DeepSeek。

---

## F. 实验与实现计划 (TDD 约束)

1. **最小实现文件集合**：
   - `backend/tools/opti_server.py` (包含 FastAPI, WLS 初始化, 内存安全 `with` 块与沙盒执行逻辑)。
   - `backend/tools/requirements_opti.txt` (包含 `gurobipy`, `fastapi`, `uvicorn`)。
   - `backend/qknow-module-kb/src/main/java/tech/qiantong/qknow/kb/tool/GurobiOptimizerTool.java` (Java 端大模型工具封装)。

2. **数据流与不可变量**：
   - WLS 凭证必须从根目录 `.env` (或启动脚本环境变量) 安全载入 Python 侧车，绝不硬编码。

3. **测试验收标准 (TDD Passing Criteria)**：
   - 断言：启动 `opti_server.py` 后，向其发送经典运输问题的 `gurobipy` 代码，能正常返回 JSON 最优解，无 License 报错。
   - 断言：发送相互矛盾的约束，接口能正确抛出 `status="INFEASIBLE"` 及 IIS 冲突集。

---

## G. 风险、停止条件和后续授权边界

1. **残余风险**：沙盒安全性风险。由于使用了 `exec()` 运行大模型生成的代码，存在越权执行恶意命令的隐患。必须在 Python 侧车严格限制 `locals()` 与 `globals()` 命名空间，封禁 `os`, `sys` 等高危模块。
2. **立即停止条件 (Kill Switch)**：
   - 如果 WLS Server 触发 `ERROR 10009: License is expired or invalid`，立即挂起该微服务并降级返回错误提示。
   - 如果单次求解时间超过硬性 `TimeLimit`（如 10秒），触发异常中止，防止 CPU 耗尽。
3. **下一步执行边界**：
   - 当前状态：**计划已就绪，等待用户审批。**
   - **未经明确 [同意] 授权前，绝不创建或修改上述相关代码文件。**
