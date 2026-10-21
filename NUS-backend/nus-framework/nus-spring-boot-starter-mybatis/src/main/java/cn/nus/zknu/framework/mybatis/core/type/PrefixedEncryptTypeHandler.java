package cn.nus.zknu.framework.mybatis.core.type;

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
 * 带格式前缀的加密字段处理器，可兼容迁移前的明文值。
 */
public class PrefixedEncryptTypeHandler extends BaseTypeHandler<String> {

    public static final String PREFIX = "ENC$";
    private static final String ENCRYPTOR_PROPERTY_NAME = "mybatis-plus.encryptor.password";
    private static AES aes;

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, PREFIX + getEncryptor().encryptBase64(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decryptIfNecessary(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decryptIfNecessary(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decryptIfNecessary(cs.getString(columnIndex));
    }

    private String decryptIfNecessary(String value) {
        if (value == null || !value.startsWith(PREFIX)) {
            return value;
        }
        return getEncryptor().decryptStr(value.substring(PREFIX.length()));
    }

    private static AES getEncryptor() {
        if (aes != null) {
            return aes;
        }
        String password = SpringUtil.getProperty(ENCRYPTOR_PROPERTY_NAME);
        Assert.notEmpty(password, "配置项({}) 不能为空", ENCRYPTOR_PROPERTY_NAME);
        aes = SecureUtil.aes(SecureUtil.sha256().digest(password));
        return aes;
    }

}
