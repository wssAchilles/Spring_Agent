# Hermes Harness Stage 11A 实施与验证报告

- 分支：`feat/hermes-harness-stage10`，验证基线 `90494325ce6c9f7c18a7f1dad9a5699548dbbcf7`
- 契约：[62-harness11-taskbook.md](62-harness11-taskbook.md) Revision 2；用户已另行授权 11A，11B 仍未授权
- 环境：Maven 3.9.16，Eclipse Adoptium Java 21.0.5，Maven Compiler Plugin 3.14.1，protoc 3.25.1
- 结论：11A 代码和可移植夹具已交付；独立干净检出连续三次单步构建通过。但原工作区三次中两次失败，历史及本次间歇故障根因均为 `UNPROVEN`，**不宣称单步 clean 问题已修复**。11A 全环境稳定性验收待复核；11B 继续 `BLOCKED`。

## 交付与隔离

1. `25aa4ce5` 提交 Stage 10 的 12 个主源码和 2 个离线测试文件；`90494325` 提交三类派生夹具、离线生成脚本及 importer/classpath 测试调整。未改 `main`、`AgentOrchestrator`、MCP、HITL 或生产在线读取路径。
2. 派生夹具位于 `backend/qknow-hermes/qknow-hermes-core/src/test/resources/harness9-replay/c44e7e20-5e33-49c4-bc57-105a8cc3c347/`，包含 15 个 probes、`ppc_summary.json`、`selected_candidate.json`、`test-checksums.sha256` 和 `provenance-map.json`。原始 live 目录未提交。
3. 原始 manifest SHA-256 为 `7cc4f4c71d7e675eac4f99d47f9d722c34a6ffa3c159bddd808ed73951a61288`；派生 manifest SHA-256 为 `aa8276739f96d48d7bce272fc7d0235b2054c856e505f99149c54065478b131c`。派生 17 个数据文件的 `shasum -c` 通过。映射只保留路径/哈希及原始到派生 voucher 指纹，不复制原始文本。
4. 测试只从 classpath 读取派生资源，不读取 Git 忽略的 `experiment/outputs/harness9-live`。独立检出中该目录不存在，离线模式全量测试仍通过。派生夹具一致性不等于原始证据来源真实性。

## 离线契约

- 两次定向执行均为 **9 tests，0 failures，0 errors，0 skipped**（`HarnessExperienceStage10Test` 5 项、`ExperienceLibraryContractTest` 4 项）。
- 导入结果为 7 条 `UNSCOPED_REPLAY`：5 条 `OBSERVED`、2 条 `REJECTED`；其中 HISTORY/COMPOSITE 两条为 `failureObserved=true`、`attributionStatus=UNRESOLVED`。`ADOPTED=0`、`INVALID_FIXTURE=0`；同域和业务域正向召回均为空。
- 测试覆盖单文件篡改、文件与 manifest 同时重算、summary/selected 语义不一致和脱敏/指纹映射。11A 测试只使用本地文件与内存对象，没有模型客户端或付费 API 调用；全量 Maven 离线运行通过，但未做操作系统级禁网监测，不据此宣称全仓测试绝无网络套接字。

## 单步 clean 记录

命令在同一 `HEAD`、零工作树改动下串行执行，未使用 proto 预构建或 `clean && test` 替代：

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl tests -am clean test
```

| 检出/轮次 | 退出码 | 结果 | Proto / 失败目标状态 |
|---|---:|---|---|
| 原工作区 1 | 1 | `qknow-hermes-core` 编译缺少 `tech.qiantong.qknow.hermes.proto` | protobuf 报处理 1 个 proto，compiler 报 `Nothing to compile`；当次生成文件快照未在日志保全，不能据此确定根因 |
| 原工作区 2 | 0 | `BUILD SUCCESS`；2168 tests，0 failure/error，8 skipped | proto 编译 47 个 Java 源文件 |
| 原工作区 3 | 1 | `qknow-hermes-core` 的 clean 无法删除 `target/classes/.../tool` | proto 已编译 47 个 Java 源文件；失败后目标目录存在，事后 `lsof` 未发现占用，仍不足以确定删除失败原因 |
| 独立检出离线预检 | 0 | `mvn -o ... clean test` 通过；2168 tests，0 failure/error，8 skipped | 不依赖原工作区 ignored 资产 |
| 独立检出单步 1 | 0 | `BUILD SUCCESS`；2168 tests，0 failure/error，8 skipped | proto 编译 47 个 Java 源文件 |
| 独立检出单步 2 | 0 | `BUILD SUCCESS`；2168 tests，0 failure/error，8 skipped | proto 编译 47 个 Java 源文件 |
| 独立检出单步 3 | 0 | `BUILD SUCCESS`；2168 tests，0 failure/error，8 skipped | proto 编译 47 个 Java 源文件；末次落盘 47 个生成 Java 文件和 108 个 class |

成功轮次的 Reactor 均为 43 个模块；`qknow-hermes-proto` 是第 34 个，`qknow-hermes-core` 是第 39 个，`qknow-tests` 是第 43 个。完整日志按轮次压缩保存于 [evidence/harness11a](evidence/harness11a)，哈希见 [logs.sha256](evidence/harness11a/logs.sha256)；在该目录执行 `shasum -a 256 -c logs.sha256` 可校验。原工作区失败记录不因独立检出通过而作废。

## 裁定与后续边界

Stage 10 的默认测试可移植性与 Stage 9 隔离回放语义已验证。干净检出在当前环境三连通过，只支持“该独立检出重复单步验证通过”；原工作区的 proto 编译跳过和目录删除失败均未归因，不能写成已修复。若后续要宣称构建故障已消除，须先取得可复现根因并在修复后重新冻结、连续验证。11B 的递归 PPC、经验感知 Proposer、余弦召回、负经验掩码、统计泛化承诺、生产接入和付费 API 均未实施，也未获授权。
