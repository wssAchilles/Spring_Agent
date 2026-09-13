# App 模块前端重构与优化指南 (Monochrome Glassmorphism)

## 1. 模块视图清单
当前 `frontend/src/views/app` 模块 (知识图谱/探索) 包含以下 4 个视图文件：
1. `graphExploration/index.vue` (主视图)
2. `graphExploration/addEntity.vue` (添加实体弹窗视图)
3. `graphExploration/addRelationship.vue` (添加三元组弹窗视图)
4. `graphExploration/selection/entitySingle.vue` (单选实体弹窗视图)

## 2. 当前 DOM/CSS 设计缺陷分析
在对现有 Vue 文件进行审查后，发现以下不符合“单色毛玻璃 (Monochrome Glassmorphism)”与“动态粒子动效 (v-ripple)”规范的缺陷：

1. **色彩搭配违规**：
   - 当前存在大量的默认 Element Plus 主题色（如 `type="primary"`, `type="danger"` 的按钮）。
   - 代码中存在硬编码的高饱和度颜色（例如：`#315790`, `#7dbffa4d`, `#89b8ff` 等蓝色系节点颜色），不符合仅允许黑白灰（Monochrome）的配色要求。
2. **材质与层级（Glassmorphism 缺失）**：
   - 主页面的侧边栏 (`.control-tree`), 顶部栏 (`.head-title`), 详情面板 (`.details-dialog`) 以及所有的弹窗本体 (`<el-dialog>`) 未使用毛玻璃材质，缺乏高级感。
3. **按钮与交互动效缺失**：
   - 全局 `<el-button>` 和其他可交互元素（如 `.toolbar-item`, `.docTd`）均缺失 `.glass-btn` 类以及 `v-ripple` 点击水波纹/粒子动效。
   - 分页、折叠面板等组件使用的是默认样式。
4. **背景颜色与文本颜色未统一**：
   - 未在最外层容器强行指定背景为 `#F5F5F7` 和字体颜色 `#1D1D1F`。

## 3. 重构与替换方案

### 3.1 颜色规范与图谱色彩重置
- **背景与文字**：将应用外层或 `div.app-container` 设置 `background: #F5F5F7; color: #1D1D1F;`。
- **图谱节点色彩**：移除 Vis.js 配置里的蓝色彩色。替换为黑白灰渐变，例如节点背景设为 `#E5E5EA`，边框设为 `#D1D1D6`，高亮设为 `#1D1D1F`，文字全部采用 `#1D1D1F` 或 `#8E8E93`。

### 3.2 引入毛玻璃材质 (Glassmorphism)
所有浮窗、面板、卡片区域全部引入 `.glass-card`。
- CSS 基础类定义（如全局已提供则直接使用）：
  ```css
  .glass-card {
    backdrop-filter: blur(24px) saturate(180%);
    background: rgba(255, 255, 255, 0.4);
    border: 1px solid rgba(255, 255, 255, 0.5);
    border-radius: 12px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  }
  ```
- **改造点**：
  - `index.vue` 中的 `.head-title`, `.control-tree`, `.toolbar`, `.details-dialog`, `.tool-mask` 增加 `.glass-card` class。
  - 对于所有的 `<el-dialog>`，可通过 `custom-class="glass-card"` 或覆盖 `:deep(.el-dialog)` 来赋予毛玻璃背景，同时移除默认的白色实心背景。

### 3.3 按钮与动态粒子交互 (v-ripple)
全局移除 `type="primary"`, `type="danger"` 的高亮彩色属性，统一通过黑白灰单色和玻璃质感实现。
- 将所有的 `<el-button>` 改造为：
  ```vue
  <!-- 原代码 -->
  <el-button type="primary" @click="submitForm">提交</el-button>
  
  <!-- 替换为 -->
  <el-button class="glass-btn" v-ripple @click="submitForm">提交</el-button>
  ```
- 对于图标操作如 `<div class="toolbar-item">` 和关闭按钮 `<el-icon class="icon">`，也需要加上 `v-ripple`：
  ```vue
  <div class="toolbar-item glass-btn" v-ripple @click="toolbarClick(item)">...</div>
  ```

### 3.4 弹窗视图改造示例 (`addEntity.vue` 等)
针对 `addEntity.vue`, `addRelationship.vue`, `entitySingle.vue` 中的弹窗结构：
1. 取消 `<el-dialog>` 默认实色底板。
2. 将 `<el-button type="primary" plain>` 和 `<el-button type="danger" plain>` 去除类型约束，应用 `class="glass-btn" v-ripple`。
3. 表格部分 (`<el-table>`) 设置透明背景，取消斑马纹的实色，转而通过透明度的黑/灰相间色阶实现，以融合入毛玻璃底层容器中。

---
> 备注：在替换和修改代码时，严格保留现有的所有通过 emit、ref 和 API 进行交互的业务逻辑，仅对视图层的 HTML class 与 v-ripple 指令进行介入。
