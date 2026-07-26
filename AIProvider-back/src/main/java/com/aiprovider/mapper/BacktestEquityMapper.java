package com.aiprovider.mapper;
import com.aiprovider.mapper.row.BacktestEquityRow; import org.apache.ibatis.annotations.*; import java.util.Collection; import java.util.List;
@Mapper public interface BacktestEquityMapper {
 String COLUMNS="Id,RunId,PointIndex,OpenTimeMs,EquityRatio,DrawdownRatio,InPosition";
 @Insert("<script>INSERT INTO q_backtest_equity(RunId,PointIndex,OpenTimeMs,EquityRatio,DrawdownRatio,InPosition) VALUES <foreach collection='rows' item='r' separator=','>(#{r.runId},#{r.pointIndex},#{r.openTimeMs},#{r.equityRatio},#{r.drawdownRatio},#{r.inPosition})</foreach></script>") int insertBatch(@Param("rows") List<BacktestEquityRow> rows);
 @Select("SELECT COUNT(*) FROM q_backtest_equity WHERE RunId=#{runId}") int count(@Param("runId") String runId);
 @Results(id="backtestEquityRow",value={@Result(column="Id",property="id"),@Result(column="RunId",property="runId"),@Result(column="PointIndex",property="pointIndex"),@Result(column="OpenTimeMs",property="openTimeMs"),@Result(column="EquityRatio",property="equityRatio"),@Result(column="DrawdownRatio",property="drawdownRatio"),@Result(column="InPosition",property="inPosition")})
 @Select("SELECT " + COLUMNS + " FROM q_backtest_equity WHERE RunId=#{runId} ORDER BY PointIndex ASC") List<BacktestEquityRow> findAll(@Param("runId") String runId);
 @ResultMap("backtestEquityRow") @Select("<script>SELECT " + COLUMNS + " FROM q_backtest_equity WHERE RunId IN <foreach collection='runIds' item='runId' open='(' separator=',' close=')'>#{runId}</foreach> ORDER BY RunId ASC,OpenTimeMs ASC,PointIndex ASC</script>") List<BacktestEquityRow> findAllByRunIds(@Param("runIds") Collection<String> runIds);
 @ResultMap("backtestEquityRow") @Select("<script>SELECT " + COLUMNS + " FROM q_backtest_equity WHERE RunId=#{runId} AND PointIndex IN <foreach collection='indices' item='index' open='(' separator=',' close=')'>#{index}</foreach> ORDER BY PointIndex ASC</script>") List<BacktestEquityRow> findAtIndices(@Param("runId") String runId,@Param("indices") List<Integer> indices);
 @Delete("DELETE FROM q_backtest_equity WHERE RunId=#{runId}") int deleteByRunId(@Param("runId") String runId);
}
