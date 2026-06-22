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
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionApplyPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionApplySaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacSolutionApplyDO;
import tech.qiantong.qknow.module.app.dal.mapper.kac.KacSolutionApplyMapper;
import tech.qiantong.qknow.module.app.service.kac.IKacSolutionApplyService;
/**
 * 解决方案应用关联Service业务层处理
 *
 * @author qknow
 * @date 2026-06-22
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class KacSolutionApplyServiceImpl extends ServiceImpl<KacSolutionApplyMapper, KacSolutionApplyDO> implements IKacSolutionApplyService {
    @Resource
    private KacSolutionApplyMapper kacSolutionApplyMapper;

    @Override
    public PageResult<KacSolutionApplyDO> getKacSolutionApplyPage(KacSolutionApplyPageReqVO pageReqVO) {
        return kacSolutionApplyMapper.selectPage(pageReqVO);
    }

    @Override
    public Long createKacSolutionApply(KacSolutionApplySaveReqVO createReqVO) {
        KacSolutionApplyDO dictType = BeanUtils.toBean(createReqVO, KacSolutionApplyDO.class);
        kacSolutionApplyMapper.insert(dictType);
        return dictType.getId();
    }

    @Override
    public int updateKacSolutionApply(KacSolutionApplySaveReqVO updateReqVO) {
        KacSolutionApplyDO updateObj = BeanUtils.toBean(updateReqVO, KacSolutionApplyDO.class);
        return kacSolutionApplyMapper.updateById(updateObj);
    }

    @Override
    public int removeKacSolutionApply(Collection<Long> idList) {
        return kacSolutionApplyMapper.deleteBatchIds(idList);
    }

    @Override
    public KacSolutionApplyDO getKacSolutionApplyById(Long id) {
        return kacSolutionApplyMapper.selectById(id);
    }

    @Override
    public List<KacSolutionApplyDO> getKacSolutionApplyList() {
        return kacSolutionApplyMapper.selectList();
    }
}
