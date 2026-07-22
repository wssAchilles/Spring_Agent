import json
import subprocess
import os

def run_cmd(cmd):
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout, res.stderr

def build_commands_for_cell(file, cell_path, new_texts):
    cmds = []

    # Add new massive texts
    for text in new_texts:
        cmds.append({
            "command": "add",
            "parent": cell_path,
            "type": "paragraph",
            "props": {
                "text": text,
                "size": "12pt",
                "font.ea": "宋体",
                "font.latin": "Times New Roman",
                "lineSpacing": "1.5x"
            }
        })

    return cmds

def run_batch(file, commands):
    if not commands:
        return
    print(f"Applying to {file}...")
    json_path = f"{file}.batch.json"
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(commands, f, ensure_ascii=False)

    res, err = run_cmd(["officecli", "batch", file, "--input", json_path, "--force"])
    if "Error" in err or "Exception" in err:
        print(f"Error on {file}: {err}")
    else:
        print(f"Success on {file}")

    if os.path.exists(json_path):
        os.remove(json_path)

desc1_extra = [
    "在非结构化数据摄入侧，本项目独创性地自研了一套高可用、多模态的文档解析流水线。针对企业中广泛存在的扫描版 PDF、带有复杂水印的财务报表以及双栏排版的学术论文，系统集成了先进的光学字符识别（OCR）与版面分析（Layout Analysis）算法。这不仅能够精准剥离文档中的图片与干扰噪点，还能完美还原表格的三维结构，彻底解决了传统 RAG 系统在面对复杂排版时“读不懂、切不对”的核心痛点。",
    "在切片工程（Chunking）方面，系统创新性地采用了滑动窗口与多级父子块（Parent-Child Chunking）融合的语义切分策略。传统的固定字数切分常常将一个完整的逻辑段落拦腰斩断，导致大模型在生成时丢失关键的上下文信息。为此，本系统利用轻量级 NLI（自然语言推理）模型，在段落之间计算语义连贯性，动态决定最佳的切分断点。同时，所有的子块均保留对其源文档“父节点”的强引用索引。在检索召回阶段，算法会优先匹配具有高语义浓度的子块，但在最终送入大模型组装答案时，则会向上溯源提取整个父块上下文，以此达到“精度与广度”的完美平衡。",
    "在混合检索（Hybrid Search）架构的搭建上，本项目没有单纯依赖单一的密集向量（Dense Vector）距离计算，而是引入了基于 Elasticsearch 的稀疏词汇匹配（BM25）引擎。当遇到诸如订单号、特定人名或专有专业术语的查询时，纯向量检索往往会发生严重的语义漂移，而 BM25 能够通过精确的倒排索引将其精准拦截。系统在最后阶段采用倒数排序融合（RRF）算法，为不同的召回链路分配动态权重，从而在千亿级词表中实现毫秒级的跨模态极速融合。",
    "此外，为了满足极高的数据保密需求，项目在本地算力节点上通过 vLLM 推理框架量化部署了多参数量级的开源大语言模型。借助 PagedAttention 技术，我们将大模型的显存占用降低了整整 40%，使其能够在消费级显卡上流畅运行，并且原生支持并发的多路流式生成（Streaming Generation）。整个底座架构完全遵循“离线私有化”的设计原则，从根本上杜绝了企业机密数据向云端外发泄露的安全隐患，展现出了极高的工程韧性与商业落地价值。"
]

desc2_extra = [
    "在图谱建模与入库层面，本项目抛弃了传统关系型数据库死板的表结构，将海量非结构化文本通过深度学习大模型（LLM）进行实体与关系的自动抽取（Information Extraction），并将其构建为三元组，无缝导入 Neo4j 原生图数据库。在这一过程中，我们为每一个实体节点和关系边注入了多维度的元数据与向量化属性（Vector Properties）。这使得系统在执行 Cypher 查询时，不仅能进行精准的图结构遍历，还能利用 Neo4j 内置的向量索引实现语义层面的图谱模糊搜索，极大地扩展了数据关联的发现边界。",
    "本项目最核心的突破在于落地了前沿的 Leiden 社区发现图算法。当遇到诸如“请总结本年度公司的总体发展趋势”这类跨文档、跨领域的宏观查询时，传统的 RAG 架构会立刻瘫痪。而本系统能够利用 Leiden 算法，将全图谱的知识点根据拓扑连接的紧密度进行多层级（Hierarchical）的社区聚类。随后，系统会调度大模型对每一个知识社区进行全局聚合与提炼，生成层层递进的社区摘要（Community Summaries）。这种自下而上的图谱涌现能力，赋予了机器真正意义上的“宏观洞察力”。",
    "在多智能体（Multi-Agent）协作机制的设计上，我引入了极具工业级防呆水准的 CRAG（纠正型检索增强生成）策略模式。系统内置了一个专职的“评测智能体（Evaluator Agent）”，它会实时对检索回来的内部资料进行置信度打分。一旦评估判定私有知识库的内容不足以支撑准确回答，评测智能体便会立刻触发断路器，激活“全网兜底智能体（Web Fallback Agent）”。后者将自动调用搜索引擎 API 抓取外部互联网的最新资讯，剔除杂音后与内部知识融合计算，从源头上死死卡住了大模型最致命的“幻觉（Hallucination）”问题。",
    "为了让这套复杂的智能体引擎具备友好的交互性，我在前端引入了 @vue-flow/core 开发了一套所见即所得的 DAG（有向无环图）拖拽工作流画布。非技术研发人员也可以像拼接乐高积木一样，在网页上自由连接不同能力的智能体节点（如：意图识别、图谱查询、文本总结等）。后端引擎利用拓扑排序算法，将前端传递的 JSON DSL 实时编译为 CompletableFuture 的异步执行流，并通过 SSE（Server-Sent Events）建立双向微通道，将每一个智能体节点的推演步骤与思考状态以动画的形式流式推送到前端页面，完美实现了 AI 决策全过程的极致透明化与可解释性。"
]

c1 = build_commands_for_cell("6-实训报告-项目1.docx", "/body/tbl[1]/tr[2]/tc[2]", desc1_extra)
run_batch("6-实训报告-项目1.docx", c1)

c2 = build_commands_for_cell("8-实训报告-项目2.docx", "/body/tbl[1]/tr[2]/tc[2]", desc2_extra)
run_batch("8-实训报告-项目2.docx", c2)

print("Done appending massive texts.")
