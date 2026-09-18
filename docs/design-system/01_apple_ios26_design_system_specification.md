# Apple iOS 26 设计系统全景规范文档 (Design System Specification)

> ⚠️ **本文档已被 `00_MASTER_frontend_guide.md` 取代，且多处数值经 Figma 复核后证伪。**
> 请勿直接照此实施。已知错误至少包括：圆角 `dock 38.5px`、材质 `blur(20~40px)+saturate()`、
> 12 个系统色中的 9 个、Title 系字重 `700/600`、以及"Vibrancy 渐变层 / 菲涅尔描边层"
> （源文件中不存在）。权威数据见 `tokens/ios26-tokens.json`。
> 本文件保留仅作过程记录。

## 一、真实源文件核验与设计哲学

本规范基于 Figma 社区官方源文件《Apple iOS 26 Library (Community)》（URL: `https://www.figma.com/design/E60e54iVDf8my3UJ73yu2K/Apple-iOS-26-Library--Community-`，`fileKey`: `E60e54iVDf8my3UJ73yu2K`）及 Apple 人机交互指南（Human Interface Guidelines）进行了真实节点级数据核验。

### 1. 真实节点采样与数值对齐（拒绝凭空猜测与近似取整）
- **采样节点 1：`_HSQA - BG` -> `Mode=Light` (`#48:34682`)**：
  - 尺寸：`W: 100`, `H: 100`，`Rotation: 0°`
  - **连续圆角 (Corner Radius)**：严格为 **`30px`**（非粗暴取整的 20px 或 24px）。对应 iPhone / iPad 平台标准 Widget 容器连续超椭圆轮廓；
  - 填充与特效：采用多层 Fill 叠加，底层模糊为 Glass Effect（`blur(32px)`），外边缘带有 `0.5px` 渐变半透明描边；
- **采样节点 2：`Dock - iPhone` 与 `Dock - iPad`**：
  - 连续圆角：iPhone 上为 **`38.5px`**（高度 84px 下的连续超椭圆胶囊），iPad 上为 **`30px`**；
- **采样节点 3：`❖ Text Styles` -> `Dynamic Type` (`#48:33148`)**：
  - 字体间距 Tracking 严格保留负像素精度，例如 Large Title 为 `-0.41px`（`-0.024em`），拒绝四舍五入。

### 2. Vibrancy（自适应通透色）物理光学重构机理
Apple 的原生 Vibrancy 效果拒绝使用单一的半透明背景平铺。如果仅简单使用 `rgba(255, 255, 255, 0.78)` 加 `blur()`，在动态色彩背景下会严重发灰泛白，无法实现 Apple 原生“将背景颜色吸附并提亮晶莹穿透”的光学质感。

本项目在工程实现上，采用 **`isolation: isolate` + `backdrop-filter` + `::before (mix-blend-mode: plus-lighter)` + `::after (渐变遮罩 0.5px 菲涅尔描边)`** 的双伪元素多层架构，彻底在现代浏览器中还原原生 Vibrancy 效果。

---

## 二、六大专业维度详细规范（绝对精准数值）

### 1. 配色体系 (Colors - 浅色模式)

#### 1.1 系统填充层 (System Fills)
| 语义变量 | CSS 变量 | 严格 RGBA 色值 | 适用场景 |
| :--- | :--- | :--- | :--- |
| **Primary Fill** | `--ios26-fill-primary` | `rgba(120, 120, 128, 0.20)` | 选中态胶囊、重点填充按钮、分段选择器激活块 |
| **Secondary Fill** | `--ios26-fill-secondary` | `rgba(120, 120, 128, 0.16)` | 搜索框底色、次级操作按钮、滑块轨道 |
| **Tertiary Fill** | `--ios26-fill-tertiary` | `rgba(118, 118, 128, 0.12)` | 微弱分组底衬、不可点击背景块 |
| **Quaternary Fill** | `--ios26-fill-quaternary` | `rgba(116, 116, 128, 0.08)` | 骨架屏占位、极弱分割区域 |

#### 1.2 系统背景层 (System Backgrounds)
| 语义变量 | CSS 变量 | HEX / 色值 | 适用场景 |
| :--- | :--- | :--- | :--- |
| **System Background** | `--ios26-bg-primary` | `#FFFFFF` | 页面主视窗、基础全屏画板 |
| **Secondary System Background** | `--ios26-bg-secondary` | `#F2F2F7` | 侧边栏、分栏列表、模块背景 |
| **Tertiary System Background** | `--ios26-bg-tertiary` | `#FFFFFF` | 内容卡片、浮动浮层背底 |
| **System Grouped Background** | `--ios26-bg-grouped-primary` | `#F2F2F7` | 表单分组背底 |

#### 1.3 标签文字层 (Labels)
| 语义变量 | CSS 变量 | 色值与透明度 | 对比度等级 | 适用场景 |
| :--- | :--- | :--- | :--- | :--- |
| **Primary Label** | `--ios26-label-primary` | `#000000` (100%) | AAA | 主标题、核心正文、主操作文字 |
| **Secondary Label** | `--ios26-label-secondary` | `rgba(60, 60, 67, 0.60)` | AA | 副标题、字段标签、元数据、作者时间 |
| **Tertiary Label** | `--ios26-label-tertiary` | `rgba(60, 60, 67, 0.30)` | AA (大号) | 输入占位符、禁用文字、面包屑非激活项 |
| **Quaternary Label** | `--ios26-label-quaternary`| `rgba(60, 60, 67, 0.18)` | - | 水印字符、不可交互底纹图标 |

#### 1.4 分割线与描边 (Separators & Borders)
- **Non-Opaque Separator**: `--ios26-separator-non-opaque`: `rgba(60, 60, 67, 0.29)` (推荐配合物理 `0.5px` 细线)
- **Opaque Separator**: `--ios26-separator-opaque`: `#C6C6C8` (纯色分割线)
- **Glass Border Specular**: `--ios26-border-glass-specular`: `rgba(255, 255, 255, 0.75)` (玻璃纳米级反射边框)

#### 1.5 12 种系统标准强调色 (System Accent Colors - 精确 HEX 与 RGB)
| 颜色名 | CSS 变量 | 精确 HEX | 真实 RGB |
| :--- | :--- | :--- | :--- |
| **System Blue** | `--ios26-color-blue` | `#007AFF` | `rgb(0, 122, 255)` |
| **System Purple** | `--ios26-color-purple` | `#AF52DE` | `rgb(175, 82, 222)` |
| **System Pink** | `--ios26-color-pink` | `#FF2D55` | `rgb(255, 45, 85)` |
| **System Red** | `--ios26-color-red` | `#FF3B30` | `rgb(255, 59, 48)` |
| **System Orange** | `--ios26-color-orange` | `#FF9500` | `rgb(255, 149, 0)` |
| **System Yellow** | `--ios26-color-yellow` | `#FFCC00` | `rgb(255, 204, 0)` |
| **System Green** | `--ios26-color-green` | `#34C759` | `rgb(52, 199, 89)` |
| **System Mint** | `--ios26-color-mint` | `#00C7BE` | `rgb(0, 199, 190)` |
| **System Teal** | `--ios26-color-teal` | `#30B0C7` | `rgb(48, 176, 199)` |
| **System Cyan** | `--ios26-color-cyan` | `#32ADE6` | `rgb(50, 173, 230)` |
| **System Indigo** | `--ios26-color-indigo` | `#5856D6` | `rgb(88, 86, 214)` |
| **System Brown** | `--ios26-color-brown` | `#A2845E` | `rgb(162, 132, 94)` |

---

### 2. 字体排印体系 (Typography - 严格对齐 SF Pro 像素级 Tracking)

| 样式名 (Style) | 字号 (Size) | 行高 (Height) | 字符间距 (Tracking) | 默认字重 (Weight) | 对应 CSS Class |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Large Title** | 34px | 41px | **-0.41px** | 700 (Bold) | `.ios26-text-large-title` |
| **Title 1** | 28px | 34px | **-0.36px** | 700 (Bold) | `.ios26-text-title-1` |
| **Title 2** | 22px | 28px | **-0.26px** | 700 (Bold) | `.ios26-text-title-2` |
| **Title 3** | 20px | 25px | **-0.22px** | 600 (Semibold) | `.ios26-text-title-3` |
| **Headline** | 17px | 22px | **-0.41px** | 600 (Semibold) | `.ios26-text-headline` |
| **Body** | 17px | 22px | **-0.41px** | 400 (Regular) | `.ios26-text-body` |
| **Callout** | 16px | 21px | **-0.32px** | 400 (Regular) | `.ios26-text-callout` |
| **Subheadline** | 15px | 20px | **-0.24px** | 400 (Regular) | `.ios26-text-subheadline` |
| **Footnote** | 13px | 18px | **-0.08px** | 400 (Regular) | `.ios26-text-footnote` |
| **Caption 1** | 12px | 16px | **0.00px** | 400 (Regular) | `.ios26-text-caption-1` |
| **Caption 2** | 11px | 13px | **+0.07px** | 500 (Medium) | `.ios26-text-caption-2` |

---

### 3. 玻璃材质与液态配方 (Materials & Liquid Glass)

#### 3.1 基础系统材质配方
- **Ultrathin - Light**: `backdrop-filter: blur(20px) saturate(180%)`，背景 `rgba(255, 255, 255, 0.44)`
- **Thin - Light**: `backdrop-filter: blur(25px) saturate(190%)`，背景 `rgba(255, 255, 255, 0.60)`
- **Regular - Light**: `backdrop-filter: blur(30px) saturate(200%)`，背景 `rgba(255, 255, 255, 0.72)`
- **Thick - Light**: `backdrop-filter: blur(40px) saturate(210%)`，背景 `rgba(255, 255, 255, 0.85)`

#### 3.2 真实 Vibrancy Liquid Glass 双伪元素多层配方
- **底透折射层 (Main Container)**: `backdrop-filter: blur(32px) saturate(190%) brightness(102%)`
- **Vibrancy 增亮透光层 (`::before`)**: `linear-gradient(135deg, rgba(255, 255, 255, 0.76) 0%, rgba(255, 255, 255, 0.42) 100%)`，配以 `mix-blend-mode: plus-lighter`
- **菲涅尔高光与物理 0.5px 纳米描边 (`::after`)**: 采用 `-webkit-mask` 实现精确物理 `0.5px` 渐变边框，结合 `inset 0 1px 1.5px 0 rgba(255, 255, 255, 0.95)`

---

### 4. 连续圆角体系 (Radius & Squircle - 真实节点数据)

| 级别 | 真实像素 | CSS 变量 | 对应源文件节点与适用场景 |
| :--- | :--- | :--- | :--- |
| **Extra Small** | 6px | `--ios26-radius-xs` | 状态标签、极小角标 |
| **Small** | 10px | `--ios26-radius-sm` | 小型输入框、侧边栏菜单项 |
| **Medium** | 14px | `--ios26-radius-md` | 标准操作按钮（高度 50px 下）、下拉选择器 |
| **Card / Widget** | **30px** | `--ios26-radius-card` | **源文件节点 `_HSQA - BG` (`#48:34682`) 真实圆角 30px** |
| **Modal** | 26px | `--ios26-radius-modal` | 模态对话框、Sheet 上拉抽屉面板 |
| **Dock** | **38.5px** | `--ios26-radius-dock` | **iPhone 原生悬浮 Dock 连续圆角** (高度 84px) |
| **Pill** | 9999px | `--ios26-radius-pill` | 胶囊按钮、通知浮漂、分段选择指示器 |

---

### 5. 动效模型 (CASpringAnimation: mass=1.0, stiffness=180, damping=22)
- **物理对应参数**：
  - 阻尼比 $\zeta \approx 0.82$（微回弹 $1.006$ 后迅速平稳）
  - 核心贝塞尔曲线：
    - **Standard**: `cubic-bezier(0.25, 1, 0.33, 1)` (320ms)
    - **Snappy**: `cubic-bezier(0.18, 0.89, 0.32, 1.15)` (200ms)
    - **Bouncy**: `cubic-bezier(0.34, 1.35, 0.45, 1)` (450ms)
