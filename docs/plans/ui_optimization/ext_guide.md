# ext 模块 UI/UX 重构与优化指南

## 1. 模块视图层级梳理
`frontend/src/views/ext` 模块共包含 30 个 Vue 视图文件，主要可划分为以下业务子模块：

### 数据源管理 (extDatasource)
- `index.vue` - 数据源列表主页
- `detail/index.vue` - 数据源详情页
- `detail/componentOne.vue` - 详情子组件 1
- `detail/componentTwo.vue` - 详情子组件 2
- `selection/datasourceMultiple.vue` - 数据源多选组件
- `selection/datasourceSingle.vue` - 数据源单选组件

### 概念与关系管理 (extSchema / extSchemaRelation)
- `extSchema/index.vue` - 概念管理主页
- `extSchema/detail/index.vue` - 概念详情页
- `extSchemaRelation/index.vue` - 概念关系列表
- `extSchemaRelation/selection/relationMultiple.vue` - 关系多选组件
- `extSchemaRelation/selection/relationSingle.vue` - 关系单选组件

### 映射管理 (Mapping)
- `extSchemaMapping/index.vue` - 概念映射
- `extAttributeMapping/index.vue` - 属性映射
- `extRelationMapping/index.vue` - 关系映射

### 结构化抽取任务 (extStructTask)
- `index.vue` - 任务列表
- `addStructTask.vue` / `editStructTask.vue` - 新增/编辑任务主入口
- `structTask.vue` - 任务核心表单组件
- `importTable.vue` - 导入表结构组件
- `mapping.vue` - 映射配置组件
- `add/index.vue` - 新增任务向导主页
- `add/basicInfoForm.vue` - 基础信息表单
- `add/tableMapping.vue` - 表映射表单
- `add/relationMapping.vue` - 关系映射表单

### 非结构化任务及结果 (extUnstructTask / extractResults)
- `extUnstructTask/index.vue` - 非结构化任务列表
- `extUnstructTaskDocRel/index.vue` - 任务文档关联
- `extractResults/index.vue` - 抽取结果主页
- `extractResults/structuredResult.vue` - 结构化结果展示

### 任务日志 (extTaskLog)
- `index.vue` - 任务日志列表
- `taskLogDialog.vue` - 任务日志详情弹窗

---

## 2. 当前 DOM/CSS 设计缺陷分析
在对上述模块的现有代码（如 `extDatasource/index.vue`, `extSchema/index.vue` 等）进行深度走查后，发现以下不符合 Apple 级设计基线的缺陷：

1. **色彩高饱和度滥用**：
   - 当前项目大量使用了 Element Plus 的默认语义色彩，如 `type="primary"` (蓝色), `type="danger"` (红色), `type="warning"` (橙色) 等。
   - 包含硬编码的彩色配置（如 `#409EFF`, `#67C23A` 等），完全不符合「单色毛玻璃」的规范限制。
2. **缺乏空间材质感**：
   - 现有的容器（如 `div.app-container`, `div.pagecont-top`, `div.pagecont-bottom`）均采用纯色或无背景。
   - 视觉层级扁平，没有使用高级的毛玻璃 (Glassmorphism) 材质，导致整体缺乏通透感和纵深。
3. **按钮与交互生硬**：
   - `el-button` 和表格中的操作按钮缺乏物理点击反馈，点击状态仅依赖微小的颜色变化，缺乏动态粒子反馈（`v-ripple`）。
4. **全局背景与文字对比度**：
   - 没有统一严格遵循深空灰（黑白灰）色阶的约束，页面背景和文字色彩不够克制。

---

## 3. 具体重构与替换方案

为了将系统升级至「单色毛玻璃」极简交互规范，请严格按照以下步骤对 `ext` 模块的 30 个视图进行代码重构：

### 3.1 材质与容器替换 (Glassmorphism)
找出所有的容器面板元素（包含 `el-card`, `div.pagecont-top`, `div.pagecont-bottom`, `div.panel` 等），为其添加 `.glass-card` class。
- **目标 CSS（通过全局 class 应用）**：
  ```css
  .glass-card {
    backdrop-filter: blur(24px) saturate(180%);
    background: rgba(255, 255, 255, 0.4);
    border: 1px solid rgba(255, 255, 255, 0.3);
    border-radius: 12px; /* Apple 圆角 */
    box-shadow: 0 4px 24px rgba(0, 0, 0, 0.05);
  }
  ```
- **示例**：
  ```html
  <!-- 重构前 -->
  <div class="pagecont-top" v-show="showSearch">
  
  <!-- 重构后 -->
  <div class="pagecont-top glass-card" v-show="showSearch">
  ```

### 3.2 按钮交互与样式改造
删除所有 `<el-button>` 上的 `type="primary"`, `type="danger"`, `plain` 等彩色配置，统一添加 `class="glass-btn"` 与 `v-ripple` 指令。
- **示例**：
  ```html
  <!-- 重构前 -->
  <el-button type="primary" plain @click="handleAdd">新增</el-button>
  <el-button type="danger" link @click="handleDelete">删除</el-button>
  
  <!-- 重构后 -->
  <el-button class="glass-btn" v-ripple @click="handleAdd">新增</el-button>
  <el-button class="glass-btn" v-ripple @click="handleDelete">删除</el-button>
  ```

### 3.3 全局单色配置 (Monochrome)
- **页面底层背景**：统一在主容器或全局设置 `background-color: #F5F5F7;`。
- **文本颜色**：大标题及正文统一采用 `#1D1D1F`。次要说明文字（如 `el-table` 的 header 或者备注）使用低透明度的黑色如 `rgba(29, 29, 31, 0.6)`。
- **状态指示/标签**：替换 `el-tag` 及各种 `dict-tag` 里的彩色配置，改为灰白黑系列（如浅灰背景，深灰文字），结合圆角设计。
- **组件内部去除高饱和色**：例如 `extSchema/index.vue` 内部，将 `form.color = "#409EFF"` 等硬编码默认值改为灰色系的十六进制色值（如 `#8E8E93` 或 `#C7C7CC`），并限制调色盘组件的选择范围。

### 3.4 交互细节 (v-ripple)
确保表格行、列表卡片、可点击图标等，只要是可以交互的区域，都可以适当地加上 `v-ripple`（需要项目全局注册了该自定义指令），以强化点击时的“动态粒子涟漪”效果，带来极其灵动顺滑的体验。
