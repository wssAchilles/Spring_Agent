# Vue 模块 `kb` 前端重构与优化指南

## 1. 模块范围与视图清单

本次优化的目标模块为 `frontend/src/views/kb`，该目录下包含 **31** 个视图组件：

1. `agent/components/AgentRecallPreview.vue`
2. `agent/components/ApprovalDialog.vue`
3. `agent/components/ChatInput.vue`
4. `agent/components/MessageList.vue`
5. `agent/index.vue`
6. `bot/build/LoopWorkflowCanvas.vue`
7. `bot/build/components/ChatflowDebugConversation.vue`
8. `bot/build/components/ChatflowDebugRunPanel.vue`
9. `bot/build/components/DebugOverflowTooltipLabel.vue`
10. `bot/build/components/LlmNodeIcon.vue`
11. `bot/build/components/NodeTypeIcon.vue`
12. `bot/build/components/WorkflowDebugRunPanel.vue`
13. `bot/build/components/nodeConfig/ChatflowReplyNodeConfigForm.vue`
14. `bot/build/components/nodeConfig/ConditionNodeConfigForm.vue`
15. `bot/build/components/nodeConfig/LlmNodeConfigForm.vue`
16. `bot/build/components/nodeConfig/LoopNodeConfigForm.vue`
17. `bot/build/components/nodeConfig/ReplyNodeConfigForm.vue`
18. `bot/build/components/nodeConfig/StartNodeConfigForm.vue`
19. `bot/build/components/nodeConfig/ToolNodeConfigForm.vue`
20. `bot/build/index.vue`
21. `bot/detail/botApiKeyTable.vue`
22. `bot/detail/botLogTable.vue`
23. `bot/detail/index.vue`
24. `bot/index.vue`
25. `codeNative/JavaMonacoEditor.vue`
26. `codeNative/index.vue`
27. `tool/components/McpServerPanel.vue`
28. `tool/detail/index.vue`
29. `tool/detail/method.vue`
30. `tool/index.vue`
31. `tool/selection/method-multiple-selection.vue`

## 2. 当前 DOM 与 CSS 设计缺陷

通过分析上述组件代码，目前系统存在以下偏离“Apple 级别前端设计”标准的缺陷：

1. **色彩滥用（非 Monochrome 规范）**：目前代码中大量使用了具有高饱和度的品牌色、警告色（如 `type="primary"`, `type="danger"` 的 `el-button`），违反了“黑白灰单色（Monochrome）”的配色基准。
2. **材质干瘪（缺乏空间感与层次感）**：各个容器组件直接使用了 `el-card` 或 `.panel`，这些元素通常只是纯白背景加上单调的阴影，缺乏通透的物理光影质感。
3. **交互僵硬（缺失动效反馈）**：所有的按钮 `el-button` 以及可点击列表项仅依赖默认的伪类（hover/active）改变颜色，缺乏类似水波纹般柔和且生动的动态粒子动效（`v-ripple`）反馈。

## 3. 具体重构与替换方案

为了将该模块的 UI/UX 提升至 Apple 级别的“单色毛玻璃”设计语言，请严格按照以下规范对这 31 个文件进行改造：

### 3.1 颜色系统（全局 Monochrome 约束）
- **背景与文字**：全局容器的背景色强制使用 `#F5F5F7`，所有的文字颜色（包括标题、正文）应当统一使用 `#1D1D1F` 及其不同透明度。
- **剥离彩虹色**：移除所有的 `type="primary"`, `type="danger"`, `type="success"` 等 Element Plus 颜色属性。

### 3.2 材质与容器升级（Glassmorphism）
将所有的 `<el-card>` 与 `div.panel` 升级为毛玻璃材质。
- **HTML/DOM 修改**：为这些标签增加 `.glass-card` class。
- **CSS 规范实现**：
  ```css
  .glass-card {
    /* 核心毛玻璃效果 */
    backdrop-filter: blur(24px) saturate(180%);
    -webkit-backdrop-filter: blur(24px) saturate(180%);
    background: rgba(255, 255, 255, 0.4);
    
    /* 消除原生边框，使用微弱的高光边框增强质感 */
    border: 1px solid rgba(255, 255, 255, 0.3);
    border-radius: 12px; /* 建议圆角 */
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); /* 极度柔和的阴影 */
  }
  ```
- **示例**：
  - *重构前*：`<el-card shadow="hover"> ... </el-card>`
  - *重构后*：`<el-card class="glass-card" shadow="never"> ... </el-card>`（记得去掉原生 shadow）

### 3.3 交互与按钮动效（Dynamic Particles）
所有的 `<el-button>` 与自定义的 clickable 元素必须引入水波纹反馈体系。
- **HTML/DOM 修改**：附加 `class="glass-btn"` 并添加 `v-ripple` 自定义指令。
- **CSS 规范实现**：
  ```css
  .glass-btn {
    background: rgba(255, 255, 255, 0.5);
    color: #1D1D1F;
    border: 1px solid rgba(255, 255, 255, 0.2);
    backdrop-filter: blur(10px);
    border-radius: 8px; /* 根据实际场景调整 */
    transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  }
  .glass-btn:hover {
    background: rgba(255, 255, 255, 0.7);
  }
  ```
- **示例**：
  - *重构前*：`<el-button type="primary" @click="handleRun">运行</el-button>`
  - *重构后*：`<el-button class="glass-btn" v-ripple @click="handleRun">运行</el-button>`
