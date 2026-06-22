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
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionSaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacSolutionDO;
import tech.qiantong.qknow.module.app.dal.mapper.kac.KacSolutionMapper;
import tech.qiantong.qknow.module.app.service.kac.IKacSolutionService;
/**
 * 解决方案管理Service业务层处理
 *
 * @author qknow
 * @date 2026-06-22
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class KacSolutionServiceImpl extends ServiceImpl<KacSolutionMapper, KacSolutionDO> implements IKacSolutionService {
    @Resource
    private KacSolutionMapper kacSolutionMapper;

    @Override
    public PageResult<KacSolutionDO> getKacSolutionPage(KacSolutionPageReqVO pageReqVO) {
        return kacSolutionMapper.selectPage(pageReqVO);
    }

    @Override
    public Long createKacSolution(KacSolutionSaveReqVO createReqVO) {
        KacSolutionDO dictType = BeanUtils.toBean(createReqVO, KacSolutionDO.class);
        kacSolutionMapper.insert(dictType);
        return dictType.getId();
    }

    @Override
    public int updateKacSolution(KacSolutionSaveReqVO updateReqVO) {
        KacSolutionDO updateObj = BeanUtils.toBean(updateReqVO, KacSolutionDO.class);
        return kacSolutionMapper.updateById(updateObj);
    }

    @Override
    public int removeKacSolution(Collection<Long> idList) {
        return kacSolutionMapper.deleteBatchIds(idList);
    }

    @Override
    public KacSolutionDO getKacSolutionById(Long id) {
        return kacSolutionMapper.selectById(id);
    }

    @Override
    public List<KacSolutionDO> getKacSolutionList() {
        return kacSolutionMapper.selectList();
    }

    @Override
    public Map<Long, KacSolutionDO> getKacSolutionMap() {
        List<KacSolutionDO> kacSolutionList = kacSolutionMapper.selectList();
        return kacSolutionList.stream()
                .collect(Collectors.toMap(
                        KacSolutionDO::getId,
                        kacSolutionDO -> kacSolutionDO,
                        (existing, replacement) -> existing
                ));
    }

    @Override
    public String importKacSolution(List<KacSolutionRespVO> importExcelList, boolean isUpdateSupport, String operName) {
        if (StringUtils.isNull(importExcelList) || importExcelList.size() == 0) {
            throw new ServiceException("导入数据不能为空！");
        }

        int successNum = 0;
        int failureNum = 0;
        List<String> successMessages = new ArrayList<>();
        List<String> failureMessages = new ArrayList<>();

        for (KacSolutionRespVO respVO : importExcelList) {
            try {
                KacSolutionDO kacSolutionDO = BeanUtils.toBean(respVO, KacSolutionDO.class);
                Long kacSolutionId = respVO.getId();
                if (isUpdateSupport) {
                    if (kacSolutionId != null) {
                        KacSolutionDO existingKacSolution = kacSolutionMapper.selectById(kacSolutionId);
                        if (existingKacSolution != null) {
                            kacSolutionMapper.updateById(kacSolutionDO);
                            successNum++;
                            successMessages.add("数据更新成功，ID为 " + kacSolutionId + " 的解决方案管理记录。");
                        } else {
                            failureNum++;
                            failureMessages.add("数据更新失败，ID为 " + kacSolutionId + " 的解决方案管理记录不存在。");
                        }
                    } else {
                        failureNum++;
                        failureMessages.add("数据更新失败，某条记录的ID不存在。");
                    }
                } else {
                    QueryWrapper<KacSolutionDO> queryWrapper = new QueryWrapper<>();
                    queryWrapper.eq("id", kacSolutionId);
                    KacSolutionDO existingKacSolution = kacSolutionMapper.selectOne(queryWrapper);
                    if (existingKacSolution == null) {
                        kacSolutionMapper.insert(kacSolutionDO);
                        successNum++;
                        successMessages.add("数据插入成功，ID为 " + kacSolutionId + " 的解决方案管理记录。");
                    } else {
                        failureNum++;
                        failureMessages.add("数据插入失败，ID为 " + kacSolutionId + " 的解决方案管理记录已存在。");
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
