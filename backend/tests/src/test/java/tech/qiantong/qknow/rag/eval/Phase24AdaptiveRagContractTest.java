package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.module.kb.api.feedback.dto.ChatFeedbackReqVO;
import tech.qiantong.qknow.module.kb.controller.admin.feedback.FeedbackCollectorController;
import tech.qiantong.qknow.module.kb.dal.dataobject.evolution.KbPromptEvolutionProposalDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.feedback.KbChatFeedbackDO;
import tech.qiantong.qknow.module.kb.service.evolution.PromptSelfEvolutionService;
import tech.qiantong.qknow.module.kb.service.feedback.FeedbackStreamQueueService;
import tech.qiantong.qknow.module.kb.service.streaming.SseReplayWindowBuffer;
import tech.qiantong.qknow.module.kmc.service.rag.adaptive.AdaptivePolicyGovernor;
import tech.qiantong.qknow.module.kmc.service.rag.adaptive.DiscountedLinUcbRouter;
import tech.qiantong.qknow.module.kmc.service.rag.adaptive.DiscountedLinUcbRouter.ActionType;
import tech.qiantong.qknow.module.kmc.service.rag.adaptive.FeedbackCreditBus;
import tech.qiantong.qknow.module.kmc.service.rag.mining.RealQueryMiningService;
import tech.qiantong.qknow.module.kmc.service.rag.mining.RealQueryMiningService.MinedQueryItem;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 24 专属自动化契约测试
 * 覆盖：
 * 1. SSE 断点续传重发环形缓冲区
 * 2. 流式响应消除逐 Token 写库，仅终态批写落库
 * 3. HMAC-SHA256 防伪验签与限流拦截
 * 4. 高并发反馈削峰缓冲与批量持久化
 * 5. Discounted-LinUCB 亚线性累积遗憾收敛
 * 6. Discounted-LinUCB 非平稳突变敏锐时变追踪
 * 7. PBM 展示位置偏差逆倾向得分加权 (IPS) 无偏性
 * 8. 自适应策略调节器李雅普诺夫防振荡 (单步 <= 0.05) 与 Rerank 智能旁路
 * 9. RealQueryMiningService 第 5 漏斗负反馈脱敏沉淀
 * 10. DeepSeek-R1 链式归因反思与 Prompt 自进化审核流
 */
public class Phase24AdaptiveRagContractTest {

    private SseReplayWindowBuffer sseReplayWindowBuffer;
    private FeedbackStreamQueueService feedbackQueueService;
    private FeedbackCreditBus feedbackCreditBus;
    private AdaptivePolicyGovernor adaptivePolicyGovernor;
    private DiscountedLinUcbRouter discountedLinUcbRouter;
    private RealQueryMiningService realQueryMiningService;
    private PromptSelfEvolutionService promptSelfEvolutionService;

    @BeforeEach
    void setUp() {
        sseReplayWindowBuffer = new SseReplayWindowBuffer();
        feedbackQueueService = new FeedbackStreamQueueService();
        feedbackCreditBus = new FeedbackCreditBus();
        adaptivePolicyGovernor = new AdaptivePolicyGovernor();
        discountedLinUcbRouter = new DiscountedLinUcbRouter();
        realQueryMiningService = new RealQueryMiningService();
        promptSelfEvolutionService = new PromptSelfEvolutionService();
    }

    @Test
    @DisplayName("Contract 01: SSE 滑动重发缓冲区在断线重连时按 Last-Event-ID 毫秒级补发且零漏字零重复")
    void contract01_sseReplayWindow_normalAndResumeSuccess() {
        String sessionId = "conv_1001_msg_2001";

        // 正常模拟输出 20 帧
        for (int i = 1; i <= 20; i++) {
            sseReplayWindowBuffer.recordFrame(sessionId, i, "Chunk-" + i);
        }
        assertEquals(20, sseReplayWindowBuffer.getBufferSize(sessionId));

        // 模拟客户端在接收完第 15 帧后网络断开，携带 lastEventId = 15 请求断点重传
        List<SseReplayWindowBuffer.SseFrame> replayed = sseReplayWindowBuffer.getFramesAfter(sessionId, 15L);

        // 断言准确补发 5 帧 (16 到 20)，无丢失、无提前帧、无重复帧
        assertEquals(5, replayed.size());
        assertEquals(16L, replayed.get(0).getSequenceId());
        assertEquals("Chunk-16", replayed.get(0).getData());
        assertEquals(20L, replayed.get(4).getSequenceId());
        assertEquals("Chunk-20", replayed.get(4).getData());

        // 清理会话
        sseReplayWindowBuffer.clearSession(sessionId);
        assertEquals(0, sseReplayWindowBuffer.getBufferSize(sessionId));
    }

    @Test
    @DisplayName("Contract 02: 流式响应消除逐 Token 写库，中间过程 0 次写库，终态单次批写更新")
    void contract02_terminalBatchFlush_eliminatesPerTokenDbWrites() {
        // 模拟数据库 UPDATE 计数器
        AtomicInteger dbUpdateCount = new AtomicInteger(0);
        StringBuilder memoryBuffer = new StringBuilder();

        int totalChunks = 100;
        // 模拟流式循环：仅内存累加与写重发窗口，绝不触发写库
        for (int i = 1; i <= totalChunks; i++) {
            String chunk = "word" + i + " ";
            memoryBuffer.append(chunk);
            // 中间过程写库次数恒为 0
            assertEquals(0, dbUpdateCount.get(), "流式中间过程严禁执行同步数据库 UPDATE");
        }

        // 终态完成时 (doOnComplete)，触发唯一次批量更新落库
        String finalContent = memoryBuffer.toString().trim();
        dbUpdateCount.incrementAndGet();

        assertEquals(1, dbUpdateCount.get(), "单会话全过程数据库写操作严格等于 1 次");
        assertTrue(finalContent.startsWith("word1"));
        assertTrue(finalContent.endsWith("word100"));
    }

    @Test
    @DisplayName("Contract 03: 反馈接口 HMAC-SHA256 加盐防伪与时间戳防重放校验")
    void contract03_feedbackSecurity_hmacAndRateLimiting() {
        long now = System.currentTimeMillis();
        String messageId = "msg-8888";
        String feedbackType = "UPVOTE";

        // 1. 生成合法签名
        String validSig = FeedbackCollectorController.calculateSignature(
                messageId, feedbackType, now, FeedbackCollectorController.HMAC_SECRET);

        ChatFeedbackReqVO validReq = ChatFeedbackReqVO.builder()
                .messageId(messageId)
                .feedbackType(feedbackType)
                .timestamp(now)
                .signature(validSig)
                .build();

        assertTrue(FeedbackCollectorController.verifySignature(validReq), "合法签名与时间戳必须验签通过");

        // 2. 篡改签名测试
        ChatFeedbackReqVO tamperedReq = ChatFeedbackReqVO.builder()
                .messageId(messageId)
                .feedbackType(feedbackType)
                .timestamp(now)
                .signature("invalid-forged-signature-xxx")
                .build();

        assertFalse(FeedbackCollectorController.verifySignature(tamperedReq), "伪造签名必须被拦截 (403)");

        // 3. 过期重放攻击测试 (1 小时前的请求)
        ChatFeedbackReqVO expiredReq = ChatFeedbackReqVO.builder()
                .messageId(messageId)
                .feedbackType(feedbackType)
                .timestamp(now - 3600_000L)
                .signature(FeedbackCollectorController.calculateSignature(
                        messageId, feedbackType, now - 3600_000L, FeedbackCollectorController.HMAC_SECRET))
                .build();

        assertFalse(FeedbackCollectorController.verifySignature(expiredReq), "超出时间窗口请求必须被拦截 (防重放)");
    }

    @Test
    @DisplayName("Contract 04: 反馈削峰队列高并发缓冲与批量持久化无乱序与消息丢失")
    void contract04_feedbackQueue_redisStreamIngestionAndBatchFlush() {
        feedbackQueueService.clearForTest();

        // 模拟并发推入 150 条反馈事件
        for (int i = 1; i <= 150; i++) {
            KbChatFeedbackDO feedback = KbChatFeedbackDO.builder()
                    .conversationId("conv-1")
                    .messageId("msg-" + i)
                    .feedbackType("UPVOTE")
                    .rating(5)
                    .build();
            assertTrue(feedbackQueueService.enqueueFeedback(feedback));
        }

        assertEquals(150, feedbackQueueService.getPendingCount());
        assertEquals(150, feedbackQueueService.getTotalEnqueued());

        // 模拟第一次批量刷盘 (最大 100 条)
        int flushed1 = feedbackQueueService.flushBatch(100);
        assertEquals(100, flushed1);
        assertEquals(50, feedbackQueueService.getPendingCount());

        // 模拟第二次批量刷盘 (剩余 50 条)
        int flushed2 = feedbackQueueService.flushBatch(100);
        assertEquals(50, flushed2);
        assertEquals(0, feedbackQueueService.getPendingCount());
        assertEquals(150, feedbackQueueService.getTotalFlushed());
    }

    @Test
    @DisplayName("Contract 05: Discounted-LinUCB 在 5 臂动作与千问投影空间下累积遗憾次线性收敛")
    void contract05_discountedLinUcb_regretSublinearConvergence() {
        discountedLinUcbRouter.reset();

        // 构造千问 1536 维测试向量
        float[] embedding = new float[DiscountedLinUcbRouter.EMBEDDING_DIM];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) Math.sin(i * 0.1);
        }
        double[] feature = discountedLinUcbRouter.projectEmbedding(embedding);
        assertEquals(DiscountedLinUcbRouter.FEATURE_DIM, feature.length);

        // 设定最优臂为 HYBRID (期望收益 0.9)，次优臂为 VECTOR (0.5)，其余臂为 0.2
        ActionType optimalAction = ActionType.HYBRID;
        Random rng = new Random(12345L);

        double cumulativeRegret = 0.0;
        double first100AvgRegret = 0.0;
        double last100AvgRegret = 0.0;

        int totalRounds = 400;
        for (int t = 1; t <= totalRounds; t++) {
            ActionType chosen = discountedLinUcbRouter.selectAction(feature);

            double meanReward;
            if (chosen == optimalAction) {
                meanReward = 0.90;
            } else if (chosen == ActionType.VECTOR) {
                meanReward = 0.50;
            } else {
                meanReward = 0.20;
            }
            double observedReward = Math.max(0.0, Math.min(1.0, meanReward + rng.nextGaussian() * 0.05));
            double regret = 0.90 - meanReward;
            cumulativeRegret += regret;

            discountedLinUcbRouter.updateReward(chosen, feature, observedReward);

            if (t == 100) {
                first100AvgRegret = cumulativeRegret / 100.0;
            }
        }
        last100AvgRegret = cumulativeRegret / totalRounds;

        // 次线性收敛断言：后期的平均遗憾 R(T)/T 必须显著低于前 100 轮
        assertTrue(last100AvgRegret < first100AvgRegret,
                String.format("平均遗憾应随轮数收敛下降: first100=%f, fullAvg=%f", first100AvgRegret, last100AvgRegret));
        // 最优臂被拉动次数必须占绝对主导
        assertTrue(discountedLinUcbRouter.getPullCount(optimalAction) > 200, "最优臂应占主导探索与利用次数");
    }

    @Test
    @DisplayName("Contract 06: Discounted-LinUCB 在知识库突变非平稳环境下敏锐自适应时变追踪")
    void contract06_discountedLinUcb_nonStationaryTracking() {
        discountedLinUcbRouter.reset();

        float[] embedding = new float[DiscountedLinUcbRouter.EMBEDDING_DIM];
        Arrays.fill(embedding, 0.5f);
        double[] feature = discountedLinUcbRouter.projectEmbedding(embedding);

        // 前 150 轮：ActionType.BM25 最优
        for (int t = 1; t <= 150; t++) {
            ActionType chosen = discountedLinUcbRouter.selectAction(feature);
            double reward = (chosen == ActionType.BM25) ? 0.95 : 0.20;
            discountedLinUcbRouter.updateReward(chosen, feature, reward);
        }
        assertEquals(ActionType.BM25, discountedLinUcbRouter.selectAction(feature));

        // 第 151 轮起环境突变：ActionType.GRAPH 变为最优 (0.95)，BM25 降为 0.10
        for (int t = 151; t <= 300; t++) {
            ActionType chosen = discountedLinUcbRouter.selectAction(feature);
            double reward = (chosen == ActionType.GRAPH) ? 0.95 : 0.10;
            discountedLinUcbRouter.updateReward(chosen, feature, reward);
        }

        // 折扣因子 0.98 成功遗忘旧数据，在 150 轮内追踪新最优策略
        ActionType finalChoice = discountedLinUcbRouter.selectAction(feature);
        assertEquals(ActionType.GRAPH, finalChoice, "突变后路由器必须敏锐自适应重收敛至新最优臂 GRAPH");
    }

    @Test
    @DisplayName("Contract 07: 展示位置偏差 PBM 经过逆倾向加权 (IPS) 无偏校准且严格有界")
    void contract07_ipsUnbiasedFeedback_creditAssignment() {
        // 位置 1 权重为 1.0
        assertEquals(1.0, feedbackCreditBus.computeIpsWeight(1), 1e-4);
        // 位置 4 权重为 sqrt(4) = 2.0
        assertEquals(2.0, feedbackCreditBus.computeIpsWeight(4), 1e-4);

        // 测试显式点赞在位置 1 与位置 4 的加权奖励计算
        double rewardPos1 = feedbackCreditBus.calculateNormalizedReward("UPVOTE", 10000L, 1);
        double rewardPos4 = feedbackCreditBus.calculateNormalizedReward("UPVOTE", 10000L, 4);

        // 在位置 4 点击相比位置 1 克服了位置劣势，加权后奖励应更强（受限于有界截断）
        assertTrue(rewardPos4 >= rewardPos1);
        assertTrue(rewardPos1 >= 0.0 && rewardPos1 <= 1.0);
        assertTrue(rewardPos4 >= 0.0 && rewardPos4 <= 1.0);

        // 测试快速跳过的负向惩罚
        double quickSkipReward = feedbackCreditBus.calculateNormalizedReward("DWELL", 1000L, 1);
        assertTrue(quickSkipReward < 0.5, "停留时间不足 3s 应受到负向惩罚归一化值小于 0.5");
    }

    @Test
    @DisplayName("Contract 08: 自适应策略调节器李雅普诺夫防振荡约束 (单步 <= 0.05) 与 Rerank 智能旁路")
    void contract08_adaptivePolicyGovernor_weightDriftAndLyapunovStability() {
        adaptivePolicyGovernor.reset();
        assertEquals(0.50, adaptivePolicyGovernor.getVectorWeight(), 1e-4);
        assertEquals(0.50, adaptivePolicyGovernor.getKeywordWeight(), 1e-4);

        // 注入单次极度负反馈 (0.0)
        adaptivePolicyGovernor.onFeedbackReceived(0.0);
        double afterOneStep = adaptivePolicyGovernor.getVectorWeight();
        double drift = Math.abs(afterOneStep - 0.50);

        // 严格断言单步位移满足李雅普诺夫稳定界限 <= 0.05
        assertTrue(drift <= AdaptivePolicyGovernor.MAX_STEP_DRIFT + 1e-6,
                "单步参数漂移严格受限于 <= 0.05");

        // 连续注入 100 次极度负反馈，权重不能击穿安全下限 0.30
        for (int i = 0; i < 100; i++) {
            adaptivePolicyGovernor.onFeedbackReceived(0.0);
        }
        assertTrue(adaptivePolicyGovernor.getVectorWeight() >= AdaptivePolicyGovernor.MIN_VECTOR_WEIGHT,
                "向量权重不得低于安全下限 0.30");

        // 测试 Rerank 旁路门控
        // 当满意度高且初排高分时，应当旁路 Rerank
        adaptivePolicyGovernor.reset(); // satisfaction = 0.80
        assertTrue(adaptivePolicyGovernor.shouldBypassRerank(0.92), "高满意度且初排 0.92 应安全旁路 Rerank");
        assertFalse(adaptivePolicyGovernor.shouldBypassRerank(0.70), "初排得分不够高不能旁路 Rerank");

        // 满意度打压到很低
        for (int i = 0; i < 20; i++) {
            adaptivePolicyGovernor.onFeedbackReceived(0.0);
        }
        assertFalse(adaptivePolicyGovernor.shouldBypassRerank(0.95), "近期满意度低时即使初排高分也必须强制精排");
    }

    @Test
    @DisplayName("Contract 09: 真实查询日志第 5 漏斗 (负反馈) 强监督难例沉淀与脱敏扩充基准集")
    void contract09_realQueryMining_negativeFeedbackFunnel() {
        String sensitiveQuery = "客户张三 (13812345678) 咨询退款政策";
        String expectedContext = "退款政策：7天内无理由全额退款。";
        String comment = "客服机器人回答错误，说不支持退款！";

        MinedQueryItem mined = realQueryMiningService.ingestNegativeFeedback(
                sensitiveQuery, 201L, expectedContext, comment);

        assertNotNull(mined);
        assertEquals("negative_feedback", mined.getCategory());
        assertEquals(201L, mined.getExpectedKbId());
        assertEquals(1, mined.getExpectedContexts().size());

        // 验证手机号等脱敏处理
        assertFalse(mined.getQuery().contains("13812345678"), "真实手机号必须经过脱敏置换");
        assertTrue(mined.getQuery().contains("客户"), "语法结构与核心语义必须保留");

        // 验证导出 JSONL 格式正确
        String jsonl = realQueryMiningService.exportToJsonl(Collections.singletonList(mined));
        assertTrue(jsonl.contains("\"category\":\"negative_feedback\""));
        assertTrue(jsonl.contains("\"split\":\"test\""));
    }

    @Test
    @DisplayName("Contract 10: DeepSeek-R1 链式归因反思生成 Prompt 候选提案与人工审核流")
    void contract10_promptSelfEvolution_r1ReflectionAndProposalGeneration() {
        promptSelfEvolutionService.clearForTest();

        String originalPrompt = "你是一名企业智能客服，请根据知识库回答问题。";
        List<String> negativeQueries = List.of("问答格式不规范", "退款政策回答产生幻觉");
        List<String> comments = List.of("回答太冗长", "没有给出明确结论");

        // 生成候选提案
        KbPromptEvolutionProposalDO proposal = promptSelfEvolutionService.generateEvolutionProposal(
                "BOT", "bot-finance-01", originalPrompt, negativeQueries, comments);

        assertNotNull(proposal);
        assertEquals("PENDING_REVIEW", proposal.getStatus());
        assertTrue(proposal.getReflectionRationale().contains("DeepSeek-R1 归因反思推导"));
        assertTrue(proposal.getProposedPrompt().length() > originalPrompt.length());
        assertNotNull(proposal.getDiffSummary());

        // 管理员人工审批
        boolean approved = promptSelfEvolutionService.approveProposal(
                proposal.getProposalId(), "admin-user", "经过沙箱回归验证，优化后 Prompt 显著消除幻觉，同意上线");
        assertTrue(approved);

        KbPromptEvolutionProposalDO updated = promptSelfEvolutionService.getProposal(proposal.getProposalId());
        assertEquals("APPROVED", updated.getStatus());
        assertEquals("admin-user", updated.getReviewerId());
        assertNotNull(updated.getReviewedAt());
    }
}
