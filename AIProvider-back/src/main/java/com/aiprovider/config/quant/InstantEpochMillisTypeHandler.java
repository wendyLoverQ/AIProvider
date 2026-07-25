package com.aiprovider.config.quant;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * MyBatis TypeHandler：{@link Instant} 与 BIGINT epoch milliseconds 互转。
 *
 * 用于 q_market_candle.OpenTimeMs / CloseTimeMs、q_market_dataset.EarliestOpenTimeMs / LatestOpenTimeMs
 * 等以 epoch 毫秒存储的 BIGINT 列。不注册 @MappedTypes，只在 @Result 中显式指定，
 * 避免影响 DATETIME(6) 列（CreatedAt、UpdatedAt 等使用 MyBatis 内置 InstantTypeHandler）。
 */
public class InstantEpochMillisTypeHandler extends BaseTypeHandler<Instant> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType) throws SQLException {
        ps.setLong(i, parameter.toEpochMilli());
    }

    @Override
    public Instant getNullableResult(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    @Override
    public Instant getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        long value = rs.getLong(columnIndex);
        return rs.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    @Override
    public Instant getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        long value = cs.getLong(columnIndex);
        return cs.wasNull() ? null : Instant.ofEpochMilli(value);
    }
}
