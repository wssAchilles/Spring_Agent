# Phase 20 实施计划：生产级容器编排一键交付、异构灾备实战演练与全链路高并发压测验证

> **阶段编号**：Phase 20（全路线图压轴收官阶段）  
> **前置依赖**：Phase 02 (可观测与CI), Phase 15 (指标与成本), Phase 16 (灾备与日志回放), Phase 18 (Rust原生加速与Tantivy) 全部圆满交付  
> **前置研报**：`docs/plans/phase_20_academic_report.md` & `docs/plans/phase_20_industrial_report.md`  
> **门禁状态**：**RESEARCH_GATE_PASSED**  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（V3/R1，流式 SSE 输出）；唯一向量模型为 **阿里千问 (Qwen) Embedding**（1536 维）；绝无本地大模型，彻底弃用 OpenAI/GPT API。

---

## 一、目标与背景

当前系统的核心算法、混合检索、原生加速与毛玻璃设计系统已在 Phase 01 ~ 19 全面成熟。但在最终交付与工程运维层面，现存编排仍残留旧版 MySQL 5.7 与 Weaviate，且缺乏流式反代优化与全自动灾备演练闭环。
本阶段旨在完成生产级全栈一键交付闭环：
1. 现代化生产容器编排：淘汰 MySQL/Weaviate，全面接入 PostgreSQL 16 + pgvector、Neo4j 5.26、Redis 7、Tantivy Server、Java 21 容器感知 Spring Boot 后端与 Nginx 前端；
2. 彻底解决 Nginx 流式 SSE 打字机卡死隐患（`proxy_buffering off`）与 JVM cgroups OOM-Killer 隐患（`-XX:MaxRAMPercentage=75.0`）；
3. 交付一键异构灾备实战演练脚本 `scripts/verify_dr_drill.sh`，实现 1536 维向量检索对账与数据恢复 100% 精度验证；
4. 交付全链路高并发压测套件 `scripts/stress_test_e2e.py`，量化验证系统在高负载下的稳定性与 P95 延迟。

---

## 二、详细实施步骤

### 步骤 1：部署配置全面升级与现代化编排
1. 重构 `deploy/docker/docker-compose-base.yml`：
   - 彻底移除废弃的 `mysql57` 与 `weaviate` 服务定义；
   - 新增 `postgres` 服务：采用官方镜像 `pgvector/pgvector:pg16`，挂载 `deploy/sql/postgresql` 初始化目录，配置内存参数与 `pg_isready` 健康探针；
   - 升级 `redis` 服务为 `redis:7-alpine`，配置 AOF 持久化与密码；
   - 完善 `neo4j` 服务：配置 APOC 插件、内存堆与 PageCache 限制，配置 `cypher-shell` 健康探针；
   - 新增 `tantivy` 服务：编排 Rust Tantivy-Server，挂载索引数据卷，配置端口 50051 与 HTTP 健康检查。
2. 重构 `deploy/docker/docker-compose-qknow.yml` 与 `deploy/docker/docker-compose.yml`：
   - 更新 `api` 容器环境变量，对接 PostgreSQL、Neo4j、Redis 与 Tantivy；
   - 升级依赖条件：使用 `condition: service_healthy` 严格依赖底层四大存储；
   - 注入 JVM 容器感知内存百分比参数。
3. 升级 Nginx 配置文件 `deploy/docker/nginx/nginx.conf` 与 `deploy/docker/nginx/sites/qknow.conf`：
   - 针对 `/prod-api/` 及流式端点，显式配置 `proxy_buffering off; proxy_cache off; chunked_transfer_encoding on;`；
   - 配置 `try_files $uri $uri/ /index.html;` 保证 SPA 前端路由正常。
4. 升级 `backend/Dockerfile`：
   - 升级为基于 `eclipse-temurin:21-jre-jammy` 的轻量级多阶段安全镜像，注入容器感知 JVM 参数。

### 步骤 2：编写异构灾备实战演练自动化脚本 `scripts/verify_dr_drill.sh`
- 阶段 1：自动生成包含 1536 维归一化测试向量的探针数据，注入 PostgreSQL 与 Neo4j；
- 阶段 2：调用 `scripts/backup.sh` 打包异构快照并验证 SHA-256 签名；
- 阶段 3：模拟灾难，物理销毁测试数据；
- 阶段 4：调用 `scripts/restore.sh` 运行三阶段解耦恢复（Pre-data -> Data -> Post-data 构建 HNSW）；
- 阶段 5：执行数据存在性断言与 1536 维 Cosine 向量检索对账（余弦距离 < 1e-4），清理现场并输出审计报告。
- 赋予执行权限 `chmod +x scripts/verify_dr_drill.sh`。

### 步骤 3：编写全链路高并发压测验收套件 `scripts/stress_test_e2e.py`
- 支持 `--url`、`--concurrency`、`--requests` 命令行参数；
- 基于线程池高并发执行查询与健康探测；
- 统计并输出 QPS、P50、P90、P95、P99 延迟与错误率；
- 设立自动化门禁判定：错误率必须为 0%，P95 延迟不得超过 800ms。
- 赋予执行权限 `chmod +x scripts/stress_test_e2e.py`。

### 步骤 4：全系统回归与全链路大圆满交付
1. 运行全量后端单测套件，确保 741+ 项测试全部 100% 绿灯；
2. 运行前端构建 `npm run build:prod` 确保 0 错误通过；
3. 更新 `docs/plans/00_master_index.md`，将 Phase 20 状态更新为 **Delivered**，宣告整个系统的长链路深度优化全线大功告成！

---

## 三、验证与测试计划

1. **配置语法校验**：
   - 校验 Docker Compose 配置语法与环境变量映射；
   - 校验 Nginx 配置文件语法；
2. **灾备演练与压测验证**：
   - 运行 `python3 scripts/stress_test_e2e.py --requests 20 --concurrency 5` 进行前置压测演练；
   - 运行灾备逻辑单元校验；
3. **全库回归验证**：
   - `mvn test`：741+ 项单测 100% 通过；
   - `npm run build:prod`：前端构建 100% 成功。
