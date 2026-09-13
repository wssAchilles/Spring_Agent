# KD 模块前端 UI 重构与优化指南

## 1. 视图层级清单

`frontend/src/views/kd` 模块主要包含以下 4 个视图：

1. **appOperations** (`appOperations/index.vue`) - 应用运营看板
2. **botOperation** (`botOperation/index.vue`) - Bot 运营看板
3. **knowledgeAsset** (`knowledgeAsset/index.vue`) - 知识资产大盘与全生命周期监控
4. **observability** (`observability/index.vue`) - LLM 可观测性

---

## 2. 当前 DOM/CSS 存在的设计缺陷

根据“单色毛玻璃 (Monochrome Glassmorphism)”与“动态粒子动效 (v-ripple)”规范，当前代码存在以下问题：

### (1) 配色问题 (Color Palette)
- **高饱和度色彩滥用**：当前样式中定义了大量高饱和度的品牌色和状态色（如 `--brand-blue: #346dff`, `--brand-green: #47d97b`, `--brand-orange: #ff8a3d`, 等），以及图表中分配的各种鲜艳颜色（`#3f72ff`, `#41cbc3` 等）。
- **背景与文本色不符规范**：各页面的 `var(--bg-page)` 大多定义为 `#f0f2f5` 或 `#f0f2f5`，`--bg-card` 为纯白 `#ffffff`，文本颜色定义繁杂（如 `--text-main: #1f2d3d`）。未统一使用苹果极简的 `#F5F5F7`（背景）与 `#1D1D1F`（文字）。

### (2) 材质与深度问题 (Materials & Depth)
- **缺乏毛玻璃质感**：面板均使用了普通的白色背景容器。
  - `appOperations`、`botOperation`、`knowledgeAsset` 大量使用了普通的 `<div class="panel-card">` 配合实色背景。
  - `observability` 页面使用了 Element Plus 原生的 `<el-card shadow="never">`，其底层也是普通不透明的白色 DOM 结构。
- 没有使用 `backdrop-filter` 达到半透明且模糊环境光的高级视觉深度效果。

### (3) 按钮与交互体验 (Buttons & Interactions)
- 可点击元素（如 `<el-button>`，各类自定义的 `<button class="tab-switch">`、`<button class="filter-button">`、列表的建议操作等）仍采用扁平化色块或线框按钮。
- **缺失粒子动效**：所有的按钮及可点击卡片都**没有**应用微交互反馈（如水波纹动效），缺失 `v-ripple` 指令。

---

## 3. 具体重构与替换方案

### 3.1 全局配色替换 (Monochrome)
- **背景**：将 `var(--bg-page)` 替换为 `#F5F5F7`。如果存在全局包裹容器，可直接设置 `background-color: #F5F5F7;`。
- **字体**：将主要文字颜色（如 `var(--text-main)` 或 `#303133`）统一切换为 `#1D1D1F`。次要文字使用较浅的灰度（如 `#86868B`）。
- **图表配色**：移除 `[ "#346dff", "#47d97b", ... ]` 等彩虹色，改为黑、白、灰的阶梯色系，例如不同透明度的 `#1D1D1F` (如 `rgba(29,29,31, 0.8)`, `rgba(29,29,31, 0.5)` 等)。

### 3.2 毛玻璃卡片改造 (Glassmorphism)
对于所有承担面板或卡片角色的 DOM：

- **普通 DOM (`div`)**：
  在 `<article class="panel-card ...">` 或 `<div>` 元素上，补充或替换为 `.glass-card` class。
  ```html
  <!-- 替换前 -->
  <article class="panel-card summary-card">
  <!-- 替换后 -->
  <article class="glass-card summary-card">
  ```

- **Element Plus 卡片 (`<el-card>`)**：
  在 `observability` 页面中，在组件上增加 `.glass-card`。若 Element Plus 样式覆盖有冲突，需在全局或 `<style scoped>` 中将卡片自身的背景设为透明。
  ```html
  <!-- 替换前 -->
  <el-card shadow="never" class="status-card">
  <!-- 替换后 -->
  <el-card shadow="never" class="glass-card status-card">
  ```

**所需的全局/局部 CSS 片段**：
```css
.glass-card {
  background: rgba(255, 255, 255, 0.4) !important;
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%); /* Safari 兼容 */
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 16px; /* Apple 风格大圆角 */
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.02);
}
```

### 3.3 按钮添加 `glass-btn` 与 `v-ripple` 动效
所有的 `<button>` 和 `<el-button>`，均需加上 `class="glass-btn"` 以及 `v-ripple`。

- **Element Plus 按钮**：
  ```html
  <!-- 替换前 -->
  <el-button type="primary" plain @click="openDashboard">
  <!-- 替换后 -->
  <el-button class="glass-btn" v-ripple @click="openDashboard">
  ```

- **原生 Button / 标签式按钮** (如 `tab-switch`, `filter-button`)：
  ```html
  <!-- 替换前 -->
  <button v-for="tab in trendRangeTabs" type="button" @click="...">
  <!-- 替换后 -->
  <button v-for="tab in trendRangeTabs" class="glass-btn" v-ripple type="button" @click="...">
  ```

**所需的全局/局部 CSS 片段**：
```css
.glass-btn {
  background: rgba(255, 255, 255, 0.6) !important;
  color: #1D1D1F !important;
  border: 1px solid rgba(0, 0, 0, 0.05) !important;
  backdrop-filter: blur(12px) saturate(180%);
  border-radius: 8px;
  transition: all 0.3s ease;
}
.glass-btn:hover {
  background: rgba(255, 255, 255, 0.8) !important;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}
```

*注意：需要在项目的入口文件（如 `main.js` 或 `main.ts`）中确保注册并引入了 `v-ripple` 相关的自定义指令。*
