package com.aiprovider.quant.exchange.binance.usdm;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;
import com.aiprovider.quant.market.stream.model.StreamMarkPriceEvent;
import com.aiprovider.quant.market.stream.model.StreamStatus;
import com.aiprovider.quant.market.stream.model.StreamStatusEvent;
import com.aiprovider.quant.market.stream.model.StreamTickerEvent;
import com.aiprovider.quant.market.stream.port.MarketStreamClient;
import com.aiprovider.quant.market.stream.port.MarketStreamListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Binance USDⓈ-M Futures 实时行情流客户端。
 *
 * 使用 Java 17 {@link java.net.http.WebSocket} 连接 Binance fstream 组合流端点。
 * 不使用第三方 SDK，不使用 CCXT。不重试、不降级、不伪造成功。
 *
 * 核心设计：
 * <ul>
 *   <li>同一订阅键（provider + symbol + interval）只维护一个上游连接</li>
 *   <li>多个 listener 共享一个连接，事件广播给所有 listener</li>
 *   <li>最后一个 listener 离开后关闭上游连接</li>
 *   <li>连接断开后自动重连（指数退避，上限 6 次）</li>
 * </ul>
 *
 * 组合流订阅 4 个频道：kline、ticker、markPrice@1s、bookTicker。
 * 消息格式：{@code {"stream":"btcusdt@kline_15m","data":{...}}}
 */
public class BinanceUsdmMarketStreamClient implements MarketStreamClient, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(BinanceUsdmMarketStreamClient.class);

    private final String wsBaseUrl;
    private final Duration connectTimeout;
    private final int maxReconnectAttempts;
    private final long initialReconnectDelayMs;
    private final long maxReconnectDelayMs;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final BinanceUsdmStreamResponseMapper mapper;

    /** 订阅键到连接持有者的映射。 */
    private final Map<SubscriptionKey, ConnectionHolder> connections = new ConcurrentHashMap<>();

    /** 重连调度器，单线程，守护线程。 */
    private final ScheduledExecutorService reconnectExecutor;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public BinanceUsdmMarketStreamClient(String wsBaseUrl, int connectTimeoutMs,
                                          int maxReconnectAttempts,
                                          long initialReconnectDelayMs,
                                          long maxReconnectDelayMs) {
        if (wsBaseUrl == null || wsBaseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Binance USDM wsBaseUrl 不能为空");
        }
        if (connectTimeoutMs < 1000 || connectTimeoutMs > 60000) {
            throw new IllegalArgumentException("Binance USDM connectTimeoutMs 必须在 1000 到 60000 之间");
        }
        if (maxReconnectAttempts < 1 || maxReconnectAttempts > 20) {
            throw new IllegalArgumentException("Binance USDM maxReconnectAttempts 必须在 1 到 20 之间");
        }
        this.wsBaseUrl = wsBaseUrl.replaceAll("/+$", "");
        this.connectTimeout = Duration.ofMillis(connectTimeoutMs);
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.initialReconnectDelayMs = initialReconnectDelayMs;
        this.maxReconnectDelayMs = maxReconnectDelayMs;
        this.objectMapper = new ObjectMapper();
        this.mapper = new BinanceUsdmStreamResponseMapper(MarketProviderId.BINANCE_USDM, MarketType.USDM_PERPETUAL);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "binance-usdm-stream-reconnect");
            t.setDaemon(true);
            return t;
        });
        log.info("operation=stream-init wsBaseUrl={} connectTimeoutMs={} maxReconnectAttempts={} initialDelayMs={} maxDelayMs={}",
                this.wsBaseUrl, connectTimeoutMs, maxReconnectAttempts, initialReconnectDelayMs, maxReconnectDelayMs);
    }

    // ---- MarketStreamClient 接口实现 ----

    @Override
    public void subscribe(MarketProviderId provider, String symbol, KlineInterval interval, MarketStreamListener listener) {
        Objects.requireNonNull(provider, "provider 不能为空");
        Objects.requireNonNull(symbol, "symbol 不能为空");
        Objects.requireNonNull(interval, "interval 不能为空");
        Objects.requireNonNull(listener, "listener 不能为空");

        if (provider != MarketProviderId.BINANCE_USDM) {
            throw new IllegalArgumentException("不支持的行情提供方: " + provider);
        }

        SubscriptionKey key = new SubscriptionKey(provider, symbol.toUpperCase(Locale.ROOT), interval);
        ConnectionHolder holder = connections.computeIfAbsent(key, k -> new ConnectionHolder(k));

        synchronized (holder) {
            holder.listeners.add(listener);
            if (holder.listeners.size() == 1) {
                // 第一个订阅者，启动上游连接
                holder.connect();
            } else {
                // 已有连接，通知当前状态
                notifyStatus(holder, listener, holder.currentStatus, "已加入现有订阅");
            }
        }

        log.info("operation=stream-subscribe provider={} symbol={} interval={} listenerCount={}",
                provider, symbol, interval.code(), holder.listeners.size());
    }

    @Override
    public void unsubscribe(MarketProviderId provider, String symbol, KlineInterval interval, MarketStreamListener listener) {
        Objects.requireNonNull(provider, "provider 不能为空");
        Objects.requireNonNull(symbol, "symbol 不能为空");
        Objects.requireNonNull(interval, "interval 不能为空");
        Objects.requireNonNull(listener, "listener 不能为空");

        SubscriptionKey key = new SubscriptionKey(provider, symbol.toUpperCase(Locale.ROOT), interval);
        ConnectionHolder holder = connections.get(key);
        if (holder == null) {
            return;
        }

        synchronized (holder) {
            holder.listeners.remove(listener);
            int remaining = holder.listeners.size();
            log.info("operation=stream-unsubscribe provider={} symbol={} interval={} listenerCount={}",
                    provider, symbol, interval.code(), remaining);
            if (remaining == 0) {
                // 最后一个订阅者离开，关闭上游连接
                holder.intentionalClose = true;
                holder.close();
                connections.remove(key, holder);
            }
        }
    }

    // ---- 内部方法 ----

    /** 通知单个 listener 状态变更。 */
    private void notifyStatus(ConnectionHolder holder, MarketStreamListener listener, StreamStatus status, String message) {
        try {
            StreamStatusEvent event = new StreamStatusEvent();
            event.setProvider(holder.key.provider);
            event.setSymbol(holder.key.symbol);
            event.setInterval(holder.key.interval);
            event.setStatus(status);
            event.setMessage(message);
            event.setTimestamp(Instant.now());
            listener.onStatus(event);
        } catch (Exception e) {
            log.warn("operation=stream-notify-status symbol={} status={} listenerError={}",
                    holder.key.symbol, status, e.getMessage());
        }
    }

    /** 广播状态变更给所有 listener。 */
    private void broadcastStatus(ConnectionHolder holder, StreamStatus status, String message) {
        for (MarketStreamListener listener : holder.listeners) {
            notifyStatus(holder, listener, status, message);
        }
    }

    /** 广播 kline 事件给所有 listener。 */
    private void broadcastKline(ConnectionHolder holder, StreamKlineEvent event) {
        for (MarketStreamListener listener : holder.listeners) {
            try {
                listener.onKline(event);
            } catch (Exception e) {
                log.warn("operation=stream-broadcast-kline symbol={} listenerError={}",
                        holder.key.symbol, e.getMessage());
            }
        }
    }

    private void broadcastTicker(ConnectionHolder holder, StreamTickerEvent event) {
        for (MarketStreamListener listener : holder.listeners) {
            try {
                listener.onTicker(event);
            } catch (Exception e) {
                log.warn("operation=stream-broadcast-ticker symbol={} listenerError={}",
                        holder.key.symbol, e.getMessage());
            }
        }
    }

    private void broadcastMarkPrice(ConnectionHolder holder, StreamMarkPriceEvent event) {
        for (MarketStreamListener listener : holder.listeners) {
            try {
                listener.onMarkPrice(event);
            } catch (Exception e) {
                log.warn("operation=stream-broadcast-markprice symbol={} listenerError={}",
                        holder.key.symbol, e.getMessage());
            }
        }
    }

    private void broadcastBookTicker(ConnectionHolder holder, StreamBookTickerEvent event) {
        for (MarketStreamListener listener : holder.listeners) {
            try {
                listener.onBookTicker(event);
            } catch (Exception e) {
                log.warn("operation=stream-broadcast-bookticker symbol={} listenerError={}",
                        holder.key.symbol, e.getMessage());
            }
        }
    }

    /** 处理收到的组合流文本消息。 */
    private void handleMessage(ConnectionHolder holder, String text) {
        try {
            JsonNode root = objectMapper.readTree(text);
            if (root == null || !root.isObject()) {
                return;
            }
            // 组合流格式：{"stream":"btcusdt@kline_15m","data":{...}}
            String streamName = root.path("stream").asText(null);
            JsonNode data = root.get("data");
            if (data == null || !data.isObject()) {
                // 非组合流格式，尝试直接当 data 处理
                data = root;
                streamName = null;
            }

            // 先通过 data 中的 e 字段检测事件类型，bookTicker 不含 e 字段时回退到 stream 名称
            BinanceUsdmStreamResponseMapper.StreamEventType type = mapper.detectEventType(data);
            if (type == null && streamName != null) {
                type = mapper.detectFromStreamName(streamName);
            }
            if (type == null) {
                log.debug("operation=stream-message symbol={} msg=无法识别的事件类型 stream={} raw={}",
                        holder.key.symbol, streamName,
                        text.length() > 200 ? text.substring(0, 200) + "..." : text);
                return;
            }

            switch (type) {
                case KLINE -> {
                    StreamKlineEvent event = mapper.mapKline(data);
                    if (event.getInterval() == holder.key.interval) {
                        broadcastKline(holder, event);
                    }
                }
                case TICKER -> broadcastTicker(holder, mapper.mapTicker(data));
                case MARK_PRICE -> broadcastMarkPrice(holder, mapper.mapMarkPrice(data));
                case BOOK_TICKER -> broadcastBookTicker(holder, mapper.mapBookTicker(data));
            }
        } catch (Exception e) {
            log.warn("operation=stream-message symbol={} parseError={}",
                    holder.key.symbol, e.getMessage());
        }
    }

    // ---- DisposableBean ----

    @Override
    public void destroy() {
        shutdown.set(true);
        log.info("operation=stream-shutdown msg=正在关闭所有上游连接 connections={}", connections.size());
        for (ConnectionHolder holder : connections.values()) {
            synchronized (holder) {
                holder.intentionalClose = true;
                holder.close();
            }
        }
        connections.clear();
        reconnectExecutor.shutdownNow();
        log.info("operation=stream-shutdown msg=所有上游连接已关闭");
    }

    // ---- 订阅键 ----

    static final class SubscriptionKey {
        final MarketProviderId provider;
        final String symbol;
        final KlineInterval interval;

        SubscriptionKey(MarketProviderId provider, String symbol, KlineInterval interval) {
            this.provider = provider;
            this.symbol = symbol;
            this.interval = interval;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SubscriptionKey that)) return false;
            return provider == that.provider
                    && symbol.equals(that.symbol)
                    && interval == that.interval;
        }

        @Override
        public int hashCode() {
            return Objects.hash(provider, symbol, interval);
        }
    }

    // ---- 连接持有者 ----

    /**
     * 管理一个上游 WebSocket 连接及其所有订阅者。
     *
     * 线程安全：对 holder 的状态变更（connect/close/reconnect）在 synchronized(holder) 块内完成。
     * listener 集合使用 CopyOnWriteArraySet 保证遍历安全。
     */
    final class ConnectionHolder {

        final SubscriptionKey key;
        final Set<MarketStreamListener> listeners = new CopyOnWriteArraySet<>();

        volatile WebSocket webSocket;
        volatile StreamStatus currentStatus = StreamStatus.CONNECTING;
        final AtomicInteger reconnectAttempts = new AtomicInteger(0);
        volatile boolean intentionalClose = false;
        volatile ScheduledFuture<?> reconnectFuture;

        /** 消息文本缓冲，WebSocket 可能分片投递。 */
        final StringBuilder textBuffer = new StringBuilder(4096);

        ConnectionHolder(SubscriptionKey key) {
            this.key = key;
        }

        /** 构建并连接上游 WebSocket。 */
        void connect() {
            if (shutdown.get()) {
                return;
            }
            currentStatus = StreamStatus.CONNECTING;
            broadcastStatus(this, StreamStatus.CONNECTING, "正在连接 Binance WebSocket");

            String streams = buildStreamsParam();
            URI uri = URI.create(wsBaseUrl + "/stream?streams=" + streams);
            log.info("operation=stream-connect symbol={} interval={} streams={}",
                    key.symbol, key.interval.code(), streams);

            try {
                httpClient.newWebSocketBuilder()
                        .connectTimeout(connectTimeout)
                        .buildAsync(uri, new StreamWebSocketListener(this))
                        .whenComplete((ws, ex) -> {
                            if (ex != null) {
                                log.warn("operation=stream-connect symbol={} interval={} success=false error={}",
                                        key.symbol, key.interval.code(), ex.getMessage());
                                scheduleReconnect(ex);
                            } else {
                                // 连接成功，onOpen 会处理状态
                                webSocket = ws;
                            }
                        });
            } catch (Exception e) {
                log.warn("operation=stream-connect symbol={} interval={} success=false error={}",
                        key.symbol, key.interval.code(), e.getMessage());
                scheduleReconnect(e);
            }
        }

        /** 关闭上游连接。 */
        void close() {
            WebSocket ws = webSocket;
            webSocket = null;
            if (ws != null) {
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "client closing");
                } catch (Exception e) {
                    log.debug("operation=stream-close symbol={} sendCloseError={}", key.symbol, e.getMessage());
                }
            }
            cancelReconnect();
            currentStatus = StreamStatus.DISCONNECTED;
            if (!intentionalClose) {
                broadcastStatus(this, StreamStatus.DISCONNECTED, "连接已断开");
            }
        }

        /** 安排重连。 */
        void scheduleReconnect(Throwable cause) {
            if (shutdown.get() || intentionalClose) {
                return;
            }
            int attempt = reconnectAttempts.incrementAndGet();
            if (attempt > maxReconnectAttempts) {
                log.error("operation=stream-reconnect symbol={} interval={} msg=重连次数已达上限 attempts={}",
                        key.symbol, key.interval.code(), attempt - 1);
                currentStatus = StreamStatus.FAILED;
                broadcastStatus(this, StreamStatus.FAILED, "重连次数已达上限: " + cause.getMessage());
                return;
            }

            currentStatus = StreamStatus.RECONNECTING;
            broadcastStatus(this, StreamStatus.RECONNECTING, "第 " + attempt + " 次重连: " + cause.getMessage());

            long delay = Math.min(initialReconnectDelayMs * (1L << (attempt - 1)), maxReconnectDelayMs);
            log.info("operation=stream-reconnect symbol={} interval={} attempt={} delayMs={}",
                    key.symbol, key.interval.code(), attempt, delay);

            cancelReconnect();
            reconnectFuture = reconnectExecutor.schedule(() -> {
                synchronized (this) {
                    if (!shutdown.get() && !intentionalClose && !listeners.isEmpty()) {
                        connect();
                    }
                }
            }, delay, TimeUnit.MILLISECONDS);
        }

        void cancelReconnect() {
            ScheduledFuture<?> future = reconnectFuture;
            reconnectFuture = null;
            if (future != null) {
                future.cancel(false);
            }
        }

        /** 构建组合流 streams 参数。 */
        String buildStreamsParam() {
            String sym = key.symbol.toLowerCase(Locale.ROOT);
            return sym + "@kline_" + key.interval.code()
                    + "/" + sym + "@ticker"
                    + "/" + sym + "@markPrice@1s"
                    + "/" + sym + "@bookTicker";
        }
    }

    // ---- WebSocket 监听器 ----

    /**
     * Java 17 WebSocket.Listener 实现，处理上游 Binance 连接事件。
     */
    final class StreamWebSocketListener implements WebSocket.Listener {

        private final ConnectionHolder holder;

        StreamWebSocketListener(ConnectionHolder holder) {
            this.holder = holder;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            log.info("operation=stream-onopen symbol={} interval={}",
                    holder.key.symbol, holder.key.interval.code());
            synchronized (holder) {
                holder.webSocket = webSocket;
                holder.reconnectAttempts.set(0);
                holder.currentStatus = StreamStatus.LIVE;
            }
            broadcastStatus(holder, StreamStatus.LIVE, "Binance 实时连接已建立");
            webSocket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (holder.textBuffer) {
                holder.textBuffer.append(data);
                if (last) {
                    String message = holder.textBuffer.toString();
                    holder.textBuffer.setLength(0);
                    handleMessage(holder, message);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("operation=stream-onclose symbol={} interval={} statusCode={} reason={}",
                    holder.key.symbol, holder.key.interval.code(), statusCode, reason);
            synchronized (holder) {
                holder.webSocket = null;
                if (!holder.intentionalClose && !shutdown.get()) {
                    holder.scheduleReconnect(new RuntimeException("WebSocket 关闭: " + statusCode + " " + reason));
                } else {
                    holder.currentStatus = StreamStatus.DISCONNECTED;
                }
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("operation=stream-onerror symbol={} interval={} error={}",
                    holder.key.symbol, holder.key.interval.code(), error.getMessage());
            synchronized (holder) {
                holder.webSocket = null;
                if (!holder.intentionalClose && !shutdown.get()) {
                    holder.scheduleReconnect(error);
                }
            }
        }
    }
}
