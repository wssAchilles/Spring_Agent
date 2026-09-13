package tech.qiantong.qknow.module.kmc.service.rag.nlp;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 中文领域增强词典服务
 * 提供行业专有词库识别、高频停用词过滤与受控同义词扩展。
 */
@Slf4j
@Service
public class ChineseDictionaryService {

    // 中文停用词库（含常见对话噪音、无实义修饰词）
    private final Set<String> stopWords = new HashSet<>();

    // 领域专有词库（优先按长度倒序，同长度按字典序，防止被误判为重复元素）
    private final Set<String> domainLexicon = new TreeSet<>((a, b) -> {
        int cmp = Integer.compare(b.length(), a.length());
        return cmp != 0 ? cmp : a.compareTo(b);
    });

    // 结构化同义词映射库
    private final Map<String, List<String>> synonymMatrix = new HashMap<>();

    public ChineseDictionaryService() {
        initStopWords();
        initDomainLexicon();
        initSynonymMatrix();
    }

    private void initStopWords() {
        stopWords.addAll(List.of(
                "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
                "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
                "自己", "这", "他", "她", "它", "们", "那", "里", "为", "什么", "怎么", "如何",
                "吗", "呢", "吧", "啊", "哦", "嗯", "呀", "哈", "嘛", "啦",
                "请", "请告诉我", "请问", "我想了解", "告诉我", "时候", "的时候", "主要",
                "哪些", "关于", "一下", "信息", "了解", "帮我查一下", "介绍一下", "相关说明",
                "详细说明", "具体内容", "怎么样", "为什么", "有什么", "总结一下"
        ));
    }

    private void initDomainLexicon() {
        domainLexicon.addAll(List.of(
                "检索增强生成", "知识图谱", "向量检索", "语义检索", "命名实体识别",
                "提示词工程", "语义分块", "图计算", "工作流编排", "运筹优化",
                "大语言模型", "状态图执行器", "混合检索", "重排模型", "倒排索引",
                "倒数排序融合", "实体对齐", "关系抽取", "嵌入向量", "微调训练",
                "知识抽取", "图谱对齐", "近似近邻", "余弦相似度", "分块策略",
                "自适应路由", "自我反思", "意图识别", "流式生成", "门控机制"
        ));
    }

    private void initSynonymMatrix() {
        addSynonyms("RAG", List.of("检索增强生成", "Retrieval Augmented Generation", "知识增强"));
        addSynonyms("大模型", List.of("LLM", "大语言模型", "Large Language Model"));
        addSynonyms("向量检索", List.of("语义检索", "embedding search", "向量相似度"));
        addSynonyms("知识图谱", List.of("KG", "Knowledge Graph", "知识网络"));
        addSynonyms("实体识别", List.of("NER", "命名实体识别", "实体抽取"));
        addSynonyms("分块", List.of("chunking", "chunk", "切片", "分段"));
        addSynonyms("嵌入", List.of("embedding", "特征向量"));
        addSynonyms("提示词", List.of("prompt", "指令"));
        addSynonyms("微调", List.of("fine-tuning", "finetune", "二次训练"));
        addSynonyms("幻觉", List.of("hallucination", "虚假生成"));
        addSynonyms("召回率", List.of("recall", "查全率"));
        addSynonyms("精确率", List.of("precision", "准确度"));
        addSynonyms("机器学习", List.of("ML", "Machine Learning"));
        addSynonyms("深度学习", List.of("DL", "Deep Learning"));
        addSynonyms("自然语言处理", List.of("NLP"));
        addSynonyms("计算机视觉", List.of("CV"));
        addSynonyms("第七天", List.of("Day 07", "Day07", "第七日"));
        addSynonyms("重排", List.of("rerank", "二次排序", "精排"));
        addSynonyms("混合检索", List.of("hybrid search", "多路召回"));
        addSynonyms("工作流", List.of("workflow", "DAG", "有向无环图"));
        addSynonyms("优化器", List.of("optimizer", "Gurobi", "运筹规划"));
        addSynonyms("路由", List.of("router", "分发器", "意图路由"));
        addSynonyms("智能体", List.of("Agent", "AI Agent", "认知主体"));
    }

    private void addSynonyms(String key, List<String> list) {
        synonymMatrix.put(key.toLowerCase(), list);
        for (String item : list) {
            String lowerItem = item.toLowerCase();
            List<String> reverse = new ArrayList<>(list);
            reverse.remove(item);
            reverse.add(key);
            synonymMatrix.putIfAbsent(lowerItem, reverse);
        }
    }

    public boolean isStopWord(String word) {
        if (StrUtil.isBlank(word)) {
            return true;
        }
        return stopWords.contains(word.trim().toLowerCase());
    }

    /**
     * 从查询中提取被保护的领域专业词汇
     */
    public List<String> extractDomainTerms(String query) {
        if (StrUtil.isBlank(query)) {
            return Collections.emptyList();
        }
        List<String> matched = new ArrayList<>();
        String text = query;
        for (String term : domainLexicon) {
            if (text.contains(term)) {
                matched.add(term);
            }
        }
        return matched;
    }

    /**
     * 受控的同义词扩展
     * @param terms 原始关键词列表
     * @param maxPerTerm 单个词项最大扩展数
     * @param maxTotalTerms 最终输出总词数上限（防止 SQL 膨胀）
     */
    public List<String> expandSynonyms(List<String> terms, int maxPerTerm, int maxTotalTerms) {
        if (terms == null || terms.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>(terms);

        for (String term : terms) {
            if (result.size() >= maxTotalTerms) {
                break;
            }
            if (isStopWord(term)) {
                continue;
            }
            String lower = term.toLowerCase().trim();
            List<String> syns = synonymMatrix.get(lower);
            if (syns != null && !syns.isEmpty()) {
                int added = 0;
                for (String syn : syns) {
                    if (added >= maxPerTerm || result.size() >= maxTotalTerms) {
                        break;
                    }
                    if (!isStopWord(syn)) {
                        result.add(syn);
                        added++;
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }
}
