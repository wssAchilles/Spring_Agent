# Phase 39 实施计划：智能体策略自博弈对抗竞技场与自动化 Elo 评测天梯 (Self-Play Adversarial Arena & Automated Elo Rating Ladder)

> **归档路径**：`docs/plans/phase_39_plan.md`  
> **实施纪律**：严格遵守 `AGENTS.md` Research-to-Implementation Gate；严格锁定唯一可证伪假设 H-PHASE39-001；在未获得用户明确批准前禁止修改业务代码；测试全绿后方可交付。  
> **依赖阶段**：Phase 24 (动态反馈与 Prompt 自进化), Phase 26 (高质量合成问答基准), Phase 31 (多智能体拜占庭容错与共识)。  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3 / `deepseek-reasoner` 即 R1）；唯一向量模型为 **阿里千问 (Qwen) Embedding**（1536 维超球面）；全系统绝无本地大模型，彻底弃用 OpenAI API；宿主环境严格使用隔离 **Java 21** (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)。

---

## 一、唯一待验证假设 (H-PHASE39-001)

在 DeepSeek API（V3 生成 / R1 链式裁判）、阿里千问 1536 维向量空间与 Java 21 隔离环境下：
1. 通过建立基于 Bradley-Terry-Luce (BTL) 概率选择模型与自适应 K-factor 的动态 Elo 积分迭代系统，能够在有界轮次 $T$ 内使策略积分收敛至真实能力潜变量的紧致邻域，满足离散鞅差分集中界限 $\mathbb{P}(|R_A^{(T)} - R_A^*| \ge \epsilon) \le 2\exp\left(-\frac{2\epsilon^2}{\sum_{t=1}^T K_t^2}\right)$；
2. 实施对偶双盲交换对称评估算子 $\mathcal{M}_{sym}(A, B) = \frac{1}{2}[\mathcal{J}(A, B) + (1 - \mathcal{J}(B, A))]$ 与结合字符级香农信息熵与关键实体覆盖度的下凸长度正则化惩罚 $\Omega(|A|, |B|)$，能够将一阶位置偏置严格代数消减至零（偏置残差 $\le 0.01$），并彻底阻断字数堆砌攻击（长度作弊胜率增益 $\le 2.0\%$）；
3. 实施基于贝叶斯不确定性采样的自适应锦标赛调度算法（优先配对 $|R_A - R_B|$ 最小的邻近活跃策略），能够使单次成对博弈的香农信息增益严格最大化（逼近理论上界 $1.0\text{ bit}$），在达到与全循环赛制相同排序置信度（Kendall\'s $\tau \ge 0.95$）的前提下，将成对比较的大模型裁判调用量从 $\mathcal{O}(N^2)$ 压降至 $\mathcal{O}(N \log N)$，API 调用成本与耗时削减 $\ge 65\%$。

---

## 二、10 项严苛契约测试设计 (Phase 39 Contract Test Suite)

在 `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase39AdversarialArenaAndEloLadderContractTest.java` 中构建 10 项严苛契约测试：

1. **Contract 1: BTL 理论胜率与期望积分单调对称性契约**
   - 验证 $E_A = \frac{1}{1 + 10^{(R_B - R_A)/400}}$ 满足柯尔莫哥洛夫公理，$E_A + E_B = 1.0$；当 $R_A = R_B$ 时 $E_A = 0.5$；当 $R_A - R_B = 400$ 时 $E_A \approx 0.909$。
2. **Contract 2: 动态自适应 K-factor 衰减与零和积分守恒契约 (Theorem 1.1)**
   - 验证对战双方积分更新满足 $\Delta R_A + \Delta R_B = 0$（严格零和）；验证随着参赛场次增加，$K$ 因子单调自适应退火收敛至 $K_{\min}$。
3. **Contract 3: 双盲匿名对局打乱与策略元信息绝对隔离契约**
   - 验证策略名称、版本、作者元数据在输入端被 SHA-256 匿名哈希化覆盖，50% 随机置换候选与基线物理展示位置，杜绝名称偏见。
4. **Contract 4: 对偶双盲交换对称打分与一阶位置偏差 100% 消除契约 (Theorem 2.1)**
   - 在候选回答 $A \equiv B$ 的对称探针实验中，单向打分可能受位置偏置影响，但经对偶双盲交换算子 $\mathcal{M}_{sym}(A, B)$ 判定，胜率严格恒等于 $0.50$（残差 $\le 0.01$）。
5. **Contract 5: 阶梯式长度偏差校准器与注水作弊惩罚契约 (Theorem 2.2)**
   - 验证当候选回答通过纯文本注水使其长度膨胀 $> 125\%$ 但有效实体信息未增加时，受到下凸对数惩罚，胜局强制降级为平局，注水胜率增益 $\le 0$。
6. **Contract 6: DeepSeek-R1 链式深度思考与结构化 JSON 裁判契约**
   - 验证裁判管道能够解析包含 `<think>` 思考链的多维评分响应，并严格输出合规的 `winner` (`A`/`B`/`TIE`) 与 `rationale` JSON。
7. **Contract 7: 自适应信息增益极大化锦标赛匹配调度契约 (Theorem 3.1)**
   - 验证调度器优先匹配战力最接近（$|R_A - R_B|$ 最小）的对局，单次对决香农信息增益 $I(A, B) \ge 0.90\text{ bit}$，样本复杂度相比全循环降低 $\ge 65\%$。
8. **Contract 8: 动态 Elo 天梯排行榜排序稳定性与防震荡契约**
   - 验证连续多轮对弈后，天梯积分收敛于稳定的能力全序，高水平策略稳居前列，积分震荡方差 $\text{Var}(R) \le 4.0$。
9. **Contract 9: 策略自动晋级双重门禁与自进化闭环流转契约**
   - 验证当且仅当候选策略满足“对战 Baseline 胜率 $\ge 60\%$ 且 $\Delta Elo \ge +30$ 分”时，状态自动流转为 `PROMOTED`；未达标策略自动标记为 `REJECTED`。
10. **Contract 10: 结构化竞技场对决审计报告导出契约**
    - 验证竞技场对局结束后，自动生成包含对战历史、思考链推导、Elo 积分变动与多维能力雷达图的结构化 Markdown 审计报告。

---

## 三、最小实现组件集合清单

1. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/eval/arena/dto/ArenaPolicyDO.java`
2. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/eval/arena/dto/ArenaMatchDO.java`
3. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/eval/arena/dto/ArenaLeaderboardDO.java`
4. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/eval/arena/PolicyRegistry.java`
5. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/eval/arena/VerbosityRegularizer.java`
6. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/eval/arena/DeepSeekR1JudgeEngine.java`
7. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/eval/arena/DynamicEloRatingLadder.java`
8. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/eval/arena/DoubleBlindMatchRunner.java`
9. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/eval/arena/PromotionGateService.java`
10. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/eval/arena/AdversarialArenaCoordinator.java`
11. `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase39AdversarialArenaAndEloLadderContractTest.java`
