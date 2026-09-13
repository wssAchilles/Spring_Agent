# Phase 19: 全业务模块高保真 UI/UX Pro Max 体验升华与微交互打磨实施计划

> **依据**：`AGENTS.md` Research-to-Implementation Gate 规范 与 Rule 2 `.shared/ui-ux-pro-max` 规范  
> **前置调研**：学术向智能体 (`5850ebcc`) 与工业向智能体 (`34e62188`) 双向并发深度科研已闭环完成  
> **学术研报**：`docs/plans/phase_19_academic_report.md`（Doherty 阈值认知模型、骨架屏信息熵、Blink 重排剪枝定理、毛玻璃着色器显存带宽上界、二阶带阻尼谐振子弹簧动力学方程）  
> **工业研报**：`docs/plans/phase_19_industrial_report.md`（Vercel/Linear/Apple/Stripe 设计模式、4级单色毛玻璃架构、Element Plus 单色重绘、10大模块改造方案、三大生产避坑指南）  
> **设计系统基线**：`.shared/ui-ux-pro-max` 检索基线：**Variance=5**（现代平衡架构）、**Motion=6**（标准自然缓动）、**Density=7**（高密企业级看板）  
> **模型基线**：唯一生成模型为 **DeepSeek API**，唯一向量模型为 **阿里千问 (Qwen) Embedding (1536维)**，前端完全基于标准 CSS 硬件加速与轻量物理动力学，绝无端侧模型  
> **当前状态**：**Delivered**（全业务模块单色毛玻璃、骨架屏基建、微交互悬浮操作栏落地，前端全量生产构建 0 错误通过）

---

## User Review Required

> [!IMPORTANT]
> **本阶段核心提升与视觉交互价值**：
> 1. **全业务模块全面落地 Monochromatic Glassmorphism（单色钛金毛玻璃）**：
>    - 彻底告别旧版突兀的浅蓝通用后台风格与粗暴纯白背景，建立钛金纯色阶体系（Primary: 冷钛黑 `#1D1D1F` / 冰钛银 `#EDEDEF`）；
>    - 建立严密的 4 级材质系统：L0 视口底衬（无模糊）、L1 侧边栏结构（16px 模糊）、L2 业务卡片与表格外壳（12px 模糊）、L3 模态框与浮标（8px 模糊）；
>    - **GPU 显存安全法则**：严禁在表格单元格（`th`, `td`）设置 `backdrop-filter`，彻底杜绝核显与高分屏崩溃。
> 2. **消除全屏 Spinner，全面引入 1:1 几何孪生骨架屏（`GlassSkeleton`）**：
>    - 骨架卡片与真实业务卡片尺寸严格 1:1 吻合，配合 1.4s 硬件加速 Shimmer 流光动效；
>    - 将用户主观感知等待时间降低 $\ge 35\%$，累积布局位移（CLS）控制在 $\le 0.05$ 以内。
> 3. **底端沉浸式批量操作悬浮条（`FloatingActionBar`）**：
>    - 彻底解决长列表滚动后顶部批量按钮脱节的痛点，在视口底部中轴浮现带弹簧微交互的批量操作栏。
> 4. **深浅色模式双通道闭环支持**：
>    - CSS 变量分层绑定 `@media (prefers-color-scheme: dark)` 与 `html.dark`，彻底根除“黑底黑字”严重故障。

---

## Proposed Changes

### Component 1: 全局样式与 Element Plus 单色重绘基建

#### [MODIFY] [glassmorphism.scss](file:///Users/achilles/Documents/许子祺/Agent/frontend/src/assets/system/styles/glassmorphism.scss)
- 重构单色钛金调色板与 4 级毛玻璃材质系统；
- 深度覆写 Element Plus 全局组件（`el-button`, `el-input`, `el-select`, `el-table`, `el-dialog`, `el-pagination`）；
- 确立表格单元格无模糊安全规则与斑马纹微交互；
- 提供 GPU 硬件加速与 `contain: content` 隔离工具类。

#### [MODIFY] [main.js](file:///Users/achilles/Documents/许子祺/Agent/frontend/src/main.js)
- 显式 `import '@/assets/system/styles/glassmorphism.scss'` 确保样式在整个应用全局生效。

---

### Component 2: 公共高保真微交互与骨架屏组件基建

#### [NEW] [GlassSkeleton/index.vue](file:///Users/achilles/Documents/许子祺/Agent/frontend/src/components/GlassSkeleton/index.vue)
- 支持 `type="card"`（知识库列表、模型市场）与 `type="table"`（文档列表、切片列表、Trace 表格）双模；
- 1:1 尺寸对齐与纯 CSS 硬件加速 Shimmer 流光动画。

#### [NEW] [FloatingActionBar/index.vue](file:///Users/achilles/Documents/许子祺/Agent/frontend/src/components/FloatingActionBar/index.vue)
- 视口底部中轴浮现的批量操作栏，L3 玻璃态，带进入退出的微交互弹簧动画。

#### [NEW] [GlassEmpty/index.vue](file:///Users/achilles/Documents/许子祺/Agent/frontend/src/components/GlassEmpty/index.vue)
- 钛金单色极简空状态插画组件。

#### [NEW] [useDebounceSearch.js](file:///Users/achilles/Documents/许子祺/Agent/frontend/src/utils/useDebounceSearch.js)
- 300ms 搜索防抖与 ESC 重置 Composable。

---

### Component 3: 10 大核心前台业务模块体验重构

#### [MODIFY] [kmc/knowledgeBase/index.vue](file:///Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/knowledgeBase/index.vue) & [card.vue](file:///Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/knowledgeBase/components/card.vue)
- 移除全屏 Spinner，接入 `GlassSkeleton` 卡片骨架屏与 `fade-quick` 平滑交叉淡入；
- 接入 300ms 搜索防抖；卡片绑定单色文本变量，添加 Hover 漫反射光晕（Hover Glow）。

#### [MODIFY] [kmc/kmcDocument/index.vue](file:///Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/kmcDocument/index.vue)
- 剥离表格单元格上的 blur 类名，接入 `FloatingActionBar` 批量操作栏与表格骨架屏。

#### [MODIFY] [kmc/knowledgeSegment/index.vue](file:///Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/knowledgeSegment/index.vue)
- 切片文本等高与防抖搜索，接入 `FloatingActionBar` 批量操作。

#### [MODIFY] [kb/agent/index.vue](file:///Users/achilles/Documents/许子祺/Agent/frontend/src/views/kb/agent/index.vue)
- 左右分栏独立 L2 玻璃化，提示词输入框微透明底衬与聚焦光晕。

#### [MODIFY] [kb/bot/index.vue](file:///Users/achilles/Documents/许子祺/Agent/frontend/src/views/kb/bot/index.vue)
- 沉浸式钛金对话气泡，接入骨架屏，操作按钮单色微交互。

#### [MODIFY] [ai/modelMarket/index.vue](file:///Users/achilles/Documents/许子祺/Agent/frontend/src/views/ai/modelMarket/index.vue)
- 16:9 模型规格骨架卡片，密钥配置弹窗 L3 玻璃化。

#### [MODIFY] [kd/observability/index.vue](file:///Users/achilles/Documents/许子祺/Agent/frontend/src/views/kd/observability/index.vue)
- 4 联指标卡骨架屏与 Trace 表格骨架屏，消除刷新跳变。

---

## Verification Plan

### Automated Tests & Builds
1. **前端生产构建验证**：
   ```bash
   cd frontend && npm run build:prod
   ```
   要求：0 错误构建通过，CSS 产物体积合理。

### Manual Verification
1. **深浅色无缝切换走查**：切换系统 Dark Mode 与前台主题，验证知识库卡片文本对比度 ≥ 4.5:1，无“黑底黑字”；
2. **骨架屏 CLS 走查**：Lighthouse 审计 CLS $\le 0.05$；
3. **表格多选 Floating Action Bar 走查**：勾选表格项验证底部悬浮条弹出与操作联动；
4. **输入框防抖与聚焦光晕走查**：输入自动防抖，聚焦外发光自然细腻。
