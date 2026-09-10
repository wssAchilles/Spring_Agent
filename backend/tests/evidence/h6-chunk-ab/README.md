# H6 Chunk A/B Evidence Log (incremental)

> 边跑边写，防止额度耗尽丢结果。

## 2026-09-10 run 1 — child 128 vs 256 on 人工智能.pdf subset

- model: `qwen3.7-text-embedding` (1024d)
- corpus: 400 segments of `人工智能.pdf`, re-parented ~800 chars
- children: c128 capped 800, c256 capped 600
- tokens: c128 **40,552** + c256 **50,926** ≈ **91,478**
- queries: 16 proxy queries from segment prefixes; Hit@10 = child contains 15-char shingle
- **Hit@10: c128=0.875, c256=0.500, delta=-0.375**
- raw: `h6-child-128-vs-256.json`

### Interpretation (mechanism, not production promotion)

On this English-heavy 人工智能.pdf subset, **child=128 更好**（0.875 vs 0.500）。  
与 roadmap 里「128 过碎、256 更好」的假设**相反**——至少在该子集 + 自代理 query 下如此。  
**不改生产默认（保持 child-tokens=128）**。更大中文文档集需另跑。

### Quota note

- qwen3.7-text-embedding 免费额度仍较充足（本轮仅 ~9 万 token）。
- text-embedding-v4 剩 ~40 万，优先留给已有向量库查询侧。
