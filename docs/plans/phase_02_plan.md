# Phase 02 — 可观测性与 CI（P0+P1）

> **状态**：Designed → 实施中  
> **日期**：2026-09-11  
> **总计划**：`plans/RAG长期优化链路-v2.md` P0+P1  
> **依赖**：Phase 01（指标语义已定义）  
> **性质**：纯工程，**不改检索算法**

## 证据链（研读摘要）

| 来源 | 要点 |
|---|---|
| Spring Boot 3.5 Actuator/Micrometer | `starter-actuator` + `micrometer-registry-prometheus`；默认仅 health；自定义 SecurityFilterChain 需显式放行 |
| Petclinic `.github/workflows` | checkout@v4 + setup-java@v4 + cache maven + `mvn -B test` |
| 仓库只读 | 无 workflows；无 actuator；`RagRetrievalService` 已有 elapsedMs；LangFuse 与 Actuator 正交 |

## 设计

- 暴露：`health,info,prometheus,metrics`；**不**暴露 env/heapdump/shutdown  
- 指标：`rag.retrieve.duration`（outcome）、`hermes.grpc.call.duration`、JVM/HTTP 自动  
- CI：JDK17 `mvn -B -f backend/pom.xml -pl tests -am test`  
- Security：`/actuator/health` permitAll；prometheus 走默认鉴权  

## Tasks

- [ ] T1 pom + yml + SecurityConfig actuator  
- [ ] T2 RagRetrievalService Timer  
- [ ] T3 HermesGrpcClient Timer  
- [ ] T4 `.github/workflows/ci.yml`  
- [ ] T5 单测/编译验证  

## 验收

- 编译绿；Timer bean 存在单测  
- CI 文件语法正确  
- 不暴露敏感 actuator 端点  

## 不采用

OTel 全链路、Grafana as-code、JDK21 矩阵、live Neo4j CI。
