package tech.qiantong.qknow.module.kmc.service.knowledgeBase;

import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.KmcKnowledgeRolePageReqVO;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.KmcKnowledgeRoleRespVO;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.KmcKnowledgeRoleSaveReqVO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeRoleDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;
/**
 * 知识库角色关联Service接口
 *
 * @author qknow
 * @date 2025-07-24
 */
public interface IKmcKnowledgeRoleService extends IService<KmcKnowledgeRoleDO> {

    /**
     * 获得知识库角色关联分页列表
     *
     * @param pageReqVO 分页请求
     * @return 知识库角色关联分页列表
     */
    PageResult<KmcKnowledgeRoleDO> getKmcKnowledgeRolePage(KmcKnowledgeRolePageReqVO pageReqVO);

    /**
     * 创建知识库角色关联
     *
     * @param createReqVO 知识库角色关联信息
     * @return 知识库角色关联编号
     */
    Long createKmcKnowledgeRole(KmcKnowledgeRoleSaveReqVO createReqVO);

    /**
     * 更新知识库角色关联
     *
     * @param updateReqVO 知识库角色关联信息
     */
    int updateKmcKnowledgeRole(KmcKnowledgeRoleSaveReqVO updateReqVO);

    /**
     * 删除知识库角色关联
     *
     * @param idList 知识库角色关联编号
     */
    int removeKmcKnowledgeRole(Collection<Long> idList);

    /**
     * 获得知识库角色关联详情
     *
     * @param id 知识库角色关联编号
     * @return 知识库角色关联
     */
    KmcKnowledgeRoleDO getKmcKnowledgeRoleById(Long id);

    /**
     * 获得全部知识库角色关联列表
     *
     * @return 知识库角色关联列表
     */
    List<KmcKnowledgeRoleDO> getKmcKnowledgeRoleList();

    /**
     * 获得全部知识库角色关联 Map
     *
     * @return 知识库角色关联 Map
     */
    Map<Long, KmcKnowledgeRoleDO> getKmcKnowledgeRoleMap();


    /**
     * 导入知识库角色关联数据
     *
     * @param importExcelList 知识库角色关联数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    String importKmcKnowledgeRole(List<KmcKnowledgeRoleRespVO> importExcelList, boolean isUpdateSupport, String operName);

    List<KmcKnowledgeRoleDO> findByKnowledgeBaseId(Long knowledgeBaseId);
}
