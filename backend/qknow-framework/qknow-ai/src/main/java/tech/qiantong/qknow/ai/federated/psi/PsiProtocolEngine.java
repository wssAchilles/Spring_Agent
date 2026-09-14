package tech.qiantong.qknow.ai.federated.psi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.federated.dto.PsiBlindPayloadDTO;
import tech.qiantong.qknow.ai.federated.dto.PsiIntersectionResultDTO;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

/**
 * 基于 Diffie-Hellman 的两方隐私集合求交 (DH-PSI) 协议引擎
 * 
 * 核心数学定理 (Theorem 1.1 Zero-Knowledge PSI Soundness & Invariant):
 * 在循环乘法群 G 中，(H(x)^kA)^kB = (H(x)^kB)^kA = H(x)^(kA*kB) mod p。
 * 双方盲化后比较二次盲化值，当且仅当 x_i = y_j 时二次盲化值相等。
 * 双方仅能知晓交集元素 ID，非交集元素在离散对数困难假设下完全零知识暴露 (Zero-Knowledge Leakage)。
 */
@Component
public class PsiProtocolEngine {

    private static final Logger log = LoggerFactory.getLogger(PsiProtocolEngine.class);

    /**
     * 预置安全大素数模数 p (RFC 3526 2048-bit MODP Group 紧凑安全参数)
     * 保证离散对数与 CDH 困难性，提供计算安全性。
     */
    public static final BigInteger DEFAULT_PRIME_P = new BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
            "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
            "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
            "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
            "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
            "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
            "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
            "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
            "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
            "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
            "15728E5A8AACAA68FFFFFFFFFFFFFFFF", 16);

    /** 安全素数阶 q = (p - 1) / 2 */
    public static final BigInteger DEFAULT_ORDER_Q = DEFAULT_PRIME_P.subtract(BigInteger.ONE).divide(BigInteger.valueOf(2));

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 生成参与方私钥 (k in [2, q - 1])
     */
    public BigInteger generatePrivateKey() {
        BigInteger k;
        do {
            k = new BigInteger(256, secureRandom);
        } while (k.compareTo(BigInteger.valueOf(2)) < 0 || k.compareTo(DEFAULT_ORDER_Q) >= 0);
        return k;
    }

    /**
     * 将原始实体 ID 哈希映射至乘法群 G 中的群元素 H(x) mod p
     */
    public BigInteger hashToGroup(String item) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(item.getBytes(StandardCharsets.UTF_8));
            BigInteger h = new BigInteger(1, hash);
            // 确保 h in [2, p - 1]
            return h.mod(DEFAULT_PRIME_P.subtract(BigInteger.valueOf(3))).add(BigInteger.valueOf(2));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 第一阶段：本地集合单重盲化
     * A_1 = { H(x_i)^kA mod p }
     *
     * @param items 本地私有实体列表
     * @param privateKey 本地私钥
     * @return 实体 ID -> 盲化值映射
     */
    public Map<String, BigInteger> blindLocalSet(List<String> items, BigInteger privateKey) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, BigInteger> blindedMap = new LinkedHashMap<>();
        for (String item : items) {
            BigInteger h = hashToGroup(item);
            BigInteger blinded = h.modPow(privateKey, DEFAULT_PRIME_P);
            blindedMap.put(item, blinded);
        }
        return blindedMap;
    }

    /**
     * 第二阶段：远程集合二次盲化
     * 对接收到的对端一阶盲化值应用本地私钥：(H(y_j)^kB)^kA mod p = H(y_j)^(kB*kA) mod p
     *
     * @param remoteBlindedValues 对端第一阶段盲化值列表
     * @param privateKey 本地私钥
     * @return 乱序的二次盲化值列表 (消除原始对应关系)
     */
    public List<BigInteger> blindRemoteSet(Collection<BigInteger> remoteBlindedValues, BigInteger privateKey) {
        if (remoteBlindedValues == null || remoteBlindedValues.isEmpty()) {
            return Collections.emptyList();
        }
        List<BigInteger> doubleBlindedList = new ArrayList<>();
        for (BigInteger remoteVal : remoteBlindedValues) {
            BigInteger doubleBlinded = remoteVal.modPow(privateKey, DEFAULT_PRIME_P);
            doubleBlindedList.add(doubleBlinded);
        }
        // 随机打乱顺序，严格杜绝位置信息关联泄露
        Collections.shuffle(doubleBlindedList, secureRandom);
        return doubleBlindedList;
    }

    /**
     * 执行求交判定：
     * Alice 比较自己的二次盲化集合 { (H(x_i)^kA)^kB } 与 Bob 发来的二次盲化集合 { (H(y_j)^kB)^kA }。
     *
     * @param localDoubleBlindedMap 本地实体 ID -> 本地二次盲化值映射
     * @param remoteDoubleBlindedValues 对端发来的乱序二次盲化值列表
     * @return 求交结果，包含匹配的实体 ID
     */
    public PsiIntersectionResultDTO computeIntersection(Map<String, BigInteger> localDoubleBlindedMap, List<BigInteger> remoteDoubleBlindedValues) {
        long start = System.currentTimeMillis();
        if (localDoubleBlindedMap == null || localDoubleBlindedMap.isEmpty() ||
            remoteDoubleBlindedValues == null || remoteDoubleBlindedValues.isEmpty()) {
            return new PsiIntersectionResultDTO(Collections.emptyList(), 0, true, System.currentTimeMillis() - start);
        }

        Set<BigInteger> remoteSet = new HashSet<>(remoteDoubleBlindedValues);
        List<String> matchedIds = new ArrayList<>();

        for (Map.Entry<String, BigInteger> entry : localDoubleBlindedMap.entrySet()) {
            if (remoteSet.contains(entry.getValue())) {
                matchedIds.add(entry.getKey());
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        return new PsiIntersectionResultDTO(matchedIds, matchedIds.size(), true, elapsed);
    }
}
