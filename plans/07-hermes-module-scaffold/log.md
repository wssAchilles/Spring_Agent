# [07-hermes-module-scaffold] Hermes 微服务模块脚手架

> 状态: completed | 开始: 2026-06-20T18:00:00+08:00 | 完成: 2026-06-20T19:40:00+08:00

## 目标清单

- [x] 7.1 创建 backend/qknow-hermes/ Maven 多模块结构（proto/core/starter）
- [x] 7.2 定义 hermes.proto（gRPC 服务 + protobuf 消息）
- [x] 7.3 根 pom.xml 添加 <module>qknow-hermes</module>
- [x] 7.4 创建 HermesApplication.java 独立启动类
- [x] 7.5 验证 mvn 独立编译通过

## 关键决策

### 决策 1: grpc-server-spring-boot-starter 版本选择
- **原因**: 3.5.0 在 Maven Central 不存在，3.1.0.RELEASE 是与 Spring Boot 3.5 兼容的最新稳定版
- **否决方案**: 3.5.0 (不存在), 手动配置 gRPC Server (过于复杂)

### 决策 2: HermesApplication.java 放在 starter 模块
- **原因**: Spring Boot Maven Plugin 的 repackage 目标需要在可执行 JAR 的模块中找到 main class
- **否决方案**: 放在 core 模块 (需要额外配置 mainClass)

## 遇到的问题

### 问题 1: grpc-server-spring-boot-starter:3.5.0 不存在
- **状态**: 已解决
- **解决方案**: 改为 3.1.0.RELEASE

### 问题 2: starter 模块打包时找不到 main class
- **状态**: 已解决
- **解决方案**: 将 HermesApplication.java 从 core 模块移到 starter 模块

## 修改的文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| backend/pom.xml | 修改 | 添加 qknow-hermes module |
| backend/qknow-hermes/pom.xml | 创建 | Hermes 父 POM |
| backend/qknow-hermes/qknow-hermes-proto/pom.xml | 创建 | Proto 模块 POM |
| backend/qknow-hermes/qknow-hermes-core/pom.xml | 创建 | Core 模块 POM |
| backend/qknow-hermes/qknow-hermes-starter/pom.xml | 创建 | Starter 模块 POM |
| backend/qknow-hermes/qknow-hermes-proto/src/main/proto/hermes.proto | 创建 | gRPC 协议定义 |
| backend/qknow-hermes/qknow-hermes-starter/src/main/java/.../HermesApplication.java | 创建 | 启动类 |
| backend/qknow-hermes/qknow-hermes-starter/src/main/resources/application.yml | 创建 | 服务配置 |

## 技术笔记

- Protobuf Maven Plugin 成功生成 40 个 Java 文件（消息类 + gRPC stubs）
- grpc-server-spring-boot-starter 3.1.0.RELEASE 引入 gRPC 1.63.0（与 proto 模块的 1.68.0 存在小版本差异，不影响功能）
- 全量 mvn package -DskipTests 通过

## 下一阶段预研

- [ ] 迁移 ChatModelFactory（从 ChatModelServiceImpl 重构）
- [ ] 迁移工具函数（6 个 ToolFunction）
- [ ] 迁移 AgentOrchestrator（核心重构 chatMessage 方法）
- [ ] 复活 HermesKernel + AiJudgeService
- [ ] 迁移 DAG 引擎
