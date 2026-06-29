-- ============================================================================
-- AI-Native RAG Agent Platform - PostgreSQL Schema
-- Migrated from MySQL (qKnow 2.2.1)
-- ============================================================================

SET client_encoding = 'UTF8';

-- ===========================
-- System Tables
-- ===========================

CREATE TABLE IF NOT EXISTS system_dept (
    dept_id     BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT DEFAULT 0,
    ancestors   VARCHAR(500) DEFAULT '',
    dept_name   VARCHAR(30) DEFAULT '',
    order_num   INT DEFAULT 0,
    leader      VARCHAR(20) DEFAULT NULL,
    phone       VARCHAR(11) DEFAULT NULL,
    email       VARCHAR(50) DEFAULT NULL,
    status      CHAR(1) DEFAULT '0',
    del_flag    CHAR(1) DEFAULT '0',
    create_by   VARCHAR(64) DEFAULT '',
    create_time TIMESTAMP DEFAULT NULL,
    update_by   VARCHAR(64) DEFAULT '',
    update_time TIMESTAMP DEFAULT NULL
);
COMMENT ON TABLE system_dept IS '部门表';

CREATE TABLE IF NOT EXISTS "system_user" (
    user_id      BIGSERIAL PRIMARY KEY,
    dept_id      BIGINT DEFAULT NULL,
    user_name    VARCHAR(30) NOT NULL,
    nick_name    VARCHAR(30) NOT NULL,
    user_type    VARCHAR(2) DEFAULT '00',
    email        VARCHAR(50) DEFAULT '',
    phonenumber  VARCHAR(11) DEFAULT '',
    sex          CHAR(1) DEFAULT '0',
    avatar       VARCHAR(100) DEFAULT '',
    password     VARCHAR(100) DEFAULT '',
    status       CHAR(1) DEFAULT '0',
    del_flag     CHAR(1) DEFAULT '0',
    login_ip     VARCHAR(128) DEFAULT '',
    login_date   TIMESTAMP DEFAULT NULL,
    create_by    VARCHAR(64) DEFAULT '',
    create_time  TIMESTAMP DEFAULT NULL,
    update_by    VARCHAR(64) DEFAULT '',
    update_time  TIMESTAMP DEFAULT NULL,
    remark       VARCHAR(500) DEFAULT NULL
);
COMMENT ON TABLE "system_user" IS '用户信息表';

CREATE TABLE IF NOT EXISTS "system_role" (
    role_id      BIGSERIAL PRIMARY KEY,
    role_name    VARCHAR(30) NOT NULL,
    role_key     VARCHAR(100) NOT NULL,
    role_sort    INT DEFAULT 0,
    data_scope   CHAR(1) DEFAULT '1',
    menu_check_strictly BOOLEAN DEFAULT TRUE,
    dept_check_strictly BOOLEAN DEFAULT TRUE,
    status       CHAR(1) DEFAULT '0',
    del_flag     CHAR(1) DEFAULT '0',
    create_by    VARCHAR(64) DEFAULT '',
    create_time  TIMESTAMP DEFAULT NULL,
    update_by    VARCHAR(64) DEFAULT '',
    update_time  TIMESTAMP DEFAULT NULL,
    remark       VARCHAR(500) DEFAULT NULL
);
COMMENT ON TABLE "system_role" IS '角色信息表';

CREATE TABLE IF NOT EXISTS system_post (
    post_id      BIGSERIAL PRIMARY KEY,
    post_code    VARCHAR(64) NOT NULL,
    post_name    VARCHAR(50) NOT NULL,
    post_sort    INT DEFAULT 0,
    status       CHAR(1) DEFAULT '0',
    create_by    VARCHAR(64) DEFAULT '',
    create_time  TIMESTAMP DEFAULT NULL,
    update_by    VARCHAR(64) DEFAULT '',
    update_time  TIMESTAMP DEFAULT NULL,
    remark       VARCHAR(500) DEFAULT NULL
);
COMMENT ON TABLE system_post IS '岗位信息表';

CREATE TABLE IF NOT EXISTS system_menu (
    menu_id     BIGSERIAL PRIMARY KEY,
    menu_name   VARCHAR(50) NOT NULL,
    parent_id   BIGINT DEFAULT 0,
    order_num   INT DEFAULT 0,
    path        VARCHAR(200) DEFAULT '',
    component   VARCHAR(255) DEFAULT NULL,
    query       VARCHAR(255) DEFAULT NULL,
    route_name  VARCHAR(50) DEFAULT '',
    is_frame    INT DEFAULT 1,
    is_cache    INT DEFAULT 0,
    menu_type   CHAR(1) DEFAULT '',
    visible     CHAR(1) DEFAULT '0',
    status      CHAR(1) DEFAULT '0',
    perms       VARCHAR(100) DEFAULT NULL,
    icon        VARCHAR(100) DEFAULT '#',
    create_by   VARCHAR(64) DEFAULT '',
    create_time TIMESTAMP DEFAULT NULL,
    update_by   VARCHAR(64) DEFAULT '',
    update_time TIMESTAMP DEFAULT NULL,
    remark      VARCHAR(500) DEFAULT ''
);
COMMENT ON TABLE system_menu IS '菜单权限表';

CREATE TABLE IF NOT EXISTS system_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);
COMMENT ON TABLE system_user_role IS '用户和角色关联表';

CREATE TABLE IF NOT EXISTS system_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);
COMMENT ON TABLE system_role_menu IS '角色和菜单关联表';

CREATE TABLE IF NOT EXISTS system_role_dept (
    role_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, dept_id)
);
COMMENT ON TABLE system_role_dept IS '角色和部门关联表';

CREATE TABLE IF NOT EXISTS system_user_post (
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, post_id)
);
COMMENT ON TABLE system_user_post IS '用户与岗位关联表';

CREATE TABLE IF NOT EXISTS system_dict_type (
    dict_id     BIGSERIAL PRIMARY KEY,
    dict_name   VARCHAR(100) DEFAULT '',
    dict_sort   INT DEFAULT 0,
    dict_type   VARCHAR(100) DEFAULT '',
    status      CHAR(1) DEFAULT '0',
    create_by   VARCHAR(64) DEFAULT '',
    create_time TIMESTAMP DEFAULT NULL,
    update_by   VARCHAR(64) DEFAULT '',
    update_time TIMESTAMP DEFAULT NULL,
    remark      VARCHAR(500) DEFAULT NULL
);
COMMENT ON TABLE system_dict_type IS '字典类型表';
CREATE UNIQUE INDEX IF NOT EXISTS idx_dict_type ON system_dict_type(dict_type);

CREATE TABLE IF NOT EXISTS system_dict_data (
    dict_code   BIGSERIAL PRIMARY KEY,
    dict_sort   INT DEFAULT 0,
    dict_label  VARCHAR(100) DEFAULT '',
    dict_value  VARCHAR(100) DEFAULT '',
    dict_type   VARCHAR(100) DEFAULT '',
    css_class   VARCHAR(100) DEFAULT NULL,
    list_class  VARCHAR(100) DEFAULT NULL,
    is_default  CHAR(1) DEFAULT 'N',
    status      CHAR(1) DEFAULT '0',
    create_by   VARCHAR(64) DEFAULT '',
    create_time TIMESTAMP DEFAULT NULL,
    update_by   VARCHAR(64) DEFAULT '',
    update_time TIMESTAMP DEFAULT NULL,
    remark      VARCHAR(500) DEFAULT NULL
);
COMMENT ON TABLE system_dict_data IS '字典数据表';

CREATE TABLE IF NOT EXISTS system_config (
    config_id    BIGSERIAL PRIMARY KEY,
    config_name  VARCHAR(100) DEFAULT '',
    config_key   VARCHAR(100) DEFAULT '',
    config_value VARCHAR(500) DEFAULT '',
    config_type  CHAR(1) DEFAULT 'N',
    create_by    VARCHAR(64) DEFAULT '',
    create_time  TIMESTAMP DEFAULT NULL,
    update_by    VARCHAR(64) DEFAULT '',
    update_time  TIMESTAMP DEFAULT NULL,
    remark       VARCHAR(500) DEFAULT NULL
);
COMMENT ON TABLE system_config IS '参数配置表';

CREATE TABLE IF NOT EXISTS system_oper_log (
    oper_id        BIGSERIAL PRIMARY KEY,
    title          VARCHAR(50) DEFAULT '',
    business_type  INT DEFAULT 0,
    method         VARCHAR(100) DEFAULT '',
    request_method VARCHAR(10) DEFAULT '',
    operator_type  INT DEFAULT 0,
    oper_name      VARCHAR(50) DEFAULT '',
    dept_name      VARCHAR(50) DEFAULT '',
    oper_url       VARCHAR(255) DEFAULT '',
    oper_ip        VARCHAR(128) DEFAULT '',
    oper_location  VARCHAR(255) DEFAULT '',
    oper_param     TEXT DEFAULT '',
    json_result    TEXT DEFAULT '',
    status         INT DEFAULT 0,
    error_msg      TEXT DEFAULT '',
    oper_time      TIMESTAMP DEFAULT NULL,
    cost_time      BIGINT DEFAULT 0
);
COMMENT ON TABLE system_oper_log IS '操作日志记录';

CREATE TABLE IF NOT EXISTS system_logininfor (
    info_id        BIGSERIAL PRIMARY KEY,
    user_name      VARCHAR(50) DEFAULT '',
    ipaddr         VARCHAR(128) DEFAULT '',
    login_location VARCHAR(255) DEFAULT '',
    browser        VARCHAR(50) DEFAULT '',
    os             VARCHAR(50) DEFAULT '',
    status         CHAR(1) DEFAULT '0',
    msg            VARCHAR(255) DEFAULT '',
    login_time     TIMESTAMP DEFAULT NULL
);
COMMENT ON TABLE system_logininfor IS '系统访问记录';

CREATE TABLE IF NOT EXISTS system_job (
    job_id          BIGSERIAL PRIMARY KEY,
    job_name        VARCHAR(64) NOT NULL,
    job_group       VARCHAR(64) DEFAULT 'DEFAULT',
    invoke_target   VARCHAR(500) NOT NULL,
    cron_expression VARCHAR(255) DEFAULT '',
    misfire_policy  VARCHAR(20) DEFAULT '3',
    concurrent      CHAR(1) DEFAULT '1',
    status          CHAR(1) DEFAULT '0',
    create_by       VARCHAR(64) DEFAULT '',
    create_time     TIMESTAMP DEFAULT NULL,
    update_by       VARCHAR(64) DEFAULT '',
    update_time     TIMESTAMP DEFAULT NULL,
    remark          VARCHAR(500) DEFAULT ''
);
COMMENT ON TABLE system_job IS '定时任务调度表';

CREATE TABLE IF NOT EXISTS system_job_log (
    job_log_id     BIGSERIAL PRIMARY KEY,
    job_name       VARCHAR(64) NOT NULL,
    job_group      VARCHAR(64) DEFAULT 'DEFAULT',
    invoke_target  VARCHAR(500) NOT NULL,
    job_message    VARCHAR(500) DEFAULT '',
    status         CHAR(1) DEFAULT '0',
    exception_info TEXT DEFAULT '',
    start_time     TIMESTAMP DEFAULT NULL,
    stop_time      TIMESTAMP DEFAULT NULL
);
COMMENT ON TABLE system_job_log IS '定时任务调度日志表';

CREATE TABLE IF NOT EXISTS system_notice (
    notice_id      BIGSERIAL PRIMARY KEY,
    notice_title   VARCHAR(50) NOT NULL,
    notice_type    CHAR(1) NOT NULL,
    notice_content TEXT DEFAULT NULL,
    status         CHAR(1) DEFAULT '0',
    create_by      VARCHAR(64) DEFAULT '',
    create_time    TIMESTAMP DEFAULT NULL,
    update_by      VARCHAR(64) DEFAULT '',
    update_time    TIMESTAMP DEFAULT NULL,
    remark         VARCHAR(255) DEFAULT NULL
);
COMMENT ON TABLE system_notice IS '通知公告表';

CREATE TABLE IF NOT EXISTS "system_content" (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(255) DEFAULT NULL,
    content_type  VARCHAR(64) DEFAULT NULL,
    content       TEXT DEFAULT NULL,
    order_num     INT DEFAULT 0,
    status        CHAR(1) DEFAULT '0',
    create_by     VARCHAR(64) DEFAULT '',
    create_time   TIMESTAMP DEFAULT NULL,
    update_by     VARCHAR(64) DEFAULT '',
    update_time   TIMESTAMP DEFAULT NULL,
    remark        VARCHAR(500) DEFAULT NULL
);
COMMENT ON TABLE "system_content" IS '系统内容配置';

CREATE TABLE IF NOT EXISTS message (
    id              BIGSERIAL PRIMARY KEY,
    sender_id       BIGINT DEFAULT NULL,
    receiver_id     BIGINT DEFAULT NULL,
    title           VARCHAR(128) NOT NULL DEFAULT '',
    content         VARCHAR(3072) NOT NULL DEFAULT '',
    category        SMALLINT NOT NULL DEFAULT 0,
    msg_level       SMALLINT NOT NULL DEFAULT 0,
    module          SMALLINT NOT NULL DEFAULT 0,
    entity_type     SMALLINT DEFAULT NULL,
    entity_id       BIGINT DEFAULT NULL,
    entity_url      VARCHAR(256) DEFAULT NULL,
    has_read        SMALLINT DEFAULT 0,
    has_retraction  SMALLINT DEFAULT 0,
    valid_flag      SMALLINT NOT NULL DEFAULT 1,
    del_flag        SMALLINT NOT NULL DEFAULT 0,
    create_by       VARCHAR(32) DEFAULT NULL,
    creator_id      BIGINT DEFAULT NULL,
    create_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    update_by       VARCHAR(32) DEFAULT NULL,
    update_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    updater_id      BIGINT DEFAULT NULL,
    remark          VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE message IS '消息通知表';

CREATE TABLE IF NOT EXISTS gen_table (
    table_id          BIGSERIAL PRIMARY KEY,
    table_name        VARCHAR(200) DEFAULT '',
    table_comment     VARCHAR(500) DEFAULT '',
    sub_table_name    VARCHAR(64) DEFAULT NULL,
    sub_table_fk_name VARCHAR(64) DEFAULT NULL,
    class_name        VARCHAR(100) DEFAULT '',
    tpl_category      VARCHAR(200) DEFAULT 'crud',
    tpl_web_type      VARCHAR(30) DEFAULT '',
    package_name      VARCHAR(100) DEFAULT '',
    module_name       VARCHAR(30) DEFAULT '',
    business_name     VARCHAR(30) DEFAULT '',
    function_name     VARCHAR(50) DEFAULT '',
    function_author   VARCHAR(50) DEFAULT '',
    gen_type          CHAR(1) DEFAULT '0',
    gen_path          VARCHAR(200) DEFAULT '/',
    options           VARCHAR(1000) DEFAULT NULL,
    create_by         VARCHAR(64) DEFAULT '',
    create_time       TIMESTAMP DEFAULT NULL,
    update_by         VARCHAR(64) DEFAULT '',
    update_time       TIMESTAMP DEFAULT NULL,
    remark            VARCHAR(500) DEFAULT NULL
);
COMMENT ON TABLE gen_table IS '代码生成业务表';

CREATE TABLE IF NOT EXISTS gen_table_column (
    column_id      BIGSERIAL PRIMARY KEY,
    table_id       BIGINT DEFAULT NULL,
    column_name    VARCHAR(200) DEFAULT '',
    column_comment VARCHAR(500) DEFAULT '',
    column_type    VARCHAR(100) DEFAULT '',
    java_type      VARCHAR(50) DEFAULT '',
    java_field     VARCHAR(200) DEFAULT '',
    is_pk          CHAR(1) DEFAULT '',
    is_increment   CHAR(1) DEFAULT '',
    is_required    CHAR(1) DEFAULT '',
    is_insert      CHAR(1) DEFAULT '',
    is_edit        CHAR(1) DEFAULT '',
    is_list        CHAR(1) DEFAULT '',
    is_query       CHAR(1) DEFAULT '',
    query_type     VARCHAR(200) DEFAULT 'EQ',
    html_type      VARCHAR(200) DEFAULT '',
    dict_type      VARCHAR(200) DEFAULT '',
    sort           INT DEFAULT 0,
    create_by      VARCHAR(64) DEFAULT '',
    create_time    TIMESTAMP DEFAULT NULL,
    update_by      VARCHAR(64) DEFAULT '',
    update_time    TIMESTAMP DEFAULT NULL
);
COMMENT ON TABLE gen_table_column IS '代码生成业务表字段';

-- ===========================
-- AI Model & API Key Tables
-- ===========================

CREATE TABLE IF NOT EXISTS ai_api_key (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name         VARCHAR(128) DEFAULT NULL,
    api_key      VARCHAR(128) DEFAULT NULL,
    platform     VARCHAR(32) DEFAULT NULL,
    url          VARCHAR(256) DEFAULT NULL,
    platform_tag VARCHAR(256) DEFAULT NULL,
    description  VARCHAR(1024) DEFAULT NULL,
    deploy_type  VARCHAR(32) DEFAULT NULL,
    status       SMALLINT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ai_api_key IS 'API秘钥表';

CREATE TABLE IF NOT EXISTS ai_model (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    key_id       BIGINT DEFAULT NULL,
    name         VARCHAR(128) DEFAULT NULL,
    model        VARCHAR(32) DEFAULT NULL,
    platform     VARCHAR(32) DEFAULT NULL,
    type         SMALLINT DEFAULT NULL,
    sort         INT DEFAULT NULL,
    status       SMALLINT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ai_model IS 'AI模型表';

CREATE TABLE IF NOT EXISTS ai_node_prompt (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name         VARCHAR(128) DEFAULT NULL,
    prompt       TEXT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ai_node_prompt IS '提示词配置表';

CREATE TABLE IF NOT EXISTS ai_workflow (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name         VARCHAR(128) DEFAULT NULL,
    description  VARCHAR(512) DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ai_workflow IS '工作流表';

CREATE TABLE IF NOT EXISTS ai_workflow_node (
    id             BIGSERIAL PRIMARY KEY,
    workspace_id   BIGINT NOT NULL,
    workflow_id    BIGINT DEFAULT NULL,
    name           VARCHAR(128) DEFAULT NULL,
    type           VARCHAR(32) DEFAULT NULL,
    config         TEXT DEFAULT NULL,
    prompt         TEXT DEFAULT NULL,
    model_id       BIGINT DEFAULT NULL,
    temperature    DECIMAL(3,2) DEFAULT NULL,
    max_tokens     INT DEFAULT NULL,
    top_p          DECIMAL(3,2) DEFAULT NULL,
    valid_flag     BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag       BOOLEAN NOT NULL DEFAULT FALSE,
    create_by      VARCHAR(32) DEFAULT NULL,
    creator_id     BIGINT DEFAULT NULL,
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(32) DEFAULT NULL,
    updater_id     BIGINT DEFAULT NULL,
    update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark         VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ai_workflow_node IS '工作流节点表';

CREATE TABLE IF NOT EXISTS ai_workflow_variable (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    workflow_id  BIGINT DEFAULT NULL,
    name         VARCHAR(128) DEFAULT NULL,
    type         SMALLINT DEFAULT NULL,
    value        TEXT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ai_workflow_variable IS '工作流变量表';

-- ===========================
-- Knowledge Base (kmc) Tables
-- ===========================

CREATE TABLE IF NOT EXISTS kmc_category (
    id               BIGSERIAL PRIMARY KEY,
    workspace_id     BIGINT NOT NULL,
    parent_id        BIGINT NOT NULL,
    knowledge_base_id BIGINT DEFAULT NULL,
    name             VARCHAR(128) NOT NULL,
    order_num        INT DEFAULT NULL,
    ancestors        VARCHAR(128) DEFAULT NULL,
    valid_flag       BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag         BOOLEAN NOT NULL DEFAULT FALSE,
    create_by        VARCHAR(32) DEFAULT NULL,
    creator_id       BIGINT DEFAULT NULL,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(32) DEFAULT NULL,
    updater_id       BIGINT DEFAULT NULL,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark           VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kmc_category IS '知识分类';

CREATE TABLE IF NOT EXISTS kmc_knowledge_base (
    id               BIGSERIAL PRIMARY KEY,
    workspace_id     BIGINT NOT NULL,
    qm_dataset_id    VARCHAR(128) DEFAULT NULL,
    name             VARCHAR(128) NOT NULL,
    description      VARCHAR(512) DEFAULT NULL,
    cover_image      VARCHAR(512) DEFAULT NULL,
    indexing_technique VARCHAR(32) DEFAULT 'high_quality',
    permission       VARCHAR(32) DEFAULT 'all_team_members',
    embedding_model  VARCHAR(128) DEFAULT NULL,
    embedding_model_provider VARCHAR(128) DEFAULT NULL,
    search_method    VARCHAR(32) DEFAULT NULL,
    reranking_enable BOOLEAN DEFAULT FALSE,
    reranking_provider_name VARCHAR(128) DEFAULT NULL,
    reranking_model_name VARCHAR(128) DEFAULT NULL,
    reranking_model  VARCHAR(128) DEFAULT NULL,
    top_k            INT DEFAULT 4,
    score_threshold_enabled BOOLEAN DEFAULT TRUE,
    score_threshold  DECIMAL(5,4) DEFAULT 0.5,
    keyword_weight   DECIMAL(5,4) DEFAULT 0.3,
    vector_weight    DECIMAL(5,4) DEFAULT 0.7,
    reranking_mode   VARCHAR(32) DEFAULT NULL,
    tags             VARCHAR(512) DEFAULT NULL,
    valid_flag       SMALLINT NOT NULL DEFAULT 0,
    del_flag         SMALLINT NOT NULL DEFAULT 0,
    create_by        VARCHAR(32) DEFAULT NULL,
    creator_id       BIGINT DEFAULT NULL,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(32) DEFAULT NULL,
    updater_id       BIGINT DEFAULT NULL,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark           VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kmc_knowledge_base IS '知识库表';

CREATE TABLE IF NOT EXISTS kmc_document (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        BIGINT NOT NULL,
    category_id         BIGINT NOT NULL,
    knowledge_base_id   BIGINT DEFAULT NULL,
    knowledge_base_name VARCHAR(256) DEFAULT NULL,
    category_name       VARCHAR(128) DEFAULT NULL,
    name                VARCHAR(256) NOT NULL,
    path                VARCHAR(1024) NOT NULL,
    description         VARCHAR(1024) DEFAULT NULL,
    preview_count       BIGINT DEFAULT 0,
    download_count      BIGINT DEFAULT 0,
    sync_status         SMALLINT DEFAULT 0,
    mode                VARCHAR(32) DEFAULT NULL,
    parent_mode         VARCHAR(32) DEFAULT NULL,
    remove_extra_spaces BOOLEAN DEFAULT NULL,
    remove_urls_emails  BOOLEAN DEFAULT NULL,
    chunk_overlap       VARCHAR(32) DEFAULT NULL,
    max_tokens          INT DEFAULT NULL,
    separator           VARCHAR(32) DEFAULT NULL,
    subchunk_max_tokens INT DEFAULT NULL,
    subchunk_separator  VARCHAR(256) DEFAULT NULL,
    doc_form            VARCHAR(32) DEFAULT NULL,
    doc_language        VARCHAR(32) DEFAULT NULL,
    chat_model          VARCHAR(128) DEFAULT NULL,
    chat_model_provider VARCHAR(128) DEFAULT NULL,
    valid_flag          BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag            BOOLEAN NOT NULL DEFAULT FALSE,
    create_by           VARCHAR(32) DEFAULT NULL,
    creator_id          BIGINT DEFAULT NULL,
    create_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by           VARCHAR(32) DEFAULT NULL,
    updater_id          BIGINT DEFAULT NULL,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark              VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kmc_document IS '知识文件表';

CREATE TABLE IF NOT EXISTS kmc_document_log (
    id             BIGSERIAL PRIMARY KEY,
    workspace_id   BIGINT NOT NULL,
    user_id        BIGINT DEFAULT NULL,
    user_name      VARCHAR(256) DEFAULT NULL,
    document_id    INT DEFAULT NULL,
    document_name  VARCHAR(256) DEFAULT NULL,
    type           SMALLINT DEFAULT NULL,
    valid_flag     BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag       BOOLEAN NOT NULL DEFAULT FALSE,
    create_by      VARCHAR(32) DEFAULT NULL,
    creator_id     BIGINT DEFAULT NULL,
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(32) DEFAULT NULL,
    updater_id     BIGINT DEFAULT NULL,
    update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark         VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kmc_document_log IS '文件操作日志';

CREATE TABLE IF NOT EXISTS kmc_document_segment (
    id               BIGSERIAL PRIMARY KEY,
    workspace_id     BIGINT NOT NULL,
    document_name    VARCHAR(256) NOT NULL,
    document_id      BIGINT NOT NULL,
    qm_segment_id    VARCHAR(256) DEFAULT NULL,
    position         INT DEFAULT NULL,
    qm_document_id   VARCHAR(256) DEFAULT NULL,
    content          TEXT DEFAULT NULL,
    sign_content     TEXT DEFAULT NULL,
    answer           TEXT DEFAULT NULL,
    word_count       INT DEFAULT NULL,
    tokens           INT DEFAULT NULL,
    keywords         TEXT DEFAULT NULL,
    index_node_id    VARCHAR(256) DEFAULT NULL,
    index_node_hash  VARCHAR(256) DEFAULT NULL,
    hit_count        INT DEFAULT NULL,
    enabled          BOOLEAN DEFAULT NULL,
    status           VARCHAR(32) DEFAULT NULL,
    completed_at     VARCHAR(32) DEFAULT NULL,
    error            VARCHAR(256) DEFAULT NULL,
    child_chunks     TEXT DEFAULT NULL,
    sync_status      SMALLINT DEFAULT 0,
    parent_id        VARCHAR(128) DEFAULT NULL,
    valid_flag       BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag         BOOLEAN NOT NULL DEFAULT FALSE,
    create_by        VARCHAR(32) DEFAULT NULL,
    creator_id       BIGINT DEFAULT NULL,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(32) DEFAULT NULL,
    updater_id       BIGINT DEFAULT NULL,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark           VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kmc_document_segment IS '文件分段表';
CREATE INDEX IF NOT EXISTS idx_segment_document_id ON kmc_document_segment(document_id);

CREATE TABLE IF NOT EXISTS kmc_knowledge_recall_log (
    id               BIGSERIAL PRIMARY KEY,
    workspace_id     BIGINT NOT NULL,
    knowledge_base_id BIGINT DEFAULT NULL,
    query            TEXT DEFAULT NULL,
    result           TEXT DEFAULT NULL,
    recall_time      TIMESTAMP DEFAULT NULL,
    valid_flag       BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag         BOOLEAN NOT NULL DEFAULT FALSE,
    create_by        VARCHAR(32) DEFAULT NULL,
    creator_id       BIGINT DEFAULT NULL,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(32) DEFAULT NULL,
    updater_id       BIGINT DEFAULT NULL,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark           VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kmc_knowledge_recall_log IS '召回记录表';

CREATE TABLE IF NOT EXISTS kmc_knowledge_role (
    id               BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT DEFAULT NULL,
    role_id          BIGINT DEFAULT NULL,
    valid_flag       BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag         BOOLEAN NOT NULL DEFAULT FALSE,
    create_by        VARCHAR(32) DEFAULT NULL,
    creator_id       BIGINT DEFAULT NULL,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(32) DEFAULT NULL,
    updater_id       BIGINT DEFAULT NULL,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark           VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kmc_knowledge_role IS '知识库角色关联表';

CREATE TABLE IF NOT EXISTS kmc_sync (
    id               BIGSERIAL PRIMARY KEY,
    workspace_id     BIGINT NOT NULL,
    document_id      BIGINT DEFAULT NULL,
    knowledge_base_id BIGINT DEFAULT NULL,
    status           SMALLINT DEFAULT 0,
    error_msg        TEXT DEFAULT NULL,
    valid_flag       BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag         BOOLEAN NOT NULL DEFAULT FALSE,
    create_by        VARCHAR(32) DEFAULT NULL,
    creator_id       BIGINT DEFAULT NULL,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(32) DEFAULT NULL,
    updater_id       BIGINT DEFAULT NULL,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark           VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kmc_sync IS '文件同步表';

-- ===========================
-- Knowledge Graph (kg) Tables
-- ===========================

CREATE TABLE IF NOT EXISTS kg_knowledge_category (
    id               BIGSERIAL PRIMARY KEY,
    workspace_id     BIGINT NOT NULL,
    parent_id        BIGINT NOT NULL,
    name             VARCHAR(128) NOT NULL,
    order_num        INT DEFAULT NULL,
    ancestors        VARCHAR(128) DEFAULT NULL,
    valid_flag       BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag         BOOLEAN NOT NULL DEFAULT FALSE,
    create_by        VARCHAR(32) DEFAULT NULL,
    creator_id       BIGINT DEFAULT NULL,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(32) DEFAULT NULL,
    updater_id       BIGINT DEFAULT NULL,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark           VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kg_knowledge_category IS '知识图谱分类';

CREATE TABLE IF NOT EXISTS kg_knowledge_document (
    id               BIGSERIAL PRIMARY KEY,
    workspace_id     BIGINT NOT NULL,
    category_id      BIGINT NOT NULL,
    name             VARCHAR(256) NOT NULL,
    path             VARCHAR(1024) NOT NULL,
    description      VARCHAR(1024) DEFAULT NULL,
    valid_flag       BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag         BOOLEAN NOT NULL DEFAULT FALSE,
    create_by        VARCHAR(32) DEFAULT NULL,
    creator_id       BIGINT DEFAULT NULL,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(32) DEFAULT NULL,
    updater_id       BIGINT DEFAULT NULL,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark           VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kg_knowledge_document IS '知识图谱文件表';

CREATE TABLE IF NOT EXISTS kg_knowledge_document_log (
    id             BIGSERIAL PRIMARY KEY,
    workspace_id   BIGINT NOT NULL,
    user_id        BIGINT DEFAULT NULL,
    user_name      VARCHAR(256) DEFAULT NULL,
    document_id    INT DEFAULT NULL,
    document_name  VARCHAR(256) DEFAULT NULL,
    type           SMALLINT DEFAULT NULL,
    valid_flag     BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag       BOOLEAN NOT NULL DEFAULT FALSE,
    create_by      VARCHAR(32) DEFAULT NULL,
    creator_id     BIGINT DEFAULT NULL,
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(32) DEFAULT NULL,
    updater_id     BIGINT DEFAULT NULL,
    update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark         VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kg_knowledge_document_log IS '知识图谱文件日志';

-- ===========================
-- Bot / Agent / Workflow (kb) Tables
-- ===========================

CREATE TABLE IF NOT EXISTS kb_bot (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name         VARCHAR(128) NOT NULL,
    type         SMALLINT NOT NULL,
    description  VARCHAR(256) DEFAULT NULL,
    builtin_flag SMALLINT NOT NULL DEFAULT 0,
    valid_flag   SMALLINT NOT NULL DEFAULT 0,
    del_flag     SMALLINT NOT NULL DEFAULT 0,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kb_bot IS 'Bot管理表';

CREATE TABLE IF NOT EXISTS kb_agent_config (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    bot_id          BIGINT DEFAULT NULL,
    model_config    TEXT DEFAULT NULL,
    pre_prompt      TEXT DEFAULT NULL,
    parameters      TEXT DEFAULT NULL,
    knowledge_ids   VARCHAR(128) DEFAULT NULL,
    graph_ids       VARCHAR(128) DEFAULT NULL,
    tool_method_ids VARCHAR(128) DEFAULT NULL,
    valid_flag      BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag        BOOLEAN NOT NULL DEFAULT FALSE,
    create_by       VARCHAR(32) DEFAULT NULL,
    creator_id      BIGINT DEFAULT NULL,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(32) DEFAULT NULL,
    updater_id      BIGINT DEFAULT NULL,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark          VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kb_agent_config IS 'Agent配置表';

CREATE TABLE IF NOT EXISTS kb_bot_apikey (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT DEFAULT NULL,
    bot_id       BIGINT NOT NULL,
    api_key      VARCHAR(64) NOT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kb_bot_apikey IS 'Bot访问密钥表';

CREATE TABLE IF NOT EXISTS kb_code_native (
    id            BIGSERIAL PRIMARY KEY,
    workspace_id  BIGINT DEFAULT NULL,
    bot_id        BIGINT NOT NULL,
    class_name    VARCHAR(128) NOT NULL,
    param_schema  TEXT DEFAULT NULL,
    return_schema TEXT DEFAULT NULL,
    code          TEXT DEFAULT NULL,
    valid_flag    BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag      BOOLEAN NOT NULL DEFAULT FALSE,
    create_by     VARCHAR(32) DEFAULT NULL,
    creator_id    BIGINT DEFAULT NULL,
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by     VARCHAR(32) DEFAULT NULL,
    updater_id    BIGINT DEFAULT NULL,
    update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark        VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kb_code_native IS '白盒化开发表';

CREATE TABLE IF NOT EXISTS kb_flow_node (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    bot_id       BIGINT NOT NULL,
    uuid         VARCHAR(128) NOT NULL,
    name         VARCHAR(128) DEFAULT NULL,
    type         SMALLINT NOT NULL,
    config       TEXT DEFAULT NULL,
    style        TEXT NOT NULL,
    input        TEXT DEFAULT NULL,
    output       TEXT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kb_flow_node IS 'Bot流程节点表';

CREATE TABLE IF NOT EXISTS kb_flow_edge (
    id               BIGSERIAL PRIMARY KEY,
    workspace_id     BIGINT NOT NULL,
    bot_id           BIGINT NOT NULL,
    source_node_uuid VARCHAR(128) NOT NULL,
    target_node_uuid VARCHAR(128) NOT NULL,
    source_handle    VARCHAR(128) DEFAULT NULL,
    style            TEXT NOT NULL,
    valid_flag       BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag         BOOLEAN NOT NULL DEFAULT FALSE,
    create_by        VARCHAR(32) DEFAULT NULL,
    creator_id       BIGINT DEFAULT NULL,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(32) DEFAULT NULL,
    updater_id       BIGINT DEFAULT NULL,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark           VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kb_flow_edge IS 'Bot流程关系表';

CREATE TABLE IF NOT EXISTS kb_runtime (
    id             BIGSERIAL PRIMARY KEY,
    workspace_id   BIGINT NOT NULL,
    bot_id         BIGINT NOT NULL,
    input          TEXT DEFAULT NULL,
    output         TEXT DEFAULT NULL,
    status         SMALLINT DEFAULT 0,
    runtime_ms     BIGINT DEFAULT NULL,
    valid_flag     BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag       BOOLEAN NOT NULL DEFAULT FALSE,
    create_by      VARCHAR(32) DEFAULT NULL,
    creator_id     BIGINT DEFAULT NULL,
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(32) DEFAULT NULL,
    updater_id     BIGINT DEFAULT NULL,
    update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark         VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kb_runtime IS 'Bot运行记录表';

CREATE TABLE IF NOT EXISTS kb_runtime_node (
    id             BIGSERIAL PRIMARY KEY,
    workspace_id   BIGINT NOT NULL,
    runtime_id     BIGINT NOT NULL,
    node_uuid      VARCHAR(128) DEFAULT NULL,
    step           INTEGER DEFAULT 0,
    node_name      VARCHAR(128) DEFAULT NULL,
    node_type      SMALLINT DEFAULT NULL,
    input          TEXT DEFAULT NULL,
    output         TEXT DEFAULT NULL,
    status         SMALLINT DEFAULT 0,
    runtime_ms     BIGINT DEFAULT NULL,
    valid_flag     BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag       BOOLEAN NOT NULL DEFAULT FALSE,
    create_by      VARCHAR(32) DEFAULT NULL,
    creator_id     BIGINT DEFAULT NULL,
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(32) DEFAULT NULL,
    updater_id     BIGINT DEFAULT NULL,
    update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark         VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kb_runtime_node IS 'Bot节点运行记录表';

CREATE TABLE IF NOT EXISTS kb_tool (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(512) DEFAULT NULL,
    type         SMALLINT DEFAULT NULL,
    config       TEXT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kb_tool IS '工具管理表';

CREATE TABLE IF NOT EXISTS kb_tool_method (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    tool_id      BIGINT NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(512) DEFAULT NULL,
    param_schema TEXT DEFAULT NULL,
    config       TEXT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE kb_tool_method IS '工具方法表';

-- ===========================
-- Data Management (dm) Tables
-- ===========================

CREATE TABLE IF NOT EXISTS dm_datasource (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name         VARCHAR(128) NOT NULL,
    type         SMALLINT DEFAULT NULL,
    url          VARCHAR(512) DEFAULT NULL,
    username     VARCHAR(128) DEFAULT NULL,
    password     VARCHAR(256) DEFAULT NULL,
    config       TEXT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE dm_datasource IS '数据源表';

-- ===========================
-- Knowledge Extraction (ext) Tables
-- ===========================

CREATE TABLE IF NOT EXISTS ext_schema (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(512) DEFAULT NULL,
    status       SMALLINT DEFAULT 0,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ext_schema IS '概念配置';

CREATE TABLE IF NOT EXISTS ext_schema_attribute (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    schema_id    BIGINT DEFAULT NULL,
    name         VARCHAR(128) NOT NULL,
    type         SMALLINT DEFAULT NULL,
    description  VARCHAR(512) DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ext_schema_attribute IS '概念属性';

CREATE TABLE IF NOT EXISTS ext_schema_relation (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    schema_id       BIGINT DEFAULT NULL,
    name            VARCHAR(128) NOT NULL,
    source_schema_id BIGINT DEFAULT NULL,
    target_schema_id BIGINT DEFAULT NULL,
    description     VARCHAR(512) DEFAULT NULL,
    valid_flag      BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag        BOOLEAN NOT NULL DEFAULT FALSE,
    create_by       VARCHAR(32) DEFAULT NULL,
    creator_id      BIGINT DEFAULT NULL,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(32) DEFAULT NULL,
    updater_id      BIGINT DEFAULT NULL,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark          VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ext_schema_relation IS '关系配置';

CREATE TABLE IF NOT EXISTS ext_schema_mapping (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    schema_id    BIGINT DEFAULT NULL,
    source       VARCHAR(128) DEFAULT NULL,
    target       VARCHAR(128) DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ext_schema_mapping IS '概念映射';

CREATE TABLE IF NOT EXISTS ext_struct_task (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name         VARCHAR(128) NOT NULL,
    schema_id    BIGINT DEFAULT NULL,
    status       SMALLINT DEFAULT 0,
    config       TEXT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ext_struct_task IS '结构化抽取任务';

CREATE TABLE IF NOT EXISTS ext_unstruct_task (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name         VARCHAR(128) NOT NULL,
    schema_id    BIGINT DEFAULT NULL,
    status       SMALLINT DEFAULT 0,
    config       TEXT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ext_unstruct_task IS '非结构化抽取任务';

CREATE TABLE IF NOT EXISTS ext_unstruct_task_doc_rel (
    id           BIGSERIAL PRIMARY KEY,
    task_id      BIGINT DEFAULT NULL,
    document_id  BIGINT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ext_unstruct_task_doc_rel IS '任务文件关联表';

CREATE TABLE IF NOT EXISTS ext_unstruct_task_relation (
    id           BIGSERIAL PRIMARY KEY,
    task_id      BIGINT DEFAULT NULL,
    relation_id  BIGINT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ext_unstruct_task_relation IS '任务关系关联表';

CREATE TABLE IF NOT EXISTS ext_unstruct_task_text (
    id           BIGSERIAL PRIMARY KEY,
    task_id      BIGINT DEFAULT NULL,
    document_id  BIGINT DEFAULT NULL,
    content      TEXT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ext_unstruct_task_text IS '任务文件段落关联表';

CREATE TABLE IF NOT EXISTS ext_attribute_mapping (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    attribute_id BIGINT DEFAULT NULL,
    source       VARCHAR(128) DEFAULT NULL,
    target       VARCHAR(128) DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ext_attribute_mapping IS '属性映射';

CREATE TABLE IF NOT EXISTS ext_relation_mapping (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    relation_id  BIGINT DEFAULT NULL,
    source       VARCHAR(128) DEFAULT NULL,
    target       VARCHAR(128) DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ext_relation_mapping IS '关系映射';

CREATE TABLE IF NOT EXISTS ext_relation_mapping_middle (
    id           BIGSERIAL PRIMARY KEY,
    mapping_id   BIGINT DEFAULT NULL,
    source       VARCHAR(128) DEFAULT NULL,
    target       VARCHAR(128) DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ext_relation_mapping_middle IS '关系映射中间表';

CREATE TABLE IF NOT EXISTS ext_task_log (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    task_id      BIGINT DEFAULT NULL,
    task_type    VARCHAR(32) DEFAULT NULL,
    status       SMALLINT DEFAULT 0,
    error_msg    TEXT DEFAULT NULL,
    start_time   TIMESTAMP DEFAULT NULL,
    end_time     TIMESTAMP DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ext_task_log IS '抽取任务执行日志';

CREATE TABLE IF NOT EXISTS ext_task_log_detail (
    id           BIGSERIAL PRIMARY KEY,
    log_id       BIGINT DEFAULT NULL,
    entity_type  VARCHAR(32) DEFAULT NULL,
    entity_name  VARCHAR(128) DEFAULT NULL,
    content      TEXT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ext_task_log_detail IS '抽取任务执行日志详情';

-- ===========================
-- Stage 1 New Tables: Conversation & Message for Agent Chat
-- ===========================

CREATE TABLE IF NOT EXISTS conversation (
    id               BIGSERIAL PRIMARY KEY,
    workspace_id     BIGINT NOT NULL,
    bot_id           BIGINT NOT NULL,
    user_id          BIGINT DEFAULT NULL,
    title            VARCHAR(256) DEFAULT NULL,
    status           SMALLINT DEFAULT 0,
    valid_flag       BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag         BOOLEAN NOT NULL DEFAULT FALSE,
    create_by        VARCHAR(32) DEFAULT NULL,
    creator_id       BIGINT DEFAULT NULL,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(32) DEFAULT NULL,
    updater_id       BIGINT DEFAULT NULL,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark           VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE conversation IS 'Agent对话表';

CREATE TABLE IF NOT EXISTS chat_message (
    id               BIGSERIAL PRIMARY KEY,
    conversation_id  BIGINT NOT NULL,
    role             VARCHAR(32) NOT NULL,
    content          TEXT DEFAULT NULL,
    token_count      INT DEFAULT NULL,
    metadata         JSONB DEFAULT NULL,
    valid_flag       BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag         BOOLEAN NOT NULL DEFAULT FALSE,
    create_by        VARCHAR(32) DEFAULT NULL,
    creator_id       BIGINT DEFAULT NULL,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(32) DEFAULT NULL,
    updater_id       BIGINT DEFAULT NULL,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark           VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE chat_message IS '对话消息表';
CREATE INDEX IF NOT EXISTS idx_chat_message_conv ON chat_message(conversation_id);

-- ===========================
-- AI Judge Score Table (Stage 5 prep)
-- ===========================

CREATE TABLE IF NOT EXISTS ai_judge_score (
    id               BIGSERIAL PRIMARY KEY,
    workspace_id     BIGINT NOT NULL,
    conversation_id  BIGINT DEFAULT NULL,
    message_id       BIGINT DEFAULT NULL,
    factuality_score DECIMAL(3,2) DEFAULT NULL,
    relevance_score  DECIMAL(3,2) DEFAULT NULL,
    instruction_score DECIMAL(3,2) DEFAULT NULL,
    overall_score    DECIMAL(3,2) DEFAULT NULL,
    passed           BOOLEAN DEFAULT NULL,
    retry_count      INT DEFAULT 0,
    detail           JSONB DEFAULT NULL,
    valid_flag       BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag         BOOLEAN NOT NULL DEFAULT FALSE,
    create_by        VARCHAR(32) DEFAULT NULL,
    creator_id       BIGINT DEFAULT NULL,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(32) DEFAULT NULL,
    updater_id       BIGINT DEFAULT NULL,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark           VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE ai_judge_score IS 'AI Judge评分记录表';

-- ===========================
-- Workflow Tables (Stage 4 prep)
-- ===========================

CREATE TABLE IF NOT EXISTS workflow (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(512) DEFAULT NULL,
    status       SMALLINT DEFAULT 0,
    version      INT DEFAULT 1,
    config       JSONB DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE workflow IS '工作流定义表';

CREATE TABLE IF NOT EXISTS workflow_node (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    workflow_id  BIGINT NOT NULL,
    uuid         VARCHAR(128) NOT NULL,
    name         VARCHAR(128) DEFAULT NULL,
    type         SMALLINT NOT NULL,
    config       JSONB DEFAULT NULL,
    position_x   DECIMAL(10,2) DEFAULT NULL,
    position_y   DECIMAL(10,2) DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE workflow_node IS '工作流节点表';

CREATE TABLE IF NOT EXISTS workflow_edge (
    id               BIGSERIAL PRIMARY KEY,
    workspace_id     BIGINT NOT NULL,
    workflow_id      BIGINT NOT NULL,
    source_node_uuid VARCHAR(128) NOT NULL,
    target_node_uuid VARCHAR(128) NOT NULL,
    source_handle    VARCHAR(128) DEFAULT NULL,
    condition_expr   TEXT DEFAULT NULL,
    valid_flag       BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag         BOOLEAN NOT NULL DEFAULT FALSE,
    create_by        VARCHAR(32) DEFAULT NULL,
    creator_id       BIGINT DEFAULT NULL,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by        VARCHAR(32) DEFAULT NULL,
    updater_id       BIGINT DEFAULT NULL,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark           VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE workflow_edge IS '工作流边表';

CREATE TABLE IF NOT EXISTS workflow_run (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    workflow_id  BIGINT NOT NULL,
    status       SMALLINT DEFAULT 0,
    input        JSONB DEFAULT NULL,
    output       JSONB DEFAULT NULL,
    runtime_ms   BIGINT DEFAULT NULL,
    error_msg    TEXT DEFAULT NULL,
    valid_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    del_flag     BOOLEAN NOT NULL DEFAULT FALSE,
    create_by    VARCHAR(32) DEFAULT NULL,
    creator_id   BIGINT DEFAULT NULL,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by    VARCHAR(32) DEFAULT NULL,
    updater_id   BIGINT DEFAULT NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark       VARCHAR(512) DEFAULT NULL
);
COMMENT ON TABLE workflow_run IS '工作流运行记录表';

-- ===========================
-- PgVector Store Table (auto-created by Spring AI, but explicit for clarity)
-- ===========================

CREATE TABLE IF NOT EXISTS vector_store (
    id        VARCHAR(255) PRIMARY KEY,
    content   TEXT,
    metadata  JSONB,
    embedding vector(1536)
);
COMMENT ON TABLE vector_store IS 'Spring AI PgVector向量存储表';

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- ===========================
-- Knowledge Graph Tables (PostgreSQL 邻接表方案)
-- ===========================

CREATE TABLE IF NOT EXISTS kg_node (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL DEFAULT 1001,
    label        VARCHAR(128) NOT NULL,
    type         VARCHAR(64) DEFAULT 'concept',
    properties   JSONB DEFAULT '{}',
    valid_flag   SMALLINT NOT NULL DEFAULT 0,
    del_flag     SMALLINT NOT NULL DEFAULT 0,
    create_by    VARCHAR(32) DEFAULT 'admin',
    creator_id   BIGINT,
    create_time  TIMESTAMP NOT NULL DEFAULT NOW(),
    update_by    VARCHAR(32) DEFAULT 'admin',
    updater_id   BIGINT,
    update_time  TIMESTAMP NOT NULL DEFAULT NOW(),
    remark       VARCHAR(512)
);
COMMENT ON TABLE kg_node IS '知识图谱节点表';

CREATE INDEX IF NOT EXISTS idx_kg_node_workspace ON kg_node(workspace_id);
CREATE INDEX IF NOT EXISTS idx_kg_node_type ON kg_node(type);

CREATE TABLE IF NOT EXISTS kg_edge (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL DEFAULT 1001,
    source_id    BIGINT NOT NULL,
    target_id    BIGINT NOT NULL,
    label        VARCHAR(128) DEFAULT 'related_to',
    properties   JSONB DEFAULT '{}',
    valid_flag   SMALLINT NOT NULL DEFAULT 0,
    del_flag     SMALLINT NOT NULL DEFAULT 0,
    create_by    VARCHAR(32) DEFAULT 'admin',
    creator_id   BIGINT,
    create_time  TIMESTAMP NOT NULL DEFAULT NOW(),
    update_by    VARCHAR(32) DEFAULT 'admin',
    updater_id   BIGINT,
    update_time  TIMESTAMP NOT NULL DEFAULT NOW(),
    remark       VARCHAR(512)
);
COMMENT ON TABLE kg_edge IS '知识图谱边表';

CREATE INDEX IF NOT EXISTS idx_kg_edge_workspace ON kg_edge(workspace_id);
CREATE INDEX IF NOT EXISTS idx_kg_edge_source ON kg_edge(source_id);
CREATE INDEX IF NOT EXISTS idx_kg_edge_target ON kg_edge(target_id);
-- Hermes DAG checkpoint state
CREATE TABLE IF NOT EXISTS dag_checkpoints (
    runtime_id VARCHAR(255) PRIMARY KEY,
    flow_id VARCHAR(255) NOT NULL,
    group_index INT NOT NULL,
    completed_results TEXT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT
);

-- KMC temporal facts for GraphRAG/Graphiti-style validity windows
CREATE TABLE IF NOT EXISTS kmc_temporal_facts (
    id BIGSERIAL PRIMARY KEY,
    segment_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    entity_name VARCHAR(255) NOT NULL,
    fact_content TEXT,
    valid_from BIGINT,
    valid_until BIGINT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT,
    UNIQUE(segment_id, entity_name)
);
CREATE INDEX IF NOT EXISTS idx_temporal_facts_entity ON kmc_temporal_facts(entity_name);
CREATE INDEX IF NOT EXISTS idx_temporal_facts_validity ON kmc_temporal_facts(valid_from, valid_until);
