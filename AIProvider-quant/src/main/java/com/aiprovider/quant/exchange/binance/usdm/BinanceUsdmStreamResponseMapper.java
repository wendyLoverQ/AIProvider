package com.aiprovider.quant.exchange.binance.usdm;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;
import com.aiprovider.quant.market.stream.model.StreamMarkPriceEvent;
import com.aiprovider.quant.market.stream.model.StreamTickerEvent;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Binance USDⓈ-M Futures WebSocket 流消息映射器。
 *
 * 将 Binance 组合流（combined stream）中 {@code data} 节点的 JSON 映射为 Quant 领域事件模型。
 * 所有价格与数量使用 BigDecimal（取字符串形式构造以保留精度），时间戳（毫秒）转 Instant。
 * 不重试、不降级，遇到结构不合法或缺失字段时抛出 {@link IllegalArgumentException}。
 *
 * 支持的 Binance 事件类型：
 * <ul>
 *   <li>{@code kline} — K 线增量</li>
 *   <li>{@code 24hrTicker} — 24h 行情统计</li>
 *   <li>{@code markPriceUpdate} — 标记价格、指数价格、资金费率</li>
 *   <li>{@code bookTicker} — 最佳买卖价</li>
 * </ul>
 */
public class BinanceUsdmStreamResponseMapper {

    private final MarketProviderId providerId;
    private final MarketType marketType;

    public BinanceUsdmStreamResponseMapper(MarketProviderId providerId, MarketType marketType) {
        this.providerId = providerId;
        this.marketType = marketType;
    }

    /**
     * 根据 Binance 事件类型（{@code e} 字段）返回事件分类。
     *
     * 注意：bookTicker 流不包含 {@code e} 字段，需使用 {@link #detectFromStreamName(String)} 补充检测。
     *
     * @param data 组合流中 data 节点
     * @return 事件类型枚举，无法识别时返回 null
     */
    public StreamEventType detectEventType(JsonNode data) {
        if (data == null || !data.isObject()) {
            return null;
        }
        String eventType = text(data, "e");
        if (eventType == null) {
            return null;
        }
        switch (eventType) {
            case "kline": return StreamEventType.KLINE;
            case "24hrTicker": return StreamEventType.TICKER;
            case "markPriceUpdate": return StreamEventType.MARK_PRICE;
            case "bookTicker": return StreamEventType.BOOK_TICKER;
            default: return null;
        }
    }

    /**
     * 根据组合流的 stream 名称检测事件类型。
     *
     * Binance bookTicker 流不包含 {@code e} 字段，必须通过 stream 名称识别。
     * stream 格式：{@code btcusdt@kline_15m}、{@code btcusdt@ticker}、
     * {@code btcusdt@markPrice@1s}、{@code btcusdt@bookTicker}。
     *
     * @param streamName 组合流 stream 字段值
     * @return 事件类型枚举，无法识别时返回 null
     */
    public StreamEventType detectFromStreamName(String streamName) {
        if (streamName == null || streamName.isEmpty()) {
            return null;
        }
        if (streamName.contains("@bookTicker")) return StreamEventType.BOOK_TICKER;
        if (streamName.contains("@kline_")) return StreamEventType.KLINE;
        if (streamName.contains("@ticker")) return StreamEventType.TICKER;
        if (streamName.contains("@markPrice")) return StreamEventType.MARK_PRICE;
        return null;
    }

    /**
     * 映射 kline 事件。
     *
     * @param data 组合流中 data 节点，包含 E、s、k 字段
     */
    public StreamKlineEvent mapKline(JsonNode data) {
        requireObject(data, "kline");
        JsonNode k = data.get("k");
        requireObject(k, "kline.k");

        StreamKlineEvent event = new StreamKlineEvent();
        event.setProvider(providerId);
        event.setMarketType(marketType);
        event.setSymbol(text(data, "s"));
        event.setEventTime(instant(data, "E"));
        event.setOpenTime(instant(k, "t"));
        event.setCloseTime(instant(k, "T"));
        String intervalCode = text(k, "i");
        if (intervalCode != null) {
            event.setInterval(KlineInterval.fromCode(intervalCode));
        }
        event.setOpen(decimal(k, "o"));
        event.setHigh(decimal(k, "h"));
        event.setLow(decimal(k, "l"));
        event.setClose(decimal(k, "c"));
        event.setVolume(decimal(k, "v"));
        event.setQuoteVolume(decimal(k, "q"));
        event.setTradeCount(k.path("n").asLong(0));
        event.setTakerBuyBaseVolume(decimal(k, "V"));
        event.setTakerBuyQuoteVolume(decimal(k, "Q"));
        event.setClosed(k.path("x").asBoolean(false));
        return event;
    }

    /**
     * 映射 24hrTicker 事件。
     *
     * @param data 组合流中 data 节点
     */
    public StreamTickerEvent mapTicker(JsonNode data) {
        requireObject(data, "ticker");
        StreamTickerEvent event = new StreamTickerEvent();
        event.setProvider(providerId);
        event.setMarketType(marketType);
        event.setSymbol(text(data, "s"));
        event.setEventTime(instant(data, "E"));
        event.setLastPrice(decimal(data, "c"));
        event.setPriceChange(decimal(data, "p"));
        event.setPriceChangePercent(decimal(data, "P"));
        event.setHighPrice(decimal(data, "h"));
        event.setLowPrice(decimal(data, "l"));
        event.setVolume(decimal(data, "v"));
        event.setQuoteVolume(decimal(data, "q"));
        return event;
    }

    /**
     * 映射 markPriceUpdate 事件。
     *
     * @param data 组合流中 data 节点
     */
    public StreamMarkPriceEvent mapMarkPrice(JsonNode data) {
        requireObject(data, "markPrice");
        StreamMarkPriceEvent event = new StreamMarkPriceEvent();
        event.setProvider(providerId);
        event.setMarketType(marketType);
        event.setSymbol(text(data, "s"));
        event.setEventTime(instant(data, "E"));
        event.setMarkPrice(decimal(data, "p"));
        event.setIndexPrice(decimal(data, "i"));
        event.setEstimatedSettlePrice(decimal(data, "P"));
        event.setLastFundingRate(decimal(data, "r"));
        // Binance markPriceUpdate 流不含 interestRate 字段，保持 null
        event.setInterestRate(null);
        event.setNextFundingTime(instant(data, "T"));
        return event;
    }

    /**
     * 映射 bookTicker 事件。
     *
     * Binance USDⓈ-M bookTicker 流不包含 {@code e} 字段，事件类型需通过 stream 名称识别。
     * 组合流 data 节点包含 {@code E} 字段（事件时间），缺失时使用接收时间。
     *
     * @param data 组合流中 data 节点
     */
    public StreamBookTickerEvent mapBookTicker(JsonNode data) {
        requireObject(data, "bookTicker");
        StreamBookTickerEvent event = new StreamBookTickerEvent();
        event.setProvider(providerId);
        event.setMarketType(marketType);
        event.setSymbol(text(data, "s"));
        Instant eventTime = instant(data, "E");
        event.setEventTime(eventTime != null ? eventTime : Instant.now());
        event.setBidPrice(decimal(data, "b"));
        event.setBidQuantity(decimal(data, "B"));
        event.setAskPrice(decimal(data, "a"));
        event.setAskQuantity(decimal(data, "A"));
        return event;
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

    private static Instant instant(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return Instant.ofEpochMilli(child.asLong());
    }

    private static void requireObject(JsonNode node, String name) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Binance USDM stream " + name + " 响应不是对象");
        }
    }

    /** Binance WebSocket 事件类型分类。 */
    public enum StreamEventType {
        KLINE,
        TICKER,
        MARK_PRICE,
        BOOK_TICKER
    }
}
