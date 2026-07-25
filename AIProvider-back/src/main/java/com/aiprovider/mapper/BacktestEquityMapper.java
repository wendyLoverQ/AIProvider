package com.aiprovider.mapper;
import org.apache.ibatis.annotations.*; import java.util.List; import java.util.Map;
@Mapper public interface BacktestEquityMapper {
 @Insert("<script>INSERT INTO q_backtest_equity(RunId,PointIndex,OpenTimeMs,EquityRatio,DrawdownRatio,InPosition) VALUES <foreach collection='rows' item='r' separator=','>(#{r.runId},#{r.pointIndex},#{r.openTimeMs},#{r.equityRatio},#{r.drawdownRatio},#{r.inPosition})</foreach></script>") int insertBatch(@Param("rows") List<Map<String,Object>> rows);
 @Select("SELECT COUNT(*) FROM q_backtest_equity WHERE RunId=#{runId}") int count(@Param("runId") String runId);
 @Select("SELECT * FROM q_backtest_equity WHERE RunId=#{runId} AND (PointIndex MOD #{stride})=0 ORDER BY PointIndex ASC") List<Map<String,Object>> findSampled(@Param("runId") String runId,@Param("stride") int stride);
 @Select("SELECT MAX(PointIndex) FROM q_backtest_equity WHERE RunId=#{runId}") Integer maxIndex(@Param("runId") String runId);
 @Select("SELECT * FROM q_backtest_equity WHERE RunId=#{runId} AND PointIndex=#{pointIndex}") Map<String,Object> findAt(@Param("runId") String runId,@Param("pointIndex") int pointIndex);
}
