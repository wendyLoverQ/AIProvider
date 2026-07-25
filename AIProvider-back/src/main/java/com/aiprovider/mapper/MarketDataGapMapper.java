package com.aiprovider.mapper;

import com.aiprovider.config.quant.InstantEpochMillisTypeHandler;
import com.aiprovider.quant.market.history.model.MarketDataGap;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * q_market_data_gap 表 MyBatis Mapper。
 *
 * StartOpenTimeMs、EndOpenTimeMsExclusive 以 epoch 毫秒 BIGINT 存储，
 * 使用 {@link InstantEpochMillisTypeHandler}。
 * DetectedAt 为 DATETIME(6)，使用 MyBatis 内置 InstantTypeHandler。
 */
@Mapper
public interface MarketDataGapMapper {

    @Results(id = "gapResult", value = {
            @Result(column = "Id", property = "id"),
            @Result(column = "DatasetId", property = "datasetId"),
            @Result(column = "StartOpenTimeMs", property = "startOpenTime", typeHandler = InstantEpochMillisTypeHandler.class),
            @Result(column = "EndOpenTimeMsExclusive", property = "endOpenTimeExclusive", typeHandler = InstantEpochMillisTypeHandler.class),
            @Result(column = "MissingCount", property = "missingCount"),
            @Result(column = "DetectedByTaskId", property = "detectedByTaskId"),
            @Result(column = "DetectedAt", property = "detectedAt")
    })
    @Select("SELECT Id, DatasetId, StartOpenTimeMs, EndOpenTimeMsExclusive, MissingCount, " +
            "DetectedByTaskId, DetectedAt FROM q_market_data_gap " +
            "WHERE DatasetId=#{datasetId} ORDER BY StartOpenTimeMs ASC")
    List<MarketDataGap> findByDataset(@Param("datasetId") long datasetId);

    @Delete("DELETE FROM q_market_data_gap WHERE DatasetId=#{datasetId}")
    int deleteByDataset(@Param("datasetId") long datasetId);

    @Insert("<script>" +
            "INSERT INTO q_market_data_gap(DatasetId,StartOpenTimeMs,EndOpenTimeMsExclusive," +
            "MissingCount,DetectedByTaskId,DetectedAt) VALUES " +
            "<foreach collection='gaps' item='g' separator=','>" +
            "(#{g.datasetId},#{g.startOpenTime.toEpochMilli},#{g.endOpenTimeExclusive.toEpochMilli}," +
            "#{g.missingCount},#{g.detectedByTaskId},#{g.detectedAt})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("gaps") List<MarketDataGap> gaps);
}
