package tech.qiantong.qknow.module.app.service.kac;

import java.util.List;
import java.util.Map;
import java.util.Collection;
import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacPluginSaveReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacPluginPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacPluginRespVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacPluginDO;
/**
 * 插件管理Service接口
 *
 * @author qknow
 * @date 2026-06-22
 */
public interface IKacPluginService extends IService<KacPluginDO> {

    PageResult<KacPluginDO> getKacPluginPage(KacPluginPageReqVO pageReqVO);

    Long createKacPlugin(KacPluginSaveReqVO createReqVO);

    int updateKacPlugin(KacPluginSaveReqVO updateReqVO);

    int removeKacPlugin(Collection<Long> idList);

    KacPluginDO getKacPluginById(Long id);

    List<KacPluginDO> getKacPluginList();

    Map<Long, KacPluginDO> getKacPluginMap();

    String importKacPlugin(List<KacPluginRespVO> importExcelList, boolean isUpdateSupport, String operName);
}
