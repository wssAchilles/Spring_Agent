package tech.qiantong.qknow.module.kmc.controller.admin.kmcDocument.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

import java.util.List;

/**
 * 知识文件 Request VO 对象 kmc_document
 *
 * @author qknow
 * @date 2025-02-14
 */
@Schema(description = "知识文件 Request VO")
@Data
public class KmcDocumentPageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "知识分类id", example = "")
    private Long categoryId;

    @Schema(description = "知识分类名称", example = "")
    private String categoryName;

    @Schema(description = "文件名称", example = "")
    private String name;

    @Schema(description = "文件路径", example = "")
    private String path;

    @Schema(description = "文件描述", example = "")
    private String description;

    @Schema(description = "预览次数", example = "")
    private Long previewCount;

    @Schema(description = "下载次数", example = "")
    private Long downloadCount;

    @Schema(description = "同步状态", example = "")
    private Integer syncStatus;

    /** 知识库id */
    private Long knowledgeBaseId;

    /** 知识库名称 */
    private String knowledgeBaseName;

    /** 分段模式 */
    private String mode;

    /** 父分段的召回模式 */
    private String parentMode;

    /** 替换连续空格、换行符、制表符 */
    private Boolean removeExtraSpaces;

    /** 删除 URL、电子邮件地址 */
    private Boolean removeUrlsEmails;

    /** 分段重叠 */
    private String chunkOverlap;

    /** 最大长度 */
    private Integer maxTokens;

    /** 自定义分段标识符 */
    private String separator;

    /** 索引内容的形式 */
    private String docForm;

    /** 在 Q&A 模式下，指定文档的语言 */
    private String docLanguage;

    /** 子分段最大长度 */
    private Integer subchunkMaxTokens;

    /** 子分段分隔符 */
    private String subchunkSeparator;

    @TableField(exist = false)
    private List<Long> ids ;
}
