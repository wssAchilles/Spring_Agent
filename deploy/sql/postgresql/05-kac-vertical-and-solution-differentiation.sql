-- ==============================================================================
-- 05-kac-vertical-and-solution-differentiation.sql
-- 应用中心深度差异化升级：菜单图标、纵向行业应用独立数据与解决方案治理支持
-- ==============================================================================

-- 1. 更新菜单图标配置
UPDATE system_menu SET icon = 'solution' WHERE menu_id = 2406;
UPDATE system_menu SET icon = 'my-solution' WHERE menu_id = 2409;

-- 2. 扩充 kac_apply 垂直行业属性
ALTER TABLE kac_apply ADD COLUMN IF NOT EXISTS category SMALLINT DEFAULT 0;
COMMENT ON COLUMN kac_apply.category IS '应用大类 (0: 横向通用应用, 1: 纵向行业应用)';

ALTER TABLE kac_apply ADD COLUMN IF NOT EXISTS industry VARCHAR(64) DEFAULT '';
COMMENT ON COLUMN kac_apply.industry IS '所属垂直行业分类 (金融科技、高端制造、智慧医疗等)';

ALTER TABLE kac_apply ADD COLUMN IF NOT EXISTS kpi_metrics JSONB DEFAULT '[]'::jsonb;
COMMENT ON COLUMN kac_apply.kpi_metrics IS '行业关键指标列表 (JSON 数组)';

-- 将原有通用应用显式归类为横向通用 (category = 0)
UPDATE kac_apply SET category = 0 WHERE id <= 100;

-- 3. 注入 8 个高质量垂直行业专属应用 (category = 1)
INSERT INTO kac_apply (
    id, name, description, icon, status, type, tags, workspace_id, category, industry, kpi_metrics,
    execution_mode, input_schema, output_schema, prompt_template, execution_config
) VALUES
(
    101, '金融风控反欺诈中枢', '面向银行与金融机构的交易反欺诈、信贷合规预审与洗钱风险穿透评估系统。', 'Money', 1, '金融',
    '[{"name":"金融科技"},{"name":"反欺诈"},{"name":"合规穿透"}]', 1, 1, '金融科技',
    '[{"label":"欺诈拦截召回率","value":"99.4%"},{"label":"风险研判时延","value":"<15ms"},{"label":"人工复核量减少","value":"68%"}]'::jsonb,
    'DIRECT_PROMPT_RAG',
    '[
      {"name":"transType","label":"交易场景类型","type":"select","required":true,"options":["跨境转账","消费信贷","证券质押","供应链授信"],"placeholder":"请选择交易场景"},
      {"name":"entityInfo","label":"交易主体与流水特征","type":"textarea","required":true,"placeholder":"例如: 单笔金额 500 万，交易对手为新设离岸账户，夜间突发集中流转..."},
      {"name":"riskLevel","label":"期望审计严格等级","type":"select","required":false,"options":["极高监管审计级","日常运营筛查级","事后复盘追溯级"],"placeholder":"选择审计等级"}
    ]'::jsonb,
    '{"outputType": "MARKDOWN"}'::jsonb,
    '请作为顶级金融风控专家，针对交易主体特征进行多维反欺诈研判：\n1. 场景类型：{{transType}}\n2. 主体流水特征：{{entityInfo}}\n3. 审计等级：{{riskLevel}}\n请输出包含【欺诈风险指数】、【洗钱与合规疑点排查】、【处置拦截建议】与【监管报送依据】的结构化金融风控审查报告。',
    '{"maxTokens": 4096, "temperature": 0.2}'::jsonb
),
(
    102, '临床辅助诊疗与质控中枢', '对接权威医学指南与三甲临床知识库，提供鉴别诊断建议、用药禁忌核验与病历内涵质控。', 'FirstAidKit', 1, '医疗',
    '[{"name":"智慧医疗"},{"name":"辅助诊疗"},{"name":"用药质控"}]', 1, 1, '智慧医疗',
    '[{"label":"指南依从合规率","value":"98.7%"},{"label":"配伍禁忌拦截","value":"100%"},{"label":"病历质控覆盖","value":"96%"}]'::jsonb,
    'DIRECT_PROMPT_RAG',
    '[
      {"name":"department","label":"就诊科室","type":"select","required":true,"options":["心内科","呼吸内科","神经内科","内分泌科","普外科"],"placeholder":"选择就诊科室"},
      {"name":"patientProfile","label":"患者主诉与现病史","type":"textarea","required":true,"placeholder":"例如: 患者男性 58 岁，突发胸闷伴左肩放射痛 2 小时，高血压病史 10 年..."},
      {"name":"labResults","label":"关键检查检验结果","type":"textarea","required":false,"placeholder":"例如: 肌钙蛋白升高，心电图 V1-V4 导联 ST 段抬高..."}
    ]'::jsonb,
    '{"outputType": "MARKDOWN"}'::jsonb,
    '请作为三甲医院主任医师及质控专家，基于医学循证指南给出诊疗分析：\n1. 科室：{{department}}\n2. 主诉与现病史：{{patientProfile}}\n3. 检验检查：{{labResults}}\n请输出【疑似诊断排查】、【推荐检查方案】、【警示与禁忌（红黄线）】及【病历质控提示】。',
    '{"maxTokens": 4096, "temperature": 0.3}'::jsonb
),
(
    103, '工业精密产线质检巡查中枢', '面向半导体、汽车构件与 3C 制造产线，融合机器视觉与缺陷机理图谱，毫秒级判定缺陷成因并给出工艺补偿。', 'Monitor', 1, '制造',
    '[{"name":"智能制造"},{"name":"产线质检"},{"name":"工艺补偿"}]', 1, 1, '智能制造',
    '[{"label":"单件质检节拍","value":"0.28s"},{"label":"缺陷漏检率","value":"<0.05%"},{"label":"产线稼动率提升","value":"14%"}]'::jsonb,
    'DIRECT_PROMPT_RAG',
    '[
      {"name":"workpieceType","label":"工件型号与产线阶段","type":"select","required":true,"options":["晶圆光刻胶缺陷","锂电池电芯焊缝","发动机缸体气孔","手机中框阳极氧化"],"placeholder":"选择工件与产线"},
      {"name":"defectPhenomenon","label":"检测异常现象描述","type":"textarea","required":true,"placeholder":"例如: 焊缝表面出现连续微裂纹，深度约 0.15mm，热影响区晶粒粗大..."},
      {"name":"processParams","label":"当前产线工艺参数","type":"textarea","required":false,"placeholder":"例如: 激光功率 2200W，焊接速度 45mm/s，保护气流量 18L/min..."}
    ]'::jsonb,
    '{"outputType": "MARKDOWN"}'::jsonb,
    '请作为国家级智能制造工艺与质量工程师，对产线缺陷进行根因诊断：\n1. 产线类型：{{workpieceType}}\n2. 异常表征：{{defectPhenomenon}}\n3. 工艺参数：{{processParams}}\n请输出【缺陷判定与等级】、【根因机理剖析（力/热/材料）】、【闭环工艺参数补偿建议】与【停机防呆策略】。',
    '{"maxTokens": 4096, "temperature": 0.2}'::jsonb
),
(
    104, '智能教育分层组卷与学情诊断', '结合课程标准与知识图谱认知层级，智能生成梯度化试题、学情薄弱点归因及个性化提分路径。', 'Reading', 1, '教育',
    '[{"name":"智慧教育"},{"name":"认知图谱"},{"name":"学情诊断"}]', 1, 1, '智慧教育',
    '[{"label":"知识点覆盖度","value":"100%"},{"label":"难度梯度拟合度","value":"0.94"},{"label":"个性化提分率","value":"22%"}]'::jsonb,
    'DIRECT_PROMPT_RAG',
    '[
      {"name":"subject","label":"学科门类","type":"select","required":true,"options":["高中数学","初中物理","高中化学","高中英语"],"placeholder":"选择学科"},
      {"name":"knowledgeFocus","label":"核心考察知识点与考法","type":"textarea","required":true,"placeholder":"例如: 解析几何与导数综合，考察单调性分类讨论与极值点偏移..."},
      {"name":"studentLevel","label":"目标学生学情水平","type":"select","required":true,"options":["培优竞赛组 (85%以上)","中坚强化组 (60%-85%)","基础攻坚组 (60%以下)"],"placeholder":"选择学情层次"}
    ]'::jsonb,
    '{"outputType": "MARKDOWN"}'::jsonb,
    '请作为重点中学特级教研专家，输出分层教学命题与诊断方案：\n1. 学科：{{subject}}\n2. 重点知识：{{knowledgeFocus}}\n3. 目标水平：{{studentLevel}}\n请输出【分层试题编制（含解析）】、【典型易错点预警】与【阶梯式教学策略辅导表】。',
    '{"maxTokens": 4096, "temperature": 0.4}'::jsonb
),
(
    105, '数字政务公文审校与口径合规', '依照国家党政机关公文格式国家标准，深度审查政治口径、格式规范、逻辑纰漏与法律合规风险。', 'Document', 1, '政务',
    '[{"name":"数字政务"},{"name":"公文合规"},{"name":"口径校对"}]', 1, 1, '数字政务',
    '[{"label":"口径错漏拦截率","value":"99.8%"},{"label":"敏感词核验","value":"100%"},{"label":"审校效率提升","value":"5.2x"}]'::jsonb,
    'DIRECT_PROMPT_RAG',
    '[
      {"name":"docType","label":"公文文种","type":"select","required":true,"options":["通知","请示","批复","实施意见","工作通报"],"placeholder":"选择文种"},
      {"name":"draftContent","label":"公文初稿内容","type":"textarea","required":true,"placeholder":"请粘贴需要审校的公文正文段落..."},
      {"name":"securityCheck","label":"特定审查要求","type":"select","required":false,"options":["常规政治口径与标点格式","严格涉密与数据出境审查","重大行政决策合规核查"],"placeholder":"选择审查深度"}
    ]'::jsonb,
    '{"outputType": "MARKDOWN"}'::jsonb,
    '请依照国家公文规范进行严肃、权威的合规审校：\n1. 公文文种：{{docType}}\n2. 初稿正文：{{draftContent}}\n3. 审查要求：{{securityCheck}}\n请输出【格式规范修订清单】、【政治口径与用词规范优化】、【行文逻辑重构建议】与【最终定稿润色范例】。',
    '{"maxTokens": 4096, "temperature": 0.1}'::jsonb
),
(
    106, '跨境电商多语言合规与选品智脑', '面向 Amazon、TikTok Shop 等全球出海平台，评估当地知识产权侵权风险、选品市场竞争度与本土化文案生成。', 'ShoppingCart', 1, '跨境',
    '[{"name":"跨境电商"},{"name":"全球选品"},{"name":"合规出海"}]', 1, 1, '跨境电商',
    '[{"label":"侵权风险预警率","value":"97.2%"},{"label":"地道本土化评分","value":"4.9/5"},{"label":"选品爆款预测度","value":"82%"}]'::jsonb,
    'DIRECT_PROMPT_RAG',
    '[
      {"name":"targetMarket","label":"目标出海区域与平台","type":"select","required":true,"options":["北美 (Amazon / TikTok)","欧洲五国 (VAT严格)","东南亚 (Shopee / Lazada)","日韩本土电商"],"placeholder":"选择市场"},
      {"name":"productCategory","label":"商品类目与核心卖点","type":"textarea","required":true,"placeholder":"例如: 户外便携储能电源，300W 快充，主打轻量化与应急照明..."},
      {"name":"language","label":"输出文案语种","type":"select","required":true,"options":["英语 (美国地道表达)","德语 (严谨技术参数)","日语 (礼貌敬语体系)"],"placeholder":"选择语言"}
    ]'::jsonb,
    '{"outputType": "MARKDOWN"}'::jsonb,
    '请作为资深跨境电商出海总监，提供全流程选品与合规落地报告：\n1. 目标市场：{{targetMarket}}\n2. 产品与卖点：{{productCategory}}\n3. 目标语言：{{language}}\n请输出【专利与合规壁垒警示】、【竞争格局与定价区间】、【高转化 Listing 标题与五点描述】。',
    '{"maxTokens": 4096, "temperature": 0.3}'::jsonb
),
(
    107, '水利工程防汛研判与智能调度', '面向流域梯级水库、防洪堤坝与灌区工程，结合气象降水实况与水动力学模型，推演库水位与防洪调度决策。', 'Help', 1, '水利',
    '[{"name":"智慧水利"},{"name":"防汛推演"},{"name":"工程调度"}]', 1, 1, '智慧水利',
    '[{"label":"演进推演耗时","value":"3.2s"},{"label":"调度决策采纳率","value":"91.5%"},{"label":"险情预警提前量","value":"4.5h"}]'::jsonb,
    'DIRECT_PROMPT_RAG',
    '[
      {"name":"basinName","label":"流域及水库节点","type":"select","required":true,"options":["长江中游控制性水库","黄河小浪底调度区","淮河流域滞洪区","太湖流域防洪工程"],"placeholder":"选择流域水库"},
      {"name":"rainfallForecast","label":"暴雨预报与入库流量趋势","type":"textarea","required":true,"placeholder":"例如: 未来 24 小时流域面雨量预计 120mm，当前入库流量 18000m³/s，库水位超汛限 1.2m..."},
      {"name":"constraint","label":"下游防洪控制断面限制","type":"textarea","required":false,"placeholder":"例如: 下游重点城镇控制断面最高安全泄量不超过 12000m³/s..."}
    ]'::jsonb,
    '{"outputType": "MARKDOWN"}'::jsonb,
    '请作为水利部防汛调度权威专家，进行库群水情联合研判：\n1. 流域工程：{{basinName}}\n2. 水雨情趋势：{{rainfallForecast}}\n3. 约束指标：{{constraint}}\n请输出【防洪险情态势评估】、【最优泄洪与蓄水调度窗口】、【下游淹没预警响应措施】。',
    '{"maxTokens": 4096, "temperature": 0.2}'::jsonb
),
(
    108, '新能源电网负荷预测与绿电消纳', '面向新型电力系统高比例新能源并网，精准预测风光发电波动，多目标优化微电网储能与虚拟电厂调度。', 'Cpu', 1, '能源',
    '[{"name":"智慧能源"},{"name":"负荷预测"},{"name":"绿电消纳"}]', 1, 1, '智慧能源',
    '[{"label":"日前负荷预测率","value":"97.8%"},{"label":"绿电消纳提升","value":"8.5%"},{"label":"微秒级指令下发","value":"100%"}]'::jsonb,
    'DIRECT_PROMPT_RAG',
    '[
      {"name":"gridArea","label":"电网区域与电源结构","type":"select","required":true,"options":["华北区域 (风电为主)","西北区域 (大型光伏基地)","华东区域 (高负荷虚拟电厂)"],"placeholder":"选择电网区域"},
      {"name":"weatherConditions","label":"气象辐射与风速监测","type":"textarea","required":true,"placeholder":"例如: 午后局部雷阵雨导致辐照度骤降 45%，平均风速由 8m/s 降至 3m/s..."},
      {"name":"storageStatus","label":"储能电站当前荷电状态 (SOC)","type":"textarea","required":false,"placeholder":"例如: 集中式电化学储能容量 100MWh，当前 SOC 65%，具备 2 小时快充放能力..."}
    ]'::jsonb,
    '{"outputType": "MARKDOWN"}'::jsonb,
    '请作为国家电网调度与新能源消纳高级专家，输出电网平抑与消纳决策：\n1. 电网区域：{{gridArea}}\n2. 气象波动：{{weatherConditions}}\n3. 储能现状：{{storageStatus}}\n请输出【新能源出力突降影响评估】、【储能与火电灵活性调峰分配】、【微网需求侧响应调度指令】。',
    '{"maxTokens": 4096, "temperature": 0.2}'::jsonb
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    icon = EXCLUDED.icon,
    status = EXCLUDED.status,
    type = EXCLUDED.type,
    tags = EXCLUDED.tags,
    workspace_id = EXCLUDED.workspace_id,
    category = EXCLUDED.category,
    industry = EXCLUDED.industry,
    kpi_metrics = EXCLUDED.kpi_metrics,
    execution_mode = EXCLUDED.execution_mode,
    input_schema = EXCLUDED.input_schema,
    output_schema = EXCLUDED.output_schema,
    prompt_template = EXCLUDED.prompt_template,
    execution_config = EXCLUDED.execution_config;

-- 4. 优化解决方案表，支持官方预置方案与用户私有方案差异化
UPDATE kac_solution SET creator_id = NULL, update_by = 'system' WHERE id IN (1, 2, 3, 4, 5);
UPDATE kac_solution SET creator_id = 1, status = 1 WHERE id IN (101, 102);
UPDATE kac_solution SET creator_id = 1, status = 0 WHERE id IN (103, 104);

