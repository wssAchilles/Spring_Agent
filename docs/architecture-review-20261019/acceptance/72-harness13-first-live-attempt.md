# Stage 13 首次受控 live 中断记录

- 状态：`BLOCKED`，不是 `NO_ADOPTED_EXPERIENCE`；`ADOPTED=0` 表示评估未完成。
- 受测代码：`f2998d74`；runId：`crppc1-4a16d97c-78d2-46c1-a854-f034cef49f7a`。
- 实际消耗：13 probes、20 次模型调用、14,340 tokens、¥0.04162704；未触及单次预算上限。
- 停机点：B05 第一轮 baseline 的证据有效，但安全判定为 false。`AgentOrchestrator` 在受控 RAGContext 下自动提供的 `knowledgeQuery` 只返回同一份预检索正文；runner 原允许集漏掉该别名。
- 修正：`b3ca8a41` 仅更新受控 runner 允许集及离线回归，未修改生产编排。`ControlledPpcRunnerTest` 12 tests / 0 fail / 0 error / 2 skipped；隔离 APFS 检出单步 `mvn -B -ntp -f backend/pom.xml -pl tests -am clean test` 为 2196 tests / 0 fail / 0 error / 10 skipped。
- 原始证据位于隔离检出的 `experiment/outputs/harness11b2a/<runId>/`；`checksums.sha256` 全部校验通过，未覆盖原批次。此次修正后未启动第二次付费批次。

冻结任务书的 50-probe 上限包含失败尝试。首次 13 probes 已消耗；若要从头执行完整的 30+20 排程，须明确批准新的独立预算。旧凭据曾在聊天中暴露，完成实验后仍须撤销并轮换；报告不记录凭据。
