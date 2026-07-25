package com.aiprovider.quant.market.model;

/**
 * 公共行情数据提供方标识。
 *
 * 当前仅支持 Binance USDⓈ-M Futures（U 本位合约）。后续接入新的公共行情源时在此枚举扩展，
 * 并实现 {@link com.aiprovider.quant.market.port.PublicMarketDataProvider}。
 */
public enum MarketProviderId {
    /** Binance USDⓈ-M Futures（U 本位永续合约）公共行情。 */
    BINANCE_USDM
}
