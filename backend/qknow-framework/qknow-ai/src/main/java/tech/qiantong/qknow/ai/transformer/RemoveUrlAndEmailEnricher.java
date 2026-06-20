package tech.qiantong.qknow.ai.transformer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 删除文档中 url 和 email 的转换器
 *
 * @author fabian
 */
public class RemoveUrlAndEmailEnricher implements DocumentTransformer {

    // email 匹配规则：xxx@xxx.xxx 格式，支持域名多级（如xxx@xxx.xxx.xxx）、下划线/点等合法字符
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    // url 匹配规则：http/https/www开头，支持带参数、锚点、多级域名的URL
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://|www\\.)[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=]+",
            Pattern.CASE_INSENSITIVE // 忽略大小写
    );

    /**
     * 删除文本中的所有URL和电子邮件
     *
     * @param documents the function argument
     * @return the function result
     */
    @Override
    public List<Document> apply(List<Document> documents) {
        if (CollUtil.isEmpty(documents)) {
            return documents;
        }
        List<Document> result = new ArrayList<>(documents.size());
        for (Document document : documents) {
            if (StrUtil.isBlank(document.getText())) {
                continue;
            }
            String text = document.getText();

            text = EMAIL_PATTERN.matcher(text).replaceAll("");// 替换所有邮箱地址为空字符串
            text = URL_PATTERN.matcher(text).replaceAll("");// 替换所有URL为空字符串

            Document newDoc = new Document(document.getId(), text, document.getMetadata());
            result.add(newDoc);
        }
        return result;
    }
}
