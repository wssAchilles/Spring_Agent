import json
import argparse
import math
from collections import Counter
import re

def tokenize(text: str) -> list:
    # 简单的分词器，提取英文单词或连续的中文字符串
    # 注意：在真实的中文场景下，推荐使用 jieba 等专业分词库，这里使用基础的正则实现
    return re.findall(r'\w+', text.lower())

class BM25Scorer:
    """
    BM25 相似度计算实现。
    """
    def __init__(self, corpus: list, k1: float = 1.5, b: float = 0.75):
        self.k1 = k1
        self.b = b
        self.corpus_size = len(corpus)
        self.avgdl = 0
        self.doc_freqs = []
        self.idf = {}
        self.doc_len = []
        
        df = Counter()
        total_len = 0
        
        # 统计词频、文档长度等
        for doc in corpus:
            tokens = tokenize(doc)
            self.doc_len.append(len(tokens))
            total_len += len(tokens)
            
            tf = Counter(tokens)
            self.doc_freqs.append(tf)
            for token in tf.keys():
                df[token] += 1
                
        self.avgdl = total_len / self.corpus_size if self.corpus_size > 0 else 0
        
        # 计算逆文档频率 IDF
        for word, freq in df.items():
            self.idf[word] = math.log(1 + (self.corpus_size - freq + 0.5) / (freq + 0.5))
            
    def get_scores(self, query: str) -> list:
        """
        计算给定查询与所有文档的 BM25 分数
        """
        scores = [0.0] * self.corpus_size
        query_tokens = tokenize(query)
        for i, doc_tf in enumerate(self.doc_freqs):
            dl = self.doc_len[i]
            for q in query_tokens:
                if q not in doc_tf:
                    continue
                tf = doc_tf[q]
                idf = self.idf.get(q, 0.0)
                # BM25 打分公式
                score = idf * (tf * (self.k1 + 1)) / (tf + self.k1 * (1 - self.b + self.b * (dl / self.avgdl)))
                scores[i] += score
        return scores

def mine_hard_negatives(query: str, documents: list, expected_contexts: list, top_k: int = 5) -> list:
    """
    根据给定的查询，从文档库中挖掘高 BM25 分数但不在期望文档列表中的难负例。
    
    :param query: 用户查询内容
    :param documents: 所有知识库文档列表，结构要求 [{"id": "...", "text": "..."}]
    :param expected_contexts: 正确答案的文档 ID 列表
    :param top_k: 截取最高分的前几个文档作为陷阱
    :return: 难负例文档 ID 列表
    """
    corpus_texts = [doc["text"] for doc in documents]
    scorer = BM25Scorer(corpus_texts)
    scores = scorer.get_scores(query)
    
    # 将文档 ID 与 BM25 得分绑定
    scored_docs = list(zip([doc["id"] for doc in documents], scores))
    
    # 过滤掉黄金标准里的正确文档，筛选出真正的“干扰项”
    filtered_docs = [doc for doc in scored_docs if doc[0] not in expected_contexts]
    
    # 按照 BM25 分数从高到低排序
    filtered_docs.sort(key=lambda x: x[1], reverse=True)
    
    # 取前 top_k 个文档 ID 返回
    return [doc[0] for doc in filtered_docs[:top_k]]

def main():
    parser = argparse.ArgumentParser(description="为 RAG 评估黄金数据集自动化挖掘难负例干扰项。")
    parser.add_argument("--input", "-i", type=str, required=True, help="输入的 JSON 格式的黄金数据集")
    parser.add_argument("--corpus", "-c", type=str, required=True, help="知识库或文档集的 JSON 文件路径")
    parser.add_argument("--output", "-o", type=str, required=True, help="处理后输出的 JSON 路径")
    parser.add_argument("--top_k", "-k", type=int, default=5, help="为每个 Query 挖掘的干扰陷阱最大数量")
    args = parser.parse_args()

    # 读取知识文档库
    with open(args.corpus, 'r', encoding='utf-8') as f:
        corpus = json.load(f)

    # 读取评估集
    with open(args.input, 'r', encoding='utf-8') as f:
        dataset = json.load(f)

    # 针对每条评估用例，注入难负例
    for item in dataset:
        query = item.get("query", "")
        expected_contexts = item.get("expected_contexts", [])
        hard_negatives = mine_hard_negatives(query, corpus, expected_contexts, top_k=args.top_k)
        item["hard_negatives"] = hard_negatives

    # 写回注入了 hard_negatives 的评估数据集
    with open(args.output, 'w', encoding='utf-8') as f:
        json.dump(dataset, f, ensure_ascii=False, indent=2)
        
    print(f"成功处理 {len(dataset)} 条记录，结果已保存至 {args.output}。")

if __name__ == "__main__":
    main()
