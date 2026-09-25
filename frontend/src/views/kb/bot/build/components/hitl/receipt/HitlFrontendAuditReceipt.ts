/**
 * 前端人机协同不可变密码学审计凭单 (HitlFrontendAuditReceipt)
 * 遵循 Phase 134 规范与零信任安全架构
 * 1. 字段与后端 Java 21 Record 严格镜像对齐
 * 2. 零外部重型依赖，内置纯 TypeScript 标准 SHA-256 密码哈希
 * 3. 常量时间验真 verifySignature()，防范时序侧信道攻击
 */

export interface HitlFrontendAuditReceiptData {
  readonly receiptId: string;
  readonly workflowId: string;
  readonly nodeId: string;
  readonly stepIndex: number;
  readonly branchId: string;
  readonly operatorId: string;
  readonly actionType: 'APPROVE' | 'REJECT' | 'HOT_PATCH' | 'TIMEOUT_FAILSAFE';
  readonly originalStateHash: string;
  readonly patchedStateHash: string;
  readonly reasoningContentDigest: string;
  readonly timestamp: number;
  readonly sha256Signature: string;
}

/**
 * 纯 TypeScript 标准 SHA-256 哈希实现 (FIPS PUB 180-4 兼容)
 */
export function computeSha256(input: string): string {
  function rightRotate(value: number, amount: number): number {
    return (value >>> amount) | (value << (32 - amount));
  }

  const mathPow = Math.pow;
  const maxWord = mathPow(2, 32);
  let result = '';

  const words: number[] = [];
  const asciiBitLength = input.length * 8;

  let hash = [
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
    0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
  ];

  const k = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
  ];

  for (let i = 0; i < input.length; i++) {
    const code = input.charCodeAt(i);
    words[i >> 2] |= (code & 0xff) << (24 - (i % 4) * 8);
  }

  words[asciiBitLength >> 5] |= 0x80 << (24 - (asciiBitLength % 32));
  words[(((asciiBitLength + 64) >> 9) << 4) + 15] = asciiBitLength;

  for (let j = 0; j < words.length; j += 16) {
    const w = words.slice(j, j + 16);
    const oldHash = hash.slice(0);

    for (let i = 0; i < 64; i++) {
      let w15: number, w2: number;
      if (i < 16) {
        // use w[i]
      } else {
        const s0 = rightRotate(w[i - 15], 7) ^ rightRotate(w[i - 15], 18) ^ (w[i - 15] >>> 3);
        const s1 = rightRotate(w[i - 2], 17) ^ rightRotate(w[i - 2], 19) ^ (w[i - 2] >>> 10);
        w[i] = (w[i - 16] + s0 + w[i - 7] + s1) | 0;
      }

      const ch = (hash[4] & hash[5]) ^ (~hash[4] & hash[6]);
      const maj = (hash[0] & hash[1]) ^ (hash[0] & hash[2]) ^ (hash[1] & hash[2]);
      const s0 = rightRotate(hash[0], 2) ^ rightRotate(hash[0], 13) ^ rightRotate(hash[0], 22);
      const s1 = rightRotate(hash[4], 6) ^ rightRotate(hash[4], 11) ^ rightRotate(hash[4], 25);
      const temp1 = (hash[7] + s1 + ch + k[i] + w[i]) | 0;
      const temp2 = (s0 + maj) | 0;

      hash = [(temp1 + temp2) | 0, hash[0], hash[1], hash[2], (hash[3] + temp1) | 0, hash[4], hash[5], hash[6]];
    }

    for (let i = 0; i < 8; i++) {
      hash[i] = (hash[i] + oldHash[i]) | 0;
    }
  }

  for (let i = 0; i < 8; i++) {
    for (let j = 3; j >= 0; j--) {
      const b = (hash[i] >> (j * 8)) & 255;
      result += (b < 16 ? '0' : '') + b.toString(16);
    }
  }

  return result;
}

/**
 * 常量时间字符串比对 (防范时序侧信道攻击)
 */
export function constantTimeEquals(a: string, b: string): boolean {
  if (a.length !== b.length) {
    return false;
  }
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

export class HitlFrontendAuditReceipt implements HitlFrontendAuditReceiptData {
  public readonly receiptId: string;
  public readonly workflowId: string;
  public readonly nodeId: string;
  public readonly stepIndex: number;
  public readonly branchId: string;
  public readonly operatorId: string;
  public readonly actionType: 'APPROVE' | 'REJECT' | 'HOT_PATCH' | 'TIMEOUT_FAILSAFE';
  public readonly originalStateHash: string;
  public readonly patchedStateHash: string;
  public readonly reasoningContentDigest: string;
  public readonly timestamp: number;
  public readonly sha256Signature: string;

  constructor(data: Omit<HitlFrontendAuditReceiptData, 'sha256Signature'>) {
    this.receiptId = data.receiptId;
    this.workflowId = data.workflowId;
    this.nodeId = data.nodeId;
    this.stepIndex = data.stepIndex;
    this.branchId = data.branchId;
    this.operatorId = data.operatorId;
    this.actionType = data.actionType;
    this.originalStateHash = data.originalStateHash;
    this.patchedStateHash = data.patchedStateHash;
    this.reasoningContentDigest = data.reasoningContentDigest;
    this.timestamp = data.timestamp;

    this.sha256Signature = this.calculateSignature();
  }

  /**
   * 规范化签名计算
   */
  private calculateSignature(): string {
    const canonicalPayload = [
      this.receiptId,
      this.workflowId,
      this.nodeId,
      String(this.stepIndex),
      this.branchId,
      this.operatorId,
      this.actionType,
      this.originalStateHash,
      this.patchedStateHash,
      this.reasoningContentDigest,
      String(this.timestamp)
    ].join('|');

    return computeSha256(canonicalPayload);
  }

  /**
   * 常量时间自验真
   */
  public verifySignature(): boolean {
    const expected = this.calculateSignature();
    return constantTimeEquals(this.sha256Signature, expected);
  }

  /**
   * 凭单快速工厂方法
   */
  public static create(
    workflowId: string,
    nodeId: string,
    stepIndex: number,
    branchId: string,
    operatorId: string,
    actionType: 'APPROVE' | 'REJECT' | 'HOT_PATCH' | 'TIMEOUT_FAILSAFE',
    originalState: unknown,
    patchedState: unknown,
    reasoningContent: string
  ): HitlFrontendAuditReceipt {
    const receiptId = `rcpt_hitl_${workflowId}_s${stepIndex}_${Date.now()}`;
    const origHash = computeSha256(JSON.stringify(originalState || {}));
    const patchHash = computeSha256(JSON.stringify(patchedState || {}));
    const digest = computeSha256(reasoningContent || '');

    return new HitlFrontendAuditReceipt({
      receiptId,
      workflowId,
      nodeId,
      stepIndex,
      branchId,
      operatorId,
      actionType,
      originalStateHash: origHash,
      patchedStateHash: patchHash,
      reasoningContentDigest: digest,
      timestamp: Date.now()
    });
  }
}
