# 05-hermes-kernel-and-ai-judge Hermes 认知内核 + AI Judge

> 状态: completed | 开始: 2026-06-20T12:00:00+08:00 | 完成: 2026-06-20T14:00:00+08:00

## 目标清单

- [x] 5.1 Hermes 反思循环内核
- [x] 5.2 AI Judge 三维评分系统
- [x] 5.3 控制面与认知面对接 + 质量面板

## 关键决策

### 决策 1: 使用 LLM 自身进行评分 (AI-as-Judge)
- **原因**: DeepSeek 等模型具有较强的判断能力
- **否决方案**: 人工评分、规则匹配

### 决策 2: 评分失败时默认通过
- **原因**: 避免评分服务异常影响用户体验
- **否决方案**: 评分失败时拒绝回答

### 决策 3: 评分反馈注入下次 Prompt
- **原因**: 让 LLM 能够根据反馈改进回答
- **否决方案**: 不提供反馈

## 修改的文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| service/hermes/HermesKernel.java | 新建 | 反思循环内核 |
| service/judge/AiJudgeService.java | 新建 | 三维评分服务 |
| service/judge/JudgeResult.java | 新建 | 评分结果对象 |
| service/judge/KbAiJudgeScoreService.java | 新建 | 评分持久化服务 |
| dal/dataobject/judge/KbAiJudgeScoreDO.java | 新建 | 评分记录实体 |
| dal/mapper/judge/KbAiJudgeScoreMapper.java | 新建 | 评分记录 Mapper |
| controller/admin/AiJudgeController.java | 新建 | 质量分析控制器 |

## 技术笔记

1. **反思循环**: 生成 → AI Judge 评估 → 修正，最多重试 3 次
2. **三维评分**: 事实性、相关性、指令遵循度，综合评分 >= 0.7 通过
3. **评分反馈**: 上次评分结果注入下次 Prompt，指导 LLM 改进
4. **评分持久化**: 评分记录保存到 ai_judge_score 表，支持统计分析

## 下一阶段预研

- [ ] 端到端集成测试
- [ ] 性能优化
- [ ] Docker 容器化
- [ ] 文档与答辩准备
