package tech.qiantong.qknow.module.app.service.kac;

import java.util.List;
import java.util.Collection;
import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyGraphSaveReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyGraphPageReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyGraphDO;
/**
 * 应用图谱关联Service接口
 *
 * @author qknow
 * @date 2026-06-22
 */
public interface IKacApplyGraphService extends IService<KacApplyGraphDO> {

    PageResult<KacApplyGraphDO> getKacApplyGraphPage(KacApplyGraphPageReqVO pageReqVO);

    Long createKacApplyGraph(KacApplyGraphSaveReqVO createReqVO);

    int updateKacApplyGraph(KacApplyGraphSaveReqVO updateReqVO);

    int removeKacApplyGraph(Collection<Long> idList);

    KacApplyGraphDO getKacApplyGraphById(Long id);

    List<KacApplyGraphDO> getKacApplyGraphList();
}
