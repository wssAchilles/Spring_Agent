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
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacPluginPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacPluginRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacPluginSaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacPluginDO;
import tech.qiantong.qknow.module.app.dal.mapper.kac.KacPluginMapper;
import tech.qiantong.qknow.module.app.service.kac.IKacPluginService;
/**
 * 插件管理Service业务层处理
 *
 * @author qknow
 * @date 2026-06-22
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class KacPluginServiceImpl extends ServiceImpl<KacPluginMapper, KacPluginDO> implements IKacPluginService {
    @Resource
    private KacPluginMapper kacPluginMapper;

    @Override
    public PageResult<KacPluginDO> getKacPluginPage(KacPluginPageReqVO pageReqVO) {
        return kacPluginMapper.selectPage(pageReqVO);
    }

    @Override
    public Long createKacPlugin(KacPluginSaveReqVO createReqVO) {
        KacPluginDO dictType = BeanUtils.toBean(createReqVO, KacPluginDO.class);
        kacPluginMapper.insert(dictType);
        return dictType.getId();
    }

    @Override
    public int updateKacPlugin(KacPluginSaveReqVO updateReqVO) {
        KacPluginDO updateObj = BeanUtils.toBean(updateReqVO, KacPluginDO.class);
        return kacPluginMapper.updateById(updateObj);
    }

    @Override
    public int removeKacPlugin(Collection<Long> idList) {
        return kacPluginMapper.deleteBatchIds(idList);
    }

    @Override
    public KacPluginDO getKacPluginById(Long id) {
        return kacPluginMapper.selectById(id);
    }

    @Override
    public List<KacPluginDO> getKacPluginList() {
        return kacPluginMapper.selectList();
    }

    @Override
    public Map<Long, KacPluginDO> getKacPluginMap() {
        List<KacPluginDO> kacPluginList = kacPluginMapper.selectList();
        return kacPluginList.stream()
                .collect(Collectors.toMap(
                        KacPluginDO::getId,
                        kacPluginDO -> kacPluginDO,
                        (existing, replacement) -> existing
                ));
    }

    @Override
    public String importKacPlugin(List<KacPluginRespVO> importExcelList, boolean isUpdateSupport, String operName) {
        if (StringUtils.isNull(importExcelList) || importExcelList.size() == 0) {
            throw new ServiceException("导入数据不能为空！");
        }

        int successNum = 0;
        int failureNum = 0;
        List<String> successMessages = new ArrayList<>();
        List<String> failureMessages = new ArrayList<>();

        for (KacPluginRespVO respVO : importExcelList) {
            try {
                KacPluginDO kacPluginDO = BeanUtils.toBean(respVO, KacPluginDO.class);
                Long kacPluginId = respVO.getId();
                if (isUpdateSupport) {
                    if (kacPluginId != null) {
                        KacPluginDO existingKacPlugin = kacPluginMapper.selectById(kacPluginId);
                        if (existingKacPlugin != null) {
                            kacPluginMapper.updateById(kacPluginDO);
                            successNum++;
                            successMessages.add("数据更新成功，ID为 " + kacPluginId + " 的插件管理记录。");
                        } else {
                            failureNum++;
                            failureMessages.add("数据更新失败，ID为 " + kacPluginId + " 的插件管理记录不存在。");
                        }
                    } else {
                        failureNum++;
                        failureMessages.add("数据更新失败，某条记录的ID不存在。");
                    }
                } else {
                    QueryWrapper<KacPluginDO> queryWrapper = new QueryWrapper<>();
                    queryWrapper.eq("id", kacPluginId);
                    KacPluginDO existingKacPlugin = kacPluginMapper.selectOne(queryWrapper);
                    if (existingKacPlugin == null) {
                        kacPluginMapper.insert(kacPluginDO);
                        successNum++;
                        successMessages.add("数据插入成功，ID为 " + kacPluginId + " 的插件管理记录。");
                    } else {
                        failureNum++;
                        failureMessages.add("数据插入失败，ID为 " + kacPluginId + " 的插件管理记录已存在。");
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
