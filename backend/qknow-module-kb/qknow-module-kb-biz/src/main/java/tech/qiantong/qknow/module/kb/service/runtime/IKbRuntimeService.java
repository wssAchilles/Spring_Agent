package tech.qiantong.qknow.module.kb.service.runtime;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kb.controller.admin.runtime.vo.KbRuntimePageReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.runtime.KbRuntimeDO;
import tech.qiantong.qknow.module.kb.service.flow.bo.KbFlowBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO;

import java.util.Collection;

/**
 * bot运行Service接口
 *
 * @author qknow
 * @date 2026-03-18
 */
public interface IKbRuntimeService extends IService<KbRuntimeDO> {

    /**
     * 获得bot运行分页列表
     *
     * @param pageReqVO 分页请求
     * @return bot运行分页列表
     */
    PageResult<KbRuntimeDO> getKbRuntimePage(KbRuntimePageReqVO pageReqVO);

    /**
     * 删除bot运行
     *
     * @param idList bot运行编号
     */
    int removeKbRuntime(Collection<Long> idList);

    /**
     * 获得bot运行详情
     *
     * @param id bot运行编号
     * @return bot运行
     */
    KbRuntimeDO getKbRuntimeById(Long id);

    /**
     * 创建运行时上下文
     *
     * @param flowBO 流程的 bo 对象
     * @param input  输入参数
     * @return 运行时上下文
     */
    RuntimeContextBO createRuntimeContext(KbFlowBO flowBO, JSONObject input);

    /**
     * 创建并保存运行时上下文
     *
     * @param flowBO 流程的 bo 对象
     * @param input  输入参数
     * @return 运行时上下文
     */
    RuntimeContextBO createSaveRuntimeContext(KbFlowBO flowBO, JSONObject input);

    /**
     * 保存节点运行状态
     *
     * @param nodeResult     节点执行结果
     * @param runtimeContext 流程的运行上下文
     */
    void saveRuntimeNode(NodeRunResultBO nodeResult, RuntimeContextBO runtimeContext);

    /**
     * 保存运行成功的记录
     *
     * @param runtimeDO 运行时记录
     */
    void saveRunSuccess(KbRuntimeDO runtimeDO);

    /**
     * 保存运行失败的记录
     *
     * @param runtimeDO 运行时记录
     */
    void saveRunError(KbRuntimeDO runtimeDO);
}
