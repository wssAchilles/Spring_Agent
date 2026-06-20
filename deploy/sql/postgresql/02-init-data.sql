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
