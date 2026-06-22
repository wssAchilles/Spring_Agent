package tech.qiantong.qknow.module.app.service.kac;

import java.util.List;
import java.util.Map;
import java.util.Collection;
import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplySaveReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyRespVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyDO;
/**
 * 应用管理Service接口
 *
 * @author qknow
 * @date 2026-06-22
 */
public interface IKacApplyService extends IService<KacApplyDO> {

    PageResult<KacApplyDO> getKacApplyPage(KacApplyPageReqVO pageReqVO);

    Long createKacApply(KacApplySaveReqVO createReqVO);

    int updateKacApply(KacApplySaveReqVO updateReqVO);

    int removeKacApply(Collection<Long> idList);

    KacApplyDO getKacApplyById(Long id);

    List<KacApplyDO> getKacApplyList();

    Map<Long, KacApplyDO> getKacApplyMap();

    String importKacApply(List<KacApplyRespVO> importExcelList, boolean isUpdateSupport, String operName);
}
