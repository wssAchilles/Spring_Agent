package tech.qiantong.qknow.module.kmc.api.kmcDocument.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import tech.qiantong.qknow.module.kmc.api.kmcCategory.dto.KmcCategoryReqDTO;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Treeselect树结构实体类
 *
 * @author qknow
 */
public class TreeSelectsDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 节点ID */
    private Long id;

    /** 节点名称 */
    private String label;

    /** 节点数量 */
    private Integer count;

    /** 子节点 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<TreeSelectsDTO> children;

    public TreeSelectsDTO()
    {

    }
    public TreeSelectsDTO(KmcCategoryReqDTO KmcCategoryDO)
    {
        this.id = KmcCategoryDO.getId();
        this.label = KmcCategoryDO.getName();
        this.children = KmcCategoryDO.getChildren().stream().map(TreeSelectsDTO::new).collect(Collectors.toList());
    }



    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public Integer getCount()
    {
        return count;
    }

    public void setCount(Integer count)
    {
        this.count = count;
    }

    public List<TreeSelectsDTO> getChildren()
    {
        return children;
    }

    public void setChildren(List<TreeSelectsDTO> children)
    {
        this.children = children;
    }
}
