package tech.qiantong.qknow.ai.embodied.micronano.dto;

import java.util.Arrays;
import java.util.Objects;

/**
 * 具身微纳视触力全状态时序帧 (Java 21 Record)
 * 封装工件微米级坐标、压电位移驱动电压、微牛级接触力矩、高频剪切微振动频率、显微离焦能量与阿里千问 1536 维超球面单位向量。
 *
 * @param frameId 帧唯一标识
 * @param timestampNs 纳秒时间戳
 * @param workpieceXMicron 工件空间 X 轴微米坐标 (um)
 * @param workpieceYMicron 工件空间 Y 轴微米坐标 (um)
 * @param workpieceZMicron 工件空间 Z 轴微米坐标 (um)
 * @param piezoVoltageX 压电执行器 X 轴驱动电压 (V)
 * @param piezoVoltageY 压电执行器 Y 轴驱动电压 (V)
 * @param piezoVoltageZ 压电执行器 Z 轴驱动电压 (V)
 * @param contactForceMicroN 微牛级接触力 (uN)
 * @param shearFrequencyHz 压电高频剪切微振动频率 (Hz)
 * @param defocusBlurEnergy 显微视觉离焦模糊能量测度 (0.0~1.0)
 * @param qwenEmbedding1536 阿里千问 1536 维超球面单位特征向量 (模长严格为 1.0 +- 1e-4)
 * @author Achilles
 * @since 2026-09-16
 */
public record MicroNanoStateFrame(
        String frameId,
        long timestampNs,
        double workpieceXMicron,
        double workpieceYMicron,
        double workpieceZMicron,
        double piezoVoltageX,
        double piezoVoltageY,
        double piezoVoltageZ,
        double contactForceMicroN,
        double shearFrequencyHz,
        double defocusBlurEnergy,
        double[] qwenEmbedding1536
) {
    public MicroNanoStateFrame {
        Objects.requireNonNull(frameId, "frameId cannot be null");
        if (qwenEmbedding1536 == null || qwenEmbedding1536.length != 1536) {
            throw new IllegalArgumentException("qwenEmbedding1536 must be non-null and exactly 1536 dimensions, got: "
                    + (qwenEmbedding1536 == null ? "null" : qwenEmbedding1536.length));
        }
        double normSq = 0.0;
        for (double v : qwenEmbedding1536) {
            normSq += v * v;
        }
        double norm = Math.sqrt(normSq);
        if (Math.abs(norm - 1.0) > 1e-4) {
            throw new IllegalArgumentException("qwenEmbedding1536 must be normalized to unit length on S^1535, norm: " + norm);
        }
        if (defocusBlurEnergy < 0.0 || defocusBlurEnergy > 1.0) {
            throw new IllegalArgumentException("defocusBlurEnergy must be in [0.0, 1.0], got: " + defocusBlurEnergy);
        }
        // 防御性拷贝
        qwenEmbedding1536 = qwenEmbedding1536.clone();
    }

    @Override
    public double[] qwenEmbedding1536() {
        return qwenEmbedding1536 == null ? null : qwenEmbedding1536.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MicroNanoStateFrame that)) return false;
        return timestampNs == that.timestampNs &&
                Double.compare(that.workpieceXMicron, workpieceXMicron) == 0 &&
                Double.compare(that.workpieceYMicron, workpieceYMicron) == 0 &&
                Double.compare(that.workpieceZMicron, workpieceZMicron) == 0 &&
                Double.compare(that.piezoVoltageX, piezoVoltageX) == 0 &&
                Double.compare(that.piezoVoltageY, piezoVoltageY) == 0 &&
                Double.compare(that.piezoVoltageZ, piezoVoltageZ) == 0 &&
                Double.compare(that.contactForceMicroN, contactForceMicroN) == 0 &&
                Double.compare(that.shearFrequencyHz, shearFrequencyHz) == 0 &&
                Double.compare(that.defocusBlurEnergy, defocusBlurEnergy) == 0 &&
                frameId.equals(that.frameId) &&
                Arrays.equals(qwenEmbedding1536, that.qwenEmbedding1536);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(frameId, timestampNs, workpieceXMicron, workpieceYMicron, workpieceZMicron,
                piezoVoltageX, piezoVoltageY, piezoVoltageZ, contactForceMicroN, shearFrequencyHz, defocusBlurEnergy);
        result = 31 * result + Arrays.hashCode(qwenEmbedding1536);
        return result;
    }
}
