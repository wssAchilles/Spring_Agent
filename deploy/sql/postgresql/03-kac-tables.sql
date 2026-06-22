-- ============================================================================
-- KAC Tables and Sample Data
-- ============================================================================

-- 应用表
CREATE TABLE IF NOT EXISTS kac_apply (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  description TEXT,
  icon VARCHAR(256),
  status SMALLINT DEFAULT 1,
  type VARCHAR(64),
  tags VARCHAR(256),
  workspace_id BIGINT,
  config JSONB,
  valid_flag SMALLINT DEFAULT 0,
  del_flag SMALLINT DEFAULT 0,
  create_by VARCHAR(32),
  create_time TIMESTAMP DEFAULT NOW(),
  update_by VARCHAR(32),
  update_time TIMESTAMP DEFAULT NOW()
);

-- 解决方案表
CREATE TABLE IF NOT EXISTS kac_solution (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  description TEXT,
  icon VARCHAR(256),
  status SMALLINT DEFAULT 1,
  type VARCHAR(64),
  workspace_id BIGINT,
  valid_flag SMALLINT DEFAULT 0,
  del_flag SMALLINT DEFAULT 0,
  create_by VARCHAR(32),
  create_time TIMESTAMP DEFAULT NOW(),
  update_by VARCHAR(32),
  update_time TIMESTAMP DEFAULT NOW()
);

-- 插件表
CREATE TABLE IF NOT EXISTS kac_plugin (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  description TEXT,
  type VARCHAR(64),
  config JSONB,
  status SMALLINT DEFAULT 1,
  workspace_id BIGINT,
  valid_flag SMALLINT DEFAULT 0,
  del_flag SMALLINT DEFAULT 0,
  create_by VARCHAR(32),
  create_time TIMESTAMP DEFAULT NOW(),
  update_by VARCHAR(32),
  update_time TIMESTAMP DEFAULT NOW()
);

-- 知识图谱节点表
CREATE TABLE IF NOT EXISTS kg_node (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT,
  label VARCHAR(128) NOT NULL,
  type VARCHAR(64),
  properties JSONB,
  valid_flag SMALLINT DEFAULT 0,
  del_flag SMALLINT DEFAULT 0,
  create_by VARCHAR(32),
  create_time TIMESTAMP DEFAULT NOW()
);

-- 知识图谱边表
CREATE TABLE IF NOT EXISTS kg_edge (
  id BIGSERIAL PRIMARY KEY,
  workspace_id BIGINT,
  source_id BIGINT NOT NULL,
  target_id BIGINT NOT NULL,
  label VARCHAR(128),
  properties JSONB,
  valid_flag SMALLINT DEFAULT 0,
  del_flag SMALLINT DEFAULT 0,
  create_by VARCHAR(32),
  create_time TIMESTAMP DEFAULT NOW()
);

-- ============================================================================
-- Sample Data: kac_apply (10 applications)
-- ============================================================================

INSERT INTO kac_apply (id, name, description, icon, status, type, tags, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (1, '文章编写', '文章编写插件是一类旨在辅助用户更高效、更高质量地完成文本创作任务的智能工具', 'Edit', 1, '写作', '[{"name":"写作"},{"name":"文章"}]', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_apply (id, name, description, icon, status, type, tags, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (2, '批量检索', '支持一次性上传多个查询条件并行处理，汇总输出结果，大幅提升效率', 'Search', 1, '效率', '[{"name":"效率"},{"name":"工具"}]', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_apply (id, name, description, icon, status, type, tags, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (3, '精确检索', '严格字符匹配，精准查找代码、条款或参数，无模糊干扰', 'Document', 1, '搜索', '[{"name":"搜索"},{"name":"工具"}]', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_apply (id, name, description, icon, status, type, tags, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (4, '实体关系检索', '智能识别实体与深层关系，助力知识图谱与情报分析', 'Connection', 1, '分析', '[{"name":"分析"},{"name":"数据"}]', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_apply (id, name, description, icon, status, type, tags, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (5, '语义检索', '利用深度学习理解查询意图与上下文，突破关键词匹配限制', 'Aim', 1, '搜索', '[{"name":"搜索"},{"name":"AI"}]', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_apply (id, name, description, icon, status, type, tags, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (6, '知识问答', '基于海量数据理解并回答各类事实性或解释性问题，提供准确简洁的答案', 'ChatDotRound', 1, '问答', '[{"name":"问答"},{"name":"知识"}]', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_apply (id, name, description, icon, status, type, tags, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (7, '模板报告生成', '提供多场景标准模板，引导填充并自动排版，确保企业级文档专业规范', 'Document', 1, '模板', '[{"name":"模板"},{"name":"文档"}]', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_apply (id, name, description, icon, status, type, tags, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (8, '日报周报生成', '简化周期性工作汇报撰写，输入关键事项，系统自动扩展为结构完整、逻辑清晰的报告', 'Calendar', 1, '写作', '[{"name":"写作"},{"name":"办公"}]', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_apply (id, name, description, icon, status, type, tags, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (9, '数据分析助手', '自动清洗、整理和分析数据，生成可视化图表和洞察报告', 'DataAnalysis', 1, '分析', '[{"name":"分析"},{"name":"数据"}]', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_apply (id, name, description, icon, status, type, tags, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (10, '智能摘要', '自动提取长文档的核心信息，生成简洁准确的摘要', 'Document', 1, '效率', '[{"name":"效率"},{"name":"AI"}]', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

-- ============================================================================
-- Sample Data: kac_solution (5 solutions)
-- ============================================================================

INSERT INTO kac_solution (id, name, description, icon, status, type, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (1, 'Flutter 开发知识体系', '涵盖 Flutter 框架、Dart 语言、状态管理、UI 组件等全方位开发知识', 'Monitor', 1, '技术', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_solution (id, name, description, icon, status, type, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (2, '企业文档管理方案', '提供文档分类、版本控制、权限管理、全文检索等企业级文档管理能力', 'Folder', 1, '管理', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_solution (id, name, description, icon, status, type, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (3, 'AI 研究助手', '集成文献检索、知识图谱、智能问答等功能，助力科研工作高效开展', 'Cpu', 1, 'AI', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_solution (id, name, description, icon, status, type, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (4, '数据治理平台', '提供数据质量管理、元数据管理、数据标准管理等数据治理核心能力', 'DataLine', 1, '数据', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_solution (id, name, description, icon, status, type, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (5, '智能客服方案', '基于大语言模型的智能客服系统，支持多轮对话、知识库问答、工单管理', 'Service', 1, '服务', 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

-- ============================================================================
-- Sample Data: kac_plugin (5 plugins)
-- ============================================================================

INSERT INTO kac_plugin (id, name, description, type, config, status, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (1, 'PDF 解析器', '支持 PDF 文档的内容提取、格式解析和文本识别', '文档处理', '{"supportedFormats": ["pdf"], "maxFileSize": "50MB"}', 1, 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_plugin (id, name, description, type, config, status, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (2, 'Word 文档转换', '支持 Word 文档与多种格式之间的相互转换', '文档处理', '{"supportedFormats": ["docx", "doc"], "outputFormats": ["pdf", "html", "md"]}', 1, 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_plugin (id, name, description, type, config, status, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (3, '表格数据提取', '从 Excel、CSV 等表格文件中提取和解析结构化数据', '数据处理', '{"supportedFormats": ["xlsx", "xls", "csv"], "maxRows": 1000000}', 1, 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_plugin (id, name, description, type, config, status, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (4, '图片 OCR 识别', '支持图片中的文字识别和提取，支持多语言', '图像处理', '{"supportedFormats": ["png", "jpg", "jpeg", "bmp"], "languages": ["zh", "en"]}', 1, 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO kac_plugin (id, name, description, type, config, status, workspace_id, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (5, '音频转文字', '将音频文件转换为文字，支持多种音频格式和语言', '音频处理', '{"supportedFormats": ["mp3", "wav", "m4a", "flac"], "maxDuration": "2h"}', 1, 1001, 0, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

-- ============================================================================
-- Sample Data: kg_node (15 nodes - Flutter technology concepts)
-- ============================================================================

INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (1, 1001, 'Flutter', 'technology', '{"description": "Google的跨平台UI框架", "category": "framework"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (2, 1001, 'Dart', 'technology', '{"description": "Flutter的编程语言", "category": "language"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (3, 1001, 'Widget', 'concept', '{"description": "Flutter UI构建块", "category": "core"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (4, 1001, 'StatefulWidget', 'concept', '{"description": "有状态的Widget", "category": "core"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (5, 1001, 'StatelessWidget', 'concept', '{"description": "无状态的Widget", "category": "core"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (6, 1001, 'Provider', 'technology', '{"description": "Flutter状态管理方案", "category": "library"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (7, 1001, 'Riverpod', 'technology', '{"description": "Provider的改进版本", "category": "library"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (8, 1001, 'Navigator', 'concept', '{"description": "Flutter路由导航", "category": "core"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (9, 1001, 'Material Design', 'design', '{"description": "Google设计语言", "category": "design"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (10, 1001, 'Cupertino', 'design', '{"description": "iOS风格组件", "category": "design"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (11, 1001, 'Firebase', 'technology', '{"description": "Google后端服务", "category": "backend"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (12, 1001, 'REST API', 'concept', '{"description": "RESTful接口设计", "category": "architecture"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (13, 1001, 'JSON Serialization', 'concept', '{"description": "JSON序列化", "category": "data"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (14, 1001, 'Testing', 'concept', '{"description": "测试框架", "category": "quality"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (15, 1001, 'Deployment', 'concept', '{"description": "应用部署", "category": "devops"}');

-- ============================================================================
-- Sample Data: kg_edge (20 edges - relationships)
-- ============================================================================

INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (1, 1001, 1, 2, '使用', '{"description": "Flutter使用Dart语言"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (2, 1001, 1, 3, '包含', '{"description": "Flutter包含Widget组件"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (3, 1001, 3, 4, '继承', '{"description": "StatefulWidget继承Widget"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (4, 1001, 3, 5, '继承', '{"description": "StatelessWidget继承Widget"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (5, 1001, 1, 6, '支持', '{"description": "Flutter支持Provider状态管理"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (6, 1001, 1, 7, '支持', '{"description": "Flutter支持Riverpod状态管理"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (7, 1001, 1, 8, '包含', '{"description": "Flutter包含Navigator路由"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (8, 1001, 1, 9, '遵循', '{"description": "Flutter遵循Material Design"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (9, 1001, 1, 10, '支持', '{"description": "Flutter支持Cupertino风格"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (10, 1001, 1, 11, '集成', '{"description": "Flutter集成Firebase"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (11, 1001, 1, 12, '调用', '{"description": "Flutter调用REST API"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (12, 1001, 2, 13, '支持', '{"description": "Dart支持JSON Serialization"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (13, 1001, 1, 14, '支持', '{"description": "Flutter支持Testing"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (14, 1001, 1, 15, '部署', '{"description": "Flutter应用部署"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (15, 1001, 6, 4, '管理状态', '{"description": "Provider管理StatefulWidget状态"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (16, 1001, 7, 6, '替代', '{"description": "Riverpod是Provider的替代方案"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (17, 1001, 8, 3, '导航', '{"description": "Navigator导航Widget页面"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (18, 1001, 11, 12, '使用', '{"description": "Firebase使用REST API"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (19, 1001, 14, 3, '测试', '{"description": "Testing测试Widget组件"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (20, 1001, 15, 1, '发布', '{"description": "Deployment发布Flutter应用"}');

-- ============================================================================
-- Reset sequences to avoid conflicts
-- ============================================================================

SELECT setval('kac_apply_id_seq', 100);
SELECT setval('kac_solution_id_seq', 100);
SELECT setval('kac_plugin_id_seq', 100);
SELECT setval('kg_node_id_seq', 100);
SELECT setval('kg_edge_id_seq', 100);
