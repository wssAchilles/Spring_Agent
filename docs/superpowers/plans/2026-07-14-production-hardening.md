# Production Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Every behavior change follows superpowers:test-driven-development and every completion claim follows superpowers:verification-before-completion.

**Goal:** 修复已确认的数据库契约、文档同步、Agent/RAG、密钥、取消传播、部署和质量门禁问题，同时保证现有 PostgreSQL 文档分段、embedding、vector_store 和 Lucene 数据不被修改。

**Architecture:** 本次按九个独立任务顺序实施。数据库结构变更只修改初始化脚本并新增版本迁移文件；迁移仅在 Testcontainers 临时 PostgreSQL 验证，绝不对本机 ai_agent 执行。运行时修复沿现有 Spring、MyBatis、Reactor、gRPC 和 Vue 边界完成，不引入新的平台层。

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, Reactor, gRPC, PostgreSQL/PgVector, Redis, Vue 3, fetch-event-source, Vitest, Docker Compose, GitHub Actions.

---

## Safety Invariants

- 禁止对本机 ai_agent 执行 ALTER、UPDATE、DELETE、TRUNCATE、REINDEX、迁移、重切分或向量重建。
- 禁止修改、删除或重建 kmc_document_segment、vector_store、embedding 数据。
- .env 只能由本机命令 source；任何日志、测试快照、代码和提交都不得包含秘密值。
- 数据库行为测试只使用 mock 或 Testcontainers 临时数据库。
- deploy/compose.yml 只执行 config 验证，不执行 up。
- 每个任务完成后先做规格审查，再做代码质量审查。

### Task 1: Restore The Existing Test Baseline

**Files:**
- Modify: backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImpl.java
- Modify: backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/SleepTimeMemoryAgent.java
- Test: backend/tests/src/test/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImplTest.java
- Test: backend/tests/src/test/java/tech/qiantong/qknow/hermes/memory/SleepTimeMemoryAgentTest.java

- [x] Confirm RED with:

      rtk mvn -pl tests -am test -DskipITs

  Expected: 543 tests, 4 failures, 3 errors, 4 skipped.

- [x] Keep EmbeddingServiceImplTest expecting ServiceException for an unsupported platform and assert the message contains the platform name.
- [x] Change the unsupported platform branch to throw ServiceException instead of silently falling back to OpenAI.
- [x] Initialize SleepTimeMemoryAgent.enabled to true so direct construction matches the Spring property default.
- [x] Run the two focused test classes and then the full Maven command. Expected: 0 failures and 0 errors.

### Task 2: Fix PostgreSQL Contracts Without Touching The Live Database

**Files:**
- Modify: deploy/sql/postgresql/01-schema.sql
- Create: deploy/sql/postgresql/migrations/V015__runtime_contract_fixes.sql
- Create: backend/tests/src/test/java/tech/qiantong/qknow/schema/PostgresSchemaContractTest.java

- [ ] Write PostgresSchemaContractTest assertions that conversation/chat_message valid_flag are SMALLINT DEFAULT 1 and del_flag are SMALLINT DEFAULT 0.
- [ ] Assert KbConversationDO and KbChatMessageDO keep Integer fields and @TableLogic.
- [ ] Assert the migration contains no TRUNCATE, REINDEX, kmc_document_segment, vector_store, or embedding references.
- [ ] Run the focused test and confirm it fails against the current BOOLEAN DDL.
- [ ] Change only the four new-schema columns to SMALLINT with 0/1 CHECK constraints.
- [ ] Add an idempotent migration that converts only conversation/chat_message flags and DAG checkpoint timestamps, and adds kmc_document.sync_version.
- [ ] Validate the migration against a disposable PostgreSQL Testcontainer. Do not source .env and do not connect to ai_agent.

### Task 3: Remove The Broken Embedded Redis Fallback And Fix DAG Checkpoints

**Files:**
- Delete: backend/qknow-server/src/main/java/tech/qiantong/qknow/server/config/EmbeddedRedisConfig.java
- Modify: backend/qknow-server/pom.xml
- Modify: backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/dag/DagCheckpointManager.java
- Test: backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/dag/DagCheckpointManagerTest.java
- Modify: README.md

- [ ] Add RED assertions that checkpoint DDL does not use BIGINT for created_at/updated_at and UPSERT does not bind epoch millis.
- [ ] Change checkpoint columns and UPSERT to CURRENT_TIMESTAMP-compatible timestamp values.
- [ ] Delete EmbeddedRedisConfig and its dependency because the project now requires the real local Redis at 127.0.0.1:6379.
- [ ] Add a README sentence stating that startup fails fast when local Redis is unavailable; no embedded server is started.
- [ ] Run the focused test, qknow-server compilation, and scripts/dev/test-compose.sh.

### Task 4: Make Document Synchronization Claimable And SQL-Atomic

**Files:**
- Modify: backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/impl/KmcSyncServiceImpl.java
- Modify: backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/dal/dataobject/document/KmcDocumentDO.java
- Modify: backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/dal/mapper/document/KmcDocumentMapper.java
- Modify: backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/dal/dataobject/document/enums/DocumentSyncStatus.java
- Create: backend/tests/src/test/java/tech/qiantong/qknow/module/kmc/service/sync/KmcSyncServiceTransactionTest.java
- Create: backend/tests/src/test/java/tech/qiantong/qknow/module/kmc/service/sync/KmcDocumentClaimPostgresTest.java

- [ ] RED: force segment insertion to fail after delete and assert the original SQL segments remain.
- [ ] RED: run two Testcontainer transactions and assert FOR UPDATE SKIP LOCKED returns a document to only one worker.
- [ ] RED: assert a stale sync_version cannot mark a newer claim successful.
- [ ] Add PROCESSING status and syncVersion.
- [ ] Claim documents with one PostgreSQL CTE using FOR UPDATE SKIP LOCKED and increment sync_version.
- [ ] Replace self-invoked @Transactional with TransactionTemplate around SQL segment and metadata replacement.
- [ ] Re-throw metadata failures so SQL replacement rolls back.
- [ ] Update success/error status using id + PROCESSING + sync_version CAS.
- [ ] Keep Lucene and Vector operations outside the SQL transaction and idempotent by document ID; only mark SUCCESS after all stores complete.
- [ ] Do not add any test that calls the live parsing, embedding, vectorization, or reindex path.

### Task 5: Propagate Identity And Make Cache/RAG Fail Closed

**Files:**
- Modify: backend/qknow-hermes/qknow-hermes-proto/src/main/proto/hermes.proto
- Modify: backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/impl/KbAgentConfigServiceImpl.java
- Modify: backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java
- Modify: backend/qknow-module-kmc/qknow-module-kmc-api/src/main/java/tech/qiantong/qknow/module/kmc/api/knowledgeBase/dto/SemanticCacheLookupReqDTO.java
- Modify: backend/qknow-module-kmc/qknow-module-kmc-api/src/main/java/tech/qiantong/qknow/module/kmc/api/knowledgeBase/dto/SemanticCacheSaveReqDTO.java
- Modify: backend/qknow-module-kmc/qknow-module-kmc-api/src/main/java/tech/qiantong/qknow/module/kmc/api/service/IKmcApiService.java
- Modify: backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/api/KmcApiServiceImpl.java
- Modify: backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/SemanticCacheService.java
- Modify: backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/rag/RagRetrievalService.java
- Modify: backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/bo/KnowledgeNodeBO.java
- Test: backend/tests/src/test/java/tech/qiantong/qknow/module/kb/service/agent/impl/KbAgentConfigServiceCacheTest.java
- Test: backend/tests/src/test/java/tech/qiantong/qknow/rag/SemanticCacheServiceTest.java
- Test: backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/rag/RagRetrievalServiceTest.java

- [ ] Add userId and conversationId to ChatRequest using new protobuf field numbers; preserve existing fields.
- [ ] RED: serialize and deserialize request identity and assert no field is lost.
- [ ] RED: assert memory uses workspace+bot scope, userId user, and conversationId session; incomplete identity produces no memory writes.
- [ ] Set bot/workspace/user/conversation/request fields in the control-plane request.
- [ ] Perform knowledge ACL checks before semantic cache lookup. Permission failure or permission-service failure must stop cache and Hermes calls.
- [ ] Include userId and an ACL version/fingerprint in cache lookup/save DTOs and keys; old cache entries must miss.
- [ ] Add RagRetrievalException with TIMEOUT, PERMISSION_DENIED, DEPENDENCY_UNAVAILABLE and EMPTY_RESULT kinds.
- [ ] Remove catch-all empty-list fallbacks. Only EMPTY_RESULT may continue without context; permission errors never degrade.
- [ ] Run protobuf generation and all three focused test classes.

### Task 6: Protect Model API Keys And Health Endpoints

**Files:**
- Modify: backend/qknow-module-ai/qknow-module-ai-biz/src/main/java/tech/qiantong/qknow/module/ai/api/modelMarket/AiModelApiServiceImpl.java
- Modify: backend/qknow-module-ai/qknow-module-ai-biz/src/main/java/tech/qiantong/qknow/module/ai/service/modelMarket/impl/AiApiKeyServiceImpl.java
- Modify: backend/qknow-module-ai/qknow-module-ai-biz/src/main/java/tech/qiantong/qknow/module/ai/dal/dataobject/modelMarket/AiApiKeyDO.java
- Modify: backend/qknow-module-ai/qknow-module-ai-api/src/main/java/tech/qiantong/qknow/module/ai/api/dto/AiApiKeyRespDTO.java
- Modify: backend/qknow-module-ai/qknow-module-ai-biz/src/main/java/tech/qiantong/qknow/module/ai/controller/admin/modelMarket/vo/AiApiKeyRespVO.java
- Modify: backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/impl/KbAgentConfigServiceImpl.java
- Modify: backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/config/ChatModelFactory.java
- Modify: backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/controller/admin/HealthCheckController.java
- Modify: .env.example
- Create: backend/tests/src/test/java/tech/qiantong/qknow/ai/service/AiApiKeySecurityTest.java
- Test: backend/tests/src/test/java/tech/qiantong/qknow/hermes/config/ChatModelFactoryTest.java
- Create: backend/tests/src/test/java/tech/qiantong/qknow/kb/controller/HealthCheckControllerTest.java

- [ ] RED: list/detail DTO JSON must not contain a sentinel API key.
- [ ] RED: ChatRequest model_config.api_key must be empty.
- [ ] RED: ChatModelFactory resolves provider keys from Spring Environment and fails closed when no key exists.
- [ ] Store only env:VARIABLE_NAME references in ai_api_key.api_key; reject new raw secret values at the service boundary.
- [ ] Add a migration that converts known provider rows to env references without reading or writing embedding-related tables. Validate it only in Testcontainers.
- [ ] Mask all API key responses and stop placing the key in ChatRequest.
- [ ] Resolve Hermes provider credentials from environment variables such as HERMES_OPENAI_API_KEY and TONGYI_API_KEY. Never read .env directly in Java.
- [ ] Add the supported provider-to-environment mapping to .env.example without adding secret values.
- [ ] Keep only a minimal public liveness response. Add existing permission annotations to detailed system and trace endpoints.
- [ ] Whitelist trace response fields and remove raw prompts, answers, Authorization, credentials, JDBC passwords and .env reads.
- [ ] Run focused service/controller/factory tests.

### Task 7: Propagate Cancellation From Vue To Hermes

**Files:**
- Modify: frontend/src/api/kb/conversation/index.js
- Modify: frontend/src/views/kb/agent/index.vue
- Modify: frontend/src/views/kb/agent/components/ChatInput.vue
- Create: frontend/src/views/kb/agent/conversationStreams.js
- Modify: backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/controller/admin/conversation/KbConversationController.java
- Modify: backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/impl/KbAgentConfigServiceImpl.java
- Modify: backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/HermesGrpcClient.java
- Modify: backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/grpc/HermesGrpcService.java
- Modify: backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java
- Create: backend/tests/src/test/java/tech/qiantong/qknow/kb/conversation/ConversationCancellationTest.java
- Create: backend/tests/src/test/java/tech/qiantong/qknow/hermes/grpc/HermesCancellationTest.java
- Test: frontend API/helper/component tests and backend cancellation tests

- [ ] Add Vitest tests proving the caller signal is passed to fetchEventSource and stop/stopAll abort the correct controllers.
- [ ] Change sendMessageStream to accept a caller-owned AbortSignal and remove its private AbortController.
- [ ] Store one controller per conversation; abort on stop, delete, route leave, deactivation and unmount.
- [ ] Add a visible stop command to ChatInput while streaming.
- [ ] Use ClientResponseObserver and ServerCallStreamObserver cancellation callbacks to cancel/dispose downstream subscriptions.
- [ ] Prevent post-cancel message persistence and semantic-cache writes.
- [ ] Persist one completed assistant message rather than updating the database for every token.
- [ ] Run focused Vitest and Maven cancellation tests.

### Task 8: Complete DAG Resume/Approval Control Plane

**Files:**
- Modify: hermes.proto with additive resume/approval RPC messages
- Modify: backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/grpc/HermesGrpcService.java
- Modify: backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/dag/DagCheckpointManager.java
- Modify: backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/node/ApprovalNodeExecutor.java
- Modify: backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/bo/SuspendNodeBO.java
- Modify: backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/controller/admin/flow/KbFlowController.java
- Modify: backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/controller/admin/ApprovalProxyController.java
- Test: backend/tests/src/test/java/tech/qiantong/qknow/integration/DagE2ETest.java

- [ ] RED: execute a flow that reaches SUSPEND and assert a checkpoint is persisted.
- [ ] RED: resume with the wrong flow/request identity returns PERMISSION_DENIED.
- [ ] RED: approve and resume with matching identity continues exactly once from the suspended node.
- [ ] Add additive gRPC resume/approval RPCs and preserve old ExecuteFlow compatibility.
- [ ] Validate workspace/bot/user/flow identity before loading a checkpoint.
- [ ] Make resume idempotent using checkpoint state/version CAS.
- [ ] Run focused DAG tests only; no live database.

### Task 9: Add A Current-Source Deployment And Quality Gates

**Files:**
- Create: deploy/compose.yml
- Modify: backend/Dockerfile
- Modify: backend/qknow-hermes/qknow-hermes-starter/docker/Dockerfile
- Modify: frontend/Dockerfile
- Create: frontend/nginx.conf
- Create: frontend/.env.production
- Modify: frontend/package.json
- Create: frontend Vitest configuration
- Create: .github/workflows/ci.yml
- Modify: README.md
- Modify: backend/qknow-hermes/qknow-hermes-starter/src/main/resources/application.yml

- [ ] Keep root docker-compose.yml as local Neo4j only.
- [ ] Add a separate source-build compose with PostgreSQL/PgVector, Redis, Neo4j, backend, Hermes and frontend; do not run it against the local ai_agent database.
- [ ] Mark deploy/docker as legacy in README and remove it from current-source startup commands.
- [ ] Configure Nginx SSE buffering off, X-Accel-Buffering no, long read timeout, and WebSocket Upgrade headers.
- [ ] Add Vitest, Vue Test Utils and high-value cancellation tests to npm test.
- [ ] Add CI jobs for Java 17 Maven tests, npm ci/test/build, shell checks, Compose config and Nginx config.
- [ ] Correct README so Judge, Reflection, Plan-and-Solve, Eval and LangFuse are documented as default-off.
- [ ] Correct the RAG eval property prefix and guard automatic evaluation with its enabled flag.
- [ ] Validate with:

      rtk mvn -pl tests -am test -DskipITs
      npm --prefix frontend ci
      npm --prefix frontend test
      npm --prefix frontend run build:prod
      bash scripts/dev/test-compose.sh
      docker compose -f deploy/compose.yml config
      git diff --check

  Expected: all commands exit 0. The deploy compose command is config-only; never execute up.

## Final Review

- [ ] Spec reviewer checks every annotated issue against the diff.
- [ ] Code quality reviewer checks security boundaries, backwards compatibility and accidental live-data access.
- [ ] Confirm git diff contains no .env, secrets, database dumps, generated embeddings or index files.
- [ ] Run the complete verification list fresh and report exact counts.
