package com.aiprovider.mapper;

import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketCandleMapperSqlTest {
    @Test
    void rangeQueriesUseExclusiveEndAscendingLimitAndSharedResultMap() throws Exception {
        Method range = MarketCandleMapper.class.getMethod("findRangeAscending", long.class, long.class, long.class, int.class);
        String sql = String.join("", range.getAnnotation(Select.class).value());
        assertTrue(sql.contains("OpenTimeMs >= #{startOpenTimeMsInclusive}"));
        assertTrue(sql.contains("OpenTimeMs < #{endOpenTimeMsExclusive}"));
        assertTrue(sql.contains("ORDER BY OpenTimeMs ASC"));
        assertTrue(sql.contains("LIMIT #{limit}"));
        assertTrue(range.isAnnotationPresent(ResultMap.class));

        Method count = MarketCandleMapper.class.getMethod("countRangeExclusive", long.class, long.class, long.class);
        String countSql = String.join("", count.getAnnotation(Select.class).value());
        assertTrue(countSql.contains("OpenTimeMs >= #{startOpenTimeMsInclusive}"));
        assertTrue(countSql.contains("OpenTimeMs < #{endOpenTimeMsExclusive}"));
    }
}
