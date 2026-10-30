# Hermes Harness² 阶段七（Stage 7）：架构与形式化理论审计报告（Agent A）

> **审计角色**：Agent A —— 架构与形式化审计员 (Architecture & Formal Verification Auditor)  
> **物理 ConversationId**：`04f92fd9-7dc1-41a9-acdd-ab18f7724bf6`  
> **运行 Transcript**：[transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/04f92fd9-7dc1-41a9-acdd-ab18f7724bf6/.system_generated/logs/transcript.jsonl)  
> **审计范围**：Hermes Harness² 阶段七（Stage 7：Kernel 生产桥接与 Stage 6 语义收紧）  
> **审计基线**：`feat/hermes-harness-stage6` (`4411fa2f`) 源码实现、`49-harness7-bridge-design.md`、`49-harness7-research.ledger.json`  
> **几何与运行环境约束**：阿里千问 `text-embedding-v4` 1024 维单位超球面 $\mathbb{S}^{1023} \subset \mathbb{R}^{1024}$、DeepSeek API 官方规范、Java 21 不可变 Record 契约。

---

## 一、`extrinsicCentroid` (`chordalCentroid`) 与内蕴测地线 Fréchet 均值的数学边界审查

### 1.1 `HypersphericalManifoldProjector.java`（行 62–81）源码核验与等价性证明

在 [`HypersphericalManifoldProjector.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/manifold/HypersphericalManifoldProjector.java#L62-L81) 行 62–81 中，现有方法 `frechetMean(List<double[]> vectors)` 的实现逻辑为：
1. 对输入向量集 $\{\mathbf{x}_1, \dots, \mathbf{x}_k\} \subset \mathbb{R}^{1024} \setminus \{\mathbf{0}\}$ 逐一执行单位归一化 $\mathbf{v}_i = \mathbf{x}_i / \|\mathbf{x}_i\|_2 \in \mathbb{S}^{1023}$（行 68）；
2. 在环境欧氏空间 $\mathbb{R}^{1024}$ 中计算样本算术均值（行 69–70）：
   $$\bar{\mathbf{v}} = \frac{1}{k} \sum_{i=1}^{k} \mathbf{v}_i \in \mathbb{R}^{1024}$$
3. 计算欧氏范数 $\|\bar{\mathbf{v}}\|_2$（行 73–76），并在 $\|\bar{\mathbf{v}}\|_2 < 10^{-12}$ 时抛出 `IllegalArgumentException("Antipodal centroid is undefined")`（行 77–79，对应外在均值焦集 Focal Locus $\bar{\mathbf{v}} = \mathbf{0}$）；
4. 返回归一化质心与损失值（行 80）：
   $$\boldsymbol{\mu}_{\mathrm{ext}} = \frac{\bar{\mathbf{v}}}{\|\bar{\mathbf{v}}\|_2} \in \mathbb{S}^{1023}, \qquad \mathcal{L}_{\mathrm{chord}}^* = 2\bigl(1 - \min(1.0, \|\bar{\mathbf{v}}\|_2)\bigr)$$

**定理 1.1（单位超球面外在弦质心最优性恒等式）**：  
设 $\mathcal{V} = \{\mathbf{v}_1, \dots, \mathbf{v}_k\} \subset \mathbb{S}^{1023}$ 为 1024 维单位超球面上的样本集，算术均值 $\bar{\mathbf{v}} = \frac{1}{k}\sum_{i=1}^k \mathbf{v}_i$ 满足 $\|\bar{\mathbf{v}}\|_2 > 0$。对于任意候选单位向量 $\mathbf{u} \in \mathbb{S}^{1023}$，定义平均平方弦距（Average Squared Chordal Distance）目标泛函：
$$\mathcal{L}_{\mathrm{chord}}(\mathbf{u}) \triangleq \frac{1}{k} \sum_{i=1}^{k} \|\mathbf{u} - \mathbf{v}_i\|_2^2$$
则恒有：
$$\mathcal{L}_{\mathrm{chord}}(\mathbf{u}) = 2(1 - \mathbf{u} \cdot \bar{\mathbf{v}})$$
且唯一全局极小点为外在弦质心 $\boldsymbol{\mu}_{\mathrm{ext}} = \bar{\mathbf{v}} / \|\bar{\mathbf{v}}\|_2$，对应极小值为：
$$\min_{\mathbf{u} \in \mathbb{S}^{1023}} \mathcal{L}_{\mathrm{chord}}(\mathbf{u}) = \mathcal{L}_{\mathrm{chord}}(\boldsymbol{\mu}_{\mathrm{ext}}) = 2(1 - \|\bar{\mathbf{v}}\|_2)$$

**证明**：  
由 $\mathbf{u} \in \mathbb{S}^{1023}$ 及 $\mathbf{v}_i \in \mathbb{S}^{1023}$，有 $\|\mathbf{u}\|_2^2 = 1$ 且 $\|\mathbf{v}_i\|_2^2 = 1$。展开欧氏平方范数并利用内积线性性：
$$\mathcal{L}_{\mathrm{chord}}(\mathbf{u}) = \frac{1}{k} \sum_{i=1}^{k} \bigl(\|\mathbf{u}\|_2^2 + \|\mathbf{v}_i\|_2^2 - 2 \mathbf{u} \cdot \mathbf{v}_i\bigr) = \frac{1}{k} \sum_{i=1}^{k} (2 - 2\mathbf{u} \cdot \mathbf{v}_i) = 2\left(1 - \mathbf{u} \cdot \left(\frac{1}{k}\sum_{i=1}^k \mathbf{v}_i\right)\right) = 2(1 - \mathbf{u} \cdot \bar{\mathbf{v}})$$
由 Cauchy-Schwarz 不等式，对任意 $\|\mathbf{u}\|_2 = 1$，有 $\mathbf{u} \cdot \bar{\mathbf{v}} \le \|\mathbf{u}\|_2 \|\bar{\mathbf{v}}\|_2 = \|\bar{\mathbf{v}}\|_2$，当且仅当 $\mathbf{u}$ 与 $\bar{\mathbf{v}} \neq \mathbf{0}$ 正向共线（即 $\mathbf{u} = \bar{\mathbf{v}} / \|\bar{\mathbf{v}}\|_2 = \boldsymbol{\mu}_{\mathrm{ext}}$）时取等号。代入即得最小损失为 $2(1 - \|\bar{\mathbf{v}}\|_2)$。$\blacksquare$

**与内蕴测地线 Fréchet 均值的本质区别**：  
依据 Bhattacharya & Patrangenaru (2005) 与 Rygaard et al. (2025, `arXiv:2511.04301`)，内蕴 Fréchet 均值（Intrinsic / Karcher Mean）最小化的是平均平方测地线距离：
$$\mathcal{L}_{\mathrm{geo}}(\mathbf{u}) \triangleq \frac{1}{k} \sum_{i=1}^{k} d_{\mathrm{geo}}^2(\mathbf{u}, \mathbf{v}_i) = \frac{1}{k} \sum_{i=1}^{k} \arccos^2(\mathbf{u} \cdot \mathbf{v}_i)$$
其一阶平稳条件为切空间对数映射之和 $\sum_{i=1}^k \mathrm{Log}_{\mathbf{u}}(\mathbf{v}_i) = \sum_{i=1}^k \frac{\theta_i}{\sin \theta_i}(\mathbf{v}_i - (\mathbf{u}\cdot\mathbf{v}_i)\mathbf{u}) = \mathbf{0}$。由于非线性权重因子 $\frac{\theta_i}{\sin \theta_i}$ 在样本非对称分布时依赖于 $\theta_i$，内蕴 Fréchet 均值无一般闭式解，与外在弦质心 $\boldsymbol{\mu}_{\mathrm{ext}}$ 在一般非对称点集上相差 $\mathcal{O}(\theta_{\max}^3)$ 阶高阶项。因此，源码行 62 将闭式外在弦质心命名为 `frechetMean` 存在术语拔高，必须收紧为 `extrinsicCentroid` / `chordalCentroid`。

### 1.2 局部凸球冠上的弦距-测地线双边夹逼界推导

**定理 1.2（超球面弦距与测地线距离的全局双边夹逼不等式）**：  
对任意 $\mathbf{u}, \mathbf{v} \in \mathbb{S}^{1023}$，设其测地线夹角为 $\theta = d_{\mathrm{geo}}(\mathbf{u}, \mathbf{v}) = \arccos(\mathbf{u} \cdot \mathbf{v}) \in [0, \pi]$，平方弦距为 $d_{\mathrm{chord}}^2(\mathbf{u}, \mathbf{v}) = \|\mathbf{u} - \mathbf{v}\|_2^2 = 2(1 - \cos\theta)$。则对任意 $\theta \in [0, \pi]$ 恒有：
$$d_{\mathrm{chord}}^2(\mathbf{u}, \mathbf{v}) \le d_{\mathrm{geo}}^2(\mathbf{u}, \mathbf{v}) \le \frac{\pi^2}{4} d_{\mathrm{chord}}^2(\mathbf{u}, \mathbf{v})$$
进一步，若样本集位于以 $\mathbf{u}$ 为中心、测地线半径为 $\theta_{\max} \in (0, \pi)$ 的局部凸球冠 $\mathcal{B}_{\mathrm{geo}}(\mathbf{u}, \theta_{\max})$ 内（即 $\theta_i = d_{\mathrm{geo}}(\mathbf{u}, \mathbf{v}_i) \le \theta_{\max}$），则获得更紧的局部球冠失真界：
$$\mathcal{L}_{\mathrm{chord}}(\mathbf{u}) \le \mathcal{L}_{\mathrm{geo}}(\mathbf{u}) \le \kappa(\theta_{\max}) \mathcal{L}_{\mathrm{chord}}(\mathbf{u}) \le \frac{\pi^2}{4} \mathcal{L}_{\mathrm{chord}}(\mathbf{u}), \qquad \kappa(\theta_{\max}) \triangleq \left(\frac{\theta_{\max}/2}{\sin(\theta_{\max}/2)}\right)^2 \in \left(1, \frac{\pi^2}{4}\right)$$

**证明**：  
由半角恒等式 $1 - \cos\theta = 2\sin^2(\theta/2)$，平方弦距可写为：
$$d_{\mathrm{chord}}^2(\mathbf{u}, \mathbf{v}) = 2(1 - \cos\theta) = 4\sin^2\left(\frac{\theta}{2}\right)$$
令 $x = \theta / 2 \in [0, \pi/2]$，则 $d_{\mathrm{geo}}^2(\mathbf{u}, \mathbf{v}) = \theta^2 = 4x^2$。  
在区间 $x \in (0, \pi/2]$ 上，触发 Jordan 不等式（由 $\sin x$ 在 $[0, \pi/2]$ 上的严格凹性及在原点切线斜率为 $1$ 得）：
$$\frac{2}{\pi} x \le \sin x < x \quad \Longrightarrow \quad \sin^2 x \le x^2 \le \frac{\pi^2}{4} \sin^2 x$$
两边同乘 $4$，即得：
$$4\sin^2\left(\frac{\theta}{2}\right) \le \theta^2 \le \frac{\pi^2}{4} \cdot 4\sin^2\left(\frac{\theta}{2}\right) \iff d_{\mathrm{chord}}^2(\mathbf{u}, \mathbf{v}) \le d_{\mathrm{geo}}^2(\mathbf{u}, \mathbf{v}) \le \frac{\pi^2}{4} d_{\mathrm{chord}}^2(\mathbf{u}, \mathbf{v})$$
此外，因函数 $g(x) = \frac{x}{\sin x}$ 在 $(0, \pi/2]$ 上单调递增（导数 $g'(x) = \frac{\sin x - x\cos x}{\sin^2 x} = \frac{\cos x(\tan x - x)}{\sin^2 x} > 0$），当 $\theta_i \le \theta_{\max} \le \pi$ 时有 $x_i = \theta_i/2 \le \theta_{\max}/2$，故 $d_{\mathrm{geo}}^2(\mathbf{u}, \mathbf{v}_i) \le \left(\frac{\theta_{\max}/2}{\sin(\theta_{\max}/2)}\right)^2 d_{\mathrm{chord}}^2(\mathbf{u}, \mathbf{v}_i)$。对 $i=1,\dots,k$ 求算术平均即得局部与全局夹逼界。$\blacksquare$

### 1.3 精确 API 契约与论文表述收紧规范

1. **Java 21 API 契约设计（`HypersphericalManifoldProjector`）**：
   ```java
   /**
    * 计算单位超球面 S^1023 上的外在弦质心（Extrinsic / Chordal Centroid），
    * 精确最小化平均平方弦距 1/k sum ||u - v_i||_2^2 = 2(1 - u · v_bar)。
    */
   public Centroid extrinsicCentroid(List<double[]> vectors) { ... }

   /** 外在弦质心语义等价别名。 */
   public Centroid chordalCentroid(List<double[]> vectors) {
       return extrinsicCentroid(vectors);
   }

   /**
    * @deprecated 该方法返回单位超球面 S^1023 上的外在弦质心（Extrinsic Chordal Centroid），
    * 最小化平均平方弦距而非一般内蕴测地线 Fréchet 目标；请改用 {@link #extrinsicCentroid(List)} 或 {@link #chordalCentroid(List)}。
    */
   @Deprecated(since = "stage7", forRemoval = false)
   public Centroid frechetMean(List<double[]> vectors) {
       return extrinsicCentroid(vectors);
   }

   /**
    * 计算外在弦损失对应的平均平方测地线距离上界：
    * 全局界 (pi^2 / 4) * chordalLoss；若给定局部球冠半径 maxGeodesicRadians in (0, pi]，
    * 则采用紧界 ((theta_max / 2) / sin(theta_max / 2))^2 * chordalLoss。
    */
   public double geodesicDistortionUpperBound(Centroid centroid, double maxGeodesicRadians) {
       Objects.requireNonNull(centroid, "centroid");
       if (!Double.isFinite(maxGeodesicRadians) || maxGeodesicRadians < 0.0 || maxGeodesicRadians > Math.PI) {
           throw new IllegalArgumentException("maxGeodesicRadians must be within [0, pi]");
       }
       if (maxGeodesicRadians == 0.0 || centroid.chordalLoss() == 0.0) {
           return 0.0;
       }
       double half = maxGeodesicRadians / 2.0;
       double ratio = half / Math.sin(half);
       return (ratio * ratio) * centroid.chordalLoss();
   }
   ```
2. **顶刊论文表述收紧**：
   - 将论文方法论中原本泛称的“Fréchet 均值闭式解”严格修订为：**“单位超球面 $\mathbb{S}^{1023}$ 上的外在弦质心（Extrinsic Chordal Centroid）闭式投影算子 $\boldsymbol{\mu}_{\mathrm{ext}} = \bar{\mathbf{v}}/\|\bar{\mathbf{v}}\|_2$，具备 $\mathcal{O}(kd)$ 线性复杂度，精确最小化二阶弦重构损失 $\mathcal{L}_{\mathrm{chord}}(\boldsymbol{\mu}_{\mathrm{ext}}) = 2(1 - \|\bar{\mathbf{v}}\|_2)$，并在局部凸球冠 $\mathcal{B}_{\mathrm{geo}}(\boldsymbol{\mu}_{\mathrm{ext}}, \theta_{\max})$ 上相对内蕴测地线二阶失真满足严密双边夹逼界 $\mathcal{L}_{\mathrm{chord}} \le \mathcal{L}_{\mathrm{geo}} \le \left(\frac{\theta_{\max}/2}{\sin(\theta_{\max}/2)}\right)^2 \mathcal{L}_{\mathrm{chord}} \le \frac{\pi^2}{4}\mathcal{L}_{\mathrm{chord}}$”**。

---

## 二、`ReversibleRingColdBuffer` 的 `maxArchiveCapacity` 上限与有界可逆性审查

### 2.1 源码无界增长根因核验（行 48、79–84、124–133）

对 [`ReversibleRingColdBuffer.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/manifold/ReversibleRingColdBuffer.java) 进行逐行审计，发现以下 3 处导致内存无界增长与语义失配的根因：
1. **行 48**：`private final Map<String, Stored<T>> archive = new LinkedHashMap<>();` 声明了第四级溢出归档容器，但在构造器（行 52–61）中只约束了 `hotCapacity`、`warmCapacity`、`coldCapacity`，未定义 `maxArchiveCapacity`；
2. **行 79–84**：在 `add(Episode<T>)` 中，当 `cold.size() > coldCapacity` 时，直接将 `cold` 中最老条目无条件写入 `archive.put(entry.getKey(), entry.getValue())`，且 `archive` 从不淘汰，导致在长周期运行下 `archive.size()` 随总插入数 $N \to \infty$ 线性发散（$\mathcal{O}(N)$ 堆内存泄漏）；
3. **行 124–133**：在 `softDelete(RestorationToken)` 中，从 `cold.remove(token.value())` 移除后无条件调用 `archive.put(token.value(), stored)`，同样不受任何容量上限约束；且在 `restore(RestorationToken)`（行 90–93）中，从 `cold` 或 `archive` 恢复到 `hot` 时未将条目从 `cold`/`archive` 中移除或未检查归档容量边界。

### 2.2 四元组容量不变量与有界可逆状态机形式化定义

定义四级缓冲区的容量参数四元组为：
$$\mathcal{C} = (C_{\mathrm{hot}}, C_{\mathrm{warm}}, C_{\mathrm{cold}}, C_{\mathrm{archive}}) \in \mathbb{Z}_{\ge 1}^4$$
在任意离散时刻 $t \ge 0$，设四级缓冲区的当前元素集合分别为 $H_t, W_t, K_t, A_t$，则系统必须恒满足如下**有界容量不变量（Bounded Capacity Invariant）**：
$$\mathcal{I}_{\mathrm{cap}}(t): \quad 0 \le |H_t| \le C_{\mathrm{hot}} \;\land\; 0 \le |W_t| \le C_{\mathrm{warm}} \;\land\; 0 \le |K_t| \le C_{\mathrm{cold}} \;\land\; 0 \le |A_t| \le C_{\mathrm{archive}}$$

**定理 2.1（有界可逆范围定理）**：  
在四元组容量约束 $\mathcal{C}$ 下，任意已从热区 $H_t$ 驱逐的轨迹情节 $e$ 保持**确定性无损可恢复（Losslessly Restorable via `RestorationToken`）**的充要条件是：该情节位于冷区 $K_t$ 或未满溢的归档区 $A_t$ 中，即系统总无损可逆窗口上界为：
$$|K_t \cup A_t| \le C_{\mathrm{cold}} + C_{\mathrm{archive}}$$
当累积归档请求超出 $C_{\mathrm{archive}}$ 时，系统不再承诺超出容量上界 $(C_{\mathrm{cold}} + C_{\mathrm{archive}})$ 的无限期恢复，而是通过显式拒绝计数 `rejectedArchive` 暴露精确的容量饱和审计证据。

### 2.3 溢出与软删除的精确拒绝语义及 `Statistics` 契约

1. **`Statistics` Record 精确定义**：
   ```java
   public record Statistics(
           int hot,
           int warm,
           int cold,
           int archived,
           int archivedCapacity,
           long softDeleted,
           long restored,
           long rejectedArchive
   ) {}
   ```
   - 不变量：`0 <= hot <= hotCapacity`、`0 <= warm <= warmCapacity`、`0 <= cold <= coldCapacity`、`0 <= archived <= archivedCapacity`、`rejectedArchive >= 0`。
2. **`add(Episode<T>)` 溢出至 `archive` 的拒绝语义**：
   - 当 `cold.size() > coldCapacity` 弹出 `cold` 最老条目 `entry` 时：
     - 若 `archive.size() < maxArchiveCapacity`：执行 `archive.put(entry.getKey(), entry.getValue())`；
     - 若 `archive.size() >= maxArchiveCapacity`：**严禁写入 `archive`**，丢弃该溢出归档请求并递增 `rejectedArchive++`，确保 `archive.size() <= maxArchiveCapacity` 100% 恒成立。
3. **`softDelete(RestorationToken)` 的精确非破坏性拒绝语义**：
   - 先检查 `Stored<T> candidate = cold.get(token.value())`：
     - 若 `candidate == null`，返回 `false`；
     - 若 `archive.size() >= maxArchiveCapacity`：**拒绝执行软删除迁移**，**不从 `cold` 中移除该条目**（防止因归档区满而退化为不可逆的静默硬删除，严格遵守 AGENTS.md 第七章第 5 条“严禁发生不可逆的物理硬删除”），递增 `rejectedArchive++` 并返回 `false`；
     - 若 `archive.size() < maxArchiveCapacity`：从 `cold` 中移除并写入 `archive.put(token.value(), candidate)`，递增 `softDeleted++` 并返回 `true`。
4. **预检方法**：
   - 新增 `public synchronized boolean canArchive() { return archive.size() < maxArchiveCapacity; }` 与 `public int maxArchiveCapacity() { return maxArchiveCapacity; }`。

---

## 三、`HoareTripleToolGuard` 与 `GuardrailsSpecificationRecord` 的形式化合取谓词审查

### 3.1 `HoareTripleToolGuard.java`（行 40–61）缺陷核验

核验 [`HoareTripleToolGuard.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/neurosymbolic/HoareTripleToolGuard.java#L40-L61) 行 40–61 的 `verifyBefore` 方法可见：
- 行 42 仅执行了 `if (contract == null || guardrails == null || toolName == null || arguments == null)` 的非空检查；
- 行 45–59 仅检查了针对单次工具调用静态契约 `Contract` 的三项谓词：`permittedTools`（权限）、`requiredArguments`（参数 Schema 类型）、`boundary`（参数值域谓词）；
- **`GuardrailsSpecificationRecord` 中的四个核心运行期护栏阈值（`maxCycleSteps`、`loopThreshold`、`maxTokenBudget`、`maxCostCny`）在整个类中被完全忽略（零引用）**。其根本原因在于原 `verifyBefore` 签名缺少表示当前轨迹运行期累积状态的系统状态参数 $\sigma \in \Sigma_{\mathrm{exec}}$，导致霍尔前置条件退化为纯静态参数检查 $P_{\mathrm{static}}(\text{tool}, \text{args}, C)$，丢失了运行期护栏谓词 $P_{\mathrm{guard}}(\sigma, G)$。

### 3.2 形式化霍尔前置合取谓词建模

依据 ShieldAgent (`arXiv:2503.22738`) 的动作规则 $\mathcal{R}_a$ 与系统状态规则 $\mathcal{R}_p$ 分解理论，将工具调用 $a = (\text{toolName}, \text{args})$ 在运行状态 $\sigma = (s, f, \tau, c) \in \Sigma_{\mathrm{exec}}$、护栏规范 $G = (S_{\max}, F_{\max}, T_{\max}, C_{\max})$ 与工具契约 $C$ 下的完整霍尔三元组前置条件形式化定义为有序短路合取谓词：
$$P(a, \sigma, G, C) \triangleq P_{\mathrm{nonnull}}(a, \sigma, G, C) \;\land\; P_{\mathrm{guard}}(\sigma, G) \;\land\; P_{\mathrm{contract}}(a, C)$$
其中：
1. **运行期护栏状态合取谓词 $P_{\mathrm{guard}}(\sigma, G)$**：
   $$P_{\mathrm{guard}}(\sigma, G) \triangleq \underbrace{(1 \le s \le S_{\max})}_{\text{GUARDRAIL\_STEP\_LIMIT}} \;\land\; \underbrace{(1 \le f < F_{\max})}_{\text{GUARDRAIL\_LOOP\_THRESHOLD}} \;\land\; \underbrace{(0 \le \tau \le T_{\max})}_{\text{GUARDRAIL\_TOKEN\_BUDGET}} \;\land\; \underbrace{(0 \le c \le C_{\max})}_{\text{GUARDRAIL\_COST\_BUDGET}}$$
   *(注：与 `LocallyDenseCycleAutomaton` 行 48 的 `frequency >= threshold` 严格对齐，当窗口内重复频次 $f \ge F_{\max}$ 时立即触发 `GUARDRAIL_LOOP_THRESHOLD` 拒绝。)*
2. **工具契约合取谓词 $P_{\mathrm{contract}}(a, C)$**：
   $$P_{\mathrm{contract}}(\text{tool}, \text{args}, C) \triangleq \underbrace{(\text{toolName} \in C.\text{permittedTools})}_{\text{PERMISSION}} \;\land\; \underbrace{\left(\forall (k, T_k) \in C.\text{reqArgs},\; \text{args}[k] \in T_k\right)}_{\text{SCHEMA}} \;\land\; \underbrace{C.\text{boundary}(\text{args})}_{\text{BOUNDARY}}$$

### 3.3 `Violation` 枚举、`ExecutionState` Record 与完整判定顺序设计

1. **扩展 `Violation` 枚举**：
   ```java
   public enum Violation {
       MISSING_CONTRACT,
       GUARDRAIL_STEP_LIMIT,
       GUARDRAIL_LOOP_THRESHOLD,
       GUARDRAIL_TOKEN_BUDGET,
       GUARDRAIL_COST_BUDGET,
       PERMISSION,
       SCHEMA,
       BOUNDARY,
       RESULT_INTEGRITY,
       SENSITIVE_OUTPUT
   }
   ```
2. **新增 `ExecutionState` 不可变 Record**：
   ```java
   public record ExecutionState(int currentStep, int loopFrequency, long estimatedTokens, double estimatedCostCny) {
       public ExecutionState {
           if (currentStep < 1) {
               throw new IllegalArgumentException("currentStep must be >= 1");
           }
           if (loopFrequency < 1) {
               throw new IllegalArgumentException("loopFrequency must be >= 1");
           }
           if (estimatedTokens < 0L) {
               throw new IllegalArgumentException("estimatedTokens must be >= 0");
           }
           if (!Double.isFinite(estimatedCostCny) || estimatedCostCny < 0.0D) {
               throw new IllegalArgumentException("estimatedCostCny must be finite and >= 0");
           }
       }

       public static ExecutionState initial() {
           return new ExecutionState(1, 1, 0L, 0.0D);
       }
   }
   ```
3. **`verifyBefore` 确定性短路判定顺序（9 步严格序）**：
   - **Step 1 (`MISSING_CONTRACT`)**：若 `contract == null || guardrails == null || state == null || toolName == null || arguments == null`，拒绝并返回 `MISSING_CONTRACT`；
   - **Step 2 (`GUARDRAIL_STEP_LIMIT`)**：若 `state.currentStep() > guardrails.maxCycleSteps()`，拒绝并指明 `currentStep > maxCycleSteps`；
   - **Step 3 (`GUARDRAIL_LOOP_THRESHOLD`)**：若 `state.loopFrequency() >= guardrails.loopThreshold()`，拒绝并指明 `loopFrequency >= loopThreshold`；
   - **Step 4 (`GUARDRAIL_TOKEN_BUDGET`)**：若 `state.estimatedTokens() > guardrails.maxTokenBudget()`，拒绝并指明 `estimatedTokens > maxTokenBudget`；
   - **Step 5 (`GUARDRAIL_COST_BUDGET`)**：若 `state.estimatedCostCny() > guardrails.maxCostCny()`，拒绝并指明 `estimatedCostCny > maxCostCny`；
   - **Step 6 (`PERMISSION`)**：若 `!contract.permittedTools().contains(toolName)`，拒绝并返回 `PERMISSION`；
   - **Step 7 (`SCHEMA`)**：逐项校验 `contract.requiredArguments()` 类型匹配，不匹配则返回 `SCHEMA`；
   - **Step 8 (`BOUNDARY`)**：执行 `contract.boundary().test(Map.copyOf(arguments))`（捕获 `RuntimeException`），不通过则返回 `BOUNDARY`；
   - **Step 9 (`PASS`)**：返回 `Decision.pass()`。
   - **向后兼容**：保留 4 参数 `verifyBefore(toolName, arguments, guardrails, contract)`，内部从 `arguments` 可选提取运行期元数据键（或默认构造 `ExecutionState.initial()`）后委托给 5 参数重载 `verifyBefore(toolName, arguments, guardrails, contract, state)`。

---

## 四、`HermesHarnessKernel` 的状态机与组件真实调用顺序审查

### 4.1 现有 `HermesHarnessKernel.java`（行 38–76）空壳门面问题核验

在现有 [`HermesHarnessKernel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HermesHarnessKernel.java#L38-L76) 中：
- 行 38–46 虽然实例化了 `projector`、`coldBuffer`、`toolGuard`、`cycleAutomaton`、`attributionAnalyzer`、`mutator`、`paretoComposer`、`capabilityAssembler`、`harnessAssembler` 共 9 个组件；
- 但 `prepare(...)`（行 54–62）仅调用了 `capabilityAssembler.assemble(...)` 与 `harnessAssembler.assembleSystemPrompt(...)`；
- `execute(...)`（行 64–67）仅有一行 `return Objects.requireNonNull(execution, "execution").apply(prepared);`；
- 其余 7 个核心组件仅通过行 69–75 的 getter 暴露，在 Kernel 生命周期内**调用次数为 0**。此外，`coldBuffer` 与 `cycleAutomaton` 作为 `HermesHarnessKernel` 的成员变量，若在多线程请求间直接共享会产生跨请求状态串扰。

### 4.2 真实组件编排状态机与调用全序设计

我们将 `HermesHarnessKernel` 的单次受控执行生命周期形式化为四阶段确定性有限状态自动机（DFA）：
$$\mathcal{Q}_{\mathrm{kernel}} = \{\mathtt{INIT} \xrightarrow{\text{prepare()}} \mathtt{PREPARED} \xrightarrow{\text{openSession() / invokeTool()}} \mathtt{EXECUTING\_TOOLS} \xrightarrow{\text{evaluateAndEvolve()}} \mathtt{EVALUATED\_COMPLETED}\}$$

各阶段组件真实调用链路与顺序规范如下：

1. **阶段 I：装配与流形记忆预检阶段（`prepare`）**
   - **Step I.1**：调用 `capabilityAssembler.assemble(tag, ragContexts, tools, history)`，强校验声明的 `CapabilityTag` 与传入载荷的一致性；
   - **Step I.2**：针对 `history` 中的历史消息列表（或系统初始化状态），使用 `projector` 生成确定性归一化特征向量（1024 维单位向量）并写入请求级/内核级 `coldBuffer.add(episode)`，读取 `coldBuffer.canArchive()` 与 `coldBuffer.statistics()`；
   - **Step I.3**：调用 `harnessAssembler.assembleSystemPrompt(voucher, List.copyOf(context.toolCallbacks().values()))` 生成静态前缀缓存友好的受控 `systemPrompt`；
   - **Step I.4**：生成包含 `voucher`、`fingerprint`、`systemPrompt`、`context`、`coldBufferStats` 的 `PreparedExecution`。

2. **阶段 II：神经符号工具守卫与局部致密循环拦截阶段（`invokeTool` / `wrapToolCallbacks`）**
   在 `ExecutionSession`（每次 `execute` 或 `openSession` 创建的请求级隔离会话，持有独立的 `LocallyDenseCycleAutomaton` 与步数/预算计数器）中，每次工具调用严格按以下顺序执行：
   - **Step II.1（步数递增与自动机检测）**：递增当前步数 `step = ++currentStep`，调用 `LocallyDenseCycleAutomaton.Inspection inspection = sessionAutomaton.inspect(toolName, canonicalArgs)`，获取当前窗口 $W$ 内该 `(toolName, canonicalArgs)` 指纹的局部致密频次 `inspection.frequency()`；
   - **Step II.2（构造运行期状态）**：组装 `HoareTripleToolGuard.ExecutionState state = new ExecutionState(step, inspection.frequency(), estimatedTokens, estimatedCostCny)`；
   - **Step II.3（霍尔前置校验 `verifyBefore`）**：调用 `Decision preDecision = toolGuard.verifyBefore(toolName, argsMap, prepared.voucher().guardrails(), contract, state)`；
   - **Step II.4（前置阻断判据）**：若 `!preDecision.allowed()` 或 `inspection.rejected()`，**短路阻断底层 `ToolCallback` 执行**，记录失败的 `ToolExecutionEntry(toolName, false, durationMs)` 并返回结构化反例 `Counterexample`；
   - **Step II.5（底层工具执行）**：仅当前置守卫与循环自动机同时放行时，调用底层工具获取 `rawResult`；
   - **Step II.6（霍尔后置校验 `verifyAfter`）**：调用 `Decision postDecision = toolGuard.verifyAfter(rawResult, contract)`；若 `!postDecision.allowed()`，拦截违规输出（如空结果或敏感片段泄漏），记录失败 `ToolExecutionEntry` 并返回后置反例；通过则记录成功 `ToolExecutionEntry` 并返回结果。

3. **阶段 III：轨迹归因、代数变异与帕累托前沿演化阶段（`evaluateAndEvolve`）**
   在工具调用序列完成后，通过 `ExecutionSession.evaluateTrajectory(reasoningTokens, answerTokens, ragUsed, latencyMs)` 真实串联三大演化组件：
   - **Step III.1（正交退化归因）**：构造 `TrajectoryEvidence(reasoningTokens, answerTokens, ragProvided, ragUsed, recordedToolExecutions)`，调用 `AttributionVector attribution = attributionAnalyzer.analyze(evidence)` 计算 `(reasoningExpansion, toolErrorRate, ragUnderutilization)`；
   - **Step III.2（归因驱动代数算子选择与变异）**：根据 `attribution` 选择对应的 `AlgebraicHarnessMutator.Operator`（例如：当 `toolErrorRate > 0` 且工具引导未全开时选 `TOOL_GUIDANCE_SYNTHESIS`；当 `reasoningExpansion > 1.0` 时选 `CONCISE_SYSTEM_PROMPT` 或 `GUARDRAIL_TIGHTENING`；当 `ragUnderutilization > 0.5` 时选 `ADAPTIVE_MEMORY_WINDOW`），调用 `HermesHarnessVoucher mutatedVoucher = mutator.mutate(prepared.voucher(), selectedOperator)` 生成变异候选凭单；
   - **Step III.3（帕累托非支配前沿合成）**：构造基线候选 `Candidate(voucher.voucherId(), baselineFidelity, totalTokens, latencyMs)` 与变异候选 `Candidate(mutatedVoucher.voucherId(), projectedFidelity, projectedTokens, projectedLatencyMs)`，调用 `List<ParetoFrontierComposer.Candidate> frontier = paretoComposer.frontier(candidates)` 输出非支配演化前沿。

---

## 五、审计结论汇总

| 审计项 | Stage 6 现存缺陷 | Stage 7 形式化修正判定 | 审计状态 |
|---|---|---|---|
| `HypersphericalManifoldProjector` | 将外在弦质心闭式解命名为 `frechetMean` | 重命名为 `extrinsicCentroid` / `chordalCentroid`（保留 `@Deprecated frechetMean`），新增 `geodesicDistortionUpperBound`，确证 $d_{\mathrm{chord}}^2 \le d_{\mathrm{geo}}^2 \le \frac{\pi^2}{4}d_{\mathrm{chord}}^2$ | **PASS（数学严密）** |
| `ReversibleRingColdBuffer` | `archive` 为无界 `LinkedHashMap`，可无限膨胀 | 引入 `maxArchiveCapacity` 四元组容量不变量，满额时 `add` 溢出与 `softDelete` 均拒绝入档并累加 `rejectedArchive`（严禁静默硬删除） | **PASS（有界可逆）** |
| `HoareTripleToolGuard` | `verifyBefore` 仅判空 `guardrails != null`，未读 4 个阈值 | 引入 `ExecutionState` 与 4 项 `GUARDRAIL_*` 枚举，按 9 步短路合取序强校验步数、循环频次、Token 与成本上限 | **PASS（完备可证伪）** |
| `HermesHarnessKernel` | `execute` 仅透传 `Function.apply`，7 个组件零调用 | 升级为四阶段状态机，真实串联 `capabilityAssembler` $\to$ `coldBuffer` $\to$ `cycleAutomaton` $\to$ `toolGuard` $\to$ `attributionAnalyzer` $\to$ `mutator` $\to$ `paretoComposer` | **PASS（编排闭环）** |
