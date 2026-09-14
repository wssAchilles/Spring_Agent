package tech.qiantong.qknow.ai.privacy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.privacy.dp.DifferentialPrivacyScorer;
import tech.qiantong.qknow.ai.privacy.dp.PrivacyBudgetLedger;
import tech.qiantong.qknow.ai.privacy.unlearning.CascadedUnlearningEngine;
import tech.qiantong.qknow.ai.privacy.unlearning.UnlearningReceiptGenerator;
import tech.qiantong.qknow.ai.privacy.unlearning.UnlearningReceiptVO;

import java.util.List;

/**
 * 数据合规与差分隐私硬隔离总协调器 (ComplianceIsolationCoordinator)
 *
 * 统一连接：
 * 1. 差分隐私动态检索门禁 (DifferentialPrivacyScorer & PrivacyBudgetLedger)
 * 2. 异构存储六层级联注销引擎 (CascadedUnlearningEngine)
 * 3. 密码学不可篡改注销凭单签发与离线验真 (UnlearningReceiptGenerator)
 *
 * @author qknow
 */
@Slf4j
@Component
public class ComplianceIsolationCoordinator {

    private final DifferentialPrivacyScorer dpScorer;
    private final PrivacyBudgetLedger budgetLedger;
    private final CascadedUnlearningEngine unlearningEngine;
    private final UnlearningReceiptGenerator receiptGenerator;

    public ComplianceIsolationCoordinator(DifferentialPrivacyScorer dpScorer,
                                          PrivacyBudgetLedger budgetLedger,
                                          CascadedUnlearningEngine unlearningEngine,
                                          UnlearningReceiptGenerator receiptGenerator) {
        this.dpScorer = dpScorer;
        this.budgetLedger = budgetLedger;
        this.unlearningEngine = unlearningEngine;
        this.receiptGenerator = receiptGenerator;
    }

    public ComplianceIsolationCoordinator() {
        this.dpScorer = new DifferentialPrivacyScorer();
        this.budgetLedger = new PrivacyBudgetLedger();
        this.unlearningEngine = new CascadedUnlearningEngine();
        this.receiptGenerator = new UnlearningReceiptGenerator();
    }

    /**
     * 合规安全差分隐私检索打分门禁
     *
     * @param requestTenantId 发起检索的租户 ID
     * @param docTenantId     目标文档所属租户 ID (必须严格匹配)
     * @param docId           文档 ID
     * @param segmentId       切片 ID
     * @param docVector       文档向量 (1536 维)
     * @param queryVector     查询向量 (1536 维)
     * @param level           隐私保护等级
     * @return 校准后的合规检索得分；若已被注销或跨租户越权则拦截返回 -1.0
     */
    public double evaluateSecureRetrievalScore(String requestTenantId, String docTenantId,
                                              Long docId, Long segmentId,
                                              float[] docVector, float[] queryVector,
                                              DifferentialPrivacyScorer.PrivacyLevel level) {
        // 1. 多租户物理硬隔离校验
        if (requestTenantId == null || !requestTenantId.equals(docTenantId)) {
            log.warn("[ComplianceIsolation] 跨租户越权探测拦截: requestTenant={}, targetTenant={}", requestTenantId, docTenantId);
            return -1.0;
        }

        // 2. 机器遗忘墓碑状态读拦截
        if (unlearningEngine.isTombstoned(requestTenantId, docId, segmentId)) {
            log.debug("[ComplianceIsolation] 切片已注销墓碑拦截: tenant={}, docId={}, segId={}", requestTenantId, docId, segmentId);
            return -1.0;
        }

        // 3. 差分隐私预算核销与熔断校验
        budgetLedger.recordAndVerifyBudgetConsumption(requestTenantId, level);

        // 4. 超球面差分隐私加噪与无偏得分校准
        float[] perturbed = dpScorer.perturbVector(docVector, level);
        return dpScorer.scoreAndCalibrate(perturbed, queryVector, level);
    }

    /**
     * 执行用户/切片数据被遗忘权合规注销 (GDPR Article 17)
     *
     * 前台耗时 <= 20ms：
     * 1. 挂牌墓碑软删除；
     * 2. 签发 RFC 6962 密码学注销凭单；
     * 3. 提交后台 SAGA 六层级联物理擦除。
     *
     * @param tenantId   租户 ID
     * @param documentId 文档 ID
     * @param segmentIds 切片 ID 列表
     * @param reasonCode 原因代码
     * @return 具备法律效力的注销凭据
     */
    public UnlearningReceiptVO executeRightToBeForgotten(String tenantId, Long documentId,
                                                        List<Long> segmentIds, String reasonCode) {
        long start = System.currentTimeMillis();

        // 1. 提交 SAGA 异步注销任务并立即前台置墓碑
        CascadedUnlearningEngine.UnlearningTask task = unlearningEngine.submitUnlearningRequest(tenantId, documentId, segmentIds);

        // 2. 生成 RFC 6962 密码学注销凭单
        UnlearningReceiptVO receipt = receiptGenerator.generateReceipt(tenantId, documentId, segmentIds, reasonCode);

        long elapsed = System.currentTimeMillis() - start;
        log.info("[ComplianceIsolation] 成功受理注销请求: tenant={}, docId={}, taskId={}, 凭单={}, 耗时={}ms",
                tenantId, documentId, task.getTaskId(), receipt.receiptId(), elapsed);

        return receipt;
    }

    /**
     * 离线验真第三方注销凭据
     */
    public boolean verifyRevocationCertificate(UnlearningReceiptVO receipt) {
        return receiptGenerator.verifyReceipt(receipt);
    }

    public DifferentialPrivacyScorer getDpScorer() {
        return dpScorer;
    }

    public PrivacyBudgetLedger getBudgetLedger() {
        return budgetLedger;
    }

    public CascadedUnlearningEngine getUnlearningEngine() {
        return unlearningEngine;
    }

    public UnlearningReceiptGenerator getReceiptGenerator() {
        return receiptGenerator;
    }
}
