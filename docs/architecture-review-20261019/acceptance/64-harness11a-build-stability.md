# Hermes Harness Stage 11A 原工作区构建稳定性诊断

- 分支和冻结 HEAD：`feat/hermes-harness-stage10` / `b3c4db2a053373800b29f04174234460a912f69c`
- 结论：历史两类失败有完整日志，本轮原工作区**未复现**；根因仍为 `UNPROVEN`。未修改 POM、源码、配置或测试，**不宣称故障已修复**。
- 11B：继续 `BLOCKED`；本报告不构成 11B 实施授权。

## 现场快照

| 项目 | 观察 |
|---|---|
| 工作区 | `/Users/achilles/Documents/许子祺/Agent`；开始时 `git status --short` 为空 |
| 运行时 | SDKMAN Temurin Java 21.0.5；`/opt/homebrew/bin/mvn`，Maven 3.9.16；Compiler Plugin 3.14.1 |
| protoc | 系统 `protoc --version` 为 36.2；POM 指定 Maven 下载的 protoc 3.25.1，不能混为同一个编译器 |
| 存储 | 本地 APFS `/System/Volumes/Data`，可用约 226 GiB；工作区不是符号链接或 `CloudStorage` 路径。系统有 Spotlight/iCloud 后台进程，但没有证据表明它们访问了本工作区目标文件 |
| 进程与权限 | 运行前无其他 Maven、Java/Surefire 或 IDE 构建进程；`jps` 只见自身。目标目录为当前用户可写、无 immutable flag；`lsof +D` 未见打开句柄。这是事后/运行前快照，不能排除历史失败瞬间的占用 |
| 本地状态 | `git status --ignored` 显示 proto/core 两个 ignored `target/`；未发现仓库 `.mvn` 或 `~/.m2/settings.xml`。原工作区和同 HEAD 干净检出使用同一主机、JDK、Maven 与本地 Maven 仓库 |

proto POM 在 `generate-sources` 绑定 `protobuf:compile` 和 `compile-custom`；插件 0.6.1 的 descriptor 显示 `clearOutputDirectory=true`、`checkStaleness=false` 默认值。生成文件进入 Compiler Plugin 的 `inputFiles.lst`，本轮编译器每次报告 47 个源文件；没有额外 build-helper 配置。`qknow-hermes-core` 直接依赖 `qknow-hermes-proto`；完整 Reactor 中 proto 为第 34/43，core 为第 39/43，tests 为第 43/43。父 POM 使用 Java 21 和 Compiler Plugin 3.14.1，未启用 Maven 并行参数。

## 既有失败与分类

完整历史日志在 [Stage 11A 证据](evidence/harness11a)，此前报告见 [63-harness11a-implementation.md](63-harness11a-implementation.md)。

1. 原工作区历史第 1 次：proto 插件报告处理 1 个 `.proto`，Compiler Plugin 却报告 `Nothing to compile`；core 随后因缺少 `tech.qiantong.qknow.hermes.proto` 类失败。失败当刻未保全生成 Java/class 数、classpath 和进程快照，故**不能确认**是生成竞态、source-root 注册、编译增量判断还是其他进程改动了 target。
2. 原工作区历史第 3 次：proto 正常编译 47 个 Java 文件，core 的 Maven clean 无法删除 `target/classes/tech/qiantong/qknow/hermes/tool`。事后目录仍存在，`lsof` 没有发现句柄；无法确认失败瞬间文件锁、同步/索引器、权限或 APFS 删除时序。

两次是不同失败症状，不能合并归因为单一“环境问题”；也不能因本轮通过就将任一归因为已修复。Maven 配置、生成顺序和相同本地仓库在多次成功构建中可用，反驳了这些配置**必然**失败的解释，但不能排除间歇性缺陷。

## 本轮隔离与验证

原工作区冻结同一 HEAD，三次之间未改代码、POM、配置或测试，所有 Maven 串行执行。准入命令均为单步：

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl tests -am clean test
```

| 轮次 | 退出码 | 结果 | Proto |
|---|---:|---|---|
| 原工作区 1 | 0 | `BUILD SUCCESS`；2168 tests，0 failures/errors，8 skipped | 47 个 Java 源文件编译 |
| 原工作区 2 | 0 | 同上 | 47 个 Java 源文件编译 |
| 原工作区 3 | 0 | 同上 | 47 个 Java 源文件编译；末次生成 47 个 Java 文件、108 个 class |
| proto 单模块 `clean`、再 `compile` | 0 / 0 | 均通过；仅作为诊断，不替代单步门禁 | 47 个 Java 文件、108 个 class |
| core 依赖链 `-pl qknow-hermes/qknow-hermes-core -am clean test` | 0 | `BUILD SUCCESS` | proto 先于 core 完成 |
| 隔离后原工作区完整单步 | 0 | `BUILD SUCCESS`；2168 tests，0 failures/errors，8 skipped | 47 个 Java 源文件编译 |
| 同一 HEAD 临时干净检出完整单步 | 0 | `BUILD SUCCESS`；2168 tests，0 failures/errors，8 skipped | 47 个 Java 文件、108 个 class；ignored live 输出不存在 |

总数来自 1 + 4 + 184 + 35 + 1 + 1943 = 2168；最后的 1943 项中跳过 8 项。`HarnessExperienceStage10Test` 5 项及 `ExperienceLibraryContractTest` 4 项在完整测试中均通过。11A 源码、夹具与断言未改，既有测试继续覆盖 7 条 `UNSCOPED_REPLAY`、`ADOPTED=0`、`INVALID_FIXTURE=0` 及正向召回为空；这不是新的真实业务域导入证明。默认构建排除 `live` 测试组，本轮未运行付费 API。

本轮八份完整日志保存于 [evidence/harness11a-stability](evidence/harness11a-stability)，在该目录运行 `shasum -a 256 -c logs.sha256` 可校验。未删除或重写 63 报告中的历史失败日志。

## 裁定与环境边界

根因 `UNPROVEN`；无可证实的最小 POM/环境修复，因此**修复文件：无**。本轮原工作区三连通过只证明当前状态下重复单步可运行，不能推断历史失败已消除。作为待批准的 canonical build environment 候选，可使用同 HEAD 的全新本地 APFS 检出、固定 Java 21.0.5/Maven 3.9.16、单一 Maven 进程，直接执行同一单步命令；该候选已在本机成功一次，但还不是对历史两种故障的因果修复，也未改 CI。

若故障再次出现，应在失败当刻保全 proto 生成文件/class、Compiler Plugin 的 `inputFiles.lst`、core classpath、目标目录权限/ACL/flag、`lsof`/`ps`/`jps` 和只读 `fs_usage` 窗口，再分别验证增量编译与删除失败。未取得该证据前不得把 `UNPROVEN` 升级为 `CONFIRMED`。11B 维持 `BLOCKED`，须另经 11A 报告复核和单独授权。
