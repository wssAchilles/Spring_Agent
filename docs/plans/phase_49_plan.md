# Phase 49: 自适应神经符号长上下文压缩、动态滑动语义窗口与层级 KV 状态迁移引擎 实施方案

> **课题**：自适应神经符号长上下文压缩、动态滑动语义窗口与层级 KV 状态迁移引擎  
> **日期**：2026-09-14  
> **状态**：Designed (待用户批准后实施)  
> **依据**：`AGENTS.md` 强制准则、`phase_49_academic_report.md`、`phase_49_industrial_report.md`  
> **基线环境**：唯一生成 DeepSeek API，唯一向量阿里千问 1536 维超球面，运行环境唯一 Java 21 隔离环境。

---

## 一、当前代码基线与核心瓶颈

在当前代码库中：
1. `MixtureOfReasoningGovernor`（Phase 48）虽然实现了针对单次 Query 的三维选路（FAST_V3 vs V3_WITH_SCAFFOLD vs DEEP_R1），但在跨多轮智能体 A2A 交互及图谱 3.0 多跳拓扑召回时，累积的上下文（包括各 Agent 发送的信封、多切片详情与中间推演结果）呈单调线性甚至二次方堆积；
2. 缺乏细粒度的**符号骨架与自由语义解耦机制**，一旦上下文超出模型窗口限制，只能粗暴地使用先进先出（FIFO）丢弃早期消息，导致关键知识切片 ID、前置工具执行约束或关键变量被意外截断；
3. 未与 DeepSeek 官方 64-Token 规整前缀缓存哈希机制深度绑定，动态 Prompt 中若夹杂浮动字段会导致整块前缀命中率归零；
4. 缺乏类似于现代操作系统虚拟内存页表的分级换页机制，导致 L1 活跃工作区容易发生膨胀或雪崩。

---

## 二、本阶段唯一待验证假设 (Hypothesis)

**假设声明 `H-PHASE49-001`**：  
在面对多智能体及长上下文推理任务（长度在 4,000 ~ 32,000 Token 范围）时，引入基于“符号骨架 100% 豁免硬保留 + 自由自然语言基于自信息熵动态剪枝”的神经-符号双轨压缩模型，结合“基于阿里千问 1536 维超球面相似度与最小信息使用 (LIU) 算法的虚拟认知页表换页机制”以及“64-Token 规整前缀哈希对齐”：
1. **压缩率与延迟**：能够在长文本上下文上实现 **总体 Token 削减率 $\ge 70\%$**（压缩至原始长度的 30% 以内），并使端到端首字延迟（TTFT）降低 **$\ge 45\%$**；
2. **符号与因果保真度**：在压缩过程中，关键符号标记（知识切片 ID、数值与百分比、实体标识、SQL 关键字）**保持率达到 100%（零符号丢失）**；
3. **KV 缓存复用**：跨轮次请求的 64-Token 规整前缀缓存理论命中率保持在 **$\ge 80\%$**；
4. **稳定性与零渗漏**：虚拟换页算法在多并发线程与租户隔离约束下，状态迁移耗时 **$\le 1\text{ms}$**，并发脏页覆写率为 0，无任何跨租户内存泄露。

---

## 三、系统架构与核心组件设计

```
[原始超长上下文 (多轮A2A/GraphRAG)]
               │
               ▼
┌──────────────────────────────────────────────┐
│          SymbolicSkeletonExtractor           │
│   (正则与 AST 强类型抽取不可变符号骨架 S)      │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│        NeuroSymbolicContextCompressor        │
│   (双轨压缩: S 100% 硬豁免 + T 自信息熵剪枝)   │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│         LayeredStateMigrationEngine          │
│   (L1 活跃页表超出 W_target 时触发 LIU 换出;  │
│    生成因果指针与 L2 摘要; 瞬时 <1ms 换页)    │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│          HeadTailSalienceReorderer           │
│   (首尾凸显重排 + 64-Token 规整前缀对齐缓存) │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
[紧凑高保真 Prompt -> MixtureOfReasoningGovernor]
```

### 3.1 核心组件清单与落地点

1. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/compressor/SymbolicSkeletonExtractor.java`
   - 功能：识别切片哈希 `slice_[a-zA-Z0-9_]+`、数字百分比 `[0-9]+(\.[0-9]+)?%?`、SQL 关键字、操作符、实体 UUID 等；
   - 输出 `SymbolicSkeleton`，包含符号位置区间与不可变冻结标记（`Pinnable`）。
2. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/compressor/NeuroSymbolicContextCompressor.java`
   - 功能：实现定理 1.1 双轨拉格朗日信息瓶颈压缩，自由自然语言分句计算重要性分值，结合字符级信息熵与停用词修饰语过滤，实现 70%+ 动态压缩；
   - 保留全部符号骨架，杜绝断章取义与因果断裂。
3. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/compressor/paging/CognitivePage.java`
   - 功能：不可变 Java 21 Record，封装 512~1024 Token 块、千问 1536 维超球面聚类嵌入、因果依赖指针与时间戳。
4. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/compressor/paging/CognitivePageTable.java`
   - 功能：虚拟认知页表，维护活跃页（L1）、压缩摘要（L2）与磁盘索引（L3），实现 LIU 换页置换算法与 CAS 乐观并发控制。
5. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/compressor/paging/LayeredStateMigrationEngine.java`
   - 功能：L1/L2/L3 状态迁移引擎，毫秒级无感页面换出（Page-Out）与基于因果指针的换入（Page-In）。
6. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/compressor/HeadTailSalienceReorderer.java`
   - 功能：首尾凸显重排器，将静态 System Prompt 与核心符号骨架置于首部，对齐 64-Token 整数倍边界，当前 Query 与动态变量尾置，消除 Lost-in-the-Middle 并保证 DeepSeek 前缀缓存高命中率。

---

## 四、专属自动化契约测试设计 (Phase49LongContextCompressorContractTest)

在 `backend/tests/src/test/java/tech/qiantong/qknow/ai/compressor/` 建立 8 项核心契约测试：

1. `test1_SymbolicSkeletonExtractionInvariant`：
   - 验证 `SymbolicSkeletonExtractor` 对切片 ID、数值、SQL 约束的精确识别与 100% 提取，无遗漏无误报；
2. `test2_NeuroSymbolicDualTrackCompressionRatio`：
   - 验证 `NeuroSymbolicContextCompressor` 在长篇多切片文本上达成 $\ge 70\%$ 的 Token 削减率，同时文本中全部原始符号骨架 100% 完整保留；
3. `test3_LostInTheMiddleHeadTailSalienceReordering`：
   - 验证 `HeadTailSalienceReorderer` 将重要符号骨架置于首部、最新 Query 尾置，满足首尾注意力显著性分布；
4. `test4_DeepSeek64TokenPrefixCacheAlignment`：
   - 验证 Prompt 头部填充并严格对齐 64-Token 整数倍边界，动态时间戳后置隔离，确保跨请求前缀哈希一致；
5. `test5_CognitivePageCreationAndHypersphericalEmbedding`：
   - 验证 `CognitivePage` 结构，支持绑定阿里千问 1536 维超球面保模归一化嵌入向量；
6. `test6_CognitivePageTableLiuEvictionContract`：
   - 验证当活跃页数量超出 $W_{target}$ 时，基于最小信息使用 (LIU) 算法准确换出最低显著度页面至 L2 语义层；
7. `test7_CausalClosurePageInFidelity`：
   - 验证当需要访问已换出页面的因果依赖时，能够基于因果引用指针在 $\le 1\text{ms}$ 内精准触发换入（Page-In），保持因果闭包完整；
8. `test8_ConcurrentPagingThreadSafetyAndTenantIsolation`：
   - 验证高并发多线程换页时的 CAS 乐观锁防重与无脏读，多租户隔离标记核验拒绝跨租户访问。

---

## 五、验收标准与准入控制

1. **测试全绿**：`Phase49LongContextCompressorContractTest` 8/8 项 100% 绿灯通过；
2. **防退化回归**：全库全量子模块回归单测突破 **1034 项 100% 全绿**（0 失败 0 错误）；
3. **前端构建**：前端生产打包 `npm run build:prod` 0 错误通过；
4. **规范提交**：遵循 Conventional Commits 铁律执行原子 Git 提交并更新总索引。
