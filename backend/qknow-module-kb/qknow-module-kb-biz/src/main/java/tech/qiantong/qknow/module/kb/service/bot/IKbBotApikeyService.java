package tech.qiantong.qknow.module.kb.service.bot;

import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.common.core.domain.model.LoginUser;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kb.controller.admin.bot.vo.KbBotApikeyPageReqVO;
import tech.qiantong.qknow.module.kb.controller.admin.bot.vo.KbBotApikeySaveReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.bot.KbBotApikeyDO;

import java.util.Collection;

/**
 * bot访问密钥Service接口
 *
 * @author qknow
 * @date 2026-04-24
 */
public interface IKbBotApikeyService extends IService<KbBotApikeyDO> {

    /**
     * 获得bot访问密钥分页列表
     *
     * @param pageReqVO 分页请求
     * @return bot访问密钥分页列表
     */
    PageResult<KbBotApikeyDO> getKbBotApikeyPage(KbBotApikeyPageReqVO pageReqVO);

    /**
     * 删除bot访问密钥
     *
     * @param idList bot访问密钥编号
     */
    int removeKbBotApikey(Collection<Long> idList);

    /**
     * 生成bot访问密钥
     *
     * @param kbBotApikey bot访问密钥信息
     * @param currentUser 当前用户
     * @return 生成的bot访问密钥
     */
    Boolean generate(KbBotApikeySaveReqVO kbBotApikey, LoginUser currentUser);
}
