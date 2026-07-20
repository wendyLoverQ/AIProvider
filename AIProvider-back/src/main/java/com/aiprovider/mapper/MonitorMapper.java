package com.aiprovider.mapper;

import com.aiprovider.model.HttpRequestMetric;
import org.apache.ibatis.annotations.*;
import java.util.*;

@Mapper
public interface MonitorMapper {
    @Insert("INSERT INTO c_HttpRequestMetrics(Method,Route,StatusCode,DurationMs,CreatedAt) VALUES(#{method},#{route},#{statusCode},#{durationMs},#{createdAt})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertHttpRequest(HttpRequestMetric metric);

    @Select("SELECT COUNT(*) totalRequests, " +
        "COALESCE(SUM(CASE WHEN StatusCode BETWEEN 200 AND 399 THEN 1 ELSE 0 END),0) successCount, " +
        "COALESCE(SUM(CASE WHEN StatusCode NOT BETWEEN 200 AND 399 THEN 1 ELSE 0 END),0) failureCount, " +
        "COALESCE(AVG(DurationMs),0) avgDurationMs, 0 inputTokens, 0 outputTokens " +
        "FROM c_HttpRequestMetrics WHERE CreatedAt >= #{since}")
    Map<String,Object> todayOverview(@Param("since") java.time.LocalDateTime since);

    @Select("WITH ranked AS (SELECT DurationMs, ROW_NUMBER() OVER (ORDER BY DurationMs) rn, COUNT(*) OVER () total " +
        "FROM c_HttpRequestMetrics WHERE CreatedAt >= #{since}) " +
        "SELECT COALESCE(MAX(CASE WHEN rn=CEIL(total*0.95) THEN DurationMs END),0) FROM ranked")
    Long todayP95(@Param("since") java.time.LocalDateTime since);

    @Select("<script>SELECT " +
        "<choose><when test='hours == 24'>DATE_FORMAT(CreatedAt,'%Y-%m-%dT%H:00:00')</when><otherwise>DATE_FORMAT(CreatedAt,'%Y-%m-%d')</otherwise></choose> bucket, " +
        "COUNT(*) totalRequests, SUM(CASE WHEN StatusCode BETWEEN 200 AND 399 THEN 1 ELSE 0 END) successCount, " +
        "SUM(CASE WHEN StatusCode NOT BETWEEN 200 AND 399 THEN 1 ELSE 0 END) failureCount, " +
        "AVG(DurationMs) avgDurationMs, 0 inputTokens, 0 outputTokens " +
        "FROM c_HttpRequestMetrics WHERE CreatedAt >= DATE_SUB(NOW(), INTERVAL #{hours} HOUR) GROUP BY bucket ORDER BY bucket</script>")
    List<Map<String,Object>> timeseries(@Param("hours") int hours);

    @Select("WITH base AS (SELECT " +
        "CASE WHEN #{hours}=24 THEN DATE_FORMAT(CreatedAt,'%Y-%m-%dT%H:00:00') ELSE DATE_FORMAT(CreatedAt,'%Y-%m-%d') END bucket, DurationMs " +
        "FROM c_HttpRequestMetrics WHERE CreatedAt >= DATE_SUB(NOW(), INTERVAL #{hours} HOUR)), " +
        "ranked AS (SELECT bucket, DurationMs, ROW_NUMBER() OVER(PARTITION BY bucket ORDER BY DurationMs) rn, COUNT(*) OVER(PARTITION BY bucket) total FROM base) " +
        "SELECT bucket, MAX(CASE WHEN rn=CEIL(total*0.95) THEN DurationMs END) p95DurationMs FROM ranked GROUP BY bucket")
    List<Map<String,Object>> timeseriesP95(@Param("hours") int hours);

    @Select("SELECT Provider provider, Model model, COUNT(*) callCount, " +
        "MAX(CASE WHEN ResponseStatusCode BETWEEN 200 AND 399 AND COALESCE(Error,'')='' THEN CompletedAt END) lastSuccessAt, " +
        "MAX(CASE WHEN NOT (ResponseStatusCode BETWEEN 200 AND 399 AND COALESCE(Error,'')='') THEN CompletedAt END) lastFailureAt " +
        "FROM maid_LlmCallLogs WHERE CreatedAt >= DATE_SUB(NOW(), INTERVAL 30 DAY) GROUP BY Provider,Model ORDER BY callCount DESC")
    List<Map<String,Object>> providerActivity();

    @Select("SELECT DefaultProvider defaultProvider, SelectedProvider selectedProvider, LocalQwenModel localQwenModel, GeminiModel geminiModel, UpdatedAt updatedAt FROM maid_LlmProviderSelections ORDER BY UpdatedAt DESC LIMIT 1")
    Map<String,Object> providerSelection();

    @Select("<script>SELECT Id id, CreatedAt occurredAt, Source requestType, Provider provider, Model model, ResponseStatusCode statusCode, DurationMs durationMs, LEFT(Error,1000) error " +
        "FROM maid_LlmCallLogs WHERE CreatedAt &gt;= DATE_SUB(NOW(), INTERVAL #{hours} HOUR) AND NOT (ResponseStatusCode BETWEEN 200 AND 399 AND COALESCE(Error,'')='') " +
        "<if test='provider != null and provider != &quot;&quot;'>AND Provider=#{provider}</if><if test='model != null and model != &quot;&quot;'>AND Model=#{model}</if> " +
        "ORDER BY CreatedAt DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String,Object>> failures(@Param("hours") int hours,@Param("provider") String provider,@Param("model") String model,@Param("limit") int limit,@Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM maid_LlmCallLogs WHERE CreatedAt &gt;= DATE_SUB(NOW(), INTERVAL #{hours} HOUR) AND NOT (ResponseStatusCode BETWEEN 200 AND 399 AND COALESCE(Error,'')='') " +
        "<if test='provider != null and provider != &quot;&quot;'>AND Provider=#{provider}</if><if test='model != null and model != &quot;&quot;'>AND Model=#{model}</if></script>")
    long failureCount(@Param("hours") int hours,@Param("provider") String provider,@Param("model") String model);

    @Delete("DELETE FROM maid_LlmCallLogs WHERE CreatedAt &lt; DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int deleteExpired(@Param("days") int days);

    @Select("SELECT COUNT(*) FROM maid_LlmCallLogs WHERE CreatedAt &lt; DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int countExpired(@Param("days") int days);

    @Select("SELECT COUNT(*) FROM c_HttpRequestMetrics WHERE CreatedAt &lt; DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int countExpiredHttpRequests(@Param("days") int days);

    @Delete("DELETE FROM c_HttpRequestMetrics WHERE CreatedAt &lt; DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int deleteExpiredHttpRequests(@Param("days") int days);
}
