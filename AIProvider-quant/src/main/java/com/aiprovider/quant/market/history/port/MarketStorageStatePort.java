package com.aiprovider.quant.market.history.port;

/**
 * 量化行情存储状态只读端口。
 *
 * 实现由 AIProvider-back 提供，直接查询 q_market_dataset 表。
 */
public interface MarketStorageStatePort {

    /**
     * 获取当前行情存储状态。
     *
     * @return 存储状态：
     *         "MARKET_DATA_READY_EMPTY" - 表和 Repository 已就绪但没有 dataset
     *         "MARKET_DATA_AVAILABLE" - 存在 dataset 且所有 gapCount = 0
     *         "MARKET_DATA_GAPPED" - 任一 dataset gapCount > 0
     */
    String getStorageState();
}
