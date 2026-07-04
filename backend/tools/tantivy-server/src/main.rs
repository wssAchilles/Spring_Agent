use tantivy::collector::TopDocs;
use tantivy::query::QueryParser;
use tantivy::schema::*;
use tantivy::{doc, Index, IndexReader, IndexWriter, ReloadPolicy};
use tonic::{Request, Response, Status};
use std::sync::Arc;
use tokio::sync::Mutex;

// 引入生成的 protobuf 代码
pub mod search {
    tonic::include_proto!("search");
}

use search::tantivy_search_server::{TantivySearch, TantivySearchServer};
use search::{IndexRequest, IndexResponse, SearchRequest, SearchResponse, SearchResult};

// [溯源] 算法优化指南 Phase 5: Tantivy BM25 检索服务
pub struct TantivySearchService {
    index: Arc<Mutex<Option<Index>>>,
    reader: Arc<Mutex<Option<IndexReader>>>,
    schema: Schema,
}

impl TantivySearchService {
    pub fn new() -> Self {
        let mut schema_builder = Schema::builder();
        schema_builder.add_u64_field("segment_id", INDEXED | STORED);
        schema_builder.add_text_field("content", TEXT | STORED);
        schema_builder.add_text_field("document_name", TEXT | STORED);
        schema_builder.add_u64_field("knowledge_base_id", INDEXED | STORED);
        let schema = schema_builder.build();

        TantivySearchService {
            index: Arc::new(Mutex::new(None)),
            reader: Arc::new(Mutex::new(None)),
            schema,
        }
    }
}

#[tonic::async_trait]
impl TantivySearch for TantivySearchService {
    async fn index_document(&self, request: Request<IndexRequest>) -> Result<Response<IndexResponse>, Status> {
        let req = request.into_inner();
        
        let index_lock = self.index.lock().await;
        if index_lock.is_none() {
            return Ok(Response::new(IndexResponse {
                success: false,
                message: "Index not initialized".to_string(),
            }));
        }
        
        let index = index_lock.as_ref().unwrap();
        let mut writer: IndexWriter = index.writer(50_000_000)
            .map_err(|e| Status::internal(format!("Writer error: {}", e)))?;
        
        let segment_id_field = self.schema.get_field("segment_id").unwrap();
        let content_field = self.schema.get_field("content").unwrap();
        let doc_name_field = self.schema.get_field("document_name").unwrap();
        let kb_id_field = self.schema.get_field("knowledge_base_id").unwrap();
        
        writer.add_document(doc!(
            segment_id_field => req.segment_id as u64,
            content_field => req.content.as_str(),
            doc_name_field => req.document_name.as_str(),
            kb_id_field => req.knowledge_base_id as u64,
        )).map_err(|e| Status::internal(format!("Index error: {}", e)))?;
        
        writer.commit().map_err(|e| Status::internal(format!("Commit error: {}", e)))?;
        
        Ok(Response::new(IndexResponse {
            success: true,
            message: "Document indexed".to_string(),
        }))
    }

    async fn search(&self, request: Request<SearchRequest>) -> Result<Response<SearchResponse>, Status> {
        let req = request.into_inner();
        
        let reader_lock = self.reader.lock().await;
        if reader_lock.is_none() {
            return Ok(Response::new(SearchResponse {
                results: vec![],
                total: 0,
            }));
        }
        
        let reader = reader_lock.as_ref().unwrap();
        let searcher = reader.searcher();
        
        let content_field = self.schema.get_field("content").unwrap();
        let query_parser = QueryParser::for_index(searcher.index(), vec![content_field]);
        
        let query = query_parser.parse_query(&req.query)
            .map_err(|e| Status::invalid_argument(format!("Query parse error: {}", e)))?;
        
        let top_docs = searcher.search(&query, &TopDocs::with_limit(req.top_k as usize))
            .map_err(|e| Status::internal(format!("Search error: {}", e)))?;
        
        let segment_id_field = self.schema.get_field("segment_id").unwrap();
        let doc_name_field = self.schema.get_field("document_name").unwrap();
        
        let mut results = vec![];
        for (score, doc_addr) in top_docs {
            if let Ok(doc) = searcher.doc::<tantivy::TantivyDocument>(doc_addr) {
                let segment_id = doc.get_first(segment_id_field)
                    .and_then(|v| v.as_u64())
                    .unwrap_or(0);
                let content = doc.get_first(content_field)
                    .and_then(|v| v.as_str())
                    .unwrap_or("")
                    .to_string();
                let document_name = doc.get_first(doc_name_field)
                    .and_then(|v| v.as_str())
                    .unwrap_or("")
                    .to_string();
                
                results.push(SearchResult {
                    segment_id: segment_id as i64,
                    content,
                    score,
                    document_name,
                });
            }
        }
        
        let total = results.len() as i32;
        Ok(Response::new(SearchResponse { results, total }))
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let service = TantivySearchService::new();
    
    let addr = "[::1]:50051".parse()?;
    println!("TantivySearchService listening on {}", addr);
    
    tonic::transport::Server::builder()
        .add_service(TantivySearchServer::new(service))
        .serve(addr)
        .await?;
    
    Ok(())
}
