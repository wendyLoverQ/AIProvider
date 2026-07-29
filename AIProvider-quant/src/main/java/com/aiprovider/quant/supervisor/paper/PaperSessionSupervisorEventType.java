package com.aiprovider.quant.supervisor.paper;

public enum PaperSessionSupervisorEventType {
    STARTED,
    STOPPED,
    KLINE_PROCESSED,
    BOOK_TICKER_PROCESSED,
    MARK_PRICE_PROCESSED,
    TICKER_IGNORED,
    STREAM_STATUS_UPDATED,
    FAILED
}
