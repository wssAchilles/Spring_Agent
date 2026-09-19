package tech.qiantong.qknow.hermes.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.hitl.model.WorkflowStudioReceipt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 112: 密码学工作流 Studio 存证凭单契约测试套件
 * 验证 Java 21 Record 格式、SHA-256 自签名与防篡改安全性
 */
public class Phase112WorkflowStudioReceiptTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("测试用例 1: 凭单成功创建并自签名验证通过")
    void testReceiptCreationAndSignatureVerification() {
        WorkflowStudioReceipt receipt = WorkflowStudioReceipt.create(
                "wf_main_flow_01",
                42,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "top_hash_9876543210abcdef",
                "batch_debug_20260919_001",
                15
        );

        assertNotNull(receipt.receiptId());
        assertTrue(receipt.receiptId().startsWith("STU_"));
        assertEquals("wf_main_flow_01", receipt.workflowId());
        assertEquals(42, receipt.epochVersion());
        assertEquals(15, receipt.stepCount());
        assertNotNull(receipt.sha256Signature());
        assertEquals(64, receipt.sha256Signature().length(), "SHA-256 签名十六进制长度必须为 64");
        assertTrue(receipt.timestamp() > 0);

        // 验证签名
        assertTrue(receipt.verifySignature(), "合法创建的凭单自签名校验必须为 true");
    }

    @Test
    @DisplayName("测试用例 2: 字段篡改后自签名校验失败")
    void testTamperedReceiptFailsVerification() {
        WorkflowStudioReceipt original = WorkflowStudioReceipt.create(
                "wf_finance_approval",
                1,
                "dsl_hash_abc",
                "top_hash_def",
                "batch_001",
                5
        );

        // 验证原始凭单合法
        assertTrue(original.verifySignature());

        // 篡改 epochVersion
        WorkflowStudioReceipt tamperedEpoch = new WorkflowStudioReceipt(
                original.receiptId(),
                original.workflowId(),
                999, // 篡改
                original.dslSha256(),
                original.topologyHash(),
                original.debugBatchId(),
                original.stepCount(),
                original.sha256Signature(),
                original.timestamp()
        );
        assertFalse(tamperedEpoch.verifySignature(), "篡改纪元版本号后验真必须失败");

        // 篡改 dslSha256
        WorkflowStudioReceipt tamperedDsl = new WorkflowStudioReceipt(
                original.receiptId(),
                original.workflowId(),
                original.epochVersion(),
                "malicious_dsl_hash", // 篡改
                original.topologyHash(),
                original.debugBatchId(),
                original.stepCount(),
                original.sha256Signature(),
                original.timestamp()
        );
        assertFalse(tamperedDsl.verifySignature(), "篡改 DSL 哈希后验真必须失败");

        // 篡改 stepCount
        WorkflowStudioReceipt tamperedSteps = new WorkflowStudioReceipt(
                original.receiptId(),
                original.workflowId(),
                original.epochVersion(),
                original.dslSha256(),
                original.topologyHash(),
                original.debugBatchId(),
                999, // 篡改
                original.sha256Signature(),
                original.timestamp()
        );
        assertFalse(tamperedSteps.verifySignature(), "篡改执行步数后验真必须失败");
    }

    @Test
    @DisplayName("测试用例 3: JSON 序列化与反序列化双向保真")
    void testJsonSerializationFidelity() throws Exception {
        WorkflowStudioReceipt receipt = WorkflowStudioReceipt.create(
                "wf_multi_agent_mesh",
                10,
                "hash_abc123",
                "topo_xyz789",
                "batch_dbg_888",
                20
        );

        String json = objectMapper.writeValueAsString(receipt);
        assertNotNull(json);
        assertTrue(json.contains("wf_multi_agent_mesh"));
        assertTrue(json.contains("batch_dbg_888"));

        WorkflowStudioReceipt deserialized = objectMapper.readValue(json, WorkflowStudioReceipt.class);
        assertEquals(receipt.receiptId(), deserialized.receiptId());
        assertEquals(receipt.workflowId(), deserialized.workflowId());
        assertEquals(receipt.epochVersion(), deserialized.epochVersion());
        assertEquals(receipt.dslSha256(), deserialized.dslSha256());
        assertEquals(receipt.topologyHash(), deserialized.topologyHash());
        assertEquals(receipt.debugBatchId(), deserialized.debugBatchId());
        assertEquals(receipt.stepCount(), deserialized.stepCount());
        assertEquals(receipt.sha256Signature(), deserialized.sha256Signature());
        assertEquals(receipt.timestamp(), deserialized.timestamp());

        // 反序列化对象自签名仍旧有效
        assertTrue(deserialized.verifySignature());
    }
}
