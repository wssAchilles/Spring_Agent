package tech.qiantong.qknow.thirdparty.domain.dify.studio;

import lombok.Data;

/**
 * 参数类型
 * @author wang
 * @date 2025/04/23 16:42
 **/
@Data
public class ChatFile {

    /** id */
    private String id;

    /** 名称 */
    private String name;

    /** 文件大小 */
    private Integer size;

    /** 后缀 */
    private String extension;

    /** 文件类型 */
    private String mimeType;
}
