package tech.qiantong.qknow.hermes.memory;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 物理工作记忆字节预算截断器 (ByteBudgeter)
 * 严格执行单轮 Prompt 上下文 Level 0 内存 <= 4096 字节 (4KB) 物理硬约束
 */
@Slf4j
public class ByteBudgeter {

    public static final int DEFAULT_BYTE_BUDGET = 4096;

    /**
     * 将单一长文本截断至指定 UTF-8 字节上限以内，安全处理多字节字符边界 (防止切断 UTF-8 字符)
     */
    public static String truncateToByteBudget(String text, int maxBytes) {
        if (text == null || text.isEmpty() || maxBytes <= 0) {
            return "";
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }

        // 寻找最后一个合法的 UTF-8 字符起始边界
        int cutIndex = maxBytes;
        while (cutIndex > 0 && (bytes[cutIndex] & 0xC0) == 0x80) {
            cutIndex--; // 处于多字节字符的连续字节中，向前回退
        }

        return new String(bytes, 0, cutIndex, StandardCharsets.UTF_8);
    }

    /**
     * 将候选片段按优先级组装，在严格 <= maxBytes 预算内安全装配
     */
    public static String assembleContext(List<String> fragments, int maxBytes) {
        if (fragments == null || fragments.isEmpty() || maxBytes <= 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int currentBytes = 0;

        for (String fragment : fragments) {
            if (fragment == null || fragment.isBlank()) {
                continue;
            }
            byte[] fragBytes = fragment.getBytes(StandardCharsets.UTF_8);
            int needed = fragBytes.length + (currentBytes > 0 ? 1 : 0); // 计入换行符

            if (currentBytes + needed <= maxBytes) {
                if (currentBytes > 0) {
                    sb.append("\n");
                    currentBytes += 1;
                }
                sb.append(fragment);
                currentBytes += fragBytes.length;
            } else {
                // 空间不足，对当前片段执行边缘安全截断
                int remainingBytes = maxBytes - currentBytes - (currentBytes > 0 ? 1 : 0);
                if (remainingBytes > 32) { // 剩余容量有意义时截取局部
                    if (currentBytes > 0) {
                        sb.append("\n");
                    }
                    String truncated = truncateToByteBudget(fragment, remainingBytes);
                    sb.append(truncated);
                }
                break; // 预算耗尽，终止装配
            }
        }

        String result = sb.toString();
        // 双重安全断言保证
        byte[] finalBytes = result.getBytes(StandardCharsets.UTF_8);
        if (finalBytes.length > maxBytes) {
            result = truncateToByteBudget(result, maxBytes);
        }
        return result;
    }
}
