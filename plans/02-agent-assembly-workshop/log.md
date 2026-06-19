# 02-agent-assembly-workshop 智能体装配车间

> 状态: completed | 开始: 2026-06-20T03:00:00+08:00 | 完成: 2026-06-20T05:00:00+08:00

## 目标清单

- [x] 2.1 Conversation/Message 后端实体与服务层
- [x] 2.2 Agent 对话控制器与历史集成
- [x] 2.3 前端对话侧边栏与消息持久化

## 关键决策

### 决策 1: Conversation/Message 放在 qknow-module-kb 模块
- **原因**: 与 Agent 逻辑紧密耦合，减少跨模块依赖
- **否决方案**: 放在 qknow-module-app

### 决策 2: 新增 /kb/conversation/send SSE 端点
- **原因**: 保持与现有 SSE 流式输出模式一致
- **否决方案**: WebSocket、修改现有调试端点

### 决策 3: 前端自动创建对话
- **原因**: 降低用户操作门槛
- **否决方案**: 强制用户先创建对话

## 遇到的问题

### 问题 1: Controller 方法名不匹配
- **状态**: 已解决
- **解决方案**: 修正为 getKbAgentConfigByBotId

## 修改的文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| dal/dataobject/conversation/KbConversationDO.java | 新建 | 对话实体 |
| dal/dataobject/conversation/KbChatMessageDO.java | 新建 | 消息实体 |
| dal/mapper/conversation/*.java | 新建 | Mapper 接口 |
| service/conversation/*.java | 新建 | Service 接口与实现 |
| controller/admin/conversation/*.java | 新建 | Controller + VO |
| controller/admin/agent/vo/KbAgentConfigReqVO.java | 修改 | 新增 historyMessages |
| service/agent/impl/KbAgentConfigServiceImpl.java | 修改 | 实现对话历史注入 |
| frontend/src/api/kb/conversation/index.js | 新建 | 对话 API |
| frontend/src/views/kb/agent/index.vue | 修改 | 对话管理 UI |

## 技术笔记

1. **对话历史注入**: Agent 构建消息列表时，遍历 historyMessages，user 角色转 UserMessage，assistant 角色转 AssistantMessage
2. **自动创建对话**: 前端首次发消息时自动创建对话，标题取用户问题前 20 字符
3. **消息持久化**: 用户消息在发送时保存，机器人消息通过流式完成后保存（需前端配合）

## 风险提示

1. 机器人回复的完整内容需要前端拼接后保存，当前实现中流式输出是增量的
2. 对话列表没有分页，大量对话时可能需要优化

## 下一阶段预研

- [ ] 工具注册中心完善
- [ ] Function Calling 集成
- [ ] ReAct 推理循环验证
- [ ] MCP 协议适配
