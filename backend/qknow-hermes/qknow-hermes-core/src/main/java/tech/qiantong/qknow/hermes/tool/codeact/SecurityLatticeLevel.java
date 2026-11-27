package tech.qiantong.qknow.hermes.tool.codeact;

/**
 * 信息流安全格等级偏序定义（对应 H-NSCDS v3 定理 8.2）。
 * <p>
 * 偏序全链：{@code PURE_ARITH < RO_DATA_READ < SANDBOX_MEM < FS_MUTATE < NET_IO < HOST_PRIV}。
 * 安全格门禁仅允许 {@link #isWithin(SecurityLatticeLevel)} 不超过 {@link #SANDBOX_MEM} 的只读程序执行。
 * </p>
 */
public enum SecurityLatticeLevel {
    /** 零进程纯确定性数学运算（Tier-0 内置算术） */
    PURE_ARITH(0),
    /** 仅消费不可变只读内存数据视图 ReadOnlyDatasetView */
    RO_DATA_READ(1),
    /** 受限临时沙盒内存隔离执行（Tier-1） */
    SANDBOX_MEM(2),
    /** 宿主文件系统写入权限（严禁准入） */
    FS_MUTATE(3),
    /** 网络 I/O 访问权限（严禁准入） */
    NET_IO(4),
    /** 宿主特权级操作（静默拦截） */
    HOST_PRIV(5);

    private final int rank;

    SecurityLatticeLevel(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    /**
     * 判断当前安全格等级是否在最大允许上界 {@code maxAllowed} 之内。
     */
    public boolean isWithin(SecurityLatticeLevel maxAllowed) {
        return maxAllowed != null && this.rank <= maxAllowed.rank;
    }
}
