package com.aiprovider.quant.exchange.binance.usdm;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketSnapshot;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.model.PerpetualContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BinanceUsdmResponseMapper} 单元测试。
 *
 * 验证：exchangeInfo 解析、filters 按 filterType 解析（不假设下标）、BigDecimal 精度、
 * K 线数组映射、未闭合 K 线判断、不合法结构/interval 抛异常、上游错误 JSON 抛异常。
 */
class BinanceUsdmResponseMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BinanceUsdmResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new BinanceUsdmResponseMapper(MarketProviderId.BINANCE_USDM, MarketType.USDM_PERPETUAL);
    }

    @Test
    void mapContractsParsesBtcusdtPerpetualTradingUsdt() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {
                  "symbols": [
                    {
                      "symbol": "BTCUSDT",
                      "pair": "BTCUSDT",
                      "contractType": "PERPETUAL",
                      "deliveryDate": 4133404800000,
                      "onboardDate": 1569398400000,
                      "status": "TRADING",
                      "baseAsset": "BTC",
                      "quoteAsset": "USDT",
                      "marginAsset": "USDT",
                      "pricePrecision": 2,
                      "quantityPrecision": 3,
                      "filters": [
                        {"filterType": "PRICE_FILTER", "minPrice": "0.10", "maxPrice": "1000000", "tickSize": "0.10"},
                        {"filterType": "LOT_SIZE", "minQty": "0.001", "maxQty": "1000", "stepSize": "0.001"},
                        {"filterType": "MARKET_LOT_SIZE", "minQty": "0.001", "maxQty": "120", "stepSize": "0.001"},
                        {"filterType": "MIN_NOTIONAL", "notional": "5"}
                      ]
                    },
                    {
                      "symbol": "BTCUSDC",
                      "pair": "BTCUSDC",
                      "contractType": "PERPETUAL",
                      "status": "TRADING",
                      "baseAsset": "BTC",
                      "quoteAsset": "USDC",
                      "marginAsset": "USDC",
                      "pricePrecision": 2,
                      "quantityPrecision": 3,
                      "filters": []
                    }
                  ]
                }
                """);

        List<PerpetualContract> usdtContracts = mapper.mapContracts(root, "USDT");
        assertThat(usdtContracts).hasSize(1);
        PerpetualContract btc = usdtContracts.get(0);
        assertThat(btc.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(btc.getContractType()).isEqualTo("PERPETUAL");
        assertThat(btc.getStatus()).isEqualTo("TRADING");
        assertThat(btc.getQuoteAsset()).isEqualTo("USDT");
        assertThat(btc.getMarginAsset()).isEqualTo("USDT");
        assertThat(btc.getBaseAsset()).isEqualTo("BTC");
        assertThat(btc.getProvider()).isEqualTo(MarketProviderId.BINANCE_USDM);
        assertThat(btc.getMarketType()).isEqualTo(MarketType.USDM_PERPETUAL);
        assertThat(btc.getPricePrecision()).isEqualTo(2);
        assertThat(btc.getQuantityPrecision()).isEqualTo(3);
        assertThat(btc.getOnboardDate()).isEqualTo(Instant.ofEpochMilli(1569398400000L));
    }

    @Test
    void filtersParsedByFilterTypeNotByIndex() throws Exception {
        // 故意打乱 filter 顺序，验证按 filterType 解析而非下标
        JsonNode root = objectMapper.readTree("""
                {
                  "symbols": [
                    {
                      "symbol": "ETHUSDT",
                      "pair": "ETHUSDT",
                      "contractType": "PERPETUAL",
                      "status": "TRADING",
                      "baseAsset": "ETH",
                      "quoteAsset": "USDT",
                      "marginAsset": "USDT",
                      "pricePrecision": 2,
                      "quantityPrecision": 3,
                      "filters": [
                        {"filterType": "MIN_NOTIONAL", "notional": "20"},
                        {"filterType": "MARKET_LOT_SIZE", "minQty": "0.01", "maxQty": "500", "stepSize": "0.01"},
                        {"filterType": "PRICE_FILTER", "minPrice": "0.01", "maxPrice": "50000", "tickSize": "0.01"},
                        {"filterType": "LOT_SIZE", "minQty": "0.001", "maxQty": "2000", "stepSize": "0.001"}
                      ]
                    }
                  ]
                }
                """);

        List<PerpetualContract> contracts = mapper.mapContracts(root, "USDT");
        PerpetualContract eth = contracts.get(0);
        // PRICE_FILTER 字段
        assertThat(eth.getTickSize()).isEqualByComparingTo("0.01");
        assertThat(eth.getMinPrice()).isEqualByComparingTo("0.01");
        assertThat(eth.getMaxPrice()).isEqualByComparingTo("50000");
        // LOT_SIZE 字段
        assertThat(eth.getStepSize()).isEqualByComparingTo("0.001");
        assertThat(eth.getMinQty()).isEqualByComparingTo("0.001");
        assertThat(eth.getMaxQty()).isEqualByComparingTo("2000");
        // MARKET_LOT_SIZE 字段
        assertThat(eth.getMarketStepSize()).isEqualByComparingTo("0.01");
        assertThat(eth.getMarketMinQty()).isEqualByComparingTo("0.01");
        assertThat(eth.getMarketMaxQty()).isEqualByComparingTo("500");
        // MIN_NOTIONAL 字段
        assertThat(eth.getMinNotional()).isEqualByComparingTo("20");
    }

    @Test
    void bigDecimalPrecisionPreserved() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {
                  "symbols": [
                    {
                      "symbol": "BTCUSDT",
                      "pair": "BTCUSDT",
                      "contractType": "PERPETUAL",
                      "status": "TRADING",
                      "baseAsset": "BTC",
                      "quoteAsset": "USDT",
                      "marginAsset": "USDT",
                      "filters": [
                        {"filterType": "PRICE_FILTER", "minPrice": "0.10000000", "maxPrice": "1000000.00000000", "tickSize": "0.10000000"},
                        {"filterType": "LOT_SIZE", "minQty": "0.00100000", "maxQty": "1000.00000000", "stepSize": "0.00100000"},
                        {"filterType": "MARKET_LOT_SIZE", "minQty": "0.00100000", "maxQty": "120.00000000", "stepSize": "0.00100000"},
                        {"filterType": "MIN_NOTIONAL", "notional": "5.00000000"}
                      ]
                    }
                  ]
                }
                """);

        List<PerpetualContract> contracts = mapper.mapContracts(root, "USDT");
        PerpetualContract btc = contracts.get(0);
        assertThat(btc.getTickSize()).isEqualByComparingTo(new BigDecimal("0.10000000"));
        assertThat(btc.getTickSize().scale()).isEqualTo(8);
        assertThat(btc.getMinNotional()).isEqualByComparingTo(new BigDecimal("5.00000000"));
        assertThat(btc.getMinNotional().scale()).isEqualTo(8);
    }

    @Test
    void mapKlinesParsesArray() throws Exception {
        JsonNode root = objectMapper.readTree("""
                [
                  [1499040000000, "0.01634790", "0.80000000", "0.01575800", "0.01577100", "148976.11427815", 1499644799999, "2434.19055334", 308, "1756.87402397", "28.46694368", "0"],
                  [1499644800000, "0.01577100", "0.01579800", "0.01570500", "0.01571300", "1234.56789012", 1500249599999, "19.43500000", 50, "1000.00000000", "15.71300000", "0"]
                ]
                """);
        Instant serverTime = Instant.ofEpochMilli(1500250000000L);

        List<MarketCandle> candles = mapper.mapKlines("BTCUSDT", KlineInterval.M15, root, serverTime);
        assertThat(candles).hasSize(2);

        MarketCandle first = candles.get(0);
        assertThat(first.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(first.getInterval()).isEqualTo(KlineInterval.M15);
        assertThat(first.getOpenTime()).isEqualTo(Instant.ofEpochMilli(1499040000000L));
        assertThat(first.getCloseTime()).isEqualTo(Instant.ofEpochMilli(1499644799999L));
        assertThat(first.getOpen()).isEqualByComparingTo("0.01634790");
        assertThat(first.getHigh()).isEqualByComparingTo("0.80000000");
        assertThat(first.getLow()).isEqualByComparingTo("0.01575800");
        assertThat(first.getClose()).isEqualByComparingTo("0.01577100");
        assertThat(first.getVolume()).isEqualByComparingTo("148976.11427815");
        assertThat(first.getQuoteVolume()).isEqualByComparingTo("2434.19055334");
        assertThat(first.getTradeCount()).isEqualTo(308L);
        assertThat(first.getTakerBuyBaseVolume()).isEqualByComparingTo("1756.87402397");
        assertThat(first.getTakerBuyQuoteVolume()).isEqualByComparingTo("28.46694368");
        // closeTime=1499644799999 < serverTime=1500250000000 → 已闭合
        assertThat(first.isClosed()).isTrue();

        MarketCandle second = candles.get(1);
        // closeTime=1500249599999 < serverTime=1500250000000 → 已闭合
        assertThat(second.isClosed()).isTrue();
    }

    @Test
    void unclosedKlineWhenServerTimeBeforeCloseTime() throws Exception {
        JsonNode root = objectMapper.readTree("""
                [
                  [1499040000000, "0.01634790", "0.80000000", "0.01575800", "0.01577100", "148976.11427815", 9999999999999, "2434.19055334", 308, "1756.87402397", "28.46694368", "0"]
                ]
                """);
        // serverTime 远早于 closeTime → 未闭合
        Instant serverTime = Instant.ofEpochMilli(1500250000000L);

        List<MarketCandle> candles = mapper.mapKlines("BTCUSDT", KlineInterval.M1, root, serverTime);
        assertThat(candles).hasSize(1);
        assertThat(candles.get(0).isClosed()).isFalse();
    }

    @Test
    void invalidKlineStructureThrows() throws Exception {
        // 行元素不足 11 个
        JsonNode root = objectMapper.readTree("""
                [
                  [1499040000000, "0.01634790", "0.80000000"]
                ]
                """);
        assertThatThrownBy(() -> mapper.mapKlines("BTCUSDT", KlineInterval.M15, root, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void klinesRootNotArrayThrows() throws Exception {
        JsonNode root = objectMapper.readTree("{\"code\": -1121, \"msg\": \"Invalid symbol\"}");
        assertThatThrownBy(() -> mapper.mapKlines("BTCUSDT", KlineInterval.M15, root, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidIntervalThrows() {
        assertThatThrownBy(() -> KlineInterval.fromCode("2m"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KlineInterval.fromCode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upstreamErrorJsonThrowsWhenPassedToContracts() throws Exception {
        JsonNode errorRoot = objectMapper.readTree("{\"code\": -1121, \"msg\": \"Invalid symbol\"}");
        // 错误 JSON 没有 symbols 数组 → 抛异常
        assertThatThrownBy(() -> mapper.mapContracts(errorRoot, "USDT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotMappingCombinesFourEndpoints() throws Exception {
        JsonNode ticker = objectMapper.readTree("""
                {
                  "symbol": "BTCUSDT",
                  "priceChange": "0.50",
                  "priceChangePercent": "0.0100",
                  "weightedAvgPrice": "50.00",
                  "lastPrice": "50000.00",
                  "lastQty": "0.001",
                  "openPrice": "49999.50",
                  "highPrice": "50100.00",
                  "lowPrice": "49900.00",
                  "volume": "1000.000",
                  "quoteVolume": "50000000.00",
                  "openTime": 1499344800000,
                  "closeTime": 1499431200000,
                  "count": 10000
                }
                """);
        JsonNode premiumIndex = objectMapper.readTree("""
                {
                  "symbol": "BTCUSDT",
                  "markPrice": "50001.00",
                  "indexPrice": "50000.50",
                  "estimatedSettlePrice": "50000.00",
                  "lastFundingRate": "0.00010000",
                  "interestRate": "0.00010000",
                  "nextFundingTime": 1499431200000,
                  "time": 1499431200000
                }
                """);
        JsonNode bookTicker = objectMapper.readTree("""
                {
                  "symbol": "BTCUSDT",
                  "bidPrice": "49999.00",
                  "bidQty": "1.000",
                  "askPrice": "50001.00",
                  "askQty": "2.000",
                  "time": 1499431200000
                }
                """);
        JsonNode openInterest = objectMapper.readTree("""
                {
                  "symbol": "BTCUSDT",
                  "openInterest": "50000.000",
                  "time": 1499431200000
                }
                """);

        MarketSnapshot snapshot = mapper.mapSnapshot("BTCUSDT", ticker, premiumIndex, bookTicker, openInterest);
        assertThat(snapshot.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(snapshot.getLastPrice()).isEqualByComparingTo("50000.00");
        assertThat(snapshot.getMarkPrice()).isEqualByComparingTo("50001.00");
        assertThat(snapshot.getIndexPrice()).isEqualByComparingTo("50000.50");
        assertThat(snapshot.getBidPrice()).isEqualByComparingTo("49999.00");
        assertThat(snapshot.getAskPrice()).isEqualByComparingTo("50001.00");
        assertThat(snapshot.getOpenInterest()).isEqualByComparingTo("50000.000");
        // spread = ask - bid = 50001 - 49999 = 2
        assertThat(snapshot.getSpread()).isEqualByComparingTo("2");
        // spreadRate = spread / ask = 2 / 50001
        assertThat(snapshot.getSpreadRate()).isNotNull();
        assertThat(snapshot.getLastFundingRate()).isEqualByComparingTo("0.00010000");
        assertThat(snapshot.getNextFundingTime()).isEqualTo(Instant.ofEpochMilli(1499431200000L));
    }

    @Test
    void nonPerpetualOrNonTradingContractsFilteredOut() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {
                  "symbols": [
                    {
                      "symbol": "BTCUSDT", "pair": "BTCUSDT", "contractType": "PERPETUAL", "status": "TRADING",
                      "baseAsset": "BTC", "quoteAsset": "USDT", "marginAsset": "USDT",
                      "filters": []
                    },
                    {
                      "symbol": "BTC_250627", "pair": "BTCUSDT", "contractType": "CURRENT_QUARTER", "status": "TRADING",
                      "baseAsset": "BTC", "quoteAsset": "USDT", "marginAsset": "USDT",
                      "filters": []
                    },
                    {
                      "symbol": "ETHUSDT", "pair": "ETHUSDT", "contractType": "PERPETUAL", "status": "PENDING_TRADING",
                      "baseAsset": "ETH", "quoteAsset": "USDT", "marginAsset": "USDT",
                      "filters": []
                    }
                  ]
                }
                """);
        List<PerpetualContract> contracts = mapper.mapContracts(root, "USDT");
        assertThat(contracts).hasSize(1);
        assertThat(contracts.get(0).getSymbol()).isEqualTo("BTCUSDT");
    }
}
