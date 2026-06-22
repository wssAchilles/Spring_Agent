package tech.qiantong.qknow.module.app.service.kac.impl;

import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.common.core.utils.object.BeanUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyGraphPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyGraphSaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyGraphDO;
import tech.qiantong.qknow.module.app.dal.mapper.kac.KacApplyGraphMapper;
import tech.qiantong.qknow.module.app.service.kac.IKacApplyGraphService;
/**
 * 应用图谱关联Service业务层处理
 *
 * @author qknow
 * @date 2026-06-22
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class KacApplyGraphServiceImpl extends ServiceImpl<KacApplyGraphMapper, KacApplyGraphDO> implements IKacApplyGraphService {
    @Resource
    private KacApplyGraphMapper kacApplyGraphMapper;

    @Override
    public PageResult<KacApplyGraphDO> getKacApplyGraphPage(KacApplyGraphPageReqVO pageReqVO) {
        return kacApplyGraphMapper.selectPage(pageReqVO);
    }

    @Override
    public Long createKacApplyGraph(KacApplyGraphSaveReqVO createReqVO) {
        KacApplyGraphDO dictType = BeanUtils.toBean(createReqVO, KacApplyGraphDO.class);
        kacApplyGraphMapper.insert(dictType);
        return dictType.getId();
    }

    @Override
    public int updateKacApplyGraph(KacApplyGraphSaveReqVO updateReqVO) {
        KacApplyGraphDO updateObj = BeanUtils.toBean(updateReqVO, KacApplyGraphDO.class);
        return kacApplyGraphMapper.updateById(updateObj);
    }

    @Override
    public int removeKacApplyGraph(Collection<Long> idList) {
        return kacApplyGraphMapper.deleteBatchIds(idList);
    }

    @Override
    public KacApplyGraphDO getKacApplyGraphById(Long id) {
        return kacApplyGraphMapper.selectById(id);
    }

    @Override
    public List<KacApplyGraphDO> getKacApplyGraphList() {
        return kacApplyGraphMapper.selectList();
    }
}
