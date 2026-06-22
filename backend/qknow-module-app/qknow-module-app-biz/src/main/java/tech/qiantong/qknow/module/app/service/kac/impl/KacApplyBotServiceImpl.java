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
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyBotPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyBotSaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyBotDO;
import tech.qiantong.qknow.module.app.dal.mapper.kac.KacApplyBotMapper;
import tech.qiantong.qknow.module.app.service.kac.IKacApplyBotService;
/**
 * 应用机器人关联Service业务层处理
 *
 * @author qknow
 * @date 2026-06-22
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class KacApplyBotServiceImpl extends ServiceImpl<KacApplyBotMapper, KacApplyBotDO> implements IKacApplyBotService {
    @Resource
    private KacApplyBotMapper kacApplyBotMapper;

    @Override
    public PageResult<KacApplyBotDO> getKacApplyBotPage(KacApplyBotPageReqVO pageReqVO) {
        return kacApplyBotMapper.selectPage(pageReqVO);
    }

    @Override
    public Long createKacApplyBot(KacApplyBotSaveReqVO createReqVO) {
        KacApplyBotDO dictType = BeanUtils.toBean(createReqVO, KacApplyBotDO.class);
        kacApplyBotMapper.insert(dictType);
        return dictType.getId();
    }

    @Override
    public int updateKacApplyBot(KacApplyBotSaveReqVO updateReqVO) {
        KacApplyBotDO updateObj = BeanUtils.toBean(updateReqVO, KacApplyBotDO.class);
        return kacApplyBotMapper.updateById(updateObj);
    }

    @Override
    public int removeKacApplyBot(Collection<Long> idList) {
        return kacApplyBotMapper.deleteBatchIds(idList);
    }

    @Override
    public KacApplyBotDO getKacApplyBotById(Long id) {
        return kacApplyBotMapper.selectById(id);
    }

    @Override
    public List<KacApplyBotDO> getKacApplyBotList() {
        return kacApplyBotMapper.selectList();
    }
}
