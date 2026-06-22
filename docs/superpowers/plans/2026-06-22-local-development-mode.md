# Local Development Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run frontend, qknow-server, and Hermes locally with automatic rebuild/restart while retaining only PostgreSQL and Redis in Docker.

**Architecture:** Vite owns frontend HMR. Two local Java supervisors watch their complete Maven module source sets, run targeted reactor builds, and restart Spring Boot processes; DevTools remains enabled for fast starter-module restarts. Shared shell helpers own PID files, logs, port checks, and health waits under `.runtime/dev`.

**Tech Stack:** Bash, Vite 5, Maven 3.9, Spring Boot 3.5, Spring Boot DevTools, Docker Compose, PostgreSQL, Redis.

---

### Task 1: Restrict Docker Compose to infrastructure

**Files:**
- Modify: `docker-compose.yml`
- Create: `scripts/dev/test-compose.sh`

- [ ] **Step 1: Write the failing Compose boundary test**

```bash
#!/usr/bin/env bash
set -euo pipefail
services=$(docker compose config --services)
test "$services" = $'postgres\nredis'
```

- [ ] **Step 2: Run it and verify failure**

Run: `bash scripts/dev/test-compose.sh`
Expected: non-zero because `hermes`, `backend`, and `frontend` are present.

- [ ] **Step 3: Remove application service blocks**

Keep only `postgres`, `redis`, and their named volumes in `docker-compose.yml`. Do not delete or rename `pg_data` or `redis_data`.

- [ ] **Step 4: Verify and commit**

Run: `bash scripts/dev/test-compose.sh`
Expected: exit 0 and only `postgres` and `redis`.

```bash
git add docker-compose.yml scripts/dev/test-compose.sh
git commit -m "chore(dev): 精简开发环境容器" -m $'- 仅保留 PostgreSQL 与 Redis 基础设施\n- 增加 Compose 服务边界检查'
```

### Task 2: Enable Spring Boot development restart support

**Files:**
- Modify: `backend/qknow-server/pom.xml`
- Modify: `backend/qknow-hermes/qknow-hermes-starter/pom.xml`
- Create: `scripts/dev/test-devtools.sh`

- [ ] **Step 1: Write the failing dependency test**

```bash
#!/usr/bin/env bash
set -euo pipefail
for pom in backend/qknow-server/pom.xml backend/qknow-hermes/qknow-hermes-starter/pom.xml; do
  grep -q '<artifactId>spring-boot-devtools</artifactId>' "$pom"
done
```

- [ ] **Step 2: Run it and verify failure**

Run: `bash scripts/dev/test-devtools.sh`
Expected: non-zero because DevTools is commented out or absent.

- [ ] **Step 3: Add optional runtime dependencies**

Add to both starter POMs:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

Remove the old commented DevTools block from `qknow-server/pom.xml`. Do not alter production profiles.

- [ ] **Step 4: Verify and commit**

Run: `bash scripts/dev/test-devtools.sh`
Expected: exit 0.

Run: `rtk mvn -f backend/pom.xml -pl qknow-server,qknow-hermes/qknow-hermes-starter -am compile -DskipTests`
Expected: `BUILD SUCCESS`.

```bash
git add backend/qknow-server/pom.xml backend/qknow-hermes/qknow-hermes-starter/pom.xml scripts/dev/test-devtools.sh
git commit -m "feat(dev): 启用后端自动重启" -m $'- 为主后端与 Hermes 启用 DevTools\n- 增加开发依赖检查'
```

### Task 3: Add shared process and Java watch helpers

**Files:**
- Modify: `.gitignore`
- Create: `scripts/dev/common.sh`
- Create: `scripts/dev/watch-java.sh`
- Create: `scripts/dev/test-common.sh`

- [ ] **Step 1: Write failing helper tests**

```bash
#!/usr/bin/env bash
set -euo pipefail
source scripts/dev/common.sh
test "$RUNTIME_DIR" = "$PROJECT_DIR/.runtime/dev"
test "$(service_pid_file backend)" = "$PROJECT_DIR/.runtime/dev/backend.pid"
test "$(service_log_file hermes)" = "$PROJECT_DIR/.runtime/dev/hermes.log"
```

- [ ] **Step 2: Run it and verify failure**

Run: `bash scripts/dev/test-common.sh`
Expected: non-zero because `common.sh` does not exist.

- [ ] **Step 3: Implement focused shared helpers**

`common.sh` defines `PROJECT_DIR`, `RUNTIME_DIR`, `service_pid_file`, `service_log_file`, `is_running`, `assert_port_available`, `wait_for_http`, and `stop_service`. Port checks must refuse to kill unknown processes. Add `/.runtime/` to `.gitignore`.

- [ ] **Step 4: Implement the Java supervisor**

`watch-java.sh` accepts:

```text
watch-java.sh <service> <watch-root> <maven-project-list> <jar-path> [environment assignments...]
```

It hashes modification timestamps for Java/resources/POM files once per second, runs a targeted reactor package, retains the previous healthy JVM on compilation failure, restarts only its recorded child after success, forwards TERM/INT, and removes PID files on exit.

- [ ] **Step 5: Verify and commit**

Run: `bash scripts/dev/test-common.sh && bash -n scripts/dev/common.sh scripts/dev/watch-java.sh`
Expected: exit 0.

```bash
git add .gitignore scripts/dev/common.sh scripts/dev/watch-java.sh scripts/dev/test-common.sh
git commit -m "feat(dev): 增加本机服务监督器" -m $'- 管理 PID、日志和端口检查\n- 自动编译并重启 Java 服务'
```

### Task 4: Replace development lifecycle commands

**Files:**
- Modify: `scripts/start.sh`
- Create: `scripts/stop.sh`
- Create: `scripts/status.sh`
- Create: `scripts/dev/test-scripts.sh`

- [ ] **Step 1: Write the failing command contract test**

```bash
#!/usr/bin/env bash
set -euo pipefail
bash -n scripts/start.sh scripts/stop.sh scripts/status.sh
grep -q 'npm run dev' scripts/start.sh
grep -q 'watch-java.sh backend' scripts/start.sh
grep -q 'watch-java.sh hermes' scripts/start.sh
! grep -q 'java -jar qknow-server/target' scripts/start.sh
```

- [ ] **Step 2: Run it and verify failure**

Run: `bash scripts/dev/test-scripts.sh`
Expected: non-zero because stop/status scripts and supervisors do not exist.

- [ ] **Step 3: Rewrite `scripts/start.sh`**

The script starts only PostgreSQL/Redis through Compose, refuses to kill unknown port owners, starts the backend and Hermes supervisors with localhost configuration, starts `npm run dev`, records all PIDs, waits for ports 80/8099/9090, and prints log paths without credentials.

- [ ] **Step 4: Implement stop and status commands**

`scripts/stop.sh` stops only PID-file-owned application processes. `scripts/status.sh` reports local application PIDs/ports plus PostgreSQL/Redis state. Neither deletes volumes.

- [ ] **Step 5: Verify and commit**

Run: `bash scripts/dev/test-scripts.sh`
Expected: exit 0.

```bash
git add scripts/start.sh scripts/stop.sh scripts/status.sh scripts/dev/test-scripts.sh
git commit -m "feat(dev): 切换本机开发生命周期" -m $'- 一键启动前端、主后端与 Hermes\n- 提供安全停止和状态检查'
```

### Task 5: Migrate active environment and verify hot restart

**Files:**
- No persistent source changes expected.

- [ ] **Step 1: Preserve infrastructure and remove application containers**

```bash
docker compose ps postgres redis
docker rm -f agent-frontend agent-backend qknow-hermes 2>/dev/null || true
docker compose up -d postgres redis
```

Expected: PostgreSQL and Redis remain healthy with existing volumes; no application containers remain.

- [ ] **Step 2: Start local development services**

Run: `bash scripts/start.sh`
Expected: Vite on 80, qknow-server on 8099, Hermes on 9090, all owned by local processes.

- [ ] **Step 3: Verify communication**

```bash
curl -fsS http://localhost/ >/dev/null
curl -fsS http://localhost:8099/captchaImage | grep -q '"captchaEnabled":false'
lsof -nP -iTCP:9090 -sTCP:LISTEN | grep -q java
```

Expected: all commands exit 0.

- [ ] **Step 4: Verify frontend HMR**

Temporarily add an HTML comment to `frontend/src/views/system/login.vue`, confirm the Vite log contains `hmr update /src/views/system/login.vue`, then remove exactly that comment with `apply_patch`.

- [ ] **Step 5: Verify Java automatic restart**

Touch the two application entry files without changing content:

```bash
touch backend/qknow-server/src/main/java/tech/qiantong/qknow/server/QKnowApplication.java
touch backend/qknow-hermes/qknow-hermes-starter/src/main/java/tech/qiantong/qknow/hermes/HermesApplication.java
```

Expected: supervisors detect changes, build, restart their child JVMs, and restore ports 8099/9090.

- [ ] **Step 6: Run final verification**

```bash
bash scripts/dev/test-compose.sh
bash scripts/dev/test-devtools.sh
bash scripts/dev/test-common.sh
bash scripts/dev/test-scripts.sh
rtk npm --prefix frontend run build:prod
rtk mvn -f backend/pom.xml -pl qknow-server,qknow-hermes/qknow-hermes-starter -am package -DskipTests
rtk git diff --check
bash scripts/status.sh
```

Expected: all checks pass; status reports local application processes and healthy infrastructure containers.

- [ ] **Step 7: Commit only necessary verification adjustments**

Do not create an empty commit. Any required adjustment must use the required Chinese Conventional Commit format.

