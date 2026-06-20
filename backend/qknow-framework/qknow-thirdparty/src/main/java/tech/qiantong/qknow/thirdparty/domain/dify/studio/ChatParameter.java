package tech.qiantong.qknow.thirdparty.domain.dify.studio;

import com.alibaba.fastjson2.JSONArray;
import lombok.Data;

/**
 * 参数类型
 * @author wang
 * @date 2025/04/23 16:42
 **/
@Data
public class ChatParameter {

    /** 参数名称 */
    private String type;

    /** 字段名称 */
    private String fieldName;

    /** 显示名称 */
    private String label;

    /** 是否必填 */
    private Boolean required;

    /** 默认值 */
    private String defaultValue;

    /** 最大长度 */
    private Integer maxLength;

    /** 文件类型 */
    private JSONArray fileTypes;

    /** 文件格式 */
    private JSONArray fileExtensions;
}
