package com.aiprovider.quant.market.history.model;

/**
 * 历史行情数据导入来源模式。
 *
 * 决定任务使用哪种数据源完成 K 线回填。
 * 来源：binance/binance-public-data 官方数据包路径规则。
 */
public enum ArchiveImportMode {
    /** 由规划器自动选择月包、日包和 REST 尾部 */
    AUTO,
    /** 只导入完整月包 (data/futures/um/monthly/klines/...) */
    ARCHIVE_MONTHLY,
    /** 只导入指定日包 (data/futures/um/daily/klines/...) */
    ARCHIVE_DAILY,
    /** 只使用现有 /fapi/v1/klines 修补指定范围 */
    REST_GAP_REPAIR
}
