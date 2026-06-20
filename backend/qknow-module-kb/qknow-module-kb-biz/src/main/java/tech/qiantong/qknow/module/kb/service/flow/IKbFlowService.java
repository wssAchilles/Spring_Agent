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
}
