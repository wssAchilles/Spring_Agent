package tech.qiantong.qknow.ai.pipeline.stage.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.gateway.model.ChannelStatus;
import tech.qiantong.qknow.ai.gateway.model.ProviderChannel;
import tech.qiantong.qknow.ai.gateway.model.ProviderType;
import tech.qiantong.qknow.ai.gateway.model.RoutingScenario;
import tech.qiantong.qknow.ai.gateway.router.LatencyAwareSlaRouter;
import tech.qiantong.qknow.ai.pipeline.context.AiPipelineContext;
import tech.qiantong.qknow.ai.pipeline.stage.PipelineStage;
import tech.qiantong.qknow.ai.pipeline.stage.PipelineStageHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 阶段 2 处理器: SLA 延迟与成本感知路由选路
 *
 * 依据业务画像动态计算 Pareto 最优选路通道并写入上下文。
 * 容灾策略: Fail-Open 降级，路由异常时采用默认主通道。
 *
 * @author qknow
 */
@Slf4j
@Component
public class SlaRoutingStageHandler implements PipelineStageHandler {

    private final LatencyAwareSlaRouter slaRouter;
    private final List<ProviderChannel> defaultCandidates = new ArrayList<>();

    @Autowired
    public SlaRoutingStageHandler(LatencyAwareSlaRouter slaRouter) {
        this.slaRouter = slaRouter;
        initDefaultCandidates();
    }

    private void initDefaultCandidates() {
        ProviderChannel official = ProviderChannel.builder()
                .channelId("official-deepseek")
                .providerType(ProviderType.OFFICIAL_DEEPSEEK)
                .channelName("官方直连-低延迟通道")
                .costPerMillionTokens(2.0)
                .status(new AtomicReference<>(ChannelStatus.HEALTHY))
                .priority(0)
                .weight(100)
                .build();

        ProviderChannel volc = ProviderChannel.builder()
                .channelId("volcengine-ark")
                .providerType(ProviderType.VOLC_ENGINE)
                .channelName("火山引擎-高吞吐通道")
                .costPerMillionTokens(1.0)
                .status(new AtomicReference<>(ChannelStatus.HEALTHY))
                .priority(1)
                .weight(50)
                .build();

        defaultCandidates.add(official);
        defaultCandidates.add(volc);
    }

    @Override
    public PipelineStage getStage() {
        return PipelineStage.SLA_ROUTING;
    }

    @Override
    public boolean shouldActivate(AiPipelineContext context) {
        return true;
    }

    @Override
    public long getStageTimeoutMs(AiPipelineContext context) {
        return 500L;
    }

    @Override
    public void handle(AiPipelineContext context) throws Exception {
        RoutingScenario scenario = RoutingScenario.INTERACTIVE_CHAT;
        String profile = context.getProfile();
        if (profile != null && (profile.toUpperCase().contains("BATCH") || profile.toUpperCase().contains("OFFLINE"))) {
            scenario = RoutingScenario.OFFLINE_EMBEDDING;
        }

        try {
            ProviderChannel selected = slaRouter.selectOptimalChannel(defaultCandidates, scenario);
            String model = selected.resolveUpstreamModel("deepseek-chat");
            context.setRoutingResult(selected.getChannelId(), model, selected.getCostPerMillionTokens());
            log.debug("[SlaRouting] 选路完成: scenario={}, channelId={}, model={}",
                    scenario, selected.getChannelId(), model);
        } catch (Exception e) {
            log.warn("[SlaRouting] 选路异常，Fail-Open 降级默认通道: {}", e.getMessage());
            context.setRoutingResult("official-deepseek", "deepseek-chat", 2.0);
        }
    }
}
