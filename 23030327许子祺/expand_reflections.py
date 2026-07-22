import json
import subprocess
import os

def run_cmd(cmd):
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout, res.stderr

def get_cell_paragraphs(file, cell_path):
    out, err = run_cmd(["officecli", "get", file, cell_path, "--depth", "1"])
    paras = []
    import re
    for line in out.splitlines():
        if "(paragraph)" in line:
            m = re.match(r'^(/[^ ]+p\[@paraId=[^\]]+\])\s+\(paragraph\)\s+"(.*)"', line)
            if not m:
                m = re.match(r'^(/[^ ]+p\[@paraId=[^\]]+\])\s+\(paragraph\)', line)
                if m:
                    text_match = re.search(r'\(paragraph\)\s+"(.*?)"', line)
                    text = text_match.group(1) if text_match else ""
                    paras.append((m.group(1), text))
            else:
                paras.append((m.group(1), m.group(2)))
    return paras

def build_commands_for_cell(file, cell_path, new_texts):
    cmds = []
    paras = get_cell_paragraphs(file, cell_path)

    # Remove all old paras
    for path, text in paras:
        cmds.append({"command": "remove", "path": path})

    # Add new massive texts with indent
    for i, text in enumerate(new_texts):
        formatted_text = "　　" + text
        cmds.append({
            "command": "add",
            "parent": cell_path,
            "type": "paragraph",
            "props": {
                "text": formatted_text,
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

w2_full = [
    "在深入学习系统架构的过程中，我深刻领悟到：技术框架的价值不在于堆砌名词，而在于是否能精准契合业务痛点。模块化单体架构让我在前期开发中保持了单进程极速调试的优势，同时又通过硬隔离的 API 层防止了业务代码的盘根错节。此外，JMeter 压测让我第一次直观感受到了并发漏斗与数据库连接池配置不当所带来的雪崩效应。",
    "在部署 Neo4j 图数据库与 pgvector 插件时，我深刻体会到了底层存储选型对于整个知识管道的决定性影响。以前仅仅在书本上学过图数据库的原理，但真正配置 Cypher 节点查询和测试向量相似度搜索的响应延迟时，我才意识到索引机制的复杂性。比如，对 pgvector 的 ivfflat 索引进行内存调参，让我明白了在海量高维数据下，B-Tree 索引是完全失效的，只有基于近似最近邻（ANN）的算法才能在召回率与计算开销之间找到平衡点。这段经历打破了我对传统关系型数据库的认知局限。",
    "在业务模块的设计上，我摒弃了原先急于求成的“面条代码”编写方式，开始主动应用领域驱动设计（DDD）的思想。我将系统的认证鉴权、知识提取流水线、检索调度中心这三大核心领域进行了硬边界隔离。起初，这种强迫性的接口抽象和 DTO/PO 转换让我觉得非常繁琐，但在后期的联调中，我惊喜地发现这种高内聚低耦合的架构让定位 Bug 的速度提升了数倍。不仅如此，统一的全局异常捕获和 AOP 切面日志记录，使得每一次 HTTP 请求的生命周期都清清楚楚，极大增强了系统的可观测性。",
    "本周最大的感悟在于，软件工程绝不仅仅是写代码，而是构建一个有韧性的生态。在进行 JMeter 高并发压测时，我目睹了连接池耗尽导致的瞬间雪崩。为了排查这一现象，我查阅了 HikariCP 的源码，调整了 connectionTimeout 和 maxLifetime 参数。这让我从一名纯粹的“API 调用者”成长为能够思考计算机底层线程模型与网络 I/O 的“架构师”。这种思维方式的转变，将成为我未来职业生涯中最宝贵的财富。"
]

w4_full = [
    "通过本周在数据预处理上的反复实验，我终于明白，大模型时代“数据决定上限”并不是一句空话。无论检索层和生成层多么精妙，如果送入模型的知识块本身被斩断了上下文关联，依然会产生灾难性的幻觉。在编写大量 Prompt 的过程中，我锻炼了与机器“沟通”的逻辑思维，意识到只有将指令极度结构化、清晰化，才能让 AI 输出具有确定性的企业级可用数据。",
    "深入到非结构化文档的解析层，我才发现真正的工业级数据清洗远比想象中复杂。我们在测试集中引入了包含密集数学公式、复杂嵌套表格以及双栏排版的学术论文，结果发现开源的简单切分库（如 LangChain 的 RecursiveCharacterTextSplitter）会将表格内容切得支离破碎。为了解决这个问题，我自学了版面分析算法，尝试利用视觉语言模型（VLM）进行坐标定位，再结合正则表达式对表格进行 Markdown 还原。这一过程极大地锻炼了我处理极其脏乱差数据的耐心和工程能力。",
    "在进行父子块（Parent-Child Chunking）机制的编码时，我深刻体会到了“以空间换时间”算法策略的精妙之处。大模型上下文窗口虽然在不断扩大，但在 RAG 场景中，喂入过多的无关噪声反而会稀释其注意力（Lost in the Middle 现象）。通过保留高密度的子块进行向量召回，再在最后关头组装其所在的宏观父块，我完美地利用了文档自身的树状拓扑结构。这不仅大幅提升了召回的命中率，还大大降低了消耗在大模型推理上的 Token 成本，让我意识到了工程学中“降本增效”的核心价值。",
    "此外，与本地 Ollama 大模型进行 Prompt 交互的经历也让我大开眼界。大模型就像是一个有着无穷知识但极度缺乏专注力的大脑。如果不给它加上严格的 JSON Schema 约束和 Few-shot 示例边界，它就会产生无边无际的幻觉。我花费了大量的精力设计提示词框架，强迫模型在输出答案前先输出“思考过程（Chain of Thought）”，这使得模型抽取出的 QA 对质量有了质的飞跃。这种通过自然语言去“编程”和“驾驭”深度神经网络的体验，极大地激发了我对人工智能底层机制的求知欲。"
]

w6_full = [
    "跨语言系统集成是我本周最大的收获。过去我仅仅满足于写出能运行的 Java 业务代码，但当真正面对成千上万条多维向量的重排压力时，高级语言的抽象开销变得无法忍受。通过引入 Rust 与 JNI，我亲自体验了掌控内存生命周期与指针传递的硬核快感。这种底层优化的思维，极大地拓宽了我的工程视野。",
    "在引入 Rust 进行 JNI 桥接的过程中，我经历了可以说是实训以来最痛苦但也最充实的调试时光。Rust 极其苛刻的借用检查器（Borrow Checker）和所有权机制，彻底颠覆了我长期在 JVM 自动垃圾回收机制下养成的随意分配内存的编程习惯。在编写 Java 与 Native 代码的内存交互层时，我反复遭遇指针悬垂和段错误（Segmentation Fault）。但正是这种一次次触发底层 Core Dump 的折磨，逼迫我去查阅操作系统关于堆栈分配与内存对齐的底层知识，让我对计算机系统的本质有了极其具象的理解。",
    "在实现 ColBERT 晚期交互的 RRF（倒数排序融合）多路召回引擎时，我对高维度空间的数据降维有了全新的认识。为了压榨那几十毫秒的延迟，我不得不放弃 Java 中方便但臃肿的 List 泛型，转而将数据拍平至原始的一维 float 数组，并且利用了 CPU 的 SIMD 矢量化执行指令。当在控制台上看到优化后的重排耗时从 850 毫秒雪崩式地降至 15 毫秒以内时，那种无与伦比的技术成就感油然而生。这让我明白，在遇到真正的性能天花板时，唯有深入到硬件架构与系统底层，才能获得突破性的力量。",
    "在这一周的高强度开发中，我还掌握了 CompletableFuture 异步编排的高阶用法。为了同时向 Neo4j、PostgreSQL 与 Elasticsearch 发起召回请求，我设计了一套非阻塞的异步流水线。这不仅考验了我对线程池核心参数调优的把握，还要求我对多线程环境下的锁竞争与上下文切换开销了如指掌。看着控制台里数十个异构召回任务以毫秒级的误差并发完成并汇总合并，我深刻体会到了并发编程在大型分布式系统中的极致魅力与压迫感。"
]

w8_full = [
    "站在全栈开发者的视角，我体会到后端算法的强悍必须通过前端出色的交互才能被最终用户感知。处理 GraphRAG 图谱海量节点渲染时导致的浏览器掉帧，让我不得不在 Vue 组件的生命周期与 Canvas 渲染流之间寻找极致的平衡。这一个多月的实训，不仅让我在技术栈上完成了闭环，更在架构思维、代码审美和工程韧性上实现了蜕变。",
    "在落地 Leiden 社区发现图算法的阶段，我被图数据科学（GDS）展现出的宏观洞察力所深深震撼。这不仅仅是写几行 Cypher 语句那么简单，而是需要将离散的知识碎片，通过复杂的数学拓扑计算，自下而上地涌现出层次化的结构。当我们向系统提问一个极其宏大的问题，而看到系统通过图谱遍历精准提炼出全域摘要时，我真切地感受到机器似乎拥有了类似于人类专家的系统性思考能力。这段经历让我对图计算在解决信息孤岛问题上的潜力充满了无限的想象。",
    "前端可视化交互的开发则让我体验到了全栈工程另一面的挑战。使用 @vue-flow/core 开发拖拽编排画布，让我不得不深入研究前端 DOM 树的渲染机制与 Canvas 绘图引擎。尤其是在处理成百上千个知识点节点的力导向图渲染时，由于频繁的重绘导致浏览器卡顿，我被迫引入了 Web Worker 进行后台布局计算，并采用了按需加载的视图剔除（Frustum Culling）技术。这种在极客追求与用户体验之间的反复拉扯，让我真正理解了什么是“以用户为中心”的软件工程哲学。",
    "回望整个长达八周的实训旅程，这不仅仅是一个堆砌代码的过程，更是一次架构思维的涅槃。从最初面对浩如烟海的需求文档时的茫然，到熟练运用领域驱动设计、JNI 底层优化、以及多智能体流式编排，我在无数个熬夜 Debug 的夜晚中完成了自我蜕变。这段宝贵的实训经历，彻底重塑了我的知识体系与解决复杂工程问题的自信心，不仅让我在技术栈的广度与深度上实现了质的飞跃，更为我未来走向真正的工业级研发岗位铺平了坚实的道路。"
]

def main():
    print("Expanding weekly reflections...")
    run_batch("1-第2周报.docx", build_commands_for_cell("1-第2周报.docx", "/body/tbl[1]/tr[3]/tc[2]", w2_full))
    run_batch("2-第4周报.docx", build_commands_for_cell("2-第4周报.docx", "/body/tbl[1]/tr[3]/tc[2]", w4_full))
    run_batch("3-第6周报.docx", build_commands_for_cell("3-第6周报.docx", "/body/tbl[1]/tr[3]/tc[2]", w6_full))
    run_batch("4-第8周报.docx", build_commands_for_cell("4-第8周报.docx", "/body/tbl[1]/tr[3]/tc[2]", w8_full))
    print("Done expanding reflections.")

if __name__ == "__main__":
    main()
