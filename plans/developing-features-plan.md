# 开发计划书：修复所有"开发中"功能

## 一、现状分析

### 1.1 审计结果

经过 Puppeteer 自动化测试 43 个页面，发现以下"开发中"页面：

| 页面 | URL | 实际状态 | 根因 |
|------|-----|----------|------|
| SystemNotice | `/system/notice` | ✅ 正常（空表） | 无数据，非功能缺失 |
| SystemMessage | `/bases/message` | ✅ 正常（空表） | 无数据，非功能缺失 |
| SystemJob | `/monitor/job` | ✅ 正常（空表） | 无数据，非功能缺失 |
| GenTable | `/tool/gen` | ✅ 正常（空表） | 无数据，非功能缺失 |
| **KacHorizontal** | `/kac/horizontal` | ⚠️ 占位页面 | 路由指向 `system/developing/index` |
| **KgGraph** | `/kg/graph` | ❌ 后端错误 | ext 模块被排除（依赖 Neo4j） |

### 1.2 结论

- **4 个页面**：正常工作，只是没有数据（用户未创建内容）
- **1 个页面**：路由配置错误，指向占位页面
- **1 个页面**：后端模块被排除，需要替代方案

---

## 二、开发计划

### 阶段一：路由修复（KacHorizontal）

**目标**：修复横向通用应用页面的路由配置

**任务清单**：
1. 检查 `frontend/src/views/kac/horizontal/index.vue` 文件是否存在
2. 如果存在，更新数据库菜单配置：
   ```sql
   UPDATE system_menu SET component = 'kac/horizontal/index' WHERE menu_id = 2407;
   ```
3. 如果不存在，创建基础页面组件
4. 验证页面正常加载

**预计工时**：0.5 小时

---

### 阶段二：知识图谱替代方案（KgGraph）

**目标**：在不依赖 Neo4j 的情况下实现知识图谱功能

**方案选择**：
- **方案 A**：使用 PostgreSQL + 邻接表实现轻量级图存储
- **方案 B**：使用纯前端 D3.js 可视化 + JSON 数据
- **方案 C**：保持当前状态，显示友好的"需要 Neo4j 服务"提示

**推荐方案**：方案 A（PostgreSQL 邻接表）

**任务清单**：
1. 创建图数据表结构：
   ```sql
   CREATE TABLE kg_node (
     id BIGSERIAL PRIMARY KEY,
     workspace_id BIGINT NOT NULL,
     label VARCHAR(128) NOT NULL,
     type VARCHAR(64),
     properties JSONB,
     valid_flag SMALLINT DEFAULT 0,
     del_flag SMALLINT DEFAULT 0,
     create_by VARCHAR(32),
     create_time TIMESTAMP DEFAULT NOW(),
     update_by VARCHAR(32),
     update_time TIMESTAMP DEFAULT NOW()
   );
   
   CREATE TABLE kg_edge (
     id BIGSERIAL PRIMARY KEY,
     workspace_id BIGINT NOT NULL,
     source_id BIGINT NOT NULL,
     target_id BIGINT NOT NULL,
     label VARCHAR(128),
     properties JSONB,
     valid_flag SMALLINT DEFAULT 0,
     del_flag SMALLINT DEFAULT 0,
     create_by VARCHAR(32),
     create_time TIMESTAMP DEFAULT NOW(),
     update_by VARCHAR(32),
     update_time TIMESTAMP DEFAULT NOW()
   );
   ```

2. 创建后端 API：
   - `GET /kg/graph/nodes` - 获取节点列表
   - `GET /kg/graph/edges` - 获取边列表
   - `POST /kg/graph/nodes` - 创建节点
   - `POST /kg/graph/edges` - 创建边
   - `GET /kg/graph/search` - 图搜索

3. 修改前端 KgGraph 页面：
   - 移除 ext 模块依赖
   - 使用 D3.js 或 vis-network 渲染图谱
   - 实现节点/边的 CRUD 操作

4. 从 PDF 内容中提取知识图谱数据：
   - 提取 Flutter 技术概念（节点）
   - 提取概念间关系（边）
   - 导入数据库

**预计工时**：4 小时

---

### 阶段三：数据初始化（所有空表）

**目标**：为所有空表插入示例数据，确保没有空白页面

**任务清单**：

#### 3.1 知识库数据（已完成）
- ✅ 1 个知识库：盛趣游戏实习工作日志
- ✅ 19 个文档
- ✅ 165 个分段

#### 3.2 AI 模型数据（已完成）
- ✅ 10 个 AI 模型
- ✅ 5 个 API Key

#### 3.3 Bot 数据（已完成）
- ✅ 4 个 Bot
- ✅ Agent 配置

#### 3.4 工具数据（已完成）
- ✅ 5 个工具
- ✅ 5 个工具方法

#### 3.5 通知公告数据
```sql
INSERT INTO system_notice (notice_id, notice_title, notice_type, notice_content, status, create_by, create_time)
VALUES 
  (1, '系统上线通知', 1, 'qKnow 知识平台已成功上线，欢迎使用！', 0, 'admin', NOW()),
  (2, '功能更新说明', 1, '本次更新包含知识库管理、Agent 配置、工具管理等功能。', 0, 'admin', NOW()),
  (3, '安全提醒', 2, '请定期修改密码，确保账户安全。', 0, 'admin', NOW());
```

#### 3.6 定时任务数据
```sql
INSERT INTO system_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time)
VALUES 
  (1, '知识库同步任务', 'SYSTEM', 'knowledgeSyncTask.sync()', '0 0 2 * * ?', 3, 1, 1, 'admin', NOW()),
  (2, '日志清理任务', 'SYSTEM', 'logCleanTask.clean()', '0 0 3 * * ?', 3, 1, 1, 'admin', NOW()),
  (3, '数据备份任务', 'SYSTEM', 'backupTask.backup()', '0 0 4 * * ?', 3, 1, 1, 'admin', NOW());
```

**预计工时**：1 小时

---

### 阶段四：代码生成器配置（GenTable）

**目标**：配置代码生成器，使其可以正常使用

**任务清单**：
1. 确认代码生成器模块正常加载
2. 导入示例表配置：
   ```sql
   INSERT INTO gen_table (table_id, table_name, table_comment, class_name, tpl_category, package_name, module_name, business_name, function_name, function_author, gen_type, gen_path, create_by, create_time)
   VALUES 
     (1, 'demo_product', '产品示例表', 'DemoProduct', 'crud', 'tech.qiantong.qknow.module.demo', 'demo', 'product', '产品管理', 'admin', 0, '/', 'admin', NOW());
   ```

3. 验证代码生成功能

**预计工时**：0.5 小时

---

## 三、实施计划

### 3.1 执行顺序

```
阶段一（路由修复）→ 阶段三（数据初始化）→ 阶段四（代码生成器）→ 阶段二（知识图谱）
```

### 3.2 并行执行

- 阶段一和阶段三可以并行执行
- 阶段四依赖阶段三完成
- 阶段二独立执行

### 3.3 验证方法

每个阶段完成后，使用 Puppeteer 自动化测试验证：
1. 页面无 JS 错误
2. 页面无"开发中"消息
3. 数据正确显示
4. API 正常响应

---

## 四、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Neo4j 依赖复杂 | 知识图谱功能受限 | 使用 PostgreSQL 邻接表替代 |
| 数据表结构不匹配 | 数据插入失败 | 先检查表结构，再插入数据 |
| 前端组件缺失 | 页面无法渲染 | 检查组件文件是否存在 |

---

## 五、验收标准

1. **所有页面无"开发中"消息**
2. **所有页面无 JS 错误**
3. **所有页面有数据或友好的空状态提示**
4. **所有 API 正常响应（200 状态码）**
5. **知识图谱功能可用（基于 PostgreSQL）**

---

## 六、预计总工时

| 阶段 | 工时 | 依赖 |
|------|------|------|
| 阶段一：路由修复 | 0.5 小时 | 无 |
| 阶段二：知识图谱 | 4 小时 | 无 |
| 阶段三：数据初始化 | 1 小时 | 无 |
| 阶段四：代码生成器 | 0.5 小时 | 阶段三 |
| **总计** | **6 小时** | |

---

## 七、技术栈

- **后端**：Spring Boot 3 + MyBatis-Plus + PostgreSQL
- **前端**：Vue 3 + Element Plus + D3.js/vis-network
- **数据库**：PostgreSQL + PgVector
- **测试**：Puppeteer 自动化测试

---

## 八、交付物

1. 修复后的路由配置
2. 知识图谱模块代码
3. 初始化数据 SQL 脚本
4. Puppeteer 测试报告
5. 更新后的文档
