package tech.qiantong.qknow.module.ai.service.modelMarket;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.ai.controller.admin.modelMarket.vo.AiModelPageReqVO;
import tech.qiantong.qknow.module.ai.controller.admin.modelMarket.vo.AiModelSaveReqVO;
import tech.qiantong.qknow.module.ai.dal.dataobject.modelMarket.AiModelDO;


/**
 * AI 模型Service接口
 *
 * @author qknow
 * @date 2025-12-23
 */
public interface IAiModelService extends IService<AiModelDO> {

    /**
     * 创建AI 模型
     *
     * @param createReqVO AI 模型信息
     * @return AI 模型编号
     */
    Long createAiModel(AiModelSaveReqVO createReqVO);

    /**
     * 删除AI 模型
     *
     * @param keyId     密钥id
     * @param modelName 模型名称
     */
    int removeAiModel(Long keyId, String modelName);

    /**
     * 更改模型的启用状态
     *
     * @param aiModel
     * @return
     */
    Boolean changeModelEnable(AiModelSaveReqVO aiModel);

    /**
     * 获取具体的聊天模型
     *
     * @param keyId     模型平台id
     * @param modelName 模型名称
     * @return chatModel 对象
     */
    ChatModel getChatModel(Long keyId, String modelName);

    /**
     * 获取具体的 embedding 模型
     *
     * @param keyId     模型平台id
     * @param modelName 模型名称
     * @return EmbeddingModel 对象
     */
    EmbeddingModel getEmbeddingModel(Long keyId, String modelName);

    /**
     * 获取模型字典
     *
     * @param type 模型类型
     */
    JSONArray getModelDict(Integer type);

    /**
     * 获取重排序模型，暂时支持通义平台的
     *
     * @param keyId     模型平台id
     * @param modelName 模型名称
     * @return 重排序模型
     */
    DashScopeRerankModel getRerankModel(Long keyId, String modelName);

    /**
     * 获取模型分页列表
     *
     * @param modelPage 模型分页参数
     * @return 模型列表
     */
    PageResult<AiModelDO> getModelPage(AiModelPageReqVO modelPage);
}
