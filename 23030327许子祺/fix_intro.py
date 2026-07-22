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

    # 1. Add new massive texts FIRST
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

    # 2. Remove all OLD paragraphs
    for path, text in paras:
        keep = False
        if keep_prefixes:
            for pfx in keep_prefixes:
                if text.strip().startswith(pfx):
                    keep = True
                    break
        if not keep:
            cmds.append({"command": "remove", "path": path})

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

desc1 = [
    "qKnow 知识服务平台（核心引擎侧）是一个面向企业级高安全与高并发场景的智能知识底座。本项目抛弃了传统知识管理系统仅依赖关键字检索的弊端，创新性地引入了 RAG（检索增强生成）架构。核心技术栈基于 Spring Boot 3 与 Java 17，采用模块化单体（Modular Monolith）设计，确保了在单进程内极高的数据流转效率。",
    "在最吃计算资源的向量重排环节，系统通过 JNI 将计算下推至 Rust 原生层，利用 SIMD 指令集将 ColBERT 的晚期交互打分从 850 毫秒压榨至 15 毫秒以内。此外，系统通过双级缓存机制与多路并发异构召回（向量+图谱+倒排），实现了海量数据下的毫秒级精准应答。项目不仅提供高度可扩展的 API 接口，还包含严格的鉴权防线，旨在为高校与企业提供一套完全私有化、数据不泄露的智能化知识萃取解决方案。"
]

desc2 = [
    "qKnow 知识服务平台（图谱与智能体编排侧）是基础 RAG 引擎的智能化升维版本。本项目核心目标是解决通用大模型在复杂垂直场景下严重的“幻觉”现象与长跨度逻辑断层。系统深度融合了 Neo4j 图数据库，落地了前沿的 GraphRAG 范式。通过 Leiden 图数据科学算法，实现了对海量知识碎片的三元组社区聚类与全局摘要生成，赋予了机器解答宏观问题与跨域推演的能力。",
    "在人机交互方面，项目通过 Vue3 构建了一套高度响应式的可视化编排画布，允许非技术用户通过拖拽 DAG（有向无环图）节点，零代码定义专属的工作流策略。整个知识处理流水线由多个专项智能体（如 CRAG 评测防幻觉智能体、兜底搜索智能体）协同运作，最终通过 SSE 服务端流式推送将推理结论丝滑展现，真正达到工业级 AI 辅助决策标准。"
]

c1 = build_commands_for_cell("6-实训报告-项目1.docx", "/body/tbl[1]/tr[2]/tc[2]", desc1)
run_batch("6-实训报告-项目1.docx", c1)

c2 = build_commands_for_cell("8-实训报告-项目2.docx", "/body/tbl[1]/tr[2]/tc[2]", desc2)
run_batch("8-实训报告-项目2.docx", c2)

print("Done")
