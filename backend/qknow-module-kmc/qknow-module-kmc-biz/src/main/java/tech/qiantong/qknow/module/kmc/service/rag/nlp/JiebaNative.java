package tech.qiantong.qknow.module.kmc.service.rag.nlp;

import lombok.extern.slf4j.Slf4j;

/**
 * jieba-rs JNI 桥接类
 * [溯源] 算法优化指南 Phase 2: 中文分词 jieba-rs 集成
 *
 * 使用 Rust jieba-rs 库进行中文分词，比 Java 实现快 20-27x
 * 降级策略：JNI 加载失败时回退到 Java 分词
 */
@Slf4j
public class JiebaNative {

    private static volatile boolean loaded = false;
    private static volatile boolean attempted = false;

    static {
        try {
            System.loadLibrary("jieba_jni");
            loaded = true;
            log.info("jieba-rs JNI library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            loaded = false;
            log.warn("jieba-rs JNI library not found, falling back to Java tokenization: {}", e.getMessage());
        }
        attempted = true;
    }

    /**
     * 中文分词（精确模式）
     * @param text 输入文本
     * @return 分词结果数组
     */
    public static native String[] cut(String text);

    /**
     * 中文分词 + 词性标注
     * @param text 输入文本
     * @return "word|pos" 格式数组
     */
    public static native String[] cutWithPos(String text);

    /**
     * 检查 JNI 库是否可用
     */
    public static boolean isAvailable() {
        return loaded;
    }

    /**
     * 安全分词：JNI 不可用时返回 null
     */
    public static String[] safeCut(String text) {
        if (!loaded || text == null || text.isBlank()) {
            return null;
        }
        try {
            return cut(text);
        } catch (Exception e) {
            log.debug("jieba-rs cut failed: {}", e.getMessage());
            return null;
        }
    }
}
