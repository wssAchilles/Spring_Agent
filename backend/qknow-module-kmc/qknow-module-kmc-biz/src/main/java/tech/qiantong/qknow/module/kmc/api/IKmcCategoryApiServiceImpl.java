package tech.qiantong.qknow.module.kmc.api;

import org.springframework.stereotype.Service;
import tech.qiantong.qknow.common.core.utils.object.BeanUtils;
import tech.qiantong.qknow.module.kmc.api.kmcCategory.dto.KmcCategoryRespDTO;
import tech.qiantong.qknow.module.kmc.api.service.IKmcCategoryApiService;
import tech.qiantong.qknow.module.kmc.dal.dataobject.kmcCategory.KmcCategoryDO;
import tech.qiantong.qknow.module.kmc.service.kmcCategory.IKmcCategoryService;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * @author zyg
 * @version 1.0.0
 * <p>
 * class description goes here
 * descritpion:
 * @date 2025/12/11 11:02
 */
@Service
public class IKmcCategoryApiServiceImpl implements IKmcCategoryApiService {
    @Resource
    private IKmcCategoryService categoryService;

    /**
     * 获取知识库文件分类列表
     */

    public List<KmcCategoryRespDTO> kmcCategoryList(){
        List<KmcCategoryDO> kmcCategoryList = categoryService.getKmcCategoryList();
        List<KmcCategoryRespDTO> dtos = BeanUtils.toBean(kmcCategoryList, KmcCategoryRespDTO.class);
        return dtos;
    }
}
