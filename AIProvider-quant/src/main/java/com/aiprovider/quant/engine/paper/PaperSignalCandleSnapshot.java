package com.aiprovider.quant.engine.paper;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class PaperSignalCandleSnapshot {
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String symbol;
    private final KlineInterval interval;
    private final Instant openTime;
    private final Instant closeTime;
    private final BigDecimal openPrice;
    private final BigDecimal highPrice;
    private final BigDecimal lowPrice;
    private final BigDecimal closePrice;
    private final BigDecimal volume;
    private final BigDecimal quoteVolume;
    private final long tradeCount;

    public PaperSignalCandleSnapshot(
            MarketProviderId provider, MarketType marketType, String symbol, KlineInterval interval,
            Instant openTime, Instant closeTime, BigDecimal openPrice, BigDecimal highPrice,
            BigDecimal lowPrice, BigDecimal closePrice, BigDecimal volume,
            BigDecimal quoteVolume, long tradeCount) {
        if (provider == null || marketType == null || blank(symbol) || interval == null
                || openTime == null || closeTime == null || !closeTime.isAfter(openTime)
                || !positive(openPrice) || !positive(highPrice) || !positive(lowPrice)
                || !positive(closePrice) || !nonNegative(volume) || !nonNegative(quoteVolume)
                || tradeCount < 0) {
            throw new PaperTradingException(PaperTradingException.PAPER_TRADING_REQUEST_INVALID,
                    "closed candle fields are incomplete or invalid");
        }
        this.provider = provider;
        this.marketType = marketType;
        this.symbol = symbol;
        this.interval = interval;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.quoteVolume = quoteVolume;
        this.tradeCount = tradeCount;
    }

    public static PaperSignalCandleSnapshot from(HistoricalCandle candle) {
        if (candle == null) {
            throw new PaperTradingException(PaperTradingException.PAPER_TRADING_REQUEST_INVALID,
                    "candle must not be null");
        }
        return new PaperSignalCandleSnapshot(
                candle.getProvider(), candle.getMarketType(), candle.getSymbol(), candle.getInterval(),
                candle.getOpenTime(), candle.getCloseTime(), candle.getOpenPrice(), candle.getHighPrice(),
                candle.getLowPrice(), candle.getClosePrice(), candle.getVolume(),
                candle.getQuoteVolume(), candle.getTradeCount());
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private static boolean nonNegative(BigDecimal value) { return value != null && value.signum() >= 0; }

    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public String getSymbol() { return symbol; }
    public KlineInterval getInterval() { return interval; }
    public Instant getOpenTime() { return openTime; }
    public Instant getCloseTime() { return closeTime; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public BigDecimal getHighPrice() { return highPrice; }
    public BigDecimal getLowPrice() { return lowPrice; }
    public BigDecimal getClosePrice() { return closePrice; }
    public BigDecimal getVolume() { return volume; }
    public BigDecimal getQuoteVolume() { return quoteVolume; }
    public long getTradeCount() { return tradeCount; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperSignalCandleSnapshot that)) return false;
        return tradeCount == that.tradeCount && provider == that.provider && marketType == that.marketType
                && Objects.equals(symbol, that.symbol) && interval == that.interval
                && Objects.equals(openTime, that.openTime) && Objects.equals(closeTime, that.closeTime)
                && Objects.equals(openPrice, that.openPrice) && Objects.equals(highPrice, that.highPrice)
                && Objects.equals(lowPrice, that.lowPrice) && Objects.equals(closePrice, that.closePrice)
                && Objects.equals(volume, that.volume) && Objects.equals(quoteVolume, that.quoteVolume);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, marketType, symbol, interval, openTime, closeTime, openPrice,
                highPrice, lowPrice, closePrice, volume, quoteVolume, tradeCount);
    }
}
