# Stage 11B-1 Canonical 构建环境契约

- 批准范围：仅 11B-1 离线机制实验；11B-2 真实模型与付费 PPC 不在此授权内。
- 证据基线：[63-harness11a-implementation.md](63-harness11a-implementation.md) 与 [64-harness11a-build-stability.md](64-harness11a-build-stability.md)。
- 根因状态：`UNPROVEN`。本环境是工作区状态隔离措施，**不是**历史 proto 编译跳过或 `target` 删除失败的因果修复；原工作区间歇故障仍可能重现。

## 固定条件

1. 从待验证的同一 Git HEAD 创建**全新、本地 APFS** 检出；不复用旧 `target/`，不使用 iCloud/OneDrive/外置卷或符号链接工作区。
2. 固定 SDKMAN Temurin Java 21.0.5：`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`；Maven 3.9.16。项目 proto POM 固定 protoc 3.25.1；系统 `protoc --version` 的 36.2 不是此构建使用的编译器。
3. 同一时间只有一个 Maven 构建；不使用 `-T`、Maven daemon 或并行 CI job 共用检出。不手动预生成 proto，不以 `clean && test`、预构建或跳过模块替代单步验证。
4. 验证前确认 `git rev-parse HEAD` 与预期一致、`git status --short` 为空；测试结果需记录 Reactor 顺序、执行/失败/跳过数和完整日志。

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl tests -am clean test
```

## 失败停止与保全

只要出现 proto 生成失败、`target` 删除失败或单步 `clean test` 失败，立即停止 11B-1 候选实验，**不把故障自动归因于 11B 代码**，也不继续运行下一候选。保全当次完整日志及退出码，并在失败现场记录：

- proto `target/generated-sources` Java 文件数与失败目标文件是否存在；`target/classes` class 数、目录权限/ACL/flags；
- Compiler Plugin 的 `target/maven-status/.../inputFiles.lst`、core 编译 classpath 和 Reactor 模块顺序；
- `ps`、`jps`、`lsof +D`；macOS 如可用，采集只读 `fs_usage` 窗口以辨别文件占用/删除时序；
- Java/Maven/protoc 版本、磁盘空间、文件系统类型、Git HEAD/工作区状态及是否有其他构建进程。

历史故障与本地重复通过的证据同时保留，不以一次或三次成功抹去失败事实。只有取得可复现的因果证据并完成修复后，才可将构建根因从 `UNPROVEN` 改为 `CONFIRMED`。本文件不修改 CI，也不授权生产接入、外网模型调用或 11B-2。
