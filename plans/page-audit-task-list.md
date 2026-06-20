# 全量页面功能修复任务清单

## 审计结果总览（基于 Puppeteer 自动化实测）

| 状态 | 数量 | 说明 |
|------|------|------|
| ✅ 正常工作 | 16 | 有数据或正常显示 |
| 📭 空表（正常） | 20 | 表存在但无数据（用户未创建内容） |
| ⚠️ 路由问题 | 1 | 路由指向占位页面 |
| ❌ 后端错误 | 1 | API 返回 500 错误 |
| 🔧 需要服务 | 1 | 需要 Neo4j 服务 |

---

## 一、路由问题（1个）

### T1.1 KacHorizontal（横向通用应用）
- **URL**: `/kac/horizontal`
- **现象**: 显示"功能未开发"
- **根因**: `system_menu` 表中 `menu_id=2407` 的 `component` 字段指向 `system/developing/index`
- **修复**: 
  ```sql
  UPDATE system_menu SET component = 'kac/horizontal/index' WHERE menu_id = 2407;
  ```
- **注意**: 需要确认 `frontend/src/views/kac/horizontal/index.vue` 文件存在

### T1.2 其他指向 developing 的菜单
- **menu_id=2079** (产品动态) - 保持 developing（确实是未开发功能）
- **menu_id=2408** (纵向行业应用) - 保持 developing（确实是未开发功能）
- **menu_id=2410** (我的应用) - 保持 developing（确实是未开发功能）

---

## 二、后端错误（1个）

### T2.1 KgGraph（知识图谱探索）
- **URL**: `/kg/graph`
- **现象**: 页面显示"暂无数据"，API 返回 `No static resource ext/schema/list.`
- **根因**: `ext` 模块被排除在组件扫描之外（因为依赖 Neo4j）
- **修复选项**:
  1. **方案A**: 重新启用 ext 模块，但排除 Neo4j 相关的 Repository
  2. **方案B**: 保持禁用，在前端显示友好的"需要 Neo4j 服务"提示
  3. **方案C**: 创建独立的 ext 控制器，不依赖 Neo4j

---

## 三、空表页面（20个 — 正常状态）

以下页面表格为空是因为用户尚未创建相关内容，**无需修复**：

### 知识管理类
- KnowledgeBase（知识库） - `kmc_knowledge_base` 表为空
- KgCategory（知识分类） - `kg_knowledge_category` 表为空
- KgDocument（知识文件） - `kg_knowledge_document` 表为空

### Bot/Agent 类
- BotWorkflow（工作流） - `kb_bot` 表为空
- BotChatflow（Chatflow） - `kb_bot` 表为空
- AgentBuild（Agent 编排） - `kb_agent_config` 表为空
- ToolMgmt（工具管理） - `kb_tool` 表为空

### AI 模型类
- AIModelMarket（模型市场） - `ai_model` 表为空
- AIModelMy（我的模型） - `ai_model` 表为空

### 知识抽取类
- ExtSchema（概念配置） - `ext_schema` 表为空
- ExtRelation（关系配置） - `ext_schema_relation` 表为空
- ExtStructTask（结构化抽取） - `ext_struct_task` 表为空
- ExtUnstructTask（非结构化抽取） - `ext_unstruct_task` 表为空
- ExtTaskLog（抽取日志） - `ext_task_log` 表为空

### 应用中心类
- KacOverview（应用概览） - 无应用数据
- KacMySolution（我的解决方案） - 无解决方案数据
- PluginCenter（插件中心） - 无插件数据

### 看板类
- KnowledgeAsset（知识资产看板） - 无知识资产数据
- BotOperation（Bot 运营看板） - 无 Bot 运营数据
- AppOperations（应用运营看板） - 无应用运营数据

### 其他
- DmDatasource（数据源） - `dm_datasource` 表为空
- UserProfile（个人中心） - 用户信息正常，扩展字段为空

---

## 四、正常工作的页面（16个）

- Dashboard（综合看板） ✅
- SystemUser（用户管理） ✅
- SystemRole（角色管理） ✅
- SystemMenu（菜单管理） ✅
- SystemDept（部门管理） ✅
- SystemPost（岗位管理） ✅
- SystemDict（字典管理） ✅
- SystemConfig（参数设置） ✅
- SystemNotice（通知公告） ✅
- SystemMessage（我的消息） ✅
- SystemJob（定时任务） ✅
- OperLog（操作日志） ✅
- Logininfor（登录日志） ✅
- OnlineUser（在线用户） ✅
- ServerMonitor（服务监控） ✅
- CacheMonitor（缓存监控） ✅

---

## 五、修复优先级

### P0（必须修复 — 1个）
1. **T1.1** KacHorizontal 路由修复

### P1（建议修复 — 1个）
2. **T2.1** KgGraph 后端错误处理

### P2（可选优化 — 数据初始化）
3. 为关键空表插入示例数据（可选）
   - 知识库示例数据
   - AI 模型示例数据
   - 工具示例数据

---

## 六、修复方法

### 方法一：路由修复（T1.1）
```sql
-- 修复横向通用应用路由
UPDATE system_menu SET component = 'kac/horizontal/index' WHERE menu_id = 2407;
```

### 方法二：后端错误处理（T2.1）
在前端添加友好提示，而不是显示"暂无数据"：
```vue
<template v-if="!neo4jEnabled">
  <el-empty description="知识图谱功能需要 Neo4j 服务支持">
    <el-button type="primary" @click="showSetupGuide">查看配置指南</el-button>
  </el-empty>
</template>
```

### 方法三：数据初始化（P2）
从 MySQL 初始化脚本提取示例数据，转换为 PostgreSQL 格式插入。

---

## 七、验证方法

1. 使用 Puppeteer 自动化测试每个页面
2. 检查页面是否显示"开发中"消息
3. 检查表格是否有数据或友好的空状态提示
4. 检查是否有 JS 错误
5. 检查 API 响应是否正常（200 状态码）
