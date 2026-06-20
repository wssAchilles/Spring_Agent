package tech.qiantong.qknow.module.kmc.service.knowledgeSegment;

import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeSegment.vo.KmcDocumentSegmentPageReqVO;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeSegment.vo.KmcDocumentSegmentRespVO;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeSegment.vo.KmcDocumentSegmentSaveReqVO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeSegment.KmcDocumentSegmentDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;
/**
 * 文件分段Service接口
 *
 * @author qknow
 * @date 2025-08-28
 */
public interface IKmcDocumentSegmentService extends IService<KmcDocumentSegmentDO> {

    /**
     * 获得文件分段分页列表
     *
     * @param pageReqVO 分页请求
     * @return 文件分段分页列表
     */
    PageResult<KmcDocumentSegmentDO> getKmcDocumentSegmentPage(KmcDocumentSegmentPageReqVO pageReqVO);


    /**
     * 获得文件分段分页列表树形结构
     *
     * @param pageReqVO 分页请求
     * @return 文件分段分页列表
     */
    PageResult<KmcDocumentSegmentDO> getKmcDocumentSegmentTreePage(KmcDocumentSegmentPageReqVO pageReqVO);

    /**
     * 根据文件id获取所有顶层分段节点
     * @param documentId 文件id
     * @return 顶层分段节点
     */
    List<KmcDocumentSegmentDO> getAllLevelNodes(Long documentId);

    /**
     * 创建文件分段
     *
     * @param createReqVO 文件分段信息
     * @return 文件分段编号
     */
    Long createKmcDocumentSegment(KmcDocumentSegmentSaveReqVO createReqVO);

    /**
     * 更新文件分段
     *
     * @param updateReqVO 文件分段信息
     */
    int updateKmcDocumentSegment(KmcDocumentSegmentSaveReqVO updateReqVO);

    /**
     * 删除文件分段
     *
     * @param idList 文件分段编号
     */
    int removeKmcDocumentSegment(Collection<Long> idList);




    /**
     * 获得文件分段详情
     *
     * @param id 文件分段编号
     * @return 文件分段
     */
    KmcDocumentSegmentDO getKmcDocumentSegmentById(Long id);

    /**
     * 获得全部文件分段列表
     *
     * @return 文件分段列表
     */
    List<KmcDocumentSegmentDO> getKmcDocumentSegmentList();

    /**
     * 获得全部文件分段 Map
     *
     * @return 文件分段 Map
     */
    Map<Long, KmcDocumentSegmentDO> getKmcDocumentSegmentMap();


    /**
     * 导入文件分段数据
     *
     * @param importExcelList 文件分段数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName 操作用户
     * @return 结果
     */
    String importKmcDocumentSegment(List<KmcDocumentSegmentRespVO> importExcelList, boolean isUpdateSupport, String operName);

}
