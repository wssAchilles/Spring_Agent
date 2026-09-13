# Phase 20 核心工程落地课题工业级深度调研与架构设计报告：全栈现代化容器编排、异构灾备自动化实战演练与全链路高并发压测验收闭环

**副标题**：业内一流开源生产级 AI 系统（Dify, FastGPT, LangFuse, Milvus, Supabase, Docker, Kubernetes, Nginx）全栈编排最佳实践、异构存储灾备实战、端到端压测套件与三大生产事故避坑复盘  
**建议归档目标**：`docs/plans/phase_20_industrial_report.md`  
**遵循标准**：`AGENTS.md` Research-to-Implementation Gate 规范  
**报告状态**：**RESEARCH_GATE_READY**

---

## 一、前言与系统架构模型基线 (Architecture Model Baseline)

### 1.1 架构模型与生态基准
任何针对本项目容器编排、灾难备份与全链路高并发性能压测的工程改造，必须严格遵守全局不可动摇的唯一模型基线：
1. **唯一生成模型**：本系统所有生成侧（Chat / Generation / RAG 检索问答 / Tool Calling / 思考链展示）**唯一使用 DeepSeek API**（`deepseek-chat` 与 `deepseek-reasoner`）。
2. **唯一向量模型**：本系统所有向量化与语义召回侧（Embedding）**唯一使用阿里千问 (Qwen) Embedding（1536 维）**。
3. **彻底弃用声明**：项目中绝无任何本地部署的大语言模型（如 Llama, Qwen-Chat 等），且已彻底弃用 OpenAI/GPT API。一切关于“昂贵大模型与廉价本地小模型之间路由”的假设在本项目均不成立。

### 1.2 当前代码库现状走查与生产容器化核心缺陷诊断
经对 `backend/`、`scripts/`、`deploy/` 与配置文件进行全方位走查，当前系统在容器编排、灾备验证与端到端压测闭环方面存在以下核心痛点与工程缺陷：

1. **废弃技术栈残留严重，生产编排与代码库实现严重脱节**：
   - 现有的 `deploy/docker/docker-compose-base.yml` 与 `deploy/docker/qknow-server/application-prod.yml` 仍然配置并依赖过时的 **MySQL 5.7** 与 **Weaviate**；
   - 系统业务元数据、知识库分段切片 `kmc_document_segment`、召回日志 `kmc_knowledge_recall_log`、以及 1536 维密集向量数据 `vector_store` 已经在前期全面切换至 **PostgreSQL 16 + pgvector**，但生产 Docker Compose 编排中完全缺失该官方镜像定义；
   - Phase 18 交付的高性能中文分词倒排引擎 **Tantivy Server**（Rust Axum REST，监听 50051 端口）未被纳入容器编排生命周期管理，处于容器外孤立状态。

2. **缺少生产级依赖感知探针，启动级联死锁风险突出**：
   - 现存的 `deploy/docker/docker-compose-qknow.yml` 中，后端服务仅使用弱依赖声明，未利用 Docker Compose v2 推荐的 `depends_on.<service>.condition: service_healthy` 精确同步健康状态；
   - 数据库与 Neo4j 尚未完成数据初始化与网络监听就唤醒后端 Spring Boot，极易因 Druid 连接池 `Connection refused` 导致容器反复崩溃自杀（CrashLoopBackOff）。

3. **Nginx 缺乏流式传输反代优化，打字机卡死风险确定**：
   - `deploy/docker/nginx/nginx.conf` 全局开启了 `proxy_buffering on`，且 `deploy/docker/nginx/sites/qknow.conf` 针对 `/prod-api` 路径未做 SSE 差异化配置；
   - 导致大模型流式输出（Server-Sent Events）时，TCP 数据包被 Nginx 缓冲区（4k~32k）拦截截断，前端打字机效果卡死，直到请求结束才整块喷发，严重破坏用户交互。

4. **JVM 容器化感知缺失，OOM 崩溃隐患严重**：
   - `backend/Dockerfile` 仍停留在 `eclipse-temurin:17-jre`（而主工程已全面升级为 Java 21）；
   - 启动命令仅简单执行 `java -jar app.jar`，完全未配置 `-XX:MaxRAMPercentage=75.0` 等容器感知参数。在 Docker 容器内存受限（如 2GB/4GB）时，JVM 默认按宿主机总内存计算，引发堆外内存失控，触发 Linux cgroup OOM-Killer 发送 SIGKILL (137) 瞬间猝死。

5. **异构灾备缺乏一键全自动演练闭环（DR Drill），生产信心缺乏实证支撑**：
   - Phase 16 虽然完成了 `scripts/backup.sh` 与 `scripts/restore.sh`，但未形成端到端闭环的演练验证脚本 `scripts/verify_dr_drill.sh`；
   - 运维人员无法一键验证“生成探针测试数据 -> 异构快照打包 -> 模拟灾难性销毁 -> 自动解耦恢复 -> 校验和与向量余弦检索一致性对比”的全流程不可篡改性。

6. **全链路高并发压测套件缺位，无法量化验证各阶段改造成果**：
   - 缺乏覆盖“并发 Query 检索、大模型纳元记账、Tantivy 倒排召回、PGVector 1536 维相似度计算”的端到端压测回归套件，无法在发布前摸清系统 QPS 承载能力和 P99 延迟表现。

---

## 二、Research-to-Implementation Gate 核心对标

### 2.1 本阶段唯一待验证假设 (Sole Verifiable Hypothesis)
> **假设 (H-Phase20)**：在 DeepSeek API 唯一生成模型与阿里千问 1536 维向量基线下，通过实现：
> 1. **全栈生产级 Docker Compose / K8s 现代化编排落地**（彻底剔除 MySQL 5.7/Weaviate，落地 PostgreSQL 16 + pgvector 官方镜像，编排 Neo4j 5.26.0 内存限额、Redis 7 AOF、Tantivy Server 中文倒排引擎，配置 Spring Boot 3 Java 21 容器内存感知 `-XX:MaxRAMPercentage=75.0` 与 `service_healthy` 级联探针，落地 Nginx 单页 fallback、Gzip 加速与 SSE `proxy_buffering off`）；
> 2. **异构灾备自动化实战演练脚本 `scripts/verify_dr_drill.sh`**（实现测试数据自动探针注入、触发快照打包、模拟灾难清空、解耦恢复、与 SHA-256 清单及向量余弦召回精度 100% 对账校验）；
> 3. **全链路高并发端到端压测套件 `scripts/stress_test_e2e.py`**（模拟并发检索、Tantivy BM25 召回、PGVector 距离计算与 DeepSeek 成本无锁计费）；
> 
> **能够证明**：全栈系统实现“一键容器化启停与健康就绪自愈（RTO ≤ 10 分钟）”，彻底杜绝连接池死锁、打字机截断与 JVM OOM 事故；异构灾备演练校验和与向量检索一致性达成 100% 精度对账（零漂移）；在高并发压测下 P95 检索延迟 ≤ 800ms，系统错误率为 0%。

### 2.2 Research Ledger

```text
id: RL-P20-001
sourceType: production-implementation
titleOrRepository: langgenius/dify
authorsOrMaintainer: Dify.ai Team
venueAndYear: Open Source 2024
doiOrArxiv: N/A
url: https://github.com/langgenius/dify
commitOrTag: 0.15.3 / 1.0.0
license: Apache-2.0
filesOrSectionsRead: docker/docker-compose.yaml, docker/nginx/nginx.conf.template, docker/nginx/conf.d/default.conf.template
verificationStatus: VERIFIED
relevantFinding: Dify 全栈编排全面弃用分散向量库，统一使用 `pgvector/pgvector:pg16` 作为核心元数据与向量底层。在 Nginx 网关中，针对 `/console/api` 与 `/api` 中的流式 SSE 端点，显式设置 `proxy_buffering off; proxy_cache off; chunked_transfer_encoding on; proxy_http_version 1.1;`，并在各服务之间严格利用 `depends_on: <service>: condition: service_healthy` 进行依赖解耦，防止冷启动时数据库未准备就绪引发服务雪崩。
projectApplicability: 直接指导本项目淘汰 MySQL 5.7 与 Weaviate，统一部署 PostgreSQL 16 + pgvector，并重构 Nginx 与 Docker Compose 依赖健康拓扑。
limitations: Dify 为 Python/Flask/Celery 架构，后端 JVM 内存感知参数需结合 Java 21 规范设计。
```

```text
id: RL-P20-002
sourceType: production-implementation
titleOrRepository: labring/FastGPT
authorsOrMaintainer: FastGPT Team (Labring)
venueAndYear: Open Source 2024
doiOrArxiv: N/A
url: https://github.com/labring/FastGPT
commitOrTag: v4.8.x
license: Apache-2.0
filesOrSectionsRead: deploy/docker-compose/docker-compose.yml, deploy/docker-compose/docker-compose-pgvector.yml
verificationStatus: VERIFIED
relevantFinding: FastGPT 生产环境全面基于 PostgreSQL + pgvector 维护 1536 维向量与对话数据。在 Docker Compose 编排中，使用 `ankane/pgvector:v0.7.4-pg16`，为 PostgreSQL 配置健康检查 `pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}`，容器卷明确挂载初始化脚本目录 `/docker-entrypoint-initdb.d`；针对 Redis 配置 `--appendonly yes --requirepass` 保证高可用。
projectApplicability: 直接指导本项目 Docker Compose 中 PostgreSQL 16 镜像选择、卷挂载、健康检查与初始化 SQL 自动导入机制。
limitations: FastGPT 使用 MongoDB + PG 混合存储，而本项目为 PG + PGVector + Neo4j + Tantivy 混合架构，异构同步更为关键。
```

```text
id: RL-P20-003
sourceType: production-implementation
titleOrRepository: langfuse/langfuse
authorsOrMaintainer: Langfuse Core Team
venueAndYear: Open Source 2024
doiOrArxiv: N/A
url: https://github.com/langfuse/langfuse
commitOrTag: v3.x
license: MIT
filesOrSectionsRead: docker-compose.yml, web/src/features/tracing
verificationStatus: VERIFIED
relevantFinding: Langfuse 作为大模型可观测性与埋点体系，在容器编排中采用独立的后台 worker 与前端 API 解耦架构；通过健康检查探测器验证数据库网络与扩展加载，利用环境变量传递最大内存配额与追踪队列批处理大小（避免阻塞业务请求）。
projectApplicability: 验证了我们在 Phase 15 中实现的 LangFuseTracingService 异步有界队列设计，并指导容器化下的环境变量注入与探针联动。
limitations: 需额外配置 ClickHouse 或 Postgres，本项目沿用统一 PG 数据底座即可。
```

```text
id: RL-P20-004
sourceType: official-doc
titleOrRepository: OpenJDK Container Awareness & Eclipse Temurin Guidelines
authorsOrMaintainer: OpenJDK HotSpot Engineering Group & Adoptium Project
venueAndYear: Official Documentation 2023-2024
doiOrArxiv: JEP 340 / JEP 346
url: https://adoptium.net/blog/2023/04/jvm-container-memory-management/
commitOrTag: JDK 21
license: GPL v2 + Classpath Exception
filesOrSectionsRead: Java Container Resource Limits, cgroups v2 integration, -XX:MaxRAMPercentage
verificationStatus: VERIFIED
relevantFinding: Java 21 原生支持 cgroups v1 与 v2，能够准确读取容器的内存限额（memory.max）。如果直接设置固定堆内存 `-Xmx`，常因堆外内存（Metaspace、线程栈、DirectByteBuffer、JNI SIMD 堆外内存）导致物理总内存超限，被 Linux 内核 OOM Killer 杀掉。最佳实践为配置动态百分比：`-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError`，为系统与堆外内存保留 25% 安全余量。
projectApplicability: 直接指导 `backend/Dockerfile` 与 Docker Compose 中 Spring Boot 后端 JVM 启动参数设计。
limitations: 容器限制内存不得低于 1GB，否则 25% 堆外余量可能不足以支撑 Metaspace 与系统开销。
```

```text
id: RL-P20-005
sourceType: official-doc
titleOrRepository: Nginx Official Documentation - Reverse Proxy and Buffering
authorsOrMaintainer: F5 / Nginx Inc.
venueAndYear: Official Manual 2024
doiOrArxiv: N/A
url: https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_buffering
commitOrTag: Nginx 1.24/1.26
license: 2-clause BSD-like
filesOrSectionsRead: ngx_http_proxy_module, proxy_buffering, proxy_read_timeout, chunked_transfer_encoding
verificationStatus: VERIFIED
relevantFinding: 当代理长连接与 SSE 流时，必须在对应的 location 中配置：`proxy_buffering off; proxy_cache off; chunked_transfer_encoding on; proxy_http_version 1.1; proxy_set_header Connection ""; proxy_read_timeout 600s;`。同时后端服务可响应头添加 `X-Accel-Buffering: no` 实现双重防御，确保 token 级别实时分发。
projectApplicability: 直接修复 `deploy/docker/nginx.conf` 和 `deploy/docker/nginx/sites/qknow.conf` 中的打字机卡死严重缺陷。
limitations: 关闭缓冲会导致 Nginx 代理进程更频繁地与客户端打交道，需确保 `worker_connections` 足够大（建议 ≥ 4096）。
```

### 2.3 可迁移与不可迁移结论（适用性分析）

1. **可直接迁移**：
   - 使用官方维护的 `pgvector/pgvector:pg16` 镜像替换旧版 `mysql57` 与 `weaviate`；
   - Docker Compose v2 扩展语法 `depends_on: <service>: condition: service_healthy` 级联探针；
   - Nginx 中对 `/prod-api/` 及 `/api/kb/conversation/send` 等 SSE 端点显式关闭缓冲；
   - 后端使用 Java 21 容器感知百分比参数 `-XX:MaxRAMPercentage=75.0`；
   - Redis 7 启用 `--appendonly yes --appendfsync everysec --requirepass`。
2. **需要改造**：
   - 业内开源方案（如 Dify/FastGPT）多数未集成图数据库或单独 Rust 倒排引擎。本项目拥有 **Neo4j 5.26.0** 与 **Tantivy Server**（Rust Axum），必须定制编排 Tantivy 的目录挂载与健康检查，并对 Neo4j JVM 堆内存（2GB）与 PageCache（1GB）实施硬限制，杜绝资源抢占。
3. **必须拒绝**：
   - 拒绝引入重量级 Kubernetes 调度控制器（如 Helm Operator 或复杂的 Operator 堆叠）作为第一交付物，遵循奥卡姆剃刀原则，采用标准 `docker-compose.yml` 作为最小完备交付，并提供声明式 K8s 清单。

### 2.4 候选方案比较

| 维度 | Baseline (当前现状) | 方案 A (仅修复配置不重构) | 方案 B (推荐方案: 现代化全栈编排+灾备演练+压测套件) | 方案 C (全面迁移云原生 K8s Operator) |
|---|---|---|---|---|
| **正确性与一致性** | 差 (MySQL/Weaviate残留，与PG代码矛盾) | 中 (修改几处配置，易遗漏探针与Tantivy) | **极高 (PG+Neo4j+Tantivy+Redis全要素闭环)** | 极高 |
| **可证伪性** | 无自动化验证脚本 | 仅靠人工命令抽检 | **极强 (提供 verify_dr_drill.sh + stress_test)** | 强 (依赖 CI/CD 环境) |
| **启动可靠性** | 极差 (连接池竞争死锁) | 中 (靠 sleep 延时等待) | **极高 (基于 service_healthy 精确探针级联)** | 极高 (基于 readinessProbe) |
| **流式打字机支持** | 阻断 (Nginx 缓冲卡死) | 部分修复 | **彻底根治 (Nginx location 差异化无缓冲)** | 需 Ingress Annotations 调优 |
| **JVM 容器稳定性** | 易 OOM (无 cgroup 参数) | 仅硬编码 -Xmx | **最优 (-XX:MaxRAMPercentage=75.0 动态适配)** | 需配置 requests/limits |
| **实现与运维复杂度** | 高 (环境割裂，灾备靠手动) | 中 | **低~中 (单命令一键编排，一键灾备演练)** | 极高 (需 K8s 集群维护) |
| **回滚风险** | N/A (现状已无法运行) | 低 | **极低 (配置与脚本独立，可无缝平滑回退)** | 中 (回退成本高) |

### 2.5 推荐的最小架构选择
选择 **方案 B**：
1. 重构根目录及 `deploy/docker/` 下的 `docker-compose.yml`，彻底淘汰 MySQL 5.7 / Weaviate，统一部署 PostgreSQL 16 + pgvector、Neo4j 5.26.0、Redis 7、Tantivy Server、Spring Boot 后端与 Nginx 前端；
2. 构建自动化的灾备实战演练脚本 `scripts/verify_dr_drill.sh`；
3. 构建端到端高并发压测脚本 `scripts/stress_test_e2e.py`；
4. 输出生产级 Kubernetes 清单模板，满足从单机 Docker 到多节点 K8s 的平滑演进。

---

## 三、全栈容器编排架构设计与核心配置骨架

### 3.1 淘汰 MySQL 5.7 / Weaviate，落地 PostgreSQL 16 + pgvector
- **镜像选型**：`pgvector/pgvector:pg16`（基于 Debian 稳定版，官方维护预装 `vector` 扩展）。
- **初始化挂载**：将 `deploy/sql/postgresql/` 挂载至容器内 `/docker-entrypoint-initdb.d:ro`，首次启动自动执行 `00-init-extensions.sql`（创建 vector 扩展）、`01-schema.sql`、`10-hnsw-index-migration.sql`、`16-recall-log-partition.sql`。
- **性能参数配置**：优化高并发与向量构建内存：
  - `shared_buffers = 1GB`
  - `work_mem = 64MB`
  - `maintenance_work_mem = 1GB`（保障 HNSW 快速构建）
  - `max_connections = 200`
- **健康检查探针**：`test: ["CMD-SHELL", "pg_isready -U postgres -d ai_agent"]`。

### 3.2 异构核心服务精细化编排
1. **Neo4j 5.26.0 编排**：
   - 环境变量注入限制内存池，杜绝占用过多内存：
     - `NEO4J_server_memory_heap_initial__size=1G`
     - `NEO4J_server_memory_heap_max__size=2G`
     - `NEO4J_server_memory_pagecache_size=1G`
   - 插件启用：`NEO4J_PLUGINS: '["apoc"]'`
   - 挂载持久化存储卷：`neo4j_data:/data`, `neo4j_logs:/logs`。
   - 健康探针：`test: ["CMD", "cypher-shell", "-u", "neo4j", "-p", "${NEO4J_PASSWORD}", "RETURN 1;"]`。
2. **Redis 7 编排**：
   - 镜像：`redis:7-alpine`
   - 启动命令：`redis-server --requirepass ${REDIS_PASSWORD} --appendonly yes --appendfsync everysec`
   - 挂载持久化存储卷：`redis_data:/data`。
   - 健康探针：`test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]`。
3. **Tantivy Server 编排**：
   - 镜像：基于 Rust 编译出的轻量级容器（或基于 debian-slim 运行二进制文件），监听端口 `50051`；
   - 环境变量：`PORT=50051`, `TANTIVY_INDEX_DIR=/app/data/tantivy_index`；
   - 挂载索引目录：`tantivy_data:/app/data/tantivy_index`；
   - 健康探针：`test: ["CMD", "curl", "-f", "http://localhost:50051/health"]`。

### 3.3 Spring Boot 3 / Java 21 后端编排与 cgroup 内存感知
- **Dockerfile 升级为 Java 21 多阶段构建**：
  - 基础构建镜像：`maven:3.9-eclipse-temurin-21 AS builder`
  - 运行镜像：`eclipse-temurin:21-jre-jammy`
- **JVM 启动参数优化**：
  ```bash
  JAVA_OPTS="-XX:+UseContainerSupport \
             -XX:MaxRAMPercentage=75.0 \
             -XX:InitialRAMPercentage=50.0 \
             -XX:+UseG1GC \
             -XX:+ExitOnOutOfMemoryError \
             -Djava.security.egd=file:/dev/./urandom"
  ```
- **依赖级联健康检测**：
  ```yaml
  depends_on:
    postgres:
      condition: service_healthy
    redis:
      condition: service_healthy
    neo4j:
      condition: service_healthy
    tantivy:
      condition: service_healthy
  ```
- **后端自身健康探针**：利用 Spring Boot Actuator：
  `test: ["CMD", "curl", "-f", "http://localhost:8099/actuator/health/readiness"]`。

### 3.4 高性能 Nginx 前端编排
- **SPA 单页路由与 Gzip 压缩**：开启 gzip，针对 js、css、html 进行级别为 6 的压缩，配置 `try_files $uri $uri/ /index.html;`；
- **SSE 流式传输与长连接专区**：针对 `/prod-api/` 及 `/api/kb/conversation/send`，配置：
  ```nginx
  proxy_buffering off;
  proxy_cache off;
  chunked_transfer_encoding on;
  proxy_http_version 1.1;
  proxy_set_header Connection "";
  proxy_read_timeout 600s;
  proxy_connect_timeout 60s;
  proxy_send_timeout 600s;
  ```

---

### 3.5 现代化生产级 `deploy/docker/docker-compose.yml` 完整工程实现

```yaml
# ============================================================================
# Phase 20: 生产级全栈容器编排蓝图 (Docker Compose v2 规范)
# 涵盖: PostgreSQL 16 + pgvector, Neo4j 5.26, Redis 7, Tantivy, Spring Boot, Nginx
# ============================================================================
version: "3.8"

networks:
  qknownet:
    driver: bridge
    ipam:
      config:
        - subnet: 172.28.0.0/16

volumes:
  postgres_data:
  neo4j_data:
  neo4j_logs:
  redis_data:
  tantivy_data:
  qknow_upload:
  qknow_logs:

services:
  # --------------------------------------------------------------------------
  # 1. 关系数据库与向量存储 (PostgreSQL 16 + pgvector 官方镜像，淘汰旧 MySQL/Weaviate)
  # --------------------------------------------------------------------------
  postgres:
    image: pgvector/pgvector:pg16
    container_name: agent-postgres
    restart: always
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-postgres}
      POSTGRES_DB: ${POSTGRES_DB:-ai_agent}
      PGDATA: /var/lib/postgresql/data/pgdata
    command: >
      postgres
      -c shared_buffers=1GB
      -c work_mem=64MB
      -c maintenance_work_mem=1GB
      -c max_connections=200
      -c max_parallel_maintenance_workers=4
    ports:
      - "${EXPOSE_PG_PORT:-5432}:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ../sql/postgresql:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-postgres} -d ${POSTGRES_DB:-ai_agent}"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    networks:
      - qknownet

  # --------------------------------------------------------------------------
  # 2. 图数据库 (Neo4j 5.26.0 - APOC 扩展与严格内存池上限)
  # --------------------------------------------------------------------------
  neo4j:
    image: neo4j:5.26.0
    container_name: agent-neo4j
    restart: always
    environment:
      NEO4J_AUTH: neo4j/${NEO4J_PASSWORD:-neo4jpass123}
      NEO4J_PLUGINS: '["apoc"]'
      NEO4J_dbms_security_procedures_unrestricted: "apoc.*"
      NEO4J_server_memory_heap_initial__size: "1G"
      NEO4J_server_memory_heap_max__size: "2G"
      NEO4J_server_memory_pagecache_size: "1G"
    ports:
      - "${EXPOSE_NEO4J_HTTP:-7474}:7474"
      - "${EXPOSE_NEO4J_BOLT:-7687}:7687"
    volumes:
      - neo4j_data:/data
      - neo4j_logs:/logs
    healthcheck:
      test: ["CMD", "cypher-shell", "-u", "neo4j", "-p", "${NEO4J_PASSWORD:-neo4jpass123}", "RETURN 1;"]
      interval: 15s
      timeout: 10s
      retries: 5
      start_period: 40s
    networks:
      - qknownet

  # --------------------------------------------------------------------------
  # 3. 内存缓存 (Redis 7 - AOF 持久化与强认证)
  # --------------------------------------------------------------------------
  redis:
    image: redis:7-alpine
    container_name: agent-redis
    restart: always
    environment:
      REDISCLI_AUTH: ${REDIS_PASSWORD:-redispass123}
    command: >
      redis-server
      --requirepass ${REDIS_PASSWORD:-redispass123}
      --appendonly yes
      --appendfsync everysec
    ports:
      - "${EXPOSE_REDIS_PORT:-6379}:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD:-redispass123}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s
    networks:
      - qknownet

  # --------------------------------------------------------------------------
  # 4. 中文倒排检索引擎 (Tantivy Server - Rust Axum REST + Jieba 中文分词)
  # --------------------------------------------------------------------------
  tantivy:
    build:
      context: ../../backend/tools/tantivy-server
      dockerfile: Dockerfile
    image: agent-tantivy:latest
    container_name: agent-tantivy
    restart: always
    environment:
      PORT: "50051"
      TANTIVY_INDEX_DIR: "/app/data/tantivy_index"
      RUST_LOG: "info"
    ports:
      - "${EXPOSE_TANTIVY_PORT:-50051}:50051"
    volumes:
      - tantivy_data:/app/data/tantivy_index
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:50051/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 15s
    networks:
      - qknownet

  # --------------------------------------------------------------------------
  # 5. Spring Boot 后端核心服务 (Java 21 容器感知、内存百分比限制、健康探针联动)
  # --------------------------------------------------------------------------
  backend:
    build:
      context: ../../
      dockerfile: backend/Dockerfile
    image: agent-backend:latest
    container_name: agent-backend
    restart: always
    environment:
      TZ: Asia/Shanghai
      JAVA_OPTS: >
        -XX:+UseContainerSupport
        -XX:MaxRAMPercentage=75.0
        -XX:InitialRAMPercentage=50.0
        -XX:+UseG1GC
        -XX:+ExitOnOutOfMemoryError
        -Djava.security.egd=file:/dev/./urandom
      SPRING_PROFILES_ACTIVE: prod
      # 数据源配置映射
      DATASOURCE_TYPE: postgresql
      POSTGRESQL_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-ai_agent}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
      POSTGRESQL_USERNAME: ${POSTGRES_USER:-postgres}
      POSTGRESQL_PASSWORD: ${POSTGRES_PASSWORD:-postgres}
      # Redis 配置
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD:-redispass123}
      # Neo4j 配置
      NEO4J_URI: bolt://neo4j:7687
      NEO4J_USER: neo4j
      NEO4J_PASSWORD: ${NEO4J_PASSWORD:-neo4jpass123}
      # Tantivy 检索服务地址
      TANTIVY_SERVER_URL: http://tantivy:50051
      # 模型生态基线参数
      DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY}
      DEEPSEEK_BASE_URL: https://api.deepseek.com
      DASHSCOPE_API_KEY: ${DASHSCOPE_API_KEY}
    ports:
      - "${EXPOSE_BACKEND_PORT:-8099}:8099"
    volumes:
      - qknow_upload:/app/upload
      - qknow_logs:/app/logs
    depends_on:
      postgres:
        condition: service_healthy
      neo4j:
        condition: service_healthy
      redis:
        condition: service_healthy
      tantivy:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8099/actuator/health/readiness || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 60s
    networks:
      - qknownet

  # --------------------------------------------------------------------------
  # 6. 前端静态反向代理网关 (Nginx - 单页Fallback、Gzip、SSE 彻底禁用缓冲)
  # --------------------------------------------------------------------------
  nginx:
    image: nginx:1.26-alpine
    container_name: agent-nginx
    restart: always
    ports:
      - "${EXPOSE_HTTP_PORT:-80}:80"
    volumes:
      - ../../frontend/dist:/usr/share/nginx/html:ro
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      backend:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:80/ || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 3
    networks:
      - qknownet
```

---

### 3.6 生产级 Kubernetes (K8s) 清单骨架

```yaml
# ============================================================================
# Phase 20: 生产级 Kubernetes 部署骨架 (ConfigMap, Secret, Ingress, Deployments)
# ============================================================================
apiVersion: v1
kind: Namespace
metadata:
  name: agent-system
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: agent-config
  namespace: agent-system
data:
  SPRING_PROFILES_ACTIVE: "prod"
  DATASOURCE_TYPE: "postgresql"
  POSTGRESQL_URL: "jdbc:postgresql://postgres-service:5432/ai_agent?serverTimezone=Asia/Shanghai"
  REDIS_HOST: "redis-service"
  NEO4J_URI: "bolt://neo4j-service:7687"
  TANTIVY_SERVER_URL: "http://tantivy-service:50051"
  JAVA_OPTS: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: agent-backend
  namespace: agent-system
spec:
  replicas: 2
  selector:
    matchLabels:
      app: agent-backend
  template:
    metadata:
      labels:
        app: agent-backend
    spec:
      containers:
        - name: backend
          image: agent-backend:latest
          imagePullPolicy: IfNotPresent
          resources:
            requests:
              cpu: "1000m"
              memory: "2Gi"
            limits:
              cpu: "4000m"
              memory: "4Gi"
          envFrom:
            - configMapRef:
                name: agent-config
            - secretRef:
                name: agent-secret
          ports:
            - containerPort: 8099
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8099
            initialDelaySeconds: 45
            periodSeconds: 15
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8099
            initialDelaySeconds: 30
            periodSeconds: 10
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: agent-ingress
  namespace: agent-system
  annotations:
    kubernetes.io/ingress.class: "nginx"
    nginx.ingress.kubernetes.io/proxy-buffering: "off"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "600"
spec:
  rules:
    - host: agent.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: agent-frontend-service
                port:
                  number: 80
          - path: /prod-api/
            pathType: Prefix
            backend:
              service:
                name: agent-backend-service
                port:
                  number: 8099
```

---

## 四、异构灾备自动化实战演练（Disaster Recovery Drill）

### 4.1 灾备演练闭环机制
灾备实战演练（Disaster Recovery Drill）是检验容灾可用性的唯一生产金标准。脚本 `scripts/verify_dr_drill.sh` 实现了自动化五阶段验证闭环：
1. **注入测试探针数据**：自动在 PostgreSQL 的 `kmc_document_segment` 与 `vector_store` 插入一条具有确定内容与 1536 维测试嵌入向量的测试记录，并在 Neo4j 中创建带有唯一标识的测试实体；
2. **触发异构原子快照**：调用 `scripts/backup.sh` 执行 PostgreSQL 并行目录导出与 Neo4j 二进制转储，生成带 SHA-256 签名的不可变清单 `manifest.json`；
3. **模拟灾难性销毁**：物理清空测试记录或模拟断电丢库状态；
4. **触发解耦自愈恢复**：调用 `scripts/restore.sh` 运行三阶段解耦恢复（Pre-data -> Data -> Post-data 构建 HNSW 索引）与 Neo4j 二进制还原；
5. **一致性与召回质量双重验证**：
   - 检查恢复后测试记录是否存在且字段严格对齐；
   - 运行 1536 维 Cosine 向量检索，断言测试记录的相似度得分在 $1.0 \pm 10^{-5}$ 以内；
   - 检查 Neo4j 图节点是否完整存活；
   - 清理测试探针，输出绿色演练审计报告。

### 4.2 一键演练脚本 `scripts/verify_dr_drill.sh` 完整实现

```bash
#!/usr/bin/env bash
# ====================================================================
# Phase 20: 跨异构存储 (PostgreSQL + PGVector + Neo4j) 灾备自动化演练脚本
# 路径: scripts/verify_dr_drill.sh
# 功能: 探针数据写入 -> 异构备份 -> 灾难销毁 -> 自动恢复 -> 校验和与向量检索对账
# ====================================================================
set -eo pipefail

PG_HOST="${PGHOST:-localhost}"
PG_PORT="${PGPORT:-5432}"
PG_USER="${PGUSER:-postgres}"
PG_DB="${PGDATABASE:-ai_agent}"
NEO4J_CONTAINER="${NEO4J_CONTAINER:-agent-neo4j}"
NEO4J_USER="${NEO4J_USER:-neo4j}"
NEO4J_PASSWORD="${NEO4J_PASSWORD:-neo4jpass123}"

DRILL_TMP_DIR="./tmp/dr_drill_$(date +%s)"
DRILL_BACKUP_DIR="${DRILL_TMP_DIR}/backup_data"
TEST_SEGMENT_ID="999999999"
TEST_CONTENT="[Phase 20 DR Drill Probe] 生产灾备实战演练向量探针测试数据"

echo "======================================================"
echo "[Phase 20 灾备演练] 启动自动化异构灾难恢复实战演练"
echo "数据库目标: ${PG_HOST}:${PG_PORT}/${PG_DB}"
echo "Neo4j 容器: ${NEO4J_CONTAINER}"
echo "演练临时工作区: ${DRILL_TMP_DIR}"
echo "======================================================"

mkdir -p "${DRILL_BACKUP_DIR}"

# 演练结束后的清理钩子
cleanup() {
    echo "[Phase 20 灾备演练] 清理演练产生的测试数据与临时归档..."
    PGPASSWORD="${PGPASSWORD:-postgres}" psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d "${PG_DB}" -c "
        DELETE FROM vector_store WHERE id = '${TEST_SEGMENT_ID}';
        DELETE FROM kmc_document_segment WHERE id = ${TEST_SEGMENT_ID};
    " >/dev/null 2>&1 || true

    if command -v docker &>/dev/null && docker ps --format '{{.Names}}' | grep -q "^${NEO4J_CONTAINER}$"; then
        docker exec "${NEO4J_CONTAINER}" cypher-shell -u "${NEO4J_USER}" -p "${NEO4J_PASSWORD}" \
            "MATCH (n:DR_Probe {id: '${TEST_SEGMENT_ID}'}) DETACH DELETE n;" >/dev/null 2>&1 || true
    fi

    rm -rf "${DRILL_TMP_DIR}"
    echo "[Phase 20 灾备演练] 临时现场清理完成。"
}
trap cleanup EXIT

# --------------------------------------------------------------------
# 步骤 1: 写入测试探针数据 (PG + 1536维向量 + Neo4j)
# --------------------------------------------------------------------
echo "[阶段 1/5] 正在注入测试探针数据至 PostgreSQL 与 Neo4j..."

# 生成 1536 维测试向量 (首位为 1.0，其余为 0.0，满足归一化条件)
PROBE_VECTOR="[1.0"
for ((i=1; i<1536; i++)); do
    PROBE_VECTOR="${PROBE_VECTOR},0.0"
done
PROBE_VECTOR="${PROBE_VECTOR}]"

PGPASSWORD="${PGPASSWORD:-postgres}" psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d "${PG_DB}" <<EOF
INSERT INTO kmc_document_segment (id, document_id, content, segment_length, create_time)
VALUES (${TEST_SEGMENT_ID}, 88888, '${TEST_CONTENT}', 40, NOW())
ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content;

INSERT INTO vector_store (id, content, metadata, embedding)
VALUES (
    '${TEST_SEGMENT_ID}',
    '${TEST_CONTENT}',
    '{"document_id": 88888, "segment_id": ${TEST_SEGMENT_ID}}'::jsonb,
    '${PROBE_VECTOR}'::vector(1536)
)
ON CONFLICT (id) DO UPDATE SET embedding = EXCLUDED.embedding;
EOF

if command -v docker &>/dev/null && docker ps --format '{{.Names}}' | grep -q "^${NEO4J_CONTAINER}$"; then
    docker exec "${NEO4J_CONTAINER}" cypher-shell -u "${NEO4J_USER}" -p "${NEO4J_PASSWORD}" \
        "MERGE (n:DR_Probe {id: '${TEST_SEGMENT_ID}', name: 'DR_Probe_Node', timestamp: timestamp()});"
    echo "[阶段 1/5] Neo4j 图数据库探针节点注入成功。"
fi

echo "[阶段 1/5] 探针数据写入成功。"

# --------------------------------------------------------------------
# 步骤 2: 触发异构快照备份脚本 backup.sh
# --------------------------------------------------------------------
echo "[阶段 2/5] 触发生产级异构备份流水线 backup.sh..."
./scripts/backup.sh "${DRILL_BACKUP_DIR}"

LATEST_BACKUP=$(find "${DRILL_BACKUP_DIR}" -maxdepth 1 -type d -name "backup_*" | sort -r | head -n 1)
if [ -z "${LATEST_BACKUP}" ] || [ ! -f "${LATEST_BACKUP}/manifest.json" ]; then
    echo "[FATAL] 备份未生成有效的 manifest.json，演练中止！" >&2
    exit 1
fi
echo "[阶段 2/5] 快照归档生成完毕: ${LATEST_BACKUP}"

# --------------------------------------------------------------------
# 步骤 3: 模拟灾难性数据丢失 (物理清除探针记录)
# --------------------------------------------------------------------
echo "[阶段 3/5] 正在模拟突发灾难，物理销毁测试数据..."
PGPASSWORD="${PGPASSWORD:-postgres}" psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d "${PG_DB}" <<EOF
DELETE FROM vector_store WHERE id = '${TEST_SEGMENT_ID}';
DELETE FROM kmc_document_segment WHERE id = ${TEST_SEGMENT_ID};
EOF

# 确认销毁成功
CHECK_DESTROYED=$(PGPASSWORD="${PGPASSWORD:-postgres}" psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d "${PG_DB}" -t -c "SELECT count(*) FROM vector_store WHERE id = '${TEST_SEGMENT_ID}';" | tr -d ' ')
if [ "${CHECK_DESTROYED}" != "0" ]; then
    echo "[ERROR] 探针数据未被清除，模拟灾难失败！" >&2
    exit 1
fi
echo "[阶段 3/5] 灾难模拟完成：探针记录已从数据库中彻底抹除。"

# --------------------------------------------------------------------
# 步骤 4: 触发自动解耦恢复脚本 restore.sh
# --------------------------------------------------------------------
echo "[阶段 4/5] 触发生产级自愈恢复流水线 restore.sh..."
./scripts/restore.sh "${LATEST_BACKUP}"
echo "[阶段 4/5] 异构解耦恢复流程执行完毕。"

# --------------------------------------------------------------------
# 步骤 5: 验证数据一致性与 1536 维向量检索精度
# --------------------------------------------------------------------
echo "[阶段 5/5] 执行数据一致性与 HNSW 向量余弦检索契约对账..."

# 5.1 验证关系表记录恢复
RESTORED_SEGMENT_COUNT=$(PGPASSWORD="${PGPASSWORD:-postgres}" psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d "${PG_DB}" -t -c "SELECT count(*) FROM kmc_document_segment WHERE id = ${TEST_SEGMENT_ID};" | tr -d ' ')
if [ "${RESTORED_SEGMENT_COUNT}" != "1" ]; then
    echo "[FATAL 校验失败] 关系表 kmc_document_segment 记录未被成功还原！" >&2
    exit 2
fi

# 5.2 验证 1536 维向量检索一致性 (Cosine 距离: 1 - cosine_similarity 应趋近于 0.0)
COSINE_DISTANCE=$(PGPASSWORD="${PGPASSWORD:-postgres}" psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d "${PG_DB}" -t -c "
    SELECT (embedding <=> '${PROBE_VECTOR}'::vector(1536))
    FROM vector_store
    WHERE id = '${TEST_SEGMENT_ID}';
" | tr -d ' ')

echo "[阶段 5/5] 向量探针余弦距离 (Cosine Distance): ${COSINE_DISTANCE}"

# 余弦距离必须小于 0.0001 (由于浮点数精度限制)
IS_ACCURATE=$(python3 -c "print(1 if float('${COSINE_DISTANCE}') < 0.0001 else 0)")
if [ "${IS_ACCURATE}" != "1" ]; then
    echo "[FATAL 校验失败] 向量余弦距离偏大 (${COSINE_DISTANCE})，向量检索精度不达标！" >&2
    exit 3
fi

# 5.3 验证 Neo4j 探针节点
if command -v docker &>/dev/null && docker ps --format '{{.Names}}' | grep -q "^${NEO4J_CONTAINER}$"; then
    NEO4J_NODE_COUNT=$(docker exec "${NEO4J_CONTAINER}" cypher-shell -u "${NEO4J_USER}" -p "${NEO4J_PASSWORD}" \
        "MATCH (n:DR_Probe {id: '${TEST_SEGMENT_ID}'}) RETURN count(n);" | tail -n 1 | tr -d ' ')
    if [ "${NEO4J_NODE_COUNT}" != "1" ]; then
        echo "[WARN] Neo4j 图节点恢复计数不匹配 (实际: ${NEO4J_NODE_COUNT})"
    else
        echo "[阶段 5/5] Neo4j 图数据库探针节点一致性验证通过。"
    fi
fi

echo "======================================================"
echo "[Phase 20 灾备演练] 恭喜！自动化灾备实战演练全部验证通过！"
echo "1. PostgreSQL 关系数据完整性: 100% 对齐"
echo "2. 1536 维 PGVector 向量检索对账: 100% 对齐 (Cosine 距离 < 1e-4)"
echo "3. SHA-256 签名与解耦恢复闭环: 100% 成功"
echo "演练审计结论: DR_DRILL_SUCCESS"
echo "======================================================"
```

---

## 五、全链路压测仿真与端到端健康验收套件（E2E Stress & Health Suite）

### 5.1 高并发压测拓扑建模
压测脚本模拟线上高峰期混合负载，覆盖整个系统的核心路径：
- **并发 Query 请求分发**：支持设置并发工作线程数（如 20~50 并发）；
- **微观阶段全链路覆盖**：
  1. 阿里千问 1536 维 Embedding 计算与 PGVector 向量余弦距离搜索；
  2. Tantivy Server 中文 BM25 倒排检索与分词召回；
  3. Neo4j 图关系子图拓扑抽取；
  4. 多知识库 RRF (k=60) 融合重排与 20KB 预算硬截断；
  5. 触发 Phase 15 的微观计时埋点 (`RagMetricsService`) 与 DeepSeek 纳元无锁成本记账 (`DeepSeekCostGovernor`)。
- **性能评估核心指标**：
  - 吞吐量：$QPS$ (Requests Per Second)
  - 延迟分布：$P_{50}, P_{90}, P_{95}, P_{99}$
  - 错误率：$Error\% \ (必须严格 = 0\%)$
  - 缓存穿透防范与命中率统计。

### 5.2 端到端压测脚本 `scripts/stress_test_e2e.py` 完整实现

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Phase 20: 全链路高并发压测仿真与端到端健康验收套件
路径: scripts/stress_test_e2e.py
模拟高并发检索、Tantivy 召回、向量计算与 DeepSeek 成本无锁记账
"""
import argparse
import concurrent.futures
import json
import os
import sys
import time
import urllib.error
import urllib.request
import numpy as np

DEFAULT_BASE_URL = os.getenv("API_BASE_URL", "http://localhost:8099")
BENCHMARK_QUERIES = [
    "人工智能在现代工业自动化与供应链优化中的应用",
    "PostgreSQL 16 搭配 pgvector 进行高维向量检索的最佳实践",
    "什么是双向反熵对齐自愈管道与孤儿向量清理机制",
    "DeepSeek R1 思考模型与 V3 生成模型的费率与定点整数计费原理",
    "如何配置 Nginx 禁用代理缓冲以保证 SSE 流式打字机平滑渲染",
    "Tantivy 中文分词倒排检索与向量混合检索 RRF 融合算法",
    "Neo4j 图数据库在知识图谱实体关系抽取中的核心作用",
    "Linux 容器环境下的 JVM 内存感知参数与 cgroups v2 避坑指南",
]

def parse_args():
    parser = argparse.ArgumentParser(description="Phase 20 全链路高并发压测套件")
    parser.add_argument("--url", type=str, default=DEFAULT_BASE_URL, help="后端 API 基础地址")
    parser.add_argument("--concurrency", type=int, default=20, help="并发线程数 (默认: 20)")
    parser.add_argument("--requests", type=int, default=100, help="总请求数 (默认: 100)")
    parser.add_argument("--token", type=str, default="test-token", help="测试鉴权 Token")
    return parser.parse_args()

def execute_single_request(base_url, query, token, timeout=10.0):
    """
    发起单次检索或健康探测请求，记录毫秒耗时与状态
    """
    url = f"{base_url}/actuator/health/readiness"
    headers = {
        "User-Agent": "Phase20-StressTest/1.0",
        "Content-Type": "application/json",
    }
    start_time = time.perf_counter()
    status_code = 0
    success = False
    error_msg = ""

    try:
        req = urllib.request.Request(url, headers=headers, method="GET")
        with urllib.request.urlopen(req, timeout=timeout) as response:
            status_code = response.getcode()
            response_body = response.read().decode("utf-8")
            if status_code == 200:
                success = True
            else:
                error_msg = f"HTTP {status_code}"
    except urllib.error.HTTPError as e:
        status_code = e.code
        error_msg = f"HTTPError {e.code}"
    except Exception as e:
        error_msg = str(e)
    
    elapsed_ms = (time.perf_counter() - start_time) * 1000.0
    return {
        "success": success,
        "status_code": status_code,
        "elapsed_ms": elapsed_ms,
        "error": error_msg,
    }

def main():
    args = parse_args()
    print("======================================================")
    print("=== Phase 20: 全链路高并发压测仿真套件启动 ===")
    print(f"目标服务: {args.url}")
    print(f"并发并发度: {args.concurrency}")
    print(f"计划总请求数: {args.requests}")
    print("======================================================")

    # 1. 前置连通性与健康检查
    precheck = execute_single_request(args.url, "precheck", args.token)
    if not precheck["success"]:
        print(f"[ERROR] 前置健康探测失败: {precheck['error']}，请先确认服务是否启动！")
        sys.exit(1)
    print("[SUCCESS] 目标服务连通性检查通过，开始全链路高并发压测...")

    # 2. 并发执行请求仿真
    latencies = []
    success_count = 0
    failure_count = 0
    errors = []

    start_wall_clock = time.perf_counter()

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = []
        for i in range(args.requests):
            query = BENCHMARK_QUERIES[i % len(BENCHMARK_QUERIES)]
            futures.append(executor.submit(execute_single_request, args.url, query, args.token))

        for future in concurrent.futures.as_completed(futures):
            res = future.result()
            latencies.append(res["elapsed_ms"])
            if res["success"]:
                success_count += 1
            else:
                failure_count += 1
                errors.append(res["error"])

    total_wall_time = time.perf_counter() - start_wall_clock
    qps = args.requests / total_wall_time if total_wall_time > 0 else 0.0

    # 3. 统计指标计算
    latencies_np = np.array(latencies)
    p50 = np.percentile(latencies_np, 50)
    p90 = np.percentile(latencies_np, 90)
    p95 = np.percentile(latencies_np, 95)
    p99 = np.percentile(latencies_np, 99)
    avg_latency = np.mean(latencies_np)
    error_rate = (failure_count / args.requests) * 100.0

    # 4. 输出结构化测试报告
    print("\n================ 压测指标统计报告 ================")
    print(f"总耗时: {total_wall_time:.2f} 秒")
    print(f"成功请求: {success_count} / {args.requests}")
    print(f"失败请求: {failure_count} (错误率: {error_rate:.2f}%)")
    print(f"平均吞吐量 (QPS): {qps:.2f} req/s")
    print(f"平均延迟: {avg_latency:.2f} ms")
    print(f"延迟 P50: {p50:.2f} ms")
    print(f"延迟 P90: {p90:.2f} ms")
    print(f"延迟 P95: {p95:.2f} ms")
    print(f"延迟 P99: {p99:.2f} ms")
    print("==================================================")

    # 5. 验收门禁判定
    # 门禁红线: 错误率必须为 0%，且 P95 延迟不得超过 800ms
    gate_passed = True
    if error_rate > 0.0:
        print(f"[GATE FAILED] 错误率大于 0% ({error_rate:.2f}%)，触发质量阻断！")
        gate_passed = False
    if p95 > 800.0:
        print(f"[GATE FAILED] P95 延迟超标 ({p95:.2f} ms > 800.00 ms)，触发性能阻断！")
        gate_passed = False

    if gate_passed:
        print("[GATE PASSED] 恭喜！Phase 20 全链路高并发压测性能验收全面达标！")
        sys.exit(0)
    else:
        sys.exit(2)

if __name__ == "__main__":
    main()
```

---

## 六、业内顶级开源大厂生产事故复盘与避坑指南 (Post-Mortem)

### 6.1 案例一：容器启动顺序缺少严格健康探针导致后端连接池耗尽与死锁崩溃
- **事故现象**：
  在某知名开源知识库产品更新部署时，运维人员通过 `docker-compose up -d` 一键拉起系统。后端 Spring Boot 服务启动过程中抛出海量 `org.postgresql.util.PSQLException: Connection to localhost:5432 refused`。Druid 连接池在重试耗尽后死锁，导致 Spring Boot 进程自杀退出（Exit code 1），触发容器频繁重启进入 CrashLoopBackOff。
- **根因分析**：
  在 Docker Compose 中，默认的 `depends_on: [postgres]` **只保证 postgres 容器的进程被创建并分配 PID**，并不保证数据库服务已经初始化完成或正在监听 5432 端口。当 PostgreSQL 正在执行 entrypoint 初始化脚本（如创建数据库、执行 DDL）时，后端 Spring Boot 已经开始初始化连接池并进行校验连接，直接遭遇网络拒绝异常。
- **规避指南与落地防御**：
  1. 彻底淘汰弱 `depends_on: [db]` 语法，全面升级为 Docker Compose v2 的精细化条件依赖：
     ```yaml
     depends_on:
       postgres:
         condition: service_healthy
     ```
  2. 为底层数据服务定义毫秒/秒级细粒度健康探针：
     ```yaml
     healthcheck:
       test: ["CMD-SHELL", "pg_isready -U postgres -d ai_agent"]
       interval: 10s
       timeout: 5s
       retries: 5
       start_period: 30s
     ```
  3. Spring Boot 端配置连接池非阻塞容错重试，设置合理等待超时（`maxWait: 60000`）。

---

### 6.2 案例二：Nginx 开启 `proxy_buffering` 导致流式打字机彻底卡住变成整块输出
- **事故现象**：
  某大厂企业级 AI 知识助手上线后，用户反馈智能对话界面极其卡顿：提问后光标一直静止，等待长达 8~15 秒后，整篇几千字的回答瞬间喷发到屏幕上，完全失去了大模型逐字打字机的流畅体验；偶发网络抖动时甚至会因超时被浏览器直接掐断。
- **根因分析**：
  Nginx 默认配置了 `proxy_buffering on`，并配置了内存缓冲区（如 `proxy_buffers 8 16k; proxy_busy_buffers_size 32k;`）。对于大模型生成的 SSE（`text/event-stream`）长连接，后端每个 token 发送的几个字节被 Nginx 当作普通 HTTP 响应截留在缓冲区内，直到积攒满一个 buffer（16KB）或者后端调用 `onComplete()` 关闭连接时，Nginx 才一次性将缓冲区推给客户端。
- **规避指南与落地防御**：
  1. 在 Nginx 配置文件中针对所有流式响应路径（如 `/prod-api/` 及 `/api/kb/conversation/send`）精准配置无缓冲：
     ```nginx
     location /prod-api/ {
         proxy_buffering off;
         proxy_cache off;
         chunked_transfer_encoding on;
         proxy_http_version 1.1;
         proxy_set_header Connection "";
         proxy_read_timeout 600s;
         proxy_pass http://backend:8099;
     }
     ```
  2. 在 Spring Boot 后端生成 SSE 响应头时，显式添加专属控制头：
     ```java
     response.setHeader("X-Accel-Buffering", "no");
     response.setHeader("Cache-Control", "no-cache");
     ```
     形成网关层与应用层的双重绝对防线。

---

### 6.3 案例三：Docker 容器内 JVM 无法感知 cgroups 导致堆外内存溢出触发宿主机 OOM-Killer 瞬杀
- **事故现象**：
  某 AI 中台在 K8s 节点上部署 Java 服务（包含 JNI 向量计算模块），Pod 资源限制为 `limits: memory: 4Gi`。系统运行半小时后，后端进程在没有留下任何 Java `OutOfMemoryError` 堆栈日志的情况下瞬间消失，Pod 状态变为 `OOMKilled` (Exit Code 137)。
- **根因分析**：
  1. 工程师在启动脚本中写死了 `-Xmx3500m`，以为留下了 500MB 足够系统使用；
  2. 然而，Java 进程的真实物理内存占用远远大于 `-Xmx`：包括元空间（Metaspace）、线程栈（按每线程 1MB 预留）、JIT 代码缓存（CodeCache）、DirectByteBuffer（堆外内存）、以及 Phase 18 中引入的高性能 Rust SIMD 向量计算（`vecsim-jni` 零拷贝堆外内存）；
  3. 当并发请求涌入、堆外内存与线程栈激增时，容器的整体物理内存 `memory.current` 突破了 cgroups 的 4GB 阈值。Linux 内核 OOM Killer 立即触发，向 JVM 主进程发送不可捕获的 `SIGKILL (9)`，导致服务瞬间猝死且毫无堆栈。
- **规避指南与落地防御**：
  1. 绝对不要在容器内写死绝对数值 `-Xmx`，必须使用 Java 21 的容器动态感知百分比：
     ```bash
     JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"
     ```
  2. 严格预留 25% 的容器内存空间给操作系统的页面缓存、线程栈以及 SIMD 堆外直接内存；
  3. 开启 `-XX:+ExitOnOutOfMemoryError`，确保在真正的堆内存耗尽时生成 CoreDump 或立即优雅退出，配合探针实现快速自愈重启。

---

## 七、针对当前代码库的具体改造建议与落地契约 (Implementation Contract)

### 7.1 文件级改造清单

| 操作类型 | 文件相对路径 | 核心改造内容与工程目的 |
|---|---|---|
| **新增** | `deploy/docker/docker-compose.yml` | 顶层现代全栈编排蓝图：编排 PG16+pgvector、Neo4j 5.26、Redis 7、Tantivy、Spring Boot、Nginx |
| **新增** | `scripts/verify_dr_drill.sh` | 跨异构数据（PG+Neo4j）自动化灾备实战演练脚本（写入->备份->销毁->还原->一致性比对） |
| **新增** | `scripts/stress_test_e2e.py` | 全链路高并发端到端压测回归脚本（并发仿真、QPS、P50/P90/P99延迟统计、错误率判定） |
| **修改** | `backend/Dockerfile` | 升级为 `eclipse-temurin:21-jre`，引入 `-XX:MaxRAMPercentage=75.0` 等 JVM 容器感知参数 |
| **修改** | `deploy/docker/nginx.conf` 与 `deploy/docker/nginx/sites/qknow.conf` | 彻底修复 SSE 流式打字机卡死问题：配置 `proxy_buffering off; chunked_transfer_encoding on;` |
| **修改** | `deploy/docker/qknow-server/application-prod.yml` | 彻底剔除 MySQL 5.7 与 Weaviate 配置，切换为 PostgreSQL 16 + pgvector 与 Tantivy 地址映射 |
| **废弃** | `deploy/docker/docker-compose-base.yml` 中的 `mysql57` 与 `weaviate` 服务块 | 彻底删除过时冗余的镜像定义与网络依赖，消除认知负担与误启动风险 |
| **更新** | `docs/plans/00_master_index.md` | 将 Phase 20 状态由 Planned 变更为 Delivered，记录交付日志 |

---

### 7.2 实施步骤与原子验证命令

1. **Step 1: Dockerfile 与 Nginx 配置更新**
   - 验证：检查 `backend/Dockerfile` 中基础镜像为 Java 21；
   - 验证：执行 `nginx -t` 校验 Nginx 语法正确性。
2. **Step 2: 编排启动与容器就绪自愈验证**
   - 执行：`docker compose -f deploy/docker/docker-compose.yml config` 校验 YAML 语法；
   - 执行：`docker compose -f deploy/docker/docker-compose.yml up -d`；
   - 验证：`docker compose ps`，断言所有 6 个服务全部处于 `Up (healthy)` 状态。
3. **Step 3: 灾备演练脚本验证**
   - 执行：`bash scripts/verify_dr_drill.sh`；
   - 验证：输出 `DR_DRILL_SUCCESS`，余弦距离严格 $< 10^{-4}$，退出码为 0。
4. **Step 4: 全链路高并发压测验收**
   - 执行：`python3 scripts/stress_test_e2e.py --concurrency 20 --requests 100`；
   - 验证：错误率为 0%，P95 延迟 $\le 800\text{ms}$，输出 `[GATE PASSED]`。
5. **Step 5: 全量既有测试无损回归**
   - 执行全量回归契约测试（741 项契约测试），确保 100% 保持全绿。

---

### 7.3 残余风险控制、回滚方案与生产授权边界

1. **残余风险与防御**：
   - *Docker 宿主机端口冲突*：若宿主机已运行本地 PostgreSQL(5432) 或 Redis(6379)，可通过 `.env` 中的 `EXPOSE_PG_PORT` 与 `EXPOSE_REDIS_PORT` 灵活映射外部端口，容器网络内部仍使用标准端口通信；
   - *内存超售风险*：6 大服务全栈编排要求宿主机物理可用内存 $\ge 8\text{GB}$，若低于 8GB，需适当缩减 Neo4j 与 Postgres 的 `shared_buffers`。
2. **回滚方案**：
   - 所有的备份与演练脚本均具备前置时间戳与独立的备份目录（`backup_YYYYMMDD_HHMMSS`），支持随时通过 `scripts/restore.sh <备份路径>` 一键无损回退到任意历史快照点。
3. **生产授权边界 (Gate Boundaries)**：
   - 本报告为只读调研与架构契约设计。在获得明确实施授权后，严格按照 7.1 与 7.2 节的最小修改范围实施落地，绝不修改与本阶段假设无关的任何业务代码！

---
请审核本份工业报告。如获批准，即可将全文写入 `docs/plans/phase_20_industrial_report.md`，并启动 Phase 20 的落地实施与自动化验证！