# Agent 前端验收指南

> 基于实际前后端代码的详细验收说明。每个功能点均标注了对应的源文件和行号。

---

## 一、页面入口与布局

### 1.1 页面路由

前端路由入口在 `frontend/src/views/kb/bot/index.vue:460-477`，当 URL 包含 `/agent` 时切换到 Agent 视图。

**验收方法**：访问 `http://localhost/kb/bot/agent?id=2`（id 为 botId），确认页面正常加载。

### 1.2 页面布局结构

文件：`frontend/src/views/kb/agent/index.vue:1-206`

页面分为左右两栏：
- **左侧（el-aside）**：Agent 编排面板，包含模型选择、提示词、知识库、工具配置
- **右侧（el-main）**：调试与预览面板，包含对话列表、消息展示、输入框

**验收方法**：确认左右两栏均正常显示，左侧宽度约 500px，右侧占据剩余空间。

---

## 二、Agent 配置功能

### 2.1 模型选择

前端：`index.vue:13-31` — 使用 `el-select` 展示模型列表
API：`frontend/src/api/ai/myModel/myModel.js` — `getChatModelDict()` 获取模型字典
后端：`KbAgentConfigController.java:98-123` — `GET /kb/agent/byBot/{botId}` 获取配置

**数据流**：
1. 页面加载时调用 `loadAgentConfig()`（`index.vue:345`）
2. `getConfigByBotId(botId)` 获取已保存配置（`index.vue:352`）
3. `getModelList()` 获取可用模型列表（`index.vue:408`）
4. `watchEffect` 自动将选中的 modelName 映射到 modelId（`index.vue:272-283`）

**验收方法**：
1. 打开 Agent 页面，确认模型下拉框正常加载
2. 选择一个模型，确认 modelId 自动更新
3. 保存后刷新页面，确认模型选择被正确恢复

### 2.2 提示词配置

前端：`index.vue:39-49` — `el-input` textarea，最大 1024 字符
后端：`KbAgentConfigSaveReqVO.java:37` — `prePrompt` 字段

**验收方法**：输入提示词，保存后刷新，确认内容被正确恢复。

### 2.3 知识库关联

前端：`index.vue:82-105` — 知识库选择和展示
组件：`frontend/src/views/kmc/knowledgeBase/selection/knowledgeBaseMultiple.vue` — 多选弹窗
后端：`KbAgentConfigSaveReqVO.java:44` — `knowledgeIds` 字段（逗号分隔）

**数据流**：
1. 点击「导入知识库」按钮（`index.vue:84`）→ 打开多选弹窗
2. 选择后触发 `handleKnowledgeConfirm`（`index.vue:433`）
3. 保存时将 `knowledges` 数组转为逗号分隔的 ID 字符串（`index.vue:641`）

**验收方法**：
1. 点击「导入知识库」，确认弹窗正常打开
2. 选择多个知识库，确认表格正确显示
3. 点击删除按钮，确认知识库被移除
4. 保存后刷新，确认知识库关联被正确恢复

### 2.4 工具关联

前端：`index.vue:108-128` — 工具选择和展示
组件：`frontend/src/views/kb/tool/selection/method-multiple-selection.vue` — 多选弹窗
后端：`KbAgentConfigSaveReqVO.java:52` — `toolMethodIds` 字段（逗号分隔）

**验收方法**：同知识库关联，确认工具的选择、展示、删除、保存功能正常。

### 2.5 配置保存

前端：`index.vue:620-659` — `handleSubmit()` 函数
API：`frontend/src/api/kb/agent/config.js:29-44` — `addConfig()` / `updateConfig()`
后端：`KbAgentConfigController.java:125-142` — `POST /kb/agent` / `PUT /kb/agent`

**请求体结构**（`KbAgentConfigSaveReqVO.java`）：
```json
{
  "id": null,           // 新增时为 null，修改时为配置 ID
  "botId": 2,           // Bot ID
  "modelConfig": "{\"modelId\":1,\"modelName\":\"deepseek-chat\"}",
  "prePrompt": "你是...",
  "parameters": "{}",
  "knowledgeIds": "1,7",  // 逗号分隔的知识库 ID
  "toolMethodIds": "",     // 逗号分隔的工具方法 ID
  "remark": ""
}
```

**验收方法**：
1. 填写配置后点击「保存」，确认提示「保存成功」
2. 修改配置后点击「保存」，确认提示「更新成功」
3. 刷新页面，确认所有配置被正确恢复

---

## 三、对话管理功能

### 3.1 对话列表

前端：`index.vue:141-169` — 对话选择器和操作按钮
API：`frontend/src/api/kb/conversation/index.js:3-9` — `getConversations(botId, workspaceId)`
后端：`KbConversationController.java:41-48` — `GET /kb/conversation/list?botId=&workspaceId=`

**验收方法**：
1. 保存 Agent 配置后，确认对话选择器加载已有对话
2. 确认对话列表显示正确

### 3.2 创建新对话

前端：`index.vue:294-308` — `handleNewConversation()`
API：`frontend/src/api/kb/conversation/index.js:11-17` — `createConversation(data)`
后端：`KbConversationController.java:50-56` — `POST /kb/conversation`

**请求体**：
```json
{
  "botId": 2,
  "workspaceId": 1001,
  "title": "新对话 16:30:00"
}
```

**验收方法**：
1. 点击「新对话」按钮，确认新对话被创建
2. 确认对话选择器自动切换到新对话
3. 确认消息列表被清空

### 3.3 切换对话

前端：`index.vue:312-329` — `handleConversationChange(conversationId)`
API：`frontend/src/api/kb/conversation/index.js:26-31` — `getMessages(conversationId)`
后端：`KbConversationController.java:66-71` — `GET /kb/conversation/{conversationId}/messages`

**验收方法**：
1. 从下拉框选择一个已有对话
2. 确认消息列表加载该对话的历史消息
3. 确认消息类型正确（type=0 用户消息居右，type=1 机器人消息居左）

### 3.4 删除对话

前端：`index.vue:332-342` — `handleDeleteConversation()`
API：`frontend/src/api/kb/conversation/index.js:19-24` — `deleteConversation(id)`
后端：`KbConversationController.java:58-64` — `DELETE /kb/conversation/{id}`

**验收方法**：
1. 选择一个对话，点击「删除」按钮
2. 确认弹出确认对话框
3. 确认后，确认对话从列表中移除
4. 确认消息列表被清空

---

## 四、Agent 对话功能（核心）

### 4.1 消息发送

前端：`index.vue:468-592` — `handleSendMessage()` → `doSendMessage()`
组件：`frontend/src/views/kb/agent/components/ChatInput.vue` — 输入框组件
API：`frontend/src/api/kb/agent/debug.js:5-22` — `debugAgent()` 使用 `fetchEventSource` SSE

**请求体**（`KbAgentConfigReqVO.java`）：
```json
{
  "conversationId": 1,
  "botId": 2,
  "workspaceId": 1001,
  "question": "OTA固件升级做了什么",
  "input": null,
  "modelConfig": "{\"modelId\":1,\"modelName\":\"deepseek-chat\"}",
  "prePrompt": "你是...",
  "parameters": "{}",
  "knowledgeIds": "1",
  "toolMethodIds": ""
}
```

**数据流**：
1. 用户输入问题，点击「发送」或按 Enter（`ChatInput.vue:72`）
2. 前端添加用户消息到消息列表（`index.vue:493-498`）
3. 前端创建机器人消息占位，显示「思考中」（`index.vue:510-516`）
4. 前端调用 `debugAgent()` 发起 SSE 请求（`index.vue:547`）
5. 后端 `KbAgentConfigController.java:153-166` 接收请求，调用 `chatMessage()`
6. 后端 `KbAgentConfigServiceImpl.java:200-349` 处理请求：
   - 解析模型配置（行 201-229）
   - 检查语义缓存（行 232-235）
   - 预检索 RAG 上下文（行 238-276）
   - 获取工具方法（行 279-287）
   - 构建 gRPC 请求（行 304-319）
   - 构造 memory_recall 事件（行 322-333）
   - 调用 Hermes 微服务（行 338-340）
7. 前端 `onmessage` 回调接收流式数据（`index.vue:550-570`）

**验收方法**：
1. 输入问题，点击「发送」
2. 确认用户消息立即显示在右侧（蓝色气泡）
3. 确认机器人消息开始流式显示在左侧（灰色气泡）
4. 确认流式输出完成后加载状态消失
5. 确认消息自动滚动到底部

### 4.2 SSE 事件类型

后端返回的 SSE 数据格式（`KbChatMessageSendRespVO.java`）：
```json
{
  "code": 200,
  "data": {
    "send": { "type": 0, "content": "用户问题" },
    "receive": { 
      "type": 1, 
      "eventType": "text|tool_call|memory_recall",
      "content": "回复内容",
      "toolName": "工具名称",
      "toolStatus": "success|error"
    }
  }
}
```

**事件类型说明**：

| eventType | 说明 | 触发时机 |
|-----------|------|----------|
| `text` | 普通文本回复 | LLM 生成的流式文本块 |
| `tool_call` | 工具调用 | Hermes 执行工具时 |
| `memory_recall` | 记忆召回 | Agent 调用开始前，展示 RAG 召回结果 |

#### 4.2.1 text 事件

前端处理：`index.vue:550-570` — 累加 `botContent` 并更新消息
后端生成：`HermesGrpcClient.java:101-107` — 转换 gRPC `StreamingChunk`

**验收方法**：发送消息后，确认机器人回复正常流式显示。

#### 4.2.2 tool_call 事件

前端处理：`MessageList.vue:59-90` — 工具调用消息组件
后端生成：`HermesGrpcClient.java:108-117` — 转换 gRPC `ToolInvoked`

**前端展示**：
- 工具名称（`item.toolName`）
- 工具状态标签（success=绿色，error=红色，执行中=黄色）
- 可展开的详情面板（输入、输出、耗时）

**验收方法**：触发工具调用时，确认工具调用卡片正确显示，点击可展开详情。

#### 4.2.3 memory_recall 事件

前端处理：`MessageList.vue:92-113` — 记忆召回消息组件
后端生成：`KbAgentConfigServiceImpl.java:322-333` — 构造 memory_recall 事件

**前端展示**：
- 橙色卡片，显示「召回 N 条记忆」
- 每条记忆显示内容和相似度分数（sim）/ 衰减分数（decay）

**验收方法**：发送与知识库相关的问题，确认 memory_recall 事件正确显示。

### 4.3 消息列表组件

文件：`frontend/src/views/kb/agent/components/MessageList.vue`

**消息类型渲染**：
- `type === 0`：用户消息，靠右蓝色气泡（行 45-57）
- `type === 1`：机器人消息，靠左灰色气泡，使用 MarkdownView 渲染（行 6-43）
- `type === 'tool_call'`：工具调用卡片（行 59-90）
- `type === 'memory_recall'`：记忆召回卡片（行 92-113）

**验收方法**：确认所有消息类型正确渲染，Markdown 内容正确显示。

---

## 五、知识库召回预览功能

### 5.1 召回预览入口

前端：`index.vue:86-87` — 「召回预览」按钮，仅在有关联知识库时显示
组件：`frontend/src/views/kb/agent/components/AgentRecallPreview.vue`

**验收方法**：关联知识库后，确认「召回预览」按钮出现。

### 5.2 召回测试

前端：`AgentRecallPreview.vue:104-143` — `handleTest()` 函数
API：`frontend/src/api/kmc/knowledgeBase/knowledgeBase.js:105-111` — `recallDebug(data)`
后端：`KmcKnowledgeBaseController.java:160-166` — `POST /kmc/knowledgeBase/recallDebug`

**请求体**：
```json
{
  "id": 1,           // 知识库 ID
  "query": "OTA固件升级"  // 测试查询
}
```

**响应体**（`RecallDebugRespVO.java`）：
```json
{
  "code": 200,
  "data": {
    "results": [
      {
        "id": "segment-id",
        "documentId": "doc-id",
        "documentName": "Day04-OTA固件升级开发",
        "content": "匹配的内容...",
        "score": 1.0164,
        "source": "vector"
      }
    ],
    "debugInfo": {
      "queryEnhance": { "strategy": "none", "originalQuery": "..." },
      "contextBytes": 3724,
      "maxContextBytes": 12000,
      "excludedPaths": [],
      "elapsedMs": 150,
      "searchMethod": "hybrid_search",
      "vectorResultCount": 3,
      "keywordResultCount": 0,
      "metadataResultCount": 0,
      "fusedCount": 3,
      "rerankedCount": 3
    },
    "contextPreview": "最终注入上下文的预览文本..."
  }
}
```

**验收方法**：
1. 点击「召回预览」按钮，确认抽屉打开
2. 输入测试问题，点击「测试召回」
3. 确认结果按知识库分 Tab 显示
4. 确认每条结果显示：文档名称、来源标签、得分
5. 确认调试信息正确显示：耗时、检索模式、各路召回数量
6. 确认「最终注入上下文预览」正确显示

---

## 六、健康检查功能

### 6.1 健康检查 API

API：前端未直接调用，通过 curl 测试
后端：`HealthCheckController.java:29-46` — `GET /kb/health`

**响应体**：
```json
{
  "code": 200,
  "data": {
    "database": { "status": "ok", "url": "jdbc:postgresql://...", "driver": "PostgreSQL" },
    "mcp": { "servers": 0, "tools": 0, "details": [] },
    "langfuse": { "enabled": false, "baseUrl": "https://cloud.langfuse.com" },
    "system": { "javaVersion": "21", "osName": "Mac OS X", ... }
  }
}
```

**验收方法**：
```bash
TOKEN=$(curl -s -X POST "http://localhost:8099/login" -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))")
curl -s "http://localhost:8099/kb/health" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

---

## 七、审批功能

### 7.1 审批代理

前端：`frontend/src/views/kb/agent/components/ApprovalDialog.vue`
API：使用 `request` 直接调用（`ApprovalDialog.vue:74-78, 93-99`）
后端：`ApprovalProxyController.java` — `/hermes/approval/*`

**端点列表**：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/hermes/approval/pending` | 获取待审批列表 |
| POST | `/hermes/approval/approve` | 审批通过 |
| POST | `/hermes/approval/reject` | 审批拒绝 |
| POST | `/hermes/approval/dev/create` | 创建测试审批数据（仅 dev） |

**验收方法**：
```bash
# 获取待审批列表
curl -s "http://localhost:8099/hermes/approval/pending" -H "Authorization: Bearer $TOKEN"

# 创建测试审批数据
curl -s -X POST "http://localhost:8099/hermes/approval/dev/create" -H "Authorization: Bearer $TOKEN"

# 审批通过
curl -s -X POST "http://localhost:8099/hermes/approval/approve" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"flowId":"test-flow-xxx","requestId":"req-xxx","nodeId":"approval-node-1"}'
```

---

## 八、前后端通信架构总览

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Vue 3)                              │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │  Agent 页面   │  │  消息列表     │  │  召回预览             │   │
│  │  index.vue   │  │  MessageList  │  │  AgentRecallPreview  │   │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘   │
│         │                 │                      │               │
│         ▼                 ▼                      ▼               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │  debug.js    │  │  config.js   │  │  knowledgeBase.js    │   │
│  │  (SSE)       │  │  (REST)      │  │  (REST)              │   │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘   │
└─────────┼─────────────────┼──────────────────────┼───────────────┘
          │                 │                      │
          ▼                 ▼                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                     后端 (Spring Boot)                           │
│                                                                  │
│  ┌──────────────────────┐  ┌─────────────────────────────────┐  │
│  │  KbAgentConfig       │  │  KmcKnowledgeBase               │  │
│  │  Controller          │  │  Controller                     │  │
│  │  /kb/agent/*         │  │  /kmc/knowledgeBase/*           │  │
│  └──────────┬───────────┘  └────────────────┬────────────────┘  │
│             │                               │                    │
│             ▼                               ▼                    │
│  ┌──────────────────────┐  ┌─────────────────────────────────┐  │
│  │  KbAgentConfig       │  │  KmcKnowledgeBase               │  │
│  │  ServiceImpl         │  │  ServiceImpl                    │  │
│  │  chatMessage()       │  │  recallDebug()                  │  │
│  └──────────┬───────────┘  └────────────────┬────────────────┘  │
│             │                               │                    │
│             ▼                               ▼                    │
│  ┌──────────────────────┐  ┌─────────────────────────────────┐  │
│  │  HermesGrpcClient    │  │  RagRetrievalService            │  │
│  │  (gRPC)              │  │  retrieve()                     │  │
│  └──────────┬───────────┘  └────────────────┬────────────────┘  │
└─────────────┼───────────────────────────────┼────────────────────┘
              │                               │
              ▼                               ▼
┌──────────────────────┐  ┌─────────────────────────────────────┐
│  Hermes 微服务       │  │  PostgreSQL (vector_store)          │
│  :9090 (gRPC)        │  │  :5432                              │
│  AgentOrchestrator   │  │  PgVector 向量检索                   │
└──────────────────────┘  └─────────────────────────────────────┘
```

---

## 九、验收检查清单

### 9.1 基础功能

- [ ] 页面加载正常，左右两栏布局正确
- [ ] 模型下拉框加载可用模型
- [ ] 提示词输入框正常工作
- [ ] 知识库选择、删除、保存正常
- [ ] 工具选择、删除、保存正常
- [ ] 配置保存后刷新页面能正确恢复

### 9.2 对话管理

- [ ] 新建对话正常
- [ ] 对话列表正确加载
- [ ] 切换对话正确加载历史消息
- [ ] 删除对话正常

### 9.3 Agent 对话

- [ ] 发送消息后用户消息立即显示
- [ ] 机器人回复流式显示
- [ ] 流式完成后加载状态消失
- [ ] 消息自动滚动到底部
- [ ] Enter 发送，Shift+Enter 换行

### 9.4 SSE 事件

- [ ] text 事件：文本回复正常显示
- [ ] tool_call 事件：工具调用卡片正确显示
- [ ] memory_recall 事件：记忆召回卡片正确显示

### 9.5 召回预览

- [ ] 召回预览按钮在有关联知识库时显示
- [ ] 抽屉正常打开
- [ ] 测试召回返回结果
- [ ] 结果按知识库分 Tab 显示
- [ ] 调试信息正确显示

### 9.6 审批功能

- [ ] 待审批列表接口正常
- [ ] 创建测试审批数据正常
- [ ] 审批通过/拒绝正常

### 9.7 健康检查

- [ ] 数据库状态为 ok
- [ ] MCP 服务数量正确
- [ ] LangFuse 状态正确
- [ ] 系统信息正确

---

## 十、常用验收命令

```bash
# 登录获取 Token
TOKEN=$(curl -s -X POST "http://localhost:8099/login" -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))")

# 健康检查
curl -s "http://localhost:8099/kb/health" -H "Authorization: Bearer $TOKEN"

# 召回测试
curl -s -X POST "http://localhost:8099/kmc/knowledgeBase/recallDebug" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"id":1,"query":"OTA固件升级","topK":3}'

# Agent 对话测试（SSE）
curl -s -X POST "http://localhost:8099/kb/agent/testChatMessages" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"botId":2,"question":"OTA固件升级做了什么","modelConfig":"{\"modelId\":1,\"modelName\":\"deepseek-chat\"}","knowledgeIds":"1"}'

# 审批端点测试
curl -s "http://localhost:8099/hermes/approval/pending" -H "Authorization: Bearer $TOKEN"
curl -s -X POST "http://localhost:8099/hermes/approval/dev/create" -H "Authorization: Bearer $TOKEN"
```
