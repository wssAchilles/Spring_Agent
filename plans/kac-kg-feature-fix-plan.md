# 应用中心 & 知识图谱功能修复方案

> 基于截图分析 + 代码审计，整理所有"功能未开发"问题的完整修复方案，供复核。

---

## 一、问题总览

### 截图中发现的问题

| # | 页面 | URL | 问题描述 | 严重度 |
|---|------|-----|----------|--------|
| 1 | 横向通用应用 | `/kac/horizontal` | API 报错 `No static resource kac/apply/list.`，表格为空 | P0 |
| 2 | 概览 | `/kac/overview` | 顶部提示"功能正在开发中"，应用图标为占位符，状态显示 `● --` | P0 |
| 3 | 纵向行业应用 | `/kac/vertical` | 路由指向 `system/developing/index` 占位页面 | P1 |
| 4 | 我的应用 | `/kac/myApp` | 路由指向 `system/developing/index` 占位页面 | P1 |
| 5 | 我的解决方案 | `/kac/mySolution` | 路由指向占位页面（但前端组件已存在） | P1 |
| 6 | 解决方案 | `/kac/solution` | 路由指向占位页面 | P2 |
| 7 | 知识图谱 | `/kg/graph` | API 报错 `No static resource ext/schema/list.` | P1 |

### 根因分析

```
问题链路：
  前端页面存在 → 调用 /kac/apply/list API → 后端无对应 Controller → 404 报错
  菜单 component 字段 → 指向 system/developing/index → 显示"功能开发中"
  ext 模块被 @ComponentScan 排除 → 所有 /ext/* 接口不可用 → 知识图谱报错
```

---

## 二、架构现状

### 2.1 模块排除情况

`QKnowApplication.java` 中排除了 3 个模块：

```java
excludeFilters = @Filter(type = FilterType.REGEX, pattern = {
    "tech\\.qiantong\\.qknow\\.neo4j\\..*",       // Neo4j 框架
    "tech\\.qiantong\\.qknow\\.module\\.app\\..*", // 应用模块
    "tech\\.qiantong\\.qknow\\.module\\.ext\\..*"  // 知识抽取模块
})
```

### 2.2 前端 API 与后端 Controller 对照

| 前端 API 文件 | 调用路径 | 后端 Controller | 状态 |
|--------------|----------|----------------|------|
| `api/kac/apply/apply.js` | `/kac/apply/*` | ❌ 不存在 | **缺失** |
| `api/kac/applyBot/applyBot.js` | `/kac/bot/*` | ❌ 不存在 | **缺失** |
| `api/kac/applyGraph/applyGraph.js` | `/kac/graph/*` | ❌ 不存在 | **缺失** |
| `api/kac/applyKnowledge/applyKnowledge.js` | `/kac/knowledge/*` | ❌ 不存在 | **缺失** |
| `api/kac/plugin/plugin.js` | `/kac/plugin/*` | ❌ 不存在 | **缺失** |
| `api/kac/solution/solution.js` | `/kac/solution/*` | ❌ 不存在 | **缺失** |
| `api/kac/solution/solutionApply.js` | `/kac/solutionApply/*` | ❌ 不存在 | **缺失** |
| `api/ext/extSchema/schema.js` | `/ext/schema/*` | ✅ 存在但未加载 | **被排除** |

### 2.3 前端组件与菜单对照

| 菜单 | menu_id | component 字段 | 前端组件是否存在 | 需要操作 |
|------|---------|---------------|----------------|---------|
| 概览 | 2405 | `kac/overview/index` | ✅ 存在 | 修复"开发中"提示 |
| 解决方案 | 2406 | `system/developing/index` | ❌ 不存在 | 新建组件 |
| 横向通用应用 | 2407 | `system/developing/index`(MySQL) | ✅ 存在 | 修复路由 + 补后端 |
| 纵向行业应用 | 2408 | `system/developing/index` | ❌ 不存在 | 新建组件 |
| 我的解决方案 | 2409 | `system/developing/index` | ✅ 存在 | 修复路由 + 补后端 |
| 我的应用 | 2410 | `system/developing/index` | ❌ 不存在 | 新建组件 |

---

## 三、修复方案

### 阶段一：修复已有组件的路由配置（P0，预计 0.5h）

**目标**：让已有前端组件能正确加载

**任务**：

#### T1.1 修复横向通用应用路由（menu_id=2407）
```sql
-- MySQL
UPDATE system_menu SET component = 'kac/horizontal/index' WHERE menu_id = 2407;
-- PostgreSQL 已在 02-init-data.sql 中修复
```

#### T1.2 修复我的解决方案路由（menu_id=2409）
```sql
UPDATE system_menu SET component = 'kac/mySolution/index' WHERE menu_id = 2409;
```

#### T1.3 验证
- 访问 `/kac/horizontal` 确认页面加载（API 报错属于阶段二）
- 访问 `/kac/mySolution` 确认页面加载

---

### 阶段二：创建 KAC 后端 Controller（P0，预计 3h）

**目标**：为前端 API 提供后端接口，消除 `No static resource` 报错

**方案选择**：

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| **A：在 qknow-module-app 中创建 Controller** | 重新启用 app 模块，新建 KAC 相关 Controller | 代码组织清晰，复用现有模块 | 需要从 ComponentScan 排除中移除 app 模块 |
| **B：在 qknow-module-kb 中创建 Controller** | 在已启用的 kb 模块中新增 kac 包 | 无需修改 ComponentScan | 知识库模块职责过重 |
| **C：创建独立 qknow-module-kac 模块** | 新建独立 Maven 模块 | 完全解耦 | 改动大，需修改构建配置 |

**推荐方案：A** — 在 `qknow-module-app` 中创建，同时从排除列表移除 app 模块。

**任务**：

#### T2.1 从 ComponentScan 中移除 app 模块排除
```java
// QKnowApplication.java - 移除这行：
"tech\\.qiantong\\.qknow\\.module\\.app\\..*",
```

#### T2.2 创建数据库表

```sql
-- 应用表
CREATE TABLE kac_apply (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  description TEXT,
  icon VARCHAR(256),
  status SMALLINT DEFAULT 1,  -- 0=停用, 1=正常
  type VARCHAR(64),           -- 应用类型
  workspace_id BIGINT,
  config JSONB,               -- 应用配置
  valid_flag SMALLINT DEFAULT 0,
  del_flag SMALLINT DEFAULT 0,
  create_by VARCHAR(32),
  create_time TIMESTAMP DEFAULT NOW(),
  update_by VARCHAR(32),
  update_time TIMESTAMP DEFAULT NOW()
);

-- 应用-Bot 关联表
CREATE TABLE kac_apply_bot (
  id BIGSERIAL PRIMARY KEY,
  apply_id BIGINT NOT NULL,
  bot_id BIGINT NOT NULL,
  create_time TIMESTAMP DEFAULT NOW()
);

-- 应用-知识库关联表
CREATE TABLE kac_apply_knowledge (
  id BIGSERIAL PRIMARY KEY,
  apply_id BIGINT NOT NULL,
  knowledge_id BIGINT NOT NULL,
  create_time TIMESTAMP DEFAULT NOW()
);

-- 应用-图谱关联表
CREATE TABLE kac_apply_graph (
  id BIGSERIAL PRIMARY KEY,
  apply_id BIGINT NOT NULL,
  graph_id BIGINT NOT NULL,
  create_time TIMESTAMP DEFAULT NOW()
);

-- 解决方案表
CREATE TABLE kac_solution (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  description TEXT,
  icon VARCHAR(256),
  status SMALLINT DEFAULT 1,
  workspace_id BIGINT,
  valid_flag SMALLINT DEFAULT 0,
  del_flag SMALLINT DEFAULT 0,
  create_by VARCHAR(32),
  create_time TIMESTAMP DEFAULT NOW(),
  update_by VARCHAR(32),
  update_time TIMESTAMP DEFAULT NOW()
);

-- 解决方案-应用关联表
CREATE TABLE kac_solution_apply (
  id BIGSERIAL PRIMARY KEY,
  solution_id BIGINT NOT NULL,
  apply_id BIGINT NOT NULL,
  create_time TIMESTAMP DEFAULT NOW()
);

-- 插件表
CREATE TABLE kac_plugin (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  description TEXT,
  type VARCHAR(64),
  config JSONB,
  status SMALLINT DEFAULT 1,
  workspace_id BIGINT,
  valid_flag SMALLINT DEFAULT 0,
  del_flag SMALLINT DEFAULT 0,
  create_by VARCHAR(32),
  create_time TIMESTAMP DEFAULT NOW(),
  update_by VARCHAR(32),
  update_time TIMESTAMP DEFAULT NOW()
);
```

#### T2.3 创建 Controller（7 个）

在 `qknow-module-app` 中创建以下 Controller：

| Controller | 路径 | 对应前端 API |
|-----------|------|-------------|
| `KacApplyController` | `/kac/apply` | `apply/apply.js` |
| `KacApplyBotController` | `/kac/bot` | `applyBot/applyBot.js` |
| `KacApplyGraphController` | `/kac/graph` | `applyGraph/applyGraph.js` |
| `KacApplyKnowledgeController` | `/kac/knowledge` | `applyKnowledge/applyKnowledge.js` |
| `KacPluginController` | `/kac/plugin` | `plugin/plugin.js` |
| `KacSolutionController` | `/kac/solution` | `solution/solution.js` |
| `KacSolutionApplyController` | `/kac/solutionApply` | `solution/solutionApply.js` |

每个 Controller 提供标准 CRUD 接口（list / getById / add / edit / remove）。

#### T2.4 创建 Service + Mapper

每个 Controller 对应：
- 1 个 Service 接口 + 1 个 ServiceImpl
- 1 个 Mapper 接口 + 1 个 XML 映射文件
- 1 个 VO / DTO 类

#### T2.5 插入示例数据

为 `kac_apply` 表插入 10 个示例应用（与概览页的热门应用对应）：

| 应用名 | 类型 | 标签 |
|--------|------|------|
| 文章编写 | 写作 | 写作、文章 |
| 批量检索 | 效率 | 效率、工具 |
| 精确检索 | 搜索 | 搜索、工具 |
| 实体关系检索 | 分析 | 分析、数据 |
| 语义检索 | 搜索 | 搜索、AI |
| 知识问答 | 问答 | 问答、知识 |
| 模板报告生成 | 模板 | 模板、文档 |
| 日报/周报生成 | 写作 | 写作、办公 |
| 数据分析助手 | 分析 | 分析、数据 |
| 智能摘要 | 效率 | 效率、AI |

---

### 阶段三：修复概览页面（P0，预计 1h）

**目标**：移除"功能正在开发中"提示，修复图标和状态显示

**任务**：

#### T3.1 移除概览页的"功能正在开发中"提示
检查 `frontend/src/views/kac/overview/index.vue`，移除或隐藏顶部黄色提示。

#### T3.2 修复应用图标
- 为每个应用配置图标（使用 Element Plus 图标或自定义 SVG）
- 在 `kac_apply` 表的 `icon` 字段存储图标标识

#### T3.3 修复状态显示
- 将 `● --` 改为正确的状态文本（正常/停用）
- 使用字典 `kac_horizontal_status`：0=停用(warning), 1=正常(primary)

#### T3.4 概览页数据对接
- 统计卡片数据从 `/kac/apply/list` 接口获取
- 热门应用推荐从 `/kac/apply/list?orderBy=hot` 获取

---

### 阶段四：知识图谱修复（P1，预计 2h）

**目标**：修复 `/kg/graph` 页面的 API 报错

**方案选择**：

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| **A：启用 ext 模块，排除 Neo4j** | 从 ComponentScan 排除中移除 ext，但保留 Neo4j 排除 | 复用现有代码 | 可能引入 Neo4j 依赖编译问题 |
| **B：仅修复 kg/graph 页面** | kg/graph 已有独立 Controller（KgGraphController），只需确保其正常工作 | 最小改动 | ext 模块功能仍不可用 |
| **C：创建轻量级 ext Controller** | 在 kb 模块中创建不依赖 Neo4j 的 ext Controller | 无 Neo4j 依赖 | 需要重写部分逻辑 |

**推荐方案：B** — KgGraphController 已经使用 MySQL 的 `kg_node`/`kg_edge` 表，不依赖 Neo4j。只需确保该 Controller 正常加载。

**任务**：

#### T4.1 验证 KgGraphController 是否已加载
- `KgGraphController` 在 `qknow-module-kg` 模块中
- 检查 kg 模块是否在 ComponentScan 排除列表中
- 如果被排除，从排除列表中移除 kg 模块（仅排除 neo4j 和 ext）

#### T4.2 确认 kg_node / kg_edge 表存在
```sql
-- 如果不存在则创建
CREATE TABLE IF NOT EXISTS kg_node (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT,
  label VARCHAR(128) NOT NULL,
  type VARCHAR(64),
  properties JSONB,
  valid_flag SMALLINT DEFAULT 0,
  del_flag SMALLINT DEFAULT 0,
  create_by VARCHAR(32),
  create_time TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS kg_edge (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT,
  source_id BIGINT NOT NULL,
  target_id BIGINT NOT NULL,
  label VARCHAR(128),
  properties JSONB,
  valid_flag SMALLINT DEFAULT 0,
  del_flag SMALLINT DEFAULT 0,
  create_by VARCHAR(32),
  create_time TIMESTAMP DEFAULT NOW()
);
```

#### T4.3 前端 ext 页面友好提示
对于依赖 ext 模块的页面（概念配置、知识抽取等），在前端添加友好提示：

```vue
<template v-if="!extModuleEnabled">
  <el-empty description="知识抽取功能需要启用 ext 模块">
    <el-button type="primary" @click="showSetupGuide">查看配置指南</el-button>
  </el-empty>
</template>
```

#### T4.4 插入示例图谱数据
```sql
-- 示例节点
INSERT INTO kg_node (label, type, properties) VALUES
('Flutter', '技术', '{"description": "跨平台UI框架"}'),
('Dart', '语言', '{"description": "Flutter的编程语言"}'),
('Widget', '概念', '{"description": "Flutter UI组件"}'),
('StatefulWidget', '概念', '{"description": "有状态组件"}'),
('Provider', '框架', '{"description": "状态管理框架"}');

-- 示例边
INSERT INTO kg_edge (source_id, target_id, label) VALUES
(1, 2, '使用'),
(1, 3, '包含'),
(3, 4, '继承'),
(1, 5, '支持');
```

---

### 阶段五：缺失组件开发（P2，预计 4h）

**目标**：为占位页面创建实际组件

**任务**：

#### T5.1 纵向行业应用页面（menu_id=2408）
- 创建 `frontend/src/views/kac/vertical/index.vue`
- 参考横向通用应用的卡片布局
- 按行业分类展示应用（金融、医疗、教育等）
- 更新菜单：`UPDATE system_menu SET component = 'kac/vertical/index' WHERE menu_id = 2408;`

#### T5.2 我的应用页面（menu_id=2410）
- 创建 `frontend/src/views/kac/myApp/index.vue`
- 展示当前用户创建/收藏的应用
- 支持搜索和筛选
- 更新菜单：`UPDATE system_menu SET component = 'kac/myApp/index' WHERE menu_id = 2410;`

#### T5.3 解决方案页面（menu_id=2406）
- 创建 `frontend/src/views/kac/solution/index.vue`
- 展示解决方案列表，每个方案包含多个应用
- 更新菜单：`UPDATE system_menu SET component = 'kac/solution/index' WHERE menu_id = 2406;`

---

## 四、执行顺序

```
阶段一（路由修复）─────────────────────┐
                                       ├─→ 阶段三（概览页修复）→ 阶段五（缺失组件）
阶段二（后端 Controller）──────────────┤
                                       └─→ 阶段四（知识图谱修复）
```

- 阶段一和阶段二可并行
- 阶段三依赖阶段二（需要后端接口）
- 阶段四独立执行
- 阶段五依赖阶段二

---

## 五、风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 移除 app 模块排除后引入编译错误 | 阶段二阻塞 | 先检查 app 模块的依赖，排除有问题的 Bean |
| ext 模块编译依赖 Neo4j | 无法单独启用 ext | 使用方案 B，仅修复 kg/graph |
| 表结构与现有代码不匹配 | CRUD 功能异常 | 参考现有表结构（如 kb_bot）的字段规范 |
| 前端组件引用不存在的 API | 页面报错 | 每个阶段完成后用 Puppeteer 验证 |

---

## 六、验收标准

1. ✅ 所有 KAC 页面无"功能开发中"提示
2. ✅ 所有 KAC 页面无 `No static resource` 报错
3. ✅ 概览页统计数据正确显示
4. ✅ 应用卡片图标正常加载
5. ✅ 状态字段显示正确文本（非 `● --`）
6. ✅ 知识图谱页面可正常展示节点和边
7. ✅ 所有 API 返回 200 状态码
8. ✅ Puppeteer 自动化测试通过

---

## 七、预计总工时

| 阶段 | 工时 | 优先级 | 依赖 |
|------|------|--------|------|
| 阶段一：路由修复 | 0.5h | P0 | 无 |
| 阶段二：后端 Controller | 3h | P0 | 无 |
| 阶段三：概览页修复 | 1h | P0 | 阶段二 |
| 阶段四：知识图谱修复 | 2h | P1 | 无 |
| 阶段五：缺失组件开发 | 4h | P2 | 阶段二 |
| **总计** | **10.5h** | | |
