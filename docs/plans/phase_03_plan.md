# Phase 03 — 安全 Hardening（P2）

> **状态**：Designed → 实施中  
> **日期**：2026-09-11  
> **总计划**：`plans/RAG长期优化链路-v2.md` P2  
> **性质**：纯工程安全修复，**不改检索算法**

## 证据链（研读摘要）

| 项 | 现状 | 依据 |
|---|---|---|
| SSRF | 双份 `HttpRequestToolFunction` 无 URL/IP 校验、未禁重定向 | OWASP SSRF Cheat Sheet |
| ASYNC permitAll | `SecurityConfig` ASYNC 全放行 | Spring Security 6：ASYNC 应鉴权 |
| `/syncData/**` | 匿名可写系统数据 | 最小权限 |
| API Key 明文 | `apiKeyEncrypt` 已有但 L137/L165 注释掉 | 最小修复 |
| XSS | `html:true` + 裸 v-html + 无 DOMPurify | 存储型 XSS |

## Tasks

- [ ] T1 `UrlSafetyValidator`（common/security）+ 双 HttpRequestToolFunction 接入  
- [ ] T2 SecurityConfig：删 ASYNC permitAll；`/syncData` 收紧  
- [ ] T3 恢复 API Key 脱敏  
- [ ] T4 前端 `sanitizeMarkdown` util + 关键 v-html 消毒（不强制全站扫）  
- [ ] T5 单测  

## 验收

- SSRF 单测：私网/元数据/非 http 拒绝  
- 脱敏单测  
- Security 配置编译绿  

## 不采用

WAF、Vault 全家桶、重写 ToolFunction 为微服务。
