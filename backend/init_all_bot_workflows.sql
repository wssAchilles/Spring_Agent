-- ==============================================================================
-- 企业级 AI-Native RAG 知识库与软件智能体编排平台: 全量工作流 DAG 拓扑初始化与持久化脚本
-- 严禁前端硬编码，所有工作流的节点与边 100% 由后端数据库持久化驱动
-- ==============================================================================

BEGIN;

-- 清理旧的节点与边数据，保证全量更新不遗留碎片
DELETE FROM kb_flow_node WHERE bot_id IN (1, 2, 3, 4, 5, 6, 7, 8, 9);
DELETE FROM kb_flow_edge WHERE bot_id IN (1, 2, 3, 4, 5, 6, 7, 8, 9);

-- ==============================================================================
-- 1. Bot 9: 智能因果协同工作流 (Workflow, type=0)
-- 拓扑结构: 开始 -> 知识召回 -> 因果智能路由 -> (深度推测推理 / 快捷应答) -> 因果反事实工具 -> 结论呈现
-- ==============================================================================

-- 节点 1: 开始节点
INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES (
    1001, 9, 'start_1', '开始', 1,
    '{"label":"用户输入与目标定义","description":"定义因果协同分析的目标问题与边界上下文"}',
    '[]',
    '[{"id":"query","name":"query","label":"用户目标问题","type":"string"}]',
    '{"id":"start_1","type":"start","position":{"x":80,"y":280},"data":{"input":[],"config":{"label":"用户输入与目标定义","description":"定义因果协同分析的目标问题与边界上下文"},"output":[{"id":"query","name":"query","label":"用户目标问题","type":"string"}]}}',
    0, 0, '管理员', 1
);

-- 节点 2: 知识库检索
INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES (
    1001, 9, 'knowledge_1', '知识库检索', 5,
    '{"label":"因果图谱与学术向量召回","description":"基于阿里千问 1536 维超球面向量与因果图谱检索高保真背景事实","matchThreshold":0.85,"topK":5,"retrievalMode":"hybrid"}',
    '[]',
    '[{"id":"chunks","name":"chunks","label":"召回知识片段","type":"array"}]',
    '{"id":"knowledge_1","type":"knowledge","position":{"x":440,"y":280},"data":{"input":[],"config":{"label":"因果图谱与学术向量召回","description":"基于阿里千问 1536 维超球面向量与因果图谱检索高保真背景事实","matchThreshold":0.85,"topK":5,"retrievalMode":"hybrid"},"output":[{"id":"chunks","name":"chunks","label":"召回知识片段","type":"array"}]}}',
    0, 0, '管理员', 1
);

-- 节点 3: 条件分支路由
INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES (
    1001, 9, 'condition_1', '条件分支', 4,
    '{"label":"因果复杂度智能路由","cases":[{"id":"case-deep","label":"复杂因果与反事实分析 (深度思考)","condition":"query.length > 20","targetHandle":"condition-case-deep"},{"id":"case-fast","label":"通用事实查询与快速答复 (兜底分支)","isElse":true,"condition":"兜底分支","targetHandle":"condition-case-fast"}]}',
    '[]',
    '[]',
    '{"id":"condition_1","type":"condition","position":{"x":820,"y":280},"data":{"input":[],"config":{"label":"因果复杂度智能路由","cases":[{"id":"case-deep","label":"复杂因果与反事实分析 (深度思考)","condition":"query.length > 20","targetHandle":"condition-case-deep"},{"id":"case-fast","label":"通用事实查询与快速答复 (兜底分支)","isElse":true,"condition":"兜底分支","targetHandle":"condition-case-fast"}]},"output":[]}}',
    0, 0, '管理员', 1
);

-- 节点 4: DeepSeek 深度推理 (上路分支)
INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES (
    1001, 9, 'llm_deep_1', '大模型推理', 2,
    '{"label":"DeepSeek 思考链深度因果推理","platform":"DeepSeek","model":"deepseek-flash","temperature":1.0,"thinking":true,"prompt":"请基于以下知识切片进行深度长思维链因果分析：\\n{{chunks}}\\n目标问题：{{query}}"}',
    '[]',
    '[{"id":"reasoning","name":"reasoning_content","label":"深度思考链","type":"string"},{"id":"result","name":"result","label":"推理结果","type":"string"}]',
    '{"id":"llm_deep_1","type":"llm","position":{"x":1260,"y":110},"data":{"input":[],"config":{"label":"DeepSeek 思考链深度因果推理","platform":"DeepSeek","model":"deepseek-flash","temperature":1.0,"thinking":true,"prompt":"请基于以下知识切片进行深度长思维链因果分析：\\n{{chunks}}\\n目标问题：{{query}}"},"output":[{"id":"reasoning","name":"reasoning_content","label":"深度思考链","type":"string"},{"id":"result","name":"result","label":"推理结果","type":"string"}]}}',
    0, 0, '管理员', 1
);

-- 节点 5: 因果反事实干预工具 (上路后置验真)
INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES (
    1001, 9, 'tool_1', '工具调用', 7,
    '{"label":"因果反事实干预验证工具","toolName":"causal_counterfactual_verifier","toolSource":"custom"}',
    '[]',
    '[{"id":"verified_report","name":"verified_report","label":"反事实核验凭单","type":"string"}]',
    '{"id":"tool_1","type":"tool","position":{"x":1660,"y":110},"data":{"input":[],"config":{"label":"因果反事实干预验证工具","toolName":"causal_counterfactual_verifier","toolSource":"custom"},"output":[{"id":"verified_report","name":"verified_report","label":"反事实核验凭单","type":"string"}]}}',
    0, 0, '管理员', 1
);

-- 节点 6: 快捷轻量推理 (下路分支)
INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES (
    1001, 9, 'llm_fast_1', '大模型推理', 2,
    '{"label":"轻量快速语义应答","platform":"DeepSeek","model":"deepseek-flash","temperature":0.7,"thinking":false,"prompt":"基于检索到的知识快速回答：\\n{{chunks}}\\n问题：{{query}}"}',
    '[]',
    '[{"id":"result","name":"result","label":"应答结果","type":"string"}]',
    '{"id":"llm_fast_1","type":"llm","position":{"x":1460,"y":460},"data":{"input":[],"config":{"label":"轻量快速语义应答","platform":"DeepSeek","model":"deepseek-flash","temperature":0.7,"thinking":false,"prompt":"基于检索到的知识快速回答：\\n{{chunks}}\\n问题：{{query}}"},"output":[{"id":"result","name":"result","label":"应答结果","type":"string"}]}}',
    0, 0, '管理员', 1
);

-- 节点 7: 回复节点 (汇聚终点)
INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES (
    1001, 9, 'reply_1', '回复', 3,
    '{"label":"智能因果协同结论输出","replyType":"text"}',
    '[]',
    '[]',
    '{"id":"reply_1","type":"reply","position":{"x":2060,"y":280},"data":{"input":[],"config":{"label":"智能因果协同结论输出","replyType":"text"},"output":[]}}',
    0, 0, '管理员', 1
);

-- Bot 9 连线 (精准对齐 condition-case-* 句柄)
INSERT INTO kb_flow_edge (workspace_id, bot_id, source_node_uuid, target_node_uuid, source_handle, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 9, 'start_1', 'knowledge_1', NULL, '{"id":"e-start-knowledge","type":"default","source":"start_1","target":"knowledge_1"}', 0, 0, '管理员', 1),
(1001, 9, 'knowledge_1', 'condition_1', NULL, '{"id":"e-knowledge-condition","type":"default","source":"knowledge_1","target":"condition_1"}', 0, 0, '管理员', 1),
(1001, 9, 'condition_1', 'llm_deep_1', 'condition-case-deep', '{"id":"e-condition-deep","type":"default","source":"condition_1","target":"llm_deep_1","sourceHandle":"condition-case-deep"}', 0, 0, '管理员', 1),
(1001, 9, 'condition_1', 'llm_fast_1', 'condition-case-fast', '{"id":"e-condition-fast","type":"default","source":"condition_1","target":"llm_fast_1","sourceHandle":"condition-case-fast"}', 0, 0, '管理员', 1),
(1001, 9, 'llm_deep_1', 'tool_1', NULL, '{"id":"e-deep-tool","type":"default","source":"llm_deep_1","target":"tool_1"}', 0, 0, '管理员', 1),
(1001, 9, 'tool_1', 'reply_1', NULL, '{"id":"e-tool-reply","type":"default","source":"tool_1","target":"reply_1"}', 0, 0, '管理员', 1),
(1001, 9, 'llm_fast_1', 'reply_1', NULL, '{"id":"e-fast-reply","type":"default","source":"llm_fast_1","target":"reply_1"}', 0, 0, '管理员', 1);


-- ==============================================================================
-- 2. Bot 3: 知识检索工作流 (Workflow, type=0)
-- 拓扑结构: 开始 -> [双路知识库并发检索] -> 多源知识融合与交叉验真 -> 综合报告输出
-- ==============================================================================

INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 3, 'start_1', '开始', 1,
 '{"label":"检索意图输入","description":"接收用户关于制度与架构的复杂查询"}',
 '[]',
 '[{"id":"query","name":"query","label":"查询意图","type":"string"}]',
 '{"id":"start_1","type":"start","position":{"x":80,"y":260},"data":{"input":[],"config":{"label":"检索意图输入","description":"接收用户关于制度与架构的复杂查询"},"output":[{"id":"query","name":"query","label":"查询意图","type":"string"}]}}',
 0, 0, '管理员', 1),

(1001, 3, 'knowledge_1', '知识库检索', 5,
 '{"label":"组织政策与规章制度库","description":"针对组织制度与规范条目进行稠密向量检索","matchThreshold":0.80,"topK":4}',
 '[]',
 '[{"id":"chunks_rule","name":"chunks_rule","label":"制度切片","type":"array"}]',
 '{"id":"knowledge_1","type":"knowledge","position":{"x":440,"y":140},"data":{"input":[],"config":{"label":"组织政策与规章制度库","description":"针对组织制度与规范条目进行稠密向量检索","matchThreshold":0.80,"topK":4},"output":[{"id":"chunks_rule","name":"chunks_rule","label":"制度切片","type":"array"}]}}',
 0, 0, '管理员', 1),

(1001, 3, 'knowledge_2', '知识库检索', 5,
 '{"label":"核心技术与研发架构库","description":"针对技术架构与代码实现进行超球面余弦检索","matchThreshold":0.80,"topK":4}',
 '[]',
 '[{"id":"chunks_tech","name":"chunks_tech","label":"架构切片","type":"array"}]',
 '{"id":"knowledge_2","type":"knowledge","position":{"x":440,"y":380},"data":{"input":[],"config":{"label":"核心技术与研发架构库","description":"针对技术架构与代码实现进行超球面余弦检索","matchThreshold":0.80,"topK":4},"output":[{"id":"chunks_tech","name":"chunks_tech","label":"架构切片","type":"array"}]}}',
 0, 0, '管理员', 1),

(1001, 3, 'llm_merge_1', '大模型推理', 2,
 '{"label":"多源知识融合与交叉验真","platform":"DeepSeek","model":"deepseek-flash","temperature":0.5,"prompt":"请综合制度切片与架构切片进行交叉对齐融合：\\n制度规范：{{chunks_rule}}\\n技术实现：{{chunks_tech}}\\n用户问题：{{query}}"}',
 '[]',
 '[{"id":"merged_result","name":"merged_result","label":"融合答复","type":"string"}]',
 '{"id":"llm_merge_1","type":"llm","position":{"x":860,"y":260},"data":{"input":[],"config":{"label":"多源知识融合与交叉验真","platform":"DeepSeek","model":"deepseek-flash","temperature":0.5,"prompt":"请综合制度切片与架构切片进行交叉对齐融合：\\n制度规范：{{chunks_rule}}\\n技术实现：{{chunks_tech}}\\n用户问题：{{query}}"},"output":[{"id":"merged_result","name":"merged_result","label":"融合答复","type":"string"}]}}',
 0, 0, '管理员', 1),

(1001, 3, 'reply_1', '回复', 3,
 '{"label":"结构化综合检索报告","replyType":"text"}',
 '[]',
 '[]',
 '{"id":"reply_1","type":"reply","position":{"x":1260,"y":260},"data":{"input":[],"config":{"label":"结构化综合检索报告","replyType":"text"},"output":[]}}',
 0, 0, '管理员', 1);

-- Bot 3 连线
INSERT INTO kb_flow_edge (workspace_id, bot_id, source_node_uuid, target_node_uuid, source_handle, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 3, 'start_1', 'knowledge_1', NULL, '{"id":"e-start-k1","type":"default","source":"start_1","target":"knowledge_1"}', 0, 0, '管理员', 1),
(1001, 3, 'start_1', 'knowledge_2', NULL, '{"id":"e-start-k2","type":"default","source":"start_1","target":"knowledge_2"}', 0, 0, '管理员', 1),
(1001, 3, 'knowledge_1', 'llm_merge_1', NULL, '{"id":"e-k1-merge","type":"default","source":"knowledge_1","target":"llm_merge_1"}', 0, 0, '管理员', 1),
(1001, 3, 'knowledge_2', 'llm_merge_1', NULL, '{"id":"e-k2-merge","type":"default","source":"knowledge_2","target":"llm_merge_1"}', 0, 0, '管理员', 1),
(1001, 3, 'llm_merge_1', 'reply_1', NULL, '{"id":"e-merge-reply","type":"default","source":"llm_merge_1","target":"reply_1"}', 0, 0, '管理员', 1);


-- ==============================================================================
-- 3. Bot 4: 文档摘要Chatflow (Chatflow, type=1)
-- 拓扑结构: 开始 -> 文档切片检索 -> DeepSeek 结构化摘要 -> 流式输出
-- ==============================================================================

INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 4, 'start_1', '开始', 1,
 '{"label":"文档对话与输入","description":"接收上传长文档或待总结长文本"}',
 '[]',
 '[{"id":"query","name":"query","label":"待摘要文本","type":"string"}]',
 '{"id":"start_1","type":"start","position":{"x":80,"y":240},"data":{"input":[],"config":{"label":"文档对话与输入","description":"接收上传长文档或待总结长文本"},"output":[{"id":"query","name":"query","label":"待摘要文本","type":"string"}]}}',
 0, 0, '管理员', 1),

(1001, 4, 'knowledge_1', '知识库检索', 5,
 '{"label":"长文档全文分段检索","description":"多段落分块召回核心段落","topK":6}',
 '[]',
 '[{"id":"chunks","name":"chunks","label":"文档分块","type":"array"}]',
 '{"id":"knowledge_1","type":"knowledge","position":{"x":460,"y":240},"data":{"input":[],"config":{"label":"长文档全文分段检索","description":"多段落分块召回核心段落","topK":6},"output":[{"id":"chunks","name":"chunks","label":"文档分块","type":"array"}]}}',
 0, 0, '管理员', 1),

(1001, 4, 'llm_1', '大模型推理', 2,
 '{"label":"DeepSeek 提纲与要点摘要","platform":"DeepSeek","model":"deepseek-flash","temperature":0.3,"prompt":"请根据文档切片提取核心概要与关键结论：\\n{{chunks}}"}',
 '[]',
 '[{"id":"summary","name":"summary","label":"摘要文本","type":"string"}]',
 '{"id":"llm_1","type":"llm","position":{"x":840,"y":240},"data":{"input":[],"config":{"label":"DeepSeek 提纲与要点摘要","platform":"DeepSeek","model":"deepseek-flash","temperature":0.3,"prompt":"请根据文档切片提取核心概要与关键结论：\\n{{chunks}}"},"output":[{"id":"summary","name":"summary","label":"摘要文本","type":"string"}]}}',
 0, 0, '管理员', 1),

(1001, 4, 'reply_1', '回复', 3,
 '{"label":"流式摘要答复输出","replyType":"text"}',
 '[]',
 '[]',
 '{"id":"reply_1","type":"reply","position":{"x":1220,"y":240},"data":{"input":[],"config":{"label":"流式摘要答复输出","replyType":"text"},"output":[]}}',
 0, 0, '管理员', 1);

-- Bot 4 连线
INSERT INTO kb_flow_edge (workspace_id, bot_id, source_node_uuid, target_node_uuid, source_handle, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 4, 'start_1', 'knowledge_1', NULL, '{"id":"e-start-knowledge","type":"default","source":"start_1","target":"knowledge_1"}', 0, 0, '管理员', 1),
(1001, 4, 'knowledge_1', 'llm_1', NULL, '{"id":"e-knowledge-llm","type":"default","source":"knowledge_1","target":"llm_1"}', 0, 0, '管理员', 1),
(1001, 4, 'llm_1', 'reply_1', NULL, '{"id":"e-llm-reply","type":"default","source":"llm_1","target":"reply_1"}', 0, 0, '管理员', 1);


-- ==============================================================================
-- 4. Bot 1: 实习助手 (Agent, type=2)
-- ==============================================================================
INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 1, 'start_1', '开始', 1,
 '{"label":"实习咨询输入"}', '[]', '[{"id":"query","name":"query","label":"问题","type":"string"}]',
 '{"id":"start_1","type":"start","position":{"x":80,"y":240},"data":{"input":[],"config":{"label":"实习咨询输入"},"output":[{"id":"query","name":"query","label":"问题","type":"string"}]}}', 0, 0, '管理员', 1),
(1001, 1, 'knowledge_1', '知识库检索', 5,
 '{"label":"实习手册与工作日志库","topK":4}', '[]', '[{"id":"chunks","name":"chunks","label":"匹配条目","type":"array"}]',
 '{"id":"knowledge_1","type":"knowledge","position":{"x":460,"y":240},"data":{"input":[],"config":{"label":"实习手册与工作日志库","topK":4},"output":[{"id":"chunks","name":"chunks","label":"匹配条目","type":"array"}]}}', 0, 0, '管理员', 1),
(1001, 1, 'llm_1', '大模型推理', 2,
 '{"label":"DeepSeek 实习导师建议","platform":"DeepSeek","model":"deepseek-flash","temperature":0.7}', '[]', '[{"id":"result","name":"result","label":"指导建议","type":"string"}]',
 '{"id":"llm_1","type":"llm","position":{"x":840,"y":240},"data":{"input":[],"config":{"label":"DeepSeek 实习导师建议","platform":"DeepSeek","model":"deepseek-flash","temperature":0.7},"output":[{"id":"result","name":"result","label":"指导建议","type":"string"}]}}', 0, 0, '管理员', 1),
(1001, 1, 'reply_1', '回复', 3,
 '{"label":"导师解答输出"}', '[]', '[]',
 '{"id":"reply_1","type":"reply","position":{"x":1220,"y":240},"data":{"input":[],"config":{"label":"导师解答输出"},"output":[]}}', 0, 0, '管理员', 1);

INSERT INTO kb_flow_edge (workspace_id, bot_id, source_node_uuid, target_node_uuid, source_handle, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 1, 'start_1', 'knowledge_1', NULL, '{"id":"e-1","type":"default","source":"start_1","target":"knowledge_1"}', 0, 0, '管理员', 1),
(1001, 1, 'knowledge_1', 'llm_1', NULL, '{"id":"e-2","type":"default","source":"knowledge_1","target":"llm_1"}', 0, 0, '管理员', 1),
(1001, 1, 'llm_1', 'reply_1', NULL, '{"id":"e-3","type":"default","source":"llm_1","target":"reply_1"}', 0, 0, '管理员', 1);


-- ==============================================================================
-- 5. Bot 2: 代码审查助手 (Agent, type=2)
-- ==============================================================================
INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 2, 'start_1', '开始', 1,
 '{"label":"待审代码提交"}', '[]', '[{"id":"code","name":"code","label":"源码片段","type":"string"}]',
 '{"id":"start_1","type":"start","position":{"x":80,"y":240},"data":{"input":[],"config":{"label":"待审代码提交"},"output":[{"id":"code","name":"code","label":"源码片段","type":"string"}]}}', 0, 0, '管理员', 1),
(1001, 2, 'tool_1', '工具调用', 7,
 '{"label":"AST语法与Lint扫描工具","toolName":"ast_lint_analyzer","toolSource":"custom"}', '[]', '[{"id":"lint_errors","name":"lint_errors","label":"Lint诊断","type":"array"}]',
 '{"id":"tool_1","type":"tool","position":{"x":460,"y":240},"data":{"input":[],"config":{"label":"AST语法与Lint扫描工具","toolName":"ast_lint_analyzer","toolSource":"custom"},"output":[{"id":"lint_errors","name":"lint_errors","label":"Lint诊断","type":"array"}]}}', 0, 0, '管理员', 1),
(1001, 2, 'knowledge_1', '知识库检索', 5,
 '{"label":"工程质量规范库","topK":3}', '[]', '[{"id":"standards","name":"standards","label":"编码规约","type":"array"}]',
 '{"id":"knowledge_1","type":"knowledge","position":{"x":840,"y":240},"data":{"input":[],"config":{"label":"工程质量规范库","topK":3},"output":[{"id":"standards","name":"standards","label":"编码规约","type":"array"}]}}', 0, 0, '管理员', 1),
(1001, 2, 'llm_1', '大模型推理', 2,
 '{"label":"DeepSeek 代码审查分析","platform":"DeepSeek","model":"deepseek-flash","temperature":0.2}', '[]', '[{"id":"review_report","name":"review_report","label":"审查报告","type":"string"}]',
 '{"id":"llm_1","type":"llm","position":{"x":1220,"y":240},"data":{"input":[],"config":{"label":"DeepSeek 代码审查分析","platform":"DeepSeek","model":"deepseek-flash","temperature":0.2},"output":[{"id":"review_report","name":"review_report","label":"审查报告","type":"string"}]}}', 0, 0, '管理员', 1),
(1001, 2, 'reply_1', '回复', 3,
 '{"label":"审查报告输出"}', '[]', '[]',
 '{"id":"reply_1","type":"reply","position":{"x":1600,"y":240},"data":{"input":[],"config":{"label":"审查报告输出"},"output":[]}}', 0, 0, '管理员', 1);

INSERT INTO kb_flow_edge (workspace_id, bot_id, source_node_uuid, target_node_uuid, source_handle, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 2, 'start_1', 'tool_1', NULL, '{"id":"e-1","type":"default","source":"start_1","target":"tool_1"}', 0, 0, '管理员', 1),
(1001, 2, 'tool_1', 'knowledge_1', NULL, '{"id":"e-2","type":"default","source":"tool_1","target":"knowledge_1"}', 0, 0, '管理员', 1),
(1001, 2, 'knowledge_1', 'llm_1', NULL, '{"id":"e-3","type":"default","source":"knowledge_1","target":"llm_1"}', 0, 0, '管理员', 1),
(1001, 2, 'llm_1', 'reply_1', NULL, '{"id":"e-4","type":"default","source":"llm_1","target":"reply_1"}', 0, 0, '管理员', 1);


-- ==============================================================================
-- 6. Bot 5: 数据分析师 (Agent, type=2)
-- ==============================================================================
INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 5, 'start_1', '开始', 1,
 '{"label":"业务指标分析需求"}', '[]', '[{"id":"query","name":"query","label":"分析目标","type":"string"}]',
 '{"id":"start_1","type":"start","position":{"x":80,"y":240},"data":{"input":[],"config":{"label":"业务指标分析需求"},"output":[{"id":"query","name":"query","label":"分析目标","type":"string"}]}}', 0, 0, '管理员', 1),
(1001, 5, 'knowledge_1', '知识库检索', 5,
 '{"label":"数仓元数据与指标字典","topK":4}', '[]', '[{"id":"schemas","name":"schemas","label":"表结构定义","type":"array"}]',
 '{"id":"knowledge_1","type":"knowledge","position":{"x":460,"y":240},"data":{"input":[],"config":{"label":"数仓元数据与指标字典","topK":4},"output":[{"id":"schemas","name":"schemas","label":"表结构定义","type":"array"}]}}', 0, 0, '管理员', 1),
(1001, 5, 'tool_1', '工具调用', 7,
 '{"label":"安全只读SQL生成与执行","toolName":"safe_sql_executor","toolSource":"custom"}', '[]', '[{"id":"query_data","name":"query_data","label":"查询结果集","type":"array"}]',
 '{"id":"tool_1","type":"tool","position":{"x":840,"y":240},"data":{"input":[],"config":{"label":"安全只读SQL生成与执行","toolName":"safe_sql_executor","toolSource":"custom"},"output":[{"id":"query_data","name":"query_data","label":"查询结果集","type":"array"}]}}', 0, 0, '管理员', 1),
(1001, 5, 'llm_1', '大模型推理', 2,
 '{"label":"DeepSeek 业务洞察与统计归因","platform":"DeepSeek","model":"deepseek-flash","temperature":0.4}', '[]', '[{"id":"insights","name":"insights","label":"归因洞察","type":"string"}]',
 '{"id":"llm_1","type":"llm","position":{"x":1220,"y":240},"data":{"input":[],"config":{"label":"DeepSeek 业务洞察与统计归因","platform":"DeepSeek","model":"deepseek-flash","temperature":0.4},"output":[{"id":"insights","name":"insights","label":"归因洞察","type":"string"}]}}', 0, 0, '管理员', 1),
(1001, 5, 'reply_1', '回复', 3,
 '{"label":"指标看板与结论呈现"}', '[]', '[]',
 '{"id":"reply_1","type":"reply","position":{"x":1600,"y":240},"data":{"input":[],"config":{"label":"指标看板与结论呈现"},"output":[]}}', 0, 0, '管理员', 1);

INSERT INTO kb_flow_edge (workspace_id, bot_id, source_node_uuid, target_node_uuid, source_handle, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 5, 'start_1', 'knowledge_1', NULL, '{"id":"e-1","type":"default","source":"start_1","target":"knowledge_1"}', 0, 0, '管理员', 1),
(1001, 5, 'knowledge_1', 'tool_1', NULL, '{"id":"e-2","type":"default","source":"knowledge_1","target":"tool_1"}', 0, 0, '管理员', 1),
(1001, 5, 'tool_1', 'llm_1', NULL, '{"id":"e-3","type":"default","source":"tool_1","target":"llm_1"}', 0, 0, '管理员', 1),
(1001, 5, 'llm_1', 'reply_1', NULL, '{"id":"e-4","type":"default","source":"llm_1","target":"reply_1"}', 0, 0, '管理员', 1);


-- ==============================================================================
-- 7. Bot 6: 常州工学院总评审查助手 (Agent, type=2)
-- ==============================================================================
INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 6, 'start_1', '开始', 1,
 '{"label":"学生加分申请材料"}', '[]', '[{"id":"query","name":"query","label":"申报条目","type":"string"}]',
 '{"id":"start_1","type":"start","position":{"x":80,"y":240},"data":{"input":[],"config":{"label":"学生加分申请材料"},"output":[{"id":"query","name":"query","label":"申报条目","type":"string"}]}}', 0, 0, '管理员', 1),
(1001, 6, 'knowledge_1', '知识库检索', 5,
 '{"label":"常州工学院总评加分评审细则","topK":5}', '[]', '[{"id":"rules","name":"rules","label":"加分条款","type":"array"}]',
 '{"id":"knowledge_1","type":"knowledge","position":{"x":460,"y":240},"data":{"input":[],"config":{"label":"常州工学院总评加分评审细则","topK":5},"output":[{"id":"rules","name":"rules","label":"加分条款","type":"array"}]}}', 0, 0, '管理员', 1),
(1001, 6, 'llm_1', '大模型推理', 2,
 '{"label":"DeepSeek 规则匹配与加分计算","platform":"DeepSeek","model":"deepseek-flash","temperature":0.1}', '[]', '[{"id":"score_detail","name":"score_detail","label":"核算明细","type":"string"}]',
 '{"id":"llm_1","type":"llm","position":{"x":840,"y":240},"data":{"input":[],"config":{"label":"DeepSeek 规则匹配与加分计算","platform":"DeepSeek","model":"deepseek-flash","temperature":0.1},"output":[{"id":"score_detail","name":"score_detail","label":"核算明细","type":"string"}]}}', 0, 0, '管理员', 1),
(1001, 6, 'reply_1', '回复', 3,
 '{"label":"总评加分审核报告"}', '[]', '[]',
 '{"id":"reply_1","type":"reply","position":{"x":1220,"y":240},"data":{"input":[],"config":{"label":"总评加分审核报告"},"output":[]}}', 0, 0, '管理员', 1);

INSERT INTO kb_flow_edge (workspace_id, bot_id, source_node_uuid, target_node_uuid, source_handle, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 6, 'start_1', 'knowledge_1', NULL, '{"id":"e-1","type":"default","source":"start_1","target":"knowledge_1"}', 0, 0, '管理员', 1),
(1001, 6, 'knowledge_1', 'llm_1', NULL, '{"id":"e-2","type":"default","source":"knowledge_1","target":"llm_1"}', 0, 0, '管理员', 1),
(1001, 6, 'llm_1', 'reply_1', NULL, '{"id":"e-3","type":"default","source":"llm_1","target":"reply_1"}', 0, 0, '管理员', 1);


-- ==============================================================================
-- 8. Bot 7: 课设报告检索助手 (Agent, type=2)
-- ==============================================================================
INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 7, 'start_1', '开始', 1,
 '{"label":"课设报告检索条件"}', '[]', '[{"id":"query","name":"query","label":"学号/题目","type":"string"}]',
 '{"id":"start_1","type":"start","position":{"x":80,"y":240},"data":{"input":[],"config":{"label":"课设报告检索条件"},"output":[{"id":"query","name":"query","label":"学号/题目","type":"string"}]}}', 0, 0, '管理员', 1),
(1001, 7, 'knowledge_1', '知识库检索', 5,
 '{"label":"学生课设报告文档库","topK":4}', '[]', '[{"id":"reports","name":"reports","label":"已交报告","type":"array"}]',
 '{"id":"knowledge_1","type":"knowledge","position":{"x":460,"y":240},"data":{"input":[],"config":{"label":"学生课设报告文档库","topK":4},"output":[{"id":"reports","name":"reports","label":"已交报告","type":"array"}]}}', 0, 0, '管理员', 1),
(1001, 7, 'llm_1', '大模型推理', 2,
 '{"label":"DeepSeek 报告完整性与质量核验","platform":"DeepSeek","model":"deepseek-flash","temperature":0.3}', '[]', '[{"id":"progress_report","name":"progress_report","label":"进度评估","type":"string"}]',
 '{"id":"llm_1","type":"llm","position":{"x":840,"y":240},"data":{"input":[],"config":{"label":"DeepSeek 报告完整性与质量核验","platform":"DeepSeek","model":"deepseek-flash","temperature":0.3},"output":[{"id":"progress_report","name":"progress_report","label":"进度评估","type":"string"}]}}', 0, 0, '管理员', 1),
(1001, 7, 'reply_1', '回复', 3,
 '{"label":"课设批阅与完成度汇总"}', '[]', '[]',
 '{"id":"reply_1","type":"reply","position":{"x":1220,"y":240},"data":{"input":[],"config":{"label":"课设批阅与完成度汇总"},"output":[]}}', 0, 0, '管理员', 1);

INSERT INTO kb_flow_edge (workspace_id, bot_id, source_node_uuid, target_node_uuid, source_handle, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 7, 'start_1', 'knowledge_1', NULL, '{"id":"e-1","type":"default","source":"start_1","target":"knowledge_1"}', 0, 0, '管理员', 1),
(1001, 7, 'knowledge_1', 'llm_1', NULL, '{"id":"e-2","type":"default","source":"knowledge_1","target":"llm_1"}', 0, 0, '管理员', 1),
(1001, 7, 'llm_1', 'reply_1', NULL, '{"id":"e-3","type":"default","source":"llm_1","target":"reply_1"}', 0, 0, '管理员', 1);


-- ==============================================================================
-- 9. Bot 8: 综合测试助手 (Agent, type=2)
-- ==============================================================================
INSERT INTO kb_flow_node (workspace_id, bot_id, uuid, name, type, config, input, output, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 8, 'start_1', '开始', 1,
 '{"label":"综合功能测试指令"}', '[]', '[{"id":"query","name":"query","label":"测试目标","type":"string"}]',
 '{"id":"start_1","type":"start","position":{"x":80,"y":240},"data":{"input":[],"config":{"label":"综合功能测试指令"},"output":[{"id":"query","name":"query","label":"测试目标","type":"string"}]}}', 0, 0, '管理员', 1),
(1001, 8, 'knowledge_1', '知识库检索', 5,
 '{"label":"全链路测试用例与历史缺陷库","topK":4}', '[]', '[{"id":"cases","name":"cases","label":"用例切片","type":"array"}]',
 '{"id":"knowledge_1","type":"knowledge","position":{"x":460,"y":240},"data":{"input":[],"config":{"label":"全链路测试用例与历史缺陷库","topK":4},"output":[{"id":"cases","name":"cases","label":"用例切片","type":"array"}]}}', 0, 0, '管理员', 1),
(1001, 8, 'tool_1', '工具调用', 7,
 '{"label":"知识图谱实体抽取与契约断言","toolName":"graph_entity_extractor","toolSource":"custom"}', '[]', '[{"id":"assertions","name":"assertions","label":"断言凭单","type":"string"}]',
 '{"id":"tool_1","type":"tool","position":{"x":840,"y":240},"data":{"input":[],"config":{"label":"知识图谱实体抽取与契约断言","toolName":"graph_entity_extractor","toolSource":"custom"},"output":[{"id":"assertions","name":"assertions","label":"断言凭单","type":"string"}]}}', 0, 0, '管理员', 1),
(1001, 8, 'llm_1', '大模型推理', 2,
 '{"label":"DeepSeek 质量研判与覆盖度评定","platform":"DeepSeek","model":"deepseek-flash","temperature":0.2}', '[]', '[{"id":"eval_report","name":"eval_report","label":"测试研报","type":"string"}]',
 '{"id":"llm_1","type":"llm","position":{"x":1220,"y":240},"data":{"input":[],"config":{"label":"DeepSeek 质量研判与覆盖度评定","platform":"DeepSeek","model":"deepseek-flash","temperature":0.2},"output":[{"id":"eval_report","name":"eval_report","label":"测试研报","type":"string"}]}}', 0, 0, '管理员', 1),
(1001, 8, 'reply_1', '回复', 3,
 '{"label":"综合测试通过报告"}', '[]', '[]',
 '{"id":"reply_1","type":"reply","position":{"x":1600,"y":240},"data":{"input":[],"config":{"label":"综合测试通过报告"},"output":[]}}', 0, 0, '管理员', 1);

INSERT INTO kb_flow_edge (workspace_id, bot_id, source_node_uuid, target_node_uuid, source_handle, style, valid_flag, del_flag, create_by, creator_id)
VALUES
(1001, 8, 'start_1', 'knowledge_1', NULL, '{"id":"e-1","type":"default","source":"start_1","target":"knowledge_1"}', 0, 0, '管理员', 1),
(1001, 8, 'knowledge_1', 'tool_1', NULL, '{"id":"e-2","type":"default","source":"knowledge_1","target":"tool_1"}', 0, 0, '管理员', 1),
(1001, 8, 'tool_1', 'llm_1', NULL, '{"id":"e-3","type":"default","source":"tool_1","target":"llm_1"}', 0, 0, '管理员', 1),
(1001, 8, 'llm_1', 'reply_1', NULL, '{"id":"e-4","type":"default","source":"llm_1","target":"reply_1"}', 0, 0, '管理员', 1);

COMMIT;
