# P0-15 最终验收报告

> 对象：当前 git 工作区 P0-15 实现（基线 `7b396909`，未提交）
> 依据：`13-p015-rework-report.md`、`14-p015-rework-acceptance.md`、`15-p015-real-smoke.md`
> 日期：2026-10-24
> 结论：**PASS（功能与生产接线验收通过；质量收益仍待定量实验）**

## 1. 独立复核结果

### 1.1 连续两次 clean 全量测试

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -pl tests -am clean test
```

| 轮次 | 结果 |
|---|---|
| 第 1 次 | `BUILD SUCCESS`；`qknow-tests 1919 / 0 fail / 0 error / 8 skipped`；`qknow-hermes-core 99 / 0 fail` |
| 第 2 次 | `BUILD SUCCESS`；`qknow-tests 1919 / 0 fail / 0 error / 8 skipped`；`qknow-hermes-core 99 / 0 fail` |

- `git diff --check`：无输出
- 复核时端口 `8098` / `9098` 无监听；临时进程已释放
- `kb_agent_config id=5` 的 `parameters` 已恢复为 `{}`

### 1.2 真实链路 Smoke（引用 15 报告）

| 用例 | 结果 | 关键指标 |
|---|---|---|
| SWARM + 真实工具/RAG | PASS | `effectiveMode=SWARM`，fallback=false，13499ms，6 次 LLM，11197/2478 tokens，真实 cache hit 4992，0.032434 元 |
| SWARM 120ms 超时回退 | PASS | `effectiveMode=SINGLE`，fallback=true，SUPERVISOR_TIMEOUT，123ms 内中断，无重复回答 |
| DEBATE 1 轮三角色 | PASS | `effectiveMode=DEBATE`，fallback=false，9364ms，3 次 LLM，6916/1106 tokens，0.020673 元 |

数据库已恢复、临时进程/端口已清理；Smoke 1 日志包含真实 `knowledgeQuery` / `knowledgeBase8` 工具调用与结果。

## 2. P0 验收结论

| 项 | 状态 |
|---|---|
| P0-1 Worker 真实 ReactAgent + 工具 | PASS |
| P0-2 有界 LRU+TTL，无全文驻留 | PASS |
| P0-3 官方价格预算 + 运行期 Usage 熔断 | PASS |
| P0-4 超时取消/阻断后续调用 | PASS |
| P0-5 默认关闭 + Spring 配置 | PASS |
| 重复测试/Orchestrator 耦合 | PASS（已解耦、测试唯一） |
| 默认 SINGLE 零回归 | PASS |

## 3. 仍存在的非阻塞限制（P2）

1. **多智能体仍为伪流式**：SWARM/DEBATE 聚合完成后通过 `emitSingleAnswer` 一次性下发最终答案；只有工具调用状态帧实时推送。首字节时间高于 SINGLE。
2. **底层 HTTP Socket 中断是 best-effort**：取消令牌能阻止后续阶段调用，但正在阻塞的底层 HTTP 连接断开速度依赖客户端实现。
3. **Worker 角色能力仍由名称匹配推导**：`resolveWorkerCapabilities` 依赖 RAG/Analysis/Synthesis 命名，重命名可能改变竞标能力。
4. **质量收益尚未定量验证**：3 条 smoke 只证明功能与成本门禁，不证明 SWARM/DEBATE 在复杂问题上优于 SINGLE。P0-15 假设中的“质量收益”仍需下一阶段实验。
5. **未做生产灰度/长稳压测**：当前仅单机 smoke；并发、限流、429、长稳内存尚未验证。

## 4. 最终裁定

- **P0-15 生产接线与硬化：PASS**
- **P0-15 质量收益假设：尚未验证（下一阶段）**
- 建议：**先 commit 当前实现，再进行 SINGLE vs SWARM vs DEBATE 定量实验；默认开关保持 false。**

## 5. 下一步计划

### 阶段 1：Commit（需用户确认）
- 按中文 Conventional Commits 提交：
  - `feat(agent): 接入多智能体编排 SWARM/DEBATE 生产链路`
  - 包含 P0-15 实现、测试、配置、验收文档；不包含临时 smoke 数据。
- 提交前再次 `git diff --check`、确认无 DB/端口残留。

### 阶段 2：定量质量实验（推荐下一个任务）
- 固定 15 条多步问题（来自 P0-16 真实语料/证据），SINGLE、SWARM、DEBATE 随机顺序、3 seeds。
- 指标：质量（DeepSeek judge + 20% 人工盲审）、p95 延迟、token/成本、fallback 率、工具调用数。
- 预注册判据：
  - SWARM 质量 ≥ SINGLE +10%（绝对 0.5/5）；
  - 单题退化 ≤1 分；p95 ≤3×；成本 ≤3× 且 ≤0.05 元；fallback <5%；
  - DEBATE 仅在 SWARM 基础上再 +5% 且成本 ≤6× 时保留。
- 输出 `docs/architecture-review-20261019/p015/17-p015-quality-eval.md`。

### 阶段 3：生产灰度（独立授权）
- 单 Bot、单租户、小流量开启 `HERMES_ORCHESTRATION_ENABLED=true` + `SWARM`；
- 观察 p95、成本、fallback、429、内存；
- 通过后再评估 DEBATE 灰度；全量默认切换需最高级授权。

### 阶段 4：可选增强
- 子 Agent token-by-token 流式；
- 角色→能力显式映射；
- 长稳压测与 429 退避。
