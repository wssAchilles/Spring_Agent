# Kac 模块前端 UI 重构与优化指南

## 1. 模块视图清单
当前 `frontend/src/views/kac` 模块下共包含 **16** 个 Vue 视图及组件文件：
- `horizontal/index.vue`
- `horizontal/components/card.vue`
- `horizontal/detail/index.vue`
- `horizontal/detail/bot.vue`
- `horizontal/detail/kg.vue`
- `horizontal/detail/kmc.vue`
- `myApp/index.vue`
- `mySolution/index.vue`
- `mySolution/components/selectMyApp.vue`
- `mySolution/components/solutionCard.vue`
- `mySolution/detail/index.vue`
- `mySolution/detail/myAppList.vue`
- `overview/index.vue`
- `plugin/index.vue`
- `solution/index.vue`
- `vertical/index.vue`

## 2. 现有 DOM/CSS 缺陷分析
在审查代码（如 `overview/index.vue`、`horizontal/index.vue`、`horizontal/components/card.vue` 等）后，发现存在以下与最新 Apple 级设计基线不符的问题：
1. **配色高饱和度过载**：现有的 UI 充斥了大量彩色（如 `#409eff`, `#2666fb`, `#1f6eea`, `#eaf3ff` 等），违反了“单色（Monochrome）”规范。
2. **材质缺乏深度与通透感**：应用卡片（`.apply-card`, `.card`）、面板（`.quick-panel`, `.overview-section`）以及页面背景（`#f0f2f5`, `#ffffff`）采用了不透明的纯色块，未采用毛玻璃（Glassmorphism）效果。
3. **按钮与交互僵化**：大量使用了原生的 `<el-button type="primary">` 及其自带的蓝色渐变（如 `linear-gradient(90deg, #3d83ff 0%, #266cf4 100%)`）。缺少符合规范的动态粒子动效交互。
4. **文字颜色不统一**：文字多处使用了 `#333333`, `#1f2937`, `#6b7280`，未能统一收敛至标准色 `#1D1D1F`。

## 3. 重构与替换方案

为了达到“单色毛玻璃 (Monochrome Glassmorphism)”与“动态粒子动效 (v-ripple)”的设计标准，请在改造各页面时严格遵循以下指导：

### 3.1 颜色系统重置
- **页面背景层**：统一收敛为 `#F5F5F7`。移除原有的 `#f0f2f5` 或 `#eaf3ff`。
- **排版文字色**：全局标题、正文及图标颜色必须强制使用 `#1D1D1F` 或其带透明度的衍生单色（如 `rgba(29,29,31,0.7)` 用于辅助文本）。禁止一切蓝色、橙色等主色调残留。

### 3.2 容器毛玻璃材质替换
将原本使用不透明白色背景的容器（例如 `<el-card>`、`.card`、`.panel`、`.apply-card` 等），全部替换为毛玻璃组件方案。
**操作规范**：为所有卡片与面板容器增加 `glass-card` class。
```css
/* 全局基础 css 应包含以下毛玻璃样式 */
.glass-card {
  backdrop-filter: blur(24px) saturate(180%);
  background: rgba(255, 255, 255, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}
```
**DOM 改造示例**（以 `horizontal/components/card.vue` 为例）：
```html
<!-- 改造前 -->
<div class="card" style="background: #ffffff; border: 1px solid #e5ebf5;">...</div>

<!-- 改造后 -->
<div class="card glass-card">...</div>
```

### 3.3 按钮与动态粒子交互 (v-ripple)
取消原生 Element Plus 中带有色彩的主题类型（如 `type="primary"`），全部转向单色毛玻璃风格。同时，在所有可点击元素（`<el-button>`, `.quick-entry`, `.section-more` 等）上追加 `v-ripple` 动效指令。

**DOM 改造示例**：
```html
<!-- 改造前 -->
<el-button type="primary" class="card-action-btn">
  立即体验
</el-button>

<!-- 改造后 -->
<el-button class="glass-btn" v-ripple>
  立即体验
</el-button>
```
*注：如果是自定义的 `div`/`a`/`button` 作为可点击区域，同样直接注入 `v-ripple` 指令与相应的毛玻璃交互 class 即可。*

### 3.4 状态标签（Tag）去色化
现存如 `.status-pill--primary`、`<el-tag type="success">` 等带有明确功能色彩的组件，需统一降级为黑白灰（单色）方案。
- 可以使用 `rgba(29,29,31,0.05)` 作为标签背景。
- 文本与边框色依然保持单色。

---
请研发团队根据此指南，对 `kac` 模块下的 16 个 Vue 文件逐一进行清理与改造。
