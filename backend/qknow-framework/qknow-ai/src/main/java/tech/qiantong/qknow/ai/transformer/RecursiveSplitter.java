package tech.qiantong.qknow.ai.transformer;

import cn.hutool.core.util.StrUtil;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归文本分块器，借鉴 LangChain RecursiveCharacterTextSplitter 设计
 * <p>
 * 按优先级尝试多种分隔符（段落 → 换行 → 句号 → 分号 → 逗号 → 空格），
 * 如果切片仍然超长，则递归使用下一级分隔符继续切分。
 * </p>
 *
 * @author qknow
 */
public class RecursiveSplitter extends TextSplitter {

    private static final String[] DEFAULT_CHINESE_SEPARATORS = {
            "\n\n", "\n", "。", "；", "！", "？", "!", "?", "，", ",", " ", ""
    };

    private final int chunkSize;
    private final int chunkOverlap;
    private final String[] separators;

    public RecursiveSplitter(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, DEFAULT_CHINESE_SEPARATORS);
    }

    public RecursiveSplitter(int chunkSize, int chunkOverlap, String[] separators) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.separators = separators != null && separators.length > 0 ? separators : DEFAULT_CHINESE_SEPARATORS;
    }

    @Override
    protected List<String> splitText(String text) {
        return recursiveSplit(text, separators, 0);
    }

    /**
     * 递归切分文本
     *
     * @param text       待切分文本
     * @param seps       当前可用分隔符列表
     * @param sepIndex   当前使用的分隔符索引
     * @return 切分后的文本片段列表
     */
    private List<String> recursiveSplit(String text, String[] seps, int sepIndex) {
        if (StrUtil.isBlank(text)) {
            return new ArrayList<>();
        }

        // 找到当前层级的分隔符
        String separator = sepIndex < seps.length ? seps[sepIndex] : "";
        String sepToUse = separator.isEmpty() ? "" : separator;

        // 按分隔符切分
        List<String> splits;
        if (sepToUse.isEmpty()) {
            // 空分隔符：按字符强制切分
            splits = splitByCharacter(text);
        } else {
            splits = splitBySeparator(text, sepToUse);
        }

        // 合并小片段，递归处理大片段
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String piece : splits) {
            if (currentChunk.length() + piece.length() <= chunkSize) {
                currentChunk.append(piece);
            } else {
                // 当前累积的 chunk 已满
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk.setLength(0);
                }

                // 检查 piece 本身是否超长
                if (piece.length() > chunkSize) {
                    // 递归使用下一级分隔符
                    if (sepIndex + 1 < seps.length) {
                        List<String> subChunks = recursiveSplit(piece, seps, sepIndex + 1);
                        for (String sub : subChunks) {
                            if (currentChunk.length() + sub.length() <= chunkSize) {
                                currentChunk.append(sub);
                            } else {
                                if (currentChunk.length() > 0) {
                                    chunks.add(currentChunk.toString().trim());
                                    currentChunk.setLength(0);
                                }
                                currentChunk.append(sub);
                            }
                        }
                    } else {
                        // 没有更多分隔符，直接按字符切分
                        List<String> charSplits = splitByCharacter(piece);
                        for (String cs : charSplits) {
                            if (currentChunk.length() + cs.length() <= chunkSize) {
                                currentChunk.append(cs);
                            } else {
                                if (currentChunk.length() > 0) {
                                    chunks.add(currentChunk.toString().trim());
                                    currentChunk.setLength(0);
                                }
                                currentChunk.append(cs);
                            }
                        }
                    }
                } else {
                    currentChunk.append(piece);
                }
            }
        }

        // 收尾
        if (currentChunk.length() > 0) {
            String last = currentChunk.toString().trim();
            if (!last.isEmpty()) {
                chunks.add(last);
            }
        }

        // 过滤空白
        chunks.removeIf(StrUtil::isBlank);

        // 添加重叠
        if (chunkOverlap > 0 && chunks.size() > 1) {
            return applyOverlap(chunks);
        }

        return chunks;
    }

    /**
     * 按分隔符切分文本，保留分隔符在片段末尾
     */
    private List<String> splitBySeparator(String text, String separator) {
        List<String> result = new ArrayList<>();
        String[] parts = text.split(escapeRegex(separator), -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            // 保留分隔符在片段末尾（除了最后一个）
            if (i < parts.length - 1) {
                result.add(parts[i] + separator);
            } else {
                result.add(parts[i]);
            }
        }
        return result;
    }

    /**
     * 按字符强制切分
     */
    private List<String> splitByCharacter(String text) {
        List<String> result = new ArrayList<>();
        int len = text.length();
        for (int i = 0; i < len; i += chunkSize) {
            int end = Math.min(i + chunkSize, len);
            String chunk = text.substring(i, end).trim();
            if (!chunk.isEmpty()) {
                result.add(chunk);
            }
        }
        return result;
    }

    /**
     * 为切片添加重叠：从上一片末尾取 overlap 字符拼到当前片开头
     */
    private List<String> applyOverlap(List<String> chunks) {
        List<String> result = new ArrayList<>(chunks.size());
        result.add(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1);
            String current = chunks.get(i);
            int overlapLen = Math.min(chunkOverlap, prev.length());
            String overlap = prev.substring(prev.length() - overlapLen);
            String merged = overlap + current;
            // 确保不超长
            if (merged.length() > chunkSize + chunkOverlap) {
                merged = merged.substring(0, chunkSize + chunkOverlap);
            }
            result.add(merged);
        }
        return result;
    }

    /**
     * 转义正则特殊字符
     */
    private String escapeRegex(String s) {
        if (s.isEmpty()) return s;
        return s.replaceAll("([\\\\.*+?\\[\\](){}|^$])", "\\\\$1");
    }
}
