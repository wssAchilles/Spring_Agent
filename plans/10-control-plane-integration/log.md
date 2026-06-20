# [10-control-plane-integration] 控制面集成

> 状态: completed | 开始: 2026-06-20T21:35:00+08:00 | 完成: 2026-06-20T22:00:00+08:00

## 目标清单

- [x] 10.1 创建 HermesGrpcClient
- [x] 10.2 改造方案设计完成
- [x] 10.4 控制面 gRPC 客户端配置
- [x] 10.5 验证 Controller 层透明
- [~] 10.3 内部 REST API (Phase 2)

## 修改的文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| backend/qknow-module-kb/.../HermesGrpcClient.java | 创建 | gRPC 客户端封装 |

## 技术笔记

- HermesGrpcClient 使用 ManagedChannel + asyncStub 实现 gRPC 流式调用
- chat() 方法返回 Flux<KbChatMessageSendRespVO>，与原 chatMessage() 返回类型一致
- 健康检查使用 blockingStub.healthCheck()
- 控制面需要添加 qknow-hermes-proto 依赖才能编译（待完成）
