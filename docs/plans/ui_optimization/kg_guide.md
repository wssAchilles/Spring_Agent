# 知识图谱与管理模块 (KG Module) 前端 UI/UX 重构与优化指南

## 1. 视图层级清单
经深度分析，`frontend/src/views/kg` 模块共包含 **13** 个视图文件，层级结构梳理如下：

```text
kg/
├── graph/
│   └── index.vue (图谱主视图)
└── knowledge/
    ├── category/
    │   ├── index.vue (分类管理主视图)
    │   ├── detail/
    │   │   ├── index.vue
    │   │   ├── componentOne.vue
    │   │   └── componentTwo.vue
    │   └── selection/
    │       ├── categoryMultiple.vue
    │       └── categorySingle.vue
    └── document/
        ├── index.vue (文件管理主视图)
        ├── detail/
        │   ├── index.vue
        │   ├── componentOne.vue
        │   └── componentTwo.vue
        └── selection/
            ├── documentMultiple.vue
            └── documentSingle.vue
```

---

## 2. 现有 DOM / CSS 设计缺陷评估

结合“单色毛玻璃”与“动态粒子动效”基线规范，当前模块存在以下设计缺陷：

1. **色彩超标 (高饱和度问题)**：
   - `graph/index.vue` 中的节点着色和社区着色 (`COMMUNITY_COLORS`) 大量使用了高饱和度色彩（如 `#3b82f6` 蓝色、`#10b981` 绿色、`#f59e0b` 黄色等），严重违反了黑白灰 (Monochrome) 单色规范。
   - `category/index.vue` 与 `document/index.vue` 中大量使用了 `type="primary"`，`type="danger"` 的 Element-Plus 原生按钮，产生了视觉上的色彩混乱。
2. **材质扁平化 (缺乏空间感)**：
   - 使用了原生 `<el-card>`、以及纯背景色的 `.pagecont-top`，`.pagecont-bottom` 容器。
   - 未使用任何毛玻璃材质，UI 整体风格厚重，不符合现代 Apple 级别的通透质感规范。
3. **按钮交互匮乏**：
   - 大量按钮组件和交互图标均缺乏高级动效反馈。
   - 完全没有使用 `v-ripple` 指令产生的涟漪/粒子响应效果。

---

## 3. 具体重构与替换方案

### 3.1 颜色规范全面降级 (Monochrome 化)
1. **背景与文字基线**：
   - 全局背景替换为浅灰白：`#F5F5F7`
   - 全局文字统一为深灰黑：`#1D1D1F`
2. **图谱颜色重构 (`graph/index.vue`)**：
   - 必须移除原有彩虹色系。
   - 使用黑、白、灰渐变或透明度色阶代替，例如 `#333333`, `#666666`, `#999999`, `#CCCCCC` 等。
3. **Element-Plus 组件去色**：
   - 移除所有的 `type="primary"`, `type="danger"`, `type="success"` 属性，让它们回到默认色并结合自定义 class。

### 3.2 引入单色毛玻璃材质 (Glassmorphism)
1. 找出所有区块容器组件，如：
   - `<el-card>`
   - `class="pagecont-top"`
   - `class="pagecont-bottom"`
   - `class="left-pane"` 或 `<el-aside>`
2. 注入全局公用类 `.glass-card`：
   ```html
   <el-card class="glass-card">...</el-card>
   <div class="pagecont-top glass-card">...</div>
   ```
3. **CSS 实现定义** (建议在全局公共样式或各文件 style 中定义)：
   ```css
   .glass-card {
     backdrop-filter: blur(24px) saturate(180%);
     background: rgba(255, 255, 255, 0.4) !important;
     border: 1px solid rgba(255, 255, 255, 0.3) !important;
     border-radius: 12px;
     box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
     color: #1D1D1F;
   }
   ```

### 3.3 注入动态粒子动效 (v-ripple) 与按钮打磨
1. 查找视图中的所有的 `<el-button>` 与可点击元素 (`<a>`, `<div class="btn">` 等)。
2. 剥离原生类型，统一添加 `class="glass-btn"`，并注入 `v-ripple` 指令：
   ```html
   <!-- 改造前 -->
   <el-button type="primary" @click="handleAdd">新增</el-button>
   
   <!-- 改造后 -->
   <el-button class="glass-btn" v-ripple @click="handleAdd">新增</el-button>
   ```
3. **Glass Button CSS 定义**：
   ```css
   .glass-btn {
     backdrop-filter: blur(24px) saturate(180%);
     background: rgba(255, 255, 255, 0.5) !important;
     color: #1D1D1F !important;
     border: 1px solid rgba(0, 0, 0, 0.1) !important;
     border-radius: 8px;
     transition: all 0.3s ease;
   }
   
   .glass-btn:hover {
     background: rgba(255, 255, 255, 0.7) !important;
     box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
   }
   ```

### 3.4 模块实施步骤建议
1. 建立全局 CSS 资源：在 `src/assets/styles` 或类似目录下注册 `.glass-card` 和 `.glass-btn`。
2. 确保 Vue 项目已挂载 `v-ripple` 指令。
3. 按照“图谱模块 -> 分类模块 -> 文档模块”的顺序，逐文件清洗 HTML 模板并覆写。 
4. 启动前端检查 UI 的对比度与通透感，确保符合设计基准。
