package tech.qiantong.qknow.hermes.cognitive.evolution;

/**
 * RHO 超球面黎曼切空间自我演化不可变凭单（Java 21 Record）。
 */
public record RhoEvolutionReceipt(
        int evaluatedPairCount,
        int updatedPrototypeArmsCount,
        int recordedErrorBookCount,
        double averageUtilityMargin,
        long timestampMs
) {
}
