package com.aiprovider.quant.engine.paper;

public enum PaperTradingStepType {
    DUPLICATE_CANDLE_IGNORED,
    PENDING_ORDER_ACTIVE,
    SIGNAL_HOLD,
    RISK_REJECTED,
    ENTRY_ORDER_SUBMITTED,
    EXIT_ORDER_SUBMITTED,
    ORDER_PARTIALLY_FILLED,
    ORDER_FILLED
}
