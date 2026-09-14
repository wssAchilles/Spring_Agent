-- ====================================================================
-- Phase 24: 生产级流式事件总线、动态反馈增强与自适应 RAG 策略自进化闭环
-- 包含表：
-- 1. kb_chat_feedback (用户隐式与显式反馈流水表)
-- 2. kb_prompt_evolution_proposal (基于 DeepSeek-R1 链式归因反思的 Prompt 进化候选提案表)
-- ====================================================================

-- 1. 创建反馈记录表
CREATE TABLE IF NOT EXISTS kb_chat_feedback (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) DEFAULT 'anonymous',
    tenant_id BIGINT DEFAULT 1,
    feedback_type VARCHAR(32) NOT NULL, -- UPVOTE, DOWNVOTE, COPY, DWELL, CORRECTION
    rating INT DEFAULT NULL,
    dwell_time_ms BIGINT DEFAULT 0,
    comment TEXT,
    corrected_text TEXT,
    action_type VARCHAR(64), -- 当时采用的检索路由策略 (VECTOR, BM25, HYBRID, GRAPH, MULTI_KB)
    position_idx INT DEFAULT 0, -- 选中文档的展示排序位次
    ips_weight DOUBLE PRECISION DEFAULT 1.0, -- 逆倾向得分加权值
    client_ip VARCHAR(64),
    signature VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kb_feedback_msg ON kb_chat_feedback(message_id);
CREATE INDEX IF NOT EXISTS idx_kb_feedback_conv ON kb_chat_feedback(conversation_id);
CREATE INDEX IF NOT EXISTS idx_kb_feedback_type_time ON kb_chat_feedback(feedback_type, created_at DESC);

-- 2. 创建 Prompt 进化审核提案表
CREATE TABLE IF NOT EXISTS kb_prompt_evolution_proposal (
    id BIGSERIAL PRIMARY KEY,
    proposal_id VARCHAR(64) UNIQUE NOT NULL,
    target_type VARCHAR(32) NOT NULL, -- AGENT, BOT, KNOWLEDGE_BASE
    target_id VARCHAR(64) NOT NULL,
    original_prompt TEXT NOT NULL,
    proposed_prompt TEXT NOT NULL,
    diff_summary TEXT,
    reflection_rationale TEXT, -- DeepSeek-R1 思考归因链条
    negative_sample_count INT DEFAULT 0,
    negative_sample_ids JSONB,
    status VARCHAR(32) DEFAULT 'PENDING_REVIEW', -- PENDING_REVIEW, APPROVED, REJECTED, ROLLED_BACK
    reviewer_id VARCHAR(64),
    review_comment TEXT,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kb_prompt_prop_status ON kb_prompt_evolution_proposal(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_kb_prompt_prop_target ON kb_prompt_evolution_proposal(target_type, target_id);
