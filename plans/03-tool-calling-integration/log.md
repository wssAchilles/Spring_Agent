# 03-tool-calling-integration 工具箱集成 — Tool Calling

> 状态: completed | 开始: 2026-06-20T06:00:00+08:00 | 完成: 2026-06-20T08:00:00+08:00

## 目标清单

- [x] 3.1 内置工具扩展 (HTTP请求, Web搜索, 文本转换)
- [x] 3.2 MCP 协议基础适配 + 工具权限系统 + 健康检查

## 关键决策

### 决策 1: 借鉴 claw-code 的 PermissionPolicy 设计工具权限系统
- **原因**: claw-code 的权限系统设计精良，4 级权限控制
- **否决方案**: 无权限控制、RBAC 权限

### 决策 2: MCP 工具采用占位实现
- **原因**: MCP 协议复杂度高，先搭建框架后续扩展
- **否决方案**: 完整实现 MCP 协议、不支持 MCP

### 决策 3: Web 搜索使用 DuckDuckGo API
- **原因**: 免费无需 API Key
- **否决方案**: Google/Bing Search API

## 修改的文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| tool/function/HttpRequestToolFunction.java | 新建 | HTTP 请求工具 |
| tool/function/WebSearchToolFunction.java | 新建 | Web 搜索工具 |
| tool/function/TextTransformToolFunction.java | 新建 | 文本转换工具 |
| tool/mcp/McpToolAdapter.java | 新建 | MCP 工具适配器 |
| tool/permission/ToolPermissionLevel.java | 新建 | 权限级别枚举 |
| tool/permission/ToolPermissionEnforcer.java | 新建 | 权限执行器 |
| controller/admin/HealthCheckController.java | 新建 | 健康检查端点 |

## 技术笔记

1. **工具权限系统**: 4 级权限 (READ_ONLY/STANDARD/ELEVATED/DANGEROUS)，会话级别权限控制
2. **MCP 适配器**: 支持 MCP Server 注册/移除，工具发现和执行（占位实现）
3. **健康检查**: 数据库连接、MCP 状态、系统信息
4. **claw-code 借鉴**: PermissionPolicy、doctor 命令、模块化 crate 布局

## 下一阶段预研

- [ ] DAG 数据模型完善
- [ ] DAG 画布前端扩展
- [ ] DAG 执行引擎
- [ ] 并行执行与状态监控
