INSERT INTO system_notice (notice_title, notice_type, notice_content, status, create_by, create_time, update_by, update_time, remark)
SELECT '系统维护通知', '2', '系统将于本周六凌晨2:00-4:00进行例行维护，届时服务将暂停，请提前做好相关准备。', '0', 'admin', NOW(), 'admin', NOW(), '系统维护通知'
WHERE NOT EXISTS (SELECT 1 FROM system_notice WHERE notice_title = '系统维护通知');

INSERT INTO system_notice (notice_title, notice_type, notice_content, status, create_by, create_time, update_by, update_time, remark)
SELECT '数据安全规范', '2', '根据国家数据安全法要求，请所有用户在处理敏感数据时严格遵守数据分级保护制度，禁止将未脱敏数据用于测试环境。', '0', 'admin', NOW(), 'admin', NOW(), '数据安全规范'
WHERE NOT EXISTS (SELECT 1 FROM system_notice WHERE notice_title = '数据安全规范');

INSERT INTO system_notice (notice_title, notice_type, notice_content, status, create_by, create_time, update_by, update_time, remark)
SELECT '新功能上线：智能摘要', '1', '知识库新增智能摘要功能，支持自动为上传文档生成摘要，提升知识检索效率。', '0', 'admin', NOW(), 'admin', NOW(), '功能更新'
WHERE NOT EXISTS (SELECT 1 FROM system_notice WHERE notice_title = '新功能上线：智能摘要');

INSERT INTO system_notice (notice_title, notice_type, notice_content, status, create_by, create_time, update_by, update_time, remark)
SELECT '关于规范使用AI模型的通知', '2', '请各部门合理使用AI模型资源，避免频繁调用导致配额耗尽。如需大批量处理请联系管理员。', '0', 'admin', NOW(), 'admin', NOW(), '使用规范'
WHERE NOT EXISTS (SELECT 1 FROM system_notice WHERE notice_title = '关于规范使用AI模型的通知');

INSERT INTO system_notice (notice_title, notice_type, notice_content, status, create_by, create_time, update_by, update_time, remark)
SELECT '知识库权限调整说明', '1', '即日起知识库权限体系进行优化，新增"仅查看"角色，请各部门负责人重新分配团队成员权限。', '0', 'admin', NOW(), 'admin', NOW(), '权限调整'
WHERE NOT EXISTS (SELECT 1 FROM system_notice WHERE notice_title = '知识库权限调整说明');

INSERT INTO system_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, update_by, update_time, remark)
SELECT '向量索引重建', 'SYSTEM', 'vectorIndexTask.rebuild()', '0 0 1 ? * SUN', '3', '1', '1', 'admin', NOW(), 'admin', NOW(), '每周日凌晨1点重建向量索引'
WHERE NOT EXISTS (SELECT 1 FROM system_job WHERE job_name = '向量索引重建');

INSERT INTO system_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, update_by, update_time, remark)
SELECT '会话记录归档', 'SYSTEM', 'conversationTask.archive()', '0 30 2 * * ?', '3', '1', '0', 'admin', NOW(), 'admin', NOW(), '每日凌晨2:30归档过期会话'
WHERE NOT EXISTS (SELECT 1 FROM system_job WHERE job_name = '会话记录归档');

INSERT INTO system_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, update_by, update_time, remark)
SELECT '模型健康检查', 'AI', 'modelHealthTask.check()', '0 */30 * * * ?', '3', '1', '1', 'admin', NOW(), 'admin', NOW(), '每30分钟检查AI模型可用性'
WHERE NOT EXISTS (SELECT 1 FROM system_job WHERE job_name = '模型健康检查');

INSERT INTO system_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, update_by, update_time, remark)
SELECT '知识库统计更新', 'SYSTEM', 'kbStatsTask.update()', '0 0 5 * * ?', '3', '1', '1', 'admin', NOW(), 'admin', NOW(), '每日凌晨5点更新知识库统计数据'
WHERE NOT EXISTS (SELECT 1 FROM system_job WHERE job_name = '知识库统计更新');

INSERT INTO system_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 1, '通知', '1', 'sys_notice_type', '', '', 'Y', '0', 'admin', NOW(), ''
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'sys_notice_type' AND dict_value = '1');

INSERT INTO system_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 2, '公告', '2', 'sys_notice_type', '', '', 'N', '0', 'admin', NOW(), ''
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'sys_notice_type' AND dict_value = '2');

INSERT INTO system_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 1, '默认', 'DEFAULT', 'sys_job_group', '', '', 'Y', '0', 'admin', NOW(), ''
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'sys_job_group' AND dict_value = 'DEFAULT');

INSERT INTO system_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 2, '系统', 'SYSTEM', 'sys_job_group', '', '', 'N', '0', 'admin', NOW(), ''
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'sys_job_group' AND dict_value = 'SYSTEM');

INSERT INTO system_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 3, 'AI', 'AI', 'sys_job_group', '', '', 'N', '0', 'admin', NOW(), ''
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type = 'sys_job_group' AND dict_value = 'AI');

INSERT INTO system_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '主框架页-默认皮肤', 'sys.index.skinName', 'skin-blue', 'Y', 'admin', NOW(), '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'sys.index.skinName');

INSERT INTO system_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'Y', 'admin', NOW(), '初始化密码 123456'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'sys.user.initPassword');

INSERT INTO system_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '主框架页-侧边栏主题', 'sys.index.sideTheme', 'theme-dark', 'Y', 'admin', NOW(), '深色主题theme-dark，浅色主题theme-light'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'sys.index.sideTheme');

INSERT INTO system_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '知识库-默认Embedding模型', 'kmc.default.embedding', 'text-embedding-3-small', 'Y', 'admin', NOW(), '知识库默认使用的向量化模型'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'kmc.default.embedding');

INSERT INTO system_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '知识库-文档分段最大长度', 'kmc.doc.segment.maxSize', '1000', 'Y', 'admin', NOW(), '文档分段最大字符数'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'kmc.doc.segment.maxSize');

INSERT INTO system_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '知识库-文档分段重叠长度', 'kmc.doc.segment.overlap', '200', 'Y', 'admin', NOW(), '文档分段重叠字符数'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'kmc.doc.segment.overlap');

INSERT INTO system_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT 'AI-对话默认温度', 'ai.chat.defaultTemperature', '0.7', 'Y', 'admin', NOW(), 'AI对话默认温度参数'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'ai.chat.defaultTemperature');

INSERT INTO system_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT 'AI-对话最大Token数', 'ai.chat.maxTokens', '4096', 'Y', 'admin', NOW(), 'AI对话最大输出Token数'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'ai.chat.maxTokens');

INSERT INTO system_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '系统-文件上传大小限制', 'sys.upload.maxSize', '50', 'Y', 'admin', NOW(), '文件上传大小限制(MB)'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'sys.upload.maxSize');

INSERT INTO system_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '系统-允许上传文件类型', 'sys.upload.allowType', 'pdf,doc,docx,xls,xlsx,ppt,pptx,txt,md,csv', 'Y', 'admin', NOW(), '允许上传的文件类型'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'sys.upload.allowType');

INSERT INTO system_content (title, content_type, content, order_num, status, create_by, create_time, remark)
SELECT '快速入门指南', 'guide', E'# qKnow 知识平台快速入门\n\n## 1. 创建知识库\n进入「知识库管理」页面，点击「新建知识库」，填写名称和描述后即可创建。\n\n## 2. 上传文档\n在知识库详情页，点击「上传文档」，支持 PDF、Word、Excel、TXT、Markdown 等格式。\n\n## 3. 创建智能体\n进入「Agent 管理」页面，配置智能体的提示词、关联知识库和工具。\n\n## 4. 开始对话\n在「对话」页面选择智能体，即可开始基于知识库的智能问答。', 1, '0', 'admin', NOW(), '快速入门'
WHERE NOT EXISTS (SELECT 1 FROM system_content WHERE title = '快速入门指南');

INSERT INTO system_content (title, content_type, content, order_num, status, create_by, create_time, remark)
SELECT '常见问题', 'faq', E'# 常见问题\n\n## Q: 文档上传后多久可以检索？\nA: 通常在 1-5 分钟内完成向量化处理，具体时间取决于文档大小。\n\n## Q: 支持哪些文件格式？\nA: 支持 PDF、Word、Excel、PPT、TXT、Markdown、CSV 等常见格式。\n\n## Q: 知识库的存储容量有限制吗？\nA: 免费版支持 1000 个文档片段，企业版无限制。\n\n## Q: 如何提升检索准确率？\nA: 1) 合理分段文档 2) 使用高质量Embedding模型 3) 配置合适的相似度阈值。', 2, '0', 'admin', NOW(), '常见问题'
WHERE NOT EXISTS (SELECT 1 FROM system_content WHERE title = '常见问题');

INSERT INTO system_content (title, content_type, content, order_num, status, create_by, create_time, remark)
SELECT 'API接入说明', 'guide', E'# API 接入说明\n\n## 认证方式\n所有 API 请求需要在 Header 中携带 `Authorization: Bearer {api_key}`。\n\n## 基础 URL\n```\nhttps://api.qknow.tech/v1\n```\n\n## 主要接口\n- `POST /chat/completions` - 对话补全\n- `POST /knowledge/search` - 知识库检索\n- `GET /bots` - 获取智能体列表\n- `POST /conversations` - 创建会话', 3, '0', 'admin', NOW(), 'API文档'
WHERE NOT EXISTS (SELECT 1 FROM system_content WHERE title = 'API接入说明');

INSERT INTO ai_api_key (workspace_id, name, api_key, platform, url, description, status, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, 'OpenAI官方Key', 'sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 'openai', 'https://api.openai.com/v1', 'OpenAI GPT系列模型访问密钥', 0, 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_api_key WHERE name = 'OpenAI官方Key');

INSERT INTO ai_api_key (workspace_id, name, api_key, platform, url, description, status, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '智谱AI Key', 'zhipu-xxxxxxxxxxxxxxxxxxxxxxxx', 'zhipu', 'https://open.bigmodel.cn/api/paas/v4', '智谱GLM系列模型访问密钥', 0, 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_api_key WHERE name = '智谱AI Key');

INSERT INTO ai_api_key (workspace_id, name, api_key, platform, url, description, status, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '通义千问Key', 'sk-xxxxxxxxxxxxxxxxxxxxxxxx', 'dashscope', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '阿里云通义千问模型访问密钥', 0, 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_api_key WHERE name = '通义千问Key');

INSERT INTO ai_api_key (workspace_id, name, api_key, platform, url, description, status, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '本地Ollama', 'ollama', 'ollama', 'http://localhost:11434', '本地部署的Ollama模型服务', 0, 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_api_key WHERE name = '本地Ollama');

INSERT INTO ai_model (workspace_id, key_id, name, model, platform, type, sort, status, valid_flag, del_flag, create_by, create_time, update_time, remark)
SELECT 1, 1, 'GPT-4o', 'gpt-4o', 'openai', 1, 1, 0, 0, 0, 'admin', NOW(), NOW(), 'OpenAI旗舰对话模型'
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE name = 'GPT-4o' AND platform = 'openai');

INSERT INTO ai_model (workspace_id, key_id, name, model, platform, type, sort, status, valid_flag, del_flag, create_by, create_time, update_time, remark)
SELECT 1, 1, 'GPT-4o-mini', 'gpt-4o-mini', 'openai', 1, 2, 0, 0, 0, 'admin', NOW(), NOW(), '轻量级对话模型'
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE name = 'GPT-4o-mini' AND platform = 'openai');

INSERT INTO ai_model (workspace_id, key_id, name, model, platform, type, sort, status, valid_flag, del_flag, create_by, create_time, update_time, remark)
SELECT 1, 1, 'text-embedding-3-small', 'text-embedding-3-small', 'openai', 5, 1, 0, 0, 0, 'admin', NOW(), NOW(), '文本向量化模型'
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE name = 'text-embedding-3-small' AND platform = 'openai');

INSERT INTO ai_model (workspace_id, key_id, name, model, platform, type, sort, status, valid_flag, del_flag, create_by, create_time, update_time, remark)
SELECT 1, 2, 'GLM-4', 'glm-4', 'zhipu', 1, 1, 0, 0, 0, 'admin', NOW(), NOW(), '智谱旗舰对话模型'
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE name = 'GLM-4' AND platform = 'zhipu');

INSERT INTO ai_model (workspace_id, key_id, name, model, platform, type, sort, status, valid_flag, del_flag, create_by, create_time, update_time, remark)
SELECT 1, 2, 'GLM-4-Flash', 'glm-4-flash', 'zhipu', 1, 2, 0, 0, 0, 'admin', NOW(), NOW(), '智谱快速对话模型'
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE name = 'GLM-4-Flash' AND platform = 'zhipu');

INSERT INTO ai_model (workspace_id, key_id, name, model, platform, type, sort, status, valid_flag, del_flag, create_by, create_time, update_time, remark)
SELECT 1, 3, 'qwen-plus', 'qwen-plus', 'dashscope', 1, 1, 0, 0, 0, 'admin', NOW(), NOW(), '通义千问增强版'
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE name = 'qwen-plus' AND platform = 'dashscope');

INSERT INTO ai_model (workspace_id, key_id, name, model, platform, type, sort, status, valid_flag, del_flag, create_by, create_time, update_time, remark)
SELECT 1, 3, 'qwen-turbo', 'qwen-turbo', 'dashscope', 1, 2, 0, 0, 0, 'admin', NOW(), NOW(), '通义千问快速版'
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE name = 'qwen-turbo' AND platform = 'dashscope');

INSERT INTO ai_model (workspace_id, key_id, name, model, platform, type, sort, status, valid_flag, del_flag, create_by, create_time, update_time, remark)
SELECT 1, 4, 'llama3.1:8b', 'llama3.1:8b', 'ollama', 1, 1, 0, 0, 0, 'admin', NOW(), NOW(), '本地Llama3.1模型'
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE name = 'llama3.1:8b' AND platform = 'ollama');

INSERT INTO ai_model (workspace_id, key_id, name, model, platform, type, sort, status, valid_flag, del_flag, create_by, create_time, update_time, remark)
SELECT 1, 4, 'qwen2.5:7b', 'qwen2.5:7b', 'ollama', 1, 2, 0, 0, 0, 'admin', NOW(), NOW(), '本地通义千问模型'
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE name = 'qwen2.5:7b' AND platform = 'ollama');

INSERT INTO ai_model (workspace_id, key_id, name, model, platform, type, sort, status, valid_flag, del_flag, create_by, create_time, update_time, remark)
SELECT 1, 4, 'nomic-embed-text', 'nomic-embed-text', 'ollama', 5, 1, 0, 0, 0, 'admin', NOW(), NOW(), '本地向量化模型'
WHERE NOT EXISTS (SELECT 1 FROM ai_model WHERE name = 'nomic-embed-text' AND platform = 'ollama');

INSERT INTO kmc_knowledge_base (workspace_id, name, description, indexing_technique, permission, embedding_model, embedding_model_provider, search_method, reranking_enable, top_k, score_threshold_enabled, score_threshold, keyword_weight, vector_weight, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '产品知识库', '包含产品手册、功能说明、版本更新日志等产品相关文档', 'high_quality', 'all_team_members', 'text-embedding-3-small', 'openai', 'hybrid', false, 4, true, 0.5, 0.3, 0.7, 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kmc_knowledge_base WHERE name = '产品知识库');

INSERT INTO kmc_knowledge_base (workspace_id, name, description, indexing_technique, permission, embedding_model, embedding_model_provider, search_method, reranking_enable, top_k, score_threshold_enabled, score_threshold, keyword_weight, vector_weight, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '技术文档库', '存放技术架构文档、API文档、开发规范等技术资料', 'high_quality', 'all_team_members', 'text-embedding-3-small', 'openai', 'hybrid', false, 4, true, 0.5, 0.3, 0.7, 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kmc_knowledge_base WHERE name = '技术文档库');

INSERT INTO kmc_knowledge_base (workspace_id, name, description, indexing_technique, permission, embedding_model, embedding_model_provider, search_method, reranking_enable, top_k, score_threshold_enabled, score_threshold, keyword_weight, vector_weight, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '常见问题库', '整理客户常见问题及标准解答，用于智能客服', 'high_quality', 'all_team_members', 'text-embedding-3-small', 'openai', 'semantic', false, 5, true, 0.6, 0.3, 0.7, 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kmc_knowledge_base WHERE name = '常见问题库');

INSERT INTO kmc_knowledge_base (workspace_id, name, description, indexing_technique, permission, embedding_model, embedding_model_provider, search_method, reranking_enable, top_k, score_threshold_enabled, score_threshold, keyword_weight, vector_weight, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '政策法规库', '公司内部制度、行业法规、合规要求等文档', 'economy', 'all_team_members', NULL, NULL, 'keyword', false, 3, true, 0.5, 0.5, 0.5, 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kmc_knowledge_base WHERE name = '政策法规库');

INSERT INTO kmc_category (workspace_id, parent_id, knowledge_base_id, name, order_num, ancestors, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, 0, 1, '产品手册', 1, '0', 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kmc_category WHERE name = '产品手册' AND knowledge_base_id = 1);

INSERT INTO kmc_category (workspace_id, parent_id, knowledge_base_id, name, order_num, ancestors, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, 0, 1, '版本更新', 2, '0', 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kmc_category WHERE name = '版本更新' AND knowledge_base_id = 1);

INSERT INTO kmc_category (workspace_id, parent_id, knowledge_base_id, name, order_num, ancestors, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, 0, 1, '功能教程', 3, '0', 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kmc_category WHERE name = '功能教程' AND knowledge_base_id = 1);

INSERT INTO kb_bot (workspace_id, name, type, description, builtin_flag, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '产品助手', 1, '基于产品知识库的智能问答助手，解答产品使用相关问题', 0, 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kb_bot WHERE name = '产品助手');

INSERT INTO kb_bot (workspace_id, name, type, description, builtin_flag, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '技术支持', 3, '基于技术文档库的知识问答Agent，协助开发者解决技术问题', 0, 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kb_bot WHERE name = '技术支持');

INSERT INTO kb_bot (workspace_id, name, type, description, builtin_flag, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '客服小助手', 1, '基于常见问题库的智能客服，自动回答用户常见问题', 0, 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kb_bot WHERE name = '客服小助手');

INSERT INTO kb_bot (workspace_id, name, type, description, builtin_flag, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '数据分析师', 2, '能够执行SQL查询并分析数据的智能Agent', 0, 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kb_bot WHERE name = '数据分析师');

INSERT INTO kb_tool (workspace_id, name, description, type, config, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '天气查询', '查询指定城市的实时天气信息', 1, '{"method":"GET","url":"https://api.weather.com/v1/current","headers":{},"params":{"city":"{city}"}}', 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kb_tool WHERE name = '天气查询');

INSERT INTO kb_tool (workspace_id, name, description, type, config, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '网页抓取', '抓取指定URL的网页内容并提取关键信息', 1, '{"method":"GET","url":"{url}","timeout":30000}', 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kb_tool WHERE name = '网页抓取');

INSERT INTO kb_tool (workspace_id, name, description, type, config, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, 'SQL执行器', '执行SQL查询语句并返回结果', 3, '{"datasource_id":1,"max_rows":1000}', 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kb_tool WHERE name = 'SQL执行器');

INSERT INTO kb_tool (workspace_id, name, description, type, config, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '代码沙箱', '在安全沙箱环境中执行Python代码', 2, '{"language":"python","timeout":30,"memory_limit":"256MB"}', 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kb_tool WHERE name = '代码沙箱');

INSERT INTO kb_tool (workspace_id, name, description, type, config, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '知识库检索', '从指定知识库中检索相关文档片段', 1, '{"search_type":"hybrid","top_k":5}', 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM kb_tool WHERE name = '知识库检索');

INSERT INTO dm_datasource (workspace_id, name, type, url, username, password, description, datasource_name, datasource_type, ip, port, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '生产数据库', 1, 'jdbc:postgresql://db-server:5432/production', 'readonly', 'encrypted_password', '生产环境只读数据库', '生产库', 'PostgreSQL', '192.168.1.100', 5432, 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM dm_datasource WHERE name = '生产数据库');

INSERT INTO ai_workflow (workspace_id, name, description, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '文档智能处理流水线', '自动对上传文档进行分类、摘要、关键词提取和向量化处理', 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_workflow WHERE name = '文档智能处理流水线');

INSERT INTO ai_workflow (workspace_id, name, description, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '客服对话工作流', '接收用户问题，检索知识库，生成回复并记录日志的完整流程', 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_workflow WHERE name = '客服对话工作流');

INSERT INTO ai_workflow (workspace_id, name, description, valid_flag, del_flag, create_by, create_time, update_time)
SELECT 1, '数据ETL流程', '从数据源抽取数据，清洗转换后加载到知识库的自动化流程', 0, 0, 'admin', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ai_workflow WHERE name = '数据ETL流程');

INSERT INTO system_logininfor (user_name, ipaddr, login_location, browser, os, status, msg, login_time) VALUES
('admin', '127.0.0.1', '内网IP', 'Chrome 120', 'macOS', '0', '登录成功', NOW() - INTERVAL '2 hours'),
('admin', '192.168.1.50', '内网IP', 'Chrome 120', 'Windows 10', '0', '登录成功', NOW() - INTERVAL '5 hours'),
('admin', '10.0.0.15', '内网IP', 'Firefox 121', 'macOS', '0', '登录成功', NOW() - INTERVAL '1 day'),
('testuser', '192.168.1.88', '内网IP', 'Safari 17', 'macOS', '1', '密码错误', NOW() - INTERVAL '3 hours'),
('admin', '172.16.0.5', '内网IP', 'Edge 120', 'Windows 11', '0', '登录成功', NOW() - INTERVAL '6 hours'),
('guest', '192.168.1.100', '内网IP', 'Chrome 120', 'Windows 10', '1', '用户不存在', NOW() - INTERVAL '4 hours'),
('admin', '127.0.0.1', '内网IP', 'Chrome 121', 'macOS', '0', '登录成功', NOW() - INTERVAL '30 minutes'),
('admin', '192.168.1.50', '内网IP', 'Chrome 120', 'Windows 10', '0', '登录成功', NOW() - INTERVAL '2 days');

INSERT INTO system_oper_log (title, business_type, method, request_method, oper_name, dept_name, oper_url, oper_ip, oper_location, oper_param, json_result, status, error_msg, oper_time, cost_time) VALUES
('知识库', 1, 'tech.qiantong.qknow.module.kmc.controller.KmcKnowledgeBaseController.add()', 'POST', 'admin', '研发部门', '/kmc/knowledgeBase', '127.0.0.1', '内网IP', '{"name":"产品知识库","description":"产品相关文档"}', '{"code":200,"msg":"操作成功"}', 0, '', NOW() - INTERVAL '2 hours', 156),
('智能体', 1, 'tech.qiantong.qknow.module.kb.controller.KbBotController.add()', 'POST', 'admin', '研发部门', '/kb/bot', '127.0.0.1', '内网IP', '{"name":"产品助手","type":1}', '{"code":200,"msg":"操作成功"}', 0, '', NOW() - INTERVAL '1 hour', 89),
('用户管理', 2, 'tech.qiantong.qknow.module.system.controller.SysUserController.edit()', 'PUT', 'admin', '研发部门', '/system/user', '192.168.1.50', '内网IP', '{"userId":1,"nickName":"管理员"}', '{"code":200,"msg":"操作成功"}', 0, '', NOW() - INTERVAL '3 hours', 45),
('字典数据', 1, 'tech.qiantong.qknow.module.system.controller.SysDictDataController.add()', 'POST', 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{"dictType":"ai_model_type","dictLabel":"对话模型"}', '{"code":200,"msg":"操作成功"}', 0, '', NOW() - INTERVAL '4 hours', 32),
('AI模型', 1, 'tech.qiantong.qknow.module.ai.controller.AiModelController.add()', 'POST', 'admin', '研发部门', '/ai/model', '10.0.0.15', '内网IP', '{"name":"GPT-4o","platform":"openai"}', '{"code":200,"msg":"操作成功"}', 0, '', NOW() - INTERVAL '5 hours', 120),
('登录日志', 5, 'tech.qiantong.qknow.module.system.controller.SysLoginController.logout()', 'POST', 'admin', '研发部门', '/logout', '192.168.1.50', '内网IP', '{}', '{"code":200,"msg":"退出成功"}', 0, '', NOW() - INTERVAL '30 minutes', 12),
('文档上传', 1, 'tech.qiantong.qknow.module.kmc.controller.KmcDocumentController.upload()', 'POST', 'admin', '研发部门', '/kmc/document/upload', '127.0.0.1', '内网IP', '{"knowledgeBaseId":1,"fileName":"产品手册.pdf"}', '{"code":200,"msg":"操作成功"}', 0, '', NOW() - INTERVAL '1 hour', 2340),
('系统配置', 2, 'tech.qiantong.qknow.module.system.controller.SysConfigController.edit()', 'PUT', 'admin', '研发部门', '/system/config', '127.0.0.1', '内网IP', '{"configKey":"ai.chat.defaultTemperature","configValue":"0.7"}', '{"code":200,"msg":"操作成功"}', 0, '', NOW() - INTERVAL '6 hours', 28);

INSERT INTO system_job_log (job_name, job_group, invoke_target, job_message, status, exception_info, start_time, stop_time) VALUES
('知识库同步任务', 'SYSTEM', 'knowledgeSyncTask.sync()', '任务执行成功，同步文档 15 篇', '0', '', NOW() - INTERVAL '22 hours', NOW() - INTERVAL '22 hours' + INTERVAL '45 seconds'),
('日志清理任务', 'SYSTEM', 'logCleanTask.clean()', '任务执行成功，清理过期日志 256 条', '0', '', NOW() - INTERVAL '21 hours', NOW() - INTERVAL '21 hours' + INTERVAL '12 seconds'),
('数据备份任务', 'SYSTEM', 'backupTask.backup()', '任务执行成功，备份数据大小 128MB', '0', '', NOW() - INTERVAL '20 hours', NOW() - INTERVAL '20 hours' + INTERVAL '3 minutes'),
('知识库同步任务', 'SYSTEM', 'knowledgeSyncTask.sync()', '任务执行成功，同步文档 8 篇', '0', '', NOW() - INTERVAL '46 hours', NOW() - INTERVAL '46 hours' + INTERVAL '38 seconds'),
('日志清理任务', 'SYSTEM', 'logCleanTask.clean()', '任务执行成功，清理过期日志 128 条', '0', '', NOW() - INTERVAL '45 hours', NOW() - INTERVAL '45 hours' + INTERVAL '8 seconds'),
('模型健康检查', 'AI', 'modelHealthTask.check()', '所有模型状态正常', '0', '', NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '30 minutes' + INTERVAL '5 seconds'),
('模型健康检查', 'AI', 'modelHealthTask.check()', '检测到本地Ollama服务响应超时', '1', '', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour' + INTERVAL '30 seconds'),
('向量索引重建', 'SYSTEM', 'vectorIndexTask.rebuild()', '任务执行成功，重建索引 3 个知识库', '0', '', NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days' + INTERVAL '15 minutes');

INSERT INTO gen_table (table_name, table_comment, class_name, tpl_category, tpl_web_type, package_name, module_name, business_name, function_name, function_author, gen_type, gen_path, create_by, create_time, update_by, update_time)
SELECT 'demo_customer', '客户信息表', 'DemoCustomer', 'crud', '', 'tech.qiantong.qknow.module.demo', 'demo', 'customer', '客户管理', 'admin', '0', '/', 'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table WHERE table_name = 'demo_customer');

INSERT INTO gen_table (table_name, table_comment, class_name, tpl_category, tpl_web_type, package_name, module_name, business_name, function_name, function_author, gen_type, gen_path, create_by, create_time, update_by, update_time)
SELECT 'demo_employee', '员工信息表', 'DemoEmployee', 'crud', '', 'tech.qiantong.qknow.module.demo', 'demo', 'employee', '员工管理', 'admin', '0', '/', 'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table WHERE table_name = 'demo_employee');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_customer'), 'id', '客户ID', 'bigint', 'Long', 'id', '1', '1', '0', '0', '0', '0', '0', 'EQ', 'input', '', 1, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_customer') AND column_name = 'id');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_customer'), 'name', '客户名称', 'varchar(128)', 'String', 'name', '0', '0', '1', '1', '1', '1', '1', 'LIKE', 'input', '', 2, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_customer') AND column_name = 'name');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_customer'), 'phone', '联系电话', 'varchar(20)', 'String', 'phone', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 3, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_customer') AND column_name = 'phone');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_customer'), 'email', '电子邮箱', 'varchar(128)', 'String', 'email', '0', '0', '0', '1', '1', '1', '0', 'EQ', 'input', '', 4, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_customer') AND column_name = 'email');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_customer'), 'company', '所属公司', 'varchar(256)', 'String', 'company', '0', '0', '0', '1', '1', '1', '1', 'LIKE', 'input', '', 5, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_customer') AND column_name = 'company');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_customer'), 'status', '状态', 'tinyint', 'Integer', 'status', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'select', '', 6, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_customer') AND column_name = 'status');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_customer'), 'create_time', '创建时间', 'datetime', 'Date', 'createTime', '0', '0', '0', '1', '0', '1', '1', 'BETWEEN', 'datetime', '', 7, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_customer') AND column_name = 'create_time');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee'), 'id', '员工ID', 'bigint', 'Long', 'id', '1', '1', '0', '0', '0', '0', '0', 'EQ', 'input', '', 1, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee') AND column_name = 'id');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee'), 'name', '员工姓名', 'varchar(64)', 'String', 'name', '0', '0', '1', '1', '1', '1', '1', 'LIKE', 'input', '', 2, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee') AND column_name = 'name');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee'), 'gender', '性别', 'char(1)', 'String', 'gender', '0', '0', '1', '1', '1', '1', '0', 'EQ', 'select', 'sys_user_sex', 3, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee') AND column_name = 'gender');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee'), 'dept', '所属部门', 'varchar(128)', 'String', 'dept', '0', '0', '0', '1', '1', '1', '1', 'LIKE', 'input', '', 4, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee') AND column_name = 'dept');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee'), 'position', '职位', 'varchar(64)', 'String', 'position', '0', '0', '0', '1', '1', '1', '0', 'EQ', 'input', '', 5, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee') AND column_name = 'position');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee'), 'phone', '手机号码', 'varchar(20)', 'String', 'phone', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 6, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee') AND column_name = 'phone');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee'), 'status', '状态', 'tinyint', 'Integer', 'status', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'select', '', 7, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee') AND column_name = 'status');

INSERT INTO gen_table_column (table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, dict_type, sort, create_by, create_time)
SELECT (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee'), 'create_time', '创建时间', 'datetime', 'Date', 'createTime', '0', '0', '0', '1', '0', '1', '1', 'BETWEEN', 'datetime', '', 8, 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gen_table_column WHERE table_id = (SELECT table_id FROM gen_table WHERE table_name = 'demo_employee') AND column_name = 'create_time');
