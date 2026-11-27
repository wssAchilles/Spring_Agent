package tech.qiantong.qknow.hermes.memory.bitemporal.causal;

import java.util.List;
import java.util.Objects;

/**
 * 双时态查询锚点与时间窗规约（Java 21 不可变 Record）。
 * <p>
 * 彻底消除既有主干检索硬编码 {@code (nowMs, nowMs)} 的时间盲视缺陷，显式区分解读模式、
 * 有效事件时间区间 {@code [validFromEpochMs, validToEpochMs]} 与事务认知快照时刻 {@code txAtEpochMs}。
 * </p>
 */
public record TemporalAnchorSpec(
        QueryTemporalMode mode,
        long validFromEpochMs,
        long validToEpochMs,
        long txAtEpochMs,
        long halfLifeMs,
        List<String> parsedTimeExpressions
) {
    /** 默认时间衰减半衰期：30 天（单位毫秒） */
    public static final long DEFAULT_HALF_LIFE_MS = 30L * 24L * 3600L * 1000L;

    public enum QueryTemporalMode {
        /** 最新当前活跃视图 */
        LATEST,
        /** 历史特定事件截点视图 AS_OF(t_valid, t_tx) */
        POINT_AS_OF,
        /** 时态演化窗口视图 [validFrom, validTo] */
        EVOLUTION_WINDOW,
        /** 事务认知回溯审计视图（查某历史事务时刻系统当时所认知的状态） */
        RETROACTIVE_AUDIT
    }

    public TemporalAnchorSpec {
        Objects.requireNonNull(mode, "mode 不能为空");
        if (validToEpochMs < validFromEpochMs) {
            throw new IllegalArgumentException("时间区间上界 validToEpochMs 不可小于下界 validFromEpochMs");
        }
        halfLifeMs = halfLifeMs <= 0L ? DEFAULT_HALF_LIFE_MS : halfLifeMs;
        parsedTimeExpressions = List.copyOf(parsedTimeExpressions != null ? parsedTimeExpressions : List.of());
    }

    /**
     * 构造最新活跃截面查询锚点。
     */
    public static TemporalAnchorSpec latest(long nowMs) {
        return new TemporalAnchorSpec(QueryTemporalMode.LATEST, nowMs, nowMs, nowMs, DEFAULT_HALF_LIFE_MS, List.of());
    }

    /**
     * 构造指定事件时刻与事务时刻的二维截点查询锚点。
     */
    public static TemporalAnchorSpec pointAsOf(long validTimeMs, long txTimeMs, List<String> exprs) {
        return new TemporalAnchorSpec(
                QueryTemporalMode.POINT_AS_OF, validTimeMs, validTimeMs, txTimeMs, DEFAULT_HALF_LIFE_MS, exprs
        );
    }

    /**
     * 构造覆盖历史演化全区间的时间窗查询锚点。
     */
    public static TemporalAnchorSpec evolutionWindow(long fromMs, long toMs, long txTimeMs, List<String> exprs) {
        long safeTo = Math.max(fromMs, toMs);
        return new TemporalAnchorSpec(
                QueryTemporalMode.EVOLUTION_WINDOW, fromMs, safeTo, txTimeMs, DEFAULT_HALF_LIFE_MS, exprs
        );
    }

    /**
     * 构造历史事务认知审计查询锚点。
     */
    public static TemporalAnchorSpec retroactiveAudit(long validTimeMs, long txTimeMs, List<String> exprs) {
        return new TemporalAnchorSpec(
                QueryTemporalMode.RETROACTIVE_AUDIT, validTimeMs, validTimeMs, txTimeMs, DEFAULT_HALF_LIFE_MS, exprs
        );
    }
}
