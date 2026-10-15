package tech.qiantong.qknow.hermes.memory.paging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类 OS 虚拟内存分层分页模型契约测试 (VirtualMemoryPagingContractTest)
 * 验证 64-token 补齐、不可变 Record 凭单验签、高水位线递归压缩与冷备可逆回滚。
 */
class VirtualMemoryPagingContractTest {

    private static final int DIM = 1536;
    private static final String SECRET_KEY = "test_virtual_memory_paging_secret";

    @Test
    @DisplayName("验证 ContextCacheAligner 严格对齐 64-Token 边界")
    void testContextCacheAligner() {
        String raw = "【Hermes 系统安全宪章与核心业务角色定义】";
        assertFalse(ContextCacheAligner.isStrictlyAligned(raw), "初始文本非 64 整数倍");

        String aligned = ContextCacheAligner.alignTo64TokenBoundary(raw);
        assertTrue(ContextCacheAligner.isStrictlyAligned(aligned), "对齐后长度必须是 64 的整数倍");
        assertTrue(aligned.length() >= raw.length(), "对齐后长度不小于原文本");
        assertTrue(aligned.startsWith(raw), "前缀语义必须完全保持一致");
    }

    @Test
    @DisplayName("验证 MemoryPage 不可变 Record 模型与 SHA-256 签名常量时间验签")
    void testMemoryPageRecordsAndSignatures() {
        // 1. CorePage 验签
        MemoryPage.CorePage core = MemoryPage.CorePage.createAndSign(
                1, "System Core Prompt Instruction", Map.of("MAX_DEPTH", "3"), SECRET_KEY
        );
        assertTrue(core.verifySignature(SECRET_KEY), "合法密钥必须验签通过");
        assertFalse(core.verifySignature("WRONG_KEY"), "非法密钥必须验签失败");

        // 2. RecallPage 1536 维超球面向量严格约束
        float[] vector1536 = createNormalizedVector(DIM);
        MemoryPage.RecallPage recall = MemoryPage.RecallPage.createAndSign(
                2, "user", "执行企业对账核验", vector1536, Map.of("ENV", "PROD"), SECRET_KEY
        );
        assertTrue(recall.verifySignature(SECRET_KEY));
        assertEquals(1536, recall.sphericalVector().length);

        // 验证非 1536 维抛出异常
        assertThrows(IllegalArgumentException.class, () ->
                new MemoryPage.RecallPage(3, "user", "bad", new float[100], Map.of(), System.currentTimeMillis(), "fake")
        );
    }

    @Test
    @DisplayName("验证 MemoryPageController 高水位线 (75%) 递归压缩换出与三级冷备可逆回滚")
    void testControllerWatermarkAndColdStandbyRollback() {
        // 容量 10，高水位线 75% 即第 8 轮触发压缩
        MemoryPageController controller = new MemoryPageController(10, 20, SECRET_KEY);
        controller.setCoreInstructions("【企业级核心提示词】", Map.of("AUDIT_MODE", "STRICT"));

        // 初始注入 5 轮交互（未达高水位线）
        MemoryPage.RecallPage turn1 = controller.appendRecallTurn("user", "指令 1", createNormalizedVector(DIM), Map.of("STEP", "1"));
        for (int i = 2; i <= 5; i++) {
            controller.appendRecallTurn("user", "指令 " + i, createNormalizedVector(DIM), Map.of("STEP", String.valueOf(i)));
        }
        assertEquals(5, controller.getActiveCount(), "活跃回合应为 5");
        assertEquals(0, controller.getColdCount(), "冷备区应为空");
        assertNull(controller.getCompactedPage(), "未触发压缩，CompactedPage 应为空");

        // 再追加 3 轮交互（触碰高水位线 8/10）
        for (int i = 6; i <= 8; i++) {
            Map<String, String> facts = Map.of("STEP", String.valueOf(i), "ACTIVE_GOAL", "GOAL_" + i);
            controller.appendRecallTurn("assistant", "回复 " + i, createNormalizedVector(DIM), facts);
        }

        // 触碰高水位线后，自动压缩并回落至低水位线 (5 轮)
        assertEquals(5, controller.getActiveCount(), "压缩后活跃集应回落至 5 轮");
        assertTrue(controller.getColdCount() > 0, "被换出页面必须进入冷备区，严禁物理硬删除");
        assertNotNull(controller.getCompactedPage(), "必须生成增量因果历史摘要页");
        assertTrue(controller.getCompactedPage().historicalSummary().contains("指令 1"), "摘要必须覆盖早期事实");

        // 验证 Prompt 拼装结构：CorePage 位于 Token 0 起始处
        String assembledPrompt = controller.assembleAlignedPrompt("即时用户输入");
        assertTrue(assembledPrompt.startsWith("【企业级核心提示词】"), "必须保持严格的前缀冻结以命中 Cache");
        assertTrue(assembledPrompt.contains("【前序因果历史摘要】"), "必须包含因果摘要");
        assertTrue(assembledPrompt.contains("【当前即时查询】: 即时用户输入"), "即时输入必须挂在尾部");

        // 验证三级冷备一键复权回滚
        boolean rolledBack = controller.restoreFromColdBuffer(turn1.pageId());
        assertTrue(rolledBack, "必须成功从三级冷备中回滚页面 " + turn1.pageId());
        assertEquals(6, controller.getActiveCount(), "回滚后活跃页面数递增");
    }

    private static float[] createNormalizedVector(int dim) {
        float[] vec = new float[dim];
        Random rnd = new Random(42);
        double sumSq = 0.0;
        for (int i = 0; i < dim; i++) {
            vec[i] = (float) rnd.nextGaussian();
            sumSq += vec[i] * vec[i];
        }
        float norm = (float) Math.sqrt(sumSq);
        for (int i = 0; i < dim; i++) {
            vec[i] /= norm;
        }
        return vec;
    }
}
