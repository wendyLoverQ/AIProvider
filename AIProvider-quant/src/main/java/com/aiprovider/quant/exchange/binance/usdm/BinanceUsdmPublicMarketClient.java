package com.aiprovider.quant.exchange.binance.usdm;

import com.aiprovider.quant.market.history.port.HistoricalMarketDataProvider;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketSnapshot;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.model.PerpetualContract;
import com.aiprovider.quant.market.model.PublicMarketHealth;
import com.aiprovider.quant.market.port.PublicMarketDataProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

/**
 * Binance USDⓈ-M Futures 公共行情客户端。
 *
 * 使用 Java 17 {@link HttpClient} 直连 Binance fapi 公共端点。不使用第三方 SDK，不使用 CCXT。
 * 不重试、不降级、不伪造成功。上游失败抛出 {@link BinanceUsdmUpstreamException}。
 *
 * exchangeInfo 结果按 TTL 内存缓存，首次请求真实拉取，TTL 到期后下次请求重新拉取。
 * 刷新失败时抛出异常，不返回过期缓存伪装成功。不启动定时线程。
 *
 * snapshot 一次调用并行请求 4 个端点（24hr ticker、premiumIndex、bookTicker、openInterest），
 * 任一失败整体失败。
 */
public class BinanceUsdmPublicMarketClient implements PublicMarketDataProvider, HistoricalMarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(BinanceUsdmPublicMarketClient.class);
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z0-9]{1,32}$");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("^[A-Z0-9]{2,12}$");

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final long contractCacheTtlMs;
    private final ObjectMapper objectMapper;
    private final BinanceUsdmResponseMapper mapper;

    /** 合约目录缓存持有者，volatile 保证可见性，写入在 synchronized 块内完成。 */
    private volatile ContractCache contractCache;

    private static final class ContractCache {
        final List<PerpetualContract> contracts;
        final long fetchedAtMs;
        ContractCache(List<PerpetualContract> contracts, long fetchedAtMs) {
            this.contracts = contracts;
            this.fetchedAtMs = fetchedAtMs;
        }
    }

    public BinanceUsdmPublicMarketClient(String baseUrl, int connectTimeoutMs, int requestTimeoutMs, int contractCacheSeconds) {
        if (connectTimeoutMs < 100 || connectTimeoutMs > 60000) {
            throw new IllegalArgumentException("Binance USDM connectTimeoutMs 必须在 100 到 60000 之间");
        }
        if (requestTimeoutMs < 100 || requestTimeoutMs > 60000) {
            throw new IllegalArgumentException("Binance USDM requestTimeoutMs 必须在 100 到 60000 之间");
        }
        if (contractCacheSeconds < 1 || contractCacheSeconds > 86400) {
            throw new IllegalArgumentException("Binance USDM contractCacheSeconds 必须在 1 到 86400 之间");
        }
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.contractCacheTtlMs = contractCacheSeconds * 1000L;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.mapper = new BinanceUsdmResponseMapper(MarketProviderId.BINANCE_USDM, MarketType.USDM_PERPETUAL);
    }

    @Override
    public MarketProviderId providerId() {
        return MarketProviderId.BINANCE_USDM;
    }

    @Override
    public MarketType marketType() {
        return MarketType.USDM_PERPETUAL;
    }

    @Override
    public PublicMarketHealth health() {
        String op = "health";
        long start = System.nanoTime();
        JsonNode body = fetch("/fapi/v1/time", op, null, "/fapi/v1/time");
        long latencyMs = (System.nanoTime() - start) / 1_000_000L;

        JsonNode serverTimeNode = body.get("serverTime");
        if (serverTimeNode == null || !serverTimeNode.canConvertToLong()) {
            throw new BinanceUsdmUpstreamException(0, 0, "Binance USDM /fapi/v1/time 缺少 serverTime", null, null);
        }
        Instant serverTime = Instant.ofEpochMilli(serverTimeNode.asLong());
        Instant localTime = Instant.now();
        Instant checkedAt = Instant.now();

        PublicMarketHealth health = new PublicMarketHealth();
        health.setProvider(MarketProviderId.BINANCE_USDM);
        health.setMarketType(MarketType.USDM_PERPETUAL);
        health.setAvailable(true);
        health.setServerTime(serverTime);
        health.setLocalTime(localTime);
        health.setClockOffsetMs(serverTime.toEpochMilli() - localTime.toEpochMilli());
        health.setLatencyMs(latencyMs);
        health.setCheckedAt(checkedAt);
        log.debug("operation={} provider=BINANCE_USDM marketType=USDM_PERPETUAL success=true latencyMs={} clockOffsetMs={}",
                op, latencyMs, health.getClockOffsetMs());
        return health;
    }

    @Override
    public List<PerpetualContract> contracts(String quoteAsset) {
        String normalizedQuote = requireQuote(quoteAsset);
        ContractCache cache = this.contractCache;
        if (cache != null && (System.currentTimeMillis() - cache.fetchedAtMs) < contractCacheTtlMs) {
            return filterByQuote(cache.contracts, normalizedQuote);
        }
        synchronized (this) {
            cache = this.contractCache;
            if (cache != null && (System.currentTimeMillis() - cache.fetchedAtMs) < contractCacheTtlMs) {
                return filterByQuote(cache.contracts, normalizedQuote);
            }
            long start = System.nanoTime();
            JsonNode body = fetch("/fapi/v1/exchangeInfo", "contracts", null, "/fapi/v1/exchangeInfo");
            List<PerpetualContract> all = mapper.mapContracts(body, null);
            this.contractCache = new ContractCache(all, System.currentTimeMillis());
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            log.debug("operation=contracts provider=BINANCE_USDM upstreamPath=/fapi/v1/exchangeInfo success=true resultCount={} latencyMs={}",
                    all.size(), latencyMs);
            return filterByQuote(all, normalizedQuote);
        }
    }

    @Override
    public MarketSnapshot snapshot(String symbol) {
        String sym = requireSymbol(symbol);
        String op = "snapshot";
        long start = System.nanoTime();

        CompletableFuture<JsonNode> tickerF = fetchAsync("/fapi/v1/ticker/24hr?symbol=" + sym, op, sym, "24hr");
        CompletableFuture<JsonNode> premiumF = fetchAsync("/fapi/v1/premiumIndex?symbol=" + sym, op, sym, "premiumIndex");
        CompletableFuture<JsonNode> bookF = fetchAsync("/fapi/v1/ticker/bookTicker?symbol=" + sym, op, sym, "bookTicker");
        CompletableFuture<JsonNode> oiF = fetchAsync("/fapi/v1/openInterest?symbol=" + sym, op, sym, "openInterest");

        try {
            CompletableFuture.allOf(tickerF, premiumF, bookF, oiF).join();
        } catch (CompletionException e) {
            Throwable cause = unwrap(e);
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            logFailure(op, sym, null, latencyMs, cause);
            throw rethrow(cause);
        }

        JsonNode ticker = tickerF.join();
        JsonNode premium = premiumF.join();
        JsonNode book = bookF.join();
        JsonNode oi = oiF.join();

        MarketSnapshot snapshot = mapper.mapSnapshot(sym, ticker, premium, book, oi);
        long latencyMs = (System.nanoTime() - start) / 1_000_000L;
        log.debug("operation={} provider=BINANCE_USDM symbol={} success=true latencyMs={}", op, sym, latencyMs);
        return snapshot;
    }

    @Override
    public List<MarketCandle> klines(String symbol, KlineInterval interval, int limit) {
        String sym = requireSymbol(symbol);
        if (interval == null) {
            throw new IllegalArgumentException("K 线周期不能为空");
        }
        if (limit < 20 || limit > 500) {
            throw new IllegalArgumentException("K 线数量必须在 20 到 500 之间");
        }
        String op = "klines";
        String path = "/fapi/v1/klines?symbol=" + sym + "&interval=" + interval.code() + "&limit=" + limit;
        long start = System.nanoTime();
        JsonNode body = fetch(path, op, sym, "/fapi/v1/klines");

        // 使用 /fapi/v1/time 获取服务器时间判断 K 线闭合状态
        Instant serverTime = currentServerTime(op, sym);
        List<MarketCandle> candles = mapper.mapKlines(sym, interval, body, serverTime);
        long latencyMs = (System.nanoTime() - start) / 1_000_000L;
        log.debug("operation={} provider=BINANCE_USDM symbol={} interval={} success=true resultCount={} latencyMs={}",
                op, sym, interval.code(), candles.size(), latencyMs);
        return candles;
    }

    // ---- HistoricalMarketDataProvider ----

    @Override
    public Instant serverTime() {
        String op = "serverTime";
        JsonNode body = fetch("/fapi/v1/time", op, null, "/fapi/v1/time");
        JsonNode serverTimeNode = body.get("serverTime");
        if (serverTimeNode == null || !serverTimeNode.canConvertToLong()) {
            throw new BinanceUsdmUpstreamException(0, 0, "Binance USDM /fapi/v1/time 缺少 serverTime", null, null);
        }
        return Instant.ofEpochMilli(serverTimeNode.asLong());
    }

    @Override
    public List<MarketCandle> fetchClosedKlines(String symbol, KlineInterval interval,
                                                 long startInclusive, long endExclusive, int limit) {
        String sym = requireSymbol(symbol);
        if (interval == null) {
            throw new IllegalArgumentException("K 线周期不能为空");
        }
        if (!KlineInterval.SYNC_SUPPORTED.contains(interval)) {
            throw new IllegalArgumentException("不支持同步的 K 线周期: " + interval.code());
        }
        if (limit < 1 || limit > 1500) {
            throw new IllegalArgumentException("limit 必须在 1 到 1500 之间");
        }
        if (endExclusive <= startInclusive) {
            throw new IllegalArgumentException("endExclusive 必须大于 startInclusive");
        }

        String op = "fetchClosedKlines";
        String path = "/fapi/v1/klines?symbol=" + sym
                + "&interval=" + interval.code()
                + "&startTime=" + startInclusive
                + "&endTime=" + endExclusive
                + "&limit=" + limit;

        long start = System.nanoTime();
        JsonNode body = fetch(path, op, sym, "/fapi/v1/klines");

        Instant serverTime = serverTime();
        long serverTimeMs = serverTime.toEpochMilli();

        List<MarketCandle> all = mapper.mapKlines(sym, interval, body, serverTime);

        // 只返回已闭合 K 线（closeTime < serverTime）
        List<MarketCandle> closed = new ArrayList<>(all.size());
        for (MarketCandle c : all) {
            if (c.getCloseTime() != null && c.getCloseTime().toEpochMilli() < serverTimeMs) {
                closed.add(c);
            }
        }

        long latencyMs = (System.nanoTime() - start) / 1_000_000L;
        log.debug("operation={} provider=BINANCE_USDM symbol={} interval={} start={} end={} limit={} success=true fetched={} closed={} latencyMs={}",
                op, sym, interval.code(), startInclusive, endExclusive, limit, all.size(), closed.size(), latencyMs);
        return closed;
    }

    /** 获取上游服务器时间用于 K 线闭合判断。失败时不影响 K 线返回（closed 置为 false 表示未知）。 */
    private Instant currentServerTime(String op, String symbol) {
        try {
            return serverTime();
        } catch (BinanceUsdmUpstreamException e) {
            log.warn("operation={} provider=BINANCE_USDM symbol={} upstreamPath=/fapi/v1/time success=false httpStatus={} errorCode={} msg=服务器时间获取失败，K线闭合状态未知",
                    op, symbol, e.getHttpStatus(), e.getErrorCode());
            return null;
        }
    }

    // ---- HTTP 请求 ----

    private HttpRequest buildRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .GET()
                .header("Accept", "application/json")
                .build();
    }

    private JsonNode fetch(String path, String operation, String symbol, String upstreamPath) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(buildRequest(path), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new BinanceUsdmUpstreamException(-1, 0, "请求超时: " + upstreamPath, null, null, e);
        } catch (IOException e) {
            throw new BinanceUsdmUpstreamException(0, 0, "连接失败: " + upstreamPath, null, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BinanceUsdmUpstreamException(0, 0, "请求被中断: " + upstreamPath, null, null, e);
        }
        return handleResponse(response, operation, symbol, upstreamPath);
    }

    private CompletableFuture<JsonNode> fetchAsync(String path, String operation, String symbol, String upstream) {
        return httpClient.sendAsync(buildRequest(path), HttpResponse.BodyHandlers.ofString())
                .handle((response, ex) -> {
                    if (ex != null) {
                        throw new CompletionException(toUpstreamException(ex, path));
                    }
                    return handleResponse(response, operation, symbol, path);
                });
    }

    private JsonNode handleResponse(HttpResponse<String> response, String operation, String symbol, String upstreamPath) {
        int status = response.statusCode();
        String usedWeight = response.headers().firstValue("X-MBX-USED-WEIGHT-1M").orElse(null);

        if (status >= 200 && status < 300) {
            return parseJson(response.body(), upstreamPath);
        }

        String bodyStr = response.body();
        int errorCode = 0;
        String errorMsg = null;
        JsonNode errorNode = tryParseJson(bodyStr);
        if (errorNode != null && errorNode.isObject()) {
            JsonNode codeNode = errorNode.get("code");
            if (codeNode != null && codeNode.canConvertToInt()) {
                errorCode = codeNode.asInt();
            }
            JsonNode msgNode = errorNode.get("msg");
            if (msgNode != null && !msgNode.isNull()) {
                errorMsg = msgNode.asText();
            }
        }

        Integer retryAfter = null;
        if (status == 429 || status == 418) {
            retryAfter = response.headers().firstValue("Retry-After")
                    .flatMap(s -> {
                        try {
                            return java.util.Optional.of(Integer.parseInt(s.trim()));
                        } catch (NumberFormatException ex) {
                            return java.util.Optional.<Integer>empty();
                        }
                    })
                    .orElse(null);
            String banType = status == 429 ? "限频" : "IP被封";
            log.warn("operation={} provider=BINANCE_USDM symbol={} upstreamPath={} httpStatus={} errorCode={} usedWeight1m={} retryAfter={} success=false msg=Binance {}",
                    operation, symbol, upstreamPath, status, errorCode, usedWeight, retryAfter, banType);
        }

        throw new BinanceUsdmUpstreamException(status, errorCode, errorMsg, retryAfter, usedWeight);
    }

    private JsonNode parseJson(String body, String upstreamPath) {
        if (body == null || body.isEmpty()) {
            throw new BinanceUsdmUpstreamException(0, 0, "上游返回空响应体: " + upstreamPath, null, null);
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new BinanceUsdmUpstreamException(0, 0, "上游响应 JSON 解析失败: " + upstreamPath, null, null, e);
        }
    }

    private JsonNode tryParseJson(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 工具方法 ----

    private static BinanceUsdmUpstreamException toUpstreamException(Throwable ex, String path) {
        Throwable cause = ex;
        if (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof BinanceUsdmUpstreamException bue) {
            return bue;
        }
        if (cause instanceof HttpTimeoutException) {
            return new BinanceUsdmUpstreamException(-1, 0, "请求超时: " + path, null, null, cause);
        }
        if (cause instanceof IOException) {
            return new BinanceUsdmUpstreamException(0, 0, "连接失败: " + path, null, null, cause);
        }
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return new BinanceUsdmUpstreamException(0, 0, "请求被中断: " + path, null, null, cause);
        }
        return new BinanceUsdmUpstreamException(0, 0, "请求失败: " + path, null, null, cause);
    }

    private static Throwable unwrap(Throwable t) {
        Throwable current = t;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static RuntimeException rethrow(Throwable cause) {
        if (cause instanceof BinanceUsdmUpstreamException bue) {
            return bue;
        }
        if (cause instanceof RuntimeException re) {
            return re;
        }
        return new BinanceUsdmUpstreamException(0, 0, "并行请求失败", null, null, cause);
    }

    private void logFailure(String op, String symbol, String upstreamPath, long latencyMs, Throwable cause) {
        if (cause instanceof BinanceUsdmUpstreamException bue) {
            log.warn("operation={} provider=BINANCE_USDM symbol={} upstreamPath={} httpStatus={} errorCode={} latencyMs={} success=false msg={}",
                    op, symbol, upstreamPath, bue.getHttpStatus(), bue.getErrorCode(), latencyMs, bue.getMessage());
        } else {
            log.warn("operation={} provider=BINANCE_USDM symbol={} upstreamPath={} latencyMs={} success=false msg={}",
                    op, symbol, upstreamPath, latencyMs, cause.getMessage());
        }
    }

    private List<PerpetualContract> filterByQuote(List<PerpetualContract> all, String quoteAsset) {
        if (quoteAsset == null) {
            return all;
        }
        List<PerpetualContract> result = new ArrayList<>();
        for (PerpetualContract c : all) {
            if (quoteAsset.equalsIgnoreCase(c.getQuoteAsset()) && quoteAsset.equalsIgnoreCase(c.getMarginAsset())) {
                result.add(c);
            }
        }
        return result;
    }

    private static String requireSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (!SYMBOL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("合约符号格式不正确，应为大写英数字");
        }
        return normalized;
    }

    private static String requireQuote(String quoteAsset) {
        String normalized = quoteAsset == null ? "" : quoteAsset.trim().toUpperCase(Locale.ROOT);
        if (!QUOTE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("计价币种格式不正确");
        }
        return normalized;
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Binance USDM base-url 不能为空");
        }
        String normalized = value.trim().replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Binance USDM base-url 格式不正确: " + value, e);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Binance USDM base-url 必须使用 http 或 https: " + value);
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new IllegalArgumentException("Binance USDM base-url 缺少主机: " + value);
        }
        return normalized;
    }
}
