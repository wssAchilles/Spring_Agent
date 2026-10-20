# Hermes gRPC TLS 证书目录

生产开启 `HERMES_GRPC_TLS_ENABLED=true` 时，将以下文件放入本目录（不要提交真实私钥）：

- `ca.crt`           —— 客户端/服务端互信 CA 证书
- `server.crt`/`server.key` —— Hermes gRPC 服务端证书与私钥
- `client.crt`/`client.key` —— 控制面 gRPC 客户端证书（mTLS 时使用）

开发环境保持 `HERMES_GRPC_TLS_ENABLED=false`，走内网明文 + token 鉴权。
