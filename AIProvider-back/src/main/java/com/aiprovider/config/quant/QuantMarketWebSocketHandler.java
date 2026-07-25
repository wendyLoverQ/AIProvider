package com.aiprovider.config.quant;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;
import com.aiprovider.quant.market.stream.model.StreamMarkPriceEvent;
import com.aiprovider.quant.market.stream.model.StreamStatus;
import com.aiprovider.quant.market.stream.model.StreamStatusEvent;
import com.aiprovider.quant.market.stream.model.StreamTickerEvent;
import com.aiprovider.quant.market.stream.port.MarketStreamClient;
import com.aiprovider.quant.market.stream.port.MarketStreamListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 量化合约行情 WebSocket 处理器。
 *
 * 前端连接 {@code /ws/quant/market} 后发送 SUBSCRIBE/UNSUBSCRIBE 消息，
 * 处理器为每个会话创建一个 {@link MarketStreamListener} 并注册到 {@link MarketStreamClient}，
 * 将上游 Binance 实时事件序列化为 JSON 推送给前端。
 *
 * 消息协议：
 * <pre>
 * 前端 → 后端：
 *   {"action":"SUBSCRIBE","provider":"BINANCE_USDM","symbol":"BTCUSDT","interval":"15m"}
 *   {"action":"UNSUBSCRIBE"}
 *
 * 后端 → 前端：
 *   {"type":"STATUS","eventTime":"...","symbol":"...","interval":"...","data":{"status":"LIVE","message":"..."}}
 *   {"type":"KLINE","eventTime":"...","symbol":"...","interval":"...","data":{...kline fields...}}
 *   {"type":"TICKER","eventTime":"...","symbol":"...","data":{...ticker fields...}}
 *   {"type":"MARK_PRICE","eventTime":"...","symbol":"...","data":{...mark price fields...}}
 *   {"type":"BOOK_TICKER","eventTime":"...","symbol":"...","data":{...book ticker fields...}}
 *   {"type":"ERROR","eventTime":"...","data":{"message":"..."}}
 * </pre>
 *
 * 线程安全：handler 是 Spring 单例，Binance 回调可能在不同线程触发。
 * 每个会话的 sendMessage 在 synchronized(session) 块内完成，防止并发写入。
 */
@Component
public class QuantMarketWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(QuantMarketWebSocketHandler.class);
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z0-9]{1,32}$");

    private final MarketStreamClient streamClient;
    private final ObjectMapper objectMapper;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public QuantMarketWebSocketHandler(MarketStreamClient streamClient, ObjectMapper objectMapper) {
        this.streamClient = streamClient;
        this.objectMapper = objectMapper;
    }

    // ---- 前端消息处理 ----

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        ObjectNode node;
        try {
            node = (ObjectNode) objectMapper.readTree(payload);
        } catch (Exception e) {
            sendError(session, "消息解析失败: " + e.getMessage());
            return;
        }

        String action = node.path("action").asText("");
        switch (action) {
            case "SUBSCRIBE" -> handleSubscribe(session, node);
            case "UNSUBSCRIBE" -> handleUnsubscribe(session);
            default -> sendError(session, "未知的 action: " + action);
        }
    }

    private void handleSubscribe(WebSocketSession session, ObjectNode node) {
        String providerStr = node.path("provider").asText("");
        String symbol = node.path("symbol").asText("");
        String intervalStr = node.path("interval").asText("");

        // 先取消旧订阅
        unsubscribeCurrent(session);

        // 校验参数
        MarketProviderId provider;
        try {
            provider = MarketProviderId.valueOf(providerStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sendError(session, "不支持的行情提供方: " + providerStr);
            return;
        }

        String sym = symbol.trim().toUpperCase(Locale.ROOT);
        if (!SYMBOL_PATTERN.matcher(sym).matches()) {
            sendError(session, "合约符号格式不正确，应为大写英数字");
            return;
        }

        KlineInterval interval;
        try {
            interval = KlineInterval.fromCode(intervalStr.trim());
        } catch (IllegalArgumentException e) {
            sendError(session, "不支持的 K 线周期: " + intervalStr);
            return;
        }

        // 创建会话状态和监听器
        SessionState state = new SessionState(provider, sym, interval);
        state.listener = new SessionStreamListener(session, sym, interval);
        sessions.put(session.getId(), state);

        // 订阅上游
        streamClient.subscribe(provider, sym, interval, state.listener);
        log.info("operation=ws-subscribe sessionId={} provider={} symbol={} interval={}",
                session.getId(), provider, sym, interval.code());
    }

    private void handleUnsubscribe(WebSocketSession session) {
        unsubscribeCurrent(session);
        log.info("operation=ws-unsubscribe sessionId={}", session.getId());
    }

    // ---- 会话生命周期 ----

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        unsubscribeCurrent(session);
        log.info("operation=ws-closed sessionId={} status={} activeSessions={}",
                session.getId(), status, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) {
        log.warn("operation=ws-transport-error sessionId={} error={}",
                session.getId(), error.getMessage());
        unsubscribeCurrent(session);
    }

    // ---- 内部方法 ----

    /** 取消当前会话的上游订阅，清理会话状态。 */
    private void unsubscribeCurrent(WebSocketSession session) {
        SessionState state = sessions.remove(session.getId());
        if (state != null && state.listener != null) {
            streamClient.unsubscribe(state.provider, state.symbol, state.interval, state.listener);
        }
    }

    /** 发送错误消息给前端。 */
    private void sendError(WebSocketSession session, String message) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("type", "ERROR");
        msg.put("eventTime", Instant.now().toString());
        ObjectNode data = msg.putObject("data");
        data.put("message", message);
        sendJson(session, msg);
    }

    /** 安全发送 JSON 消息给前端，处理会话关闭和写入异常。 */
    private void sendJson(WebSocketSession session, ObjectNode message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(message);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.warn("operation=ws-send sessionId={} error={}", session.getId(), e.getMessage());
        } catch (IllegalStateException e) {
            // 会话可能正在关闭
            log.debug("operation=ws-send sessionId={} sessionStateError={}", session.getId(), e.getMessage());
        }
    }

    // ---- 会话状态 ----

    private static final class SessionState {
        final MarketProviderId provider;
        final String symbol;
        final KlineInterval interval;
        MarketStreamListener listener;

        SessionState(MarketProviderId provider, String symbol, KlineInterval interval) {
            this.provider = provider;
            this.symbol = symbol;
            this.interval = interval;
        }
    }

    // ---- 会话事件监听器 ----

    /**
     * 每个前端 WebSocket 会话对应一个监听器，将上游事件序列化为 JSON 并推送给前端。
     */
    private final class SessionStreamListener implements MarketStreamListener {

        private final WebSocketSession session;
        private final String symbol;
        private final KlineInterval interval;

        SessionStreamListener(WebSocketSession session, String symbol, KlineInterval interval) {
            this.session = session;
            this.symbol = symbol;
            this.interval = interval;
        }

        @Override
        public void onStatus(StreamStatusEvent event) {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("type", "STATUS");
            if (event.getTimestamp() != null) {
                msg.put("eventTime", event.getTimestamp().toString());
            }
            msg.put("symbol", symbol);
            msg.put("interval", interval.code());
            ObjectNode data = msg.putObject("data");
            data.put("status", event.getStatus().name());
            if (event.getMessage() != null) {
                data.put("message", event.getMessage());
            }
            sendJson(session, msg);
        }

        @Override
        public void onKline(StreamKlineEvent event) {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("type", "KLINE");
            if (event.getEventTime() != null) {
                msg.put("eventTime", event.getEventTime().toString());
            }
            msg.put("symbol", event.getSymbol() != null ? event.getSymbol() : symbol);
            msg.put("interval", interval.code());
            ObjectNode data = objectMapper.valueToTree(event);
            data.remove("provider");
            data.remove("marketType");
            data.remove("symbol");
            data.remove("interval");
            data.remove("eventTime");
            msg.set("data", data);
            sendJson(session, msg);
        }

        @Override
        public void onTicker(StreamTickerEvent event) {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("type", "TICKER");
            if (event.getEventTime() != null) {
                msg.put("eventTime", event.getEventTime().toString());
            }
            msg.put("symbol", event.getSymbol() != null ? event.getSymbol() : symbol);
            ObjectNode data = objectMapper.valueToTree(event);
            data.remove("provider");
            data.remove("marketType");
            data.remove("symbol");
            data.remove("eventTime");
            msg.set("data", data);
            sendJson(session, msg);
        }

        @Override
        public void onMarkPrice(StreamMarkPriceEvent event) {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("type", "MARK_PRICE");
            if (event.getEventTime() != null) {
                msg.put("eventTime", event.getEventTime().toString());
            }
            msg.put("symbol", event.getSymbol() != null ? event.getSymbol() : symbol);
            ObjectNode data = objectMapper.valueToTree(event);
            data.remove("provider");
            data.remove("marketType");
            data.remove("symbol");
            data.remove("eventTime");
            msg.set("data", data);
            sendJson(session, msg);
        }

        @Override
        public void onBookTicker(StreamBookTickerEvent event) {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("type", "BOOK_TICKER");
            if (event.getEventTime() != null) {
                msg.put("eventTime", event.getEventTime().toString());
            }
            msg.put("symbol", event.getSymbol() != null ? event.getSymbol() : symbol);
            ObjectNode data = objectMapper.valueToTree(event);
            data.remove("provider");
            data.remove("marketType");
            data.remove("symbol");
            data.remove("eventTime");
            msg.set("data", data);
            sendJson(session, msg);
        }
    }
}
