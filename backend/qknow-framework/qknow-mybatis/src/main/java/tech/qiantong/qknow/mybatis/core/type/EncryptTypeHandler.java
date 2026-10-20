package tech.qiantong.qknow.mybatis.core.type;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import tech.qiantong.qknow.common.utils.security.SensitiveDataCipher;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 字段加密 TypeHandler 实现类，基于 AES 实现。
 * 加解密逻辑统一委托给 {@link SensitiveDataCipher}，便于非 MyBatis 进程复用。
 *
 * @author 芋道源码
 */
public class EncryptTypeHandler extends BaseTypeHandler<String> {

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
        return SensitiveDataCipher.decrypt(value);
    }

    public static String encrypt(String rawValue) {
        return SensitiveDataCipher.encrypt(rawValue);
    }

    /**
     * 供非 MyBatis 进程显式注入与主应用一致的加密口令。
     */
    public static void configure(String password) {
        SensitiveDataCipher.configure(password);
    }

    /**
     * 解密密文；无法解密时按明文兼容返回。
     */
    public static String decryptValue(String value) {
        return SensitiveDataCipher.decrypt(value);
    }
}
