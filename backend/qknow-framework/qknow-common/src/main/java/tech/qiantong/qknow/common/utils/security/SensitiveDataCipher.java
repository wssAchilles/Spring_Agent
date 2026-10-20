package tech.qiantong.qknow.common.utils.security;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 敏感字段加解密工具（AES/Base64）。
 * 与 {@code EncryptTypeHandler} 共用同一套口令解析与密钥派生；
 * 非 MyBatis 进程（如 Hermes）可通过 {@link #configure(String)} 注入与主应用一致的口令。
 */
@Slf4j
public final class SensitiveDataCipher {

    private static final String ENCRYPTOR_PROPERTY_NAME = "mybatis-plus.encryptor.password";
    private static final String TOKEN_SECRET_PROPERTY_NAME = "token.secret";
    private static final String DEFAULT_PASSWORD = "qknow-default-aes-key-32bytes-2026!";

    private static volatile AES aes;
    private static volatile String configuredPassword;

    private SensitiveDataCipher() {
    }

    /**
     * 显式注入加密口令；非空时优先于 Spring 配置。
     */
    public static void configure(String password) {
        if (password != null && !password.isBlank()) {
            configuredPassword = password;
            aes = null;
        }
    }

    public static String encrypt(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return getEncryptor().encryptBase64(rawValue);
    }

    public static String decrypt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return getEncryptor().decryptStr(value);
        } catch (Exception e) {
            // 兼容既有存量明文数据（例如未加密的 sk-...）
            return value;
        }
    }

    private static AES getEncryptor() {
        AES current = aes;
        if (current != null) {
            return current;
        }
        synchronized (SensitiveDataCipher.class) {
            if (aes != null) {
                return aes;
            }
            String password = configuredPassword;
            if (password == null || password.isBlank()) {
                try {
                    password = SpringUtil.getProperty(ENCRYPTOR_PROPERTY_NAME);
                    if (password == null || password.isBlank()) {
                        password = SpringUtil.getProperty(TOKEN_SECRET_PROPERTY_NAME);
                    }
                } catch (Exception ignored) {
                    // 非 Spring 环境（如纯单元测试）继续使用显式口令或默认口令
                }
            }
            if (password == null || password.isBlank()) {
                password = DEFAULT_PASSWORD;
            }
            byte[] keyBytes = new byte[32];
            byte[] pwdBytes = password.getBytes();
            System.arraycopy(pwdBytes, 0, keyBytes, 0, Math.min(pwdBytes.length, 32));
            aes = SecureUtil.aes(keyBytes);
            return aes;
        }
    }
}
