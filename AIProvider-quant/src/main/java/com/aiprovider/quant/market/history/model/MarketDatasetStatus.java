package com.aiprovider.quant.market.history.model;

/**
 * 数据集连续状态。
 *
 * 只描述已保存覆盖区间（earliestOpenTime ～ latestOpenTime）的连续性，
 * 不暗示已覆盖到当前时间或 Binance 上线以来所有数据都已下载。
 */
public enum MarketDatasetStatus {

    /** 暂无数据。 */
    EMPTY,

    /** 已保存区间内 K 线连续，无缺口。 */
    CONTIGUOUS,

    /** 已保存区间内存在缺口。 */
    GAPPED,

    /** 数据不完整（如部分 K 线字段缺失或校验异常但未达到 ERROR）。 */
    PARTIAL,

    /** 校验异常。 */
    ERROR
}
