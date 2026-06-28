package tech.qiantong.qknow.module.kmc.service.knowledgeBase.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.VectorStore;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.qiantong.qknow.ai.constant.WeaviateConstant;
import tech.qiantong.qknow.ai.enums.model.AiModelTypeEnum;
import tech.qiantong.qknow.ai.service.IVectorStoreService;
import tech.qiantong.qknow.common.core.utils.SecurityUtils;
import tech.qiantong.qknow.common.exception.ServiceException;
import tech.qiantong.qknow.common.utils.StringUtils;

import tech.qiantong.qknow.common.core.domain.entity.SysRole;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.common.core.utils.object.BeanUtils;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.KmcKnowledgeBaseRespDTO;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.*;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeBaseDO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeRecallLogDO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeRoleDO;
import tech.qiantong.qknow.module.kmc.dal.mapper.knowledgeBase.KmcKnowledgeBaseMapper;
import tech.qiantong.qknow.module.kmc.service.kmcDocument.IKmcDocumentService;
import tech.qiantong.qknow.module.kmc.service.knowledgeBase.IKmcKnowledgeBaseService;
import tech.qiantong.qknow.module.kmc.service.knowledgeBase.IKmcKnowledgeRecallLogService;
import tech.qiantong.qknow.module.kmc.service.knowledgeBase.IKmcKnowledgeRoleService;
import tech.qiantong.qknow.module.kmc.service.rag.PermissionFilter;
import tech.qiantong.qknow.module.kmc.service.rag.QueryTransformService;
import tech.qiantong.qknow.module.kmc.service.rag.RagCacheService;
import tech.qiantong.qknow.module.kmc.service.rag.RagRetrievalService;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.DashScopeRerankClient;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;
import tech.qiantong.qknow.module.system.service.ISysRoleService;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.Datasets;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 知识库Service业务层处理
 *
 * @author qknow
 * @date 2025-07-22
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class KmcKnowledgeBaseServiceImpl extends ServiceImpl<KmcKnowledgeBaseMapper, KmcKnowledgeBaseDO> implements IKmcKnowledgeBaseService {

    @Resource
    private KmcKnowledgeBaseMapper kmcKnowledgeBaseMapper;
    @Resource
    private IKmcKnowledgeRoleService kmcKnowledgeRoleService;
    @Resource
    private ISysRoleService sysRoleService;
    @Resource
    private IKmcDocumentService kmcDocumentService;
    @Resource
    private IAiModelApiService aiModelService;
    @Resource
    private IVectorStoreService vectorStoreService;
    @Resource
    private IKmcKnowledgeRecallLogService kmcKnowledgeRecallLogService;
    @Resource
    private QueryTransformService queryTransformService;
    @Resource
    private RagCacheService ragCacheService;
    @Resource
    private RagRetrievalService ragRetrievalService;
    @Resource
    private PermissionFilter permissionFilter;
    @Resource
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor = null;
    // 锁对象：防止并发创建线程池
    private final Object executorLock = new Object();

    @Override
    public PageResult<KmcKnowledgeBaseDO> getKmcKnowledgeBasePage(KmcKnowledgeBasePageReqVO pageReqVO, Long userId) {
        // 获取角色列表
        List<SysRole> sysRoles = sysRoleService.selectRoleByUserId(userId);
        List<Long> roleIds = sysRoles.stream().map(SysRole::getRoleId).collect(Collectors.toList());
        Set<Long> idList = Sets.newHashSet();
        if (!roleIds.isEmpty()) {
            // 获取知识库角色列表
            List<KmcKnowledgeRoleDO> roleDOList = kmcKnowledgeRoleService.list(
                    new LambdaQueryWrapperX<KmcKnowledgeRoleDO>()
                            .inIfPresent(KmcKnowledgeRoleDO::getRoleId, roleIds)
            );
            idList = roleDOList.stream().map(KmcKnowledgeRoleDO::getKnowledgeId).collect(Collectors.toSet());
        }

        // 是否为管理员标识符
        boolean isAdmin = sysRoles.stream()
                .anyMatch(role -> "admin".equals(role.getRoleKey()) || "system".equals(role.getRoleKey())  || "sales".equals(role.getRoleKey()));
        return kmcKnowledgeBaseMapper.selectPage(pageReqVO, idList, userId, isAdmin);
    }

    @Override
    public Long createKmcKnowledgeBase(KmcKnowledgeBaseSaveReqVO createReqVO) {
        validate(createReqVO);

        Datasets datasets = BeanUtils.toBean(createReqVO, Datasets.class);
        // 默认只对当前用户可见,其他成员权限暂不支持
        datasets.setPermission("only_me");
        KmcKnowledgeBaseDO dictType = BeanUtils.toBean(createReqVO, KmcKnowledgeBaseDO.class);
        kmcKnowledgeBaseMapper.insert(dictType);
        
        // 自动创建一个默认分类
        tech.qiantong.qknow.module.kmc.dal.dataobject.kmcCategory.KmcCategoryDO defaultCategory = new tech.qiantong.qknow.module.kmc.dal.dataobject.kmcCategory.KmcCategoryDO();
        defaultCategory.setName("默认分类");
        defaultCategory.setKnowledgeBaseId(dictType.getId());
        defaultCategory.setParentId(0L);
        defaultCategory.setAncestors("0");
        defaultCategory.setWorkspaceId(dictType.getWorkspaceId() != null ? dictType.getWorkspaceId() : 1L);
        defaultCategory.setValidFlag(1);
        defaultCategory.setDelFlag(0);
        tech.qiantong.qknow.common.utils.spring.SpringUtils.getBean(tech.qiantong.qknow.module.kmc.dal.mapper.kmcCategory.KmcCategoryMapper.class).insert(defaultCategory);

        return dictType.getId();
    }

    /**
     * 更新知识库
     *
     * @param updateReqVO 知识库信息
     * @return 更新数据条数
     */
    @Override
    public int updateKmcKnowledgeBase(KmcKnowledgeBaseSaveReqVO updateReqVO) {
        validate(updateReqVO);

        Datasets datasets = BeanUtils.toBean(updateReqVO, Datasets.class);
        datasets.setId(updateReqVO.getQmDatasetId());
        // 更新知识库
        KmcKnowledgeBaseDO updateObj = BeanUtils.toBean(updateReqVO, KmcKnowledgeBaseDO.class);
        return kmcKnowledgeBaseMapper.updateById(updateObj);
    }

    /**
     * 批量删除知识库
     *
     * @param idList 知识库编号
     * @return 删除数据条数
     */
    @Override
    public int removeKmcKnowledgeBase(Collection<Long> idList) {
        return kmcKnowledgeBaseMapper.deleteByIds(idList);
    }

    @Override
    public KmcKnowledgeBaseDO getKmcKnowledgeBaseById(Long id) {
        return kmcKnowledgeBaseMapper.selectById(id);
    }

    @Override
    public List<KmcKnowledgeBaseDO> getKmcKnowledgeBaseByIds(List<Long> ids) {
        return kmcKnowledgeBaseMapper.selectByIds(ids);
    }



    @Override
    public List<KmcKnowledgeBaseDO> getKmcKnowledgeBaseList() {
        return kmcKnowledgeBaseMapper.selectList();
    }

    @Override
    public Map<Long, KmcKnowledgeBaseDO> getKmcKnowledgeBaseMap() {
        List<KmcKnowledgeBaseDO> kmcKnowledgeBaseList = kmcKnowledgeBaseMapper.selectList();
        return kmcKnowledgeBaseList.stream()
                .collect(Collectors.toMap(
                        KmcKnowledgeBaseDO::getId,
                        kmcKnowledgeBaseDO -> kmcKnowledgeBaseDO,
                        // 保留已存在的值
                        (existing, replacement) -> existing
                ));
    }

    /**
     * 导入知识库数据
     *
     * @param importExcelList 知识库数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName        操作用户
     * @return 结果
     */
    @Override
    public String importKmcKnowledgeBase(List<KmcKnowledgeBaseRespVO> importExcelList, boolean isUpdateSupport, String operName) {
        if (StringUtils.isNull(importExcelList) || importExcelList.size() == 0) {
            throw new ServiceException("导入数据不能为空！");
        }

        int successNum = 0;
        int failureNum = 0;
        List<String> successMessages = new ArrayList<>();
        List<String> failureMessages = new ArrayList<>();

        for (KmcKnowledgeBaseRespVO respVO : importExcelList) {
            try {
                KmcKnowledgeBaseDO kmcKnowledgeBaseDO = BeanUtils.toBean(respVO, KmcKnowledgeBaseDO.class);
                Long kmcKnowledgeBaseId = respVO.getId();
                if (isUpdateSupport) {
                    if (kmcKnowledgeBaseId != null) {
                        KmcKnowledgeBaseDO existingKmcKnowledgeBase = kmcKnowledgeBaseMapper.selectById(kmcKnowledgeBaseId);
                        if (existingKmcKnowledgeBase != null) {
                            kmcKnowledgeBaseMapper.updateById(kmcKnowledgeBaseDO);
                            successNum++;
                            successMessages.add("数据更新成功，ID为 " + kmcKnowledgeBaseId + " 的知识库记录。");
                        } else {
                            failureNum++;
                            failureMessages.add("数据更新失败，ID为 " + kmcKnowledgeBaseId + " 的知识库记录不存在。");
                        }
                    } else {
                        failureNum++;
                        failureMessages.add("数据更新失败，某条记录的ID不存在。");
                    }
                } else {
                    QueryWrapper<KmcKnowledgeBaseDO> queryWrapper = new QueryWrapper<>();
                    queryWrapper.eq("id", kmcKnowledgeBaseId);
                    KmcKnowledgeBaseDO existingKmcKnowledgeBase = kmcKnowledgeBaseMapper.selectOne(queryWrapper);
                    if (existingKmcKnowledgeBase == null) {
                        kmcKnowledgeBaseMapper.insert(kmcKnowledgeBaseDO);
                        successNum++;
                        successMessages.add("数据插入成功，ID为 " + kmcKnowledgeBaseId + " 的知识库记录。");
                    } else {
                        failureNum++;
                        failureMessages.add("数据插入失败，ID为 " + kmcKnowledgeBaseId + " 的知识库记录已存在。");
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

    /**
     * 获取向量化模型字典
     *
     * @return 向量化模型字典
     */
    @Override
    public JSONArray getTextEmbedding() {
        return aiModelService.getModelDict(AiModelTypeEnum.EMBEDDING.getType());
    }

    /**
     * 获取 rerank 模型字典
     *
     * @return rerank 模型字典
     */
    @Override
    public JSONArray getRerank() {
        return aiModelService.getModelDict(AiModelTypeEnum.RERANK.getType());
    }

    /**
     * 召回测试
     */
    @Override
    public List<RetrieveResultRespVO> recallTest(RetrieveResultReqVO retrieveResultReqVO) {
        // 权限检查（无用户上下文时跳过）
        try {
            List<Long> accessibleKbIds = permissionFilter.getAccessibleKnowledgeBaseIds(SecurityUtils.getUserId());
            if (accessibleKbIds != null && !accessibleKbIds.contains(retrieveResultReqVO.getId())) {
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.debug("跳过权限检查：{}", e.getMessage());
        }

        // 从知识库获取 Embedding 配置
        KmcKnowledgeBaseDO knowledgeBaseDO = baseMapper.selectById(retrieveResultReqVO.getId());
        if (knowledgeBaseDO != null) {
            retrieveResultReqVO.setEmbeddingModel(knowledgeBaseDO.getEmbeddingModel());
            retrieveResultReqVO.setEmbeddingModelProvider(knowledgeBaseDO.getEmbeddingModelProvider());
            if (retrieveResultReqVO.getRerankingEnable() == null) {
                retrieveResultReqVO.setRerankingEnable(knowledgeBaseDO.getRerankingEnable());
            }
            if (retrieveResultReqVO.getTopK() == null) {
                retrieveResultReqVO.setTopK(BigDecimal.valueOf(knowledgeBaseDO.getTopK()));
            }
            if (retrieveResultReqVO.getScoreThresholdEnabled() == null) {
                retrieveResultReqVO.setScoreThresholdEnabled(knowledgeBaseDO.getScoreThresholdEnabled());
            }
            if (retrieveResultReqVO.getScoreThreshold() == null) {
                retrieveResultReqVO.setScoreThreshold(knowledgeBaseDO.getScoreThreshold());
            }
        }

        // 添加进检索记录
        KmcKnowledgeRecallLogDO kmcKnowledgeRecallLogDO = new KmcKnowledgeRecallLogDO();
        kmcKnowledgeRecallLogDO.setWorkspaceId(retrieveResultReqVO.getWorkspaceId());
        kmcKnowledgeRecallLogDO.setKnowledgeId(retrieveResultReqVO.getId());
        kmcKnowledgeRecallLogDO.setQuery(retrieveResultReqVO.getQuery());
        kmcKnowledgeRecallLogDO.setValidFlag(1);
        kmcKnowledgeRecallLogDO.setDelFlag(0);
        kmcKnowledgeRecallLogService.save(kmcKnowledgeRecallLogDO);

        // 查询改写（保留原始查询用于意图分析）
        String originalQuery = retrieveResultReqVO.getQuery();
        retrieveResultReqVO.setQuery(queryTransformService.rewriteQuery(originalQuery));

        // 多轮对话压缩
        if (CollUtil.isNotEmpty(retrieveResultReqVO.getHistory())) {
            List<Message> messages = retrieveResultReqVO.getHistory().stream()
                    .map(h -> {
                        if ("assistant".equals(h.getRole())) {
                            return (Message) new AssistantMessage(h.getContent());
                        }
                        return (Message) new UserMessage(h.getContent());
                    })
                    .collect(Collectors.toList());
            String compressed = queryTransformService.compressQuery(originalQuery, messages);
            retrieveResultReqVO.setQuery(compressed);
            log.info("[RAG] Multi-turn query compressed: '{}' -> '{}'", originalQuery, compressed);
        }

        int topK = retrieveResultReqVO.getTopK() != null ? retrieveResultReqVO.getTopK().intValue() : 10;

        // 确定 per-KB TTL
        Long kbTtl = knowledgeBaseDO != null ? knowledgeBaseDO.getRagCacheTtl() : null;
        long ttl = (kbTtl != null && kbTtl > 0) ? kbTtl : 300;

        // RAG v2 主路径（带缓存，仅缓存非空结果）
        try {
            String ragV2CacheKey = RagCacheService.buildCacheKey(
                    retrieveResultReqVO.getId(), retrieveResultReqVO.getQuery(), "rag_v2");
            List<RetrieveResultRespVO> cached = ragCacheService.getOrRetrieve(ragV2CacheKey, ttl, () -> {
                RagResult ragResult = ragRetrievalService.retrieve(
                        retrieveResultReqVO.getId(), originalQuery, retrieveResultReqVO.getQuery(), topK, false);
                List<RetrievalResult> sources = ragResult.getSources();
                if (CollUtil.isEmpty(sources)) {
                    return Collections.emptyList();
                }
                return sources.stream().map(this::retrievalResult2vo).collect(Collectors.toList());
            });
            if (CollUtil.isNotEmpty(cached)) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("RAG v2 retrieval failed, falling back to legacy pipeline", e);
        }

        // 兜底：原有检索逻辑
        String searchMethod = normalizeSearchMethod(retrieveResultReqVO.getSearchMethod());
        retrieveResultReqVO.setSearchMethod(searchMethod);

        String cacheKey = RagCacheService.buildCacheKey(retrieveResultReqVO.getId(), retrieveResultReqVO.getQuery(), searchMethod);

        return ragCacheService.getOrRetrieve(cacheKey, ttl, () -> {
            // 混合检索
            if (Objects.equals(searchMethod, "hybrid_search")) {
                return hybridSearch(retrieveResultReqVO);
            }

            List<Document> documentList = null;
            if (Objects.equals(searchMethod, "full_text_search")) {
                // 全文检索
                documentList = fullTextSearch(retrieveResultReqVO);
            } else if (Objects.equals(searchMethod, "semantic_search")) {
                // 向量检索
                documentList = semanticSearch(retrieveResultReqVO);
            }

            if (documentList == null) {
                log.warn("Unsupported knowledge search method: knowledgeId={}, searchMethod={}",
                        retrieveResultReqVO.getId(), retrieveResultReqVO.getSearchMethod());
                return new ArrayList<>();
            }

            // 使用 reRank 模型进行重排序
            if (retrieveResultReqVO.getRerankingEnable()) {
                documentList = reRank(retrieveResultReqVO, documentList);
            }
            return aiDocument2vo(documentList);
        });
    }

    @Override
    public RecallDebugRespVO recallDebug(RetrieveResultReqVO retrieveResultReqVO) {
        // 权限检查（无用户上下文时跳过）
        try {
            List<Long> accessibleKbIds = permissionFilter.getAccessibleKnowledgeBaseIds(SecurityUtils.getUserId());
            if (accessibleKbIds != null && !accessibleKbIds.contains(retrieveResultReqVO.getId())) {
                RecallDebugRespVO respVO = new RecallDebugRespVO();
                respVO.setResults(new ArrayList<>());
                return respVO;
            }
        } catch (Exception e) {
            log.debug("跳过权限检查：{}", e.getMessage());
        }

        // 从知识库获取 Embedding 配置
        KmcKnowledgeBaseDO knowledgeBaseDO = baseMapper.selectById(retrieveResultReqVO.getId());
        if (knowledgeBaseDO != null) {
            retrieveResultReqVO.setEmbeddingModel(knowledgeBaseDO.getEmbeddingModel());
            retrieveResultReqVO.setEmbeddingModelProvider(knowledgeBaseDO.getEmbeddingModelProvider());
            if (retrieveResultReqVO.getRerankingEnable() == null) {
                retrieveResultReqVO.setRerankingEnable(knowledgeBaseDO.getRerankingEnable());
            }
            if (retrieveResultReqVO.getTopK() == null) {
                retrieveResultReqVO.setTopK(BigDecimal.valueOf(knowledgeBaseDO.getTopK()));
            }
            if (retrieveResultReqVO.getScoreThresholdEnabled() == null) {
                retrieveResultReqVO.setScoreThresholdEnabled(knowledgeBaseDO.getScoreThresholdEnabled());
            }
            if (retrieveResultReqVO.getScoreThreshold() == null) {
                retrieveResultReqVO.setScoreThreshold(knowledgeBaseDO.getScoreThreshold());
            }
        }

        // 添加进检索记录
        KmcKnowledgeRecallLogDO kmcKnowledgeRecallLogDO = new KmcKnowledgeRecallLogDO();
        kmcKnowledgeRecallLogDO.setWorkspaceId(retrieveResultReqVO.getWorkspaceId());
        kmcKnowledgeRecallLogDO.setKnowledgeId(retrieveResultReqVO.getId());
        kmcKnowledgeRecallLogDO.setQuery(retrieveResultReqVO.getQuery());
        kmcKnowledgeRecallLogDO.setValidFlag(1);
        kmcKnowledgeRecallLogDO.setDelFlag(0);
        kmcKnowledgeRecallLogService.save(kmcKnowledgeRecallLogDO);

        // 查询改写（保留原始查询用于意图分析）
        String originalQuery = retrieveResultReqVO.getQuery();
        retrieveResultReqVO.setQuery(queryTransformService.rewriteQuery(originalQuery));

        // 多轮对话压缩
        if (CollUtil.isNotEmpty(retrieveResultReqVO.getHistory())) {
            List<Message> messages = retrieveResultReqVO.getHistory().stream()
                    .map(h -> {
                        if ("assistant".equals(h.getRole())) {
                            return (Message) new AssistantMessage(h.getContent());
                        }
                        return (Message) new UserMessage(h.getContent());
                    })
                    .collect(Collectors.toList());
            String compressed = queryTransformService.compressQuery(originalQuery, messages);
            retrieveResultReqVO.setQuery(compressed);
            log.info("[RAG] Multi-turn query compressed: '{}' -> '{}'", originalQuery, compressed);
        }

        int topK = retrieveResultReqVO.getTopK() != null ? retrieveResultReqVO.getTopK().intValue() : 10;

        // 走 RAG v2 调试路径，绕过缓存
        RagResult ragResult = ragRetrievalService.retrieve(retrieveResultReqVO.getId(), originalQuery, retrieveResultReqVO.getQuery(), topK, true);

        RecallDebugRespVO respVO = new RecallDebugRespVO();
        respVO.setResults(ragResult.getSources().stream().map(this::retrievalResult2vo).collect(Collectors.toList()));
        respVO.setDebugInfo(ragResult.getDebugInfo());
        respVO.setContextPreview(ragResult.getContext());
        return respVO;
    }

    private String normalizeSearchMethod(String searchMethod) {
        if (StrUtil.isBlank(searchMethod)) {
            return "hybrid_search";
        }
        return switch (searchMethod) {
            case "hybrid", "hybrid_search" -> "hybrid_search";
            case "semantic", "semantic_search" -> "semantic_search";
            case "keyword", "full_text", "full_text_search" -> "full_text_search";
            default -> searchMethod;
        };
    }

    @Override
    public Boolean updateKnowledgeBaseRole(KmcKnowledgeRoleSaveReqVO kmcKnowledgeRoleSaveReqVO) {
        Long knowledgeId = kmcKnowledgeRoleSaveReqVO.getKnowledgeId();
        String roleIds = kmcKnowledgeRoleSaveReqVO.getRoleIds();
        String[] split = new String[]{};
        if (StringUtils.isNotEmpty(roleIds)) {
            split = StringUtils.split(roleIds, ",");
        }
        kmcKnowledgeRoleService.remove(new LambdaQueryWrapperX<KmcKnowledgeRoleDO>()
                .eqIfPresent(KmcKnowledgeRoleDO::getKnowledgeId, knowledgeId)
                .notInIfPresent(KmcKnowledgeRoleDO::getRoleId, split)
        );
        // 查询数据库中已存在的角色关联
        List<KmcKnowledgeRoleDO> existingRoles = kmcKnowledgeRoleService.list(new LambdaQueryWrapperX<KmcKnowledgeRoleDO>()
                .eqIfPresent(KmcKnowledgeRoleDO::getKnowledgeId, knowledgeId));

        // 构造已存在角色ID的Set，方便快速查找
        Set<Long> existingRoleIds = existingRoles.stream()
                .map(KmcKnowledgeRoleDO::getRoleId)
                .collect(Collectors.toSet());

        // 新增请求中有但数据库中没有的角色关联
        List<KmcKnowledgeRoleDO> newRoles = Lists.newArrayList();
        for (String roleId : split) {
            Long roleIdLong = Long.valueOf(roleId);
            if (!existingRoleIds.contains(roleIdLong)) {
                KmcKnowledgeRoleDO kmcKnowledgeRoleDO = new KmcKnowledgeRoleDO();
                kmcKnowledgeRoleDO.setKnowledgeId(knowledgeId);
                kmcKnowledgeRoleDO.setRoleId(roleIdLong);
                kmcKnowledgeRoleDO.setWorkspaceId(kmcKnowledgeRoleSaveReqVO.getWorkspaceId());
                newRoles.add(kmcKnowledgeRoleDO);
            }
        }
        return kmcKnowledgeRoleService.saveBatch(newRoles);
    }

    @Override
    public List<KmcKnowledgeBaseDO> getKmcKnowledgeBaseList(Long userId, Boolean isValid) {
        // 获取角色列表
        List<SysRole> sysRoles = sysRoleService.selectRoleByUserId(userId);
        List<Long> roleIds = sysRoles.stream().map(SysRole::getRoleId).collect(Collectors.toList());
        Set<Long> idList = Sets.newHashSet();
        if (!roleIds.isEmpty()) {
            // 获取知识库角色列表
            List<KmcKnowledgeRoleDO> roleDOList = kmcKnowledgeRoleService.list(
                    new LambdaQueryWrapperX<KmcKnowledgeRoleDO>()
                            .inIfPresent(KmcKnowledgeRoleDO::getRoleId, roleIds)
            );
            idList = roleDOList.stream().map(KmcKnowledgeRoleDO::getKnowledgeId).collect(Collectors.toSet());
        }

        // 是否为管理员标识符
        boolean isAdmin = sysRoles.stream()
                .anyMatch(role -> "admin".equals(role.getRoleKey()) || "system".equals(role.getRoleKey())  || "sales".equals(role.getRoleKey()));
        return kmcKnowledgeBaseMapper.getKmcKnowledgeBaseList(idList, userId, isAdmin, isValid);
    }

    @Override
    public Integer changeKnowledgeValid(KmcKnowledgeBaseSaveReqVO kmcKnowledgeSaveReqVO) {
        // 更新知识库
        KmcKnowledgeBaseDO updateObj = BeanUtils.toBean(kmcKnowledgeSaveReqVO, KmcKnowledgeBaseDO.class);
        return kmcKnowledgeBaseMapper.updateById(updateObj);
    }

    @Override
    public PageResult<KmcKnowledgeBaseRespVO> getKmcKnowledgeBasePageWithFileCount(KmcKnowledgeBasePageReqVO pageReqVO, Long userId) {
        PageResult<KmcKnowledgeBaseDO> page = this.getKmcKnowledgeBasePage(pageReqVO, userId);
        List<Long> idList = page.getList().stream().map(KmcKnowledgeBaseDO::getId).collect(Collectors.toList());
        Map<Long, Integer> fileCount = kmcDocumentService.getFileCount(idList);
        PageResult<KmcKnowledgeBaseRespVO> bean = BeanUtils.toBean(page, KmcKnowledgeBaseRespVO.class);
        bean.getList().forEach(item -> {
            Integer count = fileCount.get(item.getId());
            item.setFileCount(count == null ? 0 : count);
        });
        return bean;
    }

    /**
     * 知识库内容检索
     *
     * @param knowledgeBaseId 知识库id
     * @param query           查询内容
     * @return 知识库检索结果列表
     */
    @Override
    public List<RetrieveResultRespVO> search(Long knowledgeBaseId, String query) {
        KmcKnowledgeBaseDO knowledgeBaseDO = baseMapper.selectById(knowledgeBaseId);
        RetrieveResultReqVO reqVO = new RetrieveResultReqVO();
        reqVO.setId(knowledgeBaseId);
        reqVO.setQuery(query);
        reqVO.setSearchMethod(knowledgeBaseDO.getSearchMethod());
        reqVO.setEmbeddingModel(knowledgeBaseDO.getEmbeddingModel());
        reqVO.setEmbeddingModelProvider(knowledgeBaseDO.getEmbeddingModelProvider());
        reqVO.setRerankingEnable(knowledgeBaseDO.getRerankingEnable());
        reqVO.setRerankingMode(knowledgeBaseDO.getRerankingMode());
        reqVO.setRerankingModelName(knowledgeBaseDO.getRerankingModelName());
        reqVO.setRerankingProviderName(knowledgeBaseDO.getRerankingProviderName());
        reqVO.setKeywordWeight(knowledgeBaseDO.getKeywordWeight());
        reqVO.setVectorWeight(knowledgeBaseDO.getVectorWeight());
        reqVO.setTopK(BigDecimal.valueOf(knowledgeBaseDO.getTopK()));
        reqVO.setScoreThresholdEnabled(knowledgeBaseDO.getScoreThresholdEnabled());
        reqVO.setScoreThreshold(knowledgeBaseDO.getScoreThreshold());
        reqVO.setWorkspaceId(knowledgeBaseDO.getWorkspaceId());

        List<RetrieveResultRespVO> list = new ArrayList<>();
        try {
            list = recallTest(reqVO);
        } catch (Exception e) {
            return list;
        }
        return list;
    }


    /**
     * 校验数据
     *
     * @param kmcKnowledgeBaseDO 校验的数据
     */
    private void validate(KmcKnowledgeBaseSaveReqVO kmcKnowledgeBaseDO) {
        long count = this.count(new LambdaQueryWrapperX<KmcKnowledgeBaseDO>()
                .neIfPresent(KmcKnowledgeBaseDO::getId, kmcKnowledgeBaseDO.getId())
                .eqIfPresent(KmcKnowledgeBaseDO::getName, kmcKnowledgeBaseDO.getName()));
        if (count > 0) {
            throw new ServiceException("名称重复");
        }
    }

    /**
     * 向量检索
     *
     * @param reqVO 检索条件
     * @return 检索结果，会统一封装成为 aiDocument 的形式
     */
    private List<Document> semanticSearch(RetrieveResultReqVO reqVO) {
        int topK = reqVO.getTopK().intValue();
        if (reqVO.getRerankingEnable()) {
            topK = 200;
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression kbFilter = b.eq(WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID, reqVO.getId()).build();

        // 权限过滤
        Filter.Expression permFilter = null;
        try {
            Long userId = SecurityUtils.getUserId();
            if (userId != null) {
                permFilter = permissionFilter.buildPermissionFilter(userId);
            }
        } catch (Exception e) {
            // 无用户上下文时跳过权限过滤（如 Agent 对话）
            log.debug("跳过权限过滤：{}", e.getMessage());
        }
        Filter.Expression expression;
        if (permFilter == null) {
            expression = kbFilter;
        } else {
            expression = new Filter.Expression(Filter.ExpressionType.AND, kbFilter, permFilter);
        }

        EmbeddingModel embeddingModel = aiModelService.getEmbeddingModel(
                Long.valueOf(reqVO.getEmbeddingModelProvider()), reqVO.getEmbeddingModel());
        VectorStore vectorStore = vectorStoreService.getVectorStore(embeddingModel);
        SearchRequest searchRequest;
        if (reqVO.getScoreThresholdEnabled()) {
            searchRequest = SearchRequest.builder()
                    .filterExpression(expression)
                    .topK(topK)
                    .similarityThreshold(reqVO.getScoreThreshold().doubleValue())
                    .query(reqVO.getQuery())
                    .build();
        } else {
            searchRequest = SearchRequest.builder()
                    .filterExpression(expression)
                    .topK(topK)
                    .query(reqVO.getQuery())
                    .build();
        }

        List<Document> documentList = vectorStore.similaritySearch(searchRequest);
        if (CollUtil.isEmpty(documentList)) {
            return new ArrayList<>();
        }
        return documentList;
    }

    /**
     * 全文检索 (PostgreSQL tsvector + ILIKE 中文兼容)
     *
     * @param reqVO 检索条件
     * @return 检索结果，会统一封装成为 aiDocument 的形式
     */
    private List<Document> fullTextSearch(RetrieveResultReqVO reqVO) {
        int topK = reqVO.getTopK().intValue();
        if (reqVO.getRerankingEnable()) {
            topK = 200;
        }

        // 权限检查（无用户上下文时跳过）
        try {
            List<Long> accessibleKbIds = permissionFilter.getAccessibleKnowledgeBaseIds(SecurityUtils.getUserId());
            if (accessibleKbIds != null && !accessibleKbIds.contains(reqVO.getId())) {
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.debug("跳过权限检查：{}", e.getMessage());
        }

        String queryText = reqVO.getQuery();
        List<String> searchTerms = buildFullTextSearchTerms(queryText);
        List<String> dayTerms = extractDaySearchTerms(queryText);
        Long knowledgeBaseId = reqVO.getId();

        StringBuilder sql = new StringBuilder("SELECT s.id, s.content, d.knowledge_base_id, s.document_id, s.id AS segment_id, " +
                "s.document_name, s.answer " +
                "FROM kmc_document_segment s " +
                "JOIN kmc_document d ON d.id = s.document_id AND d.del_flag = 0 " +
                "WHERE d.knowledge_base_id = ? " +
                "AND s.del_flag = 0 ");

        List<Object> params = new ArrayList<>();
        params.add(knowledgeBaseId);

        if (CollUtil.isNotEmpty(dayTerms)) {
            sql.append("AND (");
            List<String> dayConditions = new ArrayList<>();
            for (String dayTerm : dayTerms) {
                dayConditions.add("d.name ILIKE ?");
                params.add("%" + dayTerm + "%");
            }
            sql.append(String.join(" OR ", dayConditions));
            sql.append(") ");
        }

        sql.append("AND (");
        List<String> conditions = new ArrayList<>();
        conditions.add("s.content_tsv @@ plainto_tsquery('simple', ?)");
        params.add(queryText);
        for (String term : searchTerms) {
            conditions.add("d.name ILIKE ?");
            params.add("%" + term + "%");
            conditions.add("s.content ILIKE ?");
            params.add("%" + term + "%");
        }
        sql.append(String.join(" OR ", conditions));
        sql.append(") ");

        sql.append("ORDER BY d.id ASC, s.position ASC NULLS LAST, s.id ASC LIMIT ?");
        params.add(Math.max(topK * 20, topK));

        List<Document> result = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> metadata = new HashMap<>();
            String content = rs.getString("content");
            String documentName = rs.getString("document_name");
            float score = calculateFullTextScore(documentName, content, searchTerms);
            metadata.put("score", score);
            metadata.put(WeaviateConstant.METADATA_FIELD_DOCUMENT_ID, rs.getLong("document_id"));
            metadata.put(WeaviateConstant.METADATA_FIELD_DOCUMENT_NAME, documentName);
            metadata.put(WeaviateConstant.METADATA_FIELD_SEGMENT_ID, rs.getLong("segment_id"));
            metadata.put(WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID, rs.getLong("knowledge_base_id"));
            String answer = rs.getString("answer");
            if (answer != null) {
                metadata.put("answer", answer);
            }
            return Document.builder()
                    .id(String.valueOf(rs.getLong("id")))
                    .text(content)
                    .score(BigDecimal.valueOf(score).doubleValue())
                    .metadata(metadata)
                    .build();
        }, params.toArray());

        if (CollUtil.isEmpty(result)) {
            return new ArrayList<>();
        }
        return result.stream()
                .filter(document -> !reqVO.getScoreThresholdEnabled()
                        || document.getScore() == null
                        || document.getScore() > reqVO.getScoreThreshold().doubleValue())
                .sorted(Comparator.comparing(Document::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(topK)
                .collect(Collectors.toList());
    }

    private List<String> buildFullTextSearchTerms(String queryText) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (StrUtil.isBlank(queryText)) {
            return new ArrayList<>();
        }
        terms.add(queryText.trim());
        Set<String> stopWords = Set.of("请", "请告诉我", "告诉我", "在", "时候", "的时候", "我", "主要",
                "哪些", "什么", "关于", "一下", "信息", "了解");
        String normalized = queryText.replaceAll("[^\\p{IsHan}A-Za-z0-9]+", " ");
        for (String token : normalized.split("\\s+")) {
            if (StrUtil.isBlank(token)) {
                continue;
            }
            if (token.length() < 2 || stopWords.contains(token)) {
                continue;
            }
            terms.add(token);
            if (token.matches("(?i)day0?\\d+")) {
                String number = token.replaceAll("(?i)day0?", "");
                try {
                    terms.add(String.format("Day%02d", Integer.parseInt(number)));
                    terms.add("Day " + String.format("%02d", Integer.parseInt(number)));
                } catch (NumberFormatException ignored) {
                    terms.add(token);
                }
            }
        }
        if (queryText.contains("项目架构")) {
            terms.add("项目架构");
            terms.add("架构");
        }
        if (queryText.contains("熟悉")) {
            terms.add("熟悉");
        }
        return new ArrayList<>(terms);
    }

    private List<String> extractDaySearchTerms(String queryText) {
        LinkedHashSet<String> dayTerms = new LinkedHashSet<>();
        if (StrUtil.isBlank(queryText)) {
            return new ArrayList<>();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)day\\s*0?(\\d{1,2})|第\\s*0?(\\d{1,2})\\s*[天日]")
                .matcher(queryText);
        while (matcher.find()) {
            String number = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            try {
                int day = Integer.parseInt(number);
                dayTerms.add(String.format("Day%02d", day));
                dayTerms.add("Day " + String.format("%02d", day));
                dayTerms.add("Day" + day);
                dayTerms.add("Day " + day);
            } catch (NumberFormatException ignored) {
                // ignore malformed day token
            }
        }
        return new ArrayList<>(dayTerms);
    }

    private float calculateFullTextScore(String documentName, String content, List<String> searchTerms) {
        float score = 0F;
        String safeDocumentName = StrUtil.blankToDefault(documentName, "");
        String safeContent = StrUtil.blankToDefault(content, "");
        for (String term : searchTerms) {
            if (StrUtil.isBlank(term)) {
                continue;
            }
            if (safeDocumentName.contains(term)) {
                score += 3F;
            }
            if (safeContent.contains(term)) {
                score += 1F;
            }
        }
        return score;
    }

    /**
     * 混合检索
     *
     * @param reqVO 检索条件
     * @return 检索结果列表
     */
    private List<RetrieveResultRespVO> hybridSearch(RetrieveResultReqVO reqVO) {
        ExecutorService executorService = getOrRebuildExecutor(3);
        Future<List<Document>> semanticSubmit = executorService.submit(() -> semanticSearch(reqVO));
        Future<List<Document>> fullTextSubmit = executorService.submit(() -> fullTextSearch(reqVO));
        List<Document> semanticList = null;// 向量检索结果
        List<Document> fullTextList = null;// 全文检索结果
        try {
            semanticList = semanticSubmit.get();
            fullTextList = fullTextSubmit.get();
        } catch (Exception e) {
            log.error("知识库操作失败", e);
        }

        if (Objects.equals(reqVO.getRerankingMode(), "weighted_score")) {// 权重设置
            List<RetrieveResultRespVO> result = new ArrayList<>();
            // 首先进行得分的计算，得分值*权重
            if (CollUtil.isNotEmpty(semanticList)) {
                semanticList.forEach(document -> {
                    RetrieveResultRespVO respVO = aiDocument2vo(document);
                    respVO.setScore(reqVO.getVectorWeight().multiply(BigDecimal.valueOf(respVO.getScore())).doubleValue());
                    result.add(respVO);
                });
            }
            if (CollUtil.isNotEmpty(fullTextList)) {
                fullTextList.forEach((document -> {
                    RetrieveResultRespVO respVO = aiDocument2vo(document);
                    BigDecimal bigDecimal = BigDecimal.valueOf((Float) document.getMetadata().get("score"));
                    respVO.setScore(bigDecimal.doubleValue());
                    respVO.setScore(reqVO.getKeywordWeight().multiply(BigDecimal.valueOf(respVO.getScore())).doubleValue());
                    result.add(respVO);
                }));
            }
            // 去重，然后获取前 n 个数据
            return deWeightAndSort(result, reqVO);
        } else if (Objects.equals(reqVO.getRerankingMode(), "reranking_model")) {// rerank 设置
            List<Document> result = new ArrayList<>();
            if (CollUtil.isNotEmpty(semanticList)) {
                result.addAll(semanticList);
            }
            if (CollUtil.isNotEmpty(fullTextList)) {
                result.addAll(fullTextList);
            }
            List<Document> documentList = reRank(reqVO, result);
            return aiDocument2vo(documentList);
        }

        List<RetrieveResultRespVO> result = new ArrayList<>();
        if (CollUtil.isNotEmpty(semanticList)) {
            semanticList.forEach(document -> result.add(aiDocument2vo(document)));
        }
        if (CollUtil.isNotEmpty(fullTextList)) {
            fullTextList.forEach(document -> result.add(aiDocument2vo(document)));
        }
        return deWeightAndSort(result, reqVO);
    }

    /**
     * 将 document 转换为 请求结果对象
     *
     * @param document ai 文档
     * @return 请求结果对象
     */
    private RetrieveResultRespVO aiDocument2vo(Document document) {
        RetrieveResultRespVO result = new RetrieveResultRespVO();
        Map<String, Object> metadata = document.getMetadata();
        result.setId(String.valueOf(metadata.get(WeaviateConstant.METADATA_FIELD_SEGMENT_ID)));
        result.setDocumentId(String.valueOf(metadata.get(WeaviateConstant.METADATA_FIELD_DOCUMENT_ID)));
        result.setContent(document.getText());
        result.setAnswer(String.valueOf(metadata.get("answer")));
        result.setWordCount(document.getText().length());
        result.setDocumentName(String.valueOf(metadata.get(WeaviateConstant.METADATA_FIELD_DOCUMENT_NAME)));
        Double score = document.getScore();
        result.setScore(score);
        return result;
    }

    /**
     * 将 document 转换为 请求结果列表
     *
     * @param documentList 文档列表
     * @return 请求结果的列表
     */
    private List<RetrieveResultRespVO> aiDocument2vo(List<Document> documentList) {
        if (CollUtil.isEmpty(documentList)) {
            return new ArrayList<>(0);
        }

        List<RetrieveResultRespVO> result = new ArrayList<>(documentList.size());
        for (Document document : documentList) {
            result.add(aiDocument2vo(document));
        }
        return result;
    }

    /**
     * 将 RetrievalResult 转换为 RetrieveResultRespVO
     */
    private RetrieveResultRespVO retrievalResult2vo(RetrievalResult r) {
        RetrieveResultRespVO vo = new RetrieveResultRespVO();
        vo.setId(r.getSegmentId() != null ? String.valueOf(r.getSegmentId()) : null);
        vo.setDocumentId(r.getDocumentId() != null ? String.valueOf(r.getDocumentId()) : null);
        vo.setContent(r.getContent());
        vo.setAnswer(r.getAnswer());
        vo.setDocumentName(r.getDocumentName());
        vo.setScore(r.getScore());
        vo.setWordCount(r.getContent() != null ? r.getContent().length() : 0);
        vo.setSource(r.getSource());
        return vo;
    }

    /**
     * 使用 rerank 模型重排序
     *
     * @param reqVO        封装的查询条件
     * @param documentList 排序前的文档列表
     * @return 排序后的文档列表
     */
    private List<Document> reRank(RetrieveResultReqVO reqVO, List<Document> documentList) {
        if (CollUtil.isEmpty(documentList)) {
            return new ArrayList<>(0);
        }
        String apiKey = aiModelService.getApiKey(reqVO.getRerankingProviderName());
        if (StrUtil.isBlank(apiKey)) {
            log.warn("Rerank API key not found for platform: {}", reqVO.getRerankingProviderName());
            return documentList;
        }

        DashScopeRerankClient client = new DashScopeRerankClient(apiKey, reqVO.getRerankingModelName());
        List<DashScopeRerankClient.RerankResult> reranked = client.rerank(
                reqVO.getQuery(), documentList, reqVO.getTopK().intValue());

        List<Document> result = new ArrayList<>();
        for (DashScopeRerankClient.RerankResult rr : reranked) {
            if (reqVO.getScoreThresholdEnabled() && rr.score() <= reqVO.getScoreThreshold().doubleValue()) {
                continue;
            }
            Document output = rr.document();
            result.add(Document.builder()
                    .id(output.getId())
                    .text(output.getText())
                    .score(rr.score())
                    .metadata(output.getMetadata())
                    .build());
        }
        return result.stream()
                .sorted(Comparator.comparingDouble(Document::getScore).reversed())
                .limit(reqVO.getTopK().intValue())
                .toList();
    }

    /**
     * 去重、排序，并获取前 n 个值
     *
     * @param list  列表对象
     * @param reqVO 查询条件
     * @return 排序后的前 n 个值
     */
    private List<RetrieveResultRespVO> deWeightAndSort(List<RetrieveResultRespVO> list, RetrieveResultReqVO reqVO) {
        Map<String, RetrieveResultRespVO> map = new HashMap<>();
        for (RetrieveResultRespVO vo : list) {
            RetrieveResultRespVO mapValue = map.get(vo.getId());
            if (!Objects.isNull(mapValue)) {// 包含
                vo.setScore(mapValue.getScore() + vo.getScore());
            }
            map.put(vo.getId(), vo);
        }
        if (reqVO.getScoreThresholdEnabled()) {
            return map.values().stream()
                    .filter(respVO -> reqVO.getScoreThreshold().compareTo(BigDecimal.valueOf(respVO.getScore())) <= 0)
                    .sorted(Comparator.comparingDouble(RetrieveResultRespVO::getScore).reversed())
                    .limit(reqVO.getTopK().intValue())
                    .toList();
        }
        return map.values().stream()
                .sorted(Comparator.comparingDouble(RetrieveResultRespVO::getScore).reversed())
                .limit(reqVO.getTopK().intValue())
                .toList();

    }

    /**
     * 获取或重建线程池（核心逻辑：存在且存活则复用，否则重建）
     *
     * @param threadNum 配置的线程数
     * @return 可用的线程池
     */
    private ExecutorService getOrRebuildExecutor(int threadNum) {
        // 双重检查锁：避免并发创建
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            synchronized (executorLock) {
                if (executor == null || executor.isShutdown() || executor.isTerminated()) {
                    executor = new ThreadPoolExecutor(
                            threadNum,  // 核心线程数=配置数
                            threadNum,  // 固定线程池，最大=核心
                            60L, TimeUnit.SECONDS,  // 空闲线程存活时间（固定线程池可设长一点）
                            new LinkedBlockingQueue<>(1000),  // 有界队列，避免OOM
                            new ThreadFactory() {  // 自定义线程名，便于排查
                                private final AtomicInteger count = new AtomicInteger(1);

                                @Override
                                public Thread newThread(@NotNull Runnable r) {
                                    return new Thread(r, "ext-task-thread-" + count.getAndIncrement());
                                }
                            },
                            new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略：调用方执行，避免任务丢失
                    );
                }
            }
        }
        return executor;
    }
}
