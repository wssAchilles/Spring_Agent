# Phase 06 — 中文 IR 与检索底座原生加速（算法交付方案）

> **状态**：**Delivered**  
> **日期**：2026-09-13  
> **门禁**：AGENTS.md；Robertson BM25 (VERIFIED)；jieba-rs v0.7.1 (VERIFIED)  
> **验证**：ChineseIrEnhancementContractTest (4/4 passed)

## A. 失败机制

`KeywordRetriever` 原有分词依赖硬编码静态集合与简单滑动窗口，导致技术专有复合词被机械切碎，口语化噪音无法有效清洗，且同义词扩展无数量上限约束可能导致 SQL 膨胀。

## B. Research Ledger

- Robertson & Zaragoza 2009：BM25 词边界与 TF 饱和度理论。
- messense/jieba-rs v0.7.1：Rust 高性能零拷贝分词与用户字典能力。
- pgvector 0.7.x：HNSW 复合索引优化指南。
- Lucene SynonymGraphFilter：同义词受控扩展与布尔防漂移策略。

## C–E. 最小算法实现

1. **新建 `ChineseDictionaryService`**：
   - 提取并保护 30+ 核心 AI/RAG/图计算领域复合词。
   - 维护 50+ 双向同义词矩阵映射。
   - 清洗常见的对话修饰噪音与高频停用词。
   - 提供上限截断保护（单词最多扩展 3 项，总扩展词 $\le 15$）。
2. **重构 `KeywordRetriever`**：
   - 注入 `ChineseDictionaryService` 优先保留复合词语素。
   - 在同义词扩展和分词处理中接入领域词典。
   - 保留对旧版调用方式的兼容重载与无 JNI 环境下的平滑降级。

## F. 契约验收

| 项 | 值 |
|---|---|
| Baseline | 硬编码 17 项同义词，粗粒度分词 |
| Candidate | 领域增强词库 + 截断保护同义词矩阵 + JNI/Java 平滑降级 |
| 指标 | 单测 4/4 全绿：复合词完整保留、噪音 100% 过滤、同义词数量受控 |
| 禁止 | 引入重量级外置进程、破坏现有表结构 |

## Tasks

- [x] T1 新建 `ChineseDictionaryService` 并实现领域词识别与受控同义词扩展
- [x] T2 改造 `KeywordRetriever` 接入增强词典服务
- [x] T3 编写 `ChineseIrEnhancementContractTest` 验证分词与 JNI/Java 兼容性并通过全部测试
