package tech.qiantong.qknow.hermes.flow.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 类型安全上下文变量访问器
 * 解决反序列化快照时泛型类型擦除、数值提升（Integer vs Long）引发的 ClassCastException
 */
@Slf4j
public class TypeSafeContextAccessor {

    private TypeSafeContextAccessor() {
        // 工具类禁止实例化
    }

    /**
     * 安全提取字符串
     */
    public static String getString(Map<String, Object> context, String key, String defaultValue) {
        Object val = getNestedValue(context, key);
        if (val == null) {
            return defaultValue;
        }
        return String.valueOf(val);
    }

    public static String getString(Map<String, Object> context, String key) {
        return getString(context, key, null);
    }

    /**
     * 安全提取 Long 类型
     */
    public static Long getLong(Map<String, Object> context, String key, Long defaultValue) {
        Object val = getNestedValue(context, key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number num) {
            return num.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(val).trim());
        } catch (NumberFormatException e) {
            log.warn("无法将键 [{}] 的值 [{}] 解析为 Long，使用默认值 {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    public static Long getLong(Map<String, Object> context, String key) {
        return getLong(context, key, null);
    }

    /**
     * 安全提取 Integer 类型
     */
    public static Integer getInteger(Map<String, Object> context, String key, Integer defaultValue) {
        Object val = getNestedValue(context, key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number num) {
            return num.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(val).trim());
        } catch (NumberFormatException e) {
            log.warn("无法将键 [{}] 的值 [{}] 解析为 Integer，使用默认值 {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    public static Integer getInteger(Map<String, Object> context, String key) {
        return getInteger(context, key, null);
    }

    /**
     * 安全提取 Double 类型
     */
    public static Double getDouble(Map<String, Object> context, String key, Double defaultValue) {
        Object val = getNestedValue(context, key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(val).trim());
        } catch (NumberFormatException e) {
            log.warn("无法将键 [{}] 的值 [{}] 解析为 Double，使用默认值 {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    public static Double getDouble(Map<String, Object> context, String key) {
        return getDouble(context, key, null);
    }

    /**
     * 安全提取 Boolean 类型
     */
    public static Boolean getBoolean(Map<String, Object> context, String key, Boolean defaultValue) {
        Object val = getNestedValue(context, key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        String str = String.valueOf(val).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(str) || "1".equals(str) || "yes".equals(str)) {
            return true;
        }
        if ("false".equals(str) || "0".equals(str) || "no".equals(str)) {
            return false;
        }
        return defaultValue;
    }

    public static Boolean getBoolean(Map<String, Object> context, String key) {
        return getBoolean(context, key, null);
    }

    /**
     * 安全提取 Map 类型
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(Map<String, Object> context, String key) {
        Object val = getNestedValue(context, key);
        if (val instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        if (val instanceof JSONObject jsonObject) {
            return new LinkedHashMap<>(jsonObject);
        }
        return Collections.emptyMap();
    }

    /**
     * 安全提取 List 类型
     */
    public static <T> List<T> getList(Map<String, Object> context, String key, Class<T> targetClass) {
        Object val = getNestedValue(context, key);
        if (val instanceof List<?> list) {
            List<T> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    if (targetClass.isInstance(item)) {
                        result.add(targetClass.cast(item));
                    } else if (targetClass == String.class) {
                        result.add(targetClass.cast(String.valueOf(item)));
                    } else if (Number.class.isAssignableFrom(targetClass) && item instanceof Number num) {
                        if (targetClass == Long.class) {
                            result.add(targetClass.cast(num.longValue()));
                        } else if (targetClass == Integer.class) {
                            result.add(targetClass.cast(num.intValue()));
                        } else if (targetClass == Double.class) {
                            result.add(targetClass.cast(num.doubleValue()));
                        }
                    }
                }
            }
            return result;
        }
        if (val instanceof JSONArray jsonArray) {
            return jsonArray.toJavaList(targetClass);
        }
        return Collections.emptyList();
    }

    /**
     * 递归点号深度寻址，如 "user.profile.age" 或直接寻址 "node_a.text"
     */
    private static Object getNestedValue(Map<String, Object> context, String key) {
        if (context == null || key == null || key.isBlank()) {
            return null;
        }
        // 1. 优先直接匹配（因很多节点 key 命名即带点号，如 "node_1.output"）
        if (context.containsKey(key)) {
            return context.get(key);
        }

        // 2. 尝试按点号分级递归
        if (!key.contains(".")) {
            return null;
        }

        String[] parts = key.split("\\.");
        Object current = context;
        for (String part : parts) {
            if (current instanceof Map<?, ?> currentMap) {
                current = currentMap.get(part);
            } else if (current instanceof JSONObject jsonObject) {
                current = jsonObject.get(part);
            } else {
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
