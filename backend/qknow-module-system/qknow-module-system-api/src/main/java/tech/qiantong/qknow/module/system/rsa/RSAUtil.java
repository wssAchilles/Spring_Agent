package tech.qiantong.qknow.module.system.rsa;

import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;
import tech.qiantong.qknow.common.utils.StringUtils;

import java.util.Base64;

/**
 * RSA 加解密工具
 * 私钥/公钥通过环境变量注入，不再打包在 classpath 中。
 *
 * 环境变量：
 *   RSA_PRIVATE_KEY — PKCS#8 私钥（Base64 编码，不含 PEM 头尾）
 *   RSA_PUBLIC_KEY  — X.509 公钥（Base64 编码，不含 PEM 头尾）
 */
public class RSAUtil {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RSAUtil.class);

    private static final String PRIVATE_KEY;
    private static final String PUBLIC_KEY;

    static {
        PRIVATE_KEY = System.getenv("RSA_PRIVATE_KEY");
        PUBLIC_KEY = System.getenv("RSA_PUBLIC_KEY");
    }

    /**
     * 使用私钥解密数据
     * @param encryptedData 用公钥加密过后的数据
     * @return 明文
     */
    public static String decryptData(String encryptedData) {
        try {
            if (StringUtils.isBlank(PRIVATE_KEY)) {
                log.error("RSA_PRIVATE_KEY 环境变量未配置");
                return null;
            }
            RSA rsa = new RSA(PRIVATE_KEY, null);
            byte[] decryptedBytes = rsa.decrypt(encryptedData, KeyType.PrivateKey);
            return new String(decryptedBytes);
        } catch (Exception e) {
            log.error("RSA 私钥解密失败", e);
            return null;
        }
    }

    /**
     * 使用私钥加密数据
     * @param data 要加密的明文数据
     * @return 加密后的数据（Base64 编码）
     */
    public static String encryptData(String data) {
        try {
            if (data == null) return "";
            if (StringUtils.isBlank(PRIVATE_KEY)) {
                log.error("RSA_PRIVATE_KEY 环境变量未配置");
                return null;
            }
            RSA rsa = new RSA(PRIVATE_KEY, null);
            byte[] encryptedBytes = rsa.encrypt(data.getBytes(), KeyType.PrivateKey);
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("RSA 私钥加密失败", e);
            return null;
        }
    }

    /**
     * 使用公钥解密数据
     * @param encryptedData 使用私钥加密过后的数据
     * @return 解密后的明文
     */
    public static String decryptWithPublicKey(String encryptedData) {
        try {
            if (StringUtils.isBlank(encryptedData)) return null;
            if (StringUtils.isBlank(PUBLIC_KEY)) {
                log.error("RSA_PUBLIC_KEY 环境变量未配置");
                return null;
            }
            RSA rsa = new RSA(null, PUBLIC_KEY);
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedBytes = rsa.decrypt(encryptedBytes, KeyType.PublicKey);
            return new String(decryptedBytes);
        } catch (Exception e) {
            log.error("RSA 公钥解密失败", e);
            return null;
        }
    }
}
