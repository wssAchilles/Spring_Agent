#!/usr/bin/env python3
"""
轻量级 Rerank 服务 — 通过 sentence-transformers CrossEncoder 提供本地精排
作为 DashScope API 不可用时的兜底方案

用法: python3 rerank_server.py [--port 8765]
"""
import json
import sys
import time
from http.server import HTTPServer, BaseHTTPRequestHandler

# 懒加载模型（首次请求时加载）
_model = None
_model_name = "cross-encoder/ms-marco-MiniLM-L-6-v2"

def get_model():
    global _model
    if _model is None:
        from sentence_transformers import CrossEncoder
        print(f"[rerank] Loading model {_model_name}...")
        _model = CrossEncoder(_model_name, max_length=512)
        print(f"[rerank] Model loaded.")
    return _model

class RerankHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/rerank":
            self.send_error(404)
            return
        try:
            content_len = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_len)
            data = json.loads(body)
            query = data["query"]
            passages = data["passages"]  # [{"id": ..., "text": ...}]
            
            if not passages:
                self._json_response([])
                return
            
            model = get_model()
            pairs = [(query, p["text"]) for p in passages]
            scores = model.predict(pairs)
            
            results = []
            for p, s in zip(passages, scores):
                results.append({"id": p["id"], "score": float(s)})
            results.sort(key=lambda x: x["score"], reverse=True)
            
            self._json_response(results)
        except Exception as e:
            self._json_response({"error": str(e)}, 500)

    def _json_response(self, data, status=200):
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        pass  # suppress logs

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    # 预加载模型
    get_model()
    server = HTTPServer(("127.0.0.1", port), RerankHandler)
    print(f"[rerank] Server listening on http://127.0.0.1:{port}")
    server.serve_forever()
