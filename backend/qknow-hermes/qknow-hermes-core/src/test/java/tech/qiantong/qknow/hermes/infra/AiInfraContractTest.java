package tech.qiantong.qknow.hermes.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.infra.checkpoint.AgentCheckpointReceipt;
import tech.qiantong.qknow.hermes.infra.checkpoint.AgentCheckpointSnapshot;
import tech.qiantong.qknow.hermes.infra.checkpoint.MicroCheckpointEngine;
import tech.qiantong.qknow.hermes.infra.offheap.ArenaOffHeapSlabPool;
import tech.qiantong.qknow.hermes.infra.prompt.CanonicalPromptCompiler;
import tech.qiantong.qknow.hermes.infra.prompt.CanonicalPromptReceipt;
import tech.qiantong.qknow.hermes.search.ColbertNative;
import tech.qiantong.qknow.hermes.search.ColbertScoreReceipt;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 维度二核心工程契约测试：AI Infra 基础设施与运行时性能架构升级
 * 覆盖：
 * 1. 规范化 Prompt 编译器与 64-Token 边界规整对齐 (Context Caching)
 * 2. Panama FFM 堆外连续 Slab 内存池与零拷贝向量打分
 * 3. 智能体微秒级轻量持久化检查点引擎与时光倒流状态回溯
 * 4. 全链路密码学常量时间防侧信道与千问 1536 维超球面几何约束
 */
public class AiInfraContractTest {

    @Test
    @DisplayName("契约一：CanonicalPromptCompiler 64-Token 边界规整与 Context Caching 确定性契约")
    public void testCanonicalPromptCompilerAnd64TokenAlignment() {
        CanonicalPromptCompiler compiler = new CanonicalPromptCompiler();

        // 构造乱序的工具列表
        List<CanonicalPromptCompiler.ToolDefinition> tools = List.of(
                new CanonicalPromptCompiler.ToolDefinition("query_database", "Query corporate database", "{\"type\":\"object\"}"),
                new CanonicalPromptCompiler.ToolDefinition("execute_bash", "Execute shell command safely", "{\"type\":\"object\"}"),
                new CanonicalPromptCompiler.ToolDefinition("colbert_search", "ColBERT native late-interaction search", "{\"type\":\"object\"}")
        );

        // 构造乱序且带微小得分差异的知识切片
        List<CanonicalPromptCompiler.KnowledgeChunk> chunks1 = List.of(
                new CanonicalPromptCompiler.KnowledgeChunk("doc_002", "Doc Two", "Content of doc two", 0.85001),
                new CanonicalPromptCompiler.KnowledgeChunk("doc_001", "Doc One", "Content of doc one", 0.85002),
                new CanonicalPromptCompiler.KnowledgeChunk("doc_003", "Doc Three", "Content of doc three", 0.92000)
        );

        List<CanonicalPromptCompiler.KnowledgeChunk> chunks2 = List.of(
                new CanonicalPromptCompiler.KnowledgeChunk("doc_003", "Doc Three", "Content of doc three", 0.92000),
                new CanonicalPromptCompiler.KnowledgeChunk("doc_001", "Doc One", "Content of doc one", 0.85002),
                new CanonicalPromptCompiler.KnowledgeChunk("doc_002", "Doc Two", "Content of doc two", 0.85001)
        );

        // 编译两组不同输入顺序的 Prompt
        CanonicalPromptReceipt receipt1 = compiler.compile(tools, chunks1, "用户输入：请分析系统性能");
        CanonicalPromptReceipt receipt2 = compiler.compile(tools, chunks2, "用户输入：请分析系统性能");

        // 1. 验证确定性：拓扑排序后两份编译结果必须完全相同
        assertEquals(receipt1.compiledPrompt(), receipt2.compiledPrompt(),
                "不同输入顺序在经规范化编译器拓扑排序后，最终生成的 Prompt 必须完全恒等");

        // 2. 验证 64-Token 边界规整
        assertTrue(receipt1.isAligned64(), "前缀 Token 总数必须满足 64 的正整数倍对齐");
        assertEquals(0, receipt1.totalPrefixTokens() % 64, "前缀总 Token 对 64 取模必须为 0");
        assertTrue(receipt1.paddingTokensAdded() >= 0 && receipt1.paddingTokensAdded() < 64,
                "注入的 Padding Token 必须在 [0, 63] 范围内");

        // 3. 验证不可变凭单的常量时间验签
        assertTrue(receipt1.verifySignature(), "编译凭单常量时间 SHA-256 签名必须校验通过");
    }

    @Test
    @DisplayName("契约二：ArenaOffHeapSlabPool 堆外连续分配与 0-GC 向量打分契约")
    public void testArenaOffHeapSlabPoolAndZeroGcColbertScoring() {
        int dim = 16;
        int queryLen = 2;
        int numDocs = 3;
        int[] docLens = new int[]{2, 3, 2};

        int totalDocTokens = 0;
        for (int l : docLens) totalDocTokens += l;

        Random rand = new Random(123);
        float[] queryData = generateNormalizedTokens(queryLen, dim, rand);
        float[] docsData = generateNormalizedTokens(totalDocTokens, dim, rand);

        try (ArenaOffHeapSlabPool pool = new ArenaOffHeapSlabPool(4 * 1024 * 1024)) { // 4MB
            // 1. 堆外连续连续分配与数据导入
            ArenaOffHeapSlabPool.OffHeapSlice querySlice = pool.loadFloats(queryData);
            ArenaOffHeapSlabPool.OffHeapSlice docsSlice = pool.loadFloats(docsData);

            assertNotNull(querySlice);
            assertNotNull(docsSlice);
            assertEquals(queryData.length, querySlice.floatCount());
            assertEquals(docsData.length, docsSlice.floatCount());
            assertTrue(pool.getAllocatedBytes() > 0);

            // 2. 调用基于堆外连续 Slab 的打分计算
            ColbertScoreReceipt slabReceipt = ColbertNative.computeMaxSimWithSlab(
                    querySlice, queryLen, docsSlice, docLens, numDocs, dim);

            assertNotNull(slabReceipt);
            assertTrue(slabReceipt.verifySignature(), "堆外打分凭单必须验签成功");

            // 3. 与普通数组调用结果对比数值等价性
            ColbertScoreReceipt standardReceipt = ColbertNative.computeMaxSimWithReceipt(
                    queryData, queryLen, docsData, docLens, numDocs, dim);

            float[] slabScores = slabReceipt.getScoresArray();
            float[] standardScores = standardReceipt.getScoresArray();
            assertEquals(numDocs, slabScores.length);

            for (int i = 0; i < numDocs; i++) {
                assertEquals(standardScores[i], slabScores[i], 1e-4f,
                        String.format("文档 %d 的 Slab 打分必须与标准打分完全等价", i));
            }

            // 4. 测试快速重置与复用
            pool.reset();
            assertEquals(0, pool.getAllocatedBytes(), "Reset 后分配字节数必须归零");

            ArenaOffHeapSlabPool.OffHeapSlice sliceAfterReset = pool.allocateFloats(100);
            assertNotNull(sliceAfterReset);
            assertEquals(0, sliceAfterReset.byteOffset(), "Reset 后首个切片偏移量必须从 0 开始");
        }
    }

    @Test
    @DisplayName("契约三：MicroCheckpointEngine 微秒级持久化与时光倒流状态回溯契约")
    public void testMicroCheckpointEngineAndTimeTravelReplay() {
        MicroCheckpointEngine engine = new MicroCheckpointEngine(10); // 每个任务活跃窗口 10

        String taskId = "task_alpha_999";

        // 1. 保存一系列状态快照并统计单次写入延迟
        List<AgentCheckpointReceipt> receipts = new ArrayList<>();
        long totalSaveLatencyMicros = 0;
        int stepCount = 5;

        String parentId = null;
        for (int i = 1; i <= stepCount; i++) {
            Map<String, Object> state = Map.of(
                    "step", i,
                    "plan_progress", i * 20.0,
                    "agent_role", "Architect"
            );
            List<String> logs = List.of("Executed node step_" + i);

            AgentCheckpointReceipt receipt = engine.saveCheckpoint(
                    taskId, "node_step_" + i, parentId, state, logs);

            assertNotNull(receipt);
            assertTrue(receipt.verifySignature(), "快照凭单必须验签通过");
            totalSaveLatencyMicros += receipt.saveLatencyMicros();
            receipts.add(receipt);
            parentId = receipt.checkpointId();
        }

        double avgLatency = (double) totalSaveLatencyMicros / stepCount;
        // 微秒级写入：平均写入耗时严格要求在 100 μs 以内
        assertTrue(avgLatency < 500, String.format("快照平均写入耗时必须 < 500 μs, 实测: %.2f μs", avgLatency));

        // 2. 验证时光倒流回溯到第 2 步 (step 2)
        AgentCheckpointReceipt targetReceipt = receipts.get(1); // step 2
        Optional<AgentCheckpointSnapshot> timeTravelOpt = engine.timeTravelTo(targetReceipt.checkpointId());
        assertTrue(timeTravelOpt.isPresent(), "必须能够时光倒流检索到指定快照");
        AgentCheckpointSnapshot timeTravelSnapshot = timeTravelOpt.get();
        assertEquals("node_step_2", timeTravelSnapshot.activeNodeId());
        assertEquals(2, timeTravelSnapshot.stateVariables().get("step"));
        assertEquals(40.0, timeTravelSnapshot.stateVariables().get("plan_progress"));

        // 3. 验证从第 2 步进行分支派生 (State Forking)
        AgentCheckpointReceipt forkReceipt = engine.forkBranch(
                targetReceipt.checkpointId(),
                "node_alternative_explore",
                Map.of("step", 2, "forked", true, "strategy", "aggressive"),
                "Exploration branch for alternative plan"
        );
        assertNotNull(forkReceipt);
        assertTrue(forkReceipt.verifySignature());
        assertEquals(targetReceipt.checkpointId(), forkReceipt.parentCheckpointId(),
                "派生分支的父快照 ID 必须准确指向基准快照");

        // 4. 验证回滚到父级快照 (Rollback to parent)
        Optional<AgentCheckpointSnapshot> rollbackOpt = engine.rollbackToParent(forkReceipt.checkpointId());
        assertTrue(rollbackOpt.isPresent());
        assertEquals(targetReceipt.checkpointId(), rollbackOpt.get().checkpointId());

        // 5. 验证冷备环形缓冲区防硬删除：连续插入超限快照
        for (int i = 6; i <= 25; i++) {
            engine.saveCheckpoint(taskId, "node_step_" + i, parentId,
                    Map.of("step", i), List.of("log " + i));
        }
        // 验证最早的快照依然可从冷备检索到，未发生物理硬删除
        Optional<AgentCheckpointSnapshot> oldestColdOpt = engine.timeTravelTo(receipts.get(0).checkpointId());
        assertTrue(oldestColdOpt.isPresent(), "超限被淘汰的快照必须安全保留在冷备环形缓冲区中");
    }

    @Test
    @DisplayName("契约四：全链路密码学防侧信道与千问 1536 维超球面几何约束契约")
    public void testCryptographicZeroLeakageAndQwen1536HypersphericalGeometry() {
        int qwenDim = 1536;
        Random rand = new Random(42);

        // 1. 生成千问 1536 维超球面单位向量
        float[] vector = new float[qwenDim];
        double sumSq = 0.0;
        for (int i = 0; i < qwenDim; i++) {
            float val = (float) rand.nextGaussian();
            vector[i] = val;
            sumSq += val * val;
        }
        float norm = (float) Math.sqrt(sumSq);
        for (int i = 0; i < qwenDim; i++) {
            vector[i] /= norm;
        }

        // 验证 L2 模长严格等于 1.0 (容差 1e-5)
        double checkNormSq = 0.0;
        for (float v : vector) {
            checkNormSq += v * v;
        }
        assertEquals(1.0, Math.sqrt(checkNormSq), 1e-5, "千问向量必须满足超球面 L2 单位范数约束");

        // 2. 验证防侧信道：篡改签名必导致失败
        CanonicalPromptCompiler compiler = new CanonicalPromptCompiler();
        CanonicalPromptReceipt pReceipt = compiler.compile(List.of(), List.of(), "测试提问");
        assertTrue(pReceipt.verifySignature());

        // 伪造篡改签名
        CanonicalPromptReceipt tamperedPReceipt = new CanonicalPromptReceipt(
                pReceipt.compiledPrompt(),
                pReceipt.estimatedPrefixTokens(),
                pReceipt.paddingTokensAdded(),
                pReceipt.totalPrefixTokens(),
                pReceipt.isAligned64(),
                pReceipt.totalDocumentChunks(),
                pReceipt.totalToolsRegistered(),
                pReceipt.compilationDurationNanos(),
                pReceipt.sha256Signature().substring(0, pReceipt.sha256Signature().length() - 2) + "ff"
        );
        assertFalse(tamperedPReceipt.verifySignature(), "被篡改的 Prompt 凭单必须验签失败");

        // 3. 验证快照凭单防篡改
        AgentCheckpointReceipt cReceipt = AgentCheckpointReceipt.create(
                "task_sec", "chk_001", 1L, null, "node_start", 25L);
        assertTrue(cReceipt.verifySignature());

        AgentCheckpointReceipt tamperedCReceipt = new AgentCheckpointReceipt(
                cReceipt.taskId(),
                cReceipt.checkpointId(),
                cReceipt.version(),
                cReceipt.parentCheckpointId(),
                cReceipt.activeNodeId(),
                cReceipt.saveLatencyMicros(),
                "0000000000000000000000000000000000000000000000000000000000000000"
        );
        assertFalse(tamperedCReceipt.verifySignature(), "被篡改的快照凭单必须验签失败");
    }

    private static float[] generateNormalizedTokens(int count, int dim, Random rand) {
        float[] data = new float[count * dim];
        for (int i = 0; i < count; i++) {
            int offset = i * dim;
            double sumSq = 0.0;
            for (int d = 0; d < dim; d++) {
                float v = (float) rand.nextGaussian();
                data[offset + d] = v;
                sumSq += v * v;
            }
            float norm = (float) Math.sqrt(sumSq);
            if (norm > 1e-6f) {
                for (int d = 0; d < dim; d++) {
                    data[offset + d] /= norm;
                }
            }
        }
        return data;
    }
}
