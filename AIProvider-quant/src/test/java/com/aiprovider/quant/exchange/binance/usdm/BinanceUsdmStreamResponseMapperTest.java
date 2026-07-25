package com.aiprovider.quant.exchange.binance.usdm;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;
import com.aiprovider.quant.market.stream.model.StreamMarkPriceEvent;
import com.aiprovider.quant.market.stream.model.StreamTickerEvent;
import com.aiprovider.quant.exchange.binance.usdm.BinanceUsdmStreamResponseMapper.StreamEventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BinanceUsdmStreamResponseMapper} 单元测试。
 *
 * 验证：4 种事件类型的 JSON 映射、detectEventType 与 detectFromStreamName、
 * BigDecimal 精度、kline interval 解析、bookTicker 缺失 E 字段回退、
 * 不合法结构抛异常。
 */
class BinanceUsdmStreamResponseMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BinanceUsdmStreamResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new BinanceUsdmStreamResponseMapper(MarketProviderId.BINANCE_USDM, MarketType.USDM_PERPETUAL);
    }

    // ---- detectEventType ----

    @Test
    void detectEventTypeKline() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {"e":"kline","E":123456789,"s":"BTCUSDT","k":{"t":123400000,"T":123460000,"i":"15m"}}
                """);
        assertThat(mapper.detectEventType(data)).isEqualTo(StreamEventType.KLINE);
    }

    @Test
    void detectEventTypeTicker() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {"e":"24hrTicker","E":123456789,"s":"BTCUSDT"}
                """);
        assertThat(mapper.detectEventType(data)).isEqualTo(StreamEventType.TICKER);
    }

    @Test
    void detectEventTypeMarkPrice() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {"e":"markPriceUpdate","E":123456789,"s":"BTCUSDT"}
                """);
        assertThat(mapper.detectEventType(data)).isEqualTo(StreamEventType.MARK_PRICE);
    }

    @Test
    void detectEventTypeBookTickerReturnsNullBecauseNoEField() throws Exception {
        // bookTicker 流不包含 e 字段，detectEventType 返回 null
        JsonNode data = objectMapper.readTree("""
                {"u":400900217,"s":"BTCUSDT","b":"25.35190000","B":"31.21000000","a":"25.36520000","A":"40.06000000","T":123456789,"E":123456789}
                """);
        assertThat(mapper.detectEventType(data)).isNull();
    }

    @Test
    void detectEventTypeUnknownReturnsNull() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {"e":"unknownEvent","s":"BTCUSDT"}
                """);
        assertThat(mapper.detectEventType(data)).isNull();
    }

    @Test
    void detectEventTypeNullReturnsNull() {
        assertThat(mapper.detectEventType(null)).isNull();
    }

    // ---- detectFromStreamName ----

    @Test
    void detectFromStreamNameKline() {
        assertThat(mapper.detectFromStreamName("btcusdt@kline_15m")).isEqualTo(StreamEventType.KLINE);
    }

    @Test
    void detectFromStreamNameTicker() {
        assertThat(mapper.detectFromStreamName("btcusdt@ticker")).isEqualTo(StreamEventType.TICKER);
    }

    @Test
    void detectFromStreamNameMarkPrice() {
        assertThat(mapper.detectFromStreamName("btcusdt@markPrice@1s")).isEqualTo(StreamEventType.MARK_PRICE);
    }

    @Test
    void detectFromStreamNameBookTicker() {
        assertThat(mapper.detectFromStreamName("btcusdt@bookTicker")).isEqualTo(StreamEventType.BOOK_TICKER);
    }

    @Test
    void detectFromStreamNameUnknownReturnsNull() {
        assertThat(mapper.detectFromStreamName("btcusdt@unknownStream")).isNull();
    }

    @Test
    void detectFromStreamNameNullReturnsNull() {
        assertThat(mapper.detectFromStreamName(null)).isNull();
        assertThat(mapper.detectFromStreamName("")).isNull();
    }

    // ---- mapKline ----

    @Test
    void mapKlineParsesAllFields() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "e": "kline",
                  "E": 123456789,
                  "s": "BTCUSDT",
                  "k": {
                    "t": 123400000,
                    "T": 123460000,
                    "s": "BTCUSDT",
                    "i": "15m",
                    "o": "0.00100000",
                    "c": "0.00200000",
                    "h": "0.00250000",
                    "l": "0.00050000",
                    "v": "1000.00000000",
                    "q": "1.50000000",
                    "n": 100,
                    "V": "500.00000000",
                    "Q": "0.75000000",
                    "x": false
                  }
                }
                """);

        StreamKlineEvent event = mapper.mapKline(data);
        assertThat(event.getProvider()).isEqualTo(MarketProviderId.BINANCE_USDM);
        assertThat(event.getMarketType()).isEqualTo(MarketType.USDM_PERPETUAL);
        assertThat(event.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(event.getEventTime()).isEqualTo(Instant.ofEpochMilli(123456789L));
        assertThat(event.getOpenTime()).isEqualTo(Instant.ofEpochMilli(123400000L));
        assertThat(event.getCloseTime()).isEqualTo(Instant.ofEpochMilli(123460000L));
        assertThat(event.getInterval()).isEqualTo(KlineInterval.M15);
        assertThat(event.getOpen()).isEqualByComparingTo("0.00100000");
        assertThat(event.getHigh()).isEqualByComparingTo("0.00250000");
        assertThat(event.getLow()).isEqualByComparingTo("0.00050000");
        assertThat(event.getClose()).isEqualByComparingTo("0.00200000");
        assertThat(event.getVolume()).isEqualByComparingTo("1000.00000000");
        assertThat(event.getQuoteVolume()).isEqualByComparingTo("1.50000000");
        assertThat(event.getTradeCount()).isEqualTo(100L);
        assertThat(event.getTakerBuyBaseVolume()).isEqualByComparingTo("500.00000000");
        assertThat(event.getTakerBuyQuoteVolume()).isEqualByComparingTo("0.75000000");
        assertThat(event.isClosed()).isFalse();
    }

    @Test
    void mapKlineClosedFlagTrue() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "e": "kline",
                  "E": 123456789,
                  "s": "BTCUSDT",
                  "k": {
                    "t": 123400000,
                    "T": 123460000,
                    "i": "1h",
                    "o": "50000.00",
                    "c": "50100.00",
                    "h": "50200.00",
                    "l": "49900.00",
                    "v": "1000",
                    "q": "50000000",
                    "n": 500,
                    "V": "600",
                    "Q": "30000000",
                    "x": true
                  }
                }
                """);

        StreamKlineEvent event = mapper.mapKline(data);
        assertThat(event.isClosed()).isTrue();
        assertThat(event.getInterval()).isEqualTo(KlineInterval.H1);
    }

    @Test
    void mapKlineBigDecimalPrecisionPreserved() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "e": "kline",
                  "E": 123456789,
                  "s": "BTCUSDT",
                  "k": {
                    "t": 123400000,
                    "T": 123460000,
                    "i": "5m",
                    "o": "0.00100000",
                    "c": "0.00200000",
                    "h": "0.00250000",
                    "l": "0.00050000",
                    "v": "1000.00000000",
                    "q": "1.50000000",
                    "n": 100,
                    "V": "500.00000000",
                    "Q": "0.75000000",
                    "x": false
                  }
                }
                """);

        StreamKlineEvent event = mapper.mapKline(data);
        assertThat(event.getOpen().scale()).isEqualTo(8);
        assertThat(event.getClose().scale()).isEqualTo(8);
        assertThat(event.getVolume().scale()).isEqualTo(8);
    }

    @Test
    void mapKlineMissingKNodeThrows() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {"e":"kline","E":123456789,"s":"BTCUSDT"}
                """);
        assertThatThrownBy(() -> mapper.mapKline(data))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapKlineNullThrows() {
        assertThatThrownBy(() -> mapper.mapKline(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- mapTicker ----

    @Test
    void mapTickerParsesAllFields() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "e": "24hrTicker",
                  "E": 123456789,
                  "s": "BTCUSDT",
                  "p": "0.0015",
                  "P": "0.2500",
                  "c": "0.0020",
                  "h": "0.0025",
                  "l": "0.0005",
                  "v": "1000.000",
                  "q": "1.500000"
                }
                """);

        StreamTickerEvent event = mapper.mapTicker(data);
        assertThat(event.getProvider()).isEqualTo(MarketProviderId.BINANCE_USDM);
        assertThat(event.getMarketType()).isEqualTo(MarketType.USDM_PERPETUAL);
        assertThat(event.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(event.getEventTime()).isEqualTo(Instant.ofEpochMilli(123456789L));
        assertThat(event.getLastPrice()).isEqualByComparingTo("0.0020");
        assertThat(event.getPriceChange()).isEqualByComparingTo("0.0015");
        assertThat(event.getPriceChangePercent()).isEqualByComparingTo("0.2500");
        assertThat(event.getHighPrice()).isEqualByComparingTo("0.0025");
        assertThat(event.getLowPrice()).isEqualByComparingTo("0.0005");
        assertThat(event.getVolume()).isEqualByComparingTo("1000.000");
        assertThat(event.getQuoteVolume()).isEqualByComparingTo("1.500000");
    }

    @Test
    void mapTickerNullThrows() {
        assertThatThrownBy(() -> mapper.mapTicker(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- mapMarkPrice ----

    @Test
    void mapMarkPriceParsesAllFields() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "e": "markPriceUpdate",
                  "E": 123456789,
                  "s": "BTCUSDT",
                  "p": "50001.00000000",
                  "i": "50000.50000000",
                  "P": "50000.00000000",
                  "r": "0.00010000",
                  "T": 123460000
                }
                """);

        StreamMarkPriceEvent event = mapper.mapMarkPrice(data);
        assertThat(event.getProvider()).isEqualTo(MarketProviderId.BINANCE_USDM);
        assertThat(event.getMarketType()).isEqualTo(MarketType.USDM_PERPETUAL);
        assertThat(event.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(event.getEventTime()).isEqualTo(Instant.ofEpochMilli(123456789L));
        assertThat(event.getMarkPrice()).isEqualByComparingTo("50001.00000000");
        assertThat(event.getIndexPrice()).isEqualByComparingTo("50000.50000000");
        assertThat(event.getEstimatedSettlePrice()).isEqualByComparingTo("50000.00000000");
        assertThat(event.getLastFundingRate()).isEqualByComparingTo("0.00010000");
        // Binance markPriceUpdate 流不含 interestRate 字段
        assertThat(event.getInterestRate()).isNull();
        assertThat(event.getNextFundingTime()).isEqualTo(Instant.ofEpochMilli(123460000L));
    }

    @Test
    void mapMarkPriceBigDecimalPrecisionPreserved() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "e": "markPriceUpdate",
                  "E": 123456789,
                  "s": "BTCUSDT",
                  "p": "50001.00000000",
                  "i": "50000.50000000",
                  "P": "50000.00000000",
                  "r": "0.00010000",
                  "T": 123460000
                }
                """);

        StreamMarkPriceEvent event = mapper.mapMarkPrice(data);
        assertThat(event.getMarkPrice().scale()).isEqualTo(8);
        assertThat(event.getLastFundingRate().scale()).isEqualTo(8);
    }

    @Test
    void mapMarkPriceNullThrows() {
        assertThatThrownBy(() -> mapper.mapMarkPrice(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- mapBookTicker ----

    @Test
    void mapBookTickerParsesAllFieldsWithEventTime() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "u": 400900217,
                  "s": "BTCUSDT",
                  "b": "49999.00000000",
                  "B": "31.21000000",
                  "a": "50001.00000000",
                  "A": "40.06000000",
                  "T": 123456789,
                  "E": 123456789
                }
                """);

        StreamBookTickerEvent event = mapper.mapBookTicker(data);
        assertThat(event.getProvider()).isEqualTo(MarketProviderId.BINANCE_USDM);
        assertThat(event.getMarketType()).isEqualTo(MarketType.USDM_PERPETUAL);
        assertThat(event.getSymbol()).isEqualTo("BTCUSDT");
        // E 字段存在时使用 E 字段
        assertThat(event.getEventTime()).isEqualTo(Instant.ofEpochMilli(123456789L));
        assertThat(event.getBidPrice()).isEqualByComparingTo("49999.00000000");
        assertThat(event.getBidQuantity()).isEqualByComparingTo("31.21000000");
        assertThat(event.getAskPrice()).isEqualByComparingTo("50001.00000000");
        assertThat(event.getAskQuantity()).isEqualByComparingTo("40.06000000");
    }

    @Test
    void mapBookTickerMissingEventTimeFallsBackToNow() throws Exception {
        JsonNode data = objectMapper.readTree("""
                {
                  "u": 400900217,
                  "s": "BTCUSDT",
                  "b": "49999.00000000",
                  "B": "31.21000000",
                  "a": "50001.00000000",
                  "A": "40.06000000",
                  "T": 123456789
                }
                """);

        Instant before = Instant.now();
        StreamBookTickerEvent event = mapper.mapBookTicker(data);
        Instant after = Instant.now();

        assertThat(event.getEventTime()).isNotNull();
        // E 字段缺失时回退到接收时间，应在 before 与 after 之间
        assertThat(event.getEventTime()).isBetween(before, after);
    }

    @Test
    void mapBookTickerNullThrows() {
        assertThatThrownBy(() -> mapper.mapBookTicker(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- 组合检测：detectEventType + detectFromStreamName 组合使用 ----

    @Test
    void bookTickerDetectedByStreamNameWhenEFieldMissing() throws Exception {
        // 模拟 Binance 组合流 bookTicker 消息（data 中没有 e 字段）
        JsonNode data = objectMapper.readTree("""
                {"u":400900217,"s":"BTCUSDT","b":"25.35190000","B":"31.21000000","a":"25.36520000","A":"40.06000000","T":123456789,"E":123456789}
                """);

        // detectEventType 返回 null
        assertThat(mapper.detectEventType(data)).isNull();
        // detectFromStreamName 补充检测
        assertThat(mapper.detectFromStreamName("btcusdt@bookTicker")).isEqualTo(StreamEventType.BOOK_TICKER);
    }
}
