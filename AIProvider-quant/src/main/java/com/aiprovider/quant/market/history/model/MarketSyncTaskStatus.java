package com.aiprovider.quant.market.history.model;

/**
 * 同步任务状态。
 *
 * 非终态：QUEUED、DOWNLOADING、WRITING、VALIDATING
 * 终态：COMPLETED、FAILED
 */
public enum MarketSyncTaskStatus {

    /** 等待执行。 */
    QUEUED,

    /** 正在下载。 */
    DOWNLOADING,

    /** 正在写入数据库。 */
    WRITING,

    /** 正在校验数据完整性和缺口。 */
    VALIDATING,

    /** 已完成。 */
    COMPLETED,

    /** 失败。 */
    FAILED;

    /**
     * 判断是否为终态（不会再变化）。
     *
     * @return true 表示 COMPLETED 或 FAILED
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
