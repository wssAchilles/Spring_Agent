package tech.qiantong.qknow.module.app.service.kac;

import java.util.List;
import java.util.Map;
import java.util.Collection;
import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionSaveReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionRespVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacSolutionDO;
/**
 * 解决方案管理Service接口
 *
 * @author qknow
 * @date 2026-06-22
 */
public interface IKacSolutionService extends IService<KacSolutionDO> {

    PageResult<KacSolutionDO> getKacSolutionPage(KacSolutionPageReqVO pageReqVO);

    Long createKacSolution(KacSolutionSaveReqVO createReqVO);

    int updateKacSolution(KacSolutionSaveReqVO updateReqVO);

    int removeKacSolution(Collection<Long> idList);

    KacSolutionDO getKacSolutionById(Long id);

    List<KacSolutionDO> getKacSolutionList();

    Map<Long, KacSolutionDO> getKacSolutionMap();

    String importKacSolution(List<KacSolutionRespVO> importExcelList, boolean isUpdateSupport, String operName);
}
