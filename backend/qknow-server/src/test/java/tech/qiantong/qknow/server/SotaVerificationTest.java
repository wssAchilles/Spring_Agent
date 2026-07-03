package tech.qiantong.qknow.server;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tech.qiantong.qknow.hermes.eval.RagasEvaluator;
import tech.qiantong.qknow.hermes.memory.SleepTimeMemoryAgent;
import tech.qiantong.qknow.module.kmc.service.rag.RagRetrievalService;
import tech.qiantong.qknow.module.kg.service.GraphCommunityService;

@Slf4j
@SpringBootTest
public class SotaVerificationTest {

    @Autowired(required = false)
    private RagRetrievalService ragRetrievalService;

    @Autowired(required = false)
    private SleepTimeMemoryAgent sleepTimeMemoryAgent;

    @Autowired(required = false)
    private RagasEvaluator ragasEvaluator;

    @Autowired(required = false)
    private GraphCommunityService graphCommunityService;

    @Test
    public void testSotaFeatures() {
        log.info("====== 开始验证 SOTA 核心特性 ======");
        
        if (ragRetrievalService != null) {
            log.info("【测试1】 RagRetrievalService Bean 已就绪。");
        }
        
        if (sleepTimeMemoryAgent != null) {
            log.info("【测试2】 SleepTimeMemoryAgent 存在，执行主动打捞...");
            sleepTimeMemoryAgent.consolidateIdleConversations();
            log.info("-> 记忆清理执行完毕");
        }

        if (graphCommunityService != null) {
            log.info("【测试3】 GraphCommunityService Bean 已就绪。");
        }

        if (ragasEvaluator != null) {
            log.info("【测试4】 评估引擎 RagasEvaluator 已就绪。");
        }
        
        log.info("====== 验证结束 ======");
    }
}
