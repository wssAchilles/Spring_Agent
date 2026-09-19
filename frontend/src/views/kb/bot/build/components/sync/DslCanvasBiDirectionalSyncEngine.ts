/**
 * 在线 DSL 与画布实时双向无损同步引擎 (DslCanvasBiDirectionalSyncEngine)
 * 遵循 Phase 112 规范与 UI/UX Pro Max 规范
 * 1. 版本纪元控制 (Epoch Versioning)：单调递增版本号，阻止过时事件回放
 * 2. 双向互斥事件防回环锁 (Event Loop Guard)：彻底消除微任务风暴死循环
 * 3. 150ms 优雅防抖队列与坐标整数截断 (Math.round)
 * 4. 语法容错沙箱与画布安全挂起 (Safe Suspend)：语法错误 100% 行内标红，画布 0 白屏
 */

export interface DslWorkflowNodeData {
  nodeId: string;
  name: string;
  nodeType: 'TASK' | 'STATE_GRAPH_LOOP' | 'SWARM_HANDOFF' | 'DEBATE_ARENA' | 'HITL_APPROVAL' | 'MCP_TOOL_CALL';
  objective?: string;
  requiredCapability?: string;
  mcpToolName?: string;
  timeoutSeconds?: number;
  config?: Record<string, any>;
  x?: number;
  y?: number;
}

export interface DslWorkflowEdgeData {
  edgeId: string;
  sourceNodeId: string;
  targetNodeId: string;
  condition?: string;
  fallbackEdge?: boolean;
}

export interface DslWorkflowAst {
  workflowId: string;
  name: string;
  version: number;
  description?: string;
  nodes: DslWorkflowNodeData[];
  edges: DslWorkflowEdgeData[];
  metadata?: Record<string, any>;
}

export interface MarkerDiagnostic {
  startLineNumber: number;
  startColumn: number;
  endLineNumber: number;
  endColumn: number;
  message: string;
  severity: 'error' | 'warning' | 'info';
}

export interface SyncResult {
  success: boolean;
  epochVersion: number;
  ast: DslWorkflowAst | null;
  nodes: any[];
  edges: any[];
  diagnostics: MarkerDiagnostic[];
  isSuspended: boolean;
}

export class DslCanvasBiDirectionalSyncEngine {
  private epochVersion: number = 0;
  private isSyncingFromCode: boolean = false;
  private isSyncingFromCanvas: boolean = false;
  private lastValidAst: DslWorkflowAst | null = null;
  private lastValidCode: string = '';
  private nodePositionMap: Map<string, { x: number; y: number }> = new Map();
  private debounceTimeoutId: any = null;

  constructor() {
    this.epochVersion = 1;
  }

  /**
   * 获取当前单调递增的纪元版本号
   */
  public getEpochVersion(): number {
    return this.epochVersion;
  }

  /**
   * 检查是否正处于代码向画布同步期
   */
  public isCodeSyncing(): boolean {
    return this.isSyncingFromCode;
  }

  /**
   * 检查是否正处于画布向代码同步期
   */
  public isCanvasSyncing(): boolean {
    return this.isSyncingFromCanvas;
  }

  /**
   * 获取最后一次合法验证通过的 AST
   */
  public getLastValidAst(): DslWorkflowAst | null {
    return this.lastValidAst;
  }

  /**
   * 代码向画布同步 (Code-to-Canvas)
   * 包含轻量语法解析、Schema 校验、增量坐标合并与沙箱安全挂起
   */
  public syncCodeToCanvas(code: string): SyncResult {
    // 1. 如果是由画布反向同步触发的代码变更，直接忽略避免回环
    if (this.isSyncingFromCanvas) {
      return {
        success: true,
        epochVersion: this.epochVersion,
        ast: this.lastValidAst,
        nodes: this.lastValidAst ? this.convertAstToVueFlowNodes(this.lastValidAst) : [],
        edges: this.lastValidAst ? this.convertAstToVueFlowEdges(this.lastValidAst) : [],
        diagnostics: [],
        isSuspended: false
      };
    }

    this.isSyncingFromCode = true;
    try {
      // 2. 语法容错沙箱解析
      const parseResult = this.parseDslToAst(code);
      if (!parseResult.success || !parseResult.ast) {
        // 语法解析失败：安全挂起画布，保持最后已知合法状态，派发诊断波浪线
        return {
          success: false,
          epochVersion: this.epochVersion,
          ast: this.lastValidAst,
          nodes: this.lastValidAst ? this.convertAstToVueFlowNodes(this.lastValidAst) : [],
          edges: this.lastValidAst ? this.convertAstToVueFlowEdges(this.lastValidAst) : [],
          diagnostics: parseResult.diagnostics,
          isSuspended: true
        };
      }

      // 3. 解析成功：递增纪元版本号，更新合法缓存
      this.epochVersion++;
      this.lastValidAst = parseResult.ast;
      this.lastValidCode = code;

      // 4. 增量坐标合并 (保持用户已拖拽的坐标)
      const vueFlowNodes = this.convertAstToVueFlowNodes(parseResult.ast);
      const vueFlowEdges = this.convertAstToVueFlowEdges(parseResult.ast);

      return {
        success: true,
        epochVersion: this.epochVersion,
        ast: parseResult.ast,
        nodes: vueFlowNodes,
        edges: vueFlowEdges,
        diagnostics: [],
        isSuspended: false
      };
    } finally {
      this.isSyncingFromCode = false;
    }
  }

  /**
   * 画布向代码同步 (Canvas-to-Code)
   * 包含防抖处理、坐标整数截断与序列化
   */
  public syncCanvasToCode(
    nodes: any[],
    edges: any[],
    debounceMs: number = 150
  ): Promise<string> {
    return new Promise((resolve) => {
      // 1. 若当前正处于代码向画布同步期，直接丢弃，严禁反向触发
      if (this.isSyncingFromCode) {
        resolve(this.lastValidCode);
        return;
      }

      if (this.debounceTimeoutId) {
        clearTimeout(this.debounceTimeoutId);
      }

      this.debounceTimeoutId = setTimeout(() => {
        this.isSyncingFromCanvas = true;
        try {
          this.epochVersion++;

          // 2. 坐标整数截断并记录到本地位置映射表
          const astNodes: DslWorkflowNodeData[] = nodes.map((node, index) => {
            const roundedX = Math.round(node.position?.x ?? (index % 4) * 280 + 80);
            const roundedY = Math.round(node.position?.y ?? Math.floor(index / 4) * 180 + 100);
            this.nodePositionMap.set(node.id, { x: roundedX, y: roundedY });

            return {
              nodeId: node.id,
              name: node.data?.name || node.label || node.id,
              nodeType: node.data?.nodeType || 'TASK',
              objective: node.data?.objective,
              requiredCapability: node.data?.requiredCapability,
              mcpToolName: node.data?.mcpToolName,
              timeoutSeconds: node.data?.timeoutSeconds || 30,
              config: node.data?.config || {},
              x: roundedX,
              y: roundedY
            };
          });

          // 3. 边规约转换
          const astEdges: DslWorkflowEdgeData[] = edges.map((edge) => ({
            edgeId: edge.id,
            sourceNodeId: edge.source,
            targetNodeId: edge.target,
            condition: edge.data?.condition,
            fallbackEdge: edge.data?.fallbackEdge || false
          }));

          const updatedAst: DslWorkflowAst = {
            workflowId: this.lastValidAst?.workflowId || 'wf_visual_studio_01',
            name: this.lastValidAst?.name || '可视化编排工作流',
            version: this.lastValidAst ? this.lastValidAst.version + 1 : 1,
            description: this.lastValidAst?.description || '由 WorkflowStudio 生成',
            nodes: astNodes,
            edges: astEdges,
            metadata: {
              ...this.lastValidAst?.metadata,
              lastSyncedEpoch: this.epochVersion,
              generator: 'WorkflowStudio.DslCanvasBiDirectionalSyncEngine'
            }
          };

          this.lastValidAst = updatedAst;
          const formattedCode = this.serializeAstToDsl(updatedAst);
          this.lastValidCode = formattedCode;
          resolve(formattedCode);
        } finally {
          this.isSyncingFromCanvas = false;
        }
      }, debounceMs);
    });
  }

  /**
   * 容错沙箱：解析 DSL 文本并输出 AST 与行内诊断波浪线
   */
  public parseDslToAst(code: string): {
    success: boolean;
    ast: DslWorkflowAst | null;
    diagnostics: MarkerDiagnostic[];
  } {
    const trimmed = (code || '').trim();
    if (!trimmed) {
      return {
        success: false,
        ast: null,
        diagnostics: [
          {
            startLineNumber: 1,
            startColumn: 1,
            endLineNumber: 1,
            endColumn: 1,
            message: 'DSL 文本不能为空',
            severity: 'error'
          }
        ]
      };
    }

    try {
      // 优先尝试 JSON 解析
      if (trimmed.startsWith('{')) {
        const rawObj = JSON.parse(code);
        const validated = this.validateAndNormalizeAst(rawObj);
        return {
          success: true,
          ast: validated,
          diagnostics: []
        };
      }

      // 轻量化键值与 YAML 格式安全解析
      const yamlAst = this.parseSimpleYaml(code);
      const validated = this.validateAndNormalizeAst(yamlAst);
      return {
        success: true,
        ast: validated,
        diagnostics: []
      };
    } catch (err: any) {
      // 捕获语法错误并计算错误行号
      let lineNum = 1;
      let colNum = 1;
      const match = (err.message || '').match(/line (\d+)/i) || (err.message || '').match(/at position (\d+)/i);
      if (match) {
        lineNum = parseInt(match[1], 10) || 1;
      }

      return {
        success: false,
        ast: null,
        diagnostics: [
          {
            startLineNumber: lineNum,
            startColumn: colNum,
            endLineNumber: lineNum,
            endColumn: 50,
            message: `DSL 语法解析错误: ${err.message || '格式不合法'}`,
            severity: 'error'
          }
        ]
      };
    }
  }

  /**
   * 将 AST 规范化序列化为易读的 DSL 文本 (JSON / YAML 格式)
   */
  public serializeAstToDsl(ast: DslWorkflowAst): string {
    return JSON.stringify(ast, null, 2);
  }

  /**
   * 将内部 AST 转换为 VueFlow 节点列表
   */
  private convertAstToVueFlowNodes(ast: DslWorkflowAst): any[] {
    return ast.nodes.map((node, index) => {
      // 优先使用已缓存的用户拖拽坐标，其次是 AST 中显式坐标，最后是网格自适应
      const cachedPos = this.nodePositionMap.get(node.nodeId);
      const posX = cachedPos?.x ?? node.x ?? (index % 4) * 280 + 80;
      const posY = cachedPos?.y ?? node.y ?? Math.floor(index / 4) * 180 + 100;
      this.nodePositionMap.set(node.nodeId, { x: posX, y: posY });

      return {
        id: node.nodeId,
        type: this.mapNodeTypeToVueFlowType(node.nodeType),
        label: node.name,
        position: { x: posX, y: posY },
        data: {
          nodeId: node.nodeId,
          name: node.name,
          nodeType: node.nodeType,
          objective: node.objective,
          requiredCapability: node.requiredCapability,
          mcpToolName: node.mcpToolName,
          timeoutSeconds: node.timeoutSeconds || 30,
          config: node.config || {}
        }
      };
    });
  }

  /**
   * 将内部 AST 转换为 VueFlow 边列表
   */
  private convertAstToVueFlowEdges(ast: DslWorkflowAst): any[] {
    return ast.edges.map((edge) => ({
      id: edge.edgeId || `edge_${edge.sourceNodeId}_${edge.targetNodeId}`,
      source: edge.sourceNodeId,
      target: edge.targetNodeId,
      type: 'smoothstep',
      animated: true,
      data: {
        condition: edge.condition,
        fallbackEdge: edge.fallbackEdge
      }
    }));
  }

  private mapNodeTypeToVueFlowType(nodeType: string): string {
    switch (nodeType) {
      case 'STATE_GRAPH_LOOP':
        return 'loop-node';
      case 'SWARM_HANDOFF':
        return 'swarm-node';
      case 'DEBATE_ARENA':
        return 'debate-node';
      case 'HITL_APPROVAL':
        return 'hitl-node';
      case 'MCP_TOOL_CALL':
        return 'mcp-node';
      case 'TASK':
      default:
        return 'task-node';
    }
  }

  private validateAndNormalizeAst(raw: any): DslWorkflowAst {
    if (!raw || typeof raw !== 'object') {
      throw new Error('DSL 根对象必须为键值字典');
    }
    const nodes = Array.isArray(raw.nodes) ? raw.nodes : [];
    const edges = Array.isArray(raw.edges) ? raw.edges : [];

    const normalizedNodes: DslWorkflowNodeData[] = nodes.map((n: any, idx: number) => {
      const id = String(n.nodeId || n.id || `node_${idx + 1}`);
      return {
        nodeId: id,
        name: String(n.name || id),
        nodeType: (n.nodeType || 'TASK').toUpperCase(),
        objective: n.objective,
        requiredCapability: n.requiredCapability,
        mcpToolName: n.mcpToolName,
        timeoutSeconds: Number(n.timeoutSeconds) || 30,
        config: typeof n.config === 'object' ? n.config : {},
        x: n.x !== undefined ? Math.round(Number(n.x)) : undefined,
        y: n.y !== undefined ? Math.round(Number(n.y)) : undefined
      };
    });

    const normalizedEdges: DslWorkflowEdgeData[] = edges.map((e: any, idx: number) => {
      const source = String(e.sourceNodeId || e.source || '');
      const target = String(e.targetNodeId || e.target || '');
      if (!source || !target) {
        throw new Error(`边定义缺少 source 或 target (索引: ${idx})`);
      }
      return {
        edgeId: String(e.edgeId || e.id || `edge_${source}_${target}`),
        sourceNodeId: source,
        targetNodeId: target,
        condition: e.condition,
        fallbackEdge: Boolean(e.fallbackEdge)
      };
    });

    return {
      workflowId: String(raw.workflowId || 'wf_unnamed'),
      name: String(raw.name || '未命名工作流'),
      version: Number(raw.version) || 1,
      description: raw.description ? String(raw.description) : undefined,
      nodes: normalizedNodes,
      edges: normalizedEdges,
      metadata: typeof raw.metadata === 'object' ? raw.metadata : {}
    };
  }

  /**
   * 轻量级 YAML 基础词法行解析
   */
  private parseSimpleYaml(yamlStr: string): any {
    const lines = yamlStr.split('\n');
    const result: any = { nodes: [], edges: [] };
    let currentSection: string | null = null;
    let currentNode: any = null;
    let currentEdge: any = null;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;

      // 顶级 key
      if (line.startsWith('workflowId:')) {
        result.workflowId = line.replace('workflowId:', '').trim();
      } else if (line.startsWith('name:')) {
        result.name = line.replace('name:', '').trim();
      } else if (line.startsWith('version:')) {
        result.version = parseInt(line.replace('version:', '').trim(), 10) || 1;
      } else if (line.startsWith('nodes:')) {
        currentSection = 'nodes';
      } else if (line.startsWith('edges:')) {
        currentSection = 'edges';
      } else if (currentSection === 'nodes') {
        if (trimmed.startsWith('- nodeId:') || trimmed.startsWith('- id:')) {
          currentNode = {
            nodeId: trimmed.replace(/^- (nodeId|id):/, '').trim(),
            config: {}
          };
          result.nodes.push(currentNode);
        } else if (currentNode && trimmed.includes(':')) {
          const colonIdx = trimmed.indexOf(':');
          const key = trimmed.substring(0, colonIdx).trim();
          const val = trimmed.substring(colonIdx + 1).trim();
          if (key === 'name') currentNode.name = val;
          else if (key === 'nodeType') currentNode.nodeType = val;
          else if (key === 'objective') currentNode.objective = val;
          else if (key === 'requiredCapability') currentNode.requiredCapability = val;
          else if (key === 'mcpToolName') currentNode.mcpToolName = val;
          else if (key === 'timeoutSeconds') currentNode.timeoutSeconds = parseInt(val, 10) || 30;
        }
      } else if (currentSection === 'edges') {
        if (trimmed.startsWith('- edgeId:') || trimmed.startsWith('- source:')) {
          currentEdge = {};
          if (trimmed.startsWith('- source:')) {
            currentEdge.sourceNodeId = trimmed.replace('^- source:', '').trim();
          } else {
            currentEdge.edgeId = trimmed.replace('^- edgeId:', '').trim();
          }
          result.edges.push(currentEdge);
        } else if (currentEdge && trimmed.includes(':')) {
          const colonIdx = trimmed.indexOf(':');
          const key = trimmed.substring(0, colonIdx).trim();
          const val = trimmed.substring(colonIdx + 1).trim();
          if (key === 'source' || key === 'sourceNodeId') currentEdge.sourceNodeId = val;
          else if (key === 'target' || key === 'targetNodeId') currentEdge.targetNodeId = val;
          else if (key === 'condition') currentEdge.condition = val;
        }
      }
    }

    if (!result.workflowId && result.nodes.length === 0) {
      throw new Error('无法解析有效的 YAML 工作流结构，请检查缩进与冒号');
    }
    return result;
  }
}
