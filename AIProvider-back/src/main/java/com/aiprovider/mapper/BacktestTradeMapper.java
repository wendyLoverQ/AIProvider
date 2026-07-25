package com.aiprovider.mapper;
import org.apache.ibatis.annotations.*; import java.util.List; import java.util.Map;
@Mapper public interface BacktestTradeMapper {
 @Insert("<script>INSERT INTO q_backtest_trade(RunId,TradeNo,EntrySignalIndex,EntryIndex,EntryTimeMs,EntryPrice,ExitSignalIndex,ExitIndex,ExitTimeMs,ExitPrice,Amount,GrossProfit,Fee,NetProfit,ReturnRatio,BarsHeld,ForcedExit,ExitReason) VALUES <foreach collection='rows' item='r' separator=','>(#{r.runId},#{r.tradeNo},#{r.entrySignalIndex},#{r.entryIndex},#{r.entryTimeMs},#{r.entryPrice},#{r.exitSignalIndex},#{r.exitIndex},#{r.exitTimeMs},#{r.exitPrice},#{r.amount},#{r.grossProfit},#{r.fee},#{r.netProfit},#{r.returnRatio},#{r.barsHeld},#{r.forcedExit},#{r.exitReason})</foreach></script>") int insertBatch(@Param("rows") List<Map<String,Object>> rows);
 @Select("SELECT * FROM q_backtest_trade WHERE RunId=#{runId} ORDER BY TradeNo ASC LIMIT #{limit} OFFSET #{offset}") List<Map<String,Object>> findPage(@Param("runId") String runId,@Param("limit") int limit,@Param("offset") int offset);
 @Select("SELECT COUNT(*) FROM q_backtest_trade WHERE RunId=#{runId}") long count(@Param("runId") String runId);
}
