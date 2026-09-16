package tech.qiantong.qknow.module.app.service.kac;

import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.AppRunReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.AppRunResultRespVO;

/**
 * 应用中心核心执行与编排引擎
 *
 * @author qknow
 */
public interface IAppExecutionEngine {

    /**
     * 同步执行应用
     *
     * @param reqVO 执行请求入参
     * @param userId 操作用户ID
     * @param username 操作用户名
     * @param workspaceId 当前工作空间ID
     * @return 执行结果与审计凭单
     */
    AppRunResultRespVO run(AppRunReqVO reqVO, Long userId, String username, Long workspaceId);

    /**
     * 流式执行应用 (通过 SSE 流式打字机推送帧)
     *
     * @param reqVO 执行请求入参
     * @param userId 操作用户ID
     * @param username 操作用户名
     * @param workspaceId 当前工作空间ID
     * @return SSE Emitter 句柄
     */
    ResponseBodyEmitter streamRun(AppRunReqVO reqVO, Long userId, String username, Long workspaceId);
}
