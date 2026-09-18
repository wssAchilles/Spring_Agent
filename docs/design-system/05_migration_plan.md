# iOS 26 视觉全面转向（路线 A）· 实施计划

## Context

项目（Vue 3.4 + Element Plus 2.7 + SCSS，251 个组件）现有视觉体系是"单色工业风玻璃"（`glassmorphism.scss`，单层 blur + 无混合模式），而经过 Figma 源文件逐项验证的 iOS 26 设计系统（`ios26-liquid-glass.scss`，双层材质 + mix-blend-mode）已就绪但零组件使用。本次将前端**全面转向 iOS 26**：系统色、玻璃材质、圆角、排版、布局四件套全部对齐已验证数据。

**已拍板的决策**：① 侧边栏改为浅色 iOS 风；② 保留被动深色模式（深色令牌向 iOS 26 Dark 值靠拢，数据已在源文件读到）；③ 分两批执行 —— 本次只做核心转向（令牌层 + 玻璃类 + Element Plus 覆写 + 布局四件套），硬编码颜色/字重清扫为第二批。

**权威数据源**：`docs/design-system/ios26-tokens.json`（每个值带 Figma 节点出处）与 `00_MASTER_frontend_guide.md`。所有数值必须从中取，不得凭记忆。

## 工程约束（违反即出 bug）

1. **层叠上下文铁律**：玻璃容器及其祖先不得有 `transform` / `will-change: transform` / `opacity<1` / `filter` / `isolation` / 自身 `z-index`。实测加 `translateZ(0)` 使玻璃偏差从 5 恶化到 17。现有 `glassmorphism.scss` 中的 `.glass-accelerated`（translateZ）、`.glass-card` hover `translateY(-2px)` 必须删除。
2. **SCSS mixin 导入顺序**：`ios26-liquid-glass.scss` 必须移到 `glassmorphism.scss` **之前**导入（glassmorphism 的类要 `@include ios26-material(...)`，mixin 需先定义）。两者都定义 `.glass-card` —— glassmorphism 中删除自己的定义，只保留 ios26 版本。
3. **@extend 与 mixin 不兼容**：`@extend .glass-modal-l3` / `@extend .glass-btn` 只复制选择器规则，**不会带伪元素**。所有 extend 改为直接 `@include`。
4. **localStorage 陷阱**：`store/system/settings.js` 读 `storageSetting.theme`（当前缓存可能是 `#2666fb`）与 `storageSetting.sideTheme`（可能缓存 `theme-dark`）。代码改默认值不够，验证时需清 localStorage 或在代码中做迁移。
5. **!important**：glassmorphism 有 64 处。重写时尽量去掉；仅保留 el-dialog/el-table 等 EP 高特异性冲突处的必要穿透。

## Phase 1 · 令牌层

### 1a. `frontend/src/assets/system/styles/glassmorphism.scss` 令牌重写
`:root` 令牌值替换（**变量名不变**，15 个 Vue 组件用 `--glass-*` 兜底，179 个组件消费 `--mono-*` 类）：

| 原令牌 | 新值（iOS 26 已核实） |
| :-- | :-- |
| `--mono-bg` `#F5F5F7` | `#f2f2f7`（grouped primary） |
| `--mono-surface` `#FFF` | `#ffffff` |
| `--mono-text-primary` `#1D1D1F` | `#000000`（label primary） |
| `--mono-text-secondary` `#6E6E73` | `rgba(60,60,67,0.6)` |
| `--mono-text-muted` `#86868B` | `rgba(60,60,67,0.3)` |
| `--mono-border` / `-subtle` / `-strong` | `rgba(0,0,0,.12)` / `.08` / `.16`（separator 系） |
| `--glass-l1-*` | thin 材质（blur 50px, tint `rgba(255,255,255,.4)`） |
| `--glass-l2-*` | regular 材质（tint `.6`） |
| `--glass-l3-*` | thick 材质（tint `.84`） |
| `--el-color-primary` 及 light/dark 派生 | 基色 `#0088ff`（派生色按新基色重算，保持现有 getLight/DarkColor 公式） |

深色 mixin `dark-theme-tokens`：值替换为源文件读到的 **Dark 实测值**（背景 `black`/`#1c1c1e`/`#2c2c2e`、标签 `white`/`rgba(235,235,245,.6/.3/.16)`、分隔线 `#38383a`/`rgba(255,255,255,.17)`、系统色 dark 组）。深色玻璃配方源文件未读，保留单层近似并加注释标注。

### 1b. Element Plus 主色 JS 注入
- `frontend/src/store/system/settings.js:13`：`theme: storageSetting.theme || '#2666fb'` → `'#0088ff'`
- `frontend/src/assets/system/styles/variables.module.scss`：`$--color-primary: #409EFF` → `#0088ff`；`$--color-success #67C23A` → `#34c759`、`$--color-warning #E6A23C` → `#ff8d28`、`$--color-danger #F56C6C` → `#ff383c`（iOS 26 已验证系统色）

### 1c. 导入顺序调整
`index.scss`：`@import "./ios26-liquid-glass.scss";` 移到 `@import "./glassmorphism.scss";` 之前。

## Phase 2 · 玻璃类重写（glassmorphism.scss 下半部）

| 类 | 新实现 |
| :-- | :-- |
| `.glass-accelerated` | **删除**（translateZ 违禁） |
| `.glass-container-l1` | `@include ios26-material(thin, 12px)` |
| `.glass-card` | **删除本文件定义**（ios26 版本接管：regular + 30px） |
| `.glass-modal-l3` | `@include ios26-material(thick, 26px)` |
| `.glass-btn` | 重写为 iOS 26 按钮：`--ios26-fill-vibrant-secondary` 底、圆角 12px、字重 510、hover 用 color/fill 变化（**不用 transform**）、active 可用 scale(0.96)（纯 fill 按钮非玻璃，transform 无害） |
| `.glass-floating-btn` | 直接 `@include` 同款实现（去掉 @extend），圆角 pill |
| `.navbar` | `@include ios26-material(thin, 0)` + 0.5px 底分隔线 |
| `.sidebar-container` | 纯色 `var(--ios26-bg-grouped-primary)`（iPadOS 设置惯例：侧栏非玻璃） |

**Element Plus 覆写块**：
- `.el-dialog`：@extend 改直接 `@include ios26-material(thick, 26px)`；圆角 16px→26px；标题字重 590
- `.el-popover` / `.el-dropdown__popper`：thick + 12px 圆角
- `.el-input__wrapper` / `.el-select__wrapper` / `.el-textarea__inner`：圆角 8px→12px，focus 环色 `#0088ff`
- `.el-table`：mono 令牌全部替换为 ios26 令牌（斑马纹 `#f2f2f7`、分隔线 `rgba(0,0,0,.12)`），保留必要的 !important

## Phase 3 · 布局四件套

| 文件 | 改动 |
| :-- | :-- |
| `layout/components/Sidebar/settings.js`（默认值 `sideTheme: 'theme-dark'` → `'theme-light'`）与 `store/system/settings.js:14`（默认改 `theme-light`） | 浅色侧边栏开关 |
| `frontend/src/assets/system/styles/variables.module.scss` 菜单变量 | 浅色组（`$base-menu-light-*`）：背景 `#f2f2f7`、激活态 `--ios26-fill-vibrant-secondary`、文字 label 色系、菜单项圆角 12px |
| `layout/components/Sidebar/Logo.vue` / `SidebarItem.vue`（如含深色硬编码） | 按需对齐浅色 token |
| `layout/components/Navbar.vue` | 硬编码 `#FFFFFF`/`#f7faff`/`rgb(248,248,248)` → `--ios26-bg-primary` / `.glass-navbar`（thin+0）；页面标题应用 `.ios26-text-headline`（590/17px） |
| `layout/components/AppMain.vue` | `#f0f2f5` → `var(--ios26-bg-grouped-primary)` |
| `layout/components/TagsView/index.vue` | 标签胶囊化：`border-radius: 1000px`、激活标签底 `--ios26-color-blue` 或 fill 系、`#42b983`（旧 vue 绿）→ 系统蓝；字体 12px/510 |

## Phase 4 · 验证

1. **编译**：`cd frontend && npx sass --no-source-map src/assets/system/styles/index.scss /dev/null`（入口级编译，覆盖全部 import 链）
2. **启动**：`cd frontend && npm run dev`，无头 Chrome 截图关键页（首页、kac/overview、kmc/knowledgeBase 任一、含 el-dialog 打开的页面）
3. **像素比对**：按 `00_MASTER_frontend_guide.md` §7 方法，对 `.glass-card` 跑 |Δ|≤8 校验（复用已保存的 Figma 参考图）
4. **清理 localStorage** 后重载，确认 theme=`#0088ff`、sideTheme 浅色生效；切系统深色模式（prefers-color-scheme）抽查深色令牌
5. **反模式扫描**：`grep -rn "translateZ(0)\|will-change" frontend/src/assets/system/styles/` 应只在 ios26 文件的**注释**中出现

## 范围外（第二批，本次不做）

- 90 个文件中的硬编码色（`#1D1D1F` 92 处、`#F5F5F7` 88 处）与 220 处 font-weight 700/600 清扫
- 14 个 Vue 的内联 style 硬编码
- `AppRunnerDrawer.vue` 等 11 个组件局部 `.glass-*` 重定义对齐
- `btn.scss` / `qiantong.scss` / `qknow.scss` / `anivia.scss` 旧色板清扫

## 风险与回退

- **可回退性**：所有改动集中在 8 个样式文件 + 5 个布局/配置文件。开工前建议先 `git commit` 当前状态作为检查点（含未跟踪的 docs/design-system 与 styles）。
- **最大风险点**：`index.scss` 导入顺序调整与 `.glass-card` 定义归属变更 —— 若页面出现样式崩塌，优先检查这两处。
- **遗留冲突**：`AppRunnerDrawer.vue:1053` 的 scoped `.glass-card` 重定义会在新体系下产生局部偏差，属第二批，先接受。
