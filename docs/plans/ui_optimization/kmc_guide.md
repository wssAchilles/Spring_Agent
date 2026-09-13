# kmc 模块前端重构与 UI/UX 优化指南

基于 Apple 级别前端架构与设计标准，本文档为 `frontend/src/views/kmc` 模块提供全面的“单色毛玻璃 (Monochrome Glassmorphism)”与“动态粒子动效 (v-ripple)”规范指导。

## 一、模块视图清单
经深度扫描，`kmc` 模块下共包含 **24 个** 核心 Vue 视图文件，层级梳理如下：

- **kmcCategory/**
  - `index.vue`
- **kmcDocument/**
  - `index.vue`
  - `selection/add.vue`
  - `selection/DocumentMultiple.vue`
  - `selection/DocumentSingle.vue`
- **knowledgeBase/**
  - `index.vue`
  - `components/card.vue`
  - `components/kmcDel.vue`
  - `components/querySet.vue`
  - `components/recall.vue`
  - `components/recallLog.vue`
  - `components/roleTable.vue`
  - `components/settings.vue`
  - `detail/index.vue`
  - `detail/componentOne.vue`
  - `detail/componentTwo.vue`
  - `selection/knowledgeBaseMultiple.vue`
  - `selection/knowledgeBaseSingle.vue`
- **knowledgeSegment/**
  - `index.vue`
  - `detail/index.vue`
  - `detail/componentOne.vue`
  - `detail/componentTwo.vue`
  - `selection/knowledgeSegmentMultiple.vue`
  - `selection/knowledgeSegmentSingle.vue`

## 二、当前 DOM/CSS 存在的痛点分析
分析上述 Vue 文件后，我们发现现有的 DOM 与 CSS 结构存在以下严重偏离极致设计规范的问题：

1. **色彩超载 (Color Overload)**
   页面中大量使用了如 `type="primary"`(通常为明蓝)、`type="danger"`(常为红) 等 Element Plus 默认的彩虹色系。不仅缺乏整体感，还给用户视觉造成高强度的冲击，完全背离了 Apple 的高级单色克制理念。
2. **材质干瘪 (Lack of Material Depth)**
   很多顶层容器采用了 `.app-container` 和默认的不透明 `<el-card>`，在多图层叠加时没有任何光影交织和空间透视感，整体显得非常僵硬。
3. **生硬的交互反馈 (Stiff Interaction)**
    `<el-button>` 与可点击元素缺乏细腻的交互响应，缺失顺滑的触控感（如水波纹、缩放缓动），导致系统的感知十分“工具化”而非“艺术品”。

## 三、具体替换与重构方案

### 1. 强制执行黑白灰单色 (Monochrome) 规范
- **背景规范**: 应用程序的 Root/Body 及模块的最底层背景强制变更为 `#F5F5F7`。
- **字体规范**: 所有主标题、文本和图标的颜色采用深灰色 `#1D1D1F`。
- **操作**: 移除所有的 `type="primary"`, `type="success"`, `type="warning"`, `type="danger"` 属性，统一走极简灰阶控制。

### 2. 引入毛玻璃材质 (Glassmorphism)
我们需要赋予所有的卡片、面板和主视图容器毛玻璃质感。针对所有 `.app-container` 和现有的 `<el-card>` 标签，添加或替换为 `.glass-card` class。

**全局 CSS 定义:**
```css
.glass-card {
  /* 核心毛玻璃参数 */
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  background: rgba(255, 255, 255, 0.4);
  /* 边框与阴影 */
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.04);
  /* 其他辅助 */
  color: #1D1D1F;
}
```
**Vue 替换示例:**
```vue
<!-- 替换前 -->
<el-card> ... </el-card>
<div class="app-container"> ... </div>

<!-- 替换后 -->
<div class="glass-card"> ... </div>
<div class="app-container glass-card"> ... </div>
```

### 3. 动态粒子与涟漪动效 (v-ripple)
针对所有的按钮和交互点击区，增加自定义 `.glass-btn` 并挂载 `v-ripple` 指令。

**按钮 CSS 定义:**
```css
.glass-btn {
  background: rgba(255, 255, 255, 0.6);
  color: #1D1D1F;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  backdrop-filter: blur(12px);
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.glass-btn:hover {
  background: rgba(255, 255, 255, 0.85);
  transform: scale(1.02);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}
```

**Vue 替换示例:**
```vue
<!-- 替换前 -->
<el-button type="primary" size="small" @click="submitForm">确 定</el-button>

<!-- 替换后 (移除 type，加入类名与动效指令) -->
<el-button class="glass-btn" size="small" v-ripple @click="submitForm">确 定</el-button>
```

## 四、执行路线
1. **全局样式注册**：首先在 `src/assets/styles` 或类似的基础样式中注册上述 `.glass-card` 及 `.glass-btn` 类。
2. **组件批量扫描与正则替换**：遍历上述 24 个文件，清空 `<el-button>` 上不合规的 `type`，植入 `class="glass-btn"` 和 `v-ripple`。
3. **布局包裹优化**：将原本的 `<el-card>` 标签全部更改为 `div.glass-card` 或 `<el-card class="glass-card">`（取消原本自带的 shadow 和 border）。
4. **人工走查**：以深灰色 `#1D1D1F` 为准绳，对所有残余的彩色图标与高亮文本进行最终统一修正。
