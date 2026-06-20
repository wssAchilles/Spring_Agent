
package tech.qiantong.qknow.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 参数类型枚举
 *
 * @author wang
 */
@AllArgsConstructor
@Getter
public enum ParameterTypeEnum {

    /**
     * 字符串
     */
    STRING("String", "字符串"),
    /**
     * 数字
     */
    NUMBER("Number", "数字"),
    /**
     * 布尔
     */
    BOOLEAN("Boolean", "布尔"),
    /**
     * 数组
     */
    ARRAY("Array", "数组");

    /**
     * 类型
     */
    private final String type;

    /**
     * 名称
     */
    private final String name;

}
