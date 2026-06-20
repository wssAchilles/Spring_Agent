package tech.qiantong.qknow.module.kb.service.agent;

import com.baomidou.mybatisplus.extension.service.IService;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kb.controller.admin.agent.vo.*;
import tech.qiantong.qknow.module.kb.dal.dataobject.agent.KbAgentConfigDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;
/**
 * agent配置Service接口
 *
 * @author qknow
 * @date 2026-03-19
 */
public interface IKbAgentConfigService extends IService<KbAgentConfigDO> {

    /**
     * 获得agent配置分页列表
     *
     * @param pageReqVO 分页请求
     * @return agent配置分页列表
     */
    PageResult<KbAgentConfigDO> getKbAgentConfigPage(KbAgentConfigPageReqVO pageReqVO);

    /**
     * 创建agent配置
     *
     * @param createReqVO agent配置信息
     * @return agent配置编号
     */
    Long createKbAgentConfig(KbAgentConfigSaveReqVO createReqVO);

    /**
     * 更新agent配置
     *
     * @param updateReqVO agent配置信息
     */
    int updateKbAgentConfig(KbAgentConfigSaveReqVO updateReqVO);

    /**
     * 删除agent配置
     *
     * @param idList agent配置编号
     */
    int removeKbAgentConfig(Collection<Long> idList);

    /**
     * 获得 agent 配置详情
     *
     * @param id agent 配置编号
     * @return agent 配置
     */
    KbAgentConfigDO getKbAgentConfigById(Long id);
    
    /**
     * 根据 botId 获得 agent 配置详情
     *
     * @param botId bot 编号
     * @return agent 配置
     */
    KbAgentConfigDO getKbAgentConfigByBotId(Long botId);

    /**
     * 获得全部agent配置列表
     *
     * @return agent配置列表
     */
    List<KbAgentConfigDO> getKbAgentConfigList();

    /**
     * 获得全部agent配置 Map
     *
     * @return agent配置 Map
     */
    Map<Long, KbAgentConfigDO> getKbAgentConfigMap();


    /**
     * 导入agent配置数据
     *
     * @param importExcelList agent配置数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    String importKbAgentConfig(List<KbAgentConfigRespVO> importExcelList, boolean isUpdateSupport, String operName);

    /**
     * 运行模型配置
     *
     * @param kbAgentConfig
     * @return
     */
    public Flux<KbChatMessageSendRespVO> chatMessage(KbAgentConfigReqVO kbAgentConfig) throws Exception;
}
