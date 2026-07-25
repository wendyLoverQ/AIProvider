package com.aiprovider.mapper;

import com.aiprovider.config.quant.InstantEpochMillisTypeHandler;
import com.aiprovider.config.quant.KlineIntervalTypeHandler;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * q_market_candle 表 MyBatis Mapper。
 *
 * 所有时间字段（OpenTimeMs、CloseTimeMs）以 epoch 毫秒 BIGINT 存储，
 * 使用 {@link InstantEpochMillisTypeHandler} 与 {@link java.time.Instant} 互转。
 * IntervalCode 使用 {@link KlineIntervalTypeHandler} 与 {@link com.aiprovider.quant.market.model.KlineInterval} 互转。
 */
@Mapper
public interface MarketCandleMapper {

    @Results(id = "candleResult", value = {
            @Result(column = "Id", property = "id"),
            @Result(column = "DatasetId", property = "datasetId"),
            @Result(column = "Provider", property = "provider"),
            @Result(column = "MarketType", property = "marketType"),
            @Result(column = "Symbol", property = "symbol"),
            @Result(column = "IntervalCode", property = "interval", typeHandler = KlineIntervalTypeHandler.class),
            @Result(column = "OpenTimeMs", property = "openTime", typeHandler = InstantEpochMillisTypeHandler.class),
            @Result(column = "CloseTimeMs", property = "closeTime", typeHandler = InstantEpochMillisTypeHandler.class),
            @Result(column = "OpenPrice", property = "openPrice"),
            @Result(column = "HighPrice", property = "highPrice"),
            @Result(column = "LowPrice", property = "lowPrice"),
            @Result(column = "ClosePrice", property = "closePrice"),
            @Result(column = "Volume", property = "volume"),
            @Result(column = "QuoteVolume", property = "quoteVolume"),
            @Result(column = "TradeCount", property = "tradeCount"),
            @Result(column = "TakerBuyBaseVolume", property = "takerBuyBaseVolume"),
            @Result(column = "TakerBuyQuoteVolume", property = "takerBuyQuoteVolume"),
            @Result(column = "Source", property = "source"),
            @Result(column = "CreatedAt", property = "createdAt")
    })
    @Select("<script>" +
            "SELECT Id, DatasetId, Provider, MarketType, Symbol, IntervalCode, " +
            "OpenTimeMs, CloseTimeMs, OpenPrice, HighPrice, LowPrice, ClosePrice, " +
            "Volume, QuoteVolume, TradeCount, TakerBuyBaseVolume, TakerBuyQuoteVolume, " +
            "Source, CreatedAt FROM q_market_candle " +
            "WHERE DatasetId=#{datasetId} AND OpenTimeMs IN " +
            "<foreach collection='openTimeMs' item='ot' open='(' separator=',' close=')'>#{ot}</foreach> " +
            "ORDER BY OpenTimeMs ASC" +
            "</script>")
    List<HistoricalCandle> findByOpenTimes(@Param("datasetId") long datasetId,
                                            @Param("openTimeMs") List<Long> openTimeMs);

    @Insert("<script>" +
            "INSERT INTO q_market_candle(DatasetId,Provider,MarketType,Symbol,IntervalCode," +
            "OpenTimeMs,CloseTimeMs,OpenPrice,HighPrice,LowPrice,ClosePrice,Volume,QuoteVolume," +
            "TradeCount,TakerBuyBaseVolume,TakerBuyQuoteVolume,Source) VALUES " +
            "<foreach collection='candles' item='c' separator=','>" +
            "(#{c.datasetId},#{c.provider},#{c.marketType},#{c.symbol},#{c.interval.code}," +
            "#{c.openTime.toEpochMilli},#{c.closeTime.toEpochMilli}," +
            "#{c.openPrice},#{c.highPrice},#{c.lowPrice},#{c.closePrice}," +
            "#{c.volume},#{c.quoteVolume},#{c.tradeCount}," +
            "#{c.takerBuyBaseVolume},#{c.takerBuyQuoteVolume},#{c.source})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("candles") List<HistoricalCandle> candles);

    @ResultMap("candleResult")
    @Select("<script>" +
            "SELECT Id, DatasetId, Provider, MarketType, Symbol, IntervalCode, " +
            "OpenTimeMs, CloseTimeMs, OpenPrice, HighPrice, LowPrice, ClosePrice, " +
            "Volume, QuoteVolume, TradeCount, TakerBuyBaseVolume, TakerBuyQuoteVolume, " +
            "Source, CreatedAt FROM q_market_candle " +
            "WHERE DatasetId=#{datasetId} " +
            "<if test='startOpenTimeMs != null'>AND OpenTimeMs &gt;= #{startOpenTimeMs} </if>" +
            "<if test='endOpenTimeMs != null'>AND OpenTimeMs &lt;= #{endOpenTimeMs} </if>" +
            "ORDER BY OpenTimeMs DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<HistoricalCandle> findPage(@Param("datasetId") long datasetId,
                                     @Param("startOpenTimeMs") Long startOpenTimeMs,
                                     @Param("endOpenTimeMs") Long endOpenTimeMs,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM q_market_candle WHERE DatasetId=#{datasetId}")
    long countByDataset(@Param("datasetId") long datasetId);

    @Select("<script>" +
            "SELECT COUNT(*) FROM q_market_candle WHERE DatasetId=#{datasetId} " +
            "<if test='startOpenTimeMs != null'>AND OpenTimeMs &gt;= #{startOpenTimeMs} </if>" +
            "<if test='endOpenTimeMs != null'>AND OpenTimeMs &lt;= #{endOpenTimeMs} </if>" +
            "</script>")
    long countByDatasetAndRange(@Param("datasetId") long datasetId,
                                @Param("startOpenTimeMs") Long startOpenTimeMs,
                                @Param("endOpenTimeMs") Long endOpenTimeMs);

    @Select("SELECT OpenTimeMs FROM q_market_candle " +
            "WHERE DatasetId=#{datasetId} AND OpenTimeMs > #{afterOpenTimeMs} " +
            "ORDER BY OpenTimeMs ASC LIMIT #{batchSize}")
    List<Long> streamOpenTimesAscending(@Param("datasetId") long datasetId,
                                         @Param("batchSize") int batchSize,
                                         @Param("afterOpenTimeMs") long afterOpenTimeMs);
}
