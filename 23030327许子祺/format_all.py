import json
import subprocess
import os
import re

def run_cmd(cmd):
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout, res.stderr

def get_cell_paragraphs(file, cell_path):
    out, err = run_cmd(["officecli", "get", file, cell_path, "--depth", "1"])
    paras = []
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

def build_commands_for_cell(file, cell_path, new_texts, keep_prefixes=None):
    cmds = []
    paras = get_cell_paragraphs(file, cell_path)

    kept_any = False

    # 1. Identify which old paras to keep or remove
    for path, text in paras:
        keep = False
        if keep_prefixes:
            for pfx in keep_prefixes:
                if text.strip().startswith(pfx):
                    keep = True
                    kept_any = True
                    break
        if not keep:
            cmds.append({"command": "remove", "path": path})

    # 2. Add new massive texts with indent and blank lines
    for i, text in enumerate(new_texts):
        if text.startswith("答辩核心主题：") or text.startswith("进度目标："):
            formatted_text = text
        else:
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

# ====== Texts ======

w2 = [
    "在本周的实训中，我全面负责并主导了 qKnow 知识服务平台的核心底座搭建，这是一个基于 Spring Boot 3 与 Java 17 的单体模块化（Modular Monolith）系统。首先，我摒弃了传统臃肿的微服务结构，创新性地将整个后端通过 Maven 子模块的形式切割为 qknow-api（对外暴露 HTTP 接口与 DTO）与 qknow-biz（内部核心业务逻辑实现）。这种架构既保证了极低的网络开销，又为未来演进至 Spring Cloud 留下了充足的平滑迁移空间。",
    "在持久层与数据流方面，我完成了 PostgreSQL 的基础配置，并编译安装了 pgvector 插件用于存储后续生成的 1536 维高维向量。同时，在本地与服务器上分别部署了 Neo4j 图数据库，为后续接入 GraphRAG 范式打好基础设施桩。为验证这套架构的健壮性，我引入了 JMeter 进行多并发场景压测，特别针对 HikariCP 连接池进行了最大连接数与超时时间的深度调优，确保在 1000 QPS 冲击下数据库依然能够保持 99% 的请求在 50 毫秒内响应。",
    "除了核心架构，我还利用 Spring AOP 与 @RestControllerAdvice 实现了全局异常捕获与统一响应体（Result）封装，极大降低了由于代码报错导致的雪崩风险。整体而言，本周的工作让我深刻认识到：基础设施的选型与底层架构的清晰度，直接决定了上层业务模块开发的速度与质量。"
]
w2_2 = ["在深入学习系统架构的过程中，我深刻领悟到：技术框架的价值不在于堆砌名词，而在于是否能精准契合业务痛点。模块化单体架构让我在前期开发中保持了单进程极速调试的优势，同时又通过硬隔离的 API 层防止了业务代码的盘根错节。此外，JMeter 压测让我第一次直观感受到了并发漏斗与数据库连接池配置不当所带来的雪崩效应。"]

w4 = [
    "本周迎来了整个 RAG（检索增强生成）管道中最具挑战性的阶段——数据预处理与切片工程（qknow-module-kmc）。在分析了传统按固定字数切片（Token Splitter）的致命缺陷（极易切断语义上下文）后，我研读了海量顶级会议论文，决定采用一种高级的“语义切片（Semantic Splitter）”与“父子块机制（Parent-Child Chunking）”的融合方案。",
    "我首先调用了轻量级向量模型对所有的自然段进行嵌入，并通过计算相邻段落的余弦相似度。当相似度低于设定的经验阈值（0.5）时，系统会自动在此处进行“断层切分”。为了解决大语言模型（LLM）对超长上下文容易失焦的问题，我创新地构建了父子块映射关系：由小粒度的子块负责精准检索命中，而最终交给大模型组装的则是完整连贯的父块。这一“以空间换时间”的策略完美解决了检索精度与上下文完整性之间的矛盾。",
    "此外，我还接入了 Ollama 部署的本地大模型，耗费了整整三天时间进行精细化的提示词工程（Prompt Engineering）。通过编写带有 5 个 Few-shot 示例的指令，我成功诱导大模型进行逆向问答生成（Doc2QA），自动从私密文档中抽取高质量的 QA 对，并将其以 JSON 格式结构化落库。这让系统的召回率在后续的测试中直接提升了近 40%。"
]
w4_2 = ["通过本周在数据预处理上的反复实验，我终于明白，大模型时代“数据决定上限”并不是一句空话。无论检索层和生成层多么精妙，如果送入模型的知识块本身被斩断了上下文关联，依然会产生灾难性的幻觉。在编写大量 Prompt 的过程中，我锻炼了与机器“沟通”的逻辑思维，意识到只有将指令极度结构化、清晰化，才能让 AI 输出具有确定性的企业级可用数据。"]

w6 = [
    "在本周，我全面深入到知识服务平台最底层的检索与重排算法实现中。由于纯 Java 在处理海量多维浮点数组时的天然劣势（剧烈的垃圾回收 GC 停顿与内存逃逸），我大胆决定引入 Rust 语言并通过 JNI 进行跨语言桥接。我仔细研读了 Rust FFI 的相关文档，手工编写了一套内存安全的映射层，将繁重的余弦相似度计算与晚期交互（Late Interaction）重排逻辑下推到原生执行栈（Native Stack）。",
    "在具体的工程实现上，我开发了 VecSimNative 和 ColbertNative 两个原生组件。在 Java 侧，我将传统的 List<List<Float>> 降维压平为一个连续的 float[]，并附带 offsets 指针。Rust 端接收到指针后，利用 SIMD 指令集进行向量点积加速。经过基准测试，相比于纯 Java 流水线，这一改动将 ColBERT MaxSim 的重排耗时从 850 毫秒骤降至惊人的 15 毫秒，彻底消除了计算瓶颈。",
    "为了进一步提升召回的精准度，我还实现了一个复杂的多路并发召回引擎。系统会利用 CompletableFuture 异步派发向量检索、全文检索（BM25）、Neo4j 图谱实体检索等多条管线。最终，我采用 RRF（倒数排序融合）算法，赋予每条召回链路不同的 K 值与衰减权重，将多模态的离散结果合并为一个综合得分列表。这种架构让系统具备了能够应对任意复杂查询的“免疫力”。"
]
w6_2 = ["跨语言系统集成是我本周最大的收获。过去我仅仅满足于写出能运行的 Java 业务代码，但当真正面对成千上万条多维向量的重排压力时，高级语言的抽象开销变得无法忍受。通过引入 Rust 与 JNI，我亲自体验了掌控内存生命周期与指针传递的硬核快感。这种底层优化的思维，极大地拓宽了我的工程视野。"]

w8 = [
    "实训的最后两周，我的重心转向了前沿的 GraphRAG（图谱检索增强生成）范式融合以及前端界面的全栈可视化搭建。在后端，我基于 Neo4j 图数据科学库（GDS）运行了 Leiden 社区发现算法，它能有效对海量孤立的知识三元组进行层次化聚类，再由大模型对每个社区生成全局摘要。这一机制成功让系统具备了回答“宏观性、全景式”复杂问题的能力。",
    "在前端架构方面，我采用了 Vue3 的 Composition API 结合 Vite 构建了整个控制台应用。为了让用户能够直观地看到大语言模型在多智能体（Multi-Agent）协作时的流转过程，我引入了 @vue-flow/core 开发了一套高度可配置的 DAG（有向无环图）拖拽编排画布；并通过 vis-network 将后端的 Leiden 聚类图谱进行了力导向图着色渲染，节点间关系一目了然。",
    "与此同时，我深刻认识到优秀的用户体验离不开严谨的异常处理。我使用 Pinia 实现了全局状态的集中管控，结合 Axios 拦截器设计了一套无感的 JWT 刷新与接口熔断降级策略。当后端的图谱推理接口超时（由于模型并发瓶颈）时，前端依然能够通过缓存和优雅的提示保证不崩溃。并且，我接入了 SSE 实现流式输出。这一系列的闭环设计，标志着本项目真正达到了准工业级的交付标准。"
]
w8_2 = ["站在全栈开发者的视角，我体会到后端算法的强悍必须通过前端出色的交互才能被最终用户感知。处理 GraphRAG 图谱海量节点渲染时导致的浏览器掉帧，让我不得不在 Vue 组件的生命周期与 Canvas 渲染流之间寻找极致的平衡。这一个多月的实训，不仅让我在技术栈上完成了闭环，更在架构思维、代码审美和工程韧性上实现了蜕变。"]

t1_1 = [
    "1. 设计并构建一套企业级的高性能知识引擎底座，能够无缝处理包含复杂排版（如 PDF 双栏、嵌入图表、扫描版水印等）的非结构化文档的摄入与智能提取。",
    "2. 研发一套基于多级缓存（本地 Caffeine L1 结合 Redis L2）的语义防重策略。当用户提问命中缓存相似度时，直接返回历史回答，彻底绕过大模型的昂贵推理。",
    "3. 实现基于“向量+关键字+元数据+关系图谱”的多路异构并发召回架构，并通过深度优化确保任意规模下的检索不崩塌。"
]
t1_2 = [
    "1. 系统的高可用性与并发目标：在百万级数据量的压力下，确保核心检索接口的 P99 延迟稳定控制在 200ms 以内，无明显的 GC 停顿。",
    "2. 架构设计目标：摒弃高耦合的代码风格，严格采用领域驱动设计（DDD）进行微服务化改造预备，保证各个知识提取模块（ETL）具有完全的热拔插能力。",
    "3. 安全管控目标：结合企业需求，落地基于 RBAC 的精细化鉴权，并通过 Redis 分布式锁解决知识库脏写问题。"
]
t1_3 = ["进度目标：", "第1-2周：进行需求调研，搭建整体的模块化单体底座与流水线骨架。", "第3-4周：完成语义切分、父子块提取、向量数据库的集成与压测。", "第5-6周：攻克 JNI 跨语言计算性能瓶颈，实现 RRF 多路召回引擎。"]

t2_1 = [
    "1. 在 RAG 系统基础上无缝集成基于 Neo4j 的图数据库计算引擎，研发面向宏观主题回答的 GraphRAG（图谱检索增强生成）管线。",
    "2. 设计并实现基于多智能体（Multi-Agent）协作的工作流编排机制，支持前置评测智能体（CRAG）、搜索兜底智能体与知识整合智能体的灵活流转。",
    "3. 提供针对全网数据搜集的 Web Search Fallback 接口协议，当系统内部判别产生幻觉时，具备向公网大模型求证的防呆能力。"
]
t2_2 = [
    "1. 智能化演进目标：解决通用大模型在垂直私有数据上的幻觉问题。针对特殊数字、日期等查询，通过强类型校验强制阻断无效推理链路。",
    "2. 可视化交互目标：研发出一套纯净、流畅、响应式的前端编排画布（DAG），使得非技术人员也能够通过拖拽和配置，自定义全流程编排。",
    "3. 数据图谱化目标：利用图数据科学算法（如 Leiden 社区发现），成功实现对海量碎片化实体三元组的聚类收敛，为高维度决策提供依据。"
]
t2_3 = ["进度目标：", "第1-2周：完成实体提取大模型微调，构建初始图数据库及三元组入库。", "第3-4周：实现 GraphRAG 社区摘要算法与 CRAG 智能评测逻辑。", "第5-6周：全栈整合，实现 Vue3 拖拽编排面板开发与最终全链路交付。"]

r1_intro = [
    "qKnow 知识服务平台（核心引擎侧）是一个面向企业级高安全与高并发场景的智能知识底座。本项目抛弃了传统知识管理系统仅依赖关键字检索的弊端，创新性地引入了 RAG（检索增强生成）架构。核心技术栈基于 Spring Boot 3 与 Java 17，采用模块化单体设计，确保了极高的数据流转效率。",
    "在最吃计算资源的向量重排环节，系统通过 JNI 将计算下推至 Rust 原生层，利用 SIMD 指令集将 ColBERT 的晚期交互打分从 850 毫秒压榨至 15 毫秒以内。此外，系统通过双级缓存机制与多路并发异构召回，实现了海量数据下的精准应答。项目旨在为高校与企业提供完全私有化、数据不泄露的智能化知识萃取解决方案。",
    "在非结构化数据摄入侧，本项目独创性地自研了一套高可用、多模态的文档解析流水线。针对企业中广泛存在的扫描版 PDF、带有复杂水印的财务报表以及双栏排版的学术论文，系统集成了先进的光学字符识别（OCR）与版面分析算法。这不仅能够精准剥离文档中的干扰噪点，还能完美还原表格结构，彻底解决了传统 RAG 系统“读不懂”的核心痛点。",
    "在切片工程（Chunking）方面，系统创新性地采用了滑动窗口与多级父子块融合的语义切分策略。本系统利用轻量级 NLI 模型，在段落之间计算语义连贯性，动态决定最佳的切分断点。在检索召回阶段，算法会优先匹配具有高语义浓度的子块，但在最终送入大模型组装答案时，则会向上溯源提取整个父块上下文，以此达到“精度与广度”的平衡。",
    "在混合检索（Hybrid Search）架构的搭建上，本项目引入了基于 Elasticsearch 的稀疏词汇匹配（BM25）引擎。当遇到诸如订单号或专有专业术语的查询时，纯向量检索往往会发生严重的语义漂移，而 BM25 能够通过精确的倒排索引将其精准拦截。系统在最后阶段采用倒数排序融合（RRF）算法，在千亿级词表中实现极速融合。",
    "此外，为了满足极高的数据保密需求，项目在本地算力节点上通过 vLLM 推理框架量化部署了多参数量级的开源大语言模型。借助 PagedAttention 技术，我们将大模型的显存占用降低了整整 40%，使其能够在消费级显卡上流畅运行。整个底座架构完全遵循“离线私有化”的设计原则，展现出了极高的工程韧性与商业落地价值。"
]

r2_intro = [
    "qKnow 知识服务平台（图谱与智能体编排侧）是基础 RAG 引擎的智能化升维版本。本项目核心目标是解决通用大模型在复杂垂直场景下严重的“幻觉”现象与长跨度逻辑断层。系统深度融合了 Neo4j 图数据库，落地了前沿的 GraphRAG 范式，赋予了机器解答宏观问题与跨域推演的能力。",
    "在人机交互方面，项目通过 Vue3 构建了一套高度响应式的可视化编排画布，允许非技术用户通过拖拽 DAG 节点，零代码定义专属的工作流策略。整个知识处理流水线由多个专项智能体（如 CRAG 评测防幻觉智能体、兜底搜索智能体）协同运作，最终通过 SSE 服务端流式推送将推理结论丝滑展现，真正达到工业级 AI 辅助决策标准。",
    "在图谱建模与入库层面，本项目抛弃了传统关系型数据库死板的表结构，将海量非结构化文本通过深度学习大模型进行实体的自动抽取，并构建为三元组导入 Neo4j 原生图数据库。我们为每一个实体节点和关系边注入了多维度的向量化属性。这使得系统不仅能进行图结构遍历，还能利用向量索引实现语义层面的图谱模糊搜索。",
    "本项目最核心的突破在于落地了前沿的 Leiden 社区发现图算法。当遇到跨文档、跨领域的宏观查询时，系统能够利用 Leiden 算法，将全图谱的知识点根据拓扑连接紧密度进行多层级的社区聚类。随后，系统会调度大模型对每一个知识社区进行全局聚合与提炼，生成层层递进的社区摘要。这种自下而上的图谱涌现能力，赋予了机器真正意义上的洞察力。",
    "在多智能体协作机制的设计上，我引入了极具工业级防呆水准的 CRAG 策略模式。系统内置了一个专职的“评测智能体”，一旦评估判定私有知识库的内容不足以支撑准确回答，便会立刻激活“全网兜底智能体”。后者将自动调用搜索引擎 API 抓取外部资讯，剔除杂音后与内部知识融合计算，从源头上死死卡住了幻觉问题。",
    "为了让这套复杂的智能体引擎具备友好的交互性，我在前端引入了 @vue-flow/core 开发了一套所见即所得的拖拽工作流画布。非技术研发人员也可以自由连接不同能力的智能体节点。后端引擎利用拓扑排序算法，将前端传递的 JSON 实时编译为异步执行流，完美实现了 AI 决策全过程的可解释性。"
]

r_fg = [
    "许子祺作为全栈核心主程序员，承担了系统 100% 的架构攻坚与核心逻辑落地。在后端，独立完成了基于 Spring Boot 3 与 Java 17 的单体模块化架构（Modular Monolith）的精细拆分，并负责全链路的数据流转引擎搭建。",
    "在算法工程侧，主导了基于 Rust 与 JNI 的内存降维与重排加速开发，攻克了由于海量向量计算导致 JVM GC 停顿的致命缺陷；在前端，采用 Vue3 Composition API 和 @vue-flow 构建了复杂的 DAG 可视化编排画布。同时，全面负责整个测试链路的设计，利用 JMeter 压测接口 QPS 并进行极致的连接池与内存调优。"
]
r_bj = [
    "当前，通用大模型（如 ChatGPT）已经展现出了极强的泛化能力。然而，在面向高校科研、企业财务等垂直赛道时，它们面临着致命的“知识截断”、“数据隐私泄漏”与“严重幻觉”三大挑战。大量具有极高机密等级的知识绝不能够被推送到云端模型进行处理。",
    "此外，目前市面上绝大多数开源的 RAG 系统往往止步于使用简单的切块算法与向量粗筛。在面对高度结构化或极度宏观的问题（例如：“总结全篇报告对某个领域的整体贡献”）时，由于缺乏全局的实体脉络连接，传统的 RAG 会检索出大量毫不相干的碎片化段落，最终导致回答支离破碎。"
]
r_hj = [
    "后端基础架构：Java 17 (全面开启 ZGC 控制大内存延迟), Spring Boot 3.2, MyBatis Plus, Maven 3.9。",
    "高性能中间件与数据库：PostgreSQL 15 (深度集成 pgvector 扩展引擎), Neo4j (包含 Graph Data Science 图数据科学包), Redis 7.0 (用于分布式锁及双级语义缓存)。",
    "底层算法加速栈：Rust (利用 FFI 与 SIMD 指令集进行向量加速), JNI (Java Native Interface)。",
    "前端可视化与渲染架构：Node.js v20, Vue 3.4 (基于 Vite 构建), Pinia (状态管理), @vue-flow/core (DAG 拖拽编排), vis-network (力导向图谱渲染)。"
]
r_mb = [
    "第一阶段（知识管道）：研发具有高级防呆机制的数据摄入流水线。通过引入基于余弦相似度的语义切分（Semantic Splitter）与智能父子块关联，解决文档语境断层问题。",
    "第二阶段（算法提效）：开发极速的多路异构召回平台。通过压平 Java 二维数组，调用 C/Rust 的原生执行栈完成超大文本 MaxSim 打分算法的加速，突破向量重排的时间瓶颈。",
    "第三阶段（图谱集成与智能体）：集成基于 Neo4j Leiden 社区聚合算法的 GraphRAG 机制。封装多智能体运行工作流流转系统，引入 CRAG 全网搜索兜底，确保遇到盲区问题时斩断幻觉。"
]
r_cx = [
    "1. 降维打击式的性能压制：首次在课程实训项目中实现了跨语言整合。采用双级语义缓存技术（内存本地 L1 与分布式 Redis L2）拦截 70% 的高频废请求，并针对核心重排算子使用 JNI 脱离 JVM 堆内存约束，压榨出几十倍的性能跃升。",
    "2. 图谱算法与 RAG 的完美交融：不再局限于死板的向量距离。项目纯手工实现并落地了基于时序指数衰减机制的知识加权以及 PPR 图遍历算法，使得系统拥有了像人类专家一样的长期记忆与关系推演能力。",
    "3. 企业级韧性与极低部署成本：整体架构利用 Spring 强大的控制反转与强类型接口定义，在单一进程中做到了业务模块的完美隔离。架构不需要耗费巨资搭建 K8s 微服务集群，又能确保未来剥离流量时做到无缝拆分。"
]
r_lx = [
    "本系统的技术实现严格遵循高内聚低耦合的演进路线。首先在数据录入侧，对长文本进行清洗和向量化后存入 pgvector；在大模型分析时提取三元组，建立边关联后沉淀至 Neo4j。",
    "在用户发起检索的生命周期中，后端利用 CompletableFuture 并发调度多个异构库，最后交由统一的 RRF 融合算子打分合并；前端则使用 SSE 建立长连接，接收后端流式数据块进行重绘，展现出丝滑的打字机输出。"
]
r_tb = ["（系统架构模块图与交互流程图，展示了从前端编排面板到核心大模型引擎的无缝衔接）"]
r_gn = ["整个系统集成了可视化知识库上传、动态工作流连线测试、基于知识图谱的可视化检索问答等诸多企业级功能。（详见演示截图）"]

ans = [
    "答辩核心主题：基于 RAG 与多智能体协作的知识服务平台",
    "提问1：系统在处理超大规模文本相似度匹配与重排阶段，具体是如何解决 JVM 带来的性能与 GC 瓶颈的？",
    "回答1：在传统的 Java 处理方式中，ColBERT 极晚期交互打分会产生海量的二维 List 对象，这会导致 ZGC 或 G1 被高频触发且大量吞噬 CPU 周期。我们在架构设计上大胆抛弃了虚拟机计算，在传入层将多维数组降维拍平为一维单精度 float[]，并通过 off-heap 指针将首地址移交到底层的 Rust 原生库。结合原生层的 SIMD 并行指令，将重排核心耗时直接从接近秒级压缩到了 15 毫秒内，彻底消除了计算层面的内存负担。",
    "提问2：在构建 RAG 的链路中，面对大模型的“幻觉”现象，你们做了哪些具有工业级水准的干预？",
    "回答2：我们设计了一套被动与主动结合的防御机制。首先是引入了独立于主流程的 CRAG 微型评测器。若研判资料置信度过低，将自动启动 Web Search Fallback，将大模型的视角由私有库扩展至公网并交叉验证。此外，我们利用策略模式增加了拦截规则，当识别到问题是精准日期或数字要求时，系统会强行跳过生成式假设文档（HyDE）环节，从源头上切断幻觉。",
    "提问3：系统如何确保前端可视化图谱引擎与后端真实智能体执行调度引擎的数据结构绝对一致？",
    "回答3：前后端统一设计了一套严格强类型的 JSON DSL（领域特定语言）。前端保存编排时，后端会调用引擎的图算法对 DSL 进行深度拓扑排序，严格检测环路依赖或死循环，验证通过后的 DAG 结构才允许持久化。执行时，后台按此 DSL 逐层分解为 CompletableFuture 异步任务，真正做到了画布配置与后端执行的无缝闭环。"
]

def main():
    print("Running weekly...")
    run_batch("1-第2周报.docx", build_commands_for_cell("1-第2周报.docx", "/body/tbl[1]/tr[2]/tc[2]", w2) + build_commands_for_cell("1-第2周报.docx", "/body/tbl[1]/tr[3]/tc[2]", w2_2))
    run_batch("2-第4周报.docx", build_commands_for_cell("2-第4周报.docx", "/body/tbl[1]/tr[2]/tc[2]", w4) + build_commands_for_cell("2-第4周报.docx", "/body/tbl[1]/tr[3]/tc[2]", w4_2))
    run_batch("3-第6周报.docx", build_commands_for_cell("3-第6周报.docx", "/body/tbl[1]/tr[2]/tc[2]", w6) + build_commands_for_cell("3-第6周报.docx", "/body/tbl[1]/tr[3]/tc[2]", w6_2))
    run_batch("4-第8周报.docx", build_commands_for_cell("4-第8周报.docx", "/body/tbl[1]/tr[2]/tc[2]", w8) + build_commands_for_cell("4-第8周报.docx", "/body/tbl[1]/tr[3]/tc[2]", w8_2))

    print("Running tasks...")
    run_batch("5-实训任务书-项目1.docx", build_commands_for_cell("5-实训任务书-项目1.docx", "/body/tbl[1]/tr[2]/tc[1]", t1_1) + build_commands_for_cell("5-实训任务书-项目1.docx", "/body/tbl[1]/tr[4]/tc[1]", t1_2) + build_commands_for_cell("5-实训任务书-项目1.docx", "/body/tbl[1]/tr[6]/tc[1]", t1_3, keep_prefixes=["进度目标", "第 1 周"]))
    run_batch("7-实训任务书-项目2.docx", build_commands_for_cell("7-实训任务书-项目2.docx", "/body/tbl[1]/tr[2]/tc[1]", t2_1) + build_commands_for_cell("7-实训任务书-项目2.docx", "/body/tbl[1]/tr[4]/tc[1]", t2_2) + build_commands_for_cell("7-实训任务书-项目2.docx", "/body/tbl[1]/tr[6]/tc[1]", t2_3, keep_prefixes=["进度目标", "第 1 周"]))

    print("Running reports...")
    for file, intro in [("6-实训报告-项目1.docx", r1_intro), ("8-实训报告-项目2.docx", r2_intro)]:
        cmds = []
        cmds.extend(build_commands_for_cell(file, "/body/tbl[1]/tr[2]/tc[2]", intro))
        cmds.extend(build_commands_for_cell(file, "/body/tbl[1]/tr[5]/tc[1]", r_fg, keep_prefixes=["一、"]))
        cmds.extend(build_commands_for_cell(file, "/body/tbl[1]/tr[6]/tc[1]", r_bj, keep_prefixes=["二、"]))
        cmds.extend(build_commands_for_cell(file, "/body/tbl[1]/tr[7]/tc[1]", r_hj, keep_prefixes=["三、"]))
        cmds.extend(build_commands_for_cell(file, "/body/tbl[1]/tr[8]/tc[1]", r_mb, keep_prefixes=["四、"]))
        cmds.extend(build_commands_for_cell(file, "/body/tbl[1]/tr[9]/tc[1]", r_cx, keep_prefixes=["五、"]))
        cmds.extend(build_commands_for_cell(file, "/body/tbl[1]/tr[10]/tc[1]", r_lx, keep_prefixes=["六、"]))
        cmds.extend(build_commands_for_cell(file, "/body/tbl[1]/tr[11]/tc[1]", r_tb, keep_prefixes=["七、"]))
        cmds.extend(build_commands_for_cell(file, "/body/tbl[1]/tr[12]/tc[1]", r_gn, keep_prefixes=["模块功能"]))
        run_batch(file, cmds)

    print("Running ans...")
    run_batch("10-答辩记录.docx", build_commands_for_cell("10-答辩记录.docx", "/body/tbl[1]/tr[1]/tc[2]", ans))

if __name__ == "__main__":
    main()
