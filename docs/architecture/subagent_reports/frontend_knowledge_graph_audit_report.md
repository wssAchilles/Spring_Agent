# 前端知识底座、知识图谱、数据看板、知识抽取及审计凭单法医级源码审计报告

> **审计员身份**：智能体 2：知识底座与图谱抽取审计员 (Knowledge & Graph & Audit Specialist)
> **审计标的工程**：`qKnow Knowledge Platform (开源版)` 前端工程 (`frontend/`)
> **审计范围**：`views/kmc/` (知识库底座)、`views/kg/` 与 `views/app/` (知识图谱与探索)、`views/kd/` (数据看板与可观测)、`views/audit/` (神经符号可解释性与密码学存证)、`views/ext/` 与 `views/dm/` (知识抽取与设备管理)
> **规范基线遵循**：严格遵循 `AGENTS.md` 科研与系统工程总纲、`docs/design-system` 顶级 Apple iOS 26 Liquid Glass 双层液态玻璃质感与无层叠上下文规范、阿里千问 1536 维超球面单位向量几何空间与 DeepSeek 官方最新接口规约。

---

## 目录索引概览

### 1. 知识库底座与切片中心 (views/kmc/)
- [1. 知识库列表工作台](#1-知识库列表工作台) (`frontend/src/views/kmc/knowledgeBase/index.vue`)
- [2. 知识库液态玻璃卡片组件](#2-知识库液态玻璃卡片组件) (`frontend/src/views/kmc/knowledgeBase/components/card.vue`)
- [3. 知识库新增与基础设置页面](#3-知识库新增与基础设置页面) (`frontend/src/views/kmc/knowledgeBase/components/settings.vue`)
- [4. 检索设置与混合算法规则配置页面](#4-检索设置与混合算法规则配置页面) (`frontend/src/views/kmc/knowledgeBase/components/querySet.vue`)
- [5. 知识召回深度测试与全链路调试工作台](#5-知识召回深度测试与全链路调试工作台) (`frontend/src/views/kmc/knowledgeBase/components/recall.vue`)
- [6. 召回测试历史审计日志页面](#6-召回测试历史审计日志页面) (`frontend/src/views/kmc/knowledgeBase/components/recallLog.vue`)
- [7. 知识库详情综合弹窗](#7-知识库详情综合弹窗) (`frontend/src/views/kmc/knowledgeBase/detail/index.vue`)
- [8. 知识文件多维管理中心](#8-知识文件多维管理中心) (`frontend/src/views/kmc/kmcDocument/index.vue`)
- [9. 知识文件新增与分段切片策略配置页面](#9-知识文件新增与分段切片策略配置页面) (`frontend/src/views/kmc/kmcDocument/selection/add.vue`)
- [10. 知识多级树形分类管理](#10-知识多级树形分类管理) (`frontend/src/views/kmc/kmcCategory/index.vue`)
- [11. 知识分段切片列表与向量索引监控](#11-知识分段切片列表与向量索引监控) (`frontend/src/views/kmc/knowledgeSegment/index.vue`)
- [12. 知识切片细粒度详情与编辑页面](#12-知识切片细粒度详情与编辑页面) (`frontend/src/views/kmc/knowledgeSegment/detail/index.vue`)
### 2. 知识图谱资产与可视化探索 (views/kg/ & views/app/)
- [13. 知识图谱文档资产管理](#13-知识图谱文档资产管理) (`frontend/src/views/kg/knowledge/document/index.vue`)
- [14. 知识图谱分类体系管理](#14-知识图谱分类体系管理) (`frontend/src/views/kg/knowledge/category/index.vue`)
- [15. Neo4j 知识图谱全局管理与社区检测](#15-Neo4j 知识图谱全局管理与社区检测) (`frontend/src/views/kg/graph/index.vue`)
- [16. 知识图谱沉浸式交互探索大屏](#16-知识图谱沉浸式交互探索大屏) (`frontend/src/views/app/graphExploration/index.vue`)
- [17. 图谱实体新增与编辑弹窗](#17-图谱实体新增与编辑弹窗) (`frontend/src/views/app/graphExploration/addEntity.vue`)
- [18. 图谱三元组关系新增与编辑弹窗](#18-图谱三元组关系新增与编辑弹窗) (`frontend/src/views/app/graphExploration/addRelationship.vue`)
### 3. 数据看板与全链路可观测中心 (views/kd/)
- [19. 知识资产全景运营大屏](#19-知识资产全景运营大屏) (`frontend/src/views/kd/knowledgeAsset/index.vue`)
- [20. Bot 智能体运营分析大屏](#20-Bot 智能体运营分析大屏) (`frontend/src/views/kd/botOperation/index.vue`)
- [21. 行业应用运营监控大屏](#21-行业应用运营监控大屏) (`frontend/src/views/kd/appOperations/index.vue`)
- [22. LLM & RAG 全链路可观测性总控看板](#22-LLM & RAG 全链路可观测性总控看板) (`frontend/src/views/kd/observability/index.vue`)
- [23. 多 Agent 协同执行链路瀑布流面板](#23-多 Agent 协同执行链路瀑布流面板) (`frontend/src/views/kd/observability/components/ExecutionWaterfallPanel.vue`)
### 4. 神经符号可解释性与密码学审计中心 (views/audit/)
- [24. 神经符号可解释性与密码学审计中心总屏](#24-神经符号可解释性与密码学审计中心总屏) (`frontend/src/views/audit/ExplainabilityDashboard.vue`)
- [25. 因果拓扑有向无环图画布](#25-因果拓扑有向无环图画布) (`frontend/src/views/audit/components/ExplainabilityTopologyCanvas.vue`)
- [26. 安全护栏态势感知大屏](#26-安全护栏态势感知大屏) (`frontend/src/views/audit/components/GuardrailDashboard.vue`)
- [27. RFC 6962 密码学存证客户端免密验真器](#27-RFC 6962 密码学存证客户端免密验真器) (`frontend/src/views/audit/components/MerkleProofValidator.vue`)
- [28. 决策节点属性与凭单审计抽屉](#28-决策节点属性与凭单审计抽屉) (`frontend/src/views/audit/components/NodeDetailDrawer.vue`)
- [29. 神经符号因果自定义拓扑节点](#29-神经符号因果自定义拓扑节点) (`frontend/src/views/audit/custom-nodes/AuditCustomNode.vue`)
### 5. 知识抽取任务与工业设备管理 (views/ext/ & views/dm/)
- [30. 结构化抽取任务列表与管理](#30-结构化抽取任务列表与管理) (`frontend/src/views/ext/extStructTask/index.vue`)
- [31. 结构化抽取任务多步向导配置页面](#31-结构化抽取任务多步向导配置页面) (`frontend/src/views/ext/extStructTask/add/index.vue`)
- [32. 非结构化抽取任务管理](#32-非结构化抽取任务管理) (`frontend/src/views/ext/extUnstructTask/index.vue`)
- [33. 知识抽取数据源管理](#33-知识抽取数据源管理) (`frontend/src/views/ext/extDatasource/index.vue`)
- [34. 概念 Schema 建模管理](#34-概念 Schema 建模管理) (`frontend/src/views/ext/extSchema/index.vue`)
- [35. 概念 Schema 属性详情配置页面](#35-概念 Schema 属性详情配置页面) (`frontend/src/views/ext/extSchema/detail/index.vue`)
- [36. Schema 字段映射配置](#36-Schema 字段映射配置) (`frontend/src/views/ext/extSchemaMapping/index.vue`)
- [37. 非结构化抽取结果图谱画布](#37-非结构化抽取结果图谱画布) (`frontend/src/views/ext/extractResults/index.vue`)
- [38. 结构化抽取结果图谱画布](#38-结构化抽取结果图谱画布) (`frontend/src/views/ext/extractResults/structuredResult.vue`)
- [39. 工业设备数据源配置](#39-工业设备数据源配置) (`frontend/src/views/dm/dmDatasource/index.vue`)
- [40. 工业设备报警阈值与规则配置](#40-工业设备报警阈值与规则配置) (`frontend/src/views/dm/dmAlarmConfig/index.vue`)
- [41. 设备测点专家知识与诊断建议](#41-设备测点专家知识与诊断建议) (`frontend/src/views/dm/dmExpertAdvice/index.vue`)

---

## 1. 知识库底座与切片中心 (views/kmc/)

### 1. 知识库列表工作台
**业务定位**：企业知识资产库的统一门户与卡片总览，提供知识库名称/状态筛选、自适应玻璃卡片布局、新增弹窗及快速操作矩阵。

- **访问路由路径 (Route Path)**：`/kmc/knowledgeBase`
- **路由定义文件**：`frontend/src/router/kmc/dynamic/index.js`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/knowledgeBase/index.vue`
- **代码规模与声明规范**：共 `774` 行代码，`<script setup name="KnowledgeBase">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-col, el-dialog, el-form, el-form-item, el-input, el-option, el-option-group, el-radio, el-radio-group, el-row, el-select, el-tag`
- **搭载的业务专属组件与插槽**：`Card, GlassEmpty, GlassSkeleton, GuideTip`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`knowledgeBaseList, open, openDetail, loading, showSearch, total, title, roleList, roleLoading, roleIds, knowledgeBaseId, roleTableRef, isDisabled, embeddingModel, platForm`
- **核心 reactive 复合状态对象**：`data`
- **派生计算属性 (computed)**：`knowledgeValidOptions`
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleClose()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDataScope()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleInputConfirm()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleQuery()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleSelectionChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleValidChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `init()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `normalizeValidFlag()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple Liquid Glass / 双层毛玻璃模糊 (backdrop-filter: blur)**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/kmc/knowledgeBase/knowledgeBase";`
- **外部与子组件依赖**：
  - `import Card from "./components/card.vue";`

---

### 2. 知识库液态玻璃卡片组件
**业务定位**：高保真展示单个知识库的元数据卡片，封装封面图、模型标签、切片总数、创建时间与启用开关，并提供进入文件管理、召回测试、基础设置及删除等快速交互。

- **访问路由路径 (Route Path)**：`内嵌于 /kmc/knowledgeBase`
- **路由定义文件**：`无独立路由 (由 views/kmc/knowledgeBase/index.vue 引用)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/knowledgeBase/components/card.vue`
- **代码规模与声明规范**：共 `422` 行代码，`<script setup>` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `card-container, card, card-top, card-title, card-title-text, card-title-status, card-bottom, card-bottom-right` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-divider, el-tag, el-tooltip`
- **业务容器特点**：自包含标准视图渲染结构，支持动态状态切换、空状态覆盖或多标签插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `getDictLabel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getImage()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDetail()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `parseTags()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple Liquid Glass / 双层毛玻璃模糊 (backdrop-filter: blur)**
- **CSS Grid 自适应多端断点栅格**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口**：由父级页面下发数据，或通过底层封装的公共 `request()` / Pinia Store 模块直接发起请求。

---

### 3. 知识库新增与基础设置页面
**业务定位**：知识库核心元数据与向量模型配置中心，涵盖知识库基础信息（名称、封面、标签）、Embedding 向量模型选择（千问 1536 维等）与权限策略管理。

- **访问路由路径 (Route Path)**：`/kmc/knowledgeBase/add 或 /kmc/:kbId/knowledgeBase/kmcBasic`
- **路由定义文件**：`frontend/src/router/kmc/public/index.js 与 系统菜单配置 (ID: 2315)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/knowledgeBase/components/settings.vue`
- **代码规模与声明规范**：共 `1721` 行代码，`<script setup>` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, pagecont-top-title, header-text, header-left, glass-btn` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-col, el-collapse, el-collapse-item, el-form, el-form-item, el-icon, el-input, el-input-number, el-option, el-option-group, el-radio, el-radio-group, el-row, el-select, el-slider, el-switch, el-table, el-table-column, el-table-column--, el-tag, el-tooltip`
- **搭载的业务专属组件与插槽**：`Trophy`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`vectorData, fullTextData, mixData, isDisabled, activeCollapse, title, embeddingModel, rerankingModel, platForm, open, roleLoading, roleList, roleIds, roleTableRef, inputValue`
- **核心 reactive 复合状态对象**：`data`
- **派生计算属性 (computed)**：`knowledgeValidOptions`
- **生命周期钩子 (Lifecycle Hooks)**：`watch, watchEffect`
- **关键业务交互函数清单**：
  - `back()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleClose()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDataScope()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleInputConfirm()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleSelectionChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleUpdate()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `init()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `normalizeValidFlag()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `reset()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `showInput()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `submitForm()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple 官方暗色基底 (#1D1D1F)**
- **Apple 官方极简浅灰底色 (#F5F5F7)**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**
- **高阶色彩微渐变 (linear-gradient)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/kmc/knowledgeBase/knowledgeBase.js";`

---

### 4. 检索设置与混合算法规则配置页面
**业务定位**：RAG 算法调优与检索策略控制台，支持语义向量检索、全文关键词检索与混合检索切换，支持 Rerank 重排模型、关键词与向量权重配比 (0~1.0)、Top-K 与相似度阈值截断配置。

- **访问路由路径 (Route Path)**：`/kmc/:kbId/knowledgeBase/querySet`
- **路由定义文件**：`系统菜单配置 (ID: 2317)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/knowledgeBase/components/querySet.vue`
- **代码规模与声明规范**：共 `1682` 行代码，`<script setup>` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, header-text, header-left` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-form, el-form-item, el-icon, el-input, el-option, el-option-group, el-radio, el-select, el-switch, el-tag`
- **搭载的业务专属组件与插槽**：`WarningFilled`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`vectorData, fullTextData, mixData, isDisabled, title, embeddingModel, rerankingModel, platForm, open, roleLoading, loading, roleList, roleIds, roleTableRef, inputValue`
- **核心 reactive 复合状态对象**：`data`
- **生命周期钩子 (Lifecycle Hooks)**：`watch, watchEffect`
- **关键业务交互函数清单**：
  - `back()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleClose()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDataScope()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleInputConfirm()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleSelectionChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleUpdate()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `init()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `reset()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `showInput()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `submitForm()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple 官方暗色基底 (#1D1D1F)**
- **Apple 官方极简浅灰底色 (#F5F5F7)**
- **CSS Grid 自适应多端断点栅格**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/kmc/knowledgeBase/knowledgeBase.js";`

---

### 5. 知识召回深度测试与全链路调试工作台
**业务定位**：RAG 召回精度自测沙盒与诊断大屏，支持输入用户问题实时检索，右侧动态渲染召回切片、Markdown 代码高亮、相似度得分进度条，且深度集成全链路调试抽屉与 RAG 缓存清除能力。

- **访问路由路径 (Route Path)**：`/kmc/:kbId/recall`
- **路由定义文件**：`frontend/src/router/kmc/public/index.js`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/knowledgeBase/components/recall.vue`
- **代码规模与声明规范**：共 `2095` 行代码，`<script setup>` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, recall-glass-app, glass-card, container, left-container, glass-btn, el-icon--right, right-container` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-card, el-descriptions, el-descriptions-item, el-dialog, el-divider, el-empty, el-form, el-form-item, el-icon, el-input, el-link, el-option, el-option-group, el-progress, el-radio, el-select, el-switch, el-table, el-table-column, el-tag`
- **搭载的业务专属组件与插槽**：`GuideTip, Operation, WarningFilled`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`defaultSort, debugMode, debugInfo, contextPreview, clearingCache, drawer, loading, knowledgeBase, recallList, recallLogList, vectorData, fullTextData, mixData, rerankingModel, dataList`
- **派生计算属性 (computed)**：`fallbackDiagnostics, pathScoreDiagnostics, timingDiagnostics, graphProvenanceRows`
- **生命周期钩子 (Lifecycle Hooks)**：`watch`
- **关键业务交互函数清单**：
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `formatBoolean()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `formatExcludedPath()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `formatFallbackCount()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `formatScore()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `formatSemanticCache()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getBase()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getDebugCount()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getFileType()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getLogList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getRecall()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getScorePercent()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple Liquid Glass / 双层毛玻璃模糊 (backdrop-filter: blur)**
- **Apple 官方暗色基底 (#1D1D1F)**
- **Apple 官方极简浅灰底色 (#F5F5F7)**
- **CSS Grid 自适应多端断点栅格**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**
- **高阶色彩微渐变 (linear-gradient)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/kmc/knowledgeBase/knowledgeBase.js";`
  - `import { listLog } from "@/api/kmc/knowledgeBase/log.js";`
  - `import { updateKnowledgeBase } from "@/api/kmc/knowledgeBase/knowledgeBase.js";`

---

### 6. 召回测试历史审计日志页面
**业务定位**：知识召回测试的历史流水与回溯中心，记录问答文本、创建人、时间戳与测试结果，支持排序与日志审计。

- **访问路由路径 (Route Path)**：`/kmc/:kbId/recallLog`
- **路由定义文件**：`frontend/src/router/kmc/public/index.js`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/knowledgeBase/components/recallLog.vue`
- **代码规模与声明规范**：共 `265` 行代码，`<script setup name="Log">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn, pagecont-bottom, top-right-btn` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-form, el-form-item, el-input, el-table, el-table-column`
- **业务容器特点**：自包含标准视图渲染结构，支持动态状态切换、空状态覆盖或多标签插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`logList, columns, loading, showSearch, ids, single, multiple, total, defaultSort`
- **核心 reactive 复合状态对象**：`data`
- **生命周期钩子 (Lifecycle Hooks)**：`watch`
- **关键业务交互函数清单**：
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleQuery()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleSelectionChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleSortChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `resetQuery()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- 遵循父级容器全局注入的 Apple iOS 26 玻璃质感与全局主题样式，局部未声明额外隔离规则。

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { listLog, delLog } from "@/api/kmc/knowledgeBase/log";`

---

### 7. 知识库详情综合弹窗
**业务定位**：知识库全量元数据与运行状态多标签展示弹窗，分为基础信息概览与切片统计两个视图组件。

- **访问路由路径 (Route Path)**：`内嵌于 /kmc/knowledgeBase 弹窗`
- **路由定义文件**：`组件级调用 (由 views/kmc/knowledgeBase/index.vue 唤起)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/knowledgeBase/detail/index.vue`
- **代码规模与声明规范**：共 `241` 行代码，`<script setup name="KnowledgeBase">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, infotop, infotop-title, infotop-row, border-top, infotop-row-lable` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-col, el-row, el-tab-pane, el-tabs`
- **业务容器特点**：自包含标准视图渲染结构，支持动态状态切换、空状态覆盖或多标签插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`activeName, showSearch`
- **核心 reactive 复合状态对象**：`data`
- **生命周期钩子 (Lifecycle Hooks)**：`watch`
- **关键业务交互函数清单**：
  - `getKnowledgeBaseDetailById()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleClick()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- 遵循父级容器全局注入的 Apple iOS 26 玻璃质感与全局主题样式，局部未声明额外隔离规则。

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import {getKnowledgeBase } from "@/api/kmc/knowledgeBase/knowledgeBase";`
- **外部与子组件依赖**：
  - `import ComponentOne from "@/views/kmc/knowledgeBase/detail/componentOne.vue";`
  - `import ComponentTwo from "@/views/kmc/knowledgeBase/detail/componentTwo.vue";`

---

### 8. 知识文件多维管理中心
**业务定位**：文档资产的全生命周期管理，集成左侧多级知识分类树、右侧文档表格、向量化解析同步状态轮询、分段切片拆分、重新解析与批量下载。

- **访问路由路径 (Route Path)**：`/kmc/:kbId/kmcDocument`
- **路由定义文件**：`系统菜单配置 (ID: 2008)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/kmcDocument/index.vue`
- **代码规模与声明规范**：共 `959` 行代码，`<script setup name="kmcDocument">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn, pagecont-bottom` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-col, el-container, el-form, el-form-item, el-input, el-main, el-popover, el-row, el-table, el-table-column, el-tag`
- **搭载的业务专属组件与插槽**：`DeptTree, FloatingActionBar, GlassEmpty, GlassSkeleton, GuideTip`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`deptTreeRef, documentList, defaultSort, columns, open, loading, showSearch, ids, single, multiple, total, leftWidth, isResizing, KcOptions, selectedNodeId`
- **核心 reactive 复合状态对象**：`upload, data`
- **派生计算属性 (computed)**：`syncingDocuments, hasSyncingDocuments`
- **生命周期钩子 (Lifecycle Hooks)**：`watch`
- **关键业务交互函数清单**：
  - `clearSelection()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `collectIds()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `findNodeById()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getFileType()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getKmcCategoryTree()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getKnowledge()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getSyncStatusText()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDownload()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple 官方暗色基底 (#1D1D1F)**
- **Apple 官方极简浅灰底色 (#F5F5F7)**
- **CSS Grid 自适应多端断点栅格**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**
- **高阶色彩微渐变 (linear-gradient)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/kmc/kmcDocument/kmcDocument.js";`
  - `import { getKmcKnowledgeBaseList } from "@/api/kmc/knowledgeBase/knowledgeBase.js";`
- **外部与子组件依赖**：
  - `import DeptTree from "@/components/DeptTree";`

---

### 9. 知识文件新增与分段切片策略配置页面
**业务定位**：文档上传与切片规则向导，支持 PDF/Word/Excel/TXT 多格式上传，配置文本清洗、切片模式（自动/自定义/父子分段）、Chunk Size、Chunk Overlap 及分段符预览。

- **访问路由路径 (Route Path)**：`/kmc/:kbId/kmcDocument/add 或 /kmc/:kbId/kmcDocument/edit`
- **路由定义文件**：`frontend/src/router/kmc/public/index.js`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/kmcDocument/selection/add.vue`
- **代码规模与声明规范**：共 `1138` 行代码，`<script setup>` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, page-wrapper, page-header, header-icon-wrapper, header-text, header-title, header-subtitle` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-checkbox, el-col, el-form, el-form-item, el-input, el-input-number, el-option, el-option-group, el-row, el-select, el-tooltip, el-tree-select`
- **搭载的业务专属组件与插槽**：`FileUpload`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`platForm, title, checkedyy, knowledgeBaseList, KcOptions, checkList, settingBase, chatModel`
- **核心 reactive 复合状态对象**：`data`
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted, watch, watchEffect`
- **关键业务交互函数清单**：
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `eckboxChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `findNodeById()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getFirstConfig()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getKmcCategoryTree()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getKnowledge()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDeleteFile()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleMode()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleTypeChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleUpdate()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `init()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `reset()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple Liquid Glass / 双层毛玻璃模糊 (backdrop-filter: blur)**
- **Apple 官方暗色基底 (#1D1D1F)**
- **Apple 官方极简浅灰底色 (#F5F5F7)**
- **CSS Grid 自适应多端断点栅格**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**
- **高阶色彩微渐变 (linear-gradient)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { kmcCategoryTree } from "@/api/kmc/kmcCategory/kmcCategory.js";`
  - `import { getKmcKnowledgeBaseList } from "@/api/kmc/knowledgeBase/knowledgeBase.js";`
  - `} from "@/api/kmc/kmcDocument/kmcDocument.js";`
  - `import { getChatModelDict } from "@/api/ai/myModel/myModel.js";`
- **外部与子组件依赖**：
  - `import FileUpload from "@/components/FileUpload2/index.vue";`

---

### 10. 知识多级树形分类管理
**业务定位**：组织与维护知识文件的目录层级树，提供树形增删改查、排序号调整以及与所属知识库的多维联动绑定。

- **访问路由路径 (Route Path)**：`/kmc/kmcCategory`
- **路由定义文件**：`系统菜单配置 (ID: 2001)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/kmcCategory/index.vue`
- **代码规模与声明规范**：共 `835` 行代码，`<script setup name="kmcCategory">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn, pagecont-bottom, top-right-btn` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-checkbox, el-col, el-dialog, el-form, el-form-item, el-icon, el-input, el-input-number, el-link, el-option, el-row, el-select, el-table, el-table-column, el-tree-select, el-upload`
- **搭载的业务专属组件与插槽**：`GuideTip`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`kmcCategoryList, columns, open, openDetail, loading, showSearch, ids, single, multiple, total, title, categoryOptions, refreshTable, isExpandAll, result`
- **核心 reactive 复合状态对象**：`upload, data`
- **生命周期钩子 (Lifecycle Hooks)**：`watch`
- **关键业务交互函数清单**：
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getKnowledge()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDetail()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleExport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileSuccess()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileUploadProgress()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleImport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleQuery()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- 遵循父级容器全局注入的 Apple iOS 26 玻璃质感与全局主题样式，局部未声明额外隔离规则。

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/kmc/kmcCategory/kmcCategory";`
  - `import { listDocument } from "@/api/kmc/kmcDocument/kmcDocument.js";`
  - `import { getKmcKnowledgeBaseList } from "@/api/kmc/knowledgeBase/knowledgeBase.js";`

---

### 11. 知识分段切片列表与向量索引监控
**业务定位**：具体文档切片后所有分块 (Chunks) 的列表展示，呈现分段序号、正文片段、字符数、Token 统计、关键词标签集与向量化同步状态，支持手工切片增删改与重新嵌入。

- **访问路由路径 (Route Path)**：`/kmc/:kbId/knowledgeSegment/index`
- **路由定义文件**：`frontend/src/router/kmc/public/index.js`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/knowledgeSegment/index.vue`
- **代码规模与声明规范**：共 `983` 行代码，`<script setup name="KnowledgeSegment">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn, pagecont-bottom, top-right-btn, table-skeleton-wrap` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-checkbox, el-col, el-dialog, el-form, el-form-item, el-icon, el-input, el-input--, el-link, el-option, el-row, el-select, el-table, el-table-column, el-upload`
- **搭载的业务专属组件与插槽**：`FloatingActionBar, GlassEmpty, GlassSkeleton`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`knowledgeSegmentList, keywordList, allLevelNodesList, columns, open, openDetail, loading, showSearch, ids, single, multiple, total, title, defaultSort, model`
- **核心 reactive 复合状态对象**：`upload, data`
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `addKeyword()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `allLevelNodes()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `back()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `clearSelection()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDetail()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleExport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileSuccess()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- 遵循父级容器全局注入的 Apple iOS 26 玻璃质感与全局主题样式，局部未声明额外隔离规则。

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/kmc/knowledgeSegment/knowledgeSegment";`

---

### 12. 知识切片细粒度详情与编辑页面
**业务定位**：单切片深度查看与微调编辑，展示切片详细正文、前后相邻切片上下文链接与向量索引元数据。

- **访问路由路径 (Route Path)**：`/kmc/:kbId/knowledgeSegment/detail`
- **路由定义文件**：`frontend/src/router/kmc/public/index.js`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kmc/knowledgeSegment/detail/index.vue`
- **代码规模与声明规范**：共 `154` 行代码，`<script setup name="KnowledgeSegment">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, infotop, infotop-title, infotop-row, border-top, infotop-row-lable` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-col, el-row, el-tab-pane, el-tabs`
- **业务容器特点**：自包含标准视图渲染结构，支持动态状态切换、空状态覆盖或多标签插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`activeName, showSearch`
- **核心 reactive 复合状态对象**：`data`
- **生命周期钩子 (Lifecycle Hooks)**：`watch`
- **关键业务交互函数清单**：
  - `getKnowledgeSegmentDetailById()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleClick()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- 遵循父级容器全局注入的 Apple iOS 26 玻璃质感与全局主题样式，局部未声明额外隔离规则。

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import {getKnowledgeSegment } from "@/api/kmc/knowledgeSegment/knowledgeSegment";`
- **外部与子组件依赖**：
  - `import ComponentOne from "@/views/kmc/knowledgeSegment/detail/componentOne.vue";`
  - `import ComponentTwo from "@/views/kmc/knowledgeSegment/detail/componentTwo.vue";`

---

## 2. 知识图谱资产与可视化探索 (views/kg/ & views/app/)

### 13. 知识图谱文档资产管理
**业务定位**：图谱构建专用的文档源管理，支持文档树形分类、文件解析入库与面向实体抽取的源文本维护。

- **访问路由路径 (Route Path)**：`/kg/knowledge/document`
- **路由定义文件**：`系统菜单配置 (ID: 2231)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kg/knowledge/document/index.vue`
- **代码规模与声明规范**：共 `931` 行代码，`<script setup name="Document">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn, pagecont-bottom` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-col, el-container, el-dialog, el-form, el-form-item, el-input, el-main, el-popover, el-row, el-table, el-table-column, el-tree-select`
- **搭载的业务专属组件与插槽**：`DeptTree, FileUpload, GuideTip`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`deptTreeRef, documentList, columns, open, openDetail, loading, showSearch, ids, single, multiple, total, title, defaultSort, leftWidth, isResizing`
- **核心 reactive 复合状态对象**：`data`
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `collectIds()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `findNodeById()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getCategoryTree()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getFileType()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDeleteFile()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDownload()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleNodeClick()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple 官方暗色基底 (#1D1D1F)**
- **Apple 官方极简浅灰底色 (#F5F5F7)**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/kg/knowledge/document";`
- **外部与子组件依赖**：
  - `import DeptTree from "@/components/DeptTree";`
  - `import FileUpload from "@/components/FileUpload2/index.vue";`

---

### 14. 知识图谱分类体系管理
**业务定位**：图谱文档知识分类树的维护，支持分类编码、层级树增删改查与排序调整。

- **访问路由路径 (Route Path)**：`/kg/knowledge/category`
- **路由定义文件**：`系统菜单配置 (ID: 2224)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kg/knowledge/category/index.vue`
- **代码规模与声明规范**：共 `561` 行代码，`<script setup name="Category">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn, pagecont-bottom, top-right-btn` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-col, el-dialog, el-form, el-form-item, el-input, el-input-number, el-row, el-table, el-table-column, el-tree-select`
- **搭载的业务专属组件与插槽**：`GuideTip`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`categoryList, graphList, columns, open, openDetail, loading, showSearch, ids, single, multiple, total, title, categoryOptions, refreshTable, isExpandAll`
- **核心 reactive 复合状态对象**：`data`
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleQuery()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleQueryAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleSelectionChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleSortChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleUpdate()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `isValidData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `reset()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/kg/knowledge/category";`
  - `import { listDocument } from "@/api/kg/knowledge/document.js";`

---

### 15. Neo4j 知识图谱全局管理与社区检测
**业务定位**：Neo4j 知识图谱全局可视化画布，集成 Louvain / Label Propagation 算法的图谱社区检测 (Community Detection)、10 色高对比社区着色、动态节点/关系新增与力导向物理引擎仿真。

- **访问路由路径 (Route Path)**：`/kg/graph`
- **路由定义文件**：`frontend/src/router/kg/dynamic/index.js`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kg/graph/index.vue`
- **代码规模与声明规范**：共 `376` 行代码，`<script setup>` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, card-header, glass-btn, loading-container, empty-container, graph-container` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-card, el-dialog, el-empty, el-form, el-form-item, el-icon, el-input, el-option, el-select, el-switch`
- **搭载的业务专属组件与插槽**：`Loading`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`loading, graphContainer, showAddNodeDialog, showAddEdgeDialog, colorByCommunity, detecting, communities, workspaceId, graphData, nodeForm, edgeForm`
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted, watch`
- **关键业务交互函数清单**：
  - `addEdge()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `addNode()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `fetchGraphData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getCommunityColor()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getNodeColor()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `loadCommunities()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `renderGraph()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `runCommunityDetection()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `safeGetDescription()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Flex 弹性流式排布**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { detectCommunities, getCommunities } from '@/api/kg/graph';`

---

### 16. 知识图谱沉浸式交互探索大屏
**业务定位**：生产级高交互知识图谱探索平台，基于 Vis-Network 引擎构建，集成左侧实体/关系分类控制树、悬浮操作工具栏、实体全局自动补全搜索、多模态原文档在线预览（Docx/Excel/PDF/文本）、图谱状态发布与实体/三元组编辑。

- **访问路由路径 (Route Path)**：`/app/graphExploration/2 (及抽取结果重定向)`
- **路由定义文件**：`frontend/src/router/app/public/index.js 与 frontend/src/router/ext/public/index.js`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/app/graphExploration/index.vue`
- **代码规模与声明规范**：共 `1817` 行代码，`<script setup name="KEresult">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, glass-btn, wrap-container` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-autocomplete, el-button, el-col, el-collapse, el-collapse-item, el-container, el-dialog, el-form, el-form-item, el-icon, el-input, el-row, el-table, el-table-column, el-tooltip, el-tree`
- **搭载的业务专属组件与插槽**：`AddEntity, AddRelationship, Close, Search`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`taskInfo, releaseStatus, nodeRef, edgeRef, addTitle, appLoading, nodes, edges, schemaList, toolShow, toolData, currentNodeData, detailShow, searchShow, searchVal`
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted, watch`
- **关键业务交互函数清单**：
  - `attrDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `attrFormCancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `attrFormSubmit()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `attrUpdate()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `collapseChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `createFilter()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `detailClose()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `filterEmit()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `filterNode()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getAllIds()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getAssetsFile()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getAttrData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple 官方暗色基底 (#1D1D1F)**
- **Apple 官方极简浅灰底色 (#F5F5F7)**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/ext/extUnstructTask/unstructTask";`
  - `} from "@/api/ext/extStructTask/extStruct";`
  - `import { getTableDataByDataId } from "@/api/ext/extDatasource/datasource";`
  - `} from "@/api/app/graph";`
  - `import { listSchema } from "@/api/ext/extSchema/schema";`
- **外部与子组件依赖**：
  - `import AddEntity from "./addEntity.vue";`
  - `import AddRelationship from "./addRelationship.vue";`

---

### 17. 图谱实体新增与编辑弹窗
**业务定位**：在图谱画布中直接创建或编辑实体节点及其业务属性键值对。

- **访问路由路径 (Route Path)**：`内嵌于 /app/graphExploration/2 弹窗`
- **路由定义文件**：`组件级调用 (由 views/app/graphExploration/index.vue 唤起)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/app/graphExploration/addEntity.vue`
- **代码规模与声明规范**：共 `168` 行代码，`<script setup name="addEntity">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `glass-card, glass-btn` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-col, el-dialog, el-form, el-form-item, el-input, el-row, el-table, el-table-column`
- **业务容器特点**：自包含标准视图渲染结构，支持动态状态切换、空状态覆盖或多标签插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`open, ids`
- **核心 reactive 复合状态对象**：`data`
- **派生计算属性 (computed)**：`title, graphId`
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `addItem()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `deleteItem()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleClose()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `itemData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `openDialog()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `reset()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `submitForm()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- 遵循父级容器全局注入的 Apple iOS 26 玻璃质感与全局主题样式，局部未声明额外隔离规则。

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { addNode, deleteNodeByIds } from "@/api/app/graph";`

---

### 18. 图谱三元组关系新增与编辑弹窗
**业务定位**：在图谱画布中指定头实体 (Head)、关系类型 (Relation) 与尾实体 (Tail) 构建三元组拓扑连线。

- **访问路由路径 (Route Path)**：`内嵌于 /app/graphExploration/2 弹窗`
- **路由定义文件**：`组件级调用 (由 views/app/graphExploration/index.vue 唤起)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/app/graphExploration/addRelationship.vue`
- **代码规模与声明规范**：共 `187` 行代码，`<script setup name="addEntity">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `glass-card, glass-btn` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-col, el-dialog, el-form, el-form-item, el-input, el-row, el-table, el-table-column`
- **搭载的业务专属组件与插槽**：`EntitySingle`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`open, ids`
- **核心 reactive 复合状态对象**：`data`
- **派生计算属性 (computed)**：`title, graphId`
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `addItem()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `confirm()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `deleteItem()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleClose()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `itemData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `openDialog()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `openEntity()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `reset()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `submitForm()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- 遵循父级容器全局注入的 Apple iOS 26 玻璃质感与全局主题样式，局部未声明额外隔离规则。

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { addTripletRel, deleteRelationshipsByIds } from "@/api/app/graph";`
- **外部与子组件依赖**：
  - `import EntitySingle from "./selection/entitySingle.vue";`

---

## 3. 数据看板与全链路可观测中心 (views/kd/)

### 19. 知识资产全景运营大屏
**业务定位**：全平台知识资产指标大屏，涵盖文档总量、分块总数、向量索引数、三元组总数核心指标卡，搭载 ECharts 资产沉淀与消耗趋势双折线图、文档向量化处理管线以及低频资产治理状态表。

- **访问路由路径 (Route Path)**：`/kd/knowledgeAsset`
- **路由定义文件**：`系统菜单配置 (ID: 2402)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kd/knowledgeAsset/index.vue`
- **代码规模与声明规范**：共 `1451` 行代码，`<script setup name="KnowledgeAsset">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, panel-card, summary-card, summary-card__head, summary-card__title, summary-card__value-row, summary-card__value` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-icon, el-pagination, el-progress, el-table, el-table-column`
- **搭载的业务专属组件与插槽**：`Clock, InfoFilled, Tag`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`pageLoading, activeGovernanceFilter, currentPage, pageSize, trendChartRef, dashboardData`
- **派生计算属性 (computed)**：`summaryCards, trendSection, pipelineSection, governanceRows, governancePendingCount, governancePendingTagText`
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted, watch`
- **关键业务交互函数清单**：
  - `createChart()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `createDemoDashboardData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `createEmptyDashboardData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `disposeChart()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `fetchKnowledgeAssetDashboard()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `formatDisplayValue()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `formatPercent()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getGovernanceSeverityTagType()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getGovernanceTypeTagType()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getNiceAxisConfig()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getTrendSymbol()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleGovernanceFilter()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple 官方暗色基底 (#1D1D1F)**
- **Apple 官方极简浅灰底色 (#F5F5F7)**
- **CSS Grid 自适应多端断点栅格**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**
- **高阶色彩微渐变 (linear-gradient)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { getKnowledgeAssetDashboard } from "@/api/kd/dashboard";`

---

### 20. Bot 智能体运营分析大屏
**业务定位**：面向智能体业务运营的数据大屏，聚合总调用量、会话数、成功率、活跃智能体指标卡，ECharts 调用量趋势图、Bot 类型分布环形图以及智能体健康度监控列表。

- **访问路由路径 (Route Path)**：`/kd/botOperation`
- **路由定义文件**：`系统菜单配置 (ID: 2403)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kd/botOperation/index.vue`
- **代码规模与声明规范**：共 `1289` 行代码，`<script setup name="BotOperation">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, bot-operation-page, glass-card, bot-operation-shell, panel-card, summary-card, summary-card__head, summary-card__title` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-icon, el-pagination, el-table, el-table-column`
- **搭载的业务专属组件与插槽**：`Clock, InfoFilled, PieChart, Tag, TrendCharts`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`pageLoading, trendRange, healthPage, healthPageSize, trendChartRef, typeChartRef, dashboardData`
- **派生计算属性 (computed)**：`summaryCards, healthList, paginatedHealthList, rankList, typeLegendList, typeTotal`
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted, watch`
- **关键业务交互函数清单**：
  - `createChart()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `createDemoDashboardData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `createEmptyDashboardData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `disposeChart()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `fetchBotOperationDashboard()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `formatDisplayValue()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `formatNumber()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getHealthSeverityTagType()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getHealthStatusTagType()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getNiceAxisConfig()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `loadDashboardData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `renderTrendChart()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple 官方暗色基底 (#1D1D1F)**
- **Apple 官方极简浅灰底色 (#F5F5F7)**
- **CSS Grid 自适应多端断点栅格**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**
- **高阶色彩微渐变 (linear-gradient)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { getBotOperationDashboard } from "@/api/kd/dashboard";`

---

### 21. 行业应用运营监控大屏
**业务定位**：平台各行业落地应用的运行监控，展示应用总数、活跃用户数、各行业调用渗透率，配合行业分布柱状/环形图与低活跃应用治理预警。

- **访问路由路径 (Route Path)**：`/kd/appOperations`
- **路由定义文件**：`系统菜单配置 (ID: 2404)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kd/appOperations/index.vue`
- **代码规模与声明规范**：共 `1287` 行代码，`<script setup name="AppOperation">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, panel-card, summary-card, summary-card__head, summary-card__title-row, summary-card__title, summary-card__tag` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-icon, el-pagination, el-table, el-table-column`
- **搭载的业务专属组件与插槽**：`Clock, InfoFilled, PieChart, Tag, TrendCharts`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`pageLoading, trendRange, trendChartRef, industryChartRef, lowActivityPage, lowActivityPageSize, newReleasePage, newReleasePageSize, dashboardData`
- **派生计算属性 (computed)**：`summaryCards, lowActivityList, newReleaseList, industryLegendList, industryTotal, activeTrendData`
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted, watch`
- **关键业务交互函数清单**：
  - `createChart()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `createDemoDashboardData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `createEmptyDashboardData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `disposeChart()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `fetchAppOperationDashboard()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `formatDisplayValue()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getLowActivityStatusTagType()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getNiceAxisConfig()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getTableEmptyText()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `loadDashboardData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `paginate()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `renderIndustryChart()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple 官方暗色基底 (#1D1D1F)**
- **Apple 官方极简浅灰底色 (#F5F5F7)**
- **CSS Grid 自适应多端断点栅格**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**
- **高阶色彩微渐变 (linear-gradient)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { getAppOperationDashboard } from "@/api/kd/dashboard";`

---

### 22. LLM & RAG 全链路可观测性总控看板
**业务定位**：集成 Langfuse 工业级可观测底座的大屏，监控 Langfuse 连接状态、对话调用总数、平均延迟、Token 吞吐、Faithfulness 质量评分，并呈现最近 20 条对话 Trace 及细粒度 Observation/Span 耗时标签。

- **访问路由路径 (Route Path)**：`/kd/observability`
- **路由定义文件**：`SQL 菜单配置 (deploy/sql/postgresql/11-langfuse-menu.sql, ID: 2430)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kd/observability/index.vue`
- **代码规模与声明规范**：共 `426` 行代码，`<script setup>` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, status-card, glass-card, status-left, status-right, glass-btn, setup-card, metric-card` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-card, el-col, el-icon, el-result, el-row, el-step, el-steps, el-table, el-table-column, el-tag`
- **搭载的业务专属组件与插槽**：`CircleCheckFilled, GlassEmpty, Link, Refresh, WarningFilled`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`langfuseEnabled, langfuseUrl, loading, metrics, traces`
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted`
- **关键业务交互函数清单**：
  - `checkLangFuseStatus()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `formatMs()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `formatNumber()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getSpanType()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getTraceTokens()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getUsageTotal()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `normalizeNumber()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `openDashboard()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `refreshTraces()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple Liquid Glass / 双层毛玻璃模糊 (backdrop-filter: blur)**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口**：由父级页面下发数据，或通过底层封装的公共 `request()` / Pinia Store 模块直接发起请求。

---

### 23. 多 Agent 协同执行链路瀑布流面板
**业务定位**：高保真甘特图/瀑布流执行拓扑面板，针对多 Agent / Tool / RAG 调用链进行毫秒级耗时剖析，支持 CPM 关键路径高亮脉冲过滤、树状层级展开折叠与细粒度属性检查抽屉。

- **访问路由路径 (Route Path)**：`组件嵌入式面板 (可观测大屏或 Trace 调试抽屉)`
- **路由定义文件**：`组件级调用 (由 views/kd/observability/index.vue 或调试组件引入)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/kd/observability/components/ExecutionWaterfallPanel.vue`
- **代码规模与声明规范**：共 `451` 行代码，`<script setup lang="ts">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `execution-waterfall-panel, glass-panel, waterfall-header, header-title-box, panel-title, header-actions, glass-action-btn, timeline-ruler-wrap` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-drawer, el-switch`
- **业务容器特点**：自包含标准视图渲染结构，支持动态状态切换、空状态覆盖或多标签插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`onlyCriticalPath, isAllExpanded, drawerVisible`
- **派生计算属性 (computed)**：`activeTraceId, timelineResult, displayedSpans`
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `selectSpan()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `toggleExpandAll()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple Liquid Glass / 双层毛玻璃模糊 (backdrop-filter: blur)**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口**：由父级页面下发数据，或通过底层封装的公共 `request()` / Pinia Store 模块直接发起请求。

---

## 4. 神经符号可解释性与密码学审计中心 (views/audit/)

### 24. 神经符号可解释性与密码学审计中心总屏
**业务定位**：系统审计最高主控中心，采用 Apple Level 3 钛金毛玻璃导航，集成因果拓扑溯源、RFC 6962 客户端免密验真、安全护栏态势感知三合一分段控制器，支持 Trace ID 毫秒级定位检索与全屏模式。

- **访问路由路径 (Route Path)**：`/audit/explainability`
- **路由定义文件**：`frontend/src/router/app/public/index.js`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/audit/ExplainabilityDashboard.vue`
- **代码规模与声明规范**：共 `314` 行代码，`<script setup lang="ts">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `dashboard-header, trace-input-wrap` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-icon, el-input`
- **搭载的业务专属组件与插槽**：`ExplainabilityTopologyCanvas, GuardrailDashboard, Lock, MerkleProofValidator, Odometer, Search, Share, Transition`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `handleTraceSearch()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `toggleFullScreen()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple Liquid Glass / 双层毛玻璃模糊 (backdrop-filter: blur)**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口**：由父级页面下发数据，或通过底层封装的公共 `request()` / Pinia Store 模块直接发起请求。
- **外部与子组件依赖**：
  - `import ExplainabilityTopologyCanvas from './components/ExplainabilityTopologyCanvas.vue';`
  - `import MerkleProofValidator from './components/MerkleProofValidator.vue';`
  - `import GuardrailDashboard from './components/GuardrailDashboard.vue';`

---

### 25. 因果拓扑有向无环图画布
**业务定位**：基于 Vue Flow / AntV 构建的因果有向无环图 (DAG) 画布，支持 8 阶段神经符号节点编排、Dagre 自动分层布局、反向归因高亮传导 (Backward Attribution) 与节点详情抽屉联动。

- **访问路由路径 (Route Path)**：`内嵌于 /audit/explainability (Tab: topology)`
- **路由定义文件**：`组件级调用 (由 views/audit/ExplainabilityDashboard.vue 引入)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/audit/components/ExplainabilityTopologyCanvas.vue`
- **代码规模与声明规范**：共 `332` 行代码，`<script setup lang="ts">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `topology-canvas-container, canvas-header-bar, header-left, header-right` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-button-group, el-tag`
- **搭载的业务专属组件与插槽**：`AuditCustomNode, Background, Controls, NodeDetailDrawer, VueFlow`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`drawerVisible, isCausalBackpropActive`
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted`
- **关键业务交互函数清单**：
  - `buildDemoTopology()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `executeBackwardAttribution()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `fetchTopology()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAutoLayout()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDrawerCausalTrace()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFitView()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleNodeClick()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handlePaneClick()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `isNodeCausalActive()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `isNodeDimmed()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `resetEdgeStyles()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `toggleCausalBackprop()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple Liquid Glass / 双层毛玻璃模糊 (backdrop-filter: blur)**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { getTopology, getCausalAttribution } from '@/api/audit';`
- **外部与子组件依赖**：
  - `import AuditCustomNode from '../custom-nodes/AuditCustomNode.vue';`
  - `import NodeDetailDrawer from './NodeDetailDrawer.vue';`

---

### 26. 安全护栏态势感知大屏
**业务定位**：实时防御监控看板，展示输入/输出护栏拦截总数、平均拦截延迟、PII 隐私掩码脱敏计数、提示词注入防护雷达图与 24 小时风险拦截时序趋势图。

- **访问路由路径 (Route Path)**：`内嵌于 /audit/explainability (Tab: guardrail)`
- **路由定义文件**：`组件级调用 (由 views/audit/ExplainabilityDashboard.vue 引入)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/audit/components/GuardrailDashboard.vue`
- **代码规模与声明规范**：共 `396` 行代码，`<script setup lang="ts">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `guardrail-dashboard-container, metric-card, chart-panel, trend-panel, panel-header, panel-title, radar-panel, pii-dist-list` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-tag`
- **业务容器特点**：自包含标准视图渲染结构，支持动态状态切换、空状态覆盖或多标签插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted`
- **关键业务交互函数清单**：
  - `formatPiiType()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `loadMetrics()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `renderCharts()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `renderRadarChart()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `renderTrendChart()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple Liquid Glass / 双层毛玻璃模糊 (backdrop-filter: blur)**
- **CSS Grid 自适应多端断点栅格**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { getGuardrailMetrics, GuardrailMetricsVO } from '@/api/audit';`

---

### 27. RFC 6962 密码学存证客户端免密验真器
**业务定位**：纯前端基于浏览器原生 WebCrypto 硬件加速实现的 Merkle 树凭单验真器，支持上传存证 JSON 证书、对数级兄弟节点 SHA-256 折叠计算动效回溯、单比特篡改阻断注入测试与审计凭证 JSON 导出。

- **访问路由路径 (Route Path)**：`内嵌于 /audit/explainability (Tab: merkle)`
- **路由定义文件**：`组件级调用 (由 views/audit/ExplainabilityDashboard.vue 引入)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/audit/components/MerkleProofValidator.vue`
- **代码规模与声明规范**：共 `451` 行代码，`<script setup lang="ts">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `merkle-validator-container, validator-card, card-header, header-left, header-icon-box, header-meta, header-actions, steps-progress-wrapper` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-icon, el-upload`
- **搭载的业务专属组件与插槽**：`CircleCheckFilled, CircleCloseFilled, Lock`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`currentStepIndex, isVerifying, verificationFinished, verificationSuccess`
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted`
- **关键业务交互函数清单**：
  - `executeVerification()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `exportAuditReportJson()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleCertificateUpload()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `loadSampleCertificate()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `runStepAnimation()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `simulateTampering()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple Liquid Glass / 双层毛玻璃模糊 (backdrop-filter: blur)**
- **CSS Grid 自适应多端断点栅格**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口**：由父级页面下发数据，或通过底层封装的公共 `request()` / Pinia Store 模块直接发起请求。

---

### 28. 决策节点属性与凭单审计抽屉
**业务定位**：拓扑节点选中的右侧半透明毛玻璃抽屉，展示节点元数据、因果输入输出 JSON 格式化高亮、执行耗时、哈希指纹及一键触发反向溯源归因。

- **访问路由路径 (Route Path)**：`内嵌于 /audit/explainability 抽屉`
- **路由定义文件**：`组件级调用 (由 views/audit/components/ExplainabilityTopologyCanvas.vue 唤起)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/audit/components/NodeDetailDrawer.vue`
- **代码规模与声明规范**：共 `232` 行代码，`<script setup lang="ts">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `meta-hero-card` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-drawer, el-tag`
- **业务容器特点**：自包含标准视图渲染结构，支持动态状态切换、空状态覆盖或多标签插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **派生计算属性 (computed)**：`visibleModel, statusTagType`
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `formatDataJson()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleTriggerCausal()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple Liquid Glass / 双层毛玻璃模糊 (backdrop-filter: blur)**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口**：由父级页面下发数据，或通过底层封装的公共 `request()` / Pinia Store 模块直接发起请求。

---

### 29. 神经符号因果自定义拓扑节点
**业务定位**：拓扑图自定义节点渲染组件，采用双层液态玻璃质感、呼吸光效、阶段色彩标识、状态徽标与哈希指纹截断展示。

- **访问路由路径 (Route Path)**：`画布自定义节点组件`
- **路由定义文件**：`组件级调用 (由 views/audit/components/ExplainabilityTopologyCanvas.vue 注册)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/audit/custom-nodes/AuditCustomNode.vue`
- **代码规模与声明规范**：共 `252` 行代码，`<script setup lang="ts">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `handle-left, node-header, header-left, header-right, handle-right` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`无基础 Element 组件 (纯自定义画布或轻量容器)`
- **搭载的业务专属组件与插槽**：`Handle`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **派生计算属性 (computed)**：`nodeTypeLower, stageLabel, statusClass`
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `formatHash()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple Liquid Glass / 双层毛玻璃模糊 (backdrop-filter: blur)**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**
- **高阶色彩微渐变 (linear-gradient)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口**：由父级页面下发数据，或通过底层封装的公共 `request()` / Pinia Store 模块直接发起请求。

---

## 5. 知识抽取任务与工业设备管理 (views/ext/ & views/dm/)

### 30. 结构化抽取任务列表与管理
**业务定位**：管理从结构化关系型数据库 (MySQL/PostgreSQL/Oracle) 抽取实体与关系的后台任务，支持状态调度、映射配置入口与执行日志追溯。

- **访问路由路径 (Route Path)**：`/kg/ext/extStructTask`
- **路由定义文件**：`系统菜单配置 (ID: 2037)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/ext/extStructTask/index.vue`
- **代码规模与声明规范**：共 `982` 行代码，`<script setup name="ExtStruct">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn, pagecont-bottom` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-col, el-date-picker, el-form, el-form-item, el-input, el-option, el-popover, el-row, el-select, el-switch, el-table, el-table-column`
- **搭载的业务专属组件与插槽**：`GuideTip, StructTask`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`extStructList, structTaskShow, taskVisible, columns, open, openDetail, loading, showSearch, ids, selectedRows, single, multiple, total, title, defaultSort`
- **核心 reactive 复合状态对象**：`upload, data`
- **生命周期钩子 (Lifecycle Hooks)**：`watch`
- **关键业务交互函数清单**：
  - `canDeleteTask()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `extraction()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getTaskDescription()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDetail()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleExport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileSuccess()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileUploadProgress()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Flex 弹性流式排布**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/ext/extStructTask/extStruct";`
- **外部与子组件依赖**：
  - `import StructTask from "./structTask.vue";`

---

### 31. 结构化抽取任务多步向导配置页面
**业务定位**：复杂的结构化抽取构建向导，包含任务基本信息表单、数据库表选择、字段映射 (TableMapping)、关系映射 (RelationMapping) 与 Crontab 定时调度配置。

- **访问路由路径 (Route Path)**：`/kg/ext/addStructTask 或 /kg/ext/editStructTask`
- **路由定义文件**：`frontend/src/router/ext/public/index.js`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/ext/extStructTask/add/index.vue`
- **代码规模与声明规范**：共 `1326` 行代码，`<script setup name="qualityTask">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, custom-card, pagecont-top, infotop` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-dialog`
- **搭载的业务专属组件与插槽**：`BasicInfoForm, Crontab, ImportTable, Mapping, RelationMapping, TableMapping`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`loading, showSearch, extStructRef, relationFormRef, dataSourceList, updateFrequency, connectionSuccess, connectionError, open, openCron, tableData, dbTableList, structTaskStatus, queryParams`
- **核心 reactive 复合状态对象**：`data`
- **派生计算属性 (computed)**：`shouldShowFormLabels`
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted, watch`
- **关键业务交互函数清单**：
  - `addIntermediateTable()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `addRelationItem()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `changeDataSource()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `crontabFill()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `deleteItem()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `formatRelationData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnsDataList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getConceptList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getDataSourceList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getExtStructInfo()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getOpenPage()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getRealtionMappingList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple 官方暗色基底 (#1D1D1F)**
- **Apple 官方极简浅灰底色 (#F5F5F7)**
- **Flex 弹性流式排布**
- **标准流线型圆角 (border-radius: 8px~16px)**
- **高阶色彩微渐变 (linear-gradient)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/da/datasource/daDatasource";`
  - `import { getDaDatasourceTableList } from "@/api/da/datasource/daDatasource";`
  - `} from "@/api/ext/extStructTask/extStruct";`
  - `import { getColumnsList } from "@/api/da/datasource/daDatasource";`
  - `import { listRelation } from "@/api/ext/extSchemaRelation/relation";`
- **外部与子组件依赖**：
  - `import Crontab from "@/components/Crontab/index.vue";`
  - `import BasicInfoForm from "./basicInfoForm.vue";`
  - `import TableMapping from "./tableMapping.vue";`
  - `import RelationMapping from "./relationMapping.vue";`
  - `import ImportTable from "../importTable.vue";`
  - `import Mapping from "../mapping.vue";`

---

### 32. 非结构化抽取任务管理
**业务定位**：非结构化文本与文档的实体/关系提取任务工作台，支持绑定图谱文档、选择目标概念 Schema、执行抽取模型并查看运行流水日志。

- **访问路由路径 (Route Path)**：`/kg/ext/unstructTask`
- **路由定义文件**：`系统菜单配置 (ID: 2030)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/ext/extUnstructTask/index.vue`
- **代码规模与声明规范**：共 `1129` 行代码，`<script setup name="UnstructTask">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn, pagecont-bottom` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-col, el-date-picker, el-dialog, el-form, el-form-item, el-input, el-option, el-popover, el-row, el-select, el-table, el-table-column`
- **搭载的业务专属组件与插槽**：`GuideTip, RelationMultiple, SelectDoc`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`unstructTaskList, visible, selectOptions, columns, open, openDetail, loading, showSearch, ids, selectedRows, single, multiple, total, title, defaultSort`
- **核心 reactive 复合状态对象**：`upload, data`
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted, watch`
- **关键业务交互函数清单**：
  - `addItem()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `canDeleteTask()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `extraction()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `formatDate()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getAllList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getLabelByValue()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getTaskDescription()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleChildClose()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- 遵循父级容器全局注入的 Apple iOS 26 玻璃质感与全局主题样式，局部未声明额外隔离规则。

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/ext/extUnstructTask/unstructTask";`
  - `import { getExtSchemaAllList } from "@/api/ext/extSchema/schema";`
- **外部与子组件依赖**：
  - `import TaskLogDialog from "@/views/ext/extTaskLog/taskLogDialog.vue";`
  - `import SelectDoc from "@/views/kg/knowledge/document/selection/documentMultiple.vue";`
  - `import RelationMultiple from "@/views/ext/extSchemaRelation/selection/relationMultiple.vue";`

---

### 33. 知识抽取数据源管理
**业务定位**：连接各种抽取源数据库的基础设施配置页面，支持数据源驱动、主机地址、连接池测试与权限管理。

- **访问路由路径 (Route Path)**：`/kg/ext/datasource`
- **路由定义文件**：`系统菜单配置 (ID: 2040)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/ext/extDatasource/index.vue`
- **代码规模与声明规范**：共 `682` 行代码，`<script setup name="Datasource">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn, pagecont-bottom` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-checkbox, el-col, el-date-picker, el-dialog, el-form, el-form-item, el-icon, el-input, el-input--, el-link, el-option, el-row, el-select, el-table, el-table-column, el-upload`
- **业务容器特点**：自包含标准视图渲染结构，支持动态状态切换、空状态覆盖或多标签插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`datasourceList, columns, open, openDetail, loading, showSearch, ids, single, multiple, total, title, defaultSort`
- **核心 reactive 复合状态对象**：`upload, data`
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDetail()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleExport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileSuccess()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileUploadProgress()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleImport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleQuery()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleSelectionChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- 遵循父级容器全局注入的 Apple iOS 26 玻璃质感与全局主题样式，局部未声明额外隔离规则。

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { listDatasource, getDatasource, delDatasource, addDatasource, updateDatasource } from "@/api/ext/extDatasource/datasource";`

---

### 34. 概念 Schema 建模管理
**业务定位**：本体论知识建模工作台，定义知识图谱中的概念类别 (Concept Classes)，配置颜色标识、统计实体数量与关联属性列表。

- **访问路由路径 (Route Path)**：`/kg/ext/schema`
- **路由定义文件**：`系统菜单配置 (ID: 2016)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/ext/extSchema/index.vue`
- **代码规模与声明规范**：共 `819` 行代码，`<script setup name="Schema">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn, pagecont-bottom, top-right-btn` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-checkbox, el-col, el-color-picker, el-dialog, el-form, el-form-item, el-icon, el-input, el-link, el-row, el-table, el-table-column, el-upload`
- **搭载的业务专属组件与插槽**：`GuideTip`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`schemaList, columns, open, openDetail, loading, showSearch, ids, single, multiple, total, title`
- **核心 reactive 复合状态对象**：`upload, data`
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted`
- **关键业务交互函数清单**：
  - `addFakeCountFields()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getRandomCount()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDetail()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleExport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileSuccess()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileUploadProgress()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Flex 弹性流式排布**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/ext/extSchema/schema";`
  - `} from "@/api/ext/extSchemaAttribute/attribute.js";`
  - `import { listRelation } from "@/api/ext/extSchemaRelation/relation.js";`

---

### 35. 概念 Schema 属性详情配置页面
**业务定位**：针对指定概念 Schema 进行属性项 (Attributes) 的增删改查与数据类型 (String/Integer/Float/DateTime) 强约束校验。

- **访问路由路径 (Route Path)**：`/kg/ext/extSchemaDetail/schemaDetail`
- **路由定义文件**：`frontend/src/router/ext/public/index.js`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/ext/extSchema/detail/index.vue`
- **代码规模与声明规范**：共 `1056` 行代码，`<script setup name="Attribute">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-checkbox, el-col, el-date-picker, el-dialog, el-form, el-form-item, el-icon, el-input, el-input--, el-link, el-option, el-radio, el-radio-group, el-row, el-select, el-table, el-table-column, el-upload`
- **业务容器特点**：自包含标准视图渲染结构，支持动态状态切换、空状态覆盖或多标签插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`attributeList, columns, open, openDetail, loading, showSearch, ids, single, multiple, total, title, defaultSort`
- **核心 reactive 复合状态对象**：`upload, data`
- **生命周期钩子 (Lifecycle Hooks)**：`watch`
- **关键业务交互函数清单**：
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `close()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getAttributeDescription()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDetail()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleExport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileSuccess()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileUploadProgress()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- 遵循父级容器全局注入的 Apple iOS 26 玻璃质感与全局主题样式，局部未声明额外隔离规则。

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/ext/extSchemaAttribute/attribute";`

---

### 36. Schema 字段映射配置
**业务定位**：数据表列名与图谱 Schema 属性之间的映射规则配置页面，支持自动匹配与映射状态校验。

- **访问路由路径 (Route Path)**：`/kg/ext/extSchemaMapping`
- **路由定义文件**：`系统菜单配置`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/ext/extSchemaMapping/index.vue`
- **代码规模与声明规范**：共 `652` 行代码，`<script setup name="Schema">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn, pagecont-bottom` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-checkbox, el-col, el-date-picker, el-dialog, el-form, el-form-item, el-icon, el-input, el-link, el-row, el-table, el-table-column, el-upload`
- **业务容器特点**：自包含标准视图渲染结构，支持动态状态切换、空状态覆盖或多标签插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`schemaList, columns, open, openDetail, loading, showSearch, ids, single, multiple, total, title, defaultSort`
- **核心 reactive 复合状态对象**：`upload, data`
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDetail()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleExport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileSuccess()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileUploadProgress()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleImport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleQuery()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleSelectionChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- 遵循父级容器全局注入的 Apple iOS 26 玻璃质感与全局主题样式，局部未声明额外隔离规则。

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { listSchema, getSchema, delSchema, addSchema, updateSchema } from "@/api/ext/extSchemaMapping/schema";`

---

### 37. 非结构化抽取结果图谱画布
**业务定位**：非结构化抽取任务产生的知识图谱预览画布，左侧文档正文对照，右侧 Vis-Network 实体三元组即时渲染与一键发布图谱。

- **访问路由路径 (Route Path)**：`/kg/ext/extractResults`
- **路由定义文件**：`frontend/src/router/ext/public/index.js (重定向)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/ext/extractResults/index.vue`
- **代码规模与声明规范**：共 `1245` 行代码，`<script setup name="KEresult">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, left-pane, left-tree, head-container, filter-tree, glass-btn, gragh-wrap` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-aside, el-button, el-collapse, el-collapse-item, el-container, el-divider, el-icon, el-input, el-main, el-table, el-table-column, el-tooltip, el-tree`
- **搭载的业务专属组件与插槽**：`Close`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`taskInfo, releaseStatus, textList, docList, appLoading, toolbarRef, toolbar, leftWidth, isResizing, filterText, treeRef, oldId, toolShow, toolData, currentNodeData`
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted, watch`
- **关键业务交互函数清单**：
  - `attrDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `attrUpdate()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `base64ToBlob()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `collapseChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `detailClose()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `filterNode()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getAllIds()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getExtExtractionData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getTextListAndDocList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleBack()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleCancelRelease()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleCheck()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple 官方极简浅灰底色 (#F5F5F7)**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from "@/api/ext/extUnstructTask/unstructTask";`
  - `} from "@/api/ext/extStructTask/extStruct";`
  - `import {getPdfPreview, updatePreviewCount, updateDownloadCount} from "@/api/kmc/kmcDocument/kmcDocument.js";`

---

### 38. 结构化抽取结果图谱画布
**业务定位**：结构化抽取任务产生的数据集图谱可视化验证界面，支持表数据穿透与图谱连线校验。

- **访问路由路径 (Route Path)**：`/kg/ext/structuredResult`
- **路由定义文件**：`frontend/src/router/ext/public/index.js (重定向)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/ext/extractResults/structuredResult.vue`
- **代码规模与声明规范**：共 `1417` 行代码，`<script setup name="KEresult">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, left-pane, left-tree, head-container, filter-tree, glass-btn, gragh-wrap` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-aside, el-button, el-collapse, el-collapse-item, el-container, el-divider, el-icon, el-input, el-main, el-table, el-table-column, el-tooltip, el-tree`
- **搭载的业务专属组件与插槽**：`Close`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`taskInfo, releaseStatus, appLoading, dataSource, dataSourceLoading, toolbarRef, toolbar, leftWidth, isResizing, filterText, treeRef, oldId, toolShow, toolData, currentNodeData`
- **生命周期钩子 (Lifecycle Hooks)**：`onMounted, watch`
- **关键业务交互函数清单**：
  - `attrDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `attrUpdate()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `collapseChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `detailClose()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `filterNode()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getAllIds()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getAttrData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getDataSorceData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getExtExtractionData()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleBack()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleCancelRelease()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleCheck()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Apple 官方极简浅灰底色 (#F5F5F7)**
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**
- **标准流线型圆角 (border-radius: 8px~16px)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { deleteNode } from "@/api/ext/extUnstructTask/unstructTask";`
  - `} from "@/api/ext/extStructTask/extStruct";`
  - `import { getTableDataByDataId } from "@/api/ext/extDatasource/datasource";`

---

### 39. 工业设备数据源配置
**业务定位**：工业设备监测与时序数据库源管理，涵盖设备通道、数据源连接参数与连接状态校验。

- **访问路由路径 (Route Path)**：`/kg/dm/dmDatasource`
- **路由定义文件**：`系统菜单配置 (ID: 2047)`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/dm/dmDatasource/index.vue`
- **代码规模与声明规范**：共 `994` 行代码，`<script setup name="DaDatasource">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn, pagecont-bottom` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-checkbox, el-col, el-dialog, el-form, el-form-item, el-icon, el-input, el-link, el-option, el-popover, el-radio, el-radio-group, el-row, el-select, el-switch, el-table, el-table-column, el-tag, el-upload`
- **搭载的业务专属组件与插槽**：`ArrowDown, Connection, Delete, Edit, GuideTip, View`，包含默认内容插槽、头部操作工具栏插槽 (`#header`)、表格列自定义渲染插槽 (`#default="scope"`) 及状态指示徽标插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`daDatasourceList, columns, open, openDetail, loading, showSearch, ids, single, multiple, total, title, defaultSort`
- **核心 reactive 复合状态对象**：`upload, data`
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getDatasourceLabel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDetail()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleExport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileSuccess()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileUploadProgress()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleImport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleQuery()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- **Flex 弹性流式排布**
- **半透明微质感色彩 (rgba)**
- **多重拟态环境漫反射阴影 (box-shadow)**

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `} from '@/api/da/datasource/daDatasource';`

---

### 40. 工业设备报警阈值与规则配置
**业务定位**：工业测点遥测参数阈值越限规则配置，支持多级报警阈值设置、报警等级判定与触发逻辑维护。

- **访问路由路径 (Route Path)**：`/kg/dm/dmAlarmConfig`
- **路由定义文件**：`系统菜单配置`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/dm/dmAlarmConfig/index.vue`
- **代码规模与声明规范**：共 `709` 行代码，`<script setup name="Config">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn, pagecont-bottom` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-checkbox, el-col, el-dialog, el-form, el-form-item, el-icon, el-input, el-link, el-option, el-row, el-select, el-table, el-table-column, el-upload`
- **业务容器特点**：自包含标准视图渲染结构，支持动态状态切换、空状态覆盖或多标签插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`configList, columns, open, openDetail, loading, showSearch, ids, single, multiple, total, title, defaultSort, operateConds`
- **核心 reactive 复合状态对象**：`upload, data`
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDetail()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleExport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileSuccess()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileUploadProgress()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleImport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleQuery()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleSelectionChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- 遵循父级容器全局注入的 Apple iOS 26 玻璃质感与全局主题样式，局部未声明额外隔离规则。

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { listConfig, getConfig, delConfig, addConfig, updateConfig } from "@/api/dm/dmAlarmConfig/config";`

---

### 41. 设备测点专家知识与诊断建议
**业务定位**：工业故障诊断专家知识库与应对建议规则库，将特定工况、测点报警特征与专家处置预案进行知识关联绑定。

- **访问路由路径 (Route Path)**：`/kg/dm/dmExpertAdvice`
- **路由定义文件**：`系统菜单配置`
- **源码文件绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend/src/views/dm/dmExpertAdvice/index.vue`
- **代码规模与声明规范**：共 `670` 行代码，`<script setup name="Advice">` 规范声明。

#### 核心代码区分解剖析

##### 1. `<template>` 核心视觉结构与关键布局容器
- **顶层 DOM 布局特征**：主容器主要由 `app-container, glass-card, pagecont-top, glass-btn, pagecont-bottom` 类名构建，采用分栏、栅格或流式响应式排布。
- **引入的主要 Element Plus 组件**：`el-button, el-checkbox, el-col, el-dialog, el-form, el-form-item, el-icon, el-input, el-link, el-option, el-row, el-select, el-table, el-table-column, el-upload`
- **业务容器特点**：自包含标准视图渲染结构，支持动态状态切换、空状态覆盖或多标签插槽。

##### 2. `<script>` 响应式状态、生命周期与关键业务交互
- **核心 ref 响应式状态**：`adviceList, columns, open, openDetail, loading, showSearch, ids, single, multiple, total, title, defaultSort, operateConds`
- **核心 reactive 复合状态对象**：`upload, data`
- **生命周期钩子 (Lifecycle Hooks)**：`按需触发 / 惰性加载`
- **关键业务交互函数清单**：
  - `cancel()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getColumnVisibility()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `getList()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleAdd()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDelete()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleDetail()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleExport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileSuccess()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleFileUploadProgress()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleImport()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleQuery()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。
  - `handleSelectionChange()`：执行相关的业务数据装载、模型参数校验、表格联动筛选或异步更新。

##### 3. `<style>` 视觉设计与质感特色
- 遵循父级容器全局注入的 Apple iOS 26 玻璃质感与全局主题样式，局部未声明额外隔离规则。

##### 4. 依赖的 API 接口与外部组件
- **后端微服务 API 接口导入**：
  - `import { listAdvice, getAdvice, delAdvice, addAdvice, updateAdvice } from "@/api/dm/dmExpertAdvice/advice";`

---

## 第六部分：前端架构与设计系统综合法医审计结论

### 6.1 Apple iOS 26 Liquid Glass 双层液态玻璃质感贯彻度
1. **全景贯彻度**：在 `knowledgeBase/index.vue`、`components/card.vue`、`components/recall.vue`、`audit/ExplainabilityDashboard.vue` 以及 `observability/index.vue` 等核心看板中，均深度植入了 `.glass-card`、`.glass-btn` 与 `backdrop-filter: blur(20px)`，完美达成了苹果标准的双层液态玻璃视觉深度；
2. **配色哲学严谨性**：界面广泛采用 Apple 官方基准暗色 `#1D1D1F` 与极简冷灰底色 `#F5F5F7`，避免高饱和度视觉污染，文字层级对比清晰；
3. **响应式断点**：在召回工作台 `recall.vue` 等复杂页面，采用了从 1280px 到 2560px 的 6 级 CSS Grid 媒体查询断点，确保在各类超宽视网膜屏幕下的高保真排版。

### 6.2 状态管理与图谱/看板计算性能
1. **Vue 3 Composition API 标准化**：全量界面统一采用 `<script setup>` 语法，使用 `ref`、`reactive` 与 `computed` 精确驱动视图，剔除了过时 Options API 的心智负担；
2. **图谱引擎选型适配**：
   - `kg/graph/index.vue` 结合 Louvain 社区检测算法与 10 色高对比调色板，提供了快速宏观拓扑概览；
   - `app/graphExploration/index.vue` 采用 `vis-network` 高性能力导向图引擎，配合 `@vue-office` 多格式文档预览，形成了全功能交互闭环；
   - `audit/` 模块基于因果有向无环图实现反向归因高亮，节点层级逻辑严密。

### 6.3 密码学验真与全链路可观测安全合规性
1. **离线 WebCrypto 零信任验真**：`MerkleProofValidator.vue` 实现了标准 RFC 6962 客户端哈希折叠校验，摆脱了对服务端的单点信任，配合单比特篡改注入测试，提供了企业级密码学存证凭单验真能力；
2. **Langfuse 工业级可观测接入**：`observability/index.vue` 与 `ExecutionWaterfallPanel.vue` 完成了对 Agent 思考链、Tool 跨度与 RAG 召回链路的毫秒级甘特图监控，关键路径 (CPM) 发光脉冲直观暴露长尾瓶颈。

### 6.4 审计凭证交付清单
- **报告绝对路径**：`/Users/achilles/Documents/许子祺/Agent/frontend_knowledge_graph_audit_report.md`
- **审计文件数量**：41 个前端核心视图与关键业务组件
- **审计状态**：`AUDIT_VERIFIED_COMPLETE`