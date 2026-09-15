# Phase 57 实施方案：多智能体动态拓扑流形收缩、跨智能体语义压缩与分布式联邦记忆蒸馏网络

## 一、算法假设与问题边界

### 1. 唯一待验证假设 (H-PHASE57-001)
在超大规模多智能体协同网络中，通过基于谱图有效阻抗采样的拓扑流形收缩器（TopologicalManifoldContractor）、基于信息瓶颈理论与因果实体豁免的跨智能体语义压缩器（InformationBottleneckCompressor），以及集成局部差分隐私（LDP）高斯加噪与超球面投影的联邦记忆蒸馏网络（FederatedMemoryDistillationNetwork），能够在保证全网分布式协同代数连通度损失 $\le 10\%$ 的前提下，同时达成：
1. **拓扑通信边数压缩率**：通信图边数削减 $\ge 70\%$（从 $\mathcal{O}(N^2)$ 压降至 $\mathcal{O}(N \ln N)$），彻底消除广播风暴；
2. **跨智能体语义压缩率**：传输上下文 Token 压缩率 $\ge 75\%$，且关键因果实体、SQL 谓词与决策约束无损保留率严格达到 $100\%$；
3. **联邦知识蒸馏安全性与无偏性**：各智能体私有原始记忆零明文出域，聚合后的全局元记忆卡在千问 1536 维超球面流形上保持期望无偏收敛；
4. **不可篡改密码学存证**：全流程签发不可变 Java 21 Record `FederatedDistillationReceipt`，自验 SHA-256 摘要通过率 $100\%$。

---

## 二、契约定义与核心组件设计

### 1. 架构总览与交互拓扑

```
+----------------------------------------------------------------------------------------------------+
|                               FederatedDistillationCoordinator                                     |
|                                (分布式联邦记忆蒸馏总控中枢)                                         |
+----------------------------------------------------------------------------------------------------+
        |                                       |                                    |
        v                                       v                                    v
+-------------------------------+ +-------------------------------+ +--------------------------------+
|  TopologicalManifoldContractor| |InformationBottleneckCompressor| |     LocalMemoryDistiller       |
| (谱图有效阻抗采样流形收缩)    | | (信息瓶颈语义压缩与因果豁免) | |  (本地私有记忆流紧凑见解抽取)  |
+-------------------------------+ +-------------------------------+ +--------------------------------+
                                                |
                                                v
                                 +--------------------------------+
                                 |    FederatedMemoryAggregator   |
                                 | (LDP差分加噪与超球面投影聚合)  |
                                 +--------------------------------+
                                                |
                                                v
                                 +--------------------------------+
                                 |  FederatedDistillationReceipt  |
                                 |    (不可变Java 21 Record凭单)   |
                                 +--------------------------------+
```

### 2. 核心组件职责
1. **`FederatedDistillationReceipt`**：
   - 不可变 Record，记录：`receiptId`, `roundId`, `participatingAgentIds`, `originalEdgesCount`, `contractedEdgesCount`, `compressionRatio`, `globalMetaMemoryHash`, `timestamp`, `receiptHash`；
   - 提供 `verifyIntegrity()` 验证 SHA-256 自签名。
2. **`TopologicalManifoldContractor`**：
   - 输入：多智能体原始加权通信拓扑邻接矩阵或边集合；
   - 算法：基于节点度数与谱图有效阻抗重要性概率采样；
   - 输出：边数压缩 $\ge 70\%$ 的骨干子图，保留代数连通度。
3. **`InformationBottleneckCompressor`**：
   - 接收原始长文本上下文或思考链；
   - 识别关键因果实体（如 `ACTION:`, `SQL:`, `RESULT:`, `STATUS:` 等关键词及特定数值、谓词），打上保真豁免标签；
   - 对自然语言修饰语进行信息熵剪枝，输出紧凑语义骨架，压缩率 $\ge 75\%$。
4. **`LocalMemoryDistiller`**：
   - 智能体在本地针对公共参考锚点集合 $\mathcal{Q}_{pub}$，基于本地长程记忆进行条件反射打分，生成本地认知见解向量（千问 1536 维超球面向量）。
5. **`FederatedMemoryAggregator`**：
   - 收集各智能体上传的本地向量，注入 $(\epsilon, \delta)$-LDP 局部差分隐私高斯噪声；
   - 执行加权平均聚合并重新执行超球面保模归一化（$\Pi_{\mathbb{S}^{1535}}$）；
   - 输出全局元记忆卡 `FederatedMetaMemoryCard`。
6. **`FederatedDistillationCoordinator`**：
   - 编排入口：拓扑流形收缩 -> 语义压缩广播 -> 各节点本地记忆蒸馏 -> 差分聚合 -> 签发不可变存证凭单。

---

## 三、比较方案 (Baseline vs Candidate)

| 维度 | Baseline (全量广播集中式) | 最小诊断方案 (静态规则稀疏化) | 候选方案 (Candidate Phase 57) |
| :--- | :--- | :--- | :--- |
| **拓扑控制** | 全连接 $\mathcal{O}(N^2)$ 通信广播，极易流量风暴 | 随机丢弃边或固定星型拓扑，易断裂 | **基于谱图有效阻抗采样流形收缩，边数压缩 $\ge 70\%$ 且代数连通度保真** |
| **上下文传输** | 原始长文本无压缩直接传输，容易 Token OOM | 简单前后字符截断，丢失核心因果实体 | **信息瓶颈理论语义骨架压缩，因果实体 100% 豁免且压缩率 $\ge 75\%$** |
| **知识聚合** | 明文集中上传记忆，严重违反 GDPR 隐私 | 不加噪简单平均，容易遭受差分反演重构 | **公共锚点蒸馏 + 局部差分隐私 (LDP) 加噪 + 千问 1536 维超球面保模聚合** |
| **审计存证** | 文本日志打印，无法溯源各方签名 | 数据库异步记录，无密码学签名 | **不可变 Java 21 Record + SHA-256 密码学防篡改凭单** |

---

## 四、反事实消融设计与泄漏防护

1. **消融实验 A（关闭因果实体豁免）**：
   - 若关闭因果实体硬保留，关键业务谓词（如 `STATUS=COMMITTED`）可能在压缩时被误剪枝，导致下游执行状态机错乱；开启豁免后，因果实体 100% 无损保真。
2. **消融实验 B（关闭差分隐私高斯加噪）**：
   - 恶意攻击者利用两轮聚合差异可反推出某特定智能体的私有偏好；开启 LDP 加噪后，单智能体输出互信息被严格限制在 $\epsilon$ 以内。
3. **泄漏防护**：
   - 公共参考锚点集为固定的公开无害意图集合，严禁反向渗漏智能体私有查询上下文。

---

## 五、预算与性能指标约束

- **拓扑收缩耗时**：百节点通信图收缩计算耗时 $\le 10.0\,\text{ms}$；
- **语义压缩耗时**：单段长上下文信息瓶颈剪枝耗时 $\le 5.0\,\text{ms}$；
- **联邦聚合耗时**：百级 1536 维向量加噪聚合与重投影耗时 $\le 10.0\,\text{ms}$；
- **总控调度代数耗时**：端到端协调流程代数开销 $\le 25.0\,\text{ms}$。

---

## 六、最小实现文件集合与禁止修改边界

### 1. 新增核心文件（`backend/qknow-framework/qknow-ai`）
- `tech.qiantong.qknow.ai.federated.FederatedDistillationReceipt.java`
- `tech.qiantong.qknow.ai.federated.TopologicalManifoldContractor.java`
- `tech.qiantong.qknow.ai.federated.InformationBottleneckCompressor.java`
- `tech.qiantong.qknow.ai.federated.LocalMemoryDistiller.java`
- `tech.qiantong.qknow.ai.federated.FederatedMemoryAggregator.java`
- `tech.qiantong.qknow.ai.federated.FederatedDistillationCoordinator.java`

### 2. 新增契约测试文件（`backend/tests`）
- `tech.qiantong.qknow.ai.federated.Phase57FederatedExecutionContractTest.java`

### 3. 禁止修改边界
- 严禁修改其他子模块生产表结构及已稳定的 Phase 01~56 既有核心业务代码；
- 严禁引入未经验证的第三方重量级依赖；
- 严格遵循 Java 21 隔离环境命令前缀运行。

---

## 七、验证命令与预期结果

1. **局部编译命令**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn compiler:compile jar:jar install:install -pl qknow-framework/qknow-ai -DskipTests
   ```
2. **Phase 57 专属契约单测验证**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests -Dtest=Phase57FederatedExecutionContractTest
   ```
   预期结果：8 项严苛单测全部通过（8/8 全绿，0 失败 0 错误）。
3. **全库全量防退化回归测试**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests
   ```
   预期结果：全库回归测试突破 **1098/1098 100% 全绿**（0 失败 0 错误）。
4. **前端生产构建检验**：
   ```bash
   cd frontend && npm run build:prod
   ```
   预期结果：0 错误 0 警告极速通过。
