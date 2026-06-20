-- ============================================================================
-- AI-Native RAG Agent Platform - Initial Data
-- ============================================================================

-- Admin user (password: admin123)
INSERT INTO "system_user" (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, password, status, del_flag, create_by, create_time, update_by, update_time, remark)
VALUES (1, 103, 'admin', '管理员', '00', 'admin@example.com', '15888888888', '0', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, '管理员');

-- Default roles
INSERT INTO "system_role" (role_id, role_name, role_key, role_sort, status, del_flag, create_by, create_time, remark) VALUES (1, '超级管理员', 'admin', 1, '0', '0', 'admin', CURRENT_TIMESTAMP, '超级管理员');
INSERT INTO "system_role" (role_id, role_name, role_key, role_sort, status, del_flag, create_by, create_time, remark) VALUES (2, '普通角色', 'common', 2, '0', '0', 'admin', CURRENT_TIMESTAMP, '普通角色');

-- User-role mapping
INSERT INTO system_user_role (user_id, role_id) VALUES (1, 1);

-- Default posts
INSERT INTO system_post (post_id, post_code, post_name, post_sort, status, create_by, create_time) VALUES (1, 'ceo', '董事长', 1, '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_post (post_id, post_code, post_name, post_sort, status, create_by, create_time) VALUES (2, 'hr', '人力资源', 2, '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_post (post_id, post_code, post_name, post_sort, status, create_by, create_time) VALUES (3, 'user', '普通员工', 3, '0', 'admin', CURRENT_TIMESTAMP);

-- User-post mapping
INSERT INTO system_user_post (user_id, post_id) VALUES (1, 1);

-- Default department
INSERT INTO system_dept (dept_id, parent_id, ancestors, dept_name, order_num, status, del_flag, create_by, create_time) VALUES (100, 0, '0', '千桐科技', 0, '0', '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_dept (dept_id, parent_id, ancestors, dept_name, order_num, status, del_flag, create_by, create_time) VALUES (101, 100, '0,100', '总部', 1, '0', '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_dept (dept_id, parent_id, ancestors, dept_name, order_num, status, del_flag, create_by, create_time) VALUES (102, 100, '0,100', '研发部门', 2, '0', '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_dept (dept_id, parent_id, ancestors, dept_name, order_num, status, del_flag, create_by, create_time) VALUES (103, 101, '0,100,101', '研发部门', 1, '0', '0', 'admin', CURRENT_TIMESTAMP);

-- Default dict types
INSERT INTO system_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) VALUES (1, '用户性别', 'sys_user_sex', '0', 'admin', CURRENT_TIMESTAMP, '用户性别列表');
INSERT INTO system_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) VALUES (2, '系统状态', 'sys_normal_disable', '0', 'admin', CURRENT_TIMESTAMP, '系统状态列表');
INSERT INTO system_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) VALUES (3, '系统是否', 'sys_yes_no', '0', 'admin', CURRENT_TIMESTAMP, '系统是否列表');
INSERT INTO system_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) VALUES (100, 'AI模型类型', 'ai_model_type', '0', 'admin', CURRENT_TIMESTAMP, 'AI模型类型');

-- Default dict data
INSERT INTO system_dict_data (dict_code, sort, label, value, dict_type, css_class, is_default, status, create_by, create_time) VALUES (1, 1, '男', '0', 'sys_user_sex', '', 'Y', '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_dict_data (dict_code, sort, label, value, dict_type, css_class, is_default, status, create_by, create_time) VALUES (2, 2, '女', '1', 'sys_user_sex', '', 'N', '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_dict_data (dict_code, sort, label, value, dict_type, css_class, is_default, status, create_by, create_time) VALUES (3, 1, '正常', '0', 'sys_normal_disable', 'primary', 'Y', '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_dict_data (dict_code, sort, label, value, dict_type, css_class, is_default, status, create_by, create_time) VALUES (4, 2, '停用', '1', 'sys_normal_disable', 'danger', 'N', '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_dict_data (dict_code, sort, label, value, dict_type, css_class, is_default, status, create_by, create_time) VALUES (5, 1, '是', 'Y', 'sys_yes_no', 'primary', 'Y', '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_dict_data (dict_code, sort, label, value, dict_type, css_class, is_default, status, create_by, create_time) VALUES (6, 2, '否', 'N', 'sys_yes_no', 'danger', 'N', '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_dict_data (dict_code, sort, label, value, dict_type, css_class, is_default, status, create_by, create_time) VALUES (100, 1, '对话模型', '1', 'ai_model_type', '', 'Y', '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_dict_data (dict_code, sort, label, value, dict_type, css_class, is_default, status, create_by, create_time) VALUES (101, 2, '图片模型', '2', 'ai_model_type', '', 'N', '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_dict_data (dict_code, sort, label, value, dict_type, css_class, is_default, status, create_by, create_time) VALUES (102, 3, '语音模型', '3', 'ai_model_type', '', 'N', '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_dict_data (dict_code, sort, label, value, dict_type, css_class, is_default, status, create_by, create_time) VALUES (103, 5, '向量模型', '5', 'ai_model_type', '', 'N', '0', 'admin', CURRENT_TIMESTAMP);
INSERT INTO system_dict_data (dict_code, sort, label, value, dict_type, css_class, is_default, status, create_by, create_time) VALUES (104, 6, '重排模型', '6', 'ai_model_type', '', 'N', '0', 'admin', CURRENT_TIMESTAMP);

-- Default AI API Key (DeepSeek - user needs to fill in real key)
INSERT INTO ai_api_key (id, workspace_id, name, platform, description, status, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (1, 1001, 'DeepSeek', 'DeepSeek', '深度求索提供的模型，如 deepseek-chat', 2, 1, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

-- Default AI Model (DeepSeek Chat)
INSERT INTO ai_model (id, workspace_id, key_id, name, model, platform, type, status, valid_flag, del_flag, create_by, create_time, update_by, update_time)
VALUES (1, 1001, 1, 'deepseek-chat', 'deepseek-chat', 'DeepSeek', 1, 2, 1, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

-- Reset sequences to avoid conflicts
SELECT setval('"system_user_user_id_seq"', 100);
SELECT setval('"system_role_role_id_seq"', 100);
SELECT setval('system_post_post_id_seq', 100);
SELECT setval('system_dept_dept_id_seq', 200);
SELECT setval('system_dict_type_dict_id_seq', 200);
SELECT setval('system_dict_data_dict_code_seq', 200);
SELECT setval('ai_api_key_id_seq', 100);
SELECT setval('ai_model_id_seq', 100);

-- ============================================================================
-- System Menu Configuration (补充菜单路由)
-- ============================================================================

-- KacHorizontal 路由修复
UPDATE system_menu SET component = 'kac/horizontal/index' WHERE menu_id = 2407;

-- KgGraph 知识图谱菜单
INSERT INTO system_menu (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (2420, '图谱探索', 2058, 1, 'graph', 'kg/graph/index', 'C', '0', '0', 'kg:graph:list', 'graph', 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, '知识图谱探索菜单')
ON CONFLICT (menu_id) DO NOTHING;

-- Role-menu association for KgGraph
INSERT INTO system_role_menu (role_id, menu_id) VALUES (1, 2420) ON CONFLICT DO NOTHING;

-- ============================================================================
-- System Notice (通知公告)
-- ============================================================================

INSERT INTO system_notice (notice_id, notice_title, notice_type, notice_content, status, create_by, create_time, update_by, update_time, remark)
VALUES (1, '系统上线通知', 1, 'qKnow 知识平台已成功上线，欢迎使用！本平台提供知识库管理、智能体配置、工具管理等功能。', 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, '系统上线通知');

INSERT INTO system_notice (notice_id, notice_title, notice_type, notice_content, status, create_by, create_time, update_by, update_time, remark)
VALUES (2, '功能更新说明', 1, '本次更新包含：1. 知识库管理功能 2. Agent 配置功能 3. 工具管理功能 4. AI 模型市场', 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, '功能更新说明');

INSERT INTO system_notice (notice_id, notice_title, notice_type, notice_content, status, create_by, create_time, update_by, update_time, remark)
VALUES (3, '安全提醒', 2, '请定期修改密码，确保账户安全。如发现异常请及时联系管理员。', 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, '安全提醒');

-- ============================================================================
-- System Job (定时任务)
-- ============================================================================

INSERT INTO system_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, update_by, update_time, remark)
VALUES (1, '知识库同步任务', 'SYSTEM', 'knowledgeSyncTask.sync()', '0 0 2 * * ?', 3, 1, 1, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, '每日凌晨2点同步知识库');

INSERT INTO system_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, update_by, update_time, remark)
VALUES (2, '日志清理任务', 'SYSTEM', 'logCleanTask.clean()', '0 0 3 * * ?', 3, 1, 1, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, '每日凌晨3点清理过期日志');

INSERT INTO system_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, update_by, update_time, remark)
VALUES (3, '数据备份任务', 'SYSTEM', 'backupTask.backup()', '0 0 4 * * ?', 3, 1, 1, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, '每日凌晨4点备份数据');

-- ============================================================================
-- Message (消息通知)
-- ============================================================================

INSERT INTO message (id, sender_id, receiver_id, title, content, category, msg_level, module, has_read, valid_flag, del_flag, create_by, create_time, update_by, update_time, remark)
VALUES (1, 1, 1, '欢迎使用 qKnow', '欢迎使用 qKnow 知识平台！您可以开始创建知识库、配置 Agent、管理工具等。', 1, 0, 1, 0, 1, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, '欢迎消息');

INSERT INTO message (id, sender_id, receiver_id, title, content, category, msg_level, module, has_read, valid_flag, del_flag, create_by, create_time, update_by, update_time, remark)
VALUES (2, 1, 1, '系统更新通知', '系统已完成最新更新，新增了多项功能。', 1, 0, 1, 0, 1, 0, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, '更新通知');

-- ============================================================================
-- Code Generator (代码生成器示例)
-- ============================================================================

INSERT INTO gen_table (table_id, table_name, table_comment, class_name, tpl_category, package_name, module_name, business_name, function_name, function_author, gen_type, gen_path, create_by, create_time, update_by, update_time)
VALUES (1, 'demo_product', '产品示例表', 'DemoProduct', 'crud', 'tech.qiantong.qknow.module.demo', 'demo', 'product', '产品管理', 'admin', '0', '/', 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO gen_table (table_id, table_name, table_comment, class_name, tpl_category, package_name, module_name, business_name, function_name, function_author, gen_type, gen_path, create_by, create_time, update_by, update_time)
VALUES (2, 'demo_order', '订单示例表', 'DemoOrder', 'crud', 'tech.qiantong.qknow.module.demo', 'demo', 'order', '订单管理', 'admin', '0', '/', 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP);

INSERT INTO gen_table_column (column_id, table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, sort, create_by, create_time)
VALUES (1, 1, 'id', '产品ID', 'bigint', 'Long', 'id', '1', '1', '0', '0', '0', '0', '0', 'EQ', 'input', 1, 'admin', CURRENT_TIMESTAMP);

INSERT INTO gen_table_column (column_id, table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, sort, create_by, create_time)
VALUES (2, 1, 'name', '产品名称', 'varchar(128)', 'String', 'name', '0', '0', '1', '1', '1', '1', '1', 'LIKE', 'input', 2, 'admin', CURRENT_TIMESTAMP);

INSERT INTO gen_table_column (column_id, table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, sort, create_by, create_time)
VALUES (3, 1, 'price', '产品价格', 'decimal(10,2)', 'BigDecimal', 'price', '0', '0', '1', '1', '1', '1', '0', 'EQ', 'input', 3, 'admin', CURRENT_TIMESTAMP);

INSERT INTO gen_table_column (column_id, table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, sort, create_by, create_time)
VALUES (4, 1, 'status', '状态', 'tinyint', 'Integer', 'status', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'select', 4, 'admin', CURRENT_TIMESTAMP);

INSERT INTO gen_table_column (column_id, table_id, column_name, column_comment, column_type, java_type, java_field, is_pk, is_increment, is_required, is_insert, is_edit, is_list, is_query, query_type, html_type, sort, create_by, create_time)
VALUES (5, 1, 'create_time', '创建时间', 'datetime', 'Date', 'createTime', '0', '0', '0', '1', '0', '1', '1', 'BETWEEN', 'datetime', 5, 'admin', CURRENT_TIMESTAMP);

-- ============================================================================
-- Knowledge Graph (知识图谱初始数据)
-- ============================================================================

-- Flutter 技术概念节点
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (1, 1001, 'Flutter', 'technology', '{"description": "Google的跨平台UI框架", "category": "framework"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (2, 1001, 'GetX', 'technology', '{"description": "Flutter状态管理和路由解决方案", "category": "library"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (3, 1001, 'Dio', 'technology', '{"description": "Flutter HTTP客户端库", "category": "library"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (4, 1001, 'Dart', 'technology', '{"description": "Flutter的编程语言", "category": "language"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (5, 1001, 'Widget', 'concept', '{"description": "Flutter UI构建块", "category": "core"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (6, 1001, 'StatefulWidget', 'concept', '{"description": "有状态的Widget", "category": "core"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (7, 1001, 'StatelessWidget', 'concept', '{"description": "无状态的Widget", "category": "core"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (8, 1001, 'Provider', 'technology', '{"description": "Flutter状态管理方案", "category": "library"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (9, 1001, 'Bloc', 'technology', '{"description": "业务逻辑组件", "category": "library"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (10, 1001, 'Riverpod', 'technology', '{"description": "Provider的改进版本", "category": "library"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (11, 1001, 'SQLite', 'technology', '{"description": "本地数据库", "category": "database"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (12, 1001, 'Firebase', 'technology', '{"description": "Google后端服务", "category": "backend"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (13, 1001, 'REST API', 'concept', '{"description": "RESTful接口设计", "category": "architecture"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (14, 1001, 'MVC', 'concept', '{"description": "模型-视图-控制器架构", "category": "architecture"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (15, 1001, 'MVVM', 'concept', '{"description": "模型-视图-视图模型架构", "category": "architecture"}');

-- 知识图谱关系边
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (1, 1001, 1, 4, 'uses', '{"description": "Flutter使用Dart语言"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (2, 1001, 1, 2, 'supports', '{"description": "Flutter支持GetX状态管理"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (3, 1001, 1, 3, 'supports', '{"description": "Flutter支持Dio网络请求"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (4, 1001, 1, 5, 'contains', '{"description": "Flutter包含Widget组件"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (5, 1001, 5, 6, 'has_type', '{"description": "Widget分为StatefulWidget"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (6, 1001, 5, 7, 'has_type', '{"description": "Widget分为StatelessWidget"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (7, 1001, 2, 14, 'implements', '{"description": "GetX实现MVC架构"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (8, 1001, 1, 8, 'supports', '{"description": "Flutter支持Provider"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (9, 1001, 1, 9, 'supports', '{"description": "Flutter支持Bloc"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (10, 1001, 1, 10, 'supports', '{"description": "Flutter支持Riverpod"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (11, 1001, 1, 11, 'integrates', '{"description": "Flutter集成SQLite"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (12, 1001, 1, 12, 'integrates', '{"description": "Flutter集成Firebase"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (13, 1001, 3, 13, 'implements', '{"description": "Dio实现REST API调用"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (14, 1001, 8, 15, 'implements', '{"description": "Provider实现MVVM架构"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (15, 1001, 9, 15, 'implements', '{"description": "Bloc实现MVVM架构"}');

-- Reset additional sequences
SELECT setval('system_notice_notice_id_seq', 100);
SELECT setval('system_job_job_id_seq', 100);
SELECT setval('message_id_seq', 100);
SELECT setval('gen_table_table_id_seq', 100);
SELECT setval('gen_table_column_column_id_seq', 100);
SELECT setval('kg_node_id_seq', 100);
SELECT setval('kg_edge_id_seq', 100);
