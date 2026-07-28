package com.aiprovider.quant.account.paper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class PaperTradingDayState {
    private final LocalDate utcDate;
    private final BigDecimal dayStartEquity;
    private final BigDecimal dailyRealizedPnl;

    PaperTradingDayState(
            LocalDate utcDate, BigDecimal dayStartEquity, BigDecimal dailyRealizedPnl) {
        this.utcDate = utcDate;
        this.dayStartEquity = dayStartEquity;
        this.dailyRealizedPnl = dailyRealizedPnl;
    }

    public LocalDate getUtcDate() { return utcDate; }
    public BigDecimal getDayStartEquity() { return dayStartEquity; }
    public BigDecimal getDailyRealizedPnl() { return dailyRealizedPnl; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperTradingDayState that)) return false;
        return Objects.equals(utcDate, that.utcDate)
                && Objects.equals(dayStartEquity, that.dayStartEquity)
                && Objects.equals(dailyRealizedPnl, that.dailyRealizedPnl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(utcDate, dayStartEquity, dailyRealizedPnl);
    }
}
