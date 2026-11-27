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
    merkleRoot: 'ebcdaaba1b5ae1176bc89a037c6f1383d58b56eefc934da09e13d1149043225e',
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
  background: transparent;
  overflow-y: auto;
  box-sizing: border-box;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro", "PingFang SC", sans-serif;

  /* Apple iOS 26 双层高透玻璃卡片（零扩散阴影 + z-index 分层隔离） */
  .validator-card {
    position: relative;
    max-width: 920px;
    margin: 0 auto;
    background: transparent;
    border: 0.5px solid rgba(255, 255, 255, 0.92);
    border-radius: 24px;
    padding: 28px;
    box-shadow: none;

    &::before {
      content: '';
      position: absolute;
      inset: 0;
      z-index: 1;
      border-radius: inherit;
      background: rgba(255, 255, 255, 0.72);
      mix-blend-mode: plus-lighter;
      pointer-events: none;
    }

    &::after {
      content: '';
      position: absolute;
      inset: 0;
      z-index: 2;
      border-radius: inherit;
      backdrop-filter: blur(50px);
      -webkit-backdrop-filter: blur(50px);
      background: rgba(255, 255, 255, 0.25);
      mix-blend-mode: color-dodge;
      pointer-events: none;
    }

    > :where(*) {
      position: relative;
      z-index: 3;
    }

    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
      margin-bottom: 24px;
      padding-bottom: 18px;
      border-bottom: 0.5px solid rgba(0, 0, 0, 0.08);

      .header-left {
        display: flex;
        align-items: center;
        gap: 14px;

        .header-icon-box {
          width: 44px;
          height: 44px;
          border-radius: 14px;
          background: rgba(0, 122, 255, 0.1);
          border: 0.5px solid rgba(0, 122, 255, 0.22);
          display: flex;
          align-items: center;
          justify-content: center;
          color: #007aff;
        }

        .header-meta {
          h3 {
            margin: 0 0 4px 0;
            font-size: 16px;
            font-weight: 590;
            color: #000000;
          }
          p {
            margin: 0;
            font-size: 12px;
            color: #6e6e73;
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
      grid-template-columns: 1.2fr 0.6fr 2fr;
      gap: 12px;
      margin-bottom: 24px;

      .summary-item {
        background: rgba(255, 255, 255, 0.68);
        border: 0.5px solid rgba(0, 0, 0, 0.08);
        border-radius: 14px;
        padding: 12px 14px;
        min-width: 0;

        .label {
          display: block;
          font-size: 10px;
          font-weight: 600;
          color: #6e6e73;
          margin-bottom: 4px;
          letter-spacing: 0.04em;
        }

        .value {
          display: block;
          font-size: 12px;
          font-weight: 500;
          color: #1d1d1f;
          word-break: break-all;

          &.mono {
            font-family: ui-monospace, 'SF Mono', 'JetBrains Mono', monospace;
          }
        }
      }
    }

    .steps-progress-wrapper {
      margin-bottom: 24px;

      .progress-title {
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 12px;
        font-weight: 590;
        letter-spacing: 0.02em;
        color: #3c3c43;
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
          background: rgba(255, 255, 255, 0.65);
          border: 0.5px solid rgba(0, 0, 0, 0.08);
          border-radius: 14px;
          padding: 12px 14px;
          transition: all 0.25s ease;

          .step-num {
            font-family: ui-monospace, 'SF Mono', 'JetBrains Mono', monospace;
            font-size: 12px;
            font-weight: 700;
            color: #007aff;
            width: 30px;
          }

          .step-content {
            flex: 1;
            display: flex;
            flex-direction: column;
            gap: 5px;

            .hash-row {
              display: flex;
              align-items: center;
              gap: 8px;
              font-size: 11px;

              .tag {
                font-size: 9px;
                font-weight: 600;
                padding: 2px 6px;
                border-radius: 6px;
                background: rgba(118, 118, 128, 0.12);
                color: #3c3c43;

                &.formula {
                  background: rgba(0, 122, 255, 0.1);
                  color: #007aff;
                }
              }

              .mono {
                font-family: ui-monospace, 'SF Mono', 'JetBrains Mono', monospace;
                color: #1d1d1f;
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
      gap: 16px;
      padding-top: 8px;

      .digital-seal {
        padding: 16px 36px;
        border-radius: 18px;
        text-align: center;
        transition: all 0.3s ease;

        &.verified {
          background: rgba(52, 199, 89, 0.1);
          border: 1.5px solid #34c759;
          color: #1f8f3d;
        }

        &.tampered {
          background: rgba(255, 59, 48, 0.1);
          border: 1.5px solid #ff3b30;
          color: #d92d20;
        }

        .seal-text-bold {
          font-family: ui-monospace, 'SF Mono', 'JetBrains Mono', monospace;
          font-size: 18px;
          font-weight: 700;
          letter-spacing: 0.08em;
          margin-top: 6px;
        }

        .seal-subtext {
          font-size: 11px;
          opacity: 0.9;
          margin-top: 2px;
        }
      }

      .seal-actions {
        margin-top: 4px;
      }
    }
  }
}
</style>
