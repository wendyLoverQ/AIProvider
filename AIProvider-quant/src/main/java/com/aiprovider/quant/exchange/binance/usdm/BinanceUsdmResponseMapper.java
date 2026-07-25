package com.aiprovider.quant.exchange.binance.usdm;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketSnapshot;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.model.PerpetualContract;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Binance USDⓈ-M Futures 公共行情响应映射器。
 *
 * 将 Jackson {@link JsonNode} 映射为 Quant 领域模型。所有价格与数量使用 BigDecimal（取字符串形式构造以保留精度），
 * 时间戳（毫秒）转 {@link Instant}。filters 按 filterType 解析，禁止假设下标顺序。
 * 不重试、不降级，遇到结构不合法或缺失字段时抛出 {@link IllegalArgumentException}。
 */
public class BinanceUsdmResponseMapper {

    private static final String FILTER_PRICE = "PRICE_FILTER";
    private static final String FILTER_LOT = "LOT_SIZE";
    private static final String FILTER_MARKET_LOT = "MARKET_LOT_SIZE";
    private static final String FILTER_MIN_NOTIONAL = "MIN_NOTIONAL";

    private static final List<KlineInterval> SUPPORTED_INTERVALS = Arrays.asList(
            KlineInterval.M1, KlineInterval.M3, KlineInterval.M5, KlineInterval.M15, KlineInterval.M30,
            KlineInterval.H1, KlineInterval.H2, KlineInterval.H4, KlineInterval.H6, KlineInterval.H12,
            KlineInterval.D1, KlineInterval.W1, KlineInterval.MO1);

    private final MarketProviderId providerId;
    private final MarketType marketType;

    public BinanceUsdmResponseMapper(MarketProviderId providerId, MarketType marketType) {
        this.providerId = providerId;
        this.marketType = marketType;
    }

    /**
     * 解析 /fapi/v1/exchangeInfo 中的 symbols 数组为永续合约目录。
     *
     * 仅保留 contractType=PERPETUAL、status=TRADING、quoteAsset 与 marginAsset 均为指定计价币种的 symbol。
     */
    public List<PerpetualContract> mapContracts(JsonNode root, String quoteAsset) {
        requireObject(root, "exchangeInfo");
        JsonNode symbols = root.get("symbols");
        requireArray(symbols, "exchangeInfo.symbols");
        List<PerpetualContract> result = new ArrayList<>(symbols.size());
        for (JsonNode symbol : symbols) {
            if (!"PERPETUAL".equals(text(symbol, "contractType"))) {
                continue;
            }
            if (!"TRADING".equals(text(symbol, "status"))) {
                continue;
            }
            String quote = text(symbol, "quoteAsset");
            if (quoteAsset != null && !quoteAsset.equalsIgnoreCase(quote)) {
                continue;
            }
            String marginAsset = text(symbol, "marginAsset");
            if (quoteAsset != null && !quoteAsset.equalsIgnoreCase(marginAsset)) {
                continue;
            }
            result.add(mapContract(symbol, quote));
        }
        return result;
    }

    private PerpetualContract mapContract(JsonNode symbol, String quoteAsset) {
        PerpetualContract contract = new PerpetualContract();
        contract.setProvider(providerId);
        contract.setMarketType(marketType);
        contract.setSymbol(text(symbol, "symbol"));
        contract.setPair(text(symbol, "pair"));
        contract.setBaseAsset(text(symbol, "baseAsset"));
        contract.setQuoteAsset(quoteAsset);
        contract.setMarginAsset(text(symbol, "marginAsset"));
        contract.setContractType(text(symbol, "contractType"));
        contract.setStatus(text(symbol, "status"));
        contract.setOnboardDate(instant(symbol, "onboardDate"));
        contract.setPricePrecision(intOr(symbol, "pricePrecision", 0));
        contract.setQuantityPrecision(intOr(symbol, "quantityPrecision", 0));
        contract.setSupportedIntervals(SUPPORTED_INTERVALS);

        JsonNode filters = symbol.get("filters");
        requireArray(filters, "exchangeInfo.symbols.filters");
        for (JsonNode filter : filters) {
            String type = text(filter, "filterType");
            if (type == null) {
                continue;
            }
            switch (type) {
                case FILTER_PRICE -> {
                    contract.setTickSize(decimal(filter, "tickSize"));
                    contract.setMinPrice(decimal(filter, "minPrice"));
                    contract.setMaxPrice(decimal(filter, "maxPrice"));
                }
                case FILTER_LOT -> {
                    contract.setStepSize(decimal(filter, "stepSize"));
                    contract.setMinQty(decimal(filter, "minQty"));
                    contract.setMaxQty(decimal(filter, "maxQty"));
                }
                case FILTER_MARKET_LOT -> {
                    contract.setMarketStepSize(decimal(filter, "stepSize"));
                    contract.setMarketMinQty(decimal(filter, "minQty"));
                    contract.setMarketMaxQty(decimal(filter, "maxQty"));
                }
                case FILTER_MIN_NOTIONAL -> contract.setMinNotional(decimal(filter, "notional"));
                default -> { /* 忽略未知 filter，不假设顺序 */ }
            }
        }
        return contract;
    }

    /**
     * 将 24hr ticker、premiumIndex、bookTicker、openInterest 四个响应合并为单个快照。
     *
     * 任一响应结构不合法时抛出异常。spread 与 spreadRate 由 bid/ask 计算。
     */
    public MarketSnapshot mapSnapshot(String symbol, JsonNode ticker, JsonNode premiumIndex,
                                      JsonNode bookTicker, JsonNode openInterest) {
        MarketSnapshot snapshot = new MarketSnapshot();
        snapshot.setProvider(providerId);
        snapshot.setMarketType(marketType);
        snapshot.setSymbol(symbol);

        // 24hr ticker
        requireObject(ticker, "ticker/24hr");
        snapshot.setEventTime(instant(ticker, "closeTime"));
        snapshot.setLastPrice(decimal(ticker, "lastPrice"));
        snapshot.setPriceChange(decimal(ticker, "priceChange"));
        snapshot.setPriceChangePercent(decimal(ticker, "priceChangePercent"));
        snapshot.setHighPrice(decimal(ticker, "highPrice"));
        snapshot.setLowPrice(decimal(ticker, "lowPrice"));
        snapshot.setVolume(decimal(ticker, "volume"));
        snapshot.setQuoteVolume(decimal(ticker, "quoteVolume"));

        // premiumIndex
        requireObject(premiumIndex, "premiumIndex");
        snapshot.setMarkPrice(decimal(premiumIndex, "markPrice"));
        snapshot.setIndexPrice(decimal(premiumIndex, "indexPrice"));
        snapshot.setEstimatedSettlePrice(decimal(premiumIndex, "estimatedSettlePrice"));
        snapshot.setLastFundingRate(decimal(premiumIndex, "lastFundingRate"));
        snapshot.setInterestRate(decimal(premiumIndex, "interestRate"));
        snapshot.setNextFundingTime(instant(premiumIndex, "nextFundingTime"));

        // bookTicker
        requireObject(bookTicker, "bookTicker");
        snapshot.setBidPrice(decimal(bookTicker, "bidPrice"));
        snapshot.setBidQuantity(decimal(bookTicker, "bidQty"));
        snapshot.setAskPrice(decimal(bookTicker, "askPrice"));
        snapshot.setAskQuantity(decimal(bookTicker, "askQty"));

        // openInterest
        requireObject(openInterest, "openInterest");
        snapshot.setOpenInterest(decimal(openInterest, "openInterest"));

        // 计算 spread 与 spreadRate
        BigDecimal bid = snapshot.getBidPrice();
        BigDecimal ask = snapshot.getAskPrice();
        if (bid != null && ask != null) {
            BigDecimal spread = ask.subtract(bid);
            snapshot.setSpread(spread);
            if (ask.signum() != 0) {
                snapshot.setSpreadRate(spread.divide(ask, 12, java.math.RoundingMode.HALF_UP));
            }
        }
        return snapshot;
    }

    /**
     * 解析 /fapi/v1/klines 数组为 K 线列表。
     *
     * closed 根据 serverTime 与 closeTime 判断：serverTime >= closeTime 视为已闭合。
     */
    public List<MarketCandle> mapKlines(String symbol, KlineInterval interval, JsonNode root, Instant serverTime) {
        requireArray(root, "klines");
        List<MarketCandle> result = new ArrayList<>(root.size());
        for (JsonNode row : root) {
            if (!row.isArray() || row.size() < 11) {
                throw new IllegalArgumentException("Binance USDM klines 行结构不合法");
            }
            MarketCandle candle = new MarketCandle();
            candle.setProvider(providerId);
            candle.setMarketType(marketType);
            candle.setSymbol(symbol);
            candle.setInterval(interval);
            candle.setOpenTime(instantFromValue(row.get(0)));
            candle.setOpen(decimalFromValue(row.get(1)));
            candle.setHigh(decimalFromValue(row.get(2)));
            candle.setLow(decimalFromValue(row.get(3)));
            candle.setClose(decimalFromValue(row.get(4)));
            candle.setVolume(decimalFromValue(row.get(5)));
            Instant closeTime = instantFromValue(row.get(6));
            candle.setCloseTime(closeTime);
            candle.setQuoteVolume(decimalFromValue(row.get(7)));
            candle.setTradeCount(row.get(8).asLong());
            candle.setTakerBuyBaseVolume(decimalFromValue(row.get(9)));
            candle.setTakerBuyQuoteVolume(decimalFromValue(row.get(10)));
            candle.setClosed(serverTime != null && !serverTime.isBefore(closeTime));
            result.add(candle);
        }
        return result;
    }

    /**
     * 解析 Binance 错误响应 {code, msg}。
     *
     * @return 长度为 2 的数组，[0]=errorCode, [1]=errorMsg；当响应不是错误结构时返回 null。
     */
    public static int[] parseErrorCode(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode codeNode = root.get("code");
        JsonNode msgNode = root.get("msg");
        if (codeNode == null || !codeNode.canConvertToInt()) {
            return null;
        }
        return new int[]{codeNode.asInt(), 0};
    }

    public static String parseErrorMsg(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode msgNode = root.get("msg");
        return msgNode == null ? null : msgNode.asText();
    }

    // ---- 工具方法 ----

    private static String text(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return new BigDecimal(child.asText());
    }

    private static BigDecimal decimalFromValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return new BigDecimal(node.asText());
    }

    private static Instant instant(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return Instant.ofEpochMilli(child.asLong());
    }

    private static Instant instantFromValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return Instant.ofEpochMilli(node.asLong());
    }

    private static int intOr(JsonNode node, String field, int defaultValue) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return defaultValue;
        }
        return child.asInt(defaultValue);
    }

    private static void requireObject(JsonNode node, String name) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Binance USDM " + name + " 响应不是对象");
        }
    }

    private static void requireArray(JsonNode node, String name) {
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException("Binance USDM " + name + " 响应不是数组");
        }
    }
}
