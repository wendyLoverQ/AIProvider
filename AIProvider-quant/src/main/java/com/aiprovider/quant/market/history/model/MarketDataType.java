package com.aiprovider.quant.market.history.model;

/**
 * 历史行情数据类型。
 *
 * 第一阶段只实现 {@link #CANDLE}（历史闭合 K 线）。
 * 其他类型为未来预留，不在本次创建数据表、空接口或假页面。
 */
public enum MarketDataType {

    /** 历史闭合 K 线。 */
    CANDLE,

    /** 资金费率历史（预留，本次不实现）。 */
    FUNDING_RATE,

    /** 未平仓量历史（预留，本次不实现）。 */
    OPEN_INTEREST
}
