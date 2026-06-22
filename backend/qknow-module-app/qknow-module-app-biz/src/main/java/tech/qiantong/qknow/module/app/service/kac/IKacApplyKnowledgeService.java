package tech.qiantong.qknow.module.app.service.kac;

import java.util.List;
import java.util.Collection;
import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyKnowledgeSaveReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyKnowledgePageReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyKnowledgeDO;
/**
 * 应用知识库关联Service接口
 *
 * @author qknow
 * @date 2026-06-22
 */
public interface IKacApplyKnowledgeService extends IService<KacApplyKnowledgeDO> {

    PageResult<KacApplyKnowledgeDO> getKacApplyKnowledgePage(KacApplyKnowledgePageReqVO pageReqVO);

    Long createKacApplyKnowledge(KacApplyKnowledgeSaveReqVO createReqVO);

    int updateKacApplyKnowledge(KacApplyKnowledgeSaveReqVO updateReqVO);

    int removeKacApplyKnowledge(Collection<Long> idList);

    KacApplyKnowledgeDO getKacApplyKnowledgeById(Long id);

    List<KacApplyKnowledgeDO> getKacApplyKnowledgeList();
}
