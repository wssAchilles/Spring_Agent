-- ============================================================================
-- 04-kac-engine-upgrade.sql
-- 应用中心执行与编排引擎升级 DDL
-- ============================================================================

-- 1. 扩充主表 kac_apply 执行与版本控制字段
ALTER TABLE kac_apply 
  ADD COLUMN IF NOT EXISTS published_version_id BIGINT,
  ADD COLUMN IF NOT EXISTS execution_mode VARCHAR(32) DEFAULT 'DIRECT_PROMPT_RAG',
  ADD COLUMN IF NOT EXISTS input_schema JSONB DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS output_schema JSONB DEFAULT '{"outputType":"MARKDOWN"}'::jsonb,
  ADD COLUMN IF NOT EXISTS prompt_template TEXT,
  ADD COLUMN IF NOT EXISTS execution_config JSONB DEFAULT '{}'::jsonb;

COMMENT ON COLUMN kac_apply.published_version_id IS '当前发布的版本ID';
COMMENT ON COLUMN kac_apply.execution_mode IS '执行模式: DIRECT_PROMPT_RAG, HERMES_AGENT, HERMES_DAG';
COMMENT ON COLUMN kac_apply.input_schema IS '动态入参表单Schema定义';
COMMENT ON COLUMN kac_apply.output_schema IS '出参Schema定义';
COMMENT ON COLUMN kac_apply.prompt_template IS '提示词模板';
COMMENT ON COLUMN kac_apply.execution_config IS '执行策略配置(模型超参、RAG参数、MCP工具绑定)';

-- 2. 应用版本快照表 (支持不可篡改发布快照与回滚)
CREATE TABLE IF NOT EXISTS kac_apply_version (
  id BIGSERIAL PRIMARY KEY,
  apply_id BIGINT NOT NULL,
  version_code VARCHAR(32) NOT NULL,
  version_title VARCHAR(128),
  version_desc TEXT,
  contract_snapshot JSONB NOT NULL,
  status SMALLINT DEFAULT 1,
  creator_id BIGINT,
  create_by VARCHAR(32),
  create_time TIMESTAMP DEFAULT NOW(),
  valid_flag SMALLINT DEFAULT 0,
  del_flag SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_kac_apply_version_apply_id ON kac_apply_version(apply_id);

-- 3. 应用执行日志与凭单审计表 (支持全链路追踪与回溯)
CREATE TABLE IF NOT EXISTS kac_apply_execution_log (
  id BIGSERIAL PRIMARY KEY,
  apply_id BIGINT NOT NULL,
  version_id BIGINT,
  workspace_id BIGINT NOT NULL,
  trace_id VARCHAR(64) NOT NULL,
  receipt_id VARCHAR(64) NOT NULL UNIQUE,
  execution_mode VARCHAR(32) NOT NULL,
  input_params JSONB NOT NULL,
  rendered_prompt TEXT,
  output_content TEXT,
  rag_sources JSONB DEFAULT '[]'::jsonb,
  tool_calls JSONB DEFAULT '[]'::jsonb,
  prompt_tokens INT DEFAULT 0,
  completion_tokens INT DEFAULT 0,
  total_tokens INT DEFAULT 0,
  latency_ms BIGINT DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  error_message TEXT,
  creator_id BIGINT,
  create_by VARCHAR(32),
  create_time TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_kac_exec_log_apply_id ON kac_apply_execution_log(apply_id);
CREATE INDEX IF NOT EXISTS idx_kac_exec_log_trace_id ON kac_apply_execution_log(trace_id);
CREATE INDEX IF NOT EXISTS idx_kac_exec_log_create_time ON kac_apply_execution_log(create_time DESC);

-- 4. 批量异步任务主表与明细表
CREATE TABLE IF NOT EXISTS kac_apply_batch_task (
  id BIGSERIAL PRIMARY KEY,
  apply_id BIGINT NOT NULL,
  version_id BIGINT,
  workspace_id BIGINT NOT NULL,
  task_name VARCHAR(128) NOT NULL,
  total_count INT DEFAULT 0,
  success_count INT DEFAULT 0,
  failure_count INT DEFAULT 0,
  status VARCHAR(32) DEFAULT 'PENDING',
  start_time TIMESTAMP,
  finish_time TIMESTAMP,
  error_summary TEXT,
  creator_id BIGINT,
  create_by VARCHAR(32),
  create_time TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS kac_apply_batch_task_item (
  id BIGSERIAL PRIMARY KEY,
  batch_task_id BIGINT NOT NULL,
  item_index INT NOT NULL,
  input_data JSONB NOT NULL,
  output_data TEXT,
  status VARCHAR(32) DEFAULT 'PENDING',
  latency_ms BIGINT DEFAULT 0,
  receipt_id VARCHAR(64),
  error_message TEXT,
  create_time TIMESTAMP DEFAULT NOW(),
  update_time TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_kac_batch_item_task_id ON kac_apply_batch_task_item(batch_task_id);

-- ============================================================================
-- 5. 初始化 10 个预置应用的结构化输入 Schema、Prompt 模板与配置
-- ============================================================================

-- 应用 1: 文章编写
UPDATE kac_apply SET 
  execution_mode = 'DIRECT_PROMPT_RAG',
  input_schema = '[
    {"field": "topic", "label": "文章主题", "type": "STRING", "required": true, "placeholder": "例如: 人工智能在企业知识管理中的应用趋势"},
    {"field": "style", "label": "文章体裁", "type": "SELECT", "required": true, "defaultValue": "科技前沿综述", "options": [{"label":"科技前沿综述","value":"科技前沿综述"},{"label":"深度商业洞察","value":"深度商业洞察"},{"label":"通俗科普指南","value":"通俗科普指南"},{"label":"企业新闻公报","value":"企业新闻公报"}]},
    {"field": "requirements", "label": "核心要点与诉求", "type": "TEXTAREA", "required": false, "placeholder": "请列出文章需重点阐述的核心观点、关键结构或目标受众"}
  ]'::jsonb,
  prompt_template = '你是一名资深行业专业内容创作者。请围绕主题《${topic}》，撰写一篇风格为【${style}】的高质量专业文章。\n\n【创作要求】\n${requirements}\n\n【知识背景】\n${knowledge_context}\n\n请输出结构完整、行文流畅、论证严密且富含洞察的文章。'
WHERE id = 1;

-- 应用 2: 批量检索
UPDATE kac_apply SET 
  execution_mode = 'DIRECT_PROMPT_RAG',
  input_schema = '[
    {"field": "queries", "label": "批量查询列表 (每行一个)", "type": "TEXTAREA", "required": true, "placeholder": "Flutter 状态管理最佳实践\nJava 21 虚拟线程性能基准\n向量数据库混合检索算法"}
  ]'::jsonb,
  prompt_template = '请对以下多项查询条件进行多维度并行处理和结构化汇总输出：\n\n【查询条件列表】\n${queries}\n\n【检索背景】\n${knowledge_context}\n\n请针对每个查询项给出精确、提炼的核心结论，并最后给出综合对比矩阵。'
WHERE id = 2;

-- 应用 3: 精确检索
UPDATE kac_apply SET 
  execution_mode = 'DIRECT_PROMPT_RAG',
  input_schema = '[
    {"field": "keyword", "label": "目标关键词或代码/参数标识", "type": "STRING", "required": true, "placeholder": "例如: CrossKbScoreCalibrator 或 错误码 401"},
    {"field": "scope", "label": "限定检索范围", "type": "STRING", "required": false, "placeholder": "例如: 架构设计文档、核心接口规范"}
  ]'::jsonb,
  prompt_template = '你是一名严格的技术文档审查官。请依据知识库背景，针对【${keyword}】进行严格精确的查找与条款说明。\n\n检索范围限定: ${scope}\n\n【参考知识库】\n${knowledge_context}\n\n要求: 无模糊猜测，精确引用原文段落与参数定义，指出具体条款或实现路径。'
WHERE id = 3;

-- 应用 4: 实体关系检索
UPDATE kac_apply SET 
  execution_mode = 'DIRECT_PROMPT_RAG',
  input_schema = '[
    {"field": "entity", "label": "核心实体名称", "type": "STRING", "required": true, "placeholder": "例如: Flutter 或 Provider"},
    {"field": "relationType", "label": "关联网度与类型", "type": "STRING", "required": false, "placeholder": "例如: 继承/依赖/关联关系"}
  ]'::jsonb,
  prompt_template = '基于知识图谱与领域实体背景，请深度剖析核心实体【${entity}】的关联网络。\n\n关注关系类型: ${relationType}\n\n【图谱上下文】\n${knowledge_context}\n\n请以图谱实体、上下游关联、属性特征三个维度清晰列出结构化关系报告。'
WHERE id = 4;

-- 应用 5: 语义检索
UPDATE kac_apply SET 
  execution_mode = 'DIRECT_PROMPT_RAG',
  input_schema = '[
    {"field": "query", "label": "意图描述或长文本问题", "type": "TEXTAREA", "required": true, "placeholder": "例如: 系统如何在内存中处理超大长文档的流式解析而不会导致堆溢出？"}
  ]'::jsonb,
  prompt_template = '用户提出了以下具有深层意图的检索请求：\n"${query}"\n\n【相关召回切片】\n${knowledge_context}\n\n请利用深度语义理解，突破字面关键词限制，深入提炼回答用户的核心疑问，并标注关键依据来源。'
WHERE id = 5;

-- 应用 6: 知识问答
UPDATE kac_apply SET 
  execution_mode = 'DIRECT_PROMPT_RAG',
  input_schema = '[
    {"field": "question", "label": "业务或技术问题", "type": "TEXTAREA", "required": true, "placeholder": "例如: 本平台四大攻坚支柱分别是什么？各有什么核心设计原则？"}
  ]'::jsonb,
  prompt_template = '请基于以下可靠的知识库背景，准确、清晰、严谨地回答用户的问题：\n\n问题: ${question}\n\n【知识库背景信息】\n${knowledge_context}\n\n如果背景信息中未包含相关事实，请如实说明，严禁无依据捏造。'
WHERE id = 6;

-- 应用 7: 模板报告生成
UPDATE kac_apply SET 
  execution_mode = 'DIRECT_PROMPT_RAG',
  input_schema = '[
    {"field": "reportType", "label": "报告类型", "type": "SELECT", "required": true, "defaultValue": "技术架构演进报告", "options": [{"label":"技术架构演进报告","value":"技术架构演进报告"},{"label":"项目复盘与总结报告","value":"项目复盘与总结报告"},{"label":"产品需求规格说明书(PRD)","value":"产品需求规格说明书(PRD)"},{"label":"安全合规审计报告","value":"安全合规审计报告"}]},
    {"field": "keyData", "label": "核心事实与数据要点", "type": "TEXTAREA", "required": true, "placeholder": "请提供报告所需的关键数据指标、里程碑进展或核心决策依据"}
  ]'::jsonb,
  prompt_template = '你是一名资深企业级报告撰写专家。请按照【${reportType}】的标准专业模板规范，结合以下关键数据与知识背景，生成一份排版严谨、结构清晰的企业级报告：\n\n【核心输入数据】\n${keyData}\n\n【知识背景】\n${knowledge_context}\n\n请使用标准 Markdown 格式输出，包含背景概述、核心进展指标、关键风险及后续落地计划。'
WHERE id = 7;

-- 应用 8: 日报周报生成
UPDATE kac_apply SET 
  execution_mode = 'DIRECT_PROMPT_RAG',
  input_schema = '[
    {"field": "workItems", "label": "本周/本日完成事项", "type": "TEXTAREA", "required": true, "placeholder": "1. 修复模型市场 NPE 与数据库缺少字段问题\n2. 重构应用中心前后端交互架构\n3. 落地 SSE 流式打字机运行抽屉"},
    {"field": "challenges", "label": "遇到的困难与解决方案", "type": "TEXTAREA", "required": false, "placeholder": "如无请留空"},
    {"field": "nextPlan", "label": "下阶段工作计划", "type": "TEXTAREA", "required": false, "placeholder": "下一步重点推进批量运行与多智能体协同"}
  ]'::jsonb,
  prompt_template = '请将以下零散的研发/业务工作记录，整理并扩充为一份结构专业、语言精炼、逻辑清晰的周报汇报文档：\n\n【已完成工作】\n${workItems}\n\n【难点与突破】\n${challenges}\n\n【下一步规划】\n${nextPlan}\n\n请按“核心成果总结”、“工作明细进展”、“问题与协同支持”、“下周重点”四段式输出，语言具备工程师与管理者双重视角。'
WHERE id = 8;

-- 应用 9: 数据分析助手
UPDATE kac_apply SET 
  execution_mode = 'DIRECT_PROMPT_RAG',
  input_schema = '[
    {"field": "dataContent", "label": "待分析数据 (表格/JSON/文本摘要)", "type": "TEXTAREA", "required": true, "placeholder": "例如: 粘贴 CSV 数据行或近期业务量统计表"},
    {"field": "analysisGoal", "label": "分析目标与重点", "type": "STRING", "required": true, "placeholder": "例如: 分析系统请求成功率趋势与异常峰值原因"}
  ]'::jsonb,
  prompt_template = '你是一名资深数据科学家与业务智能分析师。请针对以下数据进行深度洞察分析：\n\n【输入数据】\n${dataContent}\n\n【分析目标】\n${analysisGoal}\n\n【领域参考知识】\n${knowledge_context}\n\n请输出：\n1. 关键指标总结与异常检测\n2. 根因假设推演\n3. 具体的业务或技术优化建议'
WHERE id = 9;

-- 应用 10: 智能摘要
UPDATE kac_apply SET 
  execution_mode = 'DIRECT_PROMPT_RAG',
  input_schema = '[
    {"field": "documentContent", "label": "待摘要的长文档内容", "type": "TEXTAREA", "required": true, "placeholder": "请粘贴长篇文章、技术规范或会议纪要文本..."},
    {"field": "summaryLength", "label": "摘要详略程度", "type": "SELECT", "required": true, "defaultValue": "中等 (300-500字精炼版)", "options": [{"label":"极简 (100字一句话总结)","value":"极简 (100字一句话总结)"},{"label":"中等 (300-500字精炼版)","value":"中等 (300-500字精炼版)"},{"label":"详尽 (保留二级小标题与核心论据)","value":"详尽 (保留二级小标题与核心论据)"}]}
  ]'::jsonb,
  prompt_template = '请对以下长文本进行结构化提炼与智能摘要：\n\n【详略要求】: ${summaryLength}\n\n【原文正文】:\n${documentContent}\n\n请提取：核心主题、3-5个关键结论要点、潜在影响或后续行动建议。要求文字干练、客观中立。'
WHERE id = 10;

-- ============================================================================
-- 6. 关联表 workspace_id 字段补齐
-- ============================================================================
ALTER TABLE kac_apply_knowledge ADD COLUMN IF NOT EXISTS workspace_id BIGINT;
ALTER TABLE kac_apply_graph ADD COLUMN IF NOT EXISTS workspace_id BIGINT;
ALTER TABLE kac_apply_bot ADD COLUMN IF NOT EXISTS workspace_id BIGINT;
ALTER TABLE kac_solution_apply ADD COLUMN IF NOT EXISTS workspace_id BIGINT;

ALTER TABLE kac_apply_execution_log ALTER COLUMN input_params TYPE TEXT;
ALTER TABLE kac_apply_execution_log ALTER COLUMN rag_sources TYPE TEXT;
ALTER TABLE kac_apply_execution_log ALTER COLUMN tool_calls TYPE TEXT;

