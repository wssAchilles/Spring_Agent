# AI 模块 UI/UX 重构与优化指南

## 1. 视图清单
在深入分析 `frontend/src/views/ai` 模块后，梳理出以下 5 个 Vue 核心视图文件：
1. **`modelMarket/card.vue`**：模型卡片组件（承载卡片核心展示与点击交互）。
2. **`modelMarket/detail/index.vue`**：模型详情页面（信息流及 Tabs 的外层结构）。
3. **`modelMarket/detail/modelTable.vue`**：模型详情表格组件（呈现模型的启停与配置数据）。
4. **`modelMarket/index.vue`**：模型市场主页（包含搜索栏、卡片列表与底部分页器，以及配置密钥的弹窗）。
5. **`myModel/index.vue`**：我的模型主页（整体结构与模型市场高度相似）。

## 2. 当前 DOM/CSS 存在的设计缺陷

经过审查现有代码，发现当前设计与“Apple 级单色毛玻璃”规范存在以下偏差：

1. **色彩饱和度过高，未遵循单色（Monochrome）规范**：
   - 使用了大量的默认高饱和颜色，如默认的 `primary`（蓝色）、`danger`（红色）等。
   - `modelMarket/card.vue` 中的 `.card` 容器和 `index.vue` 中的 `.pagecont-bottom` 采用了生硬的纯白色（`#ffffff`）背景。缺乏高级灰度阶梯。
2. **材质干瘪，缺乏空间感与通透性**：
   - 未见任何 `backdrop-filter` 的运用，卡片（`.card`）、底部悬浮操作栏（`.pagecont-bottom`）与弹层（`.key-config-dialog`）均是实心色块，未能与底层形成光影交互。
3. **交互感知微弱，动效死板**：
   - `card.vue` 中的卡片区块与诸多 `<el-button>` 操作按键，仅有简单的悬浮放大和基础效果，**缺失 `v-ripple`（动态粒子）按压反馈效果**。代码层未能注入物理真实感。

## 3. 重构与优化方案（Monochrome Glassmorphism & v-ripple）

为将此模块提升至顶尖架构设计标准，请严格按照以下规范对这 5 个视图文件进行改造：

### 3.1 极简环境配色 (Monochrome)
- **底层背景约束**：所有页面级根节点（如 `.app-container`）如果需设定背景，须使用经典白灰 `#F5F5F7`。
- **字体色彩收敛**：全局文本默认应用 `#1D1D1F`，削弱深黑带来的突兀感；弱化辅助文字可使用纯色阶梯内的灰色，严禁使用蓝、绿等彩色文本（除非极其致命的系统警告）。

### 3.2 卡片毛玻璃化 (Glassmorphism)
涉及元素需全面移除实体背景（如 `#ffffff` 或 `#fff`），改用全局抽象出来的毛玻璃原子类，或在标签上直接应用 `.glass-card`。
- **核心实现原理**：
  ```css
  .glass-card {
    background: rgba(255, 255, 255, 0.4) !important;
    backdrop-filter: blur(24px) saturate(180%) !important;
    -webkit-backdrop-filter: blur(24px) saturate(180%);
    border: 1px solid rgba(255, 255, 255, 0.3);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.05);
  }
  ```
- **重点修改位置**：
  1. `modelMarket/card.vue` 中的 `.card` 元素（第 223 行 `background-color: #ffffff;` 需剔除，引入 `.glass-card`）。
  2. `modelMarket/index.vue` 与 `myModel/index.vue` 的 `.pagecont-bottom` 悬浮栏底色需调整为玻璃材质，使滚动内容透过底部能产生美妙的模糊映射。

### 3.3 注入触觉交互 (Dynamic Particles & v-ripple)
要求引入全局指令 `v-ripple` 及通用 `.glass-btn` 样式类以重塑一切可点按对象。
- **按钮改造**：
  查找各文件（尤其如 `card.vue` 的“密钥设置”、“模型详情”，及 `modelTable.vue` 的“编辑”、“删除”按钮）。
  将所有 `<el-button>` 升级，追加 `class="glass-btn"`，并声明 `v-ripple`：
  ```vue
  <!-- 改造前 -->
  <el-button type="primary" @click="handleUpdate(item)">
    密钥设置
  </el-button>

  <!-- 改造后 -->
  <el-button class="glass-btn" v-ripple @click="handleUpdate(item)">
    密钥设置
  </el-button>
  ```
- **卡片点击态改造**：
  `card.vue` 中的单体卡片因具备 `cursor: pointer` 特性，也应当在外层包裹节点处添加 `v-ripple`，实现用户点击整体卡片时的水波纹动效。
