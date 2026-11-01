\set ON_ERROR_STOP on
BEGIN;

DO $$
BEGIN
    IF current_database() !~ '^h11b2a_fixture_[a-z0-9_]+$' THEN
        RAISE EXCEPTION '11B-2A fixture requires an isolated h11b2a_fixture_* database';
    END IF;
END $$;

CREATE SCHEMA h11b2a;

CREATE TABLE h11b2a.knowledge_base (
    id bigint PRIMARY KEY,
    name text NOT NULL
);

CREATE TABLE h11b2a.task (
    id text PRIMARY KEY,
    split text NOT NULL CHECK (split IN ('BOOTSTRAP', 'HOLDOUT')),
    category text NOT NULL CHECK (category IN ('PROMPT_ONLY', 'RAG', 'TOOL', 'HISTORY', 'COMPOSITE')),
    knowledge_base_id bigint NOT NULL REFERENCES h11b2a.knowledge_base(id),
    question text NOT NULL,
    retrieval_query text,
    rubric jsonb NOT NULL,
    CHECK ((category IN ('RAG', 'COMPOSITE')) = (retrieval_query IS NOT NULL))
);

CREATE TABLE h11b2a.document (
    id text PRIMARY KEY,
    knowledge_base_id bigint NOT NULL REFERENCES h11b2a.knowledge_base(id),
    split text NOT NULL CHECK (split IN ('BOOTSTRAP', 'HOLDOUT')),
    title text NOT NULL
);

CREATE TABLE h11b2a.segment (
    id text PRIMARY KEY,
    document_id text NOT NULL REFERENCES h11b2a.document(id),
    body text NOT NULL,
    search_terms text NOT NULL,
    search_index tsvector GENERATED ALWAYS AS (to_tsvector('simple', search_terms)) STORED
);

CREATE INDEX segment_search_idx ON h11b2a.segment USING gin (search_index);

CREATE TABLE h11b2a.tool (
    name text PRIMARY KEY,
    description text NOT NULL,
    input_schema jsonb NOT NULL
);

CREATE TABLE h11b2a.tool_result (
    id text PRIMARY KEY,
    tool_name text NOT NULL REFERENCES h11b2a.tool(name),
    split text NOT NULL CHECK (split IN ('BOOTSTRAP', 'HOLDOUT')),
    input_args jsonb NOT NULL,
    output_value jsonb NOT NULL
);

CREATE TABLE h11b2a.task_tool (
    task_id text NOT NULL REFERENCES h11b2a.task(id),
    tool_result_id text NOT NULL REFERENCES h11b2a.tool_result(id),
    PRIMARY KEY (task_id, tool_result_id)
);

CREATE TABLE h11b2a.history_message (
    source_id text PRIMARY KEY,
    task_id text NOT NULL REFERENCES h11b2a.task(id),
    turn_no integer NOT NULL CHECK (turn_no > 0),
    role text NOT NULL CHECK (role IN ('user', 'assistant')),
    content text NOT NULL,
    UNIQUE (task_id, turn_no)
);

CREATE VIEW h11b2a.retrieval_hit AS
SELECT t.id AS task_id, t.split, t.retrieval_query,
       d.id AS document_id, s.id AS segment_id,
       ts_rank_cd(s.search_index, plainto_tsquery('simple', t.retrieval_query)) AS rank,
       s.body AS retrieved_body
FROM h11b2a.task t
JOIN h11b2a.document d ON d.knowledge_base_id = t.knowledge_base_id AND d.split = t.split
JOIN h11b2a.segment s ON s.document_id = d.id
WHERE t.retrieval_query IS NOT NULL
  AND s.search_index @@ plainto_tsquery('simple', t.retrieval_query);

INSERT INTO h11b2a.knowledge_base VALUES
    (110201, 'Harness 11B-2A controlled corpus');

INSERT INTO h11b2a.document VALUES
    ('doc-b-payment', 110201, 'BOOTSTRAP', 'Gateway retry and idempotency'),
    ('doc-b-mesh', 110201, 'BOOTSTRAP', 'Mesh architecture comparison'),
    ('doc-h-webhook', 110201, 'HOLDOUT', 'Webhook signature verification'),
    ('doc-h-queue', 110201, 'HOLDOUT', 'Queue failover procedure');

INSERT INTO h11b2a.segment (id, document_id, body, search_terms) VALUES
    ('seg-b-payment-01', 'doc-b-payment',
     '网关超时最多重试 3 次，退避间隔依次为 1000、2000、4000 ms。Idempotency-Key 是订单号、商户号和支付请求流水拼接后的 SHA-256 摘要。',
     'gateway retry idempotency payment'),
    ('seg-b-payment-02', 'doc-b-payment',
     '网关路由按商户区域选择主备通道；本段不规定超时重试。',
     'gateway routing settlement'),
    ('seg-b-mesh-01', 'doc-b-mesh',
     '对比单体、Sidecar Mesh 和 eBPF Mesh 时必须同时列出 P99 延迟与 CPU 占用；eBPF 路径可绕过 Sidecar 用户态转发。',
     'mesh ebpf baseline architecture'),
    ('seg-b-mesh-02', 'doc-b-mesh',
     'Sidecar 证书轮换属于独立安全流程，不代表数据面性能基线。',
     'mesh sidecar security'),
    ('seg-h-webhook-01', 'doc-h-webhook',
     'Webhook 使用 HMAC-SHA256 验签；时间戳允许偏差最多 5 分钟，nonce 在该窗口内只能使用一次。',
     'webhook signature replay hmac'),
    ('seg-h-webhook-02', 'doc-h-webhook',
     'Webhook 失败投递进入重试队列；本段不定义签名校验。',
     'webhook delivery retry'),
    ('seg-h-queue-01', 'doc-h-queue',
     '主队列故障时先停止写入 primary，再切换 standby；必须核对 P99 和待处理 depth，确认消费游标连续后恢复写入。',
     'queue failover recovery standby'),
    ('seg-h-queue-02', 'doc-h-queue',
     '归档队列保留期为 30 天，与主备故障切换无关。',
     'queue retention archive');

INSERT INTO h11b2a.tool VALUES
    ('transaction_lookup', '只读查询受控交易流水',
     '{"type":"object","properties":{"transactionId":{"type":"string"}},"required":["transactionId"],"additionalProperties":false}'),
    ('architecture_metrics', '只读查询受控集群架构指标',
     '{"type":"object","properties":{"clusterId":{"type":"string"}},"required":["clusterId"],"additionalProperties":false}'),
    ('queue_metrics', '只读查询受控队列指标',
     '{"type":"object","properties":{"queueId":{"type":"string"}},"required":["queueId"],"additionalProperties":false}');

INSERT INTO h11b2a.tool_result VALUES
    ('tool-b-transaction', 'transaction_lookup', 'BOOTSTRAP', '{"transactionId":"TX-FIX-B-001"}',
     '{"transactionId":"TX-FIX-B-001","status":"SUCCESS","accountId":"ACC-FIX-B-01","amount":"5800.00","receiptIssued":false}'),
    ('tool-h-transaction', 'transaction_lookup', 'HOLDOUT', '{"transactionId":"TX-FIX-H-001"}',
     '{"transactionId":"TX-FIX-H-001","status":"PENDING","accountId":"ACC-FIX-H-02","amount":"120.00","receiptIssued":false}'),
    ('tool-b-mesh', 'architecture_metrics', 'BOOTSTRAP', '{"clusterId":"CLUSTER-FIX-B"}',
     '{"clusterId":"CLUSTER-FIX-B","monolith":{"p99Ms":12,"cpuPct":25},"sidecar":{"p99Ms":17,"cpuPct":38},"ebpf":{"p99Ms":13,"cpuPct":29}}'),
    ('tool-h-queue', 'queue_metrics', 'HOLDOUT', '{"queueId":"QUEUE-FIX-H"}',
     '{"queueId":"QUEUE-FIX-H","primary":{"p99Ms":48,"depth":1200,"available":false},"standby":{"p99Ms":19,"depth":40,"available":true}}');

INSERT INTO h11b2a.task VALUES
    ('B01', 'BOOTSTRAP', 'PROMPT_ONLY', 110201,
     '检查计划时间 2026-11-31 09:30 是否为有效 ISO-8601 日期，并说明原因。', NULL,
     '{"answerAllOf":["2026-11-31","30","无效"],"forbid":["创建成功"],"goldSegmentId":null}'),
    ('B02', 'BOOTSTRAP', 'RAG', 110201,
     '依据受控网关规范，说明 gateway retry 的最大次数、三次退避间隔与 Idempotency-Key 生成规则。',
     'gateway retry',
     '{"answerAllOf":["3","1000","2000","4000","SHA-256"],"forbid":["重试次数无限"],"goldSegmentId":"seg-b-payment-01"}'),
    ('B03', 'BOOTSTRAP', 'TOOL', 110201,
     '调用 transaction_lookup，使用 transactionId=TX-FIX-B-001 核查状态、账户和是否已发凭证。', NULL,
     '{"answerAllOf":["TX-FIX-B-001","SUCCESS","ACC-FIX-B-01","未"],"forbid":["已发凭证"],"requiredToolResultId":"tool-b-transaction"}'),
    ('B04', 'BOOTSTRAP', 'HISTORY', 110201,
     '结合此前讨论，方案 A 的尾延迟主要来自哪里，方案 B 用什么路径缓解？', NULL,
     '{"answerAllOf":["上下文切换","socket","eBPF"],"forbid":["没有历史"],"requiredHistorySources":["hist-b-01","hist-b-02","hist-b-03","hist-b-04"]}'),
    ('B05', 'BOOTSTRAP', 'COMPOSITE', 110201,
     '依据 mesh ebpf 规范并调用 architecture_metrics，传入 clusterId=CLUSTER-FIX-B，对比单体、Sidecar、eBPF 的 P99 与 CPU。',
     'mesh ebpf',
     '{"answerAllOf":["Sidecar","eBPF","P99","CPU","12","17","13"],"forbid":["未查询"],"goldSegmentId":"seg-b-mesh-01","requiredToolResultId":"tool-b-mesh"}'),
    ('H01', 'HOLDOUT', 'PROMPT_ONLY', 110201,
     '检查时间戳 2026-02-30T10:00:00+08:00 是否有效，并说明日历错误。', NULL,
     '{"answerAllOf":["2026-02-30","28","无效"],"forbid":["创建成功"],"goldSegmentId":null}'),
    ('H02', 'HOLDOUT', 'RAG', 110201,
     '依据受控 webhook signature 规范说明验签算法、时间窗口和 nonce 防重放要求。',
     'webhook signature',
     '{"answerAllOf":["HMAC-SHA256","5","nonce"],"forbid":["不需要HMAC"],"goldSegmentId":"seg-h-webhook-01"}'),
    ('H03', 'HOLDOUT', 'TOOL', 110201,
     '调用 transaction_lookup，使用 transactionId=TX-FIX-H-001 核查当前状态和账户，不要推断交易已完成。', NULL,
     '{"answerAllOf":["TX-FIX-H-001","PENDING","ACC-FIX-H-02"],"forbid":["状态为SUCCESS"],"requiredToolResultId":"tool-h-transaction"}'),
    ('H04', 'HOLDOUT', 'HISTORY', 110201,
     '根据之前约定，缓存迁移失败时应选方案 A 还是 B，并说明如何回滚？', NULL,
     '{"answerAllOf":["方案 B","双写","回滚"],"forbid":["没有历史"],"requiredHistorySources":["hist-h-01","hist-h-02","hist-h-03","hist-h-04"]}'),
    ('H05', 'HOLDOUT', 'COMPOSITE', 110201,
     '依据 queue failover 规范并调用 queue_metrics，传入 queueId=QUEUE-FIX-H，比较 primary/standby 的 P99 与 depth，说明切换顺序。',
     'queue failover',
     '{"answerAllOf":["primary","standby","P99","depth","48","19"],"forbid":["跳过游标校验即可恢复写入"],"goldSegmentId":"seg-h-queue-01","requiredToolResultId":"tool-h-queue"}');

INSERT INTO h11b2a.task_tool VALUES
    ('B03', 'tool-b-transaction'),
    ('B05', 'tool-b-mesh'),
    ('H03', 'tool-h-transaction'),
    ('H05', 'tool-h-queue');

INSERT INTO h11b2a.history_message VALUES
    ('hist-b-01', 'B04', 1, 'user', '方案 A 采用 Envoy Sidecar，尾延迟如何？'),
    ('hist-b-02', 'B04', 2, 'assistant', '额外的内核/用户态上下文切换和双 socket 转发会拉高 P99。'),
    ('hist-b-03', 'B04', 3, 'user', '是否有不经 Sidecar 用户态转发的方案 B？'),
    ('hist-b-04', 'B04', 4, 'assistant', '方案 B 采用 eBPF 数据面旁路，但仍需比较资源开销。'),
    ('hist-h-01', 'H04', 1, 'user', '缓存迁移时方案 A 是直接切读，方案 B 是短期双写。'),
    ('hist-h-02', 'H04', 2, 'assistant', '选择方案 B；先验证新缓存一致性，再切读。'),
    ('hist-h-03', 'H04', 3, 'user', '若一致性校验失败呢？'),
    ('hist-h-04', 'H04', 4, 'assistant', '停止双写并回滚旧缓存读路径，不执行数据硬删除。');

COMMIT;
