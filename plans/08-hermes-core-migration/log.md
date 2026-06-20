# [08-hermes-core-migration] Hermes 核心引擎迁移

> 状态: completed | 开始: 2026-06-20T19:45:00+08:00 | 完成: 2026-06-20T21:30:00+08:00

## 目标清单

- [x] 8.1 迁移 ChatModelFactory
- [x] 8.2 迁移工具函数 (9 个)
- [x] 8.3 迁移并改造 SearchKnowledgeTool
- [x] 8.4 迁移 AgentOrchestrator
- [x] 8.5 复活 HermesKernel + AiJudgeService
- [x] 8.6 迁移 DAG 引擎
- [x] 8.7 新建轻量 DO 替代类 + 本地枚举
- [x] 8.8 编译验证 (36 个 Java 文件)

## 修改的文件

37 个文件变更，3130 行新增。详见 git commit 44a1ecc。

## 技术笔记

- ChatModelFactory 从 ChatModelServiceImpl 重构，接受 4 个参数 (platform/baseUrl/apiKey/modelName)
- AgentOrchestrator 返回 Flux<ChatEvent> 而非 Flux<KbChatMessageSendRespVO>
- DAG 引擎的 DO 类创建了轻量本地替代，避免依赖控制面 DAL 层
- HermesGrpcService 在本阶段一并完成（@GrpcService 注解）

## 下一阶段预研

- [ ] 创建 HermesGrpcClient (控制面 gRPC 客户端)
- [ ] 改造 KbAgentConfigServiceImpl.chatMessage()
- [ ] 创建内部 REST API
