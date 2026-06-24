package tech.qiantong.qknow.module.kmc.service.sync.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tech.qiantong.qknow.ai.constant.WeaviateConstant;
import tech.qiantong.qknow.ai.service.IVectorStoreService;
import tech.qiantong.qknow.ai.transformer.ContinuousWhitespaceEnricher;
import tech.qiantong.qknow.ai.transformer.GeneralSplitter;
import tech.qiantong.qknow.ai.transformer.QuestionAnswerEnricher;
import tech.qiantong.qknow.ai.transformer.RemoveUrlAndEmailEnricher;
import tech.qiantong.qknow.ai.transformer.RecursiveSplitter;
import tech.qiantong.qknow.ai.transformer.SemanticSplitter;
import tech.qiantong.qknow.ai.transformer.SplitterFactory;
import tech.qiantong.qknow.common.exception.ServiceException;
import tech.qiantong.qknow.common.utils.FileReader;
import tech.qiantong.qknow.common.utils.StringUtils;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.common.core.utils.object.BeanUtils;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kmc.controller.admin.sync.vo.KmcSyncPageReqVO;
import tech.qiantong.qknow.module.kmc.controller.admin.sync.vo.KmcSyncRespVO;
import tech.qiantong.qknow.module.kmc.controller.admin.sync.vo.KmcSyncSaveReqVO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.document.KmcDocumentDO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.document.enums.DocumentSyncStatus;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeBaseDO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeSegment.KmcDocumentSegmentDO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.sync.KmcSyncDO;
import tech.qiantong.qknow.module.kmc.dal.mapper.sync.KmcSyncMapper;
import tech.qiantong.qknow.module.kmc.service.kmcDocument.IKmcDocumentService;
import tech.qiantong.qknow.module.kmc.service.knowledgeBase.IKmcKnowledgeBaseService;
import tech.qiantong.qknow.module.kmc.service.knowledgeSegment.IKmcDocumentSegmentService;
import tech.qiantong.qknow.module.kmc.service.rag.EntityExtractionService;
import tech.qiantong.qknow.module.kmc.service.rag.RagCacheService;
import tech.qiantong.qknow.module.kmc.service.rag.SemanticCacheService;
import tech.qiantong.qknow.module.kmc.service.sync.IKmcSyncService;
import tech.qiantong.qknow.module.kmc.service.sync.ILuceneService;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;
import tech.qiantong.qknow.thirdparty.domain.dify.enums.DocFormEnum;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.CreateFile;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件同步Service业务层处理
 *
 * @author qknow
 * @date 2025-03-18
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class KmcSyncServiceImpl extends ServiceImpl<KmcSyncMapper, KmcSyncDO> implements IKmcSyncService {
    @Resource
    private IKmcDocumentService kmcDocumentService;
    @Resource
    private IKmcKnowledgeBaseService kmcKnowledgeBaseService;
    @Resource
    private IKmcDocumentSegmentService iKmcDocumentSegmentService;
    @Resource
    private IAiModelApiService aiModelService;
    @Resource
    private IVectorStoreService vectorStoreService;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private ILuceneService luceneService;
    @Resource
    private EntityExtractionService entityExtractionService;
    @Resource
    private RagCacheService ragCacheService;
    @Resource
    private SemanticCacheService semanticCacheService;

    @Value("${dromara.x-file-storage.local-plus[0].storage-path}")
    private String prefix;

    @Override
    public PageResult<KmcSyncDO> getKmcSyncPage(KmcSyncPageReqVO pageReqVO) {
        return baseMapper.selectPage(pageReqVO);
    }

    @Override
    public Long createKmcSync(KmcSyncSaveReqVO createReqVO) {
        KmcSyncDO dictType = BeanUtils.toBean(createReqVO, KmcSyncDO.class);
        baseMapper.insert(dictType);
        return dictType.getId();
    }

    @Override
    public int updateKmcSync(KmcSyncSaveReqVO updateReqVO) {
        KmcSyncDO updateObj = BeanUtils.toBean(updateReqVO, KmcSyncDO.class);
        return baseMapper.updateById(updateObj);
    }

    @Override
    public int removeKmcSync(Collection<Long> idList) {
        return baseMapper.deleteByIds(idList);
    }

    @Override
    public KmcSyncDO getKmcSyncById(Long id) {
        return baseMapper.selectById(id);
    }

    /**
     * 导入文件同步数据
     *
     * @param importExcelList 文件同步数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName        操作用户
     * @return 结果
     */
    @Override
    public String importKmcSync(List<KmcSyncRespVO> importExcelList, boolean isUpdateSupport, String operName) {
        if (StringUtils.isNull(importExcelList) || importExcelList.size() == 0) {
            throw new ServiceException("导入数据不能为空！");
        }

        int successNum = 0;
        int failureNum = 0;
        List<String> successMessages = new ArrayList<>();
        List<String> failureMessages = new ArrayList<>();

        for (KmcSyncRespVO respVO : importExcelList) {
            try {
                KmcSyncDO kmcSyncDO = BeanUtils.toBean(respVO, KmcSyncDO.class);
                Long kmcSyncId = respVO.getId();
                if (isUpdateSupport) {
                    if (kmcSyncId != null) {
                        KmcSyncDO existingKmcSync = baseMapper.selectById(kmcSyncId);
                        if (existingKmcSync != null) {
                            baseMapper.updateById(kmcSyncDO);
                            successNum++;
                            successMessages.add("数据更新成功，ID为 " + kmcSyncId + " 的文件同步记录。");
                        } else {
                            failureNum++;
                            failureMessages.add("数据更新失败，ID为 " + kmcSyncId + " 的文件同步记录不存在。");
                        }
                    } else {
                        failureNum++;
                        failureMessages.add("数据更新失败，某条记录的ID不存在。");
                    }
                } else {
                    QueryWrapper<KmcSyncDO> queryWrapper = new QueryWrapper<>();
                    queryWrapper.eq("id", kmcSyncId);
                    KmcSyncDO existingKmcSync = baseMapper.selectOne(queryWrapper);
                    if (existingKmcSync == null) {
                        baseMapper.insert(kmcSyncDO);
                        successNum++;
                        successMessages.add("数据插入成功，ID为 " + kmcSyncId + " 的文件同步记录。");
                    } else {
                        failureNum++;
                        failureMessages.add("数据插入失败，ID为 " + kmcSyncId + " 的文件同步记录已存在。");
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
     * 创建的数据同步信息
     *
     * @param kmcDocumentDO 文档对象
     * @return 操作是否成功
     */
    @Override
    public Boolean syncToCreate(KmcDocumentDO kmcDocumentDO) {
        if (kmcDocumentDO == null) {
            return false;
        }
        Path tempDir = null;
        try {
            KmcKnowledgeBaseDO knowledgeBase = kmcKnowledgeBaseService.getKmcKnowledgeBaseById(kmcDocumentDO.getKnowledgeBaseId());
            File file = getFile(kmcDocumentDO);

            // 1. 创建一个随机临时目录
            tempDir = Files.createTempDirectory("upload_");

            // 2. 在该目录下创建你想要名字的文件（名字不变！）
            Path targetPath = tempDir.resolve(kmcDocumentDO.getName());
            Files.copy(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            File copiedFile = targetPath.toFile();

            // 知识库参数请求对象
            CreateFile createFile = BeanUtils.toBean(kmcDocumentDO, CreateFile.class);
            createFile.setFile(copiedFile);
            createFile.setIndexingTechnique(knowledgeBase.getIndexingTechnique());

            kmcDocumentDO.setSyncStatus(DocumentSyncStatus.IN.code);
            kmcDocumentService.updateById(kmcDocumentDO);
        } catch (Exception e) {
            log.error("同步文件失败：{}", e.getMessage());
            kmcDocumentDO.setSyncStatus(DocumentSyncStatus.ERROR.code);
            kmcDocumentService.updateById(kmcDocumentDO);
        } finally {
            if (tempDir != null) {
                FileUtils.deleteQuietly(tempDir.toFile());
            }
        }

        return true;
    }

    @Override
    public Boolean syncToUpdate(KmcDocumentDO kmcDocumentDO) {
        Path tempDir = null;
        try {
            KmcKnowledgeBaseDO knowledgeBase = kmcKnowledgeBaseService.getKmcKnowledgeBaseById(kmcDocumentDO.getKnowledgeBaseId());

            File file = this.getFile(kmcDocumentDO);

            // 1. 创建一个随机临时目录
            tempDir = Files.createTempDirectory("upload_");
            // 2. 在该目录下创建你想要名字的文件（名字不变！）
            Path targetPath = tempDir.resolve(kmcDocumentDO.getName());
            Files.copy(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            File copiedFile = targetPath.toFile();

            // 知识库参数请求对象
            CreateFile createFile = BeanUtils.toBean(kmcDocumentDO, CreateFile.class);
            createFile.setFile(copiedFile);
            createFile.setIndexingTechnique(knowledgeBase.getIndexingTechnique());

            kmcDocumentDO.setSyncStatus(DocumentSyncStatus.IN.code);
            kmcDocumentService.updateById(kmcDocumentDO);
        } catch (Exception e) {
            log.error("同步文件失败：{}", e.getMessage());
            kmcDocumentDO.setSyncStatus(DocumentSyncStatus.ERROR.code);
            kmcDocumentService.updateById(kmcDocumentDO);
        } finally {
            if (tempDir != null) {
                FileUtils.deleteQuietly(tempDir.toFile());
            }
        }
        return true;
    }

    /**
     * 同步删除
     * @param kmcDocumentDO kmc文档对象
     * @return 操作是否成功
     */
    @Override
    public Boolean syncToRemove(KmcDocumentDO kmcDocumentDO) {
        KmcKnowledgeBaseDO knowledgeBase = kmcKnowledgeBaseService.getKmcKnowledgeBaseById(kmcDocumentDO.getKnowledgeBaseId());
        luceneService.deleteByDocumentId(String.valueOf(kmcDocumentDO.getId()));// 删除索引
        this.removeVectorStoreByDocument(knowledgeBase,kmcDocumentDO);// 删除向量数据库
        LambdaQueryWrapper<KmcDocumentSegmentDO> queryWrapper = Wrappers.<KmcDocumentSegmentDO>lambdaQuery()
                .eq(KmcDocumentSegmentDO::getDocumentId, kmcDocumentDO.getId());
        iKmcDocumentSegmentService.remove(queryWrapper);
        baseMapper.deleteById(kmcDocumentDO.getId());
        evictKnowledgeCaches(kmcDocumentDO.getKnowledgeBaseId());
        return true;
    }

    /**
     * 根据知识库id获取文档列表
     */
    @Override
    public List<KmcSyncDO> getSyncByDocumentIdList(Collection<? extends Serializable> idList, String datasetId) {
        return baseMapper.selectList(new LambdaQueryWrapperX<KmcSyncDO>()
                .eqIfPresent(KmcSyncDO::getQmDatasetId, datasetId)
                .inIfPresent(KmcSyncDO::getDocumentId, idList));
    }

    @Override
    public List<KmcSyncDO> getSyncList(List<String> idList, String datasetId) {
        return baseMapper.selectList(new LambdaQueryWrapperX<KmcSyncDO>()
                .eqIfPresent(KmcSyncDO::getQmDatasetId, datasetId)
                .inIfPresent(KmcSyncDO::getQmDocumentId, idList));
    }

    /**
     * 定时任务 - 进行文档分块和向量化
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateResult() {
        List<KmcDocumentDO> documentDOList = kmcDocumentService.list(new LambdaQueryWrapperX<KmcDocumentDO>()
                .eq(KmcDocumentDO::getSyncStatus, DocumentSyncStatus.IN.code));
        if (documentDOList.isEmpty()) {
            return;
        }
        log.info("开始同步文档分块，待处理文档数：{}", documentDOList.size());
        for (KmcDocumentDO kmcDocumentDO : documentDOList) {
            try {
                KmcKnowledgeBaseDO knowledgeBaseDO = kmcKnowledgeBaseService.getById(kmcDocumentDO.getKnowledgeBaseId());
                File file = getFile(kmcDocumentDO);
                String separator = kmcDocumentDO.getSeparator() != null ? kmcDocumentDO.getSeparator() : "\n\n";
                int maxTokens = kmcDocumentDO.getMaxTokens() != null ? kmcDocumentDO.getMaxTokens() : 512;
                int chunkOverlap = kmcDocumentDO.getChunkOverlap() != null ? Integer.parseInt(kmcDocumentDO.getChunkOverlap()) : 64;
                String mode = kmcDocumentDO.getMode() != null ? kmcDocumentDO.getMode() : SplitterFactory.MODE_RECURSIVE;
                TextSplitter splitter;
                if (SplitterFactory.MODE_SEMANTIC.equals(mode)) {
                    EmbeddingModel embeddingModel = aiModelService.getEmbeddingModel(
                            Long.valueOf(knowledgeBaseDO.getEmbeddingModelProvider()),
                            knowledgeBaseDO.getEmbeddingModel());
                    splitter = new SemanticSplitter(embeddingModel, maxTokens, 100, 0.5);
                } else {
                    splitter = SplitterFactory.create(mode, separator, maxTokens, chunkOverlap);
                }
                List<Document> documentList = this.readFile(file);

                ContinuousWhitespaceEnricher continuousWhitespaceEnricher = new ContinuousWhitespaceEnricher();
                RemoveUrlAndEmailEnricher removeUrlAndEmailEnricher = new RemoveUrlAndEmailEnricher();

                if (Boolean.TRUE.equals(kmcDocumentDO.getRemoveExtraSpaces())) {
                    documentList = continuousWhitespaceEnricher.apply(documentList);
                }
                if (Boolean.TRUE.equals(kmcDocumentDO.getRemoveUrlsEmails())) {
                    documentList = removeUrlAndEmailEnricher.apply(documentList);
                }

                List<Document> segmentList = splitter.split(documentList);
                if (SplitterFactory.MODE_RECURSIVE.equals(mode) && splitter instanceof RecursiveSplitter recursiveSplitter) {
                    int parentChunkSize = Math.max(maxTokens, 1024);
                    int childChunkSize = Math.min(maxTokens, 128);
                    segmentList = recursiveSplitter.splitParentChild(documentList, parentChunkSize, childChunkSize,
                            chunkOverlap, Math.min(chunkOverlap, 32));
                    segmentList = entityExtractionService.enrichParentChildMetadata(segmentList,
                            createDocumentChatModel(kmcDocumentDO));
                }

                if (Objects.equals(kmcDocumentDO.getDocForm(), DocFormEnum.QA_MODEL.getType())) {
                    ChatModel chatModel = aiModelService.getChatModel(Long.valueOf(kmcDocumentDO.getChatModelProvider()),
                            kmcDocumentDO.getChatModel());
                    QuestionAnswerEnricher questionAnswerEnricher = new QuestionAnswerEnricher(chatModel, kmcDocumentDO.getDocLanguage());
                    segmentList = questionAnswerEnricher.apply(segmentList);
                }

                this.saveSegment(knowledgeBaseDO, kmcDocumentDO, segmentList);
                kmcDocumentDO.setSyncStatus(DocumentSyncStatus.SUCCESS.code);
                kmcDocumentService.updateById(kmcDocumentDO);
                evictKnowledgeCaches(kmcDocumentDO.getKnowledgeBaseId());
                log.info("文档同步完成：{}，切片数：{}", kmcDocumentDO.getName(), segmentList.size());
            } catch (Exception e) {
                log.error("文档同步失败：{}，原因：{}", kmcDocumentDO.getName(), e.getMessage(), e);
                kmcDocumentDO.setSyncStatus(DocumentSyncStatus.ERROR.code);
                kmcDocumentService.updateById(kmcDocumentDO);
            }
        }
    }

    private void evictKnowledgeCaches(Long knowledgeBaseId) {
        ragCacheService.evictByKnowledgeBase(knowledgeBaseId);
        semanticCacheService.evictByKnowledgeBase(knowledgeBaseId);
    }

    private ChatModel createDocumentChatModel(KmcDocumentDO kmcDocumentDO) {
        if (kmcDocumentDO == null || org.apache.commons.lang3.StringUtils.isAnyBlank(
                kmcDocumentDO.getChatModelProvider(), kmcDocumentDO.getChatModel())) {
            return null;
        }
        try {
            return aiModelService.getChatModel(Long.valueOf(kmcDocumentDO.getChatModelProvider()),
                    kmcDocumentDO.getChatModel());
        } catch (Exception e) {
            log.warn("Failed to create chat model for entity extraction, regex fallback will be used: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 读取文档
     *
     * @param file 文档的文件对象
     * @return 文档内容
     */
    private List<Document> readFile(File file) {
        String s;
        try {
            s = FileReader.safeReadFile(file);
        } catch (TikaException | IOException e) {
            throw new RuntimeException(e);
        }
        Document document = new Document(s);
        return List.of(document);
    }

    /**
     * 从 kmcDocumentDO 中获取具体的文档文件
     *
     * @param kmcDocumentDO kmcDocumentDO 对象
     * @return 具体的文档文件
     */
    private File getFile(KmcDocumentDO kmcDocumentDO) {
        // 使用本地前缀，取出多余的/
        String base = StringUtils.substring(prefix, 0, prefix.length() - 1);
        // 获取文件
        return new File(base + kmcDocumentDO.getPath());
    }

    /**
     * 保存分段信息
     */
    private void saveSegment(KmcKnowledgeBaseDO knowledgeBaseDO, KmcDocumentDO documentDO, List<Document> segmentList) {
        Map<String, Long> map = save2sql(documentDO, segmentList);
        luceneService.saveAiDocumentBatch(map, documentDO, segmentList);
        // 如果是高质量索引，需要往向量数据库中添加
        if (Objects.equals(knowledgeBaseDO.getIndexingTechnique(), "high_quality")) {
            save2VectorStore(map, knowledgeBaseDO, documentDO, segmentList);
        }
    }

    /**
     * 保存到数据库
     *
     * @param documentDO  文档对象
     * @param segmentList 分段列表
     * @return key:向量数据库id, value:文档分段id
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Long> save2sql(KmcDocumentDO documentDO, List<Document> segmentList) {
        List<KmcDocumentSegmentDO> saveList = new ArrayList<>(segmentList.size());
        for (int i = 0; i < segmentList.size(); i++) {
            Document document = segmentList.get(i);
            Map<String, Object> metadata = document.getMetadata();

            KmcDocumentSegmentDO segmentDO = new KmcDocumentSegmentDO();
            segmentDO.setWorkspaceId(documentDO.getWorkspaceId());
            segmentDO.setDocumentName(documentDO.getName());
            segmentDO.setDocumentId(documentDO.getId());
            segmentDO.setQmSegmentId(document.getId());
            segmentDO.setParentId(stringValue(metadata.get(RecursiveSplitter.METADATA_PARENT_SEGMENT_ID)));
            segmentDO.setPosition(i + 1L);
            segmentDO.setContent(document.getText());
            segmentDO.setSignContent(document.getText());
            segmentDO.setAnswer(stringValue(metadata.get("answer")));

            saveList.add(segmentDO);
        }
        LambdaQueryWrapper<KmcDocumentSegmentDO> queryWrapper = Wrappers.<KmcDocumentSegmentDO>lambdaQuery()
                .eq(KmcDocumentSegmentDO::getDocumentId, documentDO.getId());

        iKmcDocumentSegmentService.remove(queryWrapper);// 先删除
        iKmcDocumentSegmentService.saveBatch(saveList);// 后新增
        saveEntityMetadata(saveList, segmentList);
        return saveList.stream()
                .collect(Collectors.toMap(KmcDocumentSegmentDO::getQmSegmentId, KmcDocumentSegmentDO::getId));
    }

    private void saveEntityMetadata(List<KmcDocumentSegmentDO> saveList, List<Document> segmentList) {
        try {
            List<Object[]> rows = new ArrayList<>();
            for (int i = 0; i < saveList.size(); i++) {
                KmcDocumentSegmentDO segment = saveList.get(i);
                if (segment.getId() == null) {
                    continue;
                }
                Map<String, Object> metadata = segmentList.get(i).getMetadata();
                rows.add(new Object[]{
                        segment.getDocumentId(),
                        segment.getId(),
                        segment.getQmSegmentId(),
                        JSON.toJSONString(metadata.getOrDefault("entities", List.of())),
                        JSON.toJSONString(metadata.getOrDefault("relations", List.of()))
                });
            }
            if (!rows.isEmpty()) {
                jdbcTemplate.update("DELETE FROM kmc_segment_entity_metadata WHERE document_id = ?", saveList.get(0).getDocumentId());
                jdbcTemplate.batchUpdate("""
                        INSERT INTO kmc_segment_entity_metadata(document_id, segment_id, qm_segment_id, entities, relations)
                        VALUES (?, ?, ?, ?::jsonb, ?::jsonb)
                        ON CONFLICT (segment_id) DO UPDATE SET
                            qm_segment_id = EXCLUDED.qm_segment_id,
                            entities = EXCLUDED.entities,
                            relations = EXCLUDED.relations,
                            updated_at = CURRENT_TIMESTAMP
                        """, rows);
            }
        } catch (Exception e) {
            log.warn("Failed to persist segment entity metadata, continuing sync", e);
        }
    }

    /**
     * 保存到向量数据库
     *
     * @param knowledgeBaseDO 知识库对象
     * @param documentDO      文档对象
     * @param segmentList     分段列表
     */
    /**
     * 保存到向量数据库（带自动重试）
     *
     * @param knowledgeBaseDO 知识库对象
     * @param documentDO      文档对象
     * @param segmentList     分段列表
     */
    public void save2VectorStore(Map<String, Long> map, KmcKnowledgeBaseDO knowledgeBaseDO, KmcDocumentDO documentDO,
                                 List<Document> segmentList) {
        EmbeddingModel embeddingModel = aiModelService.getEmbeddingModel(
                Long.valueOf(knowledgeBaseDO.getEmbeddingModelProvider()),
                knowledgeBaseDO.getEmbeddingModel());
        VectorStore vectorStore = vectorStoreService.getVectorStore(embeddingModel);

        List<Document> vectorDocuments = segmentList.stream()
                .filter(document -> !RecursiveSplitter.CHUNK_LEVEL_PARENT.equals(
                        stringValue(document.getMetadata().get(RecursiveSplitter.METADATA_CHUNK_LEVEL))))
                .collect(Collectors.toList());

        vectorDocuments.forEach(document -> {
            Long segmentId = map.get(document.getId());
            Map<String, Object> metadata = document.getMetadata();
            metadata.put(WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID, documentDO.getKnowledgeBaseId());
            metadata.put(WeaviateConstant.METADATA_FIELD_DOCUMENT_ID, documentDO.getId());
            metadata.put(WeaviateConstant.METADATA_FIELD_DOCUMENT_NAME, documentDO.getName());
            metadata.put(WeaviateConstant.METADATA_FIELD_SEGMENT_ID, segmentId);

            String docName = documentDO.getName();
            if (docName != null) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Day(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(docName);
                if (matcher.find()) {
                    metadata.put(WeaviateConstant.METADATA_FIELD_DAY_NO, Integer.parseInt(matcher.group(1)));
                }
            }
            metadata.put(WeaviateConstant.METADATA_FIELD_POSITION, vectorDocuments.indexOf(document));
        });

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // 先删除旧数据
                FilterExpressionBuilder b = new FilterExpressionBuilder();
                Filter.Expression expression = b.eq(WeaviateConstant.METADATA_FIELD_DOCUMENT_ID, documentDO.getId()).build();

                try {
                    vectorStore.delete(expression);
                    log.info("文档ID {} 旧向量数据已清理", documentDO.getId());
                } catch (Exception e) {
                    log.warn("文档ID {} 旧向量数据不存在，无需删除", documentDO.getId());
                }

                // 插入新数据
                List<List<Document>> partitions = Lists.partition(vectorDocuments, 5);
                for (List<Document> partition : partitions) {
                    vectorStore.add(partition);
                }
                log.info("文档ID {} 向量数据插入成功，共 {} 条", documentDO.getId(), vectorDocuments.size());
                return; // 成功则退出

            } catch (Exception e) {
                if (attempt < maxRetries) {
                    long waitTime = (long) Math.pow(2, attempt) * 1000; // 指数退避: 2s, 4s
                    log.warn("文档ID {} 向量写入失败（第{}次），{}ms 后重试：{}", documentDO.getId(), attempt, waitTime, e.getMessage());
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                } else {
                    log.error("文档ID {} 向量写入失败（已重试{}次）：{}", documentDO.getId(), maxRetries, e.getMessage(), e);
                    throw e;
                }
            }
        }
    }

    /**
     * 根据文档id 删除从向量数据库中删除
     *
     * @param knowledgeBaseDO 知识库对象
     * @param documentDO      文档对象
     */
    public void removeVectorStoreByDocument( KmcKnowledgeBaseDO knowledgeBaseDO, KmcDocumentDO documentDO) {
        EmbeddingModel embeddingModel = aiModelService.getEmbeddingModel(
                Long.valueOf(knowledgeBaseDO.getEmbeddingModelProvider()),
                knowledgeBaseDO.getEmbeddingModel());
        VectorStore vectorStore = vectorStoreService.getVectorStore(embeddingModel);

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression expression = b.eq(WeaviateConstant.METADATA_FIELD_DOCUMENT_ID, documentDO.getId()).build();
        try {
            vectorStore.delete(expression);// 删除
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
