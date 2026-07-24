package com.aiprovider.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class AsrRecordMapperSqlTest {
    @Test void quotaWindowSqlUsesExecutableComparisonOperators() throws Exception {Method method=AsrRecordMapper.class.getMethod("sumAudioDurationMs",String.class,String.class,java.time.LocalDateTime.class,java.time.LocalDateTime.class);String sql=String.join(" ",method.getAnnotation(Select.class).value());assertTrue(sql.contains("CreatedAt >= #{start}"));assertTrue(sql.contains("CreatedAt < #{end}"));assertFalse(sql.contains("&gt;"));assertFalse(sql.contains("&lt;"));}
    @Test void requestCountSqlUsesTheSameExecutableDayWindow() throws Exception {Method method=AsrRecordMapper.class.getMethod("countRequests",String.class,String.class,java.time.LocalDateTime.class,java.time.LocalDateTime.class);String sql=String.join(" ",method.getAnnotation(Select.class).value());assertTrue(sql.contains("COUNT(*)"));assertTrue(sql.contains("CreatedAt >= #{start}"));assertTrue(sql.contains("CreatedAt < #{end}"));assertFalse(sql.contains("&gt;"));assertFalse(sql.contains("&lt;"));}
}
