package tech.qiantong.qknow.module.kb.service.judge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI Judge 评分服务（控制面存根）
 *
 * 评分功能已迁移到 Hermes 微服务。此类仅保留阈值管理功能，
 * 供 AiJudgeController 使用。
 */
@Slf4j
@Service
public class AiJudgeService {

    private double threshold = 0.7;

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public double getThreshold() {
        return threshold;
    }

    /**
     * @deprecated 评分逻辑已迁移到 Hermes 微服务
     */
    @Deprecated
    public JudgeResult judge(String query, String context, String answer) {
        throw new UnsupportedOperationException("评分功能已迁移到 Hermes 微服务，请通过 gRPC 调用");
    }
}
