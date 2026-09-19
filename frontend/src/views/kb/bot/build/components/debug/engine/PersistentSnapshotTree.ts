/**
 * 持久化结构共享状态快照树 (PersistentSnapshotTree)
 * 遵循 Phase 120 规范与定理 1.1（不可变时间旅行状态完备性定理）
 * 1. 基于不可变 HAMT (Hash Array Mapped Trie) 结构共享树模型
 * 2. 路径复制算子 (Path Copying): 单步变异增量物理内存严格有界于 O(Delta_V)
 * 3. 历史时刻随机寻址: 根指针常数表索引 O(1) 瞬时重构
 * 4. 彻底杜绝反向时间污染 (Anti-Retroactive Contamination)
 * 5. 跨平台零依赖纯 TypeScript SHA-256 自签名
 */

// 分支因子 B = 32 (5 比特分片)
const BITS = 5;
const WIDTH = 1 << BITS; // 32
const MASK = WIDTH - 1; // 0x1F

/**
 * 跨平台标准 SHA-256 算法实现 (完全兼容 Node.js 与浏览器端，支持 UTF-8 字符集)
 */
export function computeSha256(input: string): string {
  // 1. 若在 Node.js 环境中 (如契约测试运行时)，优先使用原生高效 crypto
  try {
    if (typeof process !== 'undefined' && process.versions && (process.versions as any).node) {
      const nodeCrypto = eval('require')('crypto');
      return nodeCrypto.createHash('sha256').update(input, 'utf8').digest('hex');
    }
  } catch (_) {}

  // 2. 纯 JS/TS 标准 SHA-256 实现 (支持 UTF-8 与全浏览器环境)
  return pureJsSha256(input);
}

function pureJsSha256(ascii: string): string {
  // 转换为 UTF-8 字节数组
  const bytes: number[] = [];
  for (let i = 0; i < ascii.length; i++) {
    let code = ascii.charCodeAt(i);
    if (code < 0x80) {
      bytes.push(code);
    } else if (code < 0x800) {
      bytes.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f));
    } else if (code < 0xd800 || code >= 0xe000) {
      bytes.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
    } else {
      i++;
      code = 0x10000 + (((code & 0x3ff) << 10) | (ascii.charCodeAt(i) & 0x3ff));
      bytes.push(
        0xf0 | (code >> 18),
        0x80 | ((code >> 12) & 0x3f),
        0x80 | ((code >> 6) & 0x3f),
        0x80 | (code & 0x3f)
      );
    }
  }

  const K = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
  ];

  let H0 = 0x6a09e667, H1 = 0xbb67ae85, H2 = 0x3c6ef372, H3 = 0xa54ff53a;
  let H4 = 0x510e527f, H5 = 0x9b05688c, H6 = 0x1f83d9ab, H7 = 0x5be0cd19;

  const bitLength = bytes.length * 8;
  bytes.push(0x80);
  while ((bytes.length % 64) !== 56) {
    bytes.push(0);
  }

  const highBits = Math.floor(bitLength / 0x100000000);
  const lowBits = bitLength >>> 0;
  bytes.push(
    (highBits >>> 24) & 0xff, (highBits >>> 16) & 0xff, (highBits >>> 8) & 0xff, highBits & 0xff,
    (lowBits >>> 24) & 0xff, (lowBits >>> 16) & 0xff, (lowBits >>> 8) & 0xff, lowBits & 0xff
  );

  const W = new Int32Array(64);
  for (let chunk = 0; chunk < bytes.length; chunk += 64) {
    for (let i = 0; i < 16; i++) {
      const idx = chunk + (i * 4);
      W[i] = (bytes[idx] << 24) | (bytes[idx + 1] << 16) | (bytes[idx + 2] << 8) | bytes[idx + 3];
    }
    for (let i = 16; i < 64; i++) {
      const s0 = ((W[i - 15] >>> 7) | (W[i - 15] << 25)) ^ ((W[i - 15] >>> 18) | (W[i - 15] << 14)) ^ (W[i - 15] >>> 3);
      const s1 = ((W[i - 2] >>> 17) | (W[i - 2] << 15)) ^ ((W[i - 2] >>> 19) | (W[i - 2] << 13)) ^ (W[i - 2] >>> 10);
      W[i] = (W[i - 16] + s0 + W[i - 7] + s1) | 0;
    }

    let a = H0, b = H1, c = H2, d = H3, e = H4, f = H5, g = H6, h = H7;

    for (let i = 0; i < 64; i++) {
      const S1 = ((e >>> 6) | (e << 26)) ^ ((e >>> 11) | (e << 21)) ^ ((e >>> 25) | (e << 7));
      const ch = (e & f) ^ ((~e) & g);
      const temp1 = (h + S1 + ch + K[i] + W[i]) | 0;
      const S0 = ((a >>> 2) | (a << 30)) ^ ((a >>> 13) | (a << 19)) ^ ((a >>> 22) | (a << 10));
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const temp2 = (S0 + maj) | 0;

      h = g; g = f; f = e; e = (d + temp1) | 0;
      d = c; c = b; b = a; a = (temp1 + temp2) | 0;
    }

    H0 = (H0 + a) | 0; H1 = (H1 + b) | 0; H2 = (H2 + c) | 0; H3 = (H3 + d) | 0;
    H4 = (H4 + e) | 0; H5 = (H5 + f) | 0; H6 = (H6 + g) | 0; H7 = (H7 + h) | 0;
  }

  const toHex = (n: number) => (n >>> 0).toString(16).padStart(8, '0');
  return `${toHex(H0)}${toHex(H1)}${toHex(H2)}${toHex(H3)}${toHex(H4)}${toHex(H5)}${toHex(H6)}${toHex(H7)}`;
}

/**
 * 字符串 32 位无符号哈希 (DJB2 变体)
 */
export function hashString(str: string): number {
  let hash = 5381;
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) + hash) + str.charCodeAt(i);
    hash |= 0;
  }
  return hash >>> 0;
}

/**
 * 计算 32 位整数二进制中 1 的个数 (Hamming Weight)
 */
function popcount(v: number): number {
  v = v - ((v >>> 1) & 0x55555555);
  v = (v & 0x33333333) + ((v >>> 2) & 0x33333333);
  return (((v + (v >>> 4)) & 0xF0F0F0F) * 0x1010101) >>> 24;
}

/**
 * Trie 内部节点结构
 */
interface TrieNode<V> {
  readonly bitmap: number;
  readonly children: ReadonlyArray<TrieNode<V> | [string, V]>;
}

const EMPTY_NODE: TrieNode<any> = Object.freeze({
  bitmap: 0,
  children: Object.freeze([])
});

/**
 * 持久化结构共享状态树类
 */
export class PersistentSnapshotTree<V = any> {
  private readonly root: TrieNode<V>;
  private readonly _size: number;

  constructor(root: TrieNode<V> = EMPTY_NODE, size = 0) {
    this.root = root;
    this._size = size;
  }

  public get size(): number {
    return this._size;
  }

  /**
   * 检索键对应的值
   */
  public get(key: string): V | undefined {
    const hash = hashString(key);
    let current: TrieNode<V> = this.root;
    let shift = 0;

    while (shift < 32) {
      const frag = (hash >>> shift) & MASK;
      const bit = 1 << frag;

      if ((current.bitmap & bit) === 0) {
        return undefined;
      }

      const idx = popcount(current.bitmap & (bit - 1));
      const child = current.children[idx];

      if (Array.isArray(child)) {
        return child[0] === key ? child[1] : undefined;
      }

      current = child as TrieNode<V>;
      shift += BITS;
    }

    return undefined;
  }

  /**
   * 路径复制更新 (Path Copying): 插入或更新键值对，生成新树实例
   */
  public set(key: string, value: V): PersistentSnapshotTree<V> {
    const hash = hashString(key);
    let sizeDelta = 0;

    const updateHelper = (node: TrieNode<V>, shift: number): TrieNode<V> => {
      const frag = (hash >>> shift) & MASK;
      const bit = 1 << frag;
      const hasKey = (node.bitmap & bit) !== 0;
      const idx = popcount(node.bitmap & (bit - 1));

      if (!hasKey) {
        // 新增键槽位
        const newChildren = [...node.children];
        newChildren.splice(idx, 0, [key, value]);
        sizeDelta = 1;
        return Object.freeze({
          bitmap: node.bitmap | bit,
          children: Object.freeze(newChildren)
        });
      }

      const child = node.children[idx];
      if (Array.isArray(child)) {
        if (child[0] === key) {
          // 键相同，更新值
          const newChildren = [...node.children];
          newChildren[idx] = [key, value];
          return Object.freeze({
            bitmap: node.bitmap,
            children: Object.freeze(newChildren)
          });
        }
        // 发生哈希前缀碰撞，分裂为深层内部节点
        const collisionNode = updateHelper(
          updateHelper(EMPTY_NODE, shift + BITS),
          shift + BITS
        );
        // 分裂并插入旧键与新键
        const newSubTree = PersistentSnapshotTree.insertTwoKeys(
          shift + BITS,
          child[0], child[1],
          key, value
        );
        const newChildren = [...node.children];
        newChildren[idx] = newSubTree;
        sizeDelta = 1;
        return Object.freeze({
          bitmap: node.bitmap,
          children: Object.freeze(newChildren)
        });
      }

      // 递归深入内部节点进行路径复制
      const updatedChild = updateHelper(child as TrieNode<V>, shift + BITS);
      const newChildren = [...node.children];
      newChildren[idx] = updatedChild;
      return Object.freeze({
        bitmap: node.bitmap,
        children: Object.freeze(newChildren)
      });
    };

    const newRoot = updateHelper(this.root, 0);
    return new PersistentSnapshotTree(newRoot, this._size + sizeDelta);
  }

  /**
   * 批量更新键值对
   */
  public setBatch(entries: Record<string, V>): PersistentSnapshotTree<V> {
    let tree: PersistentSnapshotTree<V> = this;
    for (const [k, v] of Object.entries(entries)) {
      tree = tree.set(k, v);
    }
    return tree;
  }

  /**
   * 导出为只读扁平对象
   */
  public toObject(): Record<string, V> {
    const result: Record<string, V> = {};
    const traverse = (node: TrieNode<V>) => {
      for (const child of node.children) {
        if (Array.isArray(child)) {
          result[child[0]] = child[1];
        } else {
          traverse(child as TrieNode<V>);
        }
      }
    };
    traverse(this.root);
    return Object.freeze(result);
  }

  /**
   * 计算当前快照树的密码学 SHA-256 摘要
   */
  public computeDigest(): string {
    const obj = this.toObject();
    const sortedKeys = Object.keys(obj).sort();
    const raw = sortedKeys.map(k => `${k}=${JSON.stringify(obj[k])}`).join('&');
    return computeSha256(raw);
  }

  /**
   * 辅助方法：解决碰撞时在深层树插入两个键
   */
  private static insertTwoKeys<V>(
    shift: number,
    k1: string, v1: V,
    k2: string, v2: V
  ): TrieNode<V> {
    if (shift >= 32) {
      // 极低概率的完全哈希碰撞兜底
      return Object.freeze({
        bitmap: 1,
        children: Object.freeze([[k1, v1], [k2, v2]] as any)
      });
    }

    const h1 = hashString(k1);
    const h2 = hashString(k2);
    const f1 = (h1 >>> shift) & MASK;
    const f2 = (h2 >>> shift) & MASK;

    if (f1 === f2) {
      const subNode = PersistentSnapshotTree.insertTwoKeys(shift + BITS, k1, v1, k2, v2);
      return Object.freeze({
        bitmap: 1 << f1,
        children: Object.freeze([subNode])
      });
    }

    const b1 = 1 << f1;
    const b2 = 1 << f2;
    const bitmap = b1 | b2;
    const children = f1 < f2 ? [[k1, v1], [k2, v2]] : [[k2, v2], [k1, v1]];

    return Object.freeze({
      bitmap,
      children: Object.freeze(children as any)
    });
  }
}

/**
 * 历史时刻快照不可变根包裹体
 */
export interface SnapshotRootRecord<V = any> {
  readonly stepIndex: number;
  readonly nodeId: string;
  readonly nodeName: string;
  readonly timestamp: number;
  readonly deltaKeys: ReadonlyArray<string>;
  readonly tree: PersistentSnapshotTree<V>;
  readonly digest: string;
}

/**
 * 持久化状态快照树管理器 (PersistentSnapshotManager)
 * 维护单链执行历史根指针数组，提供 O(1) 历史重构寻址
 */
export class PersistentSnapshotManager<V = any> {
  private roots: SnapshotRootRecord<V>[] = [];

  /**
   * 记录新步长状态快照
   */
  public recordStep(
    stepIndex: number,
    nodeId: string,
    nodeName: string,
    deltaVariables: Record<string, V>
  ): SnapshotRootRecord<V> {
    const prevTree = this.roots.length > 0 
      ? this.roots[this.roots.length - 1].tree 
      : new PersistentSnapshotTree<V>();

    const deltaKeys = Object.keys(deltaVariables);
    const newTree = prevTree.setBatch(deltaVariables);
    const digest = newTree.computeDigest();

    const record: SnapshotRootRecord<V> = Object.freeze({
      stepIndex,
      nodeId,
      nodeName,
      timestamp: Date.now(),
      deltaKeys: Object.freeze([...deltaKeys]),
      tree: newTree,
      digest
    });

    this.roots.push(record);
    return record;
  }

  /**
   * O(1) 检索指定历史步长的状态树
   */
  public getSnapshotAtStep(stepIndex: number): SnapshotRootRecord<V> | undefined {
    if (stepIndex < 0 || stepIndex >= this.roots.length) {
      return undefined;
    }
    return this.roots[stepIndex];
  }

  /**
   * 获取当前总快照步数
   */
  public get length(): number {
    return this.roots.length;
  }

  /**
   * 获取只读历史快照记录列表
   */
  public getAllSnapshots(): ReadonlyArray<SnapshotRootRecord<V>> {
    return Object.freeze([...this.roots]);
  }

  /**
   * 重置快照记录
   */
  public clear(): void {
    this.roots = [];
  }
}
