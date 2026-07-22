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

# 1. 周报 2
c2 = [
    add_p("/body/tbl[1]/tr[2]/tc[2]", "深入学习了 MyBatis-Plus 的动态 SQL 机制与多数据源配置。完成了对 PostgreSQL 数据库的初步压测，利用 JMeter 模拟高并发下的连接池表现，验证了 HikariCP 在单体应用下的极致性能。"),
    add_p("/body/tbl[1]/tr[3]/tc[2]", "意识到基础架构的健壮性决定了上层业务的稳定性，规范化了全局异常处理与统一响应体封装，减少了前后端对接的沟通成本。")
]
run_batch("1-第2周报.docx", c2)

# 2. 周报 4
c4 = [
    add_p("/body/tbl[1]/tr[2]/tc[2]", "探索了 OpenAI API 与 Ollama 本地模型的联合部署方案。为保证 Doc2QA 的问题提取质量，耗费了大量时间进行 Prompt Engineering，通过 Few-shot 示例有效降低了生成过程中的格式错乱问题。"),
    add_p("/body/tbl[1]/tr[3]/tc[2]", "大模型并非万能，过度依赖往往会导致响应过慢或出现幻觉，唯有通过精细化的工程手段进行干预，才能将 AI 能力真正落地。")
]
run_batch("2-第4周报.docx", c4)

# 3. 周报 6
c6 = [
    add_p("/body/tbl[1]/tr[2]/tc[2]", "研读了 Rust FFI (Foreign Function Interface) 文档，处理了 Java 与原生 C 库跨界调用过程中的内存泄漏隐患。在重排阶段，补充实现了 BGE-M3 模型的稀疏向量权重计算，使得混合检索更加精准。"),
    add_p("/body/tbl[1]/tr[3]/tc[2]", "跨语言的系统集成极大地拓宽了我的视野，学会了如何在不同技术栈之间取长补短，用 Rust 的安全性能弥补 Java 的性能短板。")
]
run_batch("3-第6周报.docx", c6)

# 4. 周报 8
c8 = [
    add_p("/body/tbl[1]/tr[2]/tc[2]", "学习了 Vue3 的 Composition API 和 Pinia 状态管理模式，在前端实现了细粒度的 JWT 无感刷新机制。配合 Axios 拦截器，保证了复杂智能体图谱编排画布在断网或超时的容错体验。"),
    add_p("/body/tbl[1]/tr[3]/tc[2]", "前端不仅仅是页面的展示，更是用户体验的最后一道防线。一个优秀的可视化系统必须具备高容错率与优秀的防抖降级策略。")
]
run_batch("4-第8周报.docx", c8)

# 5. 任务书1
t1 = [
    add_p("/body/tbl[1]/tr[2]/tc[1]", "结合企业级安全规范，设计完备的基于 RBAC 的权限管控体系。并引入 Redis 实现分布式锁，防止极端情况下的知识库并发更新导致数据错乱。"),
    add_p("/body/tbl[1]/tr[4]/tc[1]", "确保核心业务接口 P99 延迟在 200ms 以内，系统底座需支持十万级以上长文本段落的稳定摄入与检索。"),
]
run_batch("5-实训任务书-项目1.docx", t1)

# 6. 任务书2
t2 = [
    add_p("/body/tbl[1]/tr[2]/tc[1]", "完成基于图数据库的实体对齐与关系消歧模块设计。引入多模态文件上传能力，为未来分析包含图表的复杂科研论文留出扩展接口。"),
    add_p("/body/tbl[1]/tr[4]/tc[1]", "实现直观的可视化操作面板，使得非技术人员也能轻松定义复杂多智能体的工作流流转规则与异常重试策略。"),
]
run_batch("7-实训任务书-项目2.docx", t2)

# 7. 报告 1 & 8. 报告 2
def get_report_cmds():
    return [
        add_p("/body/tbl[1]/tr[5]/tc[1]", "负责测试环节的设计，包括编写 JUnit 单元测试和集成测试，监控 Redis 内存与数据库 QPS 表现。"),
        add_p("/body/tbl[1]/tr[6]/tc[1]", "当前多数通用大模型面临严重的“知识截断”和“数据隐私泄漏”挑战。企业大量私密长文本无法直接上云。且开源的 RAG 方案通常止步于向量粗筛，在面对高度结构化的科研文献时极易导致回答支离破碎。"),
        add_p("/body/tbl[1]/tr[7]/tc[1]", "后端基于 JDK 17 (开启 ZGC 控制延迟)，并结合 Redis 7.0 与 MyBatis Plus；前端基于 Vue 3.4 + Node.js v20；构建工具为 Maven 3.9。"),
        add_p("/body/tbl[1]/tr[8]/tc[1]", "进一步实现了数据管道的高阶能力：支持复杂排版（PDF/Word）文件的智能提取；并且基于 WebSocket 实现了前后端长连接的实时打字机回答效果。"),
        add_p("/body/tbl[1]/tr[9]/tc[1]", "架构解耦创新：采用模块化单体结合强类型解耦，部署成本低却保留了随时拆分微服务的潜力。全量组件支持本地化私有部署，确保核心数据的绝对物理隔离。"),
        add_p("/body/tbl[1]/tr[10]/tc[1]", "前端基于 @vue-flow 构建 DAG 交互画布，并利用 Pinia 集中管理画布状态及图谱数据；后端通过统一的异常切面捕获各类大模型超时与数据库宕机风险。")
    ]
run_batch("6-实训报告-项目1.docx", get_report_cmds())
run_batch("8-实训报告-项目2.docx", get_report_cmds())

# 9. 答辩记录
ans = [
    add_p("/body/tbl[1]/tr[1]/tc[2]", "问：系统如何保证前端可视化编排画布与后端智能体运行引擎的数据结构一致性？"),
    add_p("/body/tbl[1]/tr[1]/tc[2]", "答：前后端统一约定了一套轻量级的 JSON DSL (领域特定语言) 来描述节点的连线。保存时先由后端进行拓扑排序校验图中是否存在死循环，校验通过后才进行落库；执行阶段由引擎解析该 DSL 并行派发 CompletableFuture 异步任务。")
]
run_batch("10-答辩记录.docx", ans)

print("All done!")
