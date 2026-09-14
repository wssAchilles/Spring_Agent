package tech.qiantong.qknow.ai.presentation.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.presentation.dto.MultimodalPresentationBlockDTO;
import tech.qiantong.qknow.ai.presentation.enums.CognitiveLoadLevel;
import tech.qiantong.qknow.ai.presentation.enums.PresentationBlockType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cowan 4±1 认知阻尼控制器
 * 
 * 核心数学定理 (Theorem 1.1 Cowan Invariant & Theorem 2.1 Pareto Dominance):
 * 1. 首屏活跃非折叠组块数 K_chunks <= loadLevel.getMaxActiveChunks() <= 4。
 * 2. 在 CRITICAL 极端负荷下，严格仅呈现 1 项 ACTION_BANNER，其余复杂内容 100% 自动折叠。
 * 3. 在 HIGH 负荷下，复杂 ECharts 图表被指标卡单调占优并折叠。
 */
@Component
public class AdaptivePresentationGovernor {

    private static final Logger log = LoggerFactory.getLogger(AdaptivePresentationGovernor.class);

    /**
     * 依据认知负荷等级对展示块列表执行阻尼裁决
     *
     * @param rawBlocks 原始候选展示块列表
     * @param loadLevel 当前认知负荷等级
     * @return 裁决分组结果 (Index 0: activeBlocks, Index 1: collapsedBlocks)
     */
    public List<List<MultimodalPresentationBlockDTO>> governPresentation(
            List<MultimodalPresentationBlockDTO> rawBlocks,
            CognitiveLoadLevel loadLevel) {

        if (rawBlocks == null || rawBlocks.isEmpty()) {
            List<List<MultimodalPresentationBlockDTO>> emptyRes = new ArrayList<>();
            emptyRes.add(new ArrayList<>());
            emptyRes.add(new ArrayList<>());
            return emptyRes;
        }

        // 按视觉优先级排序 (displayOrder 越小越靠前)
        List<MultimodalPresentationBlockDTO> sortedBlocks = new ArrayList<>(rawBlocks);
        sortedBlocks.sort(Comparator.comparingInt(MultimodalPresentationBlockDTO::getDisplayOrder));

        List<MultimodalPresentationBlockDTO> activeBlocks = new ArrayList<>();
        List<MultimodalPresentationBlockDTO> collapsedBlocks = new ArrayList<>();

        int maxActive = loadLevel.getMaxActiveChunks();
        boolean allowCharts = loadLevel.isAllowComplexCharts();

        if (loadLevel == CognitiveLoadLevel.CRITICAL) {
            // CRITICAL 极端负荷：定理 1.1 唯一核心行动项约束
            MultimodalPresentationBlockDTO criticalAction = null;
            for (MultimodalPresentationBlockDTO b : sortedBlocks) {
                if (b.getBlockType() == PresentationBlockType.ACTION_BANNER) {
                    criticalAction = b;
                    break;
                }
            }
            if (criticalAction == null && !sortedBlocks.isEmpty()) {
                criticalAction = sortedBlocks.get(0);
            }
            if (criticalAction != null) {
                criticalAction.setCollapsed(false);
                activeBlocks.add(criticalAction);
            }
            for (MultimodalPresentationBlockDTO b : sortedBlocks) {
                if (b != criticalAction) {
                    b.setCollapsed(true);
                    collapsedBlocks.add(b);
                }
            }
        } else {
            // LOW, MEDIUM, HIGH 负荷分配
            for (MultimodalPresentationBlockDTO block : sortedBlocks) {
                // 检查是否受复杂图表抑制 (定理 2.1 帕累托占优)
                if (block.getBlockType() == PresentationBlockType.ECHART_SPEC && !allowCharts) {
                    block.setCollapsed(true);
                    collapsedBlocks.add(block);
                    continue;
                }

                if (activeBlocks.size() < maxActive) {
                    block.setCollapsed(false);
                    activeBlocks.add(block);
                } else {
                    block.setCollapsed(true);
                    collapsedBlocks.add(block);
                }
            }
        }

        List<List<MultimodalPresentationBlockDTO>> result = new ArrayList<>();
        result.add(activeBlocks);
        result.add(collapsedBlocks);
        return result;
    }
}
