package tech.qiantong.qknow.mybatis.core.type;

import cn.hutool.core.lang.Assert;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.extra.spring.SpringUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 字段字段的 TypeHandler 实现类，基于 {@link AES} 实现
 * 可通过 jasypt.encryptor.password 配置项，设置密钥
 *
 * @author 芋道源码
 */
public class EncryptTypeHandler extends BaseTypeHandler<String> {

    private static final String ENCRYPTOR_PROPERTY_NAME = "mybatis-plus.encryptor.password";

    private static AES aes;

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return decrypt(value);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return decrypt(value);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return decrypt(value);
    }

    private static String decrypt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return getEncryptor().decryptStr(value);
        } catch (Exception e) {
            // 兼容既有存量明文数据 (例如未加密的 sk-...)
            return value;
        }
    }

    public static String encrypt(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return getEncryptor().encryptBase64(rawValue);
    }

    private static AES getEncryptor() {
        if (aes != null) {
            return aes;
        }
        // 构建 AES
        String password = null;
        try {
            password = SpringUtil.getProperty(ENCRYPTOR_PROPERTY_NAME);
            if (password == null || password.isBlank()) {
                password = SpringUtil.getProperty("token.secret");
            }
        } catch (Exception ignored) {
        }
        if (password == null || password.isBlank()) {
            password = "qknow-default-aes-key-32bytes-2026!";
        }
        byte[] keyBytes = new byte[32];
        byte[] pwdBytes = password.getBytes();
        System.arraycopy(pwdBytes, 0, keyBytes, 0, Math.min(pwdBytes.length, 32));
        aes = SecureUtil.aes(keyBytes);
        return aes;
    }

}
