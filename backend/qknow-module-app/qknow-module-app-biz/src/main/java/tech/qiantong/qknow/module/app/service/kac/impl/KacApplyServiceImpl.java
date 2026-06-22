package tech.qiantong.qknow.module.app.service.kac.impl;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.common.core.utils.object.BeanUtils;
import tech.qiantong.qknow.common.utils.StringUtils;
import tech.qiantong.qknow.common.exception.ServiceException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplySaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyDO;
import tech.qiantong.qknow.module.app.dal.mapper.kac.KacApplyMapper;
import tech.qiantong.qknow.module.app.service.kac.IKacApplyService;
/**
 * 应用管理Service业务层处理
 *
 * @author qknow
 * @date 2026-06-22
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class KacApplyServiceImpl extends ServiceImpl<KacApplyMapper, KacApplyDO> implements IKacApplyService {
    @Resource
    private KacApplyMapper kacApplyMapper;

    @Override
    public PageResult<KacApplyDO> getKacApplyPage(KacApplyPageReqVO pageReqVO) {
        return kacApplyMapper.selectPage(pageReqVO);
    }

    @Override
    public Long createKacApply(KacApplySaveReqVO createReqVO) {
        KacApplyDO dictType = BeanUtils.toBean(createReqVO, KacApplyDO.class);
        kacApplyMapper.insert(dictType);
        return dictType.getId();
    }

    @Override
    public int updateKacApply(KacApplySaveReqVO updateReqVO) {
        KacApplyDO updateObj = BeanUtils.toBean(updateReqVO, KacApplyDO.class);
        return kacApplyMapper.updateById(updateObj);
    }

    @Override
    public int removeKacApply(Collection<Long> idList) {
        return kacApplyMapper.deleteBatchIds(idList);
    }

    @Override
    public KacApplyDO getKacApplyById(Long id) {
        return kacApplyMapper.selectById(id);
    }

    @Override
    public List<KacApplyDO> getKacApplyList() {
        return kacApplyMapper.selectList();
    }

    @Override
    public Map<Long, KacApplyDO> getKacApplyMap() {
        List<KacApplyDO> kacApplyList = kacApplyMapper.selectList();
        return kacApplyList.stream()
                .collect(Collectors.toMap(
                        KacApplyDO::getId,
                        kacApplyDO -> kacApplyDO,
                        (existing, replacement) -> existing
                ));
    }

    @Override
    public String importKacApply(List<KacApplyRespVO> importExcelList, boolean isUpdateSupport, String operName) {
        if (StringUtils.isNull(importExcelList) || importExcelList.size() == 0) {
            throw new ServiceException("导入数据不能为空！");
        }

        int successNum = 0;
        int failureNum = 0;
        List<String> successMessages = new ArrayList<>();
        List<String> failureMessages = new ArrayList<>();

        for (KacApplyRespVO respVO : importExcelList) {
            try {
                KacApplyDO kacApplyDO = BeanUtils.toBean(respVO, KacApplyDO.class);
                Long kacApplyId = respVO.getId();
                if (isUpdateSupport) {
                    if (kacApplyId != null) {
                        KacApplyDO existingKacApply = kacApplyMapper.selectById(kacApplyId);
                        if (existingKacApply != null) {
                            kacApplyMapper.updateById(kacApplyDO);
                            successNum++;
                            successMessages.add("数据更新成功，ID为 " + kacApplyId + " 的应用管理记录。");
                        } else {
                            failureNum++;
                            failureMessages.add("数据更新失败，ID为 " + kacApplyId + " 的应用管理记录不存在。");
                        }
                    } else {
                        failureNum++;
                        failureMessages.add("数据更新失败，某条记录的ID不存在。");
                    }
                } else {
                    QueryWrapper<KacApplyDO> queryWrapper = new QueryWrapper<>();
                    queryWrapper.eq("id", kacApplyId);
                    KacApplyDO existingKacApply = kacApplyMapper.selectOne(queryWrapper);
                    if (existingKacApply == null) {
                        kacApplyMapper.insert(kacApplyDO);
                        successNum++;
                        successMessages.add("数据插入成功，ID为 " + kacApplyId + " 的应用管理记录。");
                    } else {
                        failureNum++;
                        failureMessages.add("数据插入失败，ID为 " + kacApplyId + " 的应用管理记录已存在。");
                    }
                }
            } catch (Exception e) {
                failureNum++;
                String errorMsg = "数据导入失败，错误信息：" + e.getMessage();
                failureMessages.add(errorMsg);
                log.error(errorMsg, e);
            }
        }
        StringBuilder resultMsg = new StringBuilder();
        if (failureNum > 0) {
            resultMsg.append("很抱歉，导入失败！共 ").append(failureNum).append(" 条数据格式不正确，错误如下：");
            resultMsg.append("<br/>").append(String.join("<br/>", failureMessages));
            throw new ServiceException(resultMsg.toString());
        } else {
            resultMsg.append("恭喜您，数据已全部导入成功！共 ").append(successNum).append(" 条。");
        }
        return resultMsg.toString();
    }
}
