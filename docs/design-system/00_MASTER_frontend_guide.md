# iOS 26 设计系统 · 总纲与前端落地指南

> 本文档面向 **执行改造的 agent**，是这套设计系统的唯一权威入口。
> 读完这一份即可动手，不必先读 01–04；那几份是过程稿，含已证伪的内容。

---

## 0. 文档权威链（先读这一节，避免照着错数据干活）

当文档之间冲突时，按此优先级取值：

| 优先级 | 来源 | 说明 |
| :-- | :-- | :-- |
| **1** | `tokens/ios26-tokens.json` | 每个值都标注了 Figma 节点出处与核实状态，**以此为准** |
| **2** | `frontend/src/assets/system/styles/ios26-liquid-glass.scss` | 实现层，`$ios26-materials` 是五种材质的唯一数据源 |
| **3** | 本文档 | 用法与原则 |
| **4** | `01/02/03/04` 旧文档 | **仅作背景**，多处数值已证伪 |

**旧文档中已确认错误、不要采信的内容：**

- ❌ 圆角 `dock: 38.5px` → 真实为**玻璃层 38px / 外层容器 42px**
- ❌ 材质 `blur(20/25/30/40px)` + `saturate(180~210%)` → 真实为**统一 50px，且源文件中不存在 `saturate()`**
- ❌ 12 个系统色中的 **9 个是错的**（如 blue `#007AFF` → `#0088ff`，indigo `#5856D6` → `#6155f5`）
- ❌ 排版 Title 系字重 `700/600` → 真实**全部 400**；Headline 是 **590** 不是 600
- ❌ "Vibrancy 渐变层""菲涅尔描边层" → **源文件中不存在**，是凭空设计的图层
- ❌ `separator non-opaque: rgba(60,60,67,0.29)` → 真实 `rgba(0,0,0,0.12)`
- ❌ `fill: rgba(120,120,128,a)` 家族 → 源文件用的是 **Vibrant Fills**（`#cccccc/#e0e0e0/#ededed`）
- ❌ 阴影 sm/md/lg/dock 四件套 → **源文件没有投影体系**（见 §3.7）
- ❌ 建议给玻璃加 `isolation: isolate` / `transform: translateZ(0)` → **这两条会直接破坏效果**（见 §3.1）

---

## 1. 现状诊断（读代码得出的事实，不是推测）

### 1.1 技术栈
`Vue 3.4.31` + `Vite 5.3.2` + `Element Plus 2.7.6` + `Pinia 2.1.7` + `SCSS`。251 个 Vue 组件，14 个 SCSS 文件。

### 1.2 关键事实：项目里并存两套样式体系

| | **A. glassmorphism.scss**（实际在跑） | **B. ios26-liquid-glass.scss**（已验证，几乎没人用） |
| :-- | :-- | :-- |
| 文件行数 | 401 | 677 |
| 被引用组件 | **179 个** | **0 个** |
| 热类 | `glass-btn`(956)、`glass-card`(359)、`glass-card-border`、`glass-text-primary`… | `ios26-*` 类无人使用 |
| CSS 变量前缀 | `--glass-l0~l3-*`、`--mono-*` | `--ios26-*`（仅 1 个组件用到） |
| 混合模式使用 | **0 次** | `plus-lighter` / `color-dodge` / `hard-light` / `screen` |
| 视觉方向 | **单色工业风**（Titanium 灰阶，Element Plus 主色被压成近黑 `#1D1D1F`） | **iOS 26 彩色 Vibrant 体系** |

### 1.3 当前玻璃层与 iOS 26 的实测差距

`.glass-card` 现在的实现是：

```scss
background: var(--glass-l2-bg) !important;   /* rgba(255,255,255,0.82) 单层 */
backdrop-filter: blur(12px);
border: 1px solid rgba(255,255,255,0.85);
border-radius: 12px;
transform: translateZ(0);                     /* ← 反面清单第 1 条 */
&:hover { transform: translateY(-2px); }      /* ← 反面清单第 2 条 */
```

**这正是实验里偏差最大的做法。** 用同一张壁纸、同一几何位置与 Figma 渲染图逐像素比对的结果：

| 实现方式 | 与 Figma 的偏差 |
| :-- | :-- |
| **iOS 26 双层材质**（高光层 + color-dodge 模糊层） | **\|Δ\| = 5** ✅ |
| **当前实现**（单层半透明 + blur，无混合模式） | **\|Δ\| = 39** ❌ |
| 双层材质但加了 `transform: translateZ(0)` | 17 ❌ |

也就是说：**现在的玻璃比真实 iOS 26 淡得多、透得多，且完全没有"吸附背景色彩并提亮"的通透感。**

### 1.4 附带问题
`glassmorphism.scss` 大量使用 `!important` 覆写 Element Plus，改造时需注意层叠优先级；`index.scss` 中该文件在 213 行、iOS 26 在 214 行（iOS 26 后加载）。

---

## 2. 决策点（必须先拍板，不要默认替换）

**当前是"单色工业风"，iOS 26 是"彩色 Vibrant 体系"——这是视觉方向的改变，不是打磨。** 动手前需明确选哪条路：

- **路线 A｜全面转向 iOS 26**：接受彩色系统色（系统蓝 `#0088ff` 成为主色）、Vibrant 玻璃。视觉更接近原生 iOS，但会推翻现有的单色设计语言。
- **路线 B｜只取材质与排版，保留单色**：采用双层玻璃的光学配方（这是纯技术红利，任何色彩方向都受益），配色与 Element Plus 主色维持现状。**风险最低，建议分两步走时先做这个。**
- **路线 C｜维持现状**：什么都不改。

> 本文档按 **路线 B → A** 的顺序组织：§3 与 §5 的材质、排版、圆角部分对三条路线**都成立**；配色部分（§3.2）仅在选 A 时全量采纳。

---

## 3. 已验证的设计系统（速查）

### 3.1 ⚠️ 三条铁律

**铁律一：玻璃容器及其祖先，绝不能创建层叠上下文。**

CSS 规范中**任何**层叠上下文都构成「隔离组」，会拦住 `mix-blend-mode`，使 `color-dodge` 退化为普通半透明叠加。禁用属性：

```
transform  ·  will-change: transform  ·  opacity < 1
filter     ·  isolation: isolate      ·  容器自身 z-index
```

祖先带这些属性**同样**会让内部玻璃失效。玻璃元素也不宜参与位移动画——transform 加在谁身上，效果就在哪里被隔离。悬停/按压反馈改用 `box-shadow`、`outline`、颜色变化。

**铁律二：单层 `backdrop-filter` 不能替代双层模型。**

每种材质都是「高光层 + 模糊层」，模糊层同时承载 `backdrop-filter`、色调与混合模式。拆成"容器模糊 + 一个伪元素"会丢掉 `color-dodge` 提亮。

**铁律三：Headline 是 590，其余全部 400。**

不要给标题加粗。iOS 26 的层级靠字号与颜色表达，不靠字重。

### 3.2 配色（路线 A 时全量采纳）

**系统强调色**——iOS 26 已刷新，与经典 HIG 值有 9 处不同：

```
red #ff383c   orange #ff8d28   yellow #ffcc00   green #34c759
mint #00c8b3  teal #00c3d0     cyan #00c0e8     blue #0088ff
indigo #6155f5 purple #cb30e0  pink #ff2d55     brown #ac7f5e
```

**灰阶** `#8e8e93 / #aeaeb2 / #c7c7cc / #d1d1d6 / #e5e5ea / #f2f2f7`

**语义色**（已核实）：

| 用途 | 值 |
| :-- | :-- |
| label primary / secondary / tertiary / quaternary | `#000000` / `rgba(60,60,67,.6)` / `rgba(60,60,67,.3)` / `rgba(60,60,67,.18)` |
| **vibrant** label（**材质之上**的文字） | `#000000` / `#3d3d3d` / `rgba(80,80,80,.7)` / `rgba(72,72,72,.6)` |
| background primary / secondary / tertiary | `#ffffff` / `#f2f2f7` / `#ffffff` |
| grouped background primary / secondary / tertiary | `#f2f2f7` / `#ffffff` / `#f2f2f7` |
| **vibrant fill** primary / secondary / tertiary | `#cccccc` / `#e0e0e0` / `#ededed` |
| separator non-opaque / opaque | `rgba(0,0,0,.12)` / `#c6c6c8` |

> **普通 label 与 vibrant label 是两套**，分别用于普通背景与玻璃材质之上，不要混用。

### 3.3 排版（已核实，含 7 档 Dynamic Type）

基准档（Large / Default）：

| 样式 | 字号 | 行高 | 字距 | 字重 |
| :-- | --: | --: | --: | --: |
| Large Title | 34 | 41 | **+0.40** | 400 |
| Title 1 | 28 | 34 | **+0.38** | 400 |
| Title 2 | 22 | 28 | −0.26 | 400 |
| Title 3 | 20 | 25 | **−0.45** | 400 |
| **Headline** | 17 | 22 | −0.43 | **590** |
| Body | 17 | 22 | −0.43 | 400 |
| Callout | 16 | 21 | −0.31 | 400 |
| Subheadline | 15 | 20 | −0.23 | 400 |
| Footnote | 13 | 18 | −0.08 | 400 |
| Caption 1 | 12 | 16 | 0 | 400 |
| Caption 2 | 11 | 13 | **+0.06** | 400 |

字距规律：**小字号为正、中段为负、大字号转正**（Apple 的光学修正）。不要"顺手取整"。完整 7 档（xSmall→xxxLarge）见 tokens JSON 的 `typography.dynamicType`。

字体栈：`system-ui, -apple-system, "SF Pro Text", "SF Pro Display", "SF Pro", "PingFang SC", …`，Figma 侧对应 `SF Pro` + `font-variation-settings: "wdth" 100`。

### 3.4 圆角（8 个值，全部有节点出处）

```
icon 5.25px  ·  control 12px  ·  widget 28px  ·  card 30px
container 34px  ·  dockGlass 38px  ·  dockContainer 42px  ·  pill 1000px
```

图标圆角 = 尺寸 × **25%**（21px 图标 → 5.25px），这个比例可外推到其他尺寸。

### 3.5 材质（5 种，均为双层）

| Style | 高光层 | 模糊层（`blur(50px)` + `color-dodge`） |
| :-- | :-- | :-- |
| Ultrathin | `rgba(255,255,255,.07)` | `rgba(255,255,255,.03)` |
| Thin | `rgba(255,255,255,.05)` | `rgba(255,255,255,.4)` |
| Regular | `rgba(255,255,255,.25)` + `plus-lighter` | `rgba(255,255,255,.6)` |
| Thick | `rgba(255,255,255,.34)` + `plus-lighter` | `rgba(255,255,255,.84)` |
| **Chrome** | —（唯一单层） | `blur(25px)` + **`hard-light`** + `rgba(255,255,255,.75)` |

> 注意四种厚度的模糊半径**同为 50px**——差异来自填充与混合，不是模糊强度。

### 3.6 液态玻璃与组件配方（已验证）

```
Liquid Glass Large   圆角 34px  模糊 40px + hard-light + 暗色 rgba(0,0,0,.08)
                     底层 #0f0f0f + color-dodge + rgba(250,250,250,.6)
Liquid Glass Medium  同上，仅底层叠加改 rgba(245,245,245,.4)
Liquid Glass Small   全圆角 1000px  模糊 20px
                     Bright 底 rgba(0,0,0,.04) / Dim 底 rgba(255,255,255,.05)
                     选中态：rgba(255,255,255,.5) → white/saturation → #999/overlay → #0091ff
Dock                 玻璃层圆角 38px，模糊 30px + hard-light + rgba(0,0,0,.4)
                     再叠 rgba(0,0,0,.2) + screen；外层容器 42px
Launch Pad           圆角 12px；三层堆叠，各 40px + plus-lighter + 深色 + rgba(255,255,255,.45)
快捷操作背景          圆角 30px；四层 Brighten（含 rgba(255,255,0,.3) + saturation 抽饱和度）
Scroll Edge Soft     容器模糊 = calc(var(--scroll-edge-effect-blur-radius,10px) / 2)
Scroll Edge Hard     blur(30px) + 白底 + multiply + 顶边 1px 分隔线
```

关键共性：**模糊层普遍带 `opacity: .67` + 外扩 + SVG 亮度遮罩**，形成柔光晕——这是"液态感"的来源。

### 3.7 阴影 —— 源文件里没有投影体系

查验 11 个组件/整屏（Dock、小组件、搜索框、快捷操作菜单、控制中心、锁屏控件、整屏 Home Screen……）**全部零投影**。iOS 26 的层级靠**玻璃材质 + 模糊光晕**表达。

源文件中仅两处真实阴影：

```
app 图标文字      text-shadow: 0 2px 25px #000000
LG Small 选中态   0 0 2px rgba(0,0,0,.1), 0 1px 8px rgba(0,0,0,.12)
```

`ios26-liquid-glass.scss` 里保留的 `--ios26-shadow-sm/md/lg/dock` 是**网页兜底**，不属于 iOS 26。

### 3.8 动效 —— 未核实

这 4 个页面是符号定义页，**不含原型数据**。`03_spring_motion_guide.md` 中的弹簧曲线（`cubic-bezier(0.25,1,0.33,1)` 等）**没有 Figma 出处**，属可用的设计假设，但**不要对外声称是"从 Figma 提取的 Apple 曲线"**。

---

## 4. Apple 资深设计师视角的优化原则

技术参数只是底线，真正拉开差距的是下面五条判断。

**① 层级靠材质，不靠阴影。**
不要给浮层加大投影。用材质的厚薄（Thin/Regular/Thick）与模糊强度表达"离用户多远"。投影是 Material Design 的语法，不是 iOS 的。

**② 色彩是信号，不是装饰。**
系统色只用于：可交互元素、状态、强调。大面积用于装饰会立刻失去 Apple 感。iOS 26 的界面主体是**中性色**，色彩只在关键处出现。

**③ 排版靠"光学修正"而非"整数取整"。**
字距的正负切换、Headline 的 590、图标圆角 25% 比例——这些"不整齐"的数字正是 Apple 质感的来源。取整就会变成"像但不对"。

**④ 触感优先于动画。**
点击的 0.96 缩放、150–200ms 的快速响应，比华丽的转场更重要。用户感知"跟手"远多于"好看"。

**⑤ 内容与材质的关系是"浸润"，不是"覆盖"。**
玻璃要让背后内容**透出来并被提亮**。如果做完之后背景内容变得模糊灰暗、只是被盖住，那说明做法错了——回到 §3.1 检查层叠上下文。

---

## 5. 迁移路线

### Phase 1｜建立正确的材质基座（不碰任何组件）

1. 确认 `ios26-liquid-glass.scss` 已被 `index.scss` 引入（当前在 214 行，✅ 已引入）。
2. 用 §7 的像素比对法，在项目里放一个最简测试页，验证 `.glass-card` 渲染结果与 Figma 参考图偏差 `\|Δ\| ≤ 8`。**这一关不过，后面全白做。**
3. 决定 §2 的路线 A/B/C。

### Phase 2｜逐组件迁移（按影响面排序）

| 优先级 | 目标 | 做法 |
| :-- | :-- | :-- |
| **P0** | 建立新层级 `.glass-l2 / .glass-l3` 的正确实现 | 把 `glassmorphism.scss` 中 l2/l3 的**单层背景**替换为 `@include ios26-material(...)`，保留原有 `--glass-*` 变量名做映射，避免 179 个组件同时崩 |
| **P0** | 移除两个反模式 | 删除 `glassmorphism.scss` 中所有 `transform: translateZ(0)` 与玻璃上的 hover `translateY(-2px)` |
| **P1** | 卡片 / 面板 / 抽屉 | 迁移到 `.glass-card` 的正确实现，圆角按 §3.4 分级 |
| **P1** | 按钮 | 主按钮色改用 `--ios26-color-blue`；按压反馈用 `outline` 或颜色，**不要 transform** |
| **P2** | 弹窗 / 下拉 | 用 thick 材质 + `--ios26-radius-modal`；注意不要在外层套带 transform 的动画容器 |
| **P2** | 排版 | 用 `.ios26-text-*` 工具类替换散落的 font-size/letter-spacing，纠正字重 |
| **P3** | 配色（仅路线 A） | 按 §3.2 全量替换 |

> **迁移策略要点**：不要新增一套与 `glass-*` 并行的类。**改 `glass-*` 的实现**，让 179 个组件自动受益——这是成本最低、风险最可控的路径。

### Phase 3｜验证与收口

对每个已迁移的页面跑 §7 的方法；把结果记入 §6 清单。

---

## 6. 组件改造对照清单

| 组件 | 当前 | 目标 | 状态 |
| :-- | :-- | :-- | :-- |
| 全局玻璃基座 | 单层 blur，无混合 | 双层材质 | ☐ |
| `.glass-card` | 12px 圆角 + translateZ | 30px + 双层，去 transform | ☐ |
| 导航栏 | — | `ios26-material(regular, 0)` + 0.5px 分隔线 | ☐ |
| 侧边栏 | — | 背景 `#f2f2f7`，菜单项 12px | ☐ |
| 标签页 | — | pill 圆角 + 无 transform 的按压反馈 | ☐ |
| 主按钮 | `#1D1D1F` 近黑 | 路线 A：`#0088ff` | ☐ |
| 弹窗 | 16px 圆角 + l3 | `thick` 材质 + 26px | ☐ |
| 抽屉 | — | 迁移基座即可 | ☐ |
| 表格 / 表单 | 单色 | 排版纠正 + 语义色 | ☐ |
| 图标 | — | 圆角 = 尺寸 × 25% | ☐ |

---

## 7. 验证方法（可复现）

**别靠肉眼。** 用与 Figma 逐像素比对的方式判定：

1. 从 Figma 取目标节点的渲染图（MCP `get_screenshot`）。
2. 在项目里搭一个同尺寸、同背景的测试页（用源文件里的真实壁纸）。
3. 用无头浏览器渲染：
   ```bash
   "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
     --headless --hide-scrollbars --force-device-scale-factor=1 \
     --window-size=620,440 --screenshot=/tmp/mine.png file:///path/to/test.html
   ```
4. 用 canvas 读取两图在**无内容区域**的均值色并比较：
   ```js
   const d = ctx.getImageData(x0, y0, w, h).data;   // 采样区必须避开文字与色块
   ```
5. 判定：`|Δ| ≤ 8` 合格（当前实现为 17–19，正确实现为 4–5）。

> ⚠️ **采样区必须避开参考图上的内容**（文字、色块、分隔线），否则参考均值被拉低，结论会反向。

---

## 8. 反面清单（出现即说明做错了）

| ❌ 不要 | 为什么 |
| :-- | :-- |
| 给玻璃容器或其祖先加 `transform` / `will-change: transform` / `isolation` / 容器 `z-index` | 层叠上下文会隔离混合，`color-dodge` 失效，\|Δ\| 由 5 恶化到 17 |
| 用单层 `backdrop-filter` 近似材质 | 丢掉的正是 iOS 26 最核心的提亮机制，\|Δ\| = 39 |
| 用 `saturate()` 模拟 Vibrancy | 源文件中根本不存在，方向就错了 |
| 给标题加粗（700/600） | 除 Headline(590) 外全部是 400 |
| 给浮层加大投影 | 源文件没有投影体系 |
| 沿用经典 Apple 色值（`#007AFF` 等） | 12 个系统色里 9 个已被 iOS 26 更新 |
| 把字距/圆角取整 | Apple 质感来自这些"不整齐"的精确值 |
| 新增并行类名而不改现有实现 | 179 个组件不会受益，形成第三套体系 |

---

## 9. 尚未核实的部分（诚实标注，别当成已知）

| 项 | 状态 |
| :-- | :-- |
| 非 vibrant 的 fills 族 | 源文件中**未找到**，可能已被 Vibrant Fills 取代 |
| Motion / 弹簧曲线 | 这 4 页无原型数据，现有值无出处 |
| Spacing 全局网格 | 只有零散组件值（40/20/17/7.5/3.5px），**未见 4pt/8pt 体系** |
| Liquid Glass 的 `Glass Effect` 层 | Figma 原生效果，导出器无法表达，CSS 侧无对应物 |
| Scroll Edge Soft 的 alpha 遮罩 | 素材未取回 |
| 圆角 xs(6)/sm(10)/md(14)/modal(26) | 旧文档留存值，**未经 Figma 核实** |
