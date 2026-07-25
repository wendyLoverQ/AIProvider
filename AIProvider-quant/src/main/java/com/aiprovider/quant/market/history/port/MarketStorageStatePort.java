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
     *         "MARKET_DATA_READY_EMPTY" - 无 dataset 或所有 dataset 的 CandleCount=0
     *         "MARKET_DATA_AVAILABLE" - 存在 CandleCount>0 的 dataset 且全部为 CONTIGUOUS
     *         "MARKET_DATA_GAPPED" - 存在 CandleCount>0 的 dataset 且其中存在非 CONTIGUOUS 状态
     */
    String getStorageState();
}
