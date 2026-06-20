package tech.qiantong.qknow.module.kb.dal.mapper.tool;

import tech.qiantong.qknow.module.kb.dal.dataobject.tool.KbToolDO;
import java.util.Arrays;
import com.github.yulichang.base.MPJBaseMapper;
import tech.qiantong.qknow.common.core.page.PageResult;
import java.util.HashSet;
import java.util.Set;
import tech.qiantong.qknow.module.kb.controller.admin.tool.vo.KbToolPageReqVO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

/**
 * 工具管理Mapper接口
 *
 * @author qknow
 * @date 2026-03-19
 */
public interface KbToolMapper extends BaseMapperX<KbToolDO> {

    default PageResult<KbToolDO> selectPage(KbToolPageReqVO reqVO) {
        // 定义排序的字段（防止 SQL 注入，与数据库字段名称一致）
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        // 构造动态查询条件
        return selectPage(reqVO, new LambdaQueryWrapperX<KbToolDO>()
                .eqIfPresent(KbToolDO::getWorkspaceId, reqVO.getWorkspaceId())
                .likeIfPresent(KbToolDO::getName, reqVO.getName())
                .eqIfPresent(KbToolDO::getDescription, reqVO.getDescription())
                .eqIfPresent(KbToolDO::getTags, reqVO.getTags())
                .eqIfPresent(KbToolDO::getType, reqVO.getType())
                .eqIfPresent(KbToolDO::getSource, reqVO.getSource())
                .eqIfPresent(KbToolDO::getCreateTime, reqVO.getCreateTime())
                // 如果 reqVO.getName() 不为空，则添加 name 的精确匹配条件（name = '<name>'）
                // .likeIfPresent(KbToolDO::getName, reqVO.getName())
                // 按照 createTime 字段降序排序
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }
}
