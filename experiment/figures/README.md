# Hermes 自主智能体顶刊级科研实验图表集 (Scientific Figures Portfolio)

本目录包含严格遵循 `scientific-figure-making` 规范与 `EXPERIMENT_SKILL.md` 规程生成的全部出版级科研图表。所有图表均支持 300 DPI 高清栅格图 (PNG) 与印刷级无损矢量图 (PDF)。

---

## 图表清单与学术图注 (Captions & Descriptions)

### 1. `fig1_main_ablation_performance` (主性能与正交单变量消融柱状图)
- **图题**：Task Success Rate (Pass@1) & Valid Execution Rate (VER) across Ablations
- **学术解释**：
  - 本图直观展示了 Full Hermes（提议方案）相比 5 个消融对照变体的任务完成率与合法执行率优势；
  - 误差棒代表 10,000 次 Bootstrap 重采样的 95% 置信区间 (BCa CI)；
  - 柱顶标注了与提议方案配对假设检验的显著性标记（$^{\dagger} p < 0.05, ^{\dagger\dagger} p < 0.01, ^{\dagger\dagger\dagger} p < 0.001$）；
  - 配色遵循蓝-绿-红语义映射规范，带黑色边框与打印安全纹理 (Hatch)。

### 2. `fig2_cost_performance_pareto_frontier` (成本-成功率 Pareto 效率前沿图)
- **图题**：Cost-Performance Trade-off & Pareto Efficiency Frontier
- **学术解释**：
  - 横轴表示单任务平均消费成本（毫美元 m$），纵轴表示 Pass@1 成功率；
  - 星号标注非支配点集构成的 **Pareto 最优前沿**，连线形成效率包络面；
  - 明确表明 Full Hermes 在单位成本产出上达到最高效能，而原生 Vanilla ReAct 处于严重被支配的低效区域。

### 3. `fig3_error_taxonomy_distribution` (三层 9 类失效模式分层堆叠分布图)
- **图题**：Granular Failure Mode Attribution Across 3-Tier Taxonomy
- **学术解释**：
  - 统计了各变体在 Level 1（规划与决策失效）、Level 2（工具交互与执行失效）、Level 3（上下文与认知限制）的失误占比；
  - 证实了剥离规划会导致目标漂移（PR-01）剧增，剥离工作记忆会导致关键事实沉没（CA-02）激增。

### 4. `fig4_multi_seed_convergence_radar` (多维综合效能雷达剖面图)
- **图题**：Multi-dimensional Performance & Robustness Radar Profile
- **学术解释**：
  - 涵盖 Pass@1、VER、步数收敛效率、Token 经济性与成本综合效能五大正交维度；
  - Full Hermes 展现出最大的覆盖凸包面积，证明了其在各项指标上的全局最优性与鲁棒性。

---

*生成时间: 自动化实验全生命周期产出*  
*运行环境: 本地隔离 Python 虚拟环境 (`experiment/.venv`)*
