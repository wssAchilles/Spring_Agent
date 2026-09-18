# Apple iOS 26 玻璃质感与液态材质实现指南 (Liquid Glass Implementation Guide)

> ## ✅ 实现状态（2026-09-18 已重写并经浏览器实测验证）
>
> 下文第二、第三节描述的架构**已废弃**。实际实现见
> `frontend/src/assets/system/styles/ios26-liquid-glass.scss` 的 `$ios26-materials`
> 与 `@mixin ios26-material` / `ios26-glass-dock` / `ios26-quick-action-bg` /
> `ios26-scroll-edge-effect`。
>
> ### 与源文件核对后的更正（节点 48:33518）
>
> | 本文档原值 | Figma 真实值 |
> |---|---|
> | `blur(20/25/30/40px)` + `saturate(180~210%)` 递进 | 四种厚度**统一 `blur(50px)`**；源文件**不存在 `saturate()`** |
> | 单层 `backdrop-filter` + 白色半透明 | **两层叠加**：高光层 + 模糊层（`color-dodge` + 色调填充） |
> | 无 Chrome 材质 | **Chrome 存在**：`blur(25px)` + `hard-light` + `rgba(255,255,255,0.75)`，唯一单层 |
> | 原文的「菲涅尔描边层」「Vibrancy 渐变层」 | **源文件中不存在** —— 是凭空设计的图层，已删除 |
>
> ### 经浏览器实测确认的关键约束 ⚠️
>
> **玻璃容器绝不能创建层叠上下文。** CSS 规范中任何层叠上下文都构成「隔离组」，
> 会拦住 `mix-blend-mode`，使 `color-dodge` 退化为普通半透明叠加，通透感消失。
>
> 禁用属性：`transform`、`will-change: transform`、`opacity < 1`、`filter`、
> `isolation: isolate`、容器自身 `z-index`。**祖先元素带有这些属性同样会破坏效果。**
>
> 实测数据（620×440 壁纸样本，与 Figma 渲染截图逐像素比对，取样于无内容的空白玻璃区）：
>
> | 结构 | 与参考图偏差 |
> |---|---|
> | **正确结构**（无层叠上下文 + 正 z-index 伪元素） | **\|Δ\| = 5** |
> | 加了 `transform: translateZ(0)` | 17 |
> | 加了 `will-change: transform` | 19 |
> | 纯模糊、不加混合模式 | 39 |
> | 模糊层与高光层顺序颠倒 | 39 |
>
> **层序同样由该实验确定**：高光层在下、模糊层在上。颠倒后偏差由 4 恶化到 39。
>
> ### 遗留未决
> - Liquid Glass 的 `Glass Effect` 层在 Figma 导出代码中为空 —— 那是 Figma 原生玻璃效果，CSS 侧无对应物
> - `Scroll Edge Effect - Soft` 的 alpha 遮罩素材未取回
> - 阴影值、Motion、Fills 色值仍未核实
>
> ---

## 一、为什么彻底摒弃单层半透明色块？

在传统前端样式中，开发者往往简单使用 `background: rgba(255, 255, 255, 0.75); backdrop-filter: blur(20px)` 来模拟毛玻璃。但在 iOS 26 的设计语言中，这种简化方式存在致命缺陷：
1. **暗部泛白发灰（Milky Washout）**：当背景具有色彩或复杂纹理时，单层白色半透明会像一层死板的白雾，抹杀了背底的光感；
2. **缺乏动态提亮（No Vibrancy）**：Apple 原生 Liquid Glass 具备 **Vibrancy（自适应通透色彩增益）**，它会动态汲取背景色彩的高光分量并进行叠加增亮；
3. **边缘缺乏物理光学反射（No Specular Rim）**：真实玻璃边缘受到视线入射角（菲涅尔效应）影响，顶部边缘会有明显的聚光反射，而底部边缘有轻微暗部折射。

为此，本项目遵循 Figma 源文件规范，采用 **双伪元素多层重构架构 (`::before` + `::after`)**。

---

## 二、已验证的材质架构（Figma 双层模型）

```
+─────────────────────────────────────────────────────────────+
│ ::after   模糊层（上）                                        │
│           backdrop-filter: blur(50px)                        │
│           background: <色调填充>                              │
│           mix-blend-mode: color-dodge   ← 提亮核心，不可省略   │
├─────────────────────────────────────────────────────────────┤
│ ::before  高光层（下）                                        │
│           background: <高光填充>                              │
│           mix-blend-mode: plus-lighter / normal               │
│           ※ Chrome 材质没有这一层（唯一单层材质）             │
├─────────────────────────────────────────────────────────────┤
│ 容器      position: relative; border-radius; overflow: hidden │
│           ⚠️ 不得创建层叠上下文（见开头的实测约束）            │
│           直接子元素由 > :where(*) 自动抬到 z-index 3          │
+─────────────────────────────────────────────────────────────+
```

五种材质的完整配方以 SCSS 中的 `$ios26-materials` 为唯一数据源，此处不重复数值，
以免出现两处不一致 —— 这正是本次修订要消除的问题。

---

## 三、混合宏用法与配方出处

```scss
.stat-card { @include ios26-material(regular, var(--ios26-radius-card)); }
.dock      { @include ios26-glass-dock(); }
.banner    { @include ios26-scroll-edge-effect(soft); }
```

各组件配方均有 Figma 真实节点出处：

| 组件 | 节点 | 要点 |
|---|---|---|
| Dock | 48:35586 | 模糊层 30px / hard-light / 黑 40% + 玻璃层 黑 20% / screen；玻璃层圆角 38px，外层容器 42px |
| Launch Pad | 48:35568 | 三层堆叠，各 40px / plus-lighter / 深色 + 白色 0.45；圆角 12px；图标 21px、圆角 5.25px |
| 快捷操作背景 | 48:34682 | 四层 Brighten，用 `background-blend-mode` 表达填充栈（注意含 `rgba(255,255,0,0.3)` + saturation） |
| 滚动边缘 Soft | 48:33697 | 容器模糊取 `--scroll-edge-effect-blur-radius` 的一半 |
| 滚动边缘 Hard | 48:33701 | 白底 + multiply + 顶边 1px 分隔线 |

> **性能提示**：源文档曾建议用 `transform: translateZ(0)` 强制 GPU 合成 —— 实测该做法会
> 破坏玻璃效果（\|Δ\| 由 5 恶化到 17）。`backdrop-filter` 本身即触发合成层，无需该属性。

---

## 四、层叠上下文与内容层级

> 本节原内容（要求加 `isolation: isolate`、用 `transform: translateZ(0)` 做硬件加速）
> **已被实测证伪** —— 两者都会创建层叠上下文并破坏玻璃效果。以下为更正后的规则。

1. **不得创建层叠上下文**：容器**及其祖先**都不得带 `transform`、`will-change: transform`、
   `opacity < 1`、`filter`、`isolation: isolate`、容器自身 `z-index`。
   若业务需要位移动画，玻璃元素本身不宜参与 —— transform 加在谁身上，混合就在哪里被隔离。
   悬停/按压反馈改用 `box-shadow`、`outline` 或颜色变化。

2. **内容层级**：玻璃层占用 `z-index: 1/2`，混合宏通过 `> :where(*)` 把直接子元素自动抬到
   `z-index: 3`。`:where()` 是零特异性，子元素若需绝对定位，用自己的规则即可覆盖。

3. **合成层**：`backdrop-filter` 本身就会触发 GPU 合成，不需要 `translateZ(0)`。

4. **为什么必须与页面混合**：`color-dodge` 需要以**页面内容**作为底色才能产生提亮。
   一旦隔离，它只能与玻璃自己的高光层（同样近乎纯白）混合，结果是一块平淡的半透明色块。
   这正是纯模糊对照组偏差高达 \|Δ\|=39 的原因。
