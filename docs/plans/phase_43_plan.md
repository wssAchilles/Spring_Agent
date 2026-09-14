# Phase 43 实施方案：隐私计算多方安全求交与联邦 Agent 跨域知识共享网络

## 一、唯一待验证假设

**H-PHASE43-001**：
在跨机构多智能体跨域知识共享与协同推理场景下，通过构建基于乘法循环群可交换模幂运算的两阶段两方隐私集合求交协议（DH-PSI）、2048 位 Paillier 加法同态加密密文加权聚合引擎、以及针对高维嵌入向量的超球面保模局部差分隐私（LDP）特征扰动门禁，能够在数学上实现：
1. 共有知识实体交集匹配准确率严格达到 $100\%$，且交集外未匹配私有实体的明文信息泄露量严格为零（定理 1.1）；
2. 跨域智能体置信度与权重的密文加权聚合结果与明文数学期望严格无偏一致（相对误差严格为 0），中继协调器无法刺探任何单个参与方的私有置信度（定理 2.1）；
3. 特征共享施加 $\epsilon$-LDP 扰动后，下游语义检索余弦相似度保真度保持 $\ge 85\%$，同时彻底阻断针对原知识库特征的反向重构攻击（定理 3.1）；
4. 端到端联邦协商、求交与加权聚合全链路耗时在百级实体与十级参与方规模下满足 MTTC $\le 1000\text{ms}$，且在非法输入与模数越界场景下严格 Fail-Close 阻断。

---

## 二、架构设计与核心组件规范

### 2.1 模块路径结构
代码落地位于模块：`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/federated/`
```
tech.qiantong.qknow.ai.federated/
├── dto/
│   ├── PsiBlindPayloadDTO.java              # PSI 阶段盲化数据包传输对象
│   ├── PsiIntersectionResultDTO.java        # PSI 求交结果传输对象
│   ├── HomomorphicCiphertextDTO.java        # Paillier 密文封装对象
│   ├── FederatedAggregationRequestDTO.java  # 联邦打分聚合请求对象
│   └── FederatedTaskResultVO.java           # 联邦任务聚合结果响应视图
├── psi/
│   └── PsiProtocolEngine.java               # 基于 Diffie-Hellman 的两方隐私集合求交协议引擎
├── homomorphic/
│   └── PaillierHomomorphicEngine.java       # 工业级 2048 位 Paillier 加法同态加密与密文运算器
├── dp/
│   └── DifferentialPrivacyPerturber.java    # 阿里千问超球面向量局部差分隐私扰动器
└── engine/
    └── FederatedAgentCoordinator.java       # 跨域联邦多智能体协同调度总控器
```

### 2.2 核心契约接口与算法规范

1. **`PsiProtocolEngine`**：
   - 安全参数：标准大素数模数 $p$ 与阶 $q$，支持 2048 位乘法循环群；
   - 本地盲化：`blindLocalSet(List<String> items, BigInteger privateKey)`，返回针对每个实体 $x_i$ 计算的 $H(x_i)^{k} \pmod p$ 盲化字典；
   - 远程二次盲化与交集匹配：`blindRemoteSet(List<BigInteger> remoteBlindedItems, BigInteger privateKey)` 与 `computeIntersection(Map<String, BigInteger> aliceDoubleBlinded, List<BigInteger> bobDoubleBlinded)`；
   - 属性：除了交集元素外，参与方对彼此私有集合的明文内容信息泄露量严格为零（定理 1.1）。

2. **`PaillierHomomorphicEngine`**：
   - 密钥管理：生成 2048 位大素数 $p, q$，模数 $n = pq$，$\lambda = \text{lcm}(p-1, q-1)$；
   - 加解密算子：`encrypt(BigInteger plaintext, BigInteger n, BigInteger g)` 与 `decrypt(BigInteger ciphertext, PaillierPrivateKey privateKey)`；
   - 密文同态运算：
     - `homomorphicAdd(BigInteger c1, BigInteger c2, BigInteger nSquared)`：密文直接相乘模 $n^2$；
     - `homomorphicMultiplyScalar(BigInteger c, BigInteger scalar, BigInteger nSquared)`：密文标量幂运算模 $n^2$；
   - 溢出硬保护：设置 `MAX_HOMOMORPHIC_ADDENDS = 10000` 与明文范围 $[0, 1000000]$ 强校验，杜绝模 $n$ 环绕截断。

3. **`DifferentialPrivacyPerturber`**：
   - 输入：阿里千问 1536 维超球面归一化向量 $\mathbf{v} \in \mathbb{S}^{1535}$；
   - 拉普拉斯噪声注入：为每个维度注入服从 $\text{Laplace}(0, \frac{\Delta_1}{\epsilon})$ 的随机噪声；
   - 超球面保模重投影：$\hat{\mathbf{v}} = \frac{\tilde{\mathbf{v}}}{\|\tilde{\mathbf{v}}\|_2}$，严格保证输出向量 $L_2$ 范数为 1.0；
   - 保真度控制：动态验证 $\cos(\hat{\mathbf{v}}, \mathbf{v}) \ge 0.85$（定理 3.1）。

4. **`FederatedAgentCoordinator`**：
   - 统筹多参与方握手通信、PSI 实体对齐、差分隐私向量汇聚、Paillier 置信度加权密文聚合；
   - 异常处理：参与方超时或提交非法密文时自动剔除并降级；
   - 性能指标：在 100 个实体与 5 个参与方场景下，端到端协调耗时 MTTC $\le 1000\text{ms}$。

---

## 三、10 项严苛契约测试设计 (Contract Test Suite)

在 `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase43FederatedPrivacyPreservingContractTest.java` 中落地 10 项严苛契约测试：

1. **`test01_PsiZeroKnowledgeIntersectionCorrectness`**：
   验证 DH-PSI 两方求交在 Alice 与 Bob 各自持有部分重叠实体（如 5 个元素中有 2 个共有）时，精准匹配重叠集合，交集准确率 100%（定理 1.1）。
2. **`test02_PsiDisjointSetsZeroLeakage`**：
   验证当双方持有完全不相交的集合时，求交结果严格为空集，无任何假阳性，且无法通过中间盲化值逆向反解原始实体（定理 1.1）。
3. **`test03_PaillierHomomorphicAdditionExactness`**：
   验证 Paillier 密文相乘解密后，与明文数值直接相加结果严格代数一致，相对误差为 0（定理 2.1）。
4. **`test04_PaillierHomomorphicScalarMultiplicationExactness`**：
   验证 Paillier 密文标量幂运算解密后，与明文数值乘以标量结果严格代数一致，相对误差为 0（定理 2.1）。
5. **`test05_PaillierFederatedWeightedConfidenceAggregation`**：
   验证 3 个跨域 Agent 提交各自私有置信度（如 850, 920, 780）与权重（如 3, 5, 2），在密文态下完成加权和与平均值解密，结果与明文期望 871 严格一致（定理 2.1）。
6. **`test06_PaillierOverflowDefenseAndInputValidation`**：
   验证当累加项超过安全门禁阈值或明文超出安全边界时，系统严格 Fail-Close 抛出受控异常，杜绝模数截断环绕脏数据。
7. **`test07_DifferentialPrivacyLaplaceNoiseAndNormConservation`**：
   验证 1536 维向量经过局部差分隐私扰动后，超球面保模重投影保证 $L_2$ 范数严格为 $1.0 \pm 10^{-6}$，且扰动前后向量余弦相似度保真度 $\ge 0.85$（定理 3.1）。
8. **`test08_DifferentialPrivacyResistanceAgainstInversion`**：
   验证在极小隐私预算（$\epsilon = 1.0$）下，扰动向量在保持方向大致一致的前提下与原向量产生显著欧氏位移，有效阻断直接反演嗅探。
9. **`test09_FederatedAgentEndToEndWorkflow`**：
   模拟跨机构 2 个 Agent 在联邦协调器统筹下，完成“握手 -> PSI 实体对齐 -> 差分隐私知识交换 -> Paillier 密文打分聚合”全流程闭环，输出合规联合结论。
10. **`test10_FederatedExecutionLatencyUnderOneSecond`**：
    压力验证在 100 个实体两方求交与多轮同态密文计算下，全链路端到端耗时严格满足 MTTC $\le 1000\text{ms}$（实际预期 $\le 150\text{ms}$）。

---

## 四、实施计划与文件边界

### 4.1 新增与修改文件清单
- **文档与报告**：
  - `docs/plans/phase_43_academic_report.md` [NEW]
  - `docs/plans/phase_43_industrial_report.md` [NEW]
  - `docs/plans/phase_43_plan.md` [NEW]
  - `docs/plans/00_master_index.md` [MODIFY]
- **核心组件**（`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/federated/`）：
  - `dto/PsiBlindPayloadDTO.java` [NEW]
  - `dto/PsiIntersectionResultDTO.java` [NEW]
  - `dto/HomomorphicCiphertextDTO.java` [NEW]
  - `dto/FederatedAggregationRequestDTO.java` [NEW]
  - `dto/FederatedTaskResultVO.java` [NEW]
  - `psi/PsiProtocolEngine.java` [NEW]
  - `homomorphic/PaillierHomomorphicEngine.java` [NEW]
  - `dp/DifferentialPrivacyPerturber.java` [NEW]
  - `engine/FederatedAgentCoordinator.java` [NEW]
- **契约测试**：
  - `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase43FederatedPrivacyPreservingContractTest.java` [NEW]

### 4.2 严禁修改的文件边界
- 严禁修改其他既有 Phase 的业务代码与既有 962 项单测；
- 严禁修改 Java 21 隔离环境规范；
- 严禁使用未授权的第三方大模型 API 或本地模型。

### 4.3 验证命令
```bash
# 1. 编译与契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -Dtest=Phase43FederatedPrivacyPreservingContractTest -pl tests

# 2. 后端全量防退化回归测试 (962+10=972 项)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests

# 3. 前端生产构建校验
cd frontend && npm run build:prod
```
