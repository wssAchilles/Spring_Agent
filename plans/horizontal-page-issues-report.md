# 横向通用应用页面功能"未开发"问题分析报告

> 截图时间：2026-06-22 13:32:20
> 页面地址：`localhost/kac/horizontal`
> 分析模型：xiaomi/mimo-v2.5（识图）+ mimo-v2.5-pro（报告）

---

## 一、截图现状

页面已正常加载，显示 **10 个应用卡片**，布局为 4 列网格。但存在以下 3 个问题：

| 问题 | 现象 | 影响 |
|------|------|------|
| 状态文本异常 | 所有卡片显示 `● --` 而非 `● 正常` | 用户无法判断应用状态 |
| 应用图标缺失 | 图标区域显示默认占位图 | 视觉体验差 |
| 功能按钮不可用 | 点击"立即体验"或"查看详情"均提示"功能正在开发中" | **所有功能无法使用** |

---

## 二、根因分析

### 问题 1：状态文本显示 `--`

**文件**：`frontend/src/views/kac/horizontal/components/card.vue:290-292`

```javascript
function getStatusLabel(status) {
  return getStatusOption(status)?.label || "--";
}
```

**根因**：组件使用字典 `kac_horizontal_status` 来映射状态值。数据库中该字典未配置（`system_dict_type` 表中无此记录），导致 `kac_horizontal_status.value` 为空数组，所有状态查询返回 `--`。

**修复方案**：在数据库中插入字典数据：
```sql
INSERT INTO system_dict_type (dict_name, dict_type, status, create_by, create_time)
VALUES ('应用插件状态', 'kac_horizontal_status', '0', 'admin', NOW())
ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO system_dict_data (dict_sort, dict_label, dict_value, dict_type, status, create_by, create_time)
VALUES 
  (1, '正常', '1', 'kac_horizontal_status', '0', 'admin', NOW()),
  (2, '停用', '0', 'kac_horizontal_status', '0', 'admin', NOW());
```

### 问题 2：应用图标缺失

**文件**：`frontend/src/views/kac/horizontal/components/card.vue:276-281`

```javascript
function getImage(row) {
  if (!row.icon) {
    return GraphCover;
  }
  return `${import.meta.env.VITE_APP_BASE_API}/profile${row.icon}`;
}
```

**根因**：`getImage` 函数将 `icon` 字段当作**文件路径**处理（拼接为 `/profile{icon}` 的 URL）。但我们插入的数据中 `icon` 字段存储的是 **Element Plus 图标名称**（如 `Edit`、`Search`、`ChatDotRound`），不是文件路径。导致请求 `/profileEdit` 返回 404，图片加载失败显示占位图。

**修复方案**（二选一）：

**方案 A**：修改 `getImage` 函数，支持图标名称映射：
```javascript
import * as ElementPlusIcons from "@element-plus/icons-vue";

const iconMap = {
  Edit: "https://cdn.jsdelivr.net/npm/@element-plus/icons-vue/dist/Edit.svg",
  Search: "https://cdn.jsdelivr.net/npm/@element-plus/icons-vue/dist/Search.svg",
  Document: "https://cdn.jsdelivr.net/npm/@element-plus/icons-vue/dist/Document.svg",
  // ... 其他图标
};

function getImage(row) {
  if (!row.icon) return GraphCover;
  if (iconMap[row.icon]) return iconMap[row.icon];
  if (row.icon.startsWith("/") || row.icon.startsWith("http")) {
    return `${import.meta.env.VITE_APP_BASE_API}/profile${row.icon}`;
  }
  return GraphCover;
}
```

**方案 B**：修改数据库中的 `icon` 字段，存储实际图片路径或 Base64。

**推荐方案 A**，因为图标名称更易维护。

### 问题 3："立即体验"按钮始终提示"功能正在开发中"（核心问题）

**文件**：`frontend/src/views/kac/horizontal/components/card.vue:399-403`

```javascript
function handleExperience(row) {
  ElMessage({
    message: "功能正在开发中",
    type: "warning",
  });
  return;  // ← 直接 return，所有后续逻辑被跳过！
  // 以下代码永远不会执行...
  if (props.source === "vertical") { ... }
  if (String(row.status) === "0") { ... }
  if (row.pluginId != null) { ... }
  // ...
}
```

**根因**：`handleExperience` 函数在第 403 行有一个**硬编码的 `return`**，导致无论点击哪个应用的"立即体验"按钮，都会立即弹出"功能正在开发中"提示并返回。这是**故意的占位代码**，实际功能从未实现。

### 问题 4："查看详情"按钮也提示"功能正在开发中"

**文件**：`frontend/src/views/kac/horizontal/components/card.vue:472-551`

```javascript
function handleDetail(row) {
  // ...
  if (props.source === "vertical") {
    ElMessage({ message: "功能正在开发中", type: "warning" });
    return;
  }
  // ...
  // id = 2-8: 提示开发中
  if (id === 2 || id === 3 || id === 4 || id === 5 || id === 6 || id === 7 || id === 8) {
    ElMessage({ message: "功能正在开发中", type: "warning" });
    return;
  }
  // id >= 10: 提示开发中
  if (id >= 10) {
    ElMessage({ message: "功能正在开发中", type: "warning" });
    return;
  }
}
```

**根因**：`handleDetail` 函数对特定 ID 范围（2-8、≥10）硬编码了"功能正在开发中"提示。当前数据库中的应用 ID 为 1-10，其中 ID 2-10 都会被拦截，只有 ID 1 可能正常跳转（但还需要 `pluginId` 不为 null）。

---

## 三、修复方案汇总

| # | 问题 | 修复文件 | 修复方式 | 工时 |
|---|------|----------|----------|------|
| 1 | 状态文本 `--` | 数据库 | 插入 `kac_horizontal_status` 字典数据 | 5min |
| 2 | 图标缺失 | `card.vue:276` | 修改 `getImage` 支持图标名称映射 | 15min |
| 3 | "立即体验"不可用 | `card.vue:399` | 移除硬编码 `return`，实现实际跳转逻辑 | 30min |
| 4 | "查看详情"不可用 | `card.vue:472` | 移除 ID 范围硬编码拦截，实现详情页路由 | 30min |

### 修复优先级

- **P0**（必须修复）：问题 3 + 问题 4 — 功能完全不可用
- **P1**（建议修复）：问题 1 — 状态显示异常
- **P2**（可选优化）：问题 2 — 图标显示

---

## 四、"立即体验"功能实现建议

移除硬编码 `return` 后，需要实现以下跳转逻辑：

```javascript
function handleExperience(row) {
  // 1. 状态为停用时提示
  if (String(row.status) === "0") {
    ElMessage({ message: "该应用已停用", type: "warning" });
    return;
  }

  // 2. 有 pluginId 时跳转到插件应用页
  if (row.pluginId != null) {
    router.push({
      path: "/kac/horizontal/pluginApply",
      query: { applyId: row.id, title: row.name },
    });
    return;
  }

  // 3. 根据应用类型跳转到对应功能页
  const routeMap = {
    "写作": "/workspace/writing",
    "搜索": "/workspace/search",
    "问答": "/workspace/chat",
    "效率": "/workspace/tools",
    "分析": "/workspace/analysis",
    "模板": "/workspace/template",
  };
  
  const targetPath = routeMap[row.type] || `/kac/horizontal/detail`;
  router.push({
    path: targetPath,
    query: { applyId: row.id, title: row.name },
  });
}
```

**注意**：以上路由需要对应的前端页面支持。如果目标页面尚未开发，需要先创建。

---

## 五、验收标准

修复完成后，验证以下场景：

1. ✅ 状态文本显示"正常"而非 `--`
2. ✅ 应用图标正确显示（Element Plus 图标或自定义图片）
3. ✅ 点击"立即体验"跳转到对应功能页面（或至少不弹出"开发中"提示）
4. ✅ 点击"查看详情"显示应用详情
5. ✅ 所有 10 个应用卡片均可交互
