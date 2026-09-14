# Phase 30 实施方案：代码智能体与轻量多租户代码沙箱安全自愈执行闭环 (Code Agent, Sandboxed Multi-Tenant Execution & Self-Healing Loop)

> **文档状态**：Designed / Decision-Complete（待用户明确授权后启动 TDD 编码）  
> **依据规范**：`AGENTS.md` Research-to-Implementation Gate | `docs/plans/00_master_index.md` | `plans/RAG长期优化链路-v2.md`  
> **前置调研**：[`docs/plans/phase_30_academic_report.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/plans/phase_30_academic_report.md) (学术理论与收敛性证明) & [`docs/plans/phase_30_industrial_report.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/plans/phase_30_industrial_report.md) (工业架构与避坑对标)  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（代码生成使用 `deepseek-chat` 即 V3，链式反思自愈使用 `deepseek-reasoner` 即 R1，流式 SSE 输出）；唯一向量模型为 **阿里千问 (Qwen) Embedding**（1536 维，单位超球面 $\mathbb{S}^{1535}$ 归一化内积余弦度量）；绝无本地大模型与 OpenAI API；唯一编译运行环境为 Java 21 隔离虚拟环境（`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）。

---

## 一、目标与唯一待验证假设 (Sole Falsifiable Hypothesis)

### 1.1 现状与痛点剖析
1. **环境变量深度透传引发生产凭证泄露**：
   当前代码中（如 `DeepkeExtractionServiceImpl.java` 与 `StdioMcpClient.java`）使用原始 `ProcessBuilder` 启动子进程，默认无条件继承宿主 Java 进程的全部环境变量。恶意生成的代码或提示词注入可轻易通过 `os.environ` 窃取数据库密码、Redis 密码与大模型 API Key（$I(S_H; O_L) > 0$ 严重泄密）。
2. **缺乏 OS 资源物理限额，死循环与内存炸弹击垮宿主机**：
   当前外部伴侣脚本（如 `opti_server.py`）同步调用 `exec()` 且没有任何 CPU/内存资源配额限制。一旦遇到 `while True: pass` 或 `[0] * 10**9`，导致单核 CPU 100% 飙满或宿主机内存耗尽，触发 Linux OOM-Killer 杀死后端核心 Java 进程。
3. **衍生多进程缺乏递归树销毁，僵尸孤儿进程耗尽系统句柄**：
   传统 `Process.destroy()` 仅杀死根进程，子进程派生出的 Worker（如 `multiprocessing.Pool`）会脱离控制变成孤儿进程，并发调用下大量孤儿进程迅速耗尽 Linux PID（`pid_max`）与文件描述符，导致系统雪崩。
4. **Python 内置沙箱逃逸与反射漏洞**：
   尝试通过置空 `__builtins__` 来做沙箱已被证明完全不可行，攻击者可利用元类继承链 `().__class__.__bases__[0].__subclasses__()` 获取 `os._wrap_close` 或 `subprocess.Popen`，从而绕过限制并在宿主机执行任意命令。
5. **单向故障暴露，缺乏大模型自愈修复闭环**：
   当前系统缺乏面向代码执行 Traceback 的智能体自愈状态机。代码一旦遭遇 `SyntaxError`、`ImportError` 或 `IndexError` 即抛出异常终止，无法像高级工程师一样自主反思、修正并重新验证。

### 1.2 唯一待验证假设 (H-PHASE30-001)
> **核心假设**：在 Spring Boot 3 Java 21 后端架构下，构建一套免宿主机特权（Non-Root Safe）、极速冷启动（< 150ms）的“**受限安全子进程 + AST 预检 + 环境变量白名单清空 + OS 资源配额 (rlimit) + 进程树递归销毁 + 64KB I/O 截断 + 瞬态独立工作区**”轻量多租户代码沙箱，并结合“**DeepSeek-Coder 初次代码生成 + DeepSeek-R1 链式反思诊断自愈 (最多 3 轮)**”的状态机编排：
> 1. 在恶意代码攻击测试中实现 **100% 阻断敏感环境变量泄露与越权网络反弹 Shell**，定理 1.2 信息流无干扰互信息严格为 0；
> 2. 在遭遇无限死循环（`while True: pass`）与内存炸弹（`[0] * 10**9`）时，严格在 **$\le 5000\text{ms}$ 硬超时与 $\le 128\text{MB}$ 内存限额下平稳熔断并递归销毁进程树**，宿主机 Java 堆内存波动 $\le 5\text{MB}$ 且 0 孤儿进程残留；
> 3. 在常见数据分析与计算任务中，代码初次错误时的 **3 轮自愈修复成功率达到 $\ge 85\%$**（定理 2.2 收敛验证），沙箱冷启动耗时稳定在 **$\le 150\text{ms}$**，彻底规避笨重容器的秒级延迟与特权陷阱。

---

## 二、架构基线与设计原则 (Architecture Baseline & Guiding Principles)

1. **唯一生成模型**：DeepSeek API（代码生成 `deepseek-chat`，反思自愈 `deepseek-reasoner`，流式输出）。
2. **唯一向量模型**：阿里千问 (Qwen) Embedding（1536 维，单位超球面 $\mathbb{S}^{1535}$，内积余弦度量）。
3. **Java 21 隔离环境**：所有构建与测试命令统一显式前缀 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`。
4. **多租户安全隔离原则**：
   - 环境变量绝对清空（`env.clear()`），仅保留 `PATH`, `LANG`, `PYTHONUNBUFFERED` 极简白名单；
   - 每次执行生成绝对独立的唯一瞬态工作目录 `/tmp/qknow_sandbox_{UUID}`，执行完毕 100% 自动销毁清理；
   - 标准输出与错误输出硬锁定 64KB 上限截断，杜绝管道溢出死锁；
   - 超时与销毁时使用 `ProcessHandle.descendants()` 遍历整棵子孙进程树进行强行终止（`destroyForcibly`）。
5. **最多 3 轮反思自愈闭环**：基于定理 2.2 最优截断步数推导，超过 3 轮直接触发保护性熔断并返回诊断报告，防止无限消耗 Token。

---

## 三、核心组件与数据流架构设计 (Core Design & Data Flow)

```text
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                  用户数据分析 / 动态计算需求                                      │
└────────────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                                 │
                                                 ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│          第一层：代码生成器 (DeepSeek-Chat / DeepSeek-Coder)                                      │
│          - 注入任务背景、输入数据上下文与安全格式约束（```python ... ```）                       │
└────────────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                                 │ 候选源码 (Source Code)
                                                 ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│          第二层：AST 静态安全防御预检 (AstSecurityInspector)                                     │
│          - 符号白名单与危险模块黑名单检查（禁止 os, sys, subprocess, socket, urllib, shutil 等） │
│          - 拦截魔术属性与反射逃逸链（禁止 __subclasses__, __globals__, eval, exec 等）           │
└───────────────────────┬──────────────────────────────────────────────────┬───────────────────────┘
                        │ 安全预检违规 (Security Violation)               │ 预检通过 (Safe)
                        ▼                                                  ▼
        ┌───────────────────────────────┐          ┌───────────────────────────────────────────────┐
        │ 直接反馈违规信息并触发反思重生│          │ 轻量隔离子进程沙箱 (LocalProcessSandboxImpl)  │
        └───────────────┬───────────────┘          │ - Ephemeral Tempfs 瞬态工作目录               │
                        │                          │ - 彻底清空父环境变量，仅保留极简白名单        │
                        │                          │ - 资源硬配额（内存<=128MB, CPU<=5s）          │
                        │                          │ - 5000ms 硬超时看门狗 + 递归杀死整棵进程树    │
                        │                          │ - 64KB 输出截断与管道非阻塞异步读取           │
                        │                          └───────────────────────┬───────────────────────┘
                        │                                                  │
                        │                                                  ▼ 结构化执行结果 (CodeExecutionResult)
                        │                          ┌───────────────────────────────────────────────┐
                        │                          │ 状态判定 (ExecutionStatus)                    │
                        │                          │ - SUCCESS: 提取 stdout / 图表产物交付用户     │
                        │                          │ - RUNTIME_ERROR / TIMEOUT: 触发自愈回路       │
                        │                          └───────────────────────┬───────────────────────┘
                        │                                                  │ 失败且重试 <= 3
                        ▼                                                  ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│          第三层：代码智能体自愈反思闭环 (SelfHealingCodeAgent)                                   │
│          - 提取富执行反馈（ExitCode, 错误类型, 出错行号, Traceback）                             │
│          - 调用 DeepSeek-Reasoner (R1) 进行链式思考与根因分析 (RCA)                              │
│          - 形成修复补丁，生成修正后代码并自增 retryCount，重新提交第二层验证                      │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 四、核心代码组件落地规划 (Component Specifications)

### 1. 契约模型与核心枚举 (qknow-ai 模块)
- 目录：`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/code/model/`
  - `ExecutionStatus.java`：执行状态枚举（`SUCCESS`, `SECURITY_VIOLATION`, `TIMEOUT`, `RUNTIME_ERROR`, `RESOURCE_EXCEEDED`, `INTERNAL_ERROR`）。
  - `CodeExecutionResult.java`：不可变记录类，封装退出码、标准输出、标准错误、耗时、内存消耗、重试轮次、生成文件列表与结果变量。
  - `CodeExecutionRequest.java`：入参 DTO，包含语言、代码内容、超时阈值、最大重试轮次与多租户隔离标识。

### 2. 沙箱统一抽象接口与安全检查器 (qknow-ai 模块)
- 目录：`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/code/`
  - `sandbox/CodeSandbox.java`：沙箱顶层抽象 SPI 接口。
  - `guard/AstSecurityInspector.java`：多语言 AST 静态安全分析器（针对 Python/SQL/JS/Bash 分别实施符号可达性与黑名单剪枝）。

### 3. 免特权轻量进程沙箱实现 (qknow-framework / qknow-module-kmc 模块)
- 目录：`backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/code/`
  - `LocalProcessSandboxImpl.java`：实现 `CodeSandbox` 接口。负责瞬态目录生成与清理、环境变量清空与注入、资源限制参数组装、独立子进程启动、双流 64KB 异步非阻塞截断、硬超时看门狗及 `ProcessHandle.descendants()` 递归进程树强杀。

### 4. 代码智能体与 DeepSeek-R1 自愈引擎
- 目录：`backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/code/`
  - `SelfHealingCodeAgent.java`：编排代码初次生成（`deepseek-chat`）、AST 预检、沙箱执行与 DeepSeek-R1 链式反思自愈状态机。最大重试次数锁定为 3 轮。

---

## 五、测试驱动开发 (TDD) 契约测试用例规范

在 `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase30CodeAgentSandboxContractTest.java` 中构建 10 项严密契约测试：

1. `contract01_astSecurityInspector_blocksMaliciousModuleImports`：静态 AST 拦截 `os`, `sys`, `subprocess`, `socket`, `shutil` 等敏感模块导入，耗时 $\le 10\text{ms}$，拦截率 100%。
2. `contract02_astSecurityInspector_blocksReflectionAndMagicEscapes`：拦截 `__subclasses__`, `__globals__`, `eval(`, `exec(`, `open(` 等面向对象魔术逃逸与动态反射调用。
3. `contract03_sandboxEnvironmentSanitization_preventsCredentialLeakage`：验证子进程环境变量已完全置空，执行试图读取宿主机环境变量的代码时，输出结果绝对不包含任何敏感 Key。
4. `contract04_sandboxResourceLimit_terminatesInfiniteLoopWithinTimeout`：死循环 `while True: pass` 在 5000ms 硬超时内被强制熔断并销毁，返回 `TIMEOUT` 状态，系统 0 挂死。
5. `contract05_sandboxMemoryLimit_suppressesMemoryBomb`：内存炸弹代码（申请超大连续内存）被严格限制，宿主机 Java 堆内存波动 $\le 5\text{MB}$，不影响主服务。
6. `contract06_processTreeDestruction_leavesZeroOrphanProcesses`：当子进程衍生出多级子进程时，沙箱终止能够递归杀死整棵进程树，宿主机 0 孤儿/僵尸进程残留。
7. `contract07_standardIoTruncation_preventsPipeDeadlockAt64Kb`：死循环疯狂打印海量输出的代码在达到 64KB 时被安全截断，防止阻塞操作系统管道缓冲区。
8. `contract08_ephemeralWorkspace_guaranteesZeroFileResidue`：每次执行生成的独立瞬态目录在执行完成后（无论成功还是异常）均被 100% 自动清理，零磁盘垃圾残留。
9. `contract09_selfHealingLoop_repairsSyntaxAndRuntimeErrorsWithDeepSeek`：模拟初次代码出现语法错误或运行时异常（如少导包、拼写错误），自愈引擎基于 Traceback 在 3 轮内完成修复并最终执行成功。
10. `contract10_endToEndPolyglotExecution_servesMultiLanguageSandbox`：端到端验证 Python（数据分析计算）、SQL（只读 SELECT 查询）以及 Bash（基础文件统计）的多语言沙箱安全调用。

---

## 六、实施边界与安全约束 (Implementation Boundary)

1. **绝对禁止修改的边界**：
   - 严禁修改 Java 21 虚拟机隔离环境；
   - 严禁在宿主机容器或开发机上以 root 身份赋予沙箱特权，严禁使用 Docker-in-Docker；
   - 严禁引入外部非验证依赖或 OpenAI SDK。
2. **停止与回滚条件**：
   - 若出现任何子进程逃逸读取到宿主机环境变量的情况，立即停止实施；
   - 若沙箱导致宿主机 Java 进程 OOM 或僵尸进程积累，立即终止并排查进程树清理逻辑。
