package tech.qiantong.qknow.module.kb.service.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.module.kb.controller.admin.flow.vo.KbFlowVO;
import tech.qiantong.qknow.module.kb.controller.admin.runtime.vo.KbRuntimeRespVO;

import java.util.List;

/**
 * bot流程节点Service接口
 *
 * @author qknow
 * @date 2026-03-18
 */
public interface IKbFlowService {
    /**
     * 根据 BotId 查询流程
     *
     * @param botId botId
     * @return 流程对象
     */
    KbFlowVO queryFlow(Long botId);

    /**
     * 根据 BotId 删除流程
     *
     * @param botId botId
     */
    void deleteByBotId(Long botId);

    /**
     * 创建流程
     *
     * @param flowVO 流程对象
     * @return 操作是否成功
     */
    Boolean submitFlow(KbFlowVO flowVO);

    /**
     * 测试执行流程
     *
     * @param flowVO 流程对象
     * @param input  输入参数
     * @return 执行结果
     */
    Flux<CommonResult<String>> testExecuteFlow(KbFlowVO flowVO, JSONObject input);

    /**
     * 测试运行 chatFlow
     *
     * @param flowVO 流程对象
     * @param input  输入参数
     * @return 执行结果
     */
    Flux<CommonResult<String>> testExecuteChatFlow(KbFlowVO flowVO, JSONObject input, JSONArray messageList);

    /**
     * 正式执行流程
     *
     * @param flowVO 流程对象
     * @param input  输入参数
     * @return 执行结果
     */
    KbRuntimeRespVO executeFlow(KbFlowVO flowVO, JSONObject input);

    /**
     * 正式执行流程(流式输出)
     *
     * @param flowVO 流程对象
     * @param input  输入参数
     * @return 执行结果
     */
    Flux<CommonResult<String>> executeFlowStream(KbFlowVO flowVO, JSONObject input);

    /**
     * 正式运行 chatFlow
     *
     * @param flowVO      流程对象
     * @param input       输入参数
     * @param messageList 消息列表
     * @return 执行结果
     */
    Flux<CommonResult<String>> executeChatFlow(KbFlowVO flowVO, JSONObject input, List<Message> messageList);

    /**
     * 断点续跑工作流 (基于 Checkpoint 恢复崩溃或挂起的工作流并接续拓扑执行)
     *
     * @param runtimeId  工作流运行实例 ID
     * @param humanInput 人工协同审批或额外变量（可选）
     * @return 执行结果
     */
    KbRuntimeRespVO resumeFlow(Long runtimeId, java.util.Map<String, Object> humanInput);

    /**
     * 审批闭环专用恢复入口：仅由服务端审批代理在审批通过后调用。
     * 外部 resumeFlow 不允许携带审批放行变量，避免客户端绕过高危工具审批门禁。
     *
     * @param runtimeId   运行实例
     * @param humanInput  人工补充输入（不含审批变量）
     * @param approvalKey 审批单键，用于审计与追溯
     * @return 恢复后的运行实例
     */
    KbRuntimeRespVO resumeFlowWithApproval(Long runtimeId, java.util.Map<String, Object> humanInput, String approvalKey);
}
