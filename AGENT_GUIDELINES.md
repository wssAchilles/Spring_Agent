# Agent 执行规范

本文件是项目内所有 AI Agent 的强制行为准则。任何 Agent 在本项目中执行任务时，必须严格遵守以下规范。

---

## 一、核心原则

### 1.1 先读后做（Read-Before-Act）

每个新阶段启动时，Agent 的**第一个动作**必须是：

```
读取上一阶段的 plans/<上一阶段文件夹>/log.json
```

如果上一阶段不存在 `log.json`，则**禁止开始新阶段**，必须先补全上一阶段日志。

### 1.2 阶段即文件夹（Stage-as-Folder）

每个工作阶段对应 `plans/` 下的一个独立文件夹，严禁将日志散落在项目其他位置。

### 1.3 双格式同步（Dual-Format Sync）

每个阶段结束时必须同时产出：
- `log.json` — 机器可读，供下一阶段 Agent 解析
- `log.md` — 人类可读，供开发者审阅

两个文件内容必须高度同步，`log.json` 是权威数据源。

---

## 二、目录结构规范

```
plans/
├── _schema/                      # JSON Schema 定义（只读）
│   └── log.schema.json
├── _templates/                   # 日志模板（只读）
│   ├── log.json.template
│   └── log.md.template
├── 00-env-setup-and-base-pull/   # 阶段文件夹示例
│   ├── log.json
│   └── log.md
├── 01-rag-knowledge-engine/      # 阶段文件夹示例
│   ├── log.json
│   └── log.md
└── ...
```

### 文件夹命名规则

格式：`<两位序号>-<阶段核心内容的短横线分隔描述>`

示例：
- `00-env-setup-and-base-pull`
- `01-rag-knowledge-engine`
- `02-agent-assembly-workshop`
- `03-tool-calling-integration`
- `04-dag-workflow-orchestration`
- `05-hermes-kernel-and-ai-judge`
- `06-integration-test-and-deploy`

### 阶段文件夹内必须包含

| 文件 | 用途 | 必须 |
|------|------|------|
| `log.json` | 结构化日志（机器读） | ✅ |
| `log.md` | 可读日志（人类读） | ✅ |
| `artifacts/` | 本阶段产出的额外文件（可选） | ❌ |

---

## 三、log.json Schema 定义

```json
{
  "stage_id": "string, 阶段唯一标识，如 '01-rag-knowledge-engine'",
  "stage_name": "string, 阶段中文名称",
  "status": "string, 枚举: 'not_started' | 'in_progress' | 'completed' | 'blocked'",
  "started_at": "string, ISO 8601 格式，如 '2026-06-19T22:00:00+08:00'",
  "completed_at": "string | null, ISO 8601 格式或 null",
  "context_hash": "string, 本阶段启动时上一阶段 log.json 的 SHA-256 前 8 位，用于检测上下文偏差",
  "objectives": [
    {
      "id": "string, 如 '1.1'",
      "description": "string, 任务描述",
      "status": "string, 枚举: 'pending' | 'in_progress' | 'done' | 'skipped'",
      "files_touched": ["string, 修改的文件路径列表"],
      "notes": "string | null, 备注"
    }
  ],
  "key_decisions": [
    {
      "decision": "string, 决策内容",
      "reason": "string, 决策原因",
      "alternatives_rejected": ["string, 被否决的替代方案"]
    }
  ],
  "issues_encountered": [
    {
      "issue": "string, 问题描述",
      "resolution": "string | null, 解决方案",
      "status": "string, 枚举: 'resolved' | 'open' | 'wont_fix'"
    }
  ],
  "files_modified": [
    {
      "path": "string, 文件相对路径",
      "action": "string, 枚举: 'created' | 'modified' | 'deleted'",
      "description": "string, 变更说明"
    }
  ],
  "dependencies": {
    "external_libs_added": ["string, 新增的外部依赖"],
    "services_required": ["string, 运行所需的服务，如 PostgreSQL、Redis"]
  },
  "next_stage": {
    "stage_id": "string, 下一阶段标识",
    "objectives": ["string, 下一阶段的核心目标列表"],
    "prerequisites": ["string, 开始下一阶段前需要满足的条件"],
    "risks": ["string, 潜在风险提示"]
  }
}
```

---

## 四、log.md 模板

```markdown
# [阶段ID] 阶段名称

> 状态: {status} | 开始: {started_at} | 完成: {completed_at}

## 目标清单

- [ ] 1.1 任务描述
- [ ] 1.2 任务描述
- [ ] 2.1 任务描述

## 关键决策

### 决策 1: {决策内容}
- **原因**: {原因}
- **否决方案**: {被否决的方案}

## 遇到的问题

### 问题 1: {问题描述}
- **状态**: 已解决 / 待处理
- **解决方案**: {方案}

## 修改的文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| path/to/file | 修改 | 变更说明 |

## 技术笔记

{本阶段的关键技术发现、踩坑记录、性能数据等}

## 风险提示

{对下一阶段的潜在风险预警}

## 下一阶段预研

- [ ] 下一阶段目标 1
- [ ] 下一阶段目标 2
- [ ] 前置条件检查
```

---

## 五、Agent 执行流程（状态机）

```
┌─────────────────────────────────────────────────────────┐
│                    阶段启动                               │
│                                                         │
│  1. 读取上一阶段 log.json（强制）                         │
│  2. 验证上一阶段 status == "completed"                    │
│  3. 计算上一阶段 log.json 的 SHA-256 → context_hash       │
│  4. 创建本阶段文件夹 + 空 log.json（status: in_progress）  │
│                                                         │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                    任务执行                               │
│                                                         │
│  1. 按 objectives 列表逐项执行                            │
│  2. 每完成一个目标，更新 log.json 中对应 objective 状态     │
│  3. 遇到问题立即记录到 issues_encountered                 │
│  4. 做出技术决策立即记录到 key_decisions                   │
│                                                         │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                    阶段收尾                               │
│                                                         │
│  1. 确认所有 objectives 状态为 done 或 skipped             │
│  2. 填写 files_modified 完整清单                          │
│  3. 填写 next_stage 预研信息（必须包含 risks）              │
│  4. 将 status 改为 "completed"                            │
│  5. 同步生成 log.md（内容与 log.json 一致）                │
│  6. 运行验证命令（测试、构建、类型检查等）                   │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 六、强制性检查清单

Agent 在阶段收尾时，必须逐项确认以下检查项。任何一项未通过，禁止将 status 设为 "completed"。

| # | 检查项 | 通过条件 |
|---|--------|---------|
| 1 | log.json 存在 | 文件存在于当前阶段文件夹 |
| 2 | log.md 存在 | 文件存在于当前阶段文件夹 |
| 3 | status 字段有效 | 值为 "completed" |
| 4 | 所有 objectives 已处理 | 无 pending 状态的目标 |
| 5 | next_stage 已填写 | 包含 stage_id、objectives、risks |
| 6 | files_modified 已填写 | 列出所有变更文件 |
| 7 | context_hash 已记录 | 记录了上一阶段 log.json 的哈希 |
| 8 | log.json 与 log.md 同步 | 核心数据一致 |

---

## 七、异常处理

### 7.1 阶段被阻断（Blocked）

如果阶段执行过程中遇到无法自行解决的问题：

1. 将 `status` 设为 `"blocked"`
2. 在 `issues_encountered` 中记录阻断原因
3. 在 `next_stage` 中说明解除阻断需要的条件
4. 生成 `log.md` 并在顶部醒目标注 `⚠️ 本阶段被阻断`

### 7.2 上一阶段日志缺失

如果启动新阶段时发现上一阶段没有 `log.json`：

1. **禁止开始新阶段**
2. 先为缺失日志的阶段创建 `log.json`，状态设为 `"not_started"`
3. 通知用户补全后再继续

### 7.3 上下文偏差检测

如果 `context_hash` 与上一阶段实际 `log.json` 的哈希不匹配：

1. 在 `issues_encountered` 中记录偏差
2. 重新读取上一阶段 `log.json` 并更新 `context_hash`
3. 通知用户可能存在上下文断裂
