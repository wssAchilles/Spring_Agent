package tech.qiantong.qknow.module.kmc.dal.dataobject.kmcCategory;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 知识分类 DO 对象 kmc_category
 *
 * @author qknow
 * @date 2025-02-13
 */
@Data
@TableName(value = "kmc_category")
// 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
// @KeySequence("kmc_category_seq")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KmcCategoryDO  extends BaseEntity {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /** ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 知识库id */
    private Long knowledgeBaseId;

    /** 父级id */
    private Long parentId;

    /** 分类名称 */
    private String name;

    /** 显示顺序 */
    private Long orderNum;

    /** 祖级列表 */
    private String ancestors;

    /** 是否有效 */
    private Integer validFlag;

    /** 删除标志 */
    @TableLogic
    private Integer delFlag;


    @TableField(exist = false)
    private List<KmcCategoryDO> children = new ArrayList<KmcCategoryDO>();
}
