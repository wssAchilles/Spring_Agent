/**
 * Monaco Editor 原生 JSON Schema 与智能感知补全规约 (WorkflowDslSchema)
 * 遵循 Phase 113 规范与 UI/UX Pro Max 规范
 * 1. 结构化 JSON Schema 定义，精确对齐后端 DslWorkflowDefinition
 * 2. 覆盖六大多态节点类型（TASK, STATE_GRAPH_LOOP, SWARM_HANDOFF, DEBATE_ARENA, HITL_APPROVAL, MCP_TOOL_CALL）
 * 3. 提供上下文感知的代码补全项与中文 Markdown Hover 悬浮文档
 */

export interface SchemaCompletionItem {
  label: string;
  kind: string; // 'Keyword' | 'Snippet' | 'Property' | 'Enum'
  detail: string;
  documentation: string;
  insertText: string;
}

/**
 * 标准 JSON Schema 规约对象 (可直接注入 Monaco jsonDefaults)
 */
export const WORKFLOW_DSL_JSON_SCHEMA = {
  $id: "https://qknow.qiantong.tech/schema/workflow-dsl.json",
  $schema: "http://json-schema.org/draft-07/schema#",
  title: "QKnow Hermes Workflow DSL",
  type: "object",
  required: ["workflowId", "name", "version", "nodes", "edges"],
  properties: {
    workflowId: {
      type: "string",
      description: "工作流全局唯一业务标识符",
      examples: ["wf_financial_audit_01"]
    },
    name: {
      type: "string",
      description: "工作流中文名称",
      examples: ["多智能体协同财务审计流程"]
    },
    version: {
      type: "integer",
      minimum: 1,
      description: "声明式工作流版本号（整型自增）",
      default: 1
    },
    description: {
      type: "string",
      description: "工作流业务目标与场景描述"
    },
    nodes: {
      type: "array",
      description: "拓扑节点列表",
      items: {
        type: "object",
        required: ["nodeId", "name", "nodeType"],
        properties: {
          nodeId: {
            type: "string",
            description: "节点唯一标识符",
            examples: ["node_task_01"]
          },
          name: {
            type: "string",
            description: "节点显示名称"
          },
          nodeType: {
            type: "string",
            enum: [
              "TASK",
              "STATE_GRAPH_LOOP",
              "SWARM_HANDOFF",
              "DEBATE_ARENA",
              "HITL_APPROVAL",
              "MCP_TOOL_CALL"
            ],
            description: "节点多态类型：TASK(原子任务), STATE_GRAPH_LOOP(状态图循环), SWARM_HANDOFF(上下文交接), DEBATE_ARENA(对抗辩论), HITL_APPROVAL(人机审批), MCP_TOOL_CALL(MCP工具)"
          },
          objective: {
            type: "string",
            description: "节点核心目标说明 (面向 LLM/Agent 执行)"
          },
          requiredCapability: {
            type: "string",
            description: "执行该节点所需的 Agent 能力标签"
          },
          mcpToolName: {
            type: "string",
            description: "调用的企业级 MCP 工具名称 (仅 MCP_TOOL_CALL 节点有效)"
          },
          timeoutSeconds: {
            type: "integer",
            minimum: 1,
            maximum: 600,
            default: 30,
            description: "单节点执行超时时间 (秒)"
          },
          config: {
            type: "object",
            description: "节点私有运行时配置参数"
          },
          x: {
            type: "integer",
            description: "视觉画布 X 坐标 (整数)"
          },
          y: {
            type: "integer",
            description: "视觉画布 Y 坐标 (整数)"
          }
        }
      }
    },
    edges: {
      type: "array",
      description: "拓扑有向边列表",
      items: {
        type: "object",
        required: ["sourceNodeId", "targetNodeId"],
        properties: {
          edgeId: {
            type: "string",
            description: "边的唯一标识符"
          },
          sourceNodeId: {
            type: "string",
            description: "源节点 ID"
          },
          targetNodeId: {
            type: "string",
            description: "目标节点 ID"
          },
          condition: {
            type: "string",
            description: "条件转移断言表达式"
          },
          fallbackEdge: {
            type: "boolean",
            default: false,
            description: "是否为异常软着陆兜底边"
          }
        }
      }
    }
  }
};

/**
 * 智能代码补全项生成器 (适用于 Monaco CompletionItemProvider)
 */
export function getWorkflowDslCompletions(): SchemaCompletionItem[] {
  return [
    {
      label: "nodeType: TASK",
      kind: "Enum",
      detail: "标准原子任务节点",
      documentation: "由单一专注的 Agent 承载执行具体的业务目标与子任务。",
      insertText: "nodeType: TASK\n"
    },
    {
      label: "nodeType: STATE_GRAPH_LOOP",
      kind: "Enum",
      detail: "状态机有界循环节点",
      documentation: "具备硬性迭代步数上限 (maxIterations <= 10) 与退出断言的拓扑环路。",
      insertText: "nodeType: STATE_GRAPH_LOOP\n"
    },
    {
      label: "nodeType: SWARM_HANDOFF",
      kind: "Enum",
      detail: "Swarm 动态上下文交接节点",
      documentation: "多智能体通过阿里千问 1536 维超球面名片动态发现并移交控制权。",
      insertText: "nodeType: SWARM_HANDOFF\n"
    },
    {
      label: "nodeType: DEBATE_ARENA",
      kind: "Enum",
      detail: "多智能体对抗辩论竞技场节点",
      documentation: "正反双方 Agent 进行多轮结构化对抗辩论，由裁判裁决最终结论。",
      insertText: "nodeType: DEBATE_ARENA\n"
    },
    {
      label: "nodeType: HITL_APPROVAL",
      kind: "Enum",
      detail: "人机协同审批挂起节点",
      documentation: "针对高危操作挂起工单，等待人类专家二次核验放行或驳回。",
      insertText: "nodeType: HITL_APPROVAL\n"
    },
    {
      label: "nodeType: MCP_TOOL_CALL",
      kind: "Enum",
      detail: "企业级 MCP 工具调用节点",
      documentation: "执行外部或本地注册的标准 MCP 工具，支持租约与超时管理。",
      insertText: "nodeType: MCP_TOOL_CALL\n"
    },
    {
      label: "node-template",
      kind: "Snippet",
      detail: "完整节点代码片段",
      documentation: "插入一个包含完整属性的声明式工作流节点模板。",
      insertText: [
        "- nodeId: node_${1:id}",
        "  name: \"${2:节点名称}\"",
        "  nodeType: ${3:TASK}",
        "  objective: \"${4:目标描述}\"",
        "  timeoutSeconds: 30"
      ].join("\n")
    }
  ];
}
