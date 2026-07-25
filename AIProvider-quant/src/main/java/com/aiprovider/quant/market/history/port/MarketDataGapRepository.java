package com.aiprovider.quant.market.history.port;

import com.aiprovider.quant.market.history.model.MarketDataGap;

import java.util.List;

/**
 * 数据缺口仓储端口。
 *
 * 定义在 AIProvider-quant 领域层，由 AIProvider-back 使用 MyBatis 实现。
 */
public interface MarketDataGapRepository {

    /**
     * 根据数据集 ID 查询缺口列表。
     *
     * @param datasetId 数据集 ID
     * @return 缺口列表
     */
    List<MarketDataGap> findByDataset(long datasetId);

    /**
     * 删除数据集的所有缺口记录。
     *
     * @param datasetId 数据集 ID
     * @return 影响行数
     */
    int deleteByDataset(long datasetId);

    /**
     * 批量插入缺口记录。
     *
     * @param gaps 缺口列表
     * @return 影响行数
     */
    int insertBatch(List<MarketDataGap> gaps);
}
