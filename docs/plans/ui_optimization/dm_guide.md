# `dm` 模块重构与优化指南 (Monochrome Glassmorphism & v-ripple)

## 1. 视图清单
`frontend/src/views/dm` 模块包含以下 4 个视图：
1. **dmAlarmConfig** (`dmAlarmConfig/index.vue`) - 告警配置视图
2. **dmDatasource** (`dmDatasource/index.vue`) - 数据源配置视图
3. **dmExpertAdvice** (`dmExpertAdvice/index.vue`) - 专家经验配置视图
4. **dmMeasurePoint** (`dmMeasurePoint/index.vue`) - 物联网测点配置视图

## 2. 当前 DOM/CSS 存在的问题
1. **配色不符合单色 (Monochrome) 规范**：现有代码大量使用了高饱和度的 Element UI 默认彩色按钮（如 `<el-button type="primary">`, `<el-button type="danger">`, `<el-button type="warning">`, `<el-button type="info">`），违反了仅允许黑白灰及背景 `#F5F5F7`、文字 `#1D1D1F` 的配色规范。
2. **材质缺乏毛玻璃 (Glassmorphism) 质感**：页面的主要结构层如 `div.app-container`、`div.pagecont-top`、`div.pagecont-bottom` 均未使用毛玻璃效果。弹窗 (`<el-dialog>`) 的背景也为普通纯色。
3. **缺乏动态粒子动效**：所有的 `<el-button>`、表格操作按钮以及分页控件等可交互元素，都没有添加 `v-ripple` 动效指令和对应的 `.glass-btn` 类。

## 3. 具体替换与重构方案

### 3.1 容器与卡片材质替换
所有作为板块容器的 `div`，特别是原有的白底面板，需要应用 `.glass-card` 毛玻璃类，确保包含如下 CSS 规范（或通过引入全局的 `.glass-card` 实现）：
```css
backdrop-filter: blur(24px) saturate(180%);
background: rgba(255,255,255,0.4);
```
**实施方法**：
- 修改顶层容器结构：
  ```html
  <!-- 修改前 -->
  <div class="pagecont-top">...</div>
  <div class="pagecont-bottom">...</div>
  
  <!-- 修改后 -->
  <div class="pagecont-top glass-card">...</div>
  <div class="pagecont-bottom glass-card">...</div>
  ```
- 对所有的 `<el-dialog>` 弹窗增加对应的毛玻璃自定义类（例如添加 `class="glass-card"` 或对应深度作用域样式重写背景）。

### 3.2 按钮交互与色彩重构
移除所有的 Element Plus 默认状态颜色，全面采用 `.glass-btn` 并加入 `v-ripple` 指令。
**实施方法**：
- **操作栏按钮**：
  ```html
  <!-- 修改前 -->
  <el-button type="primary" plain @click="handleAdd">
    <i class="iconfont-mini icon-xinzeng mr5"></i>新增
  </el-button>
  
  <!-- 修改后 -->
  <el-button class="glass-btn" v-ripple @click="handleAdd">
    <i class="iconfont-mini icon-xinzeng mr5"></i>新增
  </el-button>
  ```
- **表格内操作按钮**：
  ```html
  <!-- 修改前 -->
  <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
  <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
  
  <!-- 修改后 -->
  <el-button class="glass-btn" link v-ripple icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
  <el-button class="glass-btn" link v-ripple icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
  ```
- **弹窗内底部按钮**：
  ```html
  <!-- 修改前 -->
  <el-button size="small" @click="cancel">取 消</el-button>
  <el-button type="primary" size="small" @click="submitForm">确 定</el-button>
  
  <!-- 修改后 -->
  <el-button class="glass-btn" v-ripple size="small" @click="cancel">取 消</el-button>
  <el-button class="glass-btn" v-ripple size="small" @click="submitForm">确 定</el-button>
  ```

### 3.3 全局色值调整
- 确保页面最底层背景色设置为 `#F5F5F7`（可通过外层布局组件或 `app-container` 控制）。
- 确保全局默认字体颜色在这些视图中生效，呈现为 `#1D1D1F`。
- 如果表格组件 `<el-table>` 包含固定的白色背景条纹，需重写其样式使其透明（`background: transparent`），以透出后方的 `glass-card` 毛玻璃质感。
- 删除或替换状态切换 `<el-tag type="primary">` 或 `<el-tag type="danger">` 为符合规范的黑白灰标签（可利用内置的 `info` 类型或自定义灰度样式）。
