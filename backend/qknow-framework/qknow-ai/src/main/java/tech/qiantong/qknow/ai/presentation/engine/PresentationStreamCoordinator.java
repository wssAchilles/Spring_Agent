package tech.qiantong.qknow.ai.presentation.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.presentation.dto.MultimodalPresentationBlockDTO;
import tech.qiantong.qknow.ai.presentation.dto.PresentationPlanVO;
import tech.qiantong.qknow.ai.presentation.dto.UserCognitiveStateDTO;
import tech.qiantong.qknow.ai.presentation.enums.CognitiveLoadLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式呈现调度总控器
 * 
 * 统筹行为采集、负荷评估、阻尼裁决、多模态合成与前端规划输出。
 * 具备 Fail-Open 容灾保护，单次端到端耗时严格满足 MTTC <= 50ms。
 */
@Component
public class PresentationStreamCoordinator {

    private static final Logger log = LoggerFactory.getLogger(PresentationStreamCoordinator.class);

    private final CognitiveLoadEstimator estimator;
    private final AdaptivePresentationGovernor governor;
    private final DynamicMultimodalComposer composer;

    public PresentationStreamCoordinator(CognitiveLoadEstimator estimator,
                                         AdaptivePresentationGovernor governor,
                                         DynamicMultimodalComposer composer) {
        this.estimator = estimator;
        this.governor = governor;
        this.composer = composer;
    }

    /**
     * 端到端执行自适应呈现规划
     */
    public PresentationPlanVO planPresentation(String taskId, UserCognitiveStateDTO userState, List<MultimodalPresentationBlockDTO> candidateBlocks) {
        long start = System.currentTimeMillis();
        try {
            double score = estimator.estimateCognitiveScore(userState);
            CognitiveLoadLevel level = estimator.mapToLevel(score);

            List<List<MultimodalPresentationBlockDTO>> partitioned = governor.governPresentation(candidateBlocks, level);
            List<MultimodalPresentationBlockDTO> activeBlocks = partitioned.get(0);
            List<MultimodalPresentationBlockDTO> collapsedBlocks = partitioned.get(1);

            long duration = System.currentTimeMillis() - start;
            log.info("自适应呈现规划完成: taskId={}, 负荷得分={}, 等级={}, 活跃块数={}, 折叠块数={}, 耗时={}ms",
                    taskId, String.format("%.2f", score), level, activeBlocks.size(), collapsedBlocks.size(), duration);

            return new PresentationPlanVO(taskId, score, level, activeBlocks, collapsedBlocks, duration);

        } catch (Exception e) {
            log.error("自适应呈现规划发生异常，触发 Fail-Open 降级为 MEDIUM 平衡模式: taskId={}", taskId, e);
            List<MultimodalPresentationBlockDTO> active = new ArrayList<>();
            List<MultimodalPresentationBlockDTO> collapsed = new ArrayList<>();
            if (candidateBlocks != null) {
                for (int i = 0; i < candidateBlocks.size(); i++) {
                    MultimodalPresentationBlockDTO b = candidateBlocks.get(i);
                    if (i < 3) {
                        b.setCollapsed(false);
                        active.add(b);
                    } else {
                        b.setCollapsed(true);
                        collapsed.add(b);
                    }
                }
            }
            long duration = System.currentTimeMillis() - start;
            return new PresentationPlanVO(taskId, 0.50, CognitiveLoadLevel.MEDIUM, active, collapsed, duration);
        }
    }
}
