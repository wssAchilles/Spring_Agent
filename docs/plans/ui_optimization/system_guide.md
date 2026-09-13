# System 模块重构与优化指南 (Monochrome Glassmorphism)

## 1. 模块概览

- **目标模块**：`frontend/src/views/system`
- **视图数量**：52 个视图组件
- **设计基线规范**：
  1. **配色**：黑白灰单色（Monochrome），背景使用 `#F5F5F7`，文字使用 `#1D1D1F`。禁止使用高饱和度的彩虹色。
  2. **材质**：全面应用单色毛玻璃（Glassmorphism）。
  3. **交互**：所有可点击元素与按钮须加入动态粒子动效（`v-ripple` 指令）。

---

## 2. 视图清单 (共52个)

<details>
<summary>点击展开 52 个视图文件列表</summary>

1. `index.vue`
2. `login.vue`
3. `login-bak.vue`
4. `login-v2.vue`
5. `login-v3.vue`
6. `register.vue`
7. `sso.vue`
8. `developing/index.vue`
9. `redirect/index.vue`
10. `error/401.vue`
11. `error/404.vue`
12. `auth/client/index.vue`
13. `ca/subject/index.vue`
14. `ca/cert/index.vue`
15. `monitor/cache/index.vue`
16. `monitor/cache/list.vue`
17. `monitor/operlog/index.vue`
18. `monitor/server/index.vue`
19. `monitor/druid/index.vue`
20. `monitor/job/index.vue`
21. `monitor/job/log.vue`
22. `monitor/online/index.vue`
23. `monitor/logininfor/index.vue`
24. `system/role/index.vue`
25. `system/role/authUser.vue`
26. `system/role/selectUser.vue`
27. `system/post/index.vue`
28. `system/config/index.vue`
29. `system/content/index.vue`
30. `system/message/index.vue`
31. `system/message/components/messageList.vue`
32. `system/message/components/item.vue`
33. `system/user/index.vue`
34. `system/user/profile/index.vue`
35. `system/user/profile/resetPwd.vue`
36. `system/user/profile/userInfo.vue`
37. `system/user/profile/userAvatar.vue`
38. `system/user/authRole.vue`
39. `system/notice/index.vue`
40. `system/messageTemplate/index.vue`
41. `system/menu/index.vue`
42. `system/dept/index.vue`
43. `system/dict/index.vue`
44. `system/dict/data.vue`
45. `tool/swagger/index.vue`
46. `tool/gen/index.vue`
47. `tool/gen/basicInfoForm.vue`
48. `tool/gen/createTable.vue`
49. `tool/gen/genInfoForm.vue`
50. `tool/gen/importTable.vue`
51. `tool/gen/editTable.vue`
52. `tool/build/index.vue`

</details>

---

## 3. 现有 DOM/CSS 缺陷分析

通过代码扫描，当前 `system` 模块内存在以下违背新设计规范的遗留问题：

1. **色彩滥用（高饱和度）**：
   大量 `<el-button>` 依旧使用了 Element UI 默认的状态色彩体系，如 `type="primary"`, `type="success"`, `type="warning"`, `type="danger"`, `type="info"`。
   这在 `login.vue`, `system/user/index.vue`, `system/dict/data.vue`, `tool/gen/editTable.vue` 等超过 35 个视图中广泛存在，打破了 Monochrome 单色规范。
2. **缺乏毛玻璃材质 (Glassmorphism)**：
   现有的容器（如 `<el-card>`、以及带有 `panel` 类名的 `<div class="app-container">` 等），仍使用的是纯白实体背景（`#FFFFFF`）加原生边框或投影，未引入模糊半透明材质。
3. **按钮与交互元素缺少粒子动效**：
   目前的 `<el-button>` 及其它交互型 DOM 没有统一加上 `class="glass-btn"`，也没有集成 `v-ripple` 动态粒子指令，使得交互反馈显得生硬。

---

## 4. 重构与优化执行方案

### 4.1 全局材质替换 (Glassmorphism Cards)

将所有 `<el-card>` 与主要容器盒子替换为毛玻璃组件。
- **操作方式**：可以直接在组件上增加 `.glass-card` class。
- **CSS 规范（如果未全局配置，需确保存在）**：
  ```css
  .glass-card {
    background: rgba(255, 255, 255, 0.4) !important;
    backdrop-filter: blur(24px) saturate(180%);
    -webkit-backdrop-filter: blur(24px) saturate(180%);
    border: 1px solid rgba(255, 255, 255, 0.3);
    border-radius: 12px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  }
  ```

**代码替换示例**：
```html
<!-- 改造前 -->
<el-card class="box-card">
  ...
</el-card>

<!-- 改造后 -->
<el-card class="box-card glass-card">
  ...
</el-card>
<!-- 或者直接使用 div -->
<div class="glass-card">
  ...
</div>
```

### 4.2 配色降级与交互动效 (Monochrome & v-ripple)

移除所有带有颜色倾向的 type 属性（如 `primary`, `success`），改为中性色表现，并加入 `.glass-btn` 与 `v-ripple`。

- **操作方式**：批量去除 `<el-button>` 中的 `type="xxx"`，并加上 `class="glass-btn" v-ripple`。

**代码替换示例**：
```html
<!-- 改造前 -->
<el-button type="primary" icon="el-icon-plus" size="mini">新增</el-button>
<el-button type="success" icon="el-icon-edit" size="mini">修改</el-button>
<el-button type="danger" icon="el-icon-delete" size="mini">删除</el-button>

<!-- 改造后 -->
<el-button class="glass-btn" icon="el-icon-plus" size="mini" v-ripple>新增</el-button>
<el-button class="glass-btn" icon="el-icon-edit" size="mini" v-ripple>修改</el-button>
<el-button class="glass-btn" icon="el-icon-delete" size="mini" v-ripple>删除</el-button>
```

- **对应的 CSS 规范（如果未全局配置）**：
  ```css
  .glass-btn {
    background: rgba(255, 255, 255, 0.5) !important;
    backdrop-filter: blur(12px);
    border: 1px solid rgba(255, 255, 255, 0.6) !important;
    color: #1D1D1F !important; /* 字体强制遵守单色规范 */
    transition: all 0.3s ease;
  }
  .glass-btn:hover {
    background: rgba(255, 255, 255, 0.8) !important;
  }
  ```

### 4.3 整体背景与排版色控

需确保模块所处的基础背景层设置了背景色 `#F5F5F7`，所有的字体颜色默认重置为 `#1D1D1F`。可以通过在父级 `App.vue` 或 `layout` 统一注入。
如果在某些视图（例如 `login.vue`，`register.vue` 这种存在独立 full-page background 的页面）内硬编码了背景图或颜色，需一并移除并接入 `#F5F5F7` 的基底。

---

## 5. 行动清单 (Action Items)

- [ ] 针对 52 个文件，执行正则表达式批量替换（将 `<el-button type="(primary|success|warning|danger|info)"` 替换为 `<el-button class="glass-btn" v-ripple`，需注意保留其余 props）。
- [ ] 定位所有 52 个文件内的 `<el-card>` 与 `.app-container` 容器，逐一附加 `.glass-card`。
- [ ] 清理所有自定义写入的高饱和度行内样式 (`style="color: red"`, `background-color: ...`)。
- [ ] 检查全局样式表中是否已正确定义 `v-ripple` 指令、`.glass-card` 及 `.glass-btn` 样式类。若没有，在 `src/assets/styles` 或类似目录下新增 `monochrome-glass.css`。
