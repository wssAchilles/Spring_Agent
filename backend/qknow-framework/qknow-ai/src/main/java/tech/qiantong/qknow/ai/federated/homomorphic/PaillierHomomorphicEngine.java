package tech.qiantong.qknow.ai.federated.homomorphic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.federated.dto.HomomorphicCiphertextDTO;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;

/**
 * 工业级 2048 位 Paillier 加法同态加密与密文聚合引擎
 * 
 * 核心数学定理 (Theorem 2.1 Federated Homomorphic Aggregation Invariant):
 * 1. 同态加法: D(c1 * c2 mod n^2) = (m1 + m2) mod n
 * 2. 同态标量乘法: D(c^k mod n^2) = (k * m) mod n
 * 3. 联合置信度加权平均在模 n 空间无偏一致。
 */
@Component
public class PaillierHomomorphicEngine {

    private static final Logger log = LoggerFactory.getLogger(PaillierHomomorphicEngine.class);

    /** 最大同态累加项限制，杜绝模 n 溢出回绕 */
    public static final int MAX_HOMOMORPHIC_ADDENDS = 10000;
    /** 单个明文量化标量最大值 */
    public static final long MAX_PLAINTEXT_SCALAR = 1000000L;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Paillier 密钥对定义
     */
    public static class PaillierKeyPair {
        private final BigInteger n;
        private final BigInteger nSquared;
        private final BigInteger g;
        private final BigInteger lambda;
        private final BigInteger mu;

        public PaillierKeyPair(BigInteger n, BigInteger g, BigInteger lambda, BigInteger mu) {
            this.n = n;
            this.nSquared = n.multiply(n);
            this.g = g;
            this.lambda = lambda;
            this.mu = mu;
        }

        public BigInteger getN() { return n; }
        public BigInteger getNSquared() { return nSquared; }
        public BigInteger getG() { return g; }
        public BigInteger getLambda() { return lambda; }
        public BigInteger getMu() { return mu; }
    }

    /**
     * 生成 Paillier 密钥对 (bits 长度，如 1024 或 2048)
     */
    public PaillierKeyPair generateKeyPair(int bits) {
        int halfBits = bits / 2;
        BigInteger p = BigInteger.probablePrime(halfBits, secureRandom);
        BigInteger q;
        do {
            q = BigInteger.probablePrime(halfBits, secureRandom);
        } while (p.equals(q));

        BigInteger n = p.multiply(q);
        BigInteger nSquared = n.multiply(n);
        // 简化参数 g = n + 1，满足 gcd(L(g^lambda mod n^2), n) = 1
        BigInteger g = n.add(BigInteger.ONE);

        // lambda = lcm(p-1, q-1) = (p-1)*(q-1) / gcd(p-1, q-1)
        BigInteger pMinus1 = p.subtract(BigInteger.ONE);
        BigInteger qMinus1 = q.subtract(BigInteger.ONE);
        BigInteger lambda = pMinus1.multiply(qMinus1).divide(pMinus1.gcd(qMinus1));

        // 对于 g = n + 1，L(g^lambda mod n^2) = lambda mod n，故 mu = lambda^(-1) mod n
        BigInteger mu = lambda.modInverse(n);

        return new PaillierKeyPair(n, g, lambda, mu);
    }

    /**
     * 加密明文标量 m in [0, n - 1]
     */
    public HomomorphicCiphertextDTO encrypt(long plaintext, BigInteger n, BigInteger g, String participantId, String metricLabel) {
        if (plaintext < 0 || plaintext > MAX_PLAINTEXT_SCALAR) {
            throw new IllegalArgumentException("明文超出安全边界 [0, " + MAX_PLAINTEXT_SCALAR + "]: " + plaintext);
        }
        BigInteger m = BigInteger.valueOf(plaintext);
        BigInteger nSquared = n.multiply(n);

        // 选取随机数 r in [1, n - 1]，满足 gcd(r, n) = 1
        BigInteger r;
        do {
            r = new BigInteger(n.bitLength() - 1, secureRandom);
        } while (r.compareTo(BigInteger.ONE) <= 0 || r.compareTo(n) >= 0 || !r.gcd(n).equals(BigInteger.ONE));

        // c = g^m * r^n mod n^2 = ( (1 + m*n) * r^n ) mod n^2
        BigInteger gm = g.modPow(m, nSquared);
        BigInteger rn = r.modPow(n, nSquared);
        BigInteger c = gm.multiply(rn).mod(nSquared);

        return new HomomorphicCiphertextDTO(c, n, nSquared, participantId, metricLabel);
    }

    /**
     * 解密密文 c
     */
    public long decrypt(BigInteger ciphertext, PaillierKeyPair keyPair) {
        BigInteger n = keyPair.getN();
        BigInteger nSquared = keyPair.getNSquared();
        BigInteger lambda = keyPair.getLambda();
        BigInteger mu = keyPair.getMu();

        // u = c^lambda mod n^2
        BigInteger u = ciphertext.modPow(lambda, nSquared);
        // L(u) = (u - 1) / n
        BigInteger l = u.subtract(BigInteger.ONE).divide(n);
        // m = (L(u) * mu) mod n
        BigInteger m = l.multiply(mu).mod(n);

        return m.longValue();
    }

    /**
     * 密文同态加法算子
     * c_sum = c1 * c2 mod n^2
     */
    public BigInteger homomorphicAdd(BigInteger c1, BigInteger c2, BigInteger nSquared) {
        if (c1 == null || c2 == null || nSquared == null) {
            throw new IllegalArgumentException("密文或模数不能为空");
        }
        return c1.multiply(c2).mod(nSquared);
    }

    /**
     * 密文同态标量乘法算子
     * c_prod = c^scalar mod n^2
     */
    public BigInteger homomorphicMultiplyScalar(BigInteger c, long scalar, BigInteger nSquared) {
        if (c == null || nSquared == null) {
            throw new IllegalArgumentException("密文或模数不能为空");
        }
        if (scalar < 0) {
            throw new IllegalArgumentException("标量乘法权重必须非负: " + scalar);
        }
        return c.modPow(BigInteger.valueOf(scalar), nSquared);
    }

    /**
     * 密文加权聚合：C_agg = prod_{i=1}^K (c_i)^w_i mod n^2
     *
     * @param ciphertexts 参与方密文列表
     * @param weights 权重列表
     * @param nSquared 模数平方
     * @return 聚合后总密文
     */
    public BigInteger aggregateWeightedCiphertexts(List<BigInteger> ciphertexts, List<Long> weights, BigInteger nSquared) {
        if (ciphertexts == null || weights == null || ciphertexts.size() != weights.size()) {
            throw new IllegalArgumentException("密文列表与权重列表长度不匹配");
        }
        if (ciphertexts.size() > MAX_HOMOMORPHIC_ADDENDS) {
            throw new IllegalArgumentException("同态累加项突破上限阀门 [MAX=" + MAX_HOMOMORPHIC_ADDENDS + "]: " + ciphertexts.size());
        }

        BigInteger cAgg = BigInteger.ONE;
        for (int i = 0; i < ciphertexts.size(); i++) {
            BigInteger c = ciphertexts.get(i);
            long w = weights.get(i);
            BigInteger weightedC = homomorphicMultiplyScalar(c, w, nSquared);
            cAgg = homomorphicAdd(cAgg, weightedC, nSquared);
        }
        return cAgg;
    }
}
