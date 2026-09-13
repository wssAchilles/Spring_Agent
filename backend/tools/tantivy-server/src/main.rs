use axum::{
    extract::State,
    http::StatusCode,
    response::IntoResponse,
    routing::{get, post},
    Json, Router,
};
use jieba_rs::Jieba;
use lazy_static::lazy_static;
use serde::{Deserialize, Serialize};
use std::fs;
use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;
use tantivy::collector::TopDocs;
use tantivy::query::{BooleanQuery, Occur, Query, QueryParser, TermQuery};
use tantivy::schema::*;
use tantivy::tokenizer::{BoxTokenStream, Token, TokenStream, Tokenizer};
use tantivy::{doc, Index, IndexReader, IndexWriter, ReloadPolicy, Term};
use tokio::sync::{mpsc, oneshot};

lazy_static! {
    static ref GLOBAL_JIEBA: Arc<Jieba> = Arc::new(Jieba::new());
}

// --------------------------- 中文分词器 ---------------------------

#[derive(Clone)]
pub struct JiebaTokenizer {
    jieba: Arc<Jieba>,
}

impl JiebaTokenizer {
    pub fn new() -> Self {
        Self {
            jieba: GLOBAL_JIEBA.clone(),
        }
    }
}

pub struct JiebaTokenStream<'a> {
    text: &'a str,
    tokens: Vec<jieba_rs::Token<'a>>,
    index: usize,
    current_token: Token,
}

impl<'a> TokenStream for JiebaTokenStream<'a> {
    fn advance(&mut self) -> bool {
        if self.index < self.tokens.len() {
            let t = &self.tokens[self.index];
            self.current_token = Token {
                offset_from: t.start,
                offset_to: t.end,
                position: self.index,
                text: t.word.to_string(),
                position_length: 1,
            };
            self.index += 1;
            true
        } else {
            false
        }
    }

    fn token(&self) -> &Token {
        &self.current_token
    }

    fn token_mut(&mut self) -> &mut Token {
        &mut self.current_token
    }
}

impl Tokenizer for JiebaTokenizer {
    type TokenStream<'a> = JiebaTokenStream<'a>;

    fn token_stream<'a>(&'a mut self, text: &'a str) -> Self::TokenStream<'a> {
        let tokens = self.jieba.tokenize(text, jieba_rs::TokenizeMode::Search, true);
        JiebaTokenStream {
            text,
            tokens,
            index: 0,
            current_token: Token::default(),
        }
    }
}

// --------------------------- Actor 单例写入与节流 ---------------------------

pub enum IndexCommand {
    Add(tantivy::TantivyDocument, oneshot::Sender<Result<(), String>>),
    Delete(Term, oneshot::Sender<Result<(), String>>),
    Flush(oneshot::Sender<Result<(), String>>),
}

pub struct IndexActor {
    writer: IndexWriter,
    receiver: mpsc::Receiver<IndexCommand>,
}

impl IndexActor {
    pub async fn run(mut self) {
        let mut interval = tokio::time::interval(std::time::Duration::from_millis(1000));
        let mut uncommitted_count = 0;

        loop {
            tokio::select! {
                cmd = self.receiver.recv() => {
                    match cmd {
                        Some(IndexCommand::Add(doc, reply)) => {
                            let res = self.writer.add_document(doc);
                            match res {
                                Ok(_) => {
                                    uncommitted_count += 1;
                                    let _ = reply.send(Ok(()));
                                    if uncommitted_count >= 500 {
                                        let _ = self.writer.commit();
                                        uncommitted_count = 0;
                                    }
                                }
                                Err(e) => {
                                    let _ = reply.send(Err(e.to_string()));
                                }
                            }
                        }
                        Some(IndexCommand::Delete(term, reply)) => {
                            let _ = self.writer.delete_term(term);
                            uncommitted_count += 1;
                            let _ = reply.send(Ok(()));
                        }
                        Some(IndexCommand::Flush(reply)) => {
                            let _ = self.writer.commit();
                            uncommitted_count = 0;
                            let _ = reply.send(Ok(()));
                        }
                        None => break,
                    }
                }
                _ = interval.tick() => {
                    if uncommitted_count > 0 {
                        let _ = self.writer.commit();
                        uncommitted_count = 0;
                    }
                }
            }
        }
    }
}

// --------------------------- 应用共享状态与服务层 ---------------------------

#[derive(Clone)]
pub struct AppState {
    index: Index,
    reader: IndexReader,
    schema: Schema,
    segment_id_field: Field,
    content_field: Field,
    doc_name_field: Field,
    kb_id_field: Field,
    command_tx: mpsc::Sender<IndexCommand>,
}

#[derive(Deserialize)]
pub struct SearchReq {
    pub query: String,
    #[serde(default = "default_top_k")]
    pub top_k: usize,
    #[serde(default)]
    pub knowledge_base_id: i64,
}

fn default_top_k() -> usize {
    10
}

#[derive(Serialize)]
pub struct SearchHit {
    pub segment_id: i64,
    pub content: String,
    pub document_name: String,
    pub score: f32,
}

#[derive(Serialize)]
pub struct SearchResp {
    pub results: Vec<SearchHit>,
    pub total: usize,
}

#[derive(Deserialize)]
pub struct IndexDocReq {
    pub segment_id: i64,
    pub content: String,
    pub document_name: String,
    pub knowledge_base_id: i64,
}

#[derive(Deserialize)]
pub struct BatchIndexReq {
    pub documents: Vec<IndexDocReq>,
}

#[derive(Deserialize)]
pub struct DeleteDocReq {
    pub segment_id: i64,
    pub knowledge_base_id: i64,
}

// --------------------------- HTTP Handler ---------------------------

async fn ping_handler() -> impl IntoResponse {
    Json(serde_json::json!({
        "healthy": true,
        "version": "0.1.0",
        "engine": "tantivy_jieba_bm25"
    }))
}

async fn search_handler(
    State(state): State<AppState>,
    Json(req): Json<SearchReq>,
) -> Result<Json<SearchResp>, (StatusCode, String)> {
    let searcher = state.reader.searcher();

    let query_parser = QueryParser::for_index(&state.index, vec![state.content_field]);
    let content_query = query_parser
        .parse_query(&req.query)
        .map_err(|e| (StatusCode::BAD_REQUEST, format!("Query parse error: {}", e)))?;

    let final_query: Box<dyn Query> = if req.knowledge_base_id > 0 {
        let kb_term = Term::from_field_u64(state.kb_id_field, req.knowledge_base_id as u64);
        let kb_query = Box::new(TermQuery::new(kb_term, IndexRecordOption::Basic));
        Box::new(BooleanQuery::new(vec![
            (Occur::Must, content_query),
            (Occur::Must, kb_query),
        ]))
    } else {
        content_query
    };

    let top_k = req.top_k.max(1).min(100);
    let top_docs = searcher
        .search(&final_query, &TopDocs::with_limit(top_k))
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, format!("Search failed: {}", e)))?;

    let mut results = Vec::with_capacity(top_docs.len());
    for (score, doc_addr) in top_docs {
        if let Ok(doc) = searcher.doc::<tantivy::TantivyDocument>(doc_addr) {
            let segment_id = doc
                .get_first(state.segment_id_field)
                .and_then(|v| v.as_u64())
                .unwrap_or(0) as i64;
            let content = doc
                .get_first(state.content_field)
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string();
            let document_name = doc
                .get_first(state.doc_name_field)
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string();

            results.push(SearchHit {
                segment_id,
                content,
                document_name,
                score,
            });
        }
    }

    let total = results.len();
    Ok(Json(SearchResp { results, total }))
}

async fn index_document_handler(
    State(state): State<AppState>,
    Json(req): Json<IndexDocReq>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    let tantivy_doc = doc!(
        state.segment_id_field => req.segment_id as u64,
        state.content_field => req.content.as_str(),
        state.doc_name_field => req.document_name.as_str(),
        state.kb_id_field => req.knowledge_base_id as u64,
    );

    let (tx, rx) = oneshot::channel();
    state
        .command_tx
        .send(IndexCommand::Add(tantivy_doc, tx))
        .await
        .map_err(|_| (StatusCode::INTERNAL_SERVER_ERROR, "Actor closed".into()))?;

    rx.await
        .map_err(|_| (StatusCode::INTERNAL_SERVER_ERROR, "Actor timeout".into()))?
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e))?;

    Ok(Json(serde_json::json!({ "success": true })))
}

async fn batch_index_handler(
    State(state): State<AppState>,
    Json(req): Json<BatchIndexReq>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    let mut count = 0;
    for doc_req in req.documents {
        let tantivy_doc = doc!(
            state.segment_id_field => doc_req.segment_id as u64,
            state.content_field => doc_req.content.as_str(),
            state.doc_name_field => doc_req.document_name.as_str(),
            state.kb_id_field => doc_req.knowledge_base_id as u64,
        );

        let (tx, rx) = oneshot::channel();
        if state
            .command_tx
            .send(IndexCommand::Add(tantivy_doc, tx))
            .await
            .is_ok()
            && rx.await.is_ok()
        {
            count += 1;
        }
    }

    Ok(Json(serde_json::json!({
        "success": true,
        "indexed_count": count
    })))
}

async fn delete_document_handler(
    State(state): State<AppState>,
    Json(req): Json<DeleteDocReq>,
) -> Result<Json<serde_json::Value>, (StatusCode, String)> {
    let term = Term::from_field_u64(state.segment_id_field, req.segment_id as u64);
    let (tx, rx) = oneshot::channel();

    state
        .command_tx
        .send(IndexCommand::Delete(term, tx))
        .await
        .map_err(|_| (StatusCode::INTERNAL_SERVER_ERROR, "Actor closed".into()))?;

    rx.await
        .map_err(|_| (StatusCode::INTERNAL_SERVER_ERROR, "Actor timeout".into()))?
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e))?;

    Ok(Json(serde_json::json!({ "success": true })))
}

// --------------------------- 主入口 ---------------------------

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("=== 启动 Tantivy 中文 BM25 检索引擎服务 ===");

    // 1. 构建 Schema
    let mut schema_builder = Schema::builder();
    let segment_id_field = schema_builder.add_u64_field("segment_id", INDEXED | STORED | FAST);

    let text_indexing = TextFieldIndexing::default()
        .set_tokenizer("jieba")
        .set_index_option(IndexRecordOption::WithFreqsAndPositions);
    let text_options = TextOptions::default()
        .set_indexing_options(text_indexing)
        .set_stored();
    let content_field = schema_builder.add_text_field("content", text_options);
    let doc_name_field = schema_builder.add_text_field("document_name", TEXT | STORED);
    let kb_id_field = schema_builder.add_u64_field("knowledge_base_id", INDEXED | STORED | FAST);
    let schema = schema_builder.build();

    // 2. 目录持久化初始化
    let index_dir = std::env::var("TANTIVY_INDEX_DIR")
        .unwrap_or_else(|_| "./data/tantivy_index".to_string());
    let index_path = PathBuf::from(&index_dir);
    if !index_path.exists() {
        fs::create_dir_all(&index_path)?;
    }

    let index = if Index::open_in_dir(&index_path).is_ok() {
        println!("正在打开现有索引目录: {}", index_dir);
        Index::open_in_dir(&index_path)?
    } else {
        println!("正在创建全新索引目录: {}", index_dir);
        Index::create_in_dir(&index_path, schema.clone())?
    };

    // 3. 注册 Jieba 分词器
    index
        .tokenizers()
        .register("jieba", JiebaTokenizer::new());

    // 4. 初始化 IndexReader
    let reader = index
        .reader_builder()
        .reload_policy(ReloadPolicy::OnCommitWithDelay)
        .try_into()?;

    // 5. 初始化单例 IndexWriterActor
    let writer: IndexWriter = index.writer(50_000_000)?;
    let (command_tx, command_rx) = mpsc::channel(1024);
    let actor = IndexActor {
        writer,
        receiver: command_rx,
    };
    tokio::spawn(actor.run());

    let state = AppState {
        index,
        reader,
        schema,
        segment_id_field,
        content_field,
        doc_name_field,
        kb_id_field,
        command_tx,
    };

    // 6. 构建 Axum Router
    let app = Router::new()
        .route("/ping", get(ping_handler))
        .route("/health", get(ping_handler))
        .route("/search", post(search_handler))
        .route("/index", post(index_document_handler))
        .route("/batch_index", post(batch_index_handler))
        .route("/delete", post(delete_document_handler))
        .with_state(state);

    let port: u16 = std::env::var("PORT")
        .ok()
        .and_then(|p| p.parse().ok())
        .unwrap_or(50051);
    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    println!("Tantivy HTTP REST 服务正在监听: http://{}", addr);

    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await?;

    Ok(())
}
