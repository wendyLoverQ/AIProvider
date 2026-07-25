package com.aiprovider.quant.market.history.port;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.util.List;

/**
 * 历史 K 线仓储端口。
 *
 * 定义在 AIProvider-quant 领域层，由 AIProvider-back 使用 MyBatis 实现。
 * 仓储只负责数据存取，不包含业务校验逻辑。
 */
public interface MarketCandleRepository {

    /**
     * 根据数据集 ID 和 openTime 列表查询已存在的 K 线记录。
     *
     * @param datasetId 数据集 ID
     * @param openTimeMs 列表 openTime epoch 毫秒列表
     * @return 已存在的 K 线列表
     */
    List<HistoricalCandle> findByOpenTimes(long datasetId, List<Long> openTimeMs);

    /**
     * 批量插入 K 线。
     *
     * @param candles K 线列表
     * @return 实际插入行数
     */
    int insertBatch(List<HistoricalCandle> candles);

    /**
     * 按数据集 ID 分页查询 K 线，按 openTime 倒序。
     *
     * @param datasetId 数据集 ID
     * @param startOpenTimeMs 起始 openTime（包含），null 表示不限制
     * @param endOpenTimeMs 结束 openTime（包含），null 表示不限制
     * @param limit  最大返回数量
     * @param offset 偏移量
     * @return K 线列表
     */
    List<HistoricalCandle> findPage(long datasetId, Long startOpenTimeMs, Long endOpenTimeMs, int limit, int offset);

    /**
     * 统计数据集 K 线总数。
     *
     * @param datasetId 数据集 ID
     * @return K 线总数
     */
    long countByDataset(long datasetId);

    /**
     * 按 openTime 升序流式遍历数据集 K 线的 openTime 列表。
     *
     * 用于缺口校验，不一次加载全部数据。
     *
     * @param datasetId 数据集 ID
     * @param batchSize 每批加载量
     * @return 按 openTime 升序的 openTime 列表（分批）
     */
    List<Long> streamOpenTimesAscending(long datasetId, int batchSize, long afterOpenTimeMs);
}
