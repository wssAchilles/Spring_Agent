# 04-dag-workflow-orchestration 工作流编排 — DAG 画布

> 状态: completed | 开始: 2026-06-20T09:00:00+08:00 | 完成: 2026-06-20T11:00:00+08:00

## 目标清单

- [x] 4.1 DAG 拓扑排序 + 条件网关节点
- [x] 4.2 并行执行引擎 + 工作流运行监控

## 关键决策

### 决策 1: 使用 Kahn 算法进行拓扑排序
- **原因**: 直观易懂，同时可以检测环
- **否决方案**: DFS 后序遍历

### 决策 2: 条件网关支持简单比较表达式
- **原因**: 满足毕业设计需求，避免引入复杂表达式引擎
- **否决方案**: SpEL、JavaScript 脚本引擎

### 决策 3: CompletableFuture 实现并行执行
- **原因**: Java 标准库，无需额外依赖
- **否决方案**: Reactor Flux、手动线程管理

## 修改的文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| service/flow/dag/DagUtils.java | 新建 | DAG 工具类 |
| service/flow/dag/DagExecutor.java | 新建 | 并行执行器 |
| service/flow/bo/ConditionNodeBO.java | 新建 | 条件网关节点 |
| service/flow/factory/NodeFactory.java | 修改 | 新增 CONDITION 分支 |

## 技术笔记

1. **拓扑排序**: Kahn 算法，O(V+E) 时间复杂度，同时检测环
2. **并行分组**: BFS 分层，同层节点可并行执行
3. **条件网关**: 支持 ==, !=, >, <, >=, <= 操作，支持变量替换
4. **并行执行**: CompletableFuture + 固定线程池 (最大 8 线程)

## 下一阶段预研

- [ ] Hermes 内核设计 (反思循环)
- [ ] AI Judge 三维评分系统
- [ ] 控制面与认知面对接
- [ ] 质量分析面板
