import json
import subprocess
import os

def run_batch(file, commands):
    print(f"Applying to {file}...")
    json_path = f"{file}.batch.json"
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(commands, f, ensure_ascii=False)

    cmd = ["officecli", "batch", file, "--input", json_path, "--force"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"Error on {file}: {res.stderr}")
    else:
        print(f"Success on {file}")

    if os.path.exists(json_path):
        os.remove(json_path)

def add_p(path, text):
    return {
        "command": "add",
        "parent": path,
        "type": "paragraph",
        "props": {
            "text": text,
            "size": "12pt",
            "font.ea": "宋体",
            "font.latin": "Times New Roman",
            "lineSpacing": "1.5x"
        }
    }

# 7. 报告 1 & 8. 报告 2
def get_report_cmds():
    return [
        add_p("/body/tbl[1]/tr[5]/tc[1]", "许子祺：全栈核心开发，负责 Spring Boot 3 架构搭建，JNI 算法优化集成，以及 Vue 3 前端的可视化编排。"),
        add_p("/body/tbl[1]/tr[6]/tc[1]", "传统 RAG 系统在处理全局宏观知识时存在局限，且纯 Java 向量计算在遇到海量检索时容易遭遇性能瓶颈。"),
        add_p("/body/tbl[1]/tr[7]/tc[1]", "Java 17, Spring Boot 3, Vue 3, Vite, PostgreSQL (pgvector), Neo4j, Rust。"),
        add_p("/body/tbl[1]/tr[8]/tc[1]", "研发基于语义断层与父子块的高级数据摄入流水线，实现 JNI 加速的重排打分算法，集成基于 Neo4j 的 GraphRAG。"),
        add_p("/body/tbl[1]/tr[9]/tc[1]", "L1/L2 双级语义缓存与 JNI 内存降维打击加速。纯手工实现的 PPR 图遍历算法与时序指数衰减机制。"),
        add_p("/body/tbl[1]/tr[10]/tc[1]", "采用单体模块化后端架构分离接口与业务逻辑，利用 CompletableFuture 实现并发 Map-Reduce，前端利用 SSE 接收流式响应。")
    ]
run_batch("6-实训报告-项目1.docx", get_report_cmds())
run_batch("8-实训报告-项目2.docx", get_report_cmds())

print("All done!")
