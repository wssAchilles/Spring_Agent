<template>
  <div class="merkle-validator-container">
    <div class="validator-card">
      <div class="card-header">
        <div class="header-left">
          <div class="header-icon-box">
            <el-icon :size="20"><Lock /></el-icon>
          </div>
          <div class="header-meta">
            <h3>RFC 6962 密码学存证免密验真器</h3>
            <p>纯客户端浏览器 WebCrypto 硬件加速 · 零服务器信任 · 离线不可篡改审计</p>
          </div>
        </div>
        <div class="header-actions">
          <el-upload
            action=""
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleCertificateUpload"
            accept=".json"
          >
            <el-button size="small" type="primary" :icon="Upload">导入存证证书 (JSON)</el-button>
          </el-upload>
          <el-button size="small" :icon="Refresh" @click="loadSampleCertificate">载入安全样例</el-button>
          <el-button size="small" type="danger" plain @click="simulateTampering">模拟篡改测试</el-button>
        </div>
      </div>

      <!-- 凭证元数据概览卡片 -->
      <div class="cert-summary-grid" v-if="certData">
        <div class="summary-item">
          <span class="label">TRACE ID</span>
          <span class="value mono">{{ certData.traceId }}</span>
        </div>
        <div class="summary-item">
          <span class="label">叶子节点序号</span>
          <span class="value mono">#{{ certData.leafIndex }}</span>
        </div>
        <div class="summary-item">
          <span class="label">服务端预签 ROOT</span>
          <span class="value mono text-truncate" :title="certData.merkleRoot">{{ certData.merkleRoot }}</span>
        </div>
      </div>

      <!-- 动态对数级步骤折叠计算过程 -->
      <div class="steps-progress-wrapper" v-if="verificationSteps.length > 0">
        <div class="progress-title">
          <span>对数级兄弟路径逐层折叠计算 ({{ currentStepIndex }}/{{ verificationSteps.length }})</span>
          <el-button size="small" link type="primary" @click="runStepAnimation" :loading="isVerifying">
            重新回溯计算动效
          </el-button>
        </div>

        <div class="steps-list">
          <div
            v-for="(step, idx) in visibleSteps"
            :key="step.stepIndex"
            class="step-item"
          >
            <div class="step-num">L{{ idx + 1 }}</div>
            <div class="step-content">
              <div class="hash-row">
                <span class="tag">CURRENT</span>
                <span class="mono">{{ step.currentHash.substring(0, 20) }}...</span>
              </div>
              <div class="hash-row">
                <span class="tag">{{ step.isSiblingLeft ? 'SIBLING (LEFT)' : 'SIBLING (RIGHT)' }}</span>
                <span class="mono">{{ step.siblingHash.substring(0, 20) }}...</span>
              </div>
              <div class="hash-row result">
                <span class="tag formula">PARENT = SHA-256(0x01 || ...)</span>
                <span class="mono text-success">{{ step.computedParentHash.substring(0, 24) }}...</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 验真终态印章与报告导出 -->
      <div class="result-seal-container" v-if="verificationFinished">
        <div v-if="verificationSuccess" class="digital-seal verified">
          <div class="seal-inner">
            <el-icon :size="36"><CircleCheckFilled /></el-icon>
            <div class="seal-text-bold">RFC 6962 VERIFIED</div>
            <div class="seal-subtext">完整无篡改 · 密码学等价对齐</div>
          </div>
        </div>
        <div v-else class="digital-seal tampered">
          <div class="seal-inner">
            <el-icon :size="36"><CircleCloseFilled /></el-icon>
            <div class="seal-text-bold">TAMPER DETECTED</div>
            <div class="seal-subtext">根哈希破损 · 存在数据篡改风险</div>
          </div>
        </div>

        <div class="seal-actions">
          <el-button type="success" :icon="Document" @click="exportAuditReportJson">
            导出离线验真审计凭据 (JSON)
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Lock, CircleCheckFilled, CircleCloseFilled, Document, Upload, Refresh } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { verifyProofWithSteps, VerificationStep, ProofElement } from '../utils/webCryptoRfc6962';

interface MerkleProofCert {
  traceId: string;
  leafIndex: number;
  leafHash: string;
  merkleRoot: string;
  proofPath: ProofElement[];
}

const certData = ref<MerkleProofCert | null>(null);
const verificationSteps = ref<VerificationStep[]>([]);
const visibleSteps = ref<VerificationStep[]>([]);
const currentStepIndex = ref(0);
const isVerifying = ref(false);
const verificationFinished = ref(false);
const verificationSuccess = ref(false);

async function handleCertificateUpload(file: any) {
  const rawFile = file.raw;
  if (!rawFile) return;
  try {
    const text = await rawFile.text();
    const parsed = JSON.parse(text);
    if (!parsed.merkleRoot || !parsed.leafHash || !parsed.proofPath) {
      ElMessage.error('非法的 Merkle 存证证书格式');
      return;
    }
    certData.value = parsed;
    await executeVerification();
  } catch (err: any) {
    ElMessage.error('证书解析失败: ' + err.message);
  }
}

async function executeVerification() {
  if (!certData.value) return;
  isVerifying.value = true;
  verificationFinished.value = false;
  visibleSteps.value = [];

  const result = await verifyProofWithSteps(
    certData.value.merkleRoot,
    certData.value.leafHash,
    certData.value.proofPath
  );

  verificationSteps.value = result.steps;
  verificationSuccess.value = result.isValid;

  await runStepAnimation();
  verificationFinished.value = true;
  isVerifying.value = false;
}

async function runStepAnimation() {
  visibleSteps.value = [];
  currentStepIndex.value = 0;
  for (let i = 0; i < verificationSteps.value.length; i++) {
    visibleSteps.value.push(verificationSteps.value[i]);
    currentStepIndex.value = i + 1;
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
}

function simulateTampering() {
  if (!certData.value) return;
  // 单比特翻转篡改根哈希
  const origRoot = certData.value.merkleRoot;
  certData.value = {
    ...certData.value,
    merkleRoot: origRoot.startsWith('00') ? 'ff' + origRoot.substring(2) : '00' + origRoot.substring(2)
  };
  ElMessage.warning('已注入单比特伪造数据，重新验证以检验阻断力');
  executeVerification();
}

function exportAuditReportJson() {
  if (!certData.value) return;
  const auditReport = {
    auditTimestamp: new Date().toISOString(),
    engine: 'WebCrypto RFC 6962 Hardware-Accelerated Validator',
    traceId: certData.value.traceId,
    verifiedRoot: certData.value.merkleRoot,
    verificationStatus: verificationSuccess.value ? 'RFC_6962_PASSED' : 'TAMPER_DETECTED',
    proofStepsCount: verificationSteps.value.length
  };

  const blob = new Blob([JSON.stringify(auditReport, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `audit-report-${certData.value.traceId}.json`;
  a.click();
  URL.revokeObjectURL(url);
  ElMessage.success('审计凭证导出成功');
}

function loadSampleCertificate() {
  // 载入标准已知样例
  certData.value = {
    traceId: 'tr-deepseek-rfc6962-sample-01',
    leafIndex: 2,
    leafHash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    merkleRoot: 'd5786440b6e921d743a6d47b0e51ee127b4094e9f7cd59d33b8782a2e47c1a8e',
    proofPath: [
      { hash: '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8', isLeft: true },
      { hash: '4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a', isLeft: false }
    ]
  };
  executeVerification();
}

onMounted(() => {
  loadSampleCertificate();
});
</script>

<style scoped lang="scss">
.merkle-validator-container {
  width: 100%;
  height: 100%;
  padding: 24px;
  background: var(--mono-bg, #0A0A0C);
  overflow-y: auto;
  box-sizing: border-box;

  .validator-card {
    max-width: 900px;
    margin: 0 auto;
    background: var(--glass-l2-bg, rgba(28, 28, 34, 0.9));
    backdrop-filter: blur(var(--glass-l2-blur, 8px));
    border: 1px solid var(--mono-border-strong, rgba(255, 255, 255, 0.15));
    border-radius: 12px;
    padding: 28px;
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);

    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 24px;
      padding-bottom: 18px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.08);

      .header-left {
        display: flex;
        align-items: center;
        gap: 16px;

        .header-icon-box {
          width: 44px;
          height: 44px;
          border-radius: 10px;
          background: rgba(99, 102, 241, 0.12);
          border: 1px solid rgba(99, 102, 241, 0.25);
          display: flex;
          align-items: center;
          justify-content: center;
          color: #818CF8;
        }

        .header-meta {
          h3 {
            margin: 0 0 4px 0;
            font-size: 16px;
            font-weight: 700;
            color: #EDEDEF;
          }
          p {
            margin: 0;
            font-size: 12px;
            color: #9E9EA4;
          }
        }
      }

      .header-actions {
        display: flex;
        align-items: center;
        gap: 10px;
      }
    }

    .cert-summary-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin-bottom: 24px;

      .summary-item {
        background: rgba(0, 0, 0, 0.35);
        border: 1px solid rgba(255, 255, 255, 0.08);
        border-radius: 6px;
        padding: 10px 14px;

        .label {
          display: block;
          font-size: 10px;
          font-weight: 700;
          color: #71717A;
          margin-bottom: 4px;
        }

        .value {
          font-size: 12px;
          color: #EDEDEF;

          &.mono {
            font-family: 'JetBrains Mono', monospace;
          }
        }
      }
    }

    .steps-progress-wrapper {
      margin-bottom: 28px;

      .progress-title {
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 12px;
        font-weight: 700;
        letter-spacing: 0.05em;
        color: #9E9EA4;
        margin-bottom: 12px;
      }

      .steps-list {
        display: flex;
        flex-direction: column;
        gap: 10px;

        .step-item {
          display: flex;
          align-items: center;
          gap: 14px;
          background: rgba(255, 255, 255, 0.02);
          border: 1px solid rgba(255, 255, 255, 0.06);
          border-radius: 6px;
          padding: 10px 14px;
          transition: all 0.25s ease;

          .step-num {
            font-family: 'JetBrains Mono', monospace;
            font-size: 12px;
            font-weight: 700;
            color: #6366F1;
            width: 28px;
          }

          .step-content {
            flex: 1;
            display: flex;
            flex-direction: column;
            gap: 4px;

            .hash-row {
              display: flex;
              align-items: center;
              gap: 8px;
              font-size: 11px;

              .tag {
                font-size: 9px;
                font-weight: 700;
                padding: 1px 4px;
                border-radius: 3px;
                background: rgba(255, 255, 255, 0.06);
                color: #A1A1AA;

                &.formula {
                  background: rgba(99, 102, 241, 0.15);
                  color: #818CF8;
                }
              }

              .mono {
                font-family: 'JetBrains Mono', monospace;
                color: #D4D4D8;
              }

              &.result .text-success {
                color: #10B981;
                font-weight: 600;
              }
            }
          }
        }
      }
    }

    .result-seal-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 18px;
      padding-top: 10px;

      .digital-seal {
        padding: 16px 32px;
        border-radius: 12px;
        text-align: center;
        transition: all 0.3s ease;

        &.verified {
          background: rgba(16, 185, 129, 0.08);
          border: 2px solid #10B981;
          color: #10B981;
          box-shadow: 0 0 30px rgba(16, 185, 129, 0.25);
        }

        &.tampered {
          background: rgba(239, 68, 68, 0.08);
          border: 2px solid #EF4444;
          color: #EF4444;
          box-shadow: 0 0 30px rgba(239, 68, 68, 0.25);
        }

        .seal-text-bold {
          font-family: 'JetBrains Mono', monospace;
          font-size: 18px;
          font-weight: 800;
          letter-spacing: 0.1em;
          margin-top: 6px;
        }

        .seal-subtext {
          font-size: 11px;
          opacity: 0.85;
          margin-top: 2px;
        }
      }

      .seal-actions {
        margin-top: 6px;
      }
    }
  }
}
</style>
