package com.aiprovider.quant.market.runtime;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Immutable, fully validated closed candle used by runtime strategies. */
public final class RuntimeClosedCandle {
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String symbol;
    private final KlineInterval interval;
    private final Instant eventTime;
    private final Instant openTime;
    private final Instant closeTime;
    private final BigDecimal open;
    private final BigDecimal high;
    private final BigDecimal low;
    private final BigDecimal close;
    private final BigDecimal volume;
    private final BigDecimal quoteVolume;
    private final long tradeCount;
    private final BigDecimal takerBuyBaseVolume;
    private final BigDecimal takerBuyQuoteVolume;

    public RuntimeClosedCandle(MarketProviderId provider, MarketType marketType, String symbol,
                               KlineInterval interval, Instant eventTime, Instant openTime,
                               Instant closeTime, BigDecimal open, BigDecimal high, BigDecimal low,
                               BigDecimal close, BigDecimal volume, BigDecimal quoteVolume,
                               long tradeCount, BigDecimal takerBuyBaseVolume,
                               BigDecimal takerBuyQuoteVolume) {
        validate(provider, marketType, symbol, interval, eventTime, openTime, closeTime,
                open, high, low, close, volume, quoteVolume, tradeCount,
                takerBuyBaseVolume, takerBuyQuoteVolume);
        this.provider = provider;
        this.marketType = marketType;
        this.symbol = symbol;
        this.interval = interval;
        this.eventTime = copy(eventTime);
        this.openTime = copy(openTime);
        this.closeTime = copy(closeTime);
        this.open = copy(open);
        this.high = copy(high);
        this.low = copy(low);
        this.close = copy(close);
        this.volume = copy(volume);
        this.quoteVolume = copy(quoteVolume);
        this.tradeCount = tradeCount;
        this.takerBuyBaseVolume = copy(takerBuyBaseVolume);
        this.takerBuyQuoteVolume = copy(takerBuyQuoteVolume);
    }

    public static RuntimeClosedCandle from(HistoricalCandle candle) {
        if (candle == null) {
            throw invalid("historical candle is required");
        }
        return new RuntimeClosedCandle(candle.getProvider(), candle.getMarketType(), candle.getSymbol(),
                candle.getInterval(), candle.getCloseTime(), candle.getOpenTime(), candle.getCloseTime(),
                candle.getOpenPrice(), candle.getHighPrice(), candle.getLowPrice(), candle.getClosePrice(),
                candle.getVolume(), candle.getQuoteVolume(), candle.getTradeCount(),
                candle.getTakerBuyBaseVolume(), candle.getTakerBuyQuoteVolume());
    }

    public static RuntimeClosedCandle from(StreamKlineEvent event) {
        if (event == null) {
            throw invalid("stream kline event is required");
        }
        if (!event.isClosed()) {
            throw invalid("open stream kline cannot become a closed runtime candle");
        }
        return new RuntimeClosedCandle(event.getProvider(), event.getMarketType(), event.getSymbol(),
                event.getInterval(), event.getEventTime(), event.getOpenTime(), event.getCloseTime(),
                event.getOpen(), event.getHigh(), event.getLow(), event.getClose(), event.getVolume(),
                event.getQuoteVolume(), event.getTradeCount(), event.getTakerBuyBaseVolume(),
                event.getTakerBuyQuoteVolume());
    }

    public HistoricalCandle toHistoricalCandle() {
        HistoricalCandle result = new HistoricalCandle();
        result.setProvider(provider);
        result.setMarketType(marketType);
        result.setSymbol(symbol);
        result.setInterval(interval);
        result.setOpenTime(copy(openTime));
        result.setCloseTime(copy(closeTime));
        result.setOpenPrice(copy(open));
        result.setHighPrice(copy(high));
        result.setLowPrice(copy(low));
        result.setClosePrice(copy(close));
        result.setVolume(copy(volume));
        result.setQuoteVolume(copy(quoteVolume));
        result.setTradeCount(tradeCount);
        result.setTakerBuyBaseVolume(copy(takerBuyBaseVolume));
        result.setTakerBuyQuoteVolume(copy(takerBuyQuoteVolume));
        return result;
    }

    static void validateOpenEvent(StreamKlineEvent event) {
        if (event == null) throw invalid("stream kline event is required");
        validateCommon(event.getProvider(), event.getMarketType(), event.getSymbol(), event.getInterval(),
                event.getEventTime(), event.getOpenTime(), event.getCloseTime(), event.getOpen(),
                event.getHigh(), event.getLow(), event.getClose(), event.getVolume(),
                event.getQuoteVolume(), event.getTradeCount(), event.getTakerBuyBaseVolume(),
                event.getTakerBuyQuoteVolume(), false);
    }

    private static void validate(MarketProviderId provider, MarketType marketType, String symbol,
                                 KlineInterval interval, Instant eventTime, Instant openTime,
                                 Instant closeTime, BigDecimal open, BigDecimal high, BigDecimal low,
                                 BigDecimal close, BigDecimal volume, BigDecimal quoteVolume,
                                 long tradeCount, BigDecimal takerBuyBaseVolume,
                                 BigDecimal takerBuyQuoteVolume) {
        validateCommon(provider, marketType, symbol, interval, eventTime, openTime, closeTime,
                open, high, low, close, volume, quoteVolume, tradeCount,
                takerBuyBaseVolume, takerBuyQuoteVolume, true);
    }

    private static void validateCommon(MarketProviderId provider, MarketType marketType, String symbol,
                                       KlineInterval interval, Instant eventTime, Instant openTime,
                                       Instant closeTime, BigDecimal open, BigDecimal high, BigDecimal low,
                                       BigDecimal close, BigDecimal volume, BigDecimal quoteVolume,
                                       long tradeCount, BigDecimal takerBuyBaseVolume,
                                       BigDecimal takerBuyQuoteVolume, boolean requireClosedEventTime) {
        if (provider == null || marketType == null || symbol == null || symbol.isBlank()
                || interval == null || eventTime == null || openTime == null || closeTime == null) {
            throw invalid("candle context and times are required");
        }
        if (marketType != MarketType.USDM_PERPETUAL) throw invalid("only USDM_PERPETUAL is supported");
        if (!interval.isFixedDuration()) {
            throw new RuntimeMarketStateException(RuntimeMarketStateException.INTERVAL_NOT_SUPPORTED,
                    "runtime candle requires a fixed-duration interval");
        }
        Instant expectedClose;
        try {
            expectedClose = openTime.plusMillis(interval.durationMillis() - 1L);
        } catch (RuntimeException exception) {
            throw invalid("invalid candle time range");
        }
        if (!expectedClose.equals(closeTime)) throw invalid("closeTime does not match interval");
        if (requireClosedEventTime && eventTime.isBefore(closeTime)) {
            throw invalid("closed candle eventTime must not be before closeTime");
        }
        if (!positive(open) || !positive(high) || !positive(low) || !positive(close)) {
            throw invalid("OHLC values must be positive");
        }
        if (!nonNegative(volume) || !nonNegative(quoteVolume)
                || !nonNegative(takerBuyBaseVolume) || !nonNegative(takerBuyQuoteVolume)
                || tradeCount < 0L) {
            throw invalid("candle volume and trade count values must be non-negative");
        }
        if (high.compareTo(open) < 0 || high.compareTo(close) < 0 || high.compareTo(low) < 0
                || low.compareTo(open) > 0 || low.compareTo(close) > 0 || low.compareTo(high) > 0) {
            throw invalid("OHLC bounds are inconsistent");
        }
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static boolean nonNegative(BigDecimal value) {
        return value != null && value.signum() >= 0;
    }

    private static RuntimeMarketStateException invalid(String message) {
        return new RuntimeMarketStateException(RuntimeMarketStateException.CANDLE_INVALID, message);
    }

    static BigDecimal copy(BigDecimal value) {
        return value == null ? null : new BigDecimal(value.toPlainString());
    }

    static Instant copy(Instant value) {
        return value == null ? null : Instant.ofEpochSecond(value.getEpochSecond(), value.getNano());
    }

    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public String getSymbol() { return symbol; }
    public KlineInterval getInterval() { return interval; }
    public Instant getEventTime() { return copy(eventTime); }
    public Instant getOpenTime() { return copy(openTime); }
    public Instant getCloseTime() { return copy(closeTime); }
    public BigDecimal getOpen() { return copy(open); }
    public BigDecimal getHigh() { return copy(high); }
    public BigDecimal getLow() { return copy(low); }
    public BigDecimal getClose() { return copy(close); }
    public BigDecimal getVolume() { return copy(volume); }
    public BigDecimal getQuoteVolume() { return copy(quoteVolume); }
    public long getTradeCount() { return tradeCount; }
    public BigDecimal getTakerBuyBaseVolume() { return copy(takerBuyBaseVolume); }
    public BigDecimal getTakerBuyQuoteVolume() { return copy(takerBuyQuoteVolume); }

    /**
     * Compares persisted candle content while deliberately excluding the stream message event time.
     */
    public boolean hasSameCandleContent(RuntimeClosedCandle other) {
        return other != null && tradeCount == other.tradeCount
                && provider == other.provider && marketType == other.marketType
                && symbol.equals(other.symbol) && interval == other.interval
                && openTime.equals(other.openTime) && closeTime.equals(other.closeTime)
                && open.equals(other.open) && high.equals(other.high) && low.equals(other.low)
                && close.equals(other.close) && volume.equals(other.volume)
                && quoteVolume.equals(other.quoteVolume)
                && takerBuyBaseVolume.equals(other.takerBuyBaseVolume)
                && takerBuyQuoteVolume.equals(other.takerBuyQuoteVolume);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RuntimeClosedCandle)) return false;
        RuntimeClosedCandle that = (RuntimeClosedCandle) other;
        return tradeCount == that.tradeCount && provider == that.provider && marketType == that.marketType
                && symbol.equals(that.symbol) && interval == that.interval
                && eventTime.equals(that.eventTime) && openTime.equals(that.openTime)
                && closeTime.equals(that.closeTime) && open.equals(that.open) && high.equals(that.high)
                && low.equals(that.low) && close.equals(that.close) && volume.equals(that.volume)
                && quoteVolume.equals(that.quoteVolume)
                && takerBuyBaseVolume.equals(that.takerBuyBaseVolume)
                && takerBuyQuoteVolume.equals(that.takerBuyQuoteVolume);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, marketType, symbol, interval, eventTime, openTime, closeTime,
                open, high, low, close, volume, quoteVolume, tradeCount,
                takerBuyBaseVolume, takerBuyQuoteVolume);
    }
}
