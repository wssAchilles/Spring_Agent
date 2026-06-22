package tech.qiantong.qknow.module.app.service.kac;

import java.util.List;
import java.util.Collection;
import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionApplySaveReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionApplyPageReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacSolutionApplyDO;
/**
 * 解决方案应用关联Service接口
 *
 * @author qknow
 * @date 2026-06-22
 */
public interface IKacSolutionApplyService extends IService<KacSolutionApplyDO> {

    PageResult<KacSolutionApplyDO> getKacSolutionApplyPage(KacSolutionApplyPageReqVO pageReqVO);

    Long createKacSolutionApply(KacSolutionApplySaveReqVO createReqVO);

    int updateKacSolutionApply(KacSolutionApplySaveReqVO updateReqVO);

    int removeKacSolutionApply(Collection<Long> idList);

    KacSolutionApplyDO getKacSolutionApplyById(Long id);

    List<KacSolutionApplyDO> getKacSolutionApplyList();
}
