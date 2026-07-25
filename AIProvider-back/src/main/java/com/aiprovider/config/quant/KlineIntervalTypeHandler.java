package com.aiprovider.config.quant;

import com.aiprovider.quant.market.model.KlineInterval;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis TypeHandler：{@link KlineInterval} 与 VARCHAR code 互转。
 *
 * 数据库存储 Binance interval code（如 "1m"），不是枚举名（如 "M1"）。
 * 使用 {@link KlineInterval#fromCode(String)} 解析，使用 {@link KlineInterval#code()} 写入。
 *
 * 其他枚举（MarketProviderId、MarketType 等）使用 MyBatis 内置 EnumTypeHandler，
 * 走枚举 name()，不走此处理器。
 */
@MappedTypes(KlineInterval.class)
public class KlineIntervalTypeHandler extends BaseTypeHandler<KlineInterval> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, KlineInterval parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.code());
    }

    @Override
    public KlineInterval getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String code = rs.getString(columnName);
        return code == null ? null : KlineInterval.fromCode(code);
    }

    @Override
    public KlineInterval getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String code = rs.getString(columnIndex);
        return code == null ? null : KlineInterval.fromCode(code);
    }

    @Override
    public KlineInterval getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String code = cs.getString(columnIndex);
        return code == null ? null : KlineInterval.fromCode(code);
    }
}
