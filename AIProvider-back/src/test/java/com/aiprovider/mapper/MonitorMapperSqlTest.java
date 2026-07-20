package com.aiprovider.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class MonitorMapperSqlTest {
    @Test
    void serviceRequestAggregatesUseHttpMetricsInsteadOfMaidLlmLogs() throws Exception {
        assertHttpMetricQuery("todayOverview",java.time.LocalDateTime.class);
        assertHttpMetricQuery("todayP95",java.time.LocalDateTime.class);
        assertHttpMetricQuery("timeseries",int.class);
        assertHttpMetricQuery("timeseriesP95",int.class);
    }

    @Test
    void insertUsesApplicationTimestampInsteadOfDatabaseSessionTime() throws Exception {
        Method method=MonitorMapper.class.getMethod("insertHttpRequest",com.aiprovider.model.HttpRequestMetric.class);
        String sql=method.getAnnotation(org.apache.ibatis.annotations.Insert.class).value()[0];
        assertTrue(sql.contains("CreatedAt"));
        assertTrue(sql.contains("#{createdAt}"));
    }

    private void assertHttpMetricQuery(String name,Class<?> parameter) throws Exception {
        Method method=MonitorMapper.class.getMethod(name,parameter);
        String sql=String.join(" ",method.getAnnotation(Select.class).value());
        assertTrue(sql.contains("c_HttpRequestMetrics"),name+" must query HTTP request metrics");
        assertFalse(sql.contains("maid_LlmCallLogs"),name+" must not query Maid LLM calls");
    }
}
