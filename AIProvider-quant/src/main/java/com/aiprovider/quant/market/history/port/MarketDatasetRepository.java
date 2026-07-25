package com.aiprovider.quant.market.history.port;

import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.util.List;

/**
 * 数据集仓储端口。
 *
 * 定义在 AIProvider-quant 领域层，由 AIProvider-back 使用 MyBatis 实现。
 */
public interface MarketDatasetRepository {

    /**
     * 根据唯一键查找数据集。
     *
     * @param provider 行情提供方
     * @param marketType 市场类型
     * @param dataType 数据类型字符串
     * @param symbol 合约符号
     * @param intervalCode 周期代码
     * @return 数据集，不存在返回 null
     */
    MarketDataset findByKey(MarketProviderId provider, MarketType marketType, String dataType, String symbol, String intervalCode);

    /**
     * 根据数据集 ID 查找。
     *
     * @param datasetId 数据集 ID
     * @return 数据集，不存在返回 null
     */
    MarketDataset findById(long datasetId);

    /**
     * 分页查询数据集列表。
     *
     * @param provider 筛选 provider，null 表示不筛选
     * @param symbol 筛选 symbol，null 表示不筛选
     * @param intervalCode 筛选周期，null 表示不筛选
     * @param status 筛选状态，null 表示不筛选
     * @param limit 最大返回数量
     * @param offset 偏移量
     * @return 数据集列表
     */
    List<MarketDataset> findPage(MarketProviderId provider, String symbol, String intervalCode, String status, int limit, int offset);

    /**
     * 插入新数据集。
     *
     * @param dataset 数据集
     * @return 新数据集 ID
     */
    long insert(MarketDataset dataset);

    /**
     * 更新数据集统计信息和状态。
     *
     * @param dataset 数据集
     * @return 影响行数
     */
    int updateStats(MarketDataset dataset);

    /**
     * 更新数据集最后同步信息。
     *
     * @param datasetId 数据集 ID
     * @param lastSyncTaskId 最后同步任务 ID
     * @param lastSuccessfulSyncAt 最后成功同步时间
     * @return 影响行数
     */
    int updateLastSync(long datasetId, String lastSyncTaskId, java.time.Instant lastSuccessfulSyncAt);
}
