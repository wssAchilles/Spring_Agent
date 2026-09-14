/**
 * RFC 6962 客户端免密密码学验真工具库 (纯原生 WebCrypto)
 *
 * 严格遵从 RFC 6962 规范：
 * 1. 叶子节点哈希前置单字节域分离前缀 0x00；
 * 2. 内部节点哈希前置单字节域分离前缀 0x01；
 * 3. 兄弟哈希严格转换为 32 字节二进制再行拼接，零内存拷贝。
 *
 * @author qknow
 */

export const LEAF_PREFIX = 0x00;
export const NODE_PREFIX = 0x01;

// 预分配 65 字节固定缓冲区：1 字节前缀 + 32 字节左哈希 + 32 字节右哈希
const nodeCombineBuffer = new Uint8Array(65);
nodeCombineBuffer[0] = NODE_PREFIX;

/**
 * 将 16 进制 Hex 字符串高效转换为 Uint8Array 二进制字节
 */
export function hexToBytes(hex: string): Uint8Array {
  const cleanHex = hex.startsWith('0x') ? hex.slice(2) : hex;
  const len = cleanHex.length;
  const bytes = new Uint8Array(len / 2);
  for (let i = 0; i < len; i += 2) {
    bytes[i / 2] = parseInt(cleanHex.substring(i, i + 2), 16);
  }
  return bytes;
}

/**
 * 将 Uint8Array 快速转换为标准小写 Hex 字符串
 */
export function bytesToHex(bytes: Uint8Array): string {
  const hexArr: string[] = [];
  for (let i = 0; i < bytes.length; i++) {
    hexArr.push(bytes[i].toString(16).padStart(2, '0'));
  }
  return hexArr.join('');
}

/**
 * 计算叶子节点哈希 (RFC 6962 前缀 0x00)
 * 严格对齐后端: index + ":" + itemId + ":" + payloadHash + ":" + timestamp
 */
export async function computeClientLeafHash(
  index: number,
  itemId: string,
  payloadHash: string,
  timestamp: number
): Promise<string> {
  const rawString = `${index}:${itemId}:${payloadHash}:${timestamp}`;
  const utf8Bytes = new TextEncoder().encode(rawString);

  // 拼接单字节前缀 0x00
  const combined = new Uint8Array(1 + utf8Bytes.length);
  combined[0] = LEAF_PREFIX;
  combined.set(utf8Bytes, 1);

  const digestBuffer = await window.crypto.subtle.digest('SHA-256', combined);
  return bytesToHex(new Uint8Array(digestBuffer));
}

/**
 * 计算内部节点哈希 (RFC 6962 前缀 0x01)
 * 严格阻断 Hex 字符串误编码事故：入参两个 Hex 转为 32 字节二进制后再拼接
 */
export async function computeClientNodeHash(leftHex: string, rightHex: string): Promise<string> {
  const leftBytes = hexToBytes(leftHex);
  const rightBytes = hexToBytes(rightHex);

  if (leftBytes.length !== 32 || rightBytes.length !== 32) {
    throw new Error('SHA-256 节点哈希必须严格为 32 字节二进制');
  }

  // 写入单例缓冲区，实现零拷贝
  nodeCombineBuffer[0] = NODE_PREFIX;
  nodeCombineBuffer.set(leftBytes, 1);
  nodeCombineBuffer.set(rightBytes, 33);

  const digestBuffer = await window.crypto.subtle.digest('SHA-256', nodeCombineBuffer);
  return bytesToHex(new Uint8Array(digestBuffer));
}

/**
 * 证明路径元素
 */
export interface ProofElement {
  hash: string;
  isLeft: boolean;
}

/**
 * 验证步骤中间计算状态
 */
export interface VerificationStep {
  stepIndex: number;
  currentHash: string;
  siblingHash: string;
  isSiblingLeft: boolean;
  computedParentHash: string;
}

/**
 * 客户端对数级逐层折叠验真主函数
 */
export async function verifyProofWithSteps(
  rootHash: string,
  leafHash: string,
  proofPath: ProofElement[]
): Promise<{ isValid: boolean; calculatedRoot: string; steps: VerificationStep[] }> {
  let currentHash = leafHash;
  const steps: VerificationStep[] = [];

  for (let i = 0; i < proofPath.length; i++) {
    const element = proofPath[i];
    let parentHash = '';

    if (element.isLeft) {
      // 兄弟在左侧: NodeHash(sibling, current)
      parentHash = await computeClientNodeHash(element.hash, currentHash);
    } else {
      // 兄弟在右侧: NodeHash(current, sibling)
      parentHash = await computeClientNodeHash(currentHash, element.hash);
    }

    steps.push({
      stepIndex: i + 1,
      currentHash,
      siblingHash: element.hash,
      isSiblingLeft: element.isLeft,
      computedParentHash: parentHash
    });

    currentHash = parentHash;
  }

  const isValid = rootHash.toLowerCase() === currentHash.toLowerCase();
  return {
    isValid,
    calculatedRoot: currentHash,
    steps
  };
}
