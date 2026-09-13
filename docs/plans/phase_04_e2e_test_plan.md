# Phase 04 架构级全链路 E2E 联合测试白皮书

> **测试目标**：验证在 Spring Boot (控制面/认知面) 与 Vue 3 (前端画布/聊天室) 联合运行下，Phase 04-A (级联路由与 SSE 挂起澄清) 及 Phase 04-B (Gurobi 运筹优化智能体) 是否能在真实的端到端网络通信中按预期流转。

## 🧪 测试环境准备

1. **环境依赖**：
   - 确保 Mac 物理机的 PostgreSQL (带着 PgVector) 与 Redis 已在运行。
   - `.env` 文件已配置 `WLSACCESSID` 和 `WLSSECRET` (供 Gurobi 使用)。
   - DeepSeek 的 API Key 已正确配置。
2. **启动全栈服务**：
   ```bash
   bash scripts/start.sh
   ```
   *需确认后台出现 4 个服务的稳定进程：Vite(前端)、QKnow-Server(主后端)、Hermes(认知后端)、OptiServer(Python运筹侧车)。*

---

## 🟢 用例一：L1 极速语义路由测试 (零消耗直通)

**测试目的**：验证 Qwen Embedding 能否在不调用任何 DeepSeek 生成 API 的情况下，以超低延迟（< 50ms）拦截常规意图并返回前端。

1. **前端操作**：
   - 在 Vue 聊天对话框中输入极其口语化或意图清晰的文本，例如：“你好啊”、“请问你是谁”、“给我讲个笑话”。
2. **网络抓包 (F12 DevTools -> Network)**：
   - 监控 `/api/chat/stream` 接口的响应事件流 (EventStream)。
3. **后端日志验证 (`tail -f backend.log`)**：
   - **期望看到**：`L1 Route Hit: chitchat with score 0.95...`
   - **不该看到**：任何向 DeepSeek API 发起的耗时请求。
4. **前端 UI 期望**：
   - 几乎在点击回车的瞬间（< 100ms），前端即开始逐字渲染闲聊的 Markdown 回复。

---

## 🟡 用例二：L2 模糊意图拦截与前端状态挂起测试

**测试目的**：验证深层逻辑路由判定用户意图模糊时，后端能否截断大模型，向前端抛出控制帧，且前端 UI 能正确渲染“澄清表单”。

1. **前端操作**：
   - 在对话框输入具有明显多重歧义、或系统未涵盖边界的测试语句，例如：“帮我处理一下那个东西” 或 “我要查一下今天的数据（未说明什么数据）”。
2. **后端日志验证**：
   - **期望看到**：`Escalating to L2 DeepSeek Evaluator...` 
   - **期望看到**：`L2 Ambiguous Intent detected. Confidence: 0.3. Suspending graph.`
   - **期望看到**：`State Graph Suspended! Ambiguous intent detected.`
3. **网络通信断言 (SSE 控制帧)**：
   - 在浏览器的 EventStream 中，期望**最后一条消息**不是 `{"type": "content", "data": "..."}`，而必须是特殊的控制块：
     ```json
     {
       "type": "clarification_required",
       "thread_id": "<UUID>",
       "context": {
         "reason": "意图不明确，请说明您要查的是销售数据还是人事数据？",
         "options": ["销售数据", "人事数据"]
       }
     }
     ```
4. **前端 UI 期望**：
   - 前端必须中止打字机动画。
   - 在气泡下方渲染出两组按钮（“销售数据”、“人事数据”）供用户点击。
   - 点击后，前端能够携带 `thread_id` 恢复请求。

---

## 🔴 用例三：OptiAgent 运筹求解引擎 (DeepSeek + Gurobi) 全链路压测

**测试目的**：验证深层自然语言到混合整数规划（MILP）的端到端翻译执行，验证 Python 侧车的沙盒与 WLS 授权管理。

1. **前提配置**：
   - 确保在系统的 Agent 编排界面中，已为当前对话的 Agent 挂载了 `GurobiOptimizerTool`。
2. **前端操作**：
   - 抛出一个典型的运筹学题目：“我手上有 10 万预算，需要向 A、B 两个门店配送物资。A 门店最少需要 200 件，B 门店最少 150 件。卡车发往 A 门店每件成本 15 元，发往 B 门店每件成本 22 元。请用 Gurobi 帮我算出在满足最低需求下的绝对最小总运费，并告诉我各送多少件。”
3. **后端链路溯源**：
   - **Java 端**：DeepSeek 会识别到需要复杂计算，触发 Tool Calling。
   - **Python 端 (`opti_server.py` 日志)**：
     - 接收到 DeepSeek 编写的 `gurobipy` 代码。
     - 期望看到 Gurobi 内部求解日志，并返回 JSON：`{"status": "OPTIMAL", "obj": 6300, ...}`
4. **前端 UI 期望**：
   - 前端在经过约 3-5 秒的 Tool Calling 动画后，准确流式输出结论：“经过 Gurobi 计算，最小总运费为 6300 元。建议向 A 门店配送 200 件，向 B 门店配送 150 件。” （此结果必须是绝对数学准确的，不能有任何幻觉）。

---

## ☠️ 用例四：Gurobi 约束冲突下的反事实推理 (IIS 触发)

**测试目的**：测试当用户给出的条件在数学上“无解”时，系统的异常恢复能力与反问智能。

1. **前端操作**：
   - 抛出绝命题：“我的预算只有 5000 元，但 A 门店必须送 500 件，B 门店必须送 100 件（单价还是15和22）。帮我算怎么送。”（*注：这在数学上至少需要 9700 元，显然 5000 预算是无解的*）。
2. **后端链路溯源**：
   - **Python 端**：Gurobi 引擎返回 `status == GRB.INFEASIBLE`，并自动计算 IIS（不可行子系统）。
   - **Java/DeepSeek 端**：捕获到错误结果，LLM 开始分析冲突原因。
3. **前端 UI 期望**：
   - 系统绝不能报错崩溃。
   - 系统应该优雅回答：“长官，抱歉，根据 Gurobi 求解器的精密计算，您的要求在数学上无解。因为最低的运送需求（500件*15 + 100件*22 = 9700元）已经远远超出了您的 5000 元总预算。您是否考虑增加预算或降低门店的保底需求？”

---

## 📋 测试验收标准 (Definition of Done)

只有当以上四大用例在真实的 Chrome 浏览器操作下：
- **0 崩溃**：无 500 内部服务错误。
- **0 幻觉**：Gurobi 数学计算百分百准确，不带 LLM 脑补。
- **状态流转顺畅**：SSE 挂起与恢复前端无死锁。
即代表 Phase 04 架构级更新已可进行生产级发版（Production Ready）。
