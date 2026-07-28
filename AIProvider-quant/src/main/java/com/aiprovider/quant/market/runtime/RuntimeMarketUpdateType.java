package com.aiprovider.quant.market.runtime;

public enum RuntimeMarketUpdateType {
    OPEN_KLINE_IGNORED,
    CLOSED_CANDLE_APPENDED,
    DUPLICATE_CLOSED_CANDLE_IGNORED,
    TOP_OF_BOOK_UPDATED,
    DUPLICATE_TOP_OF_BOOK_IGNORED
}
