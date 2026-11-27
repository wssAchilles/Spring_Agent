package tech.qiantong.qknow.hermes.cognitive.verification;

/**
 * EG-VAR 溯源证据可信度偏序全序格（只降级不升级格约束）。
 * <p>
 * 偏序定义：{@code UNVERIFIED(0) < TOOL_WITNESS(1) < DOUBLE_ATTESTED(2)}。
 * 在推理链组合递推时，合成证据等级只能取子证据集合的最小下界 {@code min}，严禁虚高抬级。
 * </p>
 */
public enum EvidenceGrade {

    /** 未经实证工具或外部知识源核验的纯文本断言 */
    UNVERIFIED(0),
    /** 具备经签名工具调用输出凭据（AttestedToolWitness）支撑 */
    TOOL_WITNESS(1),
    /** 同时满足双时态海马体图谱交叉印证与 Hoare/Ariadne 双向核验 */
    DOUBLE_ATTESTED(2);

    private final int latticeRank;

    EvidenceGrade(int latticeRank) {
        this.latticeRank = latticeRank;
    }

    public int latticeRank() {
        return latticeRank;
    }

    /**
     * 只降级不升级格最小下界算子（Meet）：{@code meet(a, b) = min(a, b)}。
     */
    public static EvidenceGrade meet(EvidenceGrade a, EvidenceGrade b) {
        if (a == null || b == null) {
            return UNVERIFIED;
        }
        return a.latticeRank <= b.latticeRank ? a : b;
    }

    /**
     * 校验合成目标等级是否满足单调不升格公理：目标等级绝不能高于任一前驱证据等级。
     */
    public static boolean satisfiesMonotonicNonInflation(EvidenceGrade target, EvidenceGrade... premises) {
        if (target == null) {
            return false;
        }
        if (premises == null || premises.length == 0) {
            return target == UNVERIFIED;
        }
        EvidenceGrade ceiling = DOUBLE_ATTESTED;
        for (EvidenceGrade p : premises) {
            ceiling = meet(ceiling, p);
        }
        return target.latticeRank <= ceiling.latticeRank;
    }
}
