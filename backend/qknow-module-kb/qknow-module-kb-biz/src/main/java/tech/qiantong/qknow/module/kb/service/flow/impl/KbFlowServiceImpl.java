package tech.qiantong.qknow.module.kb.service.flow.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.qiantong.qknow.common.exception.ServiceException;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.common.core.utils.object.BeanUtils;
import tech.qiantong.qknow.module.kb.api.flow.util.BotUtil;
import tech.qiantong.qknow.module.kb.controller.admin.flow.vo.KbFlowVO;
import tech.qiantong.qknow.module.kb.controller.admin.runtime.vo.KbRuntimeRespVO;
import tech.qiantong.qknow.module.kb.convert.flow.KbFlowConvert;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowEdgeDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.runtime.KbRuntimeDO;
import tech.qiantong.qknow.module.kb.dal.enums.BotExecuteModeEnum;
import tech.qiantong.qknow.module.kb.dal.enums.BotTypeEnums;
import tech.qiantong.qknow.module.kb.dal.enums.FlowNodeTypeEnums;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.module.kb.service.flow.IKbFlowEdgeService;
import tech.qiantong.qknow.module.kb.service.flow.IKbFlowNodeService;
import tech.qiantong.qknow.module.kb.service.flow.IKbFlowService;
import tech.qiantong.qknow.module.kb.service.flow.bo.BaseNodeBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.KbFlowBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO;
import tech.qiantong.qknow.module.kb.service.flow.factory.NodeFactory;
import tech.qiantong.qknow.module.kb.service.runtime.IKbRuntimeService;
import tech.qiantong.qknow.module.kb.service.bot.IKbBotService;
import tech.qiantong.qknow.module.kb.dal.dataobject.bot.KbBotDO;
import tech.qiantong.qknow.hermes.flow.dag.DagUtils;
import tech.qiantong.qknow.hermes.flow.dag.DagCheckpointManager;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * bot流程节点Service业务层处理
 *
 * @author qknow
 * @date 2026-03-18
 */
@Slf4j
@Service
public class KbFlowServiceImpl implements IKbFlowService {

    @Resource
    private IKbFlowNodeService flowNodeService;
    @Resource
    private IKbFlowEdgeService flowEdgeService;
    @Resource
    private IKbRuntimeService runtimeService;
    @Resource
    private IKbBotService botService;
    @Resource
    private NodeFactory nodeFactory;

    @Autowired(required = false)
    private DagCheckpointManager dagCheckpointManager;

    private final ExecutorService dagExecutorService = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 根据 BotId 查询流程
     *
     * @param botId botId
     * @return 流程对象
     */
    @Override
    public KbFlowVO queryFlow(Long botId) {
        KbFlowVO result = new KbFlowVO();
        result.setBotId(botId);
        result.setNodes(flowNodeService.flowVOByBotId(botId));
        result.setEdges(flowEdgeService.flowVOByBotId(botId));
        return result;
    }

    /**
     * 根据 BotId 删除流程
     *
     * @param botId botId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByBotId(Long botId) {
        flowNodeService.removeByBotId(botId);
        flowEdgeService.removeByBotId(botId);
    }

    /**
     * 创建流程
     *
     * @param flowVO 流程对象
     * @return 操作是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean submitFlow(KbFlowVO flowVO) {
        boolean nodeResult = flowNodeService.submitBatch(flowVO);
        boolean edgeResult = flowEdgeService.submitBatch(flowVO);
        return nodeResult & edgeResult;
    }

    /**
     * 测试执行流程
     *
     * @param flowVO 流程对象
     * @param input  输入参数
     * @return 执行结果
     */
    @Override
    public Flux<CommonResult<String>> testExecuteFlow(KbFlowVO flowVO, JSONObject input) {
        KbFlowBO kbFlowBO = KbFlowConvert.toFlowBO(flowVO);
        RuntimeContextBO runtimeContext = runtimeService.createRuntimeContext(kbFlowBO, input);
        runtimeContext.setBotType(BotTypeEnums.WORK_FLOW);
        runtimeContext.setExecuteMode(BotExecuteModeEnum.STREAM);
        textExecuteFlow(kbFlowBO, runtimeContext);
//        Flux<CommonResult<String>> resultFlux = runtimeContext.getResultFlux();
//        // 添加一条总的数据
//        Mono<CommonResult<String>> map = resultFlux
//                .map(CommonResult::getData)
//                .collectList()
//                .map(this::margeOutPut)
//                .map(CommonResult::success);
//        runtimeContext.setResultFlux(Flux.concat(resultFlux, map));
        Flux<CommonResult<String>> resultFlux = runtimeContext.getResultFlux();
        if (resultFlux == null) {
            String errorMsg = runtimeContext.getRuntimeDO() != null ? runtimeContext.getRuntimeDO().getOutput() : null;
            String msg = (errorMsg != null && !errorMsg.isEmpty()) ? errorMsg : "工作流执行失败，未产生回复";
            return Flux.just(CommonResult.error(new ServiceException(msg, 500)));
        }
        Flux<CommonResult<String>> started = Flux.just(CommonResult.success("{\"event\":\"started\",\"text\":\"\"}"));
        return Flux.concat(started, resultFlux).onErrorResume(throwable ->
                Flux.just(CommonResult.error(new ServiceException("大模型调用异常: " + throwable.getMessage(), 500))));
    }

    /**
     * 测试运行 chatFlow
     *
     * @param flowVO 流程对象
     * @param input  输入参数
     * @return 执行结果
     */
    @Override
    public Flux<CommonResult<String>> testExecuteChatFlow(KbFlowVO flowVO, JSONObject input, JSONArray messageArray) {
        KbFlowBO kbFlowBO = KbFlowConvert.toFlowBO(flowVO);
        RuntimeContextBO runtimeContext = runtimeService.createRuntimeContext(kbFlowBO, input);
        runtimeContext.setExecuteMode(BotExecuteModeEnum.STREAM);
        runtimeContext.setBotType(BotTypeEnums.CHAT_FLOW);
        if (Objects.nonNull(messageArray) && messageArray.size() > 0) {
            List<Message> messageList = new ArrayList<>(messageArray.size());
            for (int i = 0; i < messageArray.size(); i++) {
                JSONObject messageJson = messageArray.getJSONObject(i);
                String role = messageJson.getString("role");
                String context = messageJson.getString("context");
                if (Objects.equals(role, "assistant")) {
                    messageList.add(new AssistantMessage(context));
                } else {
                    messageList.add(new UserMessage(context));
                }
            }
            runtimeContext.setMessageList(messageList);
        }
        textExecuteFlow(kbFlowBO, runtimeContext);
        Flux<CommonResult<String>> resultFlux = runtimeContext.getResultFlux();
        if (resultFlux == null) {
            String errorMsg = runtimeContext.getRuntimeDO() != null ? runtimeContext.getRuntimeDO().getOutput() : null;
            String msg = (errorMsg != null && !errorMsg.isEmpty()) ? errorMsg : "工作流执行失败，未产生回复";
            return Flux.just(CommonResult.error(new ServiceException(msg, 500)));
        }
        return resultFlux.onErrorResume(throwable ->
                Flux.just(CommonResult.error(new ServiceException("大模型调用异常: " + throwable.getMessage(), 500)))
        );
    }

    /**
     * 执行流程
     *
     * @param flowVO 流程对象
     * @param input  输入参数
     * @return 执行结果
     */
    @Override
    public KbRuntimeRespVO executeFlow(KbFlowVO flowVO, JSONObject input) {
        KbFlowBO kbFlowBO = KbFlowConvert.toFlowBO(flowVO);
        kbFlowBO.setNodeList(flowNodeService.listByBotId(flowVO.getBotId()));
        kbFlowBO.setEdgeList(flowEdgeService.listByBotId(flowVO.getBotId()));
        RuntimeContextBO runtimeContext = runtimeService.createSaveRuntimeContext(kbFlowBO, input);
        runtimeContext.setExecuteMode(BotExecuteModeEnum.BLOCK);
        runtimeContext.setBotType(BotTypeEnums.WORK_FLOW);
        return executeFlow(kbFlowBO, runtimeContext);
    }

    /**
     * 正式执行流程(流式输出)
     *
     * @param flowVO 流程对象
     * @param input  输入参数
     * @return 执行结果
     */
    @Override
    public Flux<CommonResult<String>> executeFlowStream(KbFlowVO flowVO, JSONObject input) {
        KbFlowBO kbFlowBO = KbFlowConvert.toFlowBO(flowVO);
        kbFlowBO.setNodeList(flowNodeService.listByBotId(flowVO.getBotId()));
        kbFlowBO.setEdgeList(flowEdgeService.listByBotId(flowVO.getBotId()));
        RuntimeContextBO runtimeContext = runtimeService.createSaveRuntimeContext(kbFlowBO, input);
        runtimeContext.setExecuteMode(BotExecuteModeEnum.STREAM);
        runtimeContext.setBotType(BotTypeEnums.WORK_FLOW);
        executeFlow(kbFlowBO, runtimeContext);

        Flux<CommonResult<String>> resultFlux = runtimeContext.getResultFlux();
        if (resultFlux == null) {
            String errorMsg = runtimeContext.getRuntimeDO() != null ? runtimeContext.getRuntimeDO().getOutput() : null;
            String msg = (errorMsg != null && !errorMsg.isEmpty()) ? errorMsg : "工作流执行失败，未产生回复";
            return Flux.just(CommonResult.error(new ServiceException(msg, 500)));
        }

        Mono<CommonResult<String>> last = resultFlux.last();
        last.subscribe(result -> {
            runtimeContext.getRuntimeDO().setOutput(result.getData());
            runtimeService.saveRunSuccess(runtimeContext.getRuntimeDO());
        });
        return resultFlux.onErrorResume(throwable ->
                Flux.just(CommonResult.error(new ServiceException("大模型调用异常: " + throwable.getMessage(), 500)))
        );
    }

    /**
     * 正式运行 chatFlow
     *
     * @param flowVO      流程数据
     * @param input       输入参数
     * @param messageList 消息列表
     * @return 执行结果
     */
    @Override
    public Flux<CommonResult<String>> executeChatFlow(KbFlowVO flowVO, JSONObject input, List<Message> messageList) {
        KbFlowBO kbFlowBO = KbFlowConvert.toFlowBO(flowVO);
        kbFlowBO.setNodeList(flowNodeService.listByBotId(flowVO.getBotId()));
        kbFlowBO.setEdgeList(flowEdgeService.listByBotId(flowVO.getBotId()));
        RuntimeContextBO runtimeContext = runtimeService.createSaveRuntimeContext(kbFlowBO, input);
        runtimeContext.setExecuteMode(BotExecuteModeEnum.STREAM);
        runtimeContext.setBotType(BotTypeEnums.CHAT_FLOW);
        runtimeContext.setMessageList(messageList);
        executeFlow(kbFlowBO, runtimeContext);
        Flux<CommonResult<String>> resultFlux = runtimeContext.getResultFlux();
        if (resultFlux == null) {
            String errorMsg = runtimeContext.getRuntimeDO() != null ? runtimeContext.getRuntimeDO().getOutput() : null;
            String msg = (errorMsg != null && !errorMsg.isEmpty()) ? errorMsg : "工作流执行失败，未产生回复";
            return Flux.just(CommonResult.error(new ServiceException(msg, 500)));
        }
        StringBuilder sb = new StringBuilder();
        return resultFlux
                .doOnNext(result -> {
                    String chatResponseContent = BotUtil.getChatResponseContent(result);
                    sb.append(chatResponseContent);
                })
                .doOnComplete(() -> {
                    runtimeContext.getRuntimeDO().setOutput(sb.toString());
                    runtimeService.saveRunSuccess(runtimeContext.getRuntimeDO());
                })
                .onErrorResume(throwable ->
                        Flux.just(CommonResult.error(new ServiceException("大模型调用异常: " + throwable.getMessage(), 500)))
                );
    }

    /**
     * 执行流程
     *
     * @param flowBO 流程运行对象
     * @return 执行结果
     */
    /**
     * 执行流程
     *
     * @param flowBO 流程运行对象
     * @return 执行结果
     */
    public KbRuntimeRespVO executeFlow(KbFlowBO flowBO, RuntimeContextBO runtimeContext) {
        return executeDagPipeline(flowBO, runtimeContext, false);
    }

    /**
     * 测试执行流程
     *
     * @param flowBO 流程运行对象
     * @return 执行结果
     */
    public KbRuntimeRespVO textExecuteFlow(KbFlowBO flowBO, RuntimeContextBO runtimeContext) {
        return executeDagPipeline(flowBO, runtimeContext, true);
    }

    /**
     * DAG 工作流现代执行调度管道 (支持全新执行或从断点接续)
     * 具备：环路快速检测、拓扑分层 Kahn 排序、多分支并发异步执行、条件分支动态递归剪枝、汇聚节点依赖完备保障与不可变 Checkpoint 持久化
     *
     * @param flowBO 流程定义
     * @param runtimeContext 运行时上下文
     * @param isTestMode 是否为测试/调试模式
     * @return 运行响应结果
     */
    private KbRuntimeRespVO executeDagPipeline(KbFlowBO flowBO, RuntimeContextBO runtimeContext, boolean isTestMode) {
        return executeDagPipelineWithResume(flowBO, runtimeContext, isTestMode, 0, new ConcurrentHashMap<>());
    }

    private KbRuntimeRespVO executeDagPipelineWithResume(KbFlowBO flowBO,
                                                         RuntimeContextBO runtimeContext,
                                                         boolean isTestMode,
                                                         int startGroupIndex,
                                                         Map<String, NodeRunResultBO> initialResultMap) {
        log.info("[DAG 调度器] 开始执行工作流, botId={}, isTestMode={}, startGroupIndex={}", flowBO.getBotId(), isTestMode, startGroupIndex);
        // 校验工作流基本结构
        validateWorkflow(flowBO);

        List<KbFlowNodeDO> flowNodes = flowBO.getNodeList();
        List<KbFlowEdgeDO> flowEdges = flowBO.getEdgeList() != null ? flowBO.getEdgeList() : Collections.emptyList();

        List<String> nodeUuids = flowNodes.stream().map(KbFlowNodeDO::getUuid).toList();
        List<Map.Entry<String, String>> edgePairs = flowEdges.stream()
                .map(e -> Map.entry(e.getSourceNodeUuid(), e.getTargetNodeUuid()))
                .toList();

        // 1. 验证 DAG 是否存在环路死锁
        if (DagUtils.hasCycleUuids(nodeUuids, edgePairs)) {
            log.error("[DAG 调度器] 工作流存在环路死锁，拒绝执行, botId={}", flowBO.getBotId());
            throw new ServiceException("工作流存在环路死锁，无法执行");
        }

        // 2. 获取拓扑并行分组 (BFS 拓扑分层)
        List<List<String>> parallelGroups = DagUtils.getParallelGroupsUuids(nodeUuids, edgePairs);
        log.info("[DAG 调度器] 工作流共 {} 个节点, 划分为 {} 个拓扑执行层", flowNodes.size(), parallelGroups.size());

        Map<String, KbFlowNodeDO> nodeMap = flowNodes.stream()
                .collect(Collectors.toMap(KbFlowNodeDO::getUuid, n -> n, (existing, replacement) -> replacement));
        runtimeContext.setNodeMap(nodeMap);

        // 维护动态剪枝集合（被条件分支未命中剔除的下游节点集合）
        Set<String> prunedNodes = ConcurrentHashMap.newKeySet();
        // 维护节点执行结果映射 (初始装载断点已完成结果)
        Map<String, NodeRunResultBO> resultMap = new ConcurrentHashMap<>(initialResultMap);
        AtomicInteger stepCounter = new AtomicInteger(initialResultMap.size() + 1);

        for (int groupIndex = startGroupIndex; groupIndex < parallelGroups.size(); groupIndex++) {
            List<String> group = parallelGroups.get(groupIndex);
            log.info("[DAG 调度器] 调度第 {}/{} 层，包含 {} 个节点: {}", groupIndex + 1, parallelGroups.size(), group.size(), group);

            if (group.size() == 1) {
                // 单节点顺序执行
                String nodeUuid = group.get(0);
                NodeRunResultBO existing = resultMap.get(nodeUuid);
                NodeRunResultBO nodeResult;
                if (existing != null && Objects.equals(RuntimeStatusEnums.SUCCESS.getCode(), existing.getStatus())) {
                    nodeResult = existing;
                    log.info("[DAG 调度器] 节点 [{}] 命中断点缓存，跳过重复执行", nodeUuid);
                } else {
                    nodeResult = executeSingleDagNode(nodeUuid, nodeMap, flowEdges, edgePairs, runtimeContext, prunedNodes, stepCounter, isTestMode);
                    resultMap.put(nodeUuid, nodeResult);
                }

                if (Objects.equals(RuntimeStatusEnums.ERROR.getCode(), nodeResult.getStatus())) {
                    log.error("[DAG 调度器] 节点执行失败，终止工作流: node={}, error={}", nodeResult.getNodeName(), nodeResult.getErrorMessage());
                    if (!isTestMode) {
                        runtimeService.saveRunError(runtimeContext.getRuntimeDO());
                        saveDagCheckpoint(runtimeContext, flowBO, groupIndex, resultMap);
                    } else {
                        runtimeContext.getRuntimeDO().setOutput(nodeResult.getErrorMessage());
                    }
                    return BeanUtils.toBean(runtimeContext.getRuntimeDO(), KbRuntimeRespVO.class);
                }

                if (Objects.equals(RuntimeStatusEnums.SUSPENDED.getCode(), nodeResult.getStatus())) {
                    log.info("[DAG 调度器] 节点触发人机协同审批挂起 (SUSPENDED): node={}, 保存断点并暂停执行", nodeResult.getNodeName());
                    runtimeContext.getRuntimeDO().setStatus(RuntimeStatusEnums.SUSPENDED.getCode());
                    if (!isTestMode) {
                        runtimeService.updateById(runtimeContext.getRuntimeDO());
                        saveDagCheckpoint(runtimeContext, flowBO, groupIndex, resultMap);
                    } else {
                        runtimeContext.getRuntimeDO().setOutput("工作流已挂起，等待人工审批");
                    }
                    return BeanUtils.toBean(runtimeContext.getRuntimeDO(), KbRuntimeRespVO.class);
                }
            } else {
                // 多节点并行执行 (CompletableFuture 异步并发)
                List<CompletableFuture<NodeRunResultBO>> futures = new ArrayList<>();
                for (String nodeUuid : group) {
                    NodeRunResultBO existing = resultMap.get(nodeUuid);
                    if (existing != null && Objects.equals(RuntimeStatusEnums.SUCCESS.getCode(), existing.getStatus())) {
                        log.info("[DAG 调度器] 并行节点 [{}] 命中断点缓存，跳过重复执行", nodeUuid);
                        futures.add(CompletableFuture.completedFuture(existing));
                    } else {
                        futures.add(CompletableFuture.supplyAsync(
                                () -> executeSingleDagNode(nodeUuid, nodeMap, flowEdges, edgePairs, runtimeContext, prunedNodes, stepCounter, isTestMode),
                                dagExecutorService
                        ));
                    }
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                boolean hasError = false;
                boolean hasSuspended = false;
                NodeRunResultBO failedResult = null;
                for (CompletableFuture<NodeRunResultBO> future : futures) {
                    try {
                        NodeRunResultBO r = future.get();
                        resultMap.put(r.getNodeUuid(), r);
                        if (Objects.equals(RuntimeStatusEnums.ERROR.getCode(), r.getStatus())) {
                            hasError = true;
                            failedResult = r;
                        } else if (Objects.equals(RuntimeStatusEnums.SUSPENDED.getCode(), r.getStatus())) {
                            hasSuspended = true;
                        }
                    } catch (Exception e) {
                        log.error("[DAG 调度器] 并行执行节点异常", e);
                        hasError = true;
                    }
                }

                if (hasError) {
                    log.error("[DAG 调度器] 并行执行层中存在失败节点，终止工作流");
                    if (!isTestMode) {
                        runtimeService.saveRunError(runtimeContext.getRuntimeDO());
                        saveDagCheckpoint(runtimeContext, flowBO, groupIndex, resultMap);
                    } else if (failedResult != null) {
                        runtimeContext.getRuntimeDO().setOutput(failedResult.getErrorMessage());
                    }
                    return BeanUtils.toBean(runtimeContext.getRuntimeDO(), KbRuntimeRespVO.class);
                }

                if (hasSuspended) {
                    log.info("[DAG 调度器] 并行执行层中存在挂起节点 (SUSPENDED)，保存断点并暂停执行");
                    runtimeContext.getRuntimeDO().setStatus(RuntimeStatusEnums.SUSPENDED.getCode());
                    if (!isTestMode) {
                        runtimeService.updateById(runtimeContext.getRuntimeDO());
                        saveDagCheckpoint(runtimeContext, flowBO, groupIndex, resultMap);
                    } else {
                        runtimeContext.getRuntimeDO().setOutput("工作流已挂起，等待人工审批");
                    }
                    return BeanUtils.toBean(runtimeContext.getRuntimeDO(), KbRuntimeRespVO.class);
                }
            }

            // 本层执行成功，持久化当前层检查点 (Checkpointing)
            if (!isTestMode) {
                saveDagCheckpoint(runtimeContext, flowBO, groupIndex, resultMap);
            }
        }

        if (!isTestMode) {
            runtimeService.saveRunSuccess(runtimeContext.getRuntimeDO());
            if (dagCheckpointManager != null && runtimeContext.getRuntimeDO() != null && runtimeContext.getRuntimeDO().getId() != null) {
                dagCheckpointManager.deleteCheckpoint(String.valueOf(runtimeContext.getRuntimeDO().getId()));
            }
        }
        log.info("[DAG 调度器] ========== 工作流执行顺利完成：botId={} ==========", flowBO.getBotId());
        return BeanUtils.toBean(runtimeContext.getRuntimeDO(), KbRuntimeRespVO.class);
    }

    private void saveDagCheckpoint(RuntimeContextBO runtimeContext, KbFlowBO flowBO, int groupIndex, Map<String, NodeRunResultBO> resultMap) {
        if (dagCheckpointManager != null && runtimeContext.getRuntimeDO() != null && runtimeContext.getRuntimeDO().getId() != null) {
            try {
                String runtimeId = String.valueOf(runtimeContext.getRuntimeDO().getId());
                String flowId = String.valueOf(flowBO.getBotId());
                Map<String, Object> vars = runtimeContext.getVariables() != null ? runtimeContext.getVariables() : Collections.emptyMap();

                Map<String, tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO> hermesResults = new LinkedHashMap<>();
                for (Map.Entry<String, NodeRunResultBO> entry : resultMap.entrySet()) {
                    NodeRunResultBO r = entry.getValue();
                    tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO hr = new tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO();
                    hr.setNodeUuid(r.getNodeUuid());
                    hr.setNodeName(r.getNodeName());
                    hr.setStatus(r.getStatus());
                    hr.setOutput(r.getOutput());
                    hr.setInput(r.getInput());
                    hr.setStep(r.getStep());
                    hr.setErrorMessage(r.getErrorMessage());
                    hr.setDuration(r.getDuration());
                    hr.setNextNodeIds(r.getNextNodeIds());
                    hermesResults.put(entry.getKey(), hr);
                }

                dagCheckpointManager.saveCheckpointWithVariables(runtimeId, flowId, groupIndex, hermesResults, vars);
                log.debug("[DAG 检查点] 已持久化第 {} 层检查点：runtimeId={}", groupIndex + 1, runtimeId);
            } catch (Exception e) {
                log.warn("[DAG 检查点] 保存检查点异常: {}", e.getMessage());
            }
        }
    }

    @Override
    public KbRuntimeRespVO resumeFlow(Long runtimeId, Map<String, Object> humanInput) {
        if (runtimeId == null) {
            throw new ServiceException("runtimeId 不能为空");
        }
        if (dagCheckpointManager == null) {
            throw new ServiceException("未配置检查点管理器，无法恢复执行");
        }

        DagCheckpointManager.DagCheckpoint checkpoint = dagCheckpointManager.loadCheckpoint(String.valueOf(runtimeId));
        if (checkpoint == null) {
            throw new ServiceException("未找到可恢复的工作流检查点：runtimeId=" + runtimeId);
        }

        KbRuntimeDO runtimeDO = runtimeService.getById(runtimeId);
        if (runtimeDO == null) {
            throw new ServiceException("未找到工作流运行实例：runtimeId=" + runtimeId);
        }

        Long botId = runtimeDO.getBotId();
        KbFlowVO flowVO = queryFlow(botId);
        if (flowVO == null) {
            throw new ServiceException("未找到 Bot 工作流定义：botId=" + botId);
        }

        KbFlowBO flowBO = KbFlowConvert.toFlowBO(flowVO);

        // 若存在挂起节点，先通过 CAS 乐观锁原子唤醒挂起检查点并注入人工审批变量
        dagCheckpointManager.wakeSuspendedWithLock(String.valueOf(runtimeId), humanInput);
        checkpoint = dagCheckpointManager.loadCheckpoint(String.valueOf(runtimeId));
        if (checkpoint == null) {
            throw new ServiceException("恢复唤醒后未找到有效检查点：runtimeId=" + runtimeId);
        }

        // 恢复上下文变量并合并人工审批/额外输入
        Map<String, Object> vars = dagCheckpointManager.restoreVariables(checkpoint);
        if (vars == null) {
            vars = new HashMap<>();
        }
        if (humanInput != null && !humanInput.isEmpty()) {
            vars.putAll(humanInput);
        }
        JSONObject variablesJson = new JSONObject(vars);

        RuntimeContextBO runtimeContext = new RuntimeContextBO();
        runtimeContext.setRuntimeDO(runtimeDO);
        runtimeContext.setVariables(variablesJson);
        runtimeContext.setExecuteMode(BotExecuteModeEnum.BLOCK);
        KbBotDO botDO = botService != null ? botService.getById(botId) : null;
        BotTypeEnums botType = BotTypeEnums.WORK_FLOW;
        if (botDO != null && botDO.getType() != null) {
            BotTypeEnums resolved = BotTypeEnums.get(botDO.getType());
            if (resolved != null) {
                botType = resolved;
            }
        }
        runtimeContext.setBotType(botType);

        // 恢复已完成的节点执行结果
        Map<String, tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO> hermesResults = dagCheckpointManager.restoreCompletedResults(checkpoint);
        Map<String, NodeRunResultBO> initialResultMap = new ConcurrentHashMap<>();
        if (hermesResults != null) {
            for (Map.Entry<String, tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO> entry : hermesResults.entrySet()) {
                tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO hr = entry.getValue();
                NodeRunResultBO r = new NodeRunResultBO();
                r.setNodeUuid(hr.getNodeUuid());
                r.setNodeName(hr.getNodeName());
                r.setStatus(hr.getStatus());
                r.setOutput(hr.getOutput());
                r.setInput(hr.getInput());
                r.setStep(hr.getStep());
                r.setErrorMessage(hr.getErrorMessage());
                r.setDuration(hr.getDuration());
                r.setNextNodeIds(hr.getNextNodeIds());
                initialResultMap.put(entry.getKey(), r);
            }
        }

        int startGroupIndex = checkpoint.getGroupIndex() + 1;
        log.info("[DAG 调度器] 恢复执行工作流：runtimeId={}, botId={}, 从第 {} 层继续执行, 已完成节点数={}",
                runtimeId, botId, startGroupIndex + 1, initialResultMap.size());

        // 更新状态为运行中
        runtimeDO.setStatus(RuntimeStatusEnums.RUNNING.getCode());
        runtimeService.updateById(runtimeDO);

        return executeDagPipelineWithResume(flowBO, runtimeContext, false, startGroupIndex, initialResultMap);
    }

    /**
     * 单个 DAG 节点调度执行（含剪枝判定与后继分支动态剪枝分析）
     */
    private NodeRunResultBO executeSingleDagNode(String nodeUuid,
                                                Map<String, KbFlowNodeDO> nodeMap,
                                                List<KbFlowEdgeDO> flowEdges,
                                                List<Map.Entry<String, String>> edgePairs,
                                                RuntimeContextBO runtimeContext,
                                                Set<String> prunedNodes,
                                                AtomicInteger stepCounter,
                                                boolean isTestMode) {
        KbFlowNodeDO currentNodeDef = nodeMap.get(nodeUuid);
        String nodeName = currentNodeDef != null ? currentNodeDef.getName() : nodeUuid;

        // 1. 若当前节点已被条件分支剪除，则不提交物理执行，生成 SKIPPED 结果
        if (prunedNodes.contains(nodeUuid)) {
            log.info("[DAG 调度器] 节点处于未命中条件分支，跳过物理执行并标记为 SKIPPED: uuid={}, name={}", nodeUuid, nodeName);
            NodeRunResultBO skipped = new NodeRunResultBO();
            skipped.setNodeUuid(nodeUuid);
            skipped.setNodeName(nodeName);
            skipped.setStatus(RuntimeStatusEnums.SKIPPED.getCode());
            skipped.setStep(stepCounter.getAndIncrement());
            skipped.setOutput(Map.of("status", "SKIPPED", "reason", "pruned_by_condition"));
            if (!isTestMode) {
                runtimeService.saveRuntimeNode(skipped, runtimeContext);
            }
            return skipped;
        }

        // 2. 正常物理执行 (无锁并行化，各虚拟线程独立运行 node.execute)
        BaseNodeBO node = nodeFactory.createNode(currentNodeDef, flowEdges);
        NodeRunResultBO nodeResult = node.execute(runtimeContext);
        nodeResult.setStep(stepCounter.getAndIncrement());

        if (!isTestMode) {
            runtimeService.saveRuntimeNode(nodeResult, runtimeContext);
        }

        // 3. 若为条件网关节点（产出了选中的后继分支），递归计算未选中的后继子图并加入剪枝集合
        if (nodeResult.getNextNodeIds() != null) {
            Set<String> newlyPruned = DagUtils.computePrunedNodesUuids(nodeUuid, nodeResult.getNextNodeIds(), edgePairs);
            if (!newlyPruned.isEmpty()) {
                log.info("[DAG 调度器] 条件节点 [{}] 触发分支剪枝，剪除下游 {} 个节点: {}", nodeName, newlyPruned.size(), newlyPruned);
                prunedNodes.addAll(newlyPruned);
            }
        }

        return nodeResult;
    }

    /**
     * 验证工作流配置
     */
    private void validateWorkflow(KbFlowBO flowBO) {
        if (CollUtil.isEmpty(flowBO.getNodeList())) {
            throw new ServiceException("工作流必须包含至少一个节点");
        }

        // 检查是否有且仅有一个开始节点
        long startNodeCount = flowBO.getNodeList().stream()
                .filter(n -> Objects.equals(FlowNodeTypeEnums.START.getCode(), n.getType()))
                .count();

        if (startNodeCount != 1) {
            throw new ServiceException("工作流必须有且只有一个开始节点");
        }

        // 检查节点 ID 是否唯一
        Set<String> nodeIdSet = new HashSet<>();
        for (KbFlowNodeDO node : flowBO.getNodeList()) {
            if (!nodeIdSet.add(node.getUuid())) {
                throw new ServiceException("节点 ID 重复：" + node.getId());
            }
        }

        // 检查边的引用是否有效
        if (CollUtil.isEmpty(flowBO.getEdgeList())) {
            return;
        }
        for (KbFlowEdgeDO edge : flowBO.getEdgeList()) {
            String sourceId = edge.getSourceNodeUuid();
            String targetId = edge.getTargetNodeUuid();

            if (!nodeIdSet.contains(sourceId)) {
                throw new IllegalArgumentException("边的源节点不存在：" + sourceId);
            }
            if (!nodeIdSet.contains(targetId)) {
                throw new IllegalArgumentException("边的目标节点不存在：" + targetId);
            }
        }
    }

    /**
     * 查找开始节点
     *
     * @param nodes 节点集合
     * @return 开始节点
     */
    private KbFlowNodeDO findStartNode(List<KbFlowNodeDO> nodes) {
        return nodes.stream()
                .filter(n -> Objects.equals(FlowNodeTypeEnums.START.getCode(), n.getType()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 参数校验
     * JSONObject input 输入参数
     *
     * @return true 表示校验通过
     */
    private boolean validateParam(JSONObject input) {
        Set<String> keySet = input.keySet();
        for (String key : keySet) {
            String value = input.getString(key);

            if (value == null) {
                log.error("参数 {} 为空", key);
                return false;
            }
        }
        return true;
    }

    private String margeOutPut(List<String> list) {
        Map<String, StringBuilder> sbMap = new HashMap<>();
        for (String str : list) {
            JSONObject jsonObject = JSONObject.parseObject(str);
            String key = jsonObject.getString("name");
            String text = jsonObject.getString("text");
            if (StrUtil.hasBlank(key, text)) {
                continue;
            }
            StringBuilder sb = sbMap.get(key);
            if (Objects.isNull(sb)) {
                sb = new StringBuilder();
            }
            sb.append(text);
            sbMap.put(key, sb);
        }
        return JSONObject.toJSONString(sbMap);
    }
}
