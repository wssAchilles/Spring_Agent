package tech.qiantong.qknow.module.app.service.kac;

import java.util.List;
import java.util.Collection;
import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyBotSaveReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyBotPageReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyBotDO;
/**
 * 应用机器人关联Service接口
 *
 * @author qknow
 * @date 2026-06-22
 */
public interface IKacApplyBotService extends IService<KacApplyBotDO> {

    PageResult<KacApplyBotDO> getKacApplyBotPage(KacApplyBotPageReqVO pageReqVO);

    Long createKacApplyBot(KacApplyBotSaveReqVO createReqVO);

    int updateKacApplyBot(KacApplyBotSaveReqVO updateReqVO);

    int removeKacApplyBot(Collection<Long> idList);

    KacApplyBotDO getKacApplyBotById(Long id);

    List<KacApplyBotDO> getKacApplyBotList();
}
