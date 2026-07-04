# C++/Rust 高性能算法优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Java RAG 系统中引入 C++/Rust 高性能算法，提升向量检索、排序、分词的性能。

**Architecture:** 采用 JNI (Java 22+ FFM API) 集成 Rust 高性能库，通过 `native` 方法调用 SIMD 优化的向量计算、ColBERT MaxSim、中文分词算法。Cross-Encoder 使用 ONNX Runtime Java 官方绑定直接集成。

**Tech Stack:** Rust + JNI (jni-rs crate)、ONNX Runtime Java、ndarray + SIMD、jieba-rs

**前置条件:** Rust 工具链已安装 (`rustup`)、Java 22+ FFM API 可用

---

## Phase 1: Cross-Encoder ONNX Runtime 集成（2-3 天）

### Task 1.1: 安装 ONNX Runtime Java 依赖

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/pom.xml`

- [ ] **Step 1: 添加 ONNX Runtime 依赖**

```xml
<dependency>
    <groupId>com.microsoft.onnxruntime</groupId>
    <artifactId>onnxruntime</artifactId>
    <version>1.20.0</version>
</dependency>
```

- [ ] **Step 2: 验证依赖解析**

Run: `cd backend && mvn dependency:tree -pl qknow-module-kmc/qknow-module-kmc-biz | grep onnxruntime`
Expected: `com.microsoft.onnxruntime:onnxruntime:jar:1.20.0`

- [ ] **Step 3: Commit**

```bash
git add qknow-module-kmc/qknow-module-kmc-biz/pom.xml
git commit -m "build(kmc): add ONNX Runtime Java dependency for Cross-Encoder"
```

### Task 1.2: 实现 ONNX Cross-Encoder Provider

**Covers:** Phase 1 精排优化

**Files:**
- Create: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/OnnxRerankerProvider.java`
- Test: `backend/tests/src/test/java/tech/qiantong/qknow/rag/OnnxRerankerProviderTest.java`

- [ ] **Step 1: 写失败测试**

```java
@Test
@DisplayName("ONNX Cross-Encoder 重排测试")
void rerank_withValidModel_returnsScoredResults() {
    OnnxRerankerProvider provider = new OnnxRerankerProvider(config);
    // 模型文件不存在时应抛异常或返回空
    assertThrows(Exception.class, () -> 
        provider.rerank(context, candidates, queryIntent, 5));
}
```

- [ ] **Step 2: 实现 OnnxRerankerProvider**

```java
@Slf4j
@Component
public class OnnxRerankerProvider implements RerankerProvider {
    private final OnnxEnv env = OnnxEnv.getEnvironment();
    private OrtSession session;
    
    @Override
    public String name() { return "onnx-cross-encoder"; }
    
    @Override
    public boolean supports(RerankRequestContext context) {
        return config.isEnabled() && session != null;
    }
    
    @Override
    public List<RetrievalResult> rerank(RerankRequestContext context,
            List<RetrievalResult> candidates, QueryIntent queryIntent, int topK) {
        // 1. Tokenize (query, document) pairs
        // 2. 创建 ONNX Tensor
        // 3. session.run() 推理
        // 4. 按分数排序取 topK
    }
}
```

- [ ] **Step 3: 运行测试验证**

Run: `mvn test -pl tests -Dtest="OnnxRerankerProviderTest" -DfailIfNoTests=false`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/.../OnnxRerankerProvider.java
git add backend/tests/src/test/java/.../OnnxRerankerProviderTest.java
git commit -m "feat(rag): implement ONNX Cross-Encoder reranker provider"
```

---

## Phase 2: 中文分词 jieba-rs 集成（1 周）

### Task 2.1: 创建 Rust JNI 分词库

**Covers:** Phase 2 中文分词优化

**Files:**
- Create: `backend/tools/jieba-jni/Cargo.toml`
- Create: `backend/tools/jieba-jni/src/lib.rs`

- [ ] **Step 1: 初始化 Rust 项目**

```bash
cd backend/tools && cargo new jieba-jni --lib
cd jieba-jni
```

- [ ] **Step 2: 配置 Cargo.toml**

```toml
[package]
name = "jieba_jni"
version = "0.1.0"
edition = "2021"

[lib]
crate_type = ["cdylib"]

[dependencies]
jni = "0.21"
jieba-rs = "0.7"
lazy_static = "1.4"
```

- [ ] **Step 3: 实现 JNI 接口**

```rust
use jni::JNIEnv;
use jni::objects::{JClass, JString, JObjectArray};
use jni::sys::jobjectArray;
use jieba_rs::Jieba;
use lazy_static::lazy_static;

lazy_static! {
    static ref JIEBA: Jieba = Jieba::new();
}

#[no_mangle]
pub extern "system" fn Java_com_rag_nlp_JiebaNative_cut(
    mut env: JNIEnv, _class: JClass, input: JString
) -> jobjectArray {
    let text: String = env.get_string(&input).unwrap().into();
    let words = JIEBA.cut(&text, false);
    
    let word_class = env.find_class("java/lang/String").unwrap();
    let result = env.new_object_array(words.len() as i32, word_class, JObject::null()).unwrap();
    for (i, word) in words.iter().enumerate() {
        let jword = env.new_string(word).unwrap();
        env.set_object_array_element(&result, i as i32, jword).unwrap();
    }
    result.into_raw()
}
```

- [ ] **Step 4: 编译动态库**

```bash
cargo build --release
# 输出: target/release/libjieba_jni.dylib (macOS) 或 libjieba_jni.so (Linux)
```

- [ ] **Step 5: Commit**

```bash
git add backend/tools/jieba-jni/
git commit -m "feat(nlp): create Rust jieba-rs JNI library"
```

### Task 2.2: Java 侧集成 jieba-rs

**Files:**
- Create: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/nlp/JiebaNative.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/KeywordRetriever.java`

- [ ] **Step 1: 创建 JiebaNative JNI 桥接类**

```java
public class JiebaNative {
    static { 
        try { System.loadLibrary("jieba_jni"); } 
        catch (UnsatisfiedLinkError e) { /* 降级到 Java 分词 */ }
    }
    
    public static native String[] cut(String text);
    
    public static boolean isAvailable() {
        try { cut("测试"); return true; }
        catch (UnsatisfiedLinkError e) { return false; }
    }
}
```

- [ ] **Step 2: 修改 KeywordRetriever 使用 jieba-rs**

```java
private List<String> buildSearchTerms(String queryText) {
    if (JiebaNative.isAvailable()) {
        return Arrays.asList(JiebaNative.cut(queryText));
    }
    // fallback: 当前 Java 分词逻辑
    return buildSearchTermsJava(queryText);
}
```

- [ ] **Step 3: 运行全量测试**

Run: `mvn test -pl tests -Dtest="!HttpRequestToolTest" -DfailIfNoTests=false`
Expected: 489+ tests pass

- [ ] **Step 4: Commit**

```bash
git add backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/.../nlp/JiebaNative.java
git add backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/.../KeywordRetriever.java
git commit -m "feat(rag): integrate jieba-rs JNI for Chinese tokenization"
```

---

## Phase 3: 向量相似度 Rust SIMD（1-2 周）

### Task 3.1: 创建 Rust SIMD 向量计算库

**Covers:** Phase 3 向量检索优化

**Files:**
- Create: `backend/tools/vecsim-jni/Cargo.toml`
- Create: `backend/tools/vecsim-jni/src/lib.rs`

- [ ] **Step 1: 初始化 Rust 项目**

```bash
cd backend/tools && cargo new vecsim-jni --lib
```

- [ ] **Step 2: 实现 SIMD cosine batch**

```rust
use jni::JNIEnv;
use jni::objects::{JClass, JFloatArray};
use jni::sys::jfloatArray;

#[no_mangle]
pub extern "system" fn Java_com_rag_sim_VecSimNative_cosineBatch(
    mut env: JNIEnv, _class: JClass,
    query: JFloatArray, corpus: JFloatArray, dim: i32
) -> jfloatArray {
    let q = env.get_float_array_region(&query, 0, dim as usize).unwrap();
    let corpus_len = env.get_array_length(&corpus).unwrap() as usize;
    let n = corpus_len / dim as usize;
    
    let mut scores = Vec::with_capacity(n);
    let q_norm: f32 = q.iter().map(|x| x * x).sum::<f32>().sqrt();
    
    for i in 0..n {
        let offset = i * dim as usize;
        let chunk: Vec<f32> = env.get_float_array_region(&corpus, offset as i32, dim as usize).unwrap();
        
        let dot: f32 = q.iter().zip(chunk.iter()).map(|(a, b)| a * b).sum();
        let c_norm: f32 = chunk.iter().map(|x| x * x).sum::<f32>().sqrt();
        scores.push(dot / (q_norm * c_norm));
    }
    
    let result = env.new_float_array(n as i32).unwrap();
    env.set_float_array_region(&result, 0, &scores).unwrap();
    result
}
```

- [ ] **Step 3: 编译并测试**

```bash
cargo build --release
# 测试 JNI 调用
```

- [ ] **Step 4: Commit**

```bash
git add backend/tools/vecsim-jni/
git commit -m "feat(sim): create Rust SIMD vector similarity JNI library"
```

### Task 3.2: 集成 SIMD 向量计算到检索管线

**Files:**
- Create: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/sim/VecSimNative.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/VectorRetriever.java`

- [ ] **Step 1: 创建 VecSimNative JNI 桥接**

```java
public class VecSimNative {
    static { 
        try { System.loadLibrary("vecsim_jni"); }
        catch (UnsatisfiedLinkError e) { /* fallback */ }
    }
    
    public static native float[] cosineBatch(float[] query, float[] corpus, int dim);
}
```

- [ ] **Step 2: 修改 VectorRetriever 使用 SIMD 计算**

```java
// 在向量检索后，使用 SIMD 批量计算相似度
if (VecSimNative.isAvailable()) {
    float[] scores = VecSimNative.cosineBatch(queryVector, corpusVectors, 1536);
    // 按 scores 排序取 topK
}
```

- [ ] **Step 3: 运行全量测试**

Run: `mvn test -pl tests -Dtest="!HttpRequestToolTest" -DfailIfNoTests=false`
Expected: 489+ tests pass

- [ ] **Step 4: Commit**

```bash
git add backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/.../sim/VecSimNative.java
git add backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/.../VectorRetriever.java
git commit -m "feat(rag): integrate Rust SIMD vector similarity for retrieval"
```

---

## Phase 4: ColBERT MaxSim 矩阵乘法（2-3 周）

### Task 4.1: 实现 Rust ColBERT MaxSim

**Covers:** Phase 4 粗排优化

**Files:**
- Create: `backend/tools/colbert-jni/Cargo.toml`
- Create: `backend/tools/colbert-jni/src/lib.rs`

- [ ] **Step 1: 实现 MaxSim 矩阵乘法**

```rust
use ndarray::Array2;

pub fn maxsim(query: &Array2<f32>, doc: &Array2<f32>) -> f32 {
    let sim_matrix = query.dot(&doc.t());
    sim_matrix.rows().into_iter()
        .map(|row| row.fold(f32::NEG_INFINITY, |a, &b| a.max(b)))
        .sum()
}

pub fn batch_maxsim(query: &Array2<f32>, docs: &[Array2<f32>]) -> Vec<f32> {
    docs.iter().map(|doc| maxsim(query, doc)).collect()
}
```

- [ ] **Step 2: 编译 JNI 接口**

```bash
cargo build --release
```

- [ ] **Step 3: Commit**

```bash
git add backend/tools/colbert-jni/
git commit -m "feat(rag): create Rust ColBERT MaxSim JNI library"
```

### Task 4.2: 集成 ColBERT SIMD 到粗排管线

**Files:**
- Create: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertNative.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertScorer.java`

- [ ] **Step 1: 创建 ColbertNative JNI 桥接**

```java
public class ColbertNative {
    static { System.loadLibrary("colbert_jni"); }
    public static native float maxsim(float[] queryTokens, float[] docTokens, int qLen, int dLen, int dim);
    public static native float[] batchMaxsim(float[] queryTokens, float[][] docTokens, int qLen, int dim);
}
```

- [ ] **Step 2: 修改 ColbertScorer 使用 SIMD**

```java
if (ColbertNative.isAvailable()) {
    return ColbertNative.maxsim(queryTokens, docTokens, qLen, dLen, 128);
}
// fallback: Java 实现
```

- [ ] **Step 3: Commit**

```bash
git add backend/qknow-module-kmc/.../rerank/ColbertNative.java
git add backend/qknow-module-kmc/.../rerank/ColbertScorer.java
git commit -m "feat(rag): integrate Rust SIMD ColBERT MaxSim for coarse ranking"
```

---

## Phase 5: BM25 Tantivy gRPC 服务（2-3 周）

### Task 5.1: 创建 Tantivy 检索服务

**Covers:** Phase 5 关键词检索优化

**Files:**
- Create: `backend/tools/tantivy-server/Cargo.toml`
- Create: `backend/tools/tantivy-server/src/main.rs`
- Create: `backend/tools/tantivy-server/proto/search.proto`

- [ ] **Step 1: 定义 Protobuf 接口**

```protobuf
syntax = "proto3";
package search;

service TantivySearch {
    rpc IndexDocument(IndexRequest) returns (IndexResponse);
    rpc Search(SearchRequest) returns (SearchResponse);
}

message SearchRequest {
    string query = 1;
    int32 top_k = 2;
    int64 knowledge_base_id = 3;
}

message SearchResponse {
    repeated SearchResult results = 1;
}

message SearchResult {
    int64 segment_id = 1;
    string content = 2;
    float score = 3;
}
```

- [ ] **Step 2: 实现 Tantivy 服务**

```rust
use tantivy::prelude::*;
use tonic::{Request, Response, Status};

#[tonic::async_trait]
impl TantivySearch for SearchService {
    async fn search(&self, request: Request<SearchRequest>) -> Result<Response<SearchResponse>, Status> {
        let req = request.into_inner();
        let searcher = self.reader.searcher();
        let query = self.query_parser.parse_query(&req.query).unwrap();
        let top_docs = searcher.search(&query, &TopDocs::with_limit(req.top_k as usize)).unwrap();
        // 返回结果...
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/tools/tantivy-server/
git commit -m "feat(search): create Rust Tantivy BM25 search gRPC service"
```

### Task 5.2: Java 侧集成 Tantivy gRPC 客户端

**Files:**
- Create: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/search/TantivyClient.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/KeywordRetriever.java`

- [ ] **Step 1: 创建 TantivyClient**

```java
@Component
public class TantivyClient {
    private final TantivySearchGrpc.TantivySearchBlockingStub stub;
    
    public List<RetrievalResult> search(String query, int topK, long knowledgeBaseId) {
        SearchRequest request = SearchRequest.newBuilder()
            .setQuery(query)
            .setTopK(topK)
            .setKnowledgeBaseId(knowledgeBaseId)
            .build();
        SearchResponse response = stub.search(request);
        return response.getResultsList().stream()
            .map(r -> RetrievalResult.builder()
                .segmentId(r.getSegmentId())
                .content(r.getContent())
                .score(r.getScore())
                .source("tantivy_bm25")
                .build())
            .toList();
    }
}
```

- [ ] **Step 2: 修改 KeywordRetriever 使用 Tantivy**

```java
if (tantivyClient.isAvailable()) {
    return tantivyClient.search(query, topK, knowledgeBaseId);
}
// fallback: 当前 pg_trgm 实现
```

- [ ] **Step 3: Commit**

```bash
git add backend/qknow-module-kmc/.../search/TantivyClient.java
git add backend/qknow-module-kmc/.../KeywordRetriever.java
git commit -m "feat(rag): integrate Tantivy BM25 gRPC client for keyword search"
```

---

## 验收标准

| Phase | 验收指标 | 目标 |
|-------|----------|------|
| Phase 1 | Cross-Encoder 推理延迟 | < 100ms/23 文档 |
| Phase 2 | 中文分词速度 | > 1000 queries/sec |
| Phase 3 | 向量相似度计算 | 4926×1536 < 2ms |
| Phase 4 | ColBERT MaxSim | 100 文档 < 1ms |
| Phase 5 | BM25 检索延迟 | < 10ms/query |

## 测试命令

```bash
# 全量测试
mvn test -pl tests -Dtest="!HttpRequestToolTest" -DfailIfNoTests=false

# RAGAS 评估
mvn test -pl tests -Dtest="RagasFullEvaluationTest#loadFromDbAndEvaluate" -DfailIfNoTests=false

# RAGChecker 评估
mvn test -pl tests -Dtest="RAGCheckerFullEvaluationTest#loadFromDbAndEvaluate" -DfailIfNoTests=false
```
