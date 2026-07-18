package com.medconsult.common.crypto;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 字段加解密 TypeHandler（《修改建议》§5.3 id_no 字段级加密）。
 *
 * <p>落库（setNonNullParameter）加密、读取（getResult）解密，业务侧透明。
 * 实体上声明：{@code @TableField(value="id_no", typeHandler=EncryptedStringTypeHandler.class)}
 * + 类上 {@code @TableName(autoResultMap=true)}。
 *
 * <p><b>Bean 注入难点</b>：MyBatis TypeHandler 由 MyBatis 实例化，拿不到 Spring 管理的
 * {@link CryptoService}。解法：{@link CryptoHolder} 静态 holder，由
 * {@link MedConsultCryptoAutoConfiguration} 在装配时注入单例。
 */
@MappedTypes(String.class)
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        CryptoService crypto = CryptoHolder.get();
        // crypto 为空（未配置加密 key）→ 原样落库（兼容未启用加密的环境/测试）
        ps.setString(i, crypto == null ? parameter : crypto.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return decryptIfPossible(value);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return decryptIfPossible(value);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return decryptIfPossible(value);
    }

    private String decryptIfPossible(String value) {
        if (value == null) return null;
        CryptoService crypto = CryptoHolder.get();
        // crypto 为空 → 原样返回（兼容未启用加密的环境）；CryptoService.decrypt 内部已兼容迁移期明文
        return crypto == null ? value : crypto.decrypt(value);
    }
}
