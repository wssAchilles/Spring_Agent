package tech.qiantong.qknow.ai.pipeline.stage;

/**
 * 模型执行回调接口 (ModelExecutionCallback)
 *
 * 用于承接底层的 ProceedingJoinPoint.proceed() 或模型网关代理调用。
 *
 * @author qknow
 */
@FunctionalInterface
public interface ModelExecutionCallback {

    /**
     * 执行底层业务或模型生成
     *
     * @return 执行结果对象
     * @throws Throwable 业务或调用异常
     */
    Object call() throws Throwable;
}
