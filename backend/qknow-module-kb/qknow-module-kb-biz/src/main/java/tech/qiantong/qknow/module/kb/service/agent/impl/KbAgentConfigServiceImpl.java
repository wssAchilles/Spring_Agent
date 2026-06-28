package tech.qiantong.qknow.module.kb.service.agent.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tech.qiantong.qknow.ai.enums.model.MessageTypeEnums;
import tech.qiantong.qknow.ai.service.IChatModelService;
import tech.qiantong.qknow.common.exception.ServiceException;
import tech.qiantong.qknow.common.utils.DateUtils;
import tech.qiantong.qknow.common.utils.StringUtils;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.common.core.utils.object.BeanUtils;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kb.controller.admin.agent.vo.*;
import tech.qiantong.qknow.module.kb.dal.dataobject.agent.KbAgentConfigDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.tool.KbToolMethodDO;
import tech.qiantong.qknow.module.kb.dal.mapper.agent.KbAgentConfigMapper;
import tech.qiantong.qknow.module.kb.service.agent.IKbAgentConfigService;
import tech.qiantong.qknow.module.kb.service.tool.IKbToolMethodService;
import tech.qiantong.qknow.hermes.proto.*;
import tech.qiantong.qknow.module.kb.service.agent.HermesGrpcClient;
import tech.qiantong.qknow.module.kb.tool.function.SearchKnowledgeTool;
import tech.qiantong.qknow.module.kb.tool.function.query.knowledgeQuery;
import tech.qiantong.qknow.module.kb.utils.NodeUtils;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.KmcKnowledgeBaseRespDTO;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.SemanticCacheHitDTO;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.SemanticCacheLookupReqDTO;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.SemanticCacheSaveReqDTO;
import tech.qiantong.qknow.module.kmc.api.service.IKmcApiService;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import java.util.*;
import java.util.stream.Collectors;
/**
 * agent配置Service业务层处理
 *
 * @author qknow
 * @date 2026-03-19
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class KbAgentConfigServiceImpl  extends ServiceImpl<KbAgentConfigMapper,KbAgentConfigDO> implements IKbAgentConfigService {
    @Resource
    private KbAgentConfigMapper kbAgentConfigMapper;
    @Resource
    private IKbToolMethodService kbToolMethodService;
    @Resource
    private IAiModelApiService aiModelService;
    @Resource
    private IKmcApiService kmcApiService;

    @Resource
    private ToolCallbackResolver resolver;
    @Resource
    private HermesGrpcClient hermesGrpcClient;

    @Override
    public PageResult<KbAgentConfigDO> getKbAgentConfigPage(KbAgentConfigPageReqVO pageReqVO) {
        return kbAgentConfigMapper.selectPage(pageReqVO);
    }

    @Override
    public Long createKbAgentConfig(KbAgentConfigSaveReqVO createReqVO) {
        KbAgentConfigDO dictType = BeanUtils.toBean(createReqVO, KbAgentConfigDO.class);
        kbAgentConfigMapper.insert(dictType);
        return dictType.getId();
    }

    @Override
    public int updateKbAgentConfig(KbAgentConfigSaveReqVO updateReqVO) {
        // 相关校验

        // 更新agent配置
        KbAgentConfigDO updateObj = BeanUtils.toBean(updateReqVO, KbAgentConfigDO.class);
        return kbAgentConfigMapper.updateById(updateObj);
    }
    @Override
    public int removeKbAgentConfig(Collection<Long> idList) {
        // 批量删除agent配置
        return kbAgentConfigMapper.deleteBatchIds(idList);
    }

    @Override
    public KbAgentConfigDO getKbAgentConfigById(Long id) {
        return kbAgentConfigMapper.selectById(id);
    }

    @Override
    public KbAgentConfigDO getKbAgentConfigByBotId(Long botId) {
        LambdaQueryWrapperX<KbAgentConfigDO> queryWrapper = new LambdaQueryWrapperX<>();
        queryWrapper.eq(KbAgentConfigDO::getBotId, botId);
        return kbAgentConfigMapper.selectOne(queryWrapper);
    }

    @Override
    public List<KbAgentConfigDO> getKbAgentConfigList() {
        return kbAgentConfigMapper.selectList();
    }

    @Override
    public Map<Long, KbAgentConfigDO> getKbAgentConfigMap() {
        List<KbAgentConfigDO> kbAgentConfigList = kbAgentConfigMapper.selectList();
        return kbAgentConfigList.stream()
                .collect(Collectors.toMap(
                        KbAgentConfigDO::getId,
                        kbAgentConfigDO -> kbAgentConfigDO,
                        // 保留已存在的值
                        (existing, replacement) -> existing
                ));
    }

    /**
     * 导入agent配置数据
     *
     * @param importExcelList agent配置数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    @Override
    public String importKbAgentConfig(List<KbAgentConfigRespVO> importExcelList, boolean isUpdateSupport, String operName) {
            if (StringUtils.isNull(importExcelList) || importExcelList.size() == 0) {
                throw new ServiceException("导入数据不能为空！");
            }

            int successNum = 0;
            int failureNum = 0;
            List<String> successMessages = new ArrayList<>();
            List<String> failureMessages = new ArrayList<>();

            for (KbAgentConfigRespVO respVO : importExcelList) {
                try {
                    KbAgentConfigDO kbAgentConfigDO = BeanUtils.toBean(respVO, KbAgentConfigDO.class);
                    Long kbAgentConfigId = respVO.getId();
                    if (isUpdateSupport) {
                        if (kbAgentConfigId != null) {
                            KbAgentConfigDO existingKbAgentConfig = kbAgentConfigMapper.selectById(kbAgentConfigId);
                            if (existingKbAgentConfig != null) {
                                kbAgentConfigMapper.updateById(kbAgentConfigDO);
                                successNum++;
                                successMessages.add("数据更新成功，ID为 " + kbAgentConfigId + " 的agent配置记录。");
                            } else {
                                failureNum++;
                                failureMessages.add("数据更新失败，ID为 " + kbAgentConfigId + " 的agent配置记录不存在。");
                            }
                        } else {
                            failureNum++;
                            failureMessages.add("数据更新失败，某条记录的ID不存在。");
                        }
                    } else {
                        QueryWrapper<KbAgentConfigDO> queryWrapper = new QueryWrapper<>();
                        queryWrapper.eq("id", kbAgentConfigId);
                        KbAgentConfigDO existingKbAgentConfig = kbAgentConfigMapper.selectOne(queryWrapper);
                        if (existingKbAgentConfig == null) {
                            kbAgentConfigMapper.insert(kbAgentConfigDO);
                            successNum++;
                            successMessages.add("数据插入成功，ID为 " + kbAgentConfigId + " 的agent配置记录。");
                        } else {
                            failureNum++;
                            failureMessages.add("数据插入失败，ID为 " + kbAgentConfigId + " 的agent配置记录已存在。");
                        }
                    }
                } catch (Exception e) {
                    failureNum++;
                    String errorMsg = "数据导入失败，错误信息：" + e.getMessage();
                    failureMessages.add(errorMsg);
                    log.error(errorMsg, e);
                }
            }
            StringBuilder resultMsg = new StringBuilder();
            if (failureNum > 0) {
                resultMsg.append("很抱歉，导入失败！共 ").append(failureNum).append(" 条数据格式不正确，错误如下：");
                resultMsg.append("<br/>").append(String.join("<br/>", failureMessages));
                throw new ServiceException(resultMsg.toString());
            } else {
                resultMsg.append("恭喜您，数据已全部导入成功！共 ").append(successNum).append(" 条。");
            }
            return resultMsg.toString();
        }

    @Override
    public Flux<KbChatMessageSendRespVO> chatMessage(KbAgentConfigReqVO kbAgentConfig) throws Exception {
        // 1. 解析模型配置
        String modelConfig = kbAgentConfig.getModelConfig();
        if (StringUtils.isNull(modelConfig)) {
            throw new IllegalArgumentException("模型配置不能为空！");
        }
        JSONObject jsonObject = JSONObject.parseObject(modelConfig);
        if (StringUtils.isNull(jsonObject.getString("modelId")) || StringUtils.isNull(jsonObject.getString("modelName"))) {
            throw new IllegalArgumentException("模型不能为空！");
        }
        Long modelId = Long.parseLong(jsonObject.getString("modelId"));
        String modelName = jsonObject.getString("modelName");
        String platform = "";
        String baseUrl = "";
        String apiKey = "";

        tech.qiantong.qknow.module.ai.api.dto.AiModelRespDTO aiModel = aiModelService.getAiModel(modelId);
        if (aiModel != null) {
            platform = aiModel.getPlatform();
            if (StringUtils.isNotEmpty(aiModel.getModel())) {
                modelName = aiModel.getModel();
            }
            if (aiModel.getKeyId() != null) {
                tech.qiantong.qknow.module.ai.api.dto.AiApiKeyRespDTO apiInfo = aiModelService.getAiApiKey(aiModel.getKeyId());
                if (apiInfo != null) {
                    baseUrl = apiInfo.getUrl();
                    apiKey = apiInfo.getApiKey();
                }
            }
        }

        List<Long> knowledgeBaseIds = parseKnowledgeIds(kbAgentConfig.getKnowledgeIds());
        Optional<SemanticCacheHitDTO> cacheHit = findSemanticCache(kbAgentConfig, knowledgeBaseIds, modelName);
        if (cacheHit.isPresent()) {
            return cachedAnswerFlux(kbAgentConfig.getQuestion(), cacheHit.get());
        }

        // 2. 预检索 RAG 上下文
        List<RAGContext> ragContexts = new ArrayList<>();
        JSONArray sourceRefs = new JSONArray();
        if (!knowledgeBaseIds.isEmpty()) {
            List<KmcKnowledgeBaseRespDTO> knowledgeBaseList = kmcApiService.getKnowledgeBaseByIds(knowledgeBaseIds);
            knowledgeBaseList.forEach(kb -> {
                String recalled = "";
                try {
                    var results = kmcApiService.recallTest(kb.getId(), kbAgentConfig.getQuestion());
                    if (results != null && !results.isEmpty()) {
                        StringBuilder contentBuilder = new StringBuilder();
                        for (int i = 0; i < results.size(); i++) {
                            RetrieveResult r = results.get(i);
                            contentBuilder.append("[来源 ").append(i + 1).append("] ")
                                    .append(r.getDocumentName())
                                    .append(" / segmentId=").append(r.getId())
                                    .append("\n")
                                    .append("内容：")
                                    .append(r.getContent() != null ? r.getContent() : "")
                                    .append("\n\n");
                            JSONObject source = new JSONObject();
                            source.put("documentId", r.getDocumentId());
                            source.put("documentName", r.getDocumentName());
                            source.put("segmentId", r.getId());
                            source.put("knowledgeId", kb.getId());
                            source.put("knowledgeName", kb.getName());
                            sourceRefs.add(source);
                        }
                        recalled = contentBuilder.toString();
                    }
                } catch (Exception e) {
                    log.warn("RAG 预检索失败: knowledgeId={}", kb.getId(), e);
                }
                ragContexts.add(RAGContext.newBuilder()
                        .setKnowledgeId(String.valueOf(kb.getId()))
                        .setKnowledgeName(kb.getName())
                        .setPreRetrievedContent(recalled)
                        .build());
            });
        }

        // 3. 获取工具方法 ID 列表
        List<String> toolMethodIds = new ArrayList<>();
        if (StringUtils.isNotEmpty(kbAgentConfig.getToolMethodIds())) {
            Set<String> methodIdSet = StringUtils.str2Set(kbAgentConfig.getToolMethodIds(), ",");
            List<KbToolMethodDO> kbToolMethodList = kbToolMethodService.listByIds(methodIdSet.stream().map(Long::parseLong).toList());
            toolMethodIds = kbToolMethodList.stream()
                    .map(KbToolMethodDO::getCode)
                    .filter(StringUtils::isNotEmpty)
                    .collect(Collectors.toList());
        }

        // 4. 构建历史消息
        List<ChatMessage> history = new ArrayList<>();
        if (kbAgentConfig.getHistoryMessages() != null) {
            for (var historyMsg : kbAgentConfig.getHistoryMessages()) {
                history.add(ChatMessage.newBuilder()
                        .setRole(historyMsg.getRole())
                        .setContent(historyMsg.getContent())
                        .build());
            }
        }

        // 5. 构建系统提示词
        String systemPrompt = NodeUtils.replacePlaceholder(kbAgentConfig.getPrePrompt(), kbAgentConfig.getInput());

        // 6. 构建 gRPC ChatRequest
        ChatRequest request = ChatRequest.newBuilder()
                .setRequestId(UUID.randomUUID().toString())
                .setQuestion(kbAgentConfig.getQuestion())
                .setSystemPrompt(systemPrompt != null ? systemPrompt : "")
                .setInputParams(kbAgentConfig.getInput() != null ? kbAgentConfig.getInput() : "")
                .setModelConfig(ModelConfig.newBuilder()
                        .setModelId(modelId)
                        .setModelName(modelName != null ? modelName : "")
                        .setPlatform(platform != null ? platform : "")
                        .setBaseUrl(baseUrl != null ? baseUrl : "")
                        .setApiKey(apiKey != null ? apiKey : "")
                        .build())
                .addAllRagContexts(ragContexts)
                .addAllToolMethodIds(toolMethodIds)
                .addAllHistory(history)
                .build();

        // 7. 构造 memory_recall 事件
        KbChatMessageSendRespVO memoryRecallResp = new KbChatMessageSendRespVO();
        KbChatMessageSendRespVO.Message memoryUser = new KbChatMessageSendRespVO.Message();
        memoryUser.setType(MessageTypeEnums.USER.code);
        memoryUser.setContent(kbAgentConfig.getQuestion());
        memoryUser.setCreateTime(new Date());
        memoryRecallResp.setSend(memoryUser);
        KbChatMessageSendRespVO.Message memoryReceive = new KbChatMessageSendRespVO.Message();
        memoryReceive.setType(MessageTypeEnums.ROBOT.code);
        memoryReceive.setEventType("memory_recall");
        memoryReceive.setContent("召回 " + sourceRefs.size() + " 条记忆");
        memoryReceive.setCreateTime(new Date());
        memoryRecallResp.setReceive(memoryReceive);

        // 8. 调用 Hermes 微服务
        StringBuilder answerBuffer = new StringBuilder();
        String finalModelName = modelName;
        Flux<KbChatMessageSendRespVO> chatFlux = sourceRefs.isEmpty()
                ? hermesGrpcClient.chat(request)
                : Flux.just(memoryRecallResp).concatWith(hermesGrpcClient.chat(request));
        return chatFlux
                .doOnNext(resp -> {
                    if (resp.getReceive() != null
                            && resp.getReceive().getContent() != null
                            && StringUtils.isEmpty(resp.getReceive().getEventType())) {
                        answerBuffer.append(resp.getReceive().getContent());
                    }
                })
                .doOnComplete(() -> saveSemanticCacheAsync(kbAgentConfig, knowledgeBaseIds,
                        finalModelName, answerBuffer.toString(), sourceRefs.toJSONString()));
    }

    private Optional<SemanticCacheHitDTO> findSemanticCache(KbAgentConfigReqVO req, List<Long> knowledgeBaseIds,
                                                            String modelName) {
        if (knowledgeBaseIds.isEmpty()) {
            return Optional.empty();
        }
        try {
            SemanticCacheLookupReqDTO lookup = new SemanticCacheLookupReqDTO();
            lookup.setWorkspaceId(req.getWorkspaceId());
            lookup.setBotId(req.getBotId());
            lookup.setKnowledgeBaseIds(knowledgeBaseIds);
            lookup.setQuery(req.getQuestion());
            lookup.setModelName(modelName);
            return kmcApiService.findSemanticAnswer(lookup);
        } catch (Exception e) {
            log.warn("Semantic cache lookup failed, continuing normal chat: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void saveSemanticCacheAsync(KbAgentConfigReqVO req, List<Long> knowledgeBaseIds,
                                        String modelName, String answer, String sourcesJson) {
        if (knowledgeBaseIds.isEmpty() || answer == null || answer.isBlank()) {
            return;
        }
        Mono.fromRunnable(() -> {
                    SemanticCacheSaveReqDTO saveReq = new SemanticCacheSaveReqDTO();
                    saveReq.setWorkspaceId(req.getWorkspaceId());
                    saveReq.setBotId(req.getBotId());
                    saveReq.setKnowledgeBaseIds(knowledgeBaseIds);
                    saveReq.setQuery(req.getQuestion());
                    saveReq.setAnswer(answer);
                    saveReq.setModelName(modelName);
                    saveReq.setSourcesJson(sourcesJson);
                    kmcApiService.saveSemanticAnswer(saveReq);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, error -> log.warn("Semantic cache async save failed", error));
    }

    private Flux<KbChatMessageSendRespVO> cachedAnswerFlux(String question, SemanticCacheHitDTO hit) {
        KbChatMessageSendRespVO resp = new KbChatMessageSendRespVO();
        KbChatMessageSendRespVO.Message receive = new KbChatMessageSendRespVO.Message();
        receive.setType(MessageTypeEnums.ROBOT.code);
        receive.setContent(cleanCachedAnswer(hit.getAnswer()));
        receive.setCreateTime(DateUtils.getNowDate());
        applySourceRefs(receive, hit.getSourcesJson());
        resp.setReceive(receive);

        KbChatMessageSendRespVO.Message send = new KbChatMessageSendRespVO.Message();
        send.setType(MessageTypeEnums.USER.code);
        send.setContent(question);
        send.setCreateTime(DateUtils.getNowDate());
        resp.setSend(send);
        return Flux.just(resp);
    }

    private String cleanCachedAnswer(String answer) {
        if (answer == null) {
            return null;
        }
        return answer
                .replaceFirst("^召回 \\d+ 条记忆", "")
                .replaceFirst("^工具调用: [^\\n。]*", "");
    }

    private void applySourceRefs(KbChatMessageSendRespVO.Message receive, String sourcesJson) {
        if (sourcesJson == null || sourcesJson.isBlank()) {
            return;
        }
        try {
            JSONArray sources = JSONArray.parseArray(sourcesJson);
            List<String> documentIds = new ArrayList<>();
            List<String> documentNames = new ArrayList<>();
            for (Object sourceObj : sources) {
                if (sourceObj instanceof JSONObject source) {
                    String documentId = source.getString("documentId");
                    String documentName = source.getString("documentName");
                    if (StringUtils.isNotEmpty(documentId) && !documentIds.contains(documentId)) {
                        documentIds.add(documentId);
                    }
                    if (StringUtils.isNotEmpty(documentName) && !documentNames.contains(documentName)) {
                        documentNames.add(documentName);
                    }
                }
            }
            receive.setDocumentIdList(documentIds);
            receive.setDocumentNameList(documentNames);
        } catch (Exception e) {
            log.warn("Failed to parse cached source refs: {}", e.getMessage());
        }
    }

    private List<Long> parseKnowledgeIds(String knowledgeIds) {
        if (StringUtils.isEmpty(knowledgeIds)) {
            return List.of();
        }
        return StringUtils.str2Set(knowledgeIds, ",").stream()
                .map(Long::parseLong)
                .sorted()
                .collect(Collectors.toList());
    }
}
