# Phase 99 实施详案：复杂业务 Agent 跨组织动态联盟博弈、信贷流形代数清算与跨自治域协同治理中枢
## Implementation Plan for Phase 99

---

### 一、战役背景与定位

Phase 99 严格遵守《业务定位与领域边界铁律（铁律九）》中**支柱一（复杂业务 Agent 认知与编排）**与**支柱三（高保真 RAG 知识引擎与多模态图谱）**，重点解决跨企业、跨租户与跨自治域的 Agent 合作博弈破裂、搭便车吸血与主权越权风险。

---

### 二、唯一核心待验证假设 (`H-PHASE99-001`)

1. **跨组织动态联盟形成引擎 (`DynamicCoalitionFormationEngine`)**：
   - 基于合作博弈特征函数 $v(S)$ 与贪心核心解检验，单步联盟构建与重组耗时严格 $\le 60\mu\text{s}$；
   - 联盟满足超可加性 $v(S_1 \cup S_2) \ge v(S_1) + v(S_2)$，核心解空集规避率 $100.0\%$，联盟裂解背叛概率恒为 $0.0\%$；
2. **双层沙普利-纳什信贷清算流形引擎 (`BilevelCreditSettlementManifold`)**：
   - 融合阿里千问 1536 维超球面意图与边际贡献投影，单步清算计算耗时严格 $\le 50\mu\text{s}$；
   - 信贷分配严格守恒（$\sum x_i = v(N)$，误差 $\le 10^{-6}$），搭便车与零贡献节点信贷严格归零 $\phi_{\text{dummy}} \equiv 0.0$；
3. **跨自治域主权安全治理门禁 (`CrossDomainSovereignGovernanceGate`)**：
   - 基于相对阶 $r=2$ 离散主权控制屏障函数 (Sovereign CBF) 与滑动 Nonce 防重放窗口，跨租户越权写操作与恶意欺诈拦截率 $100.0\%$；
   - 单步审计与安全投影耗时严格 $\le 30\mu\text{s}$；
4. **1000Hz 4096 槽位 Disruptor 无锁跨域总线 (`CrossDomainCoalitionControlBus`)**：
   - 写入延迟 $\le 50\text{ns}$，JitterGuard 连续 3 帧时钟抖动（>2ms）瞬切 `STATUS_DEGRADED_BUFFERED` 缓冲软着陆；
   - 不可变存证凭单 (`CoalitionSettlementReceipt`) SHA-256 自签名验真通过率 $100.0\%$。

---

### 三、系统架构与数据流向

```
跨组织任务意图 / 跨租户协作请求
   │
   ▼
[DynamicCoalitionFormationEngine] ──> 校验超可加性 & 核心解 C(v) 非空 ──> 构建/重组最优子联盟
   │
   ▼
[BilevelCreditSettlementManifold] ──> 阿里千问 1536维流形嵌入 ──> Pearl 因果反事实边际增益 ──> 沙普利守恒清算 (搭便车归零)
   │
   ▼
[CrossDomainSovereignGovernanceGate] ──> 时间戳窗口 & 滑动 Nonce 防重放 ──> 相对阶 r=2 Sovereign CBF ──> 闭式 QP 安全投影修补 / 越权拦截
   │
   ▼
[CrossDomainCoalitionControlBus] ──> 1000Hz 定长 4096 槽位 Disruptor 无锁队列 ──> JitterGuard 抖动监测 ──> 签发不可变存证凭单
```

---

### 四、最小实现文件清单与代码路径

#### 1. 核心数据传输对象 (DTOs)
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/coalition/dto/OrganizationDomain.java`（组织自治域枚举：PRIMARY_ENTERPRISE, SUPPLY_CHAIN_PARTNER, FINANCIAL_INSTITUTION, EXTERNAL_REGULATOR）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/coalition/dto/CoalitionMemberAgent.java`（联盟成员智能体 Record，严格校验千问 1536 维超球面单位向量）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/coalition/dto/DynamicCoalitionStructure.java`（动态联盟拓扑结构 Record）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/coalition/dto/BilevelSettlementScheme.java`（双层信贷清算方案 Record）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/coalition/dto/CrossDomainTransactionProposal.java`（跨域事务提案 Record）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/coalition/dto/CrossDomainAuditResolution.java`（跨域审计裁决结果 Record）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/coalition/dto/CoalitionEventFrame.java`（Disruptor 事件单帧 Record）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/coalition/dto/CoalitionSettlementReceipt.java`（不可变密码学执行凭单 Record）

#### 2. 核心执行引擎 (Engines)
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/coalition/engine/DynamicCoalitionFormationEngine.java`（跨组织动态联盟形成引擎）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/coalition/engine/BilevelCreditSettlementManifold.java`（双层沙普利-纳什信贷清算流形引擎）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/coalition/engine/CrossDomainSovereignGovernanceGate.java`（跨自治域主权安全治理门禁）
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/coalition/engine/CrossDomainCoalitionControlBus.java`（1000Hz 4096 槽位 Disruptor 无锁跨域总线）

#### 3. 专属契约测试类
- `backend/tests/src/test/java/tech/qiantong/qknow/hermes/coalition/Phase99CrossDomainCoalitionContractTest.java`（8 项严苛契约单测）

---

### 五、专属契约测试用例设计 (8 项)

1. `test01_DynamicCoalitionFormation_SuperadditivityAndCoreStability`：验证特征函数超可加性与大联盟核心解非空稳定性
2. `test02_DynamicCoalitionFormation_MicrosecondPerformance`：验证单步联盟构建与重组耗时 $\le 60\mu\text{s}$
3. `test03_BilevelSettlement_AxiomaticConservationAndParetoOptimality`：验证沙普利清算全局守恒（残差 $\le 10^{-6}$）与纳什议价帕累托前沿
4. `test04_BilevelSettlement_FreeRiderNullification`：验证搭便车无贡献节点信贷严格归零（$\phi_{\text{dummy}} = 0.0$）
5. `test05_BilevelSettlement_StrictDimensionAndNormalizationGuards`：验证千问 1536 维超球面单位向量（$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$）与非法输入拦截
6. `test06_CrossDomainSovereignGovernance_RelativeDegree2CBFSafetyVeto`：验证跨域越权写与资金超支 100% 物理硬拦截
7. `test07_CrossDomainSovereignGovernance_CounterfactualSafetyProjection`：验证合法跨域动作微秒级闭式 QP 安全投影修补与放行
8. `test08_CrossDomainControlBus_DisruptorThroughputAndJitterGuard`：验证 1000Hz 无锁总线非阻塞写入与 JitterGuard 连续 3 帧抖动切入缓冲软着陆

---

### 六、边界与纪律约束

1. **零破坏已有功能**：Phase 01~98 既有代码与测试保持 100% 零修改，全库回归确保突破 1448 项全绿；
2. **唯一模型基线**：生成模型唯一 DeepSeek API，向量模型唯一阿里千问 1536 维超球面归一化，全系统无本地大模型；
3. **Java 21 隔离环境**：编译与测试必须显式带有 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`；
4. **纯简体中文输出**：文档、注释与提示全简体中文。
