package tech.qiantong.qknow.ai.code.model;

/**
 * 代码执行状态枚举
 * 标识沙箱执行及自愈闭环中的终态与瞬态
 */
public enum ExecutionStatus {

    /**
     * 执行成功 (进程退出码 0 且无未捕获异常)
     */
    SUCCESS,

    /**
     * 静态 AST 语法树或运行时越权安全拦截
     */
    SECURITY_VIOLATION,

    /**
     * 执行超时强制熔断 (进程树已被级联强杀)
     */
    TIMEOUT,

    /**
     * 运行时异常退出 (非零退出码或解释器报错栈)
     */
    RUNTIME_ERROR,

    /**
     * 物理资源超限 (内存超限或标准输出缓冲区溢出)
     */
    RESOURCE_EXCEEDED,

    /**
     * 沙箱宿主环境内部异常
     */
    INTERNAL_ERROR
}
