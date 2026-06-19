package tech.qiantong.qknow.module.kb.service.judge;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.qiantong.qknow.module.kb.dal.dataobject.judge.KbAiJudgeScoreDO;
import tech.qiantong.qknow.module.kb.dal.mapper.judge.KbAiJudgeScoreMapper;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import java.util.List;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class KbAiJudgeScoreService extends ServiceImpl<KbAiJudgeScoreMapper, KbAiJudgeScoreDO> {

    /**
     * 保存评分记录
     */
    public Long saveScore(Long workspaceId, Long conversationId, Long messageId,
                          JudgeResult judgeResult, int retryCount) {
        KbAiJudgeScoreDO score = KbAiJudgeScoreDO.builder()
                .workspaceId(workspaceId)
                .conversationId(conversationId)
                .messageId(messageId)
                .factualityScore(judgeResult.getFactualityScore())
                .relevanceScore(judgeResult.getRelevanceScore())
                .instructionScore(judgeResult.getInstructionScore())
                .overallScore(judgeResult.getOverallScore())
                .passed(judgeResult.isPassed())
                .retryCount(retryCount)
                .feedback(judgeResult.getFeedback())
                .validFlag(true)
                .delFlag(false)
                .build();
        save(score);
        return score.getId();
    }

    /**
     * 获取对话的评分历史
     */
    public List<KbAiJudgeScoreDO> getScoresByConversationId(Long conversationId) {
        return list(new LambdaQueryWrapperX<KbAiJudgeScoreDO>()
                .eq(KbAiJudgeScoreDO::getConversationId, conversationId)
                .eq(KbAiJudgeScoreDO::getDelFlag, false)
                .orderByDesc(KbAiJudgeScoreDO::getCreateTime));
    }

    /**
     * 获取工作区的评分统计
     */
    public JudgeStatistics getStatistics(Long workspaceId) {
        List<KbAiJudgeScoreDO> scores = list(new LambdaQueryWrapperX<KbAiJudgeScoreDO>()
                .eq(KbAiJudgeScoreDO::getWorkspaceId, workspaceId)
                .eq(KbAiJudgeScoreDO::getDelFlag, false));

        if (scores.isEmpty()) {
            return new JudgeStatistics(0, 0, 0.0, 0.0, 0.0);
        }

        int total = scores.size();
        int passed = (int) scores.stream().filter(KbAiJudgeScoreDO::getPassed).count();
        double avgFactuality = scores.stream().mapToDouble(KbAiJudgeScoreDO::getFactualityScore).average().orElse(0);
        double avgRelevance = scores.stream().mapToDouble(KbAiJudgeScoreDO::getRelevanceScore).average().orElse(0);
        double avgInstruction = scores.stream().mapToDouble(KbAiJudgeScoreDO::getInstructionScore).average().orElse(0);

        return new JudgeStatistics(total, passed, avgFactuality, avgRelevance, avgInstruction);
    }

    public record JudgeStatistics(int total, int passed, double avgFactuality,
                                   double avgRelevance, double avgInstruction) {}
}
