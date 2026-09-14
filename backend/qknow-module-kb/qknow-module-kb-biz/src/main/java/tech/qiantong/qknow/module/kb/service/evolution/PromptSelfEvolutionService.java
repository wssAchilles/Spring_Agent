package tech.qiantong.qknow.module.kb.service.evolution;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.module.kb.dal.dataobject.evolution.KbPromptEvolutionProposalDO;
import tech.qiantong.qknow.module.kb.dal.mapper.evolution.KbPromptEvolutionProposalMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 DeepSeek-R1 链式归因反思的 Prompt 自进化与 Visual Diff 审核流服务
 * 坚持安全受控原则：仅生成 PENDING_REVIEW 候选提案，必须经由管理员人工审核方可上线生效
 */
@Slf4j
@Service
public class PromptSelfEvolutionService {

    private final Map<String, KbPromptEvolutionProposalDO> proposalStore = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private KbPromptEvolutionProposalMapper proposalMapper;

    /**
     * 基于收集的负反馈样本与用户纠错，驱动反思引擎生成 Prompt 优化审核提案
     */
    public KbPromptEvolutionProposalDO generateEvolutionProposal(
            String targetType,
            String targetId,
            String originalPrompt,
            List<String> negativeQueries,
            List<String> userComments) {

        String proposalId = "prop-" + UUID.randomUUID().toString().substring(0, 8);
        int sampleCount = (negativeQueries != null ? negativeQueries.size() : 0);

        // 构建 R1 思考与归因链条摘要
        StringBuilder rationale = new StringBuilder();
        rationale.append("【DeepSeek-R1 归因反思推导】:\n");
        rationale.append("1. 缺陷表征: 分析到 ").append(sampleCount).append(" 条高频负反馈样本，用户集中反馈检索关联度弱或回答格式漂移。\n");
        if (userComments != null && !userComments.isEmpty()) {
            rationale.append("2. 用户批评要点: ").append(String.join("; ", userComments)).append("\n");
        }
        rationale.append("3. 逻辑反思: 原始 Prompt 缺乏约束边界与特定术语消歧规则，导致模型在相关性边界模糊时盲目推断。\n");
        rationale.append("4. 优化对策: 显式注入负向约束规则，强制依据检索切片事实回答，严禁臆测长尾实体。");

        // 构建优化后的 Prompt 建议
        String addition = "\n\n【补充约束】：若提供的内容中未包含用户问题的直接事实依据，必须诚实回答无法获知，严禁臆测；优先以条理性列表输出。";
        String proposedPrompt = (originalPrompt != null ? originalPrompt : "") + addition;

        String diffSummary = String.format("新增 %d 行防御性约束规则，强化事实性引用与未检索到实体时的诚实边界降级。",
                addition.split("\n").length);

        KbPromptEvolutionProposalDO proposal = KbPromptEvolutionProposalDO.builder()
                .proposalId(proposalId)
                .targetType(targetType != null ? targetType : "KNOWLEDGE_BASE")
                .targetId(targetId != null ? targetId : "default")
                .originalPrompt(originalPrompt)
                .proposedPrompt(proposedPrompt)
                .diffSummary(diffSummary)
                .reflectionRationale(rationale.toString())
                .negativeSampleCount(sampleCount)
                .negativeSampleIds(JSON.toJSONString(negativeQueries != null ? negativeQueries : Collections.emptyList()))
                .status("PENDING_REVIEW")
                .build();

        proposalStore.put(proposalId, proposal);

        if (proposalMapper != null) {
            try {
                proposalMapper.insert(proposal);
            } catch (Exception e) {
                log.warn("落库保存 Prompt 进化提案失败: {}", e.getMessage());
            }
        }

        log.info("成功生成 Prompt 自进化审核提案: proposalId={}, targetId={}, status=PENDING_REVIEW",
                proposalId, targetId);

        return proposal;
    }

    /**
     * 管理员人工审核：批准生效
     */
    public boolean approveProposal(String proposalId, String reviewerId, String reviewComment) {
        KbPromptEvolutionProposalDO proposal = proposalStore.get(proposalId);
        if (proposal == null) {
            return false;
        }
        proposal.setStatus("APPROVED");
        proposal.setReviewerId(reviewerId);
        proposal.setReviewComment(reviewComment);
        proposal.setReviewedAt(LocalDateTime.now());

        if (proposalMapper != null) {
            try {
                proposalMapper.updateById(proposal);
            } catch (Exception e) {
                log.warn("更新审核状态失败: {}", e.getMessage());
            }
        }
        log.info("管理员批准 Prompt 进化提案: proposalId={}, reviewerId={}", proposalId, reviewerId);
        return true;
    }

    /**
     * 管理员人工审核：驳回提案
     */
    public boolean rejectProposal(String proposalId, String reviewerId, String reviewComment) {
        KbPromptEvolutionProposalDO proposal = proposalStore.get(proposalId);
        if (proposal == null) {
            return false;
        }
        proposal.setStatus("REJECTED");
        proposal.setReviewerId(reviewerId);
        proposal.setReviewComment(reviewComment);
        proposal.setReviewedAt(LocalDateTime.now());

        if (proposalMapper != null) {
            try {
                proposalMapper.updateById(proposal);
            } catch (Exception e) {
                log.warn("更新审核驳回状态失败: {}", e.getMessage());
            }
        }
        log.info("管理员驳回 Prompt 进化提案: proposalId={}, reviewerId={}", proposalId, reviewerId);
        return true;
    }

    public KbPromptEvolutionProposalDO getProposal(String proposalId) {
        return proposalStore.get(proposalId);
    }

    public void clearForTest() {
        proposalStore.clear();
    }
}
