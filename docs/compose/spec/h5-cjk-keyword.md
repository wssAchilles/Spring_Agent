---
feature: h5-cjk-keyword
status: delivered
updated: 2026-09-10
branch: feat/h5-cjk-keyword
commits: 994652f..HEAD
---

# H5 CJK Keyword Path

## Report

**What was built** — `KeywordRetriever` 使用 `websearch_to_tsquery` 替代 `plainto_tsquery`；检索词始终附加 CJK 2–3 gram（不再仅在 jieba 失败时）。

**Verification** — `CjkKeywordTermsTest` 4/4 PASS。短切片 keyword 消融：Hit@10 仍 **0.70**（与 H2 相同）。`库管理` 在 `kmc_document_segment` 中 **0 行**（qrel 问题，未改 fixture）。

**Journey log**
1. 语料中不存在的 expected source 会把 Hit@10 钉在天花板下。
2. websearch + n-gram 改善检索构造，不保证错误 qrel 变对。

## [S1] Problem

中文 tsquery / 短语召回弱。

## [S2] Design

websearch_to_tsquery + 恒定 CJK bigram terms。

## [S3] Out of Scope

改 golden qrel、Tantivy 服务、H6/H7。

## Tasks

- [x] T1: SQL 与 terms 改造 — acceptance: 单测绿 (covers: S2)
