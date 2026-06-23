-- qKnow 本机开发看板样例数据
-- 目的：为 /kd/knowledgeAsset、/kd/botOperation、/kd/appOperations 三个驾驶舱提供可展示数据。
-- 脚本可重复执行；会先清理 remark = 'dashboard_sample' 的样例记录，再重新生成。

begin;

delete from kb_runtime where remark = 'dashboard_sample';
delete from ext_task_log where remark = 'dashboard_sample';
delete from ext_unstruct_task where remark = 'dashboard_sample';
delete from kac_solution where remark = 'dashboard_sample';
delete from kac_plugin where remark = 'dashboard_sample';

insert into kac_solution
    (name, description, icon, status, type, workspace_id, valid_flag, del_flag,
     create_by, create_time, update_by, update_time, creator_id, updater_id, remark)
values
    ('水利工程智能问答', '面向水利工程资料的行业知识应用', null, 1, '垂直行业', 1001, 1, 0,
     'admin', now() - interval '1 day', 'admin', now(), 1, 1, 'dashboard_sample'),
    ('合同风险审查助手', '自动识别合同条款风险并生成审查建议', null, 1, '职能辅助', 1001, 1, 0,
     'admin', now() - interval '3 day', 'admin', now(), 1, 1, 'dashboard_sample'),
    ('研发规范检索门户', '聚合研发规范、发布流程与代码检查规则', null, 1, '通用效率', 1001, 1, 0,
     'admin', now() - interval '12 day', 'admin', now(), 1, 1, 'dashboard_sample'),
    ('政务材料生成助手', '面向政务材料撰写与政策依据检索的应用', null, 1, '垂直行业', 1001, 1, 0,
     'admin', now() - interval '40 day', 'admin', now(), 1, 1, 'dashboard_sample');

insert into kac_plugin
    (name, description, type, config, status, workspace_id, valid_flag, del_flag,
     create_by, create_time, update_by, update_time, creator_id, updater_id, remark)
values
    ('多格式文档抽取器', '支持 PDF/Word/Excel 的统一解析插件', '文档处理', '{"parser":"universal"}', 1, 1001, 1, 0,
     'admin', now() - interval '2 day', 'admin', now(), 1, 1, 'dashboard_sample'),
    ('知识库质量巡检器', '检测空切片、低命中切片与异常解析任务', '数据处理', '{"scan":"quality"}', 1, 1001, 1, 0,
     'admin', now() - interval '8 day', 'admin', now(), 1, 1, 'dashboard_sample'),
    ('低频应用提醒器', '识别近 7 日访问偏低的应用并提醒运营', '通用效率', '{"threshold":5}', 0, 1001, 1, 0,
     'admin', now() - interval '95 day', 'admin', now(), 1, 1, 'dashboard_sample');

insert into ext_unstruct_task
    (workspace_id, name, schema_id, status, config, valid_flag, del_flag,
     create_by, creator_id, create_time, update_by, updater_id, update_time, remark)
values
    (1001, '合同文档实体抽取任务', null, 1, '{"source":"合同库"}', 1, 0,
     'admin', 1, now() - interval '6 day', 'admin', 1, now(), 'dashboard_sample'),
    (1001, '政策法规关系抽取任务', null, 1, '{"source":"政策法规库"}', 1, 0,
     'admin', 1, now() - interval '4 day', 'admin', 1, now(), 'dashboard_sample'),
    (1001, '研发规范切片质量复检', null, 2, '{"source":"研发规范库"}', 1, 0,
     'admin', 1, now() - interval '2 day', 'admin', 1, now(), 'dashboard_sample');

with sample_tasks as (
    select id, row_number() over (order by id) as rn
    from ext_unstruct_task
    where remark = 'dashboard_sample'
)
insert into ext_task_log
    (workspace_id, task_id, task_type, status, error_msg, start_time, end_time,
     valid_flag, del_flag, create_by, creator_id, create_time, update_by, updater_id, update_time, remark)
select
    1001,
    id,
    'unstructured_extract',
    case when rn = 3 then 2 else 1 end,
    case when rn = 3 then '样例：发现 3 个低质量切片，等待人工复检' else null end,
    now() - (rn || ' day')::interval,
    now() - (rn || ' day')::interval + interval '12 minute',
    1,
    0,
    'admin',
    1,
    now() - (rn || ' day')::interval,
    'admin',
    1,
    now(),
    'dashboard_sample'
from sample_tasks;

with bots as (
    select id, row_number() over (order by id) as rn
    from kb_bot
    where del_flag = 0
    order by id
    limit 5
), days as (
    select generate_series(0, 29) as day_offset
)
insert into kb_runtime
    (workspace_id, bot_id, input, output, status, runtime_ms, valid_flag, del_flag,
     create_by, creator_id, create_time, update_by, updater_id, update_time, remark)
select
    1001,
    bots.id,
    '样例问题 #' || days.day_offset || '-' || bots.rn,
    '样例回答：已命中知识库并返回结构化答案',
    case when bots.rn = 2 and days.day_offset in (1, 8, 15) then 2 else 1 end,
    case when bots.rn = 2 and days.day_offset in (1, 8, 15) then 6800 else 520 + bots.rn * 120 + days.day_offset * 7 end,
    1,
    0,
    'admin',
    1,
    now() - (days.day_offset || ' day')::interval - (bots.rn || ' hour')::interval,
    'admin',
    1,
    now(),
    'dashboard_sample'
from bots
cross join days
where (days.day_offset + bots.rn) % 3 <> 0;

commit;
