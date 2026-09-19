package tech.qiantong.qknow.hermes.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.hitl.model.WorkflowForkReceipt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 113: 密码学工作流时空分叉存证凭单契约测试套件
 * 验证 Java 21 Record 格式、SHA-256 自签名与防篡改安全性
 */
public class Phase113WorkflowForkReceiptTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("测试用例 1: 分叉凭单成功创建并自签名校验通过")
    void testForkReceiptCreationAndVerification() {
        WorkflowForkReceipt receipt = WorkflowForkReceipt.create(
                "STU_parent_receipt_001",
                "wf_production_approval",
                7,
                "batch_fork_20260920_001",
                "hash_mutated_vars_123456"
        );

        assertNotNull(receipt.forkReceiptId());
        assertTrue(receipt.forkReceiptId().startsWith("FORK_"));
        assertEquals("STU_parent_receipt_001", receipt.parentReceiptId());
        assertEquals("wf_production_approval", receipt.workflowId());
        assertEquals(7, receipt.forkStepIndex());
        assertEquals("batch_fork_20260920_001", receipt.forkBatchId());
        assertEquals("hash_mutated_vars_123456", receipt.mutatedVariablesHash());
        assertNotNull(receipt.sha256Signature());
        assertEquals(64, receipt.sha256Signature().length());
        assertTrue(receipt.timestamp() > 0);

        // 验证签名
        assertTrue(receipt.verifySignature(), "合法分叉凭单自签名校验必须为 true");
    }

    @Test
    @DisplayName("测试用例 2: 字段篡改后分叉凭单自签名校验失败")
    void testTamperedForkReceiptFailsVerification() {
        WorkflowForkReceipt original = WorkflowForkReceipt.create(
                "STU_root_001",
                "wf_data_analysis",
                4,
                "batch_002",
                "hash_original_mock"
        );

        assertTrue(original.verifySignature());

        // 篡改 forkStepIndex
        WorkflowForkReceipt tamperedStep = new WorkflowForkReceipt(
                original.forkReceiptId(),
                original.parentReceiptId(),
                original.workflowId(),
                999, // 篡改
                original.forkBatchId(),
                original.mutatedVariablesHash(),
                original.sha256Signature(),
                original.timestamp()
        );
        assertFalse(tamperedStep.verifySignature(), "篡改分叉步数后验真必须失败");

        // 篡改 mutatedVariablesHash
        WorkflowForkReceipt tamperedHash = new WorkflowForkReceipt(
                original.forkReceiptId(),
                original.parentReceiptId(),
                original.workflowId(),
                original.forkStepIndex(),
                original.forkBatchId(),
                "malicious_mutated_hash", // 篡改
                original.sha256Signature(),
                original.timestamp()
        );
        assertFalse(tamperedHash.verifySignature(), "篡改修改哈希后验真必须失败");

        // 篡改 parentReceiptId
        WorkflowForkReceipt tamperedParent = new WorkflowForkReceipt(
                original.forkReceiptId(),
                "tampered_parent_receipt", // 篡改
                original.workflowId(),
                original.forkStepIndex(),
                original.forkBatchId(),
                original.mutatedVariablesHash(),
                original.sha256Signature(),
                original.timestamp()
        );
        assertFalse(tamperedParent.verifySignature(), "篡改父凭单溯源后验真必须失败");
    }

    @Test
    @DisplayName("测试用例 3: JSON 序列化与反序列化双向保真")
    void testJsonSerializationFidelity() throws Exception {
        WorkflowForkReceipt receipt = WorkflowForkReceipt.create(
                "STU_parent_888",
                "wf_finance_mesh",
                12,
                "batch_fork_999",
                "hash_abc_mock_inputs"
        );

        String json = objectMapper.writeValueAsString(receipt);
        assertNotNull(json);
        assertTrue(json.contains("STU_parent_888"));
        assertTrue(json.contains("batch_fork_999"));

        WorkflowForkReceipt deserialized = objectMapper.readValue(json, WorkflowForkReceipt.class);
        assertEquals(receipt.forkReceiptId(), deserialized.forkReceiptId());
        assertEquals(receipt.parentReceiptId(), deserialized.parentReceiptId());
        assertEquals(receipt.workflowId(), deserialized.workflowId());
        assertEquals(receipt.forkStepIndex(), deserialized.forkStepIndex());
        assertEquals(receipt.forkBatchId(), deserialized.forkBatchId());
        assertEquals(receipt.mutatedVariablesHash(), deserialized.mutatedVariablesHash());
        assertEquals(receipt.sha256Signature(), deserialized.sha256Signature());
        assertEquals(receipt.timestamp(), deserialized.timestamp());

        assertTrue(deserialized.verifySignature());
    }
}
