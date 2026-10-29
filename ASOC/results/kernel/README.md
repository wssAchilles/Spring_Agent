# Pareto 记忆排序内核实验

本目录保存 Java 21 内核编译、定向测试与 JaCoCo 覆盖率证据，不包含外部数据集批处理。

- 最近一次成功的非 JaCoCo 定向测试：`memory-tests-final-14-post-coverage-fix.log`；3 个测试类共 14 项，失败 0、错误 0、跳过 0。日志开头记录 OpenJDK 21.0.5 版本文本，但未记录 Surefire 测试 fork 的 `java.home`，不能单凭此前版本文本确证测试进程运行时。
- 非 JaCoCo 性能观测：`N=1000`、1024 维向量，预热后 9 次测试取中位数 2.537 ms（精确中位样本 2,536,833 ns）。该数值是单机 JUnit 内核微基准，不代表线上端到端延迟或性能提升。
- JaCoCo 运行：`pareto-coverage-post-coverage-fix.log`；结果汇总：`pareto-jacoco-report-post-coverage-fix.csv`。`ParetoMemoryRanker` 类指令、分支、行和方法覆盖率均为 100%。这不是整体项目覆盖率；插桩运行的 4.074 ms 计时不与非 JaCoCo 2.537 ms 混用。
- 编译记录：`core-compile-post-coverage-fix.log`，Java 21 编译成功。
- `memory-tests-final-14-rerun.log` 是较早的 04:28 运行，报告 2.505 ms；其后 ranker 源码于 05:59 更新。当前稿件仅引用源码更新后的 06:07 运行（2.537 ms）及 06:15 生成的 JaCoCo 报告，不将较早计时与当前源码快照混用。
- `memory-tests-final.log` 与 `memory-tests.log` 为较早历史运行，不作为本稿最终测试或计时依据。
- MSC/LongMemEval 未进入本次内核测试；缺少生产摘要到 session 的冻结映射，因此不报告检索质量、统计显著性或遗忘收益。
- `LongTermMemory.recall` 目前未在向量查询阶段按 `userId` 过滤；租户隔离需要独立修复和验证，不能据此声称生产安全。
- `memory-tests-final-14-integration-fix.log` 是 2026-10-29 09:08 的诊断重跑，不是结果运行：完整 testCompile 在 Phase151/Phase152 基准测试源码处报 17 个编译错误，目标测试未执行。
- `LongTermMemoryEnhancedTest` 的 trade-off fixture 是 2 个候选、`topK=2`，只断言两者均返回；它不验证 adapter 截断、排序或图激活效果。通用 ranker 测试单独验证截断。

<!-- @evidence[[results/kernel/memory-tests-final-14-post-coverage-fix.log#Tests run: 14, Failures: 0, Errors: 0, Skipped: 0]] -->
<!-- @evidence[[results/kernel/memory-tests-final-14-post-coverage-fix.log#pareto_n1000_embedding1024_median_ms=2.537]] -->
<!-- @evidence[[results/kernel/pareto-coverage-post-coverage-fix.log#Tests run: 14, Failures: 0, Errors: 0, Skipped: 0]] -->
<!-- @evidence[[results/kernel/pareto-jacoco-report-post-coverage-fix.csv#ParetoMemoryRanker]] -->
<!-- @evidence[[results/kernel/core-compile-post-coverage-fix.log#BUILD SUCCESS]] -->
