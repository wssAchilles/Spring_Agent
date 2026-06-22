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
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyKnowledgePageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyKnowledgeSaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyKnowledgeDO;
import tech.qiantong.qknow.module.app.dal.mapper.kac.KacApplyKnowledgeMapper;
import tech.qiantong.qknow.module.app.service.kac.IKacApplyKnowledgeService;
/**
 * 应用知识库关联Service业务层处理
 *
 * @author qknow
 * @date 2026-06-22
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class KacApplyKnowledgeServiceImpl extends ServiceImpl<KacApplyKnowledgeMapper, KacApplyKnowledgeDO> implements IKacApplyKnowledgeService {
    @Resource
    private KacApplyKnowledgeMapper kacApplyKnowledgeMapper;

    @Override
    public PageResult<KacApplyKnowledgeDO> getKacApplyKnowledgePage(KacApplyKnowledgePageReqVO pageReqVO) {
        return kacApplyKnowledgeMapper.selectPage(pageReqVO);
    }

    @Override
    public Long createKacApplyKnowledge(KacApplyKnowledgeSaveReqVO createReqVO) {
        KacApplyKnowledgeDO dictType = BeanUtils.toBean(createReqVO, KacApplyKnowledgeDO.class);
        kacApplyKnowledgeMapper.insert(dictType);
        return dictType.getId();
    }

    @Override
    public int updateKacApplyKnowledge(KacApplyKnowledgeSaveReqVO updateReqVO) {
        KacApplyKnowledgeDO updateObj = BeanUtils.toBean(updateReqVO, KacApplyKnowledgeDO.class);
        return kacApplyKnowledgeMapper.updateById(updateObj);
    }

    @Override
    public int removeKacApplyKnowledge(Collection<Long> idList) {
        return kacApplyKnowledgeMapper.deleteBatchIds(idList);
    }

    @Override
    public KacApplyKnowledgeDO getKacApplyKnowledgeById(Long id) {
        return kacApplyKnowledgeMapper.selectById(id);
    }

    @Override
    public List<KacApplyKnowledgeDO> getKacApplyKnowledgeList() {
        return kacApplyKnowledgeMapper.selectList();
    }
}
