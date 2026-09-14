package tech.qiantong.qknow.hermes.memory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户个性化长期偏好记录：PreferenceRecord
 * 支持：行级 CAS 乐观锁版本号 (version)、废弃指针 (supersededBy)、时态区间 (validFrom, validTo)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum PreferenceStatus {
        ACTIVE,
        SUPERSEDED,
        REJECTED
    }

    /**
     * 偏好键 (如 "tech_stack.backend", "java_version", "dietary.spicy")
     */
    private String preferenceKey;

    /**
     * 偏好值 (如 "Java 21", "Spring Boot 3", "不吃辣")
     */
    private String preferenceValue;

    /**
     * 行级 CAS 乐观锁版本号 (并发更新防脑裂)
     */
    @Builder.Default
    private int version = 1;

    /**
     * 偏好状态
     */
    @Builder.Default
    private PreferenceStatus status = PreferenceStatus.ACTIVE;

    /**
     * 若已被覆盖，记录替代该偏好的新偏好键值或 ID
     */
    private String supersededBy;

    /**
     * 有效起始时间戳 (毫秒)
     */
    private long validFrom;

    /**
     * 有效终止时间戳 (毫秒，若当前有效则为 null 或 0)
     */
    private Long validTo;

    /**
     * 最近更新时间戳
     */
    private long updatedAt;

    /**
     * 判定在指定时间戳下该偏好是否有效
     */
    public boolean isValidAt(long queryTime) {
        if (status != PreferenceStatus.ACTIVE) {
            return false;
        }
        if (queryTime < validFrom) {
            return false;
        }
        return validTo == null || validTo == 0L || queryTime < validTo;
    }
}
