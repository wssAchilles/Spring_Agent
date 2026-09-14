# Phase 48: 双核混合推理中枢 (MoR)、思考链认知缓存与自进化图谱 3.0 工程实施方案

> **状态**：**PROPOSED (Pending User Approval)**  
> **前置依赖**：Phase 47 (Delivered) + `phase_48_academic_report.md` (Delivered) + `phase_48_industrial_report.md` (Delivered)  
> **架构模型基线**：生成模型唯一 DeepSeek API (V3/R1)，向量模型唯一阿里千问 1536 维超球面，环境唯一 Java 21 隔离环境 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)。

---

## 一、唯一待验证核心假设 (Falsifiable Hypothesis)

> **H-PHASE48-001**：在保证用户生成体验与逻辑严密性的前提下，引入基于语义复杂度、RAG 置信度与切片冲突度三维评分函数的双核混合推理选路中枢（`MixtureOfReasoningGovernor`），结合零拷贝字符级流式思考状态机（`CoTStreamFsmParser`）与阿里千问 1536 维超球面复合键认知缓存（`CoTCognitiveCacheService`），能够在复杂查询中以 DeepSeek-V3 挂载 200~400 字决策脚手架的方式复用思考深度，使复杂查询推理端到端 P95 响应时延降低 $\ge 50\%$，API 纳元调用成本降低 $\ge 60\%$，且虚假事实引入率为 $0\%$（定理 1.1~1.3）。

---

## 二、架构领域设计与核心组件清单

### 2.1 模块与包结构 (`backend/qknow-framework/qknow-ai`)
新建子包：`tech.qiantong.qknow.ai.mor`
1. `MixtureOfReasoningGovernor.java`：双核混合推理中枢接口与实现；
   - 判定三维特征：$C_{\text{semantic}}(Q)$, $\text{Conf}_{\text{rag}}(Q)$, $\Delta_{\text{conflict}}(Q)$；
   - 判定分支：`FAST_V3`、`V3_WITH_SCAFFOLD`、`DEEP_R1`；
2. `ReasoningDecision.java`：强类型判定结果不可变 Record；
3. `CoTStreamFsmParser.java`：字符级有限状态机流式解析器（零拷贝拦截 `<think>...</think>` 并分流）；
4. `ScaffoldDistiller.java`：轻量因果决策树脚手架提炼器（压缩至 200~400 字）；
5. `cache/CoTCognitiveCacheService.java`：阿里千问 1536 维超球面与切片哈希复合键双层缓存器（Caffeine L1 + Redis L2 抽象）；
6. `cache/CognitiveScaffold.java`：缓存实体不可变 Record。

### 2.2 知识图谱 3.0 解耦设计 (`backend/qknow-module-kg`)
1. `tech.qiantong.qknow.module.kg.rag.GraphRagCoordinator.java`：图谱因果多跳与 Leiden 社区摘要统一检索门面；
2. `tech.qiantong.qknow.module.kg.event.DocumentSlicesIngestedListener.java`：异步监听切片入库事件，解耦长事务。

---

## 三、契约测试设计 (`Phase48MixtureOfReasoningContractTest.java`)

计划覆盖 8 项核心测试契约：
1. **Contract 1: MixtureOfReasoningGovernor 三维打分与 FAST_V3 选路契约**：低复杂度、高 RAG 置信度与无冲突查询准确命中 `FAST_V3`；
2. **Contract 2: MixtureOfReasoningGovernor 高冲突与歧义 DEEP_R1 选路契约**：命中时态冲突（`CONFLICTED`）或 CRAG AMBIGUOUS 时准确分流至 `DEEP_R1`；
3. **Contract 3: CoTStreamFsmParser 零拷贝字符级流式状态机拦截契约**：跨 Chunk 拆分标签无缝切分，思考流与正文流 100% 准确分离，零死锁零丢字；
4. **Contract 4: ScaffoldDistiller 决策树脚手架提取与因果压缩契约**：数千字 CoT 思考链准确提炼为 200~400 字结构化因果 Markdown，压缩比 $\ge 80\%$；
5. **Contract 5: CoTCognitiveCacheService 阿里千问 1536 维超球面复合键与 L1 读写契约**：超球面相似度 $\ge 0.92$ 且切片签名一致时准确命中，切片版本变更时自动失效；
6. **Contract 6: MixtureOfReasoningGovernor 认知缓存命中与 V3_WITH_SCAFFOLD 选路契约**：缓存存在时优先激活 `V3_WITH_SCAFFOLD`，零时延复用深度思考；
7. **Contract 7: GraphRagCoordinator 神经符号因果多跳与 Leiden 社区摘要契约**：解耦后门面调用返回强类型证据结构，支持四路召回融合；
8. **Contract 8: 双核中枢超时与 Fail-Open 优雅降级契约**：R1 超时或异常时自动透明回退至 V3 挂载基础提示，保障高可用。

---

## 四、验证与交付门禁

1. 编译 `qknow-ai` 与 `qknow-module-kg` 模块（Java 21）；
2. 专属契约测试 `Phase48MixtureOfReasoningContractTest` 8/8 100% 绿灯；
3. 全库全量防退化回归测试 `mvn test -pl tests`（目标突破 1026 项单测 100% 绿灯，0 失败 0 错误）；
4. 前端 Vite 生产构建 `npm run build:prod` 0 错误打包；
5. 更新 `docs/plans/00_master_index.md`，执行 Conventional Commits 规范 Git 原子提交。
