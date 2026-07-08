# RAG Algorithm Optimization Roadmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve the current RAG retrieval quality and latency with evidence-backed changes, without adding speculative C++/Faiss/Qdrant infrastructure before the existing Java/Postgres/Rust path is measured and tuned.

**Architecture:** Keep the current Spring Boot KMC RAG v2 orchestration. Add a real retrieval evaluation gate first, then tune pgvector/SQL behavior, then connect already-existing query transform capabilities as bounded retrieval branches, and only then invest in GraphRAG summaries or ColBERT token-index improvements.

**Tech Stack:** Java/Spring Boot, PostgreSQL + pgvector + pg_trgm, Neo4j, Redis, Vue 3, Rust JNI for Jieba/VecSim/ColBERT, Chrome DevTools MCP for E2E verification.

---

## Evidence Summary

- `QueryTransformService.expandQueries()` and `generateHypotheticalDocument()` already exist, but the main RAG retrieval path still consumes a single query.
- `RagRetrievalService.retrieveOnce()` concurrently calls vector, keyword, metadata, and graph retrievers, then fuses with RRF and reranks.
- `CandidateFusionService` uses RRF but currently filters weak paths by top-1 raw score, even though vector, keyword, metadata, and graph scores are not comparable.
- `VectorRetriever` already invokes VecSim after pgvector recall, but pays extra cost for query embedding, `vector_store.embedding::text`, parsing, flattening, and JNI copying.
- `ColbertScorer` has batch native MaxSim, but without real token embeddings it falls back to hash vectors, which is not equivalent to ColBERT/ColBERTv2.
- `GraphRagRetriever` has bounded PPR support, but graph quality risks remain around seed noise, broad node-to-document expansion, and no community/summary retrieval.
- Current golden/RAGAS/RAGChecker tests do not yet prove live retrieval quality; several are smoke or mock-based.

---

### Task 1: Live Retrieval Evaluation Gate

**Files:**
- Modify: `backend/tests/src/test/resources/rag-golden-dataset-v2.jsonl`
- Modify: `backend/tests/src/test/java/tech/qiantong/qknow/rag/RagGoldenTest.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/rag/RetrievalMetrics.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/rag/RagGoldenCase.java`

- [ ] **Step 1: Extend the golden dataset schema**

Add fields to each JSONL case:

```json
{
  "id": "rag-smoke-001",
  "kbId": 8,
  "query": "系统的主要内容是什么？",
  "scenario": "zh_keyword_vector",
  "topK": 10,
  "relevant": [
    {"documentNamePattern": ".*", "segmentId": null, "grade": 2}
  ],
  "negativeDocumentNamePatterns": [],
  "expectFallback": false,
  "maxLatencyMsP95": 5000,
  "tags": ["smoke", "zh"]
}
```

- [ ] **Step 2: Add retrieval metrics helper**

Implement `RetrievalMetrics` with `recallAtK`, `mrrAtK`, and `ndcgAtK` over retrieved `RetrievalResult` sources and expected relevant segment/document patterns.

- [ ] **Step 3: Replace empty golden assertions**

In `RagGoldenTest`, call the real RAG retrieval path for each live-enabled case and assert:

```java
assertThat(metrics.recallAt10()).isGreaterThanOrEqualTo(caseSpec.minRecallAt10());
assertThat(metrics.mrrAt10()).isGreaterThanOrEqualTo(caseSpec.minMrrAt10());
assertThat(result.getDebugInfo()).containsKey("firstVectorResultCount");
assertThat(result.getDebugInfo()).containsKey("firstKeywordResultCount");
```

- [ ] **Step 4: Run focused test**

Run:

```bash
cd /Users/achilles/Documents/许子祺/Agent/backend
rtk mvn test -pl tests -am -Dtest=RagGoldenTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: non-empty assertions execute; no empty green test.

---

### Task 2: pgvector Runtime and Query Plan Control

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/resources/application-kmc-dev.yml`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/VectorRetriever.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/SemanticCacheService.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/module/kmc/service/rag/PgVectorRuntimeSettingsTest.java`

- [ ] **Step 1: Add explicit runtime settings**

Add config keys:

```yaml
qknow:
  rag:
    pgvector:
      hnsw-ef-search: 100
      hnsw-iterative-scan: relaxed_order
      hnsw-max-scan-tuples: 20000
```

- [ ] **Step 2: Apply settings per retrieval connection**

Before vector/semantic-cache ANN queries, execute:

```sql
SET LOCAL hnsw.ef_search = ?;
SET LOCAL hnsw.iterative_scan = ?;
SET LOCAL hnsw.max_scan_tuples = ?;
```

Only use `SET LOCAL` inside an actual transaction. If the existing `VectorStore` call cannot share the transaction reliably, prefer role/database-level deployment settings and record the active values in `recallDebug`.

- [ ] **Step 3: Add live Postgres verification**

From `.env`, run:

```bash
SHOW hnsw.ef_search;
SHOW hnsw.iterative_scan;
EXPLAIN (ANALYZE, BUFFERS) <actual vector query with knowledgeBaseId/day filter>;
```

Do not print `.env` secrets.

- [ ] **Step 4: Validate**

Run:

```bash
cd /Users/achilles/Documents/许子祺/Agent/backend
rtk mvn test -pl tests -am -Dtest=VectorRetrieverTest,SemanticCacheServiceTest,PgVectorRuntimeSettingsTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: settings are visible in debug/test harness; retrieval still returns stable topK.

---

### Task 3: Controlled Multi-Query and HyDE Branches

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryTransformService.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalService.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/CandidateFusionService.java`
- Test: `backend/tests/src/test/java/tech/qiantong/qknow/kmc/rag/QueryTransformServiceTest.java`
- Test: `backend/tests/src/test/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalServiceDebugTest.java`

- [ ] **Step 1: Keep original query authoritative**

Do not replace the original query for keyword, metadata, or graph retrieval. HyDE may only create an additional vector branch named `vector_hyde`.

- [ ] **Step 2: Add bounded variants**

For `COMPLEX` route or CRAG low-confidence cases, call:

```java
List<String> variants = queryTransformService.expandQueries(query, 2);
String hyde = queryTransformService.generateHypotheticalDocument(query);
```

Limits:
- Max 2 multi-query variants.
- Max 1 HyDE branch.
- Never run for numeric or exact ID/document-name queries.

- [ ] **Step 3: Fuse branches with RRF**

Pass branch names into fusion:

```java
List.of("vector", "vector_hyde", "keyword", "metadata", "graph")
```

Add debug fields:

```json
{
  "queryEnhance": {
    "variants": ["..."],
    "hydeEnabled": true,
    "hydeUsedFor": "vector_only"
  }
}
```

- [ ] **Step 4: Test no regression for precise queries**

Add tests:
- numeric/day query does not use HyDE.
- document-name query keeps keyword/graph original query.
- complex query records variants and extra vector branch.

Run:

```bash
rtk mvn test -pl tests -am -Dtest=QueryTransformServiceTest,RagRetrievalServiceDebugTest,CandidateFusionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

---

### Task 4: RRF Weak Path Calibration

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/CandidateFusionService.java`
- Test: `backend/tests/src/test/java/tech/qiantong/qknow/module/kmc/service/rag/CandidateFusionServiceTest.java`

- [ ] **Step 1: Stop hard-filtering by raw top-1 score by default**

Change weak-path behavior from hard exclusion to debug-only marking unless a feature flag explicitly enables exclusion.

- [ ] **Step 2: Add debug evidence**

Emit per-path:

```json
{
  "path": "keyword",
  "topScore": 0.22,
  "normalizedScore": 0.22,
  "weak": true,
  "excluded": false
}
```

- [ ] **Step 3: Test unique relevant weak path**

Add a test where keyword has low raw score but contains the only relevant segment. Expected: RRF still includes it when hard exclusion is disabled.

Run:

```bash
rtk mvn test -pl tests -am -Dtest=CandidateFusionServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

---

### Task 5: GraphRAG Bounded Neighborhood and Entity Denoise

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/GraphRagRetriever.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryEntityExtractionService.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/GraphRagProperties.java`
- Test: `backend/tests/src/test/java/tech/qiantong/qknow/module/kmc/service/rag/GraphRagRetrieverTest.java`

- [ ] **Step 1: Add per-seed/per-neighbor degree caps**

Add config:

```yaml
qknow:
  rag:
    graph:
      max-seeds: 8
      max-edges-per-seed: 100
      max-edges-per-neighbor: 50
```

- [ ] **Step 2: Make edge reads deterministic**

Add deterministic ordering to edge queries:

```sql
ORDER BY weight DESC NULLS LAST, updated_at DESC NULLS LAST, id ASC
```

- [ ] **Step 3: Canonicalize and filter entities**

Normalize extracted entities:
- trim
- lower-case lookup against `kg_node(lower(label))`
- remove stopwords and generic tokens
- cap entity count before PPR

- [ ] **Step 4: Test**

Run:

```bash
rtk mvn test -pl tests -am -Dtest=GraphRagRetrieverTest,GraphRagSyncServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PPR queries use bounded seed neighborhoods; fallback telemetry remains present.

---

### Task 6: Cache Versioning and Exact Cache Eviction

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagCacheService.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/SemanticCacheService.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/knowledgeBase/impl/KmcKnowledgeBaseServiceImpl.java`
- Test: `backend/tests/src/test/java/tech/qiantong/qknow/module/kmc/service/rag/RagCacheServiceTest.java`
- Test: `backend/tests/src/test/java/tech/qiantong/qknow/rag/SemanticCacheServiceTest.java`

- [ ] **Step 1: Add KB content/config version dimensions**

Include in cache key dimensions:

```java
knowledgeBaseUpdatedAt
embeddingModelName
graphConfigVersion
retrievalSchemaVersion
```

- [ ] **Step 2: Clear local exact cache on semantic eviction**

When `evictByKnowledgeBase()` deletes DB rows, also remove matching local exact cache keys.

- [ ] **Step 3: Test stale prevention**

Test:
- same query before/after KB version change uses different RAG cache key.
- semantic cache local exact hit is removed by eviction.

Run:

```bash
rtk mvn test -pl tests -am -Dtest=RagCacheServiceTest,SemanticCacheServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

---

### Task 7: ColBERT Quality Guardrail

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertScorer.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRerankService.java`
- Test: `backend/tests/src/test/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertScorerTest.java`
- Test: `backend/tests/src/test/java/tech/qiantong/qknow/rag/RagRerankServiceTest.java`

- [ ] **Step 1: Do not let hash fallback masquerade as ColBERT**

When real token embedding config is missing, preserve input order and set debug/fallback reason:

```text
colbert_disabled_no_embedding_config
```

- [ ] **Step 2: Keep native MaxSim only for real token embeddings**

Continue using `ColbertNative.safeMaxsimBatch()` only when token vectors come from the configured embedding model or an offline token-vector index.

- [ ] **Step 3: Test**

Run:

```bash
rtk mvn test -pl tests -am -Dtest=ColbertScorerTest,RagRerankServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: missing embedding config does not reorder results as pseudo-ColBERT.

---

### Task 8: Frontend and Chrome DevTools Acceptance

**Files:**
- Modify only if needed: `frontend/src/views/kmc/knowledgeBase/components/recall.vue`
- Verify: `frontend/src/api/kmc/knowledgeBase/knowledgeBase.js`

- [ ] **Step 1: Build frontend**

Run:

```bash
cd /Users/achilles/Documents/许子祺/Agent/frontend
rtk npm run build:prod
```

- [ ] **Step 2: Start stack**

Run:

```bash
cd /Users/achilles/Documents/许子祺/Agent
bash scripts/start.sh
```

- [ ] **Step 3: Chrome DevTools MCP E2E**

Use Chrome DevTools MCP:
- Open `http://localhost/`
- Login `admin/admin123`
- Open `/kmc/8/recall`
- Trigger `/dev-api/kmc/knowledgeBase/recallTest`
- Trigger `/dev-api/kmc/knowledgeBase/recallDebug`
- Trigger `/dev-api/kmc/knowledgeBase/8/ragCache`
- Trigger `/dev-api/kmc/log/list`

Expected:
- All key requests return 200.
- Console has no JS error.
- `recallDebug` includes `queryEnhance`, retrieval counts, timings, cache, fallback, and rerank fields.

---

## Explicit Non-Goals

- Do not add C++/Faiss/Qdrant until pgvector runtime settings, filtered ANN recall, and vector query p95 are proven insufficient at production-like scale.
- Do not claim full Microsoft GraphRAG, LightRAG, RAPTOR, Self-RAG, or ColBERTv2 unless the required indexing/model components are actually implemented.
- Do not use mock-only tests or empty golden tests as proof of algorithm improvement.
- Do not enable HyDE by default for exact numeric, day, document-name, or entity lookup queries.

---

## Source Basis

- pgvector official README: filtered ANN applies filters after index scan; iterative scans can improve filtered result count.
- HyDE paper: hypothetical documents should be embedded and grounded through dense retrieval, not blindly replace the original query for lexical/graph retrieval.
- RRF SIGIR paper: simple reciprocal-rank fusion is a strong baseline; score-scale mixing is less important than rank consistency.
- ColBERT/ColBERTv2/PLAID papers: late interaction depends on token-vector indexes and MaxSim, not hash token vectors.
- Microsoft GraphRAG / LightRAG / RAPTOR: graph/community/summary retrieval is useful, but indexing and evaluation cost must be explicit.
- RAGAS/RAGChecker/ARES/eRAG: answer quality metrics complement, but do not replace live retrieval metrics such as Recall@K, MRR, and NDCG.
