package com.aiprovider.mapper;

import com.aiprovider.config.quant.InstantEpochMillisTypeHandler;
import com.aiprovider.config.quant.KlineIntervalTypeHandler;
import com.aiprovider.quant.market.history.model.MarketDataset;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * q_market_dataset 表 MyBatis Mapper。
 *
 * EarliestOpenTimeMs、LatestOpenTimeMs 以 epoch 毫秒 BIGINT 存储，
 * 使用 {@link InstantEpochMillisTypeHandler}。
 * IntervalCode 使用 {@link KlineIntervalTypeHandler}。
 * LastSuccessfulSyncAt、LastValidatedAt、CreatedAt、UpdatedAt 为 DATETIME(6)，
 * 使用 MyBatis 内置 InstantTypeHandler。
 */
@Mapper
public interface MarketDatasetMapper {

    String COLUMNS = "Id, Provider, MarketType, DataType, Symbol, IntervalCode, " +
            "EarliestOpenTimeMs, LatestOpenTimeMs, CandleCount, ExpectedInsideRange, " +
            "GapCount, GapSegmentCount, Status, LastSuccessfulSyncAt, LastValidatedAt, LastSyncTaskId, " +
            "CreatedAt, UpdatedAt";

    @Results(id = "datasetResult", value = {
            @Result(column = "Id", property = "id"),
            @Result(column = "Provider", property = "provider"),
            @Result(column = "MarketType", property = "marketType"),
            @Result(column = "DataType", property = "dataType"),
            @Result(column = "Symbol", property = "symbol"),
            @Result(column = "IntervalCode", property = "interval", typeHandler = KlineIntervalTypeHandler.class),
            @Result(column = "EarliestOpenTimeMs", property = "earliestOpenTime", typeHandler = InstantEpochMillisTypeHandler.class),
            @Result(column = "LatestOpenTimeMs", property = "latestOpenTime", typeHandler = InstantEpochMillisTypeHandler.class),
            @Result(column = "CandleCount", property = "candleCount"),
            @Result(column = "ExpectedInsideRange", property = "expectedInsideRange"),
            @Result(column = "GapCount", property = "gapCount"),
            @Result(column = "GapSegmentCount", property = "gapSegmentCount"),
            @Result(column = "Status", property = "status"),
            @Result(column = "LastSuccessfulSyncAt", property = "lastSuccessfulSyncAt"),
            @Result(column = "LastValidatedAt", property = "lastValidatedAt"),
            @Result(column = "LastSyncTaskId", property = "lastSyncTaskId"),
            @Result(column = "CreatedAt", property = "createdAt"),
            @Result(column = "UpdatedAt", property = "updatedAt")
    })
    @Select("SELECT " + COLUMNS + " FROM q_market_dataset " +
            "WHERE Provider=#{provider} AND MarketType=#{marketType} " +
            "AND DataType=#{dataType} AND Symbol=#{symbol} AND IntervalCode=#{intervalCode}")
    MarketDataset findByKey(@Param("provider") String provider,
                            @Param("marketType") String marketType,
                            @Param("dataType") String dataType,
                            @Param("symbol") String symbol,
                            @Param("intervalCode") String intervalCode);

    @ResultMap("datasetResult")
    @Select("SELECT " + COLUMNS + " FROM q_market_dataset WHERE Id=#{id}")
    MarketDataset findById(@Param("id") long id);

    @ResultMap("datasetResult")
    @Select("<script>" +
            "SELECT " + COLUMNS + " FROM q_market_dataset WHERE 1=1 " +
            "<if test='provider != null'>AND Provider=#{provider} </if>" +
            "<if test='symbol != null'>AND Symbol=#{symbol} </if>" +
            "<if test='intervalCode != null'>AND IntervalCode=#{intervalCode} </if>" +
            "<if test='status != null'>AND Status=#{status} </if>" +
            "ORDER BY UpdatedAt DESC, Id DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<MarketDataset> findPage(@Param("provider") String provider,
                                 @Param("symbol") String symbol,
                                 @Param("intervalCode") String intervalCode,
                                 @Param("status") String status,
                                 @Param("limit") int limit,
                                 @Param("offset") int offset);

    @Insert("INSERT INTO q_market_dataset(Provider,MarketType,DataType,Symbol,IntervalCode," +
            "EarliestOpenTimeMs,LatestOpenTimeMs,CandleCount,ExpectedInsideRange,GapCount,Status," +
            "LastSuccessfulSyncAt,LastValidatedAt,LastSyncTaskId) VALUES(" +
            "#{provider},#{marketType},#{dataType},#{symbol},#{interval.code}," +
            "#{earliestOpenTime, typeHandler=com.aiprovider.config.quant.InstantEpochMillisTypeHandler}," +
            "#{latestOpenTime, typeHandler=com.aiprovider.config.quant.InstantEpochMillisTypeHandler}," +
            "#{candleCount},#{expectedInsideRange},#{gapCount}," +
            "#{status},#{lastSuccessfulSyncAt},#{lastValidatedAt}," +
            "#{lastSyncTaskId})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "Id")
    int insert(MarketDataset dataset);

    @Update("UPDATE q_market_dataset SET " +
            "EarliestOpenTimeMs=#{earliestOpenTime, typeHandler=com.aiprovider.config.quant.InstantEpochMillisTypeHandler}," +
            "LatestOpenTimeMs=#{latestOpenTime, typeHandler=com.aiprovider.config.quant.InstantEpochMillisTypeHandler}," +
            "CandleCount=#{candleCount}," +
            "ExpectedInsideRange=#{expectedInsideRange}," +
            "GapCount=#{gapCount}," +
            "GapSegmentCount=#{gapSegmentCount}," +
            "Status=#{status}," +
            "LastValidatedAt=#{lastValidatedAt} " +
            "WHERE Id=#{id}")
    int updateStats(MarketDataset dataset);

    @Update("UPDATE q_market_dataset SET LastSyncTaskId=#{lastSyncTaskId}," +
            "LastSuccessfulSyncAt=#{lastSuccessfulSyncAt} WHERE Id=#{datasetId}")
    int updateLastSync(@Param("datasetId") long datasetId,
                       @Param("lastSyncTaskId") String lastSyncTaskId,
                       @Param("lastSuccessfulSyncAt") java.time.Instant lastSuccessfulSyncAt);

    @Select("SELECT COUNT(*) FROM q_market_dataset")
    long countAll();

    @Select("SELECT COUNT(*) FROM q_market_dataset WHERE GapCount > 0")
    long countWithGaps();

    @Select("SELECT COUNT(*) FROM q_market_dataset WHERE CandleCount > 0")
    long countWithCandles();

    @Select("SELECT COUNT(*) FROM q_market_dataset WHERE CandleCount > 0 AND Status != 'CONTIGUOUS'")
    long countWithCandlesNotContiguous();
}
