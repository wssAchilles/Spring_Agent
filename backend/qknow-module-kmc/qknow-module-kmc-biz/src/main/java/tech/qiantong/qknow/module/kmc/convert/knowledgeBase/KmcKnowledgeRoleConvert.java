package tech.qiantong.qknow.module.kmc.convert.knowledgeBase;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.KmcKnowledgeRolePageReqVO;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.KmcKnowledgeRoleRespVO;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.KmcKnowledgeRoleSaveReqVO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeRoleDO;

import java.util.List;

/**
 * 知识库角色关联 Convert
 *
 * @author qknow
 * @date 2025-07-24
 */
@Mapper
public interface KmcKnowledgeRoleConvert {
    KmcKnowledgeRoleConvert INSTANCE = Mappers.getMapper(KmcKnowledgeRoleConvert.class);

    /**
     * PageReqVO 转换为 DO
     * @param kmcKnowledgeRolePageReqVO 请求参数
     * @return KmcKnowledgeRoleDO
     */
     KmcKnowledgeRoleDO convertToDO(KmcKnowledgeRolePageReqVO kmcKnowledgeRolePageReqVO);

    /**
     * SaveReqVO 转换为 DO
     * @param kmcKnowledgeRoleSaveReqVO 保存请求参数
     * @return KmcKnowledgeRoleDO
     */
     KmcKnowledgeRoleDO convertToDO(KmcKnowledgeRoleSaveReqVO kmcKnowledgeRoleSaveReqVO);

    /**
     * DO 转换为 RespVO
     * @param kmcKnowledgeRoleDO 实体对象
     * @return KmcKnowledgeRoleRespVO
     */
     KmcKnowledgeRoleRespVO convertToRespVO(KmcKnowledgeRoleDO kmcKnowledgeRoleDO);

    /**
     * DOList 转换为 RespVOList
     * @param kmcKnowledgeRoleDOList 实体对象列表
     * @return List<KmcKnowledgeRoleRespVO>
     */
     List<KmcKnowledgeRoleRespVO> convertToRespVOList(List<KmcKnowledgeRoleDO> kmcKnowledgeRoleDOList);
}
