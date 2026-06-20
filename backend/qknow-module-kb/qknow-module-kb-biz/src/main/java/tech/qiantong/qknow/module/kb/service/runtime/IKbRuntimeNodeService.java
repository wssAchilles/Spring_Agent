package tech.qiantong.qknow.module.kb.service.runtime;

import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.module.kb.controller.admin.runtime.vo.KbRuntimeNodeRespVO;
import tech.qiantong.qknow.module.kb.controller.admin.runtime.vo.KbRuntimeNodeSaveReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.runtime.KbRuntimeNodeDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * bot节点运行Service接口
 *
 * @author qknow
 * @date 2026-03-18
 */
public interface IKbRuntimeNodeService extends IService<KbRuntimeNodeDO> {

    /**
     * 创建bot节点运行
     *
     * @param createReqVO bot节点运行信息
     * @return bot节点运行编号
     */
    Long createKbRuntimeNode(KbRuntimeNodeSaveReqVO createReqVO);

    /**
     * 更新bot节点运行
     *
     * @param updateReqVO bot节点运行信息
     */
    int updateKbRuntimeNode(KbRuntimeNodeSaveReqVO updateReqVO);

    /**
     * 删除bot节点运行
     *
     * @param idList bot节点运行编号
     */
    int removeKbRuntimeNode(Collection<Long> idList);

    /**
     * 获得bot节点运行详情
     *
     * @param id bot节点运行编号
     * @return bot节点运行
     */
    KbRuntimeNodeDO getKbRuntimeNodeById(Long id);

    /**
     * 获得全部bot节点运行列表
     *
     * @return bot节点运行列表
     */
    List<KbRuntimeNodeDO> getKbRuntimeNodeList();

    /**
     * 获得全部bot节点运行 Map
     *
     * @return bot节点运行 Map
     */
    Map<Long, KbRuntimeNodeDO> getKbRuntimeNodeMap();


    /**
     * 导入bot节点运行数据
     *
     * @param importExcelList bot节点运行数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在，则进行更新数据
     * @param operName        操作用户
     * @return 结果
     */
    String importKbRuntimeNode(List<KbRuntimeNodeRespVO> importExcelList, boolean isUpdateSupport, String operName);

}
