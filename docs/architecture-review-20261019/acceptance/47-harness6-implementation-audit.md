# Harness Stage 6 实施基线核对

- 核对分支：`feat/hermes-harness-stage6`；实施前 HEAD：`492f194d118b40483a12a007ef5b1ec6e6b7fad6`，工作区 clean。
- 冻结任务书：`45-harness6-taskbook.md` 版本 `2.0.0-FROZEN`。正文 SHA-256 为 `65f67a740eeb8620edf56f39a6894c39e23ca33d5d4acc41bc9e5d70df649321`；整文件 SHA-256 为 `a4b1a3a178ed1f8876c542ebeb424b67ce2ea9f2436cb6eb161db30c64e95a0c`，伴生 `.sha256` 校验通过。
- 实施前当前分支无 Stage 6 的 `src/main/java` 或 `src/test/java` 源码。`git log --all -- '*Harness6*' '*Stage6*'` 无可恢复提交；唯一 stash 属于 P1 验收证据。其他现存 worktree 未作为本分支源码来源，未移动或覆盖其文件。
- 处置：依照当前冻结任务书新建 10 个 `src/main/java` 核心类和一份确定性架构契约测试；未沿用旧版仅测试侧的 Stage 6 实施方向。
- 边界：不修改 `AgentOrchestrator`、MCP、HITL、SWARM、qrel 或已提交实验证据；不运行付费模型探针。

数学及存储解释：任务书给定的归一化算术均值是球面**外在质心**，`2(1-||mean||)` 是其平均平方弦距，不自动构成一般内在测地线 Fréchet 均值的上界。固定容量 Cold 活跃环与永久保留所有原件不能同时保持总内存有界；实现将溢出原件转入可恢复档案，档案容量未设上限。后续论文证明与持久化资源策略需分别处理这两个边界。
