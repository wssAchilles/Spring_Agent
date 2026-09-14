package tech.qiantong.qknow.module.kb.controller.admin.feedback;

import com.google.common.util.concurrent.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.common.core.controller.BaseController;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.module.kb.api.feedback.dto.ChatFeedbackReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.feedback.KbChatFeedbackDO;
import tech.qiantong.qknow.module.kb.service.evolution.PromptSelfEvolutionService;
import tech.qiantong.qknow.module.kb.service.feedback.FeedbackStreamQueueService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * 高并发防刷防投毒对话反馈采集控制器 (基于 Spring 事件发布与削峰流式队列解耦)
 */
@Slf4j
@Tag(name = "Agent 对话反馈采集与策略自进化")
@RestController
@RequestMapping("/kb/feedback")
@Validated
public class FeedbackCollectorController extends BaseController {

    public static final String HMAC_SECRET = "qknow-phase24-hmac-salt-key-2026";
    private static final long TIMESTAMP_EXPIRE_WINDOW_MS = 10 * 60 * 1000L; // 10分钟防重放

    // 令牌桶限流器 (默认 2000 QPS)
    private final RateLimiter rateLimiter = RateLimiter.create(2000.0);

    @Resource
    private FeedbackStreamQueueService feedbackQueueService;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private PromptSelfEvolutionService promptSelfEvolutionService;

    @Operation(summary = "提交对话反馈 (带 HMAC 防伪防刷)")
    @PostMapping("/submit")
    public CommonResult<Boolean> submitFeedback(@Valid @RequestBody ChatFeedbackReqVO reqVO) {
        // 1. 令牌桶限流保护
        if (!rateLimiter.tryAcquire()) {
            log.warn("反馈采集触发限流: msgId={}", reqVO.getMessageId());
            return CommonResult.error(429, "反馈请求过于频繁，请稍后再试");
        }

        // 2. HMAC-SHA256 加盐防伪签名与防重放校验
        if (!verifySignature(reqVO)) {
            log.warn("反馈签名非法或已过期: msgId={}, sig={}", reqVO.getMessageId(), reqVO.getSignature());
            return CommonResult.error(403, "非法签名或请求已过期");
        }

        // 3. 计算 IPS 逆倾向加权与无偏奖励
        double ipsWeight = computeIpsWeight(reqVO.getPositionIdx());
        double normalizedReward = calculateNormalizedReward(
                reqVO.getFeedbackType(), reqVO.getDwellTimeMs(), reqVO.getPositionIdx());

        // 4. 构造持久化实体并推入削峰队列
        KbChatFeedbackDO feedbackDO = KbChatFeedbackDO.builder()
                .conversationId(reqVO.getConversationId())
                .messageId(reqVO.getMessageId())
                .userId(reqVO.getUserId() != null ? reqVO.getUserId() : "anonymous")
                .tenantId(getWorkSpaceId() != null ? getWorkSpaceId() : 1L)
                .feedbackType(reqVO.getFeedbackType())
                .rating(reqVO.getRating())
                .dwellTimeMs(reqVO.getDwellTimeMs())
                .comment(reqVO.getComment())
                .correctedText(reqVO.getCorrectedText())
                .actionType(reqVO.getActionType())
                .positionIdx(reqVO.getPositionIdx())
                .ipsWeight(ipsWeight)
                .signature(reqVO.getSignature())
                .build();

        feedbackQueueService.enqueueFeedback(feedbackDO);

        // 5. 发布解耦事件通知下游 (如 KMC 模块的自适应调节器与难例挖掘)
        if (eventPublisher != null) {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("type", "CHAT_FEEDBACK");
            eventData.put("feedbackType", reqVO.getFeedbackType());
            eventData.put("normalizedReward", normalizedReward);
            eventData.put("ipsWeight", ipsWeight);
            eventData.put("messageId", reqVO.getMessageId());
            eventData.put("comment", reqVO.getComment());
            eventData.put("correctedText", reqVO.getCorrectedText());
            eventPublisher.publishEvent(eventData);
        }

        // 6. 负反馈强监督 Prompt 候选反思
        if ("DOWNVOTE".equalsIgnoreCase(reqVO.getFeedbackType()) ||
                (reqVO.getCorrectedText() != null && !reqVO.getCorrectedText().isBlank())) {

            if (promptSelfEvolutionService != null) {
                promptSelfEvolutionService.generateEvolutionProposal(
                        "BOT",
                        reqVO.getConversationId(),
                        "默认系统提示词",
                        Collections.singletonList(reqVO.getMessageId()),
                        Collections.singletonList(reqVO.getComment() != null ? reqVO.getComment() : "回答存在幻觉")
                );
            }
        }

        return CommonResult.success(true);
    }

    /**
     * 验证客户端 HMAC-SHA256 签名与时间戳有效性
     */
    public static boolean verifySignature(ChatFeedbackReqVO reqVO) {
        if (reqVO == null || reqVO.getSignature() == null || reqVO.getTimestamp() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - reqVO.getTimestamp()) > TIMESTAMP_EXPIRE_WINDOW_MS) {
            return false; // 超出有效时间窗口
        }

        String expected = calculateSignature(
                reqVO.getMessageId(), reqVO.getFeedbackType(), reqVO.getTimestamp(), HMAC_SECRET);
        return expected.equalsIgnoreCase(reqVO.getSignature());
    }

    /**
     * 计算 HMAC-SHA256 签名字符串
     */
    public static String calculateSignature(String messageId, String feedbackType, Long timestamp, String secret) {
        try {
            String payload = messageId + ":" + feedbackType + ":" + timestamp;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            log.error("计算 HMAC 异常: {}", e.getMessage());
            return "";
        }
    }

    public static double computeIpsWeight(Integer positionIdx) {
        if (positionIdx == null || positionIdx <= 1) {
            return 1.0;
        }
        return Math.min(3.0, Math.sqrt(positionIdx));
    }

    public static double calculateNormalizedReward(String feedbackType, Long dwellTimeMs, Integer positionIdx) {
        double baseUtility = 0.0;
        if (feedbackType != null) {
            switch (feedbackType.toUpperCase()) {
                case "UPVOTE":
                    baseUtility = 1.0;
                    break;
                case "DOWNVOTE":
                    baseUtility = -0.8;
                    break;
                case "COPY":
                    baseUtility = 0.6;
                    break;
                case "CORRECTION":
                    baseUtility = -0.5;
                    break;
                case "DWELL":
                    if (dwellTimeMs == null || dwellTimeMs < 3000) {
                        baseUtility = -0.2;
                    } else if (dwellTimeMs <= 60000) {
                        baseUtility = Math.min(0.5, 0.2 + (dwellTimeMs - 3000.0) / 100000.0);
                    } else {
                        baseUtility = 0.2;
                    }
                    break;
                default:
                    baseUtility = 0.0;
            }
        }
        double ipsWeight = computeIpsWeight(positionIdx);
        double weightedUtility = baseUtility * ipsWeight;
        double clamped = Math.max(-1.0, Math.min(1.0, weightedUtility));
        return (clamped + 1.0) / 2.0;
    }
}
